package site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MimeTypeDatabaseDao {

    //insert single account
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMimeType(mimeTypeDataEntity: MimeTypeDataEntity)

    //gets the data entity for the stored mime type, null if does not exist
    @Query("SELECT * FROM message_mime_type_table WHERE mimeTypeUrl = :mimeTypeUrl LIMIT 1")
    suspend fun getMimeType(mimeTypeUrl: String): MimeTypeDataEntity?

    //delete all
    @Query("DELETE FROM message_mime_type_table")
    suspend fun clearTable()

    //remove single mime type
    @Query("DELETE FROM message_mime_type_table WHERE mimeTypeUrl = :mimeTypeUrl")
    suspend fun removeSingleMimeTypeByURL(mimeTypeUrl: String)

    //set file path
    @Query(
        """
        UPDATE message_mime_type_table SET
            mime_type_file_path = :filePath,
            mime_type_width = :mimeTypeWidth,
            mime_type_height = :mimeTypeHeight,
            mime_type_timestamp = :timestamp,
            time_user_last_observed_mime_type = :timestamp
        WHERE mimeTypeUrl = :mimeTypeUrl
    """
    )
    suspend fun updateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        timestamp: Long,
    )

    //increment reference count and set file path
    @Query(
        """
        UPDATE message_mime_type_table SET
            number_references = number_references + 1,
            mime_type_file_path = :filePath,
            mime_type_width = :mimeTypeWidth,
            mime_type_height = :mimeTypeHeight,
            mime_type_timestamp = :timestamp
        WHERE mimeTypeUrl = :mimeTypeUrl
    """
    )
    suspend fun incrementReferenceCountUpdateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        timestamp: Long,
    )

    //increment reference count
    @Query("UPDATE message_mime_type_table SET number_references = number_references + 1 WHERE mimeTypeUrl = :mimeTypeUrl")
    suspend fun incrementReferenceCountNoFilePath(mimeTypeUrl: String)

    //retrieve all mime type data entities with the passed urls
    @Query("SELECT mimeTypeUrl, mime_type_file_path, mime_type_width, mime_type_height, mime_type_value FROM message_mime_type_table")
    suspend fun retrieveMimeType(): List<MimeTypesUrlsAndFilePaths>

    //retrieve all mime type file paths
    @Query(
        """SELECT mimeTypeUrl, mime_type_file_path, time_user_last_observed_mime_type
            FROM message_mime_type_table 
            WHERE mime_type_file_path != :emptyString
    """
    )
    suspend fun retrieveAllFilePaths(emptyString: String = ""): List<MimeTypesFilePathsAndObservedTime>

    //decrement reference count
    //WARNING: this can go below 0, the reference count must be checked elsewhere and handled before this is called, this is implemented this way
    // because a file must also be deleted if the reference count == 0
    @Query("UPDATE message_mime_type_table SET number_references = number_references - 1 WHERE mimeTypeUrl = :mimeTypeUrl")
    suspend fun decrementReferenceCount(mimeTypeUrl: String)

    //updates time_user_last_observed_mime_type to the passed timestamp for all mime types in list
    @Query(
        """
        UPDATE message_mime_type_table 
        SET time_user_last_observed_mime_type = CASE WHEN time_user_last_observed_mime_type < :timestampObserved THEN :timestampObserved ELSE time_user_last_observed_mime_type END
        WHERE mimeTypeUrl IN(:mimeTypeURLs)
        """
    )
    suspend fun updateTimestampsForPassedMimeTypeURLs(
        mimeTypeURLs: Set<String>,
        timestampObserved: Long,
    )

    //return a list of all mime types that and can be trimmed and have not been observed recently
    @Query(
        """
        SELECT mimeTypeUrl, mime_type_file_path, time_user_last_observed_mime_type
        FROM message_mime_type_table
        WHERE
            time_user_last_observed_mime_type < :timestampObservedBefore
            AND
            mime_type_file_path != :defaultFilePath
        ORDER BY time_user_last_observed_mime_type ASC
    """
    )
    suspend fun getMimeTypesNotObservedRecentlyThatCanBeTrimmed(
        timestampObservedBefore: Long,
        defaultFilePath: String = ""
    ): List<MimeTypesFilePathsAndObservedTime>

    //return a list of all mime types that can be trimmed
    @Query(
        """
        SELECT mimeTypeUrl, mime_type_file_path, time_user_last_observed_mime_type
        FROM message_mime_type_table
        WHERE
            mimeTypeUrl NOT IN(:notMimeTypeUrls)
            AND
            mime_type_file_path != :defaultFilePath
        ORDER BY time_user_last_observed_mime_type ASC
    """
    )
    suspend fun getMimeTypesThatCanBeTrimmed(
        notMimeTypeUrls: List<String>,
        defaultFilePath: String = ""
    ): List<MimeTypesFilePathsAndObservedTime>


    //sets all urls to be trimmed
    @Query(
        """
        UPDATE message_mime_type_table 
            SET mime_type_file_path = :defaultURLString,
                time_user_last_observed_mime_type = :defaultTimestamp
            WHERE  mimeTypeUrl IN(:mimeTypeUrls)
    """
    )
    suspend fun setMimeTypesInListToTrimmed(
        mimeTypeUrls: List<String>,
        defaultURLString: String = "",
        defaultTimestamp: Long = -1,
    )

}

data class MimeTypesFilePathsAndObservedTime(
    val mimeTypeUrl: String,
    val mime_type_file_path: String,
    val time_user_last_observed_mime_type: Long,
)

data class MimeTypesUrlsAndFilePaths(
    val mimeTypeUrl: String = "",
    val mime_type_file_path: String = "",
    val mime_type_width: Int = 0,
    val mime_type_height: Int = 0,
    val mime_type_value: String = "",
)