package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture

interface AccountPictureDaoIntermediateInterface {

    suspend fun insertSinglePicture(
        index: Int,
        picturePath: String,
        pictureSize: Int,
        pictureTimestamp: Long
    )

    suspend fun removeSinglePicture(index: Int)

    suspend fun getAllPictureTimestampsAndPaths(): MutableList<PictureTimestampsAndPaths>

    suspend fun getAllPictures(): MutableList<AccountPictureDataEntity>

    //NOTE: this command should never need to be inside a transaction, it is fixing a general error
    // and shouldn't cause any data collision
    suspend fun deleteExcessPictures()

    suspend fun getSinglePicture(index: Int): AccountPictureDataEntity?

    suspend fun getFirstPicture(): AccountPictureDataEntity?

    suspend fun clearTable()

}