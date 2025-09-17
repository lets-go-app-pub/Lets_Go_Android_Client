package site.letsgoapp.letsgo.applicationActivityFragments.messengerScreenFragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import grpc_chat_commands.ChatRoomCommands
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment.ChatRoomsListFragment
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databinding.FragmentPrimaryMessengerScreenBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnMatchMadeRangeInsertedDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnMatchMadeRemovedDataHolder
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import java.util.*

class MessengerScreenFragment : Fragment() {

    companion object {
        //Timeout of loading dialog when using join, leave or un match.
        const val LOADING_DIALOG_TIMEOUT_IN_MS = 15000L

        //NOTE: It is possible that join chat room could still be loading when this dialog is automatically closed, however
        // over a minute (2*deadline_time) of load is a long time to make the user wait.
        const val LOADING_DIALOG_TIMEOUT_JOIN_CHAT_ROOM_IN_MS = GlobalValues.gRPC_Join_Chat_Room_Deadline_Time
    }

    private var _binding: FragmentPrimaryMessengerScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private lateinit var thisFragmentChatRoomUniqueID: String
    private var applicationActivity: AppActivity? = null

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    //called before navigating to a chat room when user clicks the image of a chat room
    //saves the chat room object to chatRoom in sharedApplicationViewModel
    private lateinit var returnSingleChatRoomObserver: Observer<EventWrapperWithKeyString<Unit>>

    //called when user creates a chat room, will navigate to the chat room
    //saves the chat room object to chatRoom in sharedApplicationViewModel
    private lateinit var returnCreatedChatRoomObserver: Observer<EventWrapperWithKeyString<Boolean>>

    //called when user joins a chat room, will navigate to the chat room
    //saves the chat room object to chatRoom in sharedApplicationViewModel
    private lateinit var returnJoinChatRoomResultObserver: Observer<EventWrapperWithKeyString<JoinChatRoomReturnValues>>

    //called when join or created chat room is selected
    private lateinit var menuOptionSelectedObserver: Observer<EventWrapperWithKeyString<MenuOptionSelected>>

    //match made removed
    private lateinit var matchMadeRemovedObserver: Observer<EventWrapper<ReturnMatchMadeRemovedDataHolder>>

    //match made range inserted
    private lateinit var matchMadeRangeInsertedObserver: Observer<EventWrapper<ReturnMatchMadeRangeInsertedDataHolder>>

    private var messengerScreenMatchesMadeAdapter: MessengerScreenMatchesMadeAdapter? = null

