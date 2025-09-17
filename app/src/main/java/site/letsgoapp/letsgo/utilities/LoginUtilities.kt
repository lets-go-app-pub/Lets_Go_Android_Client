package site.letsgoapp.letsgo.utilities

import access_status.AccessStatusEnum
import account_login_type.AccountLoginTypeEnum
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.work.*
import email_sending_messages.AccountRecoveryResponse
import email_sending_messages.EmailVerificationResponse
import loginfunction.LoginRequest
import setfields.SetOptedInToPromotionalEmailRequest
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentAccountRecoveryBindingBinding
import site.letsgoapp.letsgo.databinding.DialogFragmentBirthdaySelectorBodyBinding
import site.letsgoapp.letsgo.databinding.DialogFragmentSuspendedBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.utilities.datePickerEditText.BirthdayPickerDialogWrapper
import site.letsgoapp.letsgo.workers.chatStreamWorker.ChatStreamWorker
import site.letsgoapp.letsgo.workers.chatStreamWorker.StartChatStreamWorker
import site.letsgoapp.letsgo.workers.cleanDatabaseWorker.CleanDatabaseWorker
import sms_verification.SMSVerificationResponse
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

//Used in SharedLoginViewModel as part of collecting some info from Google/Facebook logins.
//When logging in to different types of accounts, there are 2 ways I know of to get the email/name
// different than the email/name of the Facebook/Google account.
//  1) Log in using a phone number and set all the info, then connect the Google/Facebook account.
//  2) Log in to a google account, crash the app before finishing the login process, and then
//   continue the login process.
enum class StatusOfClientValueEnum {
    UNSET, //this means the value was not set by Facebook or Google
    HARD_SET //this means the value was set by Facebook or Google
}

enum class LoginErrorsEnum {
    UNMANAGEABLE_ERROR, //unmanageable error occurred

    LOGGED_IN_ELSEWHERE, //user was logged in somewhere else; NOTE: this would have to happen AFTER login BEFORE icons/pictures were requested

    ACCOUNT_CLOSED_SUSPENDED,
    ACCOUNT_CLOSED_BANNED,

    OUTDATED_VERSION
}

sealed class LoginFunctionStatus {
    //used before the login function has been started
    object Idle : LoginFunctionStatus()

    //login process has started however no result has been obtained yet
    object AttemptingToLogin : LoginFunctionStatus()

    //login was attempted and no account stored in database
    data class NoValidAccountStored(
        val requiresPhoneNumber: Boolean, //set to true when an account of type facebook or google requires a phone number
    ) : LoginFunctionStatus()

    //connection error occurred and login is sleeping while waiting to retry
    object ConnectionError : LoginFunctionStatus()

    //server was detected to be down and login is sleeping while waiting to retry
    object ServerDown : LoginFunctionStatus()

    //an error occurred while logging in
    data class ErrorLoggingIn(val errorEnum: LoginErrorsEnum) : LoginFunctionStatus()

    //account requires authentication
    class RequiresAuthentication(
        val smsOnCoolDown: Boolean,
    ) : LoginFunctionStatus()

    //logged in, may need to check access status to see if necessary info has been collected
    object LoggedIn : LoginFunctionStatus()

    //sms verification was called too many times by this installation id, there is now a cool down for it
    object VerificationOnCoolDown : LoginFunctionStatus()

    //this should be stopped inside the LoginFunctions before being sent to the observer
    object DoNothing : LoginFunctionStatus()
}

data class SetAlgorithmSearchOptionsReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val algorithmSearchOptions: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions
)

data class SetOptedInToPromotionalEmailsReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val optedInToPromotionalEmails: Boolean
)

data class SetEmailReturnValues(
    val setFieldsReturnValues: SetFieldsReturnValues,
    val emailAddress: String
)

data class SetBioReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val bio: String
)

data class SetCityReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val city: String
)

data class SetAgeRangeReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val ageRange: AgeRangeHolder
)

data class SetMaxDistanceReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val maxDistance: Int
)

data class SetGenderReturnValues(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val gender: String
)

data class SetGenderRangeReturnValue(
    val invalidParameterPassed: Boolean,
    val errors: GrpcFunctionErrorStatusEnum,
    val updatedTimestamp: Long,
    val genderRange: String
)

data class EmailVerificationReturnValues(
    val errors: GrpcFunctionErrorStatusEnum,
    val response: EmailVerificationResponse,
    val callingFragmentInstanceId: String,
)

