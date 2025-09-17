package site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UnsentSimpleServerCommandsDatabaseDao {

    //insert single message
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(unsentSimpleServerCommandsDataEntity: UnsentSimpleServerCommandsDataEntity)

    //select all
    @Query("SELECT * FROM unsent_user_match_options")
    suspend fun selectAll(): List<UnsentSimpleServerCommandsDataEntity>

    //delete all
    @Query("DELETE FROM unsent_user_match_options")
    suspend fun clearTable()
}