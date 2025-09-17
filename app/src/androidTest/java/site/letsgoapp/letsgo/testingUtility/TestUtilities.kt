package site.letsgoapp.letsgo.testingUtility

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.Root
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ScrollToAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.GrantPermissionRule
import categorytimeframe.CategoryTimeFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.hamcrest.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.allOf
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModelFactory
import site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment.CategoriesListAdapter


fun extractSharedLoginViewModel(
    loginActivity: LoginActivity,
    applicationContext: Context
): SharedLoginViewModel {
    val viewModelFactory = SharedLoginViewModelFactory(
        (applicationContext as LetsGoApplicationClass).loginRepository,
        applicationContext.loginSupportFunctions,
        applicationContext.picturesRepository,
    )

    return ViewModelProvider(loginActivity, viewModelFactory)[SharedLoginViewModel::class.java]
}

data class TestingAgeRangeObject(val minAge: Int, val maxAge: Int)

private const val DEFAULT_AGE_RANGE_PLUS_MINUS_CHILDREN = 2
private const val DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS = 5

fun generateDefaultAgeRange(userAge: Int): TestingAgeRangeObject {

    var minAge = -1
    var maxAge = -1

    when (userAge) {
        in 13..15 -> { //if user is between 13 and 15
            minAge = 13;
            maxAge = userAge + DEFAULT_AGE_RANGE_PLUS_MINUS_CHILDREN
        }
        in 16..19 -> { //if user is 16 to 19
            minAge = userAge - DEFAULT_AGE_RANGE_PLUS_MINUS_CHILDREN
            maxAge = userAge + DEFAULT_AGE_RANGE_PLUS_MINUS_CHILDREN
        }
        in 20..(18 + DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS) -> { //if user is 20 or older match 18 to age + DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS
            minAge = 18;
            maxAge = userAge + DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS;
        }
        in (19 + DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS)..
                (GlobalValues.server_imported_values.highestAllowedAge - DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS) -> { //if user is 24 or older match with +/- DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS years
            minAge = userAge - DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS;
            maxAge = userAge + DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS;
        }
        in (1 + GlobalValues.server_imported_values.highestAllowedAge - DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS)..
                GlobalValues.server_imported_values.highestAllowedAge -> { //if user is ~116 or older
            minAge = userAge - DEFAULT_AGE_RANGE_PLUS_MINUS_ADULTS
            maxAge = GlobalValues.server_imported_values.highestAllowedAge
        }
    }

    if (minAge > GlobalValues.server_imported_values.highestDisplayedAge) {
        //The device restricts displaying ages to HIGHEST_DISPLAYED_AGE, so for consistency
        // this function will do the same thing.
        minAge = GlobalValues.server_imported_values.highestDisplayedAge
    }
    if (maxAge >= GlobalValues.server_imported_values.highestDisplayedAge) {
        //Want HIGHEST_ALLOWED_AGE so users can match up to 120 not just up to 80, when the user
        // selects 80+ inside the device it will return 120 as well.
        maxAge = GlobalValues.server_imported_values.highestDisplayedAge
    }

    return TestingAgeRangeObject(minAge, maxAge)
}

fun <T : Activity> checkIfToastExistsString(
    activity: T?,
    string: String?
) {
    //There is an issue listed here https://github.com/android/android-test/issues/803. This doesn't
    // allow toasts to be detected by espresso on newer API versions. When this issue is resolved
    // this check can be taken off.
    if (Build.VERSION.SDK_INT < 30) {
        //Also there seems to be an issue with API 21 where this will occasionally get stuck.
//        onView(
//            withText(
//                //NOTE: MUST pass this in as a string, passing it as a resource causes it to fail
//                // or take a long time.
//                string
//            )
//        ).inRoot(
//            RootMatchers.withDecorView(Matchers.not(activity?.window?.decorView))
//        )
//            .check(
//                matches(
//                    isDisplayed()
//                )
//            )
    }
}

fun <T : Activity> checkIfToastExists(
    scenario: ActivityScenario<T>,
    stringResource: Int
) {
    scenario.onActivity {
        checkIfToastExistsString(
            it,
            it?.resources?.getString(stringResource)
        )
    }
}

