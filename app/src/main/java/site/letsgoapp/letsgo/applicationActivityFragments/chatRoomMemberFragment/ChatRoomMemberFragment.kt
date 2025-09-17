package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomMemberFragment

import account_state.AccountState
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import categorytimeframe.CategoryTimeFrame
import report_enums.ReportMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databinding.FragmentChatRoomMemberBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnUpdatedOtherUserDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.UpdateChatRoomInfoResultsDataHolder
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import type_of_chat_message.TypeOfChatMessageOuterClass
import user_account_type.UserAccountTypeOuterClass.UserAccountType

class ChatRoomMemberFragment : Fragment() {

    private var _binding: FragmentChatRoomMemberBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private var userInfoCardLogic: UserInfoCardLogic? = null

    private var deviceScreenWidth: Int = 0

    private var matchListItemDateTimeTextViewWidth =
        -1 //used in CardStackAdapter as the textView width for dates
    private var matchListItemDateTimeTextViewHeight =
        -1 //used in CardStackAdapter as the textView height for dates

    private lateinit var returnUpdatedOtherUserObserver: Observer<EventWrapper<ReturnUpdatedOtherUserDataHolder>>
    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnChatRoomInfoUpdatedObserverData: Observer<EventWrapperWithKeyString<UpdateChatRoomInfoResultsDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>
    private lateinit var returnBlockReportChatRoomResultObserver: Observer<EventWrapperWithKeyString<BlockAndReportChatRoomResultsHolder>>

    private var applicationActivity: AppActivity? = null

    private var memberDataEntity: OtherUsersDataEntity? = null

    private val navigateToMessengerFragmentResourceId =
        R.id.action_chatRoomMemberFragment_to_messengerScreenFragment

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        // Inflate the layout for this fragment
        _binding = FragmentChatRoomMemberBinding.inflate(inflater, container, false)

