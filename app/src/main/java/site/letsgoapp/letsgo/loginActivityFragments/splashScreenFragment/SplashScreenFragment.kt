package site.letsgoapp.letsgo.loginActivityFragments.splashScreenFragment

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentSplashScreenBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.*

@SuppressLint("CustomSplashScreen")
class SplashScreenFragment : Fragment() {

    companion object {
        @VisibleForTesting
        const val MINIMUM_TIME_TO_DISPLAY_SCREEN_MS = 300L

        //semi-arbitrary delay here, it must be longer than a possible login (2*(gRPC_Load_Balancer_Deadline_Time+gRPC_Short_Call_Deadline_Time))
        // and longer than MINIMUM_TIME_TO_DISPLAY_SCREEN_MS
        private const val TIME_BEFORE_NAVIGATING_TO_SELECT_METHOD_MS = 45L * 1000L
    }

    private var _binding: FragmentSplashScreenBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels()

    private lateinit var thisFragmentInstanceID: String

    private lateinit var loginObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>
    private lateinit var setDrawablesToDatabaseObserver: Observer<EventWrapperWithKeyString<Unit>>

    private lateinit var sharedPreferences: SharedPreferences

    private var loginActivity: LoginActivity? = null

    //The purpose of this handler is just in case something unforeseen happens and the app gets 'stuck' on
    // this fragment. It will navigate to select method fragment so the user can interact and (hopefully)
    // clear up any problems.
    private val navigateToSelectMethodScreen = Handler(Looper.getMainLooper())
    private val navigateToSelectMethodToken = "Navigate_To_Select_Method_Screen_"

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var fragmentStartTime = SystemClock.uptimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.i("whereAmI", "SplashScreenFragment")

        _binding = FragmentSplashScreenBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        loginActivity = requireActivity() as LoginActivity

        loginActivity?.setHalfGlobeImagesDisplayed(false)

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onDestroyView() {
        navigateToSelectMethodScreen.removeCallbacksAndMessages(navigateToSelectMethodToken)

        sharedLoginViewModel.loginFunctionData.removeObserver(loginObserver)
        sharedLoginViewModel.setDrawablesInDatabaseInfo.removeObserver(setDrawablesToDatabaseObserver)
        _binding = null
        loginActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeLogin()
    }

    private fun runCallbackAfterMinimumTimeElapsed(block: () -> Unit) {
        val timeFragmentHasBeenRunning = SystemClock.uptimeMillis() - fragmentStartTime

        if(timeFragmentHasBeenRunning >= MINIMUM_TIME_TO_DISPLAY_SCREEN_MS) {
            block()
        } else {
            Log.i("lifecycleStuff", "posting block at time")

            navigateToSelectMethodScreen.postAtTime(
                {
                    Log.i("lifecycleStuff", "running posted block")
                    block()
                },
                navigateToSelectMethodToken,
                SystemClock.uptimeMillis() + (MINIMUM_TIME_TO_DISPLAY_SCREEN_MS - timeFragmentHasBeenRunning)
            )
        }
    }

    private fun initializeLogin() {

        Log.i("ordering_stuff", "Splash Screen onViewCreated()")

        fragmentStartTime = SystemClock.uptimeMillis()

        binding.splashScreenTextView.text = null

        loginObserver = Observer { loginResponseEvent ->
            val response = loginResponseEvent.getContentIfNotHandled(thisFragmentInstanceID)
            response?.let {
                Log.i("lifecycleStuff", "SplashScreenFragment received loginObserver result")

                runCallbackAfterMinimumTimeElapsed {
                    handleLoadingResponse(it)
                }
            }
        }

        setDrawablesToDatabaseObserver = Observer { results ->
            val accountType = results.getContentIfNotHandled(thisFragmentInstanceID)
            if (accountType != null) {
                Log.i("lifecycleStuff", "SplashScreenFragment received setDrawablesToDatabaseObserver result")

                runCallbackAfterMinimumTimeElapsed {
                    navigateToSelectMethodAndClearBackStack()
                }
            }
        }

        //These should be run even if the app is minimized, so setting them to observeForever instead of by
        // lifecycle.
        sharedLoginViewModel.loginFunctionData.observeForever(loginObserver)
        sharedLoginViewModel.setDrawablesInDatabaseInfo.observeForever(setDrawablesToDatabaseObserver)

        sharedPreferences = requireActivity().getSharedPreferences(
            getString(R.string.shared_preferences_lets_go_key),
            MODE_PRIVATE
        )

        navigateToSelectMethodScreen.postAtTime(
            {
                val errorMessage = "SplashScreenFragment failed to navigate away in a timely manner.\n" +
                        "Forced navigation to LoginSelectMethodFragment."

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                navigateToSelectMethodAndClearBackStack()
            },
            navigateToSelectMethodToken,
            SystemClock.uptimeMillis() + TIME_BEFORE_NAVIGATING_TO_SELECT_METHOD_MS
        )

        val savedVersionCode =
            sharedPreferences.getInt(getString(R.string.shared_preferences_version_code_key), 0)

        Log.i("savedVersionCode", "savedVersionCode $savedVersionCode")

        //NOTE: The reason this is here is if it is put inside the ApplicationClass any
        // actions that take time will need to be done BEFORE the user gets to the splash screen
        // and there isn't any really good reason I can see to do it, even if the chat stream starts up
        // by itself it will just request the Version_Code itself
        when (savedVersionCode) {
            GlobalValues.Lets_GO_Version_Number -> { //this version has run before on this device
                Log.i("lifecycleStuff", "SplashScreenFragment about to run beginManualLogin")
                //attempt to log account in
                sharedLoginViewModel.beginManualLoginToServerExtractAccountInfo(
                    thisFragmentInstanceID
                )
            }
            0 -> { //first time app has run on this device
                Log.i(
                    "loginTesting",
                    "running setDrawablesToDatabaseIndexing()"
                )
                sharedLoginViewModel.setDrawablesToDatabaseIndexing(
                    thisFragmentInstanceID
                )

                sharedPreferences.edit().putInt(
                    getString(R.string.shared_preferences_version_code_key),
                    GlobalValues.Lets_GO_Version_Number
                ).apply()
            }
            else -> { //first time app is running since app was updated

                Log.i("run_version_stuff", "run version stuff")

                sharedLoginViewModel.updateIconDrawablesForNewestVersion()

                sharedLoginViewModel.beginManualLoginToServerExtractAccountInfo(
                    thisFragmentInstanceID
                )

                //attempt to log account in
                sharedPreferences.edit().putInt(
                    getString(R.string.shared_preferences_version_code_key),
                    GlobalValues.Lets_GO_Version_Number
                ).apply()
            }
        }
    }

