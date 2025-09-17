package site.letsgoapp.letsgo.testingUtility

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert
import org.junit.Assert.assertTrue
import requestmessages.RequestMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities.activitiesOrderedByCategory
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities.allActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.loginActivityFragments.splashScreenFragment.SplashScreenFragment
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.testingUtility.fakes.FakeDeviceIdleOrConnectionDownChecker
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStartDeleteFileInterface
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.clearAllFilesHelper
import site.letsgoapp.letsgo.workers.error_handling.ErrorHandlerWorker
import java.util.*

private const val MAX_TIME_TO_SLEEP_IN_MS = 30000L

fun setupCategoriesAndActivitiesForTesting(
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher
) {
    runBlocking {
        //This must be done for two reasons.
        // 1) The categories and activities list each require at least one element or errors can occur.
        // 2) The dispatcher and fake store errors must be passed in so the lists use the correct objects.
        CategoriesAndActivities.setupCategoriesAndActivities(
            mutableListOf(
                RequestMessages.ServerActivityOrCategoryMessage.newBuilder()
                    .setIndex(0)
                    .setOrderNumber(0.0)
                    .setDisplayName("Unknown")
                    .setIconDisplayName("")
                    .setMinAge(121)
                    .setColor("#000000")
                    .build()
            ),
            mutableListOf(
                RequestMessages.ServerActivityOrCategoryMessage.newBuilder()
                    .setIndex(0)
                    .setDisplayName("Unknown")
                    .setIconDisplayName("Unknown")
                    .setMinAge(13)
                    .setCategoryIndex(0)
                    .setIconIndex(0)
                    .build()
            ),
            dispatcher,
            fakeStoreErrors
        )
    }
}

//NOTE: CategoriesAndActivities must be set up separately.
fun cleanupPreviouslySetValues(
    applicationContext: Context,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
/*
    //cancel the chat stream worker
    //This is important because the chat stream worker can continue to the
    // next test if it is not canceled and attempt to log in without a
    // valid account stored causing odd things like the database being cleared
    // during the middle of the next test.
    //The chat stream worker is started when the application closes. Which
    // means there is a good chance it is still running
    cancelChatStreamWorker()

    val start = SystemClock.uptimeMillis()

    waitForWorkerToEndByTag(
        applicationContext,
        ChatStreamWorker.CHAT_STREAM_WORKER_TAG
    )

    Log.i("waiting_for_worker", "time_to_shutdown_worker: ${SystemClock.uptimeMillis() - start}ms")
*/

    GlobalValues.setDefaultGlobals()
    FakeClientSourceIntermediate.resetStaticToDefaults()
    cleanServiceLocatorVariables(applicationContext)
    clearSharedPreferences(applicationContext)

    //Make sure tutorial has already been viewed.
    GlobalValues.applicationContext.getSharedPreferences(
        applicationContext.getString(R.string.shared_preferences_lets_go_key),
        Context.MODE_PRIVATE
    ).edit().putBoolean(
        applicationContext.getString(R.string.shared_preferences_tutorial_shown_key),
        false
    ).apply()

    clearAllFilesHelper(
        applicationContext,
        fakeStartDeleteFileInterface
    )

    LoginFunctions.cancelAllLoginFunctionsWork(applicationContext)
    ChatStreamObject.cancelAllChatStreamObjectsWork(applicationContext)

    LoginFunctions.TIME_BETWEEN_LOGIN_RETRIES_MS = 5_000
    LoginFunctions.currentAccountOID = ""
    LoginFunctions.currentLoginToken = GlobalValues.INVALID_LOGIN_TOKEN
    LoginFunctions.loginTokenExpirationTime = -1L
    LoginFunctions.nextTimeLoadBalancingAllowed = -1L

    GlobalValues.blockedAccounts.clear()
    GlobalValues.allIcons = emptyList()
    GlobalValues.loginToChatRoomId = ""

    GlobalValues.setupForTesting = true
}

fun setupBasicTestingInjections(
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    //Injections to live StoreErrorsInterface
    ServiceLocator.globalErrorStore = fakeStoreErrors

    //Injections to fake StoreErrorsInterface
    FakeClientSourceIntermediate.fakeStoreErrors = fakeStoreErrors

    //Injections to live StartDeleteFileInterface
    ServiceLocator.testingDeleteFileInterface = fakeStartDeleteFileInterface

    //Injections to live Dispatchers
    ServiceLocator.globalIODispatcher = dispatcher

    //Injection to live DeviceIdleOrConnectionDownCheckerInterface
    ServiceLocator.deviceIdleOrConnectionDown = FakeDeviceIdleOrConnectionDownChecker()
}

