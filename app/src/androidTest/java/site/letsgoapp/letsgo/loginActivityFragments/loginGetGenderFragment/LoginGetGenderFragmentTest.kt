package site.letsgoapp.letsgo.loginActivityFragments.loginGetGenderFragment

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
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.StatusOfClientValueEnum
import java.io.IOException


@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetGenderFragmentTest {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

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

    private fun selectBinaryGender(
        radioButtonId: Int,
        gender: String
    ) {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_GENDER
        )

        //Click gender.
        Espresso.onView(ViewMatchers.withId(radioButtonId))
            .perform(ViewActions.click())

        //Click continue.
        Espresso.onView(ViewMatchers.withId(R.id.getGenderContinueButton))
            .perform(ViewActions.click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.loginGetPicturesFragment
        )

        checkGenderMatches(gender)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun select_gender_male() {
        selectBinaryGender(
            R.id.loginGetGenderMaleRadioButton,
            GlobalValues.MALE_GENDER_VALUE
        )
    }

    @Test
    fun select_gender_female() {
        selectBinaryGender(
            R.id.loginGetGenderFemaleRadioButton,
            GlobalValues.FEMALE_GENDER_VALUE
        )
    }

    @Test
    fun hard_set_gender() {
        val (navController, scenario) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_NAME
        )

        val gender = GlobalValues.FEMALE_GENDER_VALUE

        scenario.onActivity {
            val loginViewModel = extractSharedLoginViewModel(
                it,
                applicationContext
            )

            //Set up a 'hard set' gender.
            loginViewModel.newAccountInfo.gender = gender
            loginViewModel.newAccountInfo.genderStatus = StatusOfClientValueEnum.HARD_SET
        }

        continueFromSetName(
            applicationContext,
            navController,
            R.id.loginGetPicturesFragment
        )

        checkGenderMatches(gender)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

}
