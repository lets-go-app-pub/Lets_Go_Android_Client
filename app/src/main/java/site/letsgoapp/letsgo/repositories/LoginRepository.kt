package site.letsgoapp.letsgo.repositories

import account_login_type.AccountLoginTypeEnum
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.FirstNameDataHolder
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.GenderDataHolder
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.*
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import java.io.File
import java.io.IOException

class LoginRepository(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val iconsDataSource: IconsDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val _smsVerificationData: KeyStringMutableLiveData<DataHolderWrapper<SmsVerificationDataDataHolder>> =
        KeyStringMutableLiveData()
    val smsVerificationData: KeyStringLiveData<DataHolderWrapper<SmsVerificationDataDataHolder>> =
        _smsVerificationData //NOTE: This can be null, it means no internet connection or login attempt number exceeded

    private val _setFieldReturnValue: KeyStringMutableLiveData<DataHolderWrapper<SetFieldsReturnValues>> =
        KeyStringMutableLiveData()
    val setFieldReturnValue: KeyStringLiveData<DataHolderWrapper<SetFieldsReturnValues>> =
        _setFieldReturnValue //NOTE: This will be set to the timestamp of the set field, if it is -2 it is invalid data, if -1 it was not set

    private val _accountRecoveryReturnValue: KeyStringMutableLiveData<DataHolderWrapper<AccountRecoveryReturnValues>> =
        KeyStringMutableLiveData()
    val accountRecoveryReturnValue: KeyStringLiveData<DataHolderWrapper<AccountRecoveryReturnValues>> =
        _accountRecoveryReturnValue

    private val _returnEmailFromDatabase: KeyStringMutableLiveData<DataHolderWrapper<ReturnEmailFromDatabaseDataHolder>> =
        KeyStringMutableLiveData()
    val returnEmailFromDatabase: KeyStringLiveData<DataHolderWrapper<ReturnEmailFromDatabaseDataHolder>> =
        _returnEmailFromDatabase

    private val _returnBirthdayFromDatabase: KeyStringMutableLiveData<DataHolderWrapper<BirthdayHolder>> =
        KeyStringMutableLiveData()
    val returnBirthdayFromDatabase: KeyStringLiveData<DataHolderWrapper<BirthdayHolder>> =
        _returnBirthdayFromDatabase

    private val _returnFirstNameFromDatabase: KeyStringMutableLiveData<DataHolderWrapper<FirstNameDataHolder>> =
        KeyStringMutableLiveData()
    val returnFirstNameFromDatabase: KeyStringLiveData<DataHolderWrapper<FirstNameDataHolder>> =
        _returnFirstNameFromDatabase

    private val _returnGenderFromDatabase: KeyStringMutableLiveData<DataHolderWrapper<GenderDataHolder>> =
        KeyStringMutableLiveData()
    val returnGenderFromDatabase: KeyStringLiveData<DataHolderWrapper<GenderDataHolder>> =
        _returnGenderFromDatabase

    private val _setDrawablesInDatabaseInfo: KeyStringMutableLiveData<DataHolderWrapper<Unit>> =
        KeyStringMutableLiveData()
    val setDrawablesInDatabaseInfo: KeyStringLiveData<DataHolderWrapper<Unit>> =
        _setDrawablesInDatabaseInfo

    suspend fun beginManualLoginToServerWithAccountInfo(
        accountType: AccountLoginTypeEnum.AccountLoginType,
        phoneNumber: String = "~",
        accountID: String = "~",
        callingFragmentInstanceID: String,
    ) {
        (applicationContext as LetsGoApplicationClass)
            .loginFunctions
            .beginManualLoginToServer(
                callingFragmentInstanceID,
                BasicLoginInfo(
                    accountType,
                    phoneNumber,
                    accountID,
                )
            )
    }

    suspend fun beginManualLoginToServerExtractAccountInfo(callingFragmentInstanceID: String) {

        (applicationContext as LetsGoApplicationClass)
            .loginFunctions
            .beginManualLoginToServer(callingFragmentInstanceID)
    }

    suspend fun beginLoginToServerWhenReceivedInvalidToken(callingFragmentInstanceID: String) =
        withContext(ioDispatcher) {
            (applicationContext as LetsGoApplicationClass)
                .loginFunctions
                .beginLoginToServerWhenReceivedInvalidToken(callingFragmentInstanceID)
        }

    private fun getStringForSMSVerificationRequest(smsVerificationRequest: SMSVerificationRequest): String {
        return "--Request Value--\n" +
                "Request Phone Number Or Account Id: ${smsVerificationRequest.phoneNumberOrAccountId}\n" +
                "Request Verification Code: ${smsVerificationRequest.verificationCode}\n" +
                "Request Let's Go Version: ${smsVerificationRequest.letsGoVersion}\n" +
                "Request Installation Id: ${smsVerificationRequest.installationId}\n" +
                "Request Update Account Method: ${smsVerificationRequest.installationIdAddedCommand}}\n" +
                "Request Birth Year: ${smsVerificationRequest.installationIdBirthYear}\n" +
                "Request Birth Month: ${smsVerificationRequest.installationIdBirthMonth}\n" +
                "Request Birth Day Of Month: ${smsVerificationRequest.installationIdBirthDayOfMonth}\n"
    }

    //primary SMSVerification function
    suspend fun runSmsVerification(
        smsVerificationRequest: SMSVerificationRequest.Builder,
        accountID: String,
        accountType: AccountLoginTypeEnum.AccountLoginType,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String,
        passedPhoneNumber: String
    ) = withContext(ioDispatcher) {

        val returnVal = clientsIntermediate.smsVerificationClientSMSVerification(
            smsVerificationRequest.build()
        )

        Log.i(
            "return_value",
            "clientsIntermediate :${clientsIntermediate is ClientsSourceIntermediate}\nreturnVal: $returnVal"
        )

        if (returnVal.response == null) { //this means the function was running somewhere else and so returned nothing

            //do not re run the sms verification function, only 1 should be running at a time
            val errorString =
                "An SMS Verification function was attempted to run WHILE one was already running.\n" +
                        getStringForSMSVerificationRequest(smsVerificationRequest.build())

            errorHandling.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorString,
                applicationContext
            )

            //this means a version of this function should already be running so there is no reason to send a signal back to the view model

        } else { //this means the function ran and returned a response

            when (returnVal.androidErrorEnum) {
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {

                    when (returnVal.response.returnStatus) {
                        SMSVerificationResponse.Status.SUCCESS -> {

                            val phoneNumber =
                                if (accountType == AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT) {
                                    smsVerificationRequest.phoneNumberOrAccountId
                                } else {
                                    passedPhoneNumber
                                }

                            //log user in
                            beginManualLoginToServerWithAccountInfo(
                                accountType,
                                phoneNumber,
                                accountID,
                                callingFragmentInstanceID
                            )
                        }
                        //handled inside fragment
                        SMSVerificationResponse.Status.VERIFICATION_ON_COOLDOWN,
                        SMSVerificationResponse.Status.PENDING_ACCOUNT_NOT_FOUND,
                        SMSVerificationResponse.Status.VERIFICATION_CODE_EXPIRED,
                        SMSVerificationResponse.Status.INVALID_VERIFICATION_CODE,
                        SMSVerificationResponse.Status.INCORRECT_BIRTHDAY,
                        -> {
                            withContext(Main) {
                                _smsVerificationData.setValue(
                                    DataHolderWrapper(
                                        SmsVerificationDataDataHolder(
                                            GrpcFunctionErrorStatusEnum.NO_ERRORS,
                                            returnVal.response
                                        ),
                                        callingFragmentInstanceID
                                    ),
                                    sharedLoginViewModelInstanceId
                                )
                            }
                        }
                        SMSVerificationResponse.Status.DATABASE_DOWN -> {
                            withContext(Main) {
                                _smsVerificationData.setValue(
                                    DataHolderWrapper(
                                        SmsVerificationDataDataHolder(
                                            GrpcFunctionErrorStatusEnum.SERVER_DOWN,
                                            returnVal.response
                                        ),
                                        callingFragmentInstanceID
                                    ),
                                    sharedLoginViewModelInstanceId
                                )
                            }
                        }
                        SMSVerificationResponse.Status.INVALID_PHONE_NUMBER_OR_ACCOUNT_ID,
                        SMSVerificationResponse.Status.OUTDATED_VERSION,
                        SMSVerificationResponse.Status.VALUE_NOT_SET,
                        SMSVerificationResponse.Status.UNKNOWN,
                        SMSVerificationResponse.Status.LG_ERROR,
                        SMSVerificationResponse.Status.UNRECOGNIZED,
                        SMSVerificationResponse.Status.INVALID_UPDATE_ACCOUNT_METHOD_PASSED,
                        SMSVerificationResponse.Status.INVALID_INSTALLATION_ID,
                        null,
                        -> {

                            if (returnVal.response.returnStatus != SMSVerificationResponse.Status.LG_ERROR
                            ) { //LG_ERROR was stored on server side
                                val errorString =
                                    "Unknown exception thrown from login function.\n" +
                                            "Response: ${returnVal.response.returnStatus}\n" +
                                            getStringForSMSVerificationRequest(
                                                smsVerificationRequest.build()
                                            )

                                errorHandling.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorString,
                                    applicationContext
                                )
                            }

                            if (returnVal.response.returnStatus == SMSVerificationResponse.Status.INVALID_INSTALLATION_ID) {
                                reGenerateInstallationId(applicationContext)
                            }

                            //NOTE: when this function is called the user is not yet logged in so LOG_USER_OUT
                            // is pointless just use CLEAR_DATABASE_INFO
                            withContext(Main) {
                                _smsVerificationData.setValue(
                                    DataHolderWrapper(
                                        SmsVerificationDataDataHolder(
                                            GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
                                            returnVal.response
                                        ),
                                        callingFragmentInstanceID
                                    ),
                                    sharedLoginViewModelInstanceId
                                )
                            }
                        }

                    }

                }
                GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                    withContext(Main) {
                        _smsVerificationData.setValue(
                            DataHolderWrapper(
                                SmsVerificationDataDataHolder(
                                    GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
                                    returnVal.response
                                ),
                                callingFragmentInstanceID
                            ),
                            sharedLoginViewModelInstanceId
                        )
                    }
                }
                GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                    withContext(Main) {
                        _smsVerificationData.setValue(
                            DataHolderWrapper(
                                SmsVerificationDataDataHolder(
                                    GrpcFunctionErrorStatusEnum.SERVER_DOWN,
                                    returnVal.response
                                ),
                                callingFragmentInstanceID
                            ),
                            sharedLoginViewModelInstanceId
                        )
                    }
                }
                GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                    val errorString = "Unknown exception thrown from login function.\n" +
                            getStringForSMSVerificationRequest(smsVerificationRequest.build())

                    errorHandling.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorString,
                        applicationContext
                    )

                    withContext(Main) {
                        _smsVerificationData.setValue(
                            DataHolderWrapper(
                                SmsVerificationDataDataHolder(
                                    GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
                                    returnVal.response
                                ),
                                callingFragmentInstanceID
                            ),
                            sharedLoginViewModelInstanceId
                        )
                    }
                }
            }
        }
    }

    //sets the birthday on the server
    suspend fun setBirthday(
        birthYear: Int,
        birthMonth: Int,
        birthDayOfMonth: Int,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setBirthdayClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            birthYear,
            birthMonth,
            birthDayOfMonth
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Main) {
                _setFieldReturnValue.setValue(
                    DataHolderWrapper(
                        SetFieldsReturnValues(
                            returnVal.errorStatus,
                            returnVal.invalidParameterPassed
                        ),
                        callingFragmentInstanceID
                    ),
                    sharedLoginViewModelInstanceId
                )
            }
        }
    }

    //sets the email on the server
    suspend fun setEmailAddress(
        emailAddress: String,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setEmailClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            emailAddress
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Main) {
                _setFieldReturnValue.setValue(
                    DataHolderWrapper(
                        SetFieldsReturnValues(
                            returnVal.errorStatus,
                            returnVal.invalidParameterPassed
                        ),
                        callingFragmentInstanceID
                    ),
                    sharedLoginViewModelInstanceId
                )
            }
        }
    }

    //sets the birthday on the server
    suspend fun setFirstName(
        firstName: String,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setFirstNameClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            firstName
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Main) {
                _setFieldReturnValue.setValue(
                    DataHolderWrapper(
                        SetFieldsReturnValues(
                            returnVal.errorStatus,
                            returnVal.invalidParameterPassed
                        ),
                        callingFragmentInstanceID
                    ),
                    sharedLoginViewModelInstanceId
                )
            }
        }
    }

    //sets the gender on the server
    suspend fun setGender(
        gender: String,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setGenderClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            gender
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Main) {
                _setFieldReturnValue.setValue(
                    DataHolderWrapper(
                        SetFieldsReturnValues(
                            returnVal.errorStatus,
                            returnVal.invalidParameterPassed
                        ),
                        callingFragmentInstanceID
                    ),
                    sharedLoginViewModelInstanceId
                )
            }
        }
    }

    suspend fun getEmailAddressFromDatabase(
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {
        val emailAddress = accountInfoDataSource.getEmail()

        withContext(Main) {
            _returnEmailFromDatabase.setValue(
                DataHolderWrapper(
                    ReturnEmailFromDatabaseDataHolder(
                        emailAddress
                    ),
                    callingFragmentInstanceID
                ),
                sharedLoginViewModelInstanceId
            )
        }
    }

    suspend fun getBirthdayFromDatabase(
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {
        val databaseBirthdayInfo = accountInfoDataSource.getBirthdayInfo()

        val birthdayInfo =
            if (databaseBirthdayInfo == null) {

                val errorString =
                    "Birthday was not set inside of the database, this should never happen.\n"

                errorHandling.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorString,
                    applicationContext
                )

                //set to defaults and LoginGetBirthdayFragment will display 'none'.
                BirthdayHolder(
                    -1,
                    -1,
                    -1,
                    -1L
                )
            } else {
                databaseBirthdayInfo
            }

        withContext(Main) {
            _returnBirthdayFromDatabase.setValue(
                DataHolderWrapper(
                    birthdayInfo,
                    callingFragmentInstanceID
                ),
                sharedLoginViewModelInstanceId
            )
        }
    }

    suspend fun getNameFromDatabase(
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {
        val databaseFirstNameInfo = accountInfoDataSource.getFirstNameInfo()

        val firstNameInfo =
            if (databaseFirstNameInfo == null) {

                val errorString =
                    "User name was not set inside of the database, this should never happen.\n"

                errorHandling.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorString,
                    applicationContext
                )

                //set to defaults and LoginGetBirthdayFragment will display 'none'.
                FirstNameDataHolder(
                    "~",
                    -1L
                )
            } else {
                databaseFirstNameInfo
            }

        withContext(Main) {
            _returnFirstNameFromDatabase.setValue(
                DataHolderWrapper(
                    firstNameInfo,
                    callingFragmentInstanceID
                ),
                sharedLoginViewModelInstanceId
            )
        }
    }

    suspend fun getGenderFromDatabase(
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val databaseGenderInfo =
            accountInfoDataSource.getGenderInfo()//Pair(gender, genderTimestamp)

        val genderInfo =
            if (databaseGenderInfo == null) {

                val errorString =
                    "User gender was not set inside of the database, this should never happen.\n"

                errorHandling.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorString,
                    applicationContext
                )

                //set to defaults and LoginGetBirthdayFragment will display 'none'.
                GenderDataHolder(
                    "~",
                    -1L
                )
            } else {
                databaseGenderInfo
            }

        withContext(Main) {
            _returnGenderFromDatabase.setValue(
                DataHolderWrapper(
                    genderInfo,
                    callingFragmentInstanceID
                ),
                sharedLoginViewModelInstanceId
            )
        }
    }

    //Requires IconsDatabase to be locked
    private suspend fun clearAllIconsFromDatabaseAndSetDefaults(
        transactionWrapper: TransactionWrapper
    ) = withContext(ioDispatcher) {

        transactionWrapper.runTransaction {

            val sortedDatabaseIcons = iconsDataSource.getAllIcons().toMutableList()
            val (drawableIconLastUpdatedTimestampMs, sortedDrawableResourceIcons) = getAllPreLoadedIcons()

            for (databaseIcon in sortedDatabaseIcons) {
                if (databaseIcon.iconIsDownloaded) {
                    //clean up icon info
                    //Don't use the deleteFileWorker here, it can delete a file with a delay, so it
                    // can delete a file AFTER IT WAS DOWNLOADED AGAIN.
                    val file = File(databaseIcon.iconFilePath)

                    if (file.exists()) {
                        try {
                            file.delete()
                        } catch (e: IOException) {

                            val errorMessage =
                                "IOException occurred updateIconDrawablesForNewestVersion inside (possible memory leak).\n" +
                                        "pathName: ${databaseIcon.iconFilePath}" +
                                        "exception message: ${e.message}"

                            errorHandling.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage,
                                applicationContext
                            )
                        }
                    }
                }
            }

            iconsDataSource.clearTable()

            val iconDataEntities = mutableListOf<IconsDataEntity>()
            for (i in sortedDrawableResourceIcons.indices) {
                iconDataEntities.add(
                    IconsDataEntity(
                        i,
                        false,
                        "",
                        sortedDrawableResourceIcons[i],
                        drawableIconLastUpdatedTimestampMs,
                        true
                    )
                )
            }

            iconsDataSource.insertAllIcons(
                iconDataEntities
            )
        }
    }

    //this function is called the first time the app is opened on the phone
    suspend fun setIconDrawablesDatabaseIndexing(
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String,
    ) = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.ICONS
        )
        transactionWrapper.runTransaction {
            clearAllIconsFromDatabaseAndSetDefaults(
                this
            )

            this.runAfterTransaction {
                withContext(Main) {
                    _setDrawablesInDatabaseInfo.setValue(
                        DataHolderWrapper(
                            Unit,
                            callingFragmentInstanceID
                        ),
                        sharedLoginViewModelInstanceId
                    )
                }
            }
        }
    }

    suspend fun updateIconDrawablesForNewestVersion() = withContext(ioDispatcher) {

        //It's easier to remove all the icons. But there is the chance that some of the icons have
        // been updated in the meantime. This means that if the user is logged in for example, it
        // could delete a valid icon that they need to view. However, this should only be called
        // on a version update, so before the user views anything.

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.ICONS
        )
        transactionWrapper.runTransaction {
            val sortedDatabaseIcons = iconsDataSource.getAllIcons()
            val (drawableIconLastUpdatedTimestampMs, sortedDrawableResourceIcons) = getAllPreLoadedIcons()

            var redoAllIcons = false
            for ((i, databaseIcon) in sortedDatabaseIcons.withIndex()) {
                if (i != databaseIcon.iconIndex) {
                    redoAllIcons = true
                    break
                }
            }

            if (redoAllIcons) {
                val errorMessage =
                    "Database icons did not follow proper indexing. Icons should never be deleted, " +
                            "just set to no longer active. This means that all indexes should always exist.\n" +
                            "icons: $sortedDatabaseIcons"

                errorHandling.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

                clearAllIconsFromDatabaseAndSetDefaults(
                    this
                )
            } else {

                val newDatabaseIconsList = mutableListOf<IconsDataEntity>()
                //update any out of date icons
                for (databaseIcon in sortedDatabaseIcons) {
                    if (databaseIcon.iconTimestamp < drawableIconLastUpdatedTimestampMs
                        && databaseIcon.iconIndex < sortedDrawableResourceIcons.size
                    ) {
                        Log.i("icon_stuff", "updated index ${databaseIcon.iconIndex}")
                        if (databaseIcon.iconIsDownloaded) {
                            //Don't use the deleteFileWorker here, it can delete a file with a delay, so it
                            // can delete a file AFTER IT WAS DOWNLOADED AGAIN.
                            val file = File(databaseIcon.iconFilePath)

                            if (file.exists()) {
                                try {
                                    file.delete()
                                } catch (e: IOException) {

                                    val errorMessage =
                                        "IOException occurred updateIconDrawablesForNewestVersion inside (possible memory leak).\n" +
                                                "pathName: ${databaseIcon.iconFilePath}" +
                                                "exception message: ${e.message}"

                                    errorHandling.storeError(
                                        Thread.currentThread().stackTrace[2].fileName,
                                        Thread.currentThread().stackTrace[2].lineNumber,
                                        printStackTraceForErrors(),
                                        errorMessage,
                                        applicationContext
                                    )
                                }
                            }
                        }

                        newDatabaseIconsList.add(
                            IconsDataEntity(
                                databaseIcon.iconIndex,
                                false,
                                "",
                                sortedDrawableResourceIcons[databaseIcon.iconIndex],
                                drawableIconLastUpdatedTimestampMs,
                                true
                            )
                        )
                    } else {
                        Log.i("icon_stuff", "non updated index ${databaseIcon.iconIndex}")
                        newDatabaseIconsList.add(
                            databaseIcon
                        )
                    }
                }

                //add any missing icons
                for (i in sortedDatabaseIcons.lastIndex + 1 until sortedDrawableResourceIcons.size) {
                    Log.i("icon_stuff", "added index $i")
                    newDatabaseIconsList.add(
                        IconsDataEntity(
                            i,
                            false,
                            "",
                            sortedDrawableResourceIcons[i],
                            drawableIconLastUpdatedTimestampMs,
                            true
                        )
                    )
                }

                iconsDataSource.insertAllIcons(
                   newDatabaseIconsList
                )
            }
        }
    }

    suspend fun runBeginAccountRecovery(
        phoneNumber: String,
        callingFragmentInstanceID: String,
        sharedLoginViewModelInstanceId: String
    ) {
        val returnVal = runBeginAccountRecovery(
            phoneNumber,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
        )

        if (returnVal.errors != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Main) {

                _accountRecoveryReturnValue.setValue(
                    DataHolderWrapper(
                        returnVal,
                        callingFragmentInstanceID
                    ),
                    sharedLoginViewModelInstanceId
                )
            }
        }
    }

}