fun setupForActivityTests(
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface = FakeStartDeleteFileInterface()
): Context {
    Intents.init()

    val applicationContext: Context = ApplicationProvider.getApplicationContext()

    cleanupPreviouslySetValues(
        applicationContext,
        fakeStartDeleteFileInterface
    )

    setupCategoriesAndActivitiesForTesting(
        fakeStoreErrors,
        dispatcher
    )

    //This should be called before any of the setup functions below in order
    // to initialize the dispatcher, error store etc...
    setupBasicTestingInjections(
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    loginRepositorySetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher
    )

    loginFunctionsSetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    selectPicturesRepositorySetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    selectCategoriesRepositorySetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher
    )

    applicationRepositorySetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    chatStreamObjectSetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    loginSupportFunctionsSetup(
        applicationContext,
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    chatStreamWorkerRepositorySetup(
        dispatcher,
        fakeStoreErrors,
        fakeStartDeleteFileInterface
    )

    notificationInfoRepositorySetup(
        dispatcher,
        fakeStoreErrors,
        fakeStartDeleteFileInterface
    )

    cleanDatabaseWorkerRepositorySetup(
        dispatcher,
        fakeStoreErrors,
        fakeStartDeleteFileInterface
    )

    provideFindMatchesObjectLambdaSetup(
        fakeStoreErrors,
        dispatcher,
        fakeStartDeleteFileInterface
    )

    return applicationContext
}

fun waitForWorkerToEndByTag(
    applicationContext: Context,
    workerTag: String
) {
    var workerExists = true

    while (workerExists) {

        val workers =
            WorkManager.getInstance(applicationContext)
                .getWorkInfosByTag(workerTag)
                .get()

        if (workers.isEmpty()) {
            workerExists = false
        } else {
            for (worker in workers) {
                //remember that ChatStreamWorker.instanceRunning was checked above
                if (worker.state != WorkInfo.State.BLOCKED
                    && worker.state != WorkInfo.State.ENQUEUED
                    && worker.state != WorkInfo.State.RUNNING
                ) {
                    workerExists = false
                    break
                }
            }
        }
    }
}

fun waitForUniqueWorkToEndOrTimeoutByName(
    applicationContext: Context,
    workerTag: String
) {
    var workerExists = true

    val startTime = SystemClock.uptimeMillis()

    while (workerExists) {

        val workers =
            WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork(workerTag)
                .get()

        if (workers.isEmpty()) {
            workerExists = false
        } else if ((SystemClock.uptimeMillis() - startTime) > (10L * 1000L)) {
            //If expression passes 10 seconds, throw an error.
            assertTrue(false)
        } else {
            for (worker in workers) {
                //remember that ChatStreamWorker.instanceRunning was checked above
                if (worker.state != WorkInfo.State.BLOCKED
                    && worker.state != WorkInfo.State.ENQUEUED
                    && worker.state != WorkInfo.State.RUNNING
                ) {
                    workerExists = false
                    break
                }
            }
        }
    }
}

fun waitForUniqueWorkToStartOrTimeoutByName(
    applicationContext: Context,
    workerTag: String
) {
    var workerExists = false

    val startTime = SystemClock.uptimeMillis()

    while (!workerExists) {

        val workers =
            WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork(workerTag)
                .get()

        if ((SystemClock.uptimeMillis() - startTime) > (10L * 1000L)) {
            //If expression passes 10 seconds, throw an error.
            assertTrue(false)
        } else {
            for (worker in workers) {
                //remember that ChatStreamWorker.instanceRunning was checked above
                if (worker.state == WorkInfo.State.BLOCKED
                    || worker.state == WorkInfo.State.ENQUEUED
                    || worker.state == WorkInfo.State.RUNNING
                ) {
                    workerExists = true
                    break
                }
            }
        }
    }
}

fun tearDownForTesting(
    applicationContext: Context,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface = FakeStartDeleteFileInterface()
) {
    Intents.release()

    ServiceLocator.loginSupportFunctions?.cancelWorkers()

    WorkManager.getInstance(applicationContext)
        .cancelAllWorkByTag(ErrorHandlerWorker.ERROR_HANDLER_WORKER_TAG)

    FakeClientSourceIntermediate.resetStaticToDefaults()

    cleanupPreviouslySetValues(
        applicationContext,
        fakeStartDeleteFileInterface
    )
    cleanServiceLocatorVariables(applicationContext)
}

