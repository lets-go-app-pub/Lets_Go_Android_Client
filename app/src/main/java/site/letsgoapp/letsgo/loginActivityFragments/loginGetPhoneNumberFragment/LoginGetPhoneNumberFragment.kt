package site.letsgoapp.letsgo.loginActivityFragments.loginGetPhoneNumberFragment

import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginGetPhoneNumberBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.*

class LoginGetPhoneNumberFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentLoginGetPhoneNumberBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
    private lateinit var thisFragmentInstanceID: String

    private lateinit var loginObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>

    private var loginActivity: LoginActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginGetPhoneNumberBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        if(initializeLoginActivity) {
            loginActivity = requireActivity() as LoginActivity
        }

        binding.phoneNumberEditText.hint = resources.getString(R.string.phone_login_number_hint)

        loginActivity?.setHalfGlobeImagesDisplayed(true)

        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        loginActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.phoneNumberEditText.addTextChangedListener(PhoneNumberFormattingTextWatcher())

        binding.loginPhoneNumberErrorTextView.text = null
        setLoadingState(false)

        loginObserver = Observer { loginResponseEvent ->
            val response = loginResponseEvent.getContentIfNotHandled(thisFragmentInstanceID)
            response?.let {
                handleLoadingResponse(it)
            }
        }

        binding.phoneNumberEditText.setSafeEditorActionClickListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                //check phone number
                val tempPhoneNumber = binding.phoneNumberEditText.text.toString()

                binding.loginPhoneNumberErrorTextView.text = null

                if (!sharedLoginViewModel.loginToSetPhoneNumber(
                        tempPhoneNumber,
                        thisFragmentInstanceID
                    )
                ) { //if phone number is invalid
                    binding.loginPhoneNumberErrorTextView.setText(R.string.phone_login_invalid_phone_number_entered)
                } else { //if phone number is valid
                    this.hideKeyboard()
                    setLoadingState(true)
                    Log.i("LoginGetPhoneNumFrag", "Valid phone number")
                }
                return@setSafeEditorActionClickListener true
            }
            return@setSafeEditorActionClickListener false
        }

        binding.getPhoneContinueButton.setSafeOnClickListener {
            //check phone number
            val tempPhoneNumber = binding.phoneNumberEditText.text.toString()

            binding.loginPhoneNumberErrorTextView.text = null

            if (!sharedLoginViewModel.loginToSetPhoneNumber(
                    tempPhoneNumber,
                    thisFragmentInstanceID
                )
            ) { //if phone number is invalid
                binding.loginPhoneNumberErrorTextView.setText(R.string.phone_login_invalid_phone_number_entered)
            } else { //if phone number is valid
                setLoadingState(true)
                Log.i("LoginGetPhoneNumFrag", "Valid phone number")
            }
        }

        sharedLoginViewModel.loginFunctionData.observe(viewLifecycleOwner, loginObserver)
    }

    private fun handleLoadingResponse(loginResponse: LoginFunctionReturnValue) {
        binding.loginPhoneNumberErrorTextView.text = null
        setLoadingState(false)
        sharedLoginViewModel.processLoginInformation(loginResponse)

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.ErrorLoggingIn,
            is LoginFunctionStatus.Idle,
            is LoginFunctionStatus.AttemptingToLogin,
            is LoginFunctionStatus.DoNothing,
            is LoginFunctionStatus.ConnectionError,
            is LoginFunctionStatus.ServerDown -> {
                //Handled in MainActivity
            }
            is LoginFunctionStatus.LoggedIn -> {

                checkLoginAccessStatus(
                    loginResponse,
                    GlobalValues.applicationContext,
                    childFragmentManager,
                    navigateToStartCollectingInfo = {
                        loginActivity?.navigate(
                            R.id.loginGetPhoneNumberFragment,
                            R.id.action_loginGetPhoneNumberFragment_to_loginGetEmailFragment
                        )
                    },
                    navigateToSelectMethod = {}, //if account is banned or suspended stay here
                    navigateToPrimaryApplicationActivity = {
                        //navigate to App Activity
                        val appActivityIntent = Intent(activity, AppActivity::class.java)
                        appActivityIntent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(appActivityIntent)

                        loginActivity?.finish()
                    },
                    handleLG_ErrReturn = {
                        val errorString =
                            getString(
                                R.string.general_login_error_requires_info_invalid,
                                loginResponse.response.accessStatus
                            )

                        storeLoginError(
                            GlobalValues.applicationContext, errorString, loginResponse,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors(),
                            errorStore
                        )

                        //runs dialog to send user back to login
                        runUnmanageableErrorDialog()
                    }
                )
            }
            is LoginFunctionStatus.NoValidAccountStored -> {
                //NOTE: this can be true when
                // 1) user clicks login for a specific phone number with no internet connection OR server is down
                // 2) LoginFunction will retry
                // 3) it will return NoValidAccountStored
                // NOTE: this can also be true when
                // loginResponse.response.returnStatus == LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT (means a google
                // or facebook account was sent in however a phone number is not stored yet) however google or facebook
                // accounts should never be sent by this fragment
            }
            is LoginFunctionStatus.RequiresAuthentication -> {

                if (!(loginResponse.loginFunctionStatus as LoginFunctionStatus.RequiresAuthentication).smsOnCoolDown) {
                    finishRequestAuthorization(getString(R.string.sms_verification_requires_authorization_verification_code_sent))
                } else {
                    finishRequestAuthorization(getString(R.string.phone_login_sending_sms_verification_code_on_cool_down))
                }
            }
            is LoginFunctionStatus.VerificationOnCoolDown -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.sms_verification_long_cool_down,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    private fun finishRequestAuthorization(toastMessage: String) {
        Toast.makeText(
            GlobalValues.applicationContext,
            toastMessage,
            Toast.LENGTH_LONG
        ).show()

        loginActivity?.navigate(
            R.id.loginGetPhoneNumberFragment,
            R.id.action_loginGetPhoneNumberFragment_to_verifyPhoneNumbersFragment
        )
    }

    private fun runUnmanageableErrorDialog() {

        val alertDialog =
            ErrorAlertDialogFragment(
                getString(R.string.error_dialog_title),
                getString(R.string.error_dialog_body)
            ) { _, _ ->

                //don't need to log out here, if it gets this message then the login helper will auto log out on the server
                loginActivity?.navigateToSelectMethodAndClearBackStack()
            }

        alertDialog.isCancelable = false
        alertDialog.show(childFragmentManager, "fragment_alert")
    }

    private fun setLoadingState(loading: Boolean) {

        if (loading) {
            binding.getPhoneContinueButton.isEnabled = false
            binding.phoneNumberEditText.isEnabled = false

            binding.getPhoneNumberProgressBar.visibility = View.VISIBLE
        } else {
            binding.getPhoneContinueButton.isEnabled = true
            binding.phoneNumberEditText.isEnabled = true

            binding.getPhoneNumberProgressBar.visibility = View.INVISIBLE
        }

    }

}
