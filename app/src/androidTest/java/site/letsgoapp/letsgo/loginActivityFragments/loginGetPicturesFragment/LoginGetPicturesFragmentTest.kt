package site.letsgoapp.letsgo.loginActivityFragments.loginGetPicturesFragment

import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.times
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
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
import java.io.IOException

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginGetPicturesFragmentTest {

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

    @Test
    fun no_pictures_set() {

        //NOTE: If only the fragment is used through a TestFragmentFactory then
        // this test will not work with older API versions (version 21). It
        // may have something to do with that selectPicturesFragment can not
        // be set without an activity present.

        val (_, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_PICTURE
        )

        Espresso.onView(ViewMatchers.withId(R.id.getPictureContinueButton))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.loginGetPicturesErrorTextView)).check(
            ViewAssertions.matches(
                ViewMatchers.withText(R.string.get_pictures_choose_picture)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun all_three_pictures_set() {
        val (navController, _) = runLoginTest(
            applicationContext,
            StopPointWhenRunLoginTest.SET_PICTURE
        )

        val expectedIntent: Matcher<Intent> = CoreMatchers.allOf(
            IntentMatchers.hasAction(Intent.ACTION_CHOOSER)
        )
        val activityResult = createGalleryPickActivityResultStub(applicationContext)
        Intents.intending(expectedIntent).respondWith(activityResult)

        for(i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
            //Click on a picture image view at the index
            Espresso.onView(
                withIndex(
                    ViewMatchers.withId(R.id.pictureSlotImageView),
                    i
                )
            ).perform(
                ViewActions.click()
            )
        }

        Intents.intended(expectedIntent, times(GlobalValues.server_imported_values.numberPicturesStoredPerAccount))

        Espresso.onView(ViewMatchers.withId(R.id.getPictureContinueButton))
            .perform(ViewActions.click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.selectCategoriesFragment
        )

        //Guarantee all pictures inside database.
        runBlocking {
            val accountPictures =
                ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao.getAllAccountPictures()
            assertEquals(
                accountPictures.size,
                GlobalValues.server_imported_values.numberPicturesStoredPerAccount
            )

            for (i in 1 until accountPictures.size) {
                assertNotEquals(accountPictures[i].picturePath, "")
                assertNotEquals(accountPictures[i].pictureSize, 0)
            }
        }

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    /** This case is tested at [LoginFlow.login_flow_happy_path] **/
    //@Test
    //fun single_picture_set() {}

}