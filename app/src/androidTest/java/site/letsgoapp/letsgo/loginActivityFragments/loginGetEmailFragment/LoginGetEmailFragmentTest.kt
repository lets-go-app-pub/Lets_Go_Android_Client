package site.letsgoapp.letsgo.loginActivityFragments.loginGetEmailFragment

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.StatusOfClientValueEnum
import java.io.IOException

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetEmailFragmentTest {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    inner class TestFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                LoginGetEmailFragment::class.java -> {
                    LoginGetEmailFragment(false) {
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
    fun email_hard_set() = runTest(testDispatcher) {
        val newEmailAddress = generateRandomEmailForTesting()

        val (navController, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER
        )

        scenario.onActivity {
            val loginViewModel = extractSharedLoginViewModel(
                it,
                applicationContext
            )

            //Set up a 'hard set' email.
            loginViewModel.newAccountInfo.emailAddress = newEmailAddress
            loginViewModel.newAccountInfo.emailAddressStatus = StatusOfClientValueEnum.HARD_SET
        }

        continueFromVerifyFragments(
            applicationContext,
            navController,
            R.id.loginShowRulesFragment
        )

        checkEmailInfoMatches(newEmailAddress)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun invalid_email_entered() = runTest(testDispatcher) {
        launchFragmentInContainer<LoginGetEmailFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        onView(withId(R.id.getEmailEditText)).perform(
            replaceText("InvalidEmailAddress")
        )

        onView(withId(R.id.getEmailContinueButton)).perform(click())

        onView(withId(R.id.loginGetEmailErrorTextView)).check(
            matches(
                withText(R.string.get_email_invalid_email)
            )
        )
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
//    @Test
//    fun successfully_saved_email() {}

}