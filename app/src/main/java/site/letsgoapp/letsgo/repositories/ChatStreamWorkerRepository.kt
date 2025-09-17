package site.letsgoapp.letsgo.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.NotificationsEnabledChatRoomInfo
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.ReturnForNotificationMessage

class ChatStreamWorkerRepository(
    private val chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    private val messagesDaoDataSource: MessagesDaoIntermediateInterface,
    private val otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun updateAllMessagesToDoNotRequireNotifications() {
        messagesDaoDataSource.updateAllMessagesToDoNotRequireNotifications()
    }

    suspend fun updateMessageToDoesNotRequireNotifications(uuidPrimaryKey: String) {
        messagesDaoDataSource.updateMessageToDoesNotRequireNotifications(uuidPrimaryKey)
    }

    suspend fun getNotificationsChatRoomInfo(chatRoomId: String): NotificationsEnabledChatRoomInfo {
        return chatRoomsDaoDataSource.getNotificationsChatRoomInfo(chatRoomId)
    }

    suspend fun getUserInfoForNotificationMessage(userAccountOID: String): ReturnForNotificationMessage {
        return otherUsersDaoDataSource.getUserInfoForNotificationMessage(userAccountOID)
    }

    suspend fun getUserNameForNotificationMessage(userAccountOID: String): String? {
        return otherUsersDaoDataSource.getUserNameForNotificationMessage(userAccountOID)
    }

    suspend fun getFirstTwoUserNamesInChatRoom(chatRoomId: String): List<String> {
        return otherUsersDaoDataSource.getFirstTwoUserNamesInChatRoom(chatRoomId)
    }
}