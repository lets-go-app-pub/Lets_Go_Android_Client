package site.letsgoapp.letsgo.databases.otherUsersDatabase.matches

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MatchesDatabaseDao {

    //NOTE: this database will use the variable matchIndex for the order it received matches,
    // it will work like a queue where the first match stored is the first match returned (FIFO)

    //insert single match
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(matchesDataEntity: MatchesDataEntity): Long

    //remove any matches with expired timestamps
    @Query("DELETE FROM matches_table WHERE expiration_time <= :timestamp")
    suspend fun cleanExpiredMatches(timestamp: Long) //current time timestamp in seconds

    //returns number of rows in table
    @Query("SELECT Count(matchIndex) FROM matches_table")
    suspend fun countMatchesRemaining(): Int

    //get the number of matches from the table (the match with the lowest index)
    //NOTE: 'ORDER BY matchIndex' is important here in order to return the LOWEST match index
    @Query("SELECT * FROM matches_table WHERE matchIndex NOT IN(:restrictedIndexList) ORDER BY matchIndex ASC LIMIT :numMatches")
    suspend fun getMatches(
        numMatches: Int,
        restrictedIndexList: List<Long> = listOf()
    ): List<MatchesDataEntity>

    //return a list of all matches from the table
    @Query("SELECT account_oid, expiration_time, matchIndex FROM matches_table ORDER BY account_oid ASC")
    suspend fun getAllMatches(): List<GetAllMatchesDataClass>

    @Query("SELECT account_oid, expiration_time, matchIndex FROM matches_table WHERE account_oid = :matchAccountOid")
    suspend fun getAllMatchesForAccountOID(matchAccountOid: String): List<GetAllMatchesDataClass>

    //delete a specific match from the table by index
    @Query("DELETE FROM matches_table WHERE matchIndex = :matchIndex")
    suspend fun deleteMatch(matchIndex: Long)

    //delete all
    @Query("DELETE FROM matches_table")
    suspend fun clearTable()

}

data class GetAllMatchesDataClass(
    val account_oid: String,
    val expiration_time: Long,
    val matchIndex: Long
)