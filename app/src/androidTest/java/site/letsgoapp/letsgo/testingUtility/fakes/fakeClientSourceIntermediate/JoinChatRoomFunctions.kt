package site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate

import account_state.AccountState.AccountStateInChatRoom
import android.content.Context
import chat_message_to_client.ChatMessageToClientMessage
import chatroominfo.ChatRoomInfoMessageOuterClass
import com.google.protobuf.ByteString
import member_shared_info.MemberSharedInfoMessageOuterClass
import site.letsgoapp.letsgo.testingUtility.*

data class GeneratedUserInfoWithTimes(
    val memberInfoMessage: ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage,
    val userJoinedTime: Long,
    val userLastActivityTime: Long, //If the user account state is not in chat room, this will be the leave time
)

fun generateRandomUser(
    applicationContext: Context,
    messagesList: MutableList<ChatMessageToClientMessage.ChatMessageToClient> = mutableListOf(),
    passedAccountStateInChatRoom: AccountStateInChatRoom = AccountStateInChatRoom.UNRECOGNIZED
) : GeneratedUserInfoWithTimes {
    val accountStateInChatRoom =
        if (passedAccountStateInChatRoom != AccountStateInChatRoom.UNRECOGNIZED) {
            passedAccountStateInChatRoom
        } else {
            when ((0..2).random()) {
                0 -> {
                    AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM
                }
                1 -> {
                    AccountStateInChatRoom.ACCOUNT_STATE_BANNED
                }
                else -> {
                    AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                }
            }
        }

    val earliestUserCanJoin =
        FakeClientSourceIntermediate.joinChatRoomObjects.timeChatRoomCreated + 2
    val latestUserCanJoin =
        FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomInfoMessage.chatRoomLastActivityTime

    val userJoinedTime = (earliestUserCanJoin..latestUserCanJoin).random()
    val userLastActivityTime =
        (userJoinedTime..FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomInfoMessage.chatRoomLastActivityTime).random()

    return if (accountStateInChatRoom == AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM) {
        val userInfo = generateRandomMemberSharedInfoMessageForMatch(applicationContext)

        messagesList.add(
            generateDifferentUserJoinedMessage(
                GenericMessageParameters(
                    sentByAccountOID = userInfo.accountOid,
                    timestampStored = userJoinedTime,
                    onlyStoreMessage = true,
                    chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId
                ),
                accountStateInChatRoom,
                userJoinedTime
            ).build()
        )

        GeneratedUserInfoWithTimes(
            ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage.newBuilder()
                .setUserInfo(userInfo)
                .setAccountLastActivityTime(userLastActivityTime)
                .setAccountState(accountStateInChatRoom)
                .build(),
            userJoinedTime,
            userLastActivityTime
        )
    } else { //User not in chat room

        val userOid = generateRandomOidForTesting()

        messagesList.add(
            generateDifferentUserJoinedMessage(
                GenericMessageParameters(
                    sentByAccountOID = userOid,
                    timestampStored = userJoinedTime,
                    onlyStoreMessage = true,
                    chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId
                ),
                accountStateInChatRoom,
                userJoinedTime
            ).build()
        )

        if (accountStateInChatRoom == AccountStateInChatRoom.ACCOUNT_STATE_BANNED) {
            messagesList.add(
                generateUserBannedMessage(
                    GenericMessageParameters(
                        sentByAccountOID = FakeClientSourceIntermediate.accountStoredOnServer!!.accountOID,
                        timestampStored = userLastActivityTime,
                        onlyStoreMessage = true,
                        chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId
                    ),
                    userOid
                ).build()
            )
        } else if ((0..1).random() == 0) { //user was kicked
            generateUserKickedMessage(
                GenericMessageParameters(
                    sentByAccountOID = FakeClientSourceIntermediate.accountStoredOnServer!!.accountOID,
                    timestampStored = userLastActivityTime,
                    onlyStoreMessage = true,
                    chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId
                ),
                userOid
            ).build()
        } else { //user left
            messagesList.add(
                generateDifferentUserLeftMessage(
                    GenericMessageParameters(
                        sentByAccountOID = userOid,
                        timestampStored = userLastActivityTime,
                        onlyStoreMessage = true,
                        chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId
                    ),
                    userOid
                ).build()
            )
        }

        val userThumbnail = generateRandomThumbnail(applicationContext)

        GeneratedUserInfoWithTimes(
            ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage.newBuilder()
                .setUserInfo(
                    MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage.newBuilder()
                        .setAccountOid(userOid)
                        .setAccountName(generateRandomFirstNameForTesting())
                        .setAccountThumbnailIndex(0)
                        .setAccountThumbnailSize(userThumbnail.size)
                        .setAccountThumbnail(ByteString.copyFrom(userThumbnail))
                        .setAccountThumbnailTimestamp(System.currentTimeMillis())
                        .build()
                )
                .setAccountLastActivityTime(userLastActivityTime)
                .setAccountState(accountStateInChatRoom)
                .build(),
            userJoinedTime,
            userLastActivityTime
        )
    }
}

data class JoinChatRoomObjects(
    var usersToSendBackOnJoinChatRoom: List<GeneratedUserInfoWithTimes> = listOf(),
    var messagesToSendBackOnJoinChatRoom: List<ChatMessageToClientMessage.ChatMessageToClient> = listOf(),
    var chatRoomInfoMessage: ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage = ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage.getDefaultInstance(),
    var chatRoomId: String = generateRandomChatRoomIdForTesting()
) {

    var timeChatRoomCreated = 0L
        private set

    fun resetToDefaults() {
        usersToSendBackOnJoinChatRoom = listOf()
        messagesToSendBackOnJoinChatRoom = listOf()
        chatRoomInfoMessage = ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage.getDefaultInstance()
        chatRoomId = generateRandomChatRoomIdForTesting()
    }

    fun generateRandomChatRoomInfoMessage(
        passedChatRoomId: String
    ) {
        chatRoomId = passedChatRoomId

        val userAccountState = if (usersToSendBackOnJoinChatRoom.isEmpty()) {
            AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
        } else {
            AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
        }

        timeChatRoomCreated =
            (System.currentTimeMillis() / 3..System.currentTimeMillis() / 2).random()
        val earliestPossibleActivityTime = timeChatRoomCreated + 2

        //currentUserLastActivityTime <= chatRoomLastActivityTime <= lastObservedTime
        val currentUserLastActivityTime =
            (earliestPossibleActivityTime..System.currentTimeMillis()).random()
        val chatRoomLastActivityTime =
            (currentUserLastActivityTime..System.currentTimeMillis()).random()
        val lastObservedTime = (chatRoomLastActivityTime..System.currentTimeMillis()).random()

        //Must be <= currentUserLastActivityTime
        val timeJoined = (earliestPossibleActivityTime..currentUserLastActivityTime).random()

        chatRoomInfoMessage = ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage.newBuilder()
            .setChatRoomId(chatRoomId)
            .setChatRoomLastActivityTime(chatRoomLastActivityTime)
            .setChatRoomLastObservedTime(lastObservedTime)
            .setChatRoomName(generateRandomString(((1..100).random())))
            .setChatRoomPassword(generateRandomString(((0..10).random())))
            .setAccountState(userAccountState) //Current user account state
            .setUserLastActivityTime(currentUserLastActivityTime) //Current user last activity time
            .setTimeJoined(timeJoined)
            .build()
    }
}

