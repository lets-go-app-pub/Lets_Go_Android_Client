package site.letsgoapp.letsgo.standAloneObjects.loginFunctions

import access_status.AccessStatusEnum
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.*
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.LoginAccountStatus
import loginfunction.LoginRequest
import loginfunction.LoginResponse
import report_enums.ReportMessages
import request_fields.PictureResponse
import request_fields.ServerIconsRequest
import request_fields.ServerIconsResponse
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentMessageCommandType
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDataEntity
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.INVALID_LOGIN_TOKEN
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithShortRPCManagedChannel
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.repositories.requestPicturesClient
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.loginFunctionWorkers.RefreshLoginFunctionTokenWorker
import site.letsgoapp.letsgo.workers.loginFunctionWorkers.RunLoginFunctionAfterDelayWorker
import user_subscription_status.UserSubscriptionStatusOuterClass.UserSubscriptionStatus
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Can see where login is called here [login_functions_called_from]. **/
class LoginFunctions(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val iconsDataSource: IconsDaoIntermediateInterface,
    private val unsentSimpleServerCommandsDataSource: UnsentSimpleServerCommandsDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    companion object {

        //NOTE: This time MUST be sorter than the Light Doze maintenance window (I saw somewhere that it was ~10 seconds long). Also
        // if there is a cool down on the login function from the server (FUNCTION_CALLED_TOO_QUICKLY returned), this must be longer
        // than that.
        var TIME_BETWEEN_LOGIN_RETRIES_MS =
            5000L
            @VisibleForTesting set

        //The entire call should never take longer than the deadline time login functions uses plus the deadline time for pictures and a little extra.
        private const val UPDATE_MUTEX_TIMEOUT_IN_MS = GlobalValues.gRPC_Short_Call_Deadline_Time * GlobalValues.gRPC_Find_Matches_Deadline_Time + 10L * 1000L

        private val newMutableLoginFunctionObservable =
            MutableStateFlow(LoginFunctionReturnValue(""))
        private val newLoginFunctionObservable: StateFlow<LoginFunctionReturnValue> =
            newMutableLoginFunctionObservable

        //TIME_BETWEEN_LOGIN_LOAD_BALANCING_MULTIPLIER must be smaller than TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER. Otherwise
        // it could avoid load balancing when it attempted a standard load balance. Also WorkManager can have a delay in running
        // so this is set to .8 instead of a value above .9. ChatStreamObject does something similar inside
        // ChatStreamObject.setUpRefreshForStreamHandler().
        private const val TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER = 0.8
        private const val TIME_BETWEEN_LOGIN_LOAD_BALANCING_MULTIPLIER =
            TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER - 0.05

        @VisibleForTesting
        const val WORKER_PARAM_CALLING_FRAGMENT_ID_KEY = "WORKER_PARAM_CALLING_FRAGMENT_ID_KEY"
        @VisibleForTesting
        const val WORKER_PARAM_MANUAL_LOGIN_INFO_KEY = "WORKER_PARAM_MANUAL_LOGIN_INFO_KEY"

        @VisibleForTesting
        const val LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME = "LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME"

        @Volatile
        var nextTimeLoadBalancingAllowed = -1L
            @VisibleForTesting set

        private data class SubscriberInfo(val subscriberUUID: UUID, val subscribedJob: Job)

        //View model subscriptions are mutually exclusive.
        //NOTE: Only modified inside updateMutex, does not need to be volatile.
        private var viewModelSubscriber: SubscriberInfo? = null

        //This is a special case because clean database only needs the login to be able to view the
        // timestamp, and it can run at the same time as other objects requiring login feedback.
        //NOTE: Only modified inside updateMutex, does not need to be volatile.
        private var cleanDatabaseWorkerSubscribedJob: SubscriberInfo? = null

        //This is a special case because when the activities are minimized this will start. Without
        // a special subscriber here, it will pull the subscription away from the repositories and
        // force them to re-subscribe for minor things (for example the camera opening or the user
        // minimizing for a moment).
        //NOTE: Only modified inside updateMutex, does not need to be volatile.
        private var chatStreamWorkerSubscriber: SubscriberInfo? = null

        //This is used as a message that will be stored if nothing is subscribed to the LoginFunctions, it
        // will be set on every return from the LoginFunction, and when a subscriber receives it, the
        // subscriber is expected to run a receivedMessage() to clear it.
        //NOTE: Only modified inside notReceivedReentrantLock.
        private var notReceivedLoginFunctionReturnValue: LoginFunctionReturnValue? = null

        //calculates the time in milliseconds that remain until login needs to be refreshed
        //SystemClock.uptimeMillis() is expected to be added to this in order to get a 'proper' time
        private fun calculateRefreshLoginTime(): Long {

            val value =
                (TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER * GlobalValues.server_imported_values.timeBetweenLoginTokenVerification).toLong()

            //While this should not be a problem with a reasonably set login refresh time, it IS a problem when the
            // timeBetweenLoginTokenVerification is set to a low value. Also when it is moved up, branch prediction
            // should remove most of the unnecessary overhead.
            //Not using BuildConfig.DEBUG here because it is possible for a release version to have a very low refresh
            // time too.

            //NOTE: the * 2 is because the grpcFunctionCallTemplate() will retry if deadline time is exceeded
            val maxLoadBalancingTime = GlobalValues.gRPC_Load_Balancer_Deadline_Time * 2 + 1000
            return if (
                maxLoadBalancingTime >=
                (GlobalValues.server_imported_values.timeBetweenLoginTokenVerification * (1.0 - TIME_BETWEEN_TOKEN_VERIFICATION_MULTIPLIER))
            ) {
                val refreshLoginTime =
                    GlobalValues.server_imported_values.timeBetweenLoginTokenVerification - maxLoadBalancingTime

                if (refreshLoginTime < 1000) {
                    1000
                } else {
                    refreshLoginTime
                }
            } else {
                value
            }
        }

        private fun calculateRefreshLoadBalanceTime(): Long {

            //This function must be run because the load balancing time MUST be lower than then login
            // refresh time. See TIME_BETWEEN_LOGIN_LOAD_BALANCING_MULTIPLIER for details.
            val refreshLoadBalanceTime =
                (TIME_BETWEEN_LOGIN_LOAD_BALANCING_MULTIPLIER * GlobalValues.server_imported_values.timeBetweenLoginTokenVerification).toLong()
            val refreshLoginTime = calculateRefreshLoginTime()

            return if (refreshLoadBalanceTime < refreshLoginTime) {
                refreshLoadBalanceTime
            } else {
                //use login time because load balance time MUST be shorter than login time
                (refreshLoginTime * .95).toLong()
            }
        }

        //Expected to be called when the callee receives a message so LoginFunctions will be able to
        // store the last un-received message.
        suspend fun receivedMessage(_notReceivedLoginFunctionReturnValue: LoginFunctionReturnValue) {
            notReceivedReentrantLock.withLock {
                //NOTE: System.identityHashCode is not guaranteed to be non-unique, however it should be good enough because this
                // should be almost never (if ever) used
                if (notReceivedLoginFunctionReturnValue != null
                    && System.identityHashCode(notReceivedLoginFunctionReturnValue) == System.identityHashCode(
                        _notReceivedLoginFunctionReturnValue
                    )
                ) {
                    notReceivedLoginFunctionReturnValue = null
                }
            }
        }

        suspend fun viewModelSubscribe(
            subscriberUUID: UUID,
            block: suspend (loginFunctionReturnValues: LoginFunctionReturnValue) -> Unit,
        ) {
            updateMutex.withLock {
                viewModelSubscriber?.subscribedJob?.cancel()
                viewModelSubscriber = SubscriberInfo(
                    subscriberUUID,
                    CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                        newLoginFunctionObservable
                            .collect {
                                //make sure to launch a coRoutine here, otherwise the thread (and possibly
                                // coRoutine) that emit() is called from will end up doing the work
                                //Want this to run on main for the same reasons LiveData seems to, it helps
                                // with concurrency issues inside the ViewModels
                                CoroutineScope(Dispatchers.Main).launch {
                                    block(it)
                                }
                            }
                    },
                )
            }
        }

        suspend fun viewModelUnSubscribe(subscriberUUID: UUID) {
            updateMutex.withLock {
                if (viewModelSubscriber?.subscriberUUID == subscriberUUID) {

                    viewModelSubscriber?.subscribedJob?.cancel()
                    viewModelSubscriber = null
                }
            }
        }

        suspend fun cleanDatabaseWorkerSubscribe(
            subscriberUUID: UUID,
            block: suspend (loginFunctionReturnValues: LoginFunctionReturnValue) -> Unit
        ) {
            updateMutex.withLock {
                cleanDatabaseWorkerSubscribedJob?.subscribedJob?.cancel()

                cleanDatabaseWorkerSubscribedJob =
                    SubscriberInfo(
                        subscriberUUID,
                        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                            newLoginFunctionObservable
                                .collect {
                                    //make sure to launch a coRoutine here, otherwise the thread (and possibly
                                    // coRoutine) that emit() is called from will end up doing the work
                                    //Nothing inside of the CleanDatabaseWorker is forced to be on Main, so
                                    // IO is a good dispatcher here
                                    CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                                        block(it)
                                    }
                                }
                        }
                    )
            }
        }

        suspend fun cleanDatabaseWorkerUnsubscribe(subscriberUUID: UUID) {
            updateMutex.withLock {
                if (cleanDatabaseWorkerSubscribedJob?.subscriberUUID == subscriberUUID) {
                    cleanDatabaseWorkerSubscribedJob?.subscribedJob?.cancel()
                    cleanDatabaseWorkerSubscribedJob = null
                }
            }
        }

        suspend fun chatStreamWorkerSubscribe(
            subscriberUUID: UUID,
            block: suspend (loginFunctionReturnValues: LoginFunctionReturnValue) -> Unit,
        ) {
            updateMutex.withLock {
                chatStreamWorkerSubscriber?.subscribedJob?.cancel()

                chatStreamWorkerSubscriber = SubscriberInfo(
                    subscriberUUID,
                    CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                        newLoginFunctionObservable
                            .collect {
                                //make sure to launch a coRoutine here, otherwise the thread (and possibly
                                // coRoutine) that emit() is called from will end up doing the work
                                //Nothing inside of the CleanDatabaseWorker is forced to be on Main, so
                                // IO is a good dispatcher here
                                CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                                    block(it)
                                }
                            }
                    }
                )
            }
        }

        private suspend fun internalChatStreamWorkerUnsubscribe(subscriberUUID: UUID) {
            updateMutex.withLock {
                if (chatStreamWorkerSubscriber?.subscriberUUID == subscriberUUID) {
                    Log.i(
                        "chatStreamSubscription",
                        "subscriberUUID matched for ChatStreamWorker, beginning unSubscribing"
                    )
                    chatStreamWorkerSubscriber?.subscribedJob?.cancel()
                    chatStreamWorkerSubscriber = null
                }
            }
        }

        fun chatStreamWorkerUnsubscribe(subscriberUUID: UUID): Job {
            return CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                internalChatStreamWorkerUnsubscribe(subscriberUUID)
            }
        }

        fun cancelAllLoginFunctionsWork(applicationContext: Context) {
            waitingForWorker.set(false)
            val workManager = WorkManager.getInstance(applicationContext)
            workManager.cancelUniqueWork(LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME)
        }

        //Used to protect certain values from a data race this includes all subscribers and
        // loginFunctionReturnValues.
        //NOTE: This mutex is locked inside accountDatabaseMutex. This means accountDatabaseMutex cannot
        // be locked inside updateMutex or deadlock could occur (although the timeout will technically
        // protect it).
        //NOTE: Using CoroutineTimedMutex as more of a test to make sure it works in production here. It could
        // be useful. It also avoids potential deadlock cases.
        private val updateMutex = CoroutineTimedMutex(UPDATE_MUTEX_TIMEOUT_IN_MS)

        //NOTE: This will protect the variable notReceivedLoginFunctionReturnValue, a mutex is used instead of
        // an atomic because more checks need to be done than just an identity check.
        private var notReceivedReentrantLock = CoroutineReentrantLock()

        //Used to prevent functions that directly effect the user info (such as setName) from running
        // at the same time as the loginFunction will run. This will allow it to NOT be wrapped inside
        // a transaction (the transactions work a lot like mutex locks anyway so there is a definite
        // possibility of deadlock if I start nesting them).
        //There is one notable exception to this, that is when any unsent yes or no swipes are sent
        // to the server a transaction is created. This means that accountDatabaseMutex can not be
        // locked inside DatabasesToRunTransactionIn.MESSAGES transaction.
        //This mutex will protect several 'long' running functions (gRPC calls).
        val accountDatabaseMutex = CoroutineSharedMutex()

        @Volatile
        var currentAccountOID = ""
            @VisibleForTesting set

        @Volatile
        var currentLoginToken = INVALID_LOGIN_TOKEN
            @VisibleForTesting set

        //Used to calculate login expiration time, uses SystemClock.elapsedRealtime()
        //NOTE: Protected by accountDatabaseMutex, does not need to be volatile.
        private var timeLoginRequestSentToServer = -1L

        //The time that this token will expire in SystemClock.elapsedRealtime(). It is not exact. It attempts to take
        // latency and server runtime into account by checking the time the request was sent to the server instead of the time
        // the response was received, however with clock drift there is the possibility that it could be a little early
        // or a little late.
        //NOTE: Protected by accountDatabaseMutex, does not need to be volatile.
        var loginTokenExpirationTime = -1L
            @VisibleForTesting set

        @Volatile
        var loginCanceled = false
            private set(newValue) {
                if (!newValue) {
                    loginFunctionIsRunning = false
                }
                field = newValue
            }

        //If the login function is currently running there are a few possible ways to return true.
        //1) If login function is currently running.
        //2) If login function is waiting to retry after a CONNECTION_ERROR.
        //3) If logged in and login function is.
        //Any other values mean this function 'stalled' and must be started from another object.
        @Volatile
        var loginFunctionIsRunning = false
            private set

        //Whenever the login function is currently running (loginFunctionIsRunning = true) and
        // waiting for a worker to return a value this will be set to true.
        var waitingForWorker = AtomicBoolean(false)
            private set

        //Time waitingForWorker was set to true in SystemClock.elapsedRealtime().
        @Volatile
        private var timeWaitingForWorkerStarted = -1L

        //loginHandler takes care of retrying login if a connection error or server down error was
        // received. It also handles refreshing the login token.
        //loginHandler access is meant to be protected by accountDatabaseMutex.
        //val loginHandler = Handler(Looper.getMainLooper())
        //const val loginToken = "LoginFunctionsToken"
    }

    /** Do not use transactions inside LoginFunctions functions, see accountDatabaseMutex for details; TLDR: deadlock potential  **/

    //updateLoginFunctionIsRunning() is thread safe, emit() is thread safe and setting the field is
    // thread safe because of the volatile annotation. This means that the variable getter and setter
    // are thread safe. When individual members of the class are changed updateMutex is used to protect
    // it after a reference is stored (can see examples of this in the code below).
    @Volatile
    var loginFunctionReturnValues = LoginFunctionReturnValue("")
        private set(_loginFunctionReturnValues) {
            if (_loginFunctionReturnValues.loginFunctionStatus !is LoginFunctionStatus.DoNothing) {
                updateLoginFunctionIsRunning(_loginFunctionReturnValues.loginFunctionStatus)
                CoroutineScope(ioDispatcher).launch {
                    notReceivedReentrantLock.withLock {
                        notReceivedLoginFunctionReturnValue = _loginFunctionReturnValues
                    }
                    newMutableLoginFunctionObservable.emit(_loginFunctionReturnValues)
                }
                field = _loginFunctionReturnValues.copy()
            }
        }

    //run the cancel for the login function and set states properly
    //NOTE: Because this function locks the accountDatabaseMutex mutex, do not call anything (such
    // as logout or clear all user data) from LoginFUnctions unless setting the
    // cancelLoginFunctions to 'off'.
    suspend fun cancelLoginFunction(
        callingFragmentInstanceID: String,
        updateLoginFunctionStatus: Boolean,
        abortAttemptIfRunning: Boolean
    ): Boolean {
        return accountDatabaseMutex.withPrimaryLock {
            //This abort must be checked after accountDatabaseMutex is locked, otherwise there is a situation where
            // something (ChatStreamWorker, AppActivity etc...) can call beginLoginToServerIfNotAlreadyRunning() while
            // the login is still running and it will back-up at the mutex then cancel it immediately when it
            // completed. It can get stuck in an endless loop returning INVALID_LOGIN_TOKEN because currentLoginToken
            // is set to INVALID_LOGIN_TOKEN which will cause another reconnect attempt.
            if (!abortAttemptIfRunning && loginFunctionIsRunning) {
                return@withPrimaryLock false
            } else {
                cancelLoginFunctionRequiresLock(
                    callingFragmentInstanceID,
                    updateLoginFunctionStatus
                )
                return@withPrimaryLock true
            }
        }
    }

    /** This is expected to be called with accountDatabaseMutex.primaryLock locked. **/
    private suspend fun cancelLoginFunctionRequiresLock(
        callingFragmentInstanceID: String,
        updateLoginFunctionStatus: Boolean
    ) {
        loginCanceled = true
        cancelAllLoginFunctionsWork(applicationContext)
        //loginHandler.removeCallbacksAndMessages(loginToken)

        //Only update the login status if the login function is not going to be run
        // immediately after.
        if (updateLoginFunctionStatus) {
            updateLoginFunctionStatus(
                callingFragmentInstanceID,
                LoginFunctionStatus.NoValidAccountStored(
                    false
                )
            )
        }

        currentAccountOID = ""
        currentLoginToken = INVALID_LOGIN_TOKEN
        timeLoginRequestSentToServer = -1L
        loginTokenExpirationTime = -1L
    }

    //NOTE: This function is only meant to be called by a Worker.
    suspend fun runAfterDelay(
        appClass: LetsGoApplicationClass,
        manualLoginInfo: Boolean,
        callingFragmentInstanceID: String
    ) {
        if (!manualLoginInfo) {
            lockLoginToServerFunction(callingFragmentInstanceID)
        } else {
            accountDatabaseMutex.withPrimaryLock {
                when (loadBalanceIfNecessary()) {
                    GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {
                        //This will clear the message, then dead end, user is expected to
                        // manually log in again.
                        updateLoginFunctionStatus(
                            callingFragmentInstanceID,
                            LoginFunctionStatus.Idle
                        )
                    }
                    GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                        runLoginAfterDelay(
                            LoginFunctionStatus.ConnectionError,
                            true,
                            callingFragmentInstanceID
                        )
                    }
                    GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                        //server was taken down
                        runLoginAfterDelay(
                            LoginFunctionStatus.ServerDown,
                            true,
                            callingFragmentInstanceID
                        )
                    }
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                        //NOTE: Exception was already stored here
                        appClass.loginSupportFunctions.clearAllUserDataAndStopObjects(
                            calledFromLoginFunctions = true
                        )

                        loginFunctionReturnValues =
                            LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                                callingFragmentInstanceID,
                                LoginRequest.getDefaultInstance()
                            )
                    }
                }
            }
        }
    }

    private fun enqueueWorker(workRequest: OneTimeWorkRequest) {
        //Should be set to true before the WorkManager is called. This way even in odd situations
        // (for example low initial delay), the bool will always be set before WorkManager starts.
        waitingForWorker.set(true)
        timeWaitingForWorkerStarted = SystemClock.elapsedRealtime()

        WorkManager.getInstance(applicationContext).beginUniqueWork(
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        ).enqueue()
    }

    //If manual login repeat load balancing until successful then dead end.
    //If automatic login repeat login until successful then que up another login.
    //accountDatabaseMutex expected to be locked (to protect loginHandler).
    private suspend fun runLoginAfterDelay(
        loginFunctionStatus: LoginFunctionStatus,
        manualLoginInfo: Boolean,
        callingFragmentInstanceID: String,
    ) {
        cancelAllLoginFunctionsWork(applicationContext)

        updateLoginFunctionStatus(callingFragmentInstanceID, loginFunctionStatus)

        val runLoginAfterDelayWorkRequest =
            OneTimeWorkRequestBuilder<RunLoginFunctionAfterDelayWorker>()
                .setInputData(
                    workDataOf(
                        WORKER_PARAM_CALLING_FRAGMENT_ID_KEY to callingFragmentInstanceID,
                        WORKER_PARAM_MANUAL_LOGIN_INFO_KEY to manualLoginInfo
                    )
                )
                //There may be times where setConstraints will already be fulfilled even though
                // the internet is down. The initial delay is here to prevent it from spamming.
                .setInitialDelay(TIME_BETWEEN_LOGIN_RETRIES_MS, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

        enqueueWorker(runLoginAfterDelayWorkRequest)
    }

    //NOTE: This must be called AFTER login process, it relies on a value imported during login.
    private fun runLoginBeforeTokenExpiration(
        callingFragmentInstanceID: String
    ) {
        cancelAllLoginFunctionsWork(applicationContext)

        val refreshLoginTokenWorkRequest =
            OneTimeWorkRequestBuilder<RefreshLoginFunctionTokenWorker>()
                .setInputData(workDataOf(WORKER_PARAM_CALLING_FRAGMENT_ID_KEY to callingFragmentInstanceID))
                .setInitialDelay(calculateRefreshLoginTime(), TimeUnit.MILLISECONDS)
                .build()

        enqueueWorker(refreshLoginTokenWorkRequest)
    }

    //Thread safe, loginFunctionIsRunning is volatile.
    private fun updateLoginFunctionIsRunning(loginFunctionStatus: LoginFunctionStatus) {
        loginFunctionIsRunning =
            if (loginCanceled) {
                false
            } else {
                when (loginFunctionStatus) {
                    LoginFunctionStatus.Idle,
                    is LoginFunctionStatus.NoValidAccountStored,
                    is LoginFunctionStatus.ErrorLoggingIn,
                    is LoginFunctionStatus.RequiresAuthentication,
                    is LoginFunctionStatus.VerificationOnCoolDown,
                    -> {
                        false
                    }
                    LoginFunctionStatus.AttemptingToLogin,
                    LoginFunctionStatus.ConnectionError,
                    LoginFunctionStatus.LoggedIn,
                    LoginFunctionStatus.ServerDown,
                    -> {
                        true
                    }
                    LoginFunctionStatus.DoNothing -> {
                        loginFunctionIsRunning
                    }
                }
            }
    }

    private suspend fun updateLoginFunctionStatus(
        callingFragmentInstanceID: String,
        loginFunctionStatus: LoginFunctionStatus
    ) {
        updateMutex.withLock {
            Log.i("loginFunctions_emit", Log.getStackTraceString(java.lang.Exception()))

            val newCopy = loginFunctionReturnValues.copy()

            //There is potential for loginFunctionReturnValues to be set while its members are
            // being accessed. In order to prevent that the reference is retrieved first.
            val reference = loginFunctionReturnValues
            reference.loginFunctionStatus = loginFunctionStatus
            reference.callingFragmentInstanceID = callingFragmentInstanceID

            newCopy.loginFunctionStatus = loginFunctionStatus
            newCopy.callingFragmentInstanceID = callingFragmentInstanceID
            updateLoginFunctionIsRunning(loginFunctionStatus)
            notReceivedReentrantLock.withLock {
                notReceivedLoginFunctionReturnValue = newCopy
            }
            newMutableLoginFunctionObservable.emit(newCopy)
        }
    }

    suspend fun updateLoginResponseAccessStatus(accessStatus: AccessStatusEnum.AccessStatus) {
        updateMutex.withLock {

            //There is potential for loginFunctionReturnValues to be set while its members are
            // being accessed. In order to prevent that the reference is retrieved first.
            val reference = loginFunctionReturnValues

            if (reference.response.returnStatus == LoginAccountStatus.LOGGED_IN) {

                val responseBuilder = reference.response.toBuilder()
                responseBuilder.accessStatus = accessStatus

                reference.response = responseBuilder.build()
            }
        }
    }

    suspend fun beginLoginToServerIfNotAlreadyRunning() =
        withContext(ioDispatcher) {
            if (!loginFunctionIsRunning) {

                Log.i(
                    "runLoginBeforeToke",
                    "beginLoginToServerIfNotAlreadyRunning() started"
                )

                val callingFragmentInstanceID = ""

                val canceledLoginFunction = cancelLoginFunction(
                    callingFragmentInstanceID,
                    updateLoginFunctionStatus = false,
                    abortAttemptIfRunning = false,
                )

                if (canceledLoginFunction) {
                    loginCanceled = false
                    lockLoginToServerFunction(callingFragmentInstanceID)
                }
            }
        }

    //NOTE: callingFragmentInstanceID only needs to be set if the returnee requires a fragment instance ID (only
    // LoginActivity at the time of writing this) otherwise can be set to "".
    suspend fun beginLoginToServerWhenReceivedInvalidToken(callingFragmentInstanceID: String) =
        withContext(ioDispatcher) {
            if (!loginFunctionIsRunning) {
                beginLoginToServerIfNotAlreadyRunning()
            }
            //It is possible with deep sleep or the Worker getting canceled by the system that loginFunctionIsRunning
            // is set to true. These checks will force the login to run anyway in case of errors occur.
            else if (
                loginFunctionIsRunning
                //This will make it so that the login cannot be spammed (it shouldn't happen, but just in case).
                && (SystemClock.elapsedRealtime() - timeWaitingForWorkerStarted) > (TIME_BETWEEN_LOGIN_RETRIES_MS * 1.1)
                //It is important that this comes last because of short circuiting. It should only run compareAndSet
                // if the entire statement evaluates to true. Using compareAndSet to close a 'gap' where multiple threads
                // or coroutines could execute login functions simultaneously. This 'gap' can occur when
                // beginLoginToServerWhenReceivedInvalidToken() is called by the system, then before it changes waitingForWorker,
                // a LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME Worker calls compareAndSet.
                && waitingForWorker.compareAndSet(true, false)
            ) {
                Log.i(
                    "runLoginBeforeToke",
                    "beginLoginToServerWhenReceivedInvalidToken() started loginFunctionIsRunning: $loginFunctionIsRunning"
                )

                cancelAllLoginFunctionsWork(applicationContext)
                //loginHandler.removeCallbacksAndMessages(loginToken)

                //Only update the login status if the login function is not going to be run
                // immediately after.
                updateLoginFunctionStatus(
                    callingFragmentInstanceID,
                    LoginFunctionStatus.ConnectionError
                )

                lockLoginToServerFunction(callingFragmentInstanceID)
            }
        }

    //This function does the following.
    // 1) Make sure some kind of return value is to be had.
    // 2) Instead of TryLock, uses withPrimaryLock to make sure it will continue even if the login
    //  is running (it could run into automatic login).
    // 3) If something fails don't retry the login, but if CONNECTION_ERROR or SERVER_DOWN are
    //  returned need to retry load balancing.
    suspend fun beginManualLoginToServer(
        callingFragmentInstanceID: String,
        basicLoginInfo: BasicLoginInfo? = null, //if this is not set, extract info from database
    ) = withContext(ioDispatcher) {

        if (callingFragmentInstanceID.isNotEmpty()) {
            Log.i("loginAccountType", "beginManualLoginToServer() starting")
        }

        try {

            //The manual login will need to wait for the lock to unlock so the user is guaranteed a return
            // value, so don't use tryLock.
            accountDatabaseMutex.lockPrimary()

            Log.i(
                "loginAccountType",
                "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            //loadBalanceIfNecessary() "should" be inside the lock, otherwise multiple can run simultaneously. There is a
            // specific situation where this can occur. It is on startup when the app is installed and the screen
            // of the device is off. It can try to log in from the ChatStreamWorker and the SplashScreenFragment
            // at the same time. Both load balances will run in parallel (as close as they can be, load balancing
            // itself has a lock) if loadBalanceIfNecessary() is not inside the lock.
            val loadBalancingResult = loadBalanceIfNecessary()

            Log.i(
                "loginAccountType",
                "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            if (loadBalancingResult == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
                Log.i(
                    "runLoginBeforeToke",
                    "beginManualLoginToServer() locked accountDatabaseMutex $basicLoginInfo"
                )

                loginFunctionReturnValues =
                    if (basicLoginInfo != null) { //build request
                        val request =
                            LoginRequest.newBuilder()
                                .setPhoneNumber(basicLoginInfo.phoneNumber)
                                .setAccountId(basicLoginInfo.accountID)
                                .setInstallationId(GlobalValues.installationId)
                                .setLetsGoVersion(GlobalValues.Lets_GO_Version_Number)
                                .setAccountType(basicLoginInfo.accountType)

                        cancelLoginFunctionRequiresLock(
                            callingFragmentInstanceID,
                            updateLoginFunctionStatus = false
                        )

                        loginCanceled = false

                        cancelAllLoginFunctionsWork(applicationContext)
                        //loginHandler.removeCallbacksAndMessages(loginToken)

                        Log.i(
                            "loginAccountType",
                            "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
                        )
                        setupRequestForLoginToServer(
                            request,
                            updateGlobalValues = true,
                            fromManualLogin = true,
                            callingFragmentInstanceID
                        )
                    } else { //extract request
                        beginExtractInfoFromDatabaseThenLoginToServer(
                            manualLoginInfo = true,
                            callingFragmentInstanceID
                        )
                    }

                Log.i(
                    "loginAccountType",
                    "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
                )
                Log.i(
                    "runLoginBeforeToke",
                    "beginManualLoginToServer() unlocking accountDatabaseMutex"
                )
            }

            when (loadBalancingResult) {
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {} //Handled above
                GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                    runLoginAfterDelay(
                        LoginFunctionStatus.ConnectionError,
                        true,
                        callingFragmentInstanceID
                    )
                }
                GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                    //server was taken down
                    runLoginAfterDelay(
                        LoginFunctionStatus.ServerDown,
                        true,
                        callingFragmentInstanceID
                    )
                }
                GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                    //NOTE: Exception was already stored above.
                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                        calledFromLoginFunctions = true
                    )

                    loginFunctionReturnValues =
                        LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                            callingFragmentInstanceID,
                            LoginRequest.getDefaultInstance()
                        )
                }
            }
        } finally {
            accountDatabaseMutex.unLockPrimary()
        }
    }

    //Lock mutex and begin login process.
    //NOTE: This is only meant to be called from RefreshLoginFunctionTokenWorker. Use
    // beginLoginToServerIfNotAlreadyRunning() in any other situation.
    suspend fun lockLoginToServerFunction(callingFragmentInstanceID: String) {
        withContext(ioDispatcher) {

            //Log.i("lockLoginToServerFun", Log.getStackTraceString(Throwable()))

            val result = accountDatabaseMutex.tryLock()

            //this will not allow multiple instances of this function to run at a time
            if (result) {
                Log.i(
                    "runLoginBeforeToke",
                    "lockLoginToServerFunction() tryLock success loginCanceled: $loginCanceled"
                )

                try {
                    cancelAllLoginFunctionsWork(applicationContext)
                    //loginHandler.removeCallbacksAndMessages(loginToken)

                    if (!loginCanceled) { //if login has not been cancelled
                        loginFunctionReturnValues = beginExtractInfoFromDatabaseThenLoginToServer(
                            manualLoginInfo = false,
                            callingFragmentInstanceID
                        )
                    }
                } finally {
                    Log.i("runLoginBeforeToke", "lockLoginToServerFunction() tryLock completed")
                    accountDatabaseMutex.unLockPrimary()
                }
            }
        }
    }

    //NOTE: loginMutex is expected to be locked for this.
    private suspend fun beginExtractInfoFromDatabaseThenLoginToServer(
        manualLoginInfo: Boolean,
        callingFragmentInstanceID: String,
    ): LoginFunctionReturnValue =
        withContext(ioDispatcher) {

            if (clientsIntermediate is ClientsSourceIntermediate) {
                Log.i(
                    "server_not_fake",
                    printStackTraceForErrors()
                )
            }

            //NOTE: Not putting a transaction here. This only requests the defining account info which
            // is only changed when the database is cleared (on error, delete account or logout). All of
            // these cases will attempt to call cancelLoginFunction() which will delay until login
            // has completed by the loginMutex. ALSO it would have to wrap the server call if doing
            // the entire login function in a transaction.

            val networkState = ServiceLocator.deviceIdleOrConnectionDown.deviceIdleOrConnectionDown(
                applicationContext
            )

            Log.i("testingDoze", "LoginFunctions networkState: $networkState")

            if (networkState != DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE) { //network is down

                Log.i(
                    "testingDoze",
                    "LoginFunctions is detecting device in networkState: $networkState"
                )
                runLoginAfterDelay(
                    LoginFunctionStatus.ConnectionError,
                    manualLoginInfo,
                    callingFragmentInstanceID
                )

                return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
            }

            val phoneNumber = accountInfoDataSource.getPhoneNumber()

            if (phoneNumber == null || !phoneNumber.isValidPhoneNumber()) { //if no account type set

                //no valid login saved to client
                setNoValidAccountStored(
                    callingFragmentInstanceID,
                    applicationContext as LetsGoApplicationClass
                )
                return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
            } else { //if user is logged in

                val request =
                    setupLoginRequestForLoginWithPhoneNumber(
                        phoneNumber,
                        GlobalValues.installationId,
                        GlobalValues.Lets_GO_Version_Number
                    )

                return@withContext setupRequestForLoginToServer(
                    request,
                    updateGlobalValues = true,
                    manualLoginInfo,
                    callingFragmentInstanceID,
                )
            }
        }

    //NOTE: updateGlobalValues is always true at the moment, however leaving it in for extendability.
    private suspend fun setupRequestForLoginToServer(
        loginRequest: LoginRequest.Builder,
        updateGlobalValues: Boolean,
        fromManualLogin: Boolean,
        callingFragmentInstanceID: String
    ): LoginFunctionReturnValue = withContext(ioDispatcher) {

        val userAccount = accountInfoDataSource.getAccountInfo(
            loginRequest.phoneNumber
        )

        //NOTE: Not putting a transaction here, iconsDataSource should only be modified inside
        // login anyway.

        //set timestamps for account info
        loginRequest
            .setDeviceName(GlobalValues.deviceName)
            .setApiNumber(Build.VERSION.SDK_INT)
            .setBirthdayTimestamp(userAccount?.birthdayTimestamp ?: -1)
            .setEmailTimestamp(userAccount?.emailTimestamp ?: -1)
            .setGenderTimestamp(userAccount?.genderTimestamp ?: -1)
            .setNameTimestamp(userAccount?.firstNameTimestamp ?: -1)
            .setCategoriesTimestamp(userAccount?.categoriesTimestamp ?: -1)
            .setPostLoginInfoTimestamp(userAccount?.postLoginTimestamp ?: -1)
            .addAllIconTimestamps(iconsDataSource.getAllIconTimestamps())

        return@withContext loginToServer(
            loginRequest.build(),
            updateGlobalValues,
            fromManualLogin,
            callingFragmentInstanceID
        )
    }

    //NOTE: This is wrapped in a function to bypass an unneeded warning.
    //Throws InvalidProtocolBufferException
    private fun parseToUserMatchOptionsRequest(protobufBytes: ByteArray): ReportMessages.UserMatchOptionsRequest {
        return ReportMessages.UserMatchOptionsRequest.parseFrom(
            protobufBytes
        )
    }

    //Primary login function.
    //NOTE: loginMutex is expected to be locked for this.
    private suspend fun loginToServer(
        loginRequest: LoginRequest,
        updateGlobalValues: Boolean,
        fromManualLogin: Boolean,
        callingFragmentInstanceID: String
    ): LoginFunctionReturnValue = withContext(ioDispatcher) {

        val loadBalancingResult =
            if (fromManualLogin) {
                //Manual login has already run load balancing.
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            } else {
                loadBalanceIfNecessary()
            }

        Log.i(
            "loginAccountType",
            "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
        )

        timeLoginRequestSentToServer = SystemClock.elapsedRealtime()
        val returnVal =
            when (loadBalancingResult) {
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {
                    //do login
                    clientsIntermediate.loginFunctionClientLogin(loginRequest)
                }
                GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                    runLoginAfterDelay(
                        LoginFunctionStatus.ConnectionError,
                        false,
                        callingFragmentInstanceID
                    )
                    return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
                }
                GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                    //server was taken down
                    runLoginAfterDelay(
                        LoginFunctionStatus.ServerDown,
                        false,
                        callingFragmentInstanceID,
                    )
                    return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
                }
                GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                    //NOTE: exception was already stored here
                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                        calledFromLoginFunctions = true
                    )

                    return@withContext LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                        callingFragmentInstanceID,
                        loginRequest
                    )
                }
            }

        Log.i(
            "loginAccountType",
            "beginManualLoginToServer() lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
        )

        if (returnVal.response == null) { //this means the function was running somewhere else and so returned nothing

            //do not re run the login function, only 1 should be running at a time
            val errorString = "Multiple login functions attempting to be run." +
                    loginRequest.toString()

            errorHandling.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorString,
                applicationContext
            )

            return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
        } else { //this means the function ran and returned a response

            when (returnVal.androidErrorEnum) {
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {

                    //Set timestamps as soon as possible, it is important for if say the user needs to enter a birthday
                    // when verifying their account.
                    //NOTE: The serverTimestamp should be set no matter what.
                    //NOTE: These should be updated no matter what for 'properly' set time values.
                    //NOTE: The timestamp could be slightly behind the server but it shouldn't hurt, this timestamp is used
                    // to show categories times and to check match expiration times (and a few other various things).
                    GlobalValues.serverTimestampStartTimeMilliseconds =
                        returnVal.response.serverTimestamp
                    GlobalValues.clientElapsedRealTimeStartTimeMilliseconds =
                        SystemClock.elapsedRealtime()

                    when (returnVal.response.returnStatus) {
                        LoginAccountStatus.FUNCTION_CALLED_TOO_QUICKLY,
                        LoginAccountStatus.VALUE_NOT_SET,
                        LoginAccountStatus.UNKNOWN,
                        -> {
                            Log.i("StreamWorker", "loginToServer() server error")

                            val errorString = "Invalid LoginAccountStatus from login function.\n" +
                                    "returnStatus: ${returnVal.response.returnStatus}\n" +
                                    "request: $loginRequest\n" +
                                    "returnVal: $returnVal"

                            errorHandling.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorString,
                                applicationContext
                            )

                            runLoginAfterDelay(
                                LoginFunctionStatus.ConnectionError,
                                fromManualLogin,
                                callingFragmentInstanceID,
                            )
                            return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
                        }
                        LoginAccountStatus.LG_ERROR -> {
                            //NOTE: This error has already been stored on server side.
                            (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                calledFromLoginFunctions = true
                            )

                            return@withContext LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                                callingFragmentInstanceID,
                                loginRequest,
                                returnVal.response
                            )
                        }
                        LoginAccountStatus.LOGGED_IN -> {
                            Log.i("StreamWorker", "loginToServer() LOGGED_IN")

                            //After 'LOGGED_IN' the database is the source of truth for the client
                            // side (the server is always considered correct).

                            currentAccountOID = returnVal.response.accountOid
                            currentLoginToken = returnVal.response.loginToken
                            loginTokenExpirationTime =
                                timeLoginRequestSentToServer + returnVal.response.loginValuesToReturnToClient.globalConstantValues.timeBetweenLoginTokenVerification

                            //Delete all numbers that are not part of the logged in account from
                            // the room database.
                            accountInfoDataSource.deleteAllButAccount(
                                returnVal.response.phoneNumber
                            )

                            //Get the user account information from database if present.
                            var userAccount =
                                accountInfoDataSource.getAccountInfo(
                                    returnVal.response.phoneNumber
                                )

                            val mandatoryInfoCollected: Boolean? =
                                when (returnVal.response.accessStatus) {
                                    AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO -> {
                                        false
                                    }
                                    AccessStatusEnum.AccessStatus.ACCESS_GRANTED -> {
                                        true
                                    }
                                    else -> {
                                        null
                                    }
                                }

                            if (userAccount == null) {  //phone number does not exist in room database
                                Log.i("uniqueWorkerStart", "userAccount==null")

                                //If the selected user was NOT the logged in user, clear all other
                                // user data.
                                //NOTE: This cancels all running workers so it must be called BEFORE
                                // anything is done such as starting a new worker.
                                (applicationContext as LetsGoApplicationClass)
                                    .loginSupportFunctions
                                    .clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                beginUniqueWorkIfNotRunning(applicationContext)

                                //Create brand new account.
                                val accountInfoDataEntity =
                                    AccountInfoDataEntity(
                                        returnVal.response.phoneNumber,
                                        returnVal.response.accountOid,
                                        loginRequest.accountType.number,
                                        AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY_VALUE,
                                        true,
                                        "~",
                                        -1L,
                                        "~",
                                        -1L,
                                        "~",
                                        -1L,
                                        -1,
                                        -1,
                                        -1,
                                        -1,
                                        -1L,
                                        "~",
                                        -1L,
                                        "~",
                                        "~",
                                        "~",
                                        -1,
                                        -1,
                                        -1,
                                        -1L,
                                        "~",
                                        ChatRoomSortMethodSelected.SORT_BY_UNREAD.ordinal,
                                        UserSubscriptionStatus.NO_SUBSCRIPTION.number,
                                        -1L,
                                        false
                                    )

                                userAccount = accountInfoDataEntity

                                //insert the account into the room database
                                accountInfoDataSource.insertAccount(
                                    accountInfoDataEntity
                                )

                            } else {
                                if (mandatoryInfoCollected == true) { //does NOT need more info
                                    beginUniqueWorkIfNotRunning(applicationContext)
                                }

                                accountInfoDataSource.setInfoForLoginFunction(
                                    loginRequest.accountType.number
                                )
                            }

                            var iconsJob: Job? = null
                            var iconsResult = GrpcFunctionErrorStatusEnum.NO_ERRORS

                            if (updateGlobalValues) {

                                //See GlobalValues.setPicturesBools for details, this must go before
                                // server_imported_values is set.
                                while (
                                    returnVal.response.loginValuesToReturnToClient.globalConstantValues.numberPicturesStoredPerAccount > GlobalValues.setPicturesBools.size()
                                ) {
                                    GlobalValues.setPicturesBools.add()
                                }

                                //This must be set.
                                GlobalValues.server_imported_values =
                                    returnVal.response.loginValuesToReturnToClient.globalConstantValues

                                GlobalValues.blockedAccounts.initializeBlockedAccounts(returnVal.response.blockedAccountsList)

                                accountInfoDataSource.setBlockedAccounts(GlobalValues.blockedAccounts.getMutableSet())

                                //Returns only the indexes that require updates.
                                val iconsIndexList =
                                    returnVal.response.loginValuesToReturnToClient.iconsIndexList

                                //Request server icons, these will be streamed back and handled by
                                // a callback.
                                iconsJob = CoroutineScope(ioDispatcher).launch {
                                    if (iconsIndexList.isNotEmpty()) {
                                        val iconsRequest = ServerIconsRequest.newBuilder()
                                            .setLoginInfo(getLoginInfo(returnVal.response.loginToken))
                                            .addAllIconIndex(iconsIndexList)
                                            .build()

                                        iconsResult =
                                            clientsIntermediate.requestFieldsClientRequestServerIcons(
                                                applicationContext,
                                                iconsRequest
                                            )
                                    }
                                }

                                //NOTE: These are expected to be sent back in order (so element 0
                                // will be named 0 etc).
                                val serverCategories =
                                    returnVal.response.loginValuesToReturnToClient.serverCategoriesList

                                //NOTE: These are expected to be sent back in order (so element 0
                                // will be named 0 etc).
                                val serverActivities =
                                    returnVal.response.loginValuesToReturnToClient.serverActivitiesList

                                CategoriesAndActivities.setupCategoriesAndActivities(
                                    serverCategories,
                                    serverActivities,
                                    ioDispatcher,
                                    errorHandling
                                )

                                //end of updateGlobalValues
                            }

                            //NOTE: calcPersonAge must be run AFTER timestamp is setup.
                            val calculatedAge =
                                if (
                                    returnVal.response.birthdayInfo.birthYear > 0
                                    && returnVal.response.birthdayInfo.birthMonth > 0
                                    && returnVal.response.birthdayInfo.birthDayOfMonth > 0
                                ) { //If birthday was returned from server
                                    val age = calcPersonAge(
                                        applicationContext,
                                        returnVal.response.birthdayInfo.birthYear,
                                        returnVal.response.birthdayInfo.birthMonth,
                                        returnVal.response.birthdayInfo.birthDayOfMonth,
                                        errorHandling,
                                    )

                                    if (age > GlobalValues.server_imported_values.highestAllowedAge) {
                                        GlobalValues.server_imported_values.highestAllowedAge
                                    } else {
                                        age
                                    }
                                } else if (
                                    userAccount.birthYear > 0
                                    && userAccount.birthMonth > 0
                                    && userAccount.birthDayOfMonth > 0
                                ) { //if birthday is already set up on client
                                    val age = calcPersonAge(
                                        applicationContext,
                                        userAccount.birthYear,
                                        userAccount.birthMonth,
                                        userAccount.birthDayOfMonth,
                                        errorHandling
                                    )

                                    if (age > GlobalValues.server_imported_values.highestAllowedAge) {
                                        GlobalValues.server_imported_values.highestAllowedAge
                                    } else {
                                        age
                                    }
                                } else { //if this is initial login to account
                                    -1
                                }

                            //update any relevant login info
                            val updateAccountInfo =
                                CoroutineScope(ioDispatcher).launch {
                                    accountInfoDataSource.setInfoFromRunningLogin(

                                        returnVal.response.algorithmSearchOptions,

                                        returnVal.response.birthdayInfo.birthYear,
                                        returnVal.response.birthdayInfo.birthMonth,
                                        returnVal.response.birthdayInfo.birthDayOfMonth,
                                        calculatedAge,
                                        returnVal.response.preLoginTimestamps.birthdayTimestamp,

                                        returnVal.response.emailInfo.email,
                                        returnVal.response.emailInfo.requiresEmailVerification,
                                        returnVal.response.preLoginTimestamps.emailTimestamp,

                                        returnVal.response.gender,
                                        returnVal.response.preLoginTimestamps.genderTimestamp,

                                        returnVal.response.name,
                                        returnVal.response.preLoginTimestamps.nameTimestamp,

                                        returnVal.response.postLoginInfo.userBio,
                                        returnVal.response.postLoginInfo.userCity,
                                        convertGenderRangeToString(returnVal.response.postLoginInfo.genderRangeList),
                                        returnVal.response.postLoginInfo.minAge,
                                        returnVal.response.postLoginInfo.maxAge,
                                        returnVal.response.postLoginInfo.maxDistance,
                                        returnVal.response.postLoginTimestamp,

                                        convertCategoryActivityMessageToStringWithErrorChecking(
                                            returnVal.response.categoriesArrayList
                                        ),
                                        returnVal.response.preLoginTimestamps.categoriesTimestamp,

                                        returnVal.response.subscriptionStatus,
                                        returnVal.response.subscriptionExpirationTime,

                                        returnVal.response.optedInToPromotionalEmail,
                                    )
                                }

                            val clientPictureTimestamps =
                                accountPicturesDataSource.getAllPictureTimestampsAndPaths()

                            //All user picture timestamps are returned from the server.
                            val serverPictureTimestamps = returnVal.response.picturesTimestampsList
                            var picturesResult = GrpcFunctionErrorStatusEnum.NO_ERRORS

                            if (clientPictureTimestamps.size > GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
                                //remove the difference
                                accountPicturesDataSource.deleteExcessPictures()
                            }

                            val pictureIndexes = mutableListOf<Int>()

                            for (i in serverPictureTimestamps.indices) {
                                val pictureTimestamp: Long =
                                    if (clientPictureTimestamps.size < i + 1) {
                                        -1L
                                    } else {
                                        clientPictureTimestamps[i].picture_timestamp
                                    }
                                if (serverPictureTimestamps[i] > pictureTimestamp
                                    || (serverPictureTimestamps[i] != -1L
                                            && !File(clientPictureTimestamps[i].picture_path).isImage())
                                ) { //User picture is out of date. OR
                                    // Picture exists on server and picture on client is corrupt.
                                    pictureIndexes.add(i)
                                } else if (
                                    serverPictureTimestamps[i] == -1L
                                    && pictureTimestamp != -1L
                                ) { //User picture exists on client but not on server.
                                    setPictureToDeleted(
                                        accountPicturesDataSource,
                                        i,
                                        deleteFileInterface
                                    )
                                } else if (
                                    clientPictureTimestamps.size < i + 1
                                ) { //User has less pictures stored than server.

                                    //Sets a picture to empty.
                                    accountPicturesDataSource.removeSinglePicture(i)
                                }
                            }

                            val picturesJob = CoroutineScope(ioDispatcher).launch {
                                //requestPictures gRPC function on server requires at least 1 picture index be requested
                                if (pictureIndexes.isNotEmpty()) {
                                    picturesResult = requestPicturesClient(
                                        applicationContext,
                                        clientsIntermediate,
                                        pictureIndexes
                                    )
                                }
                            }

                            iconsJob?.join()

                            if (updateGlobalValues && iconsResult == GrpcFunctionErrorStatusEnum.NO_ERRORS) {

                                //set updated icons to global vector
                                GlobalValues.allIcons = iconsDataSource.getAllIcons()

//                                val updatedList = mutableListOf<IconsDataEntity>()
//                                val allIcons = iconsDataSource.getAllIcons()
//                                for(i in allIcons.indices) {
//                                    updatedList.add(
//                                        IconsDataEntity(
//                                            i,
//                                            false,
//                                            "",
//                                            applicationContext.resources.getResourceEntryName(R.drawable.baseball),
//                                            System.currentTimeMillis(),
//                                            true
//                                        )
//                                    )
//                                }
//
//                                GlobalValues.allIcons = updatedList
                            }

                            updateAccountInfo.join()
                            picturesJob.join()

                            //Checking icon response first arbitrarily.
                            val runWithResult =
                                when {
                                    iconsResult != GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //an error occurred with icons
                                        iconsResult
                                    }
                                    picturesResult != GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //an error occurred with pictures
                                        picturesResult
                                    }
                                    else -> {
                                        GrpcFunctionErrorStatusEnum.NO_ERRORS
                                    }
                                }

                            when (runWithResult) {
                                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR -> {
                                    runLoginAfterDelay(
                                        LoginFunctionStatus.ConnectionError,
                                        fromManualLogin,
                                        callingFragmentInstanceID,
                                    )
                                    return@withContext LoginFunctionReturnValue(
                                        callingFragmentInstanceID
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE -> {

                                    //NOTE: Do not log out here because this is part of the login
                                    // function. It could create a situation where device 1 kicks
                                    // off device 2 then device 2 kicks off device 1 using log out.
                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue(
                                        LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.LOGGED_IN_ELSEWHERE),
                                        loginRequest,
                                        returnVal.response,
                                        callingFragmentInstanceID
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID -> {

                                    //NOTE: This should never be reached, this means immediately
                                    // after the login was reached, the token was expired.
                                    val errorString =
                                        "'GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID' enum value was returned.\n" +
                                                "This means immediately after the login was accomplished, the token was expired.\n" +
                                                "accountOID: ${returnVal.response.accountOid}"

                                    errorHandling.storeError(
                                        Thread.currentThread().stackTrace[2].fileName,
                                        Thread.currentThread().stackTrace[2].lineNumber,
                                        printStackTraceForErrors(),
                                        errorString,
                                        applicationContext
                                    )

                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue.loggedInErrorClearDatabase(
                                        callingFragmentInstanceID,
                                        loginRequest,
                                        returnVal.response
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO -> {
                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue.loggedInErrorClearDatabase(
                                        callingFragmentInstanceID,
                                        loginRequest,
                                        returnVal.response
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.DO_NOTHING, //should never be called here
                                GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY,
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION, //should never be called here
                                -> {
                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.runLogoutFunction(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue.loggedInErrorClearDatabase(
                                        callingFragmentInstanceID,
                                        loginRequest,
                                        returnVal.response
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                                    runLoginBeforeTokenExpiration(
                                        callingFragmentInstanceID
                                    )

                                    CoroutineScope(ioDispatcher).launch {

                                        val transactionWrapper =
                                            ServiceLocator.provideTransactionWrapper(
                                                applicationContext,
                                                DatabasesToRunTransactionIn.MESSAGES
                                            )

                                        var retrieveAllUnsentMessages =
                                            emptyList<UnsentSimpleServerCommandsDataEntity>()

                                        transactionWrapper.runTransaction {
                                            retrieveAllUnsentMessages =
                                                unsentSimpleServerCommandsDataSource.selectAll()

                                            unsentSimpleServerCommandsDataSource.clearTable()
                                        }

                                        for (unsentMessage in retrieveAllUnsentMessages) {

                                            val request =
                                                try {
                                                    parseToUserMatchOptionsRequest(unsentMessage.protobufBytes)
                                                } catch (e: InvalidProtocolBufferException) {

                                                    val errorString =
                                                        "Invalid protocol buffer found when extracting from database.\n" +
                                                                "exception: ${e.message}\n" +
                                                                "bytes: ${unsentMessage.protobufBytes}"

                                                    errorHandling.storeError(
                                                        Thread.currentThread().stackTrace[2].fileName,
                                                        Thread.currentThread().stackTrace[2].lineNumber,
                                                        printStackTraceForErrors(),
                                                        errorString,
                                                        applicationContext
                                                    )

                                                    continue
                                                }

                                            when (UnsentMessageCommandType.forNumber(unsentMessage.commandType)) {
                                                UnsentMessageCommandType.UNSET -> {

                                                    val errorString =
                                                        "Invalid UnsentMessageCommandType returned when extracting unsent message.\n" +
                                                                "request: $request\n" +
                                                                "commandType: ${
                                                                    UnsentMessageCommandType.forNumber(
                                                                        unsentMessage.commandType
                                                                    )
                                                                }\n" +
                                                                "index: ${unsentMessage.index}"

                                                    errorHandling.storeError(
                                                        Thread.currentThread().stackTrace[2].fileName,
                                                        Thread.currentThread().stackTrace[2].lineNumber,
                                                        printStackTraceForErrors(),
                                                        errorString,
                                                        applicationContext
                                                    )

                                                }
                                                UnsentMessageCommandType.USER_MATCH_OPTION -> {
                                                    //only try once
                                                    clientsIntermediate.userMatchOptionsSwipe(
                                                        request.toBuilder()
                                                            .setLoginInfo(
                                                                getLoginInfo(
                                                                    currentLoginToken
                                                                )
                                                            )
                                                            .build()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    return@withContext LoginFunctionReturnValue(
                                        LoginFunctionStatus.LoggedIn,
                                        loginRequest,
                                        returnVal.response,
                                        callingFragmentInstanceID
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.SERVER_DOWN -> {
                                    //server was taken down
                                    runLoginAfterDelay(
                                        LoginFunctionStatus.ServerDown,
                                        fromManualLogin,
                                        callingFragmentInstanceID,
                                    )

                                    return@withContext LoginFunctionReturnValue(
                                        callingFragmentInstanceID
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED -> {

                                    //NOTE: I suppose this could happen if the account was suspended
                                    // between login completing and requesting pictures or icons.

                                    //Can not log out because server log out function does not
                                    // accept it if banned/suspended so might as well not be logged
                                    // in.
                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue(
                                        LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED),
                                        loginRequest,
                                        returnVal.response,
                                        callingFragmentInstanceID,
                                    )
                                }
                                GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED -> {

                                    //NOTE: I suppose this could happen if the account was banned
                                    // between login completing and requesting pictures or icons.

                                    //Can not log out because server log out function does not
                                    // accept it if banned/suspended so might as well not be logged
                                    // in.
                                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                        calledFromLoginFunctions = true
                                    )

                                    return@withContext LoginFunctionReturnValue(
                                        LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_BANNED),
                                        loginRequest,
                                        returnVal.response,
                                        callingFragmentInstanceID,
                                    )
                                }
                            }
                        }
                        LoginAccountStatus.REQUIRES_AUTHENTICATION,
                        LoginAccountStatus.SMS_ON_COOL_DOWN,
                        -> { //account requires more info

                            if (returnVal.response.returnStatus == LoginAccountStatus.REQUIRES_AUTHENTICATION
                                && returnVal.response.accessStatus != AccessStatusEnum.AccessStatus.ACCESS_GRANTED
                            ) { //This means the account does not exist on server, remove any old data
                                (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                    calledFromLoginFunctions = true
                                )
                            }

                            return@withContext LoginFunctionReturnValue(
                                LoginFunctionStatus.RequiresAuthentication(
                                    returnVal.response.returnStatus == LoginAccountStatus.SMS_ON_COOL_DOWN
                                ),
                                loginRequest,
                                returnVal.response,
                                callingFragmentInstanceID,
                            )
                        }
                        LoginAccountStatus.OUTDATED_VERSION -> {

                            //NOTE: No reason to log out for this error, it won't fix it.
                            // The user needs to update their app.
                            return@withContext LoginFunctionReturnValue(
                                LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.OUTDATED_VERSION),
                                loginRequest,
                                returnVal.response,
                                callingFragmentInstanceID,
                            )
                        }
                        LoginAccountStatus.ACCOUNT_CLOSED -> {

                            //Can not log out because server log out function does not accept it if banned/suspended so
                            // might as well clear the database.
                            (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                calledFromLoginFunctions = true
                            )

                            if (returnVal.response.accessStatus == AccessStatusEnum.AccessStatus.SUSPENDED) {
                                return@withContext LoginFunctionReturnValue(
                                    LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED),
                                    loginRequest,
                                    returnVal.response,
                                    callingFragmentInstanceID,
                                )
                            } else {
                                return@withContext LoginFunctionReturnValue(
                                    LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_BANNED),
                                    loginRequest,
                                    returnVal.response,
                                    callingFragmentInstanceID,
                                )
                            }
                        }
                        LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT -> {

                            //NOTE: This means that no user account exists for the passed
                            // google/facebook accountID and so it requires a phone number to
                            // begin account creation.
                            (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                calledFromLoginFunctions = true
                            )

                            return@withContext LoginFunctionReturnValue(
                                LoginFunctionStatus.NoValidAccountStored(true),
                                loginRequest,
                                returnVal.response,
                                callingFragmentInstanceID,
                            )
                        }
                        LoginAccountStatus.DATABASE_DOWN -> {
                            //server was taken down
                            runLoginAfterDelay(
                                LoginFunctionStatus.ServerDown,
                                fromManualLogin,
                                callingFragmentInstanceID,
                            )
                            return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
                        }
                        LoginAccountStatus.VERIFICATION_ON_COOL_DOWN -> {
                            return@withContext LoginFunctionReturnValue(
                                LoginFunctionStatus.VerificationOnCoolDown,
                                loginRequest,
                                returnVal.response,
                                callingFragmentInstanceID
                            )
                        }
                        LoginAccountStatus.INVALID_PHONE_NUMBER_OR_ACCOUNT_ID,
                        LoginAccountStatus.INVALID_ACCOUNT_TYPE,
                        LoginAccountStatus.INVALID_INSTALLATION_ID, //handle by fragments/activity
                        LoginAccountStatus.UNRECOGNIZED, //handle by fragments/activity (this might be able to happen if .proto file is updated to a newer version?
                        null,
                        -> {

                            val errorString =
                                "'${returnVal.response.returnStatus}' enum value was returned.\n" +
                                        loginRequest.toString() +
                                        returnVal.toString()

                            errorHandling.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorString,
                                applicationContext
                            )

                            //NOTE: not sure how there errors could happen however logging out is about all I can do
                            (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                                calledFromLoginFunctions = true
                            )

                            if (returnVal.response.returnStatus == LoginAccountStatus.INVALID_INSTALLATION_ID) {
                                reGenerateInstallationId(applicationContext)
                            }

                            return@withContext LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                                callingFragmentInstanceID,
                                loginRequest,
                                returnVal.response
                            )
                        }
                    }
                }
                GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                    runLoginAfterDelay(
                        LoginFunctionStatus.ConnectionError,
                        fromManualLogin,
                        callingFragmentInstanceID,
                    )
                    return@withContext LoginFunctionReturnValue(callingFragmentInstanceID)
                }
                GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                    //server was taken down
                    runLoginAfterDelay(
                        LoginFunctionStatus.ServerDown,
                        fromManualLogin,
                        callingFragmentInstanceID,
                    )
                    return@withContext LoginFunctionReturnValue.errorServerDown(
                        callingFragmentInstanceID,
                        loginRequest
                    )
                }
                GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {

                    val errorString = "Unknown exception thrown from login function.\n" +
                            "Exception. ${returnVal.errorMessage}\n" +
                            "request: $loginRequest\n" +
                            "returnVal: $returnVal"

                    errorHandling.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorString,
                        applicationContext
                    )

                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects(
                        calledFromLoginFunctions = true
                    )

                    return@withContext LoginFunctionReturnValue.notLoggedInErrorClearDatabase(
                        callingFragmentInstanceID,
                        loginRequest
                    )
                }
            }
        }
    }

    suspend fun runRequestPicturesProtoRPC(response: GrpcClientResponse<PictureResponse>):
            GrpcFunctionErrorStatusEnum {

        val errorReturn = checkApplicationReturnStatusEnum(
            response.response.returnStatus,
            response
        )

        val returnString =
            when (errorReturn.second) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                    //NOTE: If the file is corrupt it will be set to deleted. This will allow
                    // for the LoginFunction to download it again next time it refreshes the
                    // token.
                    val pictureInfo = response.response.pictureInfo

                    val returnPicturesReturnStr = saveUserPictureToFileAndDatabase(
                        applicationContext,
                        accountPicturesDataSource,
                        pictureInfo.indexNumber,
                        pictureInfo.fileInBytes.toByteArray(),
                        pictureInfo.fileSize,
                        pictureInfo.timestampPictureLastUpdated,
                        deleteFileInterface
                    )

                    if (returnPicturesReturnStr.errorString != "~") {
                        returnPicturesReturnStr.errorString
                    } else {
                        errorReturn.first
                    }
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "DO_NOTHING was reached which should never happen.\n" +
                            "response: $response"
                }
                else -> {
                    errorReturn.first
                }
            }

        if (returnString != "~") {

            errorMessageRepositoryHelper(
                returnString,
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

        return errorReturn.second
    }

    suspend fun handleRequestIconsResponse(response: GrpcClientResponse<ServerIconsResponse>)
            : GrpcFunctionErrorStatusEnum {

        val errorReturn = checkApplicationReturnStatusEnum(
            response.response.returnStatus,
            response
        )

        val returnString =
            when (errorReturn.second) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                    try {

                        if (!response.response.isActive) { //icon is deleted

                            //old icon may not exist (for example on device first connection to server) that is completely fine
                            iconsDataSource.getSingleIcon(response.response.indexNumber.toInt())
                                ?.let { icon ->

                                    if (icon.iconIsDownloaded) {
                                        //clean up icon info
                                        deleteFileInterface.sendFileToWorkManager(
                                            icon.iconFilePath
                                        )
                                    }
                                }

                            iconsDataSource.insertOneIcon(
                                IconsDataEntity(
                                    response.response.indexNumber.toInt(),
                                    false,
                                    "",
                                    applicationContext.resources.getResourceEntryName(GlobalValues.defaultIconImageID),
                                    response.response.iconLastUpdatedTimestamp,
                                    response.response.isActive
                                )
                            )
                        } else { //icon is NOT deleted just updated in some way

                            //These paths will overwrite the old paths if they exist (each icon path is generated
                            // by index value).
                            val standardFile = generateIconFile(
                                response.response.indexNumber.toInt()
                            )
                            standardFile.writeBytes(response.response.iconInBytes.toByteArray())

                            iconsDataSource.insertOneIcon(
                                IconsDataEntity(
                                    response.response.indexNumber.toInt(),
                                    true,
                                    standardFile.path,
                                    "",
                                    response.response.iconLastUpdatedTimestamp,
                                    response.response.isActive
                                )
                            )
                        }
                    } catch (ex: IOException) {

                        val errorString =
                            "IOException from runRequestIconsProtoRPC\n" +
                                    "Index Number: ${response.response.indexNumber}\n" +
                                    "Icon Size: ${response.response.iconInBytes.size()}\n" +
                                    "Timestamp: ${response.response.iconLastUpdatedTimestamp}\n" +
                                    "Exception: ${ex.message}\n"

                        CoroutineScope(ioDispatcher).launch {
                            errorMessageRepositoryHelper(
                                errorString,
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
                    }

                    errorReturn.first
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "DO_NOTHING was reached which should never happen.\n" +
                            "response: $response"
                }
                else -> {
                    errorReturn.first
                }
            }

        if (returnString != "~") {

            errorMessageRepositoryHelper(
                returnString,
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

        return errorReturn.second
    }

    private fun generateIconFile(indexNumber: Int): File {
        val fileName =
            "${applicationContext.getString(R.string.icon_file_name_prefix)}_${indexNumber}_"
        return File(applicationContext.filesDir, fileName)
    }

    private suspend fun setNoValidAccountStored(
        callingFragmentInstanceID: String,
        letsGoApplicationClass: LetsGoApplicationClass
    ) {
        updateLoginFunctionStatus(
            callingFragmentInstanceID,
            LoginFunctionStatus.NoValidAccountStored(
                false
            )
        )

        letsGoApplicationClass.loginSupportFunctions.clearAllUserDataAndStopObjects(
            calledFromLoginFunctions = true
        )
    }

    //This function will NOT run a full load balance if LoginFunctions recently ran one. Instead it
    // will simple attempt to ping the current channel. If it fails it will run load balancing anyways.
    // If it succeeds it will continue.
    //NOTE: This function should be called when accountDatabaseMutex.lockPrimary() is locked.
    private suspend fun loadBalanceIfNecessary(): GrpcAndroidSideErrorsEnum {

        return if (nextTimeLoadBalancingAllowed <= SystemClock.elapsedRealtime()) {
            return runLoginLoadBalancing()
        } else { //login 'recently' completed load balancing

            val response = runWithShortRPCManagedChannel { channel ->
                clientsIntermediate.retrieveServerLoadInfo(
                    channel,
                    false
                )
            }

            if (response.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                && response.response.acceptingConnections
            ) {
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            } else {
                runLoginLoadBalancing()
            }
        }
    }

    private suspend fun runLoginLoadBalancing(): GrpcAndroidSideErrorsEnum {
        val result = GlobalValues.runLoadBalancing(
            clientsIntermediate,
            true,
            errorHandling
        )

        nextTimeLoadBalancingAllowed =
            if (result == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
                //set login to 'recently' ran load balancing
                SystemClock.elapsedRealtime() + calculateRefreshLoadBalanceTime()
            } else {
                -1L
            }

        return result
    }

}

data class LoginFunctionReturnValue(
    var loginFunctionStatus: LoginFunctionStatus,
    val request: LoginRequest,
    var response: LoginResponse,
    var callingFragmentInstanceID: String
) {

    constructor(callingFragmentInstanceID: String) : this(
        LoginFunctionStatus.DoNothing,
        LoginRequest.getDefaultInstance(),
        LoginResponse.getDefaultInstance(),
        callingFragmentInstanceID
    )

    /** This is done because the MutableStateFlow is 'Conflated' by nature, and so it will not return the same
     * value twice (compared by equals). Therefore if equals is always false it will always be emitted.*/
    //NOTE: Equals overrides both '==' and '!=' so for null checks and the like need to use 'let' or '!=='.
    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        var result = loginFunctionStatus.hashCode()
        result = 31 * result + request.hashCode()
        result = 31 * result + response.hashCode()
        return result
    }

    companion object {
        fun notLoggedInErrorClearDatabase(
            _callingFragmentInstanceID: String,
            loginRequest: LoginRequest,
            loginResponse: LoginResponse = LoginResponse.getDefaultInstance(),
        ): LoginFunctionReturnValue {
            return LoginFunctionReturnValue(
                LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.UNMANAGEABLE_ERROR),
                loginRequest,
                loginResponse,
                _callingFragmentInstanceID
            )
        }

        fun loggedInErrorClearDatabase(
            _callingFragmentInstanceID: String,
            loginRequest: LoginRequest,
            loginResponse: LoginResponse = LoginResponse.getDefaultInstance(),
        ): LoginFunctionReturnValue {
            return LoginFunctionReturnValue(
                LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.UNMANAGEABLE_ERROR),
                loginRequest,
                loginResponse,
                _callingFragmentInstanceID
            )
        }

        fun errorServerDown(
            _callingFragmentInstanceID: String,
            loginRequest: LoginRequest,
            loginResponse: LoginResponse = LoginResponse.getDefaultInstance(),
        ): LoginFunctionReturnValue {
            return LoginFunctionReturnValue(
                LoginFunctionStatus.ServerDown,
                loginRequest,
                loginResponse,
                _callingFragmentInstanceID
            )
        }
    }
}