        applicationActivity = requireActivity() as AppActivity

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()) {
            applicationActivity?.navigate(
                R.id.chatRoomMemberFragment,
                navigateToMessengerFragmentResourceId
            )
            return
        }

        deviceScreenWidth = getScreenWidth(requireActivity())

        //make sure this is BEFORE the userInfoCardLogic has been initialized, that way if there is a value
        // sitting inside the live data it will be set to handled
        returnUpdatedOtherUserObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleUpdateOtherUser(result)
            }
        }

        sharedApplicationViewModel.returnUpdatedChatRoomUser.observe(
            viewLifecycleOwner,
            returnUpdatedOtherUserObserver
        )

        returnChatRoomInfoUpdatedObserverData = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleChatRoomInfoUpdated(it)
            }
        }

        sharedApplicationViewModel.returnChatRoomInfoUpdatedData.observe(
            viewLifecycleOwner,
            returnChatRoomInfoUpdatedObserverData
        )

        returnLeaveChatRoomResultObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled()
            result?.let {
                applicationActivity?.handleLeaveChatRoom(it, navigateToMessengerFragmentResourceId)
            }
        }

        sharedApplicationViewModel.returnLeaveChatRoomResult.observe(
            viewLifecycleOwner,
            returnLeaveChatRoomResultObserver
        )

        returnJoinedLeftChatRoomObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                applicationActivity?.handleJoinedLeftChatRoomReturn(
                    it,
                    navigateToMessengerFragmentResourceId
                )
            }
        }

        sharedApplicationViewModel.returnJoinedLeftChatRoom.observe(
            viewLifecycleOwner,
            returnJoinedLeftChatRoomObserver
        )

        returnKickedBannedFromChatRoomObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                applicationActivity?.handleKickedBanned(it, navigateToMessengerFragmentResourceId)
            }
        }

        sharedApplicationViewModel.returnKickedBannedFromChatRoom.observe(
            viewLifecycleOwner,
            returnKickedBannedFromChatRoomObserver
        )

        returnBlockReportChatRoomResultObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                memberDataEntity?.let { dataEntity ->

                    if (it.accountBlocked && it.accountOID == dataEntity.accountOID) { //if account was blocked and if the viewed account was the one blocked

                        if (it.unMatch) { //if this was an un match request

                            //this should never really be sent
                            applicationActivity?.navigate(
                                R.id.chatRoomMemberFragment,
                                navigateToMessengerFragmentResourceId
                            )
                        } else { //if this is just a standard block return not un matching
                            applicationActivity?.navigate(
                                R.id.chatRoomMemberFragment,
                                R.id.action_chatRoomMemberFragment_to_chatRoomInfoFragment
                            )
                        }
                    }
                }
            }
        }

        sharedApplicationViewModel.returnBlockReportChatRoomResult.observe(
            viewLifecycleOwner,
            returnBlockReportChatRoomResultObserver
        )

        //add this view to the root view temporarily to calculate the size the textView needs to be
        val tempMatchListItem =
            View.inflate(context, R.layout.view_user_info_card_time_frame, null) as LinearLayout
        binding.root.addView(tempMatchListItem)
        val tempDateItem =
            tempMatchListItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStartTimeTextView)

        val widthMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(
                getScreenWidth(requireActivity()),
                View.MeasureSpec.AT_MOST
            )
        val heightMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        tempDateItem.measure(widthMeasureSpec, heightMeasureSpec)
        matchListItemDateTimeTextViewWidth = tempDateItem.measuredWidth
        matchListItemDateTimeTextViewHeight = tempDateItem.measuredHeight
        binding.root.removeView(tempMatchListItem)

        //NOTE: I can probably just do (screenWidth-root.width) + layout.width, but WHY isn't it working?
        userInfoCardLogic = UserInfoCardLogic(
            requireContext().applicationContext,
            GlideApp.with(this),
            true,
            binding.chatRoomMemberFragmentMatchItem.root,
            deviceScreenWidth,
            sharedApplicationViewModel.userAge,
            sharedApplicationViewModel.categories,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            childFragmentManager,
            requestUpdateSingleUser = { userAccountOID ->
                sharedApplicationViewModel.updateSingleChatRoomMemberInfo(
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

        val memberIndex =
            arguments?.let {
                val args = ChatRoomMemberFragmentArgs.fromBundle(it)
                args.memberIndex
            } ?: -1

        val otherUserInfo =
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                memberIndex
            )

        val inviteLambda: (() -> Unit)? = if (
            otherUserInfo == null
            || otherUserInfo.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number
        ) {
            null
        } else {
            {
                memberDataEntity?.let {
                    sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserOid =
                        it.accountOID
                    sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName =
                        it.name
                    applicationActivity?.navigate(
                        R.id.chatRoomMemberFragment,
                        R.id.action_chatRoomMemberFragment_to_selectChatRoomForInviteFragment
                    )
                }
            }
        }

        applicationActivity?.setupActivityMenuBars?.addMenuProviderWithMenuItems(
            viewLifecycleOwner,
            blockReportLambda = {
                memberDataEntity?.let { otherUser ->
                    if (applicationActivity == null) {

                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.chat_room_member_fragment_error_blocking_user,
                            Toast.LENGTH_SHORT
                        ).show()

                        val errorMessage =
                            "Error applicationActivity was not initialized when blockAndReport was selected from the options menu.\n"

                        storeErrorChatRoomMemberFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                    } else {
                        applicationActivity?.blockAndReportUserFromChatRoom(
                            childFragmentManager,
                            otherUser.accountOID,
                            false,
                            ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_MEMBER,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    }
                }
            },
            inviteLambda = inviteLambda,
            fragmentInstanceID = thisFragmentInstanceID,
        )

        extractAndDisplayUserInfoCard(memberIndex)
    }

    private fun handleUpdateOtherUser(result: ReturnUpdatedOtherUserDataHolder) {
        if (
            userInfoCardLogic != null &&
            (result.otherUser.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                    || result.otherUser.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN)
            && userInfoCardLogic?.initializationIfMatchingAccountOID(
                result.otherUser.otherUsersDataEntity,
                true,
                getEventActivities(result.otherUser.otherUsersDataEntity.name),
            ) == true
        ) {
            memberDataEntity = result.otherUser.otherUsersDataEntity
        }

        setNumberOfMembersInChatRoom()
    }

    private fun getEventActivities(otherUserName: String):
            MutableList<CategoryTimeFrame.CategoryActivityMessage> {
        return if (otherUserName == GlobalValues.server_imported_values.adminFirstName) {
            val chatRoom = sharedApplicationViewModel.chatRoomContainer.chatRoom
            val eventMember = chatRoom.chatRoomMembers.getFromMap(chatRoom.eventId)
            if (eventMember != null) {
                eventMember.activities
            } else {
                val errorMessage =
                    "Event did not exist as a member when extracting from event id\n" +
                            "chatRoomId: ${chatRoom.chatRoomId}\n" +
                            "eventId: ${chatRoom.eventId}\n" +
                            "chatRoomMembers: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.logMembers()}\n"

                storeErrorChatRoomMemberFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    private fun extractAndDisplayUserInfoCard(itemIndex: Int) {

        val otherUserInfo =
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                itemIndex
            )

        if (otherUserInfo != null) {
            memberDataEntity = otherUserInfo.otherUsersDataEntity

            userInfoCardLogic?.initializeInfo(
                otherUserInfo.otherUsersDataEntity,
                true,
                getEventActivities(otherUserInfo.otherUsersDataEntity.name),
            )
        } else {
            val errorMessage = "Error displaying user info card in chat room member fragment.\n" +
                    "itemIndex: $itemIndex\n" +
                    "chatRoomMembers: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.logMembers()}\n"

            storeErrorChatRoomMemberFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        }
    }

    private fun handleChatRoomInfoUpdated(result: UpdateChatRoomInfoResultsDataHolder) {

        _binding?.let { //if the view still exists

            if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId == result.message.chatRoomId
                && TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(result.message.messageType) == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE
            ) { //if the chat room matches the passed chat room
                applicationActivity?.setupActivityMenuBars?.setTopToolbarChatRoomName()
            }
        }
    }

    private fun setNumberOfMembersInChatRoom() {
        applicationActivity?.setNumberOfMembersInChatRoom()
    }

    private fun storeErrorChatRoomMemberFragment(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String
    ) {
        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            passedErrMsg
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
        applicationActivity?.setupActivityMenuBars?.setupToolbarsChatRoomFragments()
    }

    override fun onDestroyView() {
        //this fragment doesn't explicitly need to use hideMenus() to avoid leaking inside onClickListeners, however
        // because it is connected to the top menu items, it is done to be safe
        applicationActivity?.hideMenus()

        _binding = null
        userInfoCardLogic = null
        memberDataEntity = null
        applicationActivity = null

        super.onDestroyView()
    }
}