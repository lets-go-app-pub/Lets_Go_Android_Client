package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.Direction
import com.yuyakaido.android.cardstackview.Duration
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting
import report_enums.ReportMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databinding.FragmentMatchScreenBinding
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnMatchRemovedOnJoinChatRomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.findMatchesObject.FindMatchesServerSuccessTypeDataHolder
import site.letsgoapp.letsgo.standAloneObjects.findMatchesObject.FindMatchesServerSuccessTypeEnum
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import java.util.concurrent.atomic.AtomicBoolean

class MatchScreenFragment : Fragment() {

    companion object {
        const val SAFE_CLICK_LISTENER_DELAY = 400
    }

    private var _binding: FragmentMatchScreenBinding? = null
    private val binding get() = _binding!!

    private val swipeInProgress = AtomicBoolean(false)

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels()

    private lateinit var thisFragmentInstanceID: String
    private lateinit var thisFragmentChatRoomUniqueID: String

    private lateinit var matchesFromDatabaseReturnValueObserver: Observer<EventWrapperWithKeyString<Pair<OtherUsersDataEntity, MatchesDataEntity>>>
    private lateinit var findMatchesServerSuccessTypeObserver: Observer<EventWrapper<FindMatchesServerSuccessTypeDataHolder>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnUpdatedMatchUser: Observer<EventWrapper<OtherUsersDataEntity>>
    private lateinit var matchRemovedOnJoinChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnMatchRemovedOnJoinChatRomDataHolder>>

    private var cardStackLayoutManager: CardStackLayoutManager? = null
    private var cardStackAdapter: CardStackAdapter? = null

    private var widthMeasureSpec = 0
    private var heightMeasureSpec = 0
    private var matchListItemDateTimeTextViewWidth =
        -1 //Used in CardStackAdapter as the textView width for dates.
    private var matchListItemDateTimeTextViewHeight =
        -1 //Used in CardStackAdapter as the textView height for dates.

    private var deviceScreenWidth = 0

    //Declare timer
    private var cTimer: CountDownTimer? = null

    //This will essentially be a copy of the list inside of sharedApplicationViewModel.findMatchesObject.matches, it is
    // separate because there were concurrency (it was being accessed on multiple threads) problems with directly injecting
    // the list into the cardStackAdapter.
    //Only modify this object on the Main thread.
    private var matchesCopy = mutableListOf<Pair<OtherUsersDataEntity, MatchesDataEntity>>()

    private val thumbsUpCardStackSettings = SwipeAnimationSetting.Builder()
        .setDirection(Direction.Right)
        .setDuration(Duration.Normal.duration)
        .setInterpolator(AccelerateInterpolator())
        .build()

    private val thumbsDownCardStackSettings = SwipeAnimationSetting.Builder()
        .setDirection(Direction.Left)
        .setDuration(Duration.Normal.duration)
        .setInterpolator(AccelerateInterpolator())
        .build()

    private var applicationActivity: AppActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment.
        _binding = FragmentMatchScreenBinding.inflate(
            inflater,
            container,
            false
        )

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        Log.i("whereAmI", "MatchScreenFragment")

        //This fragment can be navigated to from the ChatRoomFragment if the back button is used after
        // a match was found. Set the chatRoomUniqueId in order to make sure this fragment only
        // receives info meant for it.
        sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(ChatRoomWithMemberMapDataClass())
        thisFragmentChatRoomUniqueID = sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId

        deviceScreenWidth = getScreenWidth(requireActivity())

        widthMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(deviceScreenWidth, View.MeasureSpec.AT_MOST)
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        sharedApplicationViewModel.matchesFragmentId = thisFragmentInstanceID
        sharedApplicationViewModel.findMatchesObject.setMatchesFragmentId(thisFragmentInstanceID)

        //Initialize matchesCopy to the same as sharedApplicationViewModel.findMatchesObject.matches.
        sharedApplicationViewModel.findMatchesObject.makePassedListShallowCopyOfMatches(matchesCopy)

        applicationActivity = requireActivity() as AppActivity

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.i("ApplicationActStuff", "MatchScreenFragment onViewCreated()")

        matchesFromDatabaseReturnValueObserver = Observer { result ->
            val extractedResult = result.getContentIfNotHandled(thisFragmentInstanceID)
            extractedResult?.let {
                handleMatchesFromDatabase(it)
            }
        }

        findMatchesServerSuccessTypeObserver = Observer { result ->
            val extractedResult = result.getContentIfNotHandled()
            extractedResult?.let {
                handleFindMatchesSuccessType(it)
            }
        }

