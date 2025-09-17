package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.navigation.NavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import categorytimeframe.CategoryTimeFrame.CategoryActivityMessage
import categorytimeframe.CategoryTimeFrame.CategoryTimeFrameMessage
import com.google.common.primitives.Ints.min
import com.yuyakaido.android.cardstackview.CardStackView
import findmatches.FindMatches
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import report_enums.ReportMessages
import report_enums.ReportMessages.ReportReason
import report_enums.ReportMessages.ResponseType
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.*
import java.io.IOException
import java.util.*


@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MatchScreenFragmentTest {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    var permissionRules: GrantPermissionRule = grantAllAppPermissionRules()

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

    data class OtherUserAndFindMatchesResponse(
        val otherUserOid: String,
        val findMatchesResponse: FindMatches.FindMatchesResponse
    )

    //Pass in a non null OtherUsersDataEntity OR a valid userAccountOid to request
    // an OtherUsersDataEntity.
    private suspend fun checkUserInfoProperlyDisplayed(
        activity: AppActivity,
        passedOtherUserDataEntity: OtherUsersDataEntity? = null,
        userAccountOid: String = ""
    ) {
        var layoutImageView: ImageView? =
            try {
                //Spin until time limit is reached or picture is loaded
                activity.findViewById(R.id.matchLayoutPictureImageView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is
                // called inside onCreate(), it will occasionally return a null ptr exception.
                null
            }

        val startTime = SystemClock.uptimeMillis()
        while (
            SystemClock.uptimeMillis() - startTime < 5000
            && layoutImageView?.drawable == null
        ) {
            yield()
            delay(1)
            try {
                if (layoutImageView == null)
                    layoutImageView = activity.findViewById(R.id.matchLayoutPictureImageView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is called inside
                // onCreate(), it will occasionally return a null ptr exception.
            }
        }

        //image loaded to image view
        assertNotNull(layoutImageView?.drawable)

        //This must be requested here in order to get the pictures string.
        // It can't be set before because the user has not actually been downloaded
        // yet, it was sitting on the 'server' as a match.
        val otherUserDataEntity =
            if (passedOtherUserDataEntity == null && userAccountOid.isValidMongoDBOID()) {
                ServiceLocator.otherUsersDatabase?.otherUsersDatabaseDao!!.getSingleOtherUser(
                    userAccountOid
                )!!
            } else {
                passedOtherUserDataEntity!!
            }

        assertNotNull(otherUserDataEntity)

        val userPicturesListSize = convertPicturesStringToList(
            otherUserDataEntity.pictures
        ).size

        val peripheralsVisible = userPicturesListSize > 1

        val checkIfProperlyDisplayed = { viewResourceId: Int, visibility: Visibility ->

            //This check does not seem to work on API 21 (not sure about
            // other versions). It returns the error
            // Looped for 3875 iterations over 60 SECONDS. The following Idle Conditions failed MAIN_LOOPER_HAS_IDLED
            // in onView()
            if(Build.VERSION.SDK_INT > 21) {
                onView(
                    withIndex(
                        withId(R.id.userMatchOptionsCardStackView),
                        0
                    )
                ).check(
                    matches(
                        hasDescendant(
                            allOf(
                                withId(viewResourceId),
                                withEffectiveVisibility(
                                    if (peripheralsVisible)
                                        Visibility.VISIBLE
                                    else
                                        visibility
                                )
                            )
                        )
                    )
                )
            }
        }

        //check pictures visuals
        checkIfProperlyDisplayed(
            R.id.matchLayoutLeftChevronImageView,
            Visibility.INVISIBLE
        )

        checkIfProperlyDisplayed(
            R.id.matchLayoutRightChevronImageView,
            Visibility.INVISIBLE
        )

        checkIfProperlyDisplayed(
            R.id.matchLayoutPictureIndexLinearLayout,
            Visibility.GONE
        )

        //This check does not seem to work on API 21 (see above for more info).
        if (peripheralsVisible && Build.VERSION.SDK_INT > 21) {
            onView(
                withIndex(
                    withId(R.id.userMatchOptionsCardStackView),
                    0
                )
            ).check(
                matches(
                    hasDescendant(
                        allOf(
                            withId(R.id.matchLayoutPictureIndexLinearLayout),
                            withViewCount(
                                instanceOf(View::class.java),
                                userPicturesListSize + 1
                            )
                        )
                    )
                )
            )
        }

        //check name and age
        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchLayoutNameAgeTextView),
                        withText(
                            applicationContext.getString(
                                R.string.match_screen_fragment_name_age_text,
                                otherUserDataEntity.name,
                                otherUserDataEntity.age
                            )
                        )
                    )
                )
            )
        )

        //check city and location
        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchLayoutLocationTextView),
                        withText(
                            if (otherUserDataEntity.cityName.isNotEmpty()) {
                                applicationContext.getString(
                                    R.string.match_screen_fragment_location_text,
                                    otherUserDataEntity.cityName,
                                    String.format("%.1f", otherUserDataEntity.distance)
                                )
                            } else {
                                applicationContext.getString(
                                    R.string.match_screen_fragment_no_city_location_text,
                                    String.format("%.1f", otherUserDataEntity.distance)
                                )
                            }
                        )
                    )
                )
            )
        )

        //check bio
        if (otherUserDataEntity.bio.isNotEmpty()) {
            onView(
                withIndex(
                    withId(R.id.userMatchOptionsCardStackView),
                    0
                )
            ).check(
                matches(
                    hasDescendant(
                        allOf(
                            withId(R.id.matchLayoutBioTextView),
                            withEffectiveVisibility(Visibility.VISIBLE),
                            withText(otherUserDataEntity.bio)
                        )
                    )
                )
            )
        } else {
            onView(
                withIndex(
                    withId(R.id.userMatchOptionsCardStackView),
                    0
                )
            ).check(
                matches(
                    hasDescendant(
                        allOf(
                            withId(R.id.matchLayoutBioTextView),
                            not(
                                isDisplayed()
                            )
                        )
                    )
                )
            )
        }

        val otherUserActivities =
            convertStringToCategoryActivityMessageAndTrimTimes(otherUserDataEntity.activities).second

        var numVisibleActivities = 0

        //Exclude activities this user is too young for.
        for (userActivity in otherUserActivities) {
            if (
                (FakeClientSourceIntermediate.accountStoredOnServer?.age ?: 0) >=
                CategoriesAndActivities.allActivities[userActivity.activityIndex].activity.minAge
            ) {
                numVisibleActivities++
            }
        }

        //Make sure the correct number of activities is displayed.
        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchLayoutActivitiesListLinearLayout),
                        withViewCount(
                            withId(R.id.matchListItemWholeCardLinearLayout),
                            numVisibleActivities
                        )
                    )
                )
            )
        )

