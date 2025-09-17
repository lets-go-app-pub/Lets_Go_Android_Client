package site.letsgoapp.letsgo.databases.iconsDatabase.icons

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IconsDatabaseDao {

    //insert single icon
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIcon(iconsDataEntity: IconsDataEntity)

    //insert list of icons
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIcons(iconsDataEntities: List<IconsDataEntity>)

    //gets the single icon
    @Query("SELECT * FROM icon_info_table WHERE iconIndex = :iconIndex LIMIT 1")
    suspend fun getSingleIcon(iconIndex: Int): IconsDataEntity?

    //gets all icons
    @Query("SELECT * FROM icon_info_table ORDER BY iconIndex ASC")
    suspend fun getAllIcons(): List<IconsDataEntity>

    //gets all icon timestamps
    @Query("SELECT icon_timestamp FROM icon_info_table ORDER BY iconIndex ASC")
    suspend fun getAllIconTimestamps(): List<Long>

    //delete all
    @Query("DELETE FROM icon_info_table")
    suspend fun clearTable()

}