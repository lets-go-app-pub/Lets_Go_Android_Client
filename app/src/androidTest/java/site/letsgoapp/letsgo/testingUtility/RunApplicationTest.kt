package site.letsgoapp.letsgo.testingUtility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertTrue
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStartDeleteFileInterface
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.setupLoginActivitiesList
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.setupLoginCategoriesList
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import java.io.ByteArrayOutputStream


suspend fun storeUserPicturesInDatabase(
    applicationContext: Context,
    deleteFileInterface: StartDeleteFileInterface = FakeStartDeleteFileInterface()
) {
    ServiceLocator.accountInfoDatabase?.accountPictureDatabaseDao?.clearTable()

    for (pic in FakeClientSourceIntermediate.picturesStoredOnServer) {
        if (String(pic.fileInBytes.toByteArray()) == "~") { //If picture is deleted
            AccountPictureDaoIntermediate(
                ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
            ).removeSinglePicture(pic.indexNumber)
        } else {
            saveUserPictureToFileAndDatabase(
                applicationContext,
                AccountPictureDaoIntermediate(
                    ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
                ),
                pic.indexNumber,
                pic.fileInBytes.toByteArray(),
                pic.fileSize,
                pic.timestampPictureLastUpdated,
                deleteFileInterface
            )
        }
    }
}

fun generateAccountAndNavigateToAppActivity(
    applicationContext: Context,
) {
    runLoginTest(
        applicationContext,
        StopPointWhenRunLoginTest.SET_PHONE_NUMBER
    )

    FakeClientSourceIntermediate.setupCompleteServerAccount()

    //insert account to database
    runBlocking {
        ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.clearTable()
        ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.insertAccount(
            FakeClientSourceIntermediate.accountStoredOnServer!!
        )
    }

    //insert pictures to database
    runBlocking {
        storeUserPicturesInDatabase(applicationContext)

        ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.insertAccount(
            FakeClientSourceIntermediate.accountStoredOnServer!!
        )
    }

    val phoneNumber = FakeClientSourceIntermediate.accountStoredOnServer!!.phoneNumber
    //Enter a valid phone number
    Espresso.onView(ViewMatchers.withId(R.id.phoneNumberEditText))
        .perform(ViewActions.replaceText(phoneNumber))

    //Click phone login continue button
    Espresso.onView(ViewMatchers.withId(R.id.getPhoneContinueButton)).perform(ViewActions.click())

    checkAppActivityWasReached()
}

suspend fun launchAppActivityFromIntent(
    applicationContext: Context,
    intentDataString: String
): Pair<NavController?, AppActivity> {
    val chatRoomDataClass = generateRandomNewChatRoom()

    runBlocking {
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ).insertChatRoom(chatRoomDataClass)
    }

    val sharedPreferences = applicationContext.getSharedPreferences(
        applicationContext.resources.getString(R.string.shared_preferences_lets_go_key),
        Context.MODE_PRIVATE
    )

    //set up account to manually log in
    sharedPreferences.edit().putInt(
        applicationContext.resources.getString(R.string.shared_preferences_version_code_key),
        GlobalValues.Lets_GO_Version_Number
    ).apply()

    //Setup account inside database and 'server'.
    FakeClientSourceIntermediate.setupCompleteServerAccount()
    runBlocking {
        ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
            FakeClientSourceIntermediate.accountStoredOnServer!!
        )
    }

    val resultIntent = Intent(applicationContext, LoginActivity::class.java).apply {
        this.flags =
            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(
            NotificationInfo.ACTIVITY_STARTED_FROM_NOTIFICATION_CHAT_ROOM_ID_KEY,
            intentDataString
        )
        putExtra(
            "ignoreTaskRootCheck",
            true
        )
    }

    ActivityScenario.launch<LoginActivity>(resultIntent)

    while (getActivityInstance() !is AppActivity) {
        yield()
    }

    val currentActivity = getActivityInstance()

    assertTrue(currentActivity != null)
    assertTrue(currentActivity is AppActivity)

    val navController = currentActivity!!.findNavController(R.id.navigationHostFragment)

    return Pair(navController, currentActivity as AppActivity)
}

suspend fun launchToMatchScreen(
    applicationContext: Context,
): Pair<NavController?, AppActivity> {
    val (navController, activity) = launchAppActivity(applicationContext)

    guaranteeFragmentReached(
        applicationContext,
        navController,
        R.id.matchScreenFragment
    )

    return Pair(navController, activity)
}

fun drawable2Bytes(d: Drawable): ByteArray {
    val bitmap = drawable2Bitmap(d)
    return bitmap2Bytes(bitmap)
}

fun drawable2Bitmap(drawable: Drawable): Bitmap {
    val bitmap = Bitmap
        .createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
    val canvas = Canvas(bitmap)
    drawable.setBounds(
        0,
        0,
        drawable.intrinsicWidth,
        drawable.intrinsicHeight
    )
    drawable.draw(canvas)
    return bitmap
}