data class AccountRecoveryReturnValues(
    val errors: GrpcFunctionErrorStatusEnum,
    val response: AccountRecoveryResponse,
    val phoneNumber: String,
)

data class ReturnEmailFromDatabaseDataHolder(
    val email: String?
)

data class DataHolderWrapper<T>(
    val dataHolder: T,
    val fragmentInstanceId: String
)

data class BasicLoginInfo(
    val accountType: AccountLoginTypeEnum.AccountLoginType,
    val phoneNumber: String = "~",
    val accountID: String = "~",
)

data class SmsVerificationDataDataHolder(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val smsVerificationResponse: SMSVerificationResponse
)

//Used in VerifyPhoneNumbersFragment as part of collecting birthday
class YesNoDialogFragment(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
    private var cancelButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(R.string.Ok, okButtonAction)
        alertDialogBuilder.setNegativeButton(R.string.Cancel, cancelButtonAction)
        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
        cancelButtonAction = null
    }
}

//Used in VerifyPhoneNumbersFragment as part of collecting birthday
class UpdateCreateNewDialogFragment(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
    private var cancelButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(
            R.string.set_phone_dialog_new_device_detected_replace,
            okButtonAction
        )
        alertDialogBuilder.setNegativeButton(
            R.string.set_phone_dialog_new_device_detected_existing,
            cancelButtonAction
        )

        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
        cancelButtonAction = null
    }
}

//Used in VerifyPhoneNumbersFragment as part of collecting birthday
class EnterBirthdayDialogFragment(
    private val title: String,
    private var okButtonInjection: ((String?) -> Unit)?,
    private var cancelButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    private var _binding: DialogFragmentBirthdaySelectorBodyBinding? = null
    private val binding get() = _binding!!

    private var handleBirthdayPickerDialog: BirthdayPickerDialogWrapper? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.i("DiaFragment","onCreateDialog()")

        _binding =
            DialogFragmentBirthdaySelectorBodyBinding.inflate(requireActivity().layoutInflater)
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        handleBirthdayPickerDialog = BirthdayPickerDialogWrapper(
            binding.birthdayDialogSelectorEditText,
            parentFragmentManager
        )

        alertDialogBuilder.setTitle(title)
        //alertDialogBuilder.setMessage(classBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
            okButtonInjection?.let { it(binding.birthdayDialogSelectorEditText.text.toString()) }
        }
        alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelButtonAction)
        alertDialogBuilder.setView(binding.root)
        return alertDialogBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of known bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        binding.birthdayDialogSelectorEditText.isCursorVisible = false
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonInjection = null
        cancelButtonAction = null
        _binding = null
    }

}

class AccountRecoveryDialogFragment(
    private var okButtonInjection: ((String) -> Unit)?,
    private var cancelButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    private var _binding: DialogFragmentAccountRecoveryBindingBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        _binding =
            DialogFragmentAccountRecoveryBindingBinding.inflate(requireActivity().layoutInflater)
        binding.accountRecoveryPhoneNumberEditText.addTextChangedListener(
            PhoneNumberFormattingTextWatcher()
        )

        binding.accountRecoveryPhoneNumberEditText.hint = resources.getString(R.string.phone_login_number_hint)

        isCancelable = false

        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        val title = requireContext().getString(R.string.account_recovery_dialog_title)
        val body = requireContext().getString(R.string.account_recovery_dialog_body)

        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(body)
        alertDialogBuilder.setPositiveButton(android.R.string.ok, null)
        alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelButtonAction)
        alertDialogBuilder.setView(binding.root)
        alertDialogBuilder.setCancelable(false)

        return alertDialogBuilder.create()

    }

    //Don't want the dialog to vanish if an invalid input is provided so
    //onStart() is where dialog.show() is actually called on
    //the underlying dialog, so we have to do it there or
    //later in the lifecycle.
    //Doing it in onResume() makes sure that even if there is a config change
    //environment that skips onStart then the dialog will still be functioning
    //properly after a rotation.
    override fun onResume() {
        super.onResume()
        val alertDialog = dialog as AlertDialog

        val positiveButton = alertDialog.getButton(Dialog.BUTTON_POSITIVE)
        positiveButton.setSafeOnClickListener {

            val finalPhoneNumberValue = binding.accountRecoveryPhoneNumberEditText.text.toString().validateAndFormatPhoneNumber()

            if (finalPhoneNumberValue.isEmpty()) {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.phone_login_invalid_phone_number_entered,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                okButtonInjection?.let { it1 -> it1(finalPhoneNumberValue) }
                alertDialog.dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        binding.accountRecoveryPhoneNumberEditText.isCursorVisible = false
        super.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        okButtonInjection = null
        cancelButtonAction = null
    }
}

//Used in VerifyPhoneNumbersFragment as part of collecting birthday
class ErrorAlertDialogFragment(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(android.R.string.ok, okButtonAction)
        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
    }
}

