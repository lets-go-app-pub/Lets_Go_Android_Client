package site.letsgoapp.letsgo.loginActivityFragments.loginGetBirthdayFragment

import android.content.Context
import android.widget.DatePicker
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.LoginFlow
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.StatusOfClientValueEnum
import site.letsgoapp.letsgo.utilities.datePickerEditText.BirthdayPickerDialogWrapper
import java.io.IOException
import java.util.*

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetBirthdayFragmentTest {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    inner class TestFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            return when (loadFragmentClass(classLoader, className)) {
                LoginGetBirthdayFragment::class.java -> {
                    LoginGetBirthdayFragment(false) {
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
    fun birthday_hard_set() {
        val (navController, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SHOW_OPT_IN
        )

        val birthdayCalendar = generateRandomBirthdayForTesting()

        scenario.onActivity {
            val loginViewModel = extractSharedLoginViewModel(
                it,
                applicationContext
            )

            //Set up a 'hard set' birthday.
            loginViewModel.newAccountInfo.birthYear = birthdayCalendar.get(Calendar.YEAR)
            loginViewModel.newAccountInfo.birthMonth = birthdayCalendar.get(Calendar.MONTH) + 1
            loginViewModel.newAccountInfo.birthDayOfMonth =
                birthdayCalendar.get(Calendar.DAY_OF_MONTH)
            loginViewModel.newAccountInfo.birthDayStatus = StatusOfClientValueEnum.HARD_SET
        }

        continueFromOptIn(
            applicationContext,
            navController,
            R.id.loginGetNameFragment
        )

        checkBirthdayInfoMatches(birthdayCalendar)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun invalid_birthday_entered() {
        //Must have an account inside the database to run getBirthdayFromDatabase().
        runBlocking {
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
                generateRandomValidAccountInfoDataEntity(
                    true,
                    fakeStoreErrors
                )
            )
        }

        launchFragmentInContainer<LoginGetBirthdayFragment>(
            themeResId = R.style.AppTheme,
            factory = TestFragmentFactory()
        )

        val birthdayCalendar = generateRandomBirthdayForTesting()

        //Click DatePicker edit text.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayEditText)).perform(ViewActions.click())

        //Set date picker (this must be done to set the variable 'dateSet' inside DatePickerEditText).
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo(DatePicker::class.qualifiedName)))
            .perform(
                PickerActions.setDate(
                    birthdayCalendar.get(Calendar.YEAR),
                    birthdayCalendar.get(Calendar.MONTH) + 1,
                    birthdayCalendar.get(Calendar.DAY_OF_MONTH)
                )
            )

        //Click Ok on DatePicker.
        Espresso.onView(ViewMatchers.withId(android.R.id.button1)).perform(ViewActions.click())

        //Enter invalid birthday
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayEditText)).perform(
            ViewActions.replaceText("00/23/1986")
        )

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayContinueButton))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.loginGetBirthdayErrorTextView)).check(
            ViewAssertions.matches(
                ViewMatchers.withText(R.string.get_birthday_invalid_birthday)
            )
        )

        //An error should be stored
        assertNotEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun user_too_young() {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_BIRTHDAY
        )

        val currentCalendar = Calendar.getInstance()
        val birthdayCalendar = generateRandomBirthdayForTesting()

        var tooYoungYear =
            currentCalendar.get(Calendar.YEAR) - GlobalValues.server_imported_values.lowestAllowedAge + 1

        if (
            currentCalendar.get(Calendar.MONTH) < birthdayCalendar.get(Calendar.MONTH)
            || (currentCalendar.get(Calendar.MONTH) == birthdayCalendar.get(Calendar.MONTH)
                    && currentCalendar.get(Calendar.DAY_OF_MONTH) <= birthdayCalendar.get(Calendar.DAY_OF_MONTH))
        ) {
            tooYoungYear--
        }

        //Click DatePicker edit text.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayEditText)).perform(ViewActions.click())

        //Set date picker.
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo(DatePicker::class.qualifiedName)))
            .perform(
                PickerActions.setDate(
                    tooYoungYear,
                    birthdayCalendar.get(Calendar.MONTH) + 1,
                    birthdayCalendar.get(Calendar.DAY_OF_MONTH)
                )
            )

        //Click Ok on DatePicker.
        Espresso.onView(ViewMatchers.withId(android.R.id.button1)).perform(ViewActions.click())

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayContinueButton))
            .perform(ViewActions.click())

        //Underage dialog warning displayed.
        Espresso.onView(
            ViewMatchers.withText(
                applicationContext.resources.getString(
                    R.string.underage_dialog_body,
                    GlobalValues.server_imported_values.lowestAllowedAge
                )
            )
        ).check(
            ViewAssertions.matches(
                ViewMatchers.isDisplayed()
            )
        )

        //Click Ok on Dialog.
        Espresso.onView(ViewMatchers.withId(android.R.id.button1)).perform(ViewActions.click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.loginSelectMethodFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun user_too_old() {
        val (_, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_BIRTHDAY
        )

        val currentCalendar = Calendar.getInstance()
        val birthdayCalendar = generateRandomBirthdayForTesting()

        var tooOldYear =
            currentCalendar.get(Calendar.YEAR) - GlobalValues.server_imported_values.highestAllowedAge - 1

        if (
            currentCalendar.get(Calendar.MONTH) < birthdayCalendar.get(Calendar.MONTH)
            || (currentCalendar.get(Calendar.MONTH) == birthdayCalendar.get(Calendar.MONTH)
                    && currentCalendar.get(Calendar.DAY_OF_MONTH) <= birthdayCalendar.get(Calendar.DAY_OF_MONTH))
        ) {
            tooOldYear--
        }

        //Click DatePicker edit text.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayEditText)).perform(ViewActions.click())

        //Set date picker.
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo(DatePicker::class.qualifiedName)))
            .perform(
                PickerActions.setDate(
                    tooOldYear,
                    birthdayCalendar.get(Calendar.MONTH) + 1,
                    birthdayCalendar.get(Calendar.DAY_OF_MONTH)
                )
            )

        //Click Accept on DatePicker.
        Espresso.onView(ViewMatchers.withId(android.R.id.button1)).perform(ViewActions.click())

        //Make sure it is impossible to select any older than maximum age (this should be set to the oldest valid birthday, NOT the above date).
        scenario.onActivity {
            val birthdayEditText: EditText = it.findViewById(R.id.getBirthdayEditText)

            val month = birthdayEditText.text.toString().substring(0, 2).toInt()
            val day = birthdayEditText.text.toString().substring(3, 5).toInt()
            val year = birthdayEditText.text.toString().substring(6).toInt()

            val oldestValidBirthday = Calendar.getInstance()
            oldestValidBirthday.add(
                Calendar.YEAR,
                -1 * (GlobalValues.server_imported_values.highestAllowedAge + 1)
            )
            oldestValidBirthday.add(
                Calendar.DAY_OF_MONTH,
                1
            )

            assertEquals(
                year,
                oldestValidBirthday.get(Calendar.YEAR)
            )
            assertEquals(month - 1, oldestValidBirthday.get(Calendar.MONTH))
            assertEquals(day, oldestValidBirthday.get(Calendar.DAY_OF_MONTH))
        }

        //Force the string to be set to a birthday that is too old
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayEditText))
            .perform(
                ViewActions.replaceText(
                    String.format(
                        "%02d/%02d/%04d",
                        birthdayCalendar.get(Calendar.MONTH) + 1,
                        birthdayCalendar.get(Calendar.DAY_OF_MONTH),
                        tooOldYear
                    )
                )
            )

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getBirthdayContinueButton))
            .perform(ViewActions.click())

        checkBirthdayInfoMatches(Calendar.getInstance(), true)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
//    @Test
//    fun successfully_saved_birthday() {}
}