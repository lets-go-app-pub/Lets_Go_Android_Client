package site.letsgoapp.letsgo.databases.messagesDatabase.messages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import type_of_chat_message.TypeOfChatMessageOuterClass

@Dao
interface MessagesDatabaseDao {

    //insert single message
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(messagesDataEntity: MessagesDataEntity)

    //delete all
    @Query("DELETE FROM messages_table")
    suspend fun clearTable()

    //insert multiple messages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultipleMessages(messagesDataEntities: MutableList<MessagesDataEntity>): List<Long>

    //retrieve single message
    @Query("SELECT * FROM messages_table WHERE messageUUIDPrimaryKey = :uuidKey LIMIT 1")
    suspend fun retrieveSingleMessageByUUID(uuidKey: String): MessagesDataEntity?

    //retrieve message_has_been_sent for single message by index
    @Query("SELECT message_sent_status FROM messages_table WHERE messageUUIDPrimaryKey = :uuidPrimaryKey LIMIT 1")
    suspend fun retrieveHasBeenSentStatusByIndex(uuidPrimaryKey: String): Int?

    //retrieve single message index by message oid
    @Query("SELECT 1 FROM messages_table WHERE messageUUIDPrimaryKey = :messageOID LIMIT 1")
    suspend fun messageExistsInDatabase(messageOID: String): Int?

    //sets the message cap stored time, this can be used for designating where the cap is after the history was cleared
    @Query("UPDATE messages_table SET message_stored_server_time = :newTime, message_stored_database_timestamp = :newTime WHERE chat_room_id = :chatRoomID AND message_type = :messageType")
    suspend fun setChatRoomCapMessageStoredTime(
        chatRoomID: String,
        newTime: Long,
        messageType: Int = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE.number
    )

    @Query("SELECT * FROM messages_table WHERE messageUUIDPrimaryKey = :messageOID LIMIT 1")
    suspend fun retrieveSingleMessage(messageOID: String): MessagesDataEntity?

    //returns null if the oid does not exist and the
    @Query("SELECT 1 FROM messages_table WHERE messageUUIDPrimaryKey = :messageOID LIMIT 1")
    suspend fun checkIfOIDExists(messageOID: String): Int?

    //retrieve all messages for the passed chat room
    @Query("""SELECT * FROM messages_table WHERE chat_room_id = :chatRoomId ORDER BY message_stored_database_timestamp ASC""")
    suspend fun retrieveAllMessagesForChatRoom(chatRoomId: String): List<MessagesDataEntity>

    //retrieve all messages with matching UUIDs
    @Query(
        """SELECT
            messageUUIDPrimaryKey,
            sent_by_account_id,
            message_type,
            chat_room_id,
            message_stored_server_time,
            has_complete_info,
            message_text,
            account_id
        FROM messages_table
        WHERE messageUUIDPrimaryKey IN(:messageUUIDs)
        ORDER BY chat_room_id ASC"""
    )
    suspend fun requestAllMessageByUUID(messageUUIDs: List<String>): List<MessageFieldsForNotifications>

    //retrieve all message that need to be sent to the server and order them
    @Query("""SELECT * FROM messages_table WHERE message_sent_status = :defaultParam ORDER BY message_stored_database_timestamp ASC""")
    suspend fun retrieveUnsentMessages(defaultParam: Int = ChatMessageStoredStatus.NOT_YET_STORED.ordinal): List<MessagesDataEntity>

    //retrieve all message that require notifications
    @Query("""SELECT * FROM messages_table WHERE message_requires_notification = :trueVal ORDER BY message_stored_database_timestamp ASC""")
    suspend fun retrieveMessagesRequiresNotifications(trueVal: Boolean = true): List<MessagesDataEntity>

