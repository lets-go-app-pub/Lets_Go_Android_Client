package site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatRoomsDatabaseDao {

    //insert single match
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatRoom(chatRoomsDataEntity: ChatRoomsDataEntity)

    //return a specified chat room from the table
    @Query("SELECT 1 FROM chat_rooms_table WHERE chatRoomID = :chatRoomID LIMIT 1")
    suspend fun chatRoomExists(chatRoomID: String): Int?

    //returns each chat rooms last observed time
    @Query("SELECT chatRoomID, last_observed_time FROM chat_rooms_table ORDER BY chatRoomID")
    suspend fun getAllChatRoomLastObservedTimes(): List<ChatRoomObservedTimeInfo>

    //return a specified chat room from the table
    @Query("SELECT * FROM chat_rooms_table WHERE chatRoomID = :chatRoomID LIMIT 1")
    suspend fun getSingleChatRoom(chatRoomID: String): ChatRoomsDataEntity?

    //returns chat room id if a matching chat room exists for the accountOID
    //returns null if matching chat room does NOT exist
    @Query(
        """
            SELECT chatRoomID
            FROM chat_rooms_table 
            WHERE matching_chat_room_oid = :accountOID
            LIMIT 1
        """
    )
    suspend fun matchingChatRoomExists(accountOID: String): String?

    //gets the last time observed from the select chat room
    @Query("SELECT last_observed_time FROM chat_rooms_table WHERE chatRoomID = :chatRoomId LIMIT 1")
    suspend fun getSingleChatRoomLastTimeObserved(chatRoomId: String): Long?

    //gets the last time observed from the select chat room
    @Query("SELECT chat_room_last_active_time FROM chat_rooms_table WHERE chatRoomID = :chatRoomId LIMIT 1")
    suspend fun getSingleChatRoomLastActiveTime(chatRoomId: String): Long?

    //sets the last updated time for a specified chat room
    @Query("""
        UPDATE chat_rooms_table SET
            last_observed_time = :newLastTimeUpdated,
            last_time_updated = :newLastTimeUpdated
        WHERE chatRoomID = :chatRoomID
    """)
    suspend fun setSingleChatRoomLastTimeUpdatedLastTimeObserved(
        chatRoomID: String,
        newLastTimeUpdated: Long
    )

    //return a specified chat room from the table by the matching account OID
    @Query("SELECT * FROM chat_rooms_table WHERE matching_chat_room_oid = :matchingChatRoomOID LIMIT 1")
    suspend fun getSingleChatRoomByMatchingOID(matchingChatRoomOID: String): ChatRoomsDataEntity?

    //return a list of all matches from the table
    @Query("SELECT * FROM chat_rooms_table ORDER BY time_joined ASC")
    suspend fun getAllChatRooms(): List<ChatRoomsDataEntity>

    //return a list of requested chat room ids and their joined time
    @Query("SELECT chatRoomID, time_joined FROM chat_rooms_table WHERE chatRoomID in (:chatRoomIds)")
    suspend fun getChatRoomIdsJoinedTime(chatRoomIds: List<String>): List<ChatRoomIdAndTimeJoined>

    //return a list of all chat room ids and their last time updated
    @Query("SELECT chatRoomID, last_time_updated, last_observed_time FROM chat_rooms_table")
    suspend fun getAllChatRoomIdsTimeLastUpdatedAndLastObservedTime(): List<ChatRoomIdAndTimeLastUpdated>

    //delete a specific match from the table by index
    @Query("DELETE FROM chat_rooms_table WHERE chatRoomID = :chatRoomID")
    suspend fun deleteChatRoom(chatRoomID: String)

    //delete all
    @Query("DELETE FROM chat_rooms_table")
    suspend fun clearTable()

    //gets messageIDs returns null if not found
    @Query("SELECT user_state_in_chat_room FROM chat_rooms_table WHERE chatRoomID = :chatRoomID LIMIT 1")
    suspend fun getAccountState(chatRoomID: String): Int?

    //NOTE: this block of Queries will update different variations of userLastActivityTime, chatRoomLastActivityTime, lastTimeUpdated and matchingChatRoomOID
    //sets lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomLastObservedTime (if greater than stored value), userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            last_observed_time = CASE WHEN last_observed_time < :timestampStored THEN :timestampStored ELSE last_observed_time END,
            user_last_active_time = CASE WHEN user_last_active_time < :timestampStored THEN :timestampStored ELSE user_last_active_time END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
        """
    )
    suspend fun updateChatRoomObservedTimeUserLastActiveTimeMatchingOid(
        chatRoomID: String,
        timestampStored: Long,
        emptyString: String = "",
    )

    //sets chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateUserLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomLastObservedTime (if greater than stored value), userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            last_observed_time = CASE WHEN last_observed_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_observed_time END,
            user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomObservedTimeUserLastActiveTimeChatRoomLastActiveTimeMatchingOid(
        chatRoomID: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomLastObservedTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value) and lastTimeUpdated (if greater than stored value)
    @Query(
        """
        UPDATE chat_rooms_table SET
            last_observed_time = CASE WHEN last_observed_time < :timestampStored THEN :timestampStored ELSE last_observed_time END,
            user_last_active_time = CASE WHEN user_last_active_time < :timestampStored THEN :timestampStored ELSE user_last_active_time END
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomObservedTimeUserLastActiveTime(
        chatRoomID: String,
        timestampStored: Long,
    )

    //sets chatRoomName (if not empty), chatRoomPassword (if not empty) and chatRoomLastActivityTime (if greater than stored value)
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_name = CASE WHEN :chatRoomName = :emptyString THEN chat_room_name ELSE :chatRoomName END,
            chat_room_password = CASE WHEN :chatRoomPassword = :emptyString THEN chat_room_password ELSE :chatRoomPassword END,
            event_id = CASE WHEN :eventOid = :emptyString THEN event_id ELSE :emptyString END,
            pinned_location_latitude = :pinnedLocationLatitude,
            pinned_location_longitude = :pinnedLocationLongitude,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :chatRoomLastActiveTime THEN :chatRoomLastActiveTime ELSE chat_room_last_active_time END
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomNameChatRoomPasswordChatRoomLastActiveTime(
        chatRoomID: String,
        chatRoomName: String,
        chatRoomPassword: String,
        eventOid: String,
        pinnedLocationLatitude: Double,
        pinnedLocationLongitude: Double,
        chatRoomLastActiveTime: Long,
        emptyString: String = "",
    )

    @Query(
        """
        UPDATE chat_rooms_table SET
            qr_code_path = :qrCodePath,
            qr_code_message = :qrCodeMessage,
            qr_code_time_updated = :qrCodeTimeUpdated
        WHERE chatRoomID = :chatRoomId
    """
    )
    suspend fun setQrCodeValues(
        chatRoomId: String,
        qrCodePath: String,
        qrCodeMessage: String,
        qrCodeTimeUpdated: Long,
    );

    //sets lastTimeUpdated (if greater than stored value)
    @Query(
        """
        UPDATE chat_rooms_table SET
            last_time_updated = CASE WHEN last_time_updated < :timeLastUpdated THEN :timeLastUpdated ELSE last_time_updated END
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateTimeLastUpdated(
        chatRoomID: String,
        timeLastUpdated: Long,
    )

    //sets userLastActivityTime (if greater than stored value)
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_last_active_time = CASE WHEN user_last_active_time < :timestamp THEN :timestamp ELSE user_last_active_time END
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateUserLastActiveTime(chatRoomID: String, timestamp: Long)

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_state_in_chat_room = :accountState, user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateAccountStateUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomID: String,
        accountState: Int,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets userStateInChatRoom, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_state_in_chat_room = :accountState, chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateAccountStateChatRoomLastActiveTimeLastTimeUpdateMatchingOid(
        chatRoomID: String,
        accountState: Int,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets userStateInChatRoom, userLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value)
    @Query(
        """
        UPDATE chat_rooms_table SET
            user_state_in_chat_room = :accountState, user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateAccountStateUserLastActiveTimeLastTimeUpdate(
        chatRoomID: String,
        accountState: Int,
        lastTimeUpdated: Long,
    )

    //return account state of chat room
    @Query(
        """
        SELECT 
           user_state_in_chat_room,
           chat_room_name,
           chat_room_password,
           chat_room_last_active_time,
           last_time_updated,
           pinned_location_longitude,
           pinned_location_latitude,
           qr_code_time_updated,
           event_id
        FROM chat_rooms_table 
        WHERE chatRoomID = :chatRoomId 
        LIMIT 1
    """
    )
    suspend fun getUpdateChatRoomInfo(chatRoomId: String): UpdateChatRoomInfo?

    @Query(
        """
        SELECT qr_code_path
        FROM chat_rooms_table 
        WHERE chatRoomID = :chatRoomId 
        LIMIT 1
    """
    )
    suspend fun getQRCodePath(chatRoomId: String): String?

    @Query(
        """
        SELECT event_id
        FROM chat_rooms_table 
        WHERE chatRoomID = :chatRoomId 
        LIMIT 1
    """
    )
    suspend fun getEventId(chatRoomId: String): String?

    //set chatRoomLastObservedTime
    @Query("UPDATE chat_rooms_table SET last_observed_time = CASE WHEN last_observed_time < :lastObservedTime THEN :lastObservedTime ELSE last_observed_time END WHERE chatRoomID = :chatRoomId")
    suspend fun updateUserLastObservedTime(chatRoomId: String, lastObservedTime: Long)

    //sets accountState
    @Query("UPDATE chat_rooms_table SET user_state_in_chat_room = :accountState WHERE chatRoomID = :chatRoomId")
    suspend fun updateAccountState(chatRoomId: String, accountState: Int)

    @Query("UPDATE chat_rooms_table SET event_id = :eventOid WHERE chatRoomID = :chatRoomId")
    suspend fun updateEventOid(chatRoomId: String, eventOid: String)

    //sets chatRoomName, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_name = :chatRoomName, user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomNameUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        chatRoomName: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomName, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_name = :chatRoomName, chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomNameChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        chatRoomName: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomPassword, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_password = :chatRoomPassword, user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomPasswordUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        chatRoomPassword: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomPassword, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            chat_room_password = :chatRoomPassword, chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomPasswordChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        chatRoomPassword: String,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomPassword, userLastActivityTime (if greater than stored value), chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            pinned_location_longitude = :longitude,
            pinned_location_latitude = :latitude,
            user_last_active_time = CASE WHEN user_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE user_last_active_time END,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomPinnedLocationUserLastActiveTimeChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        longitude: Double,
        latitude: Double,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //sets chatRoomPassword, chatRoomLastActivityTime (if greater than stored value), lastTimeUpdated (if greater than stored value) and matchingChatRoomOID
    @Query(
        """
        UPDATE chat_rooms_table SET
            pinned_location_longitude = :longitude,
            pinned_location_latitude = :latitude,
            chat_room_last_active_time = CASE WHEN chat_room_last_active_time < :lastTimeUpdated THEN :lastTimeUpdated ELSE chat_room_last_active_time END,
            last_time_updated = CASE WHEN last_time_updated < :lastTimeUpdated THEN :lastTimeUpdated ELSE last_time_updated END,
            matching_chat_room_oid = :emptyString
        WHERE chatRoomID = :chatRoomID
    """
    )
    suspend fun updateChatRoomPinnedLocationChatRoomLastActiveTimeLastTimeUpdatedMatchingOid(
        chatRoomID: String,
        longitude: Double,
        latitude: Double,
        lastTimeUpdated: Long,
        emptyString: String = "",
    )

    //set if notifications are enabled for the passed chat room
    @Query("UPDATE chat_rooms_table SET notifications_enable = :notificationsEnabled WHERE chatRoomID = :chatRoomID")
    suspend fun setNotificationsEnabled(chatRoomID: String, notificationsEnabled: Boolean)

    //return info required by the notification for the passed chat room
    @Query("SELECT chatRoomID, chat_room_name, notifications_enable FROM chat_rooms_table WHERE chatRoomID = :chatRoomID LIMIT 1")
    suspend fun getNotificationsChatRoomInfo(chatRoomID: String): NotificationsEnabledChatRoomInfo?

    //returns a list of chat room Ids that match the passed criteria
    //NOTE: LIKE is case insensitive which is desired in this case
    @Query(
        """
            SELECT chatRoomID
            FROM chat_rooms_table 
            WHERE 
                chatRoomID LIKE '%' || :matchingString || '%'
                OR
                chat_room_name LIKE '%' || :matchingString || '%'
                OR
                chat_room_password LIKE '%' || :matchingString || '%'
        """
    )
    suspend fun searchForChatRoomMatches(matchingString: String): List<String>

    //returns 0 if a chat room has a message that has NOT been observed
    //returns null if no chat rooms have any such messages
    @Query(
        """
            SELECT 0
            FROM chat_rooms_table 
            WHERE last_observed_time < chat_room_last_active_time
            LIMIT 1
        """
    )
    suspend fun allChatRoomMessagesHaveBeenObserved(): Int?

    @Query("SELECT chatRoomID, qr_code_path FROM chat_rooms_table ORDER BY chatRoomID")
    suspend fun getAllChatRoomFiles(): List<ChatRoomFileInfo>
}

data class NotificationsEnabledChatRoomInfo(
    val chatRoomID: String,
    val chat_room_name: String?,
    val notifications_enable: Boolean?,
)

data class UpdateChatRoomInfo(
    val user_state_in_chat_room: Int,
    val chat_room_name: String,
    val chat_room_password: String,
    val chat_room_last_active_time: Long,
    val last_time_updated: Long,
    val pinned_location_longitude: Double,
    val pinned_location_latitude: Double,
    val qr_code_time_updated: Long,
    val event_id: String,
)

data class ChatRoomObservedTimeInfo(
    val chatRoomID: String,
    val last_observed_time: Long,
)

data class ChatRoomFileInfo(
    val chatRoomID: String,
    val qr_code_path: String,
)
