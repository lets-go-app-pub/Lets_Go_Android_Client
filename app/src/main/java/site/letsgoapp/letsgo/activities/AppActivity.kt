package site.letsgoapp.letsgo.activities

import account_state.AccountState
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.MapView
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import report_enums.ReportMessages
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.ChatRoomFragment
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databinding.ActivityAppBinding
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.globalAccess.ServiceLocator.provideSharedApplicationViewModelFactory
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import java.util.*
import kotlin.collections.set


class AppActivity : AppCompatActivity() {

    private val applicationViewModel: SharedApplicationViewModel by viewModels()

    var sharedApplicationViewModelInstanceId = ""
        private set

    private lateinit var grpcFunctionErrorStatusEnumToActivityObserver: Observer<EventWrapperWithKeyString<GrpcFunctionErrorStatusReturnValues>>
    private lateinit var locationRequestedObserver: Observer<EventWrapper<TypeOfLocationUpdate>>
    private lateinit var serverReturnedInvalidParameterErrorObserver: Observer<EventWrapper<String>>
    private lateinit var modifyingValueFailedErrorObserver: Observer<EventWrapper<String>>

    private lateinit var logoutFunctionCompletedObserver: Observer<EventWrapper<Unit>>
    private lateinit var deleteFunctionCompletedObserver: Observer<EventWrapper<GrpcFunctionErrorStatusEnum>>
    private lateinit var clearAllUserDataAndStopObjectsCompletedObserver: Observer<EventWrapper<Unit>>
    private lateinit var loginFunctionDataObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>
    private lateinit var allChatRoomMessagesHaveBeenObservedResults: Observer<EventWrapper<AllChatRoomMessagesHaveBeenObservedHolder>>

    private lateinit var displayToastFromActivityObserver: Observer<EventWrapper<String>>

    private lateinit var bottomNavigationView: BottomNavigationView

    //these 3 variables are used to change the messenger fragment icon in the bottom navigation menu
    private lateinit var bottomNavigationMenuMessengerMenuItem: MenuItem

    var setupActivityMenuBars: SetupActivityMenuBars? = null
        private set

    //chat room lists top menu with join chat room, new chat room etc...
    private var chatRoomFragmentPopupMenu: PopupMenu? = null

    //pop up over an individual message, allows for delete, reply etc...
    val chatRoomActiveMessagePopupMenu = ChatRoomActiveMessagePopupMenu(this)

    //user options pop up menu, allows for things like block & report, kick, ban etc...
    val adminUserOptionsFragmentPopupMenu = AdminUserOptionsFragmentPopupMenu(this)

    //location screen fragment, allows to change map type fragment thingy
    private var locationScreenFragmentLayersPopupMenu: PopupMenu? = null

    //navigate to Login Activity
    private val navigateToMainActivity = {

        setLoadingDialogState(enableLoading = false)

        //clear the handler so it doesn't try to run stuff
        applicationViewModel.clearHandlers()
        applicationViewModel.clearFindMatchesVariables()
        connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)

        GlobalValues.loginToChatRoomId = ""

        val mainActivityIntent = Intent(this, LoginActivity::class.java)
        mainActivityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(mainActivityIntent)

        this.finish()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoCoder: Geocoder
    private var locationDeniedSnackBar: Snackbar? = null

    private lateinit var errorTextView: TextView

    var currentChatRoomFragmentInstanceID = "~"

    private val _finishedChatRoomLocationRequest =
        MutableLiveData<EventWrapperWithKeyString<ChatRoomLocationRequestReturn>>()
    val finishedChatRoomLocationRequest: LiveData<EventWrapperWithKeyString<ChatRoomLocationRequestReturn>> =
        _finishedChatRoomLocationRequest

    private var loadingDialogFragment: LoadingDialogFragment? = null
    private var loadingDialogFragmentExpirationTimer: Timer? = null

    //controls whether certain messages are shown when logging out, deleting account or clearing database
    private var backToMainDueToError = false

    private val connectionErrorHandler = Handler(Looper.getMainLooper())
    private val connectionErrorToken = "Conn_Err_Token"

    private var _binding: ActivityAppBinding? = null
    private val binding get() = _binding!!

    //used by fragments, nullable so they can tell if the activity is being inflated still
    val fragmentBinding get() = _binding

    //Run at the end of onCreate(), used by fragments when they need to access something relating to fragmentBinding
    // because it could NOT be initialized if the Activity needs to restart.
    private val onCreateLambda = mutableMapOf<String, () -> Unit>()

    private lateinit var navController: NavController

    private val cancellationTokenSource = CancellationTokenSource()