    //sets all messages to trimmed from the passed message UUIDs
    @Query(
        """
        UPDATE messages_table SET
            message_text = :emptyString,
            file_path = :emptyString,
            reply_is_sent_from_oid = :emptyString,
            reply_is_from_uuid = :emptyString,
            reply_type = :defaultReplyType,
            reply_is_from_message_text = :emptyString,
            reply_is_from_mime_type = :emptyString,
            reply_is_from_thumbnail_file_path = :emptyString,
            time_user_last_observed_message = :defaultTimestamp,
            amount_of_message = :amountOfMessageSkeleton,
            has_complete_info = CASE 
                WHEN is_reply = 1 THEN 0
                WHEN message_type = :textMessageType OR message_type = :pictureMessageType THEN 0
                ELSE 1 
            END
        WHERE messageUUIDPrimaryKey IN(:primaryMessageUUIDs)
        """
    )
    suspend fun setMessagesInListToTrimmed(
        primaryMessageUUIDs: List<String>,
        emptyString: String = "",
        defaultTimestamp: Long = -1L,
        defaultReplyType: Int = TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET.number,
        textMessageType: Int = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE.number,
        pictureMessageType: Int = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE.number,
        amountOfMessageSkeleton: Int = TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE
    )

    //retrieve all messages sent by users list that have amountOfMessage != SKELETON_ONLY
    //WHEN CAN MESSAGE BE TRIMMED
    //AmountOfMessage == SKELETON_ONLY && hasCompleteInfo && isReply --- should never be possible
    //AmountOfMessage == SKELETON_ONLY && hasCompleteInfo && !isReply --- CANNOT BE TRIMMED
    //AmountOfMessage == SKELETON_ONLY && !hasCompleteInfo && isReply --- CANNOT BE TRIMMED
    //AmountOfMessage == SKELETON_ONLY && !hasCompleteInfo && !isReply --- CANNOT BE TRIMMED
    //AmountOfMessage == FINAL_MESSAGE && hasCompleteInfo && isReply --- should never be possible
    //AmountOfMessage == FINAL_MESSAGE && hasCompleteInfo && !isReply --- CAN BE TRIMMED
    //AmountOfMessage == FINAL_MESSAGE && !hasCompleteInfo && isReply --- CAN BE TRIMMED
    //AmountOfMessage == FINAL_MESSAGE && !hasCompleteInfo && !isReply --- CAN BE TRIMMED
    //AmountOfMessage == COMPLETE_MESSAGE_INFO && hasCompleteInfo && isReply --- CAN BE TRIMMED
    //AmountOfMessage == COMPLETE_MESSAGE_INFO && hasCompleteInfo && !isReply --- CAN BE TRIMMED
    //AmountOfMessage == COMPLETE_MESSAGE_INFO && !hasCompleteInfo && isReply --- should never be possible
    //AmountOfMessage == COMPLETE_MESSAGE_INFO && !hasCompleteInfo && !isReply --- should never be possible
    @Query(
        """
        SELECT messageUUIDPrimaryKey, message_type, message_text, file_path, reply_is_from_thumbnail_file_path, time_user_last_observed_message
        FROM messages_table
        WHERE 
            sent_by_account_id IN(:sentByAccountOIDS)
            AND
            amount_of_message != :skeletonMessageOrdinal
        """
    )
    suspend fun retrieveMessagesSentByUsersThatCanBeTrimmed(
        sentByAccountOIDS: List<String>,
        skeletonMessageOrdinal: Int = TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE
    ): List<MessageFieldsForTrimming>

    //retrieve all messages sent by users list that have amountOfMessage == SKELETON_ONLY and are not sent by the passed accountOIDs
    @Query(
        """
        SELECT messageUUIDPrimaryKey, message_type, message_text, file_path, reply_is_from_thumbnail_file_path, time_user_last_observed_message
        FROM messages_table
        WHERE 
            messageUUIDPrimaryKey NOT IN(:notMessageUUIDs)
            AND
            amount_of_message != :skeletonMessageOrdinal
        ORDER BY time_user_last_observed_message ASC
        """
    )
    suspend fun retrieveMessagesThatCanBeTrimmed(
        notMessageUUIDs: List<String>,
        skeletonMessageOrdinal: Int = TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE
    ): List<MessageFieldsForTrimming>

    //retrieve all messages that have a file path attached
    @Query(
        """
        SELECT messageUUIDPrimaryKey, file_path, reply_is_from_thumbnail_file_path
        FROM messages_table
        WHERE 
            file_path != :emptyField
            OR
            reply_is_from_thumbnail_file_path != :emptyField
        ORDER BY time_user_last_observed_message ASC
        """
    )
    suspend fun retrieveMessageFilePaths(
        emptyField: String = ""
    ): List<MessageFieldsWithFileNames>

