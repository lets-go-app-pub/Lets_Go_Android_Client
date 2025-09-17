package site.letsgoapp.letsgo.standAloneObjects.findMatchesObject

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Constraints
import androidx.work.NetworkType
import findmatches.FindMatches
import kotlinx.coroutines.*
import report_enums.ReportMessages
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentMessageCommandType
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.GetAllMatchesDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.ApplicationRepository
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.utilities.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class FindMatchesServerSuccessTypeEnum {
    SUCCESSFULLY_EXTRACTED,
    NO_MATCHES_FOUND,
    MATCH_ALGORITHM_ON_COOL_DOWN,
    NO_SWIPES_REMAINING,
}

data class FindMatchesServerSuccessTypeDataHolder(
    val successTypeEnum: FindMatchesServerSuccessTypeEnum,
    val timeCoolDownEnds: Long = -1L, //time in SystemClock.elapsedRealtime() that cool down ends
)

class FindMatchesObject(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val matchesDataSource: MatchesDaoIntermediateInterface,
    private val otherUsersDataSource: OtherUsersDaoIntermediateInterface,
    private val unsentSimpleServerCommandsDataSource: UnsentSimpleServerCommandsDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val sharedApplicationOrLoginViewModelInstanceId: String,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) {

    private val _returnGrpcFunctionErrorStatusEnumToActivity: MutableLiveData<EventWrapperWithKeyString<GrpcFunctionErrorStatusEnum>> =
        MutableLiveData()
    val returnGrpcFunctionErrorStatusEnumToActivity: LiveData<EventWrapperWithKeyString<GrpcFunctionErrorStatusEnum>> =
        _returnGrpcFunctionErrorStatusEnumToActivity

    private val _matchesFromDatabaseReturnValue: MutableLiveData<EventWrapperWithKeyString<Pair<OtherUsersDataEntity, MatchesDataEntity>>> =
        MutableLiveData()
    val matchesFromDatabaseReturnValue: LiveData<EventWrapperWithKeyString<Pair<OtherUsersDataEntity, MatchesDataEntity>>> =
        _matchesFromDatabaseReturnValue

    private val _findMatchesServerSuccessType: MutableLiveData<EventWrapper<FindMatchesServerSuccessTypeDataHolder>> =
        MutableLiveData()
    val findMatchesServerSuccessType: LiveData<EventWrapper<FindMatchesServerSuccessTypeDataHolder>> =
        _findMatchesServerSuccessType

    //Pair<type of location, number matches requested>
    private val _locationRequested: MutableLiveData<EventWrapper<TypeOfLocationUpdate>> =
        MutableLiveData()
    val locationRequested: LiveData<EventWrapper<TypeOfLocationUpdate>> = _locationRequested

    //This will be called in 2 cases
    //1 when the requestMatchFromDatabase is called and started
    //2 when parameters are set for the matching algorithm setCategories for example, then it will be unset at the end
    private var stopMatchesFromBeingRequestedInt = AtomicInteger(0)

    //this will be passed to the fragment
    private var matchesFragmentId = "~"

    //This boolean is used to tell if the findMatches gRPC client is currently running
    //NOTE: This value is set to true before the location is requested, if something happens like the activity dies while it is running or a value is not properly
    // returned to onActivityResult or onRequestPermissions then this will be permanently true and the algorithm will never run again.
    //NOTE: This is stored inside the FindMatchesObject so that.
    // 1) It can survive configuration changes (registerForActivityResult() calls also will).
    // 2) AppActivity and SharedApplicationViewModel will be able to access it.
    // 3) If the variable is somehow set to false, restarting will re-create the SharedApplicationViewModel (and by extension this class) fixing
    // the problem for the user.
    val findMatchesClientRunning = AtomicBooleanWithApproximateUpdateTimestamp(
        applicationContext,
        errorHandling
    )

    //This class will combine a timestamp with a boolean value. Whenever the boolean is updated, the timestamp will be
    // updated to uptimeMillis() (not always exactly correct). Then if the boolean gets 'stuck' on true an error can be
    // logged and the boolean can be set back to false.
    class AtomicBooleanWithApproximateUpdateTimestamp(
        private val applicationContext: Context,
        private val errorStore: StoreErrorsInterface
    ) {

        //NOTE: These values are not 'connected' in the sense that they are not both wrapped in a lock
        // and forced to be updated together. However the nature of this object is such that an
        // approximate time of last update is perfectly fine. That is because this class is just used to check
        // and see if the boolean got 'stuck' and an approximate time is perfectly fine for that.
        private val bool = AtomicBoolean(false)
        private val timestampMillis = AtomicLong(-1L)

        private fun setTimestamp(successful: Boolean): Boolean {
            //uptimeMillis is important to use because it should NOT count sleep, otherwise this
            // could be set to true before the device goes to sleep, then it will 'think' that it
            // has been set for several hours.
            if (successful)
                timestampMillis.set(SystemClock.uptimeMillis())
            return successful
        }

        fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
            return setTimestamp(bool.compareAndSet(expect, update))
        }

        fun set(update: Boolean) {
            bool.set(update)
            setTimestamp(true)
        }

        fun isDeadlockingOnFindMatchesBoolean() {

            val currentBool = bool.get()
            val timestampLastSetMillis = timestampMillis.get()

            //uptimeMillis() is important, see above
            val currentTimeInMillis = SystemClock.uptimeMillis()
            val timeSetAsThisValue = currentTimeInMillis - timestampLastSetMillis

            //If boolean has been set to 'true' for longer than MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED, send an
            // error. This means it is almost certainly stuck in the 'true' position meaning the app
            // can no longer properly attempt to run the algorithm.
            if (currentBool && timeSetAsThisValue > MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED) {

                val errorMessage =
                    "findMatchesClientRunning Boolean was 'stuck' as true.\n" +
                            "currentTimeInMillis: ${currentTimeInMillis}\n" +
                            "timestampLastSetMillis: ${timestampLastSetMillis}\n" +
                            "timeSetAsThisValue: ${timeSetAsThisValue}\n" +
                            "MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED: ${MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED}\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

                //if the value has not been changed, set it back to false so that algorithm can be run again
                this.set(false)
            }
        }

        companion object {
            //maximum time findMatchesClientRunning can be locked before throwing an exception
            private const val MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED = 2L * 60L * 1000L

            //time between attempting isDeadlockingOnFindMatchesBoolean()
            const val TIME_BETWEEN_CHECKS_IN_MILLIS = 60L * 1000L
        }
    }

    fun setMatchesFragmentId(matchScreenFragmentInstanceId: String) {
        matchesFragmentId = matchScreenFragmentInstanceId
    }

    //This class will confine the matches Mutable list to a specific list providing atomicity
    //NOTE: There wasn't much reason behind the decision to use thread confinement instead of
    // the standard approach of a Mutex
    class ThreadConfinedList {

        //NOTE: always shows the item in index 0
        //the matches currently stored in RAM, lets say 3
        //0 for the user to see
        //1 behind the primary match
        //2 behind the seconds match while the 1st is being replaced
        private var matches = mutableListOf<Pair<OtherUsersDataEntity, MatchesDataEntity>>()

        private val matchesContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        suspend fun add(match: Pair<OtherUsersDataEntity, MatchesDataEntity>) =
            withContext(matchesContext) {
                matches.add(match)
            }

        suspend fun removeMatch(matchAccountOID: String): Boolean {
            return withContext(matchesContext) {
                var indexOfValue = -1
                for (i in matches.indices) {
                    if (matches[i].second.accountOID == matchAccountOID) {
                        indexOfValue = i
                        break
                    }
                }

                //this should almost always be index 0
                if (indexOfValue != -1) {
                    matches.removeAt(indexOfValue)
                    return@withContext true
                }

                return@withContext false
            }
        }

        suspend fun clear() {
            try {
                withContext(matchesContext) {
                    matches.clear()
                }
            } catch (e: CancellationException) {
                //This can happen if close() is called before clear() is.
            }
        }

        suspend fun iterateOverMatches(block: (Pair<OtherUsersDataEntity, MatchesDataEntity>) -> Unit) {
            withContext(matchesContext) {
                for (match in matches) {
                    block(match)
                }
            }
        }

        suspend fun updateSingleMatchOtherUserDataEntity(
            passedUser: OtherUsersDataEntity,
        ) = withContext(matchesContext) {
            for (i in matches.indices) {
                if (matches[i].first.accountOID == passedUser.accountOID) {
                    matches[i] = Pair(
                        passedUser,
                        matches[i].second
                    )
                    break
                }
            }
        }

        suspend fun size(): Int =
            withContext(matchesContext) {
                return@withContext matches.size
            }

        fun close() {
            matchesContext.close()
        }
    }

    private val matchesList = ThreadConfinedList()

    //This Handler requests matches from the server through a LiveData. Because of that it will not be returned
    // to the AppActivity after onStop() is called (this is intentional).
    private val mainHandler = Handler(Looper.getMainLooper())

    private var uptimeMillisToPostAt = -1L

    //NOTE: Making these string types because for some reason was not working as an int in repository.
    private val handlerFindMatchesRunnableToken = "find_matches_algorithm_running"
    private val handlerBooleanErrorRunnableToken = "find_matches_bool_error"

    init {
        checkForBooleanStuck()
    }

    private fun checkForBooleanStuck() {
        mainHandler.postAtTime(
            {
                Log.i("mainHandlerMatch", "Running Handler")
                findMatchesClientRunning.isDeadlockingOnFindMatchesBoolean()
                checkForBooleanStuck()
            },
            handlerBooleanErrorRunnableToken,
            SystemClock.uptimeMillis() + AtomicBooleanWithApproximateUpdateTimestamp.TIME_BETWEEN_CHECKS_IN_MILLIS
        )
    }

    fun clearVariables() {
        stopMatchesFromBeingRequestedInt.set(0)
        runBlocking {
            matchesList.clear()
        }
    }

    //This function will clear all callbacks from the handler, it is only meant to be called
    // in onCleared() from the ViewModel.
    fun clearHandlerForViewModelCleared() {
        stopAllHandlers()

        matchesList.close()
    }

    fun makePassedListShallowCopyOfMatches(matchesShallowCopy: MutableList<Pair<OtherUsersDataEntity, MatchesDataEntity>>) {
        matchesShallowCopy.clear()
        runBlocking {
            matchesList.iterateOverMatches { match ->
                matchesShallowCopy.add(match)
            }
        }
    }

    fun updateMatchOtherUserDataEntity(
        passedUser: OtherUsersDataEntity,
    ) = CoroutineScope(ioDispatcher).launch {
        matchesList.updateSingleMatchOtherUserDataEntity(passedUser)
    }

    suspend fun clearMatchesFromList() = withContext(ioDispatcher) {
        stopMatchesFromBeingRequestedInt.addAndGet(1)

        //NOTE: this is called when updating post login info or changing the categories
        // it should never need a live data back to the MatchScreenFragment because there
        // is no direct path from the fragments that modify these to the MatchScreenFragment
        matchesList.clear()
    }

    //If the update was successful (activities or user profile values changed successfully) then clear all
    // matches from the database (clearMatchesFromList() should have been called already). If failed then
    // reload the matches into matchesList.
    suspend fun functionToUpdateAlgorithmParametersCompleted(successful: Boolean) {
        if (successful) {
            clearMatchesFromDatabase()
        } else {
            stopMatchesFromBeingRequestedInt.addAndGet(-1)
            requestMatchFromDatabase()
        }
    }

    //removes the matching 'Object Requiring Info' from the passed matching users
    //matches is the map of accountOID to the Match data
    //accountOidToDeleteList is a list of account OIDs to the respective 'Object Requiring Info' from
    private suspend fun removeUserMatchObjectsFromMultipleUsers(
        matches: Map<String, GetAllMatchesDataClass>,
        accountOidToDeleteList: List<String>,
        transactionWrapper: TransactionWrapper,
        errorStore: StoreErrorsInterface
    ) = withContext(ioDispatcher) {
        transactionWrapper.runTransaction {
            val otherUsersToDelete =
                otherUsersDataSource.getOtherUsersInList(accountOidToDeleteList)
                    .associateBy { it.accountOID }

            if (accountOidToDeleteList.size != otherUsersToDelete.size) { //if not enough other users were returned

                //get values that do NOT exist inside otherUsersToDelete list but DO exist inside accountOidToDeleteList
                val differenceList =
                    accountOidToDeleteList.minus(otherUsersToDelete.keys.map { it }.toSet())

                val errorString =
                    "When removing 'other user' as a match, other users did not exist in database.\n" +
                            "accountOidToDeleteList.size: ${accountOidToDeleteList.size}\n" +
                            "otherUsersToDelete.size: ${otherUsersToDelete.size}\n" +
                            "differenceList: $differenceList"

                sendFindMatchesError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors()
                )
            }

            for (accountOid in accountOidToDeleteList) {

                val otherUser = otherUsersToDelete[accountOid]
                val matchIndex = matches[accountOid]?.matchIndex

                if (otherUser != null && matchIndex != null) {
                    removeObjectRequiringInfo(
                        otherUser,
                        ReferencingObjectType.MATCH_REFERENCE,
                        matchIndex.toString(),
                        this,
                        errorStore
                    )
                } else {

                    //NOTE: This can send a duplicate error to the error above. However it could also be that
                    // the arrays are the same size AND have different elements.
                    val errorString =
                        "When removing 'other user' as a match, match or other user was null.\n" +
                                "accountOidToDeleteList.size: ${accountOidToDeleteList.size}\n" +
                                "otherUsersToDelete.size: ${otherUsersToDelete.size}\n" +
                                "otherUser $otherUser\n" +
                                "match ${matches[accountOid]}\n"

                    sendFindMatchesError(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors()
                    )
                }
            }
        }
    }

    private suspend fun clearMatchesFromDatabase() = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.OTHER_USERS
        )

        transactionWrapper.runTransaction {

            val matches = matchesDataSource.getAllMatches().associateBy { it.account_oid }

            matches.let {

                val userOidList = matches.map { it.key }

                //matching other_users are removed here if necessary
                removeUserMatchObjectsFromMultipleUsers(
                    matches,
                    userOidList,
                    this,
                    errorHandling
                )

                //matches are deleted above, this is just repeating what the above code did inside the 'other_users' database
                matchesDataSource.clearTable()
            }
        }

        stopMatchesFromBeingRequestedInt.addAndGet(-1)

        requestMatchFromDatabase()
    }

    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    private suspend fun cleanExpiredMatchesFromDatabase(
        transactionWrapper: TransactionWrapper,
    ) = withContext(ioDispatcher) {
        transactionWrapper.runTransaction {

            val currentTimestamp = getCurrentTimestampInMillis()
            val matches = matchesDataSource.getAllMatches().associateBy {
                it.account_oid
            }

            val accountOidToDelete = mutableListOf<String>()

            for (match in matches) {
                if (match.value.expiration_time <= currentTimestamp) {
                    accountOidToDelete.add(match.value.account_oid)
                }
            }

            if (accountOidToDelete.isNotEmpty()) { //if there are matches to be removed
                //matching other_users are removed here if necessary
                removeUserMatchObjectsFromMultipleUsers(
                    matches,
                    accountOidToDelete,
                    this,
                    errorHandling
                )

                //matches are deleted above, this is just repeating what the above code did inside the 'other_users' database
                matchesDataSource.cleanExpiredMatches(currentTimestamp)
            }
        }
    }

    //will request from database then from server if necessary
    suspend fun requestMatchFromDatabase(
        makeServerCall: Boolean = true
    ) {
        Log.i("testingDoze", "FindMatchesObject starting requestMatchFromDatabase()")
        if (matchesList.size() < GlobalValues.maximumNumberMatchesStoredInViewModel
            && stopMatchesFromBeingRequestedInt.compareAndSet(0, 1)
        ) {
            //removing the callback to run this function on a Handler
            stopFindMatchesHandlerMessage()

            CoroutineScope(ioDispatcher).launch {
                requestMatchFromDatabaseHelper(makeServerCall)
            }
        }
    }

    private suspend fun requestMatchFromDatabaseHelper(
        makeServerCall: Boolean
    ) = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.OTHER_USERS
        )
        transactionWrapper.runTransaction {

            cleanExpiredMatchesFromDatabase(this)

            val numMatchesRemainingInDatabase = matchesDataSource.countMatchesRemaining()

            val numberMatchesNeeded =
                GlobalValues.maximumNumberMatchesStoredInViewModel - matchesList.size()

            val numberMatchesToRequestFromDatabase =
                if (numberMatchesNeeded > numMatchesRemainingInDatabase) {
                    numMatchesRemainingInDatabase
                } else {
                    numberMatchesNeeded
                }

            val returnList = mutableListOf<Pair<OtherUsersDataEntity, MatchesDataEntity>>()

            //Store needed arrays from database
            if (numberMatchesToRequestFromDatabase > 0) {

                val restrictedIndex = mutableListOf<Long>()
                matchesList.iterateOverMatches { match ->
                    restrictedIndex.add(match.second.matchIndex)
                }

                val matchesDataEntity = matchesDataSource.getMatches(
                    numberMatchesToRequestFromDatabase,
                    restrictedIndex.toList()
                ).associateBy {
                    it.accountOID
                }


                var stringMatches = ""
                for (i in matchesDataEntity) {
                    stringMatches += i.value.matchIndex
                    stringMatches += " "
                }

                val accountOIDs = matchesDataEntity.map {
                    it.key
                }

                val otherUsersList =
                    if (accountOIDs.isNotEmpty()) {
                        otherUsersDataSource.getOtherUsersInList(accountOIDs)
                            .associateBy { it.accountOID }
                    } else {
                        mapOf()
                    }

                Log.i(
                    "matches_results",
                    "otherUsersList.size: ${otherUsersList.size} matchesDataEntity.size: ${matchesDataEntity.size}"
                )

                if (otherUsersList.size != matchesDataEntity.size) {
                    //get values that do NOT exist inside otherUsersToDelete list but DO exist inside accountOidToDeleteList
                    val differenceList =
                        otherUsersList.keys.map { it }
                            .minus(matchesDataEntity.keys.map { it }.toSet())

                    val errorString =
                        "When requesting 'other user' as a match, other users did not exist in database.\n" +
                                "otherUsersList.size: ${otherUsersList.size}\n" +
                                "matchesDataEntity.size: ${matchesDataEntity.size}\n" +
                                "differenceList $differenceList\n"

                    sendFindMatchesError(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors()
                    )
                }

                for (accountOID in accountOIDs) {
                    val otherUser = otherUsersList[accountOID]
                    val match = matchesDataEntity[accountOID]

                    if (otherUser != null && match != null) {
                        returnList.add(
                            Pair(
                                otherUser,
                                match
                            )
                        )
                    } else {

                        val errorString =
                            "When removing 'other user' as a match, match or other user was null.\n" +
                                    "otherUsersList.size: ${otherUsersList.size}\n" +
                                    "matchesDataEntity.size: ${matchesDataEntity.size}\n" +
                                    "otherUser $otherUser\n" +
                                    "match $match\n"

                        sendFindMatchesError(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors()
                        )

                    }
                }

                for (x in returnList) {
                    Log.i(
                        "return_list",
                        "matchIndex: ${x.second.matchIndex} pointValue: ${x.second.pointValue} name: ${x.first.name}"
                    )
                }

                //sort them so earliest matches are always first
                returnList.sortBy {
                    it.second.matchIndex
                }
            }

            for (ele in returnList) {
                matchesList.add(ele)

                transactionWrapper.runAfterTransaction {
                    withContext(Dispatchers.Main) {
                        //NOTE: because Activities only receive live data in RESUMED or STARTED states, when the app
                        // is minimized and onStop() is called, this will no longer fire, making it so that Matches will
                        // not be requested when the Activity is in the background (this is intentional, less overhead).
                        _matchesFromDatabaseReturnValue.value = EventWrapperWithKeyString(
                            ele,
                            matchesFragmentId
                        )
                    }
                }
            }

            //allow more matches to be requested from database
            stopMatchesFromBeingRequestedInt.addAndGet(-1)

            val numberMatchesNeededFromServer =
                GlobalValues.server_imported_values.maximumNumberResponseMessages - numMatchesRemainingInDatabase

            //If matches were needed by the database, run the client.
            //The findMatchesClientRunning bool will be checked inside AppActivity. See liveDataObserver inside AppActivity
            // for more information.
            //NOTE: Because this calls request matches from database do it after the database is unlocked
            if (makeServerCall && numberMatchesNeededFromServer > 0) {
                transactionWrapper.runAfterTransaction {
                    //NOTE: It is important than ANY end path of requesting location sets findMatchesClientRunning to false.
                    withContext(Dispatchers.Main) {
                        _locationRequested.value =
                            EventWrapper(TypeOfLocationUpdate.ALGORITHM_REQUEST)
                    }
                }
            }
        }
    }

    suspend fun requestMatchesFromServer(
        longitude: Double,
        latitude: Double,
    ) = withContext(ioDispatcher) {

        val numMatchesToRequest =
            GlobalValues.server_imported_values.maximumNumberResponseMessages - matchesDataSource.countMatchesRemaining()

        //NOTE: This check is important for below when findMatchesReturn() is called. The cap message
        // will return SUCCESSFULLY_EXTRACTED if numMatchesToRequest were returned. If numMatchesToRequest
        // is zero none will be returned and so it will fizzle because it will send nothing back to
        // the fragment.
        if (0 < numMatchesToRequest) { //if number matches is within acceptable bounds

            //extract loginToken
            val loginToken = loginTokenIsValid()

            try {
                if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                    val findMatchesRequest = FindMatches.FindMatchesRequest.newBuilder()
                        .setLoginInfo(getLoginInfo(loginToken))
                        .setNumberMessages(numMatchesToRequest)
                        .setClientLongitude(longitude)
                        .setClientLatitude(latitude)
                        .build()

                    clientsIntermediate.findMatches(
                        findMatchesRequest
                    ).collect {
                        findMatchesReturn(it)
                    }

                } else { //if login token not good

                    //navigate back to login activity (error was already stored)
                    withContext(Dispatchers.Main) {
                        _returnGrpcFunctionErrorStatusEnumToActivity.value =
                            EventWrapperWithKeyString(
                                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                                sharedApplicationOrLoginViewModelInstanceId
                            )
                    }
                }
            } finally {
                findMatchesClientRunning.set(false)
            }
        } else { //if number matches is invalid

            val errorString = "Number of matches was an invalid value\n" +
                    "GlobalValues.server_imported_values.maximumNumberResponseMessages: ${GlobalValues.server_imported_values.maximumNumberResponseMessages}\n" +
                    "numMatchesToRequest: $numMatchesToRequest\n"

            sendFindMatchesError(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors()
            )

            // Since the change to where findMatchesReturn() can no longer make a server call, I
            // don't think this block can be hit anymore. However, setting it up to recover
            // just in case.
            findMatchesClientRunning.set(false)

            //Want to avoid a potential dead end. Would rather have an extra
            // server call here rather than starvation.
            requestMatchFromDatabase()
        }

    }

    suspend fun removeMatch(
        matchAccountOid: String,
        matchIndex: Long = -1,
    ): Boolean {
        return withContext(ioDispatcher) {
            //remove match from list
            val matchRemoved = matchesList.removeMatch(matchAccountOid)

            val finalMatchIndex =
                if (matchIndex == -1L) {

                    val allMatches = matchesDataSource.getAllMatchesForAccountOID(matchAccountOid)
                    if (allMatches.isEmpty()){
                       return@withContext matchRemoved
                    } else if (allMatches.size > 1) {
                        val errorString =
                            "The same account existed multiple times inside the database as a match.\n" +
                                    "matchAccountOid: $matchAccountOid" +
                                    "allMatches: $allMatches"

                        sendFindMatchesError(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors()
                        )

                       //ok to continue here
                    }

                    allMatches[0].matchIndex
                } else {
                    matchIndex
                }

            //remove match from database
            removeMatchFromDatabase(matchAccountOid, finalMatchIndex)

            return@withContext matchRemoved
        }
    }

    suspend fun userRespondedToMatch(
        match: Pair<OtherUsersDataEntity, MatchesDataEntity>,
        responseType: ReportMessages.ResponseType,
        reportReason: ReportMessages.ReportReason,
        otherInfo: String,
    ) = withContext(ioDispatcher) {
        removeMatch(match.first.accountOID, match.second.matchIndex)

        //send response to server
        sendUserMatchOptionResponseToServer(
            match.first.accountOID,
            responseType,
            reportReason,
            otherInfo
        )

        requestMatchFromDatabase()
    }

    private suspend fun sendUserMatchOptionResponseToServer(
        matchAccountID: String,
        responseType: ReportMessages.ResponseType,
        reportReason: ReportMessages.ReportReason,
        otherInfo: String,
    ) = withContext(ioDispatcher) {

        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            val request = ReportMessages.UserMatchOptionsRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .setMatchAccountId(matchAccountID)
                .setResponseType(responseType)
                .setReportReason(reportReason)
                .setOtherInfo(otherInfo)
                .build()

            //send request to server
            val response = clientsIntermediate.userMatchOptionsSwipe(request)

            val errorReturn = checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

            if (errorReturn.first != "~") {
                val errorMessage = "Send user match option has unknown error occur.\n" +
                        "errorReturn: $errorReturn\n" +
                        "matchAccountID: $matchAccountID\n" +
                        "responseType: $responseType\n" +
                        "reportReason: $reportReason\n"

                errorMessageRepositoryHelper(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors(),
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    errorHandling,
                    ioDispatcher
                )
            }

            if (errorReturn.second != GrpcFunctionErrorStatusEnum.DO_NOTHING
                && errorReturn.second != GrpcFunctionErrorStatusEnum.NO_ERRORS
            ) { //if send response failed

                //if the message failed to be sent because of a problem with the login or connection
                // store it to be sent later
                //if the message is a type such as LG_ERROR, do NOT try and re-send it, it could be corrupt
                if (errorReturn.second == GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
                    || errorReturn.second == GrpcFunctionErrorStatusEnum.SERVER_DOWN
                    || errorReturn.second == GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
                ) {
                    unsentSimpleServerCommandsDataSource.insertMessage(
                        UnsentSimpleServerCommandsDataEntity(
                            UnsentMessageCommandType.USER_MATCH_OPTION.ordinal,
                            request.toByteArray()
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _returnGrpcFunctionErrorStatusEnumToActivity.value =
                        EventWrapperWithKeyString(
                            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                            sharedApplicationOrLoginViewModelInstanceId
                        )
                }
            }

        } else { //if login token not good

            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.value =
                    EventWrapperWithKeyString(
                        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                        sharedApplicationOrLoginViewModelInstanceId
                    )
            }
        }
    }

    private suspend fun removeMatchFromDatabase(
        matchAccountOid: String,
        matchIndex: Long,
    ) = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.OTHER_USERS
        )
        transactionWrapper.runTransaction {

            //NOTE: MUST request the user info from the database here using
            // otherUsersDataSource.getSingleOtherUser(match.first.accountOID)
            // instead of using match.first, otherwise the info can be very outdated and cause problems
            // for instance if 2 of the same accountOID are loaded to the screen (which shouldn't happen
            // either) a memory leak can ensue by saving an outdated objectsRequiringInfo to the
            // database
            removeObjectRequiringInfo(
                otherUsersDataSource.getSingleOtherUser(matchAccountOid),
                ReferencingObjectType.MATCH_REFERENCE,
                matchIndex.toString(),
                this,
                errorHandling
            )

            matchesDataSource.deleteMatch(matchIndex)
        }
    }

    private suspend fun findMatchesReturn(
        nextResponseDataClass: GrpcClientResponse<FindMatches.FindMatchesResponse>,
    ) = withContext(ioDispatcher) {

        Log.i(
            "return_list",
            "findMatchesReturn() name: ${nextResponseDataClass.response.singleMatch.memberInfo.accountName} pointValue: ${nextResponseDataClass.response.singleMatch.pointValue}"
        )

        var returnErrorStatusEnum = GrpcFunctionErrorStatusEnum.LOG_USER_OUT
        var errorStringForEnums = "~"

        if (nextResponseDataClass.response.hasSingleMatch()) {

            Log.i("find_matches_final", "response.hasSingleMatch()")

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.OTHER_USERS
            )

            transactionWrapper.runTransaction {

                val returnValues = checkApplicationAndroidErrorEnum(
                    nextResponseDataClass.androidErrorEnum,
                    nextResponseDataClass.errorMessage
                ) {
                    Pair("~", GrpcFunctionErrorStatusEnum.NO_ERRORS)
                }

                returnErrorStatusEnum = returnValues.second
                errorStringForEnums =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            Log.i("find_matches_final", "response.hasSingleMatch() NO_ERRORS")

                            //NOTE: It is possible to get 2 identical matches, it should technically never happen
                            // because the server should never send them. However, if the server DOES send them then
                            // it will treat them as individual matches. This is to avoid attempting to ignore one
                            // and it will allow for the user to connect. If this happens none of the development
                            // options to handle the situation are 'clean' and so letting the user deal with both
                            // of them as individual matches should help resolve the problem in a more standard way.
                            val matchIndex = matchesDataSource.insertMatch(
                                MatchesDataEntity(
                                    nextResponseDataClass.response.singleMatch.memberInfo.accountOid,
                                    nextResponseDataClass.response.singleMatch.pointValue,
                                    nextResponseDataClass.response.singleMatch.expirationTime,
                                    nextResponseDataClass.response.singleMatch.otherUserMatch,
                                    nextResponseDataClass.response.singleMatch.swipesRemaining,
                                    nextResponseDataClass.response.singleMatch.swipesTimeBeforeReset
                                )
                            )

                            insertOrUpdateOtherUser(
                                nextResponseDataClass.response.singleMatch.memberInfo,
                                ApplicationRepository.UpdateMemberReasonEnum.ALGORITHM_MATCH_RETURN,
                                otherUsersDataSource,
                                applicationContext,
                                this,
                                errorHandling,
                                deleteFileInterface,
                                referenceType = ReferencingObjectType.MATCH_REFERENCE,
                                referenceId = matchIndex.toString(),
                            )

                            this.runAfterTransaction {
                                //The server is currently returning matches, no reason to make a
                                // server call here again.
                                requestMatchFromDatabase(false)
                                withContext(Dispatchers.Main) {
                                    _findMatchesServerSuccessType.value = EventWrapper(
                                        FindMatchesServerSuccessTypeDataHolder(
                                            FindMatchesServerSuccessTypeEnum.SUCCESSFULLY_EXTRACTED
                                        )
                                    )
                                }
                            }

                            returnValues.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "DO_NOTHING should never reach this point inside findMatchesReturn().\n"
                        }
                        else -> {
                            returnValues.first
                        }
                    }

            } //end of transaction
        } else { //final message (this will probably be findMatchesCap, however if the connection is lost it could be not set)

            Log.i("find_matches_final", "response: ${nextResponseDataClass.response}")

            val returnValues =
                if (nextResponseDataClass.response.hasFindMatchesCap()) {
                    checkApplicationReturnStatusEnum(
                        nextResponseDataClass.response.findMatchesCap.returnStatus,
                        nextResponseDataClass
                    )
                } else { //Something went wrong with cap message, it is not set AND returns NO_ERRORS.
                    checkApplicationAndroidErrorEnum(
                        nextResponseDataClass.androidErrorEnum,
                        nextResponseDataClass.errorMessage
                    ) {
                        CoroutineScope(ioDispatcher).launch {
                            val errorString =
                                "The final message of findMatches was returned and was NOT a findMatchesCap type but also no an error.\n" +
                                        nextResponseDataClass.response.toString()

                            sendFindMatchesError(
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                Thread.currentThread().stackTrace[2].fileName,
                                printStackTraceForErrors()
                            )
                        }
                        Pair("~", GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                    }
                }

            //The cap is always expected to be sent back unless a problem with the connection occurs.
            returnErrorStatusEnum = returnValues.second
            errorStringForEnums =
                when (returnErrorStatusEnum) {
                    GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                        //If the block is reached it means hasFindMatchesCap is true.

                        when (nextResponseDataClass.response.findMatchesCap.successType) {
                            FindMatches.FindMatchesCapMessage.SuccessTypes.UNKNOWN -> {
                                val errorString =
                                    "Invalid success type returned with a ReturnStatus.SUCCESS when findMatches was run.\n" +
                                            "returnErrorStatusEnum: $returnErrorStatusEnum\n" +
                                            nextResponseDataClass.response.toString()

                                sendFindMatchesError(
                                    errorString,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    Thread.currentThread().stackTrace[2].fileName,
                                    printStackTraceForErrors()
                                )

                                //Will use POLLING_DELAY_BETWEEN_FIND_MATCHES_ATTEMPTS when set to 0.
                                setupHandleToTryToFindMatchesAgain(0)
                                withContext(Dispatchers.Main) {
                                    _findMatchesServerSuccessType.value = EventWrapper(
                                        FindMatchesServerSuccessTypeDataHolder(
                                            FindMatchesServerSuccessTypeEnum.NO_MATCHES_FOUND
                                        )
                                    )
                                }
                            }
                            FindMatches.FindMatchesCapMessage.SuccessTypes.SUCCESSFULLY_EXTRACTED -> {
                                //This means that the number of requested matches were successfully sent back. Each
                                // match individually will send a signal back to the fragment.

                                //In an odd edge case, the user could be swiping faster than matches are returned.
                                // This situation could cause starvation to occur because when a SingleMatchMessage
                                // is returned from the server, it will not make another server request at
                                // that time.
                                requestMatchFromDatabase()
                            }
                            FindMatches.FindMatchesCapMessage.SuccessTypes.NO_MATCHES_FOUND -> {
                                setupHandleToTryToFindMatchesAgain(nextResponseDataClass.response.findMatchesCap.coolDownOnMatchAlgorithm)
                                withContext(Dispatchers.Main) {
                                    _findMatchesServerSuccessType.value = EventWrapper(
                                        FindMatchesServerSuccessTypeDataHolder(
                                            FindMatchesServerSuccessTypeEnum.NO_MATCHES_FOUND
                                        )
                                    )
                                }
                            }
                            FindMatches.FindMatchesCapMessage.SuccessTypes.MATCH_ALGORITHM_ON_COOL_DOWN -> {
                                //There are 2 ways MATCH_ALGORITHM_ON_COOL_DOWN can be returned.
                                //1) No matches were found for the person with the given parameters and so the algorithm will not re-run for a certain
                                // period of time after no matches were found.
                                //2) The algorithm ran too quickly, this would require the user to extract AND swipe past something like 20 matches in 3 seconds
                                // this is mostly to prevent the algorithm from being spammed (either from a bug or trolling).
                                //Either way the 'no matches found' should be an acceptable response.
                                setupHandleToTryToFindMatchesAgain(nextResponseDataClass.response.findMatchesCap.coolDownOnMatchAlgorithm)
                                withContext(Dispatchers.Main) {
                                    _findMatchesServerSuccessType.value = EventWrapper(
                                        FindMatchesServerSuccessTypeDataHolder(
                                            FindMatchesServerSuccessTypeEnum.MATCH_ALGORITHM_ON_COOL_DOWN
                                        )
                                    )
                                }
                            }
                            FindMatches.FindMatchesCapMessage.SuccessTypes.NO_SWIPES_REMAINING -> {
                                setupHandleToTryToFindMatchesAgain(nextResponseDataClass.response.findMatchesCap.swipesTimeBeforeReset)
                                withContext(Dispatchers.Main) {
                                    _findMatchesServerSuccessType.value = EventWrapper(
                                        FindMatchesServerSuccessTypeDataHolder(
                                            FindMatchesServerSuccessTypeEnum.NO_SWIPES_REMAINING,
                                            SystemClock.elapsedRealtime() + nextResponseDataClass.response.findMatchesCap.swipesTimeBeforeReset
                                        )
                                    )
                                }
                            }
                            null,
                            FindMatches.FindMatchesCapMessage.SuccessTypes.UNRECOGNIZED,
                            -> {

                                val errorString =
                                    "FindMatches returned an invalid GrpcFunctionErrorStatusEnum and SingleMatchMessage.SuccessTypes combination.\n" +
                                            "returnErrorStatusEnum: $returnErrorStatusEnum\n" +
                                            "type: ${nextResponseDataClass.response.responseTypesCase}\n" +
                                            nextResponseDataClass.response.toString()

                                sendFindMatchesError(
                                    errorString,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    Thread.currentThread().stackTrace[2].fileName,
                                    printStackTraceForErrors()
                                )

                                withContext(Dispatchers.Main) {
                                    _returnGrpcFunctionErrorStatusEnumToActivity.value =
                                        EventWrapperWithKeyString(
                                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                            sharedApplicationOrLoginViewModelInstanceId
                                        )
                                }
                            }
                        }

                        returnValues.first
                    }
                    GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                        "DO_NOTHING should never reach this point inside findMatchesReturn().\n"
                    }
                    else -> {
                        returnValues.first
                    }
                }
        }

        if (errorStringForEnums != "~") {
            CoroutineScope(ioDispatcher).launch {
                sendFindMatchesError(
                    errorStringForEnums,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors()
                )
            }
        }

        if (returnErrorStatusEnum != GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            //Wait and try again, the application activity can shut it down if an error that is
            // unrecoverable occurs.
            setupHandleToTryToFindMatchesAgain(GlobalValues.POLLING_DELAY_BETWEEN_FIND_MATCHES_ATTEMPTS)

            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.value =
                    EventWrapperWithKeyString(
                        returnErrorStatusEnum,
                        sharedApplicationOrLoginViewModelInstanceId
                    )
            }
        }
    }

    //pass coolDownRemaining in ms
    private fun setupHandleToTryToFindMatchesAgain(coolDownRemainingInMillis: Long) {

        val postTime = //5000L
            if (coolDownRemainingInMillis > GlobalValues.POLLING_DELAY_BETWEEN_FIND_MATCHES_ATTEMPTS) {
                coolDownRemainingInMillis
            } else {
                GlobalValues.POLLING_DELAY_BETWEEN_FIND_MATCHES_ATTEMPTS
            }

        Log.i("setupHandler", "setupHandleToTryToFindMatchesAgain() postTime: $postTime")

        //this is protection in case it runs twice
        stopFindMatchesHandlerMessage()

        uptimeMillisToPostAt = SystemClock.uptimeMillis() + postTime
        mainHandler.postAtTime(
            {
                Log.i("mainHandlerMatch", "Running Handler")
                //This Handler uses the Main looper, want to change threads as soon as possible
                CoroutineScope(ioDispatcher).launch {
                    requestMatchFromDatabase()
                }
            },
            handlerFindMatchesRunnableToken,
            uptimeMillisToPostAt
        )
    }

    fun startHandlerIfNecessary() {
        val currentUptimeMillis = SystemClock.uptimeMillis()

        if (currentUptimeMillis >= (uptimeMillisToPostAt + 1000L)) {
            CoroutineScope(ioDispatcher).launch {
                requestMatchFromDatabase()
            }
        } else {
            mainHandler.postAtTime(
                {
                    //This Handler uses the Main looper, want to change threads as soon as possible
                    CoroutineScope(ioDispatcher).launch {
                        requestMatchFromDatabase()
                    }
                },
                handlerFindMatchesRunnableToken,
                uptimeMillisToPostAt
            )
        }

        mainHandler.postAtTime(
            {
                Log.i("mainHandlerMatch", "Running Handler")
                findMatchesClientRunning.isDeadlockingOnFindMatchesBoolean()
                checkForBooleanStuck()
            },
            handlerBooleanErrorRunnableToken,
            SystemClock.uptimeMillis() + AtomicBooleanWithApproximateUpdateTimestamp.TIME_BETWEEN_CHECKS_IN_MILLIS
        )
    }

    fun stopAllHandlers() {
        stopFindMatchesHandlerMessage()
        mainHandler.removeCallbacksAndMessages(handlerBooleanErrorRunnableToken)
    }

    private fun stopFindMatchesHandlerMessage() {
        uptimeMillisToPostAt = -1
        mainHandler.removeCallbacksAndMessages(handlerFindMatchesRunnableToken)
    }

    //removes a reference to an other user belonging to OtherUsersDataEntity objectsRequiringInfo list
    //NOTE: should not be used with chat rooms, OtherUsersDataEntity chatRoomObjects stores references to chat rooms
    /** transactionWrapper requires OtherUsersDatabase to be locked **/
    private suspend fun removeObjectRequiringInfo(
        otherUser: OtherUsersDataEntity?,
        referenceType: ReferencingObjectType,
        iD: String,
        transactionWrapper: TransactionWrapper,
        errorStore: StoreErrorsInterface
    ) = withContext(ioDispatcher) {
        transactionWrapper.runTransaction {
            if (otherUser != null) { //if other user exists in other users database

                val objectsRequiringInfo =
                    convertObjectsRequiringInfoStringToSet(
                        otherUser.objectsRequiringInfo,
                        errorStore
                    )

                val objectRequiringInfo = ObjectRequiringInfo(
                    referenceType,
                    iD
                )

                val containsObject = objectsRequiringInfo.contains(objectRequiringInfo)

                if (containsObject) { //reference exists

                    objectsRequiringInfo.remove(objectRequiringInfo)

                    if (otherUser.chatRoomObjects.isEmpty() && objectsRequiringInfo.isEmpty()) { //if no references remaining

                        deleteOtherUser(
                            otherUsersDataSource,
                            otherUser,
                            deleteFileInterface,
                            errorHandling
                        )
                    } else { //if at least one reference remains

                        otherUser.objectsRequiringInfo =
                            convertObjectsRequiringInfoSetToString(objectsRequiringInfo)

                        removePicturesIfOnlyPartialInfoRequired(
                            otherUser,
                            null,
                            deleteFileInterface,
                            errorHandling
                        )

                        //NOTE: This needs to be updated no matter what because something will have
                        // been added or removed probably from chat rooms or other object list.
                        otherUsersDataSource.upsertSingleOtherUser(otherUser)
                    }

                }
                //else { //this is possible if a match is loaded twice }

            } else { //if other user does not exist in other users database, however a message was sent that they were removed

                val errorString =
                    "When removing 'other user' as a match, other users did not exist in database.\n"

                sendFindMatchesError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors()
                )
            }
        }
    }

    private suspend fun sendFindMatchesError(
        errorString: String,
        lineNumber: Int,
        fileName: String,
        stackTrace: String,
    ) = withContext(ioDispatcher) {

        errorMessageRepositoryHelper(
            errorString, lineNumber, fileName, stackTrace,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            errorHandling,
            ioDispatcher
        )
    }
}