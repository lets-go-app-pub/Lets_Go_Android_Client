package site.letsgoapp.letsgo.loginActivityFragments.loginSelectMethodFragment

import account_login_type.AccountLoginTypeEnum
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import email_sending_messages.AccountRecoveryResponse
import email_sending_messages.EmailSentStatus
import error_origin_enum.ErrorOriginEnum
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginSelectMethodBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.*

class LoginSelectMethodFragment : Fragment() {

    private var _binding: FragmentLoginSelectMethodBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels()
    private lateinit var thisFragmentInstanceID: String

    //facebook login
    //~FACEBOOK TAG~
    //private val callbackManager: CallbackManager = CallbackManager.Factory.create()

    private lateinit var loginObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>

    private lateinit var accountRecoveryReturnValueObserver: Observer<EventWrapperWithKeyString<AccountRecoveryReturnValues>>

    private var loginActivity: LoginActivity? = null

    private var googleSignInOptions: GoogleSignInOptions? = null
    private var mGoogleSignInClient: GoogleSignInClient? = null

    private var accountRecoveryDialogFragment: AccountRecoveryDialogFragment? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var beginGoogleSignIn: ActivityResultLauncher<Intent>? =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            Log.i("loginReturnActivity", "result: $result")
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            if (result.resultCode == Activity.RESULT_OK) {
                val task =
                    GoogleSignIn.getSignedInAccountFromIntent(result?.data)
                handleSignInResult(task)
            } else {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.google_cannot_connect_error,
                    Toast.LENGTH_SHORT
                ).show()