        returnJoinedLeftChatRoomObserver = Observer { result ->
            val extractedResult = result.getContentIfNotHandled(thisFragmentChatRoomUniqueID)
            extractedResult?.let {
                handleJoinedLeftChatRoom(it)
            }
        }

        sharedApplicationViewModel.returnJoinedLeftChatRoom.observe(
            viewLifecycleOwner,
            returnJoinedLeftChatRoomObserver
        )

        //add this view to the root view temporarily to calculate the size the textView needs to be
        val tempMatchListItem =
            View.inflate(context, R.layout.view_user_info_card_time_frame, null) as LinearLayout
        binding.root.addView(tempMatchListItem)
        val tempDateItem =
            tempMatchListItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStartTimeTextView)
        tempDateItem.measure(widthMeasureSpec, heightMeasureSpec)
        matchListItemDateTimeTextViewWidth = tempDateItem.measuredWidth
        matchListItemDateTimeTextViewHeight = tempDateItem.measuredHeight
        binding.root.removeView(tempMatchListItem)

        sharedApplicationViewModel.findMatchesObject.matchesFromDatabaseReturnValue.observe(
            viewLifecycleOwner,
            matchesFromDatabaseReturnValueObserver
        )

        sharedApplicationViewModel.findMatchesObject.findMatchesServerSuccessType.observe(
            viewLifecycleOwner,
            findMatchesServerSuccessTypeObserver
        )

        returnUpdatedMatchUser = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleUpdateOtherUser(result)
            }
        }

        //make sure this is BEFORE the card stack layout manager has been initialized, that way if there is a value
        // sitting inside the live data it will be set to handled
        sharedApplicationViewModel.returnUpdatedMatchUser.observe(
            viewLifecycleOwner,
            returnUpdatedMatchUser
        )

        matchRemovedOnJoinChatRoomObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleMatchRemovedOnJoinChatRoom(result)
            }
        }

        sharedApplicationViewModel.matchRemovedOnJoinChatRoom.observe(
            viewLifecycleOwner,
            matchRemovedOnJoinChatRoomObserver
        )

        cardStackLayoutManager = CardStackLayoutManager(context, CardStackListenerImpl(
            {
                if (swipeInProgress.compareAndSet(false, true)) {
                    handleSwipes(ReportMessages.ResponseType.USER_MATCH_OPTION_YES)
                }
            },
            {
                if (swipeInProgress.compareAndSet(false, true)) {
                    handleSwipes(ReportMessages.ResponseType.USER_MATCH_OPTION_NO)
                }
            }
        ))

        cardStackAdapter = CardStackAdapter(
            requireContext(),
            GlideApp.with(this),
            matchesCopy,
            deviceScreenWidth,
            sharedApplicationViewModel.userAge,
            sharedApplicationViewModel.categories,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            childFragmentManager,
            requestUpdateSingleUser = { userAccountOID ->
                sharedApplicationViewModel.updateSingleMatchMemberInfo(
                    userAccountOID
                )
            },
            updateOtherUserToObserved = { userAccountOID ->
                sharedApplicationViewModel.updateOtherUserObservedTime(
                    userAccountOID
                )
            },
            errorStore
        )

        cardStackLayoutManager?.setSwipeThreshold(0.3f)
        cardStackLayoutManager?.setCanScrollVertical(false)
        cardStackLayoutManager?.setVisibleCount(2)
        cardStackLayoutManager?.setDirections(Direction.HORIZONTAL)
        binding.userMatchOptionsCardStackView.layoutManager = cardStackLayoutManager
        binding.userMatchOptionsCardStackView.adapter = cardStackAdapter
        binding.userMatchOptionsCardStackView.itemAnimator = DefaultItemAnimator()

        binding.matchLayoutThumbsUpImageView.setSafeOnClickListener(SAFE_CLICK_LISTENER_DELAY) {
            runSwipe(true)
        }

        binding.matchLayoutThumbsDownImageView.setSafeOnClickListener(SAFE_CLICK_LISTENER_DELAY) {
            runSwipe(false)
        }

        binding.matchLayoutBlockAndReportTextView.setSafeOnClickListener(SAFE_CLICK_LISTENER_DELAY) {
            blockAndReport()
        }

        Log.i(
            "mainHandlerMatch",
            "MatchScreenFragment onViewCreated() doNotRunOnCreateViewInMatchScreenFragment: ${sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment}"
        )
        //do not run the request in specific cases
        if (!sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment) {
            sharedApplicationViewModel.requestMatchFromDatabase()
        } else {
            sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment = false
        }
    }

    private fun handleFindMatchesSuccessType(result: FindMatchesServerSuccessTypeDataHolder) {

        //Reset timer so it cannot interfere with whatever new is going to be set inside of
        // userMatchOptionsBottomLayerTextView.
        cTimer?.cancel()
        cTimer = null

        when (result.successTypeEnum) {
            FindMatchesServerSuccessTypeEnum.SUCCESSFULLY_EXTRACTED -> {
                binding.userMatchOptionsBottomLayerTextView.text = null
                displayTextOrProgressBar(true)
            }
            FindMatchesServerSuccessTypeEnum.NO_MATCHES_FOUND,
            FindMatchesServerSuccessTypeEnum.MATCH_ALGORITHM_ON_COOL_DOWN -> {
                displayTextOrProgressBar(false)
                binding.userMatchOptionsBottomLayerTextView.text =
                    getString(R.string.match_screen_fragment_no_matches_found)
            }
            FindMatchesServerSuccessTypeEnum.NO_SWIPES_REMAINING -> {
                displayTextOrProgressBar(false)

                Log.i("same_one_z", "result.timeCoolDownEnds: ${result.timeCoolDownEnds}")
                setupNoSwipesRemainingCoolDown(result.timeCoolDownEnds)
                //binding.userMatchOptionsBottomLayerTextView.text = getString(R.string.match_screen_fragment_swipes_used_up_no_timer)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsMatchScreenFragment()
    }

    private fun displayTextOrProgressBar(displayProgressBar: Boolean) {
        if (displayProgressBar) {
            binding.userMatchOptionsBottomLayerTextView.visibility = View.GONE
            binding.userMatchOptionsBottomLayerProgressBar.visibility = View.VISIBLE
        } else {
            binding.userMatchOptionsBottomLayerTextView.visibility = View.VISIBLE
            binding.userMatchOptionsBottomLayerProgressBar.visibility = View.GONE
        }
    }

    private fun generateSwipesUsedUpText(timeRemainingInMillis: Long): String {
        return getString(
            R.string.match_screen_fragment_swipes_used_up_show_time_remaining,
            calculateTimeUntilSwipesBackString(timeRemainingInMillis)
        )
    }

    private fun setupNoSwipesRemainingCoolDown(timeCoolDownEndsInMillis: Long) {

        val timeBeforeOffCoolDownInMillis = timeCoolDownEndsInMillis - SystemClock.elapsedRealtime()

        if (timeBeforeOffCoolDownInMillis > 0) {
            cTimer?.cancel()

            binding.userMatchOptionsBottomLayerTextView.text =
                generateSwipesUsedUpText(timeBeforeOffCoolDownInMillis)

            //NOTE: The user view of the timer has potential to skip occasionally if for example NO_SWIPES_REMAINING is returned
            // WHILE this timer is currently running. With latency there is a chance that a second or so could be lost (although
            // in most cases a consistent latency between the user and the server should make the cool downs returned the same).
            cTimer = object : CountDownTimer(timeBeforeOffCoolDownInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.userMatchOptionsBottomLayerTextView.text =
                        generateSwipesUsedUpText(millisUntilFinished)
                }

                override fun onFinish() {
                    binding.userMatchOptionsBottomLayerTextView.text =
                        getString(R.string.match_screen_fragment_swipes_used_up_no_timer)
                }
            }

            cTimer?.start()
        } else {
            val errorMessage =
                "timeBeforeOffCoolDownInMillis variable inside match fragment should always be greater than 0.\n"

            storeErrorMatchScreenFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            binding.userMatchOptionsBottomLayerTextView.text =
                getString(R.string.match_screen_fragment_swipes_used_up_no_timer)
        }
    }

    private fun calculateTimeUntilSwipesBackString(timeBeforeOffCoolDownInMillis: Long): String {
        val timeBeforeOffCoolDownInSeconds = timeBeforeOffCoolDownInMillis / 1000

        val hours =
            timeBeforeOffCoolDownInSeconds / (60 * 60)
        val minutes =
            (timeBeforeOffCoolDownInSeconds % (60 * 60)) / 60
        val seconds =
            timeBeforeOffCoolDownInSeconds % 60

        var timeString = ""

        if (hours > 0) {
            timeString += "${hours}h: "
        }
        if (minutes > 0) {
            timeString += "${minutes}m: "
        }
        timeString += "${seconds}s: "

        return timeString
    }

    private fun runSwipe(thumbsUpPassed: Boolean) {
        if (matchesCopy.isNotEmpty()) {
            cardStackLayoutManager?.let {
                val swipeAnimationSetting =
                    if (thumbsUpPassed) thumbsUpCardStackSettings else thumbsDownCardStackSettings

                it.setSwipeAnimationSetting(swipeAnimationSetting)

                binding.userMatchOptionsCardStackView.swipe()
            }
        }
    }

    private fun blockAndReport() {

        if (swipeInProgress.compareAndSet(false, true)
            && matchesCopy.isNotEmpty()
        ) {

            showBlockAndReportDialog(
                requireContext(),
                childFragmentManager,
                onDismissAction = { swipeInProgress.set(false) },
                languageSelected = {
                    handleSwipes(
                        ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT,
                        ReportMessages.ReportReason.REPORT_REASON_LANGUAGE
                    )
                },
                inappropriatePictureSelected = {
                    handleSwipes(
                        ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT,
                        ReportMessages.ReportReason.REPORT_REASON_INAPPROPRIATE_PICTURE
                    )
                },
                advertisingSelected = {
                    handleSwipes(
                        ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT,
                        ReportMessages.ReportReason.REPORT_REASON_ADVERTISING
                    )
                },
                otherSelected = { otherReportInfo ->
                    handleSwipes(
                        ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT,
                        ReportMessages.ReportReason.REPORT_REASON_OTHER,
                        otherReportInfo
                    )
                }
            )

        }
    }

    private fun handleMatchesFromDatabase(element: Pair<OtherUsersDataEntity, MatchesDataEntity>) {

        Log.i(
            "MatchScreenFrag",
            "size: ${matchesCopy.size}"
        )
        for (m in matchesCopy) {
            Log.i("MatchScreenFrag", "$m")
        }

        cardStackAdapter?.let {
            matchesCopy.add(element)
            it.notifyItemInserted(matchesCopy.lastIndex)
        }
    }

    private fun handleJoinedLeftChatRoom(chatRoomAndStatus: ReturnJoinedLeftChatRoomDataHolder) {

        //this will be called when a new match is made
        when (chatRoomAndStatus.chatRoomUpdateMadeEnum) {
            ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH -> {
                if (chatRoomAndStatus.chatRoomWithMemberMap.chatRoomMembers.size() == 1) {
                    //navigate to show the user the match that was just made
                    sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(chatRoomAndStatus.chatRoomWithMemberMap)
                    applicationActivity?.navigate(
                        R.id.matchScreenFragment,
                        R.id.action_matchScreenFragment_to_matchMadeScreenFragment
                    )
                } else {
                    val errorMessage =
                        "Match fragment received a match that did not have exactly 1 member inside of it.\n"

                    storeErrorMatchScreenFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        showChatRoom = true
                    )

                    //do not navigate, there should be exactly 1 user inside the 'match'
                }
            }
            ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED -> {
                if (chatRoomAndStatus.chatRoomWithMemberMap.eventId.isValidMongoDBOID()) {
                    //navigate to show the user the match that was just made
                    sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(chatRoomAndStatus.chatRoomWithMemberMap)
                    applicationActivity?.navigate(
                        R.id.matchScreenFragment,
                        R.id.action_matchScreenFragment_to_matchMadeScreenFragment
                    )
                } else {
                    val errorMessage =
                        "Match fragment received an event that did not have a valid oid.\n"

                    storeErrorMatchScreenFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        showChatRoom = true
                    )
                }
            }
            ChatRoomUpdateMade.CHAT_ROOM_JOINED,
            ChatRoomUpdateMade.CHAT_ROOM_LEFT,
            ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED -> {
            }
        }
    }

    private fun handleSwipes(
        responseType: ReportMessages.ResponseType,
        reportReason: ReportMessages.ReportReason = ReportMessages.ReportReason.REPORT_REASON_UNKNOWN_DEFAULT,
        otherReportInfo: String = "",
    ) {

        cardStackAdapter?.let {
            val matchReference = matchesCopy[0]

            matchesCopy.removeAt(0)

            it.notifyItemRemoved(0)

            sharedApplicationViewModel.userRespondedToMatch(
                matchReference,
                responseType,
                reportReason,
                otherReportInfo
            )
        }

        swipeInProgress.set(false)
    }

    private fun handleUpdateOtherUser(result: OtherUsersDataEntity) {

        cardStackAdapter?.let {
            for (i in matchesCopy.indices) {
                if (matchesCopy[i].first.accountOID == result.accountOID) {
                    matchesCopy[i] = Pair(
                        result,
                        matchesCopy[i].second
                    )
                    it.notifyItemChanged(i)

                    //NOTE: the findMatchesObject does not need to be updated here, it is checked when the result passes
                    // through the SharedApplicationViewModel

                    break
                }
            }
        }
    }

    private fun handleMatchRemovedOnJoinChatRoom(result: ReturnMatchRemovedOnJoinChatRomDataHolder) {

        cardStackAdapter?.let {
            for (i in matchesCopy.indices) {
                if (matchesCopy[i].first.accountOID == result.matchAccountOid) {
                    matchesCopy.removeAt(i)

                    it.notifyItemRemoved(i)
                    break
                }
            }
        }
    }

    private fun storeErrorMatchScreenFragment(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String,
        matchItem: Pair<OtherUsersDataEntity, MatchesDataEntity>? = null,
        showChatRoom: Boolean = false
    ) {
        val errorMessage =
            when {
                matchItem != null -> {
                    passedErrMsg + "\n" +
                            "OtherUsersDataEntity\n" +
                            "accountOID: ${matchItem.first.accountOID}\n" +
                            "thumbnailPath: ${matchItem.first.thumbnailPath}\n" +
                            "thumbnailIndexNumber: ${matchItem.first.thumbnailIndexNumber}\n" +
                            "thumbnailLastTimeUpdated: ${matchItem.first.thumbnailLastTimeUpdated}\n" +
                            "objectsRequiringInfo: ${matchItem.first.objectsRequiringInfo}\n" +
                            "chatRoomObjects: ${matchItem.first.chatRoomObjects}\n" +
                            "distance: ${matchItem.first.distance}\n" +
                            "timestampUserInfoLastUpdated: ${matchItem.first.timestampUserInfoLastUpdated}\n" +
                            "timestampUserInfoLastObserved: ${matchItem.first.timestampUserInfoLastObserved}\n" +
                            "pictures: ${matchItem.first.pictures}\n" +
                            "picturesUpdateAttemptedTimestamp: ${matchItem.first.picturesUpdateAttemptedTimestamp}\n" +
                            "name: ${matchItem.first.name}\n" +
                            "age: ${matchItem.first.age}\n" +
                            "gender: ${matchItem.first.gender}\n" +
                            "cityName: ${matchItem.first.cityName}\n" +
                            "bio: ${matchItem.first.bio}\n" +
                            "activities: ${matchItem.first.activities}\n" +
                            "accountOID: ${matchItem.second.accountOID}\n" +
                            "pointValue: ${matchItem.second.pointValue}\n" +
                            "MatchesDataEntity\n" +
                            "expirationTime: ${matchItem.second.expirationTime}\n" +
                            "otherUserMatched: ${matchItem.second.otherUserMatched}\n" +
                            "swipesRemaining: ${matchItem.second.swipesRemaining}\n" +
                            "swipesTimeBeforeReset: ${matchItem.second.swipesTimeBeforeReset}\n" +
                            "matchIndex: ${matchItem.second.matchIndex}\n"
                }
                showChatRoom -> {
                    passedErrMsg + "\n" +
                            "chatRoomId: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId}\n" +
                            "chatRoomName: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomName}\n" +
                            "chatRoomPassword: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomPassword}\n" +
                            "notificationsEnabled: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.notificationsEnabled}\n" +
                            "accountState: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.accountState}\n" +
                            "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n" +
                            "timeJoined: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.timeJoined}\n" +
                            "matchingChatRoomOID: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID}\n" +
                            "chatRoomLastObservedTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastObservedTime}\n" +
                            "userLastActivityTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.userLastActivityTime}\n" +
                            "chatRoomLastActivityTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastActivityTime}\n" +
                            "lastTimeUpdated: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.lastTimeUpdated}\n" +
                            "finalMessage: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.finalMessage}\n" +
                            "finalPictureMessage: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.finalPictureMessage}\n" +
                            "displayChatRoom: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.displayChatRoom}\n" +
                            "showLoading: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.showLoading}\n"
                }
                else -> {
                    passedErrMsg
                }
            }

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorMessage
        )
    }

    override fun onDestroyView() {
        _binding = null
        cardStackAdapter = null
        cardStackLayoutManager = null
        applicationActivity = null
        cTimer?.cancel()
        cTimer = null
        matchesCopy.clear()

        super.onDestroyView()
    }
}

