package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import site.letsgoapp.letsgo.globalAccess.GlobalValues

@Dao
interface AccountPictureDatabaseDao {

    //NOTE: this database should always have the default values stored inside it from the intermediate, it should never be empty

    //insert single account
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountPicture(accountPictureDataEntity: AccountPictureDataEntity)

    //insert multiple documents
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllAccountPictures(allPictures: List<AccountPictureDataEntity>)

    //delete a specific match from the table by index
    @Query("DELETE FROM account_pictures_table WHERE pictureIndex > :maxIndexValue")
    suspend fun deleteExcessPictures(maxIndexValue: Int = GlobalValues.server_imported_values.numberPicturesStoredPerAccount - 1)

    //I will extract it by some latest value, when the user swipes on it I will delete that element in
    //the database then send the account_id and the response to a worker

    //gets the account of the phone number
    @Query("SELECT * FROM account_pictures_table WHERE pictureIndex = :pictureIndex LIMIT 1")
    suspend fun getAccountPicture(pictureIndex: Int): AccountPictureDataEntity?

    //gets the first index that is set
    @Query(
        """
        SELECT *
        FROM account_pictures_table
        WHERE picture_size > :defaultZero
        ORDER BY pictureIndex
        LIMIT 1
    """
    )
    suspend fun getFirstPicture(defaultZero: Int = 0): AccountPictureDataEntity?

    @Query("SELECT picture_timestamp, picture_path FROM account_pictures_table ORDER BY pictureIndex ASC")
    suspend fun getAllAccountPictureTimestampsAndPaths(): MutableList<PictureTimestampsAndPaths>

    @Query("SELECT * FROM account_pictures_table ORDER BY pictureIndex ASC")
    suspend fun getAllAccountPictures(): MutableList<AccountPictureDataEntity>

    //delete all
    @Query("DELETE FROM account_pictures_table")
    suspend fun clearTable()

    //NOTE: don't really need individual getters and setters for each field just use insert to overwrite entire object
}

data class PictureTimestampsAndPaths(
    val picture_timestamp: Long,
    val picture_path: String,
)
