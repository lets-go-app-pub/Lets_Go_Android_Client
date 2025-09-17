package site.letsgoapp.letsgo.applicationActivityFragments.blankLoadingFragment

import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import java.lang.ref.WeakReference

class BlankLoadingFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private lateinit var thisFragmentInstanceID: String

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels(
        factoryProducer = factoryProducer
    )

    private lateinit var applicationAccountInfoResult: Observer<EventWrapperWithKeyString<Unit>>
    private lateinit var returnSingleChatRoomObserver: Observer<EventWrapperWithKeyString<Unit>>
    private lateinit var returnChatStreamInitialDownloadsCompletedObserver: Observer<EventWrapper<Unit>>
    private lateinit var returnSingleChatRoomNotFoundObserver: Observer<EventWrapperWithKeyString<String>>

    private var receivedAccountInfo = false
    private var applicationActivity: AppActivity? = null
    private var initialized = false
    private var notificationsHaveBeenRequested = false

    //The purpose of this handler is just in case something unforeseen happens and the app gets 'stuck' on
    // this fragment. It will send an error and log the user out.
    private val errorNavigatingFromBlankLoadingFragment = Handler(Looper.getMainLooper())
    private val navigateFromBlankLoadingFragment = "Navigate_From_Blank_Loading_Fragment_"

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i("whereAmI", "BlankLoadingFragment")

        // Inflate the layout for this fragment.
        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        if (initializeLoginActivity) {
            applicationActivity = requireActivity() as AppActivity
        }

        return inflater.inflate(R.layout.fragment_splash_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkForNotificationPrivileges()

        applicationAccountInfoResult = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let {
                handleApplicationAccountInfoResult()
            }
        }

        //letting observer run so it can continue even when application is stopped.
        sharedApplicationViewModel.applicationAccountInfo.observeForever(
            applicationAccountInfoResult
        )

        returnSingleChatRoomObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let {
                handleRetrieveSingleChatRoom()
            }
        }

        //letting observer run so it can continue even when application is stopped
        sharedApplicationViewModel.returnSingleChatRoom.observeForever(returnSingleChatRoomObserver)

        returnChatStreamInitialDownloadsCompletedObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled()?.let {
                returnChatStreamInitialDownloadsCompleted()
            }
        }

        //letting observer run so it can continue even when application is stopped
        sharedApplicationViewModel.returnChatStreamInitialDownloadsCompleted.observeForever(
            returnChatStreamInitialDownloadsCompletedObserver
        )

        returnSingleChatRoomNotFoundObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let {
                //If chat room was not found, simply navigate to messenger screen.
                GlobalValues.loginToChatRoomId = NotificationInfo.SEND_TO_CHAT_ROOM_LIST

                fragmentSpecificNavigate(R.id.action_blankLoadingFragment_to_messengerScreenFragment)
            }
        }

        //Letting observer run so it can continue even when application is stopped.
        sharedApplicationViewModel.returnSingleChatRoomNotFound.observeForever(
            returnSingleChatRoomNotFoundObserver
        )

        errorNavigatingFromBlankLoadingFragment.postAtTime(
            {
                //No need to store error if the only thing lacking is the notification permission
                // has not been completed. It is possible for the custom dialog OR for the notification
                // request to be left open for this.
                if (
                    !receivedAccountInfo
                    || !sharedApplicationViewModel.chatStreamInitialDownloadComplete
                    || initialized
                ) {
                    val errorMessage =
                        "BlankLoadingFragment failed to navigate away in a timely manner.\n" +
                                "receivedAccountInfo: $receivedAccountInfo\n" +
                                "chatStreamInitialDownloadComplete: ${sharedApplicationViewModel.chatStreamInitialDownloadComplete}\n" +
                                "initialized: $initialized\n" +
                                "notificationsHaveBeenRequested: $notificationsHaveBeenRequested\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )
                }

                if (!receivedAccountInfo) {
                    applicationActivity?.handleGrpcFunctionError(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                } else {
                    //Could mean
                    // 1) chatStreamInitialDownloadComplete (The chat stream did not finish downloading initial info).
                    // 2) !initialized (The navigateToNextWithCondition() never made it past the condition).
                    // 3) notificationsHaveBeenRequested (The notifications are still being requested or they short circuited).
                    navigateToNextNoCondition()
                }
            },
            navigateFromBlankLoadingFragment,
            SystemClock.uptimeMillis() + 60L * 1000L //Semi-arbitrary delay here, needs to be able to run getUserAccountInfoToStoreToViewModel() and potentially retrieveSingleChatRoom().
        )

        //Set the chatRoomUniqueId in order to make sure this fragment only receives info meant for it.
        sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(ChatRoomWithMemberMapDataClass())

        sharedApplicationViewModel.getUserAccountInfoToStoreToViewModel(thisFragmentInstanceID)
    }

    private fun handleApplicationAccountInfoResult() {
        receivedAccountInfo = true
        navigateToNextWithCondition()
    }

    private fun returnChatStreamInitialDownloadsCompleted() {
        navigateToNextWithCondition()
    }

    private fun navigateToNextNoCondition() {
        when (GlobalValues.loginToChatRoomId) {
            NotificationInfo.SEND_TO_CHAT_ROOM_LIST -> { //if navigating to messenger screen
                fragmentSpecificNavigate(R.id.action_blankLoadingFragment_to_messengerScreenFragment)
            }
            "" -> {  //if NOT logging in to a specific chat room
                val sharedPreferences = GlobalValues.applicationContext.getSharedPreferences(
                    getString(R.string.shared_preferences_lets_go_key),
                    Context.MODE_PRIVATE
                )

                val tutorialAlreadyShown =
                    sharedPreferences.getBoolean(
                        getString(R.string.shared_preferences_tutorial_shown_key),
                        false
                    )

                if (tutorialAlreadyShown) {
                    fragmentSpecificNavigate(R.id.action_blankLoadingFragment_to_matchScreenFragment)
                } else {
                    sharedPreferences.edit().putBoolean(
                        getString(R.string.shared_preferences_tutorial_shown_key),
                        true
                    ).apply()

                    fragmentSpecificNavigate(R.id.action_blankLoadingFragment_to_timeFrameTutorialFragment)
                }
            }
            else -> { //if logging in to a specific chat room
                sharedApplicationViewModel.retrieveSingleChatRoom(
                    GlobalValues.loginToChatRoomId,
                    thisFragmentInstanceID,
                    chatRoomMustExist = false
                )
            }
        }
    }

    private fun navigateToNextWithCondition() {

        //once account info has been set up and chat stream has completed initial downloads
        // navigate to next fragment
        //NOTE: navigated needs to be included because returnChatStreamInitialDownloadsCompleted() can be called multiple
        // times even after this fragment is not considered the 'destination' by the NavController
        if (
            receivedAccountInfo
            && sharedApplicationViewModel.chatStreamInitialDownloadComplete
            && !initialized
            && notificationsHaveBeenRequested
        ) {
            initialized = true
            navigateToNextNoCondition()
        }
    }

    private fun handleRetrieveSingleChatRoom() {
        //send a signal to the server to update the chat room member info
        sharedApplicationViewModel.updateChatRoom()
        fragmentSpecificNavigate(R.id.action_blankLoadingFragment_to_messengerScreenFragment)
    }

    private fun fragmentSpecificNavigate(destinationAction: Int) {
        //Remove this callback when a navigation occurs because this fragment/activity could be stopped and so the
        // navigation could be delayed. Don't want the callback running if a navigation is queued.
        errorNavigatingFromBlankLoadingFragment.removeCallbacksAndMessages(
            navigateFromBlankLoadingFragment
        )

        Log.i(
            "resultInfo",
            "fragmentSpecificNavigate() destinationAction: $destinationAction"
        )

        applicationActivity?.navigate(
            R.id.blankLoadingFragment,
            destinationAction
        )
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsBlankLoadingFragment()
    }

    override fun onDestroyView() {

        errorNavigatingFromBlankLoadingFragment.removeCallbacksAndMessages(
            navigateFromBlankLoadingFragment
        )

        sharedApplicationViewModel.applicationAccountInfo.removeObserver(
            applicationAccountInfoResult
        )
        sharedApplicationViewModel.returnSingleChatRoom.removeObserver(returnSingleChatRoomObserver)
        sharedApplicationViewModel.returnChatStreamInitialDownloadsCompleted.removeObserver(
            returnChatStreamInitialDownloadsCompletedObserver
        )
        sharedApplicationViewModel.returnSingleChatRoomNotFound.removeObserver(
            returnSingleChatRoomNotFoundObserver
        )

        sharedApplicationViewModel.blankLoadingFragmentCompleted = true

        applicationActivity = null
        super.onDestroyView()
    }

    private fun checkForNotificationPrivileges() {
        //If the user has turned off notifications or the app does not have permissions, request
        // they turn them back on.
        if (
            !NotificationManagerCompat.from(GlobalValues.applicationContext)
                .areNotificationsEnabled()
        ) { //Notifications are not enabled.
            runDialogForNotificationPrivileges()
        } else { //Notifications are enabled
            notificationsHaveBeenRequested = true
        }
    }

    private fun runDialogForNotificationPrivileges() {
        val fragmentWeakReference = WeakReference(this)

        val alertDialog = MutuallyExclusiveLambdaAlertDialogFragmentWithRoundedCorners(
            getString(R.string.request_post_notification_dialog_title),
            getString(R.string.request_post_notification_dialog_body),
            okButtonAction = { _, _ ->
                Log.i("notification_stuff", "ok pressed")
                if (Build.VERSION.SDK_INT > 32) {
                    val fragmentRef = fragmentWeakReference.get()

                    fragmentRef?.applicationActivity?.registerForRequestPostNotifications {
                        //This lambda will run on the registerForActivityResult return.
                        val innerFragmentRef = fragmentWeakReference.get()

                        innerFragmentRef?.notificationsHaveBeenRequested = true
                        innerFragmentRef?.navigateToNextWithCondition()
                    }
                } else {
                    fragmentWeakReference.get()?.let { fragment ->
                        sendUserToSettingsPage()

                        fragment.notificationsHaveBeenRequested = true
                        fragment.navigateToNextWithCondition()
                    }
                }
            },
            cancelDismissAction = { _, _ ->
                val fragmentRef = fragmentWeakReference.get()

                Log.i("notification_stuff", "canceled")
                fragmentRef?.notificationsHaveBeenRequested = true
                fragmentRef?.navigateToNextWithCondition()
            }
        )

        alertDialog.show(childFragmentManager, "request_notification_permissions")
    }

    private fun sendUserToSettingsPage() {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    GlobalValues.applicationContext.packageName
                )
            } else {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra(
                    "app_package",
                    GlobalValues.applicationContext.packageName
                )
                putExtra(
                    "app_uid",
                    GlobalValues.applicationContext.applicationInfo.uid
                )
            }
        }
        startActivity(intent)
    }
}