    private var requestLocationForAlgorithm: ActivityResultLauncher<Intent>? =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            //Don't need to check for result.resultCode == Activity.RESULT_OK, simply need to check if
            // location is enabled yet and react accordingly.
            algorithmRequestResult()
        }

    private inner class RegisterForRequestLocation {
        private lateinit var globalTypeOfLocationUpdate: TypeOfLocationUpdate
        private val requestLocation =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult())
            { _ ->
                //result.resultCode == Activity.RESULT_OK does NOT need to be checked here, this is because
                // locationRequestResult needs to run either way AND if the user simply navigates back to this
                // without enabling locating, then this will not have Activity.RESULT_OK
                locationRequestResult(globalTypeOfLocationUpdate)
            }

        fun runRegisterForRequestLocationActivityResult(typeOfLocationUpdate: TypeOfLocationUpdate) {
            globalTypeOfLocationUpdate = typeOfLocationUpdate
            requestLocation.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    //Overloaded to participate in passing the view model to fragments
    // for testing.
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = provideSharedApplicationViewModelFactory(applicationContext)

    private inner class RegisterForRequestPostNotifications {

        @Volatile
        private var runOnRegisterForActivityResult: (() -> Unit)? = null

        private val requestPostNotifications =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->

                val lambdaCopy = runOnRegisterForActivityResult
                runOnRegisterForActivityResult = null
                lambdaCopy?.let {
                    it()
                }
            }

        fun runRegisterForRequestPostNotificationActivityResult(runOnReturn: () -> Unit) {
            if (
                !NotificationManagerCompat.from(GlobalValues.applicationContext)
                    .areNotificationsEnabled()
                && Build.VERSION.SDK_INT > 32
            ) { //Notifications are not enabled.
                runOnRegisterForActivityResult = runOnReturn
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                runOnReturn()
            }
        }
    }

    private var registerForRequestPostNotifications: RegisterForRequestPostNotifications? =
        RegisterForRequestPostNotifications()

    private var registerForRequestLocation: RegisterForRequestLocation? =
        RegisterForRequestLocation()

    private var runOnStart: () -> Unit = {}

    var clientsIntermediate: ClientsInterface = ClientsSourceIntermediate()
        @VisibleForTesting set

    private val errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private val ioDispatcher = ServiceLocator.globalIODispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("whereAmI", "AppActivity")

        sharedApplicationViewModelInstanceId =
            applicationViewModel.thisSharedApplicationViewModelInstanceId

        Log.i("ApplicationActStuff", "started inflating binding")
        /** inflating and setContentView() runs onCreateView for the BlankLoadingFragment.kt and so it needs to run AFTER the applicationViewModel
         * is initialized **/
        try {
            _binding = ActivityAppBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            val errorMessage =
                "Failed to inflate AppActivity fragment.\n" +
                        "exception: ${e.localizedMessage}\n" +
                        "exception: ${e.message}\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                e.stackTraceToString(),
                errorMessage
            )

            throw e
        }

        setContentView(binding.root)

        Log.i("ApplicationActStuff", "finished inflating binding")

        Log.i("sendMessageThing", "currentAccountOID: ${LoginFunctions.currentAccountOID}")

        backToMainDueToError = false

        errorTextView =
            binding.activityAppErrorTextView
        bottomNavigationView =
            binding.applicationBottomNavigationView
        bottomNavigationMenuMessengerMenuItem =
            bottomNavigationView.menu.findItem(R.id.messengerScreenFragment)
        navController = findNavController(R.id.navigationHostFragment)

        bottomNavigationView.setupWithNavController(navController)

        bottomNavigationView.setOnItemSelectedListener { item ->

            //NOTE: Something is odd about when NavOptions are passed to navigate() and setPopUpTo() is used. They
            // don't seem to work the same way the nav graph does. When onNavDestinationSelected() (found inside
            // setupWithNavController()) is modified so that the setPopUpTo() takes in the matchScreenFragment,
            // the problem can be seen. If the navigation follows the path of {matchScreenFragment->matchMadeScreenFragment->
            // chatRoomFragment->(using unMatch)messengerScreenFragment} then the bottom bar will stop working and be
            // unable to navigate to the matchScreenFragment.
            // In lieu of this, if any options are desired just modify the below block or the application_nav_host to get
            // the desired result.
            try {
                when (navController.currentDestination?.id) {
                    R.id.profileScreenFragment -> {
                        if (item.itemId == R.id.matchScreenFragment) {
                            navController.navigate(R.id.action_profileScreenFragment_to_matchScreenFragment)
                        } else if (item.itemId == R.id.messengerScreenFragment) {
                            navController.navigate(R.id.action_profileScreenFragment_to_messengerScreenFragment)
                        }
                    }
                    R.id.matchScreenFragment -> {
                        if (item.itemId == R.id.profileScreenFragment) {
                            navController.navigate(R.id.action_matchScreenFragment_to_profileScreenFragment)
                        } else if (item.itemId == R.id.messengerScreenFragment) {
                            navController.navigate(R.id.action_matchScreenFragment_to_messengerScreenFragment)
                        }
                    }
                    R.id.messengerScreenFragment -> {
                        if (item.itemId == R.id.profileScreenFragment) {
                            navController.navigate(R.id.action_messengerScreenFragment_to_profileScreenFragment)
                        } else if (item.itemId == R.id.matchScreenFragment) {
                            navController.navigate(R.id.action_messengerScreenFragment_to_matchScreenFragment)
                        }
                    }
                }

                // Return true only if the destination we've navigated to matches the MenuItem
                navController.currentDestination?.matchDestination(item.itemId) == true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        //Will initialize itself on construction, requires that the binding has been inflated.
        setupActivityMenuBars = SetupActivityMenuBars()

        logoutFunctionCompletedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleLogoutFromAccountReturnValue()
            }
        }

        (applicationContext as LetsGoApplicationClass).loginSupportFunctions.logoutFunctionCompleted.observe(
            this,
            logoutFunctionCompletedObserver
        )

        deleteFunctionCompletedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleDeleteAccountReturnValue(it)
            }
        }

        (applicationContext as LetsGoApplicationClass).loginSupportFunctions.deleteFunctionCompleted.observe(
            this,
            deleteFunctionCompletedObserver
        )

        clearAllUserDataAndStopObjectsCompletedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleLogoutFromAccountReturnValue()
            }
        }

        (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjectsCompleted.observe(
            this,
            clearAllUserDataAndStopObjectsCompletedObserver
        )

        loginFunctionDataObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(applicationViewModel.thisSharedApplicationViewModelInstanceId)
            result?.let {
                handleLoginFunctionData(it)
            }
        }

        applicationViewModel.loginFunctionData.observe(
            this,
            loginFunctionDataObserver
        )

        grpcFunctionErrorStatusEnumToActivityObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(applicationViewModel.thisSharedApplicationViewModelInstanceId)

            result?.let {
                handleGrpcFunctionError(it)
            }
        }

        applicationViewModel.returnGrpcFunctionErrorStatusEnumToActivity.observe(
            this,
            grpcFunctionErrorStatusEnumToActivityObserver
        )

        locationRequestedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                if (it == TypeOfLocationUpdate.ALGORITHM_REQUEST) {

                    //The fusedLocationClient can not run in the background, which matches up nicely with liveData which will
                    // not run until onStart() is called. However, because findMatchesClientRunning can not be set to true
                    // longer than MAXIMUM_TIME_OBJECT_CAN_BE_LOCKED, it means that findMatchesClientRunning can not be set
                    // until fusedLocationClient is ready to run. So findMatchesClientRunning is set here instead of inside
                    // FindMatchesObject.
                    //NOTE: The liveData will only return the most recent value and so only one of these will ever be attempted.
                    if (applicationViewModel.findMatchesObject.findMatchesClientRunning.compareAndSet(
                            expect = false,
                            update = true
                        )
                    ) {
                        getCurrentLocation(it)
                    }
                } else {
                    getCurrentLocation(it)
                }
            }
        }

        applicationViewModel.findMatchesObject.locationRequested.observe(
            this,
            locationRequestedObserver
        )

        serverReturnedInvalidParameterErrorObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()

            result?.let {
                serverReturnedInvalidParameterError(it)
            }
        }

        applicationViewModel.serverReturnedInvalidParameterError.observe(
            this,
            serverReturnedInvalidParameterErrorObserver
        )

        modifyingValueFailedErrorObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                modifyingValueFailed(it)
            }
        }

        applicationViewModel.modifyingValueFailedError.observe(
            this,
            modifyingValueFailedErrorObserver
        )

        allChatRoomMessagesHaveBeenObservedResults = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                updateBottomMenuForNewMessage(it)
            }
        }

        applicationViewModel.allChatRoomMessagesHaveBeenObservedResults.observe(
            this,
            allChatRoomMessagesHaveBeenObservedResults
        )

        displayToastFromActivityObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            //R.string.promote_new_admin_failed
            result?.let {
                displayToast(it)
            }
        }

        applicationViewModel.displayToastFromActivity.observe(
            this,
            displayToastFromActivityObserver
        )

        //Location stuff
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geoCoder = Geocoder(this, Locale.getDefault())

        //clear some variables in case this navigated back to the login fragment at some point
        applicationViewModel.clearFindMatchesVariables()

        // Fixing Later Map loading Delay
        CoroutineScope(ioDispatcher).launch {
            try {
                //Must pass applicationContext here not activityContext, otherwise a leak
                // can occur. See where the MapView inside ChatMessageAdapter.kt is set
                // up for more info.
                val mv = MapView(
                    GlobalValues.applicationContext
                )
                mv.onCreate(null)
                mv.onPause()
                mv.onDestroy()
            } catch (ignored: Exception) {
            }
        }

        //leave this as the last call in onCreate()
        onCreateFinishing()
    }

    private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
        hierarchy.any { it.id == destId }

    //adds a lambda to onCreateLambda, see onCreateLambda for more details
    fun addLambdaToBeCalledAtEndOfOnCreate(key: String, lambda: () -> Unit) {
        onCreateLambda[key] = lambda
    }

    //removes a lambda to onCreateLambda, see onCreateLambda for more details
    //NOTE: This was added as a failsafe, the lambdas should not be added unless
    // they are needed, then they should be immediately removed.
    fun removeLambdaCalledAtEndOfOnCreate(key: String) {
        onCreateLambda.remove(key)
    }

    //intended to be called as the last function on onCreate()
    //see onCreateLambda for more details
    private fun onCreateFinishing() {
        for (lambda in onCreateLambda.values) {
            lambda()
        }
        onCreateLambda.clear()
    }

    override fun onDestroy() {

        backToMainDueToError = false
        _binding = null
        setupActivityMenuBars = null

        requestLocationForAlgorithm = null
        registerForRequestLocation = null
        registerForRequestPostNotifications = null

        clearLoadingDialog()
        cancelAndNullTimer()
        connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)
        super.onDestroy()
        Log.i("objectDestroyed", "AppActivity")
    }

    //TESTING_NOTE: run this function along with handleLoginFunctionData() and make sure they place nice together
    fun handleGrpcFunctionError(returnValue: GrpcFunctionErrorStatusEnum) {
        handleGrpcFunctionError(
            GrpcFunctionErrorStatusReturnValues(
                returnValue,
                turnOffLoading = false
            )
        )
    }

    //TESTING_NOTE: run this function along with handleLoginFunctionData() and make sure they place nice together
    private fun handleGrpcFunctionError(returnValue: GrpcFunctionErrorStatusReturnValues) {

        val error = returnValue.errorStatus

        if (returnValue.turnOffLoading) {
            setLoadingDialogState(false)
        }

        Log.i(
            "gRPCErrorOccurred",
            "gRPCErrorOccurred() inside AppActivity error: $error\n${Log.getStackTraceString(java.lang.Exception())}"
        )

        when (error) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                //if CONNECTION_ERROR or SERVER_DOWN were rotating through, remove them
                connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)
                displayErrorMessage(null)
            }
            GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
            }
            GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED,
            GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED,
            -> {

                //The information dialogs will be run by the LoginActivity after navigating back (it
                // will run LoginFunction and receive the account state)
                //This means that this is not consistent with LoginActivity because otherwise it will
                // not be able to re-attempt login and get the suspended/banned dialog for the user to
                // see.
                navigateToMainActivity()
            }
            GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE -> {

                backToMainDueToError = true
                applicationViewModel.clearAllUserDataAndStopObjects(
                    returnLiveData = false,
                    updateLoginFunctionStatus = false
                )

                //NOTE: this does not wait for a reply that the data has been cleared
                val alertDialog =
                    ErrorAlertDialogFragment(
                        getString(R.string.logged_in_elsewhere_title),
                        getString(R.string.logged_in_elsewhere_body)
                    ) { _, _ ->
                        navigateToMainActivity()
                    }
                alertDialog.isCancelable = false
                alertDialog.show(supportFragmentManager, "fragment_alert")
            }
            GrpcFunctionErrorStatusEnum.SERVER_DOWN,
            GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
            -> {

                //NOTE: the LoginFunctions will constantly retry, however there CONNECTION_ERROR message
                // here will not do that, so that's what this lovely little handler function is for
                runCheckConnectionStatus(
                    this,
                    clientsIntermediate,
                    errorStore,
                    error,
                    connectionErrorHandler,
                    connectionErrorToken,
                    ::handleGrpcFunctionError,
                    ::displayErrorMessage
                )
            }
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID -> {

                //NOTE: There is a situation where the login token can expire while the message is set
                // to 'server is temporarily down' (servers are down for example). And without this condition
                // it will 'pulse' between 'connection error' and 'server is temporarily down'.
                if (errorTextView.visibility != View.VISIBLE || errorTextView.text != resources.getString(
                        R.string.server_down_error
                    )
                ) {
                    displayErrorMessage(resources.getString(R.string.general_login_token_expired))
                }

                //NOTE: if LoginFunctionStatus.LoggedIn is returned by login then it will
                // start the chat stream as well
                applicationViewModel.beginLoginToServerWhenReceivedInvalidToken()
            }
            GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.function_called_too_quickly_error,
                    Toast.LENGTH_LONG
                ).show()
            }
            GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO -> {
                Log.i("unmanageableErr", Log.getStackTraceString(java.lang.Exception()))

                clearDatabaseInfoAndBackToMain()
            }
            GrpcFunctionErrorStatusEnum.LOG_USER_OUT -> {
                Log.i("unmanageableErr", Log.getStackTraceString(java.lang.Exception()))

                logUserOutAndBackToMainWithError(R.string.unmanageable_error_occurred)
            }
            GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION -> {
                //TODO: will need to hide anything that is subscription related
            }
        }
    }

    private fun handleLoginFunctionData(loginResponse: LoginFunctionReturnValue) {

        Log.i(
            "AppActivityLoginResp",
            "loginResponse.loginFunctionStatus: ${loginResponse.loginFunctionStatus}"
        )

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.DoNothing,
            is LoginFunctionStatus.AttemptingToLogin,
            -> {
            }
            is LoginFunctionStatus.ConnectionError -> {
                displayErrorMessage(resources.getString(R.string.general_connection_error))
            }
            is LoginFunctionStatus.ServerDown -> {
                displayErrorMessage(resources.getString(R.string.server_down_error))
            }
            is LoginFunctionStatus.ErrorLoggingIn -> {
                //NOTE: Logging out or clearing database as well as storing any
                // relevant errors will be handled by the LoginFunctions class.
                when ((loginResponse.loginFunctionStatus as LoginFunctionStatus.ErrorLoggingIn).errorEnum) {
                    LoginErrorsEnum.UNMANAGEABLE_ERROR -> {
                        runUnmanageableErrorDialogWithoutLoggingOut()
                    }
                    LoginErrorsEnum.LOGGED_IN_ELSEWHERE, //NOTE: only called if requesting icons returns it
                    LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED,
                    LoginErrorsEnum.ACCOUNT_CLOSED_BANNED,
                    LoginErrorsEnum.OUTDATED_VERSION -> {
                        //The information dialogs will be run by the LoginActivity after navigating back
                        navigateToMainActivity()
                    }
                }
            }
            is LoginFunctionStatus.Idle -> {
                displayErrorMessage(null)
            }
            is LoginFunctionStatus.LoggedIn -> {
                //if CONNECTION_ERROR or SERVER_DOWN were rotating through, remove them
                connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)

                displayErrorMessage(null)

                //the ChatStreamObject will stop if it attempts to connect and LOGIN_TOKEN_EXPIRED_OR_INVALID is
                // returned, calling this function will make sure it is started whenever login happens
                applicationViewModel.startChatStreamIfNotRunning()
            }
            is LoginFunctionStatus.NoValidAccountStored -> {
                //NoValidAccountStored WILL be sent back when logging out or deleting the account,
                // so if one of these was initiated, don't show an error
                if (backToMainDueToError) {
                    //this depends on the fragment
                    Log.i("NoValidAccountStored", "runtime error")
                    runUnmanageableErrorDialogWithoutLoggingOut()
                }
            }
            is LoginFunctionStatus.VerificationOnCoolDown,
            is LoginFunctionStatus.RequiresAuthentication -> {
                if (loginResponse.loginFunctionStatus is LoginFunctionStatus.VerificationOnCoolDown) {
                    val errorMessage =
                        "VerificationOnCoolDown was returned during AppActivity. This should never happen in " +
                                "AppActivity because it is a response to the user attempting to get an sms verification code.\n" +
                                "loginResponse: ${loginResponse}\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )
                }

                logUserOutAndBackToMainWithError(R.string.account_authorization_required)
            }
        }
    }

    private fun updateBottomMenuForNewMessage(info: AllChatRoomMessagesHaveBeenObservedHolder) {

        Log.i(
            "messagesStuff",
            "Stored.allMsgObserved: ${applicationViewModel.mostRecentUpdatesMessage.allMessagesHaveBeenObserved} Stored.mostRecentMessageUUID: ${info.mostRecentMessageUUID} Stored.storeTime: ${applicationViewModel.mostRecentUpdatesMessage.mostRecentlyUpdatedMessageTimeStored} New.allMsgObserved: ${info.allMessagesHaveBeenObserved} New.storeTime: ${info.mostRecentMessageTimestamp}"
        )

        //don't want to run updates for older observed messages because these can run on different coRoutines
        // so for example a new message could be received BEFORE a response from
        // checkIfAllChatRoomMessagesHaveBeenObserved() even though the new message is fresh
        //<= instead of < is important if no updates have been received because if a user clicks a chat room
        // and observes the message then goes back to the messenger screen no new messages were received
        // and so the most recently viewed message will not have changed, however all messages have now
        // been viewed
        //This update can cause flashing on the UI to occur. Trying not to run it unless necessary.
        if (
            //this will be an update to the most recent message
            (applicationViewModel.mostRecentUpdatesMessage.mostRecentlyUpdatedMessageTimeStored <= info.mostRecentMessageTimestamp
                    && applicationViewModel.mostRecentUpdatesMessage.allMessagesHaveBeenObserved != info.allMessagesHaveBeenObserved)
            ||
            //this was called from when user was kicked/banned/left a chat room AND the most
            // recent message was a message from this chat room
            (info.leftChatRoomId != SharedApplicationViewModel.INVALID_CHAT_ROOM_ID
                    && info.leftChatRoomId == applicationViewModel.mostRecentUpdatesMessage.mostRecentlyUpdatedMessageChatRoomId)
            ||
            //account that previously most recent message is from is now blocked
            GlobalValues.blockedAccounts[info.mostRecentMessageSentByOID]
        ) {
            applicationViewModel.updateMostRecentUpdatesMessage(
                info.mostRecentMessageTimestamp,
                info.mostRecentMessageChatRoomId,
                info.mostRecentMessageUUID,
                info.mostRecentMessageSentByOID,
                info.allMessagesHaveBeenObserved
            )
            if (info.allMessagesHaveBeenObserved) {
                Log.i("messagesStuff", "NO_new_messages")
                bottomNavigationMenuMessengerMenuItem.setIcon(R.drawable.inset_no_message_menu_item)
            } else {
                Log.i("messagesStuff", "new_messages")
                bottomNavigationMenuMessengerMenuItem.setIcon(R.drawable.inset_new_message_menu_item)
            }
        }
    }

    private fun displayToast(text: String) {
        Toast.makeText(
            GlobalValues.applicationContext,
            text,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun runUnmanageableErrorDialogWithoutLoggingOut() {

        Log.i("unmanageableError", Log.getStackTraceString(Exception()))

        val alertDialog =
            ErrorAlertDialogFragment(
                getString(R.string.error_dialog_title),
                getString(R.string.error_dialog_body)
            ) { _, _ ->

                //don't need to log out here, if it gets this message then the login helper will auto log out on the server
                navigateToMainActivity()
            }
        alertDialog.isCancelable = false
        alertDialog.show(supportFragmentManager, "fragment_alert")
    }

    private fun clearDatabaseInfoAndBackToMain() {
        backToMainDueToError = true
        applicationViewModel.clearAllUserDataAndStopObjects(returnLiveData = true)

        setLoadingDialogState(true)

        Log.i("unmanageableErr", Log.getStackTraceString(java.lang.Exception()))

        Toast.makeText(
            GlobalValues.applicationContext,
            getString(R.string.unmanageable_error_occurred),
            Toast.LENGTH_LONG
        ).show()
    }

    fun deleteAccountInitialization() {
        backToMainDueToError = false
        setLoadingDialogState(enableLoading = true)
    }

    fun logUserOutAndBackToMainWithoutError() {
        backToMainDueToError = false
        //userManuallyNavigatingToMainActivity = true
        logUserOutAndBackToMain()
    }

    private fun logUserOutAndBackToMainWithError(stringID: Int) {
        backToMainDueToError = true

        logUserOutAndBackToMain()

        Toast.makeText(
            GlobalValues.applicationContext,
            getString(stringID),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun logUserOutAndBackToMain() {
        applicationViewModel.logoutAccount()
        setLoadingDialogState(true)
    }

    private fun displayErrorMessage(errorMessage: String?) {
        if (errorMessage == null || errorMessage == "") {
            errorTextView.visibility = View.GONE
            errorTextView.text = null
        } else {
            errorTextView.text = errorMessage
            errorTextView.visibility = View.VISIBLE
        }
    }

    private fun handleLogoutFromAccountReturnValue() {

        if (!backToMainDueToError) {
            Toast.makeText(
                GlobalValues.applicationContext,
                getString(R.string.modify_profile_screen_successfully_logged_out_toast),
                Toast.LENGTH_LONG
            ).show()
        }

        navigateToMainActivity()
    }

    private fun handleDeleteAccountReturnValue(errorStatusEnum: GrpcFunctionErrorStatusEnum) {

        when (errorStatusEnum) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    getString(R.string.modify_profile_screen_successfully_deleted_account_toast),
                    Toast.LENGTH_LONG
                ).show()
                navigateToMainActivity()
            }
            else -> {
                setLoadingDialogState(enableLoading = false)
                backToMainDueToError = true
                handleGrpcFunctionError(errorStatusEnum)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp()
    }

    fun navigateUp() {
        navController.navigateUp()
    }

    //This takes ids defined inside 'application_nav_host.xml'
    //R.id.blankLoadingFragment
    //R.id.matchScreenFragment
    //R.id.matchMadeScreenFragment
    //R.id.profileScreenFragment
    //R.id.selectCategoriesFragment2
    //R.id.modifyProfileScreenFragment
    //R.id.messengerScreenFragment
    //R.id.chatRoomFragment
    //R.id.selectLocationScreen
    //R.id.chatRoomInfoFragment
    //R.id.selectChatRoomForInviteFragment
    //R.id.chatRoomMemberFragment
    //NOTE: this is meant to be called on the Main thread
    fun navigate(currentFragment: Int, destinationAction: Int) {
        when {
            navController.currentDestination?.id != currentFragment -> {
                val errorMessage =
                    "Attempted to navigate while currentFragment was set to an incorrect destination.\n" +
                            "navController.currentDestination?.id: ${navController.currentDestination?.id}\n" +
                            "currentFragment: $currentFragment\n" +
                            "destinationAction: $destinationAction\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
            this.lifecycle.currentState > Lifecycle.State.CREATED -> { //if the state is Started or Resumed
                navController.navigate(destinationAction)
            }
            //NOTE: This case is needed if the BlankLoadingFragment is stopped before it completes the initial loading.
            else -> { //if the state is lower than started or resumed, run the navigation when the fragment reaches onStart()
                runOnStart = {
                    navController.navigate(destinationAction)
                }
            }
        }
    }

    //This takes ids defined inside 'application_nav_host.xml'
    //NOTE: this is meant to be called on the Main thread
    fun navigate(currentFragment: Int, directions: Int, arguments: Bundle?) {
        if (navController.currentDestination?.id == currentFragment) {
            navController.navigate(directions, arguments)
        } else {
            val errorMessage =
                "Attempted to navigate while currentFragment was set to an incorrect destination.\n" +
                        "navController.currentDestination?.id: ${navController.currentDestination?.id}\n" +
                        "currentFragment: $currentFragment\n" +
                        "directions: $directions\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )
        }
    }

    //This takes ids defined inside 'application_nav_host.xml'
    //NOTE: this is meant to be called on the Main thread
    fun navigate(currentFragment: Int, directions: NavDirections) {
        if (navController.currentDestination?.id == currentFragment) {
            navController.navigate(directions)
        } else {
            val errorMessage =
                "Attempted to navigate while currentFragment was set to an incorrect destination.\n" +
                        "navController.currentDestination?.id: ${navController.currentDestination?.id}\n" +
                        "currentFragment: $currentFragment\n" +
                        "directions: $directions\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )
        }
    }

    fun showMessageFragmentChatRoomListPopupMenu(
        v: View,
        createNewChatRoom: () -> Unit,
        joinChatRoom: () -> Unit,
        searchClicked: () -> Unit,
        sortBy: (ChatRoomSortMethodSelected) -> Unit,
    ) {
        chatRoomFragmentPopupMenu = PopupMenu(this, v)
        val inflater: MenuInflater? = chatRoomFragmentPopupMenu?.menuInflater
        inflater?.inflate(R.menu.chat_room_fragment_popup_menu, chatRoomFragmentPopupMenu?.menu)

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuNewChatRoomMenuItem)
            ?.setSafeOnMenuItemClickListener {
                createNewChatRoom()
            }

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuJoinChatRoomMenuItem)
            ?.setSafeOnMenuItemClickListener {
                joinChatRoom()
            }

        setupChatRoomListSearch(searchClicked)

        setupChatRoomListSort(sortBy)

        chatRoomFragmentPopupMenu?.show()

    }

    //chatRoomFragmentPopupMenu is expected to be set before this is called
    private fun setupChatRoomListSearch(
        searchClicked: () -> Unit,
    ) {
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSearchMenuItem)
            ?.setSafeOnMenuItemClickListener {
                searchClicked()
            }
    }

    //chatRoomFragmentPopupMenu is expected to be set before this is called
    private fun setupChatRoomListSort(
        sortBy: (ChatRoomSortMethodSelected) -> Unit,
    ) {

        when (applicationViewModel.chatRoomContainer.chatRoomSortMethodSelected) {
            ChatRoomSortMethodSelected.SORT_BY_UNREAD -> {
                setUnreadMenuItemToDisabled()
            }
            ChatRoomSortMethodSelected.SORT_BY_VISITED -> {
                setVisitedMenuItemToDisabled()
            }
            ChatRoomSortMethodSelected.SORT_BY_RECENT -> {
                setRecentMenuItemToDisabled()
            }
            ChatRoomSortMethodSelected.SORT_BY_JOINED -> {
                setJoinedMenuItemToDisabled()
            }
        }

        //NOTE: the menu closes after selected and so not calling the functions above
        // setUnreadMenuItemToDisabled, setRecentMenuItemToDisabled, setJoinedMenuItemToDisabled
        // to enable/disable buttons
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByUnreadMenuItem)
            ?.setSafeOnMenuItemClickListener {
                sortBy(ChatRoomSortMethodSelected.SORT_BY_UNREAD)
            }

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByVisitedMenuItem)
            ?.setSafeOnMenuItemClickListener {
                sortBy(ChatRoomSortMethodSelected.SORT_BY_VISITED)
            }

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByRecentMenuItem)
            ?.setSafeOnMenuItemClickListener {
                sortBy(ChatRoomSortMethodSelected.SORT_BY_RECENT)
            }

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByJoinedMenuItem)
            ?.setSafeOnMenuItemClickListener {
                sortBy(ChatRoomSortMethodSelected.SORT_BY_JOINED)
            }
    }

    private fun setUnreadMenuItemToDisabled() {
        //this assumes chatRoomFragmentPopupMenu has been set
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByUnreadMenuItem)?.isEnabled =
            false
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByVisitedMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByRecentMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByJoinedMenuItem)?.isEnabled =
            true
    }

    private fun setVisitedMenuItemToDisabled() {
        //this assumes chatRoomFragmentPopupMenu has been set
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByUnreadMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByVisitedMenuItem)?.isEnabled =
            false
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByRecentMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByJoinedMenuItem)?.isEnabled =
            true
    }

    private fun setRecentMenuItemToDisabled() {
        //this assumes chatRoomFragmentPopupMenu has been set
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByUnreadMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByVisitedMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByRecentMenuItem)?.isEnabled =
            false
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByJoinedMenuItem)?.isEnabled =
            true
    }

    private fun setJoinedMenuItemToDisabled() {
        //this assumes chatRoomFragmentPopupMenu has been set
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByUnreadMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByVisitedMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByRecentMenuItem)?.isEnabled =
            true
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuSortByJoinedMenuItem)?.isEnabled =
            false
    }

    fun showInviteToChatRoomListPopupMenu(
        v: View,
        searchClicked: () -> Unit,
        sortBy: (ChatRoomSortMethodSelected) -> Unit,
    ) {
        chatRoomFragmentPopupMenu = PopupMenu(this, v)
        val inflater: MenuInflater? = chatRoomFragmentPopupMenu?.menuInflater
        inflater?.inflate(R.menu.chat_room_fragment_popup_menu, chatRoomFragmentPopupMenu?.menu)

        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuNewChatRoomMenuItem)?.isVisible =
            false
        chatRoomFragmentPopupMenu?.menu?.findItem(R.id.chatRoomPopupMenuJoinChatRoomMenuItem)?.isVisible =
            false

        setupChatRoomListSearch(searchClicked)

        setupChatRoomListSort(sortBy)

        chatRoomFragmentPopupMenu?.show()

    }

    fun showLocationScreenFragmentLayersPopupMenu(
        v: View,
        setMapLayerType: (MenuItem) -> Unit,
    ) {

        locationScreenFragmentLayersPopupMenu = PopupMenu(this, v)
        val inflater: MenuInflater? = locationScreenFragmentLayersPopupMenu?.menuInflater
        inflater?.inflate(R.menu.map_options, locationScreenFragmentLayersPopupMenu?.menu)

        locationScreenFragmentLayersPopupMenu?.menu?.findItem(R.id.mapOptionsMenuNormalMap)
            ?.setSafeOnMenuItemClickListener {
                setMapLayerType(it)
            }

        locationScreenFragmentLayersPopupMenu?.menu?.findItem(R.id.mapOptionsMenuHybridMap)
            ?.setSafeOnMenuItemClickListener {
                setMapLayerType(it)
            }

        locationScreenFragmentLayersPopupMenu?.menu?.findItem(R.id.mapOptionsMenuSatelliteMap)
            ?.setSafeOnMenuItemClickListener {
                setMapLayerType(it)
            }

        locationScreenFragmentLayersPopupMenu?.show()
    }

    fun blockAndReportUserFromChatRoom(
        childFragmentManager: FragmentManager,
        accountOID: String,
        unMatch: Boolean,
        reportOrigin: ReportMessages.ReportOriginType,
        chatRoomId: String = "",
        messageUUID: String = "",
    ) {

        val matchOptionsBuilder = ReportMessages.UserMatchOptionsRequest.newBuilder()
            .setMatchAccountId(accountOID)
            .setResponseType(ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT)
            .setReportOrigin(reportOrigin)

        if (chatRoomId.isNotEmpty() && messageUUID.isNotEmpty()) {
            matchOptionsBuilder.messageUUID = messageUUID
            matchOptionsBuilder.chatRoomId = chatRoomId
        }

        showBlockAndReportDialog(
            this,
            childFragmentManager,
            onDismissAction = {
                Log.i("block&RepStuff", "onDismissAction")
            },
            languageSelected = {

                matchOptionsBuilder.reportReason =
                    ReportMessages.ReportReason.REPORT_REASON_LANGUAGE

                runBlockAndReportFromChatRoom(
                    matchOptionsBuilder,
                    unMatch
                )

            },
            inappropriatePictureSelected = {

                matchOptionsBuilder.reportReason =
                    ReportMessages.ReportReason.REPORT_REASON_INAPPROPRIATE_PICTURE

                runBlockAndReportFromChatRoom(
                    matchOptionsBuilder,
                    unMatch
                )
            },
            advertisingSelected = {

                matchOptionsBuilder.reportReason =
                    ReportMessages.ReportReason.REPORT_REASON_ADVERTISING

                runBlockAndReportFromChatRoom(
                    matchOptionsBuilder,
                    unMatch
                )
            },
            otherSelected = { otherReportInfo ->

                matchOptionsBuilder.reportReason = ReportMessages.ReportReason.REPORT_REASON_OTHER
                matchOptionsBuilder.otherInfo = otherReportInfo

                runBlockAndReportFromChatRoom(
                    matchOptionsBuilder,
                    unMatch
                )
            }
        )
    }

    private fun runBlockAndReportFromChatRoom(
        matchOptionsBuilder: ReportMessages.UserMatchOptionsRequest.Builder,
        unMatch: Boolean,
    ) {
        if (unMatch) {
            //this will perform an un match as well so setting up loading state
            setLoadingDialogState(
                true,
                ChatRoomFragment.LOADING_DIALOG_TIMEOUT_IN_MS
            )
        }

        applicationViewModel.blockAndReportFromChatRoom(
            matchOptionsBuilder,
            unMatch
        )
    }

    fun hideMenus() {
        chatRoomActiveMessagePopupMenu.dismiss()
        adminUserOptionsFragmentPopupMenu.dismiss()
        chatRoomFragmentPopupMenu?.dismiss()
        chatRoomFragmentPopupMenu = null
        setupActivityMenuBars?.dismissPopupMenus()
        locationScreenFragmentLayersPopupMenu?.dismiss()
        locationScreenFragmentLayersPopupMenu = null
    }

    fun setNumberOfMembersInChatRoom() {

        var numMembers = 0
        for (i in 0 until applicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()) {
            val member =
                applicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(i)
            member?.let {
                if (it.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                    || it.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                ) {
                    numMembers++
                }
            }
        }

        //NOTE: the + 1 is because the user themselves are not included in the members list
        val numberMembersString = resources.getQuantityString(
            R.plurals.top_toolbar_chat_room_members_placeholder,
            numMembers + 1,
            numMembers + 1
        )

        setupActivityMenuBars?.setMemberCountText(numberMembersString)
    }

    private fun removeLocationSnackBar() {
        locationDeniedSnackBar?.dismiss()
        locationDeniedSnackBar = null
    }

    private fun setupLocationSnackBarForAlgorithm(action: () -> Unit) {

        //This value needs to be set because the snack bar from the algorithm rus a compareAndSet
        // to make sure that it is FALSE when the SETTINGS button is pressed.
        applicationViewModel.findMatchesObject.findMatchesClientRunning.set(false)

        setupLocationSnackBar(action)
    }

    private fun setupLocationSnackBar(action: () -> Unit) {

        if (locationDeniedSnackBar == null) {

            locationDeniedSnackBar = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.activities_shared_location_snack_bar_message,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAnchorView(bottomNavigationView)
                .setAction(R.string.activities_shared_location_snack_bar_button_name) {
                    action()
                    locationDeniedSnackBar = null
                }

            locationDeniedSnackBar?.show()

        } else {
            locationDeniedSnackBar?.let {
                it.setText(R.string.activities_shared_location_snack_bar_message)
                    .setAction(R.string.activities_shared_location_snack_bar_button_name) {
                        action()
                        locationDeniedSnackBar = null
                    }
            }
        }
    }

    private fun serverReturnedInvalidParameterError(typeOfLocationUpdate: String) {
        Toast.makeText(
            GlobalValues.applicationContext,
            getString(R.string.invalid_value_attempts, typeOfLocationUpdate),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun modifyingValueFailed(typeOfLocationUpdate: String) {
        Toast.makeText(
            GlobalValues.applicationContext,
            getString(R.string.value_has_not_been_set, typeOfLocationUpdate),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun getCurrentLocationCompletedFailure(
        typeOfLocationUpdate: TypeOfLocationUpdate,
        errorMessageId: Int
    ) {
        Toast.makeText(
            GlobalValues.applicationContext,
            errorMessageId,
            Toast.LENGTH_LONG
        ).show()

        when (typeOfLocationUpdate) {
            TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                applicationViewModel.findMatchesObject.findMatchesClientRunning.set(false)
            }
            TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
            TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                _finishedChatRoomLocationRequest.value =
                    EventWrapperWithKeyString(
                        ChatRoomLocationRequestReturn(
                            typeOfLocationUpdate,
                            false
                        ),
                        currentChatRoomFragmentInstanceID
                    )
            }
            TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST,
            TypeOfLocationUpdate.OTHER,
            -> {
                val errorMessage =
                    "getCurrentLocationCompleted() inside AppActivity returned invalid type of update.\n" +
                            "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
        }
    }

    //NOTE: this will setup the snack bar and set the live data inside the activity view model for the fragment(s) to observe
    fun getCurrentLocation(typeOfLocationUpdate: TypeOfLocationUpdate) {

        removeLocationSnackBar()
        locationStatusCheck(
            this,
            callBackIfDisabled = {

                val alertDialog = EnableLocationDialogFragment(
                    positiveButtonListener = {

                        try {
                            registerForRequestLocation?.runRegisterForRequestLocationActivityResult(
                                typeOfLocationUpdate
                            )
                        } catch (e: ActivityNotFoundException) {

                            val errorMessage =
                                "ActivityNotFoundException returned when attempting to register for location result.\n" +
                                        "e.message: ${e.message}\n" +
                                        "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage
                            )

                            getCurrentLocationCompletedFailure(
                                typeOfLocationUpdate,
                                R.string.activities_shared_error_requesting_location
                            )
                        }
                    },
                    negativeButtonListener = { dialog: DialogInterface? ->

                        dialog?.dismiss()

                        when (typeOfLocationUpdate) {
                            TypeOfLocationUpdate.ALGORITHM_REQUEST -> {

                                setupLocationSnackBarForAlgorithm {
                                    try {
                                        //set the matches algorithm to be running
                                        if (
                                            applicationViewModel.findMatchesObject.findMatchesClientRunning.compareAndSet(
                                                expect = false,
                                                update = true
                                            )
                                        ) {
                                            requestLocationForAlgorithm?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        }
                                    } catch (e: ActivityNotFoundException) {

                                        val errorMessage =
                                            "ActivityNotFoundException returned when attempting to register for location result.\n" +
                                                    "e.message: ${e.message}\n" +
                                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                                        errorStore.storeError(
                                            Thread.currentThread().stackTrace[2].fileName,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors(),
                                            errorMessage
                                        )

                                        Toast.makeText(
                                            GlobalValues.applicationContext,
                                            R.string.activities_shared_error_requesting_location,
                                            Toast.LENGTH_LONG
                                        ).show()

                                        applicationViewModel.findMatchesObject.findMatchesClientRunning.set(
                                            false
                                        )
                                    }
                                }
                            }
                            TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                            TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                                _finishedChatRoomLocationRequest.value =
                                    EventWrapperWithKeyString(
                                        ChatRoomLocationRequestReturn(
                                            typeOfLocationUpdate,
                                            false
                                        ),
                                        currentChatRoomFragmentInstanceID
                                    )
                            }
                            TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST,
                            TypeOfLocationUpdate.OTHER,
                            -> {
                                val errorMessage =
                                    "getCurrentLocation() inside AppActivity returned invalid type of update.\n" +
                                            "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorMessage
                                )
                            }
                        }
                    }
                )

                alertDialog.show(supportFragmentManager, "request_loc_app_activity")
            },
            callBackIfEnabled = {
                accessLocation(typeOfLocationUpdate)
            }
        )
    }

    private fun accessLocation(typeOfLocationUpdate: TypeOfLocationUpdate) {

        Log.i("access_loc", printStackTraceForErrors())

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) { //if permission is not granted

            //request location permissions
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                typeOfLocationUpdate.ordinal
            )

        } else { //if permission is granted

            val functionSuccessful = {

                Log.i(
                    "requestLocation",
                    "callback when permission granted typeOfLocationUpdate: $typeOfLocationUpdate"
                )

                when (typeOfLocationUpdate) {

                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                        applicationViewModel.requestMatchesFromServer()
                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                    TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    true
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    }
                    TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST,
                    TypeOfLocationUpdate.OTHER,
                    -> {
                        val errorMessage =
                            "accessLocation() inside AppActivity returned invalid type of update.\n" +
                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }

            if (GlobalValues.lastUpdatedLocationInfo.lastTimeLocationReceived + GlobalValues.timeBetweenUpdatesInMs
                < getCurrentTimestampInMillis()
            ) { //if time limit between requesting locations has been passed (location requires updating)

                val saveLocation: (Location) -> Unit = { location ->
                    CoroutineScope(ioDispatcher).launch {

                        getAddressFromLocation(
                            location,
                            geoCoder,
                            {
                                functionSuccessful()
                            },
                            errorStore
                        )
                    }
                }

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                )
                    .addOnCompleteListener { currentLocationTask ->
                        if (currentLocationTask.isSuccessful && currentLocationTask.result != null) { //if task was successful
                            saveLocation(currentLocationTask.result)
                        } else { //if could not get current location, get last location stored
                            fusedLocationClient.lastLocation.addOnCompleteListener { lastLocationTask ->
                                if (lastLocationTask.isSuccessful && lastLocationTask.result != null) { //if task was successful
                                    saveLocation(lastLocationTask.result)
                                } else { //if task failed

                                    //This can happen if the user disabled location for all apps (happened in API 21
                                    // Nexus 5). Or if the app is minimized while location is being requested (API 30+).
                                    getCurrentLocationCompletedFailure(
                                        typeOfLocationUpdate,
                                        R.string.activities_shared_error_requesting_location_manually_enable
                                    )
                                }
                            }
                        }
                    }

            } else { //if location does not require updating
                functionSuccessful()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val typeOfLocationUpdate = TypeOfLocationUpdate.setVal(requestCode)

        if (typeOfLocationUpdate == TypeOfLocationUpdate.OTHER) {
            //NOTE: Could it simply mean android automatically requested permissions OR that
            // registerForRequestPostNotifications() was used to request POST_NOTIFICATION
            // permissions.
            return
        }

        when {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> { //permissions successfully acquired
                when (typeOfLocationUpdate) {
                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                        getCurrentLocation(TypeOfLocationUpdate.ALGORITHM_REQUEST)
                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST -> {
                        getCurrentLocation(TypeOfLocationUpdate.CHAT_LOCATION_REQUEST)
                    }
                    else -> {
                        val errorMessage =
                            "onRequestPermissionsResult() inside AppActivity returned invalid type of update.\n" +
                                    "requestCode: $requestCode\n" +
                                    "permissions: $permissions\n" +
                                    "grantResults: $grantResults\n" +
                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }
            grantResults.isEmpty() -> { //if no result returned an error occurred

                Toast.makeText(
                    GlobalValues.applicationContext,
                    "Please manually enable location inside your Settings.",
                    Toast.LENGTH_LONG
                ).show()

                when (typeOfLocationUpdate) {
                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                        applicationViewModel.findMatchesObject.findMatchesClientRunning.set(false)
                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                    TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    false
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    }
                    else -> {
                        val errorMessage =
                            "onRequestPermissionsResult() inside AppActivity returned invalid type of update.\n" +
                                    "requestCode: $requestCode\n" +
                                    "permissions: $permissions\n" +
                                    "grantResults: $grantResults\n" +
                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> { //if permissions were permanently denied

                when (typeOfLocationUpdate) {
                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {

                        setupLocationSnackBarForAlgorithm {

                            //attempt to open this apps' details settings, if not open the location settings
                            try {
                                //set the matches algorithm to be running
                                if (applicationViewModel.findMatchesObject.findMatchesClientRunning.compareAndSet(
                                        expect = false,
                                        update = true
                                    )
                                ) {
                                    requestLocationForAlgorithm?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            } catch (e: ActivityNotFoundException) {

                                Log.i("activity_not_found", "exception: ${e.message}")

                                applicationViewModel.findMatchesObject.findMatchesClientRunning.set(
                                    false
                                )
                                try {

                                    //set the matches algorithm to be running
                                    if (applicationViewModel.findMatchesObject.findMatchesClientRunning.compareAndSet(
                                            expect = false,
                                            update = true
                                        )
                                    ) {
                                        requestLocationForAlgorithm?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    }

                                } catch (e: ActivityNotFoundException) {

                                    Log.i("activity_not_found", "exception: ${e.message}")

                                    applicationViewModel.findMatchesObject.findMatchesClientRunning.set(
                                        false
                                    )

                                    Toast.makeText(
                                        GlobalValues.applicationContext,
                                        R.string.activities_shared_error_requesting_location,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }

                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                    TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    false
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    }
                    else -> {
                        val errorMessage =
                            "onRequestPermissionsResult() inside AppActivity returned invalid type of update.\n" +
                                    "requestCode: $requestCode\n" +
                                    "permissions: $permissions\n" +
                                    "grantResults: $grantResults\n" +
                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }
            grantResults[0] != PackageManager.PERMISSION_GRANTED -> { //if permissions were temporarily denied

                //NOTE: not making a toast here because these toasts will not be show until after the activity to request the permission has closed
                // so if 'Deny' is pressed then 'Allow' the toast will be shown after 'Allow' however the 'Deny' toast would show
                when (typeOfLocationUpdate) {
                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                        getCurrentLocation(TypeOfLocationUpdate.ALGORITHM_REQUEST)
                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                    TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    false
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    }
                    else -> {
                        val errorMessage =
                            "onRequestPermissionsResult() inside AppActivity returned invalid type of update.\n" +
                                    "requestCode: $requestCode\n" +
                                    "permissions: $permissions\n" +
                                    "grantResults: $grantResults\n" +
                                    "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }
            else -> { //not sure

                val errorMessage =
                    "onRequestPermissionsResult() returned an unknown set of values.\n" +
                            "requestCode: $requestCode\n" +
                            "permissions: $permissions\n" +
                            "grantResults: $grantResults\n" +
                            "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                when (typeOfLocationUpdate) {
                    TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                        applicationViewModel.findMatchesObject.findMatchesClientRunning.set(false)
                    }
                    TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
                    TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    false
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    }
                    else -> {
                        //error stored above for these cases
                    }
                }
            }
        }
    }

    private fun locationRequestResult(typeOfLocationUpdate: TypeOfLocationUpdate) {
        when (typeOfLocationUpdate) {
            TypeOfLocationUpdate.ALGORITHM_REQUEST -> {
                algorithmRequestResult()
            }
            TypeOfLocationUpdate.CHAT_LOCATION_REQUEST,
            TypeOfLocationUpdate.PINNED_LOCATION_REQUEST -> {

                locationStatusCheck(
                    this,
                    callBackIfDisabled = {
                        _finishedChatRoomLocationRequest.value =
                            EventWrapperWithKeyString(
                                ChatRoomLocationRequestReturn(
                                    typeOfLocationUpdate,
                                    false
                                ),
                                currentChatRoomFragmentInstanceID
                            )
                    },
                    callBackIfEnabled = {
                        getCurrentLocation(TypeOfLocationUpdate.CHAT_LOCATION_REQUEST)
                    }
                )
            }
            TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST,
            TypeOfLocationUpdate.OTHER,
            -> {
                val errorMessage =
                    "locationRequestResult() inside AppActivity returned invalid type of update.\n" +
                            "typeOfLocationUpdate: $typeOfLocationUpdate\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
        }
    }

    private fun algorithmRequestResult() {
        locationStatusCheck(
            this,
            callBackIfDisabled = {

                setupLocationSnackBarForAlgorithm {
                    try {
                        //set the matches algorithm to be running
                        if (applicationViewModel.findMatchesObject.findMatchesClientRunning.compareAndSet(
                                expect = false,
                                update = true
                            )
                        ) {
                            requestLocationForAlgorithm?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.activities_shared_manually_label_location,
                            Toast.LENGTH_LONG
                        ).show()

                        applicationViewModel.findMatchesObject.findMatchesClientRunning.set(false)
                    }
                }
            },
            callBackIfEnabled = {
                getCurrentLocation(TypeOfLocationUpdate.ALGORITHM_REQUEST)
            }
        )
    }

    //If the user has turned off notifications, request they turn them back on.
    // This permission request is mandatory in API levels greater than 32.
    fun registerForRequestPostNotifications(runOnReturn: () -> Unit) {
        registerForRequestPostNotifications?.runRegisterForRequestPostNotificationActivityResult(
            runOnReturn
        )
    }

    private fun cancelAndNullTimer() {
        loadingDialogFragmentExpirationTimer?.cancel()

        //removes references after cancel
        loadingDialogFragmentExpirationTimer?.purge()
        loadingDialogFragmentExpirationTimer = null
    }

    private fun clearLoadingDialog() {
        loadingDialogFragment?.dismiss()
        loadingDialogFragment = null
    }

    fun setLoadingDialogState(enableLoading: Boolean, timeoutOfLoadingInMillis: Long = -1L) {

        Log.i("block&RepStuff", "setLoadingDialogState() ${printStackTraceForErrors()}")

        if (enableLoading && loadingDialogFragment == null) { //if enable loading and dialog fragment does not already exist
            cancelAndNullTimer()
            clearLoadingDialog()
            loadingDialogFragment = LoadingDialogFragment()
            loadingDialogFragment?.show(supportFragmentManager, "loading_dialog")

            if (timeoutOfLoadingInMillis > 0) {
                loadingDialogFragmentExpirationTimer = Timer()
                loadingDialogFragmentExpirationTimer?.schedule(
                    object : TimerTask() {
                        override fun run() {
                            clearLoadingDialog()
                            cancelAndNullTimer()
                        }
                    },
                    timeoutOfLoadingInMillis
                )
            }
        } else if (!enableLoading) {
            cancelAndNullTimer()
            clearLoadingDialog()
        }
    }

    fun handleJoinedLeftChatRoomReturn(
        info: ReturnJoinedLeftChatRoomDataHolder,
        navigateToMessengerScreenResourceNum: Int,
    ) {
        val chatRoom = info.chatRoomWithMemberMap

        if (chatRoom.chatRoomId == applicationViewModel.chatRoomContainer.chatRoom.chatRoomId) {
            setLoadingDialogState(false)
            when (info.chatRoomUpdateMadeEnum) {
                ChatRoomUpdateMade.CHAT_ROOM_LEFT -> {
                } //continue but don't show toast
                ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED -> {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        "Match has been canceled.",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
                ChatRoomUpdateMade.CHAT_ROOM_JOINED,
                ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH,
                ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED -> {
                    return
                }
            }

            handleLeaveChatRoom(chatRoom.chatRoomId, navigateToMessengerScreenResourceNum)
        }
    }

    fun handleLeaveChatRoom(chatRoomId: String, navigateToMessengerScreenResourceNum: Int) {

        if (chatRoomId == applicationViewModel.chatRoomContainer.chatRoom.chatRoomId) {
            setLoadingDialogState(false)
            applicationViewModel.checkIfAllChatRoomMessagesHaveBeenObserved(chatRoomId)
            hideMenus()
            navController.navigate(navigateToMessengerScreenResourceNum)
        }
    }

    fun handleKickedBanned(
        result: ReturnKickedBannedFromChatRoomDataHolder,
        navigateToMessengerScreenResourceNum: Int,
    ) {

        if (applicationViewModel.chatRoomContainer.chatRoom.chatRoomId == result.chatRoomId) { //if chat room is this chat room

            setLoadingDialogState(false)
            hideMenus()

            runKickedOrBanned(
                result.kickOrBanEnum,
                supportFragmentManager,
            ) {
                navController.navigate(navigateToMessengerScreenResourceNum)
            }
        }
    }

    fun noCategoriesEntered() {
        Toast.makeText(
            GlobalValues.applicationContext,
            R.string.select_categories_application_activity_select_one_activity,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStart() {
        super.onStart()

        runOnStart()
        runOnStart = {}

        //check if new messages required
        applicationViewModel.checkIfAllChatRoomMessagesHaveBeenObserved()

        //FindMatchesObject handlers do not run in background, start them back up again.
        applicationViewModel.startFindMatchesHandlerIfNecessary()
    }

    override fun onPause() {
        //Dialogs and menus must be dismissed in onPause().  If they are dismissed too late then when
        // the activity is re-created (such as on a rotation change) the application will crash.
        dismissAllDialogs(supportFragmentManager)
        hideMenus()
        super.onPause()
    }

    override fun onStop() {
        removeLocationSnackBar()

        //don't let handlers run in background
        applicationViewModel.stopAllFindMatchesHandlers()

        super.onStop()
    }

    inner class SetupActivityMenuBars {

        private val topToolBar: Toolbar = binding.activityAppTopToolbar.root
        private val topToolBarTitle: TextView =
            topToolBar.findViewById(R.id.toolbarApplicationTopTitleTextView)
        private val topToolBarMemberCount: TextView =
            topToolBar.findViewById(R.id.toolbarApplicationTopMemberCountTextView)
        private val topToolBarRelativeLayout: RelativeLayout? =
            topToolBar.findViewById(R.id.toolbarApplicationTopLayoutRelativeLayout)

        private var currentMenuProvider: MenuProvider? = null
        private var currentFragmentInstanceID = ""

        private var lastTimeClicked = 0L
        private val defaultInterval = 1000L

        init {
            //The title must be set to a 'dummy title' BEFORE setSupportActionBar()
            // is called. Otherwise the top title will be set to R.string.app_name if
            // an orientation (or any activity recreation) occurs while topToolBar is
            // visible.
            topToolBar.title = ""
            setSupportActionBar(topToolBar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            topToolBar.visibility = View.GONE
        }

        fun dismissPopupMenus() {
            topToolBar.dismissPopupMenus()
        }

        fun setMemberCountText(numberMembersString: String) {
            if (topToolBarMemberCount.text != numberMembersString) {
                topToolBarMemberCount.text = numberMembersString
            }
        }

        private fun setTopToolbarToGone() {
            topToolBar.visibility = View.GONE
            topToolBarRelativeLayout?.setOnClickListener(null)
            topToolBarRelativeLayout?.visibility = View.GONE

            topToolBar.title = null
            topToolBar.setOnClickListener(null)
            topToolBar.isClickable = false
            topToolBarTitle.text = null
            topToolBarMemberCount.text = null
        }

        private fun setTopToolbarWithOnlyTitle(title: String?) {
            topToolBar.visibility = View.VISIBLE
            topToolBarRelativeLayout?.setOnClickListener(null)
            topToolBarRelativeLayout?.isClickable = false
            topToolBarRelativeLayout?.visibility = View.GONE

            topToolBar.title = title
            topToolBar.setOnClickListener(null)
            topToolBar.isClickable = false
            topToolBarTitle.text = null
            topToolBarMemberCount.text = null
        }

        //sets the bottom navigation view visibility, will update the little red dot for the
        // messenger if the view was NOT visible and is becoming visible
        private fun setBottomNavigationViewVisibility(value: Int) {
            if (value == View.VISIBLE
                && !bottomNavigationView.isVisible
            ) {
                applicationViewModel.checkIfAllChatRoomMessagesHaveBeenObserved()
            }

            bottomNavigationView.visibility = value
        }

        fun setupToolbarsBlankLoadingFragment() {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarToGone()
        }

        fun setupToolbarsMatchScreenFragment() {
            setBottomNavigationViewVisibility(View.VISIBLE)
            setTopToolbarToGone()
        }

        fun setupToolbarsMatchMadeFragment() {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarToGone()
        }

        fun setupToolbarsProfileScreenFragment() {
            setBottomNavigationViewVisibility(View.VISIBLE)
            setTopToolbarToGone()
        }

        fun setupToolbarsCategoriesFragment(viewLifecycleOwner: LifecycleOwner) {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarWithOnlyTitle(resources.getString(R.string.select_activities))
            addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner)
        }

        fun setupToolbarsModifyProfileScreenFragment(viewLifecycleOwner: LifecycleOwner) {
            //Not showing bottom navigation bar here so that any updated user info has time to reach the
            // server and save before the algorithm runs again; while user is navigating (if the view is
            // visible there will be no crash or anything, it might just not run the algorithm in time)
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarWithOnlyTitle(resources.getString(R.string.edit_profile))
            addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner)
        }

        fun setupToolbarsMessengerScreenFragment() {
            setBottomNavigationViewVisibility(View.VISIBLE)
            setTopToolbarToGone()
        }

        fun setupToolbarsSelectLocationScreenFragment(viewLifecycleOwner: LifecycleOwner) {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarWithOnlyTitle(resources.getString(R.string.location))
            addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner)
        }

        fun setupToolbarsTimeFrameTutorialFragment() {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarToGone()
        }

        private fun addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner: LifecycleOwner) {
            currentMenuProvider?.let {
                removeMenuProvider(it)
                currentMenuProvider = null
            }

            viewLifecycleOwner.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        super.onDestroy(owner)
                        currentMenuProvider = null
                    }
                }
            )

            currentMenuProvider = object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.chat_room_top_menu, menu)
                    menu.findItem(R.id.chatRoomTopMenuClearHistoryItem)?.isVisible = false
                    menu.findItem(R.id.chatRoomTopMenuLeaveChatItem)?.isVisible = false
                    menu.findItem(R.id.chatRoomTopMenuBlockReportItem)?.isVisible = false
                    menu.findItem(R.id.chatRoomTopMenuUnMatchItem)?.isVisible = false
                    menu.findItem(R.id.chatRoomTopMenuInviteItem)?.isVisible = false
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    menuItem.isVisible = false
                    return when (menuItem.itemId) {
                        R.id.home -> {
                            navController.navigateUp()
                            true
                        }
                        else -> false
                    }
                }
            }

            currentMenuProvider?.let {
                addMenuProvider(
                    it,
                    viewLifecycleOwner
                )
            }
        }

        private fun runLambdaForMenuProvider(lambda: (() -> Unit)?): Boolean {
            if (lambda != null) {
                //put a delay to avoid double clicks
                if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
                    return true
                }
                lastTimeClicked = SystemClock.elapsedRealtime()
                lambda()
                return true
            }
            return false
        }

        //Will create a MenuProvider and add it to the activity. Will remove any previously added
        // MenuProvider.
        //Pass null for each menu-item if value is not visible and has no lambda associated with it.
        //NOTE: This should be called on the main thread to avoid odd problems of the menu
        // provider being in limbo.
        fun addMenuProviderWithMenuItems(
            viewLifecycleOwner: LifecycleOwner,
            leaveChatRoomLambda: (() -> Unit)? = null,
            clearHistoryLambda: (() -> Unit)? = null,
            blockReportLambda: (() -> Unit)? = null,
            unMatchLambda: (() -> Unit)? = null,
            inviteLambda: (() -> Unit)? = null,
            fragmentInstanceID: String
        ) {

            //Must be here for cases in which the previous menu has not yet been removed
            // by the viewLifecycleObserver and a new menu must be added.
            currentMenuProvider?.let {
                removeMenuProvider(it)
                currentMenuProvider = null
                currentFragmentInstanceID = ""
            }

            //The lambdas can have references to the fragment stored inside. Need
            // to remove the reference currentMenuProvider in onDestroy() so the
            // activity does not leak the fragment.
            viewLifecycleOwner.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        super.onDestroy(owner)
                        currentMenuProvider?.let {
                            //This check must be here, otherwise a fragment could have its
                            // onDestroy() called after another fragment initializes its menu.
                            if (fragmentInstanceID == currentFragmentInstanceID) {
                                currentMenuProvider = null
                                currentFragmentInstanceID = ""
                            }
                        }
                    }
                }
            )

            currentFragmentInstanceID = fragmentInstanceID
            currentMenuProvider = object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.chat_room_top_menu, menu)
                    menu.findItem(R.id.chatRoomTopMenuLeaveChatItem)?.isVisible =
                        leaveChatRoomLambda != null
                    menu.findItem(R.id.chatRoomTopMenuClearHistoryItem)?.isVisible =
                        clearHistoryLambda != null
                    menu.findItem(R.id.chatRoomTopMenuBlockReportItem)?.isVisible =
                        blockReportLambda != null
                    menu.findItem(R.id.chatRoomTopMenuUnMatchItem)?.isVisible =
                        unMatchLambda != null
                    menu.findItem(R.id.chatRoomTopMenuInviteItem)?.isVisible = inviteLambda != null
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.home -> {
                            navigateUp()
                            true
                        }
                        R.id.chatRoomTopMenuLeaveChatItem -> {
                            runLambdaForMenuProvider(leaveChatRoomLambda)
                        }
                        R.id.chatRoomTopMenuClearHistoryItem -> {
                            runLambdaForMenuProvider(clearHistoryLambda)
                        }
                        R.id.chatRoomTopMenuBlockReportItem -> {
                            runLambdaForMenuProvider(blockReportLambda)
                        }
                        R.id.chatRoomTopMenuUnMatchItem -> {
                            runLambdaForMenuProvider(unMatchLambda)
                        }
                        R.id.chatRoomTopMenuInviteItem -> {
                            runLambdaForMenuProvider(inviteLambda)
                        }
                        else -> false
                    }
                }
            }

            currentMenuProvider?.let {
                addMenuProvider(
                    it,
                    viewLifecycleOwner
                )
            }
        }

        fun setupToolbarsChatRoomFragments() {
            setBottomNavigationViewVisibility(View.GONE)
            topToolBar.visibility = View.VISIBLE
            topToolBarRelativeLayout?.apply {
                setOnClickListener(null)
                visibility = View.VISIBLE
            }

            topToolBar.title = null
            topToolBarTitle.text =
                if (applicationViewModel.chatRoomContainer.chatRoom.chatRoomName != "" && applicationViewModel.chatRoomContainer.chatRoom.chatRoomName != "~") {
                    applicationViewModel.chatRoomContainer.chatRoom.chatRoomName
                } else {
                    null
                }

            setNumberOfMembersInChatRoom()
        }

        fun setupToolbarsSelectChatRoomForInviteFragment(viewLifecycleOwner: LifecycleOwner) {
            setupToolbarsChatRoomFragments()
            addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner)
        }

        fun setupToolbarsDisplayQrCodeFragment(viewLifecycleOwner: LifecycleOwner) {
            setupToolbarsChatRoomFragments()
            addMenuProviderWithNoMenuItemsVisible(viewLifecycleOwner)
        }

        fun setupToolbarsChatRoomFragmentMatchMade() {
            setBottomNavigationViewVisibility(View.GONE)
            setTopToolbarWithOnlyTitle(null)
        }

        fun setTopToolbarChatRoomName() {
            topToolBarTitle.text = applicationViewModel.chatRoomContainer.chatRoom.chatRoomName
        }
    }

}
