package site.letsgoapp.letsgo.loginActivityFragments.loginGetNameFragment

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.StatusOfClientValueEnum
import java.io.IOException
import java.util.*

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetNameFragmentTest {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    inner class TestFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                LoginGetNameFragment::class.java -> {
                    LoginGetNameFragment(false) {
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
    fun name_not_long_enough() {
        //Must have an account inside the database to run getNameFromDatabase().
        runBlocking {
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
                generateRandomValidAccountInfoDataEntity(
                    true,
                    fakeStoreErrors
                )
            )
        }

        launchFragmentInContainer<LoginGetNameFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        val firstName = "A"

        //Enter invalid first name
        Espresso.onView(ViewMatchers.withId(R.id.getNameEditText)).perform(
            ViewActions.replaceText(firstName)
        )

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getNameContinueButton))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.loginGetNameErrorTextView)).check(
            ViewAssertions.matches(
                ViewMatchers.withText(R.string.get_first_name_invalid_name)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun no_invalid_characters_allowed() {
        //Must have an account inside the database to run getNameFromDatabase().
        runBlocking {
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
                generateRandomValidAccountInfoDataEntity(
                    true,
                    fakeStoreErrors
                )
            )
        }

        launchFragmentInContainer<LoginGetNameFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        val firstName = "1 !@\n^\t\r&"

        //Enter invalid first name
        Espresso.onView(ViewMatchers.withId(R.id.getNameEditText)).perform(
            ViewActions.replaceText(firstName)
        )

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getNameContinueButton))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.loginGetNameErrorTextView)).check(
            ViewAssertions.matches(
                ViewMatchers.withText(R.string.get_first_name_invalid_name)
            )
        )

        //An error should be stored
        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun capitalizes_first_letter() {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_NAME
        )

        val firstName = generateRandomFirstNameForTesting()
        var noCapitalizationFirstName = ""

        for(i in firstName.indices) {
            noCapitalizationFirstName += if(i == 0) {
                firstName[i].lowercase()
            } else {
                firstName[i].uppercase()
            }
        }

        //Enter valid first name with mismatched case.
        Espresso.onView(ViewMatchers.withId(R.id.getNameEditText)).perform(
            ViewActions.replaceText(noCapitalizationFirstName)
        )

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getNameContinueButton))
            .perform(ViewActions.click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.loginGetGenderFragment
        )

        checkFirstNameMatches(firstName)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun hard_set_name() {
        val (navController, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_BIRTHDAY
        )

        val firstName = generateRandomFirstNameForTesting()

        scenario.onActivity {
            val loginViewModel = extractSharedLoginViewModel(
                it,
                applicationContext
            )

            //Set up a 'hard set' first name.
            loginViewModel.newAccountInfo.firstName = firstName
            loginViewModel.newAccountInfo.firstNameStatus = StatusOfClientValueEnum.HARD_SET
        }

        continueFromSetBirthday(
            applicationContext,
            navController,
            R.id.loginGetGenderFragment
        )

        checkFirstNameMatches(firstName)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
//    @Test
//    fun successfully_saved_name() {}
}
