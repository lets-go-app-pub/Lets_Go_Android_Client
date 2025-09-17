package site.letsgoapp.letsgo.loginActivityFragments

import account_login_type.AccountLoginTypeEnum
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.FirstNameDataHolder
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.GenderDataHolder
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.LoginRepository
import site.letsgoapp.letsgo.repositories.SelectPicturesRepository
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginSupportFunctions
import site.letsgoapp.letsgo.utilities.*
import sms_verification.SMSVerificationRequest
import java.util.*

//Activity view model accessed and modified by login fragments
//class SharedLoginViewModel(application: Application): AndroidViewModel(application) {
class SharedLoginViewModel(
    private val repository: LoginRepository,
    private val loginSupportFunctions: LoginSupportFunctions,
    private val selectPicturesRepository: SelectPicturesRepository,
    private val errorStore: StoreErrorsInterface,
    private val ioDispatcher: CoroutineDispatcher
    ) : ViewModel() {

    enum class NavigatePastLoginSelectFragment {
        NAVIGATE_TO_VERIFY_FRAGMENT,
        NAVIGATE_TO_COLLECT_EMAIL_FRAGMENT,
        NO_NAVIGATION
    }

    val thisSharedLoginViewModelInstanceId = generateViewModelInstanceId()

    private fun <T> keyStringObserverFactory(block: (T) -> Unit): KeyStringObserver<T> {
        return KeyStringObserver(block, thisSharedLoginViewModelInstanceId)
    }

    var navigatePastLoginSelectFragment = NavigatePastLoginSelectFragment.NO_NAVIGATION

    //Set in VerifyPhoneNumbersFragment
    var loginBirthMonth = -1 //used to verify the users birthday
    var loginBirthYear = -1
    var loginBirthDayOfMonth = -1

    //Set in LoginGetPhoneNumberFragment
    var loginInstallIdAddedCommand = SMSVerificationRequest.InstallationIdAddedCommand.VALUE_NOT_SET

    //Set in SharedLoginViewModel, when login is called by Splash Screen, GetPhoneNumber, SelectMethod or VerifyPhoneNumbers
    var loginPhoneNumber = "" //set after the phone number is authorized by the server
    var loginSMSVerificationCoolDown = //----NOTE: THIS IS IN SECONDS----
        -1 //set if SMS_ON_COOL_DOWN or set to the const value if REQUIRES_AUTHORIZATION returned (30 atm):
    var loginBirthDayNotRequired: Boolean? =
        null //WARNING: nullable; set after the phone number is authorized by the server
    var birthdayTimestamp: Long = -1L //will be set to -1 if data is missing from server
    var emailTimestamp: Long = -1L //will be set to -1 if data is missing from server
    var genderTimestamp: Long = -1L //will be set to -1 if data is missing from server
    private var nameTimestamp: Long = -1L //will be set to -1 if data is missing from server
    private var picturesTimestamp =
        emptyList<Long>() //will be set to -1 if data is missing from server
    var categoriesTimestamp: Long = -1L //will be set to -1 if data is missing from server

    //Breaks convention by being set in multiple places inside SplashScreenFragment and LoginSelectMethodFragment
    //this is set outside of SharedLoginViewModel before login attempts
    var loginAccountType =
        AccountLoginTypeEnum.AccountLoginType.LOGIN_TYPE_VALUE_NOT_SET //Set on callback from login with 1 exception, in select method fragment it is directly implemented
        set(value) {
            Log.i("loginAccountType", "set to $loginAccountType")
            Log.i("loginAccountType", Log.getStackTraceString(Throwable()))
            field = value
        }

    var loginAccountID = "~" //will be set to "~" if no accountID was used

    inner class NewAccountInfo(
        var emailAddress: String = "~",
        var requiresEmailAddressVerification: Boolean = false,
        var emailAddressStatus: StatusOfClientValueEnum =
            StatusOfClientValueEnum.UNSET, //status of email and emailRequiresVerification
        var birthYear: Int = -1,
        var birthMonth: Int = -1,
        var birthDayOfMonth: Int = -1,
        var birthDayStatus: StatusOfClientValueEnum =
            StatusOfClientValueEnum.UNSET, //status of BirthYear, BirthMonth, BirthDayOfMonth
        var gender: String = "~",
        var genderStatus: StatusOfClientValueEnum = StatusOfClientValueEnum.UNSET, //status of Gender and GenderOther
        var firstName: String = "~",
        var firstNameStatus: StatusOfClientValueEnum = StatusOfClientValueEnum.UNSET
    )

    val newAccountInfo = NewAccountInfo()

    fun resetHardSetsToDefaults() {
        newAccountInfo.emailAddressStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.birthDayStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.genderStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.firstNameStatus = StatusOfClientValueEnum.UNSET
    }

    fun setVariablesToDefaults() {
        navigatePastLoginSelectFragment = NavigatePastLoginSelectFragment.NO_NAVIGATION
        loginBirthMonth = -1
        loginBirthYear = -1
        loginBirthDayOfMonth = -1
        loginInstallIdAddedCommand = SMSVerificationRequest.InstallationIdAddedCommand.VALUE_NOT_SET
        loginPhoneNumber = ""
        loginSMSVerificationCoolDown = -1
        loginBirthDayNotRequired = null
        birthdayTimestamp = -1L
        emailTimestamp = -1L
        genderTimestamp = -1L
        nameTimestamp = -1L
        picturesTimestamp = emptyList()
        categoriesTimestamp = -1L
        loginAccountType = AccountLoginTypeEnum.AccountLoginType.LOGIN_TYPE_VALUE_NOT_SET
        loginAccountID = "~"
        newAccountInfo.emailAddress = "~"
        newAccountInfo.requiresEmailAddressVerification = false
        newAccountInfo.emailAddressStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.birthYear = -1
        newAccountInfo.birthMonth = -1
        newAccountInfo.birthDayOfMonth = -1
        newAccountInfo.birthDayStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.gender = "~"
        newAccountInfo.genderStatus = StatusOfClientValueEnum.UNSET
        newAccountInfo.firstName = "~"
        newAccountInfo.firstNameStatus = StatusOfClientValueEnum.UNSET
    }

    private val loginViewModelUUID = UUID.randomUUID()

    /** ------------------------------------------------------------------- **/

    private val _loginFunctionData: MutableLiveData<EventWrapperWithKeyString<LoginFunctionReturnValue>> =
        MutableLiveData()
    val loginFunctionData: LiveData<EventWrapperWithKeyString<LoginFunctionReturnValue>> =
        _loginFunctionData //NOTE: The response can be null, it means no internet connection or login attempt number exceeded

    private val _smsVerificationStatus: MutableLiveData<EventWrapperWithKeyString<SmsVerificationDataDataHolder>> =
        MutableLiveData()
    val smsVerificationStatus: LiveData<EventWrapperWithKeyString<SmsVerificationDataDataHolder>> =
        _smsVerificationStatus
    private val smsVerificationObserver =
        keyStringObserverFactory<DataHolderWrapper<SmsVerificationDataDataHolder>> { smsVerificationResponseWrapper ->
            _smsVerificationStatus.value =
                EventWrapperWithKeyString(
                    smsVerificationResponseWrapper.dataHolder,
                    smsVerificationResponseWrapper.fragmentInstanceId
                )
        }

    private val _setFieldReturnValue: MutableLiveData<EventWrapperWithKeyString<SetFieldsReturnValues>> =
        MutableLiveData()
    val setFieldReturnValue: LiveData<EventWrapperWithKeyString<SetFieldsReturnValues>> =
        _setFieldReturnValue
    private val setFieldTimestampObserver =
        keyStringObserverFactory<DataHolderWrapper<SetFieldsReturnValues>> { result ->
            _setFieldReturnValue.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _accountRecoveryReturnValue: MutableLiveData<EventWrapperWithKeyString<AccountRecoveryReturnValues>> =
        MutableLiveData()
    val accountRecoveryReturnValue: LiveData<EventWrapperWithKeyString<AccountRecoveryReturnValues>> =
        _accountRecoveryReturnValue
    private val accountRecoveryReturnValueObserver =
        keyStringObserverFactory<DataHolderWrapper<AccountRecoveryReturnValues>> { result ->
            _accountRecoveryReturnValue.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _returnEmailFromDatabase: MutableLiveData<EventWrapperWithKeyString<ReturnEmailFromDatabaseDataHolder>> =
        MutableLiveData()
    val returnEmailFromDatabase: LiveData<EventWrapperWithKeyString<ReturnEmailFromDatabaseDataHolder>> =
        _returnEmailFromDatabase
    private val returnEmailFromDatabaseObserver =
        keyStringObserverFactory<DataHolderWrapper<ReturnEmailFromDatabaseDataHolder>> { result ->
            _returnEmailFromDatabase.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _returnBirthdayFromDatabase: MutableLiveData<EventWrapperWithKeyString<BirthdayHolder>> =
        MutableLiveData()
    val returnBirthdayFromDatabase: LiveData<EventWrapperWithKeyString<BirthdayHolder>> =
        _returnBirthdayFromDatabase
    private val returnBirthdayFromDatabaseObserver =
        keyStringObserverFactory<DataHolderWrapper<BirthdayHolder>> { result ->
            _returnBirthdayFromDatabase.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _returnFirstNameFromDatabase: MutableLiveData<EventWrapperWithKeyString<FirstNameDataHolder>> =
        MutableLiveData()
    val returnFirstNameFromDatabase: LiveData<EventWrapperWithKeyString<FirstNameDataHolder>> =
        _returnFirstNameFromDatabase
    private val returnFirstNameFromDatabaseObserver =
        keyStringObserverFactory<DataHolderWrapper<FirstNameDataHolder>> { result ->
            _returnFirstNameFromDatabase.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _returnGenderFromDatabase: MutableLiveData<EventWrapperWithKeyString<GenderDataHolder>> =
        MutableLiveData()
    val returnGenderFromDatabase: LiveData<EventWrapperWithKeyString<GenderDataHolder>> =
        _returnGenderFromDatabase
    private val returnGenderFromDatabaseObserver =
        keyStringObserverFactory<DataHolderWrapper<GenderDataHolder>> { result ->
            _returnGenderFromDatabase.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _setDrawablesInDatabaseInfo: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val setDrawablesInDatabaseInfo: LiveData<EventWrapperWithKeyString<Unit>> =
        _setDrawablesInDatabaseInfo
    private val setDrawablesInDatabaseInfoObserver =
        keyStringObserverFactory<DataHolderWrapper<Unit>> { result ->
            _setDrawablesInDatabaseInfo.value = EventWrapperWithKeyString(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    private val _handleGrpcErrorStatusReturnValues: MutableLiveData<EventWrapper<GrpcFunctionErrorStatusEnum>> =
        MutableLiveData()
    val handleGrpcErrorStatusReturnValues: LiveData<EventWrapper<GrpcFunctionErrorStatusEnum>> =
        _handleGrpcErrorStatusReturnValues
    private val handleGrpcErrorStatusReturnValuesObserver =
        keyStringObserverFactory<GrpcFunctionErrorStatusEnum> { result ->
            _handleGrpcErrorStatusReturnValues.value = EventWrapper(result)
        }

    private val _finishedGettingLocation: MutableLiveData<EventWrapperWithKeyString<LocationReturnErrorStatus>> =
        MutableLiveData()
    val finishedGettingLocation: LiveData<EventWrapperWithKeyString<LocationReturnErrorStatus>> =
        _finishedGettingLocation

    private val _requestNewVerificationCodeCompleted: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val requestNewVerificationCodeCompleted: LiveData<EventWrapperWithKeyString<Unit>> =
        _requestNewVerificationCodeCompleted

    var mostRecentFragmentIDRequestingLocation = "~"

    private suspend fun respondToLogin(loginFunctionReturnValues: LoginFunctionReturnValue) =
        withContext(Dispatchers.Main) {

            Log.i(
                "loginFunctionsFreeze",
                "SharedLoginViewModel callingFragmentInstanceID: ${loginFunctionReturnValues.callingFragmentInstanceID} loginFunctionStatus: ${loginFunctionReturnValues.loginFunctionStatus}"
            )

            LoginFunctions.receivedMessage(loginFunctionReturnValues)

            when (loginFunctionReturnValues.loginFunctionStatus) {
                is LoginFunctionStatus.Idle,
                is LoginFunctionStatus.AttemptingToLogin,
                is LoginFunctionStatus.ConnectionError,
                is LoginFunctionStatus.ServerDown,
                is LoginFunctionStatus.RequiresAuthentication,
                is LoginFunctionStatus.NoValidAccountStored,
                is LoginFunctionStatus.ErrorLoggingIn,
                is LoginFunctionStatus.VerificationOnCoolDown,
                is LoginFunctionStatus.LoggedIn,
                -> {
                    //This should already be in Main context
                    _loginFunctionData.value = EventWrapperWithKeyString(
                        loginFunctionReturnValues,
                        loginFunctionReturnValues.callingFragmentInstanceID
                    )
                }
                is LoginFunctionStatus.DoNothing -> {

                }
            }
        }

    fun loginForVerificationCode(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            beginManualLoginToServer(
                loginAccountType,
                loginPhoneNumber,
                loginAccountID,
                callingFragmentInstanceID
            )

            withContext(Dispatchers.Main) {
                _requestNewVerificationCodeCompleted.value =
                    EventWrapperWithKeyString(Unit, callingFragmentInstanceID)
            }
        }
    }

    //true means phone number valid, false means invalid phone number
    //called from LoginGetPhoneNumberViewModel
    fun loginToSetPhoneNumber(phoneNumber: String, callingFragmentInstanceID: String): Boolean {
        val finalPhoneNumberValue = phoneNumber.validateAndFormatPhoneNumber()

        if (finalPhoneNumberValue.isEmpty()) {
            return false
        }

        CoroutineScope(ioDispatcher).launch {
            Log.i("loginAccountType", "loginToSetPhoneNumber() $loginAccountType")
            beginManualLoginToServerWithAccountInfo(
                loginAccountType,
                finalPhoneNumberValue,
                loginAccountID,
                callingFragmentInstanceID
            )
        }

        return true
    }

    fun beginLoginToServerWhenReceivedInvalidToken(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.beginLoginToServerWhenReceivedInvalidToken(callingFragmentInstanceID)
        }
    }

    fun loginWithAccountID(
        accountID: String,
        accountType: AccountLoginTypeEnum.AccountLoginType,
        callingFragmentInstanceID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            beginManualLoginToServer(
                accountType,
                accountID = accountID,
                callingFragmentInstanceID = callingFragmentInstanceID
            )
        }
    }

    private suspend fun beginManualLoginToServer(
        accountType: AccountLoginTypeEnum.AccountLoginType,
        phoneNumber: String = "~",
        accountID: String = "~",
        callingFragmentInstanceID: String = "",
    ) {
        beginManualLoginToServerWithAccountInfo(
            accountType,
            phoneNumber,
            accountID,
            callingFragmentInstanceID
        )
    }

    private suspend fun beginManualLoginToServerWithAccountInfo(
        accountType: AccountLoginTypeEnum.AccountLoginType,
        phoneNumber: String = "~",
        accountID: String = "~",
        callingFragmentInstanceID: String = "",
    ) {
        repository.beginManualLoginToServerWithAccountInfo(
            accountType,
            phoneNumber,
            accountID,
            callingFragmentInstanceID
        )
    }

    fun runBeginAccountRecovery(
        phoneNumber: String,
        callingFragmentInstanceID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.runBeginAccountRecovery(
                phoneNumber,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun beginManualLoginToServerExtractAccountInfo(
        callingFragmentInstanceID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.beginManualLoginToServerExtractAccountInfo(callingFragmentInstanceID)
        }
    }

    fun checkVerificationCode(verificationCode: String): Boolean {
        if (verificationCode.length != GlobalValues.verificationCodeNumberOfDigits) {
            return false
        }

        for (c in verificationCode) {
            if (!c.isDigit()) {
                return false
            }
        }

        return true
    }

    fun smsVerification(verificationCode: String, callingFragmentInstanceID: String) {

        val loginPhoneNumberOrAccountId =
            when (loginAccountType) {
                AccountLoginTypeEnum.AccountLoginType.FACEBOOK_ACCOUNT,
                AccountLoginTypeEnum.AccountLoginType.GOOGLE_ACCOUNT,
                -> {
                    if(loginAccountID.isBlank() || loginAccountID == "~") {
                        val errorMessage =
                            "Invalid loginAccountID was found during smsVerification.\n"

                        storeErrorForViewModel(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        _handleGrpcErrorStatusReturnValues.value = EventWrapper(
                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                        )

                        return
                    }

                    loginAccountID
                }
                AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT -> {
                    if(!loginPhoneNumber.isValidPhoneNumber()) {
                        val errorMessage =
                            "Invalid loginPhoneNumber was found during smsVerification.\n"

                        storeErrorForViewModel(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        _handleGrpcErrorStatusReturnValues.value = EventWrapper(
                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                        )

                        return
                    }

                    loginPhoneNumber
                }
                AccountLoginTypeEnum.AccountLoginType.LOGIN_TYPE_VALUE_NOT_SET,
                AccountLoginTypeEnum.AccountLoginType.UNRECOGNIZED,
                -> {
                    val errorMessage =
                        "Invalid login account type was called during smsVerification.\n" +
                                "loginAccountType: $loginAccountType\n" +
                                "verificationCode: $verificationCode\n" +
                                "callingFragmentInstanceID: $callingFragmentInstanceID\n"

                    storeErrorForViewModel(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    _handleGrpcErrorStatusReturnValues.value = EventWrapper(
                        GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                    )

                    return
                }
            }

        val smsVerificationRequest =
            SMSVerificationRequest.newBuilder()
                .setInstallationId(GlobalValues.installationId)
                .setAccountType(loginAccountType)
                .setPhoneNumberOrAccountId(loginPhoneNumberOrAccountId)
                .setVerificationCode(verificationCode)
                .setLetsGoVersion(GlobalValues.Lets_GO_Version_Number)
                .setInstallationIdAddedCommand(loginInstallIdAddedCommand)
                .setInstallationIdBirthYear(loginBirthYear)
                .setInstallationIdBirthMonth(loginBirthMonth)
                .setInstallationIdBirthDayOfMonth(loginBirthDayOfMonth)

        CoroutineScope(ioDispatcher).launch {
            repository.runSmsVerification(
                smsVerificationRequest,
                loginAccountID,
                loginAccountType,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId,
                loginPhoneNumber
            )
        }
    }

    //returns true if formatted correctly and false with -1 if not
    //if age is invalid (too large or too small) will return false along with the incorrect age
    fun saveBirthday(
        birthday: String,
        onAccountCreation: Boolean = false
    ): SaveBirthdayReturnValues {

        Log.i("birthDayStuff", "birthday.length: ${birthday.length}")

        //format is MM/DD/YYYY
        if (birthday.length != 10) {
            return SaveBirthdayReturnValues(false, -1)
        }

        val tempBirthYear: Int
        val tempBirthMonth: Int
        val tempBirthDayOfMonth: Int

        if (birthday[0].isDigit() && birthday[1].isDigit()) {
            val birthMonthString = birthday.substring(0, 2)
            tempBirthMonth = birthMonthString.toInt()
        } else {
            return SaveBirthdayReturnValues(false, -1)
        }

        if (birthday[3].isDigit() && birthday[4].isDigit()) {
            val birthDayOfMonthString = birthday.substring(3, 5)
            tempBirthDayOfMonth = birthDayOfMonthString.toInt()
        } else {
            return SaveBirthdayReturnValues(false, -1)
        }

        if (birthday[6].isDigit() && birthday[7].isDigit() &&
            birthday[8].isDigit() && birthday[9].isDigit()
        ) {
            val birthYearString = birthday.substring(6)
            tempBirthYear = birthYearString.toInt()
        } else {
            return SaveBirthdayReturnValues(false, -1)
        }

        //saving here so they all get updated or none do
        val calculatedAge = calcPersonAge(
            GlobalValues.applicationContext,
            tempBirthYear,
            tempBirthMonth,
            tempBirthDayOfMonth,
            errorStore
        )

        if (onAccountCreation) { //updates for account creation
            newAccountInfo.birthYear = tempBirthYear
            newAccountInfo.birthMonth = tempBirthMonth
            newAccountInfo.birthDayOfMonth = tempBirthDayOfMonth
        } else { //updates for logging in before account creation
            if (calculatedAge < GlobalValues.server_imported_values.lowestAllowedAge
                || GlobalValues.server_imported_values.highestAllowedAge < calculatedAge
            ) {
                return SaveBirthdayReturnValues(false, calculatedAge)
            }

            loginBirthYear = tempBirthYear
            loginBirthMonth = tempBirthMonth
            loginBirthDayOfMonth = tempBirthDayOfMonth
        }

        return SaveBirthdayReturnValues(true, calculatedAge)
    }

    fun verifyAndSaveEmailAddress(emailAddress: String): Boolean {
        val emailRegex = Regex(GlobalValues.EMAIL_REGEX_STRING)
        val emailValid = emailRegex.matches(emailAddress)

        if (emailValid) {
            newAccountInfo.emailAddress = emailAddress
            newAccountInfo.requiresEmailAddressVerification = false
        }

        return emailValid
    }

    fun verifyAndSaveFirstName(firstName: String): Pair<Boolean, String> {
        val returnValues = verifyFirstName(firstName)

        if (returnValues.first) {
            newAccountInfo.firstName = returnValues.second
        }

        return returnValues
    }

    fun verifyAndSaveGender(gender: String): Boolean {
        if (!gender.isValidGender()) {
            return false
        }

        newAccountInfo.gender = gender
        return true
    }

    //send birthday address to server
    fun sendBirthdayInfo(callingFragmentInstanceID: String) {
        val birthYear = newAccountInfo.birthYear
        val birthMonth = newAccountInfo.birthMonth
        val birthDayOfMonth = newAccountInfo.birthDayOfMonth

        CoroutineScope(ioDispatcher).launch {
            repository.setBirthday(
                birthYear,
                birthMonth,
                birthDayOfMonth,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    //send email address to server
    fun sendEmailAddressInfo(callingFragmentInstanceID: String) {
        val emailAddress = newAccountInfo.emailAddress

        CoroutineScope(ioDispatcher).launch {
            repository.setEmailAddress(
                emailAddress,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    //send birthday address to server
    fun sendFirstName(callingFragmentInstanceID: String) {
        val firstName = newAccountInfo.firstName
        Log.i("verifyName", "Shared Login Name: $firstName")
        CoroutineScope(ioDispatcher).launch {
            repository.setFirstName(
                firstName,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    //send birthday address to server
    fun sendGenderInfo(callingFragmentInstanceID: String) {
        val gender = newAccountInfo.gender

        CoroutineScope(ioDispatcher).launch {
            repository.setGender(
                gender,
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun getEmailAddressFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.getEmailAddressFromDatabase(
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun getBirthdayFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.getBirthdayFromDatabase(
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun getNameFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.getNameFromDatabase(
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun getGenderFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.getGenderFromDatabase(
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun logoutUserAndClearAccountInDatabase() {
        CoroutineScope(ioDispatcher).launch {
            //clear all data in account
            loginSupportFunctions.runLogoutFunction()
        }
    }

    fun clearAllUserDataAndStopObjects() {
        CoroutineScope(ioDispatcher).launch {
            //clear all data in account
            loginSupportFunctions.clearAllUserDataAndStopObjects()
        }
    }

    fun setDrawablesToDatabaseIndexing(
        callingFragmentInstanceID: String
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.setIconDrawablesDatabaseIndexing(
                callingFragmentInstanceID,
                thisSharedLoginViewModelInstanceId
            )
        }
    }

    fun updateIconDrawablesForNewestVersion() {
        CoroutineScope(ioDispatcher).launch {
            repository.updateIconDrawablesForNewestVersion()
        }
    }

    fun processLoginInformation(loginReturnValue: LoginFunctionReturnValue) {

        when (loginReturnValue.loginFunctionStatus) {
            LoginFunctionStatus.LoggedIn -> {

                //NOTE: this means loginReturnValue.errors == NO_ERRORS && loginReturnValue.response.returnStatus == LOGGED_IN
                loginSMSVerificationCoolDown = loginReturnValue.response.smsCoolDown
                loginBirthDayNotRequired = loginReturnValue.response.birthdayNotNeeded
                birthdayTimestamp =
                    loginReturnValue.response.preLoginTimestamps.birthdayTimestamp
                emailTimestamp = loginReturnValue.response.preLoginTimestamps.emailTimestamp
                genderTimestamp = loginReturnValue.response.preLoginTimestamps.genderTimestamp
                nameTimestamp = loginReturnValue.response.preLoginTimestamps.nameTimestamp
                picturesTimestamp = loginReturnValue.response.picturesTimestampsList
                categoriesTimestamp =
                    loginReturnValue.response.preLoginTimestamps.categoriesTimestamp

                loginPhoneNumber = loginReturnValue.request.phoneNumber
                loginAccountID = loginReturnValue.request.accountId
                loginAccountType = loginReturnValue.request.accountType
            }
            is LoginFunctionStatus.RequiresAuthentication -> {

                //will need phone number saved to continue verification
                loginSMSVerificationCoolDown = loginReturnValue.response.smsCoolDown
                loginBirthDayNotRequired = loginReturnValue.response.birthdayNotNeeded
                loginPhoneNumber = loginReturnValue.request.phoneNumber
                loginAccountID = loginReturnValue.request.accountId
                loginAccountType = loginReturnValue.request.accountType
            }
            is LoginFunctionStatus.NoValidAccountStored -> {

                //NOTE: Technically this is repeated inside LoginSelectMethodFragment.kt. Not sure if it is
                // needed here or not.

                //If accountID login needs a phone number entered this will need to be set.
                if ((loginReturnValue.loginFunctionStatus as LoginFunctionStatus.NoValidAccountStored).requiresPhoneNumber) {
                    loginAccountID = loginReturnValue.request.accountId
                    loginAccountType = loginReturnValue.request.accountType
                }
            }
            LoginFunctionStatus.VerificationOnCoolDown,
            LoginFunctionStatus.AttemptingToLogin,
            LoginFunctionStatus.ConnectionError,
            LoginFunctionStatus.DoNothing,
            is LoginFunctionStatus.ErrorLoggingIn,
            LoginFunctionStatus.Idle,
            LoginFunctionStatus.ServerDown,
            -> {
            }
        }
    }

    fun setLiveDataForLocationRequestComplete(locationStatus: LocationReturnErrorStatus) {
        CoroutineScope(Dispatchers.Main).launch {
            _finishedGettingLocation.value =
                EventWrapperWithKeyString(locationStatus, mostRecentFragmentIDRequestingLocation)
        }
    }

    private fun storeErrorForViewModel(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String
    ) {
        val errorMessage = passedErrMsg + "\n" +
                "navigatePastLoginSelectFragment: $navigatePastLoginSelectFragment\n" +
                "loginBirthMonth: $loginBirthMonth\n" +
                "loginBirthYear: $loginBirthYear\n" +
                "loginBirthDayOfMonth: $loginBirthDayOfMonth\n" +
                "loginUpdateAccount: $loginInstallIdAddedCommand\n" +
                "loginPhoneNumber: $loginPhoneNumber\n" +
                "loginSMSVerificationCoolDown: $loginSMSVerificationCoolDown\n" +
                "loginBirthDayNotRequired: $loginBirthDayNotRequired\n" +
                "birthdayTimestamp: $birthdayTimestamp\n" +
                "emailTimestamp: $emailTimestamp\n" +
                "genderTimestamp: $genderTimestamp\n" +
                "nameTimestamp: $nameTimestamp\n" +
                "picturesTimestamp: $picturesTimestamp\n" +
                "categoriesTimestamp: $categoriesTimestamp\n" +
                "loginAccountType: $loginAccountType\n" +
                "loginAccountID: $loginAccountID\n" +
                "newAccountEmailAddress: ${newAccountInfo.emailAddress}\n" +
                "newAccountRequiresEmailAddressVerification: ${newAccountInfo.requiresEmailAddressVerification}\n" +
                "newAccountEmailAddressStatus: ${newAccountInfo.emailAddressStatus}\n" +
                "newAccountBirthYear: ${newAccountInfo.birthYear}\n" +
                "newAccountBirthMonth: ${newAccountInfo.birthMonth}\n" +
                "newAccountBirthDayOfMonth: ${newAccountInfo.birthDayOfMonth}\n" +
                "newAccountBirthDayStatus: ${newAccountInfo.birthDayStatus}\n" +
                "newAccountGender: ${newAccountInfo.gender}\n" +
                "newAccountGenderStatus: ${newAccountInfo.genderStatus}\n" +
                "newAccountFirstName: ${newAccountInfo.firstName}\n" +
                "newAccountFirstNameStatus: ${newAccountInfo.firstNameStatus}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorMessage,
        )
    }

    init {

        CoroutineScope(ioDispatcher).launch {
            LoginFunctions.viewModelSubscribe(loginViewModelUUID, ::respondToLogin)
        }

        repository.setDrawablesInDatabaseInfo.observeForever(setDrawablesInDatabaseInfoObserver) //set drawables to database

        repository.smsVerificationData.observeForever(smsVerificationObserver) //sms verification return values

        repository.setFieldReturnValue.observeForever(setFieldTimestampObserver)

        repository.accountRecoveryReturnValue.observeForever(accountRecoveryReturnValueObserver)

        repository.returnEmailFromDatabase.observeForever(returnEmailFromDatabaseObserver) //returns email address and timestamp from database
        repository.returnBirthdayFromDatabase.observeForever(returnBirthdayFromDatabaseObserver) //returns birthday and timestamp from database
        repository.returnFirstNameFromDatabase.observeForever(returnFirstNameFromDatabaseObserver) //returns first name and timestamp from database
        repository.returnGenderFromDatabase.observeForever(returnGenderFromDatabaseObserver) //returns gender and timestamp from database

        selectPicturesRepository.returnGrpcFunctionErrorStatusEnumToActivity.observeForever(
            handleGrpcErrorStatusReturnValuesObserver
        ) //returns gender and timestamp from database

    }

    override fun onCleared() {

        CoroutineScope(ioDispatcher).launch {
            LoginFunctions.viewModelUnSubscribe(loginViewModelUUID)
        }

        repository.setDrawablesInDatabaseInfo.removeObserver(setDrawablesInDatabaseInfoObserver)

        repository.smsVerificationData.removeObserver(smsVerificationObserver)

        repository.setFieldReturnValue.removeObserver(setFieldTimestampObserver)

        repository.accountRecoveryReturnValue.removeObserver(accountRecoveryReturnValueObserver)

        repository.returnEmailFromDatabase.removeObserver(returnEmailFromDatabaseObserver)
        repository.returnBirthdayFromDatabase.removeObserver(returnBirthdayFromDatabaseObserver)
        repository.returnFirstNameFromDatabase.removeObserver(returnFirstNameFromDatabaseObserver)
        repository.returnGenderFromDatabase.removeObserver(returnGenderFromDatabaseObserver)

        selectPicturesRepository.returnGrpcFunctionErrorStatusEnumToActivity.removeObserver(
            handleGrpcErrorStatusReturnValuesObserver
        )

        super.onCleared()
    }

}

/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 */
class SharedLoginViewModelFactory(
    private val repository: LoginRepository,
    private val loginSupportFunctions: LoginSupportFunctions,
    private val selectPicturesRepository: SelectPicturesRepository,
    private val errorStore: StoreErrorsInterface = StoreErrors(),
    private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedLoginViewModel::class.java)) {
            return SharedLoginViewModel(
                repository,
                loginSupportFunctions,
                selectPicturesRepository,
                errorStore,
                ioDispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}