    //retrieve all messages not observed recently that can be trimmed
    @Query(
        """
        SELECT messageUUIDPrimaryKey, message_type, message_text, file_path, reply_is_from_thumbnail_file_path, time_user_last_observed_message 
        FROM messages_table 
        WHERE
            time_user_last_observed_message < :timestampObservedBefore
            AND
            amount_of_message != :skeletonMessageOrdinal
        """
    )
    suspend fun retrieveMessagesNotObservedRecently(
        timestampObservedBefore: Long,
        skeletonMessageOrdinal: Int = TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE
    ): List<MessageFieldsForTrimming>

    @Query("DELETE FROM messages_table WHERE messageUUIDPrimaryKey = :uuidPrimaryKey")
    suspend fun removeSingleMessageByUUID(uuidPrimaryKey: String)

    @Query("DELETE FROM messages_table WHERE chat_room_id = :chatRoomId")
    suspend fun removeAllMessagesForChatRoom(chatRoomId: String)

    @Query("DELETE FROM messages_table WHERE chat_room_id = :chatRoomId AND message_type != :messageType")
    suspend fun removeAllMessagesForChatRoomExceptCap(
        chatRoomId: String,
        messageType: Int = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE.number
    )

    @Query("DELETE FROM messages_table")
    suspend fun removeAllMessages()

    @Query("DELETE FROM messages_table WHERE chat_room_id = :chatRoomId AND message_type = :updateChatRoomTypeValue ")
    suspend fun removeAllMessagesOfPassedTypeType(chatRoomId: String, updateChatRoomTypeValue: Int)

    //update the file path of the reply
    @Query("UPDATE messages_table SET reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath WHERE messageUUIDPrimaryKey = :uuidPrimaryKey")
    suspend fun updateReplyIsFromThumbnailFilePath(
        uuidPrimaryKey: String,
        replyIsFromThumbnailFilePath: String,
    )