fun bitmap2Bytes(bm: Bitmap): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bm.compress(Bitmap.CompressFormat.JPEG, 10, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
}

//Set currentUsersDataEntity to non null in order to use it instead of generating.
// A new account. The account is expected to be stored in the 'server' and database.
suspend fun launchAppActivity(
    applicationContext: Context,
): Pair<NavController?, AppActivity> {

    val sharedPreferences = applicationContext.getSharedPreferences(
        applicationContext.resources.getString(R.string.shared_preferences_lets_go_key),
        Context.MODE_PRIVATE
    )

    //set up account to manually log in
    sharedPreferences.edit().putInt(
        applicationContext.resources.getString(R.string.shared_preferences_version_code_key),
        GlobalValues.Lets_GO_Version_Number
    ).apply()


    //set up tutorial to not show
    sharedPreferences.edit().putBoolean(
        applicationContext.resources.getString(R.string.shared_preferences_tutorial_shown_key),
        true
    ).apply()

    //Setup account inside database and 'server'.
    FakeClientSourceIntermediate.setupCompleteServerAccountIfNotSetup()
    runBlocking {
        ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao.insertAccount(
            FakeClientSourceIntermediate.accountStoredOnServer!!
        )
    }

    var initialized = false
    while(!initialized) {
        //There is a bug with ActivityScenario and initializations. More can be read here
        // https://github.com/pedrovgs/Shot/issues/135
        // although it is a different library talking about it. However, simple looping over it
        // seems to allow time for the activity to launch properly.
        try {
            ActivityScenario.launch<LoginActivity>(
                Intent(ApplicationProvider.getApplicationContext(), LoginActivity::class.java).apply {
                    putExtra("ignoreTaskRootCheck", true)
                }
            )
            initialized = true
        } catch (_: RuntimeException) {
            yield()
            delay(1)
        }
    }

    var currentActivity = getActivityInstance()

    while (currentActivity !is AppActivity) {
        yield()
        currentActivity = getActivityInstance()
    }

    val navController = currentActivity.findNavController(R.id.navigationHostFragment)

    return Pair(navController, currentActivity)
}

fun getActivityInstance(): Activity? {
    var currentActivity: Activity? = null
    getInstrumentation().runOnMainSync {
        val resumedActivities: Collection<*> =
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        if (resumedActivities.iterator().hasNext()) {
            currentActivity = resumedActivities.iterator().next() as Activity?
        }
    }
    return currentActivity
}

//Must be set up for 'generateRandomCategoriesForTesting()' to work.
suspend fun setupCategoriesAndIcons(
    applicationContext: Context,
    testDispatcher: CoroutineDispatcher,
    fakeStoreErrors: FakeStoreErrors,
) {
    //Set icons to database to be used.
    (applicationContext as LetsGoApplicationClass).loginRepository.setIconDrawablesDatabaseIndexing(
        "",
        ""
    )

    //Set activities and categories list to server to be used.
    CategoriesAndActivities.setupCategoriesAndActivities(
        setupLoginCategoriesList().toMutableList(),
        setupLoginActivitiesList().toMutableList(),
        testDispatcher,
        fakeStoreErrors
    )
}

suspend fun buildSingleRandomMatch(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
) : Pair<MatchesDataEntity, OtherUsersDataEntity> {

    val userOid = generateRandomOidForTesting()
    val currentTimestamp = System.currentTimeMillis()

    val matchDataEntity = MatchesDataEntity(
        userOid,
        generateRandomPointValue(),
        generateRandomExpirationTimeInFuture(currentTimestamp),
        generateRandomOtherUsersMatched(),
        40
    )

    val transactionWrapper = ServiceLocator.provideTransactionWrapper(
        applicationContext,
        DatabasesToRunTransactionIn.OTHER_USERS
    )

    lateinit var otherUserDataEntity: OtherUsersDataEntity

    //Transaction is here because the other user data entity requires the
    // match index in order to be stored. However, if the match is
    // extracted without the other user existing an error can occur.
    transactionWrapper.runTransaction {

        val matchIndex = MatchesDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao
        ).insertMatch(matchDataEntity)

        val objectsRequiringInfo = mutableSetOf<ObjectRequiringInfo>()

        objectsRequiringInfo.add(
            ObjectRequiringInfo(
                ReferencingObjectType.MATCH_REFERENCE,
                matchIndex.toString()
            )
        )

        otherUserDataEntity = generateRandomOtherUsersDataEntityWithPicture(
            applicationContext,
            userOid,
            fakeStoreErrors,
            currentTimestamp,
            objectsRequiringInfo
        )

        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ).upsertSingleOtherUser(otherUserDataEntity)
    }

    return Pair(matchDataEntity, otherUserDataEntity)
}