                setLoadingState(false)
            }
        }

    //NOTE: this will log out facebook and google every time the fragment is reached
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginSelectMethodBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        loginActivity = requireActivity() as LoginActivity

        loginActivity?.setHalfGlobeImagesDisplayed(false)

        Log.i("whereAmI", "LoginSelectMethodFragment")
        //if this was set to go to a specific chat room however did not make it for whatever reason, clear the chat room Id
        GlobalValues.loginToChatRoomId = ""

        //GOOGLE LOGIN
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        googleSignInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestId()
                .requestProfile()
                .requestEmail()
                .build()

        mGoogleSignInClient =
            GoogleSignIn.getClient(requireContext(), googleSignInOptions!!)

        googleLogout()

        //~FACEBOOK TAG~
        //facebookLogout()

        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        loginActivity = null

        //Log out at beginning and end of this fragment. No need to hold only a login just need
        // the basic info from it.
        googleLogout()

        //~FACEBOOK TAG~
        //facebookLogout()

        googleSignInOptions = null
        mGoogleSignInClient = null

        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Allow hyperlink to be clicked.
        binding.selectMethodTermsOfServiceLink.movementMethod = LinkMovementMethod.getInstance()

        when (sharedLoginViewModel.navigatePastLoginSelectFragment) {
            SharedLoginViewModel.NavigatePastLoginSelectFragment.NAVIGATE_TO_VERIFY_FRAGMENT -> {
                loginActivity?.navigate(
                    R.id.loginSelectMethodFragment,
                    R.id.action_loginSelectMethodFragment_to_verifyPhoneNumbersFragment
                )
            }
            SharedLoginViewModel.NavigatePastLoginSelectFragment.NAVIGATE_TO_COLLECT_EMAIL_FRAGMENT -> {
                loginActivity?.navigate(
                    R.id.loginSelectMethodFragment,
                    R.id.action_loginSelectMethodFragment_to_loginGetEmailFragment
                )
            }
            SharedLoginViewModel.NavigatePastLoginSelectFragment.NO_NAVIGATION -> {

                sharedLoginViewModel.newAccountInfo.requiresEmailAddressVerification = true
                sharedLoginViewModel.resetHardSetsToDefaults()

                binding.loginSelectMethodTextView.text = null
                setLoadingState(false)

                loginObserver =
                    Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>> { loginResponseEvent ->
                        val response =
                            loginResponseEvent.getContentIfNotHandled(thisFragmentInstanceID)
                        response?.let {
                            handleLoadingResponse(it)
                        }
                    }

                accountRecoveryReturnValueObserver =
                    Observer { wrapper ->
                        val response = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
                        if (response != null) {
                            handleAccountRecoveryReturnValue(response)
                        }
                    }

                // Account Recovery
                binding.retrieveAccountTextView.setSafeOnClickListener {

                    accountRecoveryDialogFragment =
                        AccountRecoveryDialogFragment(
                            { formattedPhoneNumber ->
                                setLoadingState(true)

                                sharedLoginViewModel.runBeginAccountRecovery(
                                    formattedPhoneNumber,
                                    thisFragmentInstanceID,
                                )
                            },
                            { dialog, _ ->
                                dialog.dismiss()
                            }
                        )
                    //alertDialog.isCancelable = false
                    accountRecoveryDialogFragment?.show(
                        childFragmentManager,
                        "fragment_alert_retrieve_account"
                    )
                }

                // Phone Login
                binding.loginWithPhoneButton.setSafeOnClickListener {
                    setLoadingState(true)
                    sharedLoginViewModel.loginAccountType =
                        AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT
                    //an important point here is that this screen should only be reached when the user is NOT logged in
                    loginActivity?.navigate(
                        R.id.loginSelectMethodFragment,
                        R.id.action_loginSelectMethodFragment_to_loginGetPhoneNumberFragment
                    )
                }

                binding.fragmentLoginSelectMethodDummyGoogleButton.setSafeOnClickListener {
                    Log.i("clickingButton", "clicking")
                    //binding.googleSignInButton.performClick()
                    setLoadingState(true)
                    val signInIntent: Intent? = mGoogleSignInClient?.signInIntent
                    try {
                        beginGoogleSignIn?.launch(signInIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.google_cannot_connect_error,
                            Toast.LENGTH_SHORT
                        ).show()

                        val errorString =
                            "Error when running Google sign in, ActivityNotFoundException returned."

                        storeErrorChatRoomInfoFragment(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                        )

                    }
                }

                //FACEBOOK LOGIN
                //public_profile comes with fields id, first_name, last_name, middle_name, name, name_format, picture and short_name
                //https://developers.facebook.com/docs/graph-api/reference/user/#default-public-profile-fields
                //NOTE FROM FACEBOOK "If you ask for permissions other than email or public_profile, you must submit your app for app review so Facebook can confirm that the app uses the data in intended ways and safeguards user privacy."
                //https://developers.facebook.com/docs/permissions/reference#p
                //~FACEBOOK TAG~
                /*
                val permissions
                        : List<String> =
                    listOf("public_profile", "email")
                binding.facebookLoginButton.setPermissions(permissions)
                binding.facebookLoginButton.setFragment(this)
                // Facebook Callback Login
                binding.facebookLoginButton.setSafeOnClickListener {
                    setLoadingState(true)
                }

                binding.fragmentLoginSelectMethodDummyFacebookButton.setSafeOnClickListener {
                    binding.facebookLoginButton.performClick()
                }

                binding.facebookLoginButton.registerCallback(
                    callbackManager,
                    object : FacebookCallback<LoginResult?> {

                        override fun onSuccess(result: LoginResult?) {
                            Log.i("facebookLogin", "onSuccess")
                            val request =
                                GraphRequest.newMeRequest(result?.accessToken) { jsonObject, response ->

                                    binding.loginSelectMethodTextView.text = null

                                    if (response?.error == null) {

                                        try {
                                            if (jsonObject?.has("email") == true) {
                                                sharedLoginViewModel.newAccountInfo.emailAddress =
                                                    jsonObject.get("email").toString()
                                                sharedLoginViewModel.newAccountInfo.requiresEmailAddressVerification =
                                                    false
                                                sharedLoginViewModel.newAccountInfo.emailAddressStatus =
                                                    StatusOfClientValueEnum.HARD_SET
                                            }
                                        } catch (_: Exception) {
                                        }

                                        try {
                                            if (jsonObject?.has("first_name") == true) {
                                                sharedLoginViewModel.newAccountInfo.firstName =
                                                    jsonObject.get("first_name").toString()
                                                sharedLoginViewModel.newAccountInfo.firstNameStatus =
                                                    StatusOfClientValueEnum.HARD_SET
                                            }
                                        } catch (_: Exception) {
                                        }

                                        try {
                                            sharedLoginViewModel.loginWithAccountID(
                                                jsonObject?.get("id").toString(),
                                                AccountLoginTypeEnum.AccountLoginType.FACEBOOK_ACCOUNT,
                                                thisFragmentInstanceID
                                            ) //token is null checked above
                                        } catch (e: Exception) {
                                            binding.loginSelectMethodTextView.setText(R.string.facebook_login_error)
                                        }

                                    } else { //facebook login error
                                        setLoadingState(false)
                                        binding.loginSelectMethodTextView.text = getString(
                                            R.string.facebook_login_error_message,
                                            response.error?.errorMessage
                                        )
                                    }

                                }

                            val parameters = Bundle()
                            parameters.putString(
                                "fields",
                                "id,email,first_name,gender,birthday"
                            ) //set as single word names
                            request.parameters = parameters
                            request.executeAsync()
                        }

                        override fun onCancel() {
                            Log.i("facebookLogin", "onCancel")
                            setLoadingState(false)
                        }

                        override fun onError(error: FacebookException) {
                            Log.i("facebookLogin", "onError")
                            setLoadingState(false)
                            binding.loginSelectMethodTextView.text =
                                getString(
                                    R.string.facebook_login_error_message,
                                    error.localizedMessage
                                )
                        }
                    })
*/
/*
                binding.googleSignInButton.setSize(SignInButton.SIZE_STANDARD)
                binding.googleSignInButton.setSafeOnClickListener {

                    setLoadingState(true)
                    val signInIntent: Intent? = mGoogleSignInClient?.signInIntent
                    try {
                        beginGoogleSignIn?.launch(signInIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.google_cannot_connect_error,
                            Toast.LENGTH_SHORT
                        ).show()

                        val errorString =
                            "Error when running Google sign in, ActivityNotFoundException returned."

                        storeErrorChatRoomInfoFragment(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                        )

                    }
                }
*/

                sharedLoginViewModel.loginFunctionData.observe(viewLifecycleOwner, loginObserver)
                sharedLoginViewModel.accountRecoveryReturnValue.observe(
                    viewLifecycleOwner,
                    accountRecoveryReturnValueObserver
                )
            }
        }

    }

    //Google sign in function
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            binding.loginSelectMethodTextView.text = null

            val account =
                completedTask.getResult(ApiException::class.java)

            if (account != null) {

                val emailRegex = Regex(GlobalValues.EMAIL_REGEX_STRING)
                val emailValid = emailRegex.matches(account.email ?: "~")

                if (!emailValid) {
                    val errorString = "When logging into Google an invalid email was returned." +
                            "email: ${account.email}\n"

                    storeErrorChatRoomInfoFragment(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                    )

                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.google_cannot_connect_error,
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.loginSelectMethodTextView.text = getString(R.string.google_login_error)

                    setLoadingState(false)
                    return
                }

                sharedLoginViewModel.newAccountInfo.emailAddress = account.email ?: "~"
                sharedLoginViewModel.newAccountInfo.requiresEmailAddressVerification = false
                sharedLoginViewModel.newAccountInfo.emailAddressStatus =
                    StatusOfClientValueEnum.HARD_SET

                sharedLoginViewModel.newAccountInfo.firstName =
                    if (account.givenName.isNullOrEmpty()) {
                        "~"
                    } else {
                        account.givenName ?: "~"
                    }

                if (sharedLoginViewModel.newAccountInfo.firstName != "~") {
                    sharedLoginViewModel.newAccountInfo.firstNameStatus =
                        StatusOfClientValueEnum.HARD_SET
                }

                //GOOGLE LOG IN
                //NOTE: The email is used as the Google sign in. This is because in their upcoming API, the id is
                // the email. This will allow for consistency across their APIs.
                sharedLoginViewModel.loginWithAccountID(
                    sharedLoginViewModel.newAccountInfo.emailAddress,
                    AccountLoginTypeEnum.AccountLoginType.GOOGLE_ACCOUNT, thisFragmentInstanceID
                ) //token is null checked above

            } else {
                //Can turn this off from interface if it spams me with errors.
                val errorString = "Google login account returned null."

                storeErrorChatRoomInfoFragment(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_LOW
                )

                setLoadingState(false)
                binding.loginSelectMethodTextView.text = getString(R.string.google_login_error)
            }

        } catch (e: ApiException) {

            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(
                "LoginSelectMethodFrag",
                "e.statusCode: ${e.statusCode} signInResult:failed code=" + GoogleSignInStatusCodes.getStatusCodeString(
                    e.statusCode
                )
            )

            when (e.statusCode) {
                10 -> { //if DEVELOPER_ERROR
                    val errorString = "Received error from google login '${
                        GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
                    }'."

                    storeErrorChatRoomInfoFragment(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                    )

                    binding.loginSelectMethodTextView.setText(R.string.google_login_error)
                }
                12501 -> { //if 'Sign in action cancelled'
                    //This can occur if user cancels the sign in (navigates away from it or closes the dialog)
                    binding.loginSelectMethodTextView.text = null
                }
                else -> { //if not DEVELOPER_ERROR
                    binding.loginSelectMethodTextView.text =
                        getString(
                            R.string.google_login_error_message,
                            GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
                        )
                }
            }
            setLoadingState(false)
        }
    }

    private fun handleLoadingResponse(loginResponse: LoginFunctionReturnValue) {

        binding.loginSelectMethodTextView.text = null
        setLoadingState(false)

        sharedLoginViewModel.processLoginInformation(loginResponse)

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.ConnectionError,
            is LoginFunctionStatus.ServerDown,
            is LoginFunctionStatus.Idle,
            is LoginFunctionStatus.ErrorLoggingIn,
            is LoginFunctionStatus.DoNothing,
            is LoginFunctionStatus.AttemptingToLogin,
            -> {
                //NOTE: handled inside MainActivity
            }
            is LoginFunctionStatus.NoValidAccountStored -> {
                //NOTE: this could mean a login attempt was made to log into account stored inside database, however that response should be
                // handled by the splash screen fragment, the error is stored inside LoginFunctions though,
                // this is true when loginResponse.response.returnStatus == LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT

                if ((loginResponse.loginFunctionStatus as LoginFunctionStatus.NoValidAccountStored).requiresPhoneNumber) {
                    //if a login was attempted and no account was stored with the info, store the info
                    sharedLoginViewModel.loginAccountID = loginResponse.request.accountId
                    sharedLoginViewModel.loginAccountType = loginResponse.request.accountType

                    loginActivity?.navigate(
                        R.id.loginSelectMethodFragment,
                        R.id.action_loginSelectMethodFragment_to_loginGetPhoneNumberFragment
                    )
                }
            }
            is LoginFunctionStatus.RequiresAuthentication -> {

                if (!(loginResponse.loginFunctionStatus as LoginFunctionStatus.RequiresAuthentication).smsOnCoolDown) {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.sms_verification_requires_authorization_verification_code_sent,
                        Toast.LENGTH_LONG
                    ).show()
                }

                loginActivity?.navigate(
                    R.id.loginSelectMethodFragment,
                    R.id.action_loginSelectMethodFragment_to_verifyPhoneNumbersFragment
                )
            }
            is LoginFunctionStatus.VerificationOnCoolDown -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.sms_verification_long_cool_down,
                    Toast.LENGTH_LONG
                ).show()
            }
            is LoginFunctionStatus.LoggedIn -> {

                checkLoginAccessStatus(loginResponse,
                    requireActivity().applicationContext,
                    childFragmentManager,
                    {
                        loginActivity?.navigate(
                            R.id.loginSelectMethodFragment,
                            R.id.action_loginSelectMethodFragment_to_loginGetEmailFragment
                        )
                    },
                    {}, //if account is banned or suspended stay here
                    {
                        //navigate to App Activity
                        val appActivityIntent = Intent(activity, AppActivity::class.java)
                        appActivityIntent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(appActivityIntent)

                        requireActivity().finish()
                    },
                    {
                        val errorString =
                            getString(
                                R.string.general_login_error_requires_info_invalid,
                                loginResponse.response.accessStatus
                            )

                        storeLoginError(
                            requireActivity().applicationContext,
                            errorString,
                            loginResponse,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors(),
                            errorStore
                        )

                        binding.loginSelectMethodTextView.setText(R.string.general_error)
                    }
                )
            }
        }
    }

    private fun handleAccountRecoveryReturnValue(returnValue: AccountRecoveryReturnValues) {
        setLoadingState(false)

        when (returnValue.errors) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                when (returnValue.response.accountRecoveryStatus) {
                    AccountRecoveryResponse.AccountRecoveryStatus.VALUE_NOT_SET,
                    AccountRecoveryResponse.AccountRecoveryStatus.UNKNOWN,
                    AccountRecoveryResponse.AccountRecoveryStatus.LG_ERROR,
                    AccountRecoveryResponse.AccountRecoveryStatus.UNRECOGNIZED,
                    null,
                    -> {
                        //error should be stored in repository
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.select_method_account_recovery_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.SUCCESS -> {

                        //Currently EmailSentStatus.EMAIL_SUCCESS is the only enum that is returned
                        // with AccountRecoveryStatus.SUCCESS in order to prevent fishing
                        // in the database. See 'NOTE' on the server function for details.
                        when (returnValue.response.emailSentStatus) {
                            EmailSentStatus.EMAIL_SUCCESS -> {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.select_method_account_recovery_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            EmailSentStatus.EMAIL_ON_COOL_DOWN -> {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.select_method_account_recovery_email_cool_down,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            EmailSentStatus.EMAIL_VALUE_NOT_SET,
                            EmailSentStatus.EMAIL_FAILED_TO_BE_SENT,
                            EmailSentStatus.UNRECOGNIZED,
                            null -> {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.select_method_account_recovery_error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.ACCOUNT_SUSPENDED,
                    AccountRecoveryResponse.AccountRecoveryStatus.ACCOUNT_BANNED,
                    -> {
                        //NOTE: these enum values are currently NOT USED by the server,
                        // they are just here for the sake of completeness
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.select_method_account_recovery_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.ACCOUNT_DOES_NOT_EXIST -> {
                        //NOTE: this enum value is currently NOT USED by the server,
                        // it is just here for the sake of completeness
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.select_method_account_recovery_account_does_not_exist,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.OUTDATED_VERSION -> {
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.outdated_version_dialog_body,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.INVALID_PHONE_NUMBER -> {
                        val errorString =
                            "INVALID_PHONE_NUMBER was returned from server during account recovery when" +
                                    " it should be checked on client side.\nphone_number: '${returnValue.phoneNumber}'"

                        storeErrorChatRoomInfoFragment(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                        )

                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.select_method_account_recovery_invalid_phone_number,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    AccountRecoveryResponse.AccountRecoveryStatus.DATABASE_DOWN -> {
                        loginActivity?.handleGrpcErrorStatusReturnValues(GrpcFunctionErrorStatusEnum.SERVER_DOWN)
                    }
                }
            }
            else -> {
                loginActivity?.handleGrpcErrorStatusReturnValues(returnValue.errors)
            }
        }
    }

    //if Google is signed in, sign out
    private fun googleLogout() {
        GoogleSignIn.getLastSignedInAccount(requireContext())?.let {
            mGoogleSignInClient?.signOut()
        }
    }

    private fun setLoadingState(loading: Boolean) {
        if (loading) {

            //~FACEBOOK TAG~
            //binding.facebookLoginButton.isEnabled = false

            //binding.googleSignInButton.isEnabled = false
            binding.fragmentLoginSelectMethodDummyGoogleButton.isEnabled = false
            binding.loginWithPhoneButton.isEnabled = false
            binding.retrieveAccountTextView.isEnabled = false

            binding.LoginSelectMethodPBar.visibility = View.VISIBLE
        } else {

            //~FACEBOOK TAG~
            //binding.facebookLoginButton.isEnabled = true

            //binding.googleSignInButton.isEnabled = true
            binding.fragmentLoginSelectMethodDummyGoogleButton.isEnabled = true
            binding.loginWithPhoneButton.isEnabled = true
            binding.retrieveAccountTextView.isEnabled = true

            binding.LoginSelectMethodPBar.visibility = View.INVISIBLE
        }
    }

    private fun storeErrorChatRoomInfoFragment(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String,
        errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel = ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_UNKNOWN
    ) {
        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            passedErrMsg,
            GlobalValues.applicationContext,
            errorUrgencyLevel
        )
    }

    override fun onPause() {
        accountRecoveryDialogFragment?.dismiss()
        accountRecoveryDialogFragment = null
        super.onPause()
    }

    override fun onDestroy() {
        beginGoogleSignIn = null
        super.onDestroy()
    }
}
