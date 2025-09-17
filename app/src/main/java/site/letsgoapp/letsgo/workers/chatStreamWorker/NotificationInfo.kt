package site.letsgoapp.letsgo.workers.chatStreamWorker

import android.app.Notification
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.*
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessageFieldsForNotifications
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.ReturnForNotificationMessage
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass

//this object will handle notifications
//each chat room has its own notification
//each message will be inside a chat room
/** How the notifications work.
 * There is a notification group which contains all notifications.
 * Each notification individually represents a chat room.
 * Each notification has messages added to it to represent messages using MessagingStyle.
 * The notificationsList contains all notifications and some relevant info related to them.
 * **/
object NotificationInfo {

    //the max number of notifications that can be shown (essentially the max number of chat rooms that can be shown to have actions)
    private const val MAX_NUMBER_OF_NOTIFICATIONS = 3

    //the max number of messages that can be shown for a given chat room
    //the tested limit on Nexus 5 API 31 was 7 messages, using 10 so even if 1 is deleted, it will still have a backup stored
    private const val MAX_NUMBER_OF_MESSAGES_PER_NOTIFICATIONS = 10
    const val ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY =
        "activity_started_from_notification"

    const val SEND_TO_CHAT_ROOM_LIST = "site.letsgoapp.letsgo.send_to_chat_room_list"

    private const val SHARED_PREFERENCES_STORED_NOTIFICATION_UUIDS =
        "site.letsgoapp.letsgo.notification_info_stored_notification_uuids"
    private const val SHARED_PREFERENCES_STORED_NOTIFICATION_CHAT_ROOM_IDS =
        "site.letsgoapp.letsgo.notification_info_chat_room_ids"

    //these separators are used inside the same string
    const val MATCH_TYPE_MESSAGE_UUID_SEPARATOR = "'''"
    private const val STORED_NOTIFICATIONS_UUID_CHAT_ROOM_SEPARATOR = ";;;"
    private const val STORED_NOTIFICATIONS_CHAT_ROOM_ID_SEPARATOR = ":::"

    //this is used as the basis for the notification id
    //NOTE: it MUST be a 3 digit number because it doubles as an intent requestCode
    private const val BASE_NOTIFICATION_ID_VALUE = 120

    //this is only used for the summary notification id, no special requirements
    private const val SUMMARY_NOTIFICATION_ID_VALUE = 102

    //private var mostRecentlyUsedIndex = MAX_NUMBER_OF_NOTIFICATIONS - 1

    private val notificationInfoMutex = CoroutineReentrantLock()

    data class MessageWithUUID(
        val messageUUIDPrimaryKey: String,
        val sentByAccountOID: String,
        var message: NotificationCompat.MessagingStyle.Message
    )

    private data class FindMessageInsideChatRoomReturnValue(
        val messageFound: Boolean,
        val indexOfChatRoom: Int,
        val indexOfMessage: Int
    )

    private data class ChatRoomStoredInfo(
        val notificationId: Int,
        val chatRoomName: String,
        val numOtherUsersInChatRoom: Int,
    )

    private data class NotificationInfoDataHolder(
        val notificationId: Int,
        var numberNewMessages: Int = 0,
        var chatRoomId: String = "",
        var lengthRestrictedChatRoomName: String = "",
        //this will be 0, 1 or 2, it does not include the current user
        var numOtherUsersInChatRoom: Int = 0,
        var summaryLine: String = "",
        var mostRecentTimestamp: Long = -1L,
        var notificationBuilder: NotificationCompat.Builder? = null,
        var messages: MutableList<MessageWithUUID> = mutableListOf(),
        var deleteIntentBroadcastReceiver: BroadcastReceiver? = null
    )

    private var totalNumberMessagesInNotifications = 0
    private val notificationsList = mutableListOf<NotificationInfoDataHolder>()

    //used to store chat rooms that are updated on intervals TO_RUN_IN_TIME_INCREMENTS
    private var chatRoomIdsRequiringUpdated = mutableSetOf<String>()

    enum class DeleteEditNotification {
        DELETE_NOTIFICATION,
        EDIT_NOTIFICATION,
    }

    private val addNotificationHandler = Handler(Looper.getMainLooper())
    private var currentlyQueued = false

    //The delay here needs to be long enough that all updates are received by the device (android
    // may ignore some updates to too many are sent at a time). However, it is short(er) than it could
    // be. This is because when a chat room is removed (say MAX_NUMBER_OF_NOTIFICATIONS was reached), the chat
    // room is removed instantly however it needs to wait this delay (in many cases) for the new chat room to
    // be added. The shorter delay will make it feel less disjointed if the user is paying attention to it.
    private const val TO_RUN_IN_TIME_INCREMENTS = 2000L
    private const val HANDLER_ADD_NOTIFICATION_RUNNABLE_TOKEN =
        "handler_add_notification_runnable_token"