    //update the specific message to be 'server received' status
    @Query(
        """
        UPDATE messages_table SET
            message_sent_status = :defaultParam,
            message_stored_server_time = :messageStoredServerTime
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updateMessageToReceivedByServer(
        uuidPrimaryKey: String,
        messageStoredServerTime: Long,
        defaultParam: Int = ChatMessageStoredStatus.STORED_ON_SERVER.ordinal,
    )

    //update all messages to be message_requires_notification = false
    @Query("UPDATE messages_table SET message_requires_notification = :falseVal WHERE message_requires_notification = :trueVal")
    suspend fun updateAllMessagesToDoNotRequireNotifications(
        trueVal: Boolean = true,
        falseVal: Boolean = false,
    )

    //update the specific message to be message_requires_notification = false
    @Query("UPDATE messages_table SET message_requires_notification = :falseVal WHERE messageUUIDPrimaryKey = :uuidPrimaryKey")
    suspend fun updateMessageToDoesNotRequireNotifications(
        uuidPrimaryKey: String,
        falseVal: Boolean = false,
    )

    //update the message to have a new file path
    @Query("UPDATE messages_table SET file_path = :filePath, image_height = :pictureHeight, image_width = :pictureWidth WHERE messageUUIDPrimaryKey = :uuidPrimaryKey")
    fun updatePictureInfo(
        uuidPrimaryKey: String,
        filePath: String,
        pictureHeight: Int,
        pictureWidth: Int,
    )

    //get the most recently stored message for the passed chat room
    //NOTE: COALESCE will make sure the field does not return null
    @Query(
        """
        SELECT 
            COALESCE(messageUUIDPrimaryKey, :defaultZero) as messageUUIDPrimaryKey,
            COALESCE(sent_by_account_id, :defaultEmptyString) as sent_by_account_id,
            COALESCE(message_stored_server_time, :defaultNegativeOne) as message_stored_server_time,
            COALESCE(message_type, :defaultNegativeOneInt) as message_type,
            COALESCE(amount_of_message, :defaultNegativeOneInt) as amount_of_message,
            COALESCE(message_text, :defaultEmptyString) as message_text,
            COALESCE(account_id, :defaultZero) as account_id,
            COALESCE(MAX(CASE WHEN message_stored_server_time = :defaultNegativeOne THEN message_stored_database_timestamp ELSE message_stored_server_time END), :defaultNegativeOne) as highest_value
        FROM messages_table
        WHERE
            chat_room_id = :chatRoomId
                AND
            deleted_type != :messageDeletedOnClient
                AND
            deleted_type != :messageDeletedForAll
                AND
            (
                message_type IN ${GlobalValues.messagesDaoAllowedBlockedMessageTypesString}
                    OR
                sent_by_account_id NOT IN(:blockedOIDs)
            )
                AND
            message_type IN ${GlobalValues.messagesDaoSelectFinalMessageString}
        LIMIT 1
    """
    )
    suspend fun getMostRecentMessageInChatRoom(
        chatRoomId: String,
        blockedOIDs: MutableList<String> = GlobalValues.blockedAccounts.getMutableList(),
        defaultNegativeOne: Long = -1L,
        defaultZero: Long = 0L,
        defaultEmptyString: String = "",
        defaultNegativeOneInt: Int = -1,
        messageDeletedOnClient: Int = TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT.number,
        messageDeletedForAll: Int = TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS.number
    ): MostRecentMessageDataHolder?

    //get the most recent message_stored_server_time which would update the chat_room_last_active_time
    // not including messages from blocked accounts
    @Query(
        """
        SELECT message_stored_server_time
        FROM messages_table 
        WHERE
            message_stored_server_time = (
                SELECT MAX(message_stored_server_time)
                FROM messages_table
                WHERE
                    chat_room_id = :chatRoomId
                    AND
                    message_type IN ${GlobalValues.messagesDaoSelectChatRoomLastActiveTimeString}
                    AND
                    deleted_type != :messageDeletedOnClient
                    AND
                    deleted_type != :messageDeletedForAll
                    AND
                    (
                        message_type IN ${GlobalValues.messagesDaoAllowedBlockedMessageTypesString}
                            OR
                        sent_by_account_id NOT IN(:blockedOIDs)
                    )
            )
    """
    )
    suspend fun getLastActivityTimeNotIncludingBlocked(
        chatRoomId: String,
        blockedOIDs: MutableList<String> = GlobalValues.blockedAccounts.getMutableList(),
        messageDeletedOnClient: Int = TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT.number,
        messageDeletedForAll: Int = TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS.number
    ): Long?

    //get the most recent message_stored_server_time which would update the chat_room_last_active_time
    // NOTE: not taking message_stored_in_database if it is only stored in the database it is a personal message
    // and will have no effect on if the user has unread messages
    @Query(
        """
        SELECT message_stored_server_time, chat_room_id, sent_by_account_id, messageUUIDPrimaryKey
        FROM messages_table 
        WHERE 
            message_stored_server_time = (
                SELECT MAX(message_stored_server_time)
                FROM messages_table
                WHERE
                    message_type IN ${GlobalValues.messagesDaoSelectChatRoomLastActiveTimeString}
                    AND
                    sent_by_account_id != :currentUserAccountOID
                    AND
                    deleted_type != :messageDeletedOnClient
                    AND
                    deleted_type != :messageDeletedForAll
                    AND
                    (
                        message_type IN ${GlobalValues.messagesDaoAllowedBlockedMessageTypesString}
                            OR
                        sent_by_account_id NOT IN(:blockedOIDs)
                    )
            )
    """
    )
    suspend fun getMostRecentMessageStoredOnServerTimeForAllChatRooms(
        currentUserAccountOID: String = LoginFunctions.currentAccountOID,
        blockedOIDs: MutableList<String> = GlobalValues.blockedAccounts.getMutableList(),
        messageDeletedOnClient: Int = TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT.number,
        messageDeletedForAll: Int = TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS.number
    ): MostRecentMessageWithSentByOidData?

    @Query(
        """
        SELECT MAX(message_stored_server_time) as message_stored_server_time, chat_room_id, messageUUIDPrimaryKey
        FROM messages_table 
        WHERE
            message_type IN ${GlobalValues.messagesDaoSelectChatRoomLastActiveTimeString}
            AND
            sent_by_account_id != :currentUserAccountOID
            AND
            deleted_type != :messageDeletedOnClient
            AND
            deleted_type != :messageDeletedForAll
            AND
            (
                message_type IN ${GlobalValues.messagesDaoAllowedBlockedMessageTypesString}
                    OR
                sent_by_account_id NOT IN(:blockedOIDs)
            )
        GROUP BY chat_room_id
        ORDER BY chat_room_id
    """
    )
    suspend fun getMostRecentMessageForEachChatRoomIncludingBlocking(
        currentUserAccountOID: String = LoginFunctions.currentAccountOID,
        blockedOIDs: MutableList<String> = GlobalValues.blockedAccounts.getMutableList(),
        messageDeletedOnClient: Int = TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT.number,
        messageDeletedForAll: Int = TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS.number
    ): List<MostRecentMessageData>

    @Query(
        """
        SELECT messageUUIDPrimaryKey, message_stored_server_time, chat_room_id
        FROM messages_table 
        WHERE message_stored_server_time >= :earliestTimestamp
        ORDER BY chat_room_id
    """
    )
    suspend fun getRecentMessagesForEachChatRoomIncludingBlocking(earliestTimestamp: Long): List<MostRecentMessageData>

    //check the final few messages to see if they require downloading
    @Query(
        """
        SELECT messageUUIDPrimaryKey, has_complete_info
        FROM messages_table
        WHERE
            (
                message_type IN ${GlobalValues.messagesDaoAllowedBlockedMessageTypesString}
                    OR
                sent_by_account_id NOT IN(:blockedOIDs)
            )
                AND
            chat_room_id = :chatRoomId
                AND
            message_type IN ${GlobalValues.messagesDaoSelectFinalMessageString}
        ORDER BY CASE WHEN message_stored_database_timestamp = :defaultNegativeOne THEN message_stored_server_time ELSE message_stored_database_timestamp END DESC
        LIMIT :numberMessagesToRequest
    """
    )
    suspend fun getFinalMessagesInChatRoomDownloadedState(
        chatRoomId: String,
        blockedOIDs: MutableList<String> = GlobalValues.blockedAccounts.getMutableList(),
        defaultNegativeOne: Long = -1L,
        numberMessagesToRequest: Int = GlobalValues.server_imported_values.maxNumberMessagesToRequest,
    ): List<MessageDownloadedState>

    //update the specific message to be edited but the edit has not been sent
    @Query(
        """
        UPDATE messages_table SET
            message_text = :newMessageText,
            edit_has_been_sent = :editHasBeenSent,
            is_edited = :edited
        WHERE messageUUIDPrimaryKey = :messageUUID
        """
    )
    suspend fun updateMessageToEditedButNotSentByServer(
        messageUUID: String,
        newMessageText: String,
        editHasBeenSent: Boolean = false,
        edited: Boolean = true,
    )

    //update existing edited message to sent
    @Query(
        """
        UPDATE messages_table SET
            edit_has_been_sent = :editHasBeenSent,
            is_edited = :edited
        WHERE messageUUIDPrimaryKey = :messageUUID
        """
    )
    suspend fun updateEditHasBeenSentToTrue(
        messageUUID: String,
        editHasBeenSent: Boolean = true,
        edited: Boolean = true,
    )

    //update the specific text message to be edited
    @Query(
        """
        UPDATE messages_table SET
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            message_text = :newMessageText,
            message_edited_time = :messageEditedTime,
            edit_has_been_sent = :editHasBeenSent,
            is_edited = :edited
        WHERE messageUUIDPrimaryKey = :messageUUID
        """
    )
    suspend fun updateTextMessageToEdited(
        messageUUID: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        newMessageText: String,
        messageEditedTime: Long,
        editHasBeenSent: Boolean,
        edited: Boolean = true,
    )

    //update the message to be expired (only relevant to invite message type)
    @Query("UPDATE messages_table SET invite_expired = :defaultValueInviteExpired WHERE messageUUIDPrimaryKey = :uuidPrimaryKey")
    suspend fun updateInviteMessageToExpired(
        uuidPrimaryKey: String,
        defaultValueInviteExpired: Boolean = true,
    )

    //updates time_user_last_observed_message to the passed timestamp for all message in list
    @Query(
        """
        UPDATE messages_table 
        SET time_user_last_observed_message = CASE WHEN time_user_last_observed_message < :timestampObserved THEN :timestampObserved ELSE time_user_last_observed_message END
        WHERE messageUUIDPrimaryKey IN(:messageUUIDPrimaryKeys)
        """
    )
    suspend fun updateTimestampsForPassedMessageUUIDs(
        messageUUIDPrimaryKeys: Set<String>,
        timestampObserved: Long,
    )

    //update the text message values
    @Query(
        """
        UPDATE messages_table SET
            message_text = :messageText,
            is_edited = :isEdited,
            message_edited_time = :editedTime,
            time_user_last_observed_message = :currentTime,
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            edit_has_been_sent = :isEdited,
            is_reply = :isReply,
            reply_is_sent_from_oid = :replyIsSentFromOid,
            reply_is_from_uuid = :replyIsFromUUID,
            reply_type = :replyType,
            reply_is_from_message_text = :replyIsFromMessageText,
            reply_is_from_mime_type = :replyIsFromMimeType,
            reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updateTextMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        messageText: String,
        currentTime: Long,
        isEdited: Boolean,
        editedTime: Long,
        isReply: Boolean,
        replyIsSentFromOid: String,
        replyIsFromUUID: String,
        replyType: Int,
        replyIsFromMessageText: String,
        replyIsFromMimeType: String,
        replyIsFromThumbnailFilePath: String,
    )

