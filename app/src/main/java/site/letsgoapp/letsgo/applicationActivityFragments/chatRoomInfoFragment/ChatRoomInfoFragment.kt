package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomInfoFragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import grpc_chat_commands.ChatRoomCommands
import report_enums.ReportMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.ChatRoomFragment
import site.letsgoapp.letsgo.databinding.FragmentChatRoomInfoBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import type_of_chat_message.TypeOfChatMessageOuterClass

class ChatRoomInfoFragment : Fragment() {

    private var _binding: FragmentChatRoomInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var returnUpdatedOtherUserObserver: Observer<EventWrapper<ReturnUpdatedOtherUserDataHolder>>
    private lateinit var returnUpdatedChatRoomMemberObserver: Observer<EventWrapperWithKeyString<ReturnUpdatedChatRoomMemberDataHolder>>
    private lateinit var returnAccountStateUpdatedObserver: Observer<EventWrapperWithKeyString<AccountStateUpdatedDataHolder>>
    private lateinit var returnEventOidUpdatedObserver: Observer<EventWrapperWithKeyString<Unit>>
    private lateinit var returnQrCodeUpdatedObserver: Observer<EventWrapperWithKeyString<ReturnQrCodeInfoUpdated>>
    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnChatRoomInfoUpdatedObserverData: Observer<EventWrapperWithKeyString<UpdateChatRoomInfoResultsDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>
    private lateinit var returnBlockReportChatRoomResultObserver: Observer<EventWrapperWithKeyString<BlockAndReportChatRoomResultsHolder>>
    private lateinit var finishedChatRoomLocationRequestObserver: Observer<EventWrapperWithKeyString<ChatRoomLocationRequestReturn>>
    private lateinit var setPinnedLocationFailedObserver: Observer<EventWrapperWithKeyString<Unit>>

    private var recyclerViewAdapter: ChatRoomInfoAdapter? = null

    private var applicationActivity: AppActivity? = null

    private var locationMessageObject = LocationSelectedObject()

    private val navigateToMessengerFragmentResourceId =
        R.id.action_chatRoomInfoFragment_to_messengerScreenFragment

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        // Inflate the layout for this fragment
        _binding = FragmentChatRoomInfoBinding.inflate(inflater, container, false)

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        applicationActivity = requireActivity() as AppActivity

        if (sharedApplicationViewModel.chatRoomContainer.pinnedLocationObject.sendLocationMessage) {
            locationMessageObject =
                sharedApplicationViewModel.chatRoomContainer.pinnedLocationObject
            sharedApplicationViewModel.chatRoomContainer.clearChatRoomLocation()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()) {
            applicationActivity?.navigate(
                R.id.chatRoomInfoFragment,
                navigateToMessengerFragmentResourceId
            )
            return
        }

