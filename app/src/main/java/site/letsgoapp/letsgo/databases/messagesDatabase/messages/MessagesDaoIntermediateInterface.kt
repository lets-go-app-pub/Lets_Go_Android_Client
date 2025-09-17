package site.letsgoapp.letsgo.databases.messagesDatabase.messages

import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.DatabasesToRunTransactionIn
import site.letsgoapp.letsgo.utilities.TransactionWrapper
import type_of_chat_message.TypeOfChatMessageOuterClass

interface MessagesDaoIntermediateInterface {

    //insert single message
    suspend fun insertMessage(messagesDataEntity: MessagesDataEntity)

    //insert multiple messages
    suspend fun insertMultipleMessages(messagesDataEntities: MutableList<MessagesDataEntity>): List<Long>

    //retrieve message_has_been_sent for single message by index
    suspend fun retrieveHasBeenSentStatusByIndex(uuidPrimaryKey: String): ChatMessageStoredStatus

    //returns True if message exists False if not
    suspend fun messageExistsInDatabase(messageUUID: String): Boolean

    //sets the last updated time for a specified chat room
    suspend fun setChatRoomCapMessageStoredTime(chatRoomId: String, newTime: Long)

    //retrieve single message for the passed message uuid
    suspend fun retrieveSingleMessage(messageUUID: String): MessagesDataEntity?

    //retrieve all messages for the passed chat room
    suspend fun retrieveAllMessagesForChatRoom(chatRoomId: String): List<MessagesDataEntity>

    //retrieve all messages with matching UUIDs
    suspend fun requestAllMessageByUUID(messageUUIDs: List<String>): List<MessageFieldsForNotifications>

    //clear all messages from chat room
    suspend fun clearAllMessagesButNotFiles()

    //retrieve all messages that need to be sent to the server
    suspend fun retrieveUnsentMessages(): List<MessagesDataEntity>

    //retrieve all messages that require notifications
    suspend fun retrieveMessagesRequiresNotifications(): List<MessagesDataEntity>

    //sets all messages to trimmed from the passed message UUIDs
    suspend fun setMessagesInListToTrimmed(primaryMessageUUIDs: List<String>)

    //retrieve all messages sent by users list that have amountOfMessage == SKELETON_ONLY
    suspend fun retrieveMessagesSentByUsersThatCanBeTrimmed(sentByAccountOIDS: List<String>): List<MessageFieldsForTrimming>

    //retrieve all messages sent by users list that have amountOfMessage == SKELETON_ONLY and are not sent by the passed accountOIDs
    suspend fun retrieveMessagesThatCanBeTrimmed(notMessageUUIDs: List<String>): List<MessageFieldsForTrimming>

    //retrieve all messages that have a file path attached
    suspend fun retrieveMessageFilePaths(): List<MessageFieldsWithFileNames>

    //retrieve all messages not observed recently
    suspend fun retrieveMessagesNotObservedRecently(): List<MessageFieldsForTrimming>

    //update the file path of the reply
    suspend fun updateReplyIsFromThumbnailFilePath(
        uuidPrimaryKey: String,
        replyIsFromThumbnailFilePath: String
    )

    //remove single message
    /** WARNING: this method is not meant to be used on items that also have other info to store such as PICTURE_MESSAGE, GIF_MESSAGE
     * or messages that have been replied to and store a thumbnail **/
    suspend fun removeSingleMessageByUUIDRaw(
        uuidPrimaryKey: String
    )

