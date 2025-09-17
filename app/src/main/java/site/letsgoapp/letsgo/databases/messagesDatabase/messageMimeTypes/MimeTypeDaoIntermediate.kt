package site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.getCurrentTimestampInMillis

class MimeTypeDaoIntermediate(
    private val mimeTypeDatabaseDao: MimeTypeDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : MimeTypeDaoIntermediateInterface {

    override suspend fun insertMimeType(mimeTypeDataEntity: MimeTypeDataEntity) {
        mimeTypeDatabaseDao.insertMimeType(mimeTypeDataEntity)
    }

    override suspend fun getMimeType(mimeTypeUrl: String): MimeTypeDataEntity? {
        return mimeTypeDatabaseDao.getMimeType(mimeTypeUrl)
    }

    override suspend fun clearTable() {
        mimeTypeDatabaseDao.clearTable()
    }

    override suspend fun removeSingleMimeTypeByURL(mimeTypeUrl: String) {
        mimeTypeDatabaseDao.removeSingleMimeTypeByURL(mimeTypeUrl)
    }

    override suspend fun updateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int
    ) {
        mimeTypeDatabaseDao.updateFilePath(
            mimeTypeUrl,
            filePath,
            mimeTypeWidth,
            mimeTypeHeight,
            getCurrentTimestampInMillis()
        )
    }

    override suspend fun incrementReferenceCountUpdateFilePath(
        mimeTypeUrl: String,
        filePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int
    ) {
        mimeTypeDatabaseDao.incrementReferenceCountUpdateFilePath(
            mimeTypeUrl,
            filePath,
            mimeTypeWidth,
            mimeTypeHeight,
            getCurrentTimestampInMillis()
        )
    }

    override suspend fun incrementReferenceCountNoFilePath(mimeTypeUrl: String) {
        mimeTypeDatabaseDao.incrementReferenceCountNoFilePath(mimeTypeUrl)
    }

    override suspend fun retrieveMimeTypes(): List<MimeTypesUrlsAndFilePaths> {
        return mimeTypeDatabaseDao.retrieveMimeType()
    }

    //retrieve all file paths
    override suspend fun retrieveAllFilePaths(): List<MimeTypesFilePathsAndObservedTime> {
        return mimeTypeDatabaseDao.retrieveAllFilePaths()
    }

    override suspend fun decrementReferenceCount(mimeTypeUrl: String) {
        mimeTypeDatabaseDao.decrementReferenceCount(mimeTypeUrl)
    }

    override suspend fun updateMimeTypesObservedTimes(
        mimeTypeURLs: Set<String>,
        timestampObserved: Long,
    ) {
        mimeTypeDatabaseDao.updateTimestampsForPassedMimeTypeURLs(mimeTypeURLs, timestampObserved)
    }

    override suspend fun getMimeTypesNotObservedRecentlyThatCanBeTrimmed(): List<MimeTypesFilePathsAndObservedTime> {
        val earliestTimestamp =
            getCurrentTimestampInMillis() - GlobalValues.server_imported_values.timeInfoHasNotBeenObservedBeforeCleaned
        return mimeTypeDatabaseDao.getMimeTypesNotObservedRecentlyThatCanBeTrimmed(earliestTimestamp)
    }

    override suspend fun getMimeTypesThatCanBeTrimmed(notMimeTypeUrls: List<String>): List<MimeTypesFilePathsAndObservedTime> {
        return mimeTypeDatabaseDao.getMimeTypesThatCanBeTrimmed(notMimeTypeUrls)
    }

    override suspend fun setMimeTypesInListToTrimmed(mimeTypeUrls: List<String>) {
        mimeTypeDatabaseDao.setMimeTypesInListToTrimmed(mimeTypeUrls)
    }
}