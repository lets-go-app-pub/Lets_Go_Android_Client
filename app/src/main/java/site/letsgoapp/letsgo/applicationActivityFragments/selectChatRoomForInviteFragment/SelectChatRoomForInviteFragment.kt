package site.letsgoapp.letsgo.applicationActivityFragments.selectChatRoomForInviteFragment

import account_state.AccountState
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment.ChatRoomsListFragment
import site.letsgoapp.letsgo.databinding.FragmentSelectChatRoomForInviteBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.utilities.*

class SelectChatRoomForInviteFragment : Fragment() {

    private var _binding: FragmentSelectChatRoomForInviteBinding? = null
    private val binding get() = _binding!!

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var thisFragmentInstanceID: String

    //sends info to selectChatRoomForInviteFragment (receives back chat room id)
    private lateinit var chatRoomSelectedForInviteObserver: Observer<EventWrapperWithKeyString<ChatRoomBasicInfoObject>>

    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>

    private val navigateToMessengerFragmentResourceId =
        R.id.action_selectChatRoomForInviteFragment_to_messengerScreenFragment

    private var applicationActivity: AppActivity? = null

    private var selectedChatRoom = false

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentSelectChatRoomForInviteBinding.inflate(inflater, container, false)

        applicationActivity = requireActivity() as AppActivity

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()) {
            applicationActivity?.navigate(
                R.id.selectChatRoomForInviteFragment,
                navigateToMessengerFragmentResourceId
            )
            return
        }

        val args = Bundle()
        args.putString(
            getString(R.string.chat_room_list_fragment_parent_instance_id_key),
            thisFragmentInstanceID
        )
        args.putString(
            getString(R.string.chat_room_list_fragment_parent_chat_room_unique_id_key),
            sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId
        )
        args.putInt(
            getString(R.string.chat_room_list_fragment_type_of_parent_key),
            ChatRoomListCalledFrom.INVITE_FRAGMENT.ordinal
        )

        //NOTE: The ChatRoomsListFragment from the xml will not have onCreateView() called until this
        // current fragment onViewCreated() has been completed.
        val chatRoomListFrag = binding.selectChatRoomForInviteChatRoomListFragmentContainerView.getFragment<ChatRoomsListFragment>()
        chatRoomListFrag.arguments = args

        chatRoomSelectedForInviteObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let { chatRoomBasicInfo ->

                //Only allow one chat room to be selected at a time.
                if (!selectedChatRoom) {
                    selectedChatRoom = true

                    CoroutineScope(Main).launch {

                        val userStateInsideChatRoom =
                            sharedApplicationViewModel.checkUserAccountStateInsideChatRoom(
                                sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserOid,
                                chatRoomBasicInfo.chatRoomId
                            )

                        when (userStateInsideChatRoom) {
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM,
                            null -> { //these 2 mean user is not inside chat room
                                sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.sendInviteMessage =
                                    true
                                sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageChatRoomBasicInfo =
                                    chatRoomBasicInfo

                                applicationActivity?.navigate(
                                    R.id.selectChatRoomForInviteFragment,
                                    R.id.action_selectChatRoomForInviteFragment_to_chatRoomFragment
                                )
                            }
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM -> { //these 2 mean the user is already inside the chat room
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    getString(
                                        R.string.select_chat_room_for_invite_user_exists_inside_chat_room,
                                        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED -> { //account is not eligible for this
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    getString(
                                        R.string.select_chat_room_for_invite_user_exists_not_eligible_chat_room,
                                        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_SUPER_ADMIN,
                            AccountState.AccountStateInChatRoom.UNRECOGNIZED -> {
                                val errorMessage =
                                    "When attempting to invite user to chat room, received an invalid AccountState.\n" +
                                            "userStateInsideChatRoom: $userStateInsideChatRoom\n" +
                                            "inviteMessageObject: ${sharedApplicationViewModel.chatRoomContainer.inviteMessageObject}\n"

                                storeError(
                                    errorMessage,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors()
                                )

                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    getString(
                                        R.string.select_chat_room_for_invite_user_exists_not_eligible_chat_room,
                                        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        selectedChatRoom = false
                    }
                }
            }
        }

        chatRoomListFrag.chatRoomSelectedForInvite.observe(
            viewLifecycleOwner,
            chatRoomSelectedForInviteObserver
        )

        val invitedMemberPossessiveName =
            when {
                sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName.isEmpty() -> { //if no user name saved
                    val errorMessage =
                        "Name was not set before navigating to SelectChatRoomForInviteFragment.\n"

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    ""
                }
                sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName.last() != 's' -> { //if user name does not end with 's'
                    sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName + "s'"
                }
                else -> { //if user name ends with 's'
                    sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName + "'"
                }
            }

        binding.selectChatRoomForInviteHeaderTextView.text =
            getString(R.string.select_chat_room_for_invite_title, invitedMemberPossessiveName)

    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsSelectChatRoomForInviteFragment(viewLifecycleOwner)
    }

    private fun storeError(
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

    override fun onDestroyView() {
        _binding = null
        applicationActivity = null
        super.onDestroyView()
    }
}