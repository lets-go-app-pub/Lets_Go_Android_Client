package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomInfoFragment

import account_state.AccountState
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import grpc_chat_commands.ChatRoomCommands
import lets_go_event_status.LetsGoEventStatusOuterClass.LetsGoEventStatus
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUserChatRoomInfo
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersInfo
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import user_account_type.UserAccountTypeOuterClass.UserAccountType

class ChatRoomInfoAdapter(
    private val context: Context,
    private val userName: String,
    private val glideContext: RequestManager,
    private val currentUserPicturePath: String,
    private val currentUserPictureTimestamp: Long,
    private val chatRoom: ChatRoomWithMemberMapDataClass,
    private val childFragmentManager: FragmentManager,
    private val navigateToMemberInfo: (Int) -> Unit,
    private val setChatRoomNotifications: (Boolean) -> Unit,
    private val showBlockEventPopupMenu: (View, String) -> Unit,
    private val showUnblockEventPopupMenu: (View, String) -> Unit,
    private val showUserNoAdminBlockAndReportPopupMenu: (View, String, String) -> Unit,
    private val showUserNoAdminUnblockPopupMenu: (View, String, String) -> Unit,
    private val showUserAdminBlockAndReportPopupMenu: (View, String, String) -> Unit,
    private val showUserAdminUnblockPopupMenu: (View, String, String) -> Unit,
    private val setChatRoomInfo: (String, ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate) -> Unit,
    private val selectNewPinnedLocationMessage: () -> Unit,
    private val removePinnedLocationMessage: () -> Unit,
    private val navigateToQrCode: () -> Unit,
    removeChatRoomQrCode: () -> Unit,
    private val errorStore: StoreErrorsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class LayoutTypes {
        LAYOUT_HEADER,
        LAYOUT_MEMBER
    }

    private val dividerLineWidthInPixels =
        context.resources.getDimension(R.dimen.chat_room_info_fragment_divider_line_height).toInt()

    var pinnedLocationProgressBarLoading = false
    var mapLoading = false

    //the + 2 on size
    //position 0 is the header
    //position 1 is the user
    override fun getItemCount(): Int =
        chatRoom.chatRoomMembers.size() + 2

    private val eventChatRoom = chatRoom.eventId.isValidMongoDBOID()
    private val eventIsOngoing: Boolean

    init {
        val eventAccount = chatRoom.chatRoomMembers.getFromMap(
            chatRoom.eventId
        )
        Log.i("activities_stuff", "activities ${eventAccount?.activities}")
        eventIsOngoing =
            if (eventAccount != null && eventAccount.otherUsersDataEntity.eventStatus == LetsGoEventStatus.ONGOING.number) {
                var allTimeframesExpired = true
                outer@ for (activity in eventAccount.activities) {
                    for (timeframe in activity.timeFrameArrayList) {
                        if (getCurrentTimestampInMillis() < timeframe.stopTimeFrame) {
                            allTimeframesExpired = false
                            break@outer
                        }
                    }
                }
                if (allTimeframesExpired && chatRoom.qrCodePath != GlobalValues.server_imported_values.qrCodeDefault) {
                    removeChatRoomQrCode()
                }
                !allTimeframesExpired
            } else {
                false
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == LayoutTypes.LAYOUT_HEADER.ordinal) {
            ChatRoomInfoHeader(
                LayoutInflater.from(context)
                    .inflate(R.layout.list_item_chat_room_info_header, parent, false),
            )
        } else {
            ChatRoomInfoViewMember(
                LayoutInflater.from(context)
                    .inflate(R.layout.list_item_chat_member_info, parent, false),
                dividerLineWidthInPixels
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            LayoutTypes.LAYOUT_HEADER.ordinal
        } else {
            LayoutTypes.LAYOUT_MEMBER.ordinal
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        //inflate different layouts depending if this is the header or a member
        if (holder.itemViewType == LayoutTypes.LAYOUT_HEADER.ordinal) { //if this is the header
            handleViewHeader(holder as ChatRoomInfoHeader)
        } else { //if this is a member
            handleViewMember(holder as ChatRoomInfoViewMember, position - 2)
        }
    }

    private fun handleViewHeader(holder: ChatRoomInfoHeader) {

        holder.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            holder.notificationState.text =
                if (isChecked) {
                    context.resources.getString(R.string.On)
                } else {
                    context.resources.getString(R.string.Off)
                }

            setChatRoomNotifications(isChecked)
        }

        holder.notificationSwitch.isChecked = chatRoom.notificationsEnabled

        if (chatRoom.chatRoomId != "~") { //if chat room is set

            //set chat room Id
            holder.chatRoomIdBody.text = chatRoom.chatRoomId

            //set chat room name
            holder.chatRoomNameTitle.text =
                if (chatRoom.chatRoomName != "") { //if name set
                    holder.chatRoomNameBody.text = chatRoom.chatRoomName
                    holder.chatRoomNameBody.visibility = View.VISIBLE
                    context.resources.getString(R.string.chat_room_info_fragment_name)
                } else { //if no name set
                    holder.chatRoomNameBody.text = null
                    holder.chatRoomNameBody.visibility = View.INVISIBLE
                    context.resources.getString(R.string.chat_room_info_fragment_no_name)
                }

            //set chat room password
            holder.chatRoomPasswordTitle.text =
                if (chatRoom.chatRoomPassword != "") { //if password set
                    holder.chatRoomPasswordBody.text = chatRoom.chatRoomPassword
                    holder.chatRoomPasswordBody.visibility = View.VISIBLE
                    context.resources.getString(R.string.chat_room_info_fragment_password)
                } else { //if no password set
                    holder.chatRoomPasswordBody.text = null
                    holder.chatRoomPasswordBody.visibility = View.INVISIBLE
                    context.resources.getString(R.string.chat_room_info_fragment_no_password)
                }

            if (chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN) { //if this account is admin

                //show editable picture
                holder.chatRoomNameImageView.visibility = View.VISIBLE
                holder.chatRoomNameBody.setPadding(
                    holder.chatRoomNameBody.paddingLeft,
                    holder.chatRoomNameBody.paddingTop,
                    0,
                    holder.chatRoomNameBody.paddingBottom
                )

                holder.chatRoomNameImageView.setSafeOnClickListener {
                    NewChatRoomInfoPassDialog(
                        context.resources.getString(
                            R.string.chat_room_info_fragment_change_info_dialog_title,
                            context.resources.getString(R.string.name)
                        ),
                        context.resources.getString(
                            R.string.chat_room_info_fragment_change_info_dialog_hint,
                            context.resources.getString(R.string.name)
                        ),
                        chatRoom.chatRoomName,
                        true
                    ) { newName ->
                        setChatRoomInfo(
                            newName,
                            ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_NAME
                        )
                    }.show(childFragmentManager, "chat_room_name")
                }

                //show editable picture
                holder.chatRoomPasswordImageView.visibility = View.VISIBLE
                holder.chatRoomPasswordBody.setPadding(
                    holder.chatRoomPasswordBody.paddingLeft,
                    holder.chatRoomPasswordBody.paddingTop,
                    0,
                    holder.chatRoomPasswordBody.paddingBottom
                )

                holder.chatRoomPasswordImageView.setSafeOnClickListener {
                    NewChatRoomInfoPassDialog(
                        context.resources.getString(
                            R.string.chat_room_info_fragment_change_info_dialog_title,
                            context.resources.getString(R.string.password)
                        ),
                        context.resources.getString(
                            R.string.chat_room_info_fragment_change_info_dialog_hint,
                            context.resources.getString(R.string.password)
                        ),
                        chatRoom.chatRoomPassword,
                        true
                    ) { newPassword ->
                        setChatRoomInfo(
                            newPassword,
                            ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_PASSWORD
                        )
                    }.show(childFragmentManager, "chat_room_password")
                }

                holder.chatRoomPinnedLocationLayout.visibility = View.VISIBLE
                holder.chatRoomPinnedLocationEditLayout.visibility = View.VISIBLE

                val constraintLayout =
                    holder.chatRoomPinnedLocationTitle.layoutParams as ConstraintLayout.LayoutParams
                constraintLayout.endToEnd = ConstraintLayout.LayoutParams.UNSET
                holder.chatRoomPinnedLocationTitle.layoutParams = constraintLayout

                holder.chatRoomPinnedLocationImageView.setSafeOnClickListener {
                    holder.setEnabledProgressBar(true)
                    pinnedLocationProgressBarLoading = true
                    selectNewPinnedLocationMessage()
                }

                holder.chatRoomPinnedLocationRemoveMapImageView.setSafeOnClickListener {
                    mapLoading = true
                    holder.chatRoomPinnedLocationMapViewLayout.visibility = View.VISIBLE
                    holder.chatRoomPinnedLocationMapView.visibility = View.GONE
                    holder.chatRoomPinnedLocationRemoveMapImageView.visibility = View.GONE

                    removePinnedLocationMessage()
                }

                if (
                    isValidLocation(
                        chatRoom.pinnedLocationLongitude,
                        chatRoom.pinnedLocationLatitude,
                    )
                ) {
                    holder.chatRoomPinnedLocationRemoveMapImageView.visibility = View.VISIBLE
                    displayValidPinnedLocation(holder)
                } else {
                    holder.chatRoomPinnedLocationMapViewLayout.visibility = View.GONE
                }

                Log.i(
                    "mapLoading_var",
                    "mapLoading: $mapLoading long: ${chatRoom.pinnedLocationLongitude} lat: ${chatRoom.pinnedLocationLatitude}"
                )
                //This must come last, it will overwrite the 'correct' setup above.
                if (mapLoading) {
                    holder.chatRoomPinnedLocationMapViewLayout.visibility = View.VISIBLE
                    holder.chatRoomPinnedLocationMapView.visibility = View.GONE
                    holder.chatRoomPinnedLocationRemoveMapImageView.visibility = View.GONE
                }

                holder.setEnabledProgressBar(pinnedLocationProgressBarLoading)

            } else { //if this account is not admin

                //hide editable picture
                holder.chatRoomNameImageView.visibility = View.GONE
                holder.chatRoomNameBody.setPadding(
                    holder.chatRoomNameBody.paddingLeft,
                    holder.chatRoomNameBody.paddingTop,
                    holder.chatRoomNameImageView.paddingRight,
                    holder.chatRoomNameBody.paddingBottom
                )

                holder.chatRoomNameImageView.setOnClickListener(null)

                //hide editable picture
                holder.chatRoomPasswordImageView.visibility = View.GONE
                holder.chatRoomPasswordBody.setPadding(
                    holder.chatRoomPasswordBody.paddingLeft,
                    holder.chatRoomPasswordBody.paddingTop,
                    holder.chatRoomPasswordImageView.paddingRight,
                    holder.chatRoomPasswordBody.paddingBottom
                )

                holder.chatRoomPasswordImageView.setOnClickListener(null)

                if (
                    isValidLocation(
                        chatRoom.pinnedLocationLongitude,
                        chatRoom.pinnedLocationLatitude,
                    )
                ) {
                    holder.chatRoomPinnedLocationImageView.setOnClickListener(null)
                    holder.chatRoomPinnedLocationRemoveMapImageView.setOnClickListener(null)

                    holder.chatRoomPinnedLocationRemoveMapImageView.visibility = View.GONE
                    holder.chatRoomPinnedLocationLayout.visibility = View.VISIBLE
                    holder.chatRoomPinnedLocationEditLayout.visibility = View.INVISIBLE

                    val constraintLayout =
                    holder.chatRoomPinnedLocationTitle.layoutParams as ConstraintLayout.LayoutParams
                    constraintLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

                    holder.chatRoomPinnedLocationTitle.layoutParams = constraintLayout

                    displayValidPinnedLocation(holder)
                } else {
                    holder.chatRoomPinnedLocationLayout.visibility = View.GONE
                }
            }

            Log.i("event_qr_code", "eventIsOngoing: $eventIsOngoing qrCodePath ${chatRoom.qrCodePath}")
            if (eventIsOngoing && chatRoom.qrCodePath != GlobalValues.server_imported_values.qrCodeDefault) {
                holder.qRCodeLayout.visibility = View.VISIBLE
                holder.qRCodeImageView.visibility = View.VISIBLE

                holder.qRCodeLayout.setSafeOnClickListener {
                    navigateToQrCode()
                }
            } else {
                holder.qRCodeLayout.visibility = View.GONE
                holder.qRCodeLayout.setOnClickListener(null)
                holder.qRCodeImageView.visibility = View.GONE
            }

        } else {
            val errorMessage =
                "ChatRoom is NOT set when the Header item is being set up inside the chat room info fragment.\n"

            sendError(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            holder.chatRoomIdBody.text = null

            holder.chatRoomNameBody.text = null
            holder.chatRoomNameImageView.setOnClickListener(null)
            holder.chatRoomNameImageView.visibility = View.GONE

            holder.chatRoomPasswordBody.text = null
            holder.chatRoomPasswordImageView.setOnClickListener(null)
            holder.chatRoomPasswordImageView.visibility = View.GONE

            holder.chatRoomPinnedLocationLayout.visibility = View.GONE
            holder.chatRoomPinnedLocationLayout.setOnClickListener(null)
            holder.chatRoomPinnedLocationImageView.visibility = View.GONE

            holder.qRCodeLayout.visibility = View.GONE
            holder.qRCodeLayout.setOnClickListener(null)
            holder.qRCodeImageView.visibility = View.GONE
        }
    }

    private fun displayValidPinnedLocation(holder: ChatRoomInfoHeader) {
        holder.chatRoomPinnedLocationMapViewLayout.visibility = View.VISIBLE
        holder.chatRoomPinnedLocationMapView.visibility = View.VISIBLE

        holder.pinnedLocationLatLng = LatLng(
            chatRoom.pinnedLocationLatitude,
            chatRoom.pinnedLocationLongitude
//                        37.3773622787,
//                        -122.099088292
        )

        try {
            Log.i("map_stuff_z", "running getMapAsync()")
            holder.chatRoomPinnedLocationMapViewProgressBar.visibility = View.VISIBLE
            holder.chatRoomPinnedLocationMapView.onCreate(null)
            holder.chatRoomPinnedLocationMapView.getMapAsync(holder)
        } catch (e: Resources.NotFoundException) {
            Log.i("map_stuff_z", "exception ${e.message}")
            //Can get a Resources$NotFoundException exception during building. In development
            // can rebuild or invalidate cache and it will work. Want to make sure it doesn't
            // happen when in production.

            val errorMessage =
                "Received an exception when starting a MapView in chat room info adapter.\n" +
                        "In development can rebuild or invalidate cache and it will work. However, " +
                        "it should not happen when in production.\n" +
                        "exceptionMessage: ${e.message}"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                e.stackTraceToString(),
                errorMessage
            )

            holder.chatRoomPinnedLocationLayout.visibility = View.GONE
            holder.chatRoomPinnedLocationMapViewProgressBar.visibility = View.GONE
            holder.chatRoomPinnedLocationMapViewLayout.visibility = View.GONE
        }
    }

    private fun handleViewMember(holder: ChatRoomInfoViewMember, position: Int) {

        val currentUser = (!eventChatRoom && position == -1) || (eventChatRoom && position == 0)
        val eventAccount = eventChatRoom && position == -1

        //NOTE: the header takes position 0, then the first position is to be this user
        val memberInfo =
            if (currentUser) {
                //set this to be the current user
                //NOTE: the current user is not clickable, so most of the info is not needed

                OtherUsersInfo(
                    OtherUsersDataEntity(
                        LoginFunctions.currentAccountOID,
                        currentUserPicturePath,
                        -1, //not used
                        currentUserPictureTimestamp,
                        "",
                        "",
                        -1.0,
                        -1L,
                        -1L,
                        "",
                        -1,
                        userName
                    ),
                    OtherUserChatRoomInfo(
                        chatRoom.chatRoomId,
                        chatRoom.accountState,
                        chatRoom.userLastActivityTime
                    ),
                    mutableListOf(),
                    mutableListOf()
                )
            } else if (eventAccount) {
                //The chatRoomMembers list should be sorted with the event first.
                chatRoom.chatRoomMembers.getFromList(0)
            } else { //if the position is 0 or above
                chatRoom.chatRoomMembers.getFromList(position)
            }

        Log.i(
            "handle_view_member",
            "eventChatRoom: $eventChatRoom position: $position accountType: ${memberInfo?.otherUsersDataEntity?.accountType}"
        )

        //will be set to null if user is NOT blocked, will be set to Unit (not null) if user is blocked
        val userBlocked = GlobalValues.blockedAccounts[memberInfo?.otherUsersDataEntity?.accountOID]

        if (memberInfo != null) {

            if (memberInfo.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT
                && !eventIsOngoing
            ) {
                holder.memberInfoListPrimaryLayout.visibility = View.GONE
                holder.eventFinishedTextView.visibility = View.VISIBLE
                holder.eventFinishedTextView.setText(
                    if (memberInfo.otherUsersDataEntity.eventStatus == LetsGoEventStatus.CANCELED.number) {
                        R.string.chat_member_list_item_event_canceled_text
                    } else {
                        R.string.chat_member_list_item_event_completed_text
                    }
                )
                return
            } else {
                holder.memberInfoListPrimaryLayout.visibility = View.VISIBLE
                holder.eventFinishedTextView.visibility = View.GONE
            }

            holder.nameTextView.text =
                if (memberInfo.otherUsersDataEntity.name == GlobalValues.server_imported_values.adminFirstName) {
                    displayEventAdminName(memberInfo.otherUsersDataEntity.name)
                } else {
                    memberInfo.otherUsersDataEntity.name
                }

            if (!currentUser) { //if this member is not the current user

                if (!userBlocked) {
                    holder.cardView.setSafeOnClickListener {
                        //navigate to the user info page
                        navigateToMemberInfo(
                            if (eventAccount) {
                                0
                            } else {
                                position
                            }
                        )
                    }
                } else {
                    holder.cardView.setOnClickListener(null)
                    holder.cardView.isClickable = false
                }

                holder.menuFrameLayout.visibility = View.VISIBLE

                //set popup menu options
                val onClickListener =
                    when {
                        memberInfo.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number
                                && !userBlocked -> { //current user is event and not blocked
                            SafeClickListener {
                                showBlockEventPopupMenu(
                                    holder.menuFrameLayout,
                                    chatRoom.eventId
                                )
                            }
                        }
                        memberInfo.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number
                                && userBlocked -> { //current user is event and blocked
                            SafeClickListener {
                                showUnblockEventPopupMenu(
                                    holder.menuFrameLayout,
                                    chatRoom.eventId
                                )
                            }
                        }
                        chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                                && !userBlocked -> { //current user is admin and displayed member is not blocked

                            //display options menu
                            SafeClickListener {
                                showUserAdminBlockAndReportPopupMenu(
                                    holder.menuFrameLayout,
                                    memberInfo.otherUsersDataEntity.accountOID,
                                    memberInfo.otherUsersDataEntity.name
                                )
                            }
                        }
                        chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                                && userBlocked -> { //current user is admin and displayed member is blocked

                            //display options menu
                            SafeClickListener {
                                showUserAdminUnblockPopupMenu(
                                    holder.menuFrameLayout,
                                    memberInfo.otherUsersDataEntity.accountOID,
                                    memberInfo.otherUsersDataEntity.name
                                )
                            }
                        }
                        !userBlocked -> { //current user is NOT admin and displayed member is not blocked

                            //display options menu
                            SafeClickListener {
                                showUserNoAdminBlockAndReportPopupMenu(
                                    holder.menuFrameLayout,
                                    memberInfo.otherUsersDataEntity.accountOID,
                                    memberInfo.otherUsersDataEntity.name
                                )
                            }
                        }
                        else -> {  //current user is NOT admin and displayed member is blocked

                            //display options menu
                            SafeClickListener {
                                showUserNoAdminUnblockPopupMenu(
                                    holder.menuFrameLayout,
                                    memberInfo.otherUsersDataEntity.accountOID,
                                    memberInfo.otherUsersDataEntity.name
                                )
                            }
                        }
                    }

                //want both the 3 dots AND the admin name be be clickable
                holder.memberStatusTextView.setSafeOnClickListener(onClickListener)
                holder.menuFrameLayout.setSafeOnClickListener(onClickListener)

            } else { //if this member is the current user
                holder.memberStatusTextView.setOnClickListener(null)
                holder.menuFrameLayout.visibility = View.GONE
            }

            when (memberInfo.chatRoom.accountStateInChatRoom) {
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM,
                -> {

                    if (userBlocked) { //if user is blocked
                        holder.blockedTextView.visibility = View.VISIBLE
                        holder.thumbnailImageView.visibility = View.GONE
                    } else { //if user is not blocked
                        holder.blockedTextView.visibility = View.GONE
                        holder.thumbnailImageView.visibility = View.VISIBLE

                        val thumbnailPath = memberInfo.otherUsersDataEntity.thumbnailPath

                        glideContext
                            .load(thumbnailPath)
                            .signature(generateFileObjectKey(memberInfo.otherUsersDataEntity.thumbnailLastTimeUpdated))
                            .error(GlobalValues.defaultPictureResourceID)
                            .apply(RequestOptions.circleCropTransform())
                            .into(holder.thumbnailImageView)
                    }

                    holder.showItemView()

                    when (memberInfo.chatRoom.accountStateInChatRoom) {
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT -> {
                            when (UserAccountType.forNumber(memberInfo.otherUsersDataEntity.accountType)) {
                                UserAccountType.ADMIN_GENERATED_EVENT_TYPE -> {
                                    holder.memberStatusTextView.visibility = View.VISIBLE
                                    holder.memberStatusTextView.text =
                                        context.resources.getString(R.string.local_event)
                                }
                                UserAccountType.USER_GENERATED_EVENT_TYPE -> {
                                    holder.memberStatusTextView.visibility = View.VISIBLE
                                    holder.memberStatusTextView.text =
                                        context.resources.getString(R.string.user_event)
                                }
                                UserAccountType.UNKNOWN_ACCOUNT_TYPE,
                                UserAccountType.USER_ACCOUNT_TYPE,
                                UserAccountType.UNRECOGNIZED,
                                null -> {
                                    holder.memberStatusTextView.visibility = View.GONE
                                    holder.memberStatusTextView.text = null

                                    val errorMessage =
                                        "Invalid account type passed to ChatRoomInfoAdapter as an event account.\n" +
                                                "accountState: ${memberInfo.chatRoom.accountStateInChatRoom}\n" +
                                                "accountType: ${UserAccountType.forNumber(memberInfo.otherUsersDataEntity.accountType)}\n"

                                    sendError(
                                        errorMessage,
                                        Thread.currentThread().stackTrace[2].lineNumber,
                                        printStackTraceForErrors()
                                    )

                                    //ok to continue here
                                }
                            }
                        }
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM -> {
                            holder.memberStatusTextView.visibility = View.GONE
                            holder.memberStatusTextView.text = null
                        }
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN -> {
                            holder.memberStatusTextView.visibility = View.VISIBLE
                            holder.memberStatusTextView.text =
                                context.resources.getString(R.string.Admin)
                        }
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED,
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM,
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_SUPER_ADMIN,
                        AccountState.AccountStateInChatRoom.UNRECOGNIZED -> {
                            holder.memberStatusTextView.visibility = View.GONE
                            holder.memberStatusTextView.text = null

                            val errorMessage =
                                "Invalid account state passed to ChatRoomInfoAdapter.\n" +
                                        "accountState: ${memberInfo.chatRoom.accountStateInChatRoom}\n"

                            sendError(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            //ok to continue here
                        }
                    }

                    //update the last seen time text
                    when {
                        userBlocked || currentUser -> { //if this is current user or user is blocked
                            holder.lastSeenTimeTextView.visibility = View.INVISIBLE
                            holder.lastSeenTimeTextView.text = null
                        }
                        eventAccount -> { //if event
                            holder.lastSeenTimeTextView.visibility = View.INVISIBLE
                            holder.lastSeenTimeTextView.text = null
                        }
                        memberInfo.chatRoom.lastActiveTimeInChatRoom != -1L -> { //if the last active time is valid
                            holder.lastSeenTimeTextView.visibility = View.VISIBLE
                            holder.lastSeenTimeTextView.text =
                                formatTimestampDateStringForChatRoomInfo(
                                    memberInfo.chatRoom.lastActiveTimeInChatRoom,
                                    errorStore
                                )
                        }
                        else -> { //if invalid last active time
                            holder.lastSeenTimeTextView.visibility = View.INVISIBLE
                            holder.lastSeenTimeTextView.text = null

                            val errorMessage =
                                "Invalid last active time in chat room attached to member.\n" +
                                        "position: $position\n" +
                                        "lastActiveTimeInChatRoom: ${memberInfo.chatRoom.lastActiveTimeInChatRoom}\n"

                            sendError(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            //can continue
                        }
                    }
                }
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_SUPER_ADMIN,
                AccountState.AccountStateInChatRoom.UNRECOGNIZED -> {
                    holder.hideItemView()
                }
            }
        } else { //Failed to find member info

            val errorMessage =
                "memberInfo returned null, this means that an invalid position was passed to chatRoom.chatRoomMembers.\n" +
                        "position: $position"

            sendError(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            holder.hideItemView()
        }
    }

    private fun sendError(errorMessage: String, lineNumber: Int, stackTrace: String) {
        val errorMsg = "\n" + errorMessage + "\n" +
                "chatRoomId: ${chatRoom.chatRoomId}\n" +
                "chatRoomName: ${chatRoom.chatRoomName}\n" +
                "chatRoomPassword: ${chatRoom.chatRoomPassword}\n" +
                "notificationsEnabled: ${chatRoom.notificationsEnabled}\n" +
                "accountState: ${chatRoom.accountState}\n" +
                "chatRoomMembers.size: ${chatRoom.chatRoomMembers.size()}\n" +
                "timeJoined: ${chatRoom.timeJoined}\n" +
                "matchingChatRoomOID: ${chatRoom.matchingChatRoomOID}\n" +
                "chatRoomLastObservedTime: ${chatRoom.chatRoomLastObservedTime}\n" +
                "userLastActivityTime: ${chatRoom.userLastActivityTime}\n" +
                "chatRoomLastActivityTime: ${chatRoom.chatRoomLastActivityTime}\n" +
                "lastTimeUpdated: ${chatRoom.lastTimeUpdated}\n" +
                "finalMessage: ${chatRoom.finalMessage}\n" +
                "finalPictureMessage: ${chatRoom.finalPictureMessage}\n" +
                "displayChatRoom: ${chatRoom.displayChatRoom}\n" +
                "showLoading: ${chatRoom.showLoading}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorMsg,
            context.applicationContext
        )
    }

    class ChatRoomInfoHeader(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView),
        OnMapReadyCallback {

        val notificationState: TextView =
            itemView.findViewById(R.id.chatRoomInfoNotificationStateTextView)
        val notificationSwitch: SwitchCompat =
            itemView.findViewById(R.id.modifyAlgorithmMatchOptionsStateSwitchCompat)

        val chatRoomIdBody: TextView = itemView.findViewById(R.id.chatRoomInfoIdBodyTextView)

        val chatRoomNameTitle: TextView = itemView.findViewById(R.id.chatRoomInfoNameTitleTextView)
        val chatRoomNameBody: TextView = itemView.findViewById(R.id.chatRoomInfoNameBodyTextView)
        val chatRoomNameImageView: ImageView = itemView.findViewById(R.id.chatRoomInfoNameImageView)

        val chatRoomPasswordTitle: TextView =
            itemView.findViewById(R.id.chatRoomInfoPasswordTitleTextView)
        val chatRoomPasswordBody: TextView =
            itemView.findViewById(R.id.chatRoomInfoPasswordBodyTextView)
        val chatRoomPasswordImageView: ImageView =
            itemView.findViewById(R.id.chatRoomInfoPasswordImageView)

        val chatRoomPinnedLocationLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationConstraintLayout)
        val chatRoomPinnedLocationEditLayout: FrameLayout =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationImageViewFrameLayout)
        val chatRoomPinnedLocationTitle: TextView =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationTitleTextView)
        private val chatRoomPinnedLocationProgressBar: ProgressBar =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationImageViewProgressBar)
        val chatRoomPinnedLocationImageView: ImageView =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationImageView)

        val chatRoomPinnedLocationMapViewLayout: FrameLayout =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationMapViewFrameLayout)
        val chatRoomPinnedLocationRemoveMapImageView: ImageView =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationMapViewImageView)
        val chatRoomPinnedLocationMapView: MapView =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationMapView)
        val chatRoomPinnedLocationMapViewProgressBar: ProgressBar =
            itemView.findViewById(R.id.chatRoomInfoPinnedLocationMapViewProgressBar)

        val qRCodeLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatRoomInfoQrCodeConstraintLayout)
        val qRCodeImageView: ImageView =
            itemView.findViewById(R.id.chatRoomInfoQrCodeImageView)

        var pinnedLocationLatLng = LatLng(
            GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
            GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
        )

        override fun onMapReady(googleMap: GoogleMap) {
            Log.i("map_stuff_z", "onMapReady() called")
            MapsInitializer.initialize(applicationContext)

            chatRoomPinnedLocationMapViewProgressBar.visibility = View.GONE

            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pinnedLocationLatLng,
                    GlobalValues.MAP_VIEW_INITIAL_ZOOM
                )
            )
            googleMap.addMarker(MarkerOptions().position(pinnedLocationLatLng))
            googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        }

        fun setEnabledProgressBar(enabled: Boolean) {
            if (enabled) {
                chatRoomPinnedLocationProgressBar.visibility = View.VISIBLE
                chatRoomPinnedLocationImageView.visibility = View.INVISIBLE
            } else {
                chatRoomPinnedLocationProgressBar.visibility = View.INVISIBLE
                chatRoomPinnedLocationImageView.visibility = View.VISIBLE
            }
        }
    }

    class ChatRoomInfoViewMember(
        itemView: View,
        private val dividerLineWidthInPixels: Int
    ) : RecyclerView.ViewHolder(itemView) {

        val cardView: CardView =
            itemView.findViewById(R.id.chatMemberInfoListPrimaryLayoutCardView)
        val thumbnailImageView: ImageView =
            itemView.findViewById(R.id.chatMemberInfoListThumbnailImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.chatMemberInfoListUserNameTextView)
        val lastSeenTimeTextView: TextView =
            itemView.findViewById(R.id.chatMemberInfoListLastTimeSeenTextView)
        val memberStatusTextView: TextView =
            itemView.findViewById(R.id.chatMemberInfoListMemberStatusTextView)
        val blockedTextView: TextView =
            itemView.findViewById(R.id.chatMemberInfoBlockedTextView)

        val menuFrameLayout: FrameLayout =
            itemView.findViewById(R.id.chatMemberInfoListMenuIconFrameLayout)

        val eventFinishedTextView: TextView =
            itemView.findViewById(R.id.chatMemberInfoListEventTextView)
        val memberInfoListPrimaryLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatMemberInfoListPrimaryConstraintLayout)

        fun showItemView() {
            cardView.visibility = View.VISIBLE
            cardView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val marginParams = (cardView.layoutParams as ViewGroup.MarginLayoutParams)
            marginParams.bottomMargin = 0
            marginParams.topMargin = dividerLineWidthInPixels
        }

        fun hideItemView() {
            //NOTE: this could also be done in a way where a different layout type is added to
            // LayoutTypes of LAYOUT_GONE or something, however if that method is used the recycler
            // view will not be able to recycle views, it will need to re-create them
            //NOTE: layout parameters are changed here (set height and width to 0) it is worth mentioning that
            // there was a bug inside of ChatRoomInfoAdapter where when height was 0 the recycler view had
            // some problems with detecting the bottom
            //NOTE: (obsolete NOTE, setting the cardView which is the itemView to View.GONE)
            // setting the primaryConstraintLayout to GONE instead of the cardLayout or the itemView
            // this is because when using notifyItemChanged() instead of notifyItemRemoved() (because the user
            // account state is updated to NOT_IN_CHAT_ROOM) the item is not actually removed from the
            // recyclerView and so a space is visibly held for it on the UI, so by setting the nested layout
            // primaryConstraintLayout to GONE instead it will properly hide the layout from the user
            cardView.visibility = View.GONE
            cardView.layoutParams = RecyclerView.LayoutParams(0, 0)
            val marginParams = (cardView.layoutParams as ViewGroup.MarginLayoutParams)
            marginParams.bottomMargin = 0
            marginParams.topMargin = 0
        }
    }
}