    //remove single message
    suspend fun removeSingleMessageByUUID(
        uuidPrimaryKey: String,
        removePicture: suspend (String) -> Unit,
        removeGifFile: suspend (String) -> Unit,
        transactionWrapper: TransactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.MESSAGES
        )
    )

    //remove all messages for passed chat room
    /** transactionWrapper requires MessagesDatabase to be locked **/
    suspend fun removeAllMessagesForChatRoom(
        chatRoomId: String,
        removePicture: suspend (String) -> Unit,
        removeGifFile: suspend (String) -> Unit,
        removeMessageCap: Boolean,
        transactionWrapper: TransactionWrapper
    )

    //remove all messages of type UPDATE_CHAT_ROOM_OBSERVED_TIME_VALUE
    suspend fun removeAllMessagesOfPassedTypeType(chatRoomId: String, updateChatRoomTypeValue: Int)

    //update the specific message to be 'server received' status
    suspend fun updateMessageToReceivedByServer(
        uuidPrimaryKey: String,
        messageStoredTime: Long,
    )

    //update the specific message to be message_requires_notification = false
    suspend fun updateMessageToDoesNotRequireNotifications(uuidPrimaryKey: String)

    //update all messages to be message_requires_notification = false
    suspend fun updateAllMessagesToDoNotRequireNotifications()

    //update the message to have a new file path
    suspend fun updatePictureInfo(
        uuidPrimaryKey: String,
        filePath: String,
        pictureHeight: Int,
        pictureWidth: Int
    )

    //finds the final GlobalValues.maxNumberMessagesUserCanRequest number of messages that require updates
    // for the passed chat room Id and returns a list of the messageUUIDs, (will return empty list if none
    // of the messages require updated)
    suspend fun getFinalMessagesRequiringUpdatesInChatRoom(chatRoomId: String): List<String>

    //get the most recently stored message for the passed chat room
    suspend fun getMostRecentMessageInChatRoom(chatRoomId: String): MostRecentMessageDataHolder?

    //gets the chat room last activity time taking blocked accounts into consideration
    suspend fun getLastActivityTimeNotIncludingBlocked(chatRoomId: String): Long

    //get the most recently stored message time (stored on server time if it exists stored in database time) for all chat rooms
    suspend fun getMostRecentMessageForEachChatRoomIncludingBlocking(): List<MostRecentMessageData>

    //get the most recently stored messages after the passed time
    suspend fun getMessagesWithinRecentTimeForEachChatRoomIncludingBlocking(earliestTimestamp: Long): List<MostRecentMessageData>

    //get the most recently stored message time (stored on server time if it exists stored in database time) if not for the passed chat room
    suspend fun getMostRecentMessageStoredTimeForAllChatRooms(): MostRecentMessageWithSentByOidData

    //update the specific message to be edited but the edit has not been sent
    suspend fun updateMessageToEditedButNotSentByServer(messageUUID: String, newMessageText: String)

    //update the specific message to be edited and the edit has been sent
    suspend fun updateMessageToEditedAndSentByServer(
        messageUUID: String,
        amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage,
        hasCompleteInfo: Boolean,
        newMessageText: String,
        messageEditedTime: Long,
        editHasBeenSent: Boolean
    )

    //update the specific message to be edited but the edit has not been sent
    suspend fun updateEditHasBeenSentToTrue(messageUUID: String)

    //update the message to be expired (only relevant to invite message type)
    suspend fun updateInviteMessageToExpired(uuidPrimaryKey: String)

    //updates time_user_last_observed_message to the passed timestamp for all message in list
    suspend fun updateTimestampsForPassedMessageUUIDs(
        messageUUIDPrimaryKeys: Set<String>,
        timestampObserved: Long
    )

    suspend fun updateTextMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        messageText: String,
        isEdited: Boolean,
        editedTime: Long,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo
    ): MessagesDataEntity?

    suspend fun updatePictureMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        pictureFilePath: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity?

    suspend fun updateLocationMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        longitude: Double,
        latitude: Double,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity?

    suspend fun updateMimeTypeMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        urlOfDownload: String,
        mimeType: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity?

    suspend fun updateInviteMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        invitedUserAccountOid: String,
        invitedUserName: String,
        chatRoomId: String,
        chatRoomName: String,
        chatRoomPassword: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity?

    //returns a list of chat room Ids that match the passed criteria
    suspend fun searchForChatRoomMatches(matchingString: String): Set<String>
}