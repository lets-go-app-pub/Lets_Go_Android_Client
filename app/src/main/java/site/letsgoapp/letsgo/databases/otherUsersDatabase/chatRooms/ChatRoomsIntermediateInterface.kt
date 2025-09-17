package site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms

import account_state.AccountState
import type_of_chat_message.TypeOfChatMessageOuterClass.MessageSpecifics

interface ChatRoomsIntermediateInterface {

    //insert single chat room
    suspend fun insertChatRoom(chatRoomDataClass: ChatRoomDataClass)

    //returns true if chat room exists false if not
    suspend fun chatRoomExists(chatRoomId: String): Boolean

    //returns each chat rooms last observed time
    suspend fun getAllChatRoomLastObservedTimes(): List<ChatRoomObservedTimeInfo>

    //return a specified chat room from the table
    suspend fun getSingleChatRoom(chatRoomId: String): ChatRoomWithMemberMapDataClass

    //return chat room Id if matching chat room exists, empty string if not
    suspend fun matchingChatRoomExists(accountOID: String): String

    //gets the last time observed from the select chat room
    suspend fun getSingleChatRoomLastTimeObserved(chatRoomId: String): Long

    //return the specified chatRoomLastActivityTime time for the chatRoomId
    suspend fun getSingleChatRoomLastActiveTime(chatRoomId: String): Long

    //sets the last updated time for a specified chat room
    suspend fun setSingleChatRoomLastTimeUpdatedLastTimeObserved(chatRoomId: String, newLastTimeUpdated: Long)

    //return a specified chat room from the table by the matching account OID
    suspend fun getSingleChatRoomByMatchingOID(matchingChatRoomOID: String): ChatRoomsDataEntity?

    //return a list of all matches from the table
    suspend fun getAllChatRooms(): MutableList<ChatRoomWithMemberMapDataClass>

    //return a list of requested chat room ids and their joined time
    suspend fun getChatRoomIdsJoinedTime(chatRoomIds: List<String>): List<ChatRoomIdAndTimeJoined>

    //return a list of all chat room ids and their last time updated
    suspend fun getAllChatRoomIdsTimeLastUpdatedAndLastObservedTime(): List<ChatRoomIdAndTimeLastUpdated>

    //delete a specific match from the table by index
    suspend fun deleteChatRoom(chatRoomId: String)

    //delete all
    suspend fun clearTable()

    //sets messageID for the passed chat room Id and updates the 'last time updated' from server value if it is greater than the stored value
    //also updates either the name or password depending on the value of typeOfChatMessage
    suspend fun updateAccountInfoUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        messageSpecifics: MessageSpecifics,
        setUserActiveTime: Boolean,
        timeLastUpdated: Long,
    )

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    suspend fun updateAccountStateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    )

    //sets userStateInChatRoom, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    suspend fun updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    )

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value)
    suspend fun updateAccountStateUserLastActiveTimeLastTimeUpdate(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom,
        timeLastUpdated: Long
    )

    //sets messageIDs for the passed chat room Id and updates the 'last time updated' from server value if it is greater than the stored value
    suspend fun updateLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        timeLastUpdated: Long
    )

    suspend fun updateChatRoomObservedTimeUserLastActiveTimeMatchingOid(
        chatRoomId: String,
        timestampStored: Long
    )

    //sets messageIDs for the passed chat room Id and updates the 'last time updated' and the 'chat room last active time' from server value if it is greater than the stored value
    suspend fun updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        timeLastUpdated: Long
    )

    suspend fun updateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        lastTimeUpdated: Long
    )

    //sets userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    suspend fun updateUserLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomId: String,
        lastTimeUpdated: Long
    )

    suspend fun updateChatRoomObservedTimeUserLastActiveTimeChatRoomLastActiveTimeMatchingOid(
        chatRoomId: String,
        timestampStored: Long
    )

    suspend fun updateChatRoomObservedTimeUserLastActiveTime(
        chatRoomId: String,
        timestampStored: Long
    )

    //sets chatRoomName (if not empty), chatRoomPassword (if not empty) and chatRoomLastActivityTime (if greater than stored value)
    suspend fun setUpdateChatRoomFunctionReturnValues(
        chatRoomId: String,
        chatRoomName: String,
        chatRoomPassword: String,
        eventOid: String,
        pinnedLocationLatitude: Double,
        pinnedLocationLongitude: Double,
        chatRoomLastActiveTime: Long
    )

    suspend fun setQrCodeValues(
        chatRoomId: String,
        qrCodePath: String,
        qrCodeMessage: String,
        qrCodeTimeUpdated: Long,
    );

    //set chatRoomLastObservedTime
    suspend fun updateUserLastObservedTime(chatRoomId: String, lastObservedTime: Long)

    //update lastUpdatedTime for this chat room if it is less than the passed time
    suspend fun updateTimeLastUpdated(
        chatRoomId: String,
        timeLastUpdated: Long
    )

    //sets userLastActivityTime (if greater than stored value)
    suspend fun updateUserLastActiveTime(chatRoomId: String, timestamp: Long)

    //gets the current users info and sends it to the server for updates
    suspend fun getUpdateChatRoomInfo(chatRoomId: String): UpdateChatRoomInfo?

    suspend fun getQRCodePath(chatRoomId: String): String

    suspend fun getEventId(chatRoomId: String): String

    //update current user's account state in the chat room
    suspend fun updateAccountState(
        chatRoomId: String,
        accountState: AccountState.AccountStateInChatRoom
    )

    suspend fun updateEventOid(
        chatRoomId: String,
        eventOid: String,
    )

    //set if notifications are enabled for the passed chat room
    suspend fun setNotificationsEnabled(chatRoomId: String, notificationsEnabled: Boolean)

    //return whether the notifications are enabled or not for the passed chat room
    suspend fun getNotificationsChatRoomInfo(chatRoomId: String): NotificationsEnabledChatRoomInfo

    //returns a list of chat room Ids that match the passed criteria
    suspend fun searchForChatRoomMatches(matchingString: String): Set<String>

    //returns true if for all chat rooms chatRoomLastObservedTime >= chatRoomLastActivityTime (no new messages in any chat room)
    //returns false otherwise
    suspend fun allChatRoomMessagesHaveBeenObserved(): Boolean

    suspend fun getAllChatRoomFiles(): List<ChatRoomFileInfo>
}
