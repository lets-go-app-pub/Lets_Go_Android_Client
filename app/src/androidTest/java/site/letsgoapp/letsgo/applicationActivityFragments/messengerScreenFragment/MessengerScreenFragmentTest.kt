package site.letsgoapp.letsgo.applicationActivityFragments.messengerScreenFragment

import android.content.Context
import androidx.navigation.NavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import chat_message_to_client.ChatMessageToClientMessage
import grpc_chat_commands.ChatRoomCommands
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment.ChatRoomsListFragment
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.GeneratedUserInfoWithTimes
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.generateRandomUser
import site.letsgoapp.letsgo.utilities.generateChatMessageUUID
import type_of_chat_message.TypeOfChatMessageOuterClass.AmountOfMessage
import java.io.IOException


@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MessengerScreenFragmentTest {

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

        runBlocking {
            setupCategoriesAndIcons(
                applicationContext,
                testDispatcher,
                fakeStoreErrors,
            )
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        tearDownForTesting(applicationContext)
    }

    private suspend fun launchToMessengerScreen(): Pair<NavController?, AppActivity> {
        val (navController, activity) = launchToMatchScreen(applicationContext)

        //Navigate to messenger screen.
        onView(
            withId(R.id.messengerScreenFragment)
        ).perform(
            click()
        )

        return Pair(navController, activity)
    }

    private fun openPopupMenuAndClickOption(
        itemString: Int
    ) {
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectPopupMenuImageView)
        )
            .perform(
                click()
            )

        onView(
            withText(itemString)
        )
            .inRoot(
                RootMatchers.isPlatformPopup()
            )
            .perform(
                click()
            )
    }

    private suspend fun runSuccessfulJoinChatRoom(
        chatRoomId: String
    ) {
        val (navController, _) = launchToMessengerScreen()

        openJoinChatRoomAndEnterId(
            chatRoomId
        )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.chatRoomFragment
        )

        //Make sure chat room name matches
        onView(
            withId(R.id.activityAppTopToolbar)
        )
            .check(
                matches(
                    hasDescendant(
                        withText(FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomInfoMessage.chatRoomName)
                    )
                )
            )

        //Make sure the proper number of messages was loaded
        onView(
            withId(R.id.chatRoomChatRecyclerView)
        )
            .check(
                RecyclerViewItemCountAssertion(
                    FakeClientSourceIntermediate.joinChatRoomObjects.messagesToSendBackOnJoinChatRoom.size + 2
                )
            )

        //Make sure the final differentUserJoined message is displayed.
        onView(
            withId(R.id.chatRoomChatRecyclerView)
        )
            .check(
                matches(
                    hasDescendant(
                        withText(
                            applicationContext.resources.getString(
                                R.string.chat_room_fragment_user_joined,
                                applicationContext.resources.getString(R.string.You_have)
                            )
                        )
                    )
                )
            )

        //Navigate to ChatRoomInfoFragment.
        onView(
            withId(R.id.activityAppTopToolbar)
        )
            .perform(
                click()
            )

        //Make sure the proper number of messages was loaded
        onView(
            withId(R.id.chatRoomInfoMembersRecyclerView)
        )
            .check(
                RecyclerViewItemCountAssertion(
                    //+2 is for the current user and the info panel that is first.
                    FakeClientSourceIntermediate.joinChatRoomObjects.usersToSendBackOnJoinChatRoom.size + 2
                )
            )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private suspend fun joinChatRoomWithServerError(
        stringResourceId: Int,
        chatRoomId: String = generateRandomChatRoomIdForTesting()
    ) {
        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val (navController, activity) = launchToMessengerScreen()

        openJoinChatRoomAndEnterId(
            chatRoomId
        )

        //Make sure no navigation happened.
        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.messengerScreenFragment
        )

        checkIfToastExistsString(
            activity,
            applicationContext.getString(stringResourceId)
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    private fun openJoinChatRoomAndEnterId(
        chatRoomId: String
    ) {
        openPopupMenuAndClickOption(R.string.chat_room_fragment_popup_menu_join_chat_room)

        //Enter a chat room id.
        onView(
            withId(R.id.joinChatRoomDialogChatRoomIdEditText)
        )
            .perform(
                replaceText(
                    chatRoomId
                )
            )

        //Press ok.
        onView(
            withId(android.R.id.button1)
        )
            .perform(
                click()
            )
    }

    private fun generateAndStoreRandomChatRoom(): ChatRoomDataClass {
        val chatRoomDataClass = generateRandomNewChatRoom()

        runBlocking {
            ChatRoomsDaoIntermediate(
                ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
            ).insertChatRoom(chatRoomDataClass)
        }

        return chatRoomDataClass
    }

    private suspend fun runSearchTest(
        useRandomText: Boolean
    ) : Pair<ChatRoomDataClass, ChatRoomDataClass> {
        val firstChatRoomDataClass = generateAndStoreRandomChatRoom()
        val secondChatRoomDataClass = generateAndStoreRandomChatRoom()

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val (_, _) = launchToMessengerScreen()

        openPopupMenuAndClickOption(R.string.chat_room_fragment_popup_menu_search)

        //Make sure first element was not hidden.
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectRecyclerView)
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withText(firstChatRoomDataClass.chatRoomName),
                        isDisplayed()
                    )
                )
            )
        )

        //Make sure second element was not hidden.
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectRecyclerView)
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withText(firstChatRoomDataClass.chatRoomName),
                        isDisplayed()
                    )
                )
            )
        )

        //Enter the second chat room name as a search.
        onView(
            withId(R.id.fragmentMessengerSearchEditText)
        ).perform(
            replaceText(
                if(useRandomText) {
                    generateChatMessageUUID()
                } else {
                    secondChatRoomDataClass.chatRoomName
                }
            )
        )

        waitForTime(ChatRoomsListFragment.TIME_BEFORE_SEARCH_EXECUTES_MS + 50)

        return Pair(firstChatRoomDataClass, secondChatRoomDataClass)
    }

    @Test
    fun createNewChatRoom() = runTest(testDispatcher) {

        val (navController, _) = launchToMessengerScreen()

        openPopupMenuAndClickOption(R.string.chat_room_fragment_popup_menu_new_chat_room)

        //At 0 the chat room on the server will generate its own name. However, that is
        // a server test not a client test.
        val chatRoomName = generateRandomString((1..100).random())

        //Enter chat room name to EditText.
        onView(
            withId(R.id.singleLineEditTextDialogEditText)
        )
            .perform(
                replaceText(
                    chatRoomName
                )
            )

        //Press ok.
        onView(
            withId(android.R.id.button1)
        )
            .perform(
                click()
            )

        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.chatRoomFragment
        )

        //Make sure chat room name matches
        onView(
            withId(R.id.activityAppTopToolbar)
        )
            .check(
                matches(
                    hasDescendant(
                        withText(chatRoomName)
                    )
                )
            )

        //Make sure user joined message is displayed.
        onView(
            withId(R.id.chatRoomChatRecyclerView)
        )
            .check(
                matches(
                    hasDescendant(
                        withText(
                            applicationContext.resources.getString(
                                R.string.chat_room_fragment_user_joined,
                                applicationContext.resources.getString(R.string.You_have)
                            )
                        )
                    )
                )
            )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun joinChatRoom_SUCCESSFULLY_JOINED_downloadsMessages() = runTest(testDispatcher) {
        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val chatRoomId = generateRandomChatRoomIdForTesting()

        FakeClientSourceIntermediate.joinChatRoomObjects.generateRandomChatRoomInfoMessage(
            chatRoomId
        )

        val userList = mutableListOf<GeneratedUserInfoWithTimes>()
        val messagesList = mutableListOf<ChatMessageToClientMessage.ChatMessageToClient>()

        //Want at least one other user.
        val numMembersToAdd = (1..10).random()

        for (i in (0 until numMembersToAdd)) {
            userList.add(
                generateRandomUser(
                    applicationContext,
                    messagesList
                )
            )
        }

        val numMessagesToAdd = (1..100).random()

        for (i in 0 until numMessagesToAdd) {
            val userIndex = (0 until userList.size).random()

            val timestampStored =
                (userList[userIndex].userJoinedTime..userList[userIndex].userLastActivityTime).random()
            val userOid = userList[userIndex].memberInfoMessage.userInfo.accountOid

            messagesList.add(
                generateTextMessage(
                    GenericMessageParameters(
                        sentByAccountOID = userOid,
                        timestampStored = timestampStored,
                        onlyStoreMessage = true,
                        chatRoomIdSentFrom = FakeClientSourceIntermediate.joinChatRoomObjects.chatRoomId,
                        amountOfMessage = AmountOfMessage.COMPLETE_MESSAGE_INFO,
                        hasCompleteInfo = true
                    ),
                    //No need to make this too long. Can be tested more in depth in ChatRoomFragment.
                    messageText = generateRandomString(
                        (1..20).random()
                    )
                ).build()
            )
        }

        messagesList.sortBy {
            it.timestampStored
        }

        FakeClientSourceIntermediate.joinChatRoomObjects.messagesToSendBackOnJoinChatRoom =
            messagesList
        FakeClientSourceIntermediate.joinChatRoomObjects.usersToSendBackOnJoinChatRoom = userList

        runSuccessfulJoinChatRoom(chatRoomId)
    }

    @Test
    fun joinChatRoom_SUCCESSFULLY_JOINED_noMessagesDownloaded() = runTest(testDispatcher) {
        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val chatRoomId = generateRandomChatRoomIdForTesting()

        FakeClientSourceIntermediate.joinChatRoomObjects.generateRandomChatRoomInfoMessage(
            chatRoomId
        )

        runSuccessfulJoinChatRoom(chatRoomId)
    }

    @Test
    fun joinChatRoom_ACCOUNT_WAS_BANNED() = runTest(testDispatcher) {
        FakeClientSourceIntermediate.joinChatRoomStatus =
            ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED

        joinChatRoomWithServerError(
            R.string.messenger_screen_fragment_account_banned_from_chat_room
        )
    }

    @Test
    fun joinChatRoom_CHAT_ROOM_DOES_NOT_EXIST() = runTest(testDispatcher) {
        FakeClientSourceIntermediate.joinChatRoomStatus =
            ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST

        joinChatRoomWithServerError(
            R.string.messenger_screen_fragment_no_chat_room_id_exists
        )
    }

    @Test
    fun joinChatRoom_INVALID_CHAT_ROOM_ID() = runTest(testDispatcher) {
        joinChatRoomWithServerError(
            R.string.messenger_screen_fragment_invalid_chat_room_id_entered,
            "a"
        )
    }

    @Test
    fun joinChatRoom_userAlreadyInsideChatRoom() = runTest(testDispatcher) {
        val chatRoomDataClass = generateAndStoreRandomChatRoom()

        FakeClientSourceIntermediate.setupCompleteServerAccount()

        val (navController, _) = launchToMessengerScreen()

        openJoinChatRoomAndEnterId(
            chatRoomDataClass.chatRoomId
        )

        //Make sure no navigation happened.
        guaranteeFragmentReached(
            applicationContext,
            navController,
            R.id.chatRoomFragment
        )

        assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }

    @Test
    fun search_stringExists() = runTest(testDispatcher) {
        val (firstChatRoomDataClass, secondChatRoomDataClass) = runSearchTest(false)

        //Make sure first element was hidden.
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectRecyclerView)
        ).check(
            matches(
                hasDescendant(
                    not(
                        withText(firstChatRoomDataClass.chatRoomName)
                    )
                )
            )
        )

        //Make sure second element was NOT hidden.
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectRecyclerView)
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        withText(secondChatRoomDataClass.chatRoomName),
                        isDisplayed()
                    )
                )
            )
        )
    }

    @Test
    fun search_stringDoesNotExist() = runTest(testDispatcher) {
        val (firstChatRoomDataClass, secondChatRoomDataClass) = runSearchTest(true)

        //Make sure both elements were hidden.
        onView(
            withId(R.id.fragmentMessengerScreenChatRoomSelectRecyclerView)
        ).check(
            matches(
                hasDescendant(
                    allOf(
                        not(
                            withText(firstChatRoomDataClass.chatRoomName)
                        ),
                        not(
                            withText(secondChatRoomDataClass.chatRoomName)
                        )
                    )
                )
            )
        )
    }

    @Test
    fun sort_unread() = runTest(testDispatcher) {
        val firstChatRoomDataClass = generateAndStoreRandomChatRoom()
        val secondChatRoomDataClass = generateAndStoreRandomChatRoom()
        val thirdChatRoomDataClass = generateAndStoreRandomChatRoom()
    }

    @Test
    fun sort_recent() = runTest(testDispatcher) {

    }

    @Test
    fun sort_visited() = runTest(testDispatcher) {

    }

    @Test
    fun sort_joined() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_messageFromBlockedUser() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_textMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_pictureMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_locationMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_mimeTypeMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_inviteMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_otherUserKickedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_otherUserBannedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_differentUserJoinedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_differentUserLeftMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_chatRoomNameUpdatedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_chatRoomPasswordUpdatedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_newAdminPromotedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receiveMessage_historyClearedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun requestMessageUpdate() = runTest(testDispatcher) {
        //TODO: ONLY_SKELETON message amount stored
    }

    @Test
    fun chatRoomFormattedFor_noMembers() = runTest(testDispatcher) {

    }

    @Test
    fun chatRoomFormattedFor_singleMember() = runTest(testDispatcher) {

    }

    @Test
    fun chatRoomFormattedFor_multipleMembers() = runTest(testDispatcher) {

    }

    @Test
    fun currentUser_receiveThisUserLeftChatRoomMessage() = runTest(testDispatcher) {

    }

    @Test
    fun currentUser_receiveThisKickedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun currentUser_receiveThisBannedMessage() = runTest(testDispatcher) {

    }

    @Test
    fun receivedNewMatch() = runTest(testDispatcher) {

    }

    @Test
    fun matchRemovedWhenOtherUserUnmatched() = runTest(testDispatcher) {

    }

    @Test
    fun matchingChatRoomChangedToNormalChatRoom() = runTest(testDispatcher) {

    }

    @Test
    fun longClickLeaveChatRoomWorks() = runTest(testDispatcher) {

    }

    @Test
    fun clickMatchForMatchingChatRoom() = runTest(testDispatcher) {
        //TODO: Do I want this or will ChatRoomFragment test these?
    }

    @Test
    fun clickChatRoomForChatRoom() = runTest(testDispatcher) {
        //TODO: Do I want this or will ChatRoomFragment test these?
    }
}
