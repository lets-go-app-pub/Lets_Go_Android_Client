package site.letsgoapp.letsgo.testingUtility

import account_state.AccountState
import chat_message_to_client.ChatMessageToClientMessage.ChatMessageToClient
import chatroominfo.ChatRoomInfoMessageOuterClass
import com.google.protobuf.ByteString
import member_shared_info.MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.generateChatMessageUUID
import type_of_chat_message.TypeOfChatMessageOuterClass
import type_of_chat_message.TypeOfChatMessageOuterClass.*

data class GenericMessageParameters(
    val messageUUID: String = generateChatMessageUUID(),
    val sentByAccountOID: String = generateRandomOidForTesting(),
    val timestampStored: Long = System.currentTimeMillis(),
    val onlyStoreMessage: Boolean = false,
    val chatRoomIdSentFrom: String = generateRandomChatRoomIdForTesting(),
    val doNotUpdateUserState: Boolean = false,
    var amountOfMessage: AmountOfMessage = AmountOfMessage.ONLY_SKELETON,
    var hasCompleteInfo: Boolean = true
)

//Set replyInfo to non-null value in order to use it.
data class ActiveMessageInfoDataClass(
    val isDeleted: Boolean = false,
    val replyInfo: ReplyChatMessageInfo? = null
) {
    fun convertToActiveMessage(): ActiveMessageInfo {
        return if (replyInfo == null) {
            ActiveMessageInfo.newBuilder()
                .setIsDeleted(isDeleted)
                .setIsReply(false)
                .build()
        } else {
            ActiveMessageInfo.newBuilder()
                .setIsDeleted(isDeleted)
                .setIsReply(true)
                .setReplyInfo(replyInfo)
                .build()
        }
    }
}

fun generateTextMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    activeMessageInfo: ActiveMessageInfoDataClass = ActiveMessageInfoDataClass(),
    messageText: String,
    isEdited: Boolean = false,
    editedTime: Long = 0L
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setTextMessage(
                if(genericParams.amountOfMessage == AmountOfMessage.ONLY_SKELETON) {
                    TextChatMessage.newBuilder()
                        .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                        .build()
                } else {
                    TextChatMessage.newBuilder()
                        .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                        .setMessageText(messageText)
                        .setIsEdited(isEdited)
                        .setEditedTime(editedTime)
                        .build()
                }
            )
            .build()
    )
}

fun generatePictureMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    activeMessageInfo: ActiveMessageInfoDataClass = ActiveMessageInfoDataClass(),
    imageWidth: Int,
    imageHeight: Int,
    pictureFileInBytes: ByteArray
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setPictureMessage(
                if (genericParams.amountOfMessage == AmountOfMessage.COMPLETE_MESSAGE_INFO) {
                    PictureChatMessage.newBuilder()
                        .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                        .setImageWidth(imageWidth)
                        .setImageHeight(imageHeight)
                        .build()
                } else {
                    PictureChatMessage.newBuilder()
                        .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                        .setImageWidth(imageWidth)
                        .setImageHeight(imageHeight)
                        .setPictureFileInBytes(ByteString.copyFrom(pictureFileInBytes))
                        .setPictureFileSize(pictureFileInBytes.size)
                        .build()
                }
            )
            .build()
    )
}

fun generateLocationMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    activeMessageInfo: ActiveMessageInfoDataClass = ActiveMessageInfoDataClass(),
    longitude: Double,
    latitude: Double,
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setLocationMessage(
                LocationChatMessage.newBuilder()
                    .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                    .setLongitude(longitude)
                    .setLatitude(latitude)
                    .build()
            )
            .build()
    )
}

fun generateMimeTypeMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    activeMessageInfo: ActiveMessageInfoDataClass = ActiveMessageInfoDataClass(),
    imageWidth: Int,
    imageHeight: Int,
    imageMimeType: String,
    urlOfDownload: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setMimeTypeMessage(
                MimeTypeChatMessage.newBuilder()
                    .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                    .setImageWidth(imageWidth)
                    .setImageHeight(imageHeight)
                    .setMimeType(imageMimeType)
                    .setUrlOfDownload(urlOfDownload)
                    .build()
            )
            .build()
    )
}

