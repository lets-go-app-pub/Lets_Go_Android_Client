package site.letsgoapp.letsgo.utilities

import chat_message_to_client.ChatMessageToClientMessage
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.ChatMessageStoredStatus
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.DefaultMessageDataEntityValues
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import type_of_chat_message.TypeOfChatMessageOuterClass

data class ReplyValuesForMessageDataEntity(
    var isReply: Boolean = DefaultMessageDataEntityValues.IS_REPLY,
    var replyIsSentFromOid: String = DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
    var replyIsFromMessageUUID: String = DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
    var replyType: TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase = TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.forNumber(
        DefaultMessageDataEntityValues.REPLY_TYPE
    ),
    var replyIsFromMessageText: String = DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
    var replyIsFromMimeType: String = DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
)

fun buildMessageDataEntityFromChatMessageToClientWithoutSpecifics(
    message: ChatMessageToClientMessage.ChatMessageToClient
): MessagesDataEntity {

    return MessagesDataEntity(
        message.messageUuid,
        message.sentByAccountId,
        message.message.messageSpecifics.messageBodyCase.number,
        message.message.standardMessageInfo.chatRoomIdMessageSentFrom,
        message.timestampStored,
        getCurrentTimestampInMillis(),
        ChatMessageStoredStatus.STORED_ON_SERVER.ordinal,
        !GlobalValues.anActivityCurrentlyRunning,
        DefaultMessageDataEntityValues.TIME_USER_LAST_OBSERVED_MESSAGE,
        message.message.standardMessageInfo.amountOfMessageValue,
        message.message.standardMessageInfo.messageHasCompleteInfo,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH,
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME
    )
}

fun buildLoadingMessageMessageDataEntity(
    messageUUID: String,
    chatRoomId: String,
    replyValuesForMessageDataEntity: ReplyValuesForMessageDataEntity
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        messageUUID,
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME,
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE,
        false, //the ChatMessageAdapter 'expects' this to be false for LOADING_MESSAGE
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        replyValuesForMessageDataEntity.isReply,
        replyValuesForMessageDataEntity.replyIsSentFromOid,
        replyValuesForMessageDataEntity.replyIsFromMessageUUID,
        replyValuesForMessageDataEntity.replyType.number,
        replyValuesForMessageDataEntity.replyIsFromMessageText,
        replyValuesForMessageDataEntity.replyIsFromMimeType,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH,
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}

fun buildTextMessageMessageDataEntity(
    chatRoomId: String,
    replyValuesForMessageDataEntity: ReplyValuesForMessageDataEntity,
    textMessage: String
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO.number,
        true,
        textMessage,
        replyValuesForMessageDataEntity.isReply,
        replyValuesForMessageDataEntity.replyIsSentFromOid,
        replyValuesForMessageDataEntity.replyIsFromMessageUUID,
        replyValuesForMessageDataEntity.replyType.number,
        replyValuesForMessageDataEntity.replyIsFromMessageText,
        replyValuesForMessageDataEntity.replyIsFromMimeType,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}

fun buildLocationMessageMessageDataEntity(
    chatRoomId: String,
    replyValuesForMessageDataEntity: ReplyValuesForMessageDataEntity,
    locationMessageObject: LocationSelectedObject
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO.number,
        true,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        replyValuesForMessageDataEntity.isReply,
        replyValuesForMessageDataEntity.replyIsSentFromOid,
        replyValuesForMessageDataEntity.replyIsFromMessageUUID,
        replyValuesForMessageDataEntity.replyType.number,
        replyValuesForMessageDataEntity.replyIsFromMessageText,
        replyValuesForMessageDataEntity.replyIsFromMimeType,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        locationMessageObject.selectLocationCurrentLocation.longitude,
        locationMessageObject.selectLocationCurrentLocation.latitude,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}

@Suppress("unused")
fun buildPictureMessageMessageDataEntity(
    chatRoomId: String,
    pictureMessageUUID: String,
    replyValuesForMessageDataEntity: ReplyValuesForMessageDataEntity,
    pictureHeight: Int,
    pictureWidth: Int,
    pictureFilePath: String
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        pictureMessageUUID,
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO.number,
        true,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        replyValuesForMessageDataEntity.isReply,
        replyValuesForMessageDataEntity.replyIsSentFromOid,
        replyValuesForMessageDataEntity.replyIsFromMessageUUID,
        replyValuesForMessageDataEntity.replyType.number,
        replyValuesForMessageDataEntity.replyIsFromMessageText,
        replyValuesForMessageDataEntity.replyIsFromMimeType,
        "", //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        pictureHeight,
        pictureWidth,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        pictureFilePath,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )

}

