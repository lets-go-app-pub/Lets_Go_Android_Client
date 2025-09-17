package site.letsgoapp.letsgo.loginActivityFragments.loginGetPhoneNumberFragment

import access_status.AccessStatusEnum
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment.VerifyPhoneNumbersFragmentTest
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionsTest
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import java.io.IOException

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetPhoneNumberFragmentTest {

    /** Some of the cases are checked for inside [LoginFunctionsTest]
     * and [VerifyPhoneNumbersFragmentTest]. **/

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    var permissionRules: GrantPermissionRule = grantAllAppPermissionRules()

    inner class TestFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                LoginGetPhoneNumberFragment::class.java -> {
                    LoginGetPhoneNumberFragment(false) {
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
    fun invalid_phone_number_passed() = runTest(testDispatcher) {
        launchFragmentInContainer<LoginGetPhoneNumberFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        //set invalid phone number
        onView(withId(R.id.phoneNumberEditText)).perform(
            replaceText("(234)567-789")
        )

        onView(withId(R.id.getPhoneContinueButton)).perform(click())

        onView(withId(R.id.loginPhoneNumberErrorTextView)).check(
            matches(
                withText(R.string.phone_login_invalid_phone_number_entered)
            )
        )
    }

    @Test
    fun userAccount_doesNotHaveAllInfo_existsOnServerNotInDatabase() = runTest(testDispatcher) {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_PHONE_NUMBER
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount(AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO)

        val phoneNumber = FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber

        //Enter a valid phone number
        onView(withId(R.id.phoneNumberEditText))
            .perform(replaceText(phoneNumber))

        //Click phone login continue button
        onView(withId(R.id.getPhoneContinueButton)).perform(click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.loginGetEmailFragment
        )

        runBlocking {
            //Login function testing will check user info more thoroughly.
            val extractedPhoneNumber =
                ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getPhoneNumber()
            assertEquals(phoneNumber, extractedPhoneNumber)
        }
        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun userAccount_doesNotHaveAllInfo_existsInDatabase() = runTest(testDispatcher) {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_PHONE_NUMBER
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount(AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO)

        runBlocking {
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.clearTable()
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.insertAccount(
                FakeClientSourceIntermediate.accountStoredOnServer!!
            )
        }

        val phoneNumber = FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber

        //Enter a valid phone number
        onView(withId(R.id.phoneNumberEditText))
            .perform(replaceText(phoneNumber))

        //Click phone login continue button
        onView(withId(R.id.getPhoneContinueButton)).perform(click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.loginGetEmailFragment
        )

        runBlocking {
            //Login function testing will check user info more thoroughly.
            val extractedPhoneNumber =
                ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getPhoneNumber()
            assertEquals(phoneNumber, extractedPhoneNumber)
        }
        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun userAccount_hasAllInfo_existsOnServerNotInDatabase() = runTest(testDispatcher) {
        runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_PHONE_NUMBER
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val phoneNumber = FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber
        //Enter a valid phone number
        onView(withId(R.id.phoneNumberEditText))
            .perform(replaceText(phoneNumber))

        //Click phone login continue button
        onView(withId(R.id.getPhoneContinueButton)).perform(click())

        checkAppActivityWasReached()

        runBlocking {
            //Login function testing will check user info more thoroughly.
            val extractedPhoneNumber =
                ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getPhoneNumber()
            assertEquals(phoneNumber, extractedPhoneNumber)
        }

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun userAccount_hasAllInfo_existsInDatabase() = runTest(testDispatcher) {
        generateAccountAndNavigateToAppActivity(applicationContext)

        runBlocking {
            //Login function testing will check user info more thoroughly.
            val extractedPhoneNumber =
                ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getPhoneNumber()
            assertEquals(FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber, extractedPhoneNumber)
        }

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
//    @Test
//    fun userAccount_doesNotExistInsideDatabaseOrServer() {}

}