        Log.i(
            "info_adapter",
            "num: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}"
        )
        recyclerViewAdapter = ChatRoomInfoAdapter(
            requireContext(),
            sharedApplicationViewModel.userName,
            GlideApp.with(this),
            sharedApplicationViewModel.firstPictureInList.picturePath,
            sharedApplicationViewModel.firstPictureInList.pictureTimestamp,
            sharedApplicationViewModel.chatRoomContainer.chatRoom,
            childFragmentManager,
            navigateToMemberInfo = { userIndex ->
                applicationActivity?.hideMenus()
                applicationActivity?.navigate(
                    R.id.chatRoomInfoFragment,
                    ChatRoomInfoFragmentDirections.actionChatRoomInfoFragmentToChatRoomMemberFragment(
                        userIndex
                    )
                )
            },
            setChatRoomNotifications = { notificationsEnabled ->
                sharedApplicationViewModel.setNotificationsForChatRoom(notificationsEnabled)
            },
            showBlockEventPopupMenu = { viewMenuIsBoundTo, accountOID ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showBlockEventPopupMenu(
                    viewMenuIsBoundTo,
                    blockAndReportMember = {
                        applicationActivity?.blockAndReportUserFromChatRoom(
                            childFragmentManager,
                            accountOID,
                            false,
                            ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_INFO,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    },
                    accountOID
                )
            },
            showUnblockEventPopupMenu = { viewMenuIsBoundTo, accountOID ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUnblockEventPopupMenu(
                    viewMenuIsBoundTo,
                    unblockMember = {
                        sharedApplicationViewModel.unblockOtherUser(
                            accountOID
                        )
                    },
                    accountOID
                )
            },
            showUserNoAdminBlockAndReportPopupMenu = { viewMenuIsBoundTo, accountOID, userName ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserNoAdminBlockAndReportPopupMenu(
                    viewMenuIsBoundTo,
                    blockAndReportMember = {
                        applicationActivity?.blockAndReportUserFromChatRoom(
                            childFragmentManager,
                            accountOID,
                            false,
                            ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_INFO,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    },
                    inviteMember = {
                        Log.i("inviteMember", "accountOID: $accountOID, userName: $userName")
                        startInviteMember(accountOID, userName)
                    },
                    accountOID
                )
            },
            showUserNoAdminUnblockPopupMenu = { viewMenuIsBoundTo, accountOID, userName ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserNoAdminUnblockPopupMenu(
                    viewMenuIsBoundTo,
                    unblockMember = {
                        sharedApplicationViewModel.unblockOtherUser(
                            accountOID
                        )
                    },
                    inviteMember = {
                        Log.i("inviteMember", "accountOID: $accountOID, userName: $userName")
                        startInviteMember(accountOID, userName)
                    },
                    accountOID
                )
            },
            showUserAdminBlockAndReportPopupMenu = { viewMenuIsBoundTo, accountOID, userName ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserAdminBlockAndReportPopupMenu(
                    viewMenuIsBoundTo,
                    promoteNewAdmin = {
                        sharedApplicationViewModel.promoteNewAdmin(
                            accountOID,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    },
                    kickMember = {
                        sharedApplicationViewModel.removeUserFromChatRoom(
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK,
                            accountOID
                        )
                    },
                    banMember = {
                        sharedApplicationViewModel.removeUserFromChatRoom(
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.BAN,
                            accountOID
                        )
                    },
                    blockAndReportMember = {
                        applicationActivity?.blockAndReportUserFromChatRoom(
                            childFragmentManager,
                            accountOID,
                            false,
                            ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_INFO,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    },
                    inviteMember = {
                        Log.i("inviteMember", "accountOID: $accountOID, userName: $userName")
                        startInviteMember(accountOID, userName)
                    },
                    accountOID
                )
            },
            showUserAdminUnblockPopupMenu = { viewMenuIsBoundTo, accountOID, userName ->
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserAdminUnblockPopupMenu(
                    viewMenuIsBoundTo,
                    promoteNewAdmin = {
                        sharedApplicationViewModel.promoteNewAdmin(
                            accountOID,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    },
                    kickMember = {
                        sharedApplicationViewModel.removeUserFromChatRoom(
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK,
                            accountOID
                        )
                    },
                    banMember = {
                        sharedApplicationViewModel.removeUserFromChatRoom(
                            ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.BAN,
                            accountOID
                        )
                    },
                    unblockMember = {
                        sharedApplicationViewModel.unblockOtherUser(
                            accountOID
                        )
                    },
                    inviteMember = {
                        Log.i("inviteMember", "accountOID: $accountOID, userName: $userName")
                        startInviteMember(accountOID, userName)
                    },
                    accountOID
                )
            },
            setChatRoomInfo = { newInfo, typeOfInfoToUpdate ->
                when (typeOfInfoToUpdate) {
                    ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_PASSWORD -> {
                        if (newInfo != sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomPassword) { //if password was updated
                            sharedApplicationViewModel.updateChatRoomInfo(
                                newInfo,
                                typeOfInfoToUpdate
                            )
                        }
                    }
                    ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_NAME -> {
                        if (newInfo != sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomName) { //if name was updated
                            sharedApplicationViewModel.updateChatRoomInfo(
                                newInfo,
                                typeOfInfoToUpdate
                            )
                        }
                    }
                    else -> {
                        val errorMessage =
                            "Invalid type was returned when setting chat room name/password.\n" +
                                    "typeOfInfoToUpdate: $typeOfInfoToUpdate\n" +
                                    "newInfo: $newInfo\n"

                        storeErrorChatRoomInfoFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        //can continue
                    }
                }
            },
            selectNewPinnedLocationMessage = {
                applicationActivity?.currentChatRoomFragmentInstanceID = thisFragmentInstanceID

                applicationActivity?.getCurrentLocation(TypeOfLocationUpdate.PINNED_LOCATION_REQUEST)
            },
            removePinnedLocationMessage = {
                sharedApplicationViewModel.setPinnedLocation(
                    GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
                    GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
                    thisFragmentInstanceID
                )
            },
            navigateToQrCode = {
                applicationActivity?.navigate(
                    R.id.chatRoomInfoFragment,
                    R.id.action_chatRoomInfoFragment_to_displayQrCodeFragment
                )
            },
            removeChatRoomQrCode = {
                sharedApplicationViewModel.removeChatRoomQrCode()
            },
            errorStore
        )

        binding.chatRoomInfoMembersRecyclerView.apply {
            adapter = recyclerViewAdapter
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = null
            setHasFixedSize(false)
        }

        returnUpdatedOtherUserObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled()
            result?.let {
                handleUpdatedOtherUser(it)
            }
        }

        sharedApplicationViewModel.returnUpdatedChatRoomUser.observe(
            viewLifecycleOwner,
            returnUpdatedOtherUserObserver
        )

        returnUpdatedChatRoomMemberObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleUpdatedChatRoomMember(it)
            }
        }

        sharedApplicationViewModel.returnUpdatedChatRoomMember.observe(
            viewLifecycleOwner,
            returnUpdatedChatRoomMemberObserver
        )

        returnAccountStateUpdatedObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleAccountStateUpdated(it)
            }
        }

