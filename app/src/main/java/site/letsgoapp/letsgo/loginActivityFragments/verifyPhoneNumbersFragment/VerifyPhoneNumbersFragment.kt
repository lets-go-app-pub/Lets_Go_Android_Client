package site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentVerifyPhoneNumbersBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.*
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import java.util.concurrent.atomic.AtomicBoolean

//a reference to this fragment is stored inside the SMSBroadcastIntermediate object
class VerifyPhoneNumbersFragment(
    private val initialTimerTime: Long = -1L,
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentVerifyPhoneNumbersBinding? = null
    private val binding get() = _binding!!

    val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
        @VisibleForTesting get

    private lateinit var thisFragmentInstanceID: String

    private lateinit var smsVerifiedObserver: Observer<EventWrapperWithKeyString<SmsVerificationDataDataHolder>>
    private lateinit var loginObserver: Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>>
    private lateinit var requestNewVerificationCodeCompletedObserver: Observer<EventWrapperWithKeyString<Unit>>

    private val smsBroadcastReceiver = SMSBroadcastReceiver()
    private lateinit var smsRetrieverClient: SmsRetrieverClient
    private val smsRetrieverRunning =
        AtomicBoolean(false) //this is used so that the smsRetriever can not be double called (not sure if it needs to be atomic)

    //Declare timer
    private var cTimer: CountDownTimer? = null

    private var cTimerRunning = false
    private var setLoadingRunning = false

    private var loginActivity: LoginActivity? = null

    private var updateCreateNewDialogFragment: UpdateCreateNewDialogFragment? = null
    private var enterBirthdayDialogFragment: EnterBirthdayDialogFragment? = null
    private var yesNoDialogFragment: YesNoDialogFragment? = null
    private var errorAlertDialogFragment: ErrorAlertDialogFragment? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Starts SmsRetriever, which waits for ONE matching SMS message until timeout
        // (5 minutes). The matching SMS message will be sent via a Broadcast Intent with
        // action SmsRetriever#SMS_RETRIEVED_ACTION.
        /** This will be initialized slightly after the SMS is sent so it may not always get
         * the verification code I would assume 99% of the time it will be fine though considering
         * the path the SMS message must take to get to the user. **/
        smsRetrieverClient = SmsRetriever.getClient(requireContext())

        smsRetrieverClient.startSmsRetriever()

        val task = smsRetrieverClient.startSmsRetriever()

        task.addOnFailureListener {
            val errorMessage = "addOnFailure listener was called for SmsRetrieverClient.\n" +
                    "exception: ${it.message}"

            ServiceLocator.globalErrorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                it.stackTraceToString(),
                errorMessage,
            )
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        if (initializeLoginActivity) {
            loginActivity = requireActivity() as LoginActivity
        }

        loginActivity?.registerReceiver(smsBroadcastReceiver, intentFilter)
        loginActivity?.setHalfGlobeImagesDisplayed(true)

        sharedLoginViewModel.navigatePastLoginSelectFragment =
            SharedLoginViewModel.NavigatePastLoginSelectFragment.NO_NAVIGATION

        _binding = FragmentVerifyPhoneNumbersBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.verifyPhoneMessagesTextView.text = null
        setLoadingState(false)

        smsVerifiedObserver = Observer { smsVerificationResponseEvent ->
            val smsVerificationResponse =
                smsVerificationResponseEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (smsVerificationResponse != null) {
                handleSMSVerificationResponse(smsVerificationResponse)
            }
        }

        loginObserver =
            Observer<EventWrapperWithKeyString<LoginFunctionReturnValue>> { loginResponseEvent ->
                val response = loginResponseEvent.getContentIfNotHandled(thisFragmentInstanceID)
                response?.let {
                    handleLoginResponse(it)
                }
            }

        requestNewVerificationCodeCompletedObserver = Observer { wrapper ->
            val response = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            response?.let {
                setLoadingState(false)
            }
        }

        //Avoids getting any data already stored in the LiveData.
        sharedLoginViewModel.smsVerificationStatus.observe(viewLifecycleOwner, smsVerifiedObserver)
        sharedLoginViewModel.loginFunctionData.observe(viewLifecycleOwner, loginObserver)
        sharedLoginViewModel.requestNewVerificationCodeCompleted.observe(
            viewLifecycleOwner,
            requestNewVerificationCodeCompletedObserver
        )

        binding.verifyNumEditText.setSafeEditorActionClickListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startSMSVerification(binding.verifyNumEditText.text.toString())
                return@setSafeEditorActionClickListener true
            }
            return@setSafeEditorActionClickListener false
        }

        binding.verifyPhoneContinueButton.setSafeOnClickListener {
            startSMSVerification(binding.verifyNumEditText.text.toString())
        }

        when {
            initialTimerTime != -1L -> {
                startTimer(initialTimerTime)
            }
            sharedLoginViewModel.loginSMSVerificationCoolDown != -1 -> {
                startTimer(sharedLoginViewModel.loginSMSVerificationCoolDown.toLong() * 1000)
            }
            else -> {
                startTimer(GlobalValues.server_imported_values.timeBetweenSendingSms)
            }
        }

        binding.sendNewCodeButton.setSafeOnClickListener {

            //When a new verification code is sent, the old one is obsolete. So want to make sure
            // the user does not accidentally submit the old code.
            binding.verifyNumEditText.setText("")

            sendNewSMSVerification()
        }
    }

    override fun onStart() {
        super.onStart()
        SMSBroadcastIntermediate.setFragment(this)
    }

    override fun onPause() {
        updateCreateNewDialogFragment?.dismiss()
        updateCreateNewDialogFragment = null
        enterBirthdayDialogFragment?.dismiss()
        enterBirthdayDialogFragment = null
        yesNoDialogFragment?.dismiss()
        yesNoDialogFragment = null
        errorAlertDialogFragment?.dismiss()
        errorAlertDialogFragment = null
        super.onPause()
    }

    override fun onStop() {
        //needed because I am breaking OOP with SMSBroadcast receiver
        SMSBroadcastIntermediate.clearFragment()
        super.onStop()
    }

    override fun onDestroyView() {
        cancelTimer() //stops memory leak
        _binding = null
        loginActivity?.unregisterReceiver(smsBroadcastReceiver)
        loginActivity = null
        super.onDestroyView()
    }

    //this function breaks OOP it is called from SMSBroadcastIntermediate which is called from MySMSBroadcastReceiver
    fun manageSMSSignal(message: String) {
        if (SMSBroadcastIntermediate.broadcastReceiverError) { // the message sent is an error
            sendErrorMessage(
                message,
                SMSBroadcastIntermediate.lineNumber,
                printStackTraceForErrors(),
            )
        } else { // the message sent is the proper code
            binding.verifyNumEditText.setText(message)
            startSMSVerification(message)
        }
    }

    private fun sendNewSMSVerification() {
        setLoadingState(true)

        sharedLoginViewModel.loginForVerificationCode(thisFragmentInstanceID)

        Toast.makeText(
            GlobalValues.applicationContext,
            getString(R.string.sms_verification_new_verification_code),
            Toast.LENGTH_SHORT
        ).show()
    }

    //this function is started by the onClickListener and the SMSReceiver callback
    private fun startSMSVerification(verificationCode: String) {
        if (!smsRetrieverRunning.getAndSet(true)) {
            binding.verifyPhoneMessagesTextView.text = null

            if (!sharedLoginViewModel.checkVerificationCode(verificationCode)) { //invalid verification code entered
                binding.verifyPhoneMessagesTextView.setText(R.string.sms_verification_invalid_verification_code_entered)
            } else if (sharedLoginViewModel.loginBirthDayNotRequired == false) { //if birthday is required, request birthday
                this.hideKeyboard()
                showNewDeviceDetectedDialog(verificationCode)
            } else {
                this.hideKeyboard()
                runSMSVerification(verificationCode)
            }
            smsRetrieverRunning.set(false)
        }
    }

    //this function is started by startSMSVerification and the dialogs
    private fun runSMSVerification(verificationCode: String) {
        setLoadingState(true)
        sharedLoginViewModel.smsVerification(verificationCode, thisFragmentInstanceID)
    }

    private fun showNewDeviceDetectedDialog(verificationCode: String) {
        updateCreateNewDialogFragment?.dismiss()
        updateCreateNewDialogFragment =
            UpdateCreateNewDialogFragment(
                getString(R.string.set_phone_dialog_new_device_detected_title),
                getString(R.string.set_phone_dialog_new_device_detected_body),
                { _, _ ->
                    confirmationDialog(verificationCode)
                },
                { _, _ ->
                    getBirthDayDialog(
                        getString(R.string.set_phone_dialog_enter_birthday_title),
                        verificationCode
                    )
                }
            )
        updateCreateNewDialogFragment?.show(childFragmentManager, "fragment_alert")
    }

    private fun getBirthDayDialog(title: String, verificationCode: String) {
        enterBirthdayDialogFragment?.dismiss()
        enterBirthdayDialogFragment =
            EnterBirthdayDialogFragment(
                title,
                { editTextString ->
                    Log.i("editTextString", "$editTextString")
                    editTextString?.let {
                        if (!sharedLoginViewModel.saveBirthday(editTextString).successful) {
                            Log.i("editTextString", "!successful")
                            getBirthDayDialog(
                                getString(R.string.set_phone_dialog_enter_birthday_invalid_title),
                                verificationCode
                            )
                        } else {
                            Log.i("editTextString", "successful")
                            sharedLoginViewModel.loginInstallIdAddedCommand =
                                SMSVerificationRequest.InstallationIdAddedCommand.UPDATE_ACCOUNT
                            //move on to sms verification
                            runSMSVerification(verificationCode)
                        }
                    }
                },
                { _, _ ->
                    showNewDeviceDetectedDialog(verificationCode)
                }
            )
        enterBirthdayDialogFragment?.show(childFragmentManager, "fragment_alert")
    }

    private fun confirmationDialog(verificationCode: String) {
        yesNoDialogFragment?.dismiss()
        yesNoDialogFragment =
            YesNoDialogFragment(
                getString(R.string.set_phone_dialog_confirmation_title),
                getString(R.string.set_phone_dialog_confirmation_body),
                { _, _ ->
                    sharedLoginViewModel.loginInstallIdAddedCommand =
                        SMSVerificationRequest.InstallationIdAddedCommand.CREATE_NEW_ACCOUNT
                    //move on to sms verification
                    runSMSVerification(verificationCode)
                }, { _, _ ->
                    showNewDeviceDetectedDialog(verificationCode)
                }
            )
        yesNoDialogFragment?.show(childFragmentManager, "fragment_alert")
    }

    //start timer function
    private fun startTimer(secondsToZero: Long) {

        //disable send sms button
        binding.sendNewCodeButton.isEnabled = false

        cancelTimer()

        //adding an extra 900 here for error protection
        cTimer = object : CountDownTimer(secondsToZero + 900, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.verifyPhoneNumberSMSCoolDownTextView.text =
                    getString(
                        R.string.sms_verification_sms_on_cool_down,
                        millisUntilFinished / 1000
                    )
            }

            override fun onFinish() {
                //if set loading did not disable button, enable send sms button
                if (!setLoadingRunning)
                    binding.sendNewCodeButton.isEnabled = true

                binding.verifyPhoneNumberSMSCoolDownTextView.text = null
                cTimerRunning = false
            }
        }

        cTimerRunning = true
        cTimer?.start()
    }

    //cancel timer
    private fun cancelTimer() {
        cTimer?.cancel()
        cTimer = null
        cTimerRunning = false
    }

    private fun handleSMSVerificationResponse(returnVal: SmsVerificationDataDataHolder) {

        binding.verifyPhoneMessagesTextView.text = null
        setLoadingState(false)

        var sendErrorMessage = true

        if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            when (returnVal.smsVerificationResponse.returnStatus) {
                SMSVerificationResponse.Status.PENDING_ACCOUNT_NOT_FOUND,
                SMSVerificationResponse.Status.VERIFICATION_CODE_EXPIRED,
                -> {
                    sendNewSMSVerification()
                    binding.verifyPhoneMessagesTextView.setText(R.string.sms_verification_code_expired)
                }
                SMSVerificationResponse.Status.INVALID_VERIFICATION_CODE -> {
                    binding.verifyPhoneMessagesTextView.setText(R.string.sms_verification_codes_do_not_match)
                }
                SMSVerificationResponse.Status.INCORRECT_BIRTHDAY -> {
                    binding.verifyPhoneMessagesTextView.setText(R.string.sms_verification_birthday_does_not_match)
                }
                SMSVerificationResponse.Status.VERIFICATION_ON_COOLDOWN -> {
                    binding.verifyPhoneMessagesTextView.setText(R.string.sms_verification_verification_on_cool_down_error)
                }
                SMSVerificationResponse.Status.INVALID_INSTALLATION_ID,
                SMSVerificationResponse.Status.INVALID_UPDATE_ACCOUNT_METHOD_PASSED,
                SMSVerificationResponse.Status.DATABASE_DOWN,
                SMSVerificationResponse.Status.INVALID_PHONE_NUMBER_OR_ACCOUNT_ID,
                SMSVerificationResponse.Status.VALUE_NOT_SET,
                SMSVerificationResponse.Status.SUCCESS, //should be logging in in the repository and not returning to this fragment
                SMSVerificationResponse.Status.OUTDATED_VERSION,
                SMSVerificationResponse.Status.UNKNOWN,
                SMSVerificationResponse.Status.LG_ERROR,
                SMSVerificationResponse.Status.UNRECOGNIZED,
                null,
                -> {
                    sendErrorMessage = false
                    loginActivity?.handleGrpcErrorStatusReturnValues(
                        GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO
                    )
                }
            }
        }

        if (sendErrorMessage) {
            loginActivity?.handleGrpcErrorStatusReturnValues(returnVal.errorStatus)
        }
    }

    private fun handleLoginResponse(loginResponse: LoginFunctionReturnValue) {

        binding.verifyPhoneMessagesTextView.text = null
        setLoadingState(false)
        sharedLoginViewModel.processLoginInformation(loginResponse)

        when (loginResponse.loginFunctionStatus) {
            is LoginFunctionStatus.ErrorLoggingIn,
            is LoginFunctionStatus.Idle,
            is LoginFunctionStatus.AttemptingToLogin,
            is LoginFunctionStatus.ConnectionError,
            is LoginFunctionStatus.ServerDown,
            is LoginFunctionStatus.DoNothing,
            -> {
                //Handled in MainActivity
            }
            is LoginFunctionStatus.NoValidAccountStored -> {
                //NOTE: this can be true when
                // 1) user clicks login for a specific phone number and makes it here
                // 2) user AFKs (or something) here until login is re-attempted
                // 3) it will return NoValidAccountStored
                // NOTE: this can also be true when
                // loginResponse.response.returnStatus == LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT (means a google
                // or facebook account was sent in however a phone number is not stored yet) however google or facebook
                // accounts should never be sent by this fragment
                loginActivity?.navigateToSelectMethodAndClearBackStack()
            }
            is LoginFunctionStatus.VerificationOnCoolDown -> {
                val errorString =
                    getString(
                        R.string.general_login_error_requires_info_invalid,
                        loginResponse.response.accessStatus
                    )

                storeLoginError(
                    GlobalValues.applicationContext,
                    errorString,
                    loginResponse,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors(),
                    errorStore
                )

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.sms_verification_long_cool_down,
                    Toast.LENGTH_LONG
                ).show()

                loginActivity?.navigateToSelectMethodAndClearBackStack()
            }
            is LoginFunctionStatus.LoggedIn -> {
                checkLoginAccessStatus(loginResponse,
                    GlobalValues.applicationContext,
                    childFragmentManager,
                    {
                        loginActivity?.navigate(
                            R.id.verifyPhoneNumbersFragment,
                            R.id.action_verifyPhoneNumbersFragment_to_loginGetEmailFragment
                        )
                    },
                    { loginActivity?.navigateToSelectMethodAndClearBackStack() },
                    {

                        //navigate to App Activity
                        val appActivityIntent = Intent(loginActivity, AppActivity::class.java)
                        appActivityIntent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(appActivityIntent)

                        loginActivity?.finish()
                    },
                    {
                        val errorString =
                            getString(
                                R.string.general_login_error_requires_info_invalid,
                                loginResponse.response.accessStatus
                            )

                        storeLoginError(
                            GlobalValues.applicationContext,
                            errorString,
                            loginResponse,
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
            is LoginFunctionStatus.RequiresAuthentication -> {
                if (!(loginResponse.loginFunctionStatus as LoginFunctionStatus.RequiresAuthentication).smsOnCoolDown) {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.sms_verification_verification_code_sent,
                        Toast.LENGTH_LONG
                    ).show()

                    startTimer(GlobalValues.server_imported_values.timeBetweenSendingSms)
                } else {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.sms_verification_sms_still_on_cool_down_general,
                        Toast.LENGTH_SHORT
                    ).show()

                    startTimer(sharedLoginViewModel.loginSMSVerificationCoolDown.toLong() * 1000)

                    //This can happen if.
                    // 1) User clicks continue on LoginGetPhoneNumberFragment with phoneNumber x.
                    // 2) User is sent to the VerifyPhoneNumbersFragment with phoneNumber x.
                    // 3) User pushes 'back' to the select method screen.
                    // 4) User attempts to log in using Google (or probably facebook as well).
                    // 5) The verification code will be generated for a different pending account but
                    //  unable to send an SMS b/c it is a new 'one'.

                    /*val errorString =
                        "SMS Message returned on cool down when it should be impossible"

                    storeLoginError(
                        requireActivity().applicationContext, errorString, loginResponse,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors()
                    )*/
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        if (loading) {
            binding.verifyPhoneContinueButton.isEnabled = false
            binding.verifyNumEditText.isEnabled = false
            binding.sendNewCodeButton.isEnabled = false

            setLoadingRunning = true

            binding.verifyPhoneNumberProgressBar.visibility = View.VISIBLE
        } else {
            binding.verifyPhoneContinueButton.isEnabled = true
            binding.verifyNumEditText.isEnabled = true

            setLoadingRunning = false

            //only set the new code button if cTimer is not in control of it
            if (!cTimerRunning) {
                binding.sendNewCodeButton.isEnabled = true
            }

            binding.verifyPhoneNumberProgressBar.visibility = View.INVISIBLE
        }

    }

    private fun runUnmanageableErrorDialog() {

        errorAlertDialogFragment =
            ErrorAlertDialogFragment(
                getString(R.string.error_dialog_title),
                getString(R.string.error_dialog_body)
            ) { _, _ ->

                //don't need to log out here, if it gets this message then the login helper will auto log out on the server
                loginActivity?.navigateToSelectMethodAndClearBackStack()
            }
        errorAlertDialogFragment?.isCancelable = false
        errorAlertDialogFragment?.show(childFragmentManager, "fragment_alert")

    }

    private fun sendErrorMessage(
        passedErrorMessage: String,
        lineNumber: Int,
        stackTrace: String,
    ) {
        val errorString = passedErrorMessage + "\n" +
                "--Request Values--\n" +
                "Request Phone Number: ${sharedLoginViewModel.loginPhoneNumber}\n" +
                "Request Account Type: ${sharedLoginViewModel.loginAccountType}\n" +
                "Request Birth Year: ${sharedLoginViewModel.loginBirthYear}\n" +
                "Request Birth Month: ${sharedLoginViewModel.loginBirthMonth}\n" +
                "Request Birth Day Of Month: ${sharedLoginViewModel.loginBirthDayOfMonth}\n" +
                "--Response Values--\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorString
        )
    }

}