    private suspend fun notifyNotificationsListChanged(
        applicationContext: Context,
        sharedPreferences: SharedPreferences,
        chatRoomId: String,
    ) {
        notificationInfoMutex.withLock {
            //this needs to be called before the update or message info can be lost in the gap
            // between notifications being updated
            convertNotificationsListToStringsAndStore(sharedPreferences)

            chatRoomIdsRequiringUpdated.add(chatRoomId)

            if (!currentlyQueued) {
                currentlyQueued = true
                addNotificationHandler.postAtTime(
                    {
                        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                            notificationInfoMutex.withLock {
                                currentlyQueued = false

                                if (ChatStreamWorker.continueChatStreamWorker.get()) {

                                    Log.i("buildNotificationStuff", "starting")

                                    //NOTE: This below part of this block is not a separate function so that nothing can call
                                    // it by 'mistake'. It is only meant to be called every TO_RUN_IN_TIME_INCREMENTS to avoid
                                    // notify() being called too frequently (it can mess with what the user sees
                                    // as updated on the UI).

                                    val indexesRequiringUpdates = mutableListOf<Int>()

                                    for (i in notificationsList.indices) {
                                        if (chatRoomIdsRequiringUpdated.contains(
                                                notificationsList[i].chatRoomId
                                            )
                                        ) {
                                            indexesRequiringUpdates.add(i)
                                        }
                                    }

                                    //values stored inside are no longer needed
                                    chatRoomIdsRequiringUpdated.clear()

                                    for (indexOfChatRoom in indexesRequiringUpdates) {

                                        val displayedChatRoomName =
                                            applicationContext.resources.getQuantityString(
                                                R.plurals.chat_message_chat_room_name,
                                                notificationsList[indexOfChatRoom].numOtherUsersInChatRoom,
                                                notificationsList[indexOfChatRoom].lengthRestrictedChatRoomName
                                            )

                                        notificationsList[indexOfChatRoom].notificationBuilder?.let { notificationBuilder ->
                                            setupMessagingStyle(indexOfChatRoom)?.let { messagingStyle ->

                                                Log.i(
                                                    "buildNotificationStuff",
                                                    "displayedChatRoomName: $displayedChatRoomName"
                                                )

                                                //it can display an empty space for the chat room name sometimes if the conversation
                                                // title is set to ""
                                                if (displayedChatRoomName.isNotEmpty()) {
                                                    messagingStyle.conversationTitle =
                                                        displayedChatRoomName
                                                }

                                                //connect the messaging style to the notification builder
                                                messagingStyle.setBuilder(notificationBuilder)

                                                //allow this coRoutine to be canceled
                                                yield()

                                                //Priority is set here, however it is only used on devices API 25 or lower, the channel
                                                // importance is used for higher API levels instead. The channel is set inside
                                                // LetsGoApplicationClass.kt and priority cannot be changed.
                                                notificationBuilder.priority =
                                                    NotificationCompat.PRIORITY_HIGH

                                                //set message
                                                NotificationManagerCompat.from(
                                                    applicationContext
                                                )
                                                    .apply {

                                                        Log.i(
                                                            "returnMessagesZAX",
                                                            "buildNotification() notify"
                                                        )

                                                        notify(
                                                            notificationsList[indexOfChatRoom].notificationId,
                                                            notificationBuilder.build()
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    HANDLER_ADD_NOTIFICATION_RUNNABLE_TOKEN,
                    SystemClock.uptimeMillis() + TO_RUN_IN_TIME_INCREMENTS
                )
            }
        }
    }

    private suspend fun convertNotificationsListToStringsAndStore(
        sharedPreferences: SharedPreferences
    ) {
        notificationInfoMutex.withLock {
            var chatRoomsStr = ""
            var messagesStr = ""

            for (item in notificationsList) {
                Log.i(
                    "returnMessagesZAX",
                    "storing string chatRoomId: ${item.chatRoomId} notificationId: ${item.notificationId}"
                )
                //These are stored without SEPARATOR because the chat room name does not have a restriction on char type, so a separator could exist
                // inside the name if a user names it poorly.
                val tempString =
                    item.lengthRestrictedChatRoomName.length.toString() + '|' + item.lengthRestrictedChatRoomName + STORED_NOTIFICATIONS_CHAT_ROOM_ID_SEPARATOR + item.chatRoomId + STORED_NOTIFICATIONS_CHAT_ROOM_ID_SEPARATOR + item.notificationId.toString() + STORED_NOTIFICATIONS_CHAT_ROOM_ID_SEPARATOR + item.numOtherUsersInChatRoom.toString()
                chatRoomsStr += tempString.length.toString() + '|' + tempString
                for (messages in item.messages) {
                    messagesStr += messages.messageUUIDPrimaryKey + STORED_NOTIFICATIONS_UUID_CHAT_ROOM_SEPARATOR
                }
            }

            sharedPreferences.edit().putString(
                SHARED_PREFERENCES_STORED_NOTIFICATION_UUIDS,
                messagesStr
            ).apply()

            sharedPreferences.edit().putString(
                SHARED_PREFERENCES_STORED_NOTIFICATION_CHAT_ROOM_IDS,
                chatRoomsStr
            ).apply()
        }
    }

    private suspend fun extractSharedPreferencesMessageUUIDs(
        sharedPreferences: SharedPreferences
    ): List<String> {
        return notificationInfoMutex.withLock {
            val str = sharedPreferences.getString(
                SHARED_PREFERENCES_STORED_NOTIFICATION_UUIDS,
                ""
            ) ?: ""

            val uuidSplitString = Regex(STORED_NOTIFICATIONS_UUID_CHAT_ROOM_SEPARATOR)

            val uuidList = str.split(uuidSplitString).filter {
                it.isNotEmpty()
            }.sortedBy { it }

            return@withLock uuidList
        }
    }

    private suspend fun extractSharedPreferencesChatRoomInfo(
        sharedPreferences: SharedPreferences
    ): Map<String, ChatRoomStoredInfo> {
        return notificationInfoMutex.withLock {
            val str = sharedPreferences.getString(
                SHARED_PREFERENCES_STORED_NOTIFICATION_CHAT_ROOM_IDS,
                ""
            ) ?: ""

            val returnMap = mutableMapOf<String, ChatRoomStoredInfo>()

            try {
                val chatRoomIdSplitString = Regex(STORED_NOTIFICATIONS_CHAT_ROOM_ID_SEPARATOR)

                val uuidList = mutableListOf<String>()
                var i = 0
                while (i < str.length) {
                    var entireChatRoomLengthStr = ""
                    while (str[i] != '|') {
                        entireChatRoomLengthStr += str[i]
                        i++
                    }

                    i++
                    val entireChatRoomLength = entireChatRoomLengthStr.toInt()

                    uuidList.add(
                        str.substring(i, i + entireChatRoomLength)
                    )
                    i += entireChatRoomLength
                }

                Log.i("returnMessagesZAX", "extracted whole string $str")

                for (chatRoomStr in uuidList) {

                    var startingIndexForName = 0
                    var numberCharsForName = ""

                    for (j in chatRoomStr.indices) {
                        if (chatRoomStr[j] == '|') {
                            startingIndexForName = j + 1
                            break
                        } else {
                            numberCharsForName += chatRoomStr[j]
                        }
                    }

                    try {

                        val finalIndexForName = startingIndexForName + numberCharsForName.toInt()

                        val chatRoomName =
                            chatRoomStr.substring(startingIndexForName, finalIndexForName)

                        val chatRoomStrNoName = chatRoomStr.substring(finalIndexForName)

                        val chatRoomInfo = chatRoomStrNoName.split(chatRoomIdSplitString).filter {
                            it.isNotEmpty()
                        }
                        Log.i("returnMessagesZAX", "extracted split string $chatRoomStr")
                        Log.i(
                            "returnMessagesZAX",
                            "extracted split string no name $chatRoomStrNoName"
                        )
                        Log.i("returnMessagesZAX", "extracted split again string $chatRoomInfo")
                        if (chatRoomInfo.size == 3) {
                            Log.i(
                                "returnMessagesZAX",
                                "extracting string chatRoomName: $chatRoomName chatRoomId: ${chatRoomInfo[0]} notificationId: ${chatRoomInfo[1].toInt()} numMembersInChat: ${chatRoomInfo[2].toInt()}"
                            )
                            returnMap[chatRoomInfo[0]] = ChatRoomStoredInfo(
                                chatRoomInfo[1].toInt(),
                                chatRoomName,
                                chatRoomInfo[2].toInt()
                            )
                        } else {
                            val errorMessage =
                                "When attempting to extract a chat room from shared preferences, it did not contain all info.\n" +
                                        "chatRoomInfo: ${chatRoomInfo}\n" +
                                        "chatRoomStr: ${chatRoomStr}\n" +
                                        "str: ${str}\n"

                            ServiceLocator.globalErrorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage
                            )
                        }

                    } catch (e: Exception) {
                        when (e) {
                            is IndexOutOfBoundsException,
                            is NumberFormatException,
                            -> {
                                val errorMessage =
                                    "When attempting to extract a chat room from shared preferences, an exception was thrown.\n" +
                                            "exception: ${e.message}\n" +
                                            "chatRoomStr: ${chatRoomStr}\n" +
                                            "str: ${str}\n"

                                ServiceLocator.globalErrorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    e.stackTraceToString(),
                                    errorMessage
                                )

                                //can continue
                            }
                            else -> {
                                throw e
                            }
                        }
                    }
                }

            } catch (e: NumberFormatException) {
                val errorMessage =
                    "When attempting to extract a chat room from shared preferences, an exception was thrown.\n" +
                            "exception: ${e.message}\n" +
                            "str: ${str}\n"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    e.stackTraceToString(),
                    errorMessage
                )

                //can continue
            }

            return@withLock returnMap
        }
    }

    private suspend fun extractNotificationsListInfo(): List<String> {
        return notificationInfoMutex.withLock {
            val returnList = mutableListOf<String>()

            for (item in notificationsList) {
                for (message in item.messages) {
                    returnList.add(message.messageUUIDPrimaryKey)
                }
            }

            returnList.sortBy { it }

            return@withLock returnList
        }
    }

    private suspend fun removeMessageFromNotificationsList(
        applicationContext: Context,
        messageUUID: String,
        chatRoomId: String = "",
    ): FindMessageInsideChatRoomReturnValue {
        return notificationInfoMutex.withLock {
            //must find message inside notificationsList, notificationsListMessages has been sorted
            var returnVal =
                if (chatRoomId.isEmpty()) {
                    findMessageInsideChatRoom(
                        messageUUID
                    )
                } else {
                    findMessageInsideChatRoom(
                        chatRoomId,
                        messageUUID
                    )
                }

            if (returnVal.messageFound) {
                val indexOfMessage = returnVal.indexOfMessage
                val indexOfChatRoom = returnVal.indexOfChatRoom

                chatRoomIdsRequiringUpdated.add(notificationsList[indexOfChatRoom].chatRoomId)

                if (notificationsList[indexOfChatRoom].messages.size == 1) {
                    removeNotification(
                        applicationContext,
                        notificationsList[indexOfChatRoom].chatRoomId,
                    )

                    //if the notification was removed, then return messageFound == false, because the
                    // index no longer exists
                    returnVal = FindMessageInsideChatRoomReturnValue(
                        false,
                        -1,
                        -1
                    )
                } else {
                    notificationsList[indexOfChatRoom].messages.removeAt(
                        indexOfMessage
                    )
                    notificationsList[indexOfChatRoom].numberNewMessages--
                    totalNumberMessagesInNotifications--
                }
            }

            return@withLock returnVal
        }
    }

    private suspend fun compareAndRemoveUUIDFromNotificationList(
        applicationContext: Context,
        notificationsListMessageUUIDs: List<String>,
        sharedPreferenceMessageUUIDs: List<String>,
    ): List<String> {
        return notificationInfoMutex.withLock {
            val uuidsToRequest = mutableListOf<String>()

            var notificationListMsgIndex = 0
            var sharedPreferenceMsgIndex = 0

            //sorted above
            while (
                notificationListMsgIndex < notificationsListMessageUUIDs.size
                && sharedPreferenceMsgIndex < sharedPreferenceMessageUUIDs.size
            ) {
                if (
                    notificationsListMessageUUIDs[notificationListMsgIndex]
                    < sharedPreferenceMessageUUIDs[sharedPreferenceMsgIndex]
                ) { //unique inside notifications list chat room

                    removeMessageFromNotificationsList(
                        applicationContext,
                        notificationsListMessageUUIDs[notificationListMsgIndex]
                    )

                    notificationListMsgIndex++
                } else if (
                    notificationsListMessageUUIDs[notificationListMsgIndex]
                    > sharedPreferenceMessageUUIDs[sharedPreferenceMsgIndex]
                ) { //unique inside shared preference list chat room

                    //request message
                    uuidsToRequest.add(sharedPreferenceMessageUUIDs[sharedPreferenceMsgIndex])

                    sharedPreferenceMsgIndex++
                } else { //match
                    notificationListMsgIndex++
                    sharedPreferenceMsgIndex++
                }
            }

            while (
                notificationListMsgIndex < notificationsListMessageUUIDs.size
            ) { //unique inside notifications list chat room
                removeMessageFromNotificationsList(
                    applicationContext,
                    notificationsListMessageUUIDs[notificationListMsgIndex]
                )

                notificationListMsgIndex++
            }

            while (
                sharedPreferenceMsgIndex < sharedPreferenceMessageUUIDs.size
            ) { //unique inside shared preference list chat room
                uuidsToRequest.add(sharedPreferenceMessageUUIDs[sharedPreferenceMsgIndex])
                sharedPreferenceMsgIndex++
            }

            return@withLock uuidsToRequest
        }
    }

    //adds the passed messageInfo to the notificationsList, will also keep
    //chatRoomIdToIndexMap updated with any added chat rooms
    private suspend fun generateAndStoreMessageInList(
        applicationContext: Context,
        messageInfo: MessageFieldsForNotifications,
        otherUsersNotificationInfo: Map<String, ReturnForNotificationMessage>,
        chatRoomIdToIndexMap: MutableMap<String, Int>,
        usersCurrentlyStored: Map<String, Person>,
        chatRoomInfo: Map<String, ChatRoomStoredInfo>
    ) {
        notificationInfoMutex.withLock {

            Log.i(
                "returnMessagesZAX",
                "chatRoom ${messageInfo.chat_room_id} index: ${chatRoomIdToIndexMap[messageInfo.chat_room_id]}"
            )

            val indexOfChatRoomID =
                chatRoomIdToIndexMap[messageInfo.chat_room_id]
                    ?: (chatRoomInfo[messageInfo.chat_room_id]?.let { chatRoomStoredInfo ->

                        val addedChatRoomIndex = addNewChatRoomToList(
                            applicationContext,
                            messageInfo.chat_room_id,
                            chatRoomStoredInfo.chatRoomName,
                            chatRoomStoredInfo.numOtherUsersInChatRoom,
                            chatRoomStoredInfo.notificationId
                        )

                        chatRoomIdToIndexMap[messageInfo.chat_room_id] = addedChatRoomIndex
                        addedChatRoomIndex
                    } ?: return@withLock)

            val messageType =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    messageInfo.message_type
                )

            when (chatRoomLayoutByMessageType(messageType)) {
                LayoutType.LAYOUT_MESSAGE -> {

                    Log.i(
                        "returnMessagesZAX",
                        "LAYOUT_MESSAGE person found: ${usersCurrentlyStored[messageInfo.sent_by_account_id] != null}"
                    )
                    usersCurrentlyStored[messageInfo.sent_by_account_id]?.let { person ->

                        NotificationsSharedFunctions.generateNotificationMessageText(
                            applicationContext,
                            messageType,
                            hasCompleteInfo = messageInfo.has_complete_info,
                            rawMessageText = messageInfo.message_text,
                        )?.let { messageText ->

                            Log.i(
                                "returnMessagesZAX",
                                "LAYOUT_MESSAGE messageText: $messageText"
                            )

                            val storedOnServerTime =
                                NotificationsSharedFunctions.extractNotificationTime(
                                    messageInfo.message_stored_server_time
                                )

                            val notificationStyleMessage =
                                NotificationCompat.MessagingStyle.Message(
                                    messageText, storedOnServerTime, person
                                )

                            MessageWithUUID(
                                messageInfo.messageUUIDPrimaryKey,
                                messageInfo.sent_by_account_id,
                                notificationStyleMessage
                            )
                        }
                    }
                }
                LayoutType.LAYOUT_SINGLE -> {

                    //Only a few message types actually need and will have a valid account_id stored (shown below)
                    // DIFFERENT_USER_JOINED_MESSAGE,
                    // DIFFERENT_USER_LEFT_MESSAGE,
                    // USER_BANNED_MESSAGE,
                    // USER_KICKED_MESSAGE

                    val userName =
                        when (messageType) {
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
                            -> {
                                val otherUserInfo =
                                    otherUsersNotificationInfo[messageInfo.sent_by_account_id]

                                if (otherUserInfo == null || otherUserInfo.name == "" || otherUserInfo.name == "~") {
                                    applicationContext.resources.getString(R.string.User)
                                } else {
                                    otherUserInfo.name
                                }

                            }
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                            -> {
                                if (messageInfo.account_id.isValidMongoDBOID() && messageInfo.account_id != LoginFunctions.currentAccountOID) { //if valid oid and it is not about this user
                                    val otherUserInfo =
                                        otherUsersNotificationInfo[messageInfo.account_id]

                                    if (otherUserInfo == null || otherUserInfo.name == "" || otherUserInfo.name == "~") {
                                        applicationContext.resources.getString(R.string.User)
                                    } else {
                                        otherUserInfo.name
                                    }
                                } else {
                                    ""
                                }
                            }
                            //NOTE: It is set up this way instead of using else so that if
                            // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
                            // a warning will be thrown.
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
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
                            null,
                            -> {
                                ""
                            }
                        }

                    Log.i(
                        "returnMessagesZAX",
                        "LAYOUT_SINGLE userName: $userName"
                    )

                    NotificationsSharedFunctions.generateNotificationMessageText(
                        applicationContext,
                        messageType,
                        hasCompleteInfo = messageInfo.has_complete_info,
                        rawMessageText = messageInfo.message_text,
                        userName = userName,
                    )?.let { messageText ->

                        Log.i(
                            "returnMessagesZAX",
                            "LAYOUT_SINGLE messageText: $messageText"
                        )

                        NotificationsSharedFunctions.generateNotificationSingleMessage(
                            applicationContext,
                            messageInfo.sent_by_account_id,
                            messageInfo.messageUUIDPrimaryKey,
                            messageText,
                            messageInfo.message_stored_server_time
                        )
                    }
                }
                LayoutType.LAYOUT_EMPTY -> {
                    null
                }
            }?.let { notificationMessage ->

                Log.i(
                    "returnMessagesZAX",
                    "addMessageToList(); indexOfChatRoomID; ${notificationsList[indexOfChatRoomID].chatRoomId}"
                )

                addMessageToList(
                    indexOfChatRoomID,
                    notificationMessage
                )
            }
        }
    }

    /**
     * This function will check if the shared preferences string matches the current setup of the notificationsList.
     *  If it DOES match, then nothing will be done, if it does NOT match, it will attempt to extract the info
     *  required to repair the list. This is done because if the application closes, then all notification info will
     *  be lost, so enough info is stored inside the shared preferences to rebuild the list.
     **/
    private suspend fun compareSharedPreferencesListWithCurrentList(
        applicationContext: Context,
        sharedPreferences: SharedPreferences
    ) {
        return notificationInfoMutex.withLock {

            val sharedPreferenceMessages = extractSharedPreferencesMessageUUIDs(sharedPreferences)
            val notificationsListMessages = extractNotificationsListInfo()

            Log.i(
                "returnMessagesZAX",
                "sharedPreferenceMessages != notificationsListMessages: ${sharedPreferenceMessages != notificationsListMessages}"
            )

            if (sharedPreferenceMessages != notificationsListMessages) {

                //NOTE: Assuming shared preferences is source of truth.

                val chatRoomInfo = extractSharedPreferencesChatRoomInfo(sharedPreferences)

                val uuidsThatAreNotPresent = compareAndRemoveUUIDFromNotificationList(
                    applicationContext,
                    notificationsListMessages,
                    sharedPreferenceMessages
                )

                Log.i(
                    "returnMessagesZAX",
                    "uuidsThatAreNotPresent: $uuidsThatAreNotPresent"
                )

                val usersCurrentlyStored = mutableMapOf<String, Person>()

                //NOTE: No more modifications to notificationsList be done after this point (several
                // are potentially done inside findUUIDAndRemoveFromNotificationList)
                for (notification in notificationsList) {
                    for (message in notification.messages) {
                        message.message.person?.let {
                            usersCurrentlyStored[message.sentByAccountOID] = it
                        }
                    }
                }

                Log.i(
                    "returnMessagesZAX",
                    "usersCurrentlyStored.size: ${usersCurrentlyStored.size}"
                )

                val uuidsToRequest = mutableListOf<String>()
                val usersToRequestInfoFromDatabase = mutableSetOf<String>()
                val fullPersonRequiredForNotification = mutableSetOf<String>()

                val matchesMadeInfo =
                    mutableListOf<NotificationsSharedFunctions.MatchesMadeDataHolder>()

                for (uuid in uuidsThatAreNotPresent) {
                    //Matches work a bit differently
                    // 1) They store the other user that the match is about
                    // 2) They store the chat room ID as the key for the 'person' (this is important to keep messages separate visually)
                    if (uuid.startsWith(ChatStreamWorker.MATCH_TYPE_MESSAGE_UUID)) {
                        val matchInfo =
                            NotificationsSharedFunctions.extractInfoFromMatchMadeUUID(uuid)

                        if (!usersCurrentlyStored.contains(matchInfo.userAccountOID)) {
                            usersToRequestInfoFromDatabase.add(matchInfo.userAccountOID)
                            fullPersonRequiredForNotification.add(matchInfo.userAccountOID)
                        }

                        matchesMadeInfo.add(matchInfo)
                    } else if (uuid.isValidUUIDKey()) {
                        uuidsToRequest.add(uuid)
                    } else {
                        val errorMessage =
                            "When attempting to extract a uuid, an invalid value was found.\n" +
                                    "uuid: ${uuid}\n" +
                                    "uuidsThatAreNotPresent: ${uuidsThatAreNotPresent}\n" +
                                    "sharedPreferenceMessages: $sharedPreferenceMessages\n" +
                                    "notificationsListMessages: $notificationsListMessages\n"

                        ServiceLocator.globalErrorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }

                Log.i(
                    "returnMessagesZAX",
                    "uuidsToRequest.size: ${uuidsToRequest.size}"
                )

                val notificationInfoRepository =
                    (applicationContext as LetsGoApplicationClass).notificationInfoRepository

                val allMessagesRequiringUpdated =
                    notificationInfoRepository.requestAllMessageByUUID(
                        uuidsToRequest
                    )

                Log.i(
                    "returnMessagesZAX",
                    "allMessagesRequiringUpdated.size: ${allMessagesRequiringUpdated.size}"
                )

                val chatRoomsLastJoinedTimeMap =
                    notificationInfoRepository.getChatRoomsLastTimeJoined(
                        matchesMadeInfo.map {
                            it.chatRoomId
                        }
                    ).associateBy(
                        {
                            it.chatRoomID
                        },
                        {
                            it.time_joined
                        }
                    )

                for (messageInfo in allMessagesRequiringUpdated) {
                    val messageType =
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                            messageInfo.message_type
                        )
                    if (!usersCurrentlyStored.contains(messageInfo.sent_by_account_id)) {

                        usersToRequestInfoFromDatabase.add(messageInfo.sent_by_account_id)

                        val layoutType = chatRoomLayoutByMessageType(messageType)
                        if (layoutType == LayoutType.LAYOUT_MESSAGE) {
                            fullPersonRequiredForNotification.add(messageInfo.sent_by_account_id)
                        }
                    }
                }

                val otherUsersNotificationInfo =
                    notificationInfoRepository.getUserInfoForNotificationMessage(
                        usersToRequestInfoFromDatabase.toList()
                    ).associateBy(
                        { it.accountOID },
                        { it }
                    )

                val buildPersonJobs = mutableListOf<Job>()
                val buildUsersResults = mutableListOf<Pair<String, Person>?>()

                for (requiredUserOID in fullPersonRequiredForNotification) {
                    otherUsersNotificationInfo[requiredUserOID]?.let { otherUserInfo ->
                        buildUsersResults.add(null)
                        val thisIndex = buildUsersResults.lastIndex
                        buildPersonJobs.add(
                            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                                val returnValue =
                                    NotificationsSharedFunctions.buildPersonForNotificationMessage(
                                        applicationContext,
                                        otherUserInfo.accountOID,
                                        otherUserInfo
                                    )
                                buildUsersResults[thisIndex] =
                                    Pair(otherUserInfo.accountOID, returnValue)
                            }
                        )
                    }
                }

                for (job in buildPersonJobs) {
                    job.join()
                }

                for (result in buildUsersResults) {
                    result?.let {
                        usersCurrentlyStored[it.first] = it.second
                    }
                }

                val chatRoomIdToIndexMap = mutableMapOf<String, Int>()

                for (i in notificationsList.indices) {
                    chatRoomIdToIndexMap[notificationsList[i].chatRoomId] = i
                }

                for (messageInfo in allMessagesRequiringUpdated) {
                    generateAndStoreMessageInList(
                        applicationContext,
                        messageInfo,
                        otherUsersNotificationInfo,
                        chatRoomIdToIndexMap,
                        usersCurrentlyStored,
                        chatRoomInfo
                    )
                }

                for (newMatchMessage in matchesMadeInfo) {
                    chatRoomsLastJoinedTimeMap[newMatchMessage.chatRoomId]?.let { timeJoinedChatRoom ->
                        otherUsersNotificationInfo[newMatchMessage.userAccountOID]?.let { otherUsersInfo ->
                            usersCurrentlyStored[newMatchMessage.userAccountOID]?.let { person ->

                                var indexOfChatRoomID =
                                    chatRoomIdToIndexMap[newMatchMessage.chatRoomId]

                                if (indexOfChatRoomID == null) {
                                    chatRoomInfo[newMatchMessage.chatRoomId]?.let { chatRoomStoredInfo ->
                                        val addedChatRoomIndex = addNewChatRoomToList(
                                            applicationContext,
                                            newMatchMessage.chatRoomId,
                                            chatRoomStoredInfo.chatRoomName,
                                            chatRoomStoredInfo.numOtherUsersInChatRoom,
                                            chatRoomStoredInfo.notificationId
                                        )

                                        chatRoomIdToIndexMap[newMatchMessage.chatRoomId] =
                                            addedChatRoomIndex

                                        indexOfChatRoomID = addedChatRoomIndex
                                    }
                                }

                                if (indexOfChatRoomID != null) {

                                    //The match needs the key to be the chat room id.
                                    val newPerson = person.toBuilder()
                                    newPerson.setKey(newMatchMessage.chatRoomId)

                                    NotificationsSharedFunctions.generateNotificationForMatch(
                                        applicationContext,
                                        newMatchMessage.chatRoomId,
                                        newMatchMessage.userAccountOID,
                                        timeJoinedChatRoom,
                                        otherUsersInfo.name,
                                        newPerson.build()
                                    ).let { notificationMessage ->
                                        addMessageToList(
                                            indexOfChatRoomID!!,
                                            notificationMessage
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                //update mostRecentTimestamp and sort messages lists
                for (notification in notificationsList) {
                    notification.messages.sortBy { it.message.timestamp }
                    notification.mostRecentTimestamp =
                        if (notification.messages.isNotEmpty()) {
                            notification.messages.last().message.timestamp
                        } else {
                            -1L
                        }
                }

                notificationsList.sortBy { it.mostRecentTimestamp }
            }
        }
    }

    private fun storeChatRoomNameAndOtherUsers(
        indexOfChatRoomID: Int,
        chatRoomName: String,
        numOtherUsersInChatRoom: Int,
    ) {

        //limit length of chat room name because it needs to be stored as a string
        // inside shared preferences
        notificationsList[indexOfChatRoomID].lengthRestrictedChatRoomName =
            if (chatRoomName.length > 50) {
                "$chatRoomName..."
            } else {
                chatRoomName
            }

        notificationsList[indexOfChatRoomID].numOtherUsersInChatRoom = numOtherUsersInChatRoom
    }

    suspend fun addNotification(
        applicationContext: Context,
        chatRoomId: String,
        chatRoomName: String,
        firstTwoUserNamesInChatRoom: List<String>,
        message: MessageWithUUID,
    ) {
        notificationInfoMutex.withLock {

            if (GlobalValues.anActivityCurrentlyRunning
                || !chatRoomId.isValidChatRoomId() //chat room id must be valid because it is stored as a string
            ) {
                return@withLock
            }

            val sharedPreferences = applicationContext.getSharedPreferences(
                applicationContext.getString(R.string.shared_preferences_lets_go_key),
                MultiDexApplication.MODE_PRIVATE
            )

            compareSharedPreferencesListWithCurrentList(
                applicationContext,
                sharedPreferences
            )

            var indexOfChatRoomID = -1

            for (i in notificationsList.indices) {
                if (notificationsList[i].chatRoomId == chatRoomId) {
                    indexOfChatRoomID = i
                    break
                }
            }

            Log.i("resultInfo", "indexOfChatRoomID: $indexOfChatRoomID")

            if (indexOfChatRoomID == -1) { //chat room was not found

                val currentIndex = addNewChatRoomToList(
                    applicationContext,
                    chatRoomId,
                    chatRoomName,
                    firstTwoUserNamesInChatRoom.size
                )

                addMessageToList(
                    currentIndex,
                    message
                )

                notifyNotificationsListChanged(
                    applicationContext,
                    sharedPreferences,
                    notificationsList[currentIndex].chatRoomId
                )

            } else { //chat room was found in list

                Log.i(
                    "addNotification",
                    "update firstTwoUserNamesInChatRoom.size ${firstTwoUserNamesInChatRoom.size}"
                )

                addMessageToList(
                    indexOfChatRoomID,
                    message
                )

                storeChatRoomNameAndOtherUsers(
                    indexOfChatRoomID,
                    chatRoomName,
                    firstTwoUserNamesInChatRoom.size
                )

                //remove (oldest) from this list if list maximum has been reached
                while (notificationsList[indexOfChatRoomID].messages.size > MAX_NUMBER_OF_MESSAGES_PER_NOTIFICATIONS) {
                    notificationsList[indexOfChatRoomID].messages.removeAt(0)
                    notificationsList[indexOfChatRoomID].numberNewMessages--
                    totalNumberMessagesInNotifications--
                }

                //remove custom heads up content view, do not want it showing custom view unless this is delete/edit (handled
                // inside editDeleteMessage)
                notificationsList[indexOfChatRoomID].notificationBuilder?.setCustomHeadsUpContentView(
                    null
                )

                notifyNotificationsListChanged(
                    applicationContext,
                    sharedPreferences,
                    notificationsList[indexOfChatRoomID].chatRoomId
                )

            }
        }
    }

    //set notificationId to a value to use that value, otherwise a unique notification Id will be used
    private suspend fun addNewChatRoomToList(
        applicationContext: Context,
        chatRoomId: String,
        chatRoomName: String,
        numOtherUsersInChatRoom: Int,
        notificationId: Int = -1
    ): Int {
        //only allow MAX_NUMBER_OF_NOTIFICATIONS number of notifications to exist
        if (notificationsList.size >= MAX_NUMBER_OF_NOTIFICATIONS) {
            removeNotification(
                applicationContext,
                notificationsList.first().chatRoomId
            )
        }

        val generatedNotificationId =
            if (notificationId == -1) {
                val setOfNotificationIds = mutableSetOf<Int>()

                for (notification in notificationsList) {
                    setOfNotificationIds.add(notification.notificationId)
                }

                var generatedNotificationId = -1

                //notificationId cannot be larger than 1000 (see BASE_NOTIFICATION_ID_VALUE for details)
                for (i in BASE_NOTIFICATION_ID_VALUE until 1000) {
                    if (!setOfNotificationIds.contains(i)) {
                        generatedNotificationId = i
                        break
                    }
                }

                generatedNotificationId
            } else {
                notificationId
            }

        notificationsList.add(
            NotificationInfoDataHolder(
                generatedNotificationId
            )
        )

        val currentIndex = notificationsList.lastIndex

        //set chat room ID for identification
        notificationsList[currentIndex].chatRoomId = chatRoomId

        //Create Intent for the Login Activity to start
        val resultIntent = Intent(applicationContext, LoginActivity::class.java).apply {
            this.flags =
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY, chatRoomId)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT > 23)
            flags = flags or PendingIntent.FLAG_IMMUTABLE

        //FLAG_UPDATE_CURRENT and setting a unique requestCode are important or the PendingIntents will
        // be re-used with outdated 'Extra' info ALSO if PendingIntent.FLAG_IMMUTABLE is not set on API >=23
        // the extraData will not go through
        //NOTE: This pending intent is used when a summary notification is used. Or when the notification has
        // not been added as part of the group summary yet. For example it is still in its popup form in the
        // top of the device.
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationsList[currentIndex].notificationId,
            resultIntent,
            flags
        )

        notificationsList[currentIndex].deleteIntentBroadcastReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                        Log.i(
                            "returnMessagesZAX",
                            "deleteIntentBroadcastReceiver onReceive() started"
                        )
                        removeNotification(applicationContext, chatRoomId)
                    }
                }
            }

        val notificationDeletedAction = "NOTIFICATION_DELETED_$chatRoomId"

        flags = if (Build.VERSION.SDK_INT > 23)
            PendingIntent.FLAG_IMMUTABLE
        else
            0

        val notificationDeletedIntent = Intent(notificationDeletedAction)
        val deleteIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            notificationDeletedIntent,
            flags
        )

        applicationContext.registerReceiver(
            notificationsList[currentIndex].deleteIntentBroadcastReceiver,
            IntentFilter(notificationDeletedAction)
        )

        notificationsList[currentIndex].notificationBuilder = NotificationCompat.Builder(
            applicationContext,
            ChatStreamWorker.CHAT_MESSAGE_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.lets_go_icon)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)
            .setGroup(ChatStreamWorker.CHAT_MESSAGE_GROUP_ID)

        storeChatRoomNameAndOtherUsers(
            currentIndex,
            chatRoomName,
            numOtherUsersInChatRoom
        )

        return currentIndex
    }

    private suspend fun addMessageToList(
        currentIndex: Int,
        message: MessageWithUUID,
    ) {
        return notificationInfoMutex.withLock {
            notificationsList[currentIndex].messages.add(message)

            notificationsList[currentIndex].numberNewMessages++
            totalNumberMessagesInNotifications++

            chatRoomIdsRequiringUpdated.add(notificationsList[currentIndex].chatRoomId)
        }
    }

    private suspend fun setupMessagingStyle(
        currentIndex: Int
    ): NotificationCompat.MessagingStyle? {
        return notificationInfoMutex.withLock {
            if (notificationsList[currentIndex].messages.isEmpty()) {
                val errorMessage =
                    "setupMessagingStyle() was called with no messages inside the passed chat room notificationsList" +
                            "notificationsList[currentIndex]: ${notificationsList[currentIndex]}\n" +
                            "notificationsList: $notificationsList\n"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
                return@withLock null
            }

            //messages list is guaranteed to have at least 1 element (it is checked for above)
            var messagingStyle: NotificationCompat.MessagingStyle? = null

            //reset number of new messages
//        notificationsList[currentIndex].numberNewMessages = 1
//        totalNumberMessagesInNotifications++

            notificationsList[currentIndex].messages.sortBy { it.message.timestamp }
            notificationsList[currentIndex].mostRecentTimestamp =
                notificationsList[currentIndex].messages.last().message.timestamp

            for (message in notificationsList[currentIndex].messages) {

                if (messagingStyle == null) {
                    messagingStyle = message.message.person?.let {
                        NotificationCompat.MessagingStyle(it)
                            .setGroupConversation(true)
                            .addMessage(message.message)
                    }
                } else {
                    messagingStyle.addMessage(message.message)
                }
                //finalMessageText = if(messages[i].message.text != null) messages[i].message.text.toString() else finalMessageText
            }
            return@withLock messagingStyle
        }
    }

    private suspend fun findMessageInsideChatRoom(
        chatRoomId: String,
        messageToBeFoundUUID: String
    ): FindMessageInsideChatRoomReturnValue {
        return notificationInfoMutex.withLock {

            Log.i(
                "resultInfo",
                "findMessageInsideChatRoom() messageToBeFoundUUID: $messageToBeFoundUUID"
            )

            var indexOfChatRoomId = -1

            for (i in notificationsList.indices) {
                if (notificationsList[i].chatRoomId == chatRoomId) {
                    indexOfChatRoomId = i
                    break
                }
            }

            Log.i("resultInfo", "indexOfChatRoomId: $indexOfChatRoomId")

            if (indexOfChatRoomId != -1) { //chat room was found
                var indexOfMessage = -1
                for (i in notificationsList[indexOfChatRoomId].messages.indices) {

                    val messageUUID =
                        notificationsList[indexOfChatRoomId].messages[i].messageUUIDPrimaryKey

                    if (messageUUID.isValidUUIDKey() && messageUUID == messageToBeFoundUUID) {
                        indexOfMessage = i
                        break
                    }
                }

                Log.i("resultInfo", "indexOfMessage: $indexOfMessage")

                //message was found inside notifications displaying
                return@withLock if (indexOfMessage != -1) {
                    FindMessageInsideChatRoomReturnValue(
                        true,
                        indexOfChatRoomId,
                        indexOfMessage
                    )
                } else {
                    FindMessageInsideChatRoomReturnValue(
                        false,
                        indexOfChatRoomId,
                        indexOfMessage
                    )
                }
            }

            return@withLock FindMessageInsideChatRoomReturnValue(
                false,
                indexOfChatRoomId,
                -1
            )
        }
    }

    private suspend fun findMessageInsideChatRoom(
        messageToBeFoundUUID: String
    ): FindMessageInsideChatRoomReturnValue {
        return notificationInfoMutex.withLock {

            Log.i(
                "resultInfo",
                "findMessageInsideChatRoom() messageToBeFoundUUID: $messageToBeFoundUUID"
            )

            var indexOfChatRoom = -1
            var indexOfMessage = -1
            var foundMessage = false

            for (i in notificationsList.indices) {
                for (j in notificationsList[i].messages.indices) {
                    if (notificationsList[i].messages[j].messageUUIDPrimaryKey == messageToBeFoundUUID) {
                        foundMessage = true
                        indexOfChatRoom = i
                        indexOfMessage = j
                        break
                    }
                }
                if (foundMessage) {
                    break
                }
            }

            return@withLock FindMessageInsideChatRoomReturnValue(
                foundMessage,
                indexOfChatRoom,
                indexOfMessage
            )
        }
    }

    suspend fun editDeleteMessage(
        applicationContext: Context,
        chatRoomId: String,
        sentByUserName: String,
        modifiedUUID: String,
        modifiedText: String,
        deleteEditNotification: DeleteEditNotification
    ) {
        notificationInfoMutex.withLock {

            var successful = false
            var indexOfChatRoom = -1

            when (deleteEditNotification) {
                DeleteEditNotification.DELETE_NOTIFICATION -> {
                    val returnValue = removeMessageFromNotificationsList(
                        applicationContext,
                        modifiedUUID,
                        chatRoomId,
                    )

                    if (returnValue.messageFound) {
                        successful = true
                        indexOfChatRoom = returnValue.indexOfChatRoom
                    }
                }
                DeleteEditNotification.EDIT_NOTIFICATION -> {
                    val returnVal = findMessageInsideChatRoom(
                        chatRoomId,
                        modifiedUUID
                    )

                    if (returnVal.messageFound) {
                        val indexOfMessage = returnVal.indexOfMessage
                        indexOfChatRoom = returnVal.indexOfChatRoom

                        notificationsList[indexOfChatRoom].messages[indexOfMessage] =
                            MessageWithUUID(
                                notificationsList[indexOfChatRoom].messages[indexOfMessage].messageUUIDPrimaryKey,
                                notificationsList[indexOfChatRoom].messages[indexOfMessage].sentByAccountOID,
                                NotificationCompat.MessagingStyle.Message(
                                    modifiedText,
                                    notificationsList[indexOfChatRoom].messages[indexOfMessage].message.timestamp,
                                    notificationsList[indexOfChatRoom].messages[indexOfMessage].message.person
                                )
                            )

                        successful = true
                    }
                }
            }

            if (successful) {
                //setup heads up display for edited and/or deleted cases
                val remoteView = RemoteViews(
                    applicationContext.packageName,
                    R.layout.view_heads_up_edited_deleted
                )

                val editedDeletedMessage =
                    when (deleteEditNotification) {
                        DeleteEditNotification.DELETE_NOTIFICATION -> {
                            applicationContext.getString(
                                R.string.chat_messages_message_heads_up_edited_deleted,
                                applicationContext.getString(
                                    R.string.deleted
                                )
                            )
                        }
                        DeleteEditNotification.EDIT_NOTIFICATION -> {
                            applicationContext.getString(
                                R.string.chat_messages_message_heads_up_edited_deleted,
                                applicationContext.getString(
                                    R.string.edited
                                )
                            )
                        }
                    }

                val displayedChatRoomName =
                    applicationContext.resources.getQuantityString(
                        R.plurals.chat_message_chat_room_name,
                        notificationsList[indexOfChatRoom].numOtherUsersInChatRoom,
                        notificationsList[indexOfChatRoom].lengthRestrictedChatRoomName
                    )

                if (displayedChatRoomName.isNotEmpty()) {
                    remoteView.setViewVisibility(
                        R.id.viewHeadsUpSecondLineLinearLayout,
                        View.VISIBLE
                    )
                    remoteView.setTextViewText(
                        R.id.viewHeadsUpChatRoomNameTextView,
                        displayedChatRoomName
                    )
                }

                remoteView.setTextViewText(R.id.viewHeadsUpNameTextView, sentByUserName)
                remoteView.setTextViewText(R.id.viewHeadsUpBodyTextView, editedDeletedMessage)

                notificationsList[indexOfChatRoom].notificationBuilder?.setCustomHeadsUpContentView(
                    remoteView
                )

                val sharedPreferences = applicationContext.getSharedPreferences(
                    applicationContext.getString(R.string.shared_preferences_lets_go_key),
                    MultiDexApplication.MODE_PRIVATE
                )

                notifyNotificationsListChanged(
                    applicationContext,
                    sharedPreferences,
                    chatRoomId
                )
            }
        }
    }

    suspend fun removeNotification(
        applicationContext: Context,
        chatRoomId: String,
    ) {
        notificationInfoMutex.withLock {

            Log.i("resultInfo", "removeNotification() running")

            var indexOfChatRoomId = -1

            for (i in notificationsList.indices) {
                if (notificationsList[i].chatRoomId == chatRoomId) {
                    indexOfChatRoomId = i
                    break
                }
            }

            //notifications not existing is possible if say a broadcast receiver was triggered at the same time
            // as clear was running
            if (indexOfChatRoomId != -1) {

                var notificationSummaryNecessary = false

                for (notification in notificationsList) {
                    if (notification.chatRoomId != chatRoomId) {
                        notificationSummaryNecessary = true
                        break
                    }
                }

                //this must be done before removing it from the list and before calling generateSummaryNotification()
                totalNumberMessagesInNotifications -= notificationsList[indexOfChatRoomId].numberNewMessages

                if (notificationSummaryNecessary) { //if at least 1 notification is still present
                    NotificationManagerCompat.from(applicationContext).apply {
                        //cancel previous notification if relevant
                        cancel(notificationsList[indexOfChatRoomId].notificationId)
                    }
                } else { //this was the only remaining notification
                    NotificationManagerCompat.from(applicationContext).apply {
                        //cancel previous notification if relevant
                        cancel(notificationsList[indexOfChatRoomId].notificationId)
                        //cancel summary notification
                        cancel(SUMMARY_NOTIFICATION_ID_VALUE)
                    }
                }

                applicationContext.unregisterReceiver(notificationsList[indexOfChatRoomId].deleteIntentBroadcastReceiver)
                notificationsList[indexOfChatRoomId].deleteIntentBroadcastReceiver = null

                notificationsList.removeAt(indexOfChatRoomId)

                val sharedPreferences = applicationContext.getSharedPreferences(
                    applicationContext.getString(R.string.shared_preferences_lets_go_key),
                    MultiDexApplication.MODE_PRIVATE
                )

                convertNotificationsListToStringsAndStore(sharedPreferences)

                //NOTE: chatRoomIdsRequiringUpdated.add() does not need to be touched here because this runs the cancel() for the
                // notification. chatRoomIdsRequiringUpdated is
            }
        }
    }

    suspend fun clearNotifications(
        applicationContext: Context
    ) {
        notificationInfoMutex.withLock {
            //cancel summary notification if relevant
            NotificationManagerCompat.from(applicationContext)
                .cancel(SUMMARY_NOTIFICATION_ID_VALUE)

            for (notification in notificationsList) {
                //cancel previous notification if relevant
                NotificationManagerCompat.from(applicationContext)
                    .cancel(notification.notificationId)

                applicationContext.unregisterReceiver(notification.deleteIntentBroadcastReceiver)
                notification.deleteIntentBroadcastReceiver = null
            }

            totalNumberMessagesInNotifications = 0
            notificationsList.clear()

            addNotificationHandler.removeCallbacksAndMessages(
                HANDLER_ADD_NOTIFICATION_RUNNABLE_TOKEN
            )

            clearMessagesStringsFromSharedPreferences(
                applicationContext
            )
        }
    }

    private fun clearMessagesStringsFromSharedPreferences(
        applicationContext: Context,
    ) {
        val sharedPreferences = applicationContext.getSharedPreferences(
            applicationContext.getString(R.string.shared_preferences_lets_go_key),
            MultiDexApplication.MODE_PRIVATE
        )

        sharedPreferences.edit().remove(
            SHARED_PREFERENCES_STORED_NOTIFICATION_UUIDS
        ).apply()

        sharedPreferences.edit().remove(
            SHARED_PREFERENCES_STORED_NOTIFICATION_CHAT_ROOM_IDS
        ).apply()
    }

    //generate group summary notification
    //expected to go AFTER the notificationsList is set up
    /** For details on why this is not used see [summary_notification_notes]. **/
    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateSummaryNotification(
        applicationContext: Context,
        currentIndex: Int,
    ): Notification {

        val resultIntent = Intent(applicationContext, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            //NOTE: If there is only one notification, send the user to the chat room id. If more send to
            // the chat room list. There are some issues with how setContentIntent() works for
            // individual notifications in the group vs the summary notification. If setContentIntent()
            // is NOT used for the summary notification, the putExtra() from the individual notifications
            // does not seem to work (if they are combined into a summary) and so the device cannot
            // navigate to the chat room after the app is opened.
            if (notificationsList.size == 1) {
                putExtra(
                    ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY,
                    notificationsList.first().chatRoomId
                )
            } else {
                putExtra(
                    ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY,
                    SEND_TO_CHAT_ROOM_LIST
                )
            }
        }

        //FLAG_UPDATE_CURRENT and setting a unique requestCode are important or the PendingIntents will be re-used with outdated 'Extra' info
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationsList[currentIndex].notificationId,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyleForSummary = NotificationCompat.InboxStyle()

        for (notification in notificationsList) {
            Log.i("buildNotificationStuff", "notification.summaryLine: ${notification.summaryLine}")
            inboxStyleForSummary
                .addLine(notification.summaryLine)
        }

        val summaryContentTitle =
            applicationContext.resources.getQuantityString(
                R.plurals.chat_message_new_messages,
                totalNumberMessagesInNotifications,
                totalNumberMessagesInNotifications
            )

        val acrossChatRooms =
            applicationContext.getString(
                R.string.chat_messages_across_chat_rooms,
                notificationsList.size
            )

        Log.i("buildNotificationStuff", "summaryContentTitle: $summaryContentTitle")

        inboxStyleForSummary
            .setBigContentTitle(acrossChatRooms)
            .setSummaryText(summaryContentTitle)

        return NotificationCompat.Builder(
            applicationContext,
            ChatStreamWorker.CHAT_MESSAGE_CHANNEL_ID
        )
            .setContentTitle(acrossChatRooms)
            //set content text to support devices running API level < 24
            //NOTE: in testing the setContentText() did not show up when an InboxStyle was
            // used, however the above comment line is from google, so setting it
            .setContentText(applicationContext.getString(R.string.chat_messages_across_chat_notification))
            .setSmallIcon(R.drawable.lets_go_icon)
            //build summary info into InboxStyle template
            .setStyle(inboxStyleForSummary)
            .setGroup(ChatStreamWorker.CHAT_MESSAGE_GROUP_ID)
            .setGroupSummary(true)
            .setContentIntent(pendingIntent)
            .build()

    }
}
