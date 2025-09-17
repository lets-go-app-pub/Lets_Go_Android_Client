package site.letsgoapp.letsgo.loginActivityFragments.loginSelectCategoriesFragment


import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment.SelectCategoriesFragment
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.FRAGMENT_CALLED_FROM_KEY
import site.letsgoapp.letsgo.utilities.SelectCategoriesFragmentCalledFrom
import java.io.IOException
import java.util.*

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginSelectCategoriesFragmentTest {
    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    inner class TestFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                SelectCategoriesFragment::class.java -> {
                    SelectCategoriesFragment( false) {
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
    fun no_activity_selected() {
        //Must have an account inside the database to run getCategoriesAndAgeFromDatabase().
        runBlocking {
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
                generateRandomValidAccountInfoDataEntity(
                    true,
                    fakeStoreErrors
                )
            )
        }

        val argumentsBundle = Bundle()
        argumentsBundle.putInt(
            FRAGMENT_CALLED_FROM_KEY,
            SelectCategoriesFragmentCalledFrom.LOGIN_FRAGMENT.ordinal
        )

        launchFragmentInContainer<SelectCategoriesFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory(),
            fragmentArgs = argumentsBundle
        )

        //NOTE: No categories are loaded here, however that is just fine for the test.

        onView(withId(R.id.selectCategoriesContinueButton))
            .perform(click())

        onView(withId(R.id.errorMessageSelectCategoriesTextView)).check(
            matches(
                withText(R.string.select_categories_login_activity_select_one_activity)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
    //@Test
    //fun single_activity_set() {}
}