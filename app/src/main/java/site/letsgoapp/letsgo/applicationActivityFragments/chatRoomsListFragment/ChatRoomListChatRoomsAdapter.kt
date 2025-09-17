package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment

import account_state.AccountState
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import lets_go_event_status.LetsGoEventStatusOuterClass.LetsGoEventStatus
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomInfoFragment.ChatRoomInfoAdapter.ChatRoomInfoViewMember
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.messagesDaoSelectFinalMessageString
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import type_of_chat_message.TypeOfChatMessageOuterClass

class ChatRoomListChatRoomsAdapter(
    private val appContext: Context,
    private val chatRooms: MutableList<ChatRoomWithMemberMapDataClass>,
    private val currentUserPicturePath: String,
    private val currentUserPictureTimestamp: Long,
    private val onClickChatRoom: (ChatRoomBasicInfoObject) -> Unit,
    private val onLongClickChatRoom: (chatRoomId: String) -> Unit,
    private val requestMessageInfoFromServer: (chatRoomId: String, messageUUID: String) -> Unit,
    private val errorStore: StoreErrorsInterface
) : RecyclerView.Adapter<ChatRoomListChatRoomsAdapter.ChatRoomViewHolder>() {

    var showAllChatRoomsInList = true
        private set

    private val dividerLineWidthInPixels =
        appContext.resources.getDimension(R.dimen.chat_room_info_fragment_divider_line_height).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder =
        ChatRoomViewHolder(
            LayoutInflater.from(appContext)
                .inflate(R.layout.list_item_chat_room_display, parent, false)
        )

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {

        //this variable should be first to avoid potential callback problems
        holder.chatRoomId = chatRooms[position].chatRoomId

        if (!showAllChatRoomsInList && !chatRooms[position].displayChatRoom) {
            holder.cardView.setOnClickListener(null)
            holder.cardView.setOnLongClickListener(null)
            holder.setViewHolderVisibility(View.GONE)
            return
        } else {
            holder.setViewHolderVisibility(View.VISIBLE)
        }

        holder.chatRoomProgressBar.visibility =
            if (chatRooms[position].showLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        //set most recent message
        //NOTE: these are the only message types that should ever be returned, they are checked for in
        // below links as well
        /**
         * [messagesDaoSelectFinalMessageString]
         * [checkIfMessageTypeFitsFinalChatRoomMessage]
         * **/
        holder.chatRoomLastMessage.text =
            when (val typeOfChatMessage =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    chatRooms[position].finalMessage.message_type
                )) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                    val amountOfMessage =
                        TypeOfChatMessageOuterClass.AmountOfMessage.forNumber(chatRooms[position].finalMessage.amount_of_message)

                    //the only time text_message will require updating is when TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON
                    if (amountOfMessage == TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON) {
                        requestMessageInfoFromServer(
                            chatRooms[position].chatRoomId,
                            chatRooms[position].finalMessage.messageUUIDPrimaryKey
                        )
                        "..."
                    } else {
                        chatRooms[position].finalMessage.message_text
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                    //NOTE: not going to store a picture inside of here, it would require a thumbnail to be sent
                    // back when amountOfMessage == ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE OR for the entire picture
                    // to be sent back, so for memory reasons not going to bother
                    //NOTE: this message will always be able to display
                    appContext.resources.getString(R.string.chat_message_type_text_picture)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
                    //NOTE: this message will always be able to display
                    appContext.resources.getString(R.string.chat_message_type_text_location)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {

                    //NOTE: this message will always be able to display
                    generateMessageForMimeType(
                        appContext,
                        chatRooms[position].finalMessage.message_text
                    )
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
                    //NOTE: this message will always be able to display
                    appContext.resources.getString(R.string.chat_message_type_text_invite)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                -> {

                    var userName = appContext.resources.getString(R.string.User)
                    chatRooms[position].finalMessage.account_id.apply {

                        if (this == LoginFunctions.currentAccountOID) { //if this is the current user
                            userName = appContext.resources.getString(R.string.You_were_space)
                        } else { //if this is a different user
                            chatRooms[position].chatRoomMembers.getFromMap(this)
                                ?.let { member ->
                                    userName = member.otherUsersDataEntity.name
                                }
                        }
                    }

                    if (typeOfChatMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE) {
                        appContext.resources.getString(
                            R.string.chat_message_user_kicked_chat_room,
                            userName
                        )
                    } else {
                        appContext.resources.getString(
                            R.string.chat_message_user_banned_chat_room,
                            userName
                        )
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                    var userName = appContext.resources.getString(R.string.User)
                    chatRooms[position].finalMessage.sent_by_account_id.apply {

                        if (this == LoginFunctions.currentAccountOID) { //if this is the current user
                            userName = appContext.resources.getString(R.string.You)
                        } else { //if this is a different user

                            chatRooms[position].chatRoomMembers.getFromMap(this)
                                ?.let { member ->
                                    userName = member.otherUsersDataEntity.name
                                }
                        }
                    }

                    appContext.resources.getString(
                        R.string.chat_message_different_user_joined_chat_room,
                        userName
                    )
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {

                    var userName = appContext.resources.getString(R.string.User)
                    chatRooms[position].finalMessage.sent_by_account_id.apply {

                        if (this == LoginFunctions.currentAccountOID) { //if this is the current user
                            userName = appContext.resources.getString(R.string.You)
                        } else { //if this is a different user
                            chatRooms[position].chatRoomMembers.getFromMap(this)
                                ?.let { member ->
                                    userName = member.otherUsersDataEntity.name
                                }
                        }
                    }

                    appContext.resources.getString(
                        R.string.chat_message_different_user_left_chat_room,
                        userName
                    )
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                    appContext.resources.getString(R.string.chat_message_chat_room_name_updated)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                    appContext.resources.getString(R.string.chat_message_chat_room_password_updated)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {
                    appContext.resources.getString(R.string.chat_message_new_admin_promoted)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                    appContext.resources.getString(R.string.chat_message_new_pinned_location)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE -> {
                    appContext.resources.getString(R.string.chat_message_history_cleared)
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(-1) -> { //if there were no messages in the chat room (chatRooms[position].finalMessage.messageType will be set to -1)
                    null
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
                -> {
                    val errorMessage = "Invalid last message type.\n" +
                            "finalMessage: $typeOfChatMessage\n"

                    sendError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        chatRooms[position]
                    )

                    null
                    // can continue here
                }
            }

        val start = SystemClock.elapsedRealtime()

        //set up chat room date
        if (chatRooms[position].chatRoomLastActivityTime != -1L) { //if chat room last activity time is enabled
            holder.chatRoomDate.visibility = View.VISIBLE
            holder.chatRoomDate.text =
                formatTimestampDateStringForMessageInfo(
                    chatRooms[position].chatRoomLastActivityTime,
                    errorStore
                )
        }
        else { //if chat room last activity time is not valid
            holder.chatRoomDate.visibility = View.INVISIBLE
            holder.chatRoomDate.text = null

            val errorMessage = "Error properly displaying chat room last active time.\n"

            sendError(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                chatRooms[position]
            )

            //can continue here
        }

        Log.i("tooktimeToDo", "time: ${SystemClock.elapsedRealtime() - start}")

        //set long click listener to display chat room
        holder.cardView.setOnLongClickListener {
            var chatRoomLoading = false

            for (i in chatRooms.indices) {
                if (chatRooms[i].chatRoomId == holder.chatRoomId) {
                    chatRoomLoading = chatRooms[i].showLoading
                    break
                }
            }

            if (chatRoomLoading) {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.chat_room_list_fragment_chat_room_currently_loading,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onLongClickChatRoom(
                    holder.chatRoomId,
                )
            }

            true
        }

        //set click listener to go to chat room
        holder.cardView.setSafeOnClickListener {
            var updatedPosition = -1
            var chatRoomLoading = false

            for (i in chatRooms.indices) {
                if (chatRooms[i].chatRoomId == holder.chatRoomId) {
                    chatRoomLoading = chatRooms[i].showLoading
                    updatedPosition = i
                    break
                }
            }

            if (chatRoomLoading) {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.chat_room_list_fragment_chat_room_currently_loading,
                    Toast.LENGTH_SHORT
                ).show()
            } else if (updatedPosition != -1 && updatedPosition < chatRooms.size) {
                onClickChatRoom(
                    ChatRoomBasicInfoObject(
                        chatRooms[position].chatRoomId,
                        chatRooms[position].chatRoomName,
                        chatRooms[position].chatRoomPassword
                    )
                )
            }
        }

        Log.i("ChatROomList_adap", "a\nchatRoomLastObservedTime: ${chatRooms[position].chatRoomLastObservedTime}\nchatRoomLastActivityTime: ${chatRooms[position].chatRoomLastActivityTime}")

        //set if has unread messages
        holder.chatRoomMessageNotificationSymbol.visibility =
            if (chatRooms[position].chatRoomLastObservedTime < chatRooms[position].chatRoomLastActivityTime) { //if this chat room is a new message
                View.VISIBLE
            } else { //if this chat room does not have a new message
                View.GONE
            }

        var eventChatRoom = false
        var numActiveChatRoomMembers = 0
        var firstActiveUserIndex = -1

        //find number of active users inside chat room (up to 2)
        for (i in 0 until chatRooms[position].chatRoomMembers.size()) {
            chatRooms[position].chatRoomMembers.getFromList(i)?.let { member ->
                Log.i("num_users_chat", "chatRoomId ${chatRooms[position].chatRoomId} account_id: ${member.otherUsersDataEntity.accountOID} account_state: ${member.chatRoom.accountStateInChatRoom}")
                if ((member.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                    || member.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN)
                    && member.otherUsersDataEntity.name != GlobalValues.server_imported_values.adminFirstName
                ) {
                    numActiveChatRoomMembers++

                    if (numActiveChatRoomMembers == 1) {
                        firstActiveUserIndex = i
                    }
                } else if (
                    member.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT
                    && member.otherUsersDataEntity.eventStatus == LetsGoEventStatus.ONGOING.number
                ) {
                    eventChatRoom = true
                }
            }

            if (numActiveChatRoomMembers > 1 || eventChatRoom) {
                break
            }
        }

        //setup picture and chat room name
        if (!eventChatRoom && numActiveChatRoomMembers == 1) { //if only 1 active member inside chat room

            //this will display as if it is a match on a dating app
            val member = chatRooms[position].chatRoomMembers.getFromList(firstActiveUserIndex)
            val thumbnailPath = member?.otherUsersDataEntity?.thumbnailPath

            if (thumbnailPath == null
                || GlobalValues.blockedAccounts[member.otherUsersDataEntity.accountOID]
            ) { //thumbnail path does not exist OR member is blocked
                getGlideContext(holder.chatRoomPicture)
                    .load(currentUserPicturePath)
                    .signature(generateFileObjectKey(currentUserPictureTimestamp))
                    .error(GlobalValues.defaultPictureResourceID)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.chatRoomPicture)

                //set this to the name of the chat room if user blocked
                holder.chatRoomName.text = chatRooms[position].chatRoomName
            } else {

                val signatureTimestamp =
                    member.otherUsersDataEntity.thumbnailLastTimeUpdated

                getGlideContext(holder.chatRoomPicture)
                    .load(thumbnailPath)
                    .signature(generateFileObjectKey(signatureTimestamp))
                    .error(GlobalValues.defaultPictureResourceID)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.chatRoomPicture)

                //set this to the name of the user if just a 1 on 1 conversation and user not blocked
                holder.chatRoomName.text = member.otherUsersDataEntity.name
            }

        }
        else { //if 0 or more than 1 active members inside chat room

            val sentByAccountOID =
                if (!eventChatRoom) {
                    chatRooms[position].finalMessage.sent_by_account_id
                } else {
                    chatRooms[position].eventId
                }

            val member =
                chatRooms[position].chatRoomMembers.getFromMap(sentByAccountOID)

            if (sentByAccountOID == LoginFunctions.currentAccountOID
                || sentByAccountOID.isEmpty()
            ) { //message sent by current account or no messages in this chat room

                getGlideContext(holder.chatRoomPicture)
                    .load(currentUserPicturePath)
                    .signature(generateFileObjectKey(currentUserPictureTimestamp))
                    .error(GlobalValues.defaultPictureResourceID)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.chatRoomPicture)

            } else { //message sent by different user

                val thumbnailPath = member?.otherUsersDataEntity?.thumbnailPath

                if (thumbnailPath == null
                    || GlobalValues.blockedAccounts[member.otherUsersDataEntity.accountOID]
                ) { //thumbnail path does not exist OR member is blocked
                    getGlideContext(holder.chatRoomPicture)
                        .load(R.drawable.lets_go_logo)
                        .error(GlobalValues.defaultPictureResourceID)
                        .apply(RequestOptions.circleCropTransform())
                        .into(holder.chatRoomPicture)
                } else {

                    val signatureTimestamp =
                        member.otherUsersDataEntity.thumbnailLastTimeUpdated

                    getGlideContext(holder.chatRoomPicture)
                        .load(thumbnailPath)
                        .signature(generateFileObjectKey(signatureTimestamp))
                        .error(GlobalValues.defaultPictureResourceID)
                        .apply(RequestOptions.circleCropTransform())
                        .into(holder.chatRoomPicture)
                }

            }

            holder.chatRoomName.text = chatRooms[position].chatRoomName
        }
    }

    private fun getGlideContext(imageView: ImageView): RequestManager {
        //Make sure to use the imageView in with(), otherwise if the recycler view is updated immediately before
        // the user navigates away. The fragment inside the FragmentContainerView
        // will not be destroyed (through GlideApp.with(fragment).onDestroy()) and so Glide can retain the
        // entire layout for a while. This happens regularly when using joinChatRoom(), chatRoomAddedRemovedObserver
        // will return a new chat room, and this can 'leak'. However inside Glide (ctrl click with and look at the
        // .with() that is called inside the wrapper function) it says to not use .with(imageView) much. So only using it inside
        // this recycler view, not other FragmentContainerView around the app because it is a trivially reproducible
        // problem here.
        return GlideApp.with(imageView)
    }

    fun setShowAllChatRoomsInList(value: Boolean): Boolean {
        val previousValue = showAllChatRoomsInList
        showAllChatRoomsInList = value
        return previousValue
    }

    override fun getItemCount(): Int {
        return chatRooms.size
    }

    private fun sendError(
        errorMessage: String,
        lineNumber: Int,
        stackTrace: String,
        chatRoom: ChatRoomWithMemberMapDataClass
    ) {
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
            appContext.applicationContext
        )
    }

    inner class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var chatRoomId = ""

        val cardView: CardView =
            itemView.findViewById(R.id.chatRoomDisplayItemPrimaryLayoutCardView)
        private val primaryConstraintLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatRoomDisplayItemPrimaryConstrainLayout)

        val chatRoomPicture: ImageView = itemView.findViewById(R.id.chatRoomDisplayItemImageView)
        val chatRoomName: TextView = itemView.findViewById(R.id.chatRoomDisplayItemNameTextView)
        val chatRoomLastMessage: TextView =
            itemView.findViewById(R.id.chatRoomDisplayItemMessageTextView)
        val chatRoomDate: TextView = itemView.findViewById(R.id.chatRoomDisplayItemDateTextView)
        val chatRoomMessageNotificationSymbol: ImageView =
            itemView.findViewById(R.id.chatRoomDisplayMessageNotificationView)

        val chatRoomProgressBar: ProgressBar =
            itemView.findViewById(R.id.chatRoomDisplayItemProgressBar)

        fun setViewHolderVisibility(visibility: Int) {
            /**
             * The reason the visibility is modified on the ConstraintLayout instead of the CardView is described here.
             * [ChatRoomInfoViewMember.hideItemView]
             * **/
            primaryConstraintLayout.visibility = visibility
            val marginParams = (cardView.layoutParams as ViewGroup.MarginLayoutParams)
            marginParams.bottomMargin = 0
            marginParams.topMargin =
                if (visibility == View.GONE) {
                    0
                } else {
                    dividerLineWidthInPixels
                }
        }

    }

}