    //update the picture message values
    @Query(
        """
        UPDATE messages_table SET
            time_user_last_observed_message = :currentTime,
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            image_height = :imageHeight,
            image_width = :imageWidth,
            file_path = :pictureFilePath,
            is_reply = :isReply,
            reply_is_sent_from_oid = :replyIsSentFromOid,
            reply_is_from_uuid = :replyIsFromUUID,
            reply_type = :replyType,
            reply_is_from_message_text = :replyIsFromMessageText,
            reply_is_from_mime_type = :replyIsFromMimeType,
            reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updatePictureMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        pictureFilePath: String,
        currentTime: Long,
        isReply: Boolean,
        replyIsSentFromOid: String,
        replyIsFromUUID: String,
        replyType: Int,
        replyIsFromMessageText: String,
        replyIsFromMimeType: String,
        replyIsFromThumbnailFilePath: String,
    )

    //update the location message values
    @Query(
        """
        UPDATE messages_table SET
            time_user_last_observed_message = :currentTime,
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            longitude = :longitude,
            latitude = :latitude,
            is_reply = :isReply,
            reply_is_sent_from_oid = :replyIsSentFromOid,
            reply_is_from_uuid = :replyIsFromUUID,
            reply_type = :replyType,
            reply_is_from_message_text = :replyIsFromMessageText,
            reply_is_from_mime_type = :replyIsFromMimeType,
            reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updateLocationMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        longitude: Double,
        latitude: Double,
        currentTime: Long,
        isReply: Boolean,
        replyIsSentFromOid: String,
        replyIsFromUUID: String,
        replyType: Int,
        replyIsFromMessageText: String,
        replyIsFromMimeType: String,
        replyIsFromThumbnailFilePath: String,
    )

