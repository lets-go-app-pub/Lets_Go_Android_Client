package site.letsgoapp.letsgo.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.LetsGoRuntimeException
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.ActivityMainBinding
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import java.util.*

class LoginActivity : AppCompatActivity() {

    //private lateinit var loginViewModel: SharedLoginViewModel
    val loginViewModel: SharedLoginViewModel by viewModels()
        @VisibleForTesting get

    var sharedLoginViewModelInstanceId = ""
        private set

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoCoder: Geocoder
    private var locationDeniedSnackBar: Snackbar? = null

    private var fragmentContainer: FragmentContainerView? = null

    private lateinit var loginObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>
    private lateinit var handleGrpcErrorStatusReturnValuesObserver: Observer<EventWrapper<GrpcFunctionErrorStatusEnum>>

    private lateinit var navigationController: NavController
    private val connectionErrorHandler = Handler(Looper.getMainLooper())
    private val connectionErrorToken = "Conn_Err_Token"

    private val cancellationTokenSource = CancellationTokenSource()

    private val errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var runOnStart: () -> Unit = {}

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    var clientsIntermediate: ClientsInterface = ClientsSourceIntermediate()
        @VisibleForTesting set

    private val ioDispatcher = ServiceLocator.globalIODispatcher

    private enum class NavFunctionToCall {
        NAVIGATE_TO_SELECT_METHOD,
        NAVIGATE_NO_BUNDLE,
        NAVIGATE_WITH_BUNDLE
    }

    private data class NavigateAfterResume(
        val functionType: NavFunctionToCall,
        val currentFragment: Int = -1,
        val destinationAction: Int = -1,
        val argumentBundle: Bundle? = null
    )

    private var activityPaused = true
    private val navigateToFragmentOnResume = mutableListOf<NavigateAfterResume>()

    //Overridden to participate in passing the view model to fragments
    // for testing.
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ServiceLocator.provideSharedLoginViewModelFactory(applicationContext)