//Used in VerifyPhoneNumbersFragment as part of collecting birthday
class SuspendedDialogFragment(
    private val title: String,
    private val suspendedMessage: String,
    private val suspendedTime: Long,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val binding = DialogFragmentSuspendedBinding.inflate(requireActivity().layoutInflater)
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(android.R.string.ok, okButtonAction)

        if (suspendedTime > 0) {
            binding.suspendedDialogMessageTimeRemainingView.text =
                formatTimestampDaysHoursMinutesForSuspendedDuration(suspendedTime)
        } else {
            binding.suspendedDialogMessageTimeRemainingView.isVisible = false
        }

        binding.suspendedDialogMessageTextView.text = suspendedMessage
        alertDialogBuilder.setView(binding.root)
        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
    }
}

inline fun displaySuspendedDialog(
    applicationContext: Context,
    childFragmentManager: FragmentManager,
    reason: String,
    suspendedDuration: Long,
    crossinline navigateToSelectMethod: () -> (Unit),
) {

    val doubleCheckReason =
        if (reason == "" || reason == "~") {
            applicationContext.getString(R.string.suspended_banned_reason_unspecified)
        } else {
            reason
        }

    val suspendedDialog =
        SuspendedDialogFragment(
            applicationContext.getString(R.string.suspended_dialog_title),
            "Reason: $doubleCheckReason",
            suspendedDuration
        ) { _, _ ->
            navigateToSelectMethod()
        }
    suspendedDialog.isCancelable = false
    suspendedDialog.show(childFragmentManager, "fragment_alert")
}

inline fun displayBannedDialog(
    applicationContext: Context,
    childFragmentManager: FragmentManager,
    reason: String,
    crossinline navigateToSelectMethod: () -> (Unit),
) {

    val doubleCheckReason =
        if (reason == "" || reason == "~") {
            applicationContext.getString(R.string.suspended_banned_reason_unspecified)
        } else {
            reason
        }

    val alertDialog =
        ErrorAlertDialogFragment(
            applicationContext.getString(R.string.banned_dialog_title),
            "Reason: $doubleCheckReason"
        ) { _, _ ->
            navigateToSelectMethod()
        }
    alertDialog.isCancelable = false
    alertDialog.show(childFragmentManager, "fragment_alert")
}

inline fun checkLoginAccessStatus(
    loginResponse: LoginFunctionReturnValue,
    applicationContext: Context,
    childFragmentManager: FragmentManager,
    navigateToStartCollectingInfo: () -> (Unit),
    crossinline navigateToSelectMethod: () -> (Unit),
    navigateToPrimaryApplicationActivity: () -> (Unit),
    handleLG_ErrReturn: () -> (Unit),
) {

    Log.i("unmanageableErr", "accessStatus: ${loginResponse.response.accessStatus}")

    Log.i(
        "loginFunctionsFreeze",
        "checkLoginAccessStatus() loginResponse.response.accessStatus: ${loginResponse.response.accessStatus}"
    )

    when (loginResponse.response.accessStatus) { //switch for looking at LoginFunctionRequiresInfoEnum
        AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO -> {
            //navigate to the fragment after VerifyPhoneNumberFragment to collect personal info
            navigateToStartCollectingInfo()
        }
        AccessStatusEnum.AccessStatus.ACCESS_GRANTED -> {  //has all info set
            navigateToPrimaryApplicationActivity()
        }
        AccessStatusEnum.AccessStatus.SUSPENDED -> {
            displaySuspendedDialog(
                applicationContext,
                childFragmentManager,
                loginResponse.response.timeOutMessage,
                loginResponse.response.timeOutDurationRemaining,
                navigateToSelectMethod
            )
        }
        AccessStatusEnum.AccessStatus.BANNED -> {
            displayBannedDialog(
                applicationContext,
                childFragmentManager,
                loginResponse.response.timeOutMessage,
                navigateToSelectMethod
            )
        }
        AccessStatusEnum.AccessStatus.STATUS_NOT_SET, //this means account returned 'LOGGED_IN' and did not set the access status
        AccessStatusEnum.AccessStatus.LG_ERR,
        AccessStatusEnum.AccessStatus.UNRECOGNIZED,
        null,
        -> {
            handleLG_ErrReturn()
        }
    }

}

