package site.letsgoapp.letsgo.standAloneObjects.loginFunctions

import access_status.AccessStatusEnum
import account_login_type.AccountLoginTypeEnum
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.LoginAccountStatus
import loginfunction.LoginRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import report_enums.ReportMessages
import request_fields.ServerIconsResponse
import requestmessages.RequestMessages
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.convertStringToBlockedAccountsMap
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentMessageCommandType
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDataEntity
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.loginGetPhoneNumberFragment.LoginGetPhoneNumberFragmentTest
import site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment.VerifyPhoneNumbersFragmentTest
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions.Companion.LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions.Companion.currentAccountOID
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions.Companion.currentLoginToken
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions.Companion.loginTokenExpirationTime
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStartDeleteFileInterface
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.generateDeletedPictureFromServer
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.generateRandomExistingPictureFromServer
import site.letsgoapp.letsgo.utilities.*
import status_enum.StatusEnum
import java.io.*
import java.util.*

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginFunctionsTest {

    /** Some cases are checked inside [LoginGetPhoneNumberFragmentTest]
     * and [VerifyPhoneNumbersFragmentTest]. **/

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var callingFragmentInstanceId: String
    private lateinit var subscriberUUID: UUID

    @Before
    fun setUp() {

        fakeStoreErrors = FakeStoreErrors(testDispatcher)
        callingFragmentInstanceId = UUID.randomUUID().toString()
        subscriberUUID = UUID.randomUUID()

        applicationContext = ApplicationProvider.getApplicationContext()

        cleanupPreviouslySetValues(
            applicationContext,
            FakeStartDeleteFileInterface()
        )

        setupCategoriesAndActivitiesForTesting(
            fakeStoreErrors,
            testDispatcher
        )

        setupBasicTestingInjections(
            fakeStoreErrors,
            testDispatcher,
            FakeStartDeleteFileInterface()
        )

        loginFunctionsSetup(
            applicationContext,
            fakeStoreErrors,
            testDispatcher,
            FakeStartDeleteFileInterface()
        )

        loginSupportFunctionsSetup(
            applicationContext,
            fakeStoreErrors,
            testDispatcher,
            FakeStartDeleteFileInterface()
        )

        (applicationContext as LetsGoApplicationClass).loginSupportFunctions.cancelWorkers()

        runBlocking {
            ServiceLocator.iconsDatabase?.iconsDatabaseDao?.clearTable()
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        FakeClientSourceIntermediate.resetStaticToDefaults()
        cleanServiceLocatorVariables(applicationContext)
    }

    //This is used to force the primitive to be passed by reference.
    private data class BooleanWrapper(var bool: Boolean = false)

    private suspend fun runManualLogin(basicLoginInfo: BasicLoginInfo?) {
        (applicationContext as LetsGoApplicationClass).loginFunctions.beginManualLoginToServer(
            callingFragmentInstanceId,
            basicLoginInfo
        )
    }

    private suspend fun spinUntilConditionMet(
        checkCondition: () -> Boolean
    ) {
        val startTime = SystemClock.uptimeMillis()
        //Spin for 10 seconds or until the condition becomes true.
        while (startTime + 10000 > SystemClock.uptimeMillis()) {
            yield()
            if (checkCondition()) {
                return
            }
        }
    }

    //This must be passed in by the wrapper in order to pass by reference.
    private suspend fun spinUntilConditionMet(returnValue: BooleanWrapper) {
        spinUntilConditionMet {
            returnValue.bool
        }
    }

    private suspend fun generateLoginRequest(
        useDefaultTimestamps: Boolean,
        originalIconTimestamps: List<Long>,
        userAccountInfo: AccountInfoDataEntity?
    ): LoginRequest {
        val builder = LoginRequest.newBuilder()
            .setPhoneNumber(FakeClientSourceIntermediate.accountStoredOnServer?.phoneNumber)
            .setAccountId("~")
            .setInstallationId(GlobalValues.installationId)
            .setLetsGoVersion(GlobalValues.Lets_GO_Version_Number)
            .setAccountType(AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT)
            .setDeviceName(GlobalValues.deviceName)
            .setApiNumber(Build.VERSION.SDK_INT)
            .addAllIconTimestamps(originalIconTimestamps)

        if (useDefaultTimestamps) {
            builder
                .setBirthdayTimestamp(-1L)
                .setEmailTimestamp(-1L)
                .setGenderTimestamp(-1L)
                .setNameTimestamp(-1L)
                .setCategoriesTimestamp(-1L).postLoginInfoTimestamp = -1L
        } else {
            val extractedUserAccountInfo =
                userAccountInfo
                    ?: ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()

            builder
                .setBirthdayTimestamp(
                    extractedUserAccountInfo?.birthdayTimestamp
                        ?: -1L
                )
                .setEmailTimestamp(
                    extractedUserAccountInfo?.emailTimestamp
                        ?: -1L
                )
                .setGenderTimestamp(
                    extractedUserAccountInfo?.genderTimestamp
                        ?: -1L
                )
                .setNameTimestamp(
                    extractedUserAccountInfo?.firstNameTimestamp
                        ?: -1L
                )
                .setCategoriesTimestamp(
                    extractedUserAccountInfo?.categoriesTimestamp
                        ?: -1L
                )
                .postLoginInfoTimestamp = extractedUserAccountInfo?.postLoginTimestamp ?: -1L
        }

        return builder.build()
    }

    private suspend fun checkLoginRequest(
        returnValue: LoginFunctionReturnValue,
        useDefaultTimestampsInRequest: Boolean,
        originalIconTimestamps: List<Long> = emptyList(),
        userAccountInfo: AccountInfoDataEntity? = null
    ) {
        assertEquals(LoginFunctionStatus.LoggedIn, returnValue.loginFunctionStatus)

        val generatedLoginRequest = generateLoginRequest(
            useDefaultTimestampsInRequest,
            originalIconTimestamps,
            userAccountInfo
        )

        assertEquals(
            returnValue.request.toByteString(),
            generatedLoginRequest.toByteString()
        )

        //NOTE: The response is generated directly inside FakeClientSourceIntermediate. No
        // reason to explicitly check everything.
        assertEquals(
            returnValue.response.returnStatus,
            LoginAccountStatus.LOGGED_IN
        )

        assertEquals(
            returnValue.response.accessStatus,
            AccessStatusEnum.AccessStatus.ACCESS_GRANTED
        )
    }

    private fun makeSureAccessoryValuesUpdated() {
        assertNotEquals(currentAccountOID, "")
        assertNotEquals(currentLoginToken, GlobalValues.INVALID_LOGIN_TOKEN)
        assertNotEquals(loginTokenExpirationTime, -1L)
        assertEquals(
            GlobalValues.blockedAccounts.getMutableList().size,
            convertStringToBlockedAccountsMap(
                FakeClientSourceIntermediate.accountStoredOnServer!!.blockedAccounts
            ).size
        )
        assertFalse(CategoriesAndActivities.allActivities.isEmpty())
    }

    private fun insertAccountToDatabaseAndServer() {
        runBlocking {
            FakeClientSourceIntermediate.setupCompleteServerAccount()
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.clearTable()
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.insertAccount(
                FakeClientSourceIntermediate.accountStoredOnServer!!
            )

            storeUserPicturesInDatabase(applicationContext)
        }
    }

    private fun checkServerInfoMatchesDatabaseInfo() {
        runBlocking {
            val accountInfo =
                ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()
            assertNotEquals(null, accountInfo)

            //This value is not stored on the client because accountID is not stored, so the
            // comparison doesn't matter
            FakeClientSourceIntermediate.accountStoredOnServer?.accountType =
                accountInfo!!.accountType

            //These values are not stored on the server, so the comparison doesn't matter.
            FakeClientSourceIntermediate.accountStoredOnServer?.chatRoomSortMethodSelected =
                accountInfo.chatRoomSortMethodSelected

            assertEquals(accountInfo, FakeClientSourceIntermediate.accountStoredOnServer)

            val picturesInfo =
                ServiceLocator.accountInfoDatabase?.accountPictureDatabaseDao?.getAllAccountPictures()

            assertNotEquals(null, picturesInfo)

            picturesInfo!!.sortBy {
                it.pictureIndex
            }

            assertEquals(
                FakeClientSourceIntermediate.picturesStoredOnServer.size,
                picturesInfo.size
            )

            for (pic in FakeClientSourceIntermediate.picturesStoredOnServer) {
                assertTrue(pic.indexNumber < picturesInfo.size)

                assertEquals(pic.indexNumber, picturesInfo[pic.indexNumber].pictureIndex)

                //Deleted pictures are represented differently coming from the server and
                // stored on the client. See AccountPictureDataEntity for more info.
                val picString = String(pic.fileInBytes.toByteArray())
                if (picString == "~") { //If picture is deleted.
                    val deletedPicture = AccountPictureDataEntity(pic.indexNumber)

                    assertEquals(deletedPicture, picturesInfo[pic.indexNumber])
                } else {
                    //expected:<2495> but was:<195995>
                    assertEquals(pic.fileSize, picturesInfo[pic.indexNumber].pictureSize)
                    assertEquals(
                        pic.timestampPictureLastUpdated,
                        picturesInfo[pic.indexNumber].pictureTimestamp
                    )

                    val pictureBytes =
                        convertFileToByteArray(picturesInfo[pic.indexNumber].picturePath)

                    assertEquals(picString, String(pictureBytes))
                }
            }
        }
    }

    private fun checkNoAccountInfoStored() {
        runBlocking {
            val accountInfo =
                ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()
            assertEquals(null, accountInfo)
        }
    }

    private suspend fun setupAndRunReturnStatusCheck(
        loginAccountStatus: LoginAccountStatus,
        expectedLoginFunctionStatus: (LoginFunctionStatus) -> Boolean,
    ) {
        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.loginFunctionReturnStatusReturn = loginAccountStatus

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)

            //This will receive a connection error first, then an unmanageable error.
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                assertTrue(expectedLoginFunctionStatus(it.loginFunctionStatus))
                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(null)

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)
    }

    private suspend fun loadBalancingWith_runLoginAfterDelay(
        typeOfError: GrpcAndroidSideErrorsEnum,
        loginFunctionStatus: LoginFunctionStatus
    ) {
        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.grpcAndroidSideErrorReturn = typeOfError

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                assertEquals(loginFunctionStatus, it.loginFunctionStatus)

                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        //Make sure no Work in queue.
        waitForUniqueWorkToEndOrTimeoutByName(
            applicationContext,
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
        )

        runManualLogin(null)

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkServerInfoMatchesDatabaseInfo()

        //Make sure Work added to queue.
        waitForUniqueWorkToStartOrTimeoutByName(
            applicationContext,
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private fun convertFileToByteArray(filePath: String): ByteArray {
        val file = File(filePath)
        val size = file.length().toInt()
        val bytes = ByteArray(size)

        //Throws FileNotFoundException and IOException, allowing them to be thrown to
        // end testing if an error occurs.
        val buf = BufferedInputStream(FileInputStream(file))
        buf.read(bytes, 0, bytes.size)
        buf.close()

        return bytes
    }

    private suspend fun updatePicturesTesting(
        picturesModifications: () -> MutableList<RequestMessages.PictureMessage>
    ) {
        insertAccountToDatabaseAndServer()

        runBlocking {
            val picturesInfo =
                ServiceLocator.accountInfoDatabase?.accountPictureDatabaseDao?.getAllAccountPictures()

            assertNotEquals(null, picturesInfo)

            picturesInfo!!.sortBy {
                it.pictureIndex
            }
        }

        val newPictureTimestamps = picturesModifications()

        newPictureTimestamps.sortBy {
            it.indexNumber
        }

        FakeClientSourceIntermediate.picturesStoredOnServer = newPictureTimestamps

        val userAccountInfo =
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()

        assertNotNull(userAccountInfo)

        runSuccessfulLogin(
            false,
            userAccountInfo = userAccountInfo
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    //Expects an account to be setup on the 'server'.
    //Does not check if errors were stored.
    private suspend fun runSuccessfulLogin(
        useDefaultTimestampsInRequest: Boolean,
        originalIconTimestamps: List<Long> = emptyList(),
        userAccountInfo: AccountInfoDataEntity? = null
    ) {
        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                checkLoginRequest(
                    it,
                    useDefaultTimestampsInRequest,
                    originalIconTimestamps,
                    userAccountInfo
                )

                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(
            BasicLoginInfo(
                AccountLoginTypeEnum.AccountLoginType.PHONE_ACCOUNT,
                FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber
            )
        )

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkServerInfoMatchesDatabaseInfo()

        makeSureAccessoryValuesUpdated()
    }

    @Test
    fun beginManualLoginToServer_basicLoginInfo_null_accountInsideDatabase() =
        runTest(testDispatcher) {

            insertAccountToDatabaseAndServer()

            val returnValue = BooleanWrapper()

            LoginFunctions.cleanDatabaseWorkerSubscribe(
                subscriberUUID
            ) {
                LoginFunctions.receivedMessage(it)
                if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                    checkLoginRequest(
                        it,
                        false
                    )

                    //Should go last, want the rest of this function to run first.
                    returnValue.bool = true
                }
            }

            runManualLogin(null)

            spinUntilConditionMet(returnValue)

            assertTrue(returnValue.bool)

            checkServerInfoMatchesDatabaseInfo()

            makeSureAccessoryValuesUpdated()

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun beginManualLoginToServer_basicLoginInfo_null_noAccountInsideDatabase() =
        runTest(testDispatcher) {

            //Insert account to 'server'.
            FakeClientSourceIntermediate.setupCompleteServerAccount()

            val returnValue = BooleanWrapper()

            LoginFunctions.cleanDatabaseWorkerSubscribe(
                subscriberUUID
            ) {
                LoginFunctions.receivedMessage(it)
                if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                    assertEquals(
                        LoginFunctionStatus.NoValidAccountStored(false),
                        it.loginFunctionStatus
                    )

                    //Should go last, want the rest of this function to run first.
                    returnValue.bool = true
                }
            }

            runManualLogin(null)

            spinUntilConditionMet(returnValue)

            assertTrue(returnValue.bool)

            checkNoAccountInfoStored()

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    //Represents a successful login.
    @Test
    fun beginManualLoginToServer_basicLoginInfo_set_accountInsideDatabase() =
        runTest(testDispatcher) {
            insertAccountToDatabaseAndServer()

            runSuccessfulLogin(false)

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    //Represents a successful login.
    @Test
    fun beginManualLoginToServer_basicLoginInfo_set_noAccountInsideDatabase() =
        runTest(testDispatcher) {
            //Insert account to 'server'.
            FakeClientSourceIntermediate.setupCompleteServerAccount()

            runSuccessfulLogin(true)

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun beginManualLoginToServer_loadBalancingResult_connectionError() = runTest(testDispatcher) {
        loadBalancingWith_runLoginAfterDelay(
            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR,
            LoginFunctionStatus.ConnectionError
        )
    }

    @Test
    fun beginManualLoginToServer_loadBalancingResult_serverDown() = runTest(testDispatcher) {
        loadBalancingWith_runLoginAfterDelay(
            GrpcAndroidSideErrorsEnum.SERVER_DOWN,
            LoginFunctionStatus.ServerDown
        )
    }

    @Test
    fun beginManualLoginToServer_loadBalancingResult_unknownException() = runTest(testDispatcher) {
        //NOTE: Want to make sure no deadlock.

        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.grpcAndroidSideErrorReturn =
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                assertEquals(
                    LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.UNMANAGEABLE_ERROR),
                    it.loginFunctionStatus
                )

                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(null)

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkNoAccountInfoStored()

        //Expect an error here.
        assertNotEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun runLoginAfterDelay_manualLoginInfo_unknownException() = runTest(testDispatcher) {
        //NOTE: Want to make sure no deadlock.
        //NOTE: This will test that the second time load balancing is called (from inside runLoginAfterDelay())
        // the unknown exception returns properly.

        insertAccountToDatabaseAndServer()

        LoginFunctions.TIME_BETWEEN_LOGIN_RETRIES_MS = 50

        //Force runLoginAfterDelay() to run.
        FakeClientSourceIntermediate.grpcAndroidSideErrorReturn =
            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            //This will receive a connection error first, then an unmanageable error.
            if (
                it.callingFragmentInstanceID == callingFragmentInstanceId
                && it.loginFunctionStatus == LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.UNMANAGEABLE_ERROR)
            ) {
                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(null)

        //Wait until the handler has a message queued.
        waitForUniqueWorkToStartOrTimeoutByName(
            applicationContext,
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
        )

        //Return UNKNOWN_EXCEPTION from inside runLoginAfterDelay().
        FakeClientSourceIntermediate.grpcAndroidSideErrorReturn =
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkNoAccountInfoStored()

        //Expect an error here.
        assertNotEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_unknownException() = runTest(testDispatcher) {
        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.loginFunctionReturnStatusReturn = LoginAccountStatus.UNKNOWN

        runManualLogin(null)

        //Make sure Work added to queue.
        waitForUniqueWorkToStartOrTimeoutByName(
            applicationContext,
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
        )

        checkServerInfoMatchesDatabaseInfo()

        assertNotEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_requiresPhoneNumberForAccountCreation() = runTest(testDispatcher) {
        setupAndRunReturnStatusCheck(
            LoginAccountStatus.REQUIRES_PHONE_NUMBER_TO_CREATE_ACCOUNT
        ) {
            if (it is LoginFunctionStatus.NoValidAccountStored) {
                it.requiresPhoneNumber
            } else {
                false
            }
        }

        checkNoAccountInfoStored()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_suspendedAccount() = runTest(testDispatcher) {
        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.loginFunctionReturnStatusReturn =
            LoginAccountStatus.ACCOUNT_CLOSED
        FakeClientSourceIntermediate.loginFunctionAccessStatusReturn =
            AccessStatusEnum.AccessStatus.SUSPENDED

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            //This will receive a connection error first, then an unmanageable error.
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                assertEquals(
                    LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_SUSPENDED),
                    it.loginFunctionStatus
                )
                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(null)

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkNoAccountInfoStored()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_bannedAccount() = runTest(testDispatcher) {
        insertAccountToDatabaseAndServer()

        FakeClientSourceIntermediate.loginFunctionReturnStatusReturn =
            LoginAccountStatus.ACCOUNT_CLOSED
        FakeClientSourceIntermediate.loginFunctionAccessStatusReturn =
            AccessStatusEnum.AccessStatus.BANNED

        val returnValue = BooleanWrapper()

        LoginFunctions.cleanDatabaseWorkerSubscribe(
            subscriberUUID
        ) {
            LoginFunctions.receivedMessage(it)
            //This will receive a connection error first, then an unmanageable error.
            if (it.callingFragmentInstanceID == callingFragmentInstanceId) {
                assertEquals(
                    LoginFunctionStatus.ErrorLoggingIn(LoginErrorsEnum.ACCOUNT_CLOSED_BANNED),
                    it.loginFunctionStatus
                )
                //Should go last, want the rest of this function to run first.
                returnValue.bool = true
            }
        }

        runManualLogin(null)

        spinUntilConditionMet(returnValue)

        assertTrue(returnValue.bool)

        checkNoAccountInfoStored()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_outdatedVersion() = runTest(testDispatcher) {
        setupAndRunReturnStatusCheck(
            LoginAccountStatus.OUTDATED_VERSION
        ) {
            if (it is LoginFunctionStatus.ErrorLoggingIn) {
                it.errorEnum == LoginErrorsEnum.OUTDATED_VERSION
            } else {
                false
            }
        }

        checkServerInfoMatchesDatabaseInfo()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_smsOnCoolDown() = runTest(testDispatcher) {
        setupAndRunReturnStatusCheck(
            LoginAccountStatus.SMS_ON_COOL_DOWN
        ) {
            if (it is LoginFunctionStatus.RequiresAuthentication) {
                it.smsOnCoolDown
            } else {
                false
            }
        }

        checkServerInfoMatchesDatabaseInfo()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnStatus_requiresAuthentication() = runTest(testDispatcher) {
        setupAndRunReturnStatusCheck(
            LoginAccountStatus.REQUIRES_AUTHENTICATION
        ) {
            if (it is LoginFunctionStatus.RequiresAuthentication) {
                !it.smsOnCoolDown
            } else {
                false
            }
        }

        checkServerInfoMatchesDatabaseInfo()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    //fun successfulLogin_accountDoesNotExistInsideDatabase is Tested above as
    // beginManualLoginToServer_basicLoginInfo_set_noAccountInsideDatabase.

    @Test
    fun successfulLogin_accountExistsInsideDatabase_iconsRequireUpdated() =
        runTest(testDispatcher) {
            val totalNumberIcons = (1L..1000L).random()

            for (i in 0L until totalNumberIcons) {
                ServiceLocator.iconsDatabase?.iconsDatabaseDao?.insertIcon(
                    IconsDataEntity(
                        i.toInt(),
                        true,
                        UUID.randomUUID().toString(),
                        "",
                        (1L..System.currentTimeMillis()).random(),
                        true
                    )
                )
            }

            val numberToRequest = (1L..totalNumberIcons).random()

            //Setup icons to say require updates for when login function runs.
            val iconIndexesToRequest = mutableSetOf<Long>()
            for (i in 0L until numberToRequest) {
                iconIndexesToRequest.add((0L until totalNumberIcons).random())
            }
            FakeClientSourceIntermediate.iconIndexRequiringUpdated = iconIndexesToRequest.toList()

            //Setup response for when login requests icons.
            val requestIconsResponseList = mutableListOf<GrpcClientResponse<ServerIconsResponse>>()
            for (i in iconIndexesToRequest) {
                val iconInBytes = UUID.randomUUID().toString()
                requestIconsResponseList.add(
                    GrpcClientResponse(
                        ServerIconsResponse.newBuilder()
                            .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                            .setIndexNumber(i)
                            .setIconInBytes(ByteString.copyFrom(iconInBytes.toByteArray()))
                            .setIconSizeInBytes(iconInBytes.length)
                            .setTimestamp((1L..System.currentTimeMillis()).random())
                            .setIsActive((0..1).random() == 1) //set half to deleted
                            .build(),
                        "~",
                        GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                    )
                )
            }
            FakeClientSourceIntermediate.requestIconsResponse = requestIconsResponseList

            val originalIconTimestamps =
                ServiceLocator.iconsDatabase?.iconsDatabaseDao?.getAllIconTimestamps()

            insertAccountToDatabaseAndServer()

            runSuccessfulLogin(
                false,
                originalIconTimestamps ?: emptyList()
            )

            val allIconsMap = ServiceLocator.iconsDatabase?.iconsDatabaseDao?.getAllIcons()?.map {
                it.iconIndex to it
            }

            assertNotNull(allIconsMap)

            for (iconResponse in requestIconsResponseList) {
                val icon = allIconsMap?.getOrNull(iconResponse.response.indexNumber.toInt())
                assertNotNull(icon)

                if (!iconResponse.response.isActive) { //icon was deleted
                    assertEquals(false, icon?.second?.iconIsDownloaded)
                    assertEquals("", icon?.second?.iconFilePath)
                    assertEquals(
                        applicationContext.resources.getResourceEntryName(GlobalValues.defaultIconImageID),
                        icon?.second?.iconBasicResourceEntryName
                    )
                } else {
                    val iconBytes =
                        convertFileToByteArray(icon!!.second.iconFilePath)

                    assertEquals(
                        String(iconBytes),
                        String(iconResponse.response.iconInBytes.toByteArray())
                    )
                }

                //Make sure icon was updated.
                assertEquals(iconResponse.response.timestamp, icon!!.second.iconTimestamp)
                assertEquals(iconResponse.response.isActive, icon.second.iconActive)
            }

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_requiresStandardUpdates() =
        runTest(testDispatcher) {

            insertAccountToDatabaseAndServer()

            //Flip the algorithm search option.
            FakeClientSourceIntermediate.accountStoredOnServer?.algorithmSearchOptions =
                if (FakeClientSourceIntermediate.accountStoredOnServer?.algorithmSearchOptions == AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_ACTIVITY.number) {
                    AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY.number
                } else {
                    AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_ACTIVITY.number
                }

            val birthdayCalendar = generateRandomBirthdayForTesting()

            val userAge = calcPersonAgeNoError(
                birthdayCalendar.get(Calendar.YEAR),
                birthdayCalendar.get(Calendar.MONTH) + 1,
                birthdayCalendar.get(Calendar.DAY_OF_MONTH)
            ) {
                val calendar = Calendar.getInstance()
                calendar
            }

            FakeClientSourceIntermediate.accountStoredOnServer!!.age = userAge
            FakeClientSourceIntermediate.accountStoredOnServer!!.birthYear =
                birthdayCalendar.get(Calendar.YEAR)
            FakeClientSourceIntermediate.accountStoredOnServer!!.birthMonth =
                birthdayCalendar.get(Calendar.MONTH) + 1
            FakeClientSourceIntermediate.accountStoredOnServer!!.birthDayOfMonth =
                birthdayCalendar.get(Calendar.DAY_OF_MONTH)
            FakeClientSourceIntermediate.accountStoredOnServer!!.birthdayTimestamp += (1..100000).random()

            FakeClientSourceIntermediate.accountStoredOnServer!!.emailAddress =
                generateRandomEmailForTesting()
            FakeClientSourceIntermediate.accountStoredOnServer!!.emailTimestamp += (1..100000).random()

            FakeClientSourceIntermediate.accountStoredOnServer!!.gender =
                generateRandomGenderForTesting()
            FakeClientSourceIntermediate.accountStoredOnServer!!.genderTimestamp += (1..100000).random()

            FakeClientSourceIntermediate.accountStoredOnServer!!.firstName =
                generateRandomFirstNameForTesting()
            FakeClientSourceIntermediate.accountStoredOnServer!!.firstNameTimestamp += (1..100000).random()

            val (minAgeRange, maxAgeRange) = generateMinAndMaxMatchableAges(
                userAge,
                fakeStoreErrors
            )

            FakeClientSourceIntermediate.accountStoredOnServer!!.userBio =
                generateRandomUserBioForTesting()
            FakeClientSourceIntermediate.accountStoredOnServer!!.userCity =
                generateRandomUserCityForTesting()
            FakeClientSourceIntermediate.accountStoredOnServer!!.userGenderRange =
                convertGenderRangeToString(generateRandomGenderRange())
            FakeClientSourceIntermediate.accountStoredOnServer!!.minAge = minAgeRange
            FakeClientSourceIntermediate.accountStoredOnServer!!.maxAge = maxAgeRange
            FakeClientSourceIntermediate.accountStoredOnServer!!.maxDistance =
                (GlobalValues.server_imported_values.minimumAllowedDistance..GlobalValues.server_imported_values.maximumAllowedDistance).random()
            FakeClientSourceIntermediate.accountStoredOnServer!!.postLoginTimestamp += (1..100000).random()

            FakeClientSourceIntermediate.accountStoredOnServer!!.categories =
                convertCategoryActivityMessageToString(generateRandomCategoriesForTesting())
            FakeClientSourceIntermediate.accountStoredOnServer!!.categoriesTimestamp += (1..100000).random()

            val userAccountInfo =
                ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()

            assertNotNull(userAccountInfo)

            runSuccessfulLogin(
                false,
                userAccountInfo = userAccountInfo
            )

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_picturesRequireUpdated() =
        runTest(testDispatcher) {
            updatePicturesTesting {
                //Setup some pictures to be updated.
                val newPictureTimestamps = mutableListOf<RequestMessages.PictureMessage>()
                for (pic in FakeClientSourceIntermediate.picturesStoredOnServer) {
                    if ((0..1).random() == 1) { //add a random picture to be updated
                        newPictureTimestamps.add(
                            generateRandomExistingPictureFromServer(
                                pic.indexNumber,
                                pic.timestampPictureLastUpdated + (1L..100000L).random()
                            )
                        )
                    } else { //use previous picture
                        newPictureTimestamps.add(
                            pic
                        )
                    }
                }

                newPictureTimestamps
            }
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_picturesRequireDeleted() =
        runTest(testDispatcher) {
            updatePicturesTesting {
                //Setup some pictures to be updated.
                val newPictureTimestamps = mutableListOf<RequestMessages.PictureMessage>()
                for (pic in FakeClientSourceIntermediate.picturesStoredOnServer) {
                    if (String(pic.fileInBytes.toByteArray()) != "~"
                        && (0..1).random() == 1
                    ) { //Set picture to deleted.
                        newPictureTimestamps.add(
                            generateDeletedPictureFromServer(pic.indexNumber)
                        )
                    } else { //use previous picture
                        newPictureTimestamps.add(
                            pic
                        )
                    }
                }

                newPictureTimestamps
            }
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_maximumNumberPicturesIncreased() =
        runTest(testDispatcher) {
            val originalNumberPictures =
                GlobalValues.server_imported_values.numberPicturesStoredPerAccount

            //Add more pictures when login response is received.
            FakeClientSourceIntermediate.globalValuesToReturn =
                GlobalValues.server_imported_values.toBuilder()
                    .setNumberPicturesStoredPerAccount(
                        originalNumberPictures + (1..20).random()
                    )
                    .build()

            updatePicturesTesting {
                val newPictureTimestamps =
                    FakeClientSourceIntermediate.picturesStoredOnServer.toMutableList()
                for (i in originalNumberPictures until FakeClientSourceIntermediate.globalValuesToReturn.numberPicturesStoredPerAccount) {
                    if ((0..1).random() == 1) { //add a random picture to be updated
                        newPictureTimestamps.add(
                            generateRandomExistingPictureFromServer(
                                i,
                                generateRandomTimestampForTesting()
                            )
                        )
                    } else { //use previous picture
                        newPictureTimestamps.add(
                            generateDeletedPictureFromServer(i)
                        )
                    }
                }

                newPictureTimestamps
            }
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_maximumNumberPicturesDecreased() =
        runTest(testDispatcher) {
            val originalNumberPictures =
                GlobalValues.server_imported_values.numberPicturesStoredPerAccount

            //Remove some pictures when login response is received.
            FakeClientSourceIntermediate.globalValuesToReturn =
                GlobalValues.server_imported_values.toBuilder()
                    .setNumberPicturesStoredPerAccount(
                        (0 until originalNumberPictures).random()
                    )
                    .build()

            updatePicturesTesting {
                val newPictureTimestamps =
                    FakeClientSourceIntermediate.picturesStoredOnServer.toMutableList()
                while (newPictureTimestamps.size > FakeClientSourceIntermediate.globalValuesToReturn.numberPicturesStoredPerAccount) {
                    newPictureTimestamps.removeLast()
                }

                newPictureTimestamps
            }
        }

    @Test
    fun successfulLogin_accountExistsInsideDatabase_unsentSimpleServerCommands() =
        runTest(testDispatcher) {
            insertAccountToDatabaseAndServer()

            //Save messages to 'unsent' database.
            val unsentCommandsList = mutableListOf<ReportMessages.UserMatchOptionsRequest>()
            val numCommands = (1..1000).random()
            for (i in 0 until numCommands) {
                unsentCommandsList.add(
                    ReportMessages.UserMatchOptionsRequest.newBuilder()
                        .setLoginInfo(getLoginInfo(generateRandomOidForTesting()))
                        .setMatchAccountId(generateRandomOidForTesting())
                        .setResponseType(ReportMessages.ResponseType.forNumber((0..4).random()))
                        .setReportReason(ReportMessages.ReportReason.forNumber((0..4).random()))
                        .setOtherInfo(generateRandomString((0..100).random()))
                        .build()
                )

                ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao.insertMessage(
                    UnsentSimpleServerCommandsDataEntity(
                        UnsentMessageCommandType.USER_MATCH_OPTION.ordinal,
                        unsentCommandsList.last().toByteArray()
                    )
                )
            }

            runSuccessfulLogin(false)

            spinUntilConditionMet {
                unsentCommandsList.size == FakeClientSourceIntermediate.userMatchOptionsRequests.size
            }

            assertEquals(
                unsentCommandsList.size,
                FakeClientSourceIntermediate.userMatchOptionsRequests.size
            )

            unsentCommandsList.sortBy {
                it.matchAccountId
            }
            FakeClientSourceIntermediate.userMatchOptionsRequests.sortBy {
                it.matchAccountId
            }

            val allMessages =
                ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao.selectAll()

            //All messages should have been sent.
            assertTrue(allMessages.isEmpty())

            //Make sure sent messages match stored messages.
            for (i in unsentCommandsList.indices) {
                //NOTE: The login info could have changed from above, make sure the most recent token is used.
                assertEquals(
                    LoginFunctions.currentLoginToken,
                    FakeClientSourceIntermediate.userMatchOptionsRequests[i].loginInfo.loggedInToken
                )
                assertEquals(
                    unsentCommandsList[i].matchAccountId,
                    FakeClientSourceIntermediate.userMatchOptionsRequests[i].matchAccountId
                )
                assertEquals(
                    unsentCommandsList[i].responseType,
                    FakeClientSourceIntermediate.userMatchOptionsRequests[i].responseType
                )
                assertEquals(
                    unsentCommandsList[i].reportReason,
                    FakeClientSourceIntermediate.userMatchOptionsRequests[i].reportReason
                )
                assertEquals(
                    unsentCommandsList[i].otherInfo,
                    FakeClientSourceIntermediate.userMatchOptionsRequests[i].otherInfo
                )
            }

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun cancelLoginFunction() = runTest(testDispatcher) {
        insertAccountToDatabaseAndServer()

        runSuccessfulLogin(false)

        LoginFunctions.cleanDatabaseWorkerUnsubscribe(
            subscriberUUID
        )

        (applicationContext as LetsGoApplicationClass).loginFunctions.cancelLoginFunction(
            callingFragmentInstanceId,
            updateLoginFunctionStatus = true,
            abortAttemptIfRunning = true
        )

        assertTrue(LoginFunctions.loginCanceled)
        assertFalse(LoginFunctions.loginFunctionIsRunning)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun restart_automatically() = runTest(testDispatcher) {
        //Make sure no callbacks inside Handler.
        insertAccountToDatabaseAndServer()

        runSuccessfulLogin(false)

        //Make sure the handler is set to run again for the restart.
        waitForUniqueWorkToStartOrTimeoutByName(
            applicationContext,
            LOGIN_FUNCTIONS_UNIQUE_WORKER_NAME
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }
}