    private var navigatingToChatRoom = false

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //if this was started by a notification being pressed
        when(GlobalValues.loginToChatRoomId) {
            NotificationInfo.SEND_TO_CHAT_ROOM_LIST -> {
                GlobalValues.loginToChatRoomId = ""
                navigatingToChatRoom = false
            }
            "" -> {
                navigatingToChatRoom = false
            }
            else -> { //should be a chat room Id
                navigatingToChatRoom = true
                GlobalValues.loginToChatRoomId = ""
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        applicationActivity = requireActivity() as AppActivity

        // Inflate the layout for this fragment
        _binding = FragmentPrimaryMessengerScreenBinding.inflate(inflater, container, false)
        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        if (!navigatingToChatRoom) {
            sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(
                ChatRoomWithMemberMapDataClass()
            )
        }

        thisFragmentChatRoomUniqueID = sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (navigatingToChatRoom) { //if navigating to the chat room from a notification
            //don't need to do any other initialization if navigating to the chat room
            navigatingToChatRoom = false

            //clear any possible 'old' messages
            sharedApplicationViewModel.chatRoomContainer.clearBetweenChatRoomFragmentInfo()

            //NOTE: this must go in onViewCreated, not in onCreateView or it will randomly throw an exception
            applicationActivity?.navigate(
                R.id.messengerScreenFragment,
                R.id.action_messengerScreenFragment_to_chatRoomFragment
            )
        } else { //if not navigating to the chat room from a notification

            Log.i("listsFragment", "MessengerScreenFragment onViewCreated")
            //chatRoomsListFragment = ChatRoomsListFragment()

            val args = Bundle()
            args.putString(
                getString(R.string.chat_room_list_fragment_parent_instance_id_key),
                thisFragmentInstanceID
            )
            args.putString(
                getString(R.string.chat_room_list_fragment_parent_chat_room_unique_id_key),
                thisFragmentChatRoomUniqueID
            )
            args.putInt(
                getString(R.string.chat_room_list_fragment_type_of_parent_key),
                ChatRoomListCalledFrom.MESSENGER_FRAGMENT.ordinal
            )

            //initialize this before attaching to any live data
            messengerScreenMatchesMadeAdapter = MessengerScreenMatchesMadeAdapter(
                requireContext(),
                binding.fragmentMessengerScreenMatchQueueTextView,
                sharedApplicationViewModel.chatRoomsListInfoContainer.matchesMade,
                navigateToChatRoom = { chatRoomId ->

                    applicationActivity?.setLoadingDialogState(true, LOADING_DIALOG_TIMEOUT_IN_MS)

                    sharedApplicationViewModel.retrieveSingleChatRoom(
                        chatRoomId,
                        thisFragmentInstanceID,
                        chatRoomMustExist = true
                    )
                },
                errorStore
            )

            binding.fragmentMessengerScreenMadeMatchesRecyclerView.apply {
                adapter = messengerScreenMatchesMadeAdapter
                val horizontalManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                layoutManager = horizontalManager
                setHasFixedSize(false)
            }

            returnSingleChatRoomObserver = Observer { eventWrapper ->
                val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
                result?.let {
                    handleRetrieveSingleChatRoomResult()
                }
            }

            sharedApplicationViewModel.returnSingleChatRoom.observe(
                viewLifecycleOwner,
                returnSingleChatRoomObserver
            )

            returnCreatedChatRoomObserver = Observer { wrapper ->
                val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
                result?.let {
                    applicationActivity?.setLoadingDialogState(false)
                    if (it) {

                        //clear any possible 'old' messages
                        sharedApplicationViewModel.chatRoomContainer.clearBetweenChatRoomFragmentInfo()

                        applicationActivity?.navigate(
                            R.id.messengerScreenFragment,
                            R.id.action_messengerScreenFragment_to_chatRoomFragment
                        )
                    }
                }
            }

            sharedApplicationViewModel.returnCreatedChatRoom.observe(
                viewLifecycleOwner,
                returnCreatedChatRoomObserver
            )

            returnJoinChatRoomResultObserver = Observer { wrapper ->
                val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
                result?.let {
                    handleJoinChatRoom(it)
                }
            }

            sharedApplicationViewModel.returnJoinChatRoomResult.observe(
                viewLifecycleOwner,
                returnJoinChatRoomResultObserver
            )

            matchMadeRemovedObserver = Observer { eventWrapper ->
                val result = eventWrapper.getContentIfNotHandled()
                result?.let {
                    handleMatchMadeRemoved(it.index)
                }
            }

            sharedApplicationViewModel.chatRoomsListInfoContainer.matchMadeRemoved.observe(
                viewLifecycleOwner,
                matchMadeRemovedObserver
            )

            matchMadeRangeInsertedObserver = Observer { eventWrapper ->
                val result = eventWrapper.getContentIfNotHandled()
                result?.let {
                    handleMatchMadeRangeInserted(it)
                }
            }

            sharedApplicationViewModel.chatRoomsListInfoContainer.matchMadeRangeInserted.observe(
                viewLifecycleOwner,
                matchMadeRangeInsertedObserver
            )

            menuOptionSelectedObserver = Observer { eventWrapper ->
                val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
                result?.let {
                    when (it) {
                        MenuOptionSelected.JOIN_CHAT_ROOM_PRESSED -> {
                            showJoinChatRoomDialog()
                        }
                        MenuOptionSelected.CREATE_CHAT_ROOM_PRESSED -> {
                            showCreateChatRoomDialog()
                        }
                    }
                }
            }

            sharedApplicationViewModel.chatRoomsListInfoContainer.matchesMade.clear()

            //set this up before calling beginTransaction() but after live data
            sharedApplicationViewModel.chatRoomsListInfoContainer.messengerFragmentOnViewCreated(
                thisFragmentInstanceID
            )

            //NOTE: The ChatRoomsListFragment from the xml will not have onCreateView() called until this
            // current fragment onViewCreated() has been completed.
            val chatRoomListFrag = binding.fragmentPrimaryMessengerScreenFragmentContainerView.getFragment<ChatRoomsListFragment>()
            chatRoomListFrag.arguments = args
            chatRoomListFrag.menuOptionSelected.observe(
                viewLifecycleOwner,
                menuOptionSelectedObserver
            )
        }
    }

    //returned when clicking on a single chat room and any other time sharedApplicationViewModel.retrieveSingleChatRoom() is called
    // (for example using 'Join Chat Room')
    private fun handleRetrieveSingleChatRoomResult() {

        //updateChatRoom() must be run even if this call comes from joinChatRoom(). This is
        // because joinChatRoom() only sends back minimal data for a lot of things.
        sharedApplicationViewModel.updateChatRoom()

        applicationActivity?.setLoadingDialogState(false)

        //clear any possible 'old' messages
        sharedApplicationViewModel.chatRoomContainer.clearReply()
        sharedApplicationViewModel.chatRoomContainer.clearChatRoomInvite()
        sharedApplicationViewModel.chatRoomContainer.clearChatRoomLocation()

        applicationActivity?.navigate(
            R.id.messengerScreenFragment,
            R.id.action_messengerScreenFragment_to_chatRoomFragment
        )
    }

    //NOTE: error status was handled inside view model, it should always be NO_ERRORS by this point
    private fun handleJoinChatRoom(result: JoinChatRoomReturnValues) {

        when (result.chatRoomStatus) {
            ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM,
            ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED,
            -> {
                val errorMessage =
                    "${result.chatRoomStatus} should be handled inside the repository, it should never get back to the MessengerScreenFragment.\n" +
                            "result: $result\n"

                storeErrorMessengerScreenFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //NOTE: Don't want to run setLoadingDialogState(false) because retrieveSingleChatRoom still
                // needs to run.
                //NOTE: chat room was already set inside application view model
                sharedApplicationViewModel.retrieveSingleChatRoom(
                    result.chatRoomId,
                    thisFragmentInstanceID,
                    chatRoomMustExist = true
                )
            }
            ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED -> {

                applicationActivity?.setLoadingDialogState(false)

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_account_banned_from_chat_room,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST,
            ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_ID,
            -> {

                applicationActivity?.setLoadingDialogState(false)

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_no_chat_room_id_exists,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_PASSWORD -> {

                applicationActivity?.setLoadingDialogState(false)

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_invalid_chat_room_password,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ChatRoomCommands.ChatRoomStatus.USER_TOO_YOUNG_FOR_CHAT_ROOM -> {
                applicationActivity?.setLoadingDialogState(false)

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_user_too_young_for_chat_room,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED -> {

                applicationActivity?.setLoadingDialogState(false)

                val errorMessage =
                    "${result.chatRoomStatus} should be handled inside the repository, it should never get back to the MessengerScreenFragment.\n" +
                            "result: $result\n"

                storeErrorMessengerScreenFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_error_joining_chat_room,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleMatchMadeRemoved(
        index: Int
    ) {
        if (index != -1) {
            messengerScreenMatchesMadeAdapter?.let {
                it.notifyItemRemoved(index)
                it.notifyItemRangeChanged(index, it.itemCount - index)
                it.updateRecyclerViewTitle()
            }
        }
    }

    private fun handleMatchMadeRangeInserted(
        info: ReturnMatchMadeRangeInsertedDataHolder
    ) {
        if (info.startIndex != -1 && info.numberItemsInserted > 0) {
            messengerScreenMatchesMadeAdapter?.let {
                it.notifyItemRangeInserted(
                    info.startIndex,
                    info.numberItemsInserted
                )

                it.updateRecyclerViewTitle()
            }
        }
    }

    private fun showCreateChatRoomDialog() {

        CreateNewChatRoomTextDialog(
            resources.getString(R.string.messenger_screen_fragment_create_chat_room_dialog_title),
            resources.getString(R.string.messenger_screen_fragment_create_chat_room_dialog_name_header)
        ) { chatRoomName ->

            applicationActivity?.setLoadingDialogState(true, LOADING_DIALOG_TIMEOUT_IN_MS)
            sharedApplicationViewModel.createChatRoom(chatRoomName, thisFragmentInstanceID)

        }.show(childFragmentManager, "create_new_chat_room")
    }

    private fun showJoinChatRoomDialog(
        passedChatRoomId: String = "",
        passedChatRoomPassword: String = ""
    ) {

        JoinChatRoomTextDialog(
            resources.getString(R.string.messenger_screen_fragment_join_chat_room_dialog_title),
            resources.getString(R.string.messenger_screen_fragment_join_chat_room_dialog_id_header),
            resources.getString(R.string.messenger_screen_fragment_join_chat_room_dialog_password_header),
            passedChatRoomId,
            passedChatRoomPassword
        ) { chatRoomId, chatRoomPassword ->

            if (chatRoomId.isValidChatRoomId()) {
                joinChatRoom(chatRoomId.lowercase(Locale.ROOT), chatRoomPassword)
            } else {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.messenger_screen_fragment_invalid_chat_room_id_entered,
                    Toast.LENGTH_SHORT
                ).show()
                showJoinChatRoomDialog(chatRoomId, chatRoomPassword)
            }
        }.show(childFragmentManager, "join_chat_room")
    }

    private fun joinChatRoom(chatRoomId: String, chatRoomPassword: String) {

        val alreadyInChatRoom =
            sharedApplicationViewModel.chatRoomsListInfoContainer.checkIfUserIsInPassedChatRoom(
                chatRoomId
            )

        applicationActivity?.setLoadingDialogState(
            true,
            LOADING_DIALOG_TIMEOUT_JOIN_CHAT_ROOM_IN_MS
        )

        if (!alreadyInChatRoom) { //if user is not in chat room
            sharedApplicationViewModel.joinChatRoom(
                chatRoomId,
                chatRoomPassword,
                thisFragmentInstanceID,
            )
        } else { //if user is in chat room
            sharedApplicationViewModel.retrieveSingleChatRoom(
                chatRoomId,
                thisFragmentInstanceID,
                chatRoomMustExist = true
            )
        }
    }

    private fun storeErrorMessengerScreenFragment(
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
        sharedApplicationViewModel.chatRoomsListInfoContainer.messengerFragmentOnStart()
        //This is inside onStart() for consistency with other functions from setupActivityMenuBars.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsMessengerScreenFragment()
    }

    override fun onStop() {
        sharedApplicationViewModel.chatRoomsListInfoContainer.messengerFragmentOnStop()
        super.onStop()
    }

    override fun onDestroyView() {

        Log.i("listsFragment", "MessengerScreenFragment onDestroyView()")
        //set this before matchesMade list is cleared
        sharedApplicationViewModel.chatRoomsListInfoContainer.messengerFragmentOnDestroyView(
            thisFragmentInstanceID
        )

        sharedApplicationViewModel.chatRoomsListInfoContainer.matchesMade.clear()

        _binding = null
        applicationActivity = null
        messengerScreenMatchesMadeAdapter = null

        super.onDestroyView()
    }
}

