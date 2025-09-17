package site.letsgoapp.letsgo.databases.otherUsersDatabase.matches

interface MatchesDaoIntermediateInterface {

    //insert single match
    suspend fun insertMatch(matchesDataEntity: MatchesDataEntity): Long

    //remove any matches with expired timestamps
    suspend fun cleanExpiredMatches(timestamp: Long) //current time timestamp in seconds

    //returns number of rows in table
    suspend fun countMatchesRemaining(): Int

    //get the number of matches from the table (the match with the lowest index)
    suspend fun getMatches(
        numMatches: Int,
        restrictedIndexList: List<Long> = listOf()
    ): List<MatchesDataEntity>

    //return a list of all matches from the table
    suspend fun getAllMatches(): List<GetAllMatchesDataClass>

    //Each SHOULD only have one accountOID per index, but there is no guarantee technically, so
    // requesting them all.
    suspend fun getAllMatchesForAccountOID(matchAccountOid: String): List<GetAllMatchesDataClass>

    //delete a specific match from the table by index
    suspend fun deleteMatch(matchIndex: Long)

    //delete all
    suspend fun clearTable()
}