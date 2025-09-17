package site.letsgoapp.letsgo.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessageFieldsForNotifications
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomIdAndTimeJoined
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.ReturnForNotificationMessage

class NotificationInfoRepository(
    private val chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    private val messagesDaoDataSource: MessagesDaoIntermediateInterface,
    private val otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher
) {
    //request notification info for listen messages
    suspend fun requestAllMessageByUUID(messageUUIDs: List<String>): List<MessageFieldsForNotifications> {
        return messagesDaoDataSource.requestAllMessageByUUID(messageUUIDs)
    }

    //return the user info needed for a notification message
    suspend fun getUserInfoForNotificationMessage(accountOIDs: List<String>): List<ReturnForNotificationMessage> {
        return otherUsersDaoDataSource.getUserInfoForNotificationMessage(accountOIDs)
    }

    //return the user info needed for a notification message
    suspend fun getChatRoomsLastTimeJoined(chatRoomIds: List<String>): List<ChatRoomIdAndTimeJoined> {
        return chatRoomsDaoDataSource.getChatRoomIdsJoinedTime(chatRoomIds)
    }
}