enum class StopPointWhenRunLoginTest {
    SELECT_METHOD,
    SET_PHONE_NUMBER,
    VERIFY_PHONE_NUMBER,
    SET_EMAIL,
    SHOW_RULES,
    SHOW_OPT_IN,
    SET_BIRTHDAY,
    SET_NAME,
    SET_GENDER,
    SET_PICTURE,
    SET_CATEGORIES,

    NO_STOP_POINT
}

//Starts activity and runs a standard login flow from selectMethod fragment
// to the AppActivity.
//stopPoint will stop at various parts of the flow for testing on specific
// fragments.
fun runLoginTest(
    applicationContext: Context,
    stopPoint: StopPointWhenRunLoginTest = StopPointWhenRunLoginTest.NO_STOP_POINT
): Pair<NavController?, ActivityScenario<LoginActivity>> {

    val scenario = ActivityScenario.launch<LoginActivity>(
        Intent(ApplicationProvider.getApplicationContext(), LoginActivity::class.java).apply {
            putExtra("ignoreTaskRootCheck", true)
        }
    )

    var navController: NavController? = null

    scenario.onActivity {
        navController = it.findNavController(R.id.nav_host_fragment)
    }

    Assert.assertNotEquals(null, navController)

    if (stopPoint == StopPointWhenRunLoginTest.SELECT_METHOD) {
        return Pair(navController, scenario)
    }

    continueFromSelectMethod(
        applicationContext,
        navController
    )

    if (stopPoint == StopPointWhenRunLoginTest.SET_PHONE_NUMBER) {
        return Pair(navController, scenario)
    }

    scenario.onActivity {
        it.loginViewModel.loginSMSVerificationCoolDown = 1000
    }

    val phoneNumber = continueFromGetPhoneNumber(
        applicationContext,
        navController
    )

    checkIfToastExists(
        scenario,
        R.string.sms_verification_requires_authorization_verification_code_sent
    )

    if (stopPoint == StopPointWhenRunLoginTest.VERIFY_PHONE_NUMBER) {
        return Pair(navController, scenario)
    }

    continueFromVerifyFragments(
        applicationContext,
        navController
    )

    checkPhoneNumberMatches(phoneNumber)

    if (stopPoint == StopPointWhenRunLoginTest.SET_EMAIL) {
        return Pair(navController, scenario)
    }

    val emailAddress = continueFromSetEmail(
        applicationContext,
        navController
    )

    checkEmailInfoMatches(emailAddress)

    if (stopPoint == StopPointWhenRunLoginTest.SHOW_RULES) {
        return Pair(navController, scenario)
    }

    continueFromCulturalGuidelines(
        applicationContext,
        navController
    )

    if (stopPoint == StopPointWhenRunLoginTest.SHOW_OPT_IN) {
        return Pair(navController, scenario)
    }

    continueFromOptIn(
        applicationContext,
        navController
    )

    if (stopPoint == StopPointWhenRunLoginTest.SET_BIRTHDAY) {
        return Pair(navController, scenario)
    }

    val birthdayCalendar = continueFromSetBirthday(
        applicationContext,
        navController
    )

    checkBirthdayInfoMatches(birthdayCalendar)

    if (stopPoint == StopPointWhenRunLoginTest.SET_NAME) {
        return Pair(navController, scenario)
    }

    val name = continueFromSetName(
        applicationContext,
        navController
    )

    checkFirstNameMatches(name)

    if (stopPoint == StopPointWhenRunLoginTest.SET_GENDER) {
        return Pair(navController, scenario)
    }

    val gender = continueFromSetGender(
        applicationContext,
        navController
    )

    checkGenderMatches(gender)

    if (stopPoint == StopPointWhenRunLoginTest.SET_PICTURE) {
        return Pair(navController, scenario)
    }

    continueFromSetPictures(
        applicationContext,
        navController
    )

    checkFirstPictureExists()

    if (stopPoint == StopPointWhenRunLoginTest.SET_CATEGORIES) {
        return Pair(navController, scenario)
    }

    continueFromSetCategories()

    checkCategoriesNotEmpty()

    return Pair(navController, scenario)
}

