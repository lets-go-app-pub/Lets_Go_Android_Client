package site.letsgoapp.letsgo.workers.chatStreamWorker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.*
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.NotificationsEnabledChatRoomInfo
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.ReturnForNotificationMessage
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.ChatStreamWorkerRepository
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class ChatStreamWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val applicationClass: LetsGoApplicationClass =
        applicationContext as LetsGoApplicationClass

    private val chatStreamWorkerUUID = UUID.randomUUID()

    //Certain options inside workerRespondToLogin() can run another login causing
    // it to endlessly cycle. This boolean will be set to true if the failedToLogin
    // lambda was called and the worker is waiting to cancel.
    private val chatStreamWorkerCanceled = AtomicBoolean(false)

    //NOTE: the application context only ends when the primary application ends and any workers (they can still be in queue) running end
    // this means the application context will not be re-created between worker runs or when the application is backgrounded
    private var subscriberWrapper: ChatStreamSubscriberWrapper? = null

    private val errorHandler: StoreErrorsInterface = ServiceLocator.globalErrorStore

    private val chatStreamDispatcher = ServiceLocator.globalIODispatcher

    override suspend fun doWork(): Result =
        withContext(chatStreamDispatcher) {
            Log.i("chatStreamSubscription", "starting ChatStreamWorker CoroutineWorker")

            Log.i("returnMessagesZAX", "starting ChatStreamWorker CoroutineWorker")
            if (chatStreamWorkerInstanceRunning.compareAndSet(false, true)) { //if instance is not running

                //NOTE: coRoutine cancellation should not be able to work inside the gap here, it
                // needs specific functions to allow this function to cancel such as delay() or
                // yield(), however don't do too much before allowing for cancellation because
                // the 10 minute timeout for workManager could break it.
                try {

                    Log.i("StreamWorker", "instance not running")

                    val runChatStreamJob = CoroutineScope(chatStreamDispatcher).launch {
                        try {

                            Log.i("StreamWorker", "starting primary work")
                            if (GlobalValues.setupForTesting) {
                                doWorkForFunction()
                            } else {
                                withTimeout(CHAT_STREAM_WORKER_RUN_TIME_MS) {
                                    doWorkForFunction()
                                }
                            }

                        } finally {

                            //These subscriptions are removed in this way because a Coroutine Mutex being locked while this
                            // coRoutine is being cancelled will automatically cancel. This will cause the finally block to
                            // end without unsubscribing leaving the object(s) alive in memory.

                            val unsubscribeLoginFunctionsJob =
                                LoginFunctions.chatStreamWorkerUnsubscribe(chatStreamWorkerUUID)

                            val unsubscribeChatStreamJob = applicationClass.chatStreamObject
                                .beginUnSubscribeJob(
                                    subscriberWrapper,
                                    ChatStreamObject.SubscriberType.CHAT_STREAM_WORKER_SUBSCRIBER
                                )

                            unsubscribeLoginFunctionsJob.join()
                            unsubscribeChatStreamJob.join()
                        }
                    }

                    runChatStreamJob.join()
                    return@withContext Result.success()
                } catch (e: Exception) {

                    //if coRoutine was cancelled, propagate it
                    if (e is CancellationException)
                        throw e

                    //The UncaughtExceptionHandler doesn't seem to catch worker exceptions.

                    val errorMessage =
                        "An exception was thrown when ChatStreamWorker was run.\n${e.message}"

                    errorHandler.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        e.stackTraceToString(),
                        errorMessage,
                        appContext
                    )

                    continueChatStreamWorker.set(false)

                    return@withContext Result.failure()
                } finally {

                    subscriberWrapper?.clear()

                    //NOTE:this block should be called if the coroutine is canceled
                    //if instance stops, set instance running to false
                    //the withLock() will be cancelled because it uses a withContext() inside of it,
                    // this means that chatStreamWorkerInstanceRunning must be set BEFORE the withLock()
                    chatStreamWorkerInstanceRunning.set(false)

                    /** This is NOT a CoroutineReentrantLock, it is a ReentrantLock. See chatStreamWorkerMutex for details. **/
                    chatStreamWorkerMutex.withLock {
                        //NOTE: isStopped can also be called because say the network goes down, this could make the worker not start again if checking for it
                        if (continueChatStreamWorker.get()) {
                            startChatStreamWorker(appContext)
                        }
                    }

                    Log.i("returnMessagesZAX", "completing ChatStreamWorker CoroutineWorker")
                }

            } else { //if another instance of this worker is already running
                Log.i(
                    "returnMessagesZAX",
                    "completing ChatStreamWorker (another instance was found to be running)"
                )
                return@withContext Result.success()
            }
        }

    private suspend fun doWorkForFunction() =
        withContext(chatStreamDispatcher) {

            val coroutineConditionVariable = MakeshiftCoroutineConditionVariable()

            applicationClass.loginFunctions.beginLoginToServerIfNotAlreadyRunning()

            //create subscriber
            subscriberWrapper = ChatStreamSubscriberWrapper(
                ChatStreamWorkerSubscriber(
                    applicationContext,
                    LoginFunctions.currentAccountOID,
                    coroutineConditionVariable,
                    errorHandler,
                    ioDispatcher = chatStreamDispatcher,
                ),
                chatStreamDispatcher
            )

            //yield before the subscription in case this is running while application repository is running
            yield()

            SystemClock.elapsedRealtime()

            LoginFunctions.chatStreamWorkerSubscribe(chatStreamWorkerUUID) {

                LoginFunctions.receivedMessage(it)

                //NOTE: Do not lock chatStreamWorkerMutex here, workerRespondToLogin can call
                // cancelWorkers() which requires locking it.
                if (!chatStreamWorkerCanceled.get()) {
                    workerRespondToLogin(
                        appContext,
                        it,
                        successfullyLoggedIn = {
                            subscribeToChatStream()
                        },
                        failedToLogin = {
                            chatStreamWorkerCanceled.set(true)
                            continueChatStreamWorker.set(false)

                            coroutineConditionVariable.notifyOne()
                        },
                        loginFunctionsRetrying = { }
                    )
                }
            }

            //the timeout wrapping this function should cancel the function before this delay finishes
            //NOTE: because this condition variable implements a channel.receive() (according to the documentation) to suspend operation here
            // a coRoutine cancel will break this just like it would with delay() or yield()
            coroutineConditionVariable.wait(
                CHAT_STREAM_WORKER_RUN_TIME_MS
            )
        }

    private suspend fun subscribeToChatStream() {

        //yield before the subscription in case this is running while application repository is running
        yield()

        //subscribe to the chat stream and start it if relevant
        ServiceLocator.chatStreamWorkerSubscribeToChatStreamObject(
            applicationContext,
            subscriberWrapper
        )
    }

    class ChatStreamWorkerSubscriber(
        private val applicationContext: Context,
        private val currentAccountOID: String,
        private val parentErrorConditionVariable: MakeshiftCoroutineConditionVariable,
        private val errorHandler: StoreErrorsInterface,
        private val chatStreamWorkerRepository: ChatStreamWorkerRepository = (applicationContext as LetsGoApplicationClass).chatStreamWorkerRepository,
        private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
    ) : ChatStreamSubscriberInterface {

        override suspend fun clientMessageToServerReturnValue(
            returnsClientMessageToServerReturnValue: ClientMessageToServerReturnValueDataHolder,
        ) {
            //NOTE: this message does not need to set messageRequiresNotification = false because the
            // messages that are sent from this device are never set to messageRequiresNotification = true
            //NOTE: technically this can happen if say a user sends a message then minimizes the app, however
            // I don't think I want to send notifications for that case
        }

        override suspend fun returnMessagesForChatRoom(
            returnsReturnMessagesForChatRoom: ReturnMessagesForChatRoomDataHolder,
        ) {

            Log.i(
                "chatStreamBoot",
                "returnMessagesForChatRoom() starting areNotificationsEnabled: ${
                    NotificationManagerCompat.from(GlobalValues.applicationContext)
                        .areNotificationsEnabled()
                }"
            )

            Log.i(
                "returnMessagesZAX",
                "chatRoomInitialization: ${returnsReturnMessagesForChatRoom.chatRoomInitialization}"
            )

            if (!returnsReturnMessagesForChatRoom.chatRoomInitialization) { //if new message and not requested for a chat room
                for (message in returnsReturnMessagesForChatRoom.messages) {

                    val sentByUserAccountOID = message.sentByAccountID

                    val messageType =
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                            message.messageType
                        )

                    Log.i("returnMessagesZAX", "messageType: $messageType")

                    var tempChatRoomInfo: NotificationsEnabledChatRoomInfo? = null
                    var firstTwoUserNamesInChatRoom = listOf<String>()

                    val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                    transactionWrapper.runTransaction {

                        chatStreamWorkerRepository.updateMessageToDoesNotRequireNotifications(
                            message.messageUUIDPrimaryKey
                        )

                        tempChatRoomInfo = chatStreamWorkerRepository.getNotificationsChatRoomInfo(
                            message.chatRoomId
                        )

                        firstTwoUserNamesInChatRoom =
                            chatStreamWorkerRepository.getFirstTwoUserNamesInChatRoom(message.chatRoomId)
                    }

                    if (!sentByUserAccountOID.isValidMongoDBOID()
                        || sentByUserAccountOID == currentAccountOID
                        || messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE
                        || !displayBlockedMessage(
                                sentByUserAccountOID,
                                message.messageType
                            )
                        || !NotificationManagerCompat.from(GlobalValues.applicationContext)
                            .areNotificationsEnabled()
                    ) { //If message was sent by current user OR oid is invalid OR is user activity message OR user is blocked OR notifications are disabled.
                        continue
                    }

                    val chatRoomInfo = tempChatRoomInfo!!

                    if (chatRoomInfo.notifications_enable == true) {

                        //Accepted Message Types
                        // TEXT_MESSAGE
                        // LOCATION_MESSAGE
                        // MIME_TYPE_MESSAGE
                        // INVITE_MESSAGE
                        // PICTURE_MESSAGE
                        // NEW_ADMIN_PROMOTED_MESSAGE
                        // CHAT_ROOM_NAME_UPDATED_MESSAGE
                        // CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
                        // NEW_PINNED_LOCATION_MESSAGE
                        // DELETED_MESSAGE
                        // EDITED_MESSAGE

                        NotificationsSharedFunctions.generateNotificationMessageText(
                            applicationContext,
                            messageType,
                            hasCompleteInfo = message.hasCompleteInfo,
                            rawMessageText = message.messageText
                        )?.let { messageText ->

                            val messageWithUUID: NotificationInfo.MessageWithUUID? =
                                when (chatRoomLayoutByMessageType(messageType)) {
                                    LayoutType.LAYOUT_MESSAGE -> {
                                        generateNotificationDisplayUser(
                                            messageText,
                                            message.messageUUIDPrimaryKey,
                                            sentByUserAccountOID,
                                            message.messageStoredOnServerTime,
                                        )
                                    }
                                    LayoutType.LAYOUT_SINGLE -> { //LayoutType.LAYOUT_SINGLE
                                        NotificationsSharedFunctions.generateNotificationSingleMessage(
                                            applicationContext,
                                            message.sentByAccountID,
                                            message.messageUUIDPrimaryKey,
                                            messageText,
                                            message.messageStoredOnServerTime
                                        )
                                    }
                                    LayoutType.LAYOUT_EMPTY -> { //DELETED_MESSAGE || EDITED_MESSAGE

                                        val deleteEdit =
                                            if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE)
                                                NotificationInfo.DeleteEditNotification.EDIT_NOTIFICATION
                                            else
                                                NotificationInfo.DeleteEditNotification.DELETE_NOTIFICATION

                                        val userName =
                                            chatStreamWorkerRepository.getUserNameForNotificationMessage(
                                                sentByUserAccountOID
                                            )
                                                ?: applicationContext.resources.getString(R.string.User)

                                        NotificationInfo.editDeleteMessage(
                                            applicationContext,
                                            chatRoomInfo.chatRoomID,
                                            userName,
                                            message.modifiedMessageUUID,
                                            message.messageText,
                                            deleteEdit
                                        )

                                        null
                                    }
                                }

                            messageWithUUID?.let { newMessage ->
                                NotificationInfo.addNotification(
                                    applicationContext,
                                    chatRoomInfo.chatRoomID,
                                    chatRoomInfo.chat_room_name ?: "",
                                    firstTwoUserNamesInChatRoom,
                                    newMessage
                                )
                            }
                        }
                    }
                }
            }
        }

        override suspend fun returnMessageWithMemberForChatRoom(
            returnsReturnMessageWithMemberForChatRoom: ReturnMessageWithMemberForChatRoomDataHolder,
        ) {
            val sentByUserAccountOID =
                returnsReturnMessageWithMemberForChatRoom.message.sentByAccountID

            val messageType =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    returnsReturnMessageWithMemberForChatRoom.message.messageType
                )

            var firstTwoUserNamesInChatRoom = listOf<String>()
            var tempChatRoomInfo: NotificationsEnabledChatRoomInfo? = null

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES,
                DatabasesToRunTransactionIn.OTHER_USERS
            )

            transactionWrapper.runTransaction {
                chatStreamWorkerRepository.updateMessageToDoesNotRequireNotifications(
                    returnsReturnMessageWithMemberForChatRoom.message.messageUUIDPrimaryKey
                )

                Log.i(
                    "StreamWorker",
                    "returnMessageWithMemberForChatRoom() messageType $messageType"
                )

                tempChatRoomInfo = chatStreamWorkerRepository.getNotificationsChatRoomInfo(
                    returnsReturnMessageWithMemberForChatRoom.message.chatRoomId
                )

                firstTwoUserNamesInChatRoom =
                    chatStreamWorkerRepository.getFirstTwoUserNamesInChatRoom(
                        returnsReturnMessageWithMemberForChatRoom.message.chatRoomId
                    )
            }

            if (!sentByUserAccountOID.isValidMongoDBOID()
                || sentByUserAccountOID == currentAccountOID
                || !displayBlockedMessage(
                    sentByUserAccountOID,
                    returnsReturnMessageWithMemberForChatRoom.message.messageType
                )
                || !NotificationManagerCompat.from(GlobalValues.applicationContext)
                    .areNotificationsEnabled()
            ) { //If message was sent by current user, oid is invalid or user is blocked or notifications are disabled.
                return
            }

            val chatRoomInfo = tempChatRoomInfo!!

            if (chatRoomInfo.notifications_enable == true) {

                //Accepts message types
                // DIFFERENT_USER_JOINED_MESSAGE
                // DIFFERENT_USER_LEFT_MESSAGE
                // USER_BANNED_MESSAGE
                // USER_KICKED_MESSAGE

                val userName =
                    when (messageType) {
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
                        -> {
                            if (returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.name != ""
                                && returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.name != "~"
                            ) {
                                returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.name
                            } else {
                                applicationContext.resources.getString(R.string.User)
                            }

                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                        -> {

                            val accountOID =
                                returnsReturnMessageWithMemberForChatRoom.message.accountOID

                            if (accountOID.isValidMongoDBOID() && accountOID != currentAccountOID) { //if valid oid and it is not this user getting kicked or banned

                                val removedUserName =
                                    returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.name

                                if (removedUserName == "" || removedUserName == "~") {
                                    applicationContext.resources.getString(R.string.User)
                                } else {
                                    removedUserName
                                }
                            } else {
                                return
                            }

                        }
                        //NOTE: it is set up this way instead of using else so that if
                        // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
                        // a warning will be thrown
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
                        null,
                        -> {
                            val errorString =
                                "Invalid message type returned inside returnMessageWithMemberForChatRoom\n" +
                                        "messageType: $messageType\n"

                            errorHandler.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorString,
                                applicationContext
                            )

                            return
                        }
                    }

                val messageText =
                    NotificationsSharedFunctions.generateNotificationMessageText(
                        applicationContext,
                        messageType,
                        userName = userName
                    )

                messageText?.let {
                    NotificationInfo.addNotification(
                        applicationContext,
                        chatRoomInfo.chatRoomID,
                        chatRoomInfo.chat_room_name ?: "",
                        firstTwoUserNamesInChatRoom,
                        NotificationsSharedFunctions.generateNotificationSingleMessage(
                            applicationContext,
                            returnsReturnMessageWithMemberForChatRoom.message.sentByAccountID,
                            returnsReturnMessageWithMemberForChatRoom.message.messageUUIDPrimaryKey,
                            messageText,
                            returnsReturnMessageWithMemberForChatRoom.message.messageStoredOnServerTime
                        )
                    )
                }
            }
        }

        override suspend fun returnAccountStateUpdated(
            accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder,
        ) {
            //NOTE: this message does not need to set messageRequiresNotification = false because the
            // messages that send these are never directly stored in the database
            //NOTE: no need to do anything here
        }

        override suspend fun returnJoinedLeftChatRoom(
            returnJoinedLeftChatRoomDataHolder: ReturnJoinedLeftChatRoomDataHolder,
        ) {

            //setup if new match made
            when (returnJoinedLeftChatRoomDataHolder.chatRoomUpdateMadeEnum) {
                ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED,
                ChatRoomUpdateMade.CHAT_ROOM_JOINED -> {}
                ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH -> {
                    val matchingMember =
                        returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomMembers.getFromList(
                            0
                        )

                    //NOTE: this message does not need to set messageRequiresNotification = false because the
                    // messages that send these are never directly stored in the database

                    if (!returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.matchingChatRoomOID.isValidMongoDBOID()
                        || returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.matchingChatRoomOID == currentAccountOID
                        || matchingMember == null
                        || !NotificationManagerCompat.from(GlobalValues.applicationContext)
                            .areNotificationsEnabled()
                    ) { //If message was sent by current user OR oid is invalid OR notifications are disabled.
                        return
                    }

                    val personMessageSentBy = extractUserInfo(
                        returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.matchingChatRoomOID,
                        ReturnForNotificationMessage(
                            matchingMember.otherUsersDataEntity.accountOID,
                            matchingMember.otherUsersDataEntity.thumbnailPath,
                            matchingMember.otherUsersDataEntity.thumbnailLastTimeUpdated,
                            applicationContext.getString(R.string.New_match)
                        ),
                        returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomId
                    )

                    val notificationMessage =
                        NotificationsSharedFunctions.generateNotificationForMatch(
                            applicationContext,
                            returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomId,
                            matchingMember.otherUsersDataEntity.accountOID,
                            returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.timeJoined,
                            matchingMember.otherUsersDataEntity.name,
                            personMessageSentBy
                        )

                    //because chatRoomName is "" and a list of names with 1 value are passed, the
                    // chat_messages_new_match_title will be the user name
                    NotificationInfo.addNotification(
                        applicationContext,
                        returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomId,
                        "",
                        listOf(applicationContext.getString(R.string.chat_messages_new_match_title)),
                        notificationMessage
                    )
                }
                ChatRoomUpdateMade.CHAT_ROOM_LEFT,
                ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED -> {
                    //if a notification exists for this chat room, remove it
                    NotificationInfo.removeNotification(
                        applicationContext,
                        returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomId
                    )
                }
            }

            Log.i(
                "StreamWorker",
                "returnJoinedLeftChatRoom() message: $returnJoinedLeftChatRoomDataHolder"
            )
        }

        override suspend fun returnChatRoomInfoUpdated(
            updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder,
        ) {

            //These will be handled inside returnMessagesForChatRoom()
            // so no need to do anything here

            /*var tempChatRoomInfo: NotificationsEnabledChatRoomInfo? = null

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES,
                DatabasesToRunTransactionIn.OTHER_USERS
            )

            transactionWrapper.runTransaction {
                chatStreamWorkerRepository.updateMessageToDoesNotRequireNotifications(
                    updateChatRoomInfoResultsDataHolder.message.messageUUIDPrimaryKey
                )

                Log.i(
                    "StreamWorker",
                    "returnChatRoomInfoUpdated() messageType ${TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(updateChatRoomInfoResultsDataHolder.message.messageType)}"
                )

                tempChatRoomInfo = chatStreamWorkerRepository.getNotificationsChatRoomInfo(
                    updateChatRoomInfoResultsDataHolder.message.chatRoomId
                )
            }

            val chatRoomInfo = tempChatRoomInfo!!

            if (!isValidMongoDBOID(updateChatRoomInfoResultsDataHolder.message.sentByAccountID)
                || updateChatRoomInfoResultsDataHolder.message.sentByAccountID == currentAccountOID
                || !NotificationManagerCompat.from(GlobalValues.applicationContext).areNotificationsEnabled()
            ) { //if message was sent by current user or oid is invalid
                return@withContext
            }

            if (chatRoomInfo.notifications_enable == true) {

                val messageText: String =
                    when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(updateChatRoomInfoResultsDataHolder.message.messageType)) {
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                            applicationContext.resources.getString(R.string.chat_message_chat_room_name_updated)
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                            applicationContext.resources.getString(R.string.chat_message_chat_room_password_updated)
                        }
                        else -> {
                            val errorString =
                                "Invalid message type returned inside returnChatRoomInfoUpdated\n" +
                                        "messageType: ${TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(updateChatRoomInfoResultsDataHolder.message.messageType)}\n"

                            errorHandler.storeError(
                                applicationContext,
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                Thread.currentThread().stackTrace[2].fileName,
                                printStackTraceForErrors()
                            )

                            return@withContext
                        }
                    }

                setNotificationEmptyUser(
                    messageText,
                    updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime,
                    chatRoomInfo,
                )

                Log.i(
                    "StreamWorker",
                    "returnMessagesForChatRoom() message: ${TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(updateChatRoomInfoResultsDataHolder.message.messageType)}"
                )

            }*/
        }

        override suspend fun returnKickedBannedFromChatRoom(
            returnKickedBannedFromChatRoomDataHolder: ReturnKickedBannedFromChatRoomDataHolder,
        ) {
            //NOTE: this message does not need to set messageRequiresNotification = false because the
            // messages that send these are never directly stored in the database

            //if a notification exists for this chat room, remove it
            NotificationInfo.removeNotification(
                applicationContext,
                returnKickedBannedFromChatRoomDataHolder.chatRoomId
            )
        }

        override suspend fun receivedMessageUpdateRequestResponse(
            returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder,
        ) {
            //NOTE: this message does not need to set messageRequiresNotification = false because this
            // is an update, not a new message.
            //NOTE: no need to do anything here
            //NOTE: New messages are sent as AmountOfMessage::ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE from the server,
            // however because text length is limited naturally by the message type notifications, it can be displayed
            // correctly without requesting updates from the server.
        }

        override suspend fun chatStreamInitialDownloadsCompleted() {
            //NOTE: no need to do anything here
        }

        override suspend fun gRPCErrorOccurred(
            error: GrpcFunctionErrorStatusEnum,
        ) {
            when (error) {
                //chat stream is restarting itself using restartChatStreamHandler
                GrpcFunctionErrorStatusEnum.DO_NOTHING,
                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
                GrpcFunctionErrorStatusEnum.SERVER_DOWN, //means ALL servers are down
                -> {
                }
                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID ->{
                    (applicationContext as LetsGoApplicationClass)
                        .loginFunctions
                        .beginLoginToServerWhenReceivedInvalidToken("")
                }
                else -> {
                    //NOTE: error was already stored here
                    parentErrorConditionVariable.notifyOne()
                }
            }
        }

        //Leave passedRequiredUserInfo null if otherUserInfo is not present with message
        private suspend fun generateNotificationDisplayUser(
            messageText: String,
            messageUUIDPrimaryKey: String,
            sentByUserAccountOID: String,
            passedStoredOnServerTimestamp: Long?
        ): NotificationInfo.MessageWithUUID = withContext(ioDispatcher) {

            val storedOnServerTime =
                NotificationsSharedFunctions.extractNotificationTime(passedStoredOnServerTimestamp)

            val personMessageSentBy =
                extractUserInfo(sentByUserAccountOID, null)

            val notificationStyleMessage = NotificationCompat.MessagingStyle.Message(
                messageText, storedOnServerTime, personMessageSentBy
            )

            NotificationInfo.MessageWithUUID(
                messageUUIDPrimaryKey,
                sentByUserAccountOID,
                notificationStyleMessage
            )
        }

        private suspend fun extractUserInfo(
            sentByUserAccountOID: String,
            passedRequiredUserInfo: ReturnForNotificationMessage? = null,
            setKey: String = sentByUserAccountOID
        ): Person = withContext(ioDispatcher) {

            val requiredUserInfo =
                passedRequiredUserInfo
                    ?: chatStreamWorkerRepository.getUserInfoForNotificationMessage(
                        sentByUserAccountOID
                    )

            return@withContext NotificationsSharedFunctions.buildPersonForNotificationMessage(
                applicationContext,
                sentByUserAccountOID,
                requiredUserInfo,
                setKey
            )
        }
    }

    companion object {
        //tag assigned to the chatStreamWorkers when they are started
        const val CHAT_STREAM_WORKER_TAG = "chat_stream_worker_tag"

        //channel ID for notifications
        const val CHAT_MESSAGE_CHANNEL_ID = "chat_message_channel_id"

        //group ID for notifications
        const val CHAT_MESSAGE_GROUP_ID = "site.letsgoapp.letsgo.chat_message_group_id"

        //used as part of matching message uuid (it isn't a real uuid)
        const val MATCH_TYPE_MESSAGE_UUID = "match_type_message"

        //this variable is used to make sure only 1 instance of this function can be running at a time
        val chatStreamWorkerInstanceRunning = AtomicBoolean(false)

        //this will be set to false if there is no reason for the chat stream worker to continue
        val continueChatStreamWorker = AtomicBoolean(true)

        //The total time the chat stream worker will run in ms.
        //Must be less than 10 minutes, otherwise the system will terminate the worker (Android feature). However,
        // the less the worker needs to restart, the less logins it will need to do.
        const val CHAT_STREAM_WORKER_RUN_TIME_MS: Long = (9L * 60L + 30L) * 1000L

        //protects against some errors where the chatStreamWorker can continue running when
        // the application starts, or where it could 2 instances of itself at the same time
        //This must NOT be a coRoutine lock because it is locked inside of the finally block for
        // the ChatStreamWorker doWork() coRoutine and so the coRoutine Mutex() could suspend
        // and cause the coRoutine to be cancelled before the new ChatStream is started.
        val chatStreamWorkerMutex = ReentrantLock()
    }
}