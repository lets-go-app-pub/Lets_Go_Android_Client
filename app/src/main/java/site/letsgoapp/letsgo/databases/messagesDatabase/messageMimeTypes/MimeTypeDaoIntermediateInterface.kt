package site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes


interface MimeTypeDaoIntermediateInterface {

    //insert single account
    suspend fun insertMimeType(mimeTypeDataEntity: MimeTypeDataEntity)

    //gets the data entity for the stored mime type, null if does not exist
    suspend fun getMimeType(mimeTypeUrl: String): MimeTypeDataEntity?

    //delete all
    suspend fun clearTable()

    //remove single mime type
    suspend fun removeSingleMimeTypeByURL(mimeTypeUrl: String)

    //set file path
    suspend fun updateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int
    )

    //increment reference count and set file path
    suspend fun incrementReferenceCountUpdateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int
    )

    //increment reference count
    suspend fun incrementReferenceCountNoFilePath(mimeTypeUrl: String)

    //retrieve all mime type data entities with the passed urls
    suspend fun retrieveMimeTypes(): List<MimeTypesUrlsAndFilePaths>

    //retrieve all mime type file paths
    suspend fun retrieveAllFilePaths(): List<MimeTypesFilePathsAndObservedTime>

    //decrement reference count
    //WARNING: this can go below 0, the reference count must be checked elsewhere and handled before this is called, this is implemented this way
    // because a file must also be deleted if the reference count == 0
    suspend fun decrementReferenceCount(mimeTypeUrl: String)

    //updates observed times to the passed timestamp for all mime types in list
    suspend fun updateMimeTypesObservedTimes(mimeTypeURLs: Set<String>, timestampObserved: Long)

    //return a list of all mime types that have not been observed recently
    suspend fun getMimeTypesNotObservedRecentlyThatCanBeTrimmed(): List<MimeTypesFilePathsAndObservedTime>

    //returns a list of all mime types that can be trimmed
    suspend fun getMimeTypesThatCanBeTrimmed(notMimeTypeUrls: List<String>): List<MimeTypesFilePathsAndObservedTime>

    //sets all mime types in list to trimmed
    suspend fun setMimeTypesInListToTrimmed(mimeTypeUrls: List<String>)

}