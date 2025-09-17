package site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment

import android.content.Context
import android.os.SystemClock
import android.widget.Button
import android.widget.DatePicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.loginActivityFragments.loginGetPhoneNumberFragment.LoginGetPhoneNumberFragmentTest
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionsTest
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.EventWrapperWithKeyString
import site.letsgoapp.letsgo.utilities.SmsVerificationDataDataHolder
import java.io.IOException
import java.util.*

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPhoneNumbersFragmentTest {

    /** Some of the cases are checked for inside [LoginFunctionsTest]
     * and [LoginGetPhoneNumberFragmentTest]. **/

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    var permissionRules: GrantPermissionRule = grantAllAppPermissionRules()

    inner class TestFragmentFactory(private val initialTimerTime: Long = -1) : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                VerifyPhoneNumbersFragment::class.java -> {
                    VerifyPhoneNumbersFragment(initialTimerTime, false) {
                        ServiceLocator.provideSharedLoginViewModelFactory(applicationContext)
                    }
                }
                else -> super.instantiate(classLoader, className)
            }
        }
    }

    @Before
    fun setUp() {
        fakeStoreErrors = FakeStoreErrors(testDispatcher)

        applicationContext = setupForActivityTests(
            fakeStoreErrors,
            testDispatcher
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        tearDownForTesting(applicationContext)
    }

    @Test
    fun invalid_verification_code_incorrectLength() = runTest(testDispatcher) {
        launchFragmentInContainer<VerifyPhoneNumbersFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        val invalidVerificationCode =
            generateRandomVerificationCodeForTesting(GlobalValues.verificationCodeNumberOfDigits - 1)

        onView(withId(R.id.verifyNumEditText)).perform(
            ViewActions.replaceText(invalidVerificationCode)
        )

        onView(withId(R.id.verifyPhoneContinueButton)).perform(click())

        onView(withId(R.id.verifyPhoneMessagesTextView)).check(
            matches(
                withText(R.string.sms_verification_invalid_verification_code_entered)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun invalid_verification_code_doesNotMatchStored() = runTest(testDispatcher) {
        val (_, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER
        )

        val passedVerificationCode = generateRandomVerificationCodeForTesting()
        FakeClientSourceIntermediate.most_recent_verification_code =
            generateRandomVerificationCodeForTesting()

        //Enter a valid phone number, however it does not match 'server' verification code.
        onView(withId(R.id.verifyNumEditText)).perform(
            ViewActions.replaceText(passedVerificationCode)
        )

        var liveData: LiveData<EventWrapperWithKeyString<SmsVerificationDataDataHolder>>? = null
        scenario.onActivity {
            liveData = it.loginViewModel.smsVerificationStatus
        }

        waitForLivedata(
            scenario,
            liveData!!,
            {
                //Click verify continue button.
                onView(withId(R.id.verifyPhoneContinueButton)).perform(click())
            },
            {
                onView(withId(R.id.verifyPhoneMessagesTextView)).check(
                    matches(
                        withText(R.string.sms_verification_codes_do_not_match)
                    )
                )

            }
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun verification_code_expired() = runTest(testDispatcher) {
        val (_, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER
        )

        val passedVerificationCode = generateRandomVerificationCodeForTesting()
        FakeClientSourceIntermediate.most_recent_verification_code = passedVerificationCode
        FakeClientSourceIntermediate.verification_code_expired = true

        //Enter a valid phone number, however it does not match 'server' verification code.
        onView(withId(R.id.verifyNumEditText)).perform(
            ViewActions.replaceText(passedVerificationCode)
        )

        var liveData: LiveData<EventWrapperWithKeyString<SmsVerificationDataDataHolder>>? = null
        scenario.onActivity {
            liveData = it.loginViewModel.smsVerificationStatus
        }

        waitForLivedata(
            scenario,
            liveData!!,
            {
                //Click verify continue button.
                onView(withId(R.id.verifyPhoneContinueButton)).perform(click())
            },
            {
                checkIfToastExists(
                    scenario,
                    R.string.sms_verification_new_verification_code
                )

                checkIfToastExists(
                    scenario,
                    R.string.sms_verification_verification_code_sent
                )

                //The loginFunction() should generate and store a new verification code.
                assertNotEquals(
                    passedVerificationCode,
                    FakeClientSourceIntermediate.most_recent_verification_code
                )
            }
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    //There is currently no way to automate sending an sms.
    // @Test
    // fun properly_receive_sms() = runTest(testDispatcher) {}

    @Test
    fun request_new_verification_code() = runTest(testDispatcher) {

        val fragmentScenario = launchFragmentInContainer<VerifyPhoneNumbersFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory(500)
        )

        //Make sure button is not enabled.
        onView(withId(R.id.sendNewCodeButton)).check(matches(not(isEnabled())))

        var button: Button? = null

        fragmentScenario.onFragment {
            button = it.view?.findViewById(R.id.sendNewCodeButton)
        }

        assertNotEquals(null, button)

        //Wait for button to become enabled
        while (!button!!.isEnabled) {
            Thread.sleep(1)
        }

        val originalVerificationCode = generateRandomVerificationCodeForTesting()
        FakeClientSourceIntermediate.most_recent_verification_code = originalVerificationCode
        FakeClientSourceIntermediate.smsVerificationRan = false
        FakeClientSourceIntermediate.accountStoredOnServer = null

        //Click send new sms button.
        onView(withId(R.id.sendNewCodeButton)).perform(click())

        //Make sure button is not enabled.
        onView(withId(R.id.sendNewCodeButton)).check(matches(not(isEnabled())))

        //Wait for message to be sent to loginFunction().
        Thread.sleep(300)

        //Make sure sms changed.
        assertNotEquals(
            originalVerificationCode,
            FakeClientSourceIntermediate.most_recent_verification_code
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private fun setupRequiresBirthday() {
        //Enter the valid verification code.
        onView(withId(R.id.verifyNumEditText))
            .perform(ViewActions.replaceText(FakeClientSourceIntermediate.most_recent_verification_code))

        //Click verify phone numbers continue button.
        onView(withId(R.id.verifyPhoneContinueButton))
            .perform(click())

        //New Device dialog is displayed.
        onView(withText(R.string.set_phone_dialog_new_device_detected_body)).check(
            matches(
                isDisplayed()
            )
        )
    }

    private fun setupRequiresBirthdayFullActivity(): Pair<NavController?, ActivityScenario<LoginActivity>> {
        val (navController, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER
        )

        //Force the request birthday dialog to pop up.
        scenario.onActivity {
            it.loginViewModel.loginBirthDayNotRequired = false
        }

        setupRequiresBirthday()

        return Pair(navController, scenario)
    }

    private fun setupRequiresBirthdayOnlyFragment() {
        GlobalValues.serverTimestampStartTimeMilliseconds =
            System.currentTimeMillis()
        GlobalValues.clientElapsedRealTimeStartTimeMilliseconds =
            SystemClock.elapsedRealtime()

        val fragmentScenario = launchFragmentInContainer<VerifyPhoneNumbersFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory(500)
        )

        //Force the request birthday dialog to pop up.
        fragmentScenario.onFragment {
            it.sharedLoginViewModel.loginBirthDayNotRequired = false
        }

        setupRequiresBirthday()
    }

    @Test
    fun requiresBirthday_newDeviceDialog_createNewAccount_confirmationDialog_cancel() =
        runTest(testDispatcher) {
            setupRequiresBirthdayOnlyFragment()

            //Click "Create New"
            onView(withId(android.R.id.button1)).perform(click())

            //Confirmation dialog is displayed.
            onView(withText(R.string.set_phone_dialog_confirmation_body)).check(matches(isDisplayed()))

            //Click Cancel
            onView(withId(android.R.id.button2)).perform(click())

            //New Device dialog is displayed.
            onView(withText(R.string.set_phone_dialog_new_device_detected_body)).check(
                matches(
                    isDisplayed()
                )
            )

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun requiresBirthday_newDeviceDialog_createNewAccount_confirmationDialog_ok() =
        runTest(testDispatcher) {
            val (navController, _) = setupRequiresBirthdayFullActivity()

            //Click "Create New"
            onView(withId(android.R.id.button1)).perform(click())

            //Confirmation dialog is displayed.
            onView(withText(R.string.set_phone_dialog_confirmation_body)).check(matches(isDisplayed()))

            //Click Ok
            onView(withId(android.R.id.button1)).perform(click())

            guaranteeFragmentReached(
                applicationContext,
                navController,
                R.id.loginGetEmailFragment
            )

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun requiresBirthday_newDeviceDialog_existingAccount_birthdayDialog_cancel() =
        runTest(testDispatcher) {
            setupRequiresBirthdayOnlyFragment()

            //Click "Use Existing".
            onView(withId(android.R.id.button2)).perform(click())

            //Enter birthday dialog is displayed.
            onView(withText(R.string.set_phone_dialog_enter_birthday_title)).check(
                matches(
                    isDisplayed()
                )
            )

            //Click Cancel.
            onView(withId(android.R.id.button2)).perform(click())

            //New Device dialog is displayed.
            onView(withText(R.string.set_phone_dialog_new_device_detected_body)).check(
                matches(
                    isDisplayed()
                )
            )

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun requiresBirthday_newDeviceDialog_existingAccount_birthdayDialog_ok_invalidBirthday() =
        runTest(testDispatcher) {
            setupRequiresBirthdayOnlyFragment()

            //Click "Use Existing".
            onView(withId(android.R.id.button2)).perform(click())

            //Enter birthday dialog is displayed.
            onView(withText(R.string.set_phone_dialog_enter_birthday_title)).check(
                matches(
                    isDisplayed()
                )
            )

            //Click on edit text to pop out DatePicker.
            onView(withId(R.id.birthdayDialogSelectorEditText)).perform(click())

            //Set date picker (this must be done to set the variable 'dateSet' inside DatePickerEditText).
            onView(withClassName(Matchers.equalTo(DatePicker::class.qualifiedName)))
                .perform(PickerActions.setDate(1986, 10, 23))

            //Click Ok on DatePicker.
            onView(withId(android.R.id.button1)).perform(click())

            //Enter invalid birthday
            onView(withId(R.id.birthdayDialogSelectorEditText)).perform(
                ViewActions.replaceText("00/10/1986")
            )

            //Click Ok on birthday dialog.
            onView(withId(android.R.id.button1)).perform(click())

            //Enter birthday dialog is displayed with updated title.
            onView(withText(R.string.set_phone_dialog_enter_birthday_invalid_title)).check(
                matches(
                    isDisplayed()
                )
            )

            assertNotEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun requiresBirthday_newDeviceDialog_existingAccount_birthdayDialog_ok_validBirthday() =
        runTest(testDispatcher) {
            val (_, scenario) = setupRequiresBirthdayFullActivity()

            //Set up a 'server' account to log into.
            FakeClientSourceIntermediate.setupSmsForNewAccountCreation = false
            FakeClientSourceIntermediate.accountStoredOnServer =
                generateRandomValidAccountInfoDataEntity(
                    false,
                    fakeStoreErrors
                )

            scenario.onActivity {
                FakeClientSourceIntermediate.accountStoredOnServer?.phoneNumber =
                    it.loginViewModel.loginPhoneNumber
            }

            //Click "Use Existing".
            onView(withId(android.R.id.button2)).perform(click())

            //Enter birthday dialog is displayed.
            onView(withText(R.string.set_phone_dialog_enter_birthday_title)).check(
                matches(
                    isDisplayed()
                )
            )

            //Click on edit text to pop out DatePicker.
            onView(withId(R.id.birthdayDialogSelectorEditText)).perform(click())

            //Set date picker (this must be done to set the variable 'dateSet' inside DatePickerEditText).
            onView(withClassName(Matchers.equalTo(DatePicker::class.qualifiedName)))
                .perform(
                    PickerActions.setDate(
                        FakeClientSourceIntermediate.accountStoredOnServer!!.birthYear,
                        FakeClientSourceIntermediate.accountStoredOnServer!!.birthMonth - 1,
                        FakeClientSourceIntermediate.accountStoredOnServer!!.birthDayOfMonth
                    )
                )

            //Click Ok on DatePicker.
            onView(withId(android.R.id.button1)).perform(click())

            //Click Ok on birthday dialog.
            onView(withId(android.R.id.button1)).perform(click())

            checkAppActivityWasReached()

            assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
        }

    @Test
    fun verificationSuccessful_continueToAppActivity() = runTest(testDispatcher) {
        val (_, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER
        )

        scenario.onActivity {
            //Set up a 'server' account to log into.
            FakeClientSourceIntermediate.setupSmsForNewAccountCreation = false
            FakeClientSourceIntermediate.accountStoredOnServer =
                generateRandomValidAccountInfoDataEntity(
                    false,
                    fakeStoreErrors
                )
            FakeClientSourceIntermediate.accountStoredOnServer?.phoneNumber =
                it.loginViewModel.loginPhoneNumber
        }

        //Enter the valid verification code.
        onView(withId(R.id.verifyNumEditText))
            .perform(ViewActions.replaceText(FakeClientSourceIntermediate.most_recent_verification_code))

        //Click verify phone numbers continue button.
        onView(withId(R.id.verifyPhoneContinueButton))
            .perform(click())

        checkAppActivityWasReached()

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
    //@Test
    //fun verificationSuccessful_continueToLoginActivity() {}
}