    private var requestLocation: ActivityResultLauncher<Intent>? =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        {

            //Don't need to check for result.resultCode == Activity.RESULT_OK, simply need to check if
            // location is enabled yet and react accordingly.
            locationStatusCheck(
                this,
                callBackIfDisabled = {
                    setupLocationSnackBar {
                        this.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                    loginViewModel.setLiveDataForLocationRequestComplete(
                        LocationReturnErrorStatus.GPS_LOCATION_RETURNED_OFF
                    )
                },
                callBackIfEnabled = {
                    getCurrentLocation()
                }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        //must be called after super.onCreate() and before setContentView() and before the condition for isTaskRoot
        //installSplashScreen()

        //must be called before the condition containing isTaskRoot
        super.onCreate(savedInstanceState)

        GlobalValues.loginToChatRoomId =
            intent.getStringExtra(NotificationInfo.ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY)
                ?: ""

        if (!isTaskRoot
            && !intent.getBooleanExtra("ignoreTaskRootCheck", false)
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && intent.action != null
            && intent.action.equals(Intent.ACTION_MAIN)
            && GlobalValues.loginToChatRoomId == ""
        ) {
            finish()
            return
        }

        Log.i("whereAmI", "LoginActivity")

        sharedLoginViewModelInstanceId = loginViewModel.thisSharedLoginViewModelInstanceId

        loginObserver = Observer { loginResponseEvent ->
            val response = loginResponseEvent.peekContent()
            handleLoginStateChanged(response.first)
        }

        loginViewModel.loginFunctionData.observe(this, loginObserver)

        handleGrpcErrorStatusReturnValuesObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            if (result != null)
                handleGrpcErrorStatusReturnValues(result)
        }

        loginViewModel.handleGrpcErrorStatusReturnValues.observe(
            this,
            handleGrpcErrorStatusReturnValuesObserver
        )

        try {
            _binding = ActivityMainBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            val errorMessage =
                "Failed to inflate LoginActivity fragment.\n" +
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

        //NOTE: Set this AFTER the view model is created and the activity has subscribed to the LoginFunctions object.
        // The splash screen relies on them.
        /** The order setContentView() appears in is important because
         * 1) When it is called onAttach(), onCreate() and onViewCreated() are called for the first fragment. **/
        setContentView(binding.root)

        navigationController = findNavController(R.id.nav_host_fragment)


        Log.i("resultInfo", "intent.data:  ${intent.data}")
        Log.i(
            "resultInfo",
            "LoginActivity GlobalValues.loginToChatRoomId: ${GlobalValues.loginToChatRoomId}"
        )

        //Setup to get first location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geoCoder = Geocoder(this, Locale.getDefault())

        fragmentContainer = findViewById(R.id.nav_host_fragment)

//        //stores the data in key value pairs to pass to the 'Worker' as parameters
//        val workerParams: Data = workDataOf(
//            ErrorHandlerWorker.ERROR_MESSAGE_KEY to "error_message",
//            ErrorHandlerWorker.COLLECTION_MESSAGE_KEY to "message")
//
//        //builds the work request using the class extending 'Worker'
//        val errorWorkRequest = OneTimeWorkRequestBuilder<ErrorHandlerWorker>()
//            .setInitialDelay(10, TimeUnit.SECONDS)
//            .setInputData(workerParams)
//            .setConstraints(
//                Constraints.Builder().setRequiredNetworkType(
//                    NetworkType.CONNECTED).build())
//            .build()
//
//        //sends the request to the work manager
//        WorkManager.getInstance(applicationContext).enqueue(errorWorkRequest)

    }

    private fun removeLocationSnackBar() {
        locationDeniedSnackBar?.dismiss()
        locationDeniedSnackBar = null
    }

    private fun setupLocationSnackBar(action: () -> Unit) {

        if (locationDeniedSnackBar == null) {

            locationDeniedSnackBar = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.activities_shared_location_snack_bar_message,
                Snackbar.LENGTH_INDEFINITE
            )
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

    //NOTE: this will setup the snack bar and set the live data inside the activity view model for the fragment(s) to observe
    fun getCurrentLocation() {

        //check if device location is enabled
        locationStatusCheck(
            this,
            callBackIfDisabled = {

                val alertDialog = EnableLocationDialogFragment(
                    positiveButtonListener = {

                        try {
                            requestLocation?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (e: ActivityNotFoundException) {

                            val errorMessage =
                                "ActivityNotFoundException exception thrown when requesting location.\n" +
                                        "exception.message: ${e.message}\n"

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

                            loginViewModel.setLiveDataForLocationRequestComplete(
                                LocationReturnErrorStatus.ACTION_LOCATION_SOURCE_SETTINGS_NOT_FOUND
                            )
                        }
                    },
                    negativeButtonListener = { dialog: DialogInterface? ->

                        dialog?.dismiss()

                        setupLocationSnackBar {
                            try {
                                this.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.activities_shared_manually_label_location,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        loginViewModel.setLiveDataForLocationRequestComplete(
                            LocationReturnErrorStatus.GPS_LOCATION_DENIED
                        )
                    }
                )

                alertDialog.show(supportFragmentManager, "request_loc_login_activity")

            },
            callBackIfEnabled = {
                accessLocation()
            }
        )
    }

    private fun accessLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) { //if permission is not granted

            //request location permissions
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST.ordinal
            )

        } else { //if permission is granted

            if (GlobalValues.lastUpdatedLocationInfo.lastTimeLocationReceived + GlobalValues.timeBetweenUpdatesInMs
                < getCurrentTimestampInMillis()
            ) { //if time limit between requesting locations has been passed (location requires updating)

                val saveLocation: (Location) -> Unit = { location ->
                    CoroutineScope(ioDispatcher).launch {
                        getAddressFromLocation(
                            location,
                            geoCoder,
                            {
                                loginViewModel.setLiveDataForLocationRequestComplete(
                                    LocationReturnErrorStatus.SUCCESSFUL
                                )
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
                                } else {

                                    //This can happen if the user disabled location for all apps (happened in API 21
                                    // Nexus 5).
                                    Toast.makeText(
                                        GlobalValues.applicationContext,
                                        R.string.activities_shared_error_requesting_location,
                                        Toast.LENGTH_LONG
                                    ).show()

                                    loginViewModel.setLiveDataForLocationRequestComplete(
                                        LocationReturnErrorStatus.FAILED_TO_RETRIEVE_LOCATION
                                    )
                                }
                            }
                        }
                    }

            } else { //if location does not require updating

                loginViewModel.setLiveDataForLocationRequestComplete(
                    LocationReturnErrorStatus.SUCCESSFUL
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            TypeOfLocationUpdate.NEED_VERIFIED_ACCOUNT_REQUEST.ordinal -> {
                when {
                    grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> { //permissions successfully acquired
                        getCurrentLocation()
                    }
                    grantResults.isEmpty() -> { //if no result returned an error occurred
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.activities_shared_manually_label_location,
                            Toast.LENGTH_LONG
                        ).show()

                        loginViewModel.setLiveDataForLocationRequestComplete(
                            LocationReturnErrorStatus.ERROR_REQUESTING_PERMISSIONS
                        )
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> { //if permissions were permanently denied

                        setupLocationSnackBar {

                            //attempt to open this apps' details settings, if not open the location settings
                            try {
                                this.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                )
                            } catch (e: ActivityNotFoundException) {

                                try {
                                    this.startActivity(
                                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(
                                        GlobalValues.applicationContext,
                                        "Please manually enable location inside your Settings.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }

                        loginViewModel.setLiveDataForLocationRequestComplete(
                            LocationReturnErrorStatus.LOCATION_PERMISSIONS_DENIED
                        )
                    }
                    grantResults[0] != PackageManager.PERMISSION_GRANTED -> { //if permissions were temporarily denied

                        Toast.makeText(
                            GlobalValues.applicationContext,
                            "Location access required.",
                            Toast.LENGTH_SHORT
                        ).show()

                        loginViewModel.setLiveDataForLocationRequestComplete(
                            LocationReturnErrorStatus.LOCATION_PERMISSIONS_DENIED
                        )
                    }
                    else -> { //not sure

                        val errorMessage =
                            "An unknown value was returned inside onRequestPermissionsResult.\n" +
                                    "requestCode: 0\n" +
                                    "permissions: $permissions\n" +
                                    "grantResults: $grantResults\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage,
                            this.applicationContext
                        )

                        loginViewModel.setLiveDataForLocationRequestComplete(
                            LocationReturnErrorStatus.ERROR_REQUESTING_PERMISSIONS
                        )
                    }
                }
            }
            else -> {
                //NOTE: Could it simply mean android automatically requested permissions OR that
                // registerForRequestPostNotifications() was used to request POST_NOTIFICATION
                // permissions.
            }
        }
    }

    fun handleGrpcErrorStatusReturnValues(error: GrpcFunctionErrorStatusEnum) {

        when (error) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)
                displayErrorMessage(null)
            }
            GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
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
                    ::handleGrpcErrorStatusReturnValues,
                    ::displayErrorMessage
                )
            }
            GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE -> {
                loginViewModel.clearAllUserDataAndStopObjects()
                val alertDialog =
                    ErrorAlertDialogFragment(
                        getString(R.string.logged_in_elsewhere_title),
                        getString(R.string.logged_in_elsewhere_body)
                    ) { _, _ ->
                        navigateToSelectMethodAndClearBackStack()
                    }
                alertDialog.isCancelable = false
                alertDialog.show(supportFragmentManager, "fragment_alert")
            }
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID -> {

                //NOTE: There is a situation where the login token can expire while the message is set
                // to 'server is temporarily down' (servers are down for example). And without this condition
                // it will 'pulse' between 'connection error' and 'server is temporarily down'.
                if (binding.activityMainErrorTextView.visibility != View.VISIBLE || binding.activityMainErrorTextView.text != resources.getString(
                        R.string.server_down_error
                    )
                ) {
                    displayErrorMessage(resources.getString(R.string.general_login_token_expired))
                }

                Log.i(
                    "loginFunctionsFreeze",
                    "LoginActivity beginLoginToServerIfNotAlreadyRunning()"
                )

                loginViewModel.beginLoginToServerWhenReceivedInvalidToken(GlobalValues.MAIN_ACTIVITY)
            }
            GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.function_called_too_quickly_error,
                    Toast.LENGTH_LONG
                ).show()
            }
            GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO -> {
                loginViewModel.clearAllUserDataAndStopObjects()
                runUnmanageableErrorDialog()
            }
            GrpcFunctionErrorStatusEnum.LOG_USER_OUT -> {

                loginViewModel.logoutUserAndClearAccountInDatabase()
                runUnmanageableErrorDialog()
            }
            GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED -> {

                //this will make it consistent with how LoginFunctions handles
                // suspended
                loginViewModel.clearAllUserDataAndStopObjects()

                Log.i("Suspended_call", "handleGrpcErrorStatusReturnValues() LoginActivity.kt")
                displaySuspendedDialog(
                    applicationContext,
                    supportFragmentManager,
                    resources.getString(R.string.generic_suspended_reason),
                    -1,
                    navigateToSelectMethod = { navigateToSelectMethodAndClearBackStack() }
                )
            }
            GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED -> {

                //this will make it consistent with how LoginFunctions handles
                // banned
                loginViewModel.clearAllUserDataAndStopObjects()

                displayBannedDialog(
                    applicationContext,
                    supportFragmentManager,
                    resources.getString(R.string.generic_banned_reason),
                    navigateToSelectMethod = { navigateToSelectMethodAndClearBackStack() }
                )
            }
            GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION -> {
                //TODO: will need to hide anything that is subscription related
            }
        }
    }

    private fun handleLoginStateChanged(loginResponse: LoginFunctionReturnValue) {
        displayErrorMessage(null)
        Log.i("handleLoginStateChanged", loginResponse.loginFunctionStatus.toString())

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.ConnectionError -> {
                displayErrorMessage(resources.getString(R.string.general_connection_error))
            }
            is LoginFunctionStatus.ServerDown -> {
                displayErrorMessage(resources.getString(R.string.server_down_error))
            }
            is LoginFunctionStatus.AttemptingToLogin,
            is LoginFunctionStatus.Idle, //can be called when manual login called and server is down or connection error
            -> {
                //do nothing here
                //runUnmanageableErrorDialog()
            }
            is LoginFunctionStatus.DoNothing -> {
                val errorMessage =
                    "LoginFunctionStatus.DoNothing was returned to LoginActivity, this should never happen."

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    this.applicationContext
                )
            }
            is LoginFunctionStatus.ErrorLoggingIn -> {

                when ((loginResponse.loginFunctionStatus as LoginFunctionStatus.ErrorLoggingIn).errorEnum) {
                    LoginErrorsEnum.UNMANAGEABLE_ERROR -> {

                        //error for these has already been stored
                        if (navigationController.currentDestination?.id == R.id.loginSelectMethodFragment) {
                            //this should never happen really (error for this is already stored)

                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.login_activity_android_errors,
                                Toast.LENGTH_LONG
                            ).show()

                            //crash app with an exception to try to clear RAM to fix the error
                            throw LetsGoRuntimeException(getString(R.string.login_activity_lets_go_runtime_exception))
                        } else {
                            runUnmanageableErrorDialog()
                        }
                    }
                    LoginErrorsEnum.LOGGED_IN_ELSEWHERE -> {
                        val alertDialog =
                            ErrorAlertDialogFragment(
                                getString(R.string.logged_in_elsewhere_title),
                                getString(R.string.logged_in_elsewhere_body)
                            ) { _, _ ->
                                navigateToSelectMethodAndClearBackStack()
                            }
                        alertDialog.isCancelable = false
                        alertDialog.show(supportFragmentManager, "fragment_alert")
                    }
                    LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED -> {
                        Log.i("Suspended_call", "handleLoginStateChanged() LoginActivity.kt")
                        displaySuspendedDialog(
                            applicationContext,
                            supportFragmentManager,
                            loginResponse.response.timeOutMessage,
                            loginResponse.response.timeOutDurationRemaining,
                            navigateToSelectMethod = { navigateToSelectMethodAndClearBackStack() }
                        )
                    }
                    LoginErrorsEnum.ACCOUNT_CLOSED_BANNED -> {
                        displayBannedDialog(
                            applicationContext,
                            supportFragmentManager,
                            loginResponse.response.timeOutMessage,
                            navigateToSelectMethod = { navigateToSelectMethodAndClearBackStack() }
                        )
                    }
                    LoginErrorsEnum.OUTDATED_VERSION -> {
                        val alertDialog =
                            ErrorAlertDialogFragment(
                                getString(R.string.outdated_version_dialog_title),
                                getString(R.string.outdated_version_dialog_body)
                            ) { _, _ ->
                                navigateToSelectMethodAndClearBackStack()
                            }
                        alertDialog.isCancelable = false
                        alertDialog.show(supportFragmentManager, "fragment_alert")
                    }
                }
            }
            is LoginFunctionStatus.VerificationOnCoolDown,
            is LoginFunctionStatus.NoValidAccountStored,
            is LoginFunctionStatus.RequiresAuthentication,
            is LoginFunctionStatus.LoggedIn,
            -> {
                //these depend on the fragment
            }
        }
    }

    private fun runUnmanageableErrorDialog() {

        Log.i("unmanageableErr", Log.getStackTraceString(Exception()))

        loginViewModel.setVariablesToDefaults()

        val alertDialog =
            ErrorAlertDialogFragment(
                getString(R.string.error_dialog_title),
                getString(R.string.error_dialog_body)
            ) { _, _ ->

                //don't need to log out here, if it gets this message then the login helper will auto log out on the server
                navigateToSelectMethodAndClearBackStack()
            }
        alertDialog.isCancelable = false
        alertDialog.show(supportFragmentManager, "fragment_alert")
    }

    fun navigateToSelectMethodAndClearBackStack() {
        Log.i(
            "navControllerLog",
            "navigateToSelectMethodAndClearBackStack(): ${navigationController.currentDestination}\nid: ${navigationController.currentDestination?.id}"
        )

        if (activityPaused) {
            navigateToFragmentOnResume.add(
                NavigateAfterResume(
                    NavFunctionToCall.NAVIGATE_TO_SELECT_METHOD
                )
            )
        }
        //NOTE: navigate must be called from the Main Thread, so this will automatically handle concurrency issues
        else if (navigationController.currentDestination?.id != R.id.loginSelectMethodFragment) {
            val navOpts =
                if (navigationController.currentDestination?.id == R.id.splashScreenFragment) {
                    NavOptions.Builder()
                        .setPopUpTo(R.id.splashScreenFragment, true)
                        .build()
                } else {
                    NavOptions.Builder()
                        .setPopUpTo(R.id.loginSelectMethodFragment, true)
                        .build()
                }

            navigationController.navigate(R.id.loginSelectMethodFragment, null, navOpts)
        }
    }

    //This takes ids defined inside 'navigation_host.xml'
    //R.id.loginGetBirthdayFragment
    //R.id.loginGetEmailFragment
    //R.id.loginGetGenderFragment
    //R.id.loginGetNameFragment
    //R.id.loginGetPhoneNumberFragment
    //R.id.loginGetPicturesFragment
    //R.id.loginSelectMethodFragment
    //R.id.loginShowRulesFragment
    //R.id.splashScreenFragment
    //R.id.verifyPhoneNumbersFragment
    //R.id.selectCategoriesFragment
    //NOTE: this is meant to be called on the Main thread
    fun navigate(currentFragment: Int, destinationAction: Int) {

        //If activity is paused, cannot run navigation, put it off until activity is back in foreground.
        if (activityPaused) {
            navigateToFragmentOnResume.add(
                NavigateAfterResume(
                    NavFunctionToCall.NAVIGATE_NO_BUNDLE,
                    currentFragment,
                    destinationAction
                )
            )
            return
        }

        when {
            navigationController.currentDestination?.id != currentFragment -> {
                val errorMessage =
                    "Navigate was called from a fragment that the user is not currently viewing.\n" +
                            "navigationController.currentDestination.id: ${navigationController.currentDestination?.id}\n" +
                            "currentFragment: $currentFragment"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    this.applicationContext
                )
            }
            this.lifecycle.currentState > Lifecycle.State.CREATED -> { //if the state is Started or Resumed
                navigationController.navigate(destinationAction)
            }
            //NOTE: This case is needed if the SplashScreenFragment is stopped before it completes the initial loading.
            else -> { //if the state is lower than started or resumed, run the navigation when the fragment reaches onStart()
                runOnStart = {
                    navigationController.navigate(destinationAction)
                }
            }
        }
    }

    fun navigate(currentFragment: Int, destinationAction: Int, argumentBundle: Bundle?) {

        //If activity is paused, cannot run navigation, put it off until activity is back in foreground.
        if (activityPaused) {
            navigateToFragmentOnResume.add(
                NavigateAfterResume(
                    NavFunctionToCall.NAVIGATE_WITH_BUNDLE,
                    currentFragment,
                    destinationAction,
                    argumentBundle
                )
            )
        } else if (navigationController.currentDestination?.id == currentFragment) {
            navigationController.navigate(destinationAction, argumentBundle)
        } else {
            val errorMessage =
                "Navigate was called from a fragment that the user is not currently viewing.\n" +
                        "navigationController.currentDestination.id: ${navigationController.currentDestination?.id}\n" +
                        "currentFragment: $currentFragment"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                this.applicationContext
            )
        }
    }

    //this function can be called by a coRoutine and so needs to be in main context
    private fun displayErrorMessage(errorMessage: String?) {
        if (errorMessage == null || errorMessage == "") {
            binding.activityMainErrorTextView.visibility = View.GONE
            binding.activityMainErrorTextView.text = null
        } else {
            binding.activityMainErrorTextView.text = errorMessage
            binding.activityMainErrorTextView.visibility = View.VISIBLE
        }
    }

    //Leaving this here in case a loading state is needed.