fun checkCategoriesNotEmpty() {
    runBlocking {
        val accountInfo =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()
        Assert.assertNotEquals("", accountInfo?.categories)
        assertTrue(
            (accountInfo?.categoriesTimestamp ?: 0) > 0
        )
    }
}

fun checkFirstPictureExists() {
    runBlocking {
        val accountPictures =
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao.getAllAccountPictures()
        Assert.assertEquals(
            accountPictures.size,
            GlobalValues.server_imported_values.numberPicturesStoredPerAccount
        )

        Assert.assertNotEquals(accountPictures.first().picturePath, "")
        Assert.assertNotEquals(accountPictures.first().pictureSize, 0)

        for (i in 1 until accountPictures.size) {
            Assert.assertEquals(accountPictures[i].picturePath, "")
            Assert.assertEquals(accountPictures[i].pictureSize, 0)
        }
    }
}

fun checkGenderMatches(gender: String) {
    runBlocking {
        val accountInfo =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()
        Assert.assertEquals(gender, accountInfo?.gender)
        assertTrue(
            (accountInfo?.genderTimestamp ?: 0) > 0
        )
    }
}

fun checkFirstNameMatches(name: String) {
    runBlocking {
        val accountInfo =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()
        Assert.assertEquals(name, accountInfo?.firstName)
        assertTrue(
            (accountInfo?.firstNameTimestamp ?: 0) > 0
        )
    }
}

fun checkBirthdayInfoMatches(birthdayCalendar: Calendar, allZero: Boolean = false) {
    runBlocking {
        val accountInfo =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()

        val (passedYear, passedMonth, passedDayOfMonth) =
            if (allZero) {

                assertTrue(
                    (accountInfo?.birthdayTimestamp ?: 0) == 0L
                )

                Triple(0, 0, 0)
            } else {

                assertTrue(
                    (accountInfo?.birthdayTimestamp ?: 0) > 0
                )

                Triple(
                    birthdayCalendar.get(Calendar.YEAR),
                    birthdayCalendar.get(Calendar.MONTH) + 1,
                    birthdayCalendar.get(Calendar.DAY_OF_MONTH)
                )
            }

        Assert.assertEquals(passedYear, accountInfo?.birthYear)
        Assert.assertEquals(passedMonth, accountInfo?.birthMonth)
        Assert.assertEquals(passedDayOfMonth, accountInfo?.birthDayOfMonth)
    }
}

fun checkEmailInfoMatches(emailAddress: String) {
    runBlocking {
        val accountInfo =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getAccountInfoForErrors()
        Assert.assertEquals(emailAddress, accountInfo?.emailAddress)
        assertTrue(
            (accountInfo?.emailTimestamp ?: 0) > 0
        )
        Assert.assertEquals(true, accountInfo?.requiresEmailAddressVerification)
    }
}

fun checkPhoneNumberMatches(phoneNumber: String) {
    runBlocking {
        //Other elements will be checked inside login function testing.
        val extractedPhoneNumber =
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.getPhoneNumber()
        Assert.assertEquals(phoneNumber, extractedPhoneNumber)
    }
}

fun checkAppActivityWasReached() {
    sleepUntilNavigationCompleteOrMaxTimeLambda {
        var continueLoop = true

        //Sleep until the AppActivity has reached the RESUMED state.
        getInstrumentation().runOnMainSync {
            val resumedActivities =
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            if (resumedActivities.iterator().hasNext()) {
                val currentActivity = resumedActivities.iterator().next()

                if (currentActivity.packageName + "." + currentActivity.localClassName
                    == AppActivity::class.qualifiedName
                ) {
                    continueLoop = false
                }
            }
        }

        continueLoop
    }
}

fun continueFromSetCategories() {
    //Click on a category.
    //Espresso.onView(matchFirstCategoryInRecyclerView()).perform(ViewActions.click())

    assert(
        activitiesOrderedByCategory.size > 1
    )
    assert(
        activitiesOrderedByCategory[1].activityIndexValues.isNotEmpty()
    )
    assert(
        allActivities.size > activitiesOrderedByCategory[1].activityIndexValues[0]
    )

    //Category 0 is 'Unknown', must use index 1.
    val categoryName = activitiesOrderedByCategory[1].category.name
    val activityIconName =
        allActivities[activitiesOrderedByCategory[1].activityIndexValues[0]].activity.iconDisplayName
    val activityIndex =
        allActivities[activitiesOrderedByCategory[1].activityIndexValues[0]].activity.index

    selectActivityByName(
        categoryName,
        activityIconName,
        activityIndex
    )

    //Click continue button
    Espresso.onView(withId(R.id.selectCategoriesContinueButton))
        .perform(click())

    checkAppActivityWasReached()

    //Guarantee AppActivity was reached
    Espresso.onView(withId(R.id.activityAppParentLayout)).check(
        ViewAssertions.matches(
            isDisplayed()
        )
    )
}

