package site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers

import account_state.AccountState
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import lets_go_event_status.LetsGoEventStatusOuterClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.globalAccess.ServiceLocator.provideTransactionWrapper
import site.letsgoapp.letsgo.utilities.*
import update_other_user_messages.UpdateOtherUserMessages
import java.io.File

//NOTE: this database is expected to be accessed in a synchronized fashion because several commands have multiple steps to them
class OtherUsersDaoIntermediate(
    private val otherUsersDatabaseDao: OtherUsersDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : OtherUsersDaoIntermediateInterface {

    //insert single user
    override suspend fun upsertSingleOtherUser(otherUsersDataEntity: OtherUsersDataEntity) {
        otherUsersDatabaseDao.upsertSingleOtherUser(otherUsersDataEntity)
    }

    //insert multiple users
    override suspend fun upsertMultipleOtherUser(otherUsersDataEntity: MutableList<OtherUsersDataEntity>) {
        otherUsersDatabaseDao.upsertMultipleOtherUser(otherUsersDataEntity)
    }

    //returns true if other user exists false if not
    override suspend fun otherUserExists(accountOID: String): Boolean {
        return otherUsersDatabaseDao.otherUserExists(accountOID) == 1
    }

    //delete all
    override suspend fun clearTable() {
        otherUsersDatabaseDao.clearTable()
    }

    //delete a specific match from the table by index
    override suspend fun deleteOtherUser(accountOID: String) {
        otherUsersDatabaseDao.deleteOtherUser(accountOID)
    }

    override suspend fun getSingleOtherUser(accountOID: String): OtherUsersDataEntity? {
        return otherUsersDatabaseDao.getSingleOtherUser(accountOID)
    }

    //return all users inside list
    override suspend fun getOtherUsersInList(accountOIDs: List<String>): List<OtherUsersDataEntity> {
        Log.i("matches_results", "requesting_oids: $accountOIDs")
        return otherUsersDatabaseDao.getOtherUsersInList(accountOIDs)
    }

    //return all users that can have info trimmed from them (amountOfMessage == SKELETON_ONLY)
    override suspend fun getOtherUsersInListThatCanBeTrimmed(accountOIDs: List<String>): List<OtherUserFieldsForTrimming> {
        return otherUsersDatabaseDao.getOtherUsersInListThatCanBeTrimmed(accountOIDs)
    }

    //return a list of all other users belonging to a specific chat room
    override suspend fun getAllOtherUsersForChatRoom(chatRoomId: String): List<OtherUsersDataEntity> {
        return otherUsersDatabaseDao.getAllOtherUsersForChatRoom(chatRoomId)
    }

    //return a list of all other users that have not been observed recently
    override suspend fun getUsersNotObservedRecentlyThatCanBeTrimmed(): List<OtherUserFieldsForTrimming> {
        val earliestTimestamp =
            getCurrentTimestampInMillis() - GlobalValues.server_imported_values.timeInfoHasNotBeenObservedBeforeCleaned
        return otherUsersDatabaseDao.getUsersNotObservedRecentlyThatCanBeTrimmed(earliestTimestamp)
    }

    //return a list of all other users that and can be trimmed and are not one of the account OIDs sent
    override suspend fun getUsersThatCanBeTrimmed(notAccountOIDs: List<String>): List<OtherUserFieldsForTrimming> {
        return otherUsersDatabaseDao.getUsersThatCanBeTrimmed(notAccountOIDs)
    }

    //return a list of all other users file paths
    override suspend fun getOtherUsersFilePaths(): List<OtherUserFilePaths> {
        return otherUsersDatabaseDao.getOtherUsersFilePaths()
    }

    //sets all accountOIDs to be trimmed
    /** This function does not remove the files themselves, it only changes the database values. **/
    override suspend fun setOtherUsersInListToTrimmed(accountOIDs: List<String>) {
        otherUsersDatabaseDao.setOtherUsersInListToTrimmed(accountOIDs)
    }

    //returns a list of member info for updates to use in requesting updated info from the server, excludes current member
    override suspend fun getMemberInfoForChatRoomUpdates(chatRoomId: String): MutableList<UpdateOtherUserMessages.OtherUserInfoForUpdates> {

        val extractedUserInfoFromDatabaseList =
            otherUsersDatabaseDao.getMemberInfoForUpdatesInfoForChatRoom(chatRoomId)

        val returnList = mutableListOf<UpdateOtherUserMessages.OtherUserInfoForUpdates>()

        for (element in extractedUserInfoFromDatabaseList) {
            convertDatabaseInfoToChatRoomMemberInfoForUpdates(chatRoomId, element)?.let {
                returnList.add(it)
            }
        }

        return returnList
    }

    //returns a single of member info for updates to use in requesting updated info from the server, excludes current member
    //will return null if user does not exist OR if user has been updated recently
    /** Only meant for use with updateSingleChatRoomMemberInfo() from ApplicationRepository. **/
    override suspend fun getSingleMemberInfoForChatRoomUpdates(
        chatRoomId: String,
        accountOID: String
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates? {

        otherUsersDatabaseDao.getSingleMemberInfoForUpdatesInfoForChatRoom(accountOID)?.let {
            if (getCurrentTimestampInMillis() - it.pictures_update_attempted_timestamp < GlobalValues.server_imported_values.timeBetweenUpdatingSingleUserFunctionRunning) {
                return null
            }

            return convertDatabaseInfoToChatRoomMemberInfoForUpdates(chatRoomId, it)
        }

        return null
    }

    //takes the info extracted from the database and converts it to UpdateOtherUserMessages.OtherUserInfoForUpdates
    //returns null if user was not found in chat room
    override suspend fun getSingleMemberInfoForMatchUpdates(
        accountOID: String
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates? {
        otherUsersDatabaseDao.getSingleMemberInfoForUpdatesInfoForChatRoom(accountOID)?.let {
            if (getCurrentTimestampInMillis() - it.pictures_update_attempted_timestamp < GlobalValues.server_imported_values.timeBetweenUpdatingSingleUserFunctionRunning) {
                return null
            }

            //NOTE: the AccountStateInChatRoom is arbitrary, it will not be checked by UpdateSingleMatchMember()
            return convertDatabaseInfoToMemberInfoForUpdates(
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM,
                it
            )
        }

        return null
    }

    override suspend fun getOtherUserAccountStateInChatRoom(
        chatRoomId: String,
        accountOID: String,
    ): AccountState.AccountStateInChatRoom? {
        otherUsersDatabaseDao.getChatRoomsForOtherUser(accountOID)?.let {
            val chatRoomsMap = convertChatRoomObjectsStringToMap(it)
            return chatRoomsMap[chatRoomId]?.accountStateInChatRoom
        }

        return null
    }

    //takes the info extracted from the database and converts it to UpdateOtherUserMessages.OtherUserInfoForUpdates
    //returns null if user was not found in chat room
    private fun convertDatabaseInfoToChatRoomMemberInfoForUpdates(
        chatRoomId: String,
        extractedUserInfoFromDatabase: ExtractedUserInfoFromDatabase
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates? {

        val chatRoomsMap =
            convertChatRoomObjectsStringToMap(extractedUserInfoFromDatabase.chat_rooms)
        val chatRoomInfo = chatRoomsMap[chatRoomId]

        chatRoomInfo?.let {
            return convertDatabaseInfoToMemberInfoForUpdates(
                chatRoomInfo.accountStateInChatRoom,
                extractedUserInfoFromDatabase
            )
        }

        return null
    }

    //takes the info extracted from the database and converts it to UpdateOtherUserMessages.OtherUserInfoForUpdates
    //returns null if user was not found in chat room
    private fun convertDatabaseInfoToMemberInfoForUpdates(
        accountState: AccountState.AccountStateInChatRoom,
        extractedUserInfoFromDatabase: ExtractedUserInfoFromDatabase
    ): UpdateOtherUserMessages.OtherUserInfoForUpdates? {

        val timestampList =
            convertPicturesStringToList(extractedUserInfoFromDatabase.pictures).map { picInfo ->
                UpdateOtherUserMessages.PictureIndexInfo.newBuilder()
                    .setIndexNumber(picInfo.indexOfPictureForUser)
                    .setLastUpdatedTimestamp(
                        //Using isImage() here because if the picture was somehow corrupt AFTER the initial download then the
                        // client can request a new copy from the server by setting the timestamp to -1. However, it also
                        // needs to take into the account that case where the picture is corrupt ON the server. If that is
                        // the case, it needs to send back the actual timestamp. This way if a new non-corrupt picture is
                        // updated to that position then it can receive the update, but ONLY if an update is there to be
                        // found.
                        if (picInfo.picturePath.isNotBlank()
                            && (picInfo.picturePath == GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
                                || File(picInfo.picturePath).isImage())
                        ) {
                            picInfo.timestampPictureLastUpdatedOnServer
                        } else {
                            //if the picture is corrupt on client or does not exist, send back -1 for an update
                            -1L
                        }
                    )
                    .build()
            }

        val memberInfoForUpdates = UpdateOtherUserMessages.OtherUserInfoForUpdates.newBuilder()

        memberInfoForUpdates.accountOid = extractedUserInfoFromDatabase.accountOID
        memberInfoForUpdates.accountState = accountState
        memberInfoForUpdates.age = extractedUserInfoFromDatabase.age
        memberInfoForUpdates.memberInfoLastUpdatedTimestamp =
            extractedUserInfoFromDatabase.user_info_last_updated
        memberInfoForUpdates.addAllPicturesLastUpdatedTimestamps(timestampList)
        memberInfoForUpdates.thumbnailIndexNumber =
            extractedUserInfoFromDatabase.thumbnail_index_number
        memberInfoForUpdates.thumbnailTimestamp =
                extractedUserInfoFromDatabase.thumbnail_last_time_updated

        memberInfoForUpdates.thumbnailSizeInBytes =
            if (extractedUserInfoFromDatabase.thumbnail_path.isNotBlank() && extractedUserInfoFromDatabase.thumbnail_path != "~") { //if the thumbnail path exists
                val file = File(extractedUserInfoFromDatabase.thumbnail_path)
                file.length().toInt()
            } else { //if the thumbnail path exists
                0
            }

        memberInfoForUpdates.firstName = extractedUserInfoFromDatabase.name

        memberInfoForUpdates.eventStatus = LetsGoEventStatusOuterClass.LetsGoEventStatus.forNumber(extractedUserInfoFromDatabase.event_status)
        memberInfoForUpdates.eventTitle = extractedUserInfoFromDatabase.event_title

        return memberInfoForUpdates.build()
    }

    //updates pictures_update_attempted_timestamp to the passed timestamp for all other users in list
    override suspend fun updateTimestampsPicturesAttemptedUpdate(
        accountOIDs: List<String>,
        timestampAttempted: Long
    ) {
        otherUsersDatabaseDao.updateTimestampsPicturesAttemptedUpdate(
            accountOIDs,
            timestampAttempted
        )
    }

    //updates picture at passed index to corrupt
    override suspend fun updatePictureToCorrupt(
        accountOID: String,
        timestamp: Long,
        index: Int
    ) {
        val transactionWrapper = provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.OTHER_USERS
        )
        transactionWrapper.runTransaction {
            otherUsersDatabaseDao.getSingleOtherUserPictureString(accountOID)
                ?.let { picturesString ->

                    val picturesList = convertPicturesStringToList(picturesString)

                    if (index > 0 && picturesList.size > index) {
                        //Want to update the new timestamp in case an update happened before this function could be called.
                        // This can happen because updatePictureToCorrupt() is not called from inside a transaction.
                        picturesList[index] = PictureInfo(
                            GlobalValues.PICTURE_NOT_FOUND_ON_SERVER,
                            index,
                            timestamp
                        )

                        val updatedPicturesString = convertPicturesListToString(picturesList)

                        otherUsersDatabaseDao.setOtherUserPictureString(
                            accountOID,
                            updatedPicturesString
                        )
                    }
                }
        }
    }

    //updates thumbnail_path, thumbnail_last_time_updated to corrupt
    override suspend fun updateThumbnailToCorrupt(accountOID: String, timestamp: Long) {
        otherUsersDatabaseDao.setSingleOtherUserThumbnail(
            accountOID,
            GlobalValues.PICTURE_NOT_FOUND_ON_SERVER,
            timestamp
        )
    }

    //updates user_info_last_observed to the passed timestamp for other user
    override suspend fun updateTimestampUserInfoLastObserved(accountOID: String, timestamp: Long) {
        otherUsersDatabaseDao.updateTimestampUserInfoLastObserved(accountOID, timestamp)
    }

    //sets another user to admin in the passed chat room
    override suspend fun updateUserAccountState(
        accountOID: String,
        chatRoomId: String,
        newAccountState: AccountState.AccountStateInChatRoom,
        transactionWrapper: TransactionWrapper
    ) {
        transactionWrapper.runTransaction {
            val chatRooms = convertChatRoomObjectsStringToMap(
                otherUsersDatabaseDao.getUserChatRooms(accountOID) ?: ""
            )

            val chatRoom = chatRooms[chatRoomId]

            chatRoom?.let {
                it.accountStateInChatRoom = newAccountState

                otherUsersDatabaseDao.setUserChatRooms(
                    accountOID,
                    convertChatRoomObjectsMapToString(chatRooms)
                )

            }
        }
    }

    override suspend fun updateUserAccountStateAndActiveTime(
        accountOID: String,
        chatRoomId: String,
        newAccountState: AccountState.AccountStateInChatRoom,
        timeStamp: Long,
        transactionWrapper: TransactionWrapper
    ) {
        transactionWrapper.runTransaction {
            val chatRooms = convertChatRoomObjectsStringToMap(
                otherUsersDatabaseDao.getUserChatRooms(accountOID) ?: ""
            )

            val chatRoom = chatRooms[chatRoomId]

            chatRoom?.let {
                it.accountStateInChatRoom = newAccountState

                if (it.lastActiveTimeInChatRoom < timeStamp) { //if the last active time requires updating
                    it.lastActiveTimeInChatRoom = timeStamp
                }

                otherUsersDatabaseDao.setUserChatRooms(
                    accountOID,
                    convertChatRoomObjectsMapToString(chatRooms)
                )
            }
        }
    }

    override suspend fun setUserLastActiveTimeInChatRoom(
        accountOID: String,
        chatRoomId: String,
        timeStamp: Long,
        transactionWrapper: TransactionWrapper
    ) {
        transactionWrapper.runTransaction {
            val chatRooms = convertChatRoomObjectsStringToMap(
                otherUsersDatabaseDao.getUserChatRooms(accountOID) ?: ""
            )

            val chatRoom = chatRooms[chatRoomId]

            chatRoom?.let {
                if (it.lastActiveTimeInChatRoom < timeStamp) { //if the last active time requires updating
                    it.lastActiveTimeInChatRoom = timeStamp
                    otherUsersDatabaseDao.setUserChatRooms(
                        accountOID,
                        convertChatRoomObjectsMapToString(chatRooms)
                    )
                }
            }
        }
    }

    //return the user info needed for a notification message
    override suspend fun getUserInfoForNotificationMessage(accountOID: String): ReturnForNotificationMessage {
        return otherUsersDatabaseDao.getUserInfoForNotificationMessage(accountOID)
            ?: ReturnForNotificationMessage(
                "",
                "",
                -1,
                "User"
            )
    }

    //return user info needed for multiple users for notification messages
    override suspend fun getUserInfoForNotificationMessage(accountOIDs: List<String>): List<ReturnForNotificationMessage> {
        if (accountOIDs.isEmpty())
            return emptyList()

        return otherUsersDatabaseDao.getUserInfoForNotificationMessage(accountOIDs)
    }

    override suspend fun getUserNameForNotificationMessage(userAccountOID: String): String? {
        return otherUsersDatabaseDao.getUserNameForNotificationMessage(userAccountOID)
    }

    //returns a list of chat room Ids that match the passed criteria
    override suspend fun searchForChatRoomMatches(matchingString: String): Set<String> {
        val chatRoomObjectsStrings = otherUsersDatabaseDao.searchForChatRoomMatches(matchingString)
        val chatRoomIdsSet = mutableSetOf<String>()
        for (chatRoomObjects in chatRoomObjectsStrings) {
            val map = convertChatRoomObjectsStringToMap(chatRoomObjects)

            for (mapElement in map) {
                if (mapElement.value.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                    || mapElement.value.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                ) {
                    chatRoomIdsSet.add(mapElement.value.chatRoomId)
                }
            }
        }

        return chatRoomIdsSet
    }

    //get up to the first 2 user names in the chat room
    override suspend fun getFirstTwoUserNamesInChatRoom(chatRoomId: String): List<String> {
        val inChatRoom =
            chatRoomId + OTHER_USER_CHAT_ROOM_INFO_DELIMITER + AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM.number
        val chatRoomAdmin =
            chatRoomId + OTHER_USER_CHAT_ROOM_INFO_DELIMITER + AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN.number

        return otherUsersDatabaseDao.getFirstTwoUserNamesInChatRoom(inChatRoom, chatRoomAdmin)
    }
}