package site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms

import account_state.AccountState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.isValidMongoDBOID
import type_of_chat_message.TypeOfChatMessageOuterClass

//NOTE: this database is expected to be accessed in a synchronized fashion because several commands have multiple steps to them
class ChatRoomsDaoIntermediate(
    private val chatRoomsDatabaseDao: ChatRoomsDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : ChatRoomsIntermediateInterface {

    override suspend fun insertChatRoom(chatRoomDataClass: ChatRoomDataClass) {
        chatRoomsDatabaseDao.insertChatRoom(
            convertChatRoomDataClassToChatRoomsDataEntity(
                chatRoomDataClass
            )
        )
    }

    //returns true if chat room exists false if not
    override suspend fun chatRoomExists(chatRoomId: String): Boolean {
        return chatRoomsDatabaseDao.chatRoomExists(chatRoomId) == 1
    }

    override suspend fun getAllChatRoomLastObservedTimes(): List<ChatRoomObservedTimeInfo> {
        return chatRoomsDatabaseDao.getAllChatRoomLastObservedTimes()
    }

    //return a specified chat room from the table
    override suspend fun getSingleChatRoom(chatRoomId: String): ChatRoomWithMemberMapDataClass {
        return convertChatRoomsDataEntityToChatRoomWithMemberMapDataClass(
            chatRoomsDatabaseDao.getSingleChatRoom(
                chatRoomId
            ) ?: ChatRoomsDataEntity()
        )
    }

    override suspend fun matchingChatRoomExists(accountOID: String): String {
        return chatRoomsDatabaseDao.matchingChatRoomExists(accountOID) ?: ""
    }

    override suspend fun getSingleChatRoomLastTimeObserved(
        chatRoomId: String
    ): Long {
        return chatRoomsDatabaseDao.getSingleChatRoomLastTimeObserved(chatRoomId) ?: -1L
    }

    //return the specified chatRoomLastActivityTime time for the chatRoomId
    override suspend fun getSingleChatRoomLastActiveTime(chatRoomId: String): Long {
        return chatRoomsDatabaseDao.getSingleChatRoomLastActiveTime(chatRoomId) ?: -1L
    }

    //sets the last updated time for a specified chat room
    override suspend fun setSingleChatRoomLastTimeUpdatedLastTimeObserved(
        chatRoomId: String,
        newLastTimeUpdated: Long
    ) {
        chatRoomsDatabaseDao.setSingleChatRoomLastTimeUpdatedLastTimeObserved(
            chatRoomId,
            newLastTimeUpdated,
        )
    }

    override suspend fun getSingleChatRoomByMatchingOID(matchingChatRoomOID: String): ChatRoomsDataEntity? {

        if (matchingChatRoomOID.isValidMongoDBOID()) {
            return chatRoomsDatabaseDao.getSingleChatRoomByMatchingOID(matchingChatRoomOID)
        }

        return null
    }

    override suspend fun getAllChatRooms(): MutableList<ChatRoomWithMemberMapDataClass> {
        val chatRooms = chatRoomsDatabaseDao.getAllChatRooms()

        val returnList = mutableListOf<ChatRoomWithMemberMapDataClass>()

        for (r in chatRooms) {
            returnList.add(convertChatRoomsDataEntityToChatRoomWithMemberMapDataClass(r))
        }

        return returnList
    }

    //return a list of requested chat room ids and their joined time
    override suspend fun getChatRoomIdsJoinedTime(chatRoomIds: List<String>): List<ChatRoomIdAndTimeJoined> {
        if (chatRoomIds.isEmpty())
            return emptyList()

        return chatRoomsDatabaseDao.getChatRoomIdsJoinedTime(chatRoomIds)
    }

    //return a list of all chat room ids and their last time updated
    override suspend fun getAllChatRoomIdsTimeLastUpdatedAndLastObservedTime(): List<ChatRoomIdAndTimeLastUpdated> {
        return chatRoomsDatabaseDao.getAllChatRoomIdsTimeLastUpdatedAndLastObservedTime()
    }

    override suspend fun deleteChatRoom(chatRoomId: String) {
        chatRoomsDatabaseDao.deleteChatRoom(chatRoomId)
    }

    override suspend fun clearTable() {
        chatRoomsDatabaseDao.clearTable()
    }

    //sets messageID for the passed chat room Id and updates the variables chatRoomLastActivityTime, lastTimeUpdated, matchingChatRoomOID
    //will update userLastActivityTime if setUserActiveTime == true and
    //will update either the name or password depending on the value of typeOfChatMessage
    override suspend fun updateAccountInfoUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        messageSpecifics: TypeOfChatMessageOuterClass.MessageSpecifics,
        setUserActiveTime: Boolean,
        timeLastUpdated: Long
    ) {

        val typeOfChatMessage = messageSpecifics.messageBodyCase

        when {
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE
                    && setUserActiveTime -> { //chat room name updated along with user last active time
                chatRoomsDatabaseDao.updateChatRoomNameUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.chatRoomNameUpdatedMessage.newChatRoomName,
                    timeLastUpdated
                )
            }
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE
                    && !setUserActiveTime -> { //chat room updated without user last active time
                chatRoomsDatabaseDao.updateChatRoomNameChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.chatRoomNameUpdatedMessage.newChatRoomName,
                    timeLastUpdated
                )
            }
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
                    && setUserActiveTime -> { //chat room password updated along with user last active time
                chatRoomsDatabaseDao.updateChatRoomPasswordUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.chatRoomPasswordUpdatedMessage.newChatRoomPassword,
                    timeLastUpdated
                )
            }
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
                    && !setUserActiveTime -> { //chat room password updated without user last active time
                chatRoomsDatabaseDao.updateChatRoomPasswordChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.chatRoomPasswordUpdatedMessage.newChatRoomPassword,
                    timeLastUpdated
                )
            }
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE
                    && setUserActiveTime -> { //chat room password updated along with user last active time
                chatRoomsDatabaseDao.updateChatRoomPinnedLocationUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.newPinnedLocationMessage.longitude,
                    messageSpecifics.newPinnedLocationMessage.latitude,
                    timeLastUpdated
                )
            }
            typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE
                    && !setUserActiveTime -> { //chat room password updated without user last active time
                chatRoomsDatabaseDao.updateChatRoomPinnedLocationChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
                    chatRoomId,
                    messageSpecifics.newPinnedLocationMessage.longitude,
                    messageSpecifics.newPinnedLocationMessage.latitude,
                    timeLastUpdated
                )
            }
            else -> {
            }
        }

    }

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    override suspend fun updateAccountStateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateAccountStateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
            chatRoomId,
            accountState.number,
            timeLastUpdated
        )
    }

    //sets userStateInChatRoom, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    override suspend fun updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
            chatRoomId,
            accountState.number,
            timeLastUpdated
        )
    }

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value)
    override suspend fun updateAccountStateUserLastActiveTimeLastTimeUpdate(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateAccountStateUserLastActiveTimeLastTimeUpdate(
            chatRoomId,
            accountState.number,
            timeLastUpdated
        )
    }

    //sets messageIDs for the passed chat room Id and updates the 'last time updated' from server value if it is greater than the stored value
    override suspend fun updateLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateLastTimeUpdatedMatchingOid(
            chatRoomId,
            timeLastUpdated
        )
    }

    override suspend fun updateChatRoomObservedTimeUserLastActiveTimeMatchingOid(
        chatRoomId: String,
        timestampStored: Long
    ) {
        chatRoomsDatabaseDao.updateChatRoomObservedTimeUserLastActiveTimeMatchingOid(
            chatRoomId,
            timestampStored
        )
    }

    //sets messageID for the passed chat room Id and updates the 'last time updated' from server value if it is greater than the stored value
    override suspend fun updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
            chatRoomId,
            timeLastUpdated
        )
    }

    override suspend fun updateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        lastTimeUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
            chatRoomId,
            lastTimeUpdated
        )
    }

    override suspend fun updateUserLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        lastTimeUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateUserLastActiveTimeLastTimeUpdatedMatchingOid(
            chatRoomId,
            lastTimeUpdated
        )
    }

    override suspend fun updateChatRoomObservedTimeUserLastActiveTimeChatRoomLastActiveTimeMatchingOid(
        chatRoomId: String,
        timestampStored: Long
    ) {
        chatRoomsDatabaseDao.updateChatRoomObservedTimeUserLastActiveTimeChatRoomLastActiveTimeMatchingOid(
            chatRoomId,
            timestampStored
        )
    }

    override suspend fun updateChatRoomObservedTimeUserLastActiveTime(
        chatRoomId: String,
        timestampStored: Long
    ) {
        chatRoomsDatabaseDao.updateChatRoomObservedTimeUserLastActiveTime(
            chatRoomId,
            timestampStored
        )
    }

    //sets chatRoomName (if not empty), chatRoomPassword (if not empty) and chatRoomLastActivityTime (if greater than stored value)
    override suspend fun setUpdateChatRoomFunctionReturnValues(
        chatRoomId: String,
        chatRoomName: String,
        chatRoomPassword: String,
        eventOid: String,
        pinnedLocationLatitude: Double,
        pinnedLocationLongitude: Double,
        chatRoomLastActiveTime: Long,
    ) {
        chatRoomsDatabaseDao.updateChatRoomNameChatRoomPasswordChatRoomLastActiveTime(
            chatRoomId,
            chatRoomName,
            chatRoomPassword,
            eventOid,
            pinnedLocationLatitude,
            pinnedLocationLongitude,
            chatRoomLastActiveTime,
        )
    }

    override suspend fun setQrCodeValues(
        chatRoomId: String,
        qrCodePath: String,
        qrCodeMessage: String,
        qrCodeTimeUpdated: Long,
    ) {
        chatRoomsDatabaseDao.setQrCodeValues(
            chatRoomId,
            qrCodePath,
            qrCodeMessage,
            qrCodeTimeUpdated,
        )
    }
    override suspend fun updateTimeLastUpdated(
        chatRoomId: String,
        timeLastUpdated: Long
    ) {
        chatRoomsDatabaseDao.updateTimeLastUpdated(
            chatRoomId,
            timeLastUpdated,
        )
    }

    override suspend fun updateUserLastObservedTime(chatRoomId: String, lastObservedTime: Long) {
        chatRoomsDatabaseDao.updateUserLastObservedTime(chatRoomId, lastObservedTime)
    }

    //sets userLastActivityTime (if greater than stored value)
    override suspend fun updateUserLastActiveTime(chatRoomId: String, timestamp: Long) {
        chatRoomsDatabaseDao.updateUserLastActiveTime(chatRoomId, timestamp)
    }

    //returns the member info for updates of this user
    override suspend fun getUpdateChatRoomInfo(chatRoomId: String): UpdateChatRoomInfo? {
        return chatRoomsDatabaseDao.getUpdateChatRoomInfo(chatRoomId)
    }

    override suspend fun getQRCodePath(chatRoomId: String): String {
        return chatRoomsDatabaseDao.getQRCodePath(chatRoomId) ?: GlobalValues.server_imported_values.qrCodeDefault;
    }

    override suspend fun getEventId(chatRoomId: String): String {
        return chatRoomsDatabaseDao.getEventId(chatRoomId) ?: GlobalValues.server_imported_values.eventIdDefault;
    }

    //update current user's account state in the chat room
    override suspend fun updateAccountState(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom
    ) {
        chatRoomsDatabaseDao.updateAccountState(
            chatRoomId,
            accountState.number
        )
    }

    override suspend fun updateEventOid(
        chatRoomId: String,
        eventOid: String,
    ) {
       chatRoomsDatabaseDao.updateEventOid(
           chatRoomId,
           eventOid
       )
    }

    //set if notifications are enabled for the passed chat room
    override suspend fun setNotificationsEnabled(
        chatRoomId: String,
        notificationsEnabled: Boolean
    ) {
        chatRoomsDatabaseDao.setNotificationsEnabled(chatRoomId, notificationsEnabled)
    }

    //return whether the notifications are enabled or not for the passed chat room
    override suspend fun getNotificationsChatRoomInfo(chatRoomId: String): NotificationsEnabledChatRoomInfo {
        return chatRoomsDatabaseDao.getNotificationsChatRoomInfo(chatRoomId)
            ?: NotificationsEnabledChatRoomInfo(
                "",
                "",
                false
            )
    }

    //returns a list of chat room Ids that match the passed criteria
    override suspend fun searchForChatRoomMatches(matchingString: String): Set<String> {
        return chatRoomsDatabaseDao.searchForChatRoomMatches(matchingString).toSet()
    }

    //returns true if for all chat rooms chatRoomLastObservedTime >= chatRoomLastActivityTime (no new messages in any chat room)
    //returns false otherwise
    override suspend fun allChatRoomMessagesHaveBeenObserved(): Boolean {
        return chatRoomsDatabaseDao.allChatRoomMessagesHaveBeenObserved() == null
    }

    override suspend fun getAllChatRoomFiles(): List<ChatRoomFileInfo> {
        return chatRoomsDatabaseDao.getAllChatRoomFiles()
    }
}