    //update the mime type message values
    @Query(
        """
        UPDATE messages_table SET
            time_user_last_observed_message = :currentTime,
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            image_height = :imageHeight,
            image_width = :imageWidth,
            download_url = :urlOfDownload,
            message_text = :mimeType,
            is_reply = :isReply,
            reply_is_sent_from_oid = :replyIsSentFromOid,
            reply_is_from_uuid = :replyIsFromUUID,
            reply_type = :replyType,
            reply_is_from_message_text = :replyIsFromMessageText,
            reply_is_from_mime_type = :replyIsFromMimeType,
            reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updateMimeTypeMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        imageHeight: Int,
        imageWidth: Int,
        currentTime: Long,
        urlOfDownload: String,
        mimeType: String,
        isReply: Boolean,
        replyIsSentFromOid: String,
        replyIsFromUUID: String,
        replyType: Int,
        replyIsFromMessageText: String,
        replyIsFromMimeType: String,
        replyIsFromThumbnailFilePath: String,
    )

    //update the invite message values
    @Query(
        """
        UPDATE messages_table SET
            time_user_last_observed_message = :currentTime,
            amount_of_message = :amountOfMessage,
            has_complete_info = :hasCompleteInfo,
            account_id = :invitedUserAccountOid,
            message_text = :invitedUserName,
            message_value_chat_room_id = :chatRoomId,
            message_value_chat_room_name = :chatRoomName,
            message_value_chat_room_password = :chatRoomPassword,
            is_reply = :isReply,
            reply_is_sent_from_oid = :replyIsSentFromOid,
            reply_is_from_uuid = :replyIsFromUUID,
            reply_type = :replyType,
            reply_is_from_message_text = :replyIsFromMessageText,
            reply_is_from_mime_type = :replyIsFromMimeType,
            reply_is_from_thumbnail_file_path = :replyIsFromThumbnailFilePath
        WHERE messageUUIDPrimaryKey = :uuidPrimaryKey
    """
    )
    suspend fun updateInviteMessageSpecifics(
        uuidPrimaryKey: String,
        amountOfMessage: Int,
        hasCompleteInfo: Boolean,
        invitedUserAccountOid: String,
        invitedUserName: String,
        chatRoomId: String,
        chatRoomName: String,
        chatRoomPassword: String,
        currentTime: Long,
        isReply: Boolean,
        replyIsSentFromOid: String,
        replyIsFromUUID: String,
        replyType: Int,
        replyIsFromMessageText: String,
        replyIsFromMimeType: String,
        replyIsFromThumbnailFilePath: String,
    )

    //returns all list of chat rooms which contain text messages with text matching the passed string to match
    //NOTE: this will not work if the message has not been fully downloaded (for example amountOfMessage == SKELETON_ONLY)
    // however it will not break anything either
    //NOTE: LIKE is case insensitive which is desired in this case
    @Query(
        """
            SELECT chat_room_id
            FROM messages_table 
            WHERE 
                (message_type = :textMessageType
                AND
                message_text LIKE '%' || :matchingString || '%')
                OR
                reply_is_from_message_text LIKE '%' || :matchingString || '%'
                OR
                message_value_chat_room_name LIKE '%' || :matchingString || '%'
        """
    )
    suspend fun searchForChatRoomMatches(
        matchingString: String,
        textMessageType: Int = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE.number
    ): List<String>
}

data class MostRecentMessageDataHolder(
    val messageUUIDPrimaryKey: String,
    val sent_by_account_id: String,
    val message_stored_server_time: Long,
    val message_type: Int,
    val amount_of_message: Int,
    val message_text: String,
    val account_id: String,
    val highest_value: Long
) {
    constructor() : this(
        "",
        "",
        -1L,
        -1,
        -1,
        "",
        "",
        -1L
    )

    constructor(messagesDataEntity: MessagesDataEntity) : this(
        messagesDataEntity.messageUUIDPrimaryKey,
        messagesDataEntity.sentByAccountID,
        messagesDataEntity.messageStoredOnServerTime,
        messagesDataEntity.messageType,
        messagesDataEntity.amountOfMessage,
        messagesDataEntity.messageText,
        messagesDataEntity.accountOID,
        -1L
    )
}

data class MessageDownloadedState(val messageUUIDPrimaryKey: String, val has_complete_info: Boolean)

data class MessageFieldsForTrimming(
    val messageUUIDPrimaryKey: String,
    val message_type: Int,
    val message_text: String,
    val file_path: String,
    val reply_is_from_thumbnail_file_path: String,
    val time_user_last_observed_message: Long
)

data class MessageFieldsWithFileNames(
    val messageUUIDPrimaryKey: String,
    val file_path: String,
    val reply_is_from_thumbnail_file_path: String
)

data class MostRecentMessageData(
    val messageUUIDPrimaryKey: String = "~",
    val message_stored_server_time: Long = -1,
    val chat_room_id: String = "~"
)

data class MostRecentMessageWithSentByOidData(
    val messageUUIDPrimaryKey: String = "~",
    val sent_by_account_id: String = "~",
    val message_stored_server_time: Long = -1,
    val chat_room_id: String = "~"
)

data class MessageFieldsForNotifications(
    val messageUUIDPrimaryKey: String,
    val sent_by_account_id: String,
    val message_type: Int,
    val chat_room_id: String,
    val message_stored_server_time: Long = -1,
    val has_complete_info: Boolean,
    val message_text: String,
    val account_id: String
)