    private fun handleLoadingResponse(loginResponse: LoginFunctionReturnValue) {

        binding.splashScreenTextView.text = null
        sharedLoginViewModel.processLoginInformation(loginResponse)

        Log.i(
            "loginFunctionsFreeze",
            "SplashScreenFragment handleLoadingResponse() loginResponse.loginFunctionStatus: ${loginResponse.loginFunctionStatus}"
        )

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.Idle,
            is LoginFunctionStatus.AttemptingToLogin,
            is LoginFunctionStatus.ErrorLoggingIn,
            is LoginFunctionStatus.DoNothing -> {
                //Handled in MainActivity
            }
            is LoginFunctionStatus.VerificationOnCoolDown,
            is LoginFunctionStatus.ConnectionError,
            is LoginFunctionStatus.ServerDown -> {
                //navigate to select method screen
                navigateToSelectMethodAndClearBackStack()
            }
            is LoginFunctionStatus.LoggedIn -> {

                checkLoginAccessStatus(loginResponse,
                    requireActivity().applicationContext,
                    childFragmentManager,
                    navigateToStartCollectingInfo = {
                        Log.i(
                            "loginFunctionsFreeze",
                            "SplashScreenFragment handleLoadingResponse() navigateToStartCollectingInfo()"
                        )
                        //this could happen if user does not have all info, will require user to log back in
                        sharedLoginViewModel.navigatePastLoginSelectFragment =
                            SharedLoginViewModel.NavigatePastLoginSelectFragment.NAVIGATE_TO_COLLECT_EMAIL_FRAGMENT
                        navigateToSelectMethodAndClearBackStack()
                    },
                    navigateToSelectMethod = { navigateToSelectMethodAndClearBackStack() },
                    navigateToPrimaryApplicationActivity = {
                        Log.i(
                            "loginFunctionsFreeze",
                            "SplashScreenFragment handleLoadingResponse() navigateToPrimaryApplicationActivity()"
                        )

                        //navigate to App Activity (this will work even if the current fragment and/or activity are stopped)
                        val appActivityIntent = Intent(loginActivity, AppActivity::class.java)
                        appActivityIntent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(appActivityIntent)

                        requireActivity().finish()
                    },
                    handleLG_ErrReturn = {
                        val errorString =
                            getString(
                                R.string.general_login_error_requires_info_invalid,
                                loginResponse.response.accessStatus
                            )

                        storeLoginError(
                            requireActivity().applicationContext, errorString, loginResponse,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors(),
                            errorStore
                        )

                        navigateToSelectMethodAndClearBackStack()
                    }
                )
            }
            is LoginFunctionStatus.NoValidAccountStored -> {
                //NOTE: This could mean a facebook or google account was attempted to log into, however no user account exists yet for it and
                // so it requires a phone number, this is true when loginReturnValue.response.returnStatus == LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT

                //navigate to select method fragment
                navigateToSelectMethodAndClearBackStack()
            }
            is LoginFunctionStatus.RequiresAuthentication -> {
                if (!(loginResponse.loginFunctionStatus as LoginFunctionStatus.RequiresAuthentication).smsOnCoolDown) {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        getString(R.string.sms_verification_requires_authorization_verification_code_sent),
                        Toast.LENGTH_LONG
                    ).show()
                }

                //this could happen if they deleted their account on a different device, will require user to log back in
                sharedLoginViewModel.navigatePastLoginSelectFragment =
                    SharedLoginViewModel.NavigatePastLoginSelectFragment.NAVIGATE_TO_VERIFY_FRAGMENT
                navigateToSelectMethodAndClearBackStack()
            }
        }
    }

    private fun navigateToSelectMethodAndClearBackStack() {
        //Remove this callback when a navigation occurs because this fragment/activity could be stopped and so the
        // navigation could be delayed. Don't want the callback running if a navigation is queued.
        navigateToSelectMethodScreen.removeCallbacksAndMessages(navigateToSelectMethodToken)

        Log.i("lifecycleStuff", "navigateToSelectMethodAndClearBackStack() loginActivity == null: ${loginActivity==null}")
        loginActivity?.navigateToSelectMethodAndClearBackStack()
    }
}
