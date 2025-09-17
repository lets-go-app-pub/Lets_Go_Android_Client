package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

class AccountPictureDaoIntermediate(
    private val accountPictureDatabaseDao: AccountPictureDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : AccountPictureDaoIntermediateInterface {

    override suspend fun insertSinglePicture(
        index: Int,
        picturePath: String,
        pictureSize: Int,
        pictureTimestamp: Long
    ) {
        val accountPictureDataEntity =
            AccountPictureDataEntity(index, picturePath, pictureSize, pictureTimestamp)
        accountPictureDatabaseDao.insertAccountPicture(accountPictureDataEntity)
    }

    override suspend fun removeSinglePicture(index: Int) {
        return accountPictureDatabaseDao.insertAccountPicture(AccountPictureDataEntity(index))
    }

    override suspend fun getAllPictureTimestampsAndPaths(): MutableList<PictureTimestampsAndPaths> {

        //NOTE: while there are 2 database commands here, the second one will always be true
        // it is just fixing an error, so no need for a transaction

        val newPictureArray = accountPictureDatabaseDao.getAllAccountPictureTimestampsAndPaths()

        deleteExcessPictures(newPictureArray)

        return newPictureArray
    }

    override suspend fun getAllPictures(): MutableList<AccountPictureDataEntity> {

        //NOTE: while there are 2 database commands here, the second one will always be true
        // it is just fixing an error, so no need for a transaction

        val newPictureArray = accountPictureDatabaseDao.getAllAccountPictures()

        deleteExcessPictures(newPictureArray)

        return newPictureArray
    }

    //NOTE: This command should never need to be inside a transaction, it is fixing a general error
    // and shouldn't cause any data collision.
    override suspend fun deleteExcessPictures() {
        accountPictureDatabaseDao.deleteExcessPictures()
    }

    private suspend fun <T> deleteExcessPictures(picturesList: MutableList<T>) {
        if (picturesList.size > GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
            deleteExcessPictures()
            while(picturesList.size > GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
                picturesList.removeLast()
            }
        }
    }

    override suspend fun getSinglePicture(index: Int): AccountPictureDataEntity? {
        return accountPictureDatabaseDao.getAccountPicture(index)
    }

    override suspend fun getFirstPicture(): AccountPictureDataEntity? {
        return accountPictureDatabaseDao.getFirstPicture()
    }

    override suspend fun clearTable() {

        val mutableList = mutableListOf<AccountPictureDataEntity>()

        for (i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
            mutableList.add(AccountPictureDataEntity(i))
        }

        //NOTE: the OnConflictStrategy.REPLACE actually does the command as a REMOVE followed
        // by a REPLACE instead of a single UPDATE operation, however the SEEMS to run inside
        // a transaction (based on the documentation of the enum OnConflictStrategy.REPLACE)
        // and so it should be fine
        accountPictureDatabaseDao.upsertAllAccountPictures(mutableList)

        //This is fine, see function for NOTE
        deleteExcessPictures()
    }

}