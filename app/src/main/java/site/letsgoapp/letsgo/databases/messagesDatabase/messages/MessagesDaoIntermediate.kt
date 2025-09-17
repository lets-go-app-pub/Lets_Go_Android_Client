package site.letsgoapp.letsgo.databases.messagesDatabase.messages

import chat_message_to_client.ChatMessageToClientMessage
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

class MessagesDaoIntermediate(
    private val messagesDatabaseDao: MessagesDatabaseDao,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val errorStore: StoreErrorsInterface,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : MessagesDaoIntermediateInterface {

    //insert single message
    override suspend fun insertMessage(messagesDataEntity: MessagesDataEntity) {
        messagesDatabaseDao.insertMessage(messagesDataEntity)
    }

    //insert multiple messages
    override suspend fun insertMultipleMessages(messagesDataEntities: MutableList<MessagesDataEntity>): List<Long> {
        return messagesDatabaseDao.insertMultipleMessages(messagesDataEntities)
    }

    override suspend fun messageExistsInDatabase(messageUUID: String): Boolean {
        return messagesDatabaseDao.messageExistsInDatabase(messageUUID) == 1
    }

    override suspend fun setChatRoomCapMessageStoredTime(chatRoomId: String, newTime: Long) {
        return messagesDatabaseDao.setChatRoomCapMessageStoredTime(chatRoomId, newTime)
    }

    override suspend fun retrieveSingleMessage(messageUUID: String): MessagesDataEntity? {
        return messagesDatabaseDao.retrieveSingleMessage(messageUUID)
    }

    //retrieve message_has_been_sent for single message by index
    override suspend fun retrieveHasBeenSentStatusByIndex(uuidPrimaryKey: String): ChatMessageStoredStatus {
        return ChatMessageStoredStatus.setVal(
            messagesDatabaseDao.retrieveHasBeenSentStatusByIndex(uuidPrimaryKey)
        )
    }

    //retrieve all messages for the passed chat room
    override suspend fun retrieveAllMessagesForChatRoom(chatRoomId: String): List<MessagesDataEntity> {
        return messagesDatabaseDao.retrieveAllMessagesForChatRoom(chatRoomId)
    }

    //retrieve all messages with matching UUIDs
    override suspend fun requestAllMessageByUUID(messageUUIDs: List<String>): List<MessageFieldsForNotifications> {
        if(messageUUIDs.isEmpty())
            return emptyList()

        return messagesDatabaseDao.requestAllMessageByUUID(messageUUIDs)
    }

    //retrieve all message that need to be sent to the server
    override suspend fun retrieveUnsentMessages(): List<MessagesDataEntity> {
        return messagesDatabaseDao.retrieveUnsentMessages()
    }

    //retrieve all message that require notifications
    override suspend fun retrieveMessagesRequiresNotifications(): List<MessagesDataEntity> {
        return messagesDatabaseDao.retrieveMessagesRequiresNotifications()
    }

    //sets all messages to trimmed from the passed message UUIDs
    override suspend fun setMessagesInListToTrimmed(primaryMessageUUIDs: List<String>) {
        messagesDatabaseDao.setMessagesInListToTrimmed(primaryMessageUUIDs)
    }

    //retrieve all messages sent by users list that have amountOfMessage == SKELETON_ONLY
    override suspend fun retrieveMessagesSentByUsersThatCanBeTrimmed(sentByAccountOIDS: List<String>): List<MessageFieldsForTrimming> {
        return messagesDatabaseDao.retrieveMessagesSentByUsersThatCanBeTrimmed(sentByAccountOIDS)
    }

    //retrieve all messages sent by users list that have amountOfMessage == SKELETON_ONLY and are not sent by the passed accountOIDs
    override suspend fun retrieveMessagesThatCanBeTrimmed(notMessageUUIDs: List<String>): List<MessageFieldsForTrimming> {
        return messagesDatabaseDao.retrieveMessagesThatCanBeTrimmed(notMessageUUIDs)
    }

    //retrieve all messages that have a file path attached
    override suspend fun retrieveMessageFilePaths(): List<MessageFieldsWithFileNames> {
        return messagesDatabaseDao.retrieveMessageFilePaths()
    }

    //retrieve all messages not observed recently
    override suspend fun retrieveMessagesNotObservedRecently(): List<MessageFieldsForTrimming> {
        val earliestTimestamp =
            getCurrentTimestampInMillis() - GlobalValues.server_imported_values.timeInfoHasNotBeenObservedBeforeCleaned
        return messagesDatabaseDao.retrieveMessagesNotObservedRecently(earliestTimestamp)
    }

    //update the file path of the reply
    override suspend fun updateReplyIsFromThumbnailFilePath(
        uuidPrimaryKey: String,
        replyIsFromThumbnailFilePath: String,
    ) {
        return messagesDatabaseDao.updateReplyIsFromThumbnailFilePath(
            uuidPrimaryKey,
            replyIsFromThumbnailFilePath
        )
    }

    //remove single message
    /** WARNING: this method is not meant to be used on items that also have other info to store such as PICTURE_MESSAGE, GIF_MESSAGE
     * or messages that have been replied to and store a thumbnail **/
    override suspend fun removeSingleMessageByUUIDRaw(
        uuidPrimaryKey: String,
    ) {
        messagesDatabaseDao.removeSingleMessageByUUID(uuidPrimaryKey)
    }

    //remove single message
    //WARNING: this method is not meant to be used on items that also have other info to store such as PICTURE_MESSAGE, GIF_MESSAGE
    // or messages that have been replied to and store a thumbnail
    override suspend fun removeSingleMessageByUUID(
        uuidPrimaryKey: String,
        removePicture: suspend (String) -> Unit,
        removeGifFile: suspend (String) -> Unit,
        transactionWrapper: TransactionWrapper,
    ) {
        transactionWrapper.runTransaction {

            val message = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)

            transactionWrapper.runAfterTransaction {
                message?.let {
                    removeFilesForMessage(message, removePicture, removeGifFile)
                }
            }

            messagesDatabaseDao.removeSingleMessageByUUID(uuidPrimaryKey)
        }
    }

    private suspend fun removeFilesForMessage(
        message: MessagesDataEntity,
        removePicture: suspend (String) -> Unit,
        removeGifFile: suspend (String) -> Unit,
    ) {

        if (message.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE.number) {
            message.filePath.apply {
                if (this != "" && this != "~") removePicture(this)
            }
        } else if (message.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE.number) {
            message.downloadUrl.apply {
                if (this != "" && this != "~") removeGifFile(this)
            }
        }

        if (message.isReply) {
            message.replyIsFromThumbnailFilePath.apply {
                if (this != "" && this != "~") removePicture(this)
            }
        }
    }

    //retrieve all messages, remove any files associated with them and remove all messages from the chat room
    //NOTE: this calls 2 database commands and so it should be called by a synchronized function
    override suspend fun removeAllMessagesForChatRoom(
        chatRoomId: String,
        removePicture: suspend (String) -> Unit,
        removeGifFile: suspend (String) -> Unit,
        removeMessageCap: Boolean,
        transactionWrapper: TransactionWrapper,
    ) {
        transactionWrapper.runTransaction {
            val messages = messagesDatabaseDao.retrieveAllMessagesForChatRoom(chatRoomId)

            transactionWrapper.runAfterTransaction {
                for (message in messages) {
                    removeFilesForMessage(message, removePicture, removeGifFile)
                }
            }

            messagesDatabaseDao.apply {
                if (removeMessageCap) {
                    removeAllMessagesForChatRoom(chatRoomId)
                } else {
                    removeAllMessagesForChatRoomExceptCap(chatRoomId)
                }
            }
        }
    }

    //remove all messages for the database
    override suspend fun clearAllMessagesButNotFiles() {
        messagesDatabaseDao.removeAllMessages()
    }

    override suspend fun removeAllMessagesOfPassedTypeType(
        chatRoomId: String,
        updateChatRoomTypeValue: Int,
    ) {
        messagesDatabaseDao.removeAllMessagesOfPassedTypeType(
            chatRoomId,
            updateChatRoomTypeValue
        )
    }

    //update a specific message generated by this client to 'server received message'
    override suspend fun updateMessageToReceivedByServer(
        uuidPrimaryKey: String,
        messageStoredTime: Long,
    ) {

        //this will update the messageOID to the value returned by the server
        messagesDatabaseDao.updateMessageToReceivedByServer(
            uuidPrimaryKey,
            messageStoredTime,
        )
    }

    //update the specific message to be message_requires_notification = false
    override suspend fun updateMessageToDoesNotRequireNotifications(uuidPrimaryKey: String) {
        messagesDatabaseDao.updateMessageToDoesNotRequireNotifications(uuidPrimaryKey)
    }

    //update all messages to be message_requires_notification = false
    override suspend fun updateAllMessagesToDoNotRequireNotifications() {
        messagesDatabaseDao.updateAllMessagesToDoNotRequireNotifications()
    }

    //update the picture type message to have a new file path
    override suspend fun updatePictureInfo(
        uuidPrimaryKey: String,
        filePath: String,
        pictureHeight: Int,
        pictureWidth: Int,
    ) {
        messagesDatabaseDao.updatePictureInfo(
            uuidPrimaryKey,
            filePath,
            pictureHeight,
            pictureWidth
        )
    }

    override suspend fun getFinalMessagesRequiringUpdatesInChatRoom(chatRoomId: String): List<String> {

        val returnedThingy = messagesDatabaseDao.getFinalMessagesInChatRoomDownloadedState(
            chatRoomId
        )

        return returnedThingy.fold(mutableListOf()) { acc, messageDownloadedState ->
            if (!messageDownloadedState.has_complete_info) {
                acc.add(messageDownloadedState.messageUUIDPrimaryKey)
            }
            acc
        }
    }

    override suspend fun getMostRecentMessageInChatRoom(chatRoomId: String): MostRecentMessageDataHolder? {
        return messagesDatabaseDao.getMostRecentMessageInChatRoom(
            chatRoomId
        )
    }

    override suspend fun getLastActivityTimeNotIncludingBlocked(chatRoomId: String): Long {
        return messagesDatabaseDao.getLastActivityTimeNotIncludingBlocked(
            chatRoomId
        ) ?: -1L
    }

    //get the most recently stored message time (stored on server time if it exists stored in database time) for all chat rooms
    override suspend fun getMostRecentMessageForEachChatRoomIncludingBlocking(): List<MostRecentMessageData> {
        return messagesDatabaseDao.getMostRecentMessageForEachChatRoomIncludingBlocking()
    }

    //get the most recently stored messages after the passed time
    override suspend fun getMessagesWithinRecentTimeForEachChatRoomIncludingBlocking(earliestTimestamp: Long): List<MostRecentMessageData> {
        return messagesDatabaseDao.getRecentMessagesForEachChatRoomIncludingBlocking(earliestTimestamp)
    }

    //get the most recently stored message time (stored on server time if it exists stored in database time) if not for the passed chat room
    override suspend fun getMostRecentMessageStoredTimeForAllChatRooms(): MostRecentMessageWithSentByOidData {
        return messagesDatabaseDao.getMostRecentMessageStoredOnServerTimeForAllChatRooms()
            ?: MostRecentMessageWithSentByOidData()
    }

    override suspend fun updateMessageToEditedButNotSentByServer(
        messageUUID: String,
        newMessageText: String,
    ) {
        if (messageUUID.isValidUUIDKey()) { //if this is a proper OID
            messagesDatabaseDao.updateMessageToEditedButNotSentByServer(
                messageUUID,
                newMessageText
            )
        }
    }

    override suspend fun updateMessageToEditedAndSentByServer(
        messageUUID: String,
        amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage,
        hasCompleteInfo: Boolean,
        newMessageText: String,
        messageEditedTime: Long,
        editHasBeenSent: Boolean,
    ) {
        if (messageUUID.isValidUUIDKey()) { //if this is a proper OID
            messagesDatabaseDao.updateTextMessageToEdited(
                messageUUID,
                amountOfMessage.number,
                hasCompleteInfo,
                newMessageText,
                messageEditedTime,
                editHasBeenSent
            )
        }
    }

    override suspend fun updateEditHasBeenSentToTrue(messageUUID: String) {
        if (messageUUID.isValidUUIDKey()) { //if this is a proper OID
            messagesDatabaseDao.updateEditHasBeenSentToTrue(messageUUID)
        }
    }

    override suspend fun updateInviteMessageToExpired(uuidPrimaryKey: String) {
        messagesDatabaseDao.updateInviteMessageToExpired(uuidPrimaryKey)
    }

    //updates time_user_last_observed_message to the passed timestamp for all message in list
    override suspend fun updateTimestampsForPassedMessageUUIDs(
        messageUUIDPrimaryKeys: Set<String>,
        timestampObserved: Long
    ) {
        messagesDatabaseDao.updateTimestampsForPassedMessageUUIDs(
            messageUUIDPrimaryKeys,
            timestampObserved
        )
    }

    private fun setupReplyForMessage(
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
        uuidPrimaryKey: String,
    ): ReplyHolderDataClass {

        var isReply = false
        var replyIsSentFromOid = ""
        var replyIsFromUUID = ""
        var replyType = -1
        var replyIsFromMessageText = ""
        var replyIsFromMimeType = ""
        var replyIsFromThumbnailFilePath = ""

        //just because isReply is set to true does not mean the reply was sent back with
        // the message, amountOfMessage must also be COMPLETE_MESSAGE_INFO
        if (activeMessageInfo.hasReplyInfo()) {

            isReply = true
            replyIsSentFromOid = activeMessageInfo.replyInfo.replyIsSentFromUserOid
            replyIsFromUUID = activeMessageInfo.replyInfo.replyIsToMessageUuid
            replyType = activeMessageInfo.replyInfo.replySpecifics.replyBodyCase.number

            when (activeMessageInfo.replyInfo.replySpecifics.replyBodyCase) {
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {
                    replyIsFromMessageText =
                        activeMessageInfo.replyInfo.replySpecifics.textReply.messageText
                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {

                    replyIsFromThumbnailFilePath = generateAndSaveMessageReplyFile(
                        activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailInBytes,
                        activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailFileSize,
                        uuidPrimaryKey,
                        deleteFileInterface,
                        errorStore
                    )

                    replyIsFromMimeType =
                        activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailMimeType

                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> {

                    replyIsFromThumbnailFilePath = generateAndSaveMessageReplyFile(
                        activeMessageInfo.replyInfo.replySpecifics.pictureReply.thumbnailInBytes,
                        activeMessageInfo.replyInfo.replySpecifics.pictureReply.thumbnailFileSize,
                        uuidPrimaryKey,
                        deleteFileInterface,
                        errorStore
                    )
                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY,
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY,
                -> {
                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET,
                null,
                -> {

                    isReply = false
                    replyIsSentFromOid = ""
                    replyIsFromUUID = ""
                    replyType = -1

                    val errorMessage =
                        "Invalid reply type set inside of ReplySpecifics when hasReplyInfo()==true.\n" +
                                "replyBodyCase: ${activeMessageInfo.replyInfo.replySpecifics.replyBodyCase}\n" +
                                "replyInfo: ${activeMessageInfo.replyInfo}\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                }
            }
        }

        return ReplyHolderDataClass(
            isReply,
            replyIsSentFromOid,
            replyIsFromUUID,
            replyType,
            replyIsFromMessageText,
            replyIsFromMimeType,
            replyIsFromThumbnailFilePath,
        )
    }

    override suspend fun updateTextMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        messageText: String,
        isEdited: Boolean,
        editedTime: Long,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity? {

        val replyHolderDataClass = setupReplyForMessage(activeMessageInfo, uuidPrimaryKey)

        val transactionWrapper =
            ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES
            )

        var messagesDataEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            messagesDatabaseDao.updateTextMessageSpecifics(
                uuidPrimaryKey,
                amountOfMessage,
                hasCompleteInfo,
                messageText,
                getCurrentTimestampInMillis(), //update observed time, or it can be removed immediately afterwards by CleanDatabaseWorker
                isEdited,
                editedTime,
                replyHolderDataClass.isReply,
                replyHolderDataClass.replyIsSentFromOid,
                replyHolderDataClass.replyIsFromUUID,
                replyHolderDataClass.replyType,
                replyHolderDataClass.replyIsFromMessageText,
                replyHolderDataClass.replyIsFromMimeType,
                replyHolderDataClass.replyIsFromThumbnailFilePath
            )

            messagesDataEntity = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)
        }

        return messagesDataEntity
    }

    override suspend fun updatePictureMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        pictureFilePath: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity? {

        val replyHolderDataClass = setupReplyForMessage(activeMessageInfo, uuidPrimaryKey)

        val transactionWrapper =
            ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES
            )

        var messagesDataEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            messagesDatabaseDao.updatePictureMessageSpecifics(
                uuidPrimaryKey,
                amountOfMessage,
                hasCompleteInfo,
                imageHeight,
                imageWidth,
                pictureFilePath,
                getCurrentTimestampInMillis(), //update observed time, or it can be removed immediately afterwards by CleanDatabaseWorker
                replyHolderDataClass.isReply,
                replyHolderDataClass.replyIsSentFromOid,
                replyHolderDataClass.replyIsFromUUID,
                replyHolderDataClass.replyType,
                replyHolderDataClass.replyIsFromMessageText,
                replyHolderDataClass.replyIsFromMimeType,
                replyHolderDataClass.replyIsFromThumbnailFilePath
            )

            messagesDataEntity = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)
        }

        return messagesDataEntity
    }

    override suspend fun updateLocationMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        longitude: Double,
        latitude: Double,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity? {

        val replyHolderDataClass = setupReplyForMessage(activeMessageInfo, uuidPrimaryKey)

        val transactionWrapper =
            ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES
            )

        var messagesDataEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            messagesDatabaseDao.updateLocationMessageSpecifics(
                uuidPrimaryKey,
                amountOfMessage,
                hasCompleteInfo,
                longitude,
                latitude,
                getCurrentTimestampInMillis(), //update observed time, or it can be removed immediately afterwards by CleanDatabaseWorker
                replyHolderDataClass.isReply,
                replyHolderDataClass.replyIsSentFromOid,
                replyHolderDataClass.replyIsFromUUID,
                replyHolderDataClass.replyType,
                replyHolderDataClass.replyIsFromMessageText,
                replyHolderDataClass.replyIsFromMimeType,
                replyHolderDataClass.replyIsFromThumbnailFilePath
            )

            messagesDataEntity = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)
        }

        return messagesDataEntity

    }

    override suspend fun updateMimeTypeMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        urlOfDownload: String,
        mimeType: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity? {

        val replyHolderDataClass = setupReplyForMessage(activeMessageInfo, uuidPrimaryKey)

        val transactionWrapper =
            ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES
            )

        var messagesDataEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            messagesDatabaseDao.updateMimeTypeMessageSpecifics(
                uuidPrimaryKey,
                amountOfMessage,
                hasCompleteInfo,
                imageHeight,
                imageWidth,
                getCurrentTimestampInMillis(), //update observed time, or it can be removed immediately afterwards by CleanDatabaseWorker
                urlOfDownload,
                mimeType,
                replyHolderDataClass.isReply,
                replyHolderDataClass.replyIsSentFromOid,
                replyHolderDataClass.replyIsFromUUID,
                replyHolderDataClass.replyType,
                replyHolderDataClass.replyIsFromMessageText,
                replyHolderDataClass.replyIsFromMimeType,
                replyHolderDataClass.replyIsFromThumbnailFilePath
            )

            messagesDataEntity = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)
        }

        return messagesDataEntity

    }

    override suspend fun updateInviteMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        invitedUserAccountOid: String,
        invitedUserName: String,
        chatRoomId: String,
        chatRoomName: String,
        chatRoomPassword: String,
        activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    ): MessagesDataEntity? {

        val replyHolderDataClass = setupReplyForMessage(activeMessageInfo, uuidPrimaryKey)

        val transactionWrapper =
            ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES
            )

        var messagesDataEntity: MessagesDataEntity? = null

        transactionWrapper.runTransaction {

            messagesDatabaseDao.updateInviteMessageSpecifics(
                uuidPrimaryKey,
                amountOfMessage,
                hasCompleteInfo,
                invitedUserAccountOid,
                invitedUserName,
                chatRoomId,
                chatRoomName,
                chatRoomPassword,
                getCurrentTimestampInMillis(), //update observed time, or it can be removed immediately afterwards by CleanDatabaseWorker
                replyHolderDataClass.isReply,
                replyHolderDataClass.replyIsSentFromOid,
                replyHolderDataClass.replyIsFromUUID,
                replyHolderDataClass.replyType,
                replyHolderDataClass.replyIsFromMessageText,
                replyHolderDataClass.replyIsFromMimeType,
                replyHolderDataClass.replyIsFromThumbnailFilePath
            )

            messagesDataEntity = messagesDatabaseDao.retrieveSingleMessageByUUID(uuidPrimaryKey)
        }

        return messagesDataEntity

    }

    //returns a list of chat room Ids that match the passed criteria
    override suspend fun searchForChatRoomMatches(matchingString: String): Set<String> {
        return messagesDatabaseDao.searchForChatRoomMatches(matchingString).toSet()
    }
}