//requires Intents to be setup.
fun continueFromSetPictures(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.selectCategoriesFragment
) {
    val expectedIntent: Matcher<Intent> = allOf(
        IntentMatchers.hasAction(Intent.ACTION_CHOOSER)
    )
    val activityResult = createGalleryPickActivityResultStub(applicationContext)
    Intents.intending(expectedIntent).respondWith(activityResult)

    //Click on a picture image view (any of them is fine so finding the id should be OK).
    Espresso.onView(
        withIndex(
            withId(R.id.pictureSlotImageView),
            (0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount).random()
        )
    ).perform(
        click()
    )
    Intents.intended(expectedIntent)

    //Click continue button
    Espresso.onView(withId(R.id.getPictureContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )
}

fun continueFromSetGender(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetPicturesFragment
): String {

    var gender = ""

    gender = when ((0..1).random()) {
        0 -> {
            Espresso.onView(withId(R.id.loginGetGenderMaleRadioButton))
                .perform(click())
            GlobalValues.MALE_GENDER_VALUE
        }
        else -> {
            Espresso.onView(withId(R.id.loginGetGenderFemaleRadioButton))
                .perform(click())
            GlobalValues.FEMALE_GENDER_VALUE
        }
    }

    //Click continue button
    Espresso.onView(withId(R.id.getGenderContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )

    return gender
}

fun continueFromSetName(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetGenderFragment
): String {
    val name = generateRandomFirstNameForTesting()

    //Enter a valid first name
    Espresso.onView(withId(R.id.getNameEditText))
        .perform(ViewActions.replaceText(name))

    //Click continue button
    Espresso.onView(withId(R.id.getNameContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )

    return name
}

fun continueFromSetBirthday(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetNameFragment
): Calendar {

    val birthdayCalendar = generateRandomBirthdayForTesting()

    //Enter a valid birthday
    Espresso.onView(withId(R.id.getBirthdayEditText))
        .perform(ViewActions.replaceText(generateRandomBirthdayStringForTesting(birthdayCalendar)))

    //Click continue button
    Espresso.onView(withId(R.id.getBirthdayContinueButton))
        .perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )

    return birthdayCalendar
}

fun continueFromCulturalGuidelines(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginPromotionsOptInFragment
) {
    //Click continue button
    Espresso.onView(withId(R.id.showRulesContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )
}

fun continueFromOptIn(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetBirthdayFragment
) {
    //Click continue button
    Espresso.onView(withId(R.id.promotionsOptInContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )
}

fun continueFromSetEmail(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginShowRulesFragment
): String {

    val email = generateRandomEmailForTesting()

    //Enter a valid email
    Espresso.onView(withId(R.id.getEmailEditText))
        .perform(ViewActions.replaceText(email))

    //Click continue button
    Espresso.onView(withId(R.id.getEmailContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )

    return email
}

fun continueFromVerifyFragments(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetEmailFragment
) {
    //Enter the valid verification code
    Espresso.onView(withId(R.id.verifyNumEditText))
        .perform(ViewActions.replaceText(FakeClientSourceIntermediate.most_recent_verification_code))

    //Click verify phone numbers continue button
    Espresso.onView(withId(R.id.verifyPhoneContinueButton))
        .perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )
}

fun continueFromGetPhoneNumber(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.verifyPhoneNumbersFragment
): String {
    val phoneNumber = generateRandomPhoneNumberForTesting()
    //Enter a valid phone number
    Espresso.onView(withId(R.id.phoneNumberEditText))
        .perform(ViewActions.replaceText(phoneNumber))

    //Click phone login continue button
    Espresso.onView(withId(R.id.getPhoneContinueButton)).perform(click())

    guaranteeFragmentReached(
        applicationContext,
        navController,
        fragmentId
    )

    return phoneNumber
}

fun continueFromSelectMethod(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int = R.id.loginGetPhoneNumberFragment
) {

    //Delay for splash screen
    Thread.sleep(SplashScreenFragment.MINIMUM_TIME_TO_DISPLAY_SCREEN_MS + 50)

    //Click phone login button
    Espresso.onView(withId(R.id.loginWithPhoneButton)).perform(click())

    //Guarantee phone login fragment was reached
    Assert.assertEquals(
        applicationContext.resources.getResourceName(fragmentId),
        navController?.currentDestination?.displayName
    )
}

fun guaranteeFragmentReached(
    applicationContext: Context,
    navController: NavController?,
    fragmentId: Int
) {
    sleepUntilNavigationCompleteOrMaxTime(
        applicationContext,
        navController,
        fragmentId
    )

    //Guarantee fragment was reached
    Assert.assertEquals(
        applicationContext.resources.getResourceName(fragmentId),
        navController?.currentDestination?.displayName
    )
}

private fun matchFirstCategoryInRecyclerView(): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        var found = false
        override fun describeTo(description: Description) {
            description.appendText("Recycler View child")
        }

        override fun matchesSafely(view: View): Boolean {
            //Only find the first matching constraint layout (which one it finds doesn't
            // actually matter).
            if (found || view.parent !is View) {
                return false
            }
            found =
                R.id.cardContainerLinearLayout == (view.parent as View).id
                        && view.id == R.id.categoryTitleConstraintLayout
            return found
        }
    }
}

private fun matchFirstActivityInsideCategory(): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        var found = false
        override fun describeTo(description: Description) {
            description.appendText("Recycler View child")
        }

        override fun matchesSafely(view: View): Boolean {
            //Only find the first matching constraint layout (which one it finds doesn't
            // actually matter).
            if (found) {
                return false
            }
            found = R.id.categoryListItemTableLayout == view.id
                    && view.visibility == View.VISIBLE
                    && view.height > 0
            return found
        }
    }
}

