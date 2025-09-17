package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

import account_state.AccountState
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.*
import chat_message_to_client.ChatMessageToClientMessage
import grpc_chat_commands.ChatRoomCommands
import grpc_stream_chat.ChatMessageStream
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import site.letsgoapp.letsgo.BuildConfig
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.*
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.convertTypeOfChatMessageToNewChatRoom
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.INVALID_LOGIN_TOKEN
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.ApplicationRepository
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs.convertTypeOfChatMessageToErrorString
import site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs.runClientMessageToServer
import site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs.update_times_for_sent_messages
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamObjectWorkers.RefreshChatStreamObjectWorker
import site.letsgoapp.letsgo.workers.chatStreamObjectWorkers.RunChatStreamObjectAfterDelayWorker
import status_enum.StatusEnum
import type_of_chat_message.TypeOfChatMessageOuterClass
import type_of_chat_message.TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase
import user_account_type.UserAccountTypeOuterClass.UserAccountType
import java.io.File
import java.util.concurrent.TimeUnit

class ChatStreamObject(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val chatRoomsDataSource: ChatRoomsIntermediateInterface,
    private val messagesDataSource: MessagesDaoIntermediateInterface,
    private val otherUsersDataSource: OtherUsersDaoIntermediateInterface,
    private val mimeTypeDataSource: MimeTypeDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    //response from sendMessage()
    private suspend fun clientMessageToServerReturnValue(
        returnsClientMessageToServerReturnValue: ClientMessageToServerReturnValueDataHolder
    ) {
        chatStreamWorkerSubscriber?.clientMessageToServerReturnValue(
            returnsClientMessageToServerReturnValue
        )
        applicationRepository?.clientMessageToServerReturnValue(
            returnsClientMessageToServerReturnValue
        )
    }

    //used with message types
    // CHAT_TEXT_MESSAGE, LOCATION_MESSAGE, MIME_TYPE_MESSAGE, INVITED_TO_CHAT_ROOM, PICTURE_MESSAGE,
    // MESSAGE_DELETED, MESSAGE_EDITED, USER_ACTIVITY_DETECTED
    // CHAT_ROOM_NAME_UPDATED_MESSAGE, CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
    // NEW_ADMIN_PROMOTED (also sends back a returnAccountStateUpdated())
    private suspend fun returnMessagesForChatRoom(
        returnsReturnMessagesForChatRoom: ReturnMessagesForChatRoomDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnMessagesForChatRoom(returnsReturnMessagesForChatRoom)
            applicationRepository?.returnMessagesForChatRoom(returnsReturnMessagesForChatRoom)
        } else {
            subscriber.returnMessagesForChatRoom(returnsReturnMessagesForChatRoom)
        }
    }

    //used with message types
    // DIFFERENT_USER_JOINED_CHAT_ROOM, DIFFERENT_USER_LEFT_CHAT_ROOM
    // handles message pertaining to other users of type DIFFERENT_USER_KICKED_FROM_CHAT_ROOM, DIFFERENT_USER_BANNED_FROM_CHAT_ROOM
    private suspend fun returnMessageWithMemberForChatRoom(
        returnsReturnMessageWithMemberForChatRoom: ReturnMessageWithMemberForChatRoomDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnMessageWithMemberForChatRoom(
                returnsReturnMessageWithMemberForChatRoom
            )
            applicationRepository?.returnMessageWithMemberForChatRoom(
                returnsReturnMessageWithMemberForChatRoom
            )
        } else {
            subscriber.returnMessageWithMemberForChatRoom(
                returnsReturnMessageWithMemberForChatRoom
            )
        }
    }

    //used with message types
    // NEW_ADMIN_PROMOTED (also sends back a returnMessagesForChatRoom())
    // DIFFERENT_USER_LEFT_CHAT_ROOM if the user leaving was admin
    private suspend fun returnAccountStateUpdated(
        accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnAccountStateUpdated(accountStateUpdatedDataHolder)
            applicationRepository?.returnAccountStateUpdated(accountStateUpdatedDataHolder)
        } else {
            subscriber.returnAccountStateUpdated(accountStateUpdatedDataHolder)
        }
    }

    //used with message types
    // THIS_USER_JOINED_CHAT_ROOM_FINISHED, THIS_USER_LEFT_CHAT_ROOM
    private suspend fun returnJoinedLeftChatRoom(
        returnJoinedLeftChatRoomDataHolder: ReturnJoinedLeftChatRoomDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder)
            applicationRepository?.returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder)
        } else {
            subscriber.returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder)
        }
    }

    //used with message types
    // CHAT_ROOM_NAME_UPDATED, CHAT_ROOM_PASSWORD_UPDATED, NEW_PINNED_LOCATION_MESSAGE
    private suspend fun returnChatRoomInfoUpdated(
        updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnChatRoomInfoUpdated(
                updateChatRoomInfoResultsDataHolder
            )
            applicationRepository?.returnChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder)
        } else {
            subscriber.returnChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder)
        }
    }

    //used with message types
    // handles message pertaining to the current user of type USER_KICKED_FROM_CHAT_ROOM, USER_BANNED_FROM_CHAT_ROOM
    private suspend fun returnKickedBannedFromChatRoom(
        returnKickedBannedFromChatRoomDataHolder: ReturnKickedBannedFromChatRoomDataHolder,
        subscriber: ChatStreamSubscriberWrapper?
    ) {
        if (subscriber == null) {
            chatStreamWorkerSubscriber?.returnKickedBannedFromChatRoom(
                returnKickedBannedFromChatRoomDataHolder
            )
            applicationRepository?.returnKickedBannedFromChatRoom(
                returnKickedBannedFromChatRoomDataHolder
            )
        } else {
            subscriber.returnKickedBannedFromChatRoom(
                returnKickedBannedFromChatRoomDataHolder
            )
        }
    }

    //used for a response when requestFullMessage is used by application repository
    private suspend fun receivedMessageUpdateRequestResponse(
        returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder
    ) {
        chatStreamWorkerSubscriber?.receivedMessageUpdateRequestResponse(
            returnMessageUpdateRequestResponseDataHolder
        )
        applicationRepository?.receivedMessageUpdateRequestResponse(
            returnMessageUpdateRequestResponseDataHolder
        )
    }

    //used when the initial info from the chat stream has been downloaded
    private suspend fun chatStreamInitialDownloadsCompleted() {
        chatStreamWorkerSubscriber?.chatStreamInitialDownloadsCompleted()
        applicationRepository?.chatStreamInitialDownloadsCompleted()
    }

    //called by ChatRoomObject when an error occurs that it cannot handle, such as the login token being invalid or an unknown error happening
    // with the stream
    //NOTE: error was already stored
    private suspend fun gRPCErrorOccurred(error: GrpcFunctionErrorStatusEnum) {

        Log.i(
            "gRPCErrorOccurred",
            "gRPCErrorOccurred() inside ChatStreamObject error: $error\n${
                Log.getStackTraceString(Exception())
            }"
        )

        var storedErrorSet = false
        requestResponseErrorMutex.singleMutexWithLock(errorMutexKey) {
            if (chatStreamWorkerSubscriber == null && applicationRepository == null) {
                storedError = error
                storedErrorSet = true
            }
        }

        //NOTE: DON'T ALLOW THE BELOW BLOCK. This is because the cancelChatStream() locks
        // both the requestMutexKey & responseMutexKey locks. Because gRPCErrorOccurred() can be
        // called from anywhere this means that the locks could end up locked out of order and
        // cause deadlock to happen (IE responseMutexKey is already locked when this is called,
        // however it is possible more locks could be added in the future complicating things).
        //if(error==GrpcFunctionErrorStatusEnum.LOG_USER_OUT
        //    || error==GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO) {
        //    cancelChatStream()
        //}

        //if the stored error was set, then there could be a gap where a subscriber
        // is initialized after the lock, this bool will prevent that from happening
        if (storedErrorSet)
            return

        chatStreamWorkerSubscriber?.gRPCErrorOccurred(error)
        applicationRepository?.gRPCErrorOccurred(error)
    }

    enum class ChatStreamObserverState {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED
    }

    enum class SubscriberType {
        APPLICATION_VIEW_MODEL_SUBSCRIBER,
        CHAT_STREAM_WORKER_SUBSCRIBER
    }

    private var chatStreamResponseObserver: ChatMessageStreamObserver? =
        null
    private var chatStreamRequestObserver: StreamObserver<ChatMessageStream.ChatToServerRequest>? =
        null

    data class RequestedMessageInfo(
        val chatRoomId: String,
        val amountOfMessageRequested: TypeOfChatMessageOuterClass.AmountOfMessage, //amount of message requested
        val messageUUID: String,
        var requested: Boolean
    )

    //number of times stream has been refreshed since server stream start, when it hits a point the chat stream
    // will be restarted instead of refreshed
    //protected by responseMutexKey Mutex
    private var numberTimesStreamRefreshed = 0

    //used to store the server the chat room is connected to, if the load balancing happens between chat stream
    // refreshing itself, it will make sure to start a new chat stream with the new channel
    //protected by responseMutexKey Mutex
    private var serverAddressPort = ""

    //NOTE: This requires suspend functions and so simply overloading the functions while inheriting
    // from an ArrayList<T> is not enough
    /*private class ThreadSafeList {
        private val sharedMutex = CoroutineSharedMutex()
        private val list = mutableListOf<RequestedMessageInfo>()
        private val preventRepeatsSet = mutableSetOf<String>()

        private fun getKeyFromValue(key: RequestedMessageInfo): String {
            return key.messageUUID
        }

        //Returns true if successfully added, false otherwise.
        suspend fun add(value: RequestedMessageInfo): Boolean {
            return sharedMutex.withPrimaryLock {
                return@withPrimaryLock if (!preventRepeatsSet.contains(getKeyFromValue(value))) {
                    list.add(value)
                } else {
                    false
                }
            }
        }

        suspend fun clear() {
            sharedMutex.withPrimaryLock {
                preventRepeatsSet.clear()
                list.clear()
            }
        }

        suspend fun isNotEmpty(): Boolean {
            return sharedMutex.withSharedLock {
                return@withSharedLock list.isNotEmpty()
            }
        }

        suspend fun getSize(): Int {
            return sharedMutex.withSharedLock {
                return@withSharedLock list.size
            }
        }

        operator fun get(index: Int): RequestedMessageInfo {
            return runBlocking {
                return@runBlocking sharedMutex.withSharedLock {
                    return@withSharedLock list[index]
                }
            }
        }

        operator fun set(index: Int, value: RequestedMessageInfo) {
            runBlocking {
                sharedMutex.withPrimaryLock {
                    if (getKeyFromValue(list[index]) != getKeyFromValue(value)) {
                        preventRepeatsSet.remove(getKeyFromValue(list[index]))
                        preventRepeatsSet.add(getKeyFromValue(value))
                    }
                    list[index] = value
                }
            }
        }

        suspend fun iterateAcrossElements(block: suspend (element: RequestedMessageInfo) -> Unit) {
            sharedMutex.withPrimaryLock {
                for (element in list) {
                    block(element)
                }
            }
        }

        suspend fun removeIf(block: suspend (element: RequestedMessageInfo) -> Boolean) {
            sharedMutex.withPrimaryLock {
                val removeElements = mutableListOf<RequestedMessageInfo>()
                for (element in list) {
                    if (block(element)) {
                        preventRepeatsSet.remove(getKeyFromValue(element))
                        removeElements.add(element)
                    }
                }
                list.removeAll(removeElements)
            }
        }

        suspend fun setAllElements(block: suspend (element: RequestedMessageInfo) -> RequestedMessageInfo) {
            sharedMutex.withPrimaryLock {
                for (i in list.indices) {
                    val newElement = block(list[i])
                    if (getKeyFromValue(list[i]) != getKeyFromValue(newElement)) {
                        preventRepeatsSet.remove(getKeyFromValue(list[i]))
                        preventRepeatsSet.add(getKeyFromValue(newElement))
                    }
                    list[i] = newElement
                }
            }
        }

        //returns first element index which causes predicate to be true, will return -1 if predicate is false
        // for each element
        suspend fun findIndex(predicate: suspend (element: RequestedMessageInfo) -> Boolean): Int {
            return sharedMutex.withSharedLock {
                for (i in list.indices) {
                    if (predicate(list[i])) {
                        return@withSharedLock i
                    }
                }
                return@withSharedLock -1
            }
        }

        suspend fun setAllMessagesInQueueToNotRequested() {
            sharedMutex.withPrimaryLock {
                for (element in list) {
                    element.requested = false
                }
            }
        }
    }*/

    //this boolean should always be locked inside the request lock so no need to make it atomic
    private var currentlyRequestingMessageUpdates = false

    data class TemporaryChatRoom(
        val chatRoomId: String,
        val receivedMessages: MutableList<ChatMessageToClientMessage.ChatMessageToClient> = mutableListOf()
    )

    //Whenever joinChatRoom is used this will be set temporarily to a chat room in order to avoid
    // messages being sent back BEFORE the joinChatRoom primer is sent back.
    @Volatile
    private var tempChatRoom: TemporaryChatRoom? = null

    //holds values for RequestFullMessageInfo
    //expected to be surrounded by messageQueueMutexKey lock when running operations on this queue
    /** For a little more info [updating_messages_notes]. **/
    private val messageQueue =
        ListWithUniqueStorage<String, RequestedMessageInfo> { it.messageUUID }

    //state of the chat stream bound to chatStreamObserver, lock chatStreamObjectMutex around set()
    //@Volatile
    //private var primaryChatStreamObserverState = ChatStreamObserverState.NOT_CONNECTED

    //the accountOID the current chat stream is running for
    private var currentChatStreamAccountOID = ""
    private var primaryChatStreamObserverState = ChatStreamObserverState.NOT_CONNECTED

    @Volatile
    private var chatStreamWorkerSubscriber: ChatStreamSubscriberWrapper? = null

    @Volatile
    private var applicationRepository: ChatStreamSubscriberWrapper? = null

    //NOTE: removeCallbacksAndMessages does not seem to be working with a token, so removing these using null
    // this means all callbacks will be removed
    //NOTE: this is cleared in onCancel (when a user logs out or deletes the account) other than that, it runs forever
    //NOTE: the function that is posted to this handler starts a coroutine so it is all right if the main looper (the main thread) is the one starting the function