data class ReplyHolderDataClass(
    val isReply: Boolean,
    val replyIsSentFromOid: String,
    val replyIsFromUUID: String,
    val replyType: Int,
    val replyIsFromMessageText: String,
    val replyIsFromMimeType: String,
    val replyIsFromThumbnailFilePath: String,
)

private fun generateActiveMessageInfo(
    messageEntity: MessagesDataEntity,
    thumbnailForReply: ByteArray,
    errorStore: StoreErrorsInterface
): TypeOfChatMessageOuterClass.ActiveMessageInfo {

    val activeMessageInfoBuilder =
        TypeOfChatMessageOuterClass.ActiveMessageInfo.newBuilder()
            .setIsDeleted(false)

    if (messageEntity.isReply) {
        activeMessageInfoBuilder.isReply = true

        val replySpecificsBuilder = TypeOfChatMessageOuterClass.ReplySpecifics.newBuilder()

        when (val replyType =
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.forNumber(messageEntity.replyType)) {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {
                replySpecificsBuilder.textReply =
                    TypeOfChatMessageOuterClass.TextChatReplyMessage.newBuilder()
                        .setMessageText(messageEntity.replyIsFromMessageText)
                        .build()
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> {
                replySpecificsBuilder.pictureReply =
                    TypeOfChatMessageOuterClass.PictureReplyMessage.newBuilder()
                        .setThumbnailInBytes(ByteString.copyFrom(thumbnailForReply))
                        .setThumbnailFileSize(thumbnailForReply.size)
                        .build()
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY -> {
                replySpecificsBuilder.locationReply =
                    TypeOfChatMessageOuterClass.LocationReplyMessage.newBuilder().build()
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {
                replySpecificsBuilder.mimeReply =
                    TypeOfChatMessageOuterClass.MimeTypeReplyMessage.newBuilder()
                        .setThumbnailInBytes(ByteString.copyFrom(thumbnailForReply))
                        .setThumbnailFileSize(thumbnailForReply.size)
                        .setThumbnailMimeType(messageEntity.replyIsFromMimeType)
                        .build()
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY -> {
                replySpecificsBuilder.inviteReply =
                    TypeOfChatMessageOuterClass.InvitedReplyMessage.newBuilder().build()
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET,
            null,
            -> {

                val errorMessage =
                    "Invalid reply type set inside of MessagesDataEntity when isReply==true.\n" +
                            "replyType: $replyType\n" +
                            "messageEntity: ${messageEntity}\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                activeMessageInfoBuilder.isReply = false
                return activeMessageInfoBuilder.build()
            }
        }

        activeMessageInfoBuilder.replyInfo =
            TypeOfChatMessageOuterClass.ReplyChatMessageInfo.newBuilder()
                .setReplyIsSentFromUserOid(messageEntity.replyIsSentFromOID)
                .setReplyIsToMessageUuid(messageEntity.replyIsFromMessageUUID)
                .setReplySpecifics(replySpecificsBuilder.build())
                .build()

    } else {
        activeMessageInfoBuilder.isReply = false
    }

    return activeMessageInfoBuilder.build()
}

class InvalidMessagePassedException(message: String) : Exception(message)

fun convertMessageDataEntityToTypeOfChatMessage(
    messageEntity: MessagesDataEntity,
    thumbnailForReply: ByteArray,
    errorStore: StoreErrorsInterface
): TypeOfChatMessageOuterClass.TypeOfChatMessage {

    val returnMessage = TypeOfChatMessageOuterClass.TypeOfChatMessage.newBuilder()

    val newBuilder = TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
        .setAmountOfMessage(TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO)
        .setMessageHasCompleteInfo(true)
        .setChatRoomIdMessageSentFrom(messageEntity.chatRoomId)

    returnMessage.setStandardMessageInfo(newBuilder)

    when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(messageEntity.messageType)) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

            val textMessage = TypeOfChatMessageOuterClass.TextChatMessage.newBuilder()
                .setActiveMessageInfo(
                    generateActiveMessageInfo(
                        messageEntity,
                        thumbnailForReply,
                        errorStore
                    )
                )
                .setMessageText(messageEntity.messageText)
                .setIsEdited(messageEntity.isEdited)
                .setEditedTime(messageEntity.messageEditedTime)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setTextMessage(textMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {

            val pictureMessageBuilder = TypeOfChatMessageOuterClass.PictureChatMessage.newBuilder()
                .setActiveMessageInfo(
                    generateActiveMessageInfo(
                        messageEntity,
                        thumbnailForReply,
                        errorStore
                    )
                )
                .setImageHeight(messageEntity.imageHeight)
                .setImageWidth(messageEntity.imageWidth)

            val catchError = runCatching {
                FileInputStream(messageEntity.filePath)
            }

            val iStream: FileInputStream? = catchError.getOrNull()

            if (iStream != null) { //successfully loaded picture
                val byteArrayOutputStream = ByteArrayOutputStream()
                val byteArray = ByteArray(1024)

                var readNum: Int

                val resultOfWriteFile = runCatching {
                    iStream.use { fileInputStream ->
                        while (fileInputStream.read(byteArray).also { readNum = it } != -1) {
                            byteArrayOutputStream.write(byteArray, 0, readNum)
                        }
                    }
                }

                if (resultOfWriteFile.isSuccess) {
                    val compressedPictureByteArray = byteArrayOutputStream.toByteArray()

                    pictureMessageBuilder.pictureFileSize = compressedPictureByteArray.size
                    pictureMessageBuilder.pictureFileInBytes =
                        ByteString.copyFrom(compressedPictureByteArray)
                } else {

                    val errorMessage =
                        "An exception occurred when attempting to read a PICTURE_MESSAGE picture file.\n" +
                                "exception message: ${resultOfWriteFile.exceptionOrNull()}" +
                                "messageEntity: $messageEntity"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                    throw InvalidMessagePassedException(errorMessage)
                }

            } else { //if failed to load picture

                val errorMessage =
                    "An exception occurred when attempting to read a PICTURE_MESSAGE picture file.\n" +
                            "exception message: ${catchError.exceptionOrNull()}" +
                            "messageEntity: $messageEntity"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                throw InvalidMessagePassedException(errorMessage)
            }

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setPictureMessage(pictureMessageBuilder.build())

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {

            val locationMessage = TypeOfChatMessageOuterClass.LocationChatMessage.newBuilder()
                .setActiveMessageInfo(
                    generateActiveMessageInfo(
                        messageEntity,
                        thumbnailForReply,
                        errorStore
                    )
                )
                .setLongitude(messageEntity.longitude)
                .setLatitude(messageEntity.latitude)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setLocationMessage(locationMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {

            val mimeTypeMessage = TypeOfChatMessageOuterClass.MimeTypeChatMessage.newBuilder()
                .setActiveMessageInfo(
                    generateActiveMessageInfo(
                        messageEntity,
                        thumbnailForReply,
                        errorStore
                    )
                )
                .setImageHeight(messageEntity.imageHeight)
                .setImageWidth(messageEntity.imageWidth)
                .setUrlOfDownload(messageEntity.downloadUrl)
                .setMimeType(messageEntity.messageText)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setMimeTypeMessage(mimeTypeMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {

            val inviteMessage = TypeOfChatMessageOuterClass.InviteChatMessage.newBuilder()
                .setActiveMessageInfo(
                    generateActiveMessageInfo(
                        messageEntity,
                        thumbnailForReply,
                        errorStore
                    )
                )
                .setInvitedUserAccountOid(messageEntity.accountOID)
                .setInvitedUserName(messageEntity.messageText)
                .setChatRoomId(messageEntity.messageValueChatRoomId)
                .setChatRoomName(messageEntity.messageValueChatRoomName)
                .setChatRoomPassword(messageEntity.messageValueChatRoomPassword)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setInviteMessage(inviteMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE -> {
            val editedMessage = TypeOfChatMessageOuterClass.EditedMessageChatMessage.newBuilder()
                .setNewMessage(messageEntity.messageText)
                .setMessageUuid(messageEntity.modifiedMessageUUID)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setEditedMessage(editedMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE -> {

            val deleteMessage = TypeOfChatMessageOuterClass.DeletedMessageChatMessage.newBuilder()
                .setDeleteType(TypeOfChatMessageOuterClass.DeleteType.forNumber(messageEntity.deletedType))
                .setMessageUuid(messageEntity.modifiedMessageUUID)
                .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setDeletedMessage(deleteMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE -> {
            val userActivityDetectedMessage =
                TypeOfChatMessageOuterClass.UserActivityDetectedChatMessage.newBuilder()
                    .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setUserActivityDetectedMessage(userActivityDetectedMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE -> {
            val updateObservedTimeMessage =
                TypeOfChatMessageOuterClass.UpdateObservedTimeChatMessage.newBuilder()
                    .build()

            val messageSpecifics = TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                .setUpdateObservedTimeMessage(updateObservedTimeMessage)

            returnMessage.setMessageSpecifics(messageSpecifics)
        }
        //NOTE: it is set up this way instead of using else so that if
        // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
        // a warning will be thrown
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        null,
        -> {
            val errorMessage =
                "A message with an invalid message type was attempted to be sent to the server.\n" +
                        "typeOfMessage: ${
                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                messageEntity.messageType
                            )
                        }" +
                        "messageEntity: $messageEntity"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            throw InvalidMessagePassedException(errorMessage)
        }
    }

    return returnMessage.build()
}

private fun extractReplyInfoToMessageDataEntity(
    messageEntity: MessagesDataEntity,
    activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    replyThumbnailFilePath: String,
    errorStore: StoreErrorsInterface
) {
    //just because isReply is set to true does not mean the reply was sent back with
    // the message, amountOfMessage must also be COMPLETE_MESSAGE_INFO
    messageEntity.isReply = activeMessageInfo.isReply

    if (activeMessageInfo.hasReplyInfo()) {

        messageEntity.replyIsSentFromOID = activeMessageInfo.replyInfo.replyIsSentFromUserOid
        messageEntity.replyIsFromMessageUUID = activeMessageInfo.replyInfo.replyIsToMessageUuid
        messageEntity.replyType = activeMessageInfo.replyInfo.replySpecifics.replyBodyCase.number

        when (activeMessageInfo.replyInfo.replySpecifics.replyBodyCase) {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {
                messageEntity.replyIsFromMessageText =
                    activeMessageInfo.replyInfo.replySpecifics.textReply.messageText
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> {
                messageEntity.replyIsFromThumbnailFilePath = replyThumbnailFilePath
            }

            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {
                messageEntity.replyIsFromThumbnailFilePath = replyThumbnailFilePath
                messageEntity.replyIsFromMimeType =
                    activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailMimeType
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY,
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY,
            -> {
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET,
            null,
            -> {

                val errorMessage =
                    "An invalid reply type was found inside of an ActiveMessageInfo.\n" +
                            "replyBodyCase: ${activeMessageInfo.replyInfo.replySpecifics.replyBodyCase}" +
                            "activeMessageInfo: $activeMessageInfo" +
                            "messageEntity: $messageEntity"

                //set the messageEntity to not have a reply
                messageEntity.isReply = DefaultMessageDataEntityValues.IS_REPLY
                messageEntity.replyIsSentFromOID =
                    DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID
                messageEntity.replyIsFromMessageUUID =
                    DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID
                messageEntity.replyType = DefaultMessageDataEntityValues.REPLY_TYPE

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

            }
        }
    }
}

//This will save the passed ChatMessageToClient to a MessagesDataEntity.
//If an error occurs MessagesDataEntity.messageType will be set to -1
suspend fun convertChatMessageToMessageDataEntity(
    message: ChatMessageToClientMessage.ChatMessageToClient,
    pictureOrGifFilePath: String = "",
    replyThumbnailFilePath: String = "",
    errorStore: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher
): MessagesDataEntity =
    //NOTE: Main is used here so LetsGoApplicationClass.onActivityStarted will share the same
    // thread and not run at the same time, minimizing inconsistencies with GlobalValues.anActivityCurrentlyRunning
    withContext(ioDispatcher) {

        val messageEntity = buildMessageDataEntityFromChatMessageToClientWithoutSpecifics(
            message
        )

        when (message.message.messageSpecifics.messageBodyCase) {
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                messageEntity.messageText = message.message.messageSpecifics.textMessage.messageText
                messageEntity.isEdited = message.message.messageSpecifics.textMessage.isEdited
                if (message.message.messageSpecifics.textMessage.editedTime < GlobalValues.TWENTY_TWENTY_ONE_START_TIMESTAMP) {
                    //There is a situation here where if the client receives the SKELETON_ONLY the edited time will
                    // not be sent back, and a -1 is required for checking if the value is actually set or not
                    messageEntity.messageEditedTime = -1
                } else {
                    messageEntity.messageEditedTime =
                        message.message.messageSpecifics.textMessage.editedTime
                }

                messageEntity.editHasBeenSent =
                    message.message.messageSpecifics.textMessage.isEdited

                extractReplyInfoToMessageDataEntity(
                    messageEntity,
                    message.message.messageSpecifics.textMessage.activeMessageInfo,
                    replyThumbnailFilePath,
                    errorStore
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                messageEntity.filePath = pictureOrGifFilePath
                messageEntity.imageHeight =
                    message.message.messageSpecifics.pictureMessage.imageHeight
                messageEntity.imageWidth =
                    message.message.messageSpecifics.pictureMessage.imageWidth

                extractReplyInfoToMessageDataEntity(
                    messageEntity,
                    message.message.messageSpecifics.pictureMessage.activeMessageInfo,
                    replyThumbnailFilePath,
                    errorStore
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
                messageEntity.latitude = message.message.messageSpecifics.locationMessage.latitude
                messageEntity.longitude = message.message.messageSpecifics.locationMessage.longitude

                extractReplyInfoToMessageDataEntity(
                    messageEntity,
                    message.message.messageSpecifics.locationMessage.activeMessageInfo,
                    replyThumbnailFilePath,
                    errorStore
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
                messageEntity.filePath = pictureOrGifFilePath
                messageEntity.imageHeight =
                    message.message.messageSpecifics.mimeTypeMessage.imageHeight
                messageEntity.imageWidth =
                    message.message.messageSpecifics.mimeTypeMessage.imageWidth

                messageEntity.downloadUrl =
                    message.message.messageSpecifics.mimeTypeMessage.urlOfDownload

                messageEntity.messageText =
                    message.message.messageSpecifics.mimeTypeMessage.mimeType

                extractReplyInfoToMessageDataEntity(
                    messageEntity,
                    message.message.messageSpecifics.mimeTypeMessage.activeMessageInfo,
                    replyThumbnailFilePath,
                    errorStore
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
                messageEntity.accountOID =
                    message.message.messageSpecifics.inviteMessage.invitedUserAccountOid
                messageEntity.messageText =
                    message.message.messageSpecifics.inviteMessage.invitedUserName

                messageEntity.messageValueChatRoomId =
                    message.message.messageSpecifics.inviteMessage.chatRoomId
                messageEntity.messageValueChatRoomName =
                    message.message.messageSpecifics.inviteMessage.chatRoomName
                messageEntity.messageValueChatRoomPassword =
                    message.message.messageSpecifics.inviteMessage.chatRoomPassword

                extractReplyInfoToMessageDataEntity(
                    messageEntity,
                    message.message.messageSpecifics.inviteMessage.activeMessageInfo,
                    replyThumbnailFilePath,
                    errorStore
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE -> {
                messageEntity.messageText =
                    message.message.messageSpecifics.editedMessage.newMessage
                messageEntity.modifiedMessageUUID =
                    message.message.messageSpecifics.editedMessage.messageUuid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE -> {
                //NOTE: delete type does not actually NEED to be stored, the client simply deletes any message
                // that this is referencing, the only reason DELETED_MESSAGE is converted to a MessageDataEntity
                // is so that it can be passed to the repository using the same live data as other new messages
                // it will never be stored
                //messageEntity.deletedType = message.message.messageSpecifics.deletedMessage.deleteTypeValue
                messageEntity.modifiedMessageUUID =
                    message.message.messageSpecifics.deletedMessage.messageUuid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE -> {
                messageEntity.accountOID =
                    message.message.messageSpecifics.userKickedMessage.kickedAccountOid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE -> {
                messageEntity.accountOID =
                    message.message.messageSpecifics.userBannedMessage.bannedAccountOid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {
                //This returns a fair amount of info, however only the sent_by_account_id is actually needed to
                // display it inside ChatRoomFragment.
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {
                messageEntity.accountOID =
                    message.message.messageSpecifics.differentUserLeftMessage.newAdminAccountOid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE -> {
                messageEntity.messageObservedTime =
                    message.message.messageSpecifics.updateObservedTimeMessage.chatRoomLastObservedTime
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE -> {
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                messageEntity.messageText =
                    message.message.messageSpecifics.chatRoomNameUpdatedMessage.newChatRoomName
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                messageEntity.messageText =
                    message.message.messageSpecifics.chatRoomPasswordUpdatedMessage.newChatRoomPassword
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {
                messageEntity.accountOID =
                    message.message.messageSpecifics.newAdminPromotedMessage.promotedAccountOid
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                messageEntity.longitude = message.message.messageSpecifics.newPinnedLocationMessage.longitude
                messageEntity.latitude = message.message.messageSpecifics.newPinnedLocationMessage.latitude
            }
            //these types should never be directly stored in the database or generated on the client and so
            // should never be converted to data entities
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
            null,
            -> {
                messageEntity.messageType = -1
                messageEntity.deletedType =
                    TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE

                val errorMessage =
                    "Invalid message type passed when converting ChatMessageToClient to MessagesDataEntity.\n" +
                            "message: $message\n" +
                            "messageEntity: $messageEntity\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
        }

        return@withContext messageEntity
    }