fun buildInviteMessageMessageDataEntity(
    chatRoomId: String,
    replyValuesForMessageDataEntity: ReplyValuesForMessageDataEntity,
    inviteMessageObject: InviteMessageObject,
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO.number,
        true,
        inviteMessageObject.inviteMessageUserName,
        replyValuesForMessageDataEntity.isReply,
        replyValuesForMessageDataEntity.replyIsSentFromOid,
        replyValuesForMessageDataEntity.replyIsFromMessageUUID,
        replyValuesForMessageDataEntity.replyType.number,
        replyValuesForMessageDataEntity.replyIsFromMessageText,
        replyValuesForMessageDataEntity.replyIsFromMimeType,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        inviteMessageObject.inviteMessageUserOid,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        inviteMessageObject.inviteMessageChatRoomBasicInfo.chatRoomId,
        inviteMessageObject.inviteMessageChatRoomBasicInfo.chatRoomName,
        inviteMessageObject.inviteMessageChatRoomBasicInfo.chatRoomPassword,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )


}

fun buildEditedMessageMessageDataEntity(
    chatRoomId: String,
    newMessageText: String,
    modifiedMessageUUIDPrimaryKey: String,
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE.number, //highest this value can go for edited message
        true,
        newMessageText,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        modifiedMessageUUIDPrimaryKey,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}

fun buildDeletedMessageMessageDataEntity(
    chatRoomId: String,
    deleteType: TypeOfChatMessageOuterClass.DeleteType,
    modifiedMessageUUIDPrimaryKey: String,
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        false,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON.number, //highest this value can go for deleted message
        true,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        deleteType.number,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        modifiedMessageUUIDPrimaryKey,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )

}

fun buildUpdateObservedTimeMessageDataEntity(
    chatRoomId: String,
    observedTime: Long,
): MessagesDataEntity {

    val timestamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE.number,
        chatRoomId,
        DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME, //will be set after server response
        timestamp,
        ChatMessageStoredStatus.NOT_YET_STORED.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        timestamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON.number,
        true,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        observedTime
    )
}

fun buildHistoryClearedMessageMessageDataEntity(
    chatRoomId: String
): MessagesDataEntity {

    val timeStamp = getCurrentTimestampInMillis()

    return MessagesDataEntity(
        generateChatMessageUUID(),
        LoginFunctions.currentAccountOID,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE.number,
        chatRoomId,
        timeStamp,
        timeStamp,
        ChatMessageStoredStatus.STORED_ON_SERVER.ordinal, //this message will never be sent
        false,
        timeStamp,
        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO.number,
        true,
        DefaultMessageDataEntityValues.MESSAGE_TEXT,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH, //will be set inside repository if necessary
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        DefaultMessageDataEntityValues.LONGITUDE,
        DefaultMessageDataEntityValues.LATITUDE,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}

fun buildMessageDataEntityForChatRoomInfoUpdated(
    typeOfChatMessage: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase,
    chatRoomId: String,
    chatRoomLastActivityTime: Long,
    chatRoomInfo: String = "",
    pinnedLongitude: Double = GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
    pinnedLatitude: Double = GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
): MessagesDataEntity {

    val messageText =
        if (typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE
            || typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
        ) {
            chatRoomInfo;
        } else {
            DefaultMessageDataEntityValues.MESSAGE_TEXT;
        }

    val (longitude, latitude) =
        if (typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE) {
            Pair(pinnedLongitude, pinnedLatitude);
        } else {
            Pair(DefaultMessageDataEntityValues.LONGITUDE, DefaultMessageDataEntityValues.LATITUDE);
        }

    return MessagesDataEntity(
        "",
        LoginFunctions.currentAccountOID,
        typeOfChatMessage.number,
        chatRoomId,
        chatRoomLastActivityTime,
        getCurrentTimestampInMillis(),
        ChatMessageStoredStatus.STORED_ON_SERVER.ordinal,
        DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
        DefaultMessageDataEntityValues.TIME_USER_LAST_OBSERVED_MESSAGE,
        TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON_VALUE,
        true,
        messageText,
        DefaultMessageDataEntityValues.IS_REPLY,
        DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
        DefaultMessageDataEntityValues.REPLY_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
        DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH,
        DefaultMessageDataEntityValues.DELETED_TYPE,
        DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
        DefaultMessageDataEntityValues.IS_EDITED,
        DefaultMessageDataEntityValues.DOWNLOAD_URL,
        DefaultMessageDataEntityValues.OID_VALUE,
        DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
        DefaultMessageDataEntityValues.IMAGE_HEIGHT,
        DefaultMessageDataEntityValues.IMAGE_WIDTH,
        longitude,
        latitude,
        DefaultMessageDataEntityValues.FILE_PATH,
        DefaultMessageDataEntityValues.ACCOUNT_OID,
        DefaultMessageDataEntityValues.INVITE_EXPIRED,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
        DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
        DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
        DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
    )
}