fun <T, U : Activity> waitForLivedata(
    scenario: ActivityScenario<U>,
    liveData: LiveData<T>,
    beforeLiveDataBlock: () -> Unit,
    afterLiveDataBlock: () -> Unit
) {

    var received: Boolean
    val smsVerifiedObserver =
        Observer<T> {
            received = true
        }

    try {
        scenario.onActivity {
            liveData.observeForever(smsVerifiedObserver)
        }

        received = false

        beforeLiveDataBlock()

        while (!received) {
            Thread.sleep(1)
        }

        afterLiveDataBlock()
    } finally {
        scenario.onActivity {
            liveData.removeObserver(smsVerifiedObserver)
        }
    }

}

fun selectActivityByName(
    categoryName: String,
    activityIconName: String,
    activityIndex: Int
) {

    onView(
        withId(R.id.selectCategoriesRecyclerView)
    )
        .perform(
            scrollTo<CategoriesListAdapter.CategoryViewHolder>(
                hasDescendant(
                    allOf(
                        withId(
                            R.id.categoryListItemTitleTextView
                        ),
                        withText(
                            categoryName
                        )
                    )
                )
            )
        )

    //Click on selected category.
    onView(
        allOf(
            withId(
                R.id.categoryListItemTitleTextView
            ),
            withText(
                categoryName
            )
        )
    ).perform(click())

    //Wait for menu to expand.
    Thread.sleep(CategoriesListAdapter.LIST_ITEM_EXPAND_DURATION + 50)

    //Must match by tag as well as text because multiple activities with the same
    // name may exist.
    onView(
        allOf(
            withTagValue(
                `is`(activityIndex)
            ),
            withText(activityIconName)
        )
    )
        .perform(
            click()
        )

    //Select 'Anytime' option.
    onView(
        allOf(
            withId(android.R.id.text1),
            withText(R.string.select_choice_dialog_choice_one)
        )
    ).perform(click())

}

fun deleteFinalActivity() {
    onView(
        withIndexFromParent(
            withId(R.id.chooseCategoryLayout),
            GlobalValues.server_imported_values.numberActivitiesStoredPerAccount - 1
        )
    ).perform(
        click()
    )

    onView(
        withId(R.id.deleteActivityStoredIconDataTextView),
    ).perform(click())
}

fun selectRandomActivity(
    currentUserActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage>,
    currentUserAge: Int
) {
    var activitySelected = false
    var randomActivityIndex = 0

    while (!activitySelected) {

        randomActivityIndex =
            (1 until CategoriesAndActivities.allActivities.size).random()

        val existingActivity = currentUserActivities.firstOrNull {
            it.activityIndex == randomActivityIndex
        }

        val categoryIndex =
            CategoriesAndActivities.allActivities[randomActivityIndex].activity.categoryIndex

        val validAgeOfActivity =
            CategoriesAndActivities.allActivities[randomActivityIndex].activity.minAge <= currentUserAge
                    && CategoriesAndActivities.activitiesOrderedByCategory[categoryIndex].category.minAge <= currentUserAge

        activitySelected = (existingActivity == null) && validAgeOfActivity
    }

    val categoryIndex =
        CategoriesAndActivities.allActivities[randomActivityIndex].activity.categoryIndex
    val activityIconName =
        CategoriesAndActivities.allActivities[randomActivityIndex].activity.iconDisplayName

    val categoryName =
        CategoriesAndActivities.activitiesOrderedByCategory[categoryIndex].category.name

    selectActivityByName(
        categoryName,
        activityIconName,
        randomActivityIndex
    )
}

//scrollTo() ViewAction for a NestedScrollView.
class ScrollToActionNested(
    private val original: ScrollToAction = ScrollToAction()
) : ViewAction by original {

    override fun getConstraints(): Matcher<View> = CoreMatchers.anyOf(
        allOf(
            withEffectiveVisibility(Visibility.VISIBLE),
            isDescendantOfA(isAssignableFrom(NestedScrollView::class.java))
        ),
        original.constraints
    )
}

fun grantAllAppPermissionRules(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

suspend fun waitForTime(
    timeToDelayInMs: Int
) {
    //Wait for value to be 'sent' to the server.
    val startTime = SystemClock.uptimeMillis()
    while (SystemClock.uptimeMillis() - startTime < timeToDelayInMs
    ) {
        yield()
        delay(10)
    }
}

class RecyclerViewItemCountAssertion(private val expectedCount: Int) : ViewAssertion {
    override fun check(view: View, noViewFoundException: NoMatchingViewException?) {
        if (noViewFoundException != null) {
            throw noViewFoundException
        }
        val recyclerView = view as RecyclerView
        val adapter = recyclerView.adapter
        assertThat(adapter!!.itemCount, `is`(expectedCount))
    }
}