package site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OtherUsersDatabaseDao {

    //insert single user
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleOtherUser(otherUsersDataEntity: OtherUsersDataEntity)

    //insert multiple users
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMultipleOtherUser(otherUsersDataEntity: List<OtherUsersDataEntity>)

    //return 1 if other user accountOID exists in table
    @Query("SELECT 1 FROM other_users_table WHERE accountOID = :accountOID LIMIT 1")
    suspend fun otherUserExists(accountOID: String): Int?

    //delete all
    @Query("DELETE FROM other_users_table")
    suspend fun clearTable()

    //delete a specific match from the table by index
    @Query("DELETE FROM other_users_table WHERE accountOID = :accountOID")
    suspend fun deleteOtherUser(accountOID: String)

    //return a single other user
    @Query("SELECT * FROM other_users_table WHERE accountOID = :accountOID")
    suspend fun getSingleOtherUser(accountOID: String): OtherUsersDataEntity?

    //returns a single other user pictures string
    @Query("""SELECT pictures FROM other_users_table WHERE accountOID = :accountOID""")
    suspend fun getSingleOtherUserPictureString(
        accountOID: String
    ): String?

    //return all users inside list
    @Query("SELECT * FROM other_users_table WHERE accountOID IN(:accountOIDs)")
    suspend fun getOtherUsersInList(accountOIDs: List<String> = listOf()): List<OtherUsersDataEntity>

    //return all users that can have info trimmed from them
    @Query(
        """
        SELECT accountOID, pictures, user_info_last_observed
            FROM other_users_table
            WHERE 
                accountOID IN(:accountOIDs)
                AND
                (pictures != :defaultPicturesString
                OR
                pictures_update_attempted_timestamp != :defaultTimestamp
                OR
                user_info_last_observed != :defaultTimestamp)
    """
    )
    suspend fun getOtherUsersInListThatCanBeTrimmed(
        accountOIDs: List<String>,
        defaultPicturesString: String = "",
        defaultTimestamp: Long = -1,
    ): List<OtherUserFieldsForTrimming>

    //return a list of all other users that have not been observed recently and can be trimmed
    @Query(
        """
        SELECT accountOID, pictures, user_info_last_observed
        FROM other_users_table
        WHERE
            user_info_last_observed < :timestampObservedBefore
            AND
            pictures != :defaultPicturesString
    """
    )
    suspend fun getUsersNotObservedRecentlyThatCanBeTrimmed(
        timestampObservedBefore: Long,
        defaultPicturesString: String = "",
    ): List<OtherUserFieldsForTrimming>

    //return a list of all other users that and can be trimmed and are not one of the account OIDs sent
    @Query(
        """
        SELECT accountOID, pictures, user_info_last_observed
        FROM other_users_table
        WHERE
            accountOID NOT IN(:notAccountOIDs)
            AND 
            pictures != :defaultPicturesString
        ORDER BY user_info_last_observed ASC
    """
    )
    suspend fun getUsersThatCanBeTrimmed(
        notAccountOIDs: List<String>,
        defaultPicturesString: String = "",
    ): List<OtherUserFieldsForTrimming>

    //return a list of all other users file paths
    @Query(
        """
        SELECT accountOID, pictures, thumbnail_path
        FROM other_users_table
        WHERE
            pictures != :emptyString
            OR
            thumbnail_path != :emptyString
        ORDER BY user_info_last_observed ASC
    """
    )
    suspend fun getOtherUsersFilePaths(
        emptyString: String = ""
    ): List<OtherUserFilePaths>

    //sets all accountOIDs to be trimmed
    @Query(
        """
            UPDATE other_users_table 
            SET pictures = :defaultPicturesString,
                pictures_update_attempted_timestamp = :defaultTimestamp,
                user_info_last_observed = :defaultTimestamp
            WHERE  accountOID IN(:accountOIDs)
    """
    )
    suspend fun setOtherUsersInListToTrimmed(
        accountOIDs: List<String>,
        defaultPicturesString: String = "",
        defaultTimestamp: Long = -1,
    )

    //sets user picture string
    //NOTE: This DOES NOT update pictures_update_attempted_timestamp or user_info_last_observed and so should
    // only be used in very specific circumstances.
    @Query(
        """
        UPDATE other_users_table 
        SET pictures = :picturesString
        WHERE accountOID = :accountOID
    """
    )
    suspend fun setOtherUserPictureString(
        accountOID: String,
        picturesString: String,
    )

    //returns a single other user pictures string
    @Query("""UPDATE other_users_table SET thumbnail_path = :thumbnailPath, thumbnail_last_time_updated = :thumbnailTimestamp WHERE accountOID = :accountOID""")
    suspend fun setSingleOtherUserThumbnail(
        accountOID: String,
        thumbnailPath: String,
        thumbnailTimestamp: Long,
    )

    //return a list of all other users belonging to a specific chat room
    @Query("SELECT * FROM other_users_table WHERE chat_rooms LIKE '%' || :chatRoomId || '%'")
    suspend fun getAllOtherUsersForChatRoom(chatRoomId: String): List<OtherUsersDataEntity>

    //return a list of all other users belonging to a specific chat room
    @Query(
        """
        SELECT
            accountOID,
            thumbnail_path,
            thumbnail_index_number,
            thumbnail_last_time_updated,
            chat_rooms,
            user_info_last_updated,
            pictures,
            pictures_update_attempted_timestamp,
            name,
            age,
            event_status,
            event_title
        FROM other_users_table
        WHERE 
            chat_rooms
                LIKE
            '%' || :chatRoomId || '%'
    """
    )
    suspend fun getMemberInfoForUpdatesInfoForChatRoom(chatRoomId: String): List<ExtractedUserInfoFromDatabase>

    //return a list of all other users belonging to a specific chat room
    @Query(
        """
        SELECT
            accountOID,
            thumbnail_path,
            thumbnail_index_number,
            thumbnail_last_time_updated,
            chat_rooms, 
            user_info_last_updated,
            pictures,
            pictures_update_attempted_timestamp,
            name,
            age,
            event_status,
            event_title
        FROM other_users_table
        WHERE
            accountOID = :accountOID
    """
    )
    suspend fun getSingleMemberInfoForUpdatesInfoForChatRoom(accountOID: String): ExtractedUserInfoFromDatabase?

    //return chat rooms
    @Query(
        """
        SELECT chat_rooms
        FROM other_users_table
        WHERE accountOID = :accountOID
    """
    )
    suspend fun getChatRoomsForOtherUser(accountOID: String): String?

    //return the specified user's chat rooms string
    @Query("SELECT chat_rooms FROM other_users_table WHERE accountOID = :accountOID")
    suspend fun getUserChatRooms(accountOID: String): String?

    //updates pictures_update_attempted_timestamp to the passed timestamp for all other users in list
    @Query(
        """
        UPDATE other_users_table 
        SET pictures_update_attempted_timestamp = CASE WHEN pictures_update_attempted_timestamp < :timestampAttempted THEN :timestampAttempted ELSE pictures_update_attempted_timestamp END
        WHERE accountOID IN(:accountOIDs)
    """
    )
    suspend fun updateTimestampsPicturesAttemptedUpdate(
        accountOIDs: List<String>,
        timestampAttempted: Long
    )

    //updates user_info_last_observed to the passed timestamp for other user
    @Query(
        """
        UPDATE other_users_table 
        SET user_info_last_observed = CASE WHEN user_info_last_observed < :timestamp THEN :timestamp ELSE user_info_last_observed END
        WHERE accountOID = :accountOID
    """
    )
    suspend fun updateTimestampUserInfoLastObserved(accountOID: String, timestamp: Long)

    //set the specified user's chat rooms string
    @Query("UPDATE other_users_table SET chat_rooms = :chatRoomsString WHERE accountOID = :accountOID")
    suspend fun setUserChatRooms(accountOID: String, chatRoomsString: String)

    //return the user info needed for a notification message
    @Query("SELECT accountOID, thumbnail_path, thumbnail_last_time_updated, name FROM other_users_table WHERE accountOID = :accountOID LIMIT 1")
    suspend fun getUserInfoForNotificationMessage(accountOID: String): ReturnForNotificationMessage?

    //return multiple users needed for notification messages
    @Query("""
        SELECT 
            accountOID,
            thumbnail_path, 
            thumbnail_last_time_updated, 
            name 
        FROM other_users_table
        WHERE accountOID IN(:accountOIDs)
        ORDER BY accountOID ASC
    """)
    suspend fun getUserInfoForNotificationMessage(accountOIDs: List<String>): List<ReturnForNotificationMessage>

    //return the user name needed for a notification message
    @Query("SELECT name FROM other_users_table WHERE accountOID = :accountOID LIMIT 1")
    suspend fun getUserNameForNotificationMessage(accountOID: String): String?

    //returns all list of chat room strings that match the passed criteria
    //NOTE: LIKE is case insensitive which is desired in this case
    @Query(
        """
            SELECT chat_rooms
            FROM other_users_table 
            WHERE name LIKE '%' || :matchingString || '%'
        """
    )
    suspend fun searchForChatRoomMatches(matchingString: String): List<String>

    //get up to the first 2 user names in the chat room
    @Query("""
        SELECT name
        FROM other_users_table
        WHERE (chat_rooms LIKE '%' || :inChatRoom || '%' OR chat_rooms LIKE '%' || :chatRoomAdmin || '%') 
        LIMIT 2
        """)
    suspend fun getFirstTwoUserNamesInChatRoom(
        inChatRoom: String,
        chatRoomAdmin: String
    ): List<String>
}

data class ReturnForNotificationMessage(
    val accountOID: String,
    val thumbnail_path: String,
    val thumbnail_last_time_updated: Long,
    val name: String
)

data class OtherUserFilePaths(
    val accountOID: String,
    val pictures: String,
    val thumbnail_path: String,
)

data class OtherUserFieldsForTrimming(
    val accountOID: String,
    val pictures: String,
    val user_info_last_observed: Long
)

data class ExtractedUserInfoFromDatabase(
    val accountOID: String,
    val thumbnail_path: String,
    val thumbnail_index_number: Int,
    val thumbnail_last_time_updated: Long,
    val chat_rooms: String,
    val user_info_last_updated: Long,
    val pictures: String,
    val pictures_update_attempted_timestamp: Long,
    val name: String,
    val age: Int,
    val event_status: Int,
    val event_title: String,
)