//    suspend fun setLoadingState(loadingEnabled: Boolean) = withContext(Dispatchers.Main) {
//        _binding?.let {
//            if (loadingEnabled) { //if loading is enabled
//                displayErrorMessage("")
//                binding.activityMainLoadingProgressBar.visibility = View.VISIBLE
//                fragmentContainer?.visibility = View.GONE
//            } else { //if loading is not enabled
//                binding.activityMainLoadingProgressBar.visibility = View.GONE
//                fragmentContainer?.visibility = View.VISIBLE
//            }
//        }
//    }

    fun setHalfGlobeImagesDisplayed(displayedImages: Boolean) {
        _binding?.let {
            if (displayedImages) {
                binding.getPhoneNumberTopGlobeImageView.visibility = View.VISIBLE
                binding.getPhoneNumberBottomGlobeImageView.visibility = View.VISIBLE
            } else {
                binding.getPhoneNumberTopGlobeImageView.visibility = View.GONE
                binding.getPhoneNumberBottomGlobeImageView.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //NOTE: This line is needed inside the application repository (see onStart() in application repository for details),
        // however not here because the chat worker will not start if all info is not collected from the user (it is
        // inside the loginObserver lambda in ChatStreamWorker.kt)
        //NOTE: While it would be nice to implement this even though it is not needed, it also has a problem in that it will
        // update whatever fragment instanceID is stored at the moment and ruin the way a lot of the fragments get subscription
        // values
        //loginViewModel.subscribeToLoginFunctions(GlobalValues.MAIN_ACTIVITY)

        runOnStart()
        runOnStart = {}
    }

    override fun onPause() {
        //Dialogs and menus must be dismissed in onPause().  If they are dismissed too late then when
        // the activity is re-created (such as on a rotation change) the application will crash.
        dismissAllDialogs(supportFragmentManager)

        activityPaused = true

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        activityPaused = false

        for (funcCall in navigateToFragmentOnResume) {
            when (funcCall.functionType) {
                NavFunctionToCall.NAVIGATE_TO_SELECT_METHOD -> {
                    navigateToSelectMethodAndClearBackStack()
                }
                NavFunctionToCall.NAVIGATE_NO_BUNDLE -> {
                    navigate(funcCall.currentFragment, funcCall.destinationAction)
                }
                NavFunctionToCall.NAVIGATE_WITH_BUNDLE -> {
                    navigate(
                        funcCall.currentFragment,
                        funcCall.destinationAction,
                        funcCall.argumentBundle
                    )
                }
            }
        }
        navigateToFragmentOnResume.clear()
    }

    override fun onStop() {
        super.onStop()
        removeLocationSnackBar()
    }

    override fun onDestroy() {
        requestLocation = null

        _binding = null
        fragmentContainer = null

        connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)
        super.onDestroy()
    }
}