fun withIndex(matcher: Matcher<View>, index: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        var currentIndex = 0
        override fun describeTo(description: Description) {
            description.appendText("with index: ")
            description.appendValue(index)
            matcher.describeTo(description)
        }

        override fun matchesSafely(view: View): Boolean {
            return matcher.matches(view) && currentIndex++ == index
        }
    }
}

fun withIndexFromParent(parentMatcher: Matcher<View>, index: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        var currentIndex = 0
        override fun describeTo(description: Description) {
            description.appendText("with index from parent: ")
            description.appendValue(index)
            parentMatcher.describeTo(description)
        }

        override fun matchesSafely(view: View): Boolean {
            val parent = view.parent
            if (parent != null && parent is View && parentMatcher.matches(view.parent)) {
                return currentIndex++ == index
            }
            return false
        }
    }
}

fun withViewCount(viewMatcher: Matcher<View>, expectedCount: Int): Matcher<View?> {
    return object : TypeSafeMatcher<View?>() {
        private var actualCount = -1
        override fun describeTo(description: Description) {
            when {
                actualCount >= 0 -> description.also {
                    it.appendText("Expected items count: $expectedCount, but got: $actualCount")
                }
            }
        }

        override fun matchesSafely(root: View?): Boolean {
            actualCount = TreeIterables.breadthFirstViewTraversal(root).count {
                viewMatcher.matches(it)
            }
            return expectedCount == actualCount
        }
    }
}

fun createGalleryPickActivityResultStub(applicationContext: Context): Instrumentation.ActivityResult {
    val imageUri = Uri.parse(
        ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                applicationContext.resources.getResourcePackageName(R.drawable.lets_go_logo) + "/" +
                applicationContext.resources.getResourceTypeName(R.drawable.lets_go_logo) + "/" +
                applicationContext.resources.getResourceEntryName(R.drawable.lets_go_logo)
    )

    val resultIntent = Intent()
    resultIntent.data = imageUri
    return Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent)
}

private fun sleepUntilNavigationCompleteOrMaxTimeLambda(
    lambda: () -> Boolean
) {
    val initialTime = SystemClock.uptimeMillis()

    while (lambda()) {
        if ((SystemClock.uptimeMillis() - initialTime) > MAX_TIME_TO_SLEEP_IN_MS) {
            break
        }
        Thread.sleep(1)
    }
}

fun sleepUntilNavigationCompleteOrMaxTime(
    applicationContext: Context,
    navController: NavController?,
    resourceId: Int
) {
    sleepUntilNavigationCompleteOrMaxTimeLambda {
        applicationContext.resources.getResourceName(resourceId) != navController?.currentDestination?.displayName
    }
}