fun generateInviteMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    activeMessageInfo: ActiveMessageInfoDataClass = ActiveMessageInfoDataClass(),
    userAccountOid: String,
    userAccountName: String,
    invitedChatRoomId: String,
    invitedChatRoomName: String,
    invitedChatRoomPassword: String,
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setInviteMessage(
                InviteChatMessage.newBuilder()
                    .setActiveMessageInfo(activeMessageInfo.convertToActiveMessage())
                    .setInvitedUserAccountOid(userAccountOid)
                    .setInvitedUserName(userAccountName)
                    .setChatRoomId(invitedChatRoomId)
                    .setChatRoomName(invitedChatRoomName)
                    .setChatRoomPassword(invitedChatRoomPassword)
                    .build()
            )
            .build()
    )
}

fun generateUserKickedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    userAccountOid: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setUserKickedMessage(
                UserKickedChatMessage.newBuilder()
                    .setKickedAccountOid(userAccountOid)
                    .build()
            )
            .build()
    )
}

fun generateUserBannedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    userAccountOid: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setUserBannedMessage(
                UserBannedChatMessage.newBuilder()
                    .setBannedAccountOid(userAccountOid)
                    .build()
            )
            .build()
    )
}

fun generateDifferentUserJoinedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    userAccountState: AccountState.AccountStateInChatRoom,
    accountLastActivityTime: Long = System.currentTimeMillis(),
    userInfo: MemberSharedInfoMessage? = null
): ChatMessageToClient.Builder {

    genericParams.amountOfMessage = AmountOfMessage.ONLY_SKELETON
    genericParams.hasCompleteInfo = true

    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setDifferentUserJoinedMessage(
                DifferentUserJoinedChatRoomChatMessage.newBuilder()
                    .setMemberInfo(
                        if (userInfo == null) {
                            ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage.newBuilder()
                                .setAccountState(userAccountState)
                                .setAccountLastActivityTime(accountLastActivityTime)
                                .build()
                        } else {
                            ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage.newBuilder()
                                .setAccountState(userAccountState)
                                .setAccountLastActivityTime(accountLastActivityTime)
                                .setUserInfo(userInfo)
                                .build()
                        }
                    )
                    .build()
            )
            .build()
    )
}

fun generateDifferentUserLeftMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    newName: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setDifferentUserLeftMessage(
                DifferentUserLeftChatRoomChatMessage.newBuilder()
                    .setNewAdminAccountOid(newName)
                    .build()
            )
            .build()
    )
}

fun generateChatRoomNameUpdatedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    newName: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setChatRoomNameUpdatedMessage(
                ChatRoomNameUpdatedChatMessage.newBuilder()
                    .setNewChatRoomName(newName)
                    .build()
            )
            .build()
    )
}

fun generateChatRoomPasswordUpdatedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    newPassword: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setChatRoomPasswordUpdatedMessage(
                ChatRoomPasswordUpdatedChatMessage.newBuilder()
                    .setNewChatRoomPassword(newPassword)
                    .build()
            )
            .build()
    )
}

fun generateNewAdminPromotedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    promotedAccountOid: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setNewAdminPromotedMessage(
                NewAdminPromotedChatMessage.newBuilder()
                    .setPromotedAccountOid(promotedAccountOid)
                    .build()
            )
            .build()
    )
}

fun generateMatchCanceledMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    matchedAccountOid: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setMatchCanceledMessage(
                MatchCanceledChatMessage.newBuilder()
                    .setMatchedAccountOid(matchedAccountOid)
                    .build()
            )
            .build()
    )
}

fun generateEditedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    editedMessageUUID: String,
    newMessageText: String
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setEditedMessage(
                EditedMessageChatMessage.newBuilder()
                    .setMessageUuid(editedMessageUUID)
                    .setNewMessage(returnTextBasedOnAmountOfMessage(newMessageText, genericParams.amountOfMessage))
                    .build()
            )
            .build()
    )
}

fun generateDeletedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    deletedMessageUUID: String,
    deleteType: DeleteType
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setDeletedMessage(
                DeletedMessageChatMessage.newBuilder()
                    .setMessageUuid(deletedMessageUUID)
                    .setDeleteType(deleteType)
                    .build()
            )
            .build()
    )
}

fun generateUserActivityDetectedMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters()
): ChatMessageToClient.Builder {
    val message = setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setUserActivityDetectedMessage(
                UserActivityDetectedChatMessage.newBuilder()
                    .build()
            )
            .build()
    )

    message.clearMessageUuid()

    return message
}

fun generateCapMessage(
    genericParams: GenericMessageParameters = GenericMessageParameters()
): ChatMessageToClient.Builder {
    return setupGenericParameters(
        genericParams,
        MessageSpecifics.newBuilder()
            .setChatRoomCapMessage(
                ChatRoomCapMessage.newBuilder()
                    .build()
            )
            .build()
    )
}