//    private val restartChatStreamHandler = Handler(Looper.getMainLooper())
//    private val chatStreamToken =
//        "Token" //NOTE: this is a string because for some reason was not working as an int

    @Volatile
    private var chatStreamCancelled = true

    //set to null under normal cases, however if an error occurs while no subscriber is attached, this
    // will be set to a value and returned to the next subscriber, it will also be cleared if the chat stream
    // is successfully started
    private var storedError: GrpcFunctionErrorStatusEnum? = null

    //these represent the reentrant mutex which will be used for surrounding the request and response observers
    // respectively; they will also be used when starting the chat stream, they will not be used
    // for sendMessages
    private val chatStreamRequestMutexKey = "REQUEST_MUTEX_KEY"
    private val messageQueueMutexKey = "MESSAGE_QUEUE_MUTEX_KEY"
    private val chatStreamResponseMutexKey = "RESPONSE_MUTEX_KEY"
    private val errorMutexKey = "ERROR_MUTEX_KEY"

    /** Order is very important here, the request lock should ALWAYS be locked inside the response lock because
     * when INITIAL_CONNECTION_PRIMER_RESPONSE is received by the stream, it is done this way.  If they are locked
     * in reverse order it can cause deadlock.
     * Error should be locked last. **/
    private val requestResponseErrorMutex =
        ReentrantLocksOrderGuarantor(
            chatStreamResponseMutexKey,
            messageQueueMutexKey,
            chatStreamRequestMutexKey,
            errorMutexKey
        )

    //wraps the sendMessage function in order to guarantee order (Mutex() is fair),
    // not sure the reEntrant lock or the guarantor are fair and so not going to
    // add this to the ReentrantLocksOrderGuarantor (also shouldn't need a reEntrant lock for it)
    private var sendMessageMutex = Mutex()

    //NOTE: the chatStreamObjectMutex here is important because primaryChatStreamObserverState is checked

    //Will do several things
    // 1) will subscribe if the newSubscriber is NOT already subscribed
    // 2) if no subscribers were connected to catch an error will retrieve the error and send it to the new subscriber
    // 3) will start the chat stream if necessary
    suspend fun subscribe(
        newSubscriber: ChatStreamSubscriberWrapper?,
        subscriberType: SubscriberType,
    ) {
        withContext(ioDispatcher) {
            requestResponseErrorMutex.specificMutexWithLocks(
                listOf(
                    chatStreamRequestMutexKey,
                    chatStreamResponseMutexKey
                )
            ) {

                Log.i(
                    "startBiDiTest",
                    "subscribe() start"
                )

                var storedErrorCopy: GrpcFunctionErrorStatusEnum? = null

                requestResponseErrorMutex.singleMutexWithLock(errorMutexKey) {

                    if (newSubscriber?.instanceID != applicationRepository?.instanceID
                        && newSubscriber?.instanceID != chatStreamWorkerSubscriber?.instanceID
                    ) {

                        if (subscriberType == SubscriberType.APPLICATION_VIEW_MODEL_SUBSCRIBER) {
                            applicationRepository = newSubscriber
                        } else { //CHAT_STREAM_WORKER_SUBSCRIBER

                            val messagesRequiringNotifications =
                                messagesDataSource.retrieveMessagesRequiresNotifications()

                            for (message in messagesRequiringNotifications) {
                                notifySubscriber(
                                    message,
                                    passedSubscriber = newSubscriber
                                )
                            }

                            chatStreamWorkerSubscriber = newSubscriber
                        }
                    }

                    storedError?.let {
                        storedErrorCopy = it
                        storedError = null
                    }
                }

                //if there was a stored error and the chat stream is not already running, then the
                // object subscribing to this will need to handle it
                when (storedErrorCopy) {
                    //action required by a subscriber
                    GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                    GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED,
                    GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED,
                    GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE,
                    GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
                    GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY,
                    GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION,
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID, //the subscriber is responsible for re-starting the chat stream
                    -> {
                        gRPCErrorOccurred(storedErrorCopy!!)
                    }
                    //should not happen but still OK
                    GrpcFunctionErrorStatusEnum.DO_NOTHING,
                        //could happen
                    GrpcFunctionErrorStatusEnum.NO_ERRORS,
                        //chat stream is restarting itself using restartChatStreamHandler
                    GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
                    GrpcFunctionErrorStatusEnum.SERVER_DOWN, //means ALL servers are down
                    null, //means no errors found
                    -> {
                        startChatStream()
                    }
                }
            }
        }
    }

    //If chat stream is NOT currently running or if this is being started for a new account OID
    // the chat stream will be restarted.
    private suspend fun startChatStream() {
        withContext(ioDispatcher) {

            Log.i("startBiDiTest", "startChatStream() about to lock mutex")
            requestResponseErrorMutex.specificMutexWithLocks(
                listOf(
                    chatStreamRequestMutexKey,
                    chatStreamResponseMutexKey
                )
            ) {

                Log.i("startBiDiTest", "startChatStream() mutex locked")

                if (currentChatStreamAccountOID != LoginFunctions.currentAccountOID
                    || primaryChatStreamObserverState == ChatStreamObserverState.NOT_CONNECTED
                ) { //if new account OID is being used to start chat stream OR chat stream is not connected, start stream

                    if (currentChatStreamAccountOID != LoginFunctions.currentAccountOID) {
                        cancelPreviouslyRunningChatStream()
                        currentChatStreamAccountOID = LoginFunctions.currentAccountOID
                    }

                    chatStreamCancelled = false

                    //This must be called here because in subscribe() CONNECTION_ERROR and SERVER_DOWN will
                    // leave callbacks in the handler to retry
                    //restartChatStreamHandler.removeCallbacksAndMessages(chatStreamToken)
                    cancelAllChatStreamObjectsWork(applicationContext)

                    Log.i(
                        "startBiDiTestStart",
                        "startChatStream from startChatStreamForCurrentAccountOID()"
                    )

                    startChatMessageStream()
                } else {
                    chatStreamInitialDownloadsCompleted()
                }
            }
        }
    }

    //if the calling object is subscribed, unsubscribes it (setting subscriber to null)
    suspend fun unSubscribe(
        passedSubscriber: ChatStreamSubscriberWrapper?,
        subscriberType: SubscriberType,
    ) {
        requestResponseErrorMutex.specificMutexWithLocks(
            listOf(
                chatStreamRequestMutexKey,
                chatStreamResponseMutexKey
            )
        ) {
            if (subscriberType == SubscriberType.APPLICATION_VIEW_MODEL_SUBSCRIBER) {
                if (applicationRepository == passedSubscriber) {
                    applicationRepository = null
                }
            } else { //CHAT_STREAM_WORKER_SUBSCRIBER
                if (chatStreamWorkerSubscriber == passedSubscriber) {
                    chatStreamWorkerSubscriber = null
                }
            }
        }
    }

    fun beginUnSubscribeJob(
        passedSubscriber: ChatStreamSubscriberWrapper?,
        subscriberType: SubscriberType,
    ): Job {
        return CoroutineScope(ioDispatcher).launch {
            unSubscribe(
                passedSubscriber,
                subscriberType,
            )
        }
    }

    fun sendNetworkUnavailableToChatStream() {
        CoroutineScope(ioDispatcher).launch {
            Log.i(
                "loadBalancingVal",
                "inside sendNetworkUnavailableToChatStream() ${chatStreamResponseObserver == null}"
            )
            chatStreamResponseObserver?.onError(IllegalArgumentException(GlobalValues.NETWORK_UNAVAILABLE))
        }
    }

    fun setupTemporaryChatRoom(chatRoomId: String) {
        tempChatRoom = TemporaryChatRoom(chatRoomId)
    }

    fun removeTemporaryChatRoom(chatRoomId: String) {
        if (tempChatRoom?.chatRoomId == chatRoomId) {
            removeTempChatRoomNoCheck()
        }
    }

    suspend fun cancelChatStream() {
        withContext(ioDispatcher) {
            requestResponseErrorMutex.specificMutexWithLocks(
                listOf(
                    chatStreamRequestMutexKey,
                    chatStreamResponseMutexKey
                )
            ) {

                //restartChatStreamHandler.removeCallbacksAndMessages(chatStreamToken)
                cancelAllChatStreamObjectsWork(applicationContext)
                chatStreamCancelled = true
                currentChatStreamAccountOID = ""
                cancelPreviouslyRunningChatStream()
            }
        }
    }

    //clear the message queue (only called when logging out after chat stream has been cancelled and updates are no longer required)
    suspend fun clearMessageQueue() {
        requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
            messageQueue.clear()
        }
    }

    //clear tempChatRoom variable
    fun removeTempChatRoomNoCheck() {
        tempChatRoom = null
    }

    //This function is used for canceling the chat stream from the ChatStreamObject itself, it is used when this will be immediately
    // restarted and so chatStreamCancelled should not be true
    private suspend fun cancelPreviouslyRunningChatStream() {
        withContext(ioDispatcher) {
            requestResponseErrorMutex.specificMutexWithLocks(
                listOf(
                    chatStreamRequestMutexKey,
                    chatStreamResponseMutexKey
                )
            ) {
                chatStreamRequestObserver?.onCompleted()
                chatStreamResponseObserver?.onCompleted()

                chatStreamRequestObserver = null
                chatStreamResponseObserver = null
            }
        }
    }

    //NOTE: the chatStreamObjectMutex here is important because primaryChatStreamObserverState is updated inside
    private suspend fun startChatMessageStream() {
        withContext(ioDispatcher) {
            Log.i("startBiDiTest", "starting startChatMessageStream()")

            requestResponseErrorMutex.specificMutexWithLocks(
                listOf(
                    chatStreamRequestMutexKey,
                    chatStreamResponseMutexKey
                )
            ) {

                if (chatStreamCancelled) {
                    return@specificMutexWithLocks
                }

                val loginToken = loginTokenIsValid()

                if (loginToken != INVALID_LOGIN_TOKEN) { //if login token is found

                    val recentMessages =
                        messagesDataSource.getMessagesWithinRecentTimeForEachChatRoomIncludingBlocking(
                            getCurrentTimestampInMillis() - GlobalValues.server_imported_values.timeToRequestPreviousMessages
                        )

                    val messagesMap = mutableMapOf<String, MutableList<MostRecentMessageData>>()

                    val chatRoomInfo =
                        chatRoomsDataSource.getAllChatRoomIdsTimeLastUpdatedAndLastObservedTime()

                    //approximate the size of the string with the chat rooms added
                    var approximateChatRoomsInBytes = chatRoomInfo.size * (
                            GlobalValues.server_imported_values.maximumNumberChatRoomIdChars //should be 8 the number of chars in 99.9% of chat room ids
                                    + 1 //represents the digit for the number of recent messages to pass back
                                    + 2 * GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter.length //represents the 2 delimiters required for the info to be passed back (assuming no chat rooms)
                            )

                    for (message in recentMessages) {
                        approximateChatRoomsInBytes += 36 + GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter.length

                        if (approximateChatRoomsInBytes >= (GlobalValues.server_imported_values.maximumMetaDataSizeToSendFromClient - 512)) {
                            break
                        } else if (message.messageUUIDPrimaryKey.isValidUUIDKey()) {
                            messagesMap.getOrPut(message.chat_room_id) { mutableListOf() }
                                .add(message)
                        }
                    }

                    val metadata = Metadata()

                    metadata.put(
                        GlobalValues.chatStreamLoginMetadataCurrentAccountIdKey,
                        LoginFunctions.currentAccountOID
                    )

                    metadata.put(
                        GlobalValues.chatStreamLoginMetadataLoggedInTokenKey,
                        loginToken
                    )

                    metadata.put(
                        GlobalValues.chatStreamLoginMetadataLetsGoVersionKey,
                        GlobalValues.Lets_GO_Version_Number.toString()
                    )

                    metadata.put(
                        GlobalValues.chatStreamLoginMetadataInstallationIdKey,
                        GlobalValues.installationId
                    )

                    var parsedChatRoomValues = ""

                    for (info in chatRoomInfo) {
                        parsedChatRoomValues += info.chatRoomID
                        parsedChatRoomValues += GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter

                        parsedChatRoomValues += info.last_time_updated.toString()
                        parsedChatRoomValues += GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter

                        parsedChatRoomValues += info.last_observed_time.toString()
                        parsedChatRoomValues += GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter

                        val messagesList = messagesMap.getOrPut(info.chatRoomID) { mutableListOf() }

                        parsedChatRoomValues += messagesList.size.toString()
                        parsedChatRoomValues += GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter

                        for (message in messagesList) {
                            parsedChatRoomValues += message.messageUUIDPrimaryKey
                            parsedChatRoomValues += GlobalValues.chatStreamLoginMetaDataChatRoomValuesDelimiter
                        }
                    }

                    if (parsedChatRoomValues != "") {
                        metadata.put(
                            GlobalValues.chatStreamLoginMetadataChatRoomValuesKey,
                            parsedChatRoomValues
                        )
                    }

                    cancelPreviouslyRunningChatStream()
                    primaryChatStreamObserverState = ChatStreamObserverState.CONNECTING

                    chatStreamResponseObserver = ChatMessageStreamObserver()

                    serverAddressPort = GlobalValues.getCurrentManagedChannelAddressPort()

                    val returnVal = clientsIntermediate.startChatStream(
                        metadata,
                        chatStreamResponseObserver!!
                    )

                    chatStreamRequestObserver = returnVal.response

                    if (returnVal.androidErrorEnum != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) { //if an error occurred
                        chatResponseOnErrorHandler(
                            returnVal.androidErrorEnum,
                            returnVal.errorMessage,
                            chatStreamResponseObserver
                        )
                    } else {
                        //this could have changed by now, however it is just a slight efficiency thing anyway
                        serverAddressPort = GlobalValues.getCurrentManagedChannelAddressPort()
                        //clear any remaining errors if present
                        requestResponseErrorMutex.singleMutexWithLock(errorMutexKey) {
                            storedError = null
                        }

                        //This will clear any connection error messages if they exist inside the activity
                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.NO_ERRORS)
                    }

                } else { //if login token is invalid

                    //the login functions will send a signal back to the AppActivity or the ChatStreamWorker
                    // and this object will be restarted when a login is complete
                    chatStreamResponseObserver = null
                    primaryChatStreamObserverState = ChatStreamObserverState.NOT_CONNECTED
                    cancelChatStream()

                    gRPCErrorOccurred(
                        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
                    )
                }
            }
        }
    }

    private suspend fun removeAndReturnMessageByUUID(messageUUID: String): RequestedMessageInfo? {
        var extractedMessageInfo: RequestedMessageInfo? = null
        requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
            var indexToRemove = -1
            for (i in messageQueue.indices) {
                if (messageQueue[i].messageUUID == messageUUID) {
                    extractedMessageInfo = messageQueue[i]
                    indexToRemove = i
                    break
                }
            }
            if (indexToRemove != -1)
                messageQueue.removeAt(indexToRemove)
        }
        return extractedMessageInfo
    }

    private suspend fun requestMessagesIfNoneRequested() {
        requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
            val messageBeingRequested =
                messageQueue.find { messageInfo ->
                    messageInfo.requested
                }

            Log.i("startBiDiTest", "messageBeingRequested: $messageBeingRequested")
            if (messageBeingRequested == null) { //no messages currently being requested
                if (messageQueue.size > 0) { //new messages to be requested

                    Log.i(
                        "startBiDiTest",
                        "finished batch of updates, still ${messageQueue.size} messages to request updates for"
                    )

                    val chatMessageToServer = buildChatMessageToServer()
                    requestResponseErrorMutex.singleMutexWithLock(
                        chatStreamRequestMutexKey
                    ) {
                        chatStreamRequestObserver?.onNext(chatMessageToServer)
                    }
                } else { //no new messages in queue at all
                    currentlyRequestingMessageUpdates = false
                }
            }
        }
    }

    private suspend fun setAllMessagesInQueueToNotRequested() {
        requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
            for (messageInfo in messageQueue) {
                messageInfo.requested = false
            }
        }
    }

    //NOTE: This function will not check and see if messages are already requested. It will simply build
    // the most recent messages from the same chat room and set them to requested. It also will not check if
    // the messageQueue is empty. It will simply send the messages to the server it extracts.
    private suspend fun buildChatMessageToServer(): ChatMessageStream.ChatToServerRequest {
        return requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
            val messageUUIDAndAmountList =
                mutableListOf<ChatMessageStream.MessageUUIDWithAmountOfMessage>()
            var chatRoomId = ""

            for (messageInfo in messageQueue) {
                if (chatRoomId.isEmpty()) {
                    chatRoomId = messageInfo.chatRoomId
                }

                if (chatRoomId == messageInfo.chatRoomId) {
                    messageUUIDAndAmountList.add(
                        ChatMessageStream.MessageUUIDWithAmountOfMessage.newBuilder()
                            .setAmountOfMessagesToRequest(messageInfo.amountOfMessageRequested)
                            .setMessageUuid(messageInfo.messageUUID)
                            .build()
                    )

                    messageInfo.requested = true
                }

                if (messageUUIDAndAmountList.size == GlobalValues.server_imported_values.maxNumberMessagesToRequest) {
                    break
                }
            }

            val request = ChatMessageStream.RequestFullMessageInfoRequest.newBuilder()
                .setChatRoomId(chatRoomId)
                .addAllMessageUuidList(messageUUIDAndAmountList)

            return@singleMutexWithLock ChatMessageStream.ChatToServerRequest.newBuilder()
                .setRequestFullMessageInfo(request)
                .build()
        }
    }

    /** For a little more info [updating_messages_notes]. **/
    suspend fun requestMessageInfo(
        chatRoomId: String,
        amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage,
        messageUUIDList: List<String>,
    ) = withContext(ioDispatcher) {
        requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {

            if (messageUUIDList.isEmpty()) {
                val errorString = "Attempted to request an empty list of messages.\n" +
                        "chatRoomId: $chatRoomId\n" +
                        "amountOfMessage: $amountOfMessage\n" +
                        "messageUUIDList: $messageUUIDList\n"

                chatStreamObjectErrorHelper(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )

                return@singleMutexWithLock
            }

            for (messageUUID in messageUUIDList) {
                messageQueue.add(
                    RequestedMessageInfo(
                        chatRoomId,
                        amountOfMessage,
                        messageUUID,
                        false
                    )
                )
            }

            if (!currentlyRequestingMessageUpdates) {

                currentlyRequestingMessageUpdates = true

                val chatMessageToServer = buildChatMessageToServer()

                Log.i(
                    "startBiDiTest",
                    "requestMessageInfo() called, beginning requesting ${messageQueue.size} messages"
                )

                requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
                    chatStreamRequestObserver?.onNext(chatMessageToServer)
                }
            } else {
                Log.i(
                    "startBiDiTest",
                    "requestMessageInfo() called, currentlyRequestingMessageUpdates was true, so not requesting anything"
                )
            }
        }
    }

    private suspend fun refreshChatStream() =
        withContext(ioDispatcher) {
            requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {

                //refresh will always return a value, if it does not then the stream went down and so onError should
                // catch something

                val chatMessageToServer = ChatMessageStream.ChatToServerRequest.newBuilder()
                    .setRefreshChatStream(ChatMessageStream.RefreshChatStreamRequest.getDefaultInstance())
                    .build()

                chatStreamRequestObserver?.onNext(chatMessageToServer)
            }
        }

    suspend fun sendMimeTypeMessage(
        messageEntity: MessagesDataEntity,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        mimeType: String,
        thumbnailForReply: ByteArray,
        fragmentInstanceID: String,
    ) {
        withContext(ioDispatcher) {

            sendMessage(
                messageEntity,
                thumbnailForReply,
                fragmentInstanceID,
                messageAlreadyStoredInDatabase = false
            ) {
                //save gif to database
                addMimeType(
                    messageEntity.downloadUrl,
                    mimeType,
                    mimeTypeFilePath,
                    mimeTypeWidth,
                    mimeTypeHeight,
                    it
                )
            }
        }
    }

    //send a message to server from client
    suspend fun sendMessage(
        messageEntity: MessagesDataEntity,
        thumbnailForReplyByteArray: ByteArray,
        fragmentInstanceID: String,
        messageAlreadyStoredInDatabase: Boolean,
        runInsideTransaction: suspend (TransactionWrapper) -> Unit,
    ) {
        withContext(ioDispatcher) {

            //NOTE: even though this uses transactions, it still requires shared concurrency in order to work properly
            // without sending duplicates (means it needs to be a coroutine Mutex() not threading types like Synchronized)
            //SIDE-NOTE: a bi-directional stream can also guarantee order but it has to have some form of shared
            // concurrency because the StreamObserver is not thread-safe so it is not an improvement
            /**
             * This mutex is called from receiveMessage where both responseMutexKey is locked, make sure that nothing
             * inside of this function or clientMessageToServer locks the response mutex.
             **/
            Log.i(
                "sendingMessage",
                "starting sendMessage() about to lock sendMessageMutex messageType: ${
                    MessageBodyCase.forNumber(
                        messageEntity.messageType
                    )
                }"
            )
            sendMessageMutex.withLock {

                Log.i(
                    "sendingMessage",
                    "sendMessageMutex() locked"
                )

                if (messageAlreadyStoredInDatabase) {

                    //NOTE: this seems redundant however if the chat stream starts and stops with multiple messages
                    // 'backed up' at the shared concurrency(sendMessagesMutex) function here, then multiple functions can be sent
                    //NOTE: this shouldn't need a transaction it is just a read and writes are synchronous, so
                    //  this will never run while an update is happening
                    val hasBeenSent = messagesDataSource.retrieveHasBeenSentStatusByIndex(
                        messageEntity.messageUUIDPrimaryKey
                    )

                    //if message has already been sent
                    if (hasBeenSent == ChatMessageStoredStatus.STORED_ON_SERVER) {
                        return@withLock
                    }
                }

                var sendMessage = true

                val messageType =
                    MessageBodyCase.forNumber(
                        messageEntity.messageType
                    )

                if (messageAlreadyStoredInDatabase &&
                    (messageType == MessageBodyCase.EDITED_MESSAGE
                            || messageType == MessageBodyCase.DELETED_MESSAGE)
                ) {

                    val modifiedMessageUUID =
                        messageEntity.modifiedMessageUUID

                    //extract message OID
                    if (modifiedMessageUUID.isValidUUIDKey()) {

                        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                            applicationContext,
                            DatabasesToRunTransactionIn.MESSAGES,
                            DatabasesToRunTransactionIn.OTHER_USERS
                        )

                        //this transaction is all right because if stored_status is set to ChatMessageStoredStatus.STORED_ON_SERVER
                        // of the other message, it will never be downgraded, and so there is no chance of data corruption
                        transactionWrapper.runTransaction {

                            val storedStatus =
                                messagesDataSource.retrieveHasBeenSentStatusByIndex(
                                    modifiedMessageUUID
                                )

                            if (storedStatus != ChatMessageStoredStatus.STORED_ON_SERVER) { //chat message has not been sent to the server yet

                                if (messageType == MessageBodyCase.DELETED_MESSAGE) {
                                    //remove the modified message
                                    messagesDataSource.removeSingleMessageByUUID(
                                        messageEntity.modifiedMessageUUID,
                                        { picturePath ->
                                            deleteFileInterface.sendFileToWorkManager(
                                                picturePath
                                            )
                                        },
                                        { gifURL ->
                                            deleteGif(
                                                mimeTypeDataSource,
                                                gifURL,
                                                deleteFileInterface,
                                                errorHandling
                                            )
                                        },
                                        this
                                    )
                                } else { //MESSAGE_EDITED

                                    //update the respective message text and that it was properly received by the server
                                    messagesDataSource.updateMessageToEditedAndSentByServer(
                                        messageEntity.modifiedMessageUUID,
                                        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO,
                                        hasCompleteInfo = true,
                                        messageEntity.messageText,
                                        //the TEXT_MESSAGE itself has not been sent and so can generate the isEdited
                                        // time on the client and not send it to the server
                                        getCurrentTimestampInMillis(),
                                        editHasBeenSent = true
                                    )
                                }

                                chatRoomsDataSource.updateUserLastObservedTime(
                                    messageEntity.chatRoomId,
                                    messageEntity.messageObservedTime
                                )

                                sendMessage = false

                                //do not insert the edited or deleted message here
                            }

                        }

                    } else {

                        sendMessage = false

                        //NOTE: -1L means the value was never set 0L means it was set but the index it was set to was never set
                        val errorString =
                            "If messageOID for modified message was not set the index should always be passed" +
                                    " down, this message has no info saved as to what it is modifying.\n$messageEntity"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )

                        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                            applicationContext,
                            DatabasesToRunTransactionIn.MESSAGES,
                            DatabasesToRunTransactionIn.OTHER_USERS
                        )

                        //NOTE: Because this message can be called from Chat Room Stream and an error could have
                        // occurred make sure to remove the message from the database
                        messagesDataSource.removeSingleMessageByUUID(
                            messageEntity.messageUUIDPrimaryKey,
                            { picturePath ->
                                deleteFileInterface.sendFileToWorkManager(
                                    picturePath
                                )
                            },
                            { gifURL ->
                                deleteGif(
                                    mimeTypeDataSource,
                                    gifURL,
                                    deleteFileInterface,
                                    errorHandling
                                )
                            },
                            transactionWrapper
                        )

                    }
                }

                if (sendMessage) { //if sending message

                    val returnVal =
                        try {
                            runClientMessageToServer(
                                thumbnailForReplyByteArray,
                                messageAlreadyStoredInDatabase,
                                applicationContext,
                                accountInfoDataSource,
                                messagesDataSource,
                                chatRoomsDataSource,
                                accountPicturesDataSource,
                                mimeTypeDataSource,
                                clientsIntermediate,
                                errorHandling,
                                messageEntity,
                                runInsideTransaction,
                                deleteFileInterface,
                                ioDispatcher
                            )
                        } catch (e: InvalidMessagePassedException) {

                            //This will remove all messages from the database
                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                        }

                    Log.i(
                        "sendingMessage",
                        "runClientMessageToServer() completed returnVal: $returnVal"
                    )

                    //NOTE: messageEntity should have been modified if the message was properly received
                    if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
                        clientMessageToServerReturnValue(
                            ClientMessageToServerReturnValueDataHolder(
                                messageEntity, returnVal, fragmentInstanceID
                            )
                        )
                    }
                }

                if (messageEntity.messageText != "") {
                    Log.i(
                        "sendMessageThing",
                        "finishing sendMessage() message: ${messageEntity.messageText}"
                    )
                }

            }

            Log.i(
                "sendingMessage",
                "completed sendingMessage()"
            )
        }
    }

    private suspend fun updateMessage(
        message: ChatMessageToClientMessage.ChatMessageToClient
    ): MessagesDataEntity {

        /** The chat stream response of 'REQUEST_FULL_MESSAGE_INFO_RESPONSE' requires the same types as when
         * messagesDataEntity are saved. If more are added or removed in this function the chat stream response
         * will need to be modified accordingly. **/

        val messagesDataEntity: MessagesDataEntity? =
        //these are currently the only types of messages (the 'active' messages) in which both conditions are true
        // 1) will be displayed inside the recycler view to the client
            // 2) can request more beyond AmountOfMessage.ONLY_SKELETON
            when (message.message.messageSpecifics.messageBodyCase) {
                MessageBodyCase.TEXT_MESSAGE -> {

                    if (!message.message.messageSpecifics.textMessage.activeMessageInfo.isDeleted) {
                        messagesDataSource.updateTextMessageSpecifics(
                            message.messageUuid,
                            message.message.standardMessageInfo.amountOfMessageValue,
                            message.message.standardMessageInfo.messageHasCompleteInfo,
                            message.message.messageSpecifics.textMessage.messageText,
                            message.message.messageSpecifics.textMessage.isEdited,
                            message.message.messageSpecifics.textMessage.editedTime,
                            message.message.messageSpecifics.textMessage.activeMessageInfo
                        )
                    } else { //if message is deleted
                        //if message is deleted, remove it from the database and set MessageDataEntity messageType to 0 to
                        // represent that it does not exist
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(message.messageUuid)
                    }
                }
                MessageBodyCase.PICTURE_MESSAGE -> {

                    // because I request the picture oid through a message, I don't need the picture OID anymore
                    if (!message.message.messageSpecifics.pictureMessage.activeMessageInfo.isDeleted) {

                        val pictureFile: File? =
                            if (message.message.standardMessageInfo.amountOfMessage == TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO
                                && message.message.messageSpecifics.pictureMessage.pictureFileInBytes.size() == message.message.messageSpecifics.pictureMessage.pictureFileSize
                            ) { //if file is proper size
                                generatePictureMessageFile(
                                    message.messageUuid,
                                    applicationContext
                                )
                            } else { //if file is wrong size (corrupt file) or not enough info
                                null
                            }

                        val messageEntity = messagesDataSource.updatePictureMessageSpecifics(
                            message.messageUuid,
                            message.message.standardMessageInfo.amountOfMessageValue,
                            message.message.standardMessageInfo.messageHasCompleteInfo,
                            message.message.messageSpecifics.pictureMessage.imageHeight,
                            message.message.messageSpecifics.pictureMessage.imageWidth,
                            pictureFile?.absolutePath ?: "",
                            message.message.messageSpecifics.pictureMessage.activeMessageInfo,
                        )

                        //store picture to file if necessary
                        //if message was not found, do not save the picture
                        if (pictureFile != null && messageEntity != null) {
                            storeChatPictureToFile(
                                pictureFile,
                                message.message.messageSpecifics.pictureMessage.pictureFileInBytes,
                                { errorString, lineNumber, fileName, stackTrace ->
                                    chatStreamObjectErrorHelper(
                                        errorString,
                                        lineNumber,
                                        fileName,
                                        stackTrace
                                    )
                                }
                            )
                        }

                        messageEntity

                    } else { //if message is deleted
                        //if message is deleted, remove it from the database and set MessageDataEntity messageType to 0 to
                        // represent that it does not exist
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(message.messageUuid)
                    }
                }
                MessageBodyCase.LOCATION_MESSAGE -> {

                    if (!message.message.messageSpecifics.locationMessage.activeMessageInfo.isDeleted) {
                        messagesDataSource.updateLocationMessageSpecifics(
                            message.messageUuid,
                            message.message.standardMessageInfo.amountOfMessageValue,
                            message.message.standardMessageInfo.messageHasCompleteInfo,
                            message.message.messageSpecifics.locationMessage.longitude,
                            message.message.messageSpecifics.locationMessage.latitude,
                            message.message.messageSpecifics.locationMessage.activeMessageInfo
                        )
                    } else { //if message is deleted
                        //if message is deleted, remove it from the database and set MessageDataEntity messageType to 0 to
                        // represent that it does not exist
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(message.messageUuid)
                    }
                }
                MessageBodyCase.MIME_TYPE_MESSAGE -> {

                    if (!message.message.messageSpecifics.mimeTypeMessage.activeMessageInfo.isDeleted) {

                        //The url & mime type are ALWAYS sent back, as long as this message is an 'update' and
                        // not a new message, this should be fine

                        messagesDataSource.updateMimeTypeMessageSpecifics(
                            message.messageUuid,
                            message.message.standardMessageInfo.amountOfMessageValue,
                            message.message.standardMessageInfo.messageHasCompleteInfo,
                            message.message.messageSpecifics.mimeTypeMessage.imageHeight,
                            message.message.messageSpecifics.mimeTypeMessage.imageWidth,
                            message.message.messageSpecifics.mimeTypeMessage.urlOfDownload,
                            message.message.messageSpecifics.mimeTypeMessage.mimeType,
                            message.message.messageSpecifics.mimeTypeMessage.activeMessageInfo
                        )
                    } else { //if message is deleted
                        //if message is deleted, remove it from the database and set MessageDataEntity messageType to 0 to
                        // represent that it does not exist
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(message.messageUuid)
                    }

                }
                MessageBodyCase.INVITE_MESSAGE -> {

                    if (!message.message.messageSpecifics.inviteMessage.activeMessageInfo.isDeleted) {

                        //The url & mime type are ALWAYS sent back, as long as this message is an 'update' and
                        // not a new message, this should be fine
                        messagesDataSource.updateInviteMessageSpecifics(
                            message.messageUuid,
                            message.message.standardMessageInfo.amountOfMessageValue,
                            message.message.standardMessageInfo.messageHasCompleteInfo,
                            message.message.messageSpecifics.inviteMessage.invitedUserAccountOid,
                            message.message.messageSpecifics.inviteMessage.invitedUserName,
                            message.message.messageSpecifics.inviteMessage.chatRoomId,
                            message.message.messageSpecifics.inviteMessage.chatRoomName,
                            message.message.messageSpecifics.inviteMessage.chatRoomPassword,
                            message.message.messageSpecifics.inviteMessage.activeMessageInfo
                        )
                    } else { //if message is deleted
                        //if message is deleted, remove it from the database and set MessageDataEntity messageType to 0 to
                        // represent that it does not exist
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(message.messageUuid)
                    }

                }
                else -> {

                    //the above are currently the only types of messages (the 'active' messages) in which both conditions are true
                    // 1) will be displayed inside the recycler view to the client
                    // 2) can request more beyond AmountOfMessage.ONLY_SKELETON

                    //Messages that can be updated (NOTE: NOT DIFFERENT_USER_JOINED_MESSAGE, the message itself only store the user it was sent by,
                    // in order to update this updateSingleUser should be used)
                    //TEXT_MESSAGE
                    //PICTURE_MESSAGE
                    //LOCATION_MESSAGE (reply)
                    //MIME_TYPE_MESSAGE (reply)
                    //INVITE_MESSAGE (reply)

                    val errorString =
                        "Incorrect message type was returned from REQUEST_FULL_MESSAGE_INFO_RESPONSE.\n" +
                                "messageBodyCase: ${message.message.messageSpecifics.messageBodyCase}" +
                                "message: ${convertChatMessageToClientToErrorString(message)}\n"

                    chatStreamObjectErrorHelper(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName
                    )

                    //generate class to remove data entity from view model
                    return generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(
                        message.messageUuid
                    )
                }
            }

        //messagesDataEntity can be null if a message was requested from the server, then the message was sent back
        // to the client when the client left the chat room.
        return messagesDataEntity ?:
        //generate class to remove data entity from view model
        generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(message.messageUuid)
    }

    private suspend fun onlyStoreMessage(
        message: ChatMessageToClientMessage.ChatMessageToClient,
    ) = withContext(ioDispatcher) {
        Log.i(
            "startBiDiTest",
            "'onlyStoreMessage' received messageBodyCase: ${message.message.messageSpecifics.messageBodyCase}"
        )

        //NOTE: the 'message.onlyStoreMessage' is only used when a new chat room info is requested to store older messages
        // this means that all the user activity times are up to date, the messages simply need to be stored and the
        // lastTimeUpdated value will need to be set (because it is a client side exclusive value),
        // even though there is a gap here between when the header for the chat room
        // (the user activity time, chat room activity time, other users last activity time) is updated and
        // the new messages are sent back it is irrelevant because as soon as DIFFERENT_USER_JOINED_MESSAGE
        // is stored in the database, that user is subscribed and so they should not miss any messages
        when (message.message.messageSpecifics.messageBodyCase) {
            //message types that would be 'update both chat room last active time and user last active time'
            MessageBodyCase.TEXT_MESSAGE,
            MessageBodyCase.LOCATION_MESSAGE,
            MessageBodyCase.INVITE_MESSAGE,
                //message type that would be 'update matching oid'
            MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
            MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
            MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
            MessageBodyCase.USER_KICKED_MESSAGE,
            MessageBodyCase.USER_BANNED_MESSAGE,
            MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
            MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
            MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
            MessageBodyCase.MATCH_CANCELED_MESSAGE,
            -> {

                val transactionWrapper =
                    ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                transactionWrapper.runTransaction {

                    //if user joined and immediately left a chat room before it finished downloading, this could happen
                    if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                        storeChatMessageInChatRoom(message, this, true)

                        //update last time chat room updated and matching OID to ""
                        chatRoomsDataSource.updateLastTimeUpdatedMatchingOid(
                            message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                            message.timestampStored
                        )
                    }
                }
            }
            MessageBodyCase.CHAT_ROOM_CAP_MESSAGE -> {
                val transactionWrapper =
                    ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                transactionWrapper.runTransaction {

                    //if user joined and immediately left a chat room before it finished downloading, this could happen
                    if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                        storeChatMessageInChatRoom(message, this, true)

                        //NOTE: No need to update any times for this message, the stored_on_server timestamp is the same as
                        // the created time for the server.
                        //NOTE: Also no need to return this to the view model, it is just implemented for future uses and doesn't
                        // do anything yet.
                    }
                }
            }
            MessageBodyCase.PICTURE_MESSAGE -> {

                val transactionWrapper =
                    ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                //store picture to file if necessary
                val pictureFilePath =
                    if (message.message.standardMessageInfo.amountOfMessage ==
                        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO
                    ) {

                        storeChatPictureToFile(
                            message.messageUuid,
                            message.message.messageSpecifics.pictureMessage.pictureFileInBytes,
                            message.message.messageSpecifics.pictureMessage.pictureFileSize,
                            applicationContext,
                            { errorString, lineNumber, fileName, stackTrace ->
                                chatStreamObjectErrorHelper(
                                    errorString,
                                    lineNumber,
                                    fileName,
                                    stackTrace
                                )
                            }
                        )

                    } else {
                        ""
                    }

                transactionWrapper.runTransaction {

                    //if user joined and immediately left a chat room before it finished downloading, this could happen
                    if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                        storeChatMessageInChatRoom(message, this, true, pictureFilePath)

                        //update last time chat room updated and matching OID to ""
                        chatRoomsDataSource.updateLastTimeUpdatedMatchingOid(
                            message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                            message.timestampStored
                        )
                    }
                }
            }
            MessageBodyCase.MIME_TYPE_MESSAGE -> {

                val transactionWrapper =
                    ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                transactionWrapper.runTransaction {

                    //if user joined and immediately left a chat room before it finished downloading, this could happen
                    if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                        //the url and the mime type are always passed back, so incrementing the message
                        // when it is initially loaded
                        addMimeType(
                            message.message.messageSpecifics.mimeTypeMessage.urlOfDownload,
                            message.message.messageSpecifics.mimeTypeMessage.mimeType,
                            transactionWrapper = this
                        )

                        storeChatMessageInChatRoom(message, this, true)

                        //update last time chat room updated and matching OID to ""
                        chatRoomsDataSource.updateLastTimeUpdatedMatchingOid(
                            message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                            message.timestampStored
                        )
                    }
                }
            }
            MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE -> {
                //NOTE: Not storing USER_ACTIVITY_DETECTED in database, the server also does not return the message UUID for this message.

                //don't update matching OID as a precaution
                chatRoomsDataSource.updateTimeLastUpdated(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.timestampStored
                )
            }
            //edited and deleted should never be sent back here because the only time the onlyStoreMessage bool
            // is set to true is when a new chat room is being returned and in that case ALL messages are retrieved
            // and so the edited or deleted values are already set
            MessageBodyCase.EDITED_MESSAGE,
            MessageBodyCase.DELETED_MESSAGE,
            MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
            MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
            MessageBodyCase.HISTORY_CLEARED_MESSAGE,
            MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
            MessageBodyCase.LOADING_MESSAGE,
            MessageBodyCase.MESSAGEBODY_NOT_SET,
            null,
            -> {

                val errorString =
                    "This type should never be sent back with onlyStoreMessage as true messageType: ${message.message.messageSpecifics.messageBodyCase}\n${
                        convertTypeOfChatMessageToErrorString(message.message)
                    }"

                chatStreamObjectErrorHelper(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )
            }
        }
    }

    //TESTING_NOTE: Try the below (might want to add it to the testing list in AndroidTODO).
    // 1) Device is in chat room
    // 2) User connects on different device
    // 3) User is kicked/leaves chat room
    // 4) User rejoins chat room
    // 5) User goes back to different device and connects to chat room
    // 6) leave/kick message is received and as soon as it is the user is disconnected (initialization msg?)
    // Need to make sure DIFFERENT_USER_JOINED_MESSAGE, DIFFERENT_USER_LEFT_MESSAGE, USER_KICKED_MESSAGE and
    //  USER_BANNED_MESSAGE all function properly in the listed situation.
    suspend fun receiveMessage(
        message: ChatMessageToClientMessage.ChatMessageToClient,
        calledFromJoinChatRoom: Boolean
    ) {
        withContext(ioDispatcher) {
            requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {
                Log.i(
                    "startBiDiTest",
                    "receiveMessage() start for ${message.message.messageSpecifics.messageBodyCase}"
                )
                try {

                    val tempChatRoomReference = tempChatRoom

                    if (message.message.standardMessageInfo.chatRoomIdMessageSentFrom == tempChatRoomReference?.chatRoomId
                        && message.message.messageSpecifics.messageBodyCase != MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE
                    ) {
                        //These messages will be handled when THIS_USER_JOINS_CHAT_ROOM is started
                        tempChatRoomReference?.receivedMessages?.add(message)
                        return@singleMutexWithLock
                    }

                    //2 types of messages
                    // 1) user-action messages (ex: left channel, kicked from channel, etc...)
                    // 2) active messages (ex: chat message, location, etc...)

                    if (message.messageUuid.isNotEmpty()) {

                        val exists =
                            messagesDataSource.messageExistsInDatabase(
                                message.messageUuid
                            )

                        val activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo? =
                            when (message.message.messageSpecifics.messageBodyCase) {
                                MessageBodyCase.TEXT_MESSAGE -> {
                                    message.message.messageSpecifics.textMessage.activeMessageInfo
                                }
                                MessageBodyCase.PICTURE_MESSAGE -> {
                                    message.message.messageSpecifics.pictureMessage.activeMessageInfo
                                }
                                MessageBodyCase.LOCATION_MESSAGE -> {
                                    message.message.messageSpecifics.locationMessage.activeMessageInfo
                                }
                                MessageBodyCase.MIME_TYPE_MESSAGE -> {
                                    message.message.messageSpecifics.mimeTypeMessage.activeMessageInfo
                                }
                                MessageBodyCase.INVITE_MESSAGE -> {
                                    message.message.messageSpecifics.inviteMessage.activeMessageInfo
                                }
                                else -> {
                                    null
                                }
                            }

                        //Duplicates can (and most likely will) happen. When they do happen it
                        // is important to not do anything with them. Otherwise messages such as
                        // DIFFERENT_USER_LEFT_MESSAGE can force a user to leave.
                        if (exists) {
                            Log.i(
                                "startBiDiTest",
                                "receiveMessage() duplicate found: ${message.message.messageSpecifics.messageBodyCase}"
                            )
                            return@singleMutexWithLock
                        }
                        //if message is deleted, remove message
                        else if (activeMessageInfo?.isDeleted == true) {

                            //remove message along with any associated files
                            messagesDataSource.removeSingleMessageByUUID(
                                message.messageUuid,
                                { picturePath ->
                                    deleteFileInterface.sendFileToWorkManager(
                                        picturePath
                                    )
                                },
                                { gifURL ->
                                    deleteGif(
                                        mimeTypeDataSource,
                                        gifURL,
                                        deleteFileInterface,
                                        errorHandling
                                    )
                                }
                            )

                            return@singleMutexWithLock
                        }
                    }

                    //NOTE: Need to make sure this client is inside the chat room that is specified, otherwise could have a memory leak.
                    when {
                        message.onlyStoreMessage -> { //if this message was sent when a new chat room was joined

                            //NOTE: the 'message.onlyStoreMessage' is only used when a new chat room info is requested to store older messages
                            // this means that all the user activity times are up to date, the messages simply need to be stored and the
                            // lastTimeUpdated value will need to be set (because it is a client side exclusive value)
                            onlyStoreMessage(message)
                        }
                        else -> { //if this was a 'standard' message
                            Log.i(
                                "startBiDiTest",
                                "'standard' received messageType: ${message.message.messageSpecifics.messageBodyCase} storedOnServerTime: ${message.timestampStored} sender: ${message.sentByAccountId}"
                            )

                            when (message.message.messageSpecifics.messageBodyCase) {
                                MessageBodyCase.TEXT_MESSAGE,
                                MessageBodyCase.LOCATION_MESSAGE,
                                MessageBodyCase.MIME_TYPE_MESSAGE,
                                MessageBodyCase.INVITE_MESSAGE,
                                -> {

                                    var messageEntity: MessagesDataEntity? = null

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client for t
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                                            if (message.message.messageSpecifics.messageBodyCase ==
                                                MessageBodyCase.MIME_TYPE_MESSAGE
                                            ) {
                                                //increase reference count of the gif url
                                                addMimeType(
                                                    message.message.messageSpecifics.mimeTypeMessage.urlOfDownload,
                                                    message.message.messageSpecifics.mimeTypeMessage.mimeType,
                                                    transactionWrapper = this
                                                )
                                            }

                                            //duplicates checked for above
                                            messageEntity =
                                                storeChatMessageInChatRoom(message, this, true)

                                            updateUserAndChatRoomActivityTimes(message, this)
                                        }
                                    }

                                    messageEntity?.let {
                                        notifySubscriber(it)
                                    }
                                }
                                MessageBodyCase.PICTURE_MESSAGE -> {

                                    //store picture to file if necessary
                                    val pictureFilePath =
                                        if (message.message.standardMessageInfo.amountOfMessage == TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO) {
                                            storeChatPictureToFile(
                                                message.messageUuid,
                                                message.message.messageSpecifics.pictureMessage.pictureFileInBytes,
                                                message.message.messageSpecifics.pictureMessage.pictureFileSize,
                                                applicationContext,
                                                { errorString, lineNumber, fileName, stackTrace ->
                                                    chatStreamObjectErrorHelper(
                                                        errorString,
                                                        lineNumber,
                                                        fileName,
                                                        stackTrace
                                                    )
                                                }
                                            )
                                        } else {
                                            ""
                                        }

                                    var messageEntity: MessagesDataEntity? = null

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client for t
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                                            if (message.message.messageSpecifics.messageBodyCase == MessageBodyCase.MIME_TYPE_MESSAGE) {
                                                //increase reference count of the gif url
                                                addMimeType(
                                                    message.message.messageSpecifics.mimeTypeMessage.urlOfDownload,
                                                    message.message.messageSpecifics.mimeTypeMessage.mimeType,
                                                    transactionWrapper = this
                                                )
                                            }

                                            messageEntity =
                                                storeChatMessageInChatRoom(
                                                    message,
                                                    this,
                                                    true,
                                                    pictureFilePath
                                                )

                                            updateUserAndChatRoomActivityTimes(message, this)
                                        }
                                    }

                                    messageEntity?.let {
                                        notifySubscriber(it)
                                    }
                                }
                                MessageBodyCase.EDITED_MESSAGE,
                                MessageBodyCase.DELETED_MESSAGE,
                                -> {
                                    //NOTE: message.messageUuid is not reliably set in MESSAGE_EDITED or MESSAGE_DELETED types

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        val modifiedMessageUUID =
                                            if (message.message.messageSpecifics.messageBodyCase ==
                                                MessageBodyCase.EDITED_MESSAGE
                                            ) {

                                                //amountOfMessage for EDITED_MESSAGE will mirror the amount of text sent back
                                                // by TEXT_MESSAGE
                                                //update the respective message text and that it was properly received by the server
                                                messagesDataSource.updateMessageToEditedAndSentByServer(
                                                    message.message.messageSpecifics.editedMessage.messageUuid,
                                                    message.message.standardMessageInfo.amountOfMessage,
                                                    message.message.standardMessageInfo.messageHasCompleteInfo,
                                                    message.message.messageSpecifics.editedMessage.newMessage,
                                                    message.timestampStored,
                                                    editHasBeenSent = true
                                                )

                                                message.message.messageSpecifics.editedMessage.messageUuid

                                            } else { //DELETED_MESSAGE

                                                //remove deleted message
                                                messagesDataSource.removeSingleMessageByUUID(
                                                    message.message.messageSpecifics.deletedMessage.messageUuid,
                                                    { picturePath ->
                                                        deleteFileInterface.sendFileToWorkManager(
                                                            picturePath
                                                        )
                                                    },
                                                    { gifURL ->
                                                        deleteGif(
                                                            mimeTypeDataSource,
                                                            gifURL,
                                                            deleteFileInterface,
                                                            errorHandling
                                                        )
                                                    },
                                                    this
                                                )

                                                message.message.messageSpecifics.deletedMessage.messageUuid
                                            }

                                        //NOTE: not updating chat room last active time because don't want this to give a notification
                                        // of chat room activity to user
                                        if (message.sentByAccountId == LoginFunctions.currentAccountOID) { //if sent by current user
                                            chatRoomsDataSource.updateUserLastActiveTimeLastTimeUpdatedMatchingOid(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                message.timestampStored
                                            )
                                        } else { //message was sent by another user

                                            val otherUserExists =
                                                otherUsersDataSource.otherUserExists(
                                                    message.sentByAccountId
                                                )

                                            if (otherUserExists) { //if other user exists

                                                otherUsersDataSource.setUserLastActiveTimeInChatRoom(
                                                    message.sentByAccountId,
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    message.timestampStored,
                                                    this
                                                )

                                            } else { //if other user does not exist

                                                val errorString =
                                                    "The user sending the message should always exist (the user would have had to have left any relevant chat rooms" +
                                                            " because other users are not removed when they simply leave the chat room)."

                                                chatStreamObjectErrorHelper(
                                                    errorString,
                                                    Thread.currentThread().stackTrace[2].lineNumber,
                                                    Thread.currentThread().stackTrace[2].fileName
                                                )

                                                //NOTE: allow this to continue in order to update last updated time
                                            }

                                            chatRoomsDataSource.updateTimeLastUpdated(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                message.timestampStored
                                            )
                                        }

                                        //NOTE: If the user clears the chat room history or an edit is sent back for a deleted message
                                        // (which is possible) the message that was supposed to be edited may not exist
                                        // so this condition is acceptable. However, if the message is deleted it will never exist.
                                        if (message.message.messageSpecifics.messageBodyCase ==
                                            MessageBodyCase.DELETED_MESSAGE
                                            || messagesDataSource.messageExistsInDatabase(
                                                modifiedMessageUUID
                                            )
                                        ) {
                                            this.runAfterTransaction {
                                                val messageEntity =
                                                    convertChatMessageToMessageDataEntity(
                                                        message,
                                                        errorStore = errorHandling,
                                                        ioDispatcher = ioDispatcher
                                                    )

                                                if (messageEntity.messageType != -1) {
                                                    notifySubscriber(messageEntity)
                                                }
                                            }
                                        }
                                    }
                                }
                                MessageBodyCase.USER_KICKED_MESSAGE -> {
                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client for t
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {
                                            handleKickBanMessage(
                                                message,
                                                this,
                                                message.message.messageSpecifics.userKickedMessage.kickedAccountOid
                                            )
                                        }
                                    }
                                }
                                MessageBodyCase.USER_BANNED_MESSAGE -> {

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {
                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client for t
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {
                                            handleKickBanMessage(
                                                message,
                                                this,
                                                message.message.messageSpecifics.userBannedMessage.bannedAccountOid
                                            )
                                        }
                                    }
                                }
                                MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                                    //This messageType is expected to be sent back with the basic user info name, activities, age etc...
                                    // as well as the thumbnail, however NOT any pictures

                                    //DIFFERENT_USER_JOINED_MESSAGE can be sent from
                                    // 1) initialization
                                    // 2) joinChatRoom (won't end up in receiveMessage because onlyStoreMessage == true)

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //NOTE: It must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added.
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                                            val messageEntity =
                                                storeChatMessageInChatRoom(message, this, true)

                                            updateUserAndChatRoomActivityTimes(message, this)

                                            if (message.message.messageSpecifics.differentUserJoinedMessage.memberInfo.hasUserInfo()) { //this can be sent back with a skeleton and only account state & user last active time set
                                                if (message.sentByAccountId != LoginFunctions.currentAccountOID) { //if this message is about a different user

                                                    val otherUser = insertOrUpdateOtherUser(
                                                        message.message.messageSpecifics.differentUserJoinedMessage.memberInfo.userInfo,
                                                        ApplicationRepository.UpdateMemberReasonEnum.JOINED_CHAT_ROOM,
                                                        otherUsersDataSource,
                                                        applicationContext,
                                                        this,
                                                        errorHandling,
                                                        deleteFileInterface,
                                                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                        message.message.messageSpecifics.differentUserJoinedMessage.memberInfo.accountState,
                                                        message.message.messageSpecifics.differentUserJoinedMessage.memberInfo.accountLastActivityTime
                                                    ).otherUser

                                                    if (otherUser.accountOID.isNotEmpty()) {
                                                        this.runAfterTransaction {
                                                            notifySubscriber(
                                                                messageEntity,
                                                                otherUser
                                                            )
                                                        }
                                                    }
                                                }
                                                // This could happen if the user left and joined on a different device. Then
                                                // had to download the messages of left->join on chat stream initialization for
                                                // the original device.
                                                else { //if this message is about the current user
                                                    this.runAfterTransaction {
                                                        notifySubscriber(
                                                            messageEntity,
                                                            null
                                                        )
                                                    }
                                                }
                                            } else { //no member info was sent back (usually means it was downloaded already using THIS_USER_JOINED_CHAT_ROOM_MEMBER), just send the message to the fragment
                                                this.runAfterTransaction {
                                                    notifySubscriber(
                                                        messageEntity,
                                                        null,
                                                        true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {
                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client for t
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                                            val messageEntity =
                                                storeChatMessageInChatRoom(message, this, true)

                                            updateUserAndChatRoomActivityTimes(message, this)

                                            if (message.message.messageSpecifics.differentUserLeftMessage.newAdminAccountOid == LoginFunctions.currentAccountOID
                                            ) { //if new admin was promoted and it is this device user

                                                //update chat room messages and account state
                                                chatRoomsDataSource.updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                                                    message.timestampStored
                                                )

                                            } else { //if this device user was not promoted to admin or a new admin was not promoted

                                                if (message.message.messageSpecifics.differentUserLeftMessage.newAdminAccountOid.isValidMongoDBOID()) { //if user that left was admin then this passed accountOID is the new admin

                                                    otherUsersDataSource.updateUserAccountState(
                                                        message.message.messageSpecifics.differentUserLeftMessage.newAdminAccountOid,
                                                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                                                        this
                                                    )
                                                }

                                                //update chat room times
                                                chatRoomsDataSource.updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    message.timestampStored
                                                )
                                            }

                                            if (message.sentByAccountId != LoginFunctions.currentAccountOID
                                                || !message.message.standardMessageInfo.doNotUpdateUserState
                                            ) {

                                                val otherUser = otherUserLeavesChatRoom(
                                                    otherUsersDataSource,
                                                    message.sentByAccountId,
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM,
                                                    this,
                                                    deleteFileInterface,
                                                    errorHandling,
                                                    message.timestampStored,
                                                )

                                                //NOTE: if other user is null, the error was already stored
                                                otherUser?.let {
                                                    this.runAfterTransaction {
                                                        notifySubscriber(
                                                            messageEntity,
                                                            otherUser
                                                        )
                                                    }
                                                }

                                            } else {
                                                //NOTE: this means it is a DIFFERENT_USER_LEFT_CHAT_ROOM about this current user
                                                //TECHNICALLY this is possible if the user
                                                // 1) joined the chat room on device_A
                                                // 2) logged out of device_A
                                                // 3) logged in on device_B
                                                // 4) left the chat room
                                                // 5) then rejoined the chat room
                                                // 6) logged out of device_B
                                                // 7) logged in on device_A
                                                // it would make the chat room run an 'update' and it would request this message not as type message.onlyStoreMessage

                                                //NOTE: this could be combined above to make only 1 database call, but it should be very rare and I think this is a bit more clear
                                                chatRoomsDataSource.updateUserLastActiveTime(
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    message.timestampStored
                                                )

                                            }

                                        }
                                    }
                                }
                                MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE -> {

                                    //this should never actually be sent back, UPDATE_OBSERVED_TIME_MESSAGE is used to signal to the
                                    // server that this user observed the chat room, then if the user logs in on another device it will
                                    // have saved their last observed time, if in the future it is implemented this will take care of it
                                    // though
                                    chatRoomsDataSource.updateUserLastObservedTime(
                                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                        message.message.messageSpecifics.updateObservedTimeMessage.chatRoomLastObservedTime
                                    )
                                }
                                MessageBodyCase.NEW_UPDATE_TIME_MESSAGE -> {
                                    //This will be sent back when the current user message is sent back by the chat stream. For example,
                                    // if user A sends in a message and when the message is stored successfully by the server then the
                                    // timestamp will be returned. If this timestamp is used to update the updated_time for the chat room
                                    // it leaves the potential for messages to be missed. So the updated_time is updated in order as messages
                                    // are streamed back from the server. This represents the time user A send their message.
                                    /** see [update_times_for_sent_messages] for details **/
                                    chatRoomsDataSource.updateTimeLastUpdated(
                                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                        message.message.messageSpecifics.newUpdateTimeMessage.messageTimestampStored
                                    )
                                }
                                MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE -> {

                                    Log.i(
                                        "joinChatRoomTime",
                                        "THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE running"
                                    )

                                    val qrCodeString =
                                        if (message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.qrCodeImageBytes.isValidUtf8) {
                                            message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.qrCodeImageBytes.toStringUtf8()
                                        } else {
                                            "qrCodeString"
                                        }

                                    Log.i("event_qr_code", "qr_code: $qrCodeString condition ${qrCodeString != GlobalValues.server_imported_values.qrCodeDefault}")

                                    val qRCodeImageFilePath =
                                        if (
                                            qrCodeString != GlobalValues.server_imported_values.qrCodeDefault
                                        ) {
                                            storeChatQRCodeToFile(
                                                message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomId,
                                                message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.qrCodeImageBytes,
                                                applicationContext,
                                                { errorString, lineNumber, fileName, stackTrace ->
                                                    chatStreamObjectErrorHelper(
                                                        errorString,
                                                        lineNumber,
                                                        fileName,
                                                        stackTrace
                                                    )
                                                }
                                            )
                                        } else {
                                            GlobalValues.server_imported_values.qrCodeDefault
                                        }

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    val passedChatRoomInfo =
                                        message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo

                                    transactionWrapper.runTransaction {

                                        if (passedChatRoomInfo.matchMadeChatRoomOid != "") { //if the message is for a match made chat room

                                            val matchingOIDChatRoom =
                                                chatRoomsDataSource.getSingleChatRoomByMatchingOID(
                                                    passedChatRoomInfo.matchMadeChatRoomOid
                                                )

                                            //if chat room already exists as a 'match made', remove old chat room
                                            matchingOIDChatRoom?.let {
                                                removeChatRoomFromDatabase(
                                                    matchingOIDChatRoom.chatRoomID,
                                                    this
                                                )
                                            }
                                        }

                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) { //if chat room already exists on device
                                            Log.i(
                                                "joinChatRoomTime",
                                                "removeChatRoomFromDatabase() running"
                                            )
                                            removeChatRoomFromDatabase(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                this
                                            )
                                        }

                                        Log.i(
                                            "joinChatRoomTime",
                                            "inserting Chat Room ${message.message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo}"
                                        )
                                        //It is important to set the chatRoomLastActivityTime as well as userLastActivityTime.
                                        //timeLastUpdated will be set by the following messages.
                                        //timeLastObserved will be set to the passed chatRoomLastObservedTime unless this is a match
                                        // made chat room. In this case it is assumed to have observed all of the most recent messages.
                                        // Otherwise the red dot for new message will pop up for a new match when the CHAT_ROOM_CAP_MESSAGE
                                        // message type is received for the chat room.
                                        chatRoomsDataSource.insertChatRoom(
                                            convertTypeOfChatMessageToNewChatRoom(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                passedChatRoomInfo,
                                                qRCodeImageFilePath,
                                                if (passedChatRoomInfo.matchMadeChatRoomOid != "") passedChatRoomInfo.chatRoomLastActivityTime else passedChatRoomInfo.chatRoomLastObservedTime,
                                                -1L
                                            )
                                        )

                                        val tempChatRoomRef = tempChatRoom

                                        //if messages were sent for this chat room before it successfully received the
                                        // THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE message, process them
                                        if (tempChatRoomRef != null
                                            && message.message.standardMessageInfo.chatRoomIdMessageSentFrom == tempChatRoomRef.chatRoomId
                                        ) {
                                            tempChatRoom = null

                                            for (receivedMessage in tempChatRoomRef.receivedMessages) {
                                                receiveMessage(
                                                    receivedMessage,
                                                    calledFromJoinChatRoom = false
                                                )
                                            }
                                        }

                                    }

                                    Log.i(
                                        "chat_room_exists",
                                        chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)
                                            .toString()
                                    )

                                }
                                MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE -> {

                                    //this message comes from sendNewChatRoomAndMessages
                                    // if user is NOT in chat room info received is
                                    // 1) First Name
                                    // 2) Thumbnail
                                    // 3) AccountOID
                                    // if user is in chat room
                                    // 1) User Info
                                    // 2) Just Thumbnail OR (Thumbnail AND Pictures)

                                    val memberInfo =
                                        message.message.messageSpecifics.thisUserJoinedChatRoomMemberMessage.memberInfo

                                    if (memberInfo.userInfo.accountOid != LoginFunctions.currentAccountOID) { //if the passed back member is not the current account

                                        val transactionWrapper =
                                            ServiceLocator.provideTransactionWrapper(
                                                applicationContext,
                                                DatabasesToRunTransactionIn.OTHER_USERS
                                            )

                                        transactionWrapper.runTransaction {

                                            Log.i("num_users_chat", "ChatStreamObject chatRoomId ${message.message.standardMessageInfo.chatRoomIdMessageSentFrom} account_type: ${memberInfo.userInfo.accountType} account_state: ${memberInfo.accountState}")
                                            //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                            // until this message has been added
                                            if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {
                                                val otherUser = insertOrUpdateOtherUser(
                                                    memberInfo.userInfo,
                                                    ApplicationRepository.UpdateMemberReasonEnum.JOINED_CHAT_ROOM,
                                                    otherUsersDataSource,
                                                    applicationContext,
                                                    this,
                                                    errorHandling,
                                                    deleteFileInterface,
                                                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                    memberInfo.accountState,
                                                    memberInfo.accountLastActivityTime
                                                )

                                                if (otherUser.otherUser.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number
                                                    && otherUser.otherUser.accountOID.isValidMongoDBOID()
                                                ) {
                                                    chatRoomsDataSource.updateEventOid(
                                                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                        otherUser.otherUser.accountOID
                                                    )
                                                }
                                            }
                                        }
                                    } else { //if the passed back member is the current account

                                        val errorString =
                                            "Current user was passed back with a THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE.\n" +
                                            "This should never happen, the function that sends these back on the server" +
                                                    " sendNewChatRoomAndMessages() should filter it out always and send" +
                                                    " it back with THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE "

                                        chatStreamObjectErrorHelper(
                                            errorString,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            Thread.currentThread().stackTrace[2].fileName
                                        )
                                    }

                                }
                                MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE -> {
                                    //this function is called from sendNewChatRoomAndMessages() on the server which can be called during
                                    // 1) chat stream initialization
                                    // 2) joinChatRoom() is called
                                    // 3) a match is made
                                    //There is no need to do the extra processing if this was called from JoinChatRoom. This is
                                    // because the functions themselves have returns to their calling fragments which will handle
                                    // the final navigation and saving of the chat room.
                                    if (!message.message.standardMessageInfo.doNotUpdateUserState
                                        && !calledFromJoinChatRoom
                                    ) { //if this is not from the initial chat stream starting & not called from joinChatRoom

                                        val transactionWrapper =
                                            ServiceLocator.provideTransactionWrapper(
                                                applicationContext,
                                                DatabasesToRunTransactionIn.OTHER_USERS
                                            )

                                        transactionWrapper.runTransaction {

                                            val chatRoom =
                                                chatRoomsDataSource.getSingleChatRoom(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)

                                            Log.i(
                                                "thisUserJoined",
                                                "chatRoom.chatRoomId: ${chatRoom.chatRoomId}"
                                            )

                                            if (chatRoom.chatRoomId != "~") { //if chat room was successfully extracted

                                                val chatRoomUpdateMade =
                                                    when (message.message.messageSpecifics.thisUserJoinedChatRoomFinishedMessage.automaticJoinTypeCase) {
                                                        TypeOfChatMessageOuterClass.ThisUserJoinedChatRoomFinishedChatMessage.AutomaticJoinTypeCase.MATCH_MADE_CHAT_ROOM_OID -> {
                                                            ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH
                                                        }
                                                        TypeOfChatMessageOuterClass.ThisUserJoinedChatRoomFinishedChatMessage.AutomaticJoinTypeCase.YES_SWIPE_EVENT_OID -> {
                                                            ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED
                                                        }
                                                        null,
                                                        TypeOfChatMessageOuterClass.ThisUserJoinedChatRoomFinishedChatMessage.AutomaticJoinTypeCase.AUTOMATICJOINTYPE_NOT_SET -> {
                                                            ChatRoomUpdateMade.CHAT_ROOM_JOINED
                                                        }
                                                    }

                                                initializeMemberListForChatRoom(
                                                    chatRoom,
                                                    chatRoom.chatRoomId,
                                                    otherUsersDataSource
                                                )

                                                Log.i("event_joined_follow", "chatRoomId: ${chatRoom.chatRoomId} num_members: ${chatRoom.chatRoomMembers.size()} updateMode: $chatRoomUpdateMade")
                                                
                                                this.runAfterTransaction {
                                                    returnJoinedLeftChatRoom(
                                                        ReturnJoinedLeftChatRoomDataHolder(
                                                            chatRoom,
                                                            chatRoomUpdateMade
                                                        ),
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                //TESTING_NOTE: test and make sure this works it can be called when
                                // 1) fresh login
                                // 2) chat stream restarts
                                // 3) another user unMatches this user
                                MessageBodyCase.MATCH_CANCELED_MESSAGE,
                                MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
                                -> {

                                    //MATCH_CANCELED_MESSAGE can be called from
                                    // 1) initialization as a message sent by the chat room
                                    // 2) chat change stream (new message from bi-di stream)
                                    // 3) can also be called from joinChatRoom if rejoining the same room (this should be sent back with onlyStoreMessage == true and won't be handled here)

                                    //THIS_USER_LEFT_CHAT_ROOM_MESSAGE can be called from
                                    // 1) initialization when user is no longer a member of a specific chat room

                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        val chatRoom =
                                            chatRoomsDataSource.getSingleChatRoom(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)

                                        if (chatRoom.chatRoomId != "~") { //if chat room exists inside database

                                            removeChatRoomFromDatabase(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                this
                                            )

                                            val doNotUpdateUserState: Boolean

                                            val messageType =
                                                if (message.message.messageSpecifics.messageBodyCase ==
                                                    MessageBodyCase.MATCH_CANCELED_MESSAGE
                                                ) {
                                                    doNotUpdateUserState =
                                                        message.message.standardMessageInfo.doNotUpdateUserState
                                                    ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED
                                                } else {
                                                    doNotUpdateUserState =
                                                        message.message.standardMessageInfo.doNotUpdateUserState
                                                    ChatRoomUpdateMade.CHAT_ROOM_LEFT
                                                }

                                            if (!doNotUpdateUserState) { //if this is not from the initial chat stream starting

                                                this.runAfterTransaction {
                                                    returnJoinedLeftChatRoom(
                                                        ReturnJoinedLeftChatRoomDataHolder(
                                                            chatRoom,
                                                            messageType
                                                        ),
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                MessageBodyCase.CHAT_ROOM_CAP_MESSAGE -> {

                                    //NOTE: The cap will have the same stored time as the chat room header. Also it should always
                                    // be returned when the chat room initially downloads so it should mostly be received here,
                                    // however just in case it will store it and update the activity times.
                                    //NOTE: 'sentByAccountId' will be the oid of the user that created the account.
                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.MESSAGES,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //If user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client and the client left or was removed from the chat
                                        // room.
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added.
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {

                                            storeChatMessageInChatRoom(message, this, true)

                                            //NOTE: No need to update any times for this message, the stored_on_server timestamp is the same as
                                            // the created time for the server.
                                            //NOTE: Also no need to return this to the view model, it is just implemented for future uses and doesn't
                                            // do anything yet.
                                        }
                                    }
                                }
                                MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE -> {
                                    //NOTE: not storing USER_ACTIVITY_DETECTED in database, the server also does not return the message OID for this message

                                    val messageEntity =
                                        convertChatMessageToMessageDataEntity(
                                            message,
                                            errorStore = errorHandling,
                                            ioDispatcher = ioDispatcher
                                        )

                                    if (message.sentByAccountId != LoginFunctions.currentAccountOID) {

                                        val transactionWrapper =
                                            ServiceLocator.provideTransactionWrapper(
                                                applicationContext,
                                                DatabasesToRunTransactionIn.OTHER_USERS
                                            )

                                        transactionWrapper.runTransaction {

                                            //NOTE: it is possible for this message to be to a chat room that was
                                            // removed; this could happen if a message was still inside the queue to
                                            // be sent to the client for the server

                                            otherUsersDataSource.setUserLastActiveTimeInChatRoom(
                                                message.sentByAccountId,
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                message.timestampStored,
                                                this
                                            )

                                            chatRoomsDataSource.updateTimeLastUpdated(
                                                message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                                message.timestampStored
                                            )

                                        }

                                    } else { //if message was sent from this user

                                        //Not updating chat room 'user_last_active_time' or 'last_time_updated' because
                                        // messages where not necessarily checked for them. Also timestampStored is simply
                                        // the current_timestamp on the server, it does not represent anything extracted.
                                        chatRoomsDataSource.updateUserLastObservedTime(
                                            message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                                            message.timestampStored
                                        )

//                                        chatRoomsDataSource.updateChatRoomObservedTimeUserLastActiveTimeLastTimeUpdated(
//                                            message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
//                                            message.timestampStored,
//                                            message.messageUuid //this will probably be empty, that is fine
//                                        )
                                    }

                                    //if error occurred (still want times updated above)
                                    if (messageEntity.messageType != -1) {
                                        notifySubscriber(messageEntity)
                                    }
                                }
                                MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
                                MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
                                MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {

                                        //If user is inside chat room. this could happen if a message was still inside the
                                        // queue to be sent to the client for it
                                        //NOTE: It must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added.
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {
                                            handleUpdateChatRoomInfo(
                                                message,
                                                this
                                            )
                                        }
                                    }
                                }
                                MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {
                                    val transactionWrapper =
                                        ServiceLocator.provideTransactionWrapper(
                                            applicationContext,
                                            DatabasesToRunTransactionIn.OTHER_USERS
                                        )

                                    transactionWrapper.runTransaction {
                                        //if user is inside chat room; this could happen if a message was still inside the
                                        // queue to be sent to the client
                                        //NOTE: it must be under the transaction in order for the chat room to NOT be updated anywhere else
                                        // until this message has been added
                                        if (chatRoomsDataSource.chatRoomExists(message.message.standardMessageInfo.chatRoomIdMessageSentFrom)) {
                                            handleNewAdminPromoted(
                                                message.message.messageSpecifics.newAdminPromotedMessage.promotedAccountOid,
                                                message,
                                                message.sentByAccountId,
                                                this
                                            )
                                        }
                                    }
                                }
                                MessageBodyCase.HISTORY_CLEARED_MESSAGE,
                                MessageBodyCase.LOADING_MESSAGE,
                                MessageBodyCase.MESSAGEBODY_NOT_SET,
                                null,
                                -> {
                                    val errorString =
                                        "This type of message should never be passed back from the server.\n${
                                            convertChatMessageToClientToErrorString(message)
                                        }"

                                    chatStreamObjectErrorHelper(
                                        errorString,
                                        Thread.currentThread().stackTrace[2].lineNumber,
                                        Thread.currentThread().stackTrace[2].fileName
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {

                    val errorString =
                        "An exception was thrown by receiveMessage()\n" +
                                "exception: " + e.message + "\n" +
                                convertChatMessageToClientToErrorString(message)

                    CoroutineScope(ioDispatcher).launch {

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                        )
                    }
                }
            }
        }
    }

    //This will send messages to subscriber, there is a little overhead in implementing it this way
    // (for example otherUser has to be extracted from the database 2x sometimes) however it saves writing code twice
    /** NOTE: this only handles messages stored inside the database, things like UPDATE_CHAT_ROOM_OBSERVED_TIME are not
     * handled here
     * NOTE: don't need to run this inside a transaction because the transactions are only
     * for coordinating info, and this has been coordinated already **/
    private suspend fun notifySubscriber(
        messageEntity: MessagesDataEntity,
        otherUser: OtherUsersDataEntity? = null,
        onlySendMessage: Boolean = false,
        passedSubscriber: ChatStreamSubscriberWrapper? = null //if this is set ONLY this Subscriber will get the messages
    ) {

        val messageType =
            MessageBodyCase.forNumber(
                messageEntity.messageType
            )

        var messageSentToSubscriber = false

        val sendMessageToSubscriber: suspend (runSubscriber: suspend () -> Unit) -> Unit =
            { runSubscriber ->
                runSubscriber()
                messageSentToSubscriber = true
            }

        if (onlySendMessage) {
            sendMessageToSubscriber {
                returnMessagesForChatRoom(
                    ReturnMessagesForChatRoomDataHolder(
                        chatRoomInitialization = false,
                        messageEntity.chatRoomId,
                        listOf(messageEntity),
                        mutableListOf()
                    ),
                    passedSubscriber
                )
            }
            return
        }

        //NOTE: the 'message.onlyStoreMessage' is only used when a new chat room info is requested to store older messages
        // this means that all the user activity times are up to date, the messages simply need to be stored and the
        // lastTimeUpdated value will need to be set (because it is a client side exclusive value)
        when (messageType) {
            MessageBodyCase.TEXT_MESSAGE,
            MessageBodyCase.LOCATION_MESSAGE,
            MessageBodyCase.MIME_TYPE_MESSAGE,
            MessageBodyCase.INVITE_MESSAGE,
            MessageBodyCase.PICTURE_MESSAGE,
            -> {

                sendMessageToSubscriber {
                    returnMessagesForChatRoom(
                        ReturnMessagesForChatRoomDataHolder(
                            chatRoomInitialization = false,
                            messageEntity.chatRoomId,
                            listOf(messageEntity),
                            mutableListOf()
                        ),
                        passedSubscriber
                    )
                }
            }
            MessageBodyCase.DELETED_MESSAGE,
            MessageBodyCase.EDITED_MESSAGE,
            -> {
                sendMessageToSubscriber {
                    returnMessagesForChatRoom(
                        ReturnMessagesForChatRoomDataHolder(
                            chatRoomInitialization = false,
                            messageEntity.chatRoomId,
                            listOf(messageEntity),
                            mutableListOf()
                        ),
                        passedSubscriber
                    )
                }
            }
            MessageBodyCase.USER_KICKED_MESSAGE,
            MessageBodyCase.USER_BANNED_MESSAGE,
            -> {

                val accountOID = messageEntity.accountOID

                if (accountOID == LoginFunctions.currentAccountOID) { //if this user is the account is being removed

                    val accountAction =
                        if (messageType == MessageBodyCase.USER_KICKED_MESSAGE) {
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK
                        } else {
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.BAN
                        }

                    sendMessageToSubscriber {
                        returnKickedBannedFromChatRoom(
                            ReturnKickedBannedFromChatRoomDataHolder(
                                messageEntity.chatRoomId,
                                accountAction
                            ),
                            passedSubscriber
                        )
                    }

                } else { //if a different account is being removed

                    //if other user was passed use it, if not extract it from database
                    val extractedOtherUser =
                        otherUser ?: otherUsersDataSource.getSingleOtherUser(accountOID)

                    extractedOtherUser?.let {

                        //NOTE: if other user is null, the error was already stored
                        val otherUserInfo =
                            convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(
                                extractedOtherUser,
                                messageEntity.chatRoomId,
                            )

                        otherUserInfo?.let {

                            //TESTING_NOTE: make sure that this will work from a 3rd party perspective, say user 1 kicks
                            // user 2, make sure user 3 will properly see the update
                            sendMessageToSubscriber {
                                returnMessageWithMemberForChatRoom(
                                    ReturnMessageWithMemberForChatRoomDataHolder(
                                        messageEntity,
                                        otherUserInfo
                                    ),
                                    passedSubscriber
                                )
                            }
                        }
                    }
                }
            }
            MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE -> {
                returnMessagesForChatRoom(
                    ReturnMessagesForChatRoomDataHolder(
                        chatRoomInitialization = false,
                        messageEntity.chatRoomId,
                        listOf(messageEntity),
                        mutableListOf()
                    ),
                    passedSubscriber
                )
            }
            MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                val sentByAccountOID = messageEntity.sentByAccountID

                val otherUserInfo: OtherUsersInfo? =
                    if (sentByAccountOID != LoginFunctions.currentAccountOID) { //if this message is about a different user

                        //if other user was passed use it, if not extract it from database
                        val extractedOtherUser =
                            otherUser ?: otherUsersDataSource.getSingleOtherUser(sentByAccountOID)

                        if (extractedOtherUser != null) {

                            val chatRoomId = messageEntity.chatRoomId

                            convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(
                                extractedOtherUser,
                                chatRoomId
                            )
                        } else {
                            null
                        }

                    } else { //if this message is about the current user
                        OtherUsersInfo(
                            OtherUsersDataEntity(
                                LoginFunctions.currentAccountOID
                            ),
                            OtherUserChatRoomInfo(),
                            mutableListOf(),
                            mutableListOf()
                        )
                    }

                otherUserInfo?.let {
                    sendMessageToSubscriber {
                        returnMessageWithMemberForChatRoom(
                            ReturnMessageWithMemberForChatRoomDataHolder(
                                messageEntity,
                                otherUserInfo
                            ),
                            passedSubscriber
                        )
                    }
                }
            }
            MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {

                Log.i("differentUserMsg", "notifySubscriber")

                val sentByAccountOID = messageEntity.sentByAccountID

                if (sentByAccountOID != LoginFunctions.currentAccountOID) {

                    //if other user was passed use it, if not extract it from database
                    val extractedOtherUser =
                        otherUser ?: otherUsersDataSource.getSingleOtherUser(sentByAccountOID)

                    //NOTE: if other user is null, the error was already stored
                    extractedOtherUser?.let {

                        val chatRoomId = messageEntity.chatRoomId

                        val otherUserInfo =
                            convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(
                                extractedOtherUser,
                                chatRoomId
                            )

                        otherUserInfo?.let {

                            sendMessageToSubscriber {
                                returnMessageWithMemberForChatRoom(
                                    ReturnMessageWithMemberForChatRoomDataHolder(
                                        messageEntity, otherUserInfo
                                    ),
                                    passedSubscriber
                                )

                                val accountID = messageEntity.accountOID

                                if (accountID != "") { //if the user leaving the chat room was admin and this passed accountOID is the new admin

                                    sendMessageToSubscriber {
                                        returnAccountStateUpdated(
                                            AccountStateUpdatedDataHolder(
                                                chatRoomId,
                                                accountID,
                                                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                                            ),
                                            passedSubscriber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
            MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
            MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
            -> {
                sendMessageToSubscriber {
                    returnChatRoomInfoUpdated(
                        UpdateChatRoomInfoResultsDataHolder(
                            messageEntity
                        ),
                        passedSubscriber
                    )

                    returnMessagesForChatRoom(
                        ReturnMessagesForChatRoomDataHolder(
                            chatRoomInitialization = false,
                            messageEntity.chatRoomId,
                            listOf(messageEntity),
                            mutableListOf()
                        ),
                        passedSubscriber
                    )
                }
            }
            MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {

                val chatRoomId = messageEntity.chatRoomId
                val newAdminAccountOID = messageEntity.accountOID
                val previousAdminAccountOID = messageEntity.sentByAccountID

                sendMessageToSubscriber {
                    returnMessagesForChatRoom(
                        ReturnMessagesForChatRoomDataHolder(
                            chatRoomInitialization = false,
                            messageEntity.chatRoomId,
                            listOf(messageEntity),
                            mutableListOf()
                        ),
                        passedSubscriber
                    )

                    returnAccountStateUpdated(
                        AccountStateUpdatedDataHolder(
                            chatRoomId,
                            previousAdminAccountOID,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                        ),
                        passedSubscriber
                    )

                    returnAccountStateUpdated(
                        AccountStateUpdatedDataHolder(
                            chatRoomId,
                            newAdminAccountOID,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                        ),
                        passedSubscriber
                    )
                }
            }
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
            MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
            MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
            MessageBodyCase.MATCH_CANCELED_MESSAGE,
            MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
            MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
            MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
            -> { /*do nothing*/
            }
            MessageBodyCase.HISTORY_CLEARED_MESSAGE,
            MessageBodyCase.LOADING_MESSAGE,
            MessageBodyCase.MESSAGEBODY_NOT_SET,
            null -> {
                val errorString =
                    "This message type should not be passed to notifySubscriber().\nmessageType: ${messageType}\n${messageEntity}"
                chatStreamObjectErrorHelper(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )
            }
        }

        //this is to avoid duplicate runs of the same message when it cannot be sent to the chatStreamWorker
        if (messageEntity.messageRequiresNotification && !messageSentToSubscriber) { //if the message requires notification and for whatever reason it was not sent to the subscriber
            messagesDataSource.updateMessageToDoesNotRequireNotifications(messageEntity.messageUUIDPrimaryKey)
        }

    }

    private fun convertChatMessageToClientToErrorString(message: ChatMessageToClientMessage.ChatMessageToClient): String {
        message.toString()
        return "messageUuid: ${message.messageUuid}\n" +
                "timestamp_stored: ${message.timestampStored}\n" +
                "current_timestamp: ${message.currentTimestamp}\n" +
                "only_store_message: ${message.onlyStoreMessage}\n" +
                "sent_by_account_id: ${message.sentByAccountId}\n" +
                "primer: ${message.primer}\n" +
                "return_status: ${message.returnStatus}\n" +
                "message:\n ${convertTypeOfChatMessageToErrorString(message.message, " ")}\n"
    }

    //Types
    //not going to notify the user when admin changes
    //1) receive NEW_ADMIN_PROMOTED message from server
    // store message, update the old admin and the new admin send back update to shared view model
    //2) send NEW_ADMIN_PROMOTED message to server and handle response
    // generate message, update self and the new admin and store it then send back to shared view model
    //3) receive DIFFERENT_USER_LEFT_CHAT with an account_id != ""
    // not a separate message so need to just update new admin and send back the update to the shared view model
    //call a version of handleAdminChanged
    /** transactionWrapper requires OtherUsersDatabase to be inside the transaction **/
    suspend fun handleNewAdminPromoted(
        newAdminAccountOID: String,
        message: ChatMessageToClientMessage.ChatMessageToClient,
        previousAdminAccountOID: String,
        transactionWrapper: TransactionWrapper,
    ) {

        transactionWrapper.runTransaction {

            //NOTE: the previous admin 'user last active time' will be updated as well as the 'chat room last active time'
            val messageEntity = storeChatMessageInChatRoom(message, this, false)

            if (previousAdminAccountOID == newAdminAccountOID) {
                //This is possible if the updateChatRoom downloads the info WHILE the chat stream is initializing
                return@runTransaction
            }

            if (previousAdminAccountOID != LoginFunctions.currentAccountOID
                && newAdminAccountOID != LoginFunctions.currentAccountOID
            ) { //if the current user did not change account states, simply store the message

                chatRoomsDataSource.updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.timestampStored
                )
            }

            //NOTE: the previous admin will need to have its 'user active time' updated
            if (previousAdminAccountOID == LoginFunctions.currentAccountOID) { //if previous admin was this user

                chatRoomsDataSource.updateAccountStateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM,
                    message.timestampStored
                )
            } else { //if previous admin was not this user

                otherUsersDataSource.updateUserAccountStateAndActiveTime(
                    previousAdminAccountOID,
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM,
                    message.timestampStored,
                    this
                )
            }

            //NOTE: the previous admin will not need to have its 'user active time' updated
            if (newAdminAccountOID == LoginFunctions.currentAccountOID) { //if new admin is this user

                chatRoomsDataSource.updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                    message.timestampStored
                )
            } else { //if new admin is not this user

                otherUsersDataSource.updateUserAccountState(
                    newAdminAccountOID,
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                    this
                )
            }

            //if error occurred (still want times updated above)
            if (messageEntity.messageType != -1) {
                this.runAfterTransaction {
                    notifySubscriber(messageEntity)
                }
            }

        }

    }

    //handles USER_KICKED_FROM_CHAT_ROOM and MessageBodyCase.USER_BANNED_MESSAGE, also returns from runRemoveFromChatRoom()
    /** transactionWrapper requires OtherUsersDatabase and MessagesDatabase to be locked **/
    suspend fun handleKickBanMessage(
        message: ChatMessageToClientMessage.ChatMessageToClient,
        transactionWrapper: TransactionWrapper,
        removedAccountOID: String,
    ) {
        transactionWrapper.runTransaction {

            val messageEntity = storeChatMessageInChatRoom(message, this, false)

            updateUserAndChatRoomActivityTimes(message, this)

            if (removedAccountOID == LoginFunctions.currentAccountOID) { //if this user is the account being removed

                //This could happen if the user left and joined on a different device. Then
                // had to download the messages of left->join on chat stream initialization for
                // the original device. It should be relatively rare.
                if (!message.message.standardMessageInfo.doNotUpdateUserState) {
                    removeChatRoomFromDatabase(
                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                        this
                    )
                }

                if (messageEntity.messageType != -1) {
                    this.runAfterTransaction {
                        notifySubscriber(
                            messageEntity,
                            null,
                            message.message.standardMessageInfo.doNotUpdateUserState
                        )
                    }
                }

            } else { //if a different account is being removed

                val accountState =
                    if (message.message.messageSpecifics.messageBodyCase ==
                        MessageBodyCase.USER_KICKED_MESSAGE
                    ) {
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM
                    } else {
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED
                    }

                val otherUser = otherUserLeavesChatRoom(
                    otherUsersDataSource,
                    removedAccountOID,
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    accountState,
                    this,
                    deleteFileInterface,
                    errorHandling
                )

                if (messageEntity.messageType != -1) {
                    otherUser?.let {
                        this.runAfterTransaction {
                            notifySubscriber(
                                messageEntity,
                                otherUser
                            )
                        }
                    }
                }
            }
        }
    }

    /** transactionWrapper requires MessagesDatabase to be locked **/
    private suspend fun storeChatMessageInChatRoom(
        message: ChatMessageToClientMessage.ChatMessageToClient,
        transactionWrapper: TransactionWrapper,
        setObservedTime: Boolean,
        pictureFilePath: String = "",
    ): MessagesDataEntity {

        var messageEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            val activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo? =
                when (message.message.messageSpecifics.messageBodyCase) {
                    MessageBodyCase.TEXT_MESSAGE -> {
                        message.message.messageSpecifics.textMessage.activeMessageInfo
                    }
                    MessageBodyCase.PICTURE_MESSAGE -> {
                        message.message.messageSpecifics.pictureMessage.activeMessageInfo
                    }
                    MessageBodyCase.LOCATION_MESSAGE -> {
                        message.message.messageSpecifics.locationMessage.activeMessageInfo
                    }
                    MessageBodyCase.MIME_TYPE_MESSAGE -> {
                        message.message.messageSpecifics.mimeTypeMessage.activeMessageInfo
                    }
                    MessageBodyCase.INVITE_MESSAGE -> {
                        message.message.messageSpecifics.inviteMessage.activeMessageInfo
                    }
                    else -> {
                        null
                    }
                }

            //generate thumbnail file path and save to file
            //just because isReply is set to true does not mean the reply was sent back with
            // the message, amountOfMessage must also be COMPLETE_MESSAGE_INFO
            val replyThumbnailFilePath =
                if (activeMessageInfo != null && activeMessageInfo.hasReplyInfo()) {

                    when (activeMessageInfo.replyInfo.replySpecifics.replyBodyCase) {
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> {
                            generateAndSaveMessageReplyFile(
                                activeMessageInfo.replyInfo.replySpecifics.pictureReply.thumbnailInBytes,
                                activeMessageInfo.replyInfo.replySpecifics.pictureReply.thumbnailFileSize,
                                message.messageUuid,
                                deleteFileInterface,
                                errorHandling,
                                this
                            )
                        }
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {
                            generateAndSaveMessageReplyFile(
                                activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailInBytes,
                                activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailFileSize,
                                message.messageUuid,
                                deleteFileInterface,
                                errorHandling,
                                this
                            )
                        }
                        else -> {
                            ""
                        }
                    }
                } else {
                    ""
                }

            messageEntity =
                convertChatMessageToMessageDataEntity(
                    message,
                    pictureFilePath,
                    replyThumbnailFilePath,
                    errorStore = errorHandling,
                    ioDispatcher = ioDispatcher
                )

            //this should have been already checked for duplicates
            //if error occurred (still want times updated on return)
            if (messageEntity?.messageType != -1) {

                if (setObservedTime)
                    messageEntity?.timeUserLastObservedMessage = getCurrentTimestampInMillis()

                messagesDataSource.insertMessage(messageEntity!!)
            }
        }

        return messageEntity!!
    }

    suspend fun removeAllMessagesToUpdateFromQueue(chatRoomId: String) {
        requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
            messageQueue.removeAll { messageInfo ->
                //If requested messages are allowed to be removed here, the system will lose its ability to 'know' when
                // more messages can be requested. It will immediately try to request more and get back
                // CURRENTLY_PROCESSING_UPDATE_REQUEST.
                !messageInfo.requested && messageInfo.chatRoomId == chatRoomId
            }
        }
    }

    /** transactionWrapper requires OtherUsersDatabase and MessagesDatabase to be locked **/
    suspend fun removeChatRoomFromDatabase(
        chatRoomId: String,
        transactionWrapper: TransactionWrapper,
    ) {
        transactionWrapper.runTransaction {

            //remove any potential messages from the queue to be updated if user leaves chat room
            removeAllMessagesToUpdateFromQueue(chatRoomId)

            val otherUsersInsideChatRoom =
                otherUsersDataSource.getAllOtherUsersForChatRoom(chatRoomId)

            //remove all members inside chat room
            for (otherUser in otherUsersInsideChatRoom) {
                userLeftChatRoomRemoveReferences(otherUser, chatRoomId)
            }

            messagesDataSource.removeAllMessagesForChatRoom(
                chatRoomId,
                { picturePath ->
                    deleteFileInterface.sendFileToWorkManager(
                        picturePath
                    )
                },
                { gifURL ->
                    deleteGif(
                        mimeTypeDataSource,
                        gifURL,
                        deleteFileInterface,
                        errorHandling
                    )
                },
                true,
                this
            )

            val qrCodePath = chatRoomsDataSource.getQRCodePath(chatRoomId)
            if (qrCodePath != GlobalValues.server_imported_values.qrCodeDefault) {
                deleteFileInterface.sendFileToWorkManager(
                    qrCodePath
                )
            }

            //delete chat room itself
            chatRoomsDataSource.deleteChatRoom(chatRoomId)
        }
    }

    //will remove chat room reference from other user chatRoomObjects and remove or modify user entirely if required
    //NOTE: should not be used when DIFFERENT_USER_LEAVES_CHAT_ROOM, because the user simply changes states
    private suspend fun userLeftChatRoomRemoveReferences(
        otherUser: OtherUsersDataEntity?, chatRoomId: String,
    ) {

        if (otherUser != null) { //if other user exists in other users database

            val chatRoomInfoMap =
                convertChatRoomObjectsStringToMap(otherUser.chatRoomObjects)

            val chatRoomInfo = chatRoomInfoMap[chatRoomId]

            if (chatRoomInfo != null) { //if chat room info exists

                chatRoomInfoMap.remove(chatRoomId)

                removeUserInfo(otherUser, chatRoomInfoMap)

            }
            //else; If chat room info does not exist
            //This is possible if the updateChatRoom downloads the info WHILE the chat stream is initializing

        }
        //else; If other user does not exist in other users database, however a message was sent that they were removed
        //This is possible if the updateChatRoom downloads the info WHILE the chat stream is initializing

    }

    //will remove the user info if no references remain, or will set partial info if necessary
    //or if full reference are still required
    private suspend fun removeUserInfo(
        otherUser: OtherUsersDataEntity,
        chatRoomInfoMap: MutableMap<String, OtherUserChatRoomInfo>,
    ) {

        otherUser.chatRoomObjects =
            convertChatRoomObjectsMapToString(chatRoomInfoMap)

        if (otherUser.chatRoomObjects == "" && otherUser.objectsRequiringInfo == "") { //if no references for the other user remain

            deleteOtherUser(
                otherUsersDataSource,
                otherUser,
                deleteFileInterface,
                errorHandling
            )

        } else { //if references for the other user remain

            removePicturesIfOnlyPartialInfoRequired(
                otherUser,
                chatRoomInfoMap,
                deleteFileInterface,
                errorHandling
            )

            //NOTE: this needs to be updated no matter what because something will have been added or removed
            // probably from chat rooms or other object list
            otherUsersDataSource.upsertSingleOtherUser(otherUser)
        }
    }

    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    //This function can take message types.
    // CHAT_ROOM_NAME_UPDATED_MESSAGE
    // CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
    // NEW_PINNED_LOCATION_MESSAGE
    suspend fun handleUpdateChatRoomInfo(
        message: ChatMessageToClientMessage.ChatMessageToClient,
        transactionWrapper: TransactionWrapper,
    ) {
        transactionWrapper.runTransaction {

            val messageEntity = storeChatMessageInChatRoom(message, this, false)

            if (message.sentByAccountId == LoginFunctions.currentAccountOID) { //if this was sent by the current user
                //update the value as well as the current user's last update time
                chatRoomsDataSource.updateAccountInfoUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.message.messageSpecifics,
                    true,
                    message.timestampStored
                )
            } else { //if this was not sent by the current user
                val otherUser = otherUsersDataSource.getSingleOtherUser(message.sentByAccountId)

                if (otherUser != null) { //if other user exists
                    otherUsersDataSource.setUserLastActiveTimeInChatRoom(
                        message.sentByAccountId,
                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                        message.timestampStored,
                        this
                    )
                }
                //else; This is possible if the updateChatRoom downloads the info WHILE the chat stream is initializing

                //update the value without the current user's last update time
                chatRoomsDataSource.updateAccountInfoUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.message.messageSpecifics,
                    false,
                    message.timestampStored
                )
            }

            //If error occurred (still want times updated above)
            if (messageEntity.messageType != -1) {
                this.runAfterTransaction {
                    notifySubscriber(messageEntity)
                }
            }
        }
    }

    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    private suspend fun updateUserAndChatRoomActivityTimes(
        message: ChatMessageToClientMessage.ChatMessageToClient,
        transactionWrapper: TransactionWrapper,
    ) {

        transactionWrapper.runTransaction {
            if (message.sentByAccountId == LoginFunctions.currentAccountOID) { //if sent by current user

                chatRoomsDataSource.updateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.timestampStored
                )

            } else { //message was sent by another user

                val otherUser = otherUsersDataSource.getSingleOtherUser(message.sentByAccountId)

                if (otherUser != null) { //if other user exists

                    otherUsersDataSource.setUserLastActiveTimeInChatRoom(
                        message.sentByAccountId,
                        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                        message.timestampStored,
                        this
                    )

                }
                //else { //if other user does not exist
                //NOTE: this function is called when a DIFFERENT_USER_JOINED_MESSAGE message is received, and so this could be possible
                //}

                chatRoomsDataSource.updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
                    message.timestampStored
                )
            }
        }
    }

    //will update the gif reference count or create the new data entity for the gif if one does not exist
    /** transactionWrapper requires Messages to be locked **/
    private suspend fun addMimeType(
        mimeTypeUrl: String,
        mimeType: String,
        mimeTypeFilePath: String = "",
        mimeTypeWidth: Int = 0,
        mimeTypeHeight: Int = 0,
        transactionWrapper: TransactionWrapper,
    ) {

        transactionWrapper.runTransaction {
            //NOTE: This is not a transaction for the message type database because it is all single commands, ALTHOUGH
            // it does share a reference inside of the messages dao data source. The worst that should be able to happen
            // is that it is added twice. However it is mostly
            val mimeTypeDatabaseEntry = mimeTypeDataSource.getMimeType(mimeTypeUrl)

            if (mimeTypeDatabaseEntry != null) { //gif exists inside database already

                val storedMimeTypeFilePath = mimeTypeDatabaseEntry.mimeTypeFilePath

                if (mimeTypeFilePath == "" || storedMimeTypeFilePath == mimeTypeFilePath) { //if file path was not passed or is the same as stored file path

                    //NOTE: in testing the path was the same no matter what, however for safety
                    // will save the absolute path each time
                    mimeTypeDataSource.incrementReferenceCountNoFilePath(mimeTypeUrl)

                } else { //if file path was passed and is different than the stored value

                    if (storedMimeTypeFilePath != "" && File(storedMimeTypeFilePath).exists()) { //if the gif file path is 'valid' and different than the passed gif file path
                        //remove stored gif path
                        deleteFileInterface.sendFileToWorkManager(
                            storedMimeTypeFilePath
                        )

                        val errorString =
                            "Mime type path was different than the stored value or was not passed.\nPath: $mimeTypeFilePath\nStored Path: $storedMimeTypeFilePath"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )
                    }

                    //NOTE: this can happen if the cache was cleared or if the gif was received from the server and has not
                    // been downloaded yet so the stored path name is still ""
                    mimeTypeDataSource.incrementReferenceCountUpdateFilePath(
                        mimeTypeUrl,
                        mimeTypeFilePath,
                        mimeTypeWidth,
                        mimeTypeHeight
                    )
                }
            } else { //gif does not exist inside database yet

                val currentTimestamp = getCurrentTimestampInMillis()

                mimeTypeDataSource.insertMimeType(
                    MimeTypeDataEntity(
                        mimeTypeUrl,
                        mimeTypeFilePath,
                        currentTimestamp,
                        1,
                        mimeTypeHeight,
                        mimeTypeWidth,
                        mimeType,
                        currentTimestamp
                    )
                )
            }
        }
    }

    private fun enqueueWorker(workRequest: OneTimeWorkRequest) {
        WorkManager.getInstance(applicationContext).beginUniqueWork(
            CHAT_STREAM_OBJECT_UNIQUE_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        ).enqueue()
    }

    fun workerFunctionSetUpRefresh(sentFrom: String) {
        CoroutineScope(ioDispatcher).launch {
            requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {

                cancelAllChatStreamObjectsWork(applicationContext)
//                restartChatStreamHandler.removeCallbacksAndMessages(
//                    chatStreamToken
//                )

                //note that there is a max number of times that the chat stream refreshes before it restarts
                // that is because with 'long running streams' the grpc developers recommend to not force
                // them to run for extremely long periods of time
                if (
                    serverAddressPort == GlobalValues.getCurrentManagedChannelAddressPort()
                    && numberTimesStreamRefreshed < GlobalValues.server_imported_values.chatRoomStreamNumberTimesToRefreshBeforeRestart
                ) {
                    refreshChatStream()
                    numberTimesStreamRefreshed++
                } else {
                    startChatMessageStream()
                }
            }
            Log.i(
                "startBiDiTest",
                "starting chat stream from $sentFrom"
            )
        }
    }

    private fun setUpRefreshForStreamHandler(
        approximateTimeStreamExpiresMs: Long,
        sentFrom: String
    ) {

        Log.i(
            "runLoginBeforeToke",
            "setUpRefreshForStreamHandler() approximateTimeStreamExpiresMs: $approximateTimeStreamExpiresMs sentFrom: $sentFrom"
        )

        val refreshStreamHandlerWorkRequest =
            OneTimeWorkRequestBuilder<RefreshChatStreamObjectWorker>()
                .setInputData(
                    workDataOf(
                        WORKER_PARAM_SENT_FROM_KEY to sentFrom
                    )
                )
                //WorkManager can have a delay in running so this is set to .81 instead of a values above .9. LoginFunctions
                // does something with the variable LoginFunctions.TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER. This is
                // slightly different so the device is not simultaneously attempting to refresh both.
                .setInitialDelay(
                    (approximateTimeStreamExpiresMs * .81).toLong(),
                    TimeUnit.MILLISECONDS
                )
                .build()

        enqueueWorker(refreshStreamHandlerWorkRequest)

        /*val timeBeforeRefresh: Long = (approximateTimeStreamExpiresMs * .8).toLong()
        restartChatStreamHandler.postAtTime(
            {
                CoroutineScope(ioDispatcher).launch {
                    requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {

                        restartChatStreamHandler.removeCallbacksAndMessages(
                            chatStreamToken
                        )

                        //note that there is a max number of times that the chat stream refreshes before it restarts
                        // that is because with 'long running streams' the grpc developers recommend to not force
                        // them to run for extremely long periods of time
                        if (
                            serverAddressPort == GlobalValues.getCurrentManagedChannelAddressPort()
                            && numberTimesStreamRefreshed < GlobalValues.server_imported_values.chatRoomStreamNumberTimesToRefreshBeforeRestart
                        ) {
                            refreshChatStream()
                            numberTimesStreamRefreshed++
                        } else {
                            startChatMessageStream()
                        }
                    }
                }
                Log.i(
                    "startBiDiTest",
                    "starting chat stream from $sentFrom"
                )
            },
            chatStreamToken,
            SystemClock.uptimeMillis() + timeBeforeRefresh
        )*/
    }

    fun workerFunctionRetryAfterDelay() {
        CoroutineScope(ioDispatcher).launch {
            //if network is still down
            val networkState =
                ServiceLocator.deviceIdleOrConnectionDown.deviceIdleOrConnectionDown(
                    applicationContext,
                )

            Log.i("testingDoze", "ChatStreamObject networkState: $networkState")

            if (networkState == DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE) { //network is up
                startChatMessageStream()
            } else { //network is down

                if (networkState == DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_UNAVAILABLE) {
                    gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.CONNECTION_ERROR)
                }

                Log.i(
                    "testingDoze",
                    "ChatStreamObject is detecting device in idle or connection error"
                )
                retryConnectionAfterDelay()
            }
        }
        Log.i(
            "startBiDiTest",
            "starting chat stream from Handler"
        )
    }

    private fun retryConnectionAfterDelay() {
        Log.i("runLoginBeforeToke", "retryConnectionAfterDelay()")

        val retryAfterConnectionErrorWorkRequest =
            OneTimeWorkRequestBuilder<RunChatStreamObjectAfterDelayWorker>()
                //There may be times where setConstraints will already be fulfilled even though
                // the internet is down. The initial delay is here to prevent it from spamming.
                .setInitialDelay(
                    POLLING_DELAY_BETWEEN_CHAT_STREAM_CONNECTION_ATTEMPTS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

        enqueueWorker(retryAfterConnectionErrorWorkRequest)

        /*// retry connection after a delay
        restartChatStreamHandler.postAtTime(
            {
                CoroutineScope(ioDispatcher).launch {
                    //if network is still down
                    val networkState =
                        ServiceLocator.deviceIdleOrConnectionDown.deviceIdleOrConnectionDown(
                            applicationContext
                        )

                    Log.i("testingDoze", "ChatStreamObject networkState: $networkState")

                    if (networkState == DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE) { //network is up
                        startChatMessageStream()
                    } else { //network is down

                        if (networkState == DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_UNAVAILABLE) {
                            gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.CONNECTION_ERROR)
                        }

                        Log.i(
                            "testingDoze",
                            "ChatStreamObject is detecting device in idle or connection error"
                        )
                        retryConnectionAfterDelay()
                    }
                }
                Log.i(
                    "startBiDiTest",
                    "starting chat stream from Handler"
                )
            },
            chatStreamToken,
            SystemClock.uptimeMillis() + GlobalValues.POLLING_DELAY_BETWEEN_CHAT_STREAM_CONNECTION_ATTEMPTS //NOTE: This time MUST be sorter than the Light Doze maintenance window (I saw somewhere that it was ~10 seconds long)
        )*/
    }

    private fun generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(messageUUID: String): MessagesDataEntity {
        //this will be expected to have the (message type == 0) if the message
        // was not stored when the message was not found
        val messageEntity = MessagesDataEntity()
        messageEntity.messageUUIDPrimaryKey = messageUUID
        messageEntity.messageType = 0
        return messageEntity
    }

    //in the response to RequestFullMessageInfo, if the message UUID was invalid or not found, this
    // will be used to remove the message from the database and to generate a message that can be used
    // to signal to application view model to clear its copy as well
    private suspend fun handleMessageNotStoredOrDeletedForRequestFullMessageInfo(messageUUID: String): MessagesDataEntity {
        //remove message along with any associated files
        messagesDataSource.removeSingleMessageByUUID(
            messageUUID,
            { picturePath ->
                deleteFileInterface.sendFileToWorkManager(
                    picturePath
                )
            },
            { gifURL ->
                deleteGif(
                    mimeTypeDataSource,
                    gifURL,
                    deleteFileInterface,
                    errorHandling
                )
            }
        )

        return generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(messageUUID)
    }

    private suspend fun handleMessageListUUID(
        messageUUID: String,
        returnStatus: StatusEnum.ReturnStatus,
        message: ChatMessageToClientMessage.ChatMessageToClient?
    ) {


        val extractedMessageInfo = removeAndReturnMessageByUUID(messageUUID)

        val returnMessageUpdateRequest =
            if (extractedMessageInfo == null) { //message no longer exists inside of messageQueue
                //will remove from view model
                ReturnMessageUpdateRequestResponseDataHolder(
                    generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(
                        messageUUID
                    ),
                    TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO
                )
            } else { //message still exists inside of messageQueue

                //StatusEnum.ReturnStatus.VALUE_NOT_SET will be set if the message uuid
                // was not found or was invalid.
                //StatusEnum.ReturnStatus.UNKNOWN will be set if it is unknown what to do
                // with the result. For example, too many uuid were sent to the server, any
                // number over GlobalValues.maxNumberMessagesUserCanRequest will be returned
                // with UNKNOWN.
                val storedMessageEntity =
                    if (returnStatus == StatusEnum.ReturnStatus.SUCCESS && message != null) {
                        updateMessage(message)
                    } else if (returnStatus == StatusEnum.ReturnStatus.UNKNOWN) {
                        //Return stored message to client (no updates happened) it can remove the message from the view model queue
                        // and request again if still necessary.
                        generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(
                            messageUUID
                        )
                    } else {
                        handleMessageNotStoredOrDeletedForRequestFullMessageInfo(
                            messageUUID
                        )
                    }

                ReturnMessageUpdateRequestResponseDataHolder(
                    storedMessageEntity,
                    extractedMessageInfo.amountOfMessageRequested
                )
            }

        receivedMessageUpdateRequestResponse(
            returnMessageUpdateRequest
        )
    }

    private suspend fun chatResponseOnNextHandler(
        messageHolder: ChatMessageStream.ChatToClientResponse,
        streamObserverInstance: StreamObserver<ChatMessageStream.ChatToClientResponse>?,
    ) = withContext(ioDispatcher) {

        Log.i(
            "startBiDiTest",
            "chat stream message type ${messageHolder.serverResponseCase} received"
        )

        when (messageHolder.serverResponseCase) {
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.INITIAL_CONNECTION_PRIMER_RESPONSE -> {

                chatStreamResponseObserver?.let { currentChatStreamObserver ->
                    if (currentChatStreamObserver == streamObserverInstance) {
                        primaryChatStreamObserverState = ChatStreamObserverState.CONNECTED
                    }
                }

                numberTimesStreamRefreshed = 0

                Log.i(
                    "startBiDiTest",
                    "initialConnectionPrimerResponse.timeUntilChatStreamExpiresInMillis: ${messageHolder.initialConnectionPrimerResponse.timeUntilChatStreamExpiresInMillis}ms"
                )

                setUpRefreshForStreamHandler(
                    messageHolder.initialConnectionPrimerResponse.timeUntilChatStreamExpiresInMillis,
                    "INITIAL_CONNECTION_PRIMER_RESPONSE"
                )

                val unsentMessages =
                    messagesDataSource.retrieveUnsentMessages()

                //send all unsent messages to server
                for (m in unsentMessages) {
                    sendMessage(
                        m,
                        byteArrayOf(),
                        CHAT_MESSAGE_STREAM_INIT,
                        messageAlreadyStoredInDatabase = true
                    ) {}
                }

                var chatMessageToServer: ChatMessageStream.ChatToServerRequest? = null

                //order of mutex being locked when nested is important here
                requestResponseErrorMutex.singleMutexWithLock(messageQueueMutexKey) {
                    if (messageQueue.isNotEmpty()) {

                        Log.i(
                            "startBiDiTest",
                            "requesting some messages from messageQueue, still ${messageQueue.size} messages to request updates for"
                        )

                        currentlyRequestingMessageUpdates = true

                        setAllMessagesInQueueToNotRequested()

                        chatMessageToServer = buildChatMessageToServer()
                    }
                }

                chatMessageToServer?.let {
                    requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
                        chatStreamRequestObserver?.onNext(it)
                    }
                }
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.REQUEST_FULL_MESSAGE_INFO_RESPONSE -> {

                /** Implementation Notes
                 * 1) Any RequestStatus besides INTERMEDIATE_MESSAGE_LIST & FINAL_MESSAGE_LIST will be the final
                 * message the server sends (the server stores any relevant errors).
                 * 2) If a message is not found on the server OR the uuid is invalid a returnStatus of VALUE_NOT_SET will
                 * be set to the message along with the UUID. If the message was successfully found and extracted a
                 * value of SUCCESS will be set to returnStatus.
                 **/

                Log.i(
                    "startBiDiTest",
                    "request requestStatus ${messageHolder.requestFullMessageInfoResponse.requestStatus} received"
                )

                when (messageHolder.requestFullMessageInfoResponse.requestStatus) {
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.INTERMEDIATE_MESSAGE_LIST,
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.FINAL_MESSAGE_LIST,
                    -> {

                        if (messageHolder.requestFullMessageInfoResponse.hasFullMessages()) {
                            for (messageFromList in messageHolder.requestFullMessageInfoResponse.fullMessages.fullMessageListList) {

                                Log.i(
                                    "startBiDiTest",
                                    "received update to messageType: ${messageFromList.message.messageSpecifics.messageBodyCase}"
                                )

                                handleMessageListUUID(
                                    messageFromList.messageUuid,
                                    messageFromList.returnStatus,
                                    messageFromList
                                )
                            }
                        } else { //does not have full messages (should not happen)

                            val errorString =
                                "Returned requestStatus should never be returned with errorMessages list.\n" +
                                        "requestStatus, ${messageHolder.requestFullMessageInfoResponse.requestStatus}\n" +
                                        "requestFullMessageInfoResponse, ${messageHolder.requestFullMessageInfoResponse}\n" +
                                        "messageQueue, $messageQueue\n"

                            chatStreamObjectErrorHelper(
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                Thread.currentThread().stackTrace[2].fileName
                            )

                            for (messageUUID in messageHolder.requestFullMessageInfoResponse.errorMessages.messageUuidListList) {
                                //VALUE_NOT_SET will set the message to deleted
                                handleMessageListUUID(
                                    messageUUID,
                                    StatusEnum.ReturnStatus.VALUE_NOT_SET,
                                    null
                                )
                            }
                        }

                        requestMessagesIfNoneRequested()
                    }
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.CURRENTLY_PROCESSING_UPDATE_REQUEST -> {
                        val messageUUIDs = mutableListOf<String>()
                        if (messageHolder.requestFullMessageInfoResponse.hasFullMessages()) {
                            for (messageFromList in messageHolder.requestFullMessageInfoResponse.fullMessages.fullMessageListList) {
                                messageUUIDs.add(messageFromList.messageUuid)
                            }
                        } else {
                            for (messageUUID in messageHolder.requestFullMessageInfoResponse.errorMessages.messageUuidListList) {
                                messageUUIDs.add(messageUUID)
                            }
                        }

                        val errorString =
                            "A return value of CURRENTLY_PROCESSING_UPDATE_REQUEST was returned by the server.\n" +
                                    "This should mean that the client requested a batch of messages before the previous batch from the same stream had already finished being returned.\n" +
                                    "response: \n${messageHolder.requestFullMessageInfoResponse}\n" +
                                    "response: \n${messageHolder.requestFullMessageInfoResponse}\n" +
                                    "messageQueue: \n${messageQueue}"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )

                        for (messageUUID in messageUUIDs) {
                            removeAndReturnMessageByUUID(messageUUID)

                            //return stored message to client (no updates happened) it can remove the message from the view model queue
                            // and request again if still necessary
                            receivedMessageUpdateRequestResponse(
                                ReturnMessageUpdateRequestResponseDataHolder(
                                    generateRequestFullMessageInfoRemoveFromViewModelMessagesDataEntity(
                                        messageUUID
                                    ),
                                    TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO
                                )
                            )
                        }

                        requestMessagesIfNoneRequested()
                    }
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.DATABASE_DOWN -> {

                        //This could simply mean that an operation_exception occurred on the server and the stream is still
                        // connected. So setting all messages to NOT requested

                        requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
                            currentlyRequestingMessageUpdates = false
                            setAllMessagesInQueueToNotRequested()
                        }

                        //I don't actually want to clear the queue here
                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.SERVER_DOWN)
                    }
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.USER_NOT_A_MEMBER_OF_CHAT_ROOM -> {

                        requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
                            //need to remove the messages from the queue, then need to send back each individual
                            // message data entity to the view model in order for it to remove the messages
                            if (messageQueue.isNotEmpty()) {

                                val messagesList =
                                    mutableListOf<Pair<String, TypeOfChatMessageOuterClass.AmountOfMessage>>()
                                val messagesToDelete =
                                    mutableListOf<RequestedMessageInfo>()

                                for (messageInfo in messageQueue) {
                                    if (messageInfo.chatRoomId == messageHolder.requestFullMessageInfoResponse.chatRoomId) {
                                        messagesToDelete.add(messageInfo)
                                        messagesList.add(
                                            Pair(
                                                messageInfo.messageUUID,
                                                messageInfo.amountOfMessageRequested
                                            )
                                        )
                                    }
                                }

                                messageQueue.removeAll(messagesToDelete)

                                //NOTE: Running this block AFTER the messageQueue.removeIf because a mutex is locked preventing
                                // the messageQueue from doing anything else during that time.
                                //if message info was found inside the list
                                for (message in messagesList) {
                                    receivedMessageUpdateRequestResponse(
                                        ReturnMessageUpdateRequestResponseDataHolder(
                                            handleMessageNotStoredOrDeletedForRequestFullMessageInfo(
                                                message.first
                                            ),
                                            message.second
                                        )
                                    )
                                }

                            } else {
                                val errorString =
                                    "Error a message being returned should always be in the queue and the message queue was empty.\n" +
                                            "chatRoomId, ${messageHolder.requestFullMessageInfoResponse.chatRoomId}\n" +
                                            "messageQueue, $messageQueue\n" +
                                            "messageQueue.size, ${messageQueue.size}\n"

                                chatStreamObjectErrorHelper(
                                    errorString,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    Thread.currentThread().stackTrace[2].fileName
                                )

                                //can continue here
                            }
                        }

                    }
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.LG_SERVER_ERROR -> {

                        //the error was stored on the server
                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                    }
                    ChatMessageStream.RequestFullMessageInfoResponse.RequestStatus.UNRECOGNIZED,
                    null,
                    -> {
                        var errorString =
                            "Invalid RequestStatus was returned for a RequestFullMessageInfoResponse.\n" +
                                    "response: \n${messageHolder.requestFullMessageInfoResponse}"

                        errorString += "messageQueue [\n"

                        //This may not be set
                        requestResponseErrorMutex.singleMutexWithLock(chatStreamRequestMutexKey) {
                            for (messageInfo in messageQueue) {
                                errorString += "   request.chatRoomId: ${messageInfo.chatRoomId}\n"
                                errorString += "   request.amountOfMessagesToRequest: ${messageInfo.amountOfMessageRequested}\n"
                                errorString += "   request.messageId: ${messageInfo.messageUUID}\n"
                                errorString += "   request.messageId: ${messageInfo.requested},\n"
                            }
                        }

                        errorString += "]\n"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )

                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                    }
                }
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.REFRESH_CHAT_STREAM_RESPONSE -> {
                setUpRefreshForStreamHandler(
                    messageHolder.refreshChatStreamResponse.timeUntilChatStreamExpiresInMillis,
                    "REFRESH_CHAT_STREAM_RESPONSE"
                )
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.INITIAL_CONNECTION_MESSAGES_RESPONSE -> {

                Log.i(
                    "startBiDiTest",
                    "messageHolder.initialConnectionPrimerMessages: ${messageHolder.initialConnectionMessagesResponse.toByteString()}"
                )

                for (message in messageHolder.initialConnectionMessagesResponse.messagesListList) {
                    receiveMessage(message, calledFromJoinChatRoom = false)
                }
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.INITIAL_CONNECTION_MESSAGES_COMPLETE_RESPONSE -> {

                Log.i(
                    "startBiDiTest",
                    "messageHolder.initialConnectionMessagesCompleteResponse received"
                )

                chatStreamInitialDownloadsCompleted()
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.RETURN_NEW_CHAT_MESSAGE -> {
                for (message in messageHolder.returnNewChatMessage.messagesListList) {
                    receiveMessage(message, calledFromJoinChatRoom = false)
                }
            }
            ChatMessageStream.ChatToClientResponse.ServerResponseCase.SERVERRESPONSE_NOT_SET,
            null,
            -> {

                val errorString =
                    "Server response returned an unknown value ${messageHolder.serverResponseCase}."

                chatStreamObjectErrorHelper(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )
            }
        }

        Log.i(
            "startBiDiTest",
            "chat stream message type ${messageHolder.serverResponseCase} completed"
        )
    }

    private suspend fun chatResponseOnErrorHandler(
        errorValue: GrpcAndroidSideErrorsEnum,
        message: String,
        streamObserverInstance: StreamObserver<ChatMessageStream.ChatToClientResponse>?,
    ) {
        withContext(ioDispatcher) {
            requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {

                Log.i("startBiDiTest", "chatResponseOnErrorHandler() errorValue: $errorValue")

                //this must be done because the ChatStreamObject will outlast login and logouts and so
                // the state must always be kept
                //NOTE: it is expected before this function is called that streamObserverInstance == chatStreamResponseObserver
                // is found to be true inside of the response mutex so that this is guaranteed to be the relevant response
                // observer
                chatStreamResponseObserver = null
                primaryChatStreamObserverState = ChatStreamObserverState.NOT_CONNECTED
                //restartChatStreamHandler.removeCallbacksAndMessages(chatStreamToken)
                cancelAllChatStreamObjectsWork(applicationContext)

                when (errorValue) {
                    GrpcAndroidSideErrorsEnum.CONNECTION_ERROR,
                    GrpcAndroidSideErrorsEnum.SERVER_DOWN,
                    -> {

                        //GrpcAndroidSideErrorsEnum.SERVER_DOWN should mean that all servers are down, CONNECTION_ERROR
                        // means the clients internet connection is down or the device entered Light Doze or Deep Doze

                        // retry connection after a delay
                        retryConnectionAfterDelay()
                    }
                    GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION,
                    -> {
                        val errorString =
                            "Error chatResponseErrorHandler got an unknown error\n$message\n"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )

                        chatStreamResponseObserver?.let { classStreamObserver ->
                            if (streamObserverInstance == classStreamObserver) { //if this instance is still relevant
                                chatStreamResponseObserver = null
                                primaryChatStreamObserverState =
                                    ChatStreamObserverState.NOT_CONNECTED
                            }
                        }

                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                    }
                }
            }
        }
    }

    private suspend fun chatResponseOnCompletedHandler(
        streamObserverInstance: StreamObserver<ChatMessageStream.ChatToClientResponse>?,
        finalStatus: Status?,
        finalMetaData: Metadata?,
    ) {
        withContext(ioDispatcher) {
            /** In order to make it here, onCompleted (called Finish() in C++) was called for this
             *  chat stream instance on the server and another chat stream instance has NOT
             *  yet been started. **/

            //this must be done because the ChatStreamObject will outlast login and logouts and so
            // the state must always be kept
            //NOTE: it is expected before this function is called that streamObserverInstance == chatStreamResponseObserver
            // is found to be true inside of the response mutex so that this is guaranteed to be the relevant response
            // observer
            chatStreamResponseObserver = null
            primaryChatStreamObserverState = ChatStreamObserverState.NOT_CONNECTED
            //restartChatStreamHandler.removeCallbacksAndMessages(chatStreamToken)
            cancelAllChatStreamObjectsWork(applicationContext)

            //this should always be true in onCompleted()
            if (finalStatus?.isOk != true) {

                val errorString =
                    "onCompleted received a finalStatus that was NOT true, the server is never expected to send back any other returnStatus besides OK.\n" +
                            "finalStatus: $finalStatus\n" +
                            "finalMetaData: $finalMetaData\n"

                chatStreamObjectErrorHelper(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )

                gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                return@withContext
            }

            Log.i(
                "startBiDiTest",
                "chatResponseOnCompletedHandler() finalMetaData: $finalMetaData streamDownReason: ${
                    ChatMessageStream.StreamDownReasons.forNumber(
                        (finalMetaData?.get(GlobalValues.REASON_STREAM_SHUT_DOWN_KEY))?.toInt() ?: -1
                    )
                }"
            )

            //will hold the cancelling installation id if STREAM_CANCELED_BY_ANOTHER_STREAM
            val optionalInfo =
                finalMetaData?.get(GlobalValues.OPTIONAL_INFO_OF_CANCELLING_STREAM)
                    ?: ""

            when (ChatMessageStream.StreamDownReasons.forNumber(
                (finalMetaData?.get(
                    GlobalValues.REASON_STREAM_SHUT_DOWN_KEY
                ))?.toInt() ?: -1
            )) {
                ChatMessageStream.StreamDownReasons.UNKNOWN_STREAM_STOP_REASON -> {
                    //when onCompleted() is called by the chat stream (cancelChatStream() is called) UNKNOWN_STREAM_STOP_REASON will be
                    // returned by the server, HOWEVER the chatStreamResponseReentrantMutex is locked while that is happening and
                    // chatStreamResponseObserver will be set to null, so nothing should actually be able to enter this when this
                    // is canceled manually by the stream

                    //restart stream for unknown cases to avoid chat stream not running
                    startChatMessageStream()

                    val errorString =
                        "Not necessarily an error, this is put inside StreamDownReasons::UNKNOWN_STREAM_STOP_REASON to check" +
                                " if there are any conditions when it is called.\n" +
                                "finalStatus: $finalStatus\n" +
                                "finalMetaData: $finalMetaData\n" +
                                "optionalInfo: $optionalInfo\n"

                    chatStreamObjectErrorHelper(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName
                    )

                }
                ChatMessageStream.StreamDownReasons.RETURN_STATUS_ATTACHED -> {
                    //something happened and a return status was sent back when server was initializing stream

                    val errorReturn =
                        checkApplicationReturnStatusEnum(
                            StatusEnum.ReturnStatus.forNumber(
                                (finalMetaData?.get(
                                    GlobalValues.RETURN_STATUS_KEY
                                ))?.toInt() ?: -1
                            ),
                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS,
                            "~",
                            false
                        )

                    //The server does a proper login inside chat_stream_container_object; initializeObject(); checkLoginToken()
                    // so it can return a variety of parameters
                    //INVALID_PARAMETER_PASSED
                    //LG_ERROR
                    //DATABASE_DOWN
                    when (errorReturn.second) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                            val errorString =
                                "Return status in ChatStreamObserver onCompleted() returned an invalid value" +
                                        " of GrpcFunctionErrorStatusEnum.NO_ERRORS. This value is only used for errors and so this " +
                                        "should never be returned. (It will end the stream)\n" +
                                        "finalStatus: $finalStatus\n" +
                                        "finalMetaData: $finalMetaData\n" +
                                        "optionalInfo: $optionalInfo\n"

                            chatStreamObjectErrorHelper(
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                Thread.currentThread().stackTrace[2].fileName
                            )

                            gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                        }
                        //the below values will be returned to the activity (if relevant)
                        GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
                        GrpcFunctionErrorStatusEnum.SERVER_DOWN,
                        GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE,
                        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                        GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
                        GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY,
                        GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                        GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED,
                        GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED,
                        GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION,
                        -> {

                            if (errorReturn.first != "~") {
                                val errorString =
                                    "Return status in ChatStreamObserver onCompleted() returned an invalid " +
                                            "value of ${errorReturn.second}.\n" +
                                            "finalStatus: $finalStatus\n" +
                                            "finalMetaData: $finalMetaData\n" +
                                            "optionalInfo: $optionalInfo\n"

                                chatStreamObjectErrorHelper(
                                    errorString,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    Thread.currentThread().stackTrace[2].fileName
                                )
                            }

                            gRPCErrorOccurred(errorReturn.second)
                        }
                    }
                }
                ChatMessageStream.StreamDownReasons.STREAM_TIMED_OUT -> {

                    //NOTE: A notable time that this could happen is during Doze. The application could be put to sleep
                    // and the uptimeMillis will not be properly updated causing the Handler to be out of date. Then when
                    // the Doze wakes up for its maintenance window this can receive the time out error.

                    startChatMessageStream()
                }
                ChatMessageStream.StreamDownReasons.STREAM_CANCELED_BY_ANOTHER_STREAM -> {

                    //The stream was cancelled by another stream starting (someone else logged in
                    // or this object restarted itself).

                    //if this was cancelled by a different user, log this user out
                    if (optionalInfo != GlobalValues.installationId) {
                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE)
                    } else {
                        val errorString =
                            "onCompleted() was entered after THIS device (measured by installation id) cancelled the stream. This means" +
                                    "chatStreamResponseObserver was not set to the new stream even though it restarted.\n" +
                                    "finalStatus: $finalStatus\n" +
                                    "finalMetaData: $finalMetaData\n" +
                                    "userInstallationId: ${GlobalValues.installationId}\n" +
                                    "passedInstallationId: ${optionalInfo}\n"

                        chatStreamObjectErrorHelper(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName
                        )

                        gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                    }
                }
                ChatMessageStream.StreamDownReasons.SERVER_SHUTTING_DOWN -> {
                    //the server that this chat stream is connected to is shutting down

                    val loadBalancingResults = GlobalValues.runLoadBalancing(
                        clientsIntermediate,
                        errorStore = errorHandling
                    )
                    if (loadBalancingResults == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
                        //if load balancing was successful
                        startChatMessageStream()
                    } else {

                        //if load balancing failed
                        chatResponseOnErrorHandler(
                            loadBalancingResults,
                            "",
                            streamObserverInstance
                        )
                    }
                }
                ChatMessageStream.StreamDownReasons.UNRECOGNIZED,
                null,
                -> {

                    val streamDownReasonString =
                        finalMetaData?.get(GlobalValues.REASON_STREAM_SHUT_DOWN_KEY)
                    val streamDownReason =
                        ChatMessageStream.StreamDownReasons.forNumber(
                            (streamDownReasonString)?.toInt()
                                ?: -1
                        )

                    val errorString =
                        "When onCompleted was received unknown value was passed back for StreamDownReason of $streamDownReason (this should always be set on the server)\n" +
                                "streamDownReasonString: $streamDownReasonString\n" +
                                "finalStatus: $finalStatus\n" +
                                "finalMetaData: $finalMetaData\n" +
                                "optionalInfo: $optionalInfo\n"

                    chatStreamObjectErrorHelper(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName
                    )

                    gRPCErrorOccurred(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                }
            }
        }
    }

    inner class ChatMessageStreamObserver : StreamObserver<ChatMessageStream.ChatToClientResponse> {

        /** For more info see [chat_stream_general_loop]. **/

        private var finalStatus: Status? = null
        private var finalMetaData: Metadata? = null

        fun saveStatusAndMetaData(status: Status?, metadata: Metadata?) {
            finalStatus = status
            finalMetaData = metadata
        }

        //NOTE: only used for testing purposes
        //NOTE: not an exact method but good enough for testing
        private val marker =
            if (BuildConfig.DEBUG) SystemClock.uptimeMillis() % 10000L else 0L

        override fun onNext(messageHolder: ChatMessageStream.ChatToClientResponse) {

            //don't make this variable member of this object, I think an object holding a reference
            // to itself will cause it to never be garbage collected?
            val streamObserverInstance = this
            CoroutineScope(ioDispatcher).launch {
                requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {

                    //This is stopped from receiving messages (even though it is technically 'wasted'
                    // info because whatever this is will need to be re-downloaded), This is because the new stream will download
                    // it either way because it has already started and sent the info to the server, so storing it is
                    // redundant
                    if (streamObserverInstance == chatStreamResponseObserver && !chatStreamCancelled) {
                        chatResponseOnNextHandler(messageHolder, streamObserverInstance)
                    }
                }
            }
            Log.i("startBiDiTest", "onNext; marker: $marker")
        }

        override fun onError(t: Throwable) {

            //don't make this variable member of this object, I think an object holding a reference
            // to itself will cause it to never be garbage collected?
            val streamObserverInstance = this
            CoroutineScope(ioDispatcher).launch {
                requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {

                    Log.i(
                        "loadBalancingVal",
                        "streamObserverInstance == chatStreamResponseObserver ${streamObserverInstance == chatStreamResponseObserver}"
                    )
                    if (streamObserverInstance == chatStreamResponseObserver) {
                        var message = GlobalValues.NETWORK_UNKNOWN

                        t.message?.let {
                            message = it
                        }

                        Log.i(
                            "loadBalancingVal",
                            "message: $message t.simpleName: ${t::class.simpleName} error: ${
                                checkExceptionMessageForGrpcError(message)
                            }"
                        )

                        Log.i("startBiDiTest", "onError;raw msg $t")
                        Log.i("testingDoze", "ChatStreamObject onError Called onError: $t")

                        chatResponseOnErrorHandler(
                            checkExceptionMessageForGrpcError(message),
                            message,
                            streamObserverInstance
                        )
                    }
                }
            }
            Log.i("startBiDiTest", "onError; marker: $marker")
        }

        override fun onCompleted() {

            //don't make this variable member of this object, I think an object holding a reference
            // to itself will cause it to never be garbage collected?
            val streamObserverInstance = this
            CoroutineScope(ioDispatcher).launch {
                requestResponseErrorMutex.singleMutexWithLock(chatStreamResponseMutexKey) {
                    if (streamObserverInstance == chatStreamResponseObserver) {
                        chatResponseOnCompletedHandler(
                            streamObserverInstance,
                            finalStatus,
                            finalMetaData
                        )
                    }
                }
            }
            Log.i("startBiDiTest", "onCompleted; marker: $marker")
        }
    }

    private suspend fun chatStreamObjectErrorHelper(
        errorString: String,
        lineNumber: Int,
        fileName: String,
        stackTrace: String = printStackTraceForErrors(),
    ) {

        val updatedErrorString =
            "User Account OID: ${LoginFunctions.currentAccountOID}\nMessage: $errorString\nChat Stream State: $primaryChatStreamObserverState\nResponse Observer: $chatStreamResponseObserver\nRequest Observer: $chatStreamRequestObserver"
        errorMessageRepositoryHelper(
            updatedErrorString,
            lineNumber,
            fileName,
            stackTrace,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            errorHandling,
            ioDispatcher
        )
    }

    companion object {
        const val CHAT_MESSAGE_STREAM_INIT = "chat_stream_init"

        const val CHAT_STREAM_OBJECT_UNIQUE_WORKER_NAME =
            "CHAT_STREAM_OBJECT_UNIQUE_WORKER_NAME"

        const val WORKER_PARAM_SENT_FROM_KEY = "WORKER_PARAM_SENT_FROM_KEY"

        //This is the time between the chat stream attempting re-connections if no connection is found
        //NOTE: This time MUST be sorter than the Light Doze maintenance window (I saw somewhere that it was ~10 seconds long)
        private const val POLLING_DELAY_BETWEEN_CHAT_STREAM_CONNECTION_ATTEMPTS: Long = 2L * 1000L

        fun cancelAllChatStreamObjectsWork(applicationContext: Context) {
            val workManager = WorkManager.getInstance(applicationContext)
            workManager.cancelUniqueWork(CHAT_STREAM_OBJECT_UNIQUE_WORKER_NAME)
        }
    }
}