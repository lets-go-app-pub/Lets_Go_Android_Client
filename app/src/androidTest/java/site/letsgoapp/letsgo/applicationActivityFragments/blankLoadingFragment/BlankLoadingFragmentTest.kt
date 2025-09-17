package site.letsgoapp.letsgo.applicationActivityFragments.blankLoadingFragment

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import java.io.IOException


@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BlankLoadingFragmentTest {
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

    @Test
    fun navigateTo_matchFragment() = runTest(testDispatcher) {
        val (navController, _) = launchAppActivity(applicationContext)

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.matchScreenFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun navigateTo_messengerFragment() = runTest(testDispatcher) {

        val (navController, _) = launchAppActivityFromIntent(
            applicationContext,
            NotificationInfo.SEND_TO_CHAT_ROOM_LIST
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.messengerScreenFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun navigateTo_chatRoom_exists() = runTest(testDispatcher) {

        val chatRoomDataClass = generateRandomNewChatRoom()

        runBlocking {
            ChatRoomsDaoIntermediate(
                ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
            ).insertChatRoom(chatRoomDataClass)
        }

        val (navController, _) = launchAppActivityFromIntent(
            applicationContext,
            chatRoomDataClass.chatRoomId
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.chatRoomFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun navigateTo_chatRoom_doesNotExist() = runTest(testDispatcher) {

        val (navController, _) = launchAppActivityFromIntent(
            applicationContext,
            "12345678"
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.messengerScreenFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }
}