fun storeLoginError(
    applicationContext: Context,
    errorString: String,
    loginResponse: LoginFunctionReturnValue,
    lineNumber: Int,
    fileName: String,
    stackTrace: String,
    errorStore: StoreErrorsInterface
) {

    val errorMsg = errorString + '\n' +
        loginResponse.request.toString() +
        loginResponse.response.toString() +
        "ErrorStatus:  ${loginResponse.loginFunctionStatus}"

    errorStore.storeError(
        fileName,
        lineNumber,
        stackTrace,
        errorMsg,
        applicationContext
    )
}

//return a setup request class for first time login using google or facebook login types
fun setupLoginRequestForLoginWithAccountID(
    accountID: String,
    installationId: String,
    accountType: AccountLoginTypeEnum.AccountLoginType,
    letsGoVersionNumber: Int,
): LoginRequest.Builder {

    //I don't need to get the phone number here at all the loginFunction on the server
    //will just get the phone number out of the database and overwrite whatever is sent
    //when an accountID is used to log in
    return LoginRequest.newBuilder()
        .setPhoneNumber("~")
        .setAccountId(accountID)
        .setInstallationId(installationId)
        .setLetsGoVersion(letsGoVersionNumber)
        .setAccountType(accountType)
}

//return a setup request class for first time login using phone number login type
fun setupLoginRequestForLoginWithPhoneNumber(
    phoneNumber: String, installationId: String, letsGoVersionNumber: Int,
): LoginRequest.Builder {

    return LoginRequest.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setAccountId("~")
        .setInstallationId(installationId)
        .setLetsGoVersion(letsGoVersionNumber)
        .setAccountType(AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT)
}

//this is used to setup and start the ChatStreamWorker
fun startChatStreamWorker(
    applicationContext: Context,
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val chatStreamWorkRequest = OneTimeWorkRequestBuilder<ChatStreamWorker>()
        .setConstraints(constraints)
        .addTag(ChatStreamWorker.CHAT_STREAM_WORKER_TAG)
        .build()

    Log.i("chatStreamSubscription", "(starting) about to lock chatStreamWorkerMutex")

    ChatStreamWorker.chatStreamWorkerMutex.withLock {

        Log.i("chatStreamSubscription", "(starting) chatStreamWorkerMutex locked")

        ChatStreamWorker.continueChatStreamWorker.set(true)

        Log.i("chatStreamSubscription", "(starting) continueChatStreamWorker set to true")
        //keep the mutex locked until the operation is finished (it is locked above)
        WorkManager
            .getInstance(applicationContext)
            .enqueue(chatStreamWorkRequest)
            .result
            .get()

        Log.i(
            "chatStreamSubscription",
            "(starting) ChatStreamWorker enqueue in WorkManager finished"
        )
    }

    Log.i("chatStreamSubscription", "(starting) chatStreamWorkerMutex unlocked")

    //NOTE: this is here to avoid an error (it thinks there is a return type coming from the if else statement above)
    return

}

/** This function will start the CleanDatabaseWorker. It runs on the system time instead of the login
 * time so it will fit what the user time looks like. **/
fun startStartChatStreamWorker(
    applicationContext: Context,
) {
    //set up the worker to make sure the chat stream is running (it could go down for example if the app is terminated)
    val myWork = PeriodicWorkRequestBuilder<StartChatStreamWorker>(
        StartChatStreamWorker.TIME_BETWEEN_START_CHAT_STREAM_WORKER_RUNNING,
        TimeUnit.MINUTES
    )
        .build()

    WorkManager.getInstance(applicationContext)
        .enqueueUniquePeriodicWork(
            StartChatStreamWorker.START_CHAT_STREAM_UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            myWork
        )
}

fun cancelStartChatStreamWorker(
    applicationContext: Context,
) {
    WorkManager
        .getInstance(applicationContext)
        .cancelUniqueWork(StartChatStreamWorker.START_CHAT_STREAM_UNIQUE_WORK_NAME)
}

/** This function will start the CleanDatabaseWorker. It runs on the system time instead of the login
 * time so it will fit what the user time looks like. **/