fun generateThisUserJoinedChatRoomStart(
    userAccountOid: String,
    chatRoomInfo: ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage
): ChatMessageToClient.Builder {
    return ChatMessageToClient.newBuilder()
        .setSentByAccountId(userAccountOid)
        .setMessage(
            TypeOfChatMessage.newBuilder()
                .setStandardMessageInfo(
                    StandardChatMessageInfo.newBuilder()
                        .setChatRoomIdMessageSentFrom(chatRoomInfo.chatRoomId)
                        .build()
                )
                .setMessageSpecifics(
                    MessageSpecifics.newBuilder()
                        .setThisUserJoinedChatRoomStartMessage(
                            ThisUserJoinedChatRoomStartChatMessage.newBuilder()
                                .setChatRoomInfo(chatRoomInfo)
                                .build()
                        )
                        .build()
                )
                .build()
        )
}

fun generateThisUserJoinedChatRoomMember(
    userAccountOid: String,
    chatRoomIdSentFrom: String,
    memberInfo: ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage,
): ChatMessageToClient.Builder {
    return ChatMessageToClient.newBuilder()
        .setSentByAccountId(userAccountOid)
        .setMessage(
            TypeOfChatMessage.newBuilder()
                .setStandardMessageInfo(
                    StandardChatMessageInfo.newBuilder()
                        .setChatRoomIdMessageSentFrom(chatRoomIdSentFrom)
                        .build()
                )
                .setMessageSpecifics(
                    MessageSpecifics.newBuilder()
                        .setThisUserJoinedChatRoomMemberMessage(
                            ThisUserJoinedChatRoomMemberChatMessage.newBuilder()
                                .setMemberInfo(
                                    memberInfo
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
}

fun generateThisUserJoinedChatRoomFinished(
    userAccountOid: String,
    chatRoomIdSentFrom: String,
    matchMadeChatRoomOid: String = ""
): ChatMessageToClient.Builder {
    return ChatMessageToClient.newBuilder()
        .setSentByAccountId(userAccountOid)
        .setMessage(
            TypeOfChatMessage.newBuilder()
                .setStandardMessageInfo(
                    StandardChatMessageInfo.newBuilder()
                        .setDoNotUpdateUserState(false)
                        .setChatRoomIdMessageSentFrom(chatRoomIdSentFrom)
                        .build()
                )
                .setMessageSpecifics(
                    MessageSpecifics.newBuilder()
                        .setThisUserJoinedChatRoomFinishedMessage(
                            ThisUserJoinedChatRoomFinishedChatMessage.newBuilder()
                                .setMatchMadeChatRoomOid(matchMadeChatRoomOid)
                                .build()
                        )
                        .build()
                )
                .build()
        )
}

fun setupGenericParameters(
    genericParams: GenericMessageParameters = GenericMessageParameters(),
    messageSpecifics: MessageSpecifics
): ChatMessageToClient.Builder {
    return ChatMessageToClient.newBuilder()
        .setMessageUuid(genericParams.messageUUID)
        .setSentByAccountId(genericParams.sentByAccountOID)
        .setTimestampStored(genericParams.timestampStored)
        .setOnlyStoreMessage(genericParams.onlyStoreMessage)
        .setMessage(
            TypeOfChatMessage.newBuilder()
                .setStandardMessageInfo(
                    StandardChatMessageInfo.newBuilder()
                        .setChatRoomIdMessageSentFrom(genericParams.chatRoomIdSentFrom)
                        .setDoNotUpdateUserState(genericParams.doNotUpdateUserState)
                        .setAmountOfMessage(genericParams.amountOfMessage)
                        .setMessageHasCompleteInfo(genericParams.hasCompleteInfo)
                        .build()
                )
                .setMessageSpecifics(
                    messageSpecifics
                )
                .build()
        )
}

fun returnTextBasedOnAmountOfMessage(
    newMessageText: String,
    amountOfMessage: AmountOfMessage
): String {
    return when (amountOfMessage) {
        AmountOfMessage.ONLY_SKELETON -> {
            ""
        }
        AmountOfMessage.ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE -> {
            if (
                newMessageText.isEmpty()
                || newMessageText.length <= GlobalValues.server_imported_values.maximumNumberBytesTrimmedTextMessage
            ) {
                newMessageText
            } else {
                newMessageText.substring(
                    0,
                    GlobalValues.server_imported_values.maximumNumberBytesTrimmedTextMessage
                )
            }
        }
        else -> { // COMPLETE_MESSAGE_INFO
            newMessageText
        }
    }
}
