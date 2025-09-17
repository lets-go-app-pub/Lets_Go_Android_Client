package site.letsgoapp.letsgo.workers.chatStreamWorker

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.ReturnForNotificationMessage
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import type_of_chat_message.TypeOfChatMessageOuterClass

object NotificationsSharedFunctions {

    data class MatchesMadeDataHolder(
        val chatRoomId: String,
        val userAccountOID: String
    )

    fun extractNotificationTime(passedStoredOnServerTimestamp: Long?): Long {
        return passedStoredOnServerTimestamp.let {
            if (it != null && 1 < it) { //valid time
                it
            } else { //invalid time
                System.currentTimeMillis()
            }
        }
    }

    fun extractInfoFromMatchMadeUUID(
        matchMadeUUID: String
    ): MatchesMadeDataHolder {

        val uuidSplitString = Regex(NotificationInfo.MATCH_TYPE_MESSAGE_UUID_SEPARATOR)

        val uuidList = matchMadeUUID.split(uuidSplitString).filter {
            it.isNotEmpty()
        }

        if(uuidList.size < 3) {
            val errorMessage =
                "When attempting to extract a match made message from shared preferences, not all info was found.\n" +
                        "matchMadeUUID: ${matchMadeUUID}\n" +
                        "uuidList: ${uuidList}\n"

            ServiceLocator.globalErrorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            return MatchesMadeDataHolder("", "")
        }

        //NOTE: uuidList[0] is a prefix to identify a match made type id. It is not needed here.

        return MatchesMadeDataHolder(
            uuidList[1],
            uuidList[2]
        )
    }

    private fun generateMatchMadeUUID(
        chatRoomId: String,
        matchingAccountOID: String,
    ): String {
        return ChatStreamWorker.MATCH_TYPE_MESSAGE_UUID + NotificationInfo.MATCH_TYPE_MESSAGE_UUID_SEPARATOR +
                chatRoomId + NotificationInfo.MATCH_TYPE_MESSAGE_UUID_SEPARATOR + matchingAccountOID
    }

    fun generateNotificationForMatch(
        applicationContext: Context,
        chatRoomId: String,
        matchingAccountOID: String,
        timeJoinedChatRoom: Long,
        rawUserName: String,
        personMessageSentBy: Person
    ): NotificationInfo.MessageWithUUID {

        val notificationTime =
            extractNotificationTime(timeJoinedChatRoom)

        val userName =
            if(rawUserName.isBlank() || rawUserName == "~") {
                applicationContext.getString(R.string.User)
            } else {
                rawUserName
            }

        val messageText = applicationContext.getString(
            R.string.chat_message_match_has_been_made,
            userName
        )

        val notificationStyleMessage = NotificationCompat.MessagingStyle.Message(
            messageText, notificationTime, personMessageSentBy
        )

        return NotificationInfo.MessageWithUUID(
            generateMatchMadeUUID(chatRoomId, matchingAccountOID),
            matchingAccountOID,
            notificationStyleMessage
        )
    }

    fun generateNotificationSingleMessage(
        applicationContext: Context,
        otherUserSentBy: String,
        messageUUIDPrimaryKey: String,
        messageText: String,
        passedStoredOnServerTimestamp: Long?,
    ): NotificationInfo.MessageWithUUID {

        //This will display a user named "Chat Room" so that the messages that represent something can be
        // displayed without a user, however it is a bit 'hacky' and a directly supported alternative
        // would be better
        //If using an empty drawable like empty_shape, there is no way to differentiate this message from
        // previous messages sent by a different user, however not 100% sure that the globe_logo itself is
        // the best substitute
        //The name for this must be non-empty, so it cannot be "" or null
        //The key used for a new match is the chatRoomId, so don't use that again here
        val emptyPerson = Person.Builder()
            .setKey("single_message_key")
            .setIcon(
                IconCompat.createWithResource(
                    applicationContext,
                    R.drawable.lets_go_icon
                )
            )
            .setName(applicationContext.getString(R.string.Chat_room))
            .build()

        val storedOnServerTime = extractNotificationTime(passedStoredOnServerTimestamp)

        val notificationStyleMessage = NotificationCompat.MessagingStyle.Message(
            messageText, storedOnServerTime, emptyPerson
        )

        return NotificationInfo.MessageWithUUID(
            messageUUIDPrimaryKey,
            otherUserSentBy,
            notificationStyleMessage
        )
    }