fun startCleanDatabaseWorker(
    applicationContext: Context,
    workPolicy: ExistingWorkPolicy
) {

    val currentDate = Calendar.getInstance()
    val dueDate = Calendar.getInstance()

    dueDate.set(Calendar.HOUR_OF_DAY, 3)
    dueDate.set(Calendar.MINUTE, 0)
    dueDate.set(Calendar.SECOND, 0)

    if (dueDate.before(currentDate)) {
        dueDate.add(Calendar.HOUR_OF_DAY, 24)
    }

    var timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

    if (timeDiff > GlobalValues.MILLIS_IN_TWENTY_FOUR_HOURS) {
        //the only time this (should be able to?) happen is if the user changed the time between currentDate and dueDate
        // variables being initialized however this will account for any other situations as well
        timeDiff = GlobalValues.MILLIS_IN_TWENTY_FOUR_HOURS
    }

    //NOTE: Not using PeriodicWorkRequestBuilder because
    // It cannot be used at a specific time every day. This can be 'gone around' by setting an initial
    // delay OR by creating a OneTimeWorkRequestBuilder to fire the PeriodicWorkRequest at the correct time.
    // Both of these approaches have 2 problems. First they don't take the user time zone changes into account
    // and second they will not run the first time. They will only run after the first time period has passed (for
    // example if it runs every 24 hours then it will only work the first time after 24 hours has passed, so if it is
    // set to run at 8 AM and it is currently 6 AM then it will run 26 hours from now).

    //not setting setRequiresDeviceIdle() because this is a Foreground worker anyway AND I think it will
    // be cancelled if the device STOPS being idle which I don't want
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    //set up the worker to periodically clean database
    val cleanDatabaseWorker = OneTimeWorkRequestBuilder<CleanDatabaseWorker>()
        .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(applicationContext)
        .enqueueUniqueWork(
            CleanDatabaseWorker.CLEAN_DATABASE_WORKER_UNIQUE_WORK_NAME,
            workPolicy,
            cleanDatabaseWorker
        )
}

fun cancelCleanDatabaseWorker(
    applicationContext: Context,
) {
    WorkManager
        .getInstance(applicationContext)
        .cancelUniqueWork(CleanDatabaseWorker.CLEAN_DATABASE_WORKER_UNIQUE_WORK_NAME)
}

//begin the unique workers that should always be running if they are NOT currently enqueue or
// running
fun beginUniqueWorkIfNotRunning(
    applicationContext: Context
) {
    startCleanDatabaseWorker(applicationContext, ExistingWorkPolicy.KEEP)

    startStartChatStreamWorker(applicationContext)
}

suspend fun workerRespondToLogin(
    applicationContext: Context,
    loginFunctionReturnValues: LoginFunctionReturnValue,
    successfullyLoggedIn: suspend () -> Unit,
    failedToLogin: suspend () -> Unit,
    loginFunctionsRetrying: suspend () -> Unit = {},
) {

    Log.i("chatStreamWorkerLogin", "workerRespondToLogin() loginFunctionStatus: ${loginFunctionReturnValues.loginFunctionStatus}")

    when (loginFunctionReturnValues.loginFunctionStatus) {
        LoginFunctionStatus.Idle,
        LoginFunctionStatus.AttemptingToLogin,
        LoginFunctionStatus.ConnectionError,
        LoginFunctionStatus.ServerDown,
        -> {
            //handled by LoginFunctions (it attempts to reconnect)
            loginFunctionsRetrying()
        }
        LoginFunctionStatus.DoNothing -> {
            //this may be reachable on initial device startup
        }
        LoginFunctionStatus.LoggedIn -> {
            if (loginFunctionReturnValues.response.accessStatus == AccessStatusEnum.AccessStatus.ACCESS_GRANTED) {
                successfullyLoggedIn()
            } else {
                failedToLogin()
            }
        }
        is LoginFunctionStatus.ErrorLoggingIn -> {
            when ((loginFunctionReturnValues.loginFunctionStatus as LoginFunctionStatus.ErrorLoggingIn).errorEnum) {
                LoginErrorsEnum.UNMANAGEABLE_ERROR,
                LoginErrorsEnum.LOGGED_IN_ELSEWHERE,
                LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED,
                LoginErrorsEnum.ACCOUNT_CLOSED_BANNED,
                -> {
                    failedToLogin()
                    (applicationContext as LetsGoApplicationClass).loginSupportFunctions.runLogoutFunction()
                }
                LoginErrorsEnum.OUTDATED_VERSION -> {
                    failedToLogin()
                }
            }
        }
        is LoginFunctionStatus.NoValidAccountStored -> {
            failedToLogin()
            (applicationContext as LetsGoApplicationClass).loginSupportFunctions.clearAllUserDataAndStopObjects()
        }
        is LoginFunctionStatus.VerificationOnCoolDown,
        is LoginFunctionStatus.RequiresAuthentication,
        -> {
            failedToLogin()
        }
    }
}



