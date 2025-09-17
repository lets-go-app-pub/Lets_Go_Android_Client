package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databinding.FragmentChatRoomsListBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.AddedOfRemovedIndex
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnChatRoomsListChatRoomModifiedDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnChatRoomsListJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass

class ChatRoomsListFragment : Fragment() {

    companion object {
        //the time before the handler starts the search, this will prevent it spamming the database
        // while it waits for the user to stop typing (or at least have a small delay)
        const val TIME_BEFORE_SEARCH_EXECUTES_MS = 600
    }

    private var _binding: FragmentChatRoomsListBinding? = null
    private val binding get() = _binding!!

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var thisFragmentInstanceID: String
    private lateinit var parentFragmentInstanceID: String
    private lateinit var parentFragmentChatRoomUniqueID: String
    private lateinit var typeOfFragmentCalledFrom: ChatRoomListCalledFrom

    //called when a chat room is added or removed from chatRooms list
    private lateinit var chatRoomAddedRemovedObserver: Observer<EventWrapperWithKeyString<ReturnChatRoomsListJoinedLeftChatRoomDataHolder>>

    //called when the recycler view requires updated
    private lateinit var chatRoomsListUpdateDataSetObserver: Observer<EventWrapper<Unit>>

    //called when the recycler view requires a specific message modified
    private lateinit var chatRoomModifiedObserver: Observer<EventWrapperWithKeyString<ReturnChatRoomsListChatRoomModifiedDataHolder>>

    //return a list of chatRoomIds matching the passed text for the search
    private lateinit var chatRoomSearchResultsObserver: Observer<EventWrapperWithKeyString<Set<String>>>

    //disable loading for chat rooms in case call failed
    private lateinit var disableLoadingObserver: Observer<EventWrapperWithKeyString<Int>>

    //sends info to selectChatRoomForInviteFragment (passes back chat room id)
    private val _chatRoomSelectedForInvite: MutableLiveData<EventWrapperWithKeyString<ChatRoomBasicInfoObject>> =
        MutableLiveData()
    val chatRoomSelectedForInvite: LiveData<EventWrapperWithKeyString<ChatRoomBasicInfoObject>> =
        _chatRoomSelectedForInvite

    //used to notify parent fragment that join chat room or create chat room was selected
    private val _menuOptionSelected: MutableLiveData<EventWrapperWithKeyString<MenuOptionSelected>> =
        MutableLiveData()
    val menuOptionSelected: LiveData<EventWrapperWithKeyString<MenuOptionSelected>> =
        _menuOptionSelected

    private var messengerScreenChatRoomsAdapter: ChatRoomListChatRoomsAdapter? = null

    private val runSearchHandler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null
    private val searchHandlerToken = "search_handler_token"

    private var applicationActivity: AppActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        Log.i("listsFragment", "ChatRoomsListFragment onCreateView()")

        applicationActivity = requireActivity() as AppActivity

        // Inflate the layout for this fragment
        _binding = FragmentChatRoomsListBinding.inflate(inflater, container, false)
        //thisFragmentInstanceID = generateFragmentInstanceID(this::class.simpleName)

        //get parameters
        parentFragmentInstanceID = arguments?.getString(
            getString(R.string.chat_room_list_fragment_parent_instance_id_key),
            ""
        ) ?: ""

        parentFragmentChatRoomUniqueID = arguments?.getString(
            getString(R.string.chat_room_list_fragment_parent_chat_room_unique_id_key),
            ""
        ) ?: ""

        typeOfFragmentCalledFrom =
            ChatRoomListCalledFrom.setVal(
                arguments?.getInt(
                    getString(R.string.chat_room_list_fragment_type_of_parent_key),
                    1
                )
            )

        thisFragmentInstanceID = generateFragmentInstanceID(this::class.simpleName)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeFragment()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initializeFragment() {

        val onClickChatRoom: (ChatRoomBasicInfoObject) -> Unit =
            when (typeOfFragmentCalledFrom) {
                ChatRoomListCalledFrom.MESSENGER_FRAGMENT -> {
                    { chatRoomBasicInfo ->
                        sharedApplicationViewModel.retrieveSingleChatRoom(
                            chatRoomBasicInfo.chatRoomId,
                            parentFragmentInstanceID,
                            chatRoomMustExist = true
                        )
                    }
                }
                ChatRoomListCalledFrom.INVITE_FRAGMENT -> {
                    { chatRoomBasicInfo ->
                        _chatRoomSelectedForInvite.value =
                            EventWrapperWithKeyString(chatRoomBasicInfo, parentFragmentInstanceID)
                    }
                }
            }

        val onLongClickChatRoom: (chatRoomId: String) -> Unit =
            when (typeOfFragmentCalledFrom) {
                ChatRoomListCalledFrom.MESSENGER_FRAGMENT -> {
                    { chatRoomId ->
                        ChatRoomListOptionsMenu { dialogInterface: DialogInterface, i: Int ->

                            if (i == 0) { //Leave Chat Room
                                for (j in 0 until sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms.size) {
                                    if (sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms[j].chatRoomId == chatRoomId) {
                                        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms[j].showLoading =
                                            true
                                        messengerScreenChatRoomsAdapter?.notifyItemChanged(j)
                                        break
                                    }
                                }
                                sharedApplicationViewModel.leaveChatRoomById(chatRoomId)
                            }

                            dialogInterface.dismiss()
                        }.show(childFragmentManager, "leave_chat_room_choices")
                    }
                }
                ChatRoomListCalledFrom.INVITE_FRAGMENT -> {
                    { }
                }
            }

        //make sure to set this up before initializing the live data observers
        messengerScreenChatRoomsAdapter =
            ChatRoomListChatRoomsAdapter(
                GlobalValues.applicationContext,
                sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms,
                sharedApplicationViewModel.firstPictureInList.picturePath,
                sharedApplicationViewModel.firstPictureInList.pictureTimestamp,
                onClickChatRoom,
                onLongClickChatRoom,
                requestMessageInfoFromServer = { chatRoomId, messageUUID ->
                    sharedApplicationViewModel.requestMessageUpdate(
                        chatRoomId,
                        TypeOfChatMessageOuterClass.AmountOfMessage.ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE,
                        listOf(messageUUID)
                    )
                },
                errorStore
            )

        messengerScreenChatRoomsAdapter?.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            //when the items are sorted and notifyItemMoved is called, this will make the recycler view scroll back to the
            // top if it was at the top or back to the bottom if it was at the bottom
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                Log.i("handleUpdateDataSet", "fromPosition: $fromPosition toPosition: $toPosition")
                if (fromPosition == 0 || toPosition == 0)
                    binding.fragmentMessengerScreenChatRoomSelectRecyclerView.scrollToPosition(0)
                else if (fromPosition == itemCount - 1 || toPosition == itemCount - 1)
                    binding.fragmentMessengerScreenChatRoomSelectRecyclerView.scrollToPosition(
                        itemCount - 1
                    )
            }
        })

        binding.fragmentMessengerScreenChatRoomSelectRecyclerView.apply {
            adapter = messengerScreenChatRoomsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }

        chatRoomAddedRemovedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(parentFragmentChatRoomUniqueID)
            result?.let {
                chatRoomAddedRemovedReturn(it)
            }
        }

        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomAddedRemoved.observe(
            viewLifecycleOwner,
            chatRoomAddedRemovedObserver
        )

        chatRoomsListUpdateDataSetObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleUpdateDataSet()
            }
        }

        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomsListUpdateDataSet.observe(
            viewLifecycleOwner,
            chatRoomsListUpdateDataSetObserver
        )

        chatRoomModifiedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(parentFragmentChatRoomUniqueID)
            result?.let {
                handleChatRoomModified(it)
            }
        }

        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomModified.observe(
            viewLifecycleOwner,
            chatRoomModifiedObserver
        )

        chatRoomSearchResultsObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(parentFragmentInstanceID)
            result?.let {
                handleSearchResults(it)
            }
        }

        sharedApplicationViewModel.chatRoomSearchResults.observe(
            viewLifecycleOwner,
            chatRoomSearchResultsObserver
        )

        disableLoadingObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(parentFragmentChatRoomUniqueID)
            result?.let {
                disableLoadingResults(it)
            }
        }

        sharedApplicationViewModel.disableLoading.observe(
            viewLifecycleOwner,
            disableLoadingObserver
        )

        binding.fragmentMessengerSearchEditText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    runSearchForString(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        binding.fragmentMessengerCloseSearchImageView.setSafeOnClickListener {
            cancelAnyProcessingSearches()

            setupSearchFunctionality(false)

            if (!TextUtils.isEmpty(binding.fragmentMessengerSearchEditText.text)) {
                messengerScreenChatRoomsAdapter?.notifyDataSetChanged()
            }
        }

        binding.fragmentMessengerScreenChatRoomSelectPopupMenuImageView.setSafeOnClickListener {

            val searchLambda: () -> Unit = {
                setupSearchFunctionality(true)

                //if there is already text in the window, run a search
                if (!TextUtils.isEmpty(binding.fragmentMessengerSearchEditText.text)) {
                    runSearchForString(binding.fragmentMessengerSearchEditText.text?.toString())
                }
            }


            val sortLambda: (ChatRoomSortMethodSelected) -> Unit = { chatRoomSortMethodSelected ->
                sharedApplicationViewModel.chatRoomsListInfoContainer.sortChatRooms(
                    chatRoomSortMethodSelected
                )
            }

            when (typeOfFragmentCalledFrom) {
                ChatRoomListCalledFrom.MESSENGER_FRAGMENT -> {
                    applicationActivity?.showMessageFragmentChatRoomListPopupMenu(
                        it,
                        createNewChatRoom = {
                            _menuOptionSelected.value = EventWrapperWithKeyString(
                                MenuOptionSelected.CREATE_CHAT_ROOM_PRESSED,
                                parentFragmentInstanceID
                            )
                        },
                        joinChatRoom = {
                            _menuOptionSelected.value = EventWrapperWithKeyString(
                                MenuOptionSelected.JOIN_CHAT_ROOM_PRESSED,
                                parentFragmentInstanceID
                            )
                        },
                        searchClicked = searchLambda,
                        sortBy = sortLambda
                    )
                }
                ChatRoomListCalledFrom.INVITE_FRAGMENT -> {
                    applicationActivity?.showInviteToChatRoomListPopupMenu(
                        it,
                        searchClicked = searchLambda,
                        sortBy = sortLambda
                    )
                }
            }
        }

        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms.clear()

        //this should be initialized before getChatRoomsFromDatabase() and after the live data is set up
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomListFragmentOnViewCreated(
            thisFragmentInstanceID
        )
        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomListFragmentOnViewCreated(
            thisFragmentInstanceID
        )

        sharedApplicationViewModel.getChatRoomsFromDatabase(typeOfFragmentCalledFrom)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun handleUpdateDataSet() {
        messengerScreenChatRoomsAdapter?.notifyDataSetChanged()
    }

    private fun handleChatRoomModified(returnChatRoomsListChatRoomModifiedDataHolder: ReturnChatRoomsListChatRoomModifiedDataHolder) {

        if (returnChatRoomsListChatRoomModifiedDataHolder.modifiedIndex != -1) {
            messengerScreenChatRoomsAdapter?.notifyItemChanged(
                returnChatRoomsListChatRoomModifiedDataHolder.modifiedIndex
            )
        }

        if (returnChatRoomsListChatRoomModifiedDataHolder.chatRoomMoved.indexMoved) {
            messengerScreenChatRoomsAdapter?.notifyItemMoved(
                returnChatRoomsListChatRoomModifiedDataHolder.chatRoomMoved.originalIndexNumber,
                returnChatRoomsListChatRoomModifiedDataHolder.chatRoomMoved.newIndexNumber,
            )
        }
    }

    private fun chatRoomAddedRemovedReturn(info: ReturnChatRoomsListJoinedLeftChatRoomDataHolder) {

        Log.i(
            "chatRoomAddedRemoved",
            "chatRoomAddedRemovedReturn(); info.index: ${info.index} info.addedOfRemovedIndex: ${info.addedOfRemovedIndex} itemCount: ${messengerScreenChatRoomsAdapter?.itemCount}"
        )

        if (info.index != -1) {
            when (info.addedOfRemovedIndex) {
                AddedOfRemovedIndex.INDEX_ADDED -> {
                    if (info.index < (messengerScreenChatRoomsAdapter?.itemCount ?: 0))
                        messengerScreenChatRoomsAdapter?.notifyItemInserted(info.index)
                }
                AddedOfRemovedIndex.INDEX_REMOVED -> {
                    if (info.index < (messengerScreenChatRoomsAdapter?.itemCount ?: 0) + 1) {
                        messengerScreenChatRoomsAdapter?.notifyItemRemoved(info.index)
                        messengerScreenChatRoomsAdapter?.notifyItemRangeChanged(
                            info.index,
                            (messengerScreenChatRoomsAdapter?.itemCount ?: 0) - info.index
                        )
                    }
                }
                AddedOfRemovedIndex.NEITHER_ADDED_OR_REMOVED -> {
                }
            }
        }
    }

    private fun cancelAnyProcessingSearches() {
        runSearchHandler.removeCallbacksAndMessages(
            searchHandlerToken
        )
        searchJob?.cancel()
        searchJob = null
    }

    //If the set is null all chat rooms will be assumed to be visible.
    @SuppressLint("NotifyDataSetChanged")
    private fun handleSearchResults(
        matchingChatRoomIds: Set<String>?,
    ) {
        if (messengerScreenChatRoomsAdapter?.showAllChatRoomsInList == false) {

            val chatRoomsUpdatedIndex =
                sharedApplicationViewModel.chatRoomsListInfoContainer.displayMatchingChatRoomIds(
                    matchingChatRoomIds
                )

            if (chatRoomsUpdatedIndex.isNotEmpty()) {
                if (chatRoomsUpdatedIndex.size == 1) {
                    messengerScreenChatRoomsAdapter?.notifyItemChanged(chatRoomsUpdatedIndex.first())
                } else { //more than 1 element updated
                    messengerScreenChatRoomsAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun disableLoadingResults(index: Int) {
        if (0 <= index && index < (messengerScreenChatRoomsAdapter?.itemCount ?: 0)) {
            messengerScreenChatRoomsAdapter?.notifyItemChanged(index)
        }
    }

    private fun setupSearchFunctionality(showSearchBar: Boolean) {
        val visibility =
            if (showSearchBar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        messengerScreenChatRoomsAdapter?.setShowAllChatRoomsInList(!showSearchBar)

        setupSearchBarVisibility(visibility)
    }

    //takes visibility as View.VISIBLE or View.GONE
    private fun setupSearchBarVisibility(visibility: Int) {
        binding.fragmentMessengerSearchEditText.visibility = visibility
        binding.fragmentMessengerCloseSearchImageView.visibility = visibility
    }

    private fun runSearchForString(string: String?) {
        cancelAnyProcessingSearches()

        if (string != null && string.isNotEmpty()) {

            runSearchHandler.postAtTime(
                {
                    searchJob = CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                        sharedApplicationViewModel.beginChatRoomSearchForString(
                            string.toString(),
                            parentFragmentInstanceID
                        )
                    }
                },
                searchHandlerToken,
                SystemClock.uptimeMillis() + TIME_BEFORE_SEARCH_EXECUTES_MS
            )
        } else {
            handleSearchResults(null)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onStart() {
        messengerScreenChatRoomsAdapter?.notifyDataSetChanged()

        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomListFragmentOnStart()
        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomListFragmentOnStart()

        super.onStart()
    }

    override fun onStop() {
        super.onStop()

        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomListFragmentOnStop()
        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomListFragmentOnStop()
    }

    override fun onDestroyView() {
        cancelAnyProcessingSearches()

        Log.i("listsFragment", "ChatRoomsListFragment onDestroyView()")

        //need to do this to avoid onClickListeners leaking view
        applicationActivity?.hideMenus()

        applicationActivity = null
        parentFragmentInstanceID = ""
        parentFragmentChatRoomUniqueID = ""

        //do this before the chatRooms list is cleared (on the Main thread) so that they will stay empty
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomListFragmentOnDestroyView(
            thisFragmentInstanceID
        )
        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRoomListFragmentOnDestroyView(
            thisFragmentInstanceID
        )

        sharedApplicationViewModel.chatRoomsListInfoContainer.chatRooms.clear()

        messengerScreenChatRoomsAdapter = null

        _binding = null

        super.onDestroyView()
    }

}