    suspend fun buildPersonForNotificationMessage(
        applicationContext: Context,
        sentByUserAccountOID: String,
        passedRequiredUserInfo: ReturnForNotificationMessage,
        setKey: String = sentByUserAccountOID
    ): Person {

        val coroutineConditionVariable = MakeshiftCoroutineConditionVariable()
        var useThumbnailBitmap: Bitmap? = null

        val glideFuture = GlideApp.with(applicationContext)
            .asBitmap()
            .apply(RequestOptions.circleCropTransform())
            .load(passedRequiredUserInfo.thumbnail_path)
            .signature(generateFileObjectKey(passedRequiredUserInfo.thumbnail_last_time_updated))
            .listener(object : RequestListener<Bitmap?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    isFirstResource: Boolean,
                ): Boolean {
                    coroutineConditionVariable.notifyOne()
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean,
                ): Boolean {
                    useThumbnailBitmap = resource
                    coroutineConditionVariable.notifyOne()
                    return false
                }
            }
            ).submit()

        coroutineConditionVariable.wait(3000)
        glideFuture.cancel(true)

        val iconInfo =
            if (useThumbnailBitmap == null) {
                val errorString =
                    "Thumbnail for bitmap either timed out or failed to load\n"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorString,
                    applicationContext
                )

                IconCompat.createWithResource(
                    applicationContext, R.drawable.icon_round_person_large_24
                )
            } else {
                IconCompat.createWithBitmap(
                    useThumbnailBitmap!!
                )
            }

        val userName =
            passedRequiredUserInfo.name.ifEmpty {
                //NOTE: Person must have a non empty name to be used for NotificationCompat.MessagingStyle
                applicationContext.resources.getString(R.string.User)
            }

        return Person.Builder()
            .setName(userName)
            .setKey(setKey)
            .setIcon(iconInfo)
            .build()
    }

    //returns null if a message text could not be generated
    fun generateNotificationMessageText(
        applicationContext: Context,
        messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase,
        hasCompleteInfo: Boolean = false, //only needed for certain message types
        rawMessageText: String = "", //only needed for certain message types
        userName: String = "" //only needed for certain message types
    ): String? {

        return when (messageType) {
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                //It is possible for the string to have trailing whitespace if the full message info
                // was not sent back and only enough to display final message was.
                //While leading whitespace is allowed, it will fill up the notification quickly, so
                // removing it for readability purposes
                val messageValue = rawMessageText.trim()

                //make sure message text is not too long (no reason to store absurd messages in the
                // background in memory)
                val finalMessageValue =
                    if (!hasCompleteInfo
                        && messageValue.isNotEmpty()
                    ) {
                        "${messageValue}..."
                    } else if (hasCompleteInfo) {
                        val trimmedMessage =
                            ByteLengthFilter.extractStringOfProperSizeFromBytes(
                                0,
                                messageValue.toByteArray(),
                                GlobalValues.server_imported_values.maximumNumberBytesTrimmedTextMessage
                            )
                        if (trimmedMessage < messageValue) {
                            //No need to keep trailing white space
                            "${trimmedMessage.trimEnd()}..."
                        } else {
                            messageValue
                        }
                    } else {
                        messageValue
                    }

                if (messageValue.isEmpty()) {
                    //possible if the user sends something like maximumNumberBytesTrimmedTextMessage number of
                    // leading spaces
                    "..."
                } else {
                    finalMessageValue
                }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
                applicationContext.resources.getString(
                    R.string.chat_message_type_text_location
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
                generateMessageForMimeType(
                    applicationContext,
                    rawMessageText
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_type_text_invite)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_type_text_picture)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_new_admin_promoted)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_chat_room_name_updated)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_chat_room_password_updated)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                applicationContext.resources.getString(R.string.chat_message_chat_room_pinned_location_updated)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE -> {
                "" //unused, do not return null here it is not an error
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
            -> {
                if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE) { //user joined chat room message
                    applicationContext.resources.getString(
                        R.string.chat_message_different_user_joined_chat_room,
                        userName
                    )
                } else { //user left chat room message
                    applicationContext.resources.getString(
                        R.string.chat_message_different_user_left_chat_room,
                        userName
                    )
                }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
            -> {
                if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE) {
                    applicationContext.resources.getString(
                        R.string.chat_message_user_kicked_chat_room,
                        userName
                    )
                } else {
                    applicationContext.resources.getString(
                        R.string.chat_message_user_banned_chat_room,
                        userName
                    )
                }
            }
            //NOTE: it is set up this way instead of using else so that if
            // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
            // a warning will be thrown
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET
            -> {
                val errorString =
                    "Invalid message type returned inside returnMessagesForChatRoom\n" +
                            "messageType: $messageType\n"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorString,
                    applicationContext
                )

                null
            }
        }
    }

}