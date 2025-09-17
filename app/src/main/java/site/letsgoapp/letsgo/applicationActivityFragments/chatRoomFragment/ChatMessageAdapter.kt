package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment

import account_state.AccountState
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.TileProvider.NO_TILE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomInfoFragment.ChatRoomInfoAdapter.ChatRoomInfoViewMember
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.ChatMessageStoredStatus
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersInfo
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities.ChatRoomContainer
import type_of_chat_message.TypeOfChatMessageOuterClass
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

var identifier = 0

data class MessagesDataEntityWithAdditionalInfo(
    var messageDataEntity: MessagesDataEntity,
    var messageUpdateHasBeenRequestedFromServer: Boolean,
    var messageLayoutType: LayoutType,
) {
    constructor(messagesDataEntity: MessagesDataEntity) : this(
        messagesDataEntity,
        false,
        LayoutType.LAYOUT_EMPTY
    )
}

enum class ScrollingUpOrDown {
    SCROLLING_UP,
    SCROLLING_DOWN
}

class ChatMessageAdapter(
    private val applicationContext: Context,
    private val activityContext: Context,
    private val glideContext: RequestManager,
    private val messages: MutableList<MessagesDataEntityWithAdditionalInfo>,
    private val observedMessages: MutableSet<String>,
    private val observedMimeTypeUrls: MutableSet<String>,
    private val mimeTypeFilePaths: MutableMap<String, MimeTypeHolderObject>, //url is the key, fileName is the value
    private val chatRoom: ChatRoomWithMemberMapDataClass,
    private val deviceScreenWidth: Int,
    private val userFirstName: String,
    private val hideSoftKeyboard: () -> Unit,
    private val saveNewMimeTypeFileToDatabase: (mimeTypeUrl: String, fileName: String, mimeTypeWidth: Int, mimeTypeHeight: Int) -> Unit,
    private val requestPictureFromServer: (chatRoomId: String, messageUUID: String) -> Unit,
    private val requestMessagesInfoFromServer: (chatRoomId: String, messageUUIDs: List<String>) -> Unit,
    private val moveMenuDisplayView: () -> View,
    private val showPictureMessagePopup: (pictureFilePath: String) -> Unit,
    private val showChatMessagePopup: (view: View, editSelectedLambda: () -> Unit, editable: Boolean, copyText: String, replyMessageInfoObject: ReplyMessageInfoObject, deleteMessageInfoObject: DeleteMessageInfoObject) -> Unit,
    private val updateInviteMessageToExpired: (messageUUIDPrimaryKey: String) -> Unit,
    private val joinChatRoomFromInvite: (messageUUIDPrimaryKey: String, chatRoomId: String, chatRoomPassword: String) -> Unit,
    private val sendEditedTextMessage: (messageUUIDPrimaryKey: String, newMessageText: String) -> Unit,
    private val sendDeletedMessage: (deleteMessageInfoObject: DeleteMessageInfoObject) -> Unit,
    private val scrollToSelectedMessage: (indexOfMessageInsideList: Int) -> Unit,
    private val showOtherUserNotInChatRoomOnlyBlockAndReportPopupMenu: (viewMenuIsBoundTo: View, accountOID: String, chatRoomId: String, messageUUID: String) -> Unit,
    private val showUserNoAdminBlockAndReportPopupMenu: (viewMenuIsBoundTo: View, accountOID: String, userName: String, chatRoomId: String, messageUUID: String) -> Unit,
    private val showUserNoAdminUnblockPopupMenu: (viewMenuIsBoundTo: View, accountOID: String, messageUUID: String, userName: String) -> Unit,
    private val showUserAdminBlockAndReportPopupMenu: (viewMenuIsBoundTo: View, accountOID: String, userName: String, chatRoomId: String, messageUUID: String) -> Unit,
    private val showUserAdminUnblockPopupMenu: (viewMenuIsBoundTo: View, accountOID: String, messageUUID: String, userName: String) -> Unit,
    private val sendErrorToActivity: (error: GrpcFunctionErrorStatusEnum) -> Unit,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val errorStore: StoreErrorsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        //0 is x; 1 is y
        val coordinates = arrayOf(0F, 0F)

        val saveCoordinatesOnTouchListener: (View, MotionEvent) -> Boolean = { _, event ->
            //save the X,Y coordinates
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                coordinates[0] = event.rawX
                coordinates[1] = event.rawY
            }

            false
        }
    }

    enum class MessageType {
        OTHER,
        TEXT_MESSAGE,
        LOCATION_MESSAGE,
        PICTURE_OR_MIME_TYPE_MESSAGE,
        INVITE_MESSAGE,
        LOADING_MESSAGE,
    }

    enum class InviteState {
        SENT_BY_CURRENT_USER,
        SENT_BY_DIFFERENT_USER,
        INVITE_EXPIRED
    }

    private data class ColorSchemeResourceReturns(
        val textColor: Int,
        val backgroundId: Int
    )

    //starts at the bottom so default will be scrolling up
    private var scrollingUpOrDown = ScrollingUpOrDown.SCROLLING_UP

    private val deviceScreenWidthThreeFifths = deviceScreenWidth * 3 / 5
    private val deviceScreenWidthFiveSeventhsDouble = deviceScreenWidth.toDouble() * 7.0 / 9.0
    private val deviceScreenWidthFiveSeventhsInt = deviceScreenWidthFiveSeventhsDouble.toInt()

    init {
        //These must be set up before the initial 'update' is calculated. Otherwise the bottom most
        // value will attempt to look at previous layout types and they will all be Layout.EMPTY
        // (the default value). While to some extent this is acceptable because it is how a RecyclerView
        // is designed to work. This approach gives more control over the 'batches' of messages for updates.
        for (i in messages.indices)
            findLayoutType(i)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (LayoutType.setVal(viewType)) {
            LayoutType.LAYOUT_MESSAGE -> {
                ChatMessageViewHolder(
                    LayoutInflater.from(activityContext)
                        .inflate(R.layout.list_item_chat_message_primary, parent, false),
                    glideContext,
                    applicationContext
                )
            }
            LayoutType.LAYOUT_SINGLE -> {
                ChatSingleViewHolder(
                    LayoutInflater.from(activityContext)
                        .inflate(R.layout.list_item_chat_message_single, parent, false),
                    deviceScreenWidthThreeFifths
                )
            }
            LayoutType.LAYOUT_EMPTY -> {
                ChatBlankViewHolder(
                    LayoutInflater.from(activityContext)
                        .inflate(R.layout.list_item_chat_message_empty, parent, false)
                )
            }
        }
    }

    private fun findLayoutType(position: Int): LayoutType {

        //The layout is also updated when the value is added to the messages list (see link)
        /** [ChatRoomContainer.MessagesList.add] **/
        val layoutType = findLayoutType(
            messages[position].messageDataEntity.sentByAccountID,
            messages[position].messageDataEntity.deletedType,
            messages[position].messageDataEntity.messageType
        )

        messages[position].messageLayoutType = layoutType
        return layoutType
    }

    override fun getItemViewType(position: Int): Int {
        //because the layout type is based on 'blockedAccounts' and 'deletedType' this
        // must be updated constantly
        return findLayoutType(position).ordinal
    }

    override fun getItemCount(): Int = messages.size

    private class TileProviderImpl : TileProvider {
        override fun getTile(p0: Int, p1: Int, p2: Int): Tile {
            return NO_TILE
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        if (LayoutType.setVal(holder.itemViewType) == LayoutType.LAYOUT_MESSAGE) {

            //This is to leave any MapView that was setup. Google has a known issue with
            // it listed here
            // https://issuetracker.google.com/issues/138736073
            //The issue was eventually fixed during mapViewStub setup by passing applicationContext
            // into the MapView instead of activityContext. However, left this stuff here to prevent
            // leaks on other devices.
            (holder as ChatMessageViewHolder).map?.let {
                it.setOnMapLongClickListener(null)
                it.clear()
            }
            holder.mapView?.removeAllViews()
            holder.mapView?.onDestroy()
            holder.mapView = null

            holder.mapViewStub = null
            holder.map = null
            holder.latLng = null
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        //update message to observed
        observedMessages.add(messages[position].messageDataEntity.messageUUIDPrimaryKey)

        when (LayoutType.setVal(holder.itemViewType)) {
            LayoutType.LAYOUT_MESSAGE -> {
                setUpMessage(holder as ChatMessageViewHolder, position)
            }
            LayoutType.LAYOUT_SINGLE -> {
                setUpSingle(holder as ChatSingleViewHolder, position)
            }
            LayoutType.LAYOUT_EMPTY -> {
            } //this will simply be an empty layout
        }
    }

    private fun setUpSingle(holder: ChatSingleViewHolder, position: Int) {

        when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
            messages[position].messageDataEntity.messageType
        )) {
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE -> {
                val user =
                    if (messages[position].messageDataEntity.accountOID == LoginFunctions.currentAccountOID) {
                        activityContext.resources.getString(R.string.You_have)
                    } else {
                        val userName =
                            chatRoom.chatRoomMembers.getFromMap(
                                messages[position].messageDataEntity.accountOID
                            )?.otherUsersDataEntity?.name

                        if (userName != null) {
                            activityContext.resources.getString(
                                R.string.User_has,
                                userName
                            )
                        } else {
                            activityContext.resources.getString(R.string.chat_room_fragment_user_default)
                        }
                    }

                if (user == activityContext.resources.getString(R.string.chat_room_fragment_user_default)) {
                    val errorMessage =
                        "No user name stored inside ChatMessageAdapter for USER_KICKED_MESSAGE.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

                holder.text.text =
                    activityContext.resources.getString(
                        R.string.chat_room_fragment_user_kicked,
                        user
                    )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE -> {
                val user =
                    if (messages[position].messageDataEntity.accountOID == LoginFunctions.currentAccountOID) {
                        activityContext.resources.getString(R.string.You_have)
                    } else {
                        chatRoom.chatRoomMembers.getFromMap(
                            messages[position].messageDataEntity.accountOID
                        )?.otherUsersDataEntity?.name
                            ?: activityContext.resources.getString(R.string.chat_room_fragment_user_default)
                    }

                if (user == activityContext.resources.getString(R.string.chat_room_fragment_user_default)) {
                    val errorMessage =
                        "No user name stored inside ChatMessageAdapter for USER_BANNED_MESSAGE.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

                holder.text.text =
                    activityContext.resources.getString(
                        R.string.chat_room_fragment_user_banned,
                        user
                    )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                val user =
                    if (messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID) {
                        activityContext.resources.getString(R.string.You_have)
                    } else {
                        chatRoom.chatRoomMembers.getFromMap(
                            messages[position].messageDataEntity.sentByAccountID
                        )?.otherUsersDataEntity?.name
                            ?: activityContext.resources.getString(R.string.chat_room_fragment_user_default)
                    }

                if (user == activityContext.resources.getString(R.string.chat_room_fragment_user_default)) {
                    //NOTE: This could happen if updateChatRoom returned a DIFFERENT_USER_JOINED_CHAT_ROOM message type
                    // and the user has not yet been updated.
                    val errorMessage =
                        "No user name stored inside ChatMessageAdapter for DIFFERENT_USER_JOINED_MESSAGE.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

                holder.text.text =
                    activityContext.resources.getString(
                        R.string.chat_room_fragment_user_joined,
                        user
                    )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {

                val user =
                    if (messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID) {
                        activityContext.resources.getString(R.string.You_have)
                    } else {
                        chatRoom.chatRoomMembers.getFromMap(
                            messages[position].messageDataEntity.sentByAccountID
                        )?.otherUsersDataEntity?.name
                            ?: activityContext.resources.getString(R.string.chat_room_fragment_user_default)
                    }

                if (user == activityContext.resources.getString(R.string.chat_room_fragment_user_default)) {
                    val errorMessage =
                        "No user name stored inside ChatMessageAdapter for DIFFERENT_USER_LEFT_MESSAGE.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

                holder.text.text =
                    activityContext.resources.getString(R.string.chat_room_fragment_user_left, user)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                holder.text.text =
                    if (messages[position].messageDataEntity.messageText == "") {
                        activityContext.resources.getString(R.string.chat_room_fragment_name_removed)
                    } else {
                        activityContext.resources.getString(
                            R.string.chat_room_fragment_name_updated,
                            messages[position].messageDataEntity.messageText
                        )
                    }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                holder.text.text =
                    if (messages[position].messageDataEntity.messageText == "") {
                        activityContext.resources.getString(R.string.chat_room_fragment_password_removed)
                    } else {
                        activityContext.resources.getString(
                            R.string.chat_room_fragment_password_updated,
                            messages[position].messageDataEntity.messageText
                        )
                    }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {

                val newAdminName =
                    if (messages[position].messageDataEntity.accountOID == LoginFunctions.currentAccountOID) { //if current user was promoted to admin
                        activityContext.resources.getString(R.string.You_have)
                    } else {
                        chatRoom.chatRoomMembers.getFromMap(
                            messages[position].messageDataEntity.accountOID
                        )?.otherUsersDataEntity?.name
                            ?: activityContext.resources.getString(R.string.chat_room_fragment_user_default)
                    }

                if (newAdminName == activityContext.resources.getString(R.string.chat_room_fragment_user_default)) {
                    val errorMessage =
                        "No user name stored inside ChatMessageAdapter for NEW_ADMIN_PROMOTED_MESSAGE.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

                holder.text.text = activityContext.resources.getString(
                    R.string.chat_room_fragment_admin_promoted,
                    newAdminName
                )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE -> {
                holder.text.text =
                    activityContext.resources.getString(R.string.chat_room_fragment_history_cleared)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
                holder.text.text =
                    if (messages[position].messageDataEntity.longitude == GlobalValues.server_imported_values.pinnedLocationDefaultLongitude
                        && messages[position].messageDataEntity.latitude == GlobalValues.server_imported_values.pinnedLocationDefaultLatitude) {
                        activityContext.resources.getString(R.string.chat_room_fragment_pinned_location_removed)
                    } else {
                        activityContext.resources.getString(R.string.chat_room_fragment_new_pinned_location)
                    }
            }
            //NOTE: it is set up this way instead of using else so that if
            // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
            // a warning will be thrown
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
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
            null,
            -> {
                val errorMessage =
                    "Invalid type returned to setUpSingle() inside ChatMessageAdapter.\n" +
                            "messageType: ${
                                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                    messages[position].messageDataEntity.messageType
                                )
                            }.\n" +
                            "message: ${messages[position].messageDataEntity}"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

                holder.hideItemView()

                return
            }
        }

        setUpMessageTimestamp(holder.dateText, position)
    }

    //NOTE: The suppression of ClickableViewAccessibility is because of the onTouch events below. They
    // are not something that does not have an effect on the working of the app and blind users do not
    // need to concern themselves with.
    @SuppressLint("ClickableViewAccessibility")
    private fun resetMessageViewHolder(holder: ChatMessageViewHolder, position: Int) {

        holder.bodyFrameLayout.removeAllViews()

        //The way view iDs for with the layout seems a bit odd, if I change an iD then the layout is reused it doesn't SEEM to have a problem
        // redrawing the layout (even views connected to the changed iD still connect properly) however I am adding this just in case

        holder.dateText.visibility = View.VISIBLE
        holder.senderThumbnail.visibility = View.VISIBLE

        holder.chatBubbleLayout.visibility = View.VISIBLE

        holder.chatBubbleLayout.setPadding(
            activityContext.resources.getDimension(
                R.dimen.match_list_item_primary_constraint_padding_wide
            )
                .toInt()
        )

        val chatBubbleLayoutParams =
            holder.chatBubbleLayout.layoutParams as ConstraintLayout.LayoutParams
        chatBubbleLayoutParams.matchConstraintMaxWidth =
            deviceScreenWidthFiveSeventhsInt + holder.chatBubbleLayout.paddingStart + holder.chatBubbleLayout.paddingEnd
        holder.chatBubbleLayout.layoutParams = chatBubbleLayoutParams

        holder.messageType = MessageType.OTHER

        //value set when MessageType == TEXT_MESSAGE
        holder.textMessage = ""
        holder.textMessageTextView = null
        holder.editTextMessageViewStub = null
        holder.editTextMessageStubEditText = null
        holder.editTextMessageStubSaveButton = null
        holder.editTextMessageStubCancelButton = null

        //value set when MessageType == LOCATION_MESSAGE
        holder.map?.let {
            // Clear the map and free up resources by changing the map type to none
            it.clear()
            it.mapType = GoogleMap.MAP_TYPE_NONE
        }

        holder.mapView = null
        holder.mapViewStub = null
        holder.map = null
        holder.latLng = null

        //value set when MessageType == PICTURE_OR_MIME_TYPE_MESSAGE
        holder.pictureOrMimeTypeMessageImageView = null
        holder.glideImageLoad?.let {
            it.cancel(true)
            holder.glideImageLoad = null
        }
        holder.imageWidth = 0
        holder.imageHeight = 0

        //value set when MessageType == INVITED_TO_CHAT_ROOM
        holder.inviteViewStub = null
        holder.inviteState = InviteState.INVITE_EXPIRED
        holder.inviteMessageHeader = ""
        holder.inviteChatRoomName = ""
        holder.displayChatRoomName = false
        holder.inviteChatRoomThirdLine = ""
        holder.displayChatRoomThirdLine = false
        holder.inviteChatRoomId = ""
        holder.inviteChatRoomPassword = ""

        //value set when MessageType == LOADING_MESSAGE
        holder.loadingViewStub = null

        holder.displayMenuOnLongClickListener = null

        holder.progressBar.visibility = View.GONE
        holder.messageUUIDPrimaryKey = messages[position].messageDataEntity.messageUUIDPrimaryKey
        holder.hasCompleteInfo = messages[position].messageDataEntity.hasCompleteInfo

        holder.messageSentByAccountOID = ""

        //these can be set to drawable.white_background_rounded_corners when hasCompleteInfo is false
        holder.timeText.setBackgroundResource(0)
        holder.nameText.setBackgroundResource(0)

        val nameParams = holder.nameText.layoutParams as ConstraintLayout.LayoutParams

        nameParams.setMargins(
            0,
            nameParams.topMargin,
            0,
            nameParams.bottomMargin
        )

        holder.nameText.layoutParams = nameParams

        val timeParams = holder.timeText.layoutParams as ConstraintLayout.LayoutParams

        timeParams.setMargins(
            timeParams.leftMargin,
            timeParams.topMargin,
            0,
            timeParams.bottomMargin
        )

        holder.timeText.layoutParams = timeParams

        val editedParams = holder.editedText.layoutParams as ConstraintLayout.LayoutParams

        editedParams.setMargins(
            0,
            editedParams.topMargin,
            editedParams.rightMargin,
            editedParams.bottomMargin
        )

        holder.editedText.layoutParams = editedParams

        val replyParams = holder.replyLayout.layoutParams as ConstraintLayout.LayoutParams

        replyParams.setMargins(
            0,
            0,
            0,
            replyParams.bottomMargin,
        )

        replyParams.width = RelativeLayout.LayoutParams.WRAP_CONTENT

        holder.replyLayout.layoutParams = replyParams
        holder.replyLayout.setOnClickListener(null)
        holder.replyLayout.setOnLongClickListener(null)
        holder.replyLayout.setOnTouchListener(null)
    }

    //Will download the gif type file and continue the process of setting up the layout.
    //mimeTypeUrl is expected to be valid.
    private fun downloadFileAndLoadToMimeType(
        holder: ChatMessageViewHolder,
        position: Int,
        mimeTypeUrl: String
    ) {

        holder.glideImageLoad?.let {
            it.cancel(true)
            holder.glideImageLoad = null
        }

        //NOTE: The glideContext should handle canceling this if the Fragment is destroyed.
        holder.glideImageLoad = glideContext
            .download(mimeTypeUrl)
            .listener(object : RequestListener<File?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<File?>,
                    isFirstResource: Boolean,
                ): Boolean {

                    holder.glideImageLoad = null

                    loadErrorImageIntoImageView(holder)

                    setUpMessageLayout(holder, position)

                    val errorMessage =
                        "LoadFailed was returned for downloading an image with GlideApp.\n" +
                                "messageType: ${
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                        messages[position].messageDataEntity.messageType
                                    )
                                }.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                    return false
                }

                override fun onResourceReady(
                    mimeTypeFile: File?,
                    model: Any?,
                    target: Target<File?>,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {

                    holder.glideImageLoad = null

                    if (mimeTypeFile != null) { //resource is not null

                        val generatedMimeTypeFile =
                            generateMimeTypeFile(
                                GlobalValues.applicationContext,
                                mimeTypeUrl
                            )

                        if (generatedMimeTypeFile.exists()) { //if gif file exists (this could mean that multiple of these gifs were loaded in the same screen on the recycler view for their first time)

                            val mimeTypeObjectResult =
                                mimeTypeFilePaths[mimeTypeUrl]

                            mimeTypeObjectResult?.let { mimeTypeObject ->
                                CoroutineScope(Main).launch {

                                    setPictureOrMimeTypeHeightAndWidth(
                                        deviceScreenWidth,
                                        holder,
                                        mimeTypeObject.width,
                                        mimeTypeObject.height,
                                        deviceScreenWidthFiveSeventhsDouble
                                    )

                                    setupBodyForPictureOrMimeTypeMessageType(
                                        holder,
                                        generatedMimeTypeFile.absolutePath
                                    )

                                    setUpMessageLayout(holder, position)
                                }
                            }

                        } else { //if gif file does not exist (this will be the usual case)

                            FileInputStream(mimeTypeFile).use { inputStream ->

                                FileOutputStream(generatedMimeTypeFile).use { outputStream ->

                                    val buffer = ByteArray(1024)
                                    var len: Int

                                    while (inputStream.read(buffer).also {
                                            len = it
                                        } > 0) {
                                        outputStream.write(buffer, 0, len)
                                    }

                                    val mimeTypeObject =
                                        getFileWidthAndHeightFromBitmap(
                                            generatedMimeTypeFile.absolutePath,
                                            messages[position].messageDataEntity.messageText
                                        )

                                    mimeTypeFilePaths[mimeTypeUrl] =
                                        mimeTypeObject

                                    saveNewMimeTypeFileToDatabase(
                                        mimeTypeUrl,
                                        generatedMimeTypeFile.absolutePath,
                                        mimeTypeObject.width,
                                        mimeTypeObject.height
                                    )

                                    CoroutineScope(Main).launch {

                                        setPictureOrMimeTypeHeightAndWidth(
                                            deviceScreenWidth,
                                            holder,
                                            mimeTypeObject.width,
                                            mimeTypeObject.height,
                                            deviceScreenWidthFiveSeventhsDouble
                                        )

                                        setupBodyForPictureOrMimeTypeMessageType(
                                            holder,
                                            generatedMimeTypeFile.absolutePath
                                        )

                                        setUpMessageLayout(holder, position)
                                    }
                                }
                            }

                            mimeTypeFile.delete()
                        }

                    } else { //resource is null
                        loadErrorImageIntoImageView(holder)

                        setUpMessageLayout(holder, position)

                        val errorMessage =
                            "Resource was returned as null when downloading an image with GlideApp.\n" +
                                    "messageType: ${
                                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                            messages[position].messageDataEntity.messageType
                                        )
                                    }.\n" +
                                    "message: ${messages[position].messageDataEntity}"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }

                    return false
                }
            })
            .submit()
    }

    private fun setUpMessage(holder: ChatMessageViewHolder, position: Int) {

        val messageType = TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
            messages[position].messageDataEntity.messageType
        )

        resetMessageViewHolder(holder, position)

        holder.messageSentByAccountOID = messages[position].messageDataEntity.sentByAccountID

        holder.narrowPadding =
            (messageType != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE
                    || messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID)
                    && messageType != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE

        Log.i(
            "chatMessageAdapter",
            "setUpMessage() for ${holder.identifierNum} messageType $messageType holderType ${holder.messageType}"
        )

        when (messageType) {
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE -> {

                holder.messageType = MessageType.LOADING_MESSAGE

                holder.loadingViewStub = ViewStub(activityContext)

                setUpMessageLayout(holder, position)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                holder.messageType = MessageType.TEXT_MESSAGE
                setupBodyForTextMessageType(holder, position)
                setUpMessageLayout(holder, position)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {

                holder.messageType = MessageType.LOCATION_MESSAGE

                holder.bodyFrameLayout.layoutParams.apply {
                    width = deviceScreenWidthFiveSeventhsInt
                    height = width * 2 / 3
                }

                setUpMessageLayout(holder, position)
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
            -> {
                holder.messageType = MessageType.PICTURE_OR_MIME_TYPE_MESSAGE
                holder.pictureOrMimeTypeMessageImageView = ImageView(activityContext)

                if (messages[position].messageDataEntity.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE.number) {

                    val mimeTypeUrl = messages[position].messageDataEntity.downloadUrl

                    if (mimeTypeUrl.isNotBlank()) { //url for gif exists

                        observedMimeTypeUrls.add(mimeTypeUrl)

                        val mimeTypeObject = mimeTypeFilePaths[mimeTypeUrl]
                        val mimeTypeFilePath = mimeTypeObject?.filePath ?: ""

                        if (mimeTypeObject != null && mimeTypeFilePath.isNotEmpty()) { //if file path has been set

                            setPictureOrMimeTypeHeightAndWidth(
                                deviceScreenWidth,
                                holder,
                                mimeTypeObject.width,
                                mimeTypeObject.height,
                                deviceScreenWidthFiveSeventhsDouble
                            )

                            val mimeTypeFile = File(mimeTypeFilePath)
                            if (mimeTypeFile.isImage()) { //if file exists and is an image
                                holder.progressBar.visibility = View.VISIBLE

                                setupBodyForPictureOrMimeTypeMessageType(
                                    holder,
                                    mimeTypeFilePath
                                )

                                setUpMessageLayout(holder, position)
                            } else { //if file does not exist (this could mean memory was freed or cache was cleared)
                                holder.progressBar.visibility = View.VISIBLE

                                setUpMessageLayout(holder, position)

                                downloadFileAndLoadToMimeType(
                                    holder,
                                    position,
                                    mimeTypeUrl
                                )
                            }

                        } else if (mimeTypeObject != null) { //if file path has not been set

                            setPictureOrMimeTypeHeightAndWidth(
                                deviceScreenWidth,
                                holder,
                                messages[position].messageDataEntity.imageWidth,
                                messages[position].messageDataEntity.imageHeight,
                                deviceScreenWidthFiveSeventhsDouble
                            )

                            holder.progressBar.visibility = View.VISIBLE
                            setUpMessageLayout(holder, position)

                            downloadFileAndLoadToMimeType(
                                holder,
                                position,
                                mimeTypeUrl
                            )
                        } else { //mimeTypeObject == null

                            val errorMessage =
                                "A mimeTypeObject was null mimeTypeObject should never be null, even when this user sends the GIF.\n" +
                                        "mimeTypeUrl: $mimeTypeUrl.\n" +
                                        "messageType: ${
                                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                                messages[position].messageDataEntity.messageType
                                            )
                                        }.\n" +
                                        "message: ${messages[position].messageDataEntity}"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage,
                                applicationContext
                            )

                            sendErrorToActivity(GrpcFunctionErrorStatusEnum.LOG_USER_OUT)
                        }
                    } else { //do not have a url for the gif
                        //NOTE: Not putting an error here or else update will be sent every time
                        // the image is displayed.
                        loadErrorImageIntoImageView(holder)

                        setUpMessageLayout(holder, position)
                    }

                } else { //if message is not mime type

                    val pictureFilePath = messages[position].messageDataEntity.filePath
                    val pictureHeight = messages[position].messageDataEntity.imageHeight
                    val pictureWidth = messages[position].messageDataEntity.imageWidth

                    if (File(pictureFilePath).isImage()) { //if file exists and is not corrupt

                        setPictureOrMimeTypeHeightAndWidth(
                            deviceScreenWidth,
                            holder,
                            pictureWidth,
                            pictureHeight,
                            deviceScreenWidthFiveSeventhsDouble
                        )

                        holder.progressBar.visibility = View.VISIBLE

                        setupBodyForPictureOrMimeTypeMessageType(
                            holder,
                            pictureFilePath
                        )

                        setUpMessageLayout(holder, position)

                    } else { //if no picture file or file is invalid

                        holder.progressBar.visibility = View.VISIBLE

                        if (pictureWidth <= 0 || pictureHeight <= 0) { //if picture width or height are invalid
                            holder.imageWidth = deviceScreenWidth / 2
                            holder.imageHeight = deviceScreenWidth / 2

                            val errorMessage = "Picture width and height are invalid.\n" +
                                    "messageType: ${
                                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                            messages[position].messageDataEntity.messageType
                                        )
                                    }.\n" +
                                    "pictureWidth: $pictureWidth\n" +
                                    "pictureHeight: $pictureHeight\n" +
                                    "message: ${messages[position].messageDataEntity}"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage
                            )

                        } else { //if picture width and height are valid (this could happen if the cache is cleared)
                            setPictureOrMimeTypeHeightAndWidth(
                                deviceScreenWidth,
                                holder,
                                pictureWidth,
                                pictureHeight,
                                deviceScreenWidthFiveSeventhsDouble
                            )
                        }

                        holder.pictureOrMimeTypeMessageImageView?.let {
                            if (it.parent == null) {
                                holder.bodyFrameLayout.addView(it)
                                it.layoutParams.width =
                                    holder.imageWidth//FrameLayout.LayoutParams.WRAP_CONTENT
                                it.layoutParams.height =
                                    holder.imageHeight//FrameLayout.LayoutParams.WRAP_CONTENT
                            }
                        }

                        //this can happen if the cache is cleared, want the 'normal' update to run inside setUpMessageLayout() otherwise
                        if (messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.STORED_ON_SERVER.ordinal
                            && messages[position].messageDataEntity.hasCompleteInfo
                        ) { //message has been downloaded already, however pic does not exist

                            Log.i(
                                "numUpdatesTooSmall",
                                "running requestPictureFromServer()"
                            )

                            requestPictureFromServer(
                                messages[position].messageDataEntity.chatRoomId,
                                messages[position].messageDataEntity.messageUUIDPrimaryKey
                            )

                            messages[position].messageUpdateHasBeenRequestedFromServer = true
                        }

                        setUpMessageLayout(holder, position)
                    }
                }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {

                holder.messageType = MessageType.INVITE_MESSAGE

                holder.inviteViewStub = ViewStub(activityContext)

                val sentByAccountID = messages[position].messageDataEntity.sentByAccountID
                val messageExpired = messages[position].messageDataEntity.inviteExpired
                val messageTimeStoredOnServer =
                    messages[position].messageDataEntity.messageStoredOnServerTime

                val inviteExpiredLambda = {
                    holder.inviteMessageHeader =
                        applicationContext.getString(R.string.chat_message_adapter_invite_to_chat_room_expired)
                    holder.displayChatRoomName = false
                    holder.displayChatRoomThirdLine = false
                    holder.inviteState = InviteState.INVITE_EXPIRED
                }

                holder.bodyFrameLayout.layoutParams.apply {
                    width = FrameLayout.LayoutParams.WRAP_CONTENT
                    height = FrameLayout.LayoutParams.WRAP_CONTENT
                }

                when {
                    messageExpired -> {
                        inviteExpiredLambda()
                    }
                    messageTimeStoredOnServer > 0 && //NOTE: this check needs to be done because when the message is initially sent, the time will be -1 or 0
                            getCurrentTimestampInMillis() - messageTimeStoredOnServer > GlobalValues.server_imported_values.timeBetweenChatMessageInviteExpiration -> {

                        messages[position].messageDataEntity.inviteExpired = true

                        //update the message to expired if it reaches this point
                        updateInviteMessageToExpired(messages[position].messageDataEntity.messageUUIDPrimaryKey)

                        inviteExpiredLambda()
                    }
                    sentByAccountID == LoginFunctions.currentAccountOID -> {
                        val chatRoomName =
                            messages[position].messageDataEntity.messageValueChatRoomName
                        val messageSentByName = messages[position].messageDataEntity.messageText

                        //NOTE: the name and password can be empty or ~
                        holder.inviteMessageHeader =
                            applicationContext.getString(R.string.chat_message_adapter_invite_to_chat_room_sent_header)
                        holder.inviteChatRoomName = chatRoomName
                        holder.displayChatRoomName = true
                        holder.inviteChatRoomThirdLine = applicationContext.getString(
                            R.string.chat_message_adapter_invite_to_chat_room_sent_final_line,
                            messageSentByName
                        )
                        holder.displayChatRoomThirdLine = true

                        holder.inviteState = InviteState.SENT_BY_CURRENT_USER
                    }
                    else -> {
                        val chatRoomName =
                            messages[position].messageDataEntity.messageValueChatRoomName
                        val chatRoomPassword =
                            messages[position].messageDataEntity.messageValueChatRoomPassword
                        val chatRoomIdToJoin =
                            messages[position].messageDataEntity.messageValueChatRoomId

                        if (chatRoomIdToJoin != "" && chatRoomIdToJoin != "~"
                        ) { //chat room name, password and id are valid (NOTE: the name and password can be empty or ~)

                            holder.inviteChatRoomId = chatRoomIdToJoin
                            holder.inviteChatRoomPassword = chatRoomPassword

                            holder.inviteMessageHeader =
                                applicationContext.getString(R.string.chat_message_adapter_invited_to_chat_room_header)
                            holder.inviteChatRoomName = chatRoomName
                            holder.displayChatRoomName = true
                            holder.displayChatRoomThirdLine = false

                            holder.inviteState = InviteState.SENT_BY_DIFFERENT_USER
                        } else { //chat room id

                            val errorMessage =
                                "LoadFailed was returned for downloading an image with GlideApp.\n" +
                                        "messageType: ${
                                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                                messages[position].messageDataEntity.messageType
                                            )
                                        }.\n" +
                                        "message: ${messages[position].messageDataEntity}"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage
                            )

                            //Update message to expired so that this error will not be spammed to the server.
                            updateInviteMessageToExpired(messages[position].messageDataEntity.messageUUIDPrimaryKey)

                            inviteExpiredLambda()
                        }
                    }
                }

                setUpMessageLayout(holder, position)
            }
            //NOTE: it is set up this way instead of using else so that if
            // TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase is updated
            // a warning will be thrown
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
            null,
            -> {

                val errorMessage =
                    "Invalid type returned to setUpMessage() inside ChatMessageAdapter.\n" +
                            "messageType: $messageType.\n" +
                            "message: ${messages[position].messageDataEntity}"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

                holder.hideItemView()

                return
            }
        }
    }

    //loads error image into image view
    //NOTE: Cannot call Glide from inside Glide, that is what the runGlide parameter is used for.
    private fun loadErrorImageIntoImageView(holder: ChatMessageViewHolder) {

        holder.pictureOrMimeTypeMessageImageView?.let { imageView ->

            imageView.adjustViewBounds = true

            imageView.maxHeight = deviceScreenWidthFiveSeventhsInt

            holder.imageHeight = deviceScreenWidth / 2
            holder.imageWidth = deviceScreenWidth / 2

            //if this function is running after the image view was added to the layout
            if (imageView.parent != null) {
                imageView.layoutParams.width =
                    holder.imageWidth//FrameLayout.LayoutParams.WRAP_CONTENT
                imageView.layoutParams.height =
                    holder.imageHeight//FrameLayout.LayoutParams.WRAP_CONTENT
            }

            CoroutineScope(Main).launch {

                //and exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                // if this is called from inside another glide context
                glideContext
                    .load(R.drawable.icon_round_broken_image_24)
                    .listener(
                        object : RequestListener<Drawable?> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable?>,
                                isFirstResource: Boolean,
                            ): Boolean {
                                holder.progressBar.visibility = View.GONE
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable?>,
                                dataSource: DataSource,
                                isFirstResource: Boolean,
                            ): Boolean {
                                holder.progressBar.visibility = View.GONE
                                return false
                            }
                        }
                    )
                    .error(R.drawable.icon_round_broken_image_24)
                    .into(imageView)
            }
        }

        holder.bodyFrameLayout.layoutParams.apply {
            width = FrameLayout.LayoutParams.WRAP_CONTENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
        }
    }

    private fun setupBodyForTextMessageType(holder: ChatMessageViewHolder, position: Int) {

        holder.textMessage = messages[position].messageDataEntity.messageText
        holder.textMessageTextView = TextView(activityContext)

        holder.textMessageTextView?.apply {
            text = messages[position].messageDataEntity.messageText
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.match_list_item_text_message_text_size)
            )
        }

        if (messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID) {
            holder.editTextMessageViewStub = ViewStub(activityContext)
        } else {
            holder.editTextMessageViewStub = null
            holder.editTextMessageStubEditText = null
            holder.editTextMessageStubSaveButton = null
            holder.editTextMessageStubCancelButton = null
        }

        holder.bodyFrameLayout.layoutParams.apply {
            width = FrameLayout.LayoutParams.WRAP_CONTENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
        }
    }

    private fun setupBodyForPictureOrMimeTypeMessageType(
        holder: ChatMessageViewHolder,
        filePathOrUrl: String
    ) {

        holder.pictureOrMimeTypeMessageImageView?.let { imageView ->
            imageView.adjustViewBounds = true

            imageView.maxHeight = deviceScreenWidthFiveSeventhsInt

            if (filePathOrUrl == GlobalValues.PICTURE_NOT_FOUND_ON_SERVER) { //if picture was not found on server for some reason or it was corrupt
                loadErrorImageIntoImageView(holder)
            } else { //if picture/mime file was not error message

                //if this function is running after the image view was added to the layout
                if (imageView.parent != null) {
                    imageView.layoutParams.width =
                        holder.imageWidth//FrameLayout.LayoutParams.WRAP_CONTENT
                    imageView.layoutParams.height =
                        holder.imageHeight//FrameLayout.LayoutParams.WRAP_CONTENT
                }

                CoroutineScope(Main).launch {
                    //and exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                    // if this is called from inside another glide context
                    glideContext
                        .load(filePathOrUrl)
                        .listener(
                            object : RequestListener<Drawable?> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable?>,
                                    isFirstResource: Boolean,
                                ): Boolean {

                                    holder.progressBar.visibility = View.GONE

                                    loadErrorImageIntoImageView(holder)

                                    val pictureFile = File(filePathOrUrl)

                                    if (pictureFile.exists()
                                        && !File(filePathOrUrl).isImage()
                                    ) { //if file is corrupt
                                        //delete previously stored file
                                        //NOTE: There is the chance that this will call delete on the same file multiple
                                        // times. However it should not matter, the gif will continue to download until
                                        // a corrupt file is fixed.
                                        deleteFileInterface.sendFileToWorkManager(
                                            filePathOrUrl
                                        )
                                    }

                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable?,
                                    model: Any?,
                                    target: Target<Drawable?>,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean,
                                ): Boolean {
                                    holder.progressBar.visibility = View.GONE
                                    return false
                                }
                            }
                        )
                        .error(R.drawable.icon_round_broken_image_24)
                        .into(imageView)
                }

                holder.bodyFrameLayout.layoutParams.apply {
                    width = FrameLayout.LayoutParams.WRAP_CONTENT
                    height = FrameLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }
    }

    private fun attachMessageToBodyFrameLayout(holder: ChatMessageViewHolder, position: Int) {

        Log.i(
            "chatMessageAdapter",
            "onViewAttachedToWindow() for ${holder.identifierNum} type ${holder.messageType}"
        )

        if (holder.hasCompleteInfo) { //if message does not need to download any info

            when (holder.messageType) {
                MessageType.OTHER -> {
                }
                MessageType.TEXT_MESSAGE -> {

                    holder.textMessageTextView?.let {
                        if (it.parent == null) {
                            holder.bodyFrameLayout.addView(it)
                        }
                    }

                    //set up Edit message menu item
                    //editTextMessageViewStub will be null if not sent by the current user
                    holder.editTextMessageViewStub?.let { viewStub ->
                        if (viewStub.parent == null) {
                            holder.bodyFrameLayout.addView(viewStub)
                            viewStub.layoutParams.width =
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            viewStub.layoutParams.height =
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            viewStub.layoutResource = R.layout.view_chat_message_edit_text_message
                            val inflated = viewStub.inflate()
                            viewStub.visibility = View.GONE

                            holder.editTextMessageStubEditText =
                                inflated.findViewById(R.id.chatMessageEditTextHeaderTextView)
                            holder.editTextMessageStubSaveButton =
                                inflated.findViewById(R.id.chatMessageEditTextSaveButtonTextView)
                            holder.editTextMessageStubCancelButton =
                                inflated.findViewById(R.id.chatMessageEditTextCancelButtonTextView)

                            holder.editTextMessageStubEditText?.apply {
                                setText(holder.textMessage, TextView.BufferType.EDITABLE)

                                setTextSize(
                                    TypedValue.COMPLEX_UNIT_PX,
                                    resources.getDimension(R.dimen.match_list_item_text_message_text_size)
                                )
                            }

                            holder.editTextMessageStubCancelButton?.setSafeOnClickListener {
                                hideSoftKeyboard()
                                holder.textMessageTextView?.let { textView ->
                                    holder.editTextMessageStubEditText?.let { editText ->
                                        viewStub.visibility = View.GONE
                                        textView.visibility = View.VISIBLE

                                        editText.setText(
                                            holder.textMessage,
                                            TextView.BufferType.EDITABLE
                                        )
                                    }
                                }
                            }

                            holder.editTextMessageStubSaveButton?.setSafeOnClickListener {

                                hideSoftKeyboard()
                                holder.textMessageTextView?.let { textView ->

                                    viewStub.visibility = View.GONE
                                    textView.visibility = View.VISIBLE

                                    holder.editTextMessageStubEditText?.let { editText ->

                                        val editedMessage = editText.text.toString().trimEnd()

                                        Log.i("editedMessage", "editedMessage: $editedMessage")
                                        if (editedMessage != holder.textMessage) { //if message was changed

                                            if (editedMessage.isNotEmpty()
                                            ) { //if message is not just white space and has characters (trimEnd was run above)

                                                //set up item to be in edited but not yet sent to server state
                                                textView.text = editedMessage
                                                holder.textMessage = editedMessage

                                                Log.i(
                                                    "settingDraw",
                                                    "setClock lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                )

                                                holder.timeText.setCompoundDrawablesWithIntrinsicBounds(
                                                    null,
                                                    null,
                                                    ResourcesCompat.getDrawable(
                                                        applicationContext.resources,
                                                        R.drawable.icon_round_access_time_chat_messenger_24,
                                                        null
                                                    ),
                                                    null,
                                                )

                                                sendEditedTextMessage(
                                                    holder.messageUUIDPrimaryKey,
                                                    editedMessage
                                                )

                                            } else { //if message is only white space or empty, delete instead

                                                val deleteMessageInfoObject =
                                                    DeleteMessageInfoObject(
                                                        holder.messageUUIDPrimaryKey,
                                                        DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS,
                                                        false
                                                    )

                                                sendDeletedMessage(deleteMessageInfoObject)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                MessageType.LOCATION_MESSAGE -> {
                    //this will be done inside of initializeMapForLocation() inside of the ChatMessageViewHolder
                    holder.latLng = LatLng(
                        messages[position].messageDataEntity.latitude,
                        messages[position].messageDataEntity.longitude
                    )

                    //Important: Can not use activity context here or the activity can leak.
                    // To reproduce the leak.
                    // 1) Have location messages (used 2 when testing) in a chat room.
                    // 2) Log the user out. This might be enough to see the leak.
                    // 3) Completely close the app.
                    // 4) Open the app and log back in
                    // 5) Navigate to and view the location message.
                    // 6) Log the user out, leak canary will give something like zzcb.zza leaking.
                    holder.mapViewStub = ViewStub(applicationContext)

                    holder.mapViewStub?.let {
                        if (it.parent == null) {
                            //View.inflate() does not seem to work on older APIs (21 specifically). So
                            // using a LayoutInflater instead.
                            val layoutInflater = LayoutInflater.from(applicationContext)
                            val inflated = layoutInflater.inflate(R.layout.view_chat_message_location, holder.bodyFrameLayout)
                            holder.mapView =
                                inflated.findViewById(R.id.chatRoomAdapterListItemMapView)

                            try {
                                holder.mapView?.onCreate(null)
                                Log.i("map_stuff_z", "ChatMessageAdapter getMapAsync")
                                holder.mapView?.getMapAsync(holder)
                            } catch (e: NotFoundException) {

                                Log.i("map_stuff_z", "ChatMessageAdapter exception ${e.message}")

                                //Can get a Resources$NotFoundException exception during building. In development
                                // can rebuild or invalidate cache and it will work. Want to make sure it doesn't
                                // happen when in production.

                                val errorMessage =
                                    "Received an exception when starting a MapView in chat message adapter.\n" +
                                            "In development can rebuild or invalidate cache and it will work. However, " +
                                            "it should not happen when in production.\n" +
                                            "exceptionMessage: ${e.message}"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    e.stackTraceToString(),
                                    errorMessage
                                )

                                //Set the map view to null so it will show up as empty instead of crashing the app.
                                holder.mapView = null
                            }
                        }
                    }
                }
                MessageType.PICTURE_OR_MIME_TYPE_MESSAGE -> {
                    holder.pictureOrMimeTypeMessageImageView?.let {
                        if (it.parent == null) {
                            holder.bodyFrameLayout.addView(it)
                            it.layoutParams.width =
                                holder.imageWidth//FrameLayout.LayoutParams.WRAP_CONTENT
                            it.layoutParams.height =
                                holder.imageHeight//FrameLayout.LayoutParams.WRAP_CONTENT
                        }
                    }
                }
                MessageType.INVITE_MESSAGE -> {
                    holder.inviteViewStub?.let {
                        if (it.parent == null) {
                            holder.bodyFrameLayout.addView(it)
                            it.layoutParams.width =
                                FrameLayout.LayoutParams.MATCH_PARENT
                            it.layoutParams.height =
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            it.layoutResource = R.layout.view_chat_message_invite
                            val inflated = it.inflate()

                            val headerTextView =
                                inflated.findViewById<TextView>(R.id.chatMessageInviteHeaderTextView)
                            val chatRoomNameTextView =
                                inflated.findViewById<TextView>(R.id.chatMessageInviteChatRoomNameTextView)
                            val thirdLineTextView =
                                inflated.findViewById<TextView>(R.id.chatMessageInviteThirdLineTextView)
                            val buttonTextView =
                                inflated.findViewById<TextView>(R.id.chatMessageInviteJoinButtonTextView)

                            headerTextView?.text = holder.inviteMessageHeader

                            //setup text
                            if (holder.displayChatRoomName) { //if displaying name
                                chatRoomNameTextView?.text = holder.inviteChatRoomName
                                chatRoomNameTextView?.visibility = View.VISIBLE

                                if (holder.displayChatRoomThirdLine) { //if displaying 3rd line
                                    thirdLineTextView?.text = holder.inviteChatRoomThirdLine
                                    thirdLineTextView?.visibility = View.VISIBLE
                                } else { //if not displaying 3rd line
                                    thirdLineTextView?.visibility = View.GONE
                                }

                            } else { //if not displaying name
                                chatRoomNameTextView?.visibility = View.GONE
                                thirdLineTextView?.visibility = View.GONE
                            }

                            //setup button
                            when (holder.inviteState) {
                                InviteState.SENT_BY_CURRENT_USER,
                                InviteState.INVITE_EXPIRED,
                                -> {
                                    buttonTextView?.visibility = View.GONE
                                }
                                InviteState.SENT_BY_DIFFERENT_USER -> {
                                    buttonTextView?.visibility = View.VISIBLE
                                    buttonTextView?.setSafeOnClickListener {
                                        joinChatRoomFromInvite(
                                            holder.messageUUIDPrimaryKey,
                                            holder.inviteChatRoomId,
                                            holder.inviteChatRoomPassword
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MessageType.LOADING_MESSAGE -> {
                    //NOTE: LOADING_MESSAGE should not spam the server for errors every time this opens because by the nature of it
                    // it should finish loading.
                    val errorMessage =
                        "MessageType.LOADING_MESSAGE should always have (holder.hasCompleteInfo == false).\n" +
                                "messageType: ${
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                        messages[position].messageDataEntity.messageType
                                    )
                                }.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                    holder.progressBar.visibility = View.VISIBLE
                }
            }

        } else if (holder.messageType == MessageType.LOADING_MESSAGE) { //if message DOES need to download new info & is LOADING_MESSAGE
            //TESTING_NOTE: Make sure to test loading message.
            holder.progressBar.visibility = View.VISIBLE
        }
    }

    private fun calculateCurrentTextColorId(position: Int): ColorSchemeResourceReturns {
        return if (messages[position].messageDataEntity.sentByAccountID != LoginFunctions.currentAccountOID) {
            ColorSchemeResourceReturns(
                R.color.message_received_text_color,
                R.drawable.background_reply_indentation_line_received,
            )
        } else {
            ColorSchemeResourceReturns(
                R.color.message_sent_text_color,
                R.drawable.background_reply_indentation_line_sent
            )
        }
    }

    //Set up chat bubble layout text color for passed ChatMessageViewHolder (does not set reply views).
    private fun setupMainUITextColor(holder: ChatMessageViewHolder, position: Int) {

        val colorResourceIds = calculateCurrentTextColorId(position)

        holder.nameText.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )
        holder.timeText.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )
        holder.editedText.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )
        holder.textMessageTextView?.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )

    }

    //Set up reply Layout text color and background for indentation bar for passed ChatMessageViewHolder.
    private fun setupReplyUITextColor(holder: ChatMessageViewHolder, position: Int) {
        val colorResourceIds = calculateCurrentTextColorId(position)

        holder.replyNameTextView.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )

        holder.replyMessageTypeTextView.setTextColor(
            ResourcesCompat.getColor(
                applicationContext.resources,
                colorResourceIds.textColor,
                null
            )
        )

        holder.replyIndentationLine.setBackgroundResource(colorResourceIds.backgroundId)
    }

    private fun setupMessageListItemLayoutToOtherUserMessage(
        holder: ChatMessageViewHolder,
        position: Int,
        hasCompleteInfo: Boolean,
    ) {

        //Changing thumbnail layout here (instead of chatBubble layout) because it is the head of the chain and so its bias is used
        val thumbnailLayout =
            holder.senderThumbnail.layoutParams as ConstraintLayout.LayoutParams

        thumbnailLayout.horizontalBias = 0F

        holder.senderThumbnail.layoutParams = thumbnailLayout
        holder.senderThumbnail.visibility = View.VISIBLE

        //display options menu
        holder.senderThumbnail.setSafeOnClickListener(400) {

            //extract this each time and in case account state of the user changes
            val userInsideOnClickListener =
                chatRoom.chatRoomMembers.getFromMap(messages[position].messageDataEntity.sentByAccountID)

            //will be set to null if user is NOT blocked, will be set to Unit (not null) if user is blocked
            val userBlocked =
                GlobalValues.blockedAccounts[userInsideOnClickListener?.otherUsersDataEntity?.accountOID]

            if (userInsideOnClickListener != null) { //user exists

                if (userInsideOnClickListener.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                    || userInsideOnClickListener.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                ) { //user is inside chat room

                    //set popup menu for thumbnail options
                    when {
                        chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                                && !userBlocked -> { //current user is admin and displayed member is not blocked

                            //display options menu
                            showUserAdminBlockAndReportPopupMenu(
                                holder.senderThumbnail,
                                userInsideOnClickListener.otherUsersDataEntity.accountOID,
                                userInsideOnClickListener.otherUsersDataEntity.name,
                                chatRoom.chatRoomId,
                                messages[position].messageDataEntity.messageUUIDPrimaryKey
                            )
                        }
                        chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
                                && userBlocked -> { //current user is admin and displayed member is blocked

                            //display options menu
                            showUserAdminUnblockPopupMenu(
                                holder.senderThumbnail,
                                userInsideOnClickListener.otherUsersDataEntity.accountOID,
                                messages[position].messageDataEntity.messageUUIDPrimaryKey,
                                userInsideOnClickListener.otherUsersDataEntity.name
                            )
                        }
                        !userBlocked -> { //current user is NOT admin and displayed member is not blocked

                            //display options menu
                            showUserNoAdminBlockAndReportPopupMenu(
                                holder.senderThumbnail,
                                userInsideOnClickListener.otherUsersDataEntity.accountOID,
                                userInsideOnClickListener.otherUsersDataEntity.name,
                                chatRoom.chatRoomId,
                                messages[position].messageDataEntity.messageUUIDPrimaryKey
                            )
                        }
                        else -> {  //current user is NOT admin and displayed member is blocked

                            //display options menu
                            showUserNoAdminUnblockPopupMenu(
                                holder.senderThumbnail,
                                userInsideOnClickListener.otherUsersDataEntity.accountOID,
                                messages[position].messageDataEntity.messageUUIDPrimaryKey,
                                userInsideOnClickListener.otherUsersDataEntity.name,
                            )
                        }
                    }

                } else { //user is NOT inside chat room

                    showOtherUserNotInChatRoomOnlyBlockAndReportPopupMenu(
                        holder.senderThumbnail,
                        userInsideOnClickListener.otherUsersDataEntity.accountOID,
                        chatRoom.chatRoomId,
                        messages[position].messageDataEntity.messageUUIDPrimaryKey
                    )
                }

            }
        }

        val user =
            chatRoom.chatRoomMembers.getFromMap(messages[position].messageDataEntity.sentByAccountID)

        holder.chatBubbleLayout.setBackgroundResource(R.drawable.background_chat_message_received)

        if (hasCompleteInfo) {
            user?.let {
                holder.nameText.visibility = View.VISIBLE
                holder.nameText.text = it.otherUsersDataEntity.name
            }
        } else {
            //filing up name so it has some width
            holder.nameText.text = "                "
            holder.nameText.visibility = View.VISIBLE
            holder.nameText.setBackgroundResource(R.drawable.background_white_rounded_corners)
        }

        holder.setupThumbnailForCurrentUser(user, holder.senderThumbnail)

    }

    private fun setupMessageListItemLayoutToCurrentUserMessage(
        holder: ChatMessageViewHolder,
    ) {

        //Changing thumbnail layout here (instead of chatBubble layout) because it is the head of the chain and so its bias is used
        val thumbnailLayout =
            holder.senderThumbnail.layoutParams as ConstraintLayout.LayoutParams

        thumbnailLayout.horizontalBias = 1F

        holder.senderThumbnail.layoutParams = thumbnailLayout

        holder.chatBubbleLayout.setBackgroundResource(R.drawable.background_chat_message_sent)

        holder.senderThumbnail.visibility = View.GONE
        holder.nameText.visibility = View.GONE
    }

    private fun setChatBubbleLayoutPaddingNarrowGeneral(
        holder: ChatMessageViewHolder
    ): Int {

        val widePadding = activityContext.resources.getDimension(
            R.dimen.match_list_item_primary_constraint_padding_wide
        ).toInt()

        val narrowPadding = activityContext.resources.getDimension(
            R.dimen.match_list_item_primary_constraint_padding_narrow
        ).toInt()

        //This will allow the top and left to visually be the same as when there are no margins.
        val paddingDifference = widePadding - narrowPadding

        holder.chatBubbleLayout.setPadding(narrowPadding)

        val timeParams = holder.timeText.layoutParams as ConstraintLayout.LayoutParams

        timeParams.setMargins(
            timeParams.leftMargin,
            timeParams.topMargin,
            paddingDifference,
            timeParams.bottomMargin
        )

        holder.timeText.layoutParams = timeParams

        val editedParams = holder.editedText.layoutParams as ConstraintLayout.LayoutParams

        editedParams.setMargins(
            paddingDifference,
            editedParams.topMargin,
            editedParams.rightMargin,
            editedParams.bottomMargin
        )

        holder.editedText.layoutParams = editedParams

        return paddingDifference
    }

    private fun setChatBubbleLayoutPaddingWideGeneral(
        holder: ChatMessageViewHolder
    ): Int {
        val widePadding = activityContext.resources.getDimension(
            R.dimen.match_list_item_primary_constraint_padding_wide
        ).toInt()

        holder.chatBubbleLayout.setPadding(widePadding)

        val timeParams = holder.timeText.layoutParams as ConstraintLayout.LayoutParams

        timeParams.setMargins(
            timeParams.leftMargin,
            timeParams.topMargin,
            0,
            timeParams.bottomMargin
        )

        holder.timeText.layoutParams = timeParams

        val editedParams = holder.editedText.layoutParams as ConstraintLayout.LayoutParams

        editedParams.setMargins(
            0,
            editedParams.topMargin,
            editedParams.rightMargin,
            editedParams.bottomMargin
        )

        holder.editedText.layoutParams = editedParams

        return 0
    }

    private fun setChatBubbleLayoutPadding(
        holder: ChatMessageViewHolder,
        currentUser: Boolean
    ) {

        val setPadding =
            if (holder.narrowPadding) {
                setChatBubbleLayoutPaddingNarrowGeneral(holder)
            } else {
                setChatBubbleLayoutPaddingWideGeneral(holder)
            }

        if (!currentUser) {
            val nameParams = holder.nameText.layoutParams as ConstraintLayout.LayoutParams

            nameParams.setMargins(
                setPadding,
                nameParams.topMargin,
                setPadding,
                nameParams.bottomMargin
            )

            holder.nameText.layoutParams = nameParams
        }
    }

    //set up the layout based on who the message is from and the message type
    //NOTE: The suppression of ClickableViewAccessibility is because of the onTouch events below. They
    // are not something that does not have an effect on the working of the app and blind users do not
    // need to concern themselves with.
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpMessageLayout(holder: ChatMessageViewHolder, position: Int) {

        setChatBubbleLayoutPadding(
            holder,
            messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID
        )

        val typeOfChatMessage =
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                messages[position].messageDataEntity.messageType
            )

        attachMessageToBodyFrameLayout(holder, position)

        setupMainUITextColor(
            holder,
            position
        )

        if (messages[position].messageDataEntity.hasCompleteInfo
            && typeOfChatMessage != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE
        ) { //message does not require updated

            if (messages[position].messageDataEntity.isEdited) { //if message has been edited
                holder.editedText.text =
                    applicationContext.resources.getString(R.string.chat_message_primary_list_item_message_edited)
            } else { //if message has not been edited
                holder.editedText.text = ""
            }

            var filePath = ""
            var mimeType = ""

            //extract mime type and file path for reply
            when (typeOfChatMessage) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
                    mimeTypeFilePaths[messages[position].messageDataEntity.downloadUrl]?.let { mimeTypeInfo ->

                        filePath = mimeTypeInfo.filePath
                        mimeType = mimeTypeInfo.mimeTypeValue
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                    filePath = messages[position].messageDataEntity.filePath
                }
                else -> {
                }
            }

            val messageUUIDPrimaryKey = messages[position].messageDataEntity.messageUUIDPrimaryKey

            val replyMessageInfoObject = ReplyMessageInfoObject(
                chatRoom.chatRoomId,
                messages[position].messageDataEntity.sentByAccountID,
                messageUUIDPrimaryKey,
                convertMessageTypeToReplyBodyCase(typeOfChatMessage),
                messages[position].messageDataEntity.messageText,
                mimeType,
                filePath
            )

            //setup card view to have rounded corners and white background
            when (typeOfChatMessage) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE -> {

                    holder.bodyFrameLayout.radius = 0F
                    holder.bodyFrameLayout.setCardBackgroundColor(
                        ContextCompat.getColor(applicationContext, R.color.colorTransparent)
                    )

                    //LOADING_MESSAGE should always have hasCompleteInfo==false AND it is checked for above.
                    val errorMessage =
                        "It should be impossible for LOADING_MESSAGE to hit this point.\n" +
                                "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )

                    //anchor the message body be attached in the middle
                    val bodyFrameConstraintLayout =
                        holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                    //setting this to the 'default' value that images take before they know the constraints
                    bodyFrameConstraintLayout.width = deviceScreenWidth / 2
                    bodyFrameConstraintLayout.height = deviceScreenWidth / 2
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
                    holder.bodyFrameLayout.radius =
                        applicationContext.resources.getDimension(R.dimen.match_list_item_message_corner_radius)
                    holder.bodyFrameLayout.setCardBackgroundColor(
                        ContextCompat.getColor(applicationContext, R.color.colorTransparent)
                    )

                    //NOTE: See background_white_with_drawable_padding on details why the image view is set instead of
                    // the card view. The card view background is 'special' in that it is a part of the view and
                    // so should not be changed.
                    holder.pictureOrMimeTypeMessageImageView?.let {
                        it.background = ResourcesCompat.getDrawable(
                            applicationContext.resources,
                            R.drawable.background_chat_room_picture_message,
                            activityContext.theme
                        )
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
                -> {
                    cardViewRoundedCornersAndWhiteBackground(holder)
                }
                else -> { //CHAT_TEXT_MESSAGE
                    holder.bodyFrameLayout.radius = 0F
                    holder.bodyFrameLayout.setCardBackgroundColor(
                        ContextCompat.getColor(applicationContext, R.color.colorTransparent)
                    )
                }
            }

            /** see [chat_message_popup] for details on setOnTouchListener **/
            holder.chatBubbleLayout.setOnTouchListener(saveCoordinatesOnTouchListener)
            holder.replyLayout.setOnTouchListener(saveCoordinatesOnTouchListener)

            if (messages[position].messageDataEntity.sentByAccountID != LoginFunctions.currentAccountOID) { //message sent by a different account

                val deleteMessageType =
                    if (chatRoom.accountState == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN) {
                        DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS
                    } else {
                        DeleteMessageTypes.CAN_ONLY_DELETE_FOR_SELF
                    }

                val deleteMessageInfoObject = DeleteMessageInfoObject(
                    messages[position].messageDataEntity.messageUUIDPrimaryKey,
                    deleteMessageType,
                    true
                )

                //extract long click listener and set onClick listeners
                val displayMenuOnLongClickListener: (View) -> Unit =
                    when (typeOfChatMessage) {
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {
                            holder.chatBubbleLayout.setOnClickListener(null)
                            holder.chatBubbleLayout.isClickable = false

                            //NOTE: if this is sent by a different account OID then it must be stored on the server
                            val lambda: (View) -> Unit =
                                {
                                    val showEditText: () -> Unit = {}

                                    showChatMessagePopup(
                                        it,
                                        showEditText,
                                        false,
                                        messages[position].messageDataEntity.messageText,
                                        replyMessageInfoObject,
                                        deleteMessageInfoObject
                                    )
                                }

                            lambda
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {

                            mimeTypeFilePaths[messages[position].messageDataEntity.downloadUrl]?.let { gifFileInfo ->
                                holder.chatBubbleLayout.setSafeOnClickListener {
                                    showPictureMessagePopup(gifFileInfo.filePath)
                                }
                            }

                            //NOTE: if this is sent by a different account OID then it must be stored on the server
                            val lambda: (View) -> Unit =
                                { view ->
                                    val showEditText: () -> Unit = {}

                                    showChatMessagePopup(
                                        view,
                                        showEditText,
                                        false,
                                        "",
                                        replyMessageInfoObject,
                                        deleteMessageInfoObject
                                    )
                                }

                            lambda
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                            holder.chatBubbleLayout.setSafeOnClickListener {
                                showPictureMessagePopup(messages[position].messageDataEntity.filePath)
                            }

                            //NOTE: if this is sent by a different account OID then it must be stored on the server
                            val lambda: (View) -> Unit =
                                {
                                    val showEditText: () -> Unit = {}

                                    showChatMessagePopup(
                                        it,
                                        showEditText,
                                        false,
                                        "",
                                        replyMessageInfoObject,
                                        deleteMessageInfoObject
                                    )
                                }

                            lambda
                        }
                        else -> { //if not chat or picture message
                            holder.chatBubbleLayout.setOnClickListener(null)
                            //NOTE: isClickable is different than isLongClickable
                            holder.chatBubbleLayout.isClickable = false

                            //NOTE: if this is sent by a different account OID then it must be stored on the server
                            val lambda: (View) -> Unit =
                                {
                                    val showEditText: () -> Unit = {}

                                    showChatMessagePopup(
                                        it,
                                        showEditText,
                                        false,
                                        "",
                                        replyMessageInfoObject,
                                        deleteMessageInfoObject
                                    )
                                }

                            lambda
                        }
                    }

                holder.displayMenuOnLongClickListener = displayMenuOnLongClickListener

                //set up chat bubble to display the menu on long click
                holder.chatBubbleLayout.setOnLongClickListener {
                    val moveView = moveMenuDisplayView()
                    displayMenuOnLongClickListener(moveView)
                    true
                }

                if (messages[position].messageDataEntity.isReply) {

                    holder.replyLayout.setOnLongClickListener {
                        val moveView = moveMenuDisplayView()
                        displayMenuOnLongClickListener(moveView)
                        true
                    }
                }

                //extract setup layout params for bodyFrameLayout
                when (typeOfChatMessage) {
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                        //anchor the message body be attached to the left side
                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd = -1

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {

                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        //anchor the message body be attached in the middle
                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {

                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        //anchor the message body be attached in the middle
                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    else -> {

                        //anchor the message body be attached in the middle
                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                }

                setupMessageListItemLayoutToOtherUserMessage(holder, position, true)

                Log.i(
                    "settingDraw",
                    "clear lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
                )

                //remove icon from timeText
                holder.timeText.clearDrawables()

            } else { //sent by this account

                val deleteMessageInfoObject = DeleteMessageInfoObject(
                    messages[position].messageDataEntity.messageUUIDPrimaryKey,
                    DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS,
                    true
                )

                val displayMenuOnLongClickListener: (View) -> Unit =
                    when (typeOfChatMessage) {
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> { //if chat message

                            holder.chatBubbleLayout.setOnClickListener(null)
                            holder.chatBubbleLayout.isClickable = false

                            val lambda: (View) -> Unit =
                                if (messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.NOT_YET_STORED.ordinal) { //message has not been stored inside database yet
                                    /*Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.chat_room_fragment_message_processing,
                                    Toast.LENGTH_SHORT
                                ).show();*/

                                    {}
                                } else { //message has been stored inside database
                                    {
                                        val showEditText: () -> Unit = {

                                            holder.textMessageTextView?.let { textView ->
                                                holder.editTextMessageViewStub?.let { viewStub ->
                                                    textView.visibility = View.GONE
                                                    viewStub.visibility = View.VISIBLE
                                                }
                                            }
                                        }

                                        showChatMessagePopup(
                                            it,
                                            showEditText,
                                            true,
                                            messages[position].messageDataEntity.messageText,
                                            replyMessageInfoObject,
                                            deleteMessageInfoObject
                                        )
                                    }
                                }

                            lambda
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {

                            mimeTypeFilePaths[messages[position].messageDataEntity.downloadUrl]?.let { gifFileInfo ->
                                holder.chatBubbleLayout.setSafeOnClickListener {
                                    showPictureMessagePopup(gifFileInfo.filePath)
                                }
                            }

                            val lambda: (View) -> Unit =
                                if (messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.NOT_YET_STORED.ordinal) { //message has not been stored inside database yet
                                    /*Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.chat_room_fragment_message_processing,
                                    Toast.LENGTH_SHORT
                                ).show()*/

                                    {}
                                } else { //message has been stored inside database
                                    { view ->
                                        val showEditText: () -> Unit = {}

                                        showChatMessagePopup(
                                            view,
                                            showEditText,
                                            false,
                                            "",
                                            replyMessageInfoObject,
                                            deleteMessageInfoObject
                                        )
                                    }
                                }

                            lambda
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {  //if gif or picture message

                            holder.chatBubbleLayout.setSafeOnClickListener {
                                showPictureMessagePopup(messages[position].messageDataEntity.filePath)
                            }

                            val lambda: (View) -> Unit =
                                if (messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.NOT_YET_STORED.ordinal) { //message has not been stored inside database yet
                                    /*Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.chat_room_fragment_message_processing,
                                    Toast.LENGTH_SHORT
                                ).show()*/

                                    {}
                                } else { //message has been stored inside database
                                    {
                                        val showEditText: () -> Unit = {}

                                        showChatMessagePopup(
                                            it,
                                            showEditText,
                                            false,
                                            "",
                                            replyMessageInfoObject,
                                            deleteMessageInfoObject
                                        )
                                    }
                                }

                            lambda
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE -> {  //if loading message

                            //NOTE: This should never happen, however the error for it is already stored above.
                            holder.chatBubbleLayout.setOnClickListener(null)
                            //NOTE: isClickable is different than isLongClickable
                            holder.chatBubbleLayout.isClickable = false

                            val lambda: (View) -> Unit = {}

                            lambda
                        }
                        else -> { //if not chat or picture message

                            holder.chatBubbleLayout.setOnClickListener(null)
                            //NOTE: isClickable is different than isLongClickable
                            holder.chatBubbleLayout.isClickable = false

                            val lambda: (View) -> Unit =
                                if (messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.NOT_YET_STORED.ordinal) { //message has not been stored inside database yet
                                    {}
                                } else { //message has been stored inside database
                                    {
                                        val showEditText: () -> Unit = {}

                                        showChatMessagePopup(
                                            it,
                                            showEditText,
                                            false,
                                            "",
                                            replyMessageInfoObject,
                                            deleteMessageInfoObject
                                        )
                                    }
                                }

                            lambda
                        }
                    }

                holder.displayMenuOnLongClickListener = displayMenuOnLongClickListener


                //set up chat bubble to display the menu on long click
                holder.chatBubbleLayout.setOnLongClickListener {
                    val moveView = moveMenuDisplayView()
                    displayMenuOnLongClickListener(moveView)
                    true
                }

                if (messages[position].messageDataEntity.isReply) {

                    holder.replyLayout.setOnLongClickListener {
                        val moveView = moveMenuDisplayView()
                        displayMenuOnLongClickListener(moveView)
                        true
                    }
                }

                when (typeOfChatMessage) {
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

                        //anchor the message body be attached to the right side
                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        chatTextConstraintLayout.startToStart = -1
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {

                        //anchor the message body be attached in the middle
                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {

                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        //anchor the message body be attached in the middle
                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                    else -> {

                        //anchor the message body be attached in the middle
                        val chatTextConstraintLayout =
                            holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

                        chatTextConstraintLayout.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                        chatTextConstraintLayout.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                        chatTextConstraintLayout.startToStart =
                            R.id.chatMessageListItemTextBubbleConstraintLayout
                        chatTextConstraintLayout.endToEnd =
                            R.id.chatMessageListItemTextBubbleConstraintLayout

                        holder.bodyFrameLayout.layoutParams = chatTextConstraintLayout
                    }
                }

                setupMessageListItemLayoutToCurrentUserMessage(holder)

                val drawableResourceId: Int

                messages[position].messageDataEntity.apply {
                    drawableResourceId =
                        if ((this.isEdited && this.editHasBeenSent)
                            || this.messageSentStatus == ChatMessageStoredStatus.STORED_ON_SERVER.ordinal
                        ) { //if message has been edited and send OR message status is stored on server
                            R.drawable.icon_round_done_chat_messenger_24
                        } else { //message has not been sent to server yet
                            R.drawable.icon_round_access_time_chat_messenger_24
                        }
                }

                holder.timeText.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    ResourcesCompat.getDrawable(
                        applicationContext.resources,
                        drawableResourceId,
                        null
                    ),
                    null,
                )

            }

            //if message does NOT have full info, do NOT show the timestamp for it, show the white bar
            setUpMessageTimestamp(holder.dateText, position, holder.timeText)

            //this should be called last because it measures the layout width
            setUpReplyForMessageLayout(holder, position)

        } else { //message requires updated

            //hide the reply layout (if message does NOT have complete info, the reply will not be downloaded)
            holder.replyLayout.visibility = View.GONE

            holder.editedText.text = ""

            //setup card view to have rounded corners and white background
            cardViewRoundedCornersAndWhiteBackground(holder)

            //anchor the message body be attached in the middle
            val bodyFrameConstraintLayout =
                holder.bodyFrameLayout.layoutParams as ConstraintLayout.LayoutParams

            bodyFrameConstraintLayout.startToStart =
                R.id.chatMessageListItemTextBubbleConstraintLayout
            bodyFrameConstraintLayout.endToEnd =
                R.id.chatMessageListItemTextBubbleConstraintLayout

            when (typeOfChatMessage) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {
                    bodyFrameConstraintLayout.width = deviceScreenWidthThreeFifths
                    bodyFrameConstraintLayout.height = bodyFrameConstraintLayout.width * 1 / 5
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
                    bodyFrameConstraintLayout.width = deviceScreenWidthThreeFifths
                    bodyFrameConstraintLayout.height = bodyFrameConstraintLayout.width * 2 / 3
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
                -> {
                    //set the size of the CardView to be the image size
                    bodyFrameConstraintLayout.width = holder.imageWidth
                    bodyFrameConstraintLayout.height = holder.imageHeight
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE -> {
                    //setting this to the 'default' value that images take before they know the constraints
                    bodyFrameConstraintLayout.width = deviceScreenWidth / 2
                    bodyFrameConstraintLayout.height = deviceScreenWidth / 2
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
                    //this is set inside setUpMessage
                }
                else -> {
                    val errorMessage = "Invalid message type passed to setUpMessageLayout().\n" +
                            "typeOfChatMessage: $typeOfChatMessage\n" +
                            "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )
                }
            }

            holder.bodyFrameLayout.layoutParams = bodyFrameConstraintLayout

            if (messages[position].messageDataEntity.sentByAccountID != LoginFunctions.currentAccountOID
            ) { //message sent by a different account
                setupMessageListItemLayoutToOtherUserMessage(holder, position, false)
            } else { //sent by this account
                setupMessageListItemLayoutToCurrentUserMessage(holder)
            }

            holder.timeText.setBackgroundResource(R.drawable.background_white_rounded_corners)

            //filling up timeText (arbitrary amount) to give the timeText some width for the white bar
            holder.timeText.text = "                "

            holder.dateText.visibility = View.GONE

            Log.i(
                "settingDraw",
                "clearDrawables lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            holder.timeText.clearDrawables()

            val messageUUIDsToRequest = mutableListOf<String>()

            //request message update for a batch of messages if not already requested
            if (!messages[position].messageUpdateHasBeenRequestedFromServer) {

                Log.i(
                    "followingUpdates",
                    "ChatMessageAdapter: scrollingUpOrDown: $scrollingUpOrDown"
                )

                var numberDisplayedMessagesFound = 0

                val requestMessagesForLoopRange =
                    if (scrollingUpOrDown == ScrollingUpOrDown.SCROLLING_UP) {
                        //request message ABOVE the current message
                        //this could happen if the user clicks a reply and is sent a ways up the recycler view
                        // then begins scrolling back down
                        position downTo 0
                    } else {
                        //request message BELOW the current message
                        position..messages.lastIndex
                    }

                Log.i(
                    "numUpdatesTooSmall",
                    "requestMessagesForLoopRange: $requestMessagesForLoopRange"
                )

                for (i in requestMessagesForLoopRange) {

                    Log.i(
                        "numUpdatesTooSmall",
                        "messageLayoutType: ${messages[i].messageLayoutType}"
                    )

                    if (messages[i].messageLayoutType == LayoutType.LAYOUT_MESSAGE) {
                        numberDisplayedMessagesFound++
                        if (!messages[i].messageUpdateHasBeenRequestedFromServer
                            && !messages[i].messageDataEntity.hasCompleteInfo
                            && messages[position].messageDataEntity.messageSentStatus == ChatMessageStoredStatus.STORED_ON_SERVER.ordinal
                        ) {
                            messages[i].messageUpdateHasBeenRequestedFromServer = true
                            messageUUIDsToRequest.add(messages[i].messageDataEntity.messageUUIDPrimaryKey)
                        }

                    } else if (messages[i].messageLayoutType == LayoutType.LAYOUT_SINGLE) {
                        numberDisplayedMessagesFound++
                    }

                    Log.i(
                        "numUpdatesTooSmall",
                        "numberDisplayedMessagesFound: $numberDisplayedMessagesFound maxNumberMessagesToRequest: ${GlobalValues.server_imported_values.maxNumberMessagesToRequest}"
                    )

                    //break if this loop has checked the max number of messages the user can request
                    if (numberDisplayedMessagesFound == GlobalValues.server_imported_values.maxNumberMessagesToRequest) {
                        break
                    }
                }
            }

            if (messageUUIDsToRequest.isNotEmpty()) {
                Log.i(
                    "numUpdatesTooSmall",
                    "num message updates requested: ${messageUUIDsToRequest.size}"
                )
                //download relevant messages
                requestMessagesInfoFromServer(chatRoom.chatRoomId, messageUUIDsToRequest)
            }
        }
    }

    //setup card view to have rounded corners and white background
    private fun cardViewRoundedCornersAndWhiteBackground(holder: ChatMessageViewHolder) {
        holder.bodyFrameLayout.radius =
            applicationContext.resources.getDimension(R.dimen.match_list_item_message_corner_radius)
        holder.bodyFrameLayout.setCardBackgroundColor(
            ContextCompat.getColor(applicationContext, R.color.colorWhite)
        )
    }

    //setup reply views
    //NOTE: This expects the chatBubbleLayout to be set up, it will call measure() on it.
    private fun setUpReplyForMessageLayout(holder: ChatMessageViewHolder, position: Int) {

        if (messages[position].messageDataEntity.isReply) { //if message is reply type

            if (messages[position].messageDataEntity.replyIsFromMessageUUID.isValidUUIDKey()
                && messages[position].messageDataEntity.replyIsSentFromOID.isValidMongoDBOID()
            ) {

                holder.replyLayout.visibility = View.VISIBLE

                var indexInsideMessages = -1
                var isDeleted = false

                for (i in position downTo 0) {
                    if (messages[i].messageDataEntity.messageUUIDPrimaryKey == messages[position].messageDataEntity.replyIsFromMessageUUID) {

                        if (messages[position].messageDataEntity.deletedType == TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE) {
                            isDeleted = true
                            break
                        }

                        indexInsideMessages = i
                        break
                    }
                }

                //NOTE: There may be an unusual situation where say userA can reply to a message from userB then
                // userC will see the reply first because of how messages are stored when one user is offline
                // so reading the entire list if message was not found
                if (indexInsideMessages == -1 && !isDeleted) { //message was not found
                    for (i in position + 1..messages.lastIndex) {
                        if (messages[i].messageDataEntity.messageUUIDPrimaryKey == messages[position].messageDataEntity.replyIsFromMessageUUID) {

                            if (messages[position].messageDataEntity.deletedType == TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE) {
                                break
                            }

                            indexInsideMessages = i
                            break
                        }
                    }
                }

                if (indexInsideMessages != -1
                    //NOTE: don't move this out, indexInsideMessages can be -1 until it is checked
                    && convertMessageTypeToReplyBodyCase(
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                            messages[indexInsideMessages].messageDataEntity.messageType
                        )
                    ).number != messages[position].messageDataEntity.replyType
                ) { //if message was found however message types do not match
                    val errorMessage = "Reply type should never be different than message type.\n" +
                            "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )

                    indexInsideMessages = -1
                }

                if (indexInsideMessages != -1) { //if message was found inside list
                    holder.replyLayout.setOnClickListener {
                        scrollToSelectedMessage(indexInsideMessages)
                    }
                } else { //if message was NOT found inside list

                    //possible if for example the history was cleared or message was deleted
                    holder.replyLayout.setOnClickListener(null)
                    holder.replyLayout.isClickable = false
                }

                val replyType = TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.forNumber(
                    messages[position].messageDataEntity.replyType
                )

                if (replyType != TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET) { //message that can be replied to

                    //set up name on reply
                    extractAndSetupName(
                        applicationContext,
                        messages[position].messageDataEntity.replyIsSentFromOID,
                        userFirstName,
                        holder.replyNameTextView,
                        chatRoom.chatRoomMembers,
                        errorStore
                    )

                    when (replyType) {
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {

                            //hide the image view
                            holder.replyImageView.visibility = View.GONE

                            val replyIsFromMessage =
                                messages[position].messageDataEntity.replyIsFromMessageText

                            if (replyIsFromMessage.isNotEmpty()) { //if message text is set
                                holder.replyMessageTypeTextView.text = replyIsFromMessage
                            } else { //if message text is not set
                                holder.replyLayout.visibility = View.GONE

                                val errorMessage =
                                    "This message (which was checked to be a CHAT_TEXT_MESSAGE) should have something set.\n" +
                                            "indexInsideMessages: $indexInsideMessages\n" +
                                            "message: ${messages[position].messageDataEntity}"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorMessage,
                                    applicationContext
                                )
                            }
                        }
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY -> {
                            //hide the image view
                            holder.replyImageView.visibility = View.GONE

                            holder.replyMessageTypeTextView.setText(
                                R.string.chat_message_type_text_location
                            )
                        }
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY -> {
                            //hide the image view
                            holder.replyImageView.visibility = View.GONE

                            holder.replyMessageTypeTextView.setText(
                                R.string.chat_message_type_text_invite
                            )
                        }
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY,
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY,
                        -> {

                            val thumbnailPath =
                                messages[position].messageDataEntity.replyIsFromThumbnailFilePath

                            holder.replyImageView.visibility = View.VISIBLE

                            //hide image view if thumbnail is not properly displayed
                            //NOTE: do not display an error if filepath is == "" it is perfectly valid for this to be true if
                            // the user thumbnail was removed because it is inappropriate
                            //NOTE: If the path was set to GlobalValues.PICTURE_NOT_FOUND_ON_SERVER then glide will simply hide
                            // the image view when it fails to load the image.
                            CoroutineScope(Main).launch {
                                //An exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                                // If this is called from inside another glide context. Therefore it is called from a fresh coroutine.
                                glideContext
                                    .load(thumbnailPath)
                                    .error(R.drawable.icon_round_broken_image_24)
                                    .listener(
                                        object : RequestListener<Drawable?> {
                                            override fun onLoadFailed(
                                                e: GlideException?,
                                                model: Any?,
                                                target: Target<Drawable?>,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                holder.replyImageView.visibility = View.GONE
                                                return false
                                            }

                                            override fun onResourceReady(
                                                resource: Drawable?,
                                                model: Any?,
                                                target: Target<Drawable?>,
                                                dataSource: DataSource,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                return false
                                            }
                                        }
                                    )
                                    .into(holder.replyImageView)
                            }

                            val replyIsFromMessage =
                                generateNameForPictureOrMimeTypeMessageReply(
                                    replyType,
                                    activityContext,
                                    errorStore
                                ) {
                                    messages[position].messageDataEntity.replyIsFromMimeType
                                }

                            if (replyIsFromMessage.isNotEmpty()) { //if message text is set
                                holder.replyMessageTypeTextView.text = replyIsFromMessage
                            } else { //if message text is not set
                                holder.replyLayout.visibility = View.GONE

                                val errorMessage =
                                    "This message (which was checked to be a PICTURE or MIME_TYPE) should have something set.\n" +
                                            "indexInsideMessages: $indexInsideMessages\n" +
                                            "message: ${messages[position].messageDataEntity}"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorMessage,
                                    applicationContext
                                )
                            }
                        }
                        else -> {
                            holder.replyLayout.visibility = View.GONE

                            val errorMessage =
                                "This should never be reached, message type is checked before reaching this point.\n" +
                                        "replyType: $replyType\n" +
                                        "message: ${messages[position].messageDataEntity}"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage,
                                applicationContext
                            )
                        }
                    }

                    //NOTE: This is done programmatically because having a double nested ConstraintLayout does not play nice
                    // with a parent of parent having the maxWidth set. If layout_constrainedWidth="true" on the child views
                    // (replyNameTextView & replyMessageTypeTextView) then they will be constrained inside of the parent
                    // of the parent layouts constraints (0dp will work the same way) instead of constrained inside of their
                    // immediate parent.
                    //    In other words the TextViews will get so large they go outside the bounds of the chat message bubble.
                    if (holder.replyLayout.visibility != View.GONE) { //if no errors occurred above

                        setupReplyUITextColor(
                            holder,
                            position
                        )

                        val updatedSideMargins =
                            if (holder.narrowPadding) {
                                val widePadding = activityContext.resources.getDimension(
                                    R.dimen.match_list_item_primary_constraint_padding_wide
                                ).toInt()

                                val narrowPadding = activityContext.resources.getDimension(
                                    R.dimen.match_list_item_primary_constraint_padding_narrow
                                ).toInt()

                                widePadding - narrowPadding
                            } else {
                                0
                            }

                        val replyLayoutParam =
                            holder.replyLayout.layoutParams as ConstraintLayout.LayoutParams

                        val updatedTopMargin =
                            if (messages[position].messageDataEntity.sentByAccountID == LoginFunctions.currentAccountOID
                                && holder.narrowPadding
                            ) {
                                activityContext.resources.getDimension(
                                    R.dimen.match_list_item_vertical_gaps_between_bubble_items
                                ).toInt()
                            } else {
                                0
                            }

                        //NOTE: Setting reply layout margin here, adding padding instead will make the replyLayout.measure below will
                        // not return the proper value. Setting padding AFTER the measurement is taken won't work either. It will make
                        // a bug occur where reply layouts will end up undersized (the width is almost nothing).
                        replyLayoutParam.setMargins(
                            updatedSideMargins,
                            updatedTopMargin,
                            updatedSideMargins,
                            replyLayoutParam.bottomMargin,
                        )

                        holder.replyLayout.layoutParams = replyLayoutParam
                    }

                } else {
                    holder.replyLayout.visibility = View.GONE

                    val errorMessage = "$replyType is not an allowed message type to reply to.\n" +
                            "replyType: $replyType\n" +
                            "message: ${messages[position].messageDataEntity}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )
                }

            } else { //if improper mongoDB OID
                holder.replyLayout.visibility = View.GONE

                val errorMessage =
                    "The replyIsFromUUID/replyIsSentFromOID is not a properly formatted UUID/OID.\n" +
                            "replyIsSentFromOID: ${messages[position].messageDataEntity.replyIsSentFromOID}" +
                            "replyIsFromMessageUUID: ${messages[position].messageDataEntity.replyIsFromMessageUUID}" +
                            "message: ${messages[position].messageDataEntity}"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

            }

        } else { //if message is not reply type
            holder.replyLayout.visibility = View.GONE
        }
    }

    private fun extractCurrentMessageTime(messageDataEntity: MessagesDataEntity): Long {

        return when {
            messageDataEntity.messageEditedTime != -1L -> { //if the message is edited
                messageDataEntity.messageEditedTime
            }
            messageDataEntity.messageStoredOnServerTime != -1L -> { //if message has been stored on server
                messageDataEntity.messageStoredOnServerTime
            }
            else -> { //if message has only been stored in database (this should always be set)
                messageDataEntity.messageStoredInDatabaseTime
            }
        }
    }

    //set up the timestamp for the message
    private fun setUpMessageTimestamp(
        dateText: TextView,
        position: Int,
        timeText: TextView? = null,
    ) {

        val currentMessageCalendar = Calendar.getInstance()

        val currentYear = currentMessageCalendar.get(Calendar.YEAR)
        val currentMonth = currentMessageCalendar.get(Calendar.MONTH)
        val currentDayOfMonth = currentMessageCalendar.get(Calendar.DAY_OF_MONTH)

        currentMessageCalendar.timeInMillis =
            extractCurrentMessageTime(messages[position].messageDataEntity)

        //if the message requires a time, set it
        timeText?.text = convertTimeToString(
            currentMessageCalendar.get(Calendar.HOUR_OF_DAY),
            currentMessageCalendar.get(Calendar.MINUTE)
        )

        var previousIndex = -1

        for (i in position - 1 downTo 0) {
            if (messages[i].messageLayoutType != LayoutType.LAYOUT_EMPTY) {
                previousIndex = i
                break
            }
        }

        if (previousIndex == -1) { //if first element in list

            dateText.text = displayDateForChatMessage(
                currentMessageCalendar.get(Calendar.YEAR),
                currentMessageCalendar.get(Calendar.MONTH),
                currentMessageCalendar.get(Calendar.DAY_OF_MONTH),
                currentYear,
                currentMonth,
                currentDayOfMonth,
                errorStore
            )

            dateText.visibility = View.VISIBLE
        } else { //if not the first element in the list

            val previousMessageCalendar = Calendar.getInstance()

            //find previous element stored time
            previousMessageCalendar.timeInMillis =
                extractCurrentMessageTime(messages[previousIndex].messageDataEntity)

            if (previousMessageCalendar.get(Calendar.YEAR) != currentMessageCalendar.get(Calendar.YEAR)
                || previousMessageCalendar.get(Calendar.MONTH) != currentMessageCalendar.get(
                    Calendar.MONTH
                )
                || previousMessageCalendar.get(Calendar.DAY_OF_MONTH) != currentMessageCalendar.get(
                    Calendar.DAY_OF_MONTH
                )
            ) { //if the previous message was sent the day before (or longer) then show a new date
                dateText.text = displayDateForChatMessage(
                    currentMessageCalendar.get(Calendar.YEAR),
                    currentMessageCalendar.get(Calendar.MONTH),
                    currentMessageCalendar.get(Calendar.DAY_OF_MONTH),
                    currentYear,
                    currentMonth,
                    currentDayOfMonth,
                    errorStore
                )

                dateText.visibility = View.VISIBLE
            } else { //if the previous message was sent the same day
                dateText.visibility = View.GONE
            }
        }
    }

    fun setScrollingUpOrDown(passedScrollingUpOrDown: ScrollingUpOrDown) {
        scrollingUpOrDown = passedScrollingUpOrDown
    }

    class ChatMessageViewHolder(
        itemView: View,
        private val glideContext: RequestManager,
        private val applicationContext: Context
    ) : RecyclerView.ViewHolder(itemView),
        OnMapReadyCallback {

        val identifierNum: Int = identifier

        init {
            identifier++
        }

        val chatBubbleLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatMessageListItemTextBubbleConstraintLayout)
        val senderThumbnail: ImageView =
            itemView.findViewById(R.id.chatMessageListItemThumbnailImageView)
        val dateText: TextView = itemView.findViewById(R.id.chatMessageListItemDateTextView)

        var nameText: TextView = itemView.findViewById(R.id.chatMessageListItemNameTextView)
        var timeText: TextView = itemView.findViewById(R.id.chatMessageListItemTimeTextView)
        var editedText: TextView = itemView.findViewById(R.id.chatMessageListItemEditedTextView)

        var progressBar: ProgressBar = itemView.findViewById(R.id.chatMessageListItemProgressBar)

        //will be used with a message that is a 'reply'
        var replyLayout: ConstraintLayout =
            itemView.findViewById(R.id.chatMessageListItemTextReplyInclude)
        var replyImageView: ImageView =
            itemView.findViewById(R.id.chatMessageListItemReplyImageView)
        var replyNameTextView: TextView =
            itemView.findViewById(R.id.chatMessageListItemReplyNameTextView)

        //this object is never directly hidden if message is a reply
        var replyMessageTypeTextView: TextView =
            itemView.findViewById(R.id.chatMessageListItemReplyMessageTypeTextView)

        var replyIndentationLine: View =
            itemView.findViewById(R.id.chatRoomMessagesIndentationLineView)

        //will add the different type of messages to this CardView (inherits from FrameLayout)
        //NOTE: CardView extends FrameLayout, so use frame layout parameters for it
        val bodyFrameLayout: CardView =
            itemView.findViewById(R.id.chatMessageListItemMessageBodyCardView)
        var messageType: MessageType = MessageType.OTHER
        var messageUUIDPrimaryKey: String = ""
        var hasCompleteInfo: Boolean = true

        var messageSentByAccountOID = ""

        //value set when MessageType == TEXT_MESSAGE
        var textMessage: String = ""
        var textMessageTextView: TextView? = null
        var editTextMessageViewStub: ViewStub? = null
        var editTextMessageStubEditText: EditText? = null
        var editTextMessageStubSaveButton: TextView? = null
        var editTextMessageStubCancelButton: TextView? = null

        //value set when MessageType == LOCATION_MESSAGE
        var mapView: MapView? = null
        var mapViewStub: ViewStub? = null
        var map: GoogleMap? = null
        var latLng: LatLng? = null

        //value set when MessageType == PICTURE_OR_MIME_TYPE_MESSAGE
        var pictureOrMimeTypeMessageImageView: ImageView? = null
        var glideImageLoad: FutureTarget<File?>? = null
        var imageWidth: Int = 0
        var imageHeight: Int = 0

        //value set when MessageType == INVITED_TO_CHAT_ROOM
        var inviteViewStub: ViewStub? = null
        var inviteState: InviteState = InviteState.INVITE_EXPIRED
        var inviteMessageHeader = ""
        var inviteChatRoomName = ""
        var displayChatRoomName = false
        var inviteChatRoomThirdLine = ""
        var displayChatRoomThirdLine = false
        var inviteChatRoomId = ""
        var inviteChatRoomPassword = ""

        //value set when MessageType == LOADING_MESSAGE
        var loadingViewStub: ViewStub? = null

        var displayMenuOnLongClickListener: ((View) -> Unit)? = null

        //Will be set to false if a narrow padding is used around the message. This is the
        // padding of chatBubbleLayout, however there are a variety of things that are based
        // on it.
        var narrowPadding = false

        override fun onMapReady(googleMap: GoogleMap) {
            Log.i("map_stuff_z", "onMapReady() called")
            MapsInitializer.initialize(applicationContext)
            // If map is not initialised properly
            map = googleMap

            /** onTouchListener is not overridden, see [chat_message_popup] for details**/

            map?.setOnMapLongClickListener {
                displayMenuOnLongClickListener?.let { listener -> listener(bodyFrameLayout) }
            }

            setMapLocation()
        }

        private fun setMapLocation() {
            map?.let {
                it.addTileOverlay(
                    TileOverlayOptions().tileProvider(TileProviderImpl()).zIndex(3000F)
                )
                latLng?.let { latLngItem ->
                    it.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            latLngItem,
                            GlobalValues.MAP_VIEW_INITIAL_ZOOM
                        )
                    )
                    it.addMarker(MarkerOptions().position(latLngItem))
                    it.mapType = GoogleMap.MAP_TYPE_NORMAL
                }
            }
        }

        fun hideItemView() {
            /** See [ChatRoomInfoViewMember.hideItemView]
             *  for details on why this is done in this way.
             */
            dateText.visibility = View.GONE
            senderThumbnail.visibility = View.GONE
            chatBubbleLayout.visibility = View.GONE
        }

        fun updateThumbnailPictureIfCorrectUserOID(user: OtherUsersInfo?) {
            if (
                messageSentByAccountOID.isNotEmpty()
                && user?.otherUsersDataEntity?.accountOID == messageSentByAccountOID
            ) {
                setupThumbnailForCurrentUser(user, senderThumbnail)
            }
        }

        fun setupThumbnailForCurrentUser(
            user: OtherUsersInfo?,
            senderThumbnailImageView: ImageView
        ) {

            //NOTE: thumbnail path could be empty if user picture was removed because it was
            // inappropriate
            val thumbnailPath =
                user?.otherUsersDataEntity?.thumbnailPath ?: ""

            val signatureTimestamp = user?.otherUsersDataEntity?.thumbnailLastTimeUpdated ?: -1

            CoroutineScope(Main).launch {
                //and exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                // if this is called from inside another glide context
                glideContext
                    .load(thumbnailPath)
                    .signature(generateFileObjectKey(signatureTimestamp))
                    .apply(RequestOptions.circleCropTransform())
                    .error(GlobalValues.defaultPictureResourceID)
                    .into(senderThumbnailImageView)
            }
        }
    }

    class ChatSingleViewHolder(
        itemView: View,
        deviceScreenWidthThreeFifths: Int
    ) :
        RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.chatMessageListSingleItemTextView)
        val dateText: TextView = itemView.findViewById(R.id.chatMessageListSingleItemDateTextView)

        init {
            text.maxWidth = deviceScreenWidthThreeFifths
        }

        fun hideItemView() {
            /** See [ChatRoomInfoViewMember.hideItemView]
             *  for details on why this is done in this way.
             */
            text.visibility = View.GONE
            dateText.visibility = View.GONE
        }
    }

    class ChatBlankViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        init {
            itemView.visibility = View.GONE
        }
    }

}