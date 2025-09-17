package site.letsgoapp.letsgo.databases.otherUsersDatabase.matches

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors

class MatchesDaoIntermediate(
    private val matchesDatabaseDao: MatchesDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : MatchesDaoIntermediateInterface {

    //insert single match
    override suspend fun insertMatch(matchesDataEntity: MatchesDataEntity): Long {
        return matchesDatabaseDao.insertMatch(matchesDataEntity)
    }

    //remove any matches with expired timestamps
    //parameter is current time timestamp in seconds
    override suspend fun cleanExpiredMatches(timestamp: Long) {
        matchesDatabaseDao.cleanExpiredMatches(timestamp)
    }

    //returns number of matches(rows) in table
    override suspend fun countMatchesRemaining(): Int {
        return matchesDatabaseDao.countMatchesRemaining()
    }

    //get the next match from the table (the match with the lowest index)
    //get the number of matches from the table (the match with the lowest index)
    override suspend fun getMatches(
        numMatches: Int,
        restrictedIndexList: List<Long>
    ): List<MatchesDataEntity> {
        return matchesDatabaseDao.getMatches(numMatches, restrictedIndexList)
    }

    //return a list of all matches from the table
    override suspend fun getAllMatches(): List<GetAllMatchesDataClass> {
        return matchesDatabaseDao.getAllMatches()
    }

    //Each SHOULD only have one accountOID per index, but there is no guarantee technically, so
    // requesting them all.
    override suspend fun getAllMatchesForAccountOID(matchAccountOid: String): List<GetAllMatchesDataClass> {
       return matchesDatabaseDao.getAllMatchesForAccountOID(matchAccountOid)
    }

    //delete a specific match from the table by index
    override suspend fun deleteMatch(matchIndex: Long) {
        matchesDatabaseDao.deleteMatch(matchIndex)
    }

    //delete all
    override suspend fun clearTable() {
        matchesDatabaseDao.clearTable()
    }
}