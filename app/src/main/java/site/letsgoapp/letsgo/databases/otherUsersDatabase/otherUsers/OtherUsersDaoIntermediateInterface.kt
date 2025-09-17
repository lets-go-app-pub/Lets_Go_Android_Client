package site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers

import account_state.AccountState
import site.letsgoapp.letsgo.utilities.TransactionWrapper
import update_other_user_messages.UpdateOtherUserMessages

interface OtherUsersDaoIntermediateInterface {

    //insert single user
    suspend fun upsertSingleOtherUser(otherUsersDataEntity: OtherUsersDataEntity)

    //insert multiple users
    suspend fun upsertMultipleOtherUser(otherUsersDataEntity: MutableList<OtherUsersDataEntity>)

    //returns true if other user exists false if not
    suspend fun otherUserExists(accountOID: String): Boolean

    //delete all
    suspend fun clearTable()

    //delete a specific match from the table by index
    suspend fun deleteOtherUser(accountOID: String)

    //get a single member
    suspend fun getSingleOtherUser(accountOID: String): OtherUsersDataEntity?

    //return all users inside list
    suspend fun getOtherUsersInList(accountOIDs: List<String> = listOf()): List<OtherUsersDataEntity>

    //return all users that can have info trimmed from them (amountOfMessage == SKELETON_ONLY)
    suspend fun getOtherUsersInListThatCanBeTrimmed(accountOIDs: List<String>): List<OtherUserFieldsForTrimming>

    //return a list of all other users belonging to a specific chat room
    suspend fun getAllOtherUsersForChatRoom(chatRoomId: String): List<OtherUsersDataEntity>

    //return a list of all other users that have not been observed recently
    suspend fun getUsersNotObservedRecentlyThatCanBeTrimmed(): List<OtherUserFieldsForTrimming>

    //return a list of all other users that and can be trimmed and are not one of the account OIDs sent
    suspend fun getUsersThatCanBeTrimmed(notAccountOIDs: List<String>): List<OtherUserFieldsForTrimming>

    //return a list of all other users file paths
    suspend fun getOtherUsersFilePaths(): List<OtherUserFilePaths>

    //sets all accountOIDs to be trimmed
    /** This function does not remove the files themselves, it only changes the database values. **/
    suspend fun setOtherUsersInListToTrimmed(accountOIDs: List<String>)

    //returns a list of member info for updates to use in requesting updated info from the server
    suspend fun getMemberInfoForChatRoomUpdates(chatRoomId: String): MutableList<UpdateOtherUserMessages.OtherUserInfoForUpdates>

    //returns a single of member info for updates to use in requesting updated info from the server, excludes current member
    // will return null if user does not exist OR if user has been updated recently. Only used for requesting updates to
    // chat room members (also see getSingleMemberInfoForMatchUpdates()).
    /** Only meant for use with updateSingleChatRoomMemberInfo() from ApplicationRepository. **/
    suspend fun getSingleMemberInfoForChatRoomUpdates(
        chatRoomId: String,
        accountOID: String,
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates?

    //returns a single of member info for updates to use in requesting updated info from the server, excludes current member
    // will return null if user does not exist OR if user has been updated recently. Only used for requesting updates to
    // matching members (also see getSingleMemberInfoForChatRoomUpdates()).
    /** Only meant for use with updateSingleMatchMemberInfo() from ApplicationRepository. **/
    suspend fun getSingleMemberInfoForMatchUpdates(
        accountOID: String
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates?

    //returns a single of member info for updates to use in requesting updated info from the server, excludes current member
    suspend fun getOtherUserAccountStateInChatRoom(
        chatRoomId: String,
        accountOID: String,
    ): AccountState.AccountStateInChatRoom?

    //updates pictures_update_attempted_timestamp to the passed timestamp for all other users in list
    suspend fun updateTimestampsPicturesAttemptedUpdate(
        accountOIDs: List<String>,
        timestampAttempted: Long
    )

    //updates picture at passed index to corrupt
    suspend fun updatePictureToCorrupt(
        accountOID: String,
        timestamp: Long,
        index: Int
    )

    //updates other user thumbnail to corrupt
    suspend fun updateThumbnailToCorrupt(
        accountOID: String,
        timestamp: Long,
    )

    //updates user_info_last_observed to the passed timestamp for other user
    suspend fun updateTimestampUserInfoLastObserved(accountOID: String, timestamp: Long)

    //sets another user to admin in the passed chat room
    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    suspend fun updateUserAccountState(
        accountOID: String,
        chatRoomId: String,
        newAccountState: AccountState.AccountStateInChatRoom,
        transactionWrapper: TransactionWrapper,
    )

    //sets another user to account state and active time for the passed chat room
    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    suspend fun updateUserAccountStateAndActiveTime(
        accountOID: String,
        chatRoomId: String,
        newAccountState: AccountState.AccountStateInChatRoom,
        timeStamp: Long,
        transactionWrapper: TransactionWrapper
    )

    //sets a last active time to the specific chat room (if it is greater than the previous time)
    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    suspend fun setUserLastActiveTimeInChatRoom(
        accountOID: String,
        chatRoomId: String,
        timeStamp: Long,
        transactionWrapper: TransactionWrapper
    )

    //return the user info needed for a notification message
    suspend fun getUserInfoForNotificationMessage(accountOID: String): ReturnForNotificationMessage

    //return user info needed for multiple users for a notification messages
    suspend fun getUserInfoForNotificationMessage(accountOIDs: List<String>): List<ReturnForNotificationMessage>

    //return the user name needed for a notification message
    suspend fun getUserNameForNotificationMessage(userAccountOID: String): String?

    //returns a list of chat room Ids that match the passed criteria
    suspend fun searchForChatRoomMatches(matchingString: String): Set<String>

    //count the number of users in the chat room
    suspend fun getFirstTwoUserNamesInChatRoom(chatRoomId: String): List<String>
}