        sharedApplicationViewModel.returnAccountStateUpdated.observe(
            viewLifecycleOwner,
            returnAccountStateUpdatedObserver
        )

        returnEventOidUpdatedObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleEventOidUpdated()
            }
        }

        sharedApplicationViewModel.returnChatRoomEventOidUpdated.observe(
            viewLifecycleOwner,
            returnEventOidUpdatedObserver
        )

        returnQrCodeUpdatedObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleQrCodeUpdate()
            }
        }

        sharedApplicationViewModel.returnQrInfoUpdated.observe(
            viewLifecycleOwner,
            returnQrCodeUpdatedObserver,
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
            val result =
                eventWrapper.getContentIfNotHandled()
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
                handleBlockReportChatRoomResult(it)
            }
        }

        sharedApplicationViewModel.returnBlockReportChatRoomResult.observe(
            viewLifecycleOwner,
            returnBlockReportChatRoomResultObserver
        )

        finishedChatRoomLocationRequestObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                if (it.typeOfLocationUpdate == TypeOfLocationUpdate.PINNED_LOCATION_REQUEST) {
                    if (it.successful) {
                        applicationActivity?.navigate(
                            R.id.chatRoomInfoFragment,
                            ChatRoomInfoFragmentDirections.actionChatRoomInfoFragmentToSelectLocationScreen(
                                ReasonSelectLocationCalled.CALLED_FOR_PINNED_LOCATION
                            )
                        )
                    } else { //failed to request location
                        recyclerViewAdapter?.pinnedLocationProgressBarLoading = false
                        recyclerViewAdapter?.notifyItemChanged(0)
                    }
                }
            }
        }

        applicationActivity?.finishedChatRoomLocationRequest?.observe(
            viewLifecycleOwner,
            finishedChatRoomLocationRequestObserver
        )

        setPinnedLocationFailedObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                recyclerViewAdapter?.mapLoading = false
                recyclerViewAdapter?.notifyItemChanged(0)
            }
        }

        sharedApplicationViewModel.returnSetPinnedLocationFailed.observe(
            viewLifecycleOwner,
            setPinnedLocationFailedObserver
        )

        if (locationMessageObject.sendLocationMessage) {

            if (locationMessageObject.selectLocationCurrentLocation.longitude != sharedApplicationViewModel.chatRoomContainer.chatRoom.pinnedLocationLongitude
                || locationMessageObject.selectLocationCurrentLocation.latitude != sharedApplicationViewModel.chatRoomContainer.chatRoom.pinnedLocationLatitude
            ) {
                recyclerViewAdapter?.mapLoading = true
                recyclerViewAdapter?.notifyItemChanged(0)

                sharedApplicationViewModel.setPinnedLocation(
                    locationMessageObject.selectLocationCurrentLocation.longitude,
                    locationMessageObject.selectLocationCurrentLocation.latitude,
                    thisFragmentInstanceID
                )
            }
            locationMessageObject = LocationSelectedObject()
        }

        applicationActivity?.setupActivityMenuBars?.addMenuProviderWithMenuItems(
            viewLifecycleOwner,
            leaveChatRoomLambda = {
                val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                    resources.getString(R.string.chat_room_general_double_check_leave_chat_room_title),
                    resources.getString(R.string.chat_room_general_double_check_leave_chat_room_message)
                ) { _: DialogInterface, _: Int ->
                    applicationActivity?.setLoadingDialogState(
                        true,
                        ChatRoomFragment.LOADING_DIALOG_TIMEOUT_IN_MS
                    )
                    sharedApplicationViewModel.leaveCurrentChatRoom()
                }

                alertDialog.show(
                    childFragmentManager,
                    "info_fragment_double_check_leave_chat_room"
                )
            },
            clearHistoryLambda = {
                val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                    resources.getString(R.string.chat_room_general_double_check_clear_history_title),
                    resources.getString(R.string.chat_room_general_double_check_clear_history_message)
                ) { _: DialogInterface, _: Int ->
                    //NOTE: No reason to put loading dialog here, this fragment has no response to the liveData.
                    sharedApplicationViewModel.clearHistoryFromChatRoom(thisFragmentInstanceID)
                }

                alertDialog.show(childFragmentManager, "info_fragment_double_check_clear_history")
            },
            fragmentInstanceID = thisFragmentInstanceID,
        )

    }

    private fun startInviteMember(accountOID: String, userName: String) {
        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserOid =
            accountOID
        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName =
            userName
        applicationActivity?.navigate(
            R.id.chatRoomInfoFragment,
            R.id.action_chatRoomInfoFragment_to_selectChatRoomForInviteFragment
        )
    }

    private fun handleUpdatedOtherUser(value: ReturnUpdatedOtherUserDataHolder) {
        if (
            value.index < sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()
        ) { //if the message is for this chat room and it is in range of the index
            recyclerViewAdapter?.let {
                it.notifyItemChanged(value.index + 2)
                setNumberOfMembersInChatRoom()
            }
        }
    }

    private fun handleUpdatedChatRoomMember(value: ReturnUpdatedChatRoomMemberDataHolder) {

        if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId == value.chatRoomId
            && value.index < sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()
        ) { //if the message is for this chat room and it is in range of the index

            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.logMembers()

            //options should have vanished for user
            applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                    value.index
                )?.otherUsersDataEntity?.accountOID ?: ""
            )

            when (value.typeOfUpdatedOtherUser) {
                TypeOfUpdatedOtherUser.OTHER_USER_UPDATED -> {
                    recyclerViewAdapter?.notifyItemChanged(value.index + 2)
                }
                TypeOfUpdatedOtherUser.OTHER_USER_JOINED -> {
                    recyclerViewAdapter?.notifyItemInserted(value.index + 2)
                }
            }

            setNumberOfMembersInChatRoom()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun handleAccountStateUpdated(accountStateInfo: AccountStateUpdatedDataHolder) {

        if (accountStateInfo.updatedAccountOID == LoginFunctions.currentAccountOID) { //if admin was changed and it involves this user

            //hide any menus because options have changed
            applicationActivity?.hideMenus()

            //NOTE: need to change entire data set here because the admin options need to pop up or be removed
            // also the only 2 transitions this account can be in is ACCOUNT_STATE_IS_ADMIN -> ACCOUNT_STATE_IN_CHAT_ROOM
            // and ACCOUNT_STATE_IN_CHAT_ROOM -> ACCOUNT_STATE_IS_ADMIN
            recyclerViewAdapter?.notifyDataSetChanged()
        } else { //if this user was not the one updated

            //options may have changed for user
            applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(
                accountStateInfo.updatedAccountOID
            )

            val index = sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers
                .getIndexByAccountOID(accountStateInfo.updatedAccountOID)

            if (index.first) { //if successfully retrieved index
                recyclerViewAdapter?.notifyItemChanged(index.second + 2)
            } else {
                val errorMessage =
                    "Unable to properly update user account state from chat room info. User did not exist in list.\n" +
                            "accountStateInfo: $accountStateInfo\n"

                storeErrorChatRoomInfoFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //can continue here
            }
        }
    }

    private fun handleEventOidUpdated() {
        recyclerViewAdapter?.notifyItemChanged(0)
    }

    private fun handleQrCodeUpdate() {
        recyclerViewAdapter?.notifyItemChanged(0)
    }

    private fun handleChatRoomInfoUpdated(result: UpdateChatRoomInfoResultsDataHolder) {
        if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId == result.message.chatRoomId) { //if the chat room matches the passed chat room (just a double check)

            when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(result.message.messageType)) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                    applicationActivity?.setupActivityMenuBars?.setTopToolbarChatRoomName()
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                    recyclerViewAdapter?.mapLoading = false
                }
                else -> {}
            }

            //Will update name, password and/or pinned location depending on what is necessary.
            recyclerViewAdapter?.notifyItemChanged(0)
        }
    }

    private fun handleBlockReportChatRoomResult(result: BlockAndReportChatRoomResultsHolder) {

        //options may have changed for user
        applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(result.accountOID)

        if (result.accountOID == sharedApplicationViewModel.chatRoomContainer.chatRoom.eventId) {
            recyclerViewAdapter?.notifyItemChanged(1)
        } else {
            val member =
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getIndexByAccountOID(
                    result.accountOID
                )

            if (member.first) { //if user exists inside chat room
                recyclerViewAdapter?.notifyItemChanged(member.second + 2)
            }
        }
    }

    private fun setNumberOfMembersInChatRoom() {
        applicationActivity?.setNumberOfMembersInChatRoom()
    }

    private fun storeErrorChatRoomInfoFragment(
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

    @SuppressLint("NotifyDataSetChanged")
    override fun onStart() {
        super.onStart()

        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsChatRoomFragments()

        //data set could have changed (some of the info inside of chatRoomContainer.chatRoom)
        recyclerViewAdapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {

        //need to do this to avoid onClickListeners leaking view
        applicationActivity?.hideMenus()

        _binding = null
        applicationActivity = null
        recyclerViewAdapter = null

        super.onDestroyView()
    }
}