//        //NOTE: Times are important but it us difficult to test in this format, need
//        // to view them visually.
    }

    private suspend fun setupSingleMatchAndCheckIfDisplayed(): Pair<NavController?, AppActivity> {

        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        val (_, otherUserDataEntity) = buildSingleRandomMatch(
            applicationContext,
            fakeStoreErrors,
        )

        val (navController, activity) = launchToMatchScreen(applicationContext)

        //Check if the user info is properly displayed.
        checkUserInfoProperlyDisplayed(
            activity,
            otherUserDataEntity
        )

        return Pair(navController, activity)
    }

    private suspend fun waitForMatchToBeSentToServer(
        expectedSize: Int = 1
    ) {
        //Wait for value to be 'sent' to the server.
        val startTime = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - startTime < 5000
            && FakeClientSourceIntermediate.userMatchOptionsRequests.size < expectedSize
        ) {
            yield()
            delay(10)
        }
    }

    private suspend fun waitForLoadingToComplete(
        activity: AppActivity
    ) {
        var progressBar: ProgressBar? =
            try {
                //Spin until time limit is reached or picture is loaded
                activity.findViewById(R.id.userMatchOptionsBottomLayerProgressBar)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is
                // called inside onCreate(), it will occasionally return a null ptr exception.
                null
            }

        val startTime = SystemClock.uptimeMillis()
        while (
            SystemClock.uptimeMillis() - startTime < 10000
            && progressBar?.visibility != View.GONE
        ) {
            yield()
            delay(1)
            try {
                if (progressBar == null)
                    progressBar = activity.findViewById(R.id.userMatchOptionsBottomLayerProgressBar)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is called inside
                // onCreate(), it will occasionally return a null ptr exception.
            }
        }
    }

    private suspend fun waitForFirstMatchToLoad(
        activity: AppActivity
    ) {
        var cardStackView: CardStackView? =
            try {
                //Spin until time limit is reached or picture is loaded
                activity.findViewById(R.id.userMatchOptionsCardStackView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is
                // called inside onCreate(), it will occasionally return a null ptr exception.
                null
            }

        val startTime = SystemClock.uptimeMillis()
        var lastTime = startTime + 100
        while (
            SystemClock.uptimeMillis() - startTime < 10000
            && (cardStackView?.childCount == null
                    || cardStackView.childCount == 0)
        ) {
            yield()
            delay(1)
            try {
                if (cardStackView == null)
                    cardStackView = activity.findViewById(R.id.userMatchOptionsCardStackView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is called inside
                // onCreate(), it will occasionally return a null ptr exception.
            }
            if (SystemClock.uptimeMillis() > lastTime) {
                lastTime = SystemClock.uptimeMillis() + 100
            }
        }
    }

    private suspend fun waitForTextToBeDisplayed(
        activity: AppActivity
    ): String {
        var textView: TextView? =
            try {
                //Spin until time limit is reached or picture is loaded
                activity.findViewById(R.id.userMatchOptionsBottomLayerTextView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is
                // called inside onCreate(), it will occasionally return a null ptr exception.
                null
            }

        val startTime = SystemClock.uptimeMillis()
        while (
            SystemClock.uptimeMillis() - startTime < 5000
            && textView?.text.isNullOrBlank()
        ) {
            yield()
            delay(1)
            try {
                if (textView == null)
                    textView = activity.findViewById(R.id.userMatchOptionsBottomLayerTextView)
            } catch (_: NullPointerException) {
                //NOTE: Because findViewById() can be called before setContentView() is called inside
                // onCreate(), it will occasionally return a null ptr exception.
            }
        }

        assertNotNull(textView)

        return textView!!.text.toString()
    }

    private suspend fun swipeYesOrNo(
        thumbsUpOrDownResourceId: Int,
        responseType: ResponseType
    ) {
        setupSingleMatchAndCheckIfDisplayed()

        //swipe yes or no
        onView(
            withId(
                thumbsUpOrDownResourceId
            )
        ).perform(
            click()
        )

        waitForMatchToBeSentToServer()

        //Make sure match was removed from the database. (specifics will be checked inside FindMatchesObject)
        val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(0, allMatches.size)

        //Make sure match was sent to the server. (specifics will be checked inside FindMatchesObject)
        assertEquals(1, FakeClientSourceIntermediate.userMatchOptionsRequests.size)
        assertEquals(
            FakeClientSourceIntermediate.userMatchOptionsRequests[0].responseType,
            responseType
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private suspend fun swipeBlockAndReport(
        selectionDialogStringId: Int,
        reportReason: ReportReason,
        otherString: String = ""
    ) {
        setupSingleMatchAndCheckIfDisplayed()

        //Swipe block and report.
        onView(withId(R.id.matchLayoutBlockAndReportTextView)).perform(
            click()
        )

        //Select dialog option.
        onView(
            allOf(
                withId(android.R.id.text1),
                withText(selectionDialogStringId)
            )
        ).perform(click())

        if (reportReason == ReportReason.REPORT_REASON_OTHER) {
            onView(withId(R.id.dialogFeedbackActivitySuggestionOtherEditText)).perform(
                replaceText(otherString)
            )

            onView(
                allOf(
                    withId(android.R.id.button1),
                    withText(android.R.string.ok)
                )
            ).perform(click())
        }

        waitForMatchToBeSentToServer()

        //Make sure match was removed from the database. (specifics will be checked inside FindMatchesObject)
        val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(0, allMatches.size)

        //Make sure match was sent to the server. (specifics will be checked inside FindMatchesObject)
        assertEquals(1, FakeClientSourceIntermediate.userMatchOptionsRequests.size)
        assertEquals(
            FakeClientSourceIntermediate.userMatchOptionsRequests[0].responseType,
            ResponseType.USER_MATCH_OPTION_REPORT
        )
        assertEquals(
            FakeClientSourceIntermediate.userMatchOptionsRequests[0].reportReason,
            reportReason
        )

        if (reportReason == ReportReason.REPORT_REASON_OTHER) {
            assertEquals(
                FakeClientSourceIntermediate.userMatchOptionsRequests[0].otherInfo,
                otherString
            )
        }

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private suspend fun navigateAndChangeSomething(
        resourceIdToClickOn: Int,
        resourceIdOfDestination: Int,
        modifySomething: suspend () -> Unit
    ) {
        val (navController, activity) = setupSingleMatchAndCheckIfDisplayed()

        //Navigate to profile screen.
        onView(
            withId(R.id.profileScreenFragment)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.profileScreenFragment
        )

        onView(
            withId(resourceIdToClickOn)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            resourceIdOfDestination
        )

        assertNotNull(FakeClientSourceIntermediate.accountStoredOnServer)

        modifySomething()

        //Insert new match to be returned after the current one is cleared.
        val (matchOtherUserDataEntity, matchResponse) = generateRandomMatchOnServer(
            applicationContext
        )
        FakeClientSourceIntermediate.matchesToReturn.add(matchResponse)

        //Navigate back
        onView(
            allOf(
                instanceOf(AppCompatImageButton::class.java),
                withParent(withId(R.id.activityAppTopToolbar))
            )
        )
            .perform(click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.profileScreenFragment
        )

        onView(
            withId(R.id.matchScreenFragment)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.matchScreenFragment
        )

        waitForFirstMatchToLoad(activity)

        //Make sure new match is properly displayed.
        checkUserInfoProperlyDisplayed(
            activity,
            userAccountOid = matchOtherUserDataEntity.accountOID
        )

        //Make sure match was removed from the database. (specifics will be checked inside FindMatchesObject)
        val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(1, allMatches.size)
        assertEquals(matchOtherUserDataEntity.accountOID, allMatches[0].account_oid)

        //Make sure match was NOT sent to the server. (specifics will be checked inside FindMatchesObject)
        assertEquals(0, FakeClientSourceIntermediate.userMatchOptionsRequests.size)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private suspend fun navigateAndChangeNothing(
        resourceIdToClickOn: Int,
        resourceIdOfDestination: Int
    ) {
        val (navController, activity) = setupSingleMatchAndCheckIfDisplayed()

        //Extract the previous match from the database for later
        var allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(1, allMatches.size)
        val otherUserDataEntity =
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao.getSingleOtherUser(allMatches[0].account_oid)
        assertNotNull(otherUserDataEntity)

        //Navigate to profile screen.
        onView(
            withId(R.id.profileScreenFragment)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.profileScreenFragment
        )

        onView(
            withId(resourceIdToClickOn)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            resourceIdOfDestination
        )

        //Pause for a moment here.
        waitForTime(100)

        //Navigate back
        onView(
            allOf(
                instanceOf(AppCompatImageButton::class.java),
                withParent(withId(R.id.activityAppTopToolbar))
            )
        )
            .perform(click())

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.profileScreenFragment
        )

        onView(
            withId(R.id.matchScreenFragment)
        ).perform(
            click()
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.matchScreenFragment
        )

        waitForFirstMatchToLoad(activity)

        //Make sure previous match is still displayed.
        checkUserInfoProperlyDisplayed(
            activity,
            otherUserDataEntity!!
        )

        //Make sure match was removed from the database. (specifics will be checked inside FindMatchesObject)
        allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(1, allMatches.size)
        assertEquals(otherUserDataEntity.accountOID, allMatches[0].account_oid)

        //Make sure match was NOT sent to the server. (specifics will be checked inside FindMatchesObject)
        assertEquals(0, FakeClientSourceIntermediate.userMatchOptionsRequests.size)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private suspend fun addSpecificTimeframe(
        setupTimeframes: (
            currentUserCategories: MutableList<CategoryActivityMessage>,
            otherUserCategories: MutableList<CategoryActivityMessage>,
            randomActivityIndex: Int
        ) -> Unit
    ) {
        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        val (_, otherUserDataEntity) = buildSingleRandomMatch(
            applicationContext,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val currentUserAge = FakeClientSourceIntermediate.accountStoredOnServer!!.age
        val otherUserAge = otherUserDataEntity.age

        var validAgeOfActivity = false
        var randomActivityIndex = 0

        while (!validAgeOfActivity) {

            randomActivityIndex =
                (1 until CategoriesAndActivities.allActivities.size).random()

            val categoryIndex =
                CategoriesAndActivities.allActivities[randomActivityIndex].activity.categoryIndex
            val activityMinAge =
                CategoriesAndActivities.allActivities[randomActivityIndex].activity.minAge
            val categoryMinAge =
                CategoriesAndActivities.activitiesOrderedByCategory[categoryIndex].category.minAge

            validAgeOfActivity =
                activityMinAge <= currentUserAge
                        && categoryMinAge <= currentUserAge
                        && activityMinAge <= otherUserAge
                        && categoryMinAge <= otherUserAge
        }

        val currentUserCategories = mutableListOf<CategoryActivityMessage>()
        val otherUserCategories = mutableListOf<CategoryActivityMessage>()

        setupTimeframes(
            currentUserCategories,
            otherUserCategories,
            randomActivityIndex
        )

        //Will be used inside launchToMatchScreen()
        FakeClientSourceIntermediate.accountStoredOnServer?.categories =
            convertCategoryActivityMessageToString(
                currentUserCategories
            )

        otherUserDataEntity.activities = convertCategoryActivityMessageToString(
            otherUserCategories
        )

        /**Logging here in case test fails can reproduce the strings**/
        Log.i(
            "activities_string",
            "current: ${FakeClientSourceIntermediate.accountStoredOnServer?.categories} other: ${otherUserDataEntity.activities}"
        )

        //Upsert the other user account.
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ).upsertSingleOtherUser(otherUserDataEntity)

        val (_, activity) = launchToMatchScreen(applicationContext)

        //Check if the user info is properly displayed.
        checkUserInfoProperlyDisplayed(
            activity,
            otherUserDataEntity
        )
    }

    @Test
    fun swipe_yes() = runTest(testDispatcher) {
        swipeYesOrNo(
            R.id.matchLayoutThumbsUpImageView,
            ResponseType.USER_MATCH_OPTION_YES
        )
    }

    @Test
    fun swipe_no() = runTest(testDispatcher) {
        swipeYesOrNo(
            R.id.matchLayoutThumbsDownImageView,
            ResponseType.USER_MATCH_OPTION_NO
        )
    }

    @Test
    fun swipe_blockAndReport_language() = runTest(testDispatcher) {
        swipeBlockAndReport(
            R.string.block_and_report_language_text,
            ReportReason.REPORT_REASON_LANGUAGE
        )
    }

    @Test
    fun swipe_blockAndReport_inappropriatePicture() = runTest(testDispatcher) {
        swipeBlockAndReport(
            R.string.block_and_report_inappropriate_picture_text,
            ReportReason.REPORT_REASON_INAPPROPRIATE_PICTURE
        )
    }

    @Test
    fun swipe_blockAndReport_advertising() = runTest(testDispatcher) {
        swipeBlockAndReport(
            R.string.block_and_report_advertising_text,
            ReportReason.REPORT_REASON_ADVERTISING
        )
    }

    @Test
    fun swipe_blockAndReport_other() = runTest(testDispatcher) {
        swipeBlockAndReport(
            R.string.block_and_report_other_text,
            ReportReason.REPORT_REASON_OTHER,
            generateRandomString(GlobalValues.server_imported_values.maximumNumberAllowedBytesUserFeedback)
        )
    }

    @Test
    fun returnValueOf_noMatchesFound() = runTest(testDispatcher) {
        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.findMatchesSuccessType =
            FindMatches.FindMatchesCapMessage.SuccessTypes.NO_MATCHES_FOUND

        val (_, activity) = launchToMatchScreen(applicationContext)

        waitForLoadingToComplete(activity)

        onView(withId(R.id.userMatchOptionsBottomLayerTextView)).check(
            matches(
                withText(R.string.match_screen_fragment_no_matches_found)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnValueOf_onCoolDown() = runTest(testDispatcher) {
        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.findMatchesSuccessType =
            FindMatches.FindMatchesCapMessage.SuccessTypes.MATCH_ALGORITHM_ON_COOL_DOWN

        val (_, activity) = launchToMatchScreen(applicationContext)

        waitForLoadingToComplete(activity)

        onView(withId(R.id.userMatchOptionsBottomLayerTextView)).check(
            matches(
                withText(R.string.match_screen_fragment_no_matches_found)
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun returnValueOf_noSwipesRemaining() = runTest(testDispatcher) {
        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.findMatchesSuccessType =
            FindMatches.FindMatchesCapMessage.SuccessTypes.NO_SWIPES_REMAINING

        val (_, activity) = launchToMatchScreen(applicationContext)

        waitForLoadingToComplete(activity)

        val extractedString = waitForTextToBeDisplayed(activity)
        val generatedString = applicationContext.getString(
            R.string.match_screen_fragment_swipes_used_up_show_time_remaining
        )

        val foundIndex = generatedString.indexOf('%')

        assertTrue(extractedString.length > foundIndex)
        assertTrue(generatedString.length > foundIndex)
        assertEquals(
            generatedString.substring(0, foundIndex),
            extractedString.substring(0, foundIndex)
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun swipe_through_matches_and_request_more() = runTest(testDispatcher) {
        //NOTE: The difference between database and server requests will be tested when testing
        // FindMatchesObject.

        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        //Want at least 2 matches here, 1 and 0 are already checked for in other tests.
        val totalNumberMatches =
            (2..10 + GlobalValues.maximumNumberMatchesStoredInViewModel).random()

        //The matches stored in the database are calculated first because there is a maximum number
        // the the database should realistically have stored.
        val numberMatchesFromDatabase = (0..min(
            GlobalValues.server_imported_values.maximumNumberResponseMessages,
            totalNumberMatches
        )).random()
        val numberMatchesFromServer = totalNumberMatches - numberMatchesFromDatabase

        Log.i(
            "number_matches",
            "numberMatchesFromDatabase: $numberMatchesFromDatabase numberMatchesFromServer: $numberMatchesFromServer"
        )

        val databaseMatchingUsers = mutableListOf<OtherUsersDataEntity>()
        val serverMatchingUsers =
            mutableListOf<OtherUserAndFindMatchesResponse>()

        //Generate random matches for database.
        for (i in 0 until numberMatchesFromDatabase) {
            val (_, otherUserDataEntity) = buildSingleRandomMatch(
                applicationContext,
                fakeStoreErrors,
            )
            databaseMatchingUsers.add(otherUserDataEntity)
        }

        //Generate random matches for 'server'.
        for (i in 0 until numberMatchesFromServer) {

            val (otherUserDataEntity, matchResponse) = generateRandomMatchOnServer(
                applicationContext
            )

            FakeClientSourceIntermediate.matchesToReturn.add(matchResponse)

            //This must not just be a copy of matchesToReturn list, it must take a copy of the
            // reference to the element.
            serverMatchingUsers.add(
                OtherUserAndFindMatchesResponse(
                    otherUserDataEntity.accountOID,
                    matchResponse
                )
            )
        }

        val (_, activity) = launchToMatchScreen(applicationContext)

        Log.i("follow_request_more", "starting waitForFirstMatchToLoad")

        if (totalNumberMatches > 0) {
            waitForFirstMatchToLoad(activity)
        }

        Log.i("follow_request_more", "completing waitForFirstMatchToLoad")

        //check matches from database
        for (i in databaseMatchingUsers.indices) {
            //Check if the user info is properly displayed.
            checkUserInfoProperlyDisplayed(
                activity,
                databaseMatchingUsers[i]
            )

            Log.i("follow_request_more", "completing waitForTime 1")

            //Wait for safe click listener delay.
            waitForTime(MatchScreenFragment.SAFE_CLICK_LISTENER_DELAY)

            Log.i("follow_request_more", "completing waitForTime 1")

            val (resourceId, responseType) =
                if ((0..1).random() == 1) {
                    Pair(R.id.matchLayoutThumbsUpImageView, ResponseType.USER_MATCH_OPTION_YES)
                } else {
                    Pair(R.id.matchLayoutThumbsDownImageView, ResponseType.USER_MATCH_OPTION_NO)
                }

            //swipe yes or no
            //NOTE: The ProgressBar userMatchOptionsBottomLayerProgressBar seems to cause a
            // problem when performing a standard click() here. The problem can be stopped if
            // the progress bar is set to View.GONE, However, instead of that doubleClick() seems
            // to create the same result while minimally effecting the application.
            onView(
                withId(resourceId)
            ).perform(
                doubleClick()
            )

            Log.i("follow_request_more", "starting waitForMatchToBeSentToServer 1")

            waitForMatchToBeSentToServer(i + 1)

            Log.i("follow_request_more", "completing waitForMatchToBeSentToServer 1")

            //Make sure proper number of matches were removed from the database. (specifics will be checked inside FindMatchesObject)
            val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()

            val expectedNumberMatchesIncludingServer =
                min(
                    totalNumberMatches - i - 1,
                    GlobalValues.server_imported_values.maximumNumberResponseMessages
                )

            Log.i(
                "number_matches",
                "databaseMatchingUsers.size - i - 1: ${databaseMatchingUsers.size - i - 1} expectedNumberMatchesIncludingServer: $expectedNumberMatchesIncludingServer allMatches.size(): ${allMatches.size}"
            )

            //Possibilities here are.
            //It could have not gotten any server matches yet (They can be delayed while requesting location).
            //It could have rolled zero matches from the server period.
            //It could have gotten all possible server updates.
            //It could just be missing a single server update. (Is two possible?)
            assertTrue(
                (databaseMatchingUsers.size - i - 1 == allMatches.size)
                        || (expectedNumberMatchesIncludingServer == allMatches.size)
                        || (expectedNumberMatchesIncludingServer - 1 == allMatches.size)
            )

            //Make sure match was sent to the server. (specifics will be checked inside FindMatchesObject)
            assertEquals(i + 1, FakeClientSourceIntermediate.userMatchOptionsRequests.size)
            assertEquals(
                FakeClientSourceIntermediate.userMatchOptionsRequests.last().responseType,
                responseType
            )
        }

        //check matches from server
        for (i in serverMatchingUsers.indices) {

            //Check if the user info is properly displayed.
            checkUserInfoProperlyDisplayed(
                activity,
                userAccountOid = serverMatchingUsers[i].otherUserOid
            )

            Log.i("follow_request_more", "starting waitForTime 2")

            //Wait for safe click listener delay.
            waitForTime(MatchScreenFragment.SAFE_CLICK_LISTENER_DELAY)

            Log.i("follow_request_more", "starting waitForTime 2")

            val (resourceId, responseType) =
                if ((0..1).random() == 1) {
                    Pair(R.id.matchLayoutThumbsUpImageView, ResponseType.USER_MATCH_OPTION_YES)
                } else {
                    Pair(R.id.matchLayoutThumbsDownImageView, ResponseType.USER_MATCH_OPTION_NO)
                }

            //swipe yes or no
            //NOTE: The ProgressBar userMatchOptionsBottomLayerProgressBar seems to cause a
            // problem when performing a standard click() here. The problem can be stopped if
            // the progress bar is set to View.GONE, However, instead of that doubleClick() seems
            // to create the same result while minimally effecting the application.
            onView(
                withId(resourceId)
            ).perform(
                doubleClick()
            )

            Log.i("follow_request_more", "starting waitForMatchToBeSentToServer 2")

            waitForMatchToBeSentToServer(numberMatchesFromDatabase + i + 1)

            Log.i("follow_request_more", "starting waitForMatchToBeSentToServer 2")

            //Make sure proper number of matches were removed from the database. (specifics will be checked inside FindMatchesObject)
            val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()

            val expectedNumberMatchesInDatabase =
                min(
                    serverMatchingUsers.size - i - 1,
                    GlobalValues.server_imported_values.maximumNumberResponseMessages
                )

            Log.i(
                "number_matches",
                "expectedNumberMatchesInDatabase: $expectedNumberMatchesInDatabase allMatches.size(): ${allMatches.size}"
            )

            //Possibilities here are.
            //It could have gotten all possible server updates.
            //It could just be missing a single server update. (Is multiple possible?)
            assertTrue(
                (allMatches.size == expectedNumberMatchesInDatabase)
                        || (allMatches.size == expectedNumberMatchesInDatabase - 1)
            )

            //Make sure match was sent to the server. (specifics will be checked inside FindMatchesObject)
            assertEquals(
                numberMatchesFromDatabase + i + 1,
                FakeClientSourceIntermediate.userMatchOptionsRequests.size
            )
            assertEquals(
                responseType,
                FakeClientSourceIntermediate.userMatchOptionsRequests.last().responseType
            )
        }

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun changeAnActivity_then_navigateToMatchScreen() = runTest(testDispatcher) {
        navigateAndChangeSomething(
            R.id.menuActivitiesConstraintLayout,
            R.id.selectCategoriesFragmentApplication
        ) {
            val currentUserActivities = convertStringToCategoryActivityMessageAndTrimTimes(
                FakeClientSourceIntermediate.accountStoredOnServer!!.categories
            ).second

            if (
                currentUserActivities.size == GlobalValues.server_imported_values.numberActivitiesStoredPerAccount
            ) { //number activities full
                deleteFinalActivity()
            } else { //number activities NOT full
                selectRandomActivity(
                    currentUserActivities,
                    FakeClientSourceIntermediate.accountStoredOnServer!!.age
                )
            }
        }
    }

    @Test
    fun doNotChangeAnActivity_then_navigateToMatchScreen() = runTest(testDispatcher) {
        navigateAndChangeNothing(
            R.id.menuActivitiesConstraintLayout,
            R.id.selectCategoriesFragmentApplication
        )
    }

    @Test
    fun changeAProfileValue_then_navigateToMatchScreen() = runTest(testDispatcher) {
        navigateAndChangeSomething(
            R.id.userProfileEditIconImageView,
            R.id.modifyProfileScreenFragment
        ) {
            val currentGender =
                FakeClientSourceIntermediate.accountStoredOnServer!!.gender

            //Allow view to become visible
            waitForTime(50)

            //Make sure gender is updated so activities will be cleared.
            if (currentGender == GlobalValues.MALE_GENDER_VALUE) {
                onView(
                    withId(R.id.loginGetGenderFemaleRadioButton)
                )
                    .perform(
                        ScrollToActionNested(),
                        click()
                    )
            } else {
                onView(
                    withId(R.id.loginGetGenderMaleRadioButton)
                )
                    .perform(
                        ScrollToActionNested(),
                        click()
                    )
            }
        }
    }

    @Test
    fun doNotChangeAProfileValue_then_navigateToMatchScreen() = runTest(testDispatcher) {
        navigateAndChangeNothing(
            R.id.userProfileEditIconImageView,
            R.id.modifyProfileScreenFragment
        )
    }

    @Test
    fun overlappingTimeframe() = runTest(testDispatcher) {
        //NOTE: Checking what I can, will need a visual check of these during manual test.

        addSpecificTimeframe { currentUserCategories, otherUserCategories, randomActivityIndex ->
            val currentUserTimeframeStart = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val currentUserTimeframeStop =
                currentUserTimeframeStart + (0..24L * 60L * 60L * 1000L).random()

            //between 12 hours before currentUserTimeframeStart and currentUserTimeframeStop
            val otherUserTimeframeStart =
                currentUserTimeframeStart + (-12L * 60L * 60L * 1000L..currentUserTimeframeStop - currentUserTimeframeStart).random()

            val otherUserTimeframeStop =
                if (otherUserTimeframeStart < currentUserTimeframeStart) {
                    //other user stop time must be >= current user start time
                    currentUserTimeframeStart + (0..24L * 60L * 60L * 1000L).random()
                } else {
                    //other user stop time must be <= current user stop time
                    (otherUserTimeframeStart..currentUserTimeframeStop).random()
                }

            currentUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .addTimeFrameArray(
                        CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(currentUserTimeframeStart)
                            .setStopTimeFrame(currentUserTimeframeStop)
                            .build()
                    )
                    .build()
            )

            otherUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .addTimeFrameArray(
                        CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(otherUserTimeframeStart)
                            .setStopTimeFrame(otherUserTimeframeStop)
                            .build()
                    )
                    .build()
            )
        }

        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchTimeFrameTitleTextView),
                        withText(R.string.match_screen_fragment_overlapping_times_singular_text)
                    )
                )
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun closeTimeframe() = runTest(testDispatcher) {
        addSpecificTimeframe { currentUserCategories, otherUserCategories, randomActivityIndex ->
            val currentUserTimeframeStart = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val currentUserTimeframeStop =
                currentUserTimeframeStart + (0..12L * 60L * 60L * 1000L).random()

            val (otherUserTimeframeStart, otherUserTimeframeStop) =
                if ((0..1).random() == 1) {
                    //Put the other user before the current user.
                    val otherUserTimeframeStop =
                        currentUserTimeframeStart + (-GlobalValues.server_imported_values.maxBetweenTime.toInt()..-1).random()
                    val otherUserTimeframeStart =
                        otherUserTimeframeStop + (-6L * 60L * 60L * 1000L..0).random()
                    Pair(otherUserTimeframeStart, otherUserTimeframeStop)
                } else {
                    //Put the other user after the current user.
                    val otherUserTimeframeStart =
                        currentUserTimeframeStop + (0..GlobalValues.server_imported_values.maxBetweenTime.toInt()).random()
                    val otherUserTimeframeStop =
                        otherUserTimeframeStart + (0..6L * 60L * 60L * 1000L).random()
                    Pair(otherUserTimeframeStart, otherUserTimeframeStop)
                }

            currentUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .addTimeFrameArray(
                        CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(currentUserTimeframeStart)
                            .setStopTimeFrame(currentUserTimeframeStop)
                            .build()
                    )
                    .build()
            )

            otherUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .addTimeFrameArray(
                        CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(otherUserTimeframeStart)
                            .setStopTimeFrame(otherUserTimeframeStop)
                            .build()
                    )
                    .build()
            )
        }

        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchTimeFrameTitleTextView),
                        withText(R.string.match_screen_fragment_close_times_singular_text)
                    )
                )
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun noMatchingTimeframe() = runTest(testDispatcher) {
        //NOTE: Checking what I can, will need a visual check of these during manual test.
        addSpecificTimeframe { currentUserCategories, otherUserCategories, randomActivityIndex ->
            val otherUserTimeframeStart = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val otherUserTimeframeStop =
                otherUserTimeframeStart + (0..12L * 60L * 60L * 1000L).random()

            currentUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .build()
            )

            otherUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .addTimeFrameArray(
                        CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(otherUserTimeframeStart)
                            .setStopTimeFrame(otherUserTimeframeStop)
                            .build()
                    )
                    .build()
            )
        }

        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchTimeFrameTitleTextView),
                        withText(R.string.match_screen_fragment_other_times_singular_text)
                    )
                )
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun anytimeTimeframe() = runTest(testDispatcher) {
        //NOTE: Checking what I can, will need a visual check of these during manual test.
        addSpecificTimeframe { currentUserCategories, otherUserCategories, randomActivityIndex ->
            currentUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .build()
            )

            otherUserCategories.add(
                CategoryActivityMessage.newBuilder()
                    .setActivityIndex(randomActivityIndex)
                    .build()
            )
        }

        onView(
            withIndex(
                withId(R.id.userMatchOptionsCardStackView),
                0
            )
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withId(R.id.matchAnytimeTextViewTextView),
                        withText(R.string.match_screen_fragment_anytime_text)
                    )
                )
            )
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun connectionError_matchStoredInDatabase() = runTest(testDispatcher) {
        setupSingleMatchAndCheckIfDisplayed()

        FakeClientSourceIntermediate.grpcAndroidSideErrorReturn =
            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR

        val (responseType, resourceId) =
            if ((0..1).random() == 1) {
                Pair(ResponseType.USER_MATCH_OPTION_YES, R.id.matchLayoutThumbsUpImageView)
            } else {
                Pair(ResponseType.USER_MATCH_OPTION_NO, R.id.matchLayoutThumbsDownImageView)
            }

        //swipe yes or no
        onView(
            withId(
                resourceId
            )
        ).perform(
            click()
        )

        val allStoredMessages =
            ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao.selectAll()

        //Wait for value to be stored in database.
        val startTime = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - startTime < 5000
            && allStoredMessages.isEmpty()
        ) {
            yield()
            delay(1)
        }

        assertEquals(1, allStoredMessages.size)

        assertTrue(FakeClientSourceIntermediate.userMatchOptionsRequests.isEmpty())

        //Make sure match was removed from the database. (specifics will be checked inside FindMatchesObject)
        val allMatches = ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao.getAllMatches()
        assertEquals(0, allMatches.size)

        val parsedMessage = ReportMessages.UserMatchOptionsRequest.parseFrom(
            allStoredMessages[0].protobufBytes
        )

        assertEquals(responseType, parsedMessage.responseType)

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun noActivitiesDisplayedInCard() = runTest(testDispatcher) {
        //This situation can arise when user is too young to see activities.

        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        val (_, otherUserDataEntity) = buildSingleRandomMatch(
            applicationContext,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val birthdayCalendar = Calendar.getInstance()

        birthdayCalendar.set(Calendar.YEAR, birthdayCalendar.get(Calendar.YEAR) - 15)

        val currentUserAge = calcPersonAgeNoError(
            birthdayCalendar.get(Calendar.YEAR),
            birthdayCalendar.get(Calendar.MONTH) + 1,
            birthdayCalendar.get(Calendar.DAY_OF_MONTH)
        ) {
            val calendar = Calendar.getInstance()
            calendar
        }

        FakeClientSourceIntermediate.accountStoredOnServer!!.birthYear =
            birthdayCalendar.get(Calendar.YEAR)
        FakeClientSourceIntermediate.accountStoredOnServer!!.birthMonth =
            birthdayCalendar.get(Calendar.MONTH) + 1
        FakeClientSourceIntermediate.accountStoredOnServer!!.birthDayOfMonth =
            birthdayCalendar.get(Calendar.DAY_OF_MONTH)
        FakeClientSourceIntermediate.accountStoredOnServer!!.age = currentUserAge

        //Other user must be old enough to choose the activity.
        birthdayCalendar.set(Calendar.YEAR, birthdayCalendar.get(Calendar.YEAR) - 21)

        val otherUserAge = calcPersonAgeNoError(
            birthdayCalendar.get(Calendar.YEAR),
            birthdayCalendar.get(Calendar.MONTH) + 1,
            birthdayCalendar.get(Calendar.DAY_OF_MONTH)
        ) {
            val calendar = Calendar.getInstance()
            calendar
        }

        otherUserDataEntity.age = otherUserAge
        var randomActivityIndex = 0

        for (userActivity in CategoriesAndActivities.allActivities) {
            if (userActivity.activity.minAge > currentUserAge) {
                randomActivityIndex = userActivity.activity.index
                Log.i("activity", "name: ${userActivity.activity.displayName}")
                break
            }
        }

        val otherUserCategories = mutableListOf<CategoryActivityMessage>()

        otherUserCategories.add(
            CategoryActivityMessage.newBuilder()
                .setActivityIndex(randomActivityIndex)
                .build()
        )

        otherUserDataEntity.activities = convertCategoryActivityMessageToString(
            otherUserCategories
        )

        /**Logging here in case test fails can reproduce the strings**/
        Log.i(
            "age_activities_string",
            "currentUserAge: $currentUserAge otherUserAge: $otherUserAge otherActivities: ${otherUserDataEntity.activities}"
        )

        //Upsert the other user account.
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ).upsertSingleOtherUser(otherUserDataEntity)

        val (_, activity) = launchToMatchScreen(applicationContext)

        //Check if the user info is properly displayed.
        checkUserInfoProperlyDisplayed(
            activity,
            otherUserDataEntity
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun matchRequiresUpdate() = runTest(testDispatcher) {

        setupCategoriesAndIcons(
            applicationContext,
            testDispatcher,
            fakeStoreErrors,
        )

        val (_, otherUserDataEntity) = buildSingleRandomMatch(
            applicationContext,
            fakeStoreErrors,
        )

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val index = 0
        val timestamp = System.currentTimeMillis()

        val picturesList = mutableListOf<PictureInfo>()

        val file = generateOtherUserPictureFile(
            otherUserDataEntity.accountOID,
            index,
            timestamp,
            applicationContext
        )

        picturesList.add(
            PictureInfo(
                file.absolutePath,
                0,
                timestamp
            )
        )

        otherUserDataEntity.picturesUpdateAttemptedTimestamp = -1
        otherUserDataEntity.pictures = convertPicturesListToString(
            picturesList
        )

        /**Logging here in case test fails can reproduce the strings**/
        Log.i(
            "age_activities_string",
            "otherUserDataEntity: $otherUserDataEntity"
        )

        //Upsert the other user account.
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ).upsertSingleOtherUser(otherUserDataEntity)

        //Clear the cache in order to make sure the any pictures that were previously stored
        // are removed.
        applicationContext.cacheDir.deleteRecursively()

        val (_, _) = launchToMatchScreen(applicationContext)

        //NOTE: Butchered the user a little bit to make sure they would be updated. But can't check if
        // 'properly' displayed.
//        checkUserInfoProperlyDisplayed(
//            activity,
//            otherUserDataEntity
//        )

        //Wait for the update to be requested
        waitForTime(200)

        assertEquals(
            FakeClientSourceIntermediate.numTimesUpdateSingleMatchMemberCalled,
            1
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

}