package site.letsgoapp.letsgo.utilities

import account_state.AccountState
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.work.WorkManager
import com.google.android.gms.maps.model.LatLng
import com.google.common.primitives.Ints
import com.google.protobuf.ByteString
import grpc_chat_commands.ChatRoomCommands
import login_to_server_basic_info.LoginToServerBasicInfoOuterClass
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.ChatMessageAdapter
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment.ChatRoomListChatRoomsAdapter
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ApplicationAccountInfo
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersInfo
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.convertOtherUsersDataEntityToOtherUserInfoWithChatRoom
import site.letsgoapp.letsgo.databinding.DialogFeedbackEditTextBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.INVALID_LOGIN_TOKEN
import site.letsgoapp.letsgo.globalAccess.GlobalValues.messagesDaoAllowedBlockedMessageTypesString
import site.letsgoapp.letsgo.globalAccess.GlobalValues.messagesDaoSelectChatRoomLastActiveTimeString
import site.letsgoapp.letsgo.globalAccess.GlobalValues.messagesDaoSelectFinalMessageString
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.workers.chatStreamWorker.ChatStreamWorker
import type_of_chat_message.TypeOfChatMessageOuterClass
import user_account_type.UserAccountTypeOuterClass.UserAccountType
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

const val MATCH_ARRAY_TO_STRING_SEPARATOR = ",,aaa,aaa,,"
const val PICTURE_ELEMENT_FIELDS_TO_STRING_SEPARATOR = ",,:],,"

const val PICTURE_STRING_FRAGMENT_ARGUMENT_KEY = "pictureString"
const val PICTURE_INDEX_NUMBER_FRAGMENT_ARGUMENT_KEY = "pictureIndex"
const val PICTURE_SINGLE_PATH_FRAGMENT_ARGUMENT_KEY = "picturePath"
const val PICTURE_SINGLE_TIMESTAMP_FRAGMENT_ARGUMENT_KEY = "pictureTimestamp"

enum class ChatRoomUpdateMade {
    CHAT_ROOM_JOINED,
    CHAT_ROOM_LEFT,
    CHAT_ROOM_NEW_MATCH,
    CHAT_ROOM_MATCH_CANCELED,
    CHAT_ROOM_EVENT_JOINED,
}

enum class TypeOfUpdatedOtherUser {
    OTHER_USER_UPDATED,
    OTHER_USER_JOINED
}

enum class ChatRoomListCalledFrom {
    MESSENGER_FRAGMENT,
    INVITE_FRAGMENT;

    companion object {
        fun setVal(value: Int?): ChatRoomListCalledFrom {
            return when (value) {
                0 -> MESSENGER_FRAGMENT
                else -> INVITE_FRAGMENT
            }
        }
    }
}

enum class MenuOptionSelected {
    JOIN_CHAT_ROOM_PRESSED,
    CREATE_CHAT_ROOM_PRESSED
}

enum class ReasonForLeavingChatRoom {
    USER_LEFT_CHAT_ROOM,
    UN_MATCHED_FROM_CHAT_ROOM
}

data class TimeFrameSingleTimeDataClass(
    var timeStamp: Long,
    var isStopTime: Boolean,
    var timeFrameFrom: TimeFrameFrom,
    var initialArrayIndex: Int,
)

data class LeaveChatRoomReturnDataHolder(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val chatRoomId: String,
    val reasonForLeavingChatRoom: ReasonForLeavingChatRoom,
)

data class BlockAndReportChatRoomResultsHolder(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val accountOID: String,
    val accountBlocked: Boolean, //true will mean the account was blocked, false will mean the account was unblocked
    val unMatch: Boolean = false, //can only be true if accountBlocked is false
)

data class AllChatRoomMessagesHaveBeenObservedHolder(
    val allMessagesHaveBeenObserved: Boolean,
    val leftChatRoomId: String,
    val mostRecentMessageUUID: String,
    val mostRecentMessageSentByOID: String,
    val mostRecentMessageTimestamp: Long,
    val mostRecentMessageChatRoomId: String,
)

//data class GetSingleChatImageResultsHolder(
//    var errorStatus: GrpcFunctionErrorStatusEnum,
//    var getSinglePictureStatus: ChatRoomCommands.GetSingleChatImageResponse.GetSinglePictureStatus,
//    var newPictureFilePath: String,
//    var imageWidth: Int,
//    var imageHeight: Int,
//    var uuidPrimaryKey: String,
//)

data class MimeTypeHolderObject(
    val filePath: String,
    val width: Int,
    val height: Int,
    val mimeTypeValue: String,
)

data class ChatRoomBasicInfoObject(
    val chatRoomId: String = "",
    val chatRoomName: String = "",
    val chatRoomPassword: String = "",
)

data class InviteMessageObject(
    var sendInviteMessage: Boolean = false,
    var inviteMessageChatRoomBasicInfo: ChatRoomBasicInfoObject = ChatRoomBasicInfoObject(),
    var inviteMessageUserOid: String = "",
    var inviteMessageUserName: String = "",
)

data class LocationSelectedObject(
    var sendLocationMessage: Boolean = false,
    var selectLocationCurrentLocation: LatLng = GlobalValues.defaultLocation,
)

enum class DeleteMessageTypes {
    NONE,
    CAN_DELETE_FOR_ALL_USERS,
    CAN_ONLY_DELETE_FOR_SELF
}

data class DeleteMessageInfoObject(
    val deletedMessageUUIDPrimaryKey: String = "",
    val possibleDeleteTypes: DeleteMessageTypes = DeleteMessageTypes.NONE,
    val showDialogs: Boolean = true
)

//Used as an argument for application_nav_host inside the SelectLocationFragment.
@Keep
enum class ReasonSelectLocationCalled {
    CALLED_FOR_PINNED_LOCATION,
    CALLED_FOR_LOCATION_MESSAGE
}

data class ChatRoomLocationRequestReturn(
    val typeOfLocationUpdate: TypeOfLocationUpdate,
    val successful: Boolean
)

data class ReplyMessageInfoObject(
    var chatRoomId: String = "",
    var sentByAccountOID: String = "",
    var messageBeingRepliedToUUID: String = "",
    var replyType: TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase = TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET,
    var messageText: String = "", //only set if messageType == TEXT
    var mimeType: String = "", //only set if messageType == GIF types
    var imageFilePath: String = "", //only set if messageType == (PICTURE || GIF) types
    var byteArray: ByteArray = byteArrayOf(),
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReplyMessageInfoObject

        if (chatRoomId != other.chatRoomId) return false
        if (sentByAccountOID != other.sentByAccountOID) return false
        if (messageBeingRepliedToUUID != other.messageBeingRepliedToUUID) return false
        if (replyType != other.replyType) return false
        if (messageText != other.messageText) return false
        if (mimeType != other.mimeType) return false
        if (imageFilePath != other.imageFilePath) return false
        if (!byteArray.contentEquals(other.byteArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chatRoomId.hashCode()
        result = 31 * result + sentByAccountOID.hashCode()
        result = 31 * result + messageBeingRepliedToUUID.hashCode()
        result = 31 * result + replyType.hashCode()
        result = 31 * result + messageText.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + imageFilePath.hashCode()
        result = 31 * result + byteArray.contentHashCode()
        return result
    }
}

data class ApplicationAccountInfoDataHolder(
    val pictureInfo: MutableList<AccountPictureDataEntity>,
    val accountInfo: ApplicationAccountInfo,
    val fragmentInstanceId: String,
)

//NOTE: map will be initialized in onCreateViewHolder, however each member must be added from outside by addItemToMemberMap()
enum class LayoutType {
    LAYOUT_MESSAGE,
    LAYOUT_SINGLE,
    LAYOUT_EMPTY;

    companion object {
        fun setVal(value: Int): LayoutType {
            return when (value) {
                0 -> {
                    LAYOUT_MESSAGE
                }
                1 -> {
                    LAYOUT_SINGLE
                }
                else -> {
                    LAYOUT_EMPTY
                }
            }
        }
    }
}

//Used by app activity to show loading status
class LoadingDialogFragment : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return activity?.let {

            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val inflatedView = inflater.inflate(R.layout.dialog_fragment_loading, null)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(inflatedView)
            builder.setCancelable(false)

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

//Used in chat room fragments to show kicked or banned
class KickBanAlertDialogFragment(
    private val title: Int,
    private val messageBody: Int,
    private var navigateAction: (() -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    private fun runLambda() {
        //only want lambda to be able to navigate once
        navigateAction?.let { it1 -> it1() }
        navigateAction = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)

        //NOTE: This must have a positive button, however it must ALWAYS navigate when it dies. Otherwise
        // situations can occur if the activity must be recreated while the dialog is still active. Because
        // the lambda is called in onDestroy(), this button can be empty.
        alertDialogBuilder.setPositiveButton(android.R.string.ok) { _, _ -> }

        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        runLambda()
        super.onDestroy()
    }
}

//Used in modifyProfileScreenFragment to display warnings
class WarningDialogFragment(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(android.R.string.ok, okButtonAction)
        alertDialogBuilder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
    }
}

//Used in modifyProfileScreenFragment to get the email
class CollectEmailDialogFragment(
    private val title: String,
    private val messageBody: String,
    private var saveEmail: ((String) -> Unit)?,
    private var invalidEmail: (() -> Unit)?,
) : DialogFragment() {

    private lateinit var editText: EditText

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        editText = EditText(context, null)
        editText.filters = arrayOf(
            ByteLengthFilter(
                GlobalValues.server_imported_values.maximumNumberAllowedBytes
            )
        )

        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
        alertDialogBuilder.setPositiveButton(R.string.modify_profile_screen_save_email_positive_button_name) { dialogInterface: DialogInterface, _: Int ->

            val emailAddress = editText.text

            val emailRegex = Regex(GlobalValues.EMAIL_REGEX_STRING)
            val emailValid = emailRegex.matches(emailAddress)
            if (emailValid) {
                saveEmail?.let { it(editText.text.toString()) }
            } else {
                invalidEmail?.let { it() }
            }

            dialogInterface.dismiss()
        }
        alertDialogBuilder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }

        alertDialogBuilder.setView(editText)
        return alertDialogBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        editText.isCursorVisible = false
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveEmail = null
        invalidEmail = null
    }
}

//Will run the first lambda and no more. This will either be okButtonAction or
// cancelDismissAction.
class MutuallyExclusiveLambdaAlertDialogFragmentWithRoundedCorners(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?,
    private var cancelDismissAction: ((DialogInterface, Int) -> Unit)?
) : DialogFragment() {

    private var lambdaHasRun = false

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        if (title != "") {
            alertDialogBuilder.setTitle(title)
        }

        alertDialogBuilder.setMessage(messageBody)

        alertDialogBuilder.setPositiveButton(android.R.string.ok) { dialog, index ->
            lambdaHasRun = true
            okButtonAction?.invoke(dialog, index)
        }

        alertDialogBuilder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }

        return alertDialogBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!lambdaHasRun) {
            lambdaHasRun = true
            cancelDismissAction?.invoke(dialog, 0)
        }
        super.onDismiss(dialog)
    }

    override fun onCancel(dialog: DialogInterface) {
        if (!lambdaHasRun) {
            lambdaHasRun = true
            cancelDismissAction?.invoke(dialog, 0)
        }
        super.onCancel(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
        cancelDismissAction = null
    }
}

//Used in chat room fragment to check if user is sure of their choice
class BasicAlertDialogFragmentWithRoundedCorners(
    private val title: String,
    private val messageBody: String,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        if (title != "") {
            alertDialogBuilder.setTitle(title)
        }

        alertDialogBuilder.setMessage(messageBody)

        alertDialogBuilder.setPositiveButton(android.R.string.ok, okButtonAction)
        alertDialogBuilder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }

        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
    }
}

//Used to display info to user, single button will dismiss dialog
class InfoAlertDialogFragment(
    private val title: String,
    private val messageBody: CharSequence
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        if (title != "") {
            alertDialogBuilder.setTitle(title)
        }

        alertDialogBuilder.setMessage(messageBody)
        alertDialogBuilder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
        }
        return alertDialogBuilder.create()
    }
}

//Used in chat room fragment to check if user wants to delete fragment
class DeleteMessageForAllAlertDialogFragment(
    private val optionsArray: Array<String>,
    private var itemsListener: ((DialogInterface, Int) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setItems(optionsArray, itemsListener)
        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        itemsListener = null
    }

}

//Used in chat room fragment to check if user wants to delete fragment
class EnableLocationDialogFragment(
    private var positiveButtonListener: (() -> Unit)?,
    private var negativeButtonListener: ((dialog: DialogInterface?) -> Unit)?,
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        isCancelable = false

        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        alertDialogBuilder.setMessage(R.string.activities_shared_enable_location_dialog_message)
            .setCancelable(false)
            .setPositiveButton(
                R.string.Enable
            ) { _, _ ->
                positiveButtonListener?.let { it() }
                positiveButtonListener = null
                negativeButtonListener = null
            }
            .setNegativeButton(
                R.string.Cancel
            ) { dialogInterface, _ ->
                negativeButtonListener?.let { it(dialogInterface) }
                positiveButtonListener = null
                negativeButtonListener = null
            }

        return alertDialogBuilder.create()
    }

    override fun onDestroy() {
        negativeButtonListener?.let { it(null) }
        positiveButtonListener = null
        negativeButtonListener = null
        super.onDestroy()
    }
}

fun setBackgroundPictureIndexImage(
    context: Context,
    indexElement: View,
    selected: Boolean
) {
    val border = GradientDrawable()
    border.mutate()

    border.setColor(
        ResourcesCompat.getColor(
            context.resources,
            if (selected) {
                R.color.colorGrey
            } else {
                R.color.colorLightGrey
            },
            null
        )
    )

    border.cornerRadius =
        context.resources.getDimension(R.dimen.image_button_border_radius)
    indexElement.background = border
}

fun setupPictureIndexImage(
    context: Context,
    picturesList: MutableList<PictureInfo>,
    layoutToAddTo: LinearLayout,
    picIndexHeight: Int,
    picIndexMargin: Int
) {

    for (i in picturesList.indices) {

        val indexElement = View(context, null)

        val layoutParams = LinearLayout.LayoutParams(
            picIndexHeight,
            picIndexHeight
        ).apply {
            marginStart = picIndexMargin
            marginEnd = picIndexMargin
        }

        indexElement.layoutParams = layoutParams

        setBackgroundPictureIndexImage(
            context,
            indexElement,
            i == 0
        )

        layoutToAddTo.addView(indexElement)
    }
}

data class MemberAndIndexDataClass(var member: OtherUsersInfo, var index: Int)

data class UpsertMemberReturnValue(val index: Int, val userAlreadyExisted: Boolean)

//used to enforce the convention of don't delete from this list
class MemberMutableListWrapper() {

    private var membersList = mutableListOf<OtherUsersInfo>()

    //stores the member object inside the map, the second item is the index inside the list
    //this should be acceptable because the list and the map are never deleted from
    private var membersMap = mutableMapOf<String, MemberAndIndexDataClass>()

    fun add(chatRoomMember: OtherUsersInfo) {
        val index = membersList.size

        membersList.add(chatRoomMember)
        membersMap[chatRoomMember.otherUsersDataEntity.accountOID] =
            MemberAndIndexDataClass(chatRoomMember, index)
    }

    //update the element in the list and map (if map is initialized) by the accountOID in the map
    //or insert if item does not already exist
    //will return index of inserted element in list
    fun upsertAnElementByAccountOID(
        accountOID: String,
        member: OtherUsersInfo
    ): UpsertMemberReturnValue {

        val extractedMember = membersMap[accountOID]

        return if (extractedMember == null) { //if element does not exist
            membersList.add(member)
            membersMap[accountOID] = MemberAndIndexDataClass(member, membersList.lastIndex)

            UpsertMemberReturnValue(
                membersList.lastIndex,
                false
            )
        } else { //if element does exist
            membersList[extractedMember.index] = member
            membersMap[accountOID]!!.member = member

            UpsertMemberReturnValue(
                extractedMember.index,
                true
            )
        }
    }

    //update all accountOIDs picturesUpdateAttemptedTimestamp to the passed timestamp
    fun updatePicturesUpdateAttemptedTimestampByAccountOIDs(
        accountOIDs: List<String>,
        timestamp: Long
    ) {
        for (accountOID in accountOIDs) {
            membersMap[accountOID]?.let {
                if (it.member.otherUsersDataEntity.picturesUpdateAttemptedTimestamp < timestamp) {
                    it.member.otherUsersDataEntity.picturesUpdateAttemptedTimestamp = timestamp
                }
            }
        }
    }

    //update the element's account state in the list and map; searched using the accountOID
    //will return false if update fails, true if succeeds
    fun updateAccountStateByAccountOID(
        accountOID: String,
        newAccountState: AccountState.AccountStateInChatRoom,
    ): Boolean {

        membersMap[accountOID]?.let {
            it.member.chatRoom.accountStateInChatRoom = newAccountState

            if (it.index < membersList.size) {
                membersList[it.index].chatRoom.accountStateInChatRoom =
                    newAccountState

                return true
            }

            return false
        }

        return false
    }

    //update the element's account state in the list and map; searched using the accountOID
    //will return false if update fails, true if succeeds
    fun updateAccountStateByAccountOID(member: OtherUsersInfo): Pair<Boolean, Int> {

        membersMap[member.otherUsersDataEntity.accountOID]?.let {
            it.member.chatRoom.accountStateInChatRoom = member.chatRoom.accountStateInChatRoom

            if (it.index < membersList.size) {
                membersList[it.index].chatRoom.accountStateInChatRoom =
                    member.chatRoom.accountStateInChatRoom

                return Pair(true, it.index)
            }

            return Pair(false, it.index)
        }

        return Pair(false, -1)
    }

    //returns the index of the accountOID
    //will return false if update fails, true if succeeds
    fun getIndexByAccountOID(accountOID: String): Pair<Boolean, Int> {

        membersMap[accountOID]?.let {
            return Pair(true, it.index)
        }

        return Pair(false, -1)
    }

    //returns the index of the accountOID
    //will return false if update fails, true if succeeds
    fun logMembers() {

        for (member in membersMap) {
            Log.i(
                "handleUpdatedChatRoom",
                "Map: index: ${member.value.index} accountOID: ${member.value.member.otherUsersDataEntity.accountOID}; accountStateInChatRoom: ${member.value.member.chatRoom.accountStateInChatRoom}"
            )
        }

        for (i in membersList.indices) {
            Log.i(
                "handleUpdatedChatRoom",
                "List: index: $i accountOID: ${membersList[i].otherUsersDataEntity.accountOID}; accountStateInChatRoom: ${membersList[i].chatRoom.accountStateInChatRoom}"
            )
        }

    }

    //get a value from the list
    //NOTE: it is possible to return null meaning the index was out of range
    fun getFromList(index: Int): OtherUsersInfo? {

        if (-1 < index && index < membersList.size) {
            return membersList[index]
        }

        return null
    }

    //get a value from the map
    //NOTE: it is possible to return null meaning the value was not found or the map was not initialized
    fun getFromMap(accountOID: String): OtherUsersInfo? {
        return membersMap[accountOID]?.member
    }

    //get a value from the list
    //NOTE: it is possible to return null meaning the index was out of range
    fun size(): Int {
        return membersList.size
    }

    //sets the list and map to be this value
    fun initializeList(
        valuesToInsert: List<OtherUsersDataEntity>,
        chatRoomId: String
    ) {
        membersList.clear()

        for (value in valuesToInsert) {

            val chatRoomInfo =
                convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(value, chatRoomId)

            chatRoomInfo?.let {
                membersList.add(chatRoomInfo)
            }
        }

        //Sort so that the event(s) are first in the list.
        membersList
            .sortWith { a, b ->
                when {
                    a.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number -> -1
                    b.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number -> 1
                    else -> 0
                }
            }

        membersMap.clear()
        for (i in membersList.indices) {
            membersMap[membersList[i].otherUsersDataEntity.accountOID] =
                MemberAndIndexDataClass(membersList[i], i)
        }
    }

    constructor(
        valuesToInsert: List<OtherUsersDataEntity>,
        chatRoomId: String,
    ) : this() {
        initializeList(valuesToInsert, chatRoomId)
    }
}

data class AgeRangeObject(val minAge: Int, val maxAge: Int)

fun getMinAndMaxMatchableAges(
    userAge: Int,
    errorStore: StoreErrorsInterface
): AgeRangeObject {

    //set age range values
    val minAgeMatchable: Int
    val maxAgeMatchable: Int

    //make the minAge and maxAge fit the user criteria (more of this error checking is at the top and below this if, else if block)
    when (userAge) {
        in GlobalValues.server_imported_values.lowestAllowedAge..15 -> { //if age is between 13->15 the age range must be between 13->17 (13 is check for above)
            minAgeMatchable = GlobalValues.server_imported_values.lowestAllowedAge
            maxAgeMatchable = 17
        }
        16, 17 -> { //if age is 16 or 17 then age range must be between 13->(userAge+2) (13 is check for above)
            minAgeMatchable = GlobalValues.server_imported_values.lowestAllowedAge
            maxAgeMatchable = userAge + 2
        }
        18, 19 -> {  //if age is 18 or 19 then age range must be between (userAge-2)-120
            minAgeMatchable = userAge - 2
            maxAgeMatchable = GlobalValues.server_imported_values.highestDisplayedAge
        }
        in 20..GlobalValues.server_imported_values.highestAllowedAge -> { //if age is 20 or above the age range must be between 18->120 (120 is checked for above)
            minAgeMatchable = 18
            maxAgeMatchable = GlobalValues.server_imported_values.highestDisplayedAge
        }
        else -> { //if user age is out of bounds

            val errorString = "user age is out of bounds age:'$userAge"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorString
            )

            //setting standard ages as default values, if the age is bugged it should be impossible for minors to match with anyone else
            // unless the other persons age is bugged as well
            minAgeMatchable = 18
            maxAgeMatchable = GlobalValues.server_imported_values.highestDisplayedAge
        }
    }

    return AgeRangeObject(minAgeMatchable, maxAgeMatchable)
}

fun runKickedOrBanned(
    kickOrBan: ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan,
    fragmentManager: FragmentManager,
    navigateToMessengerScreen: () -> Unit,
) {
    val titleAndBody =
        if (ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK == kickOrBan) {
            Pair(
                R.string.chat_room_dialogs_kicked_title,
                R.string.chat_room_dialogs_kicked_body
            )
        } else {
            Pair(
                R.string.chat_room_dialogs_banned_title,
                R.string.chat_room_dialogs_banned_body
            )
        }

    val alertDialog =
        KickBanAlertDialogFragment(
            titleAndBody.first,
            titleAndBody.second
        ) {
            navigateToMessengerScreen()
        }

    alertDialog.show(
        fragmentManager, "fragment_kick_ban"
    )
}

fun getFileWidthAndHeightFromBitmap(
    filePath: String,
    mimeTypeValue: String,
): MimeTypeHolderObject {

    val options = BitmapFactory.Options()
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(filePath, options)

    return MimeTypeHolderObject(
        filePath,
        options.outWidth,
        options.outHeight,
        mimeTypeValue
    )
}

fun generateNameForPictureOrMimeTypeMessageReply(
    replyType: TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase,
    activityContext: Context,
    errorStore: StoreErrorsInterface,
    extractMimeType: () -> String,
): String {

    return when (replyType) {
        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> { //message is picture type
            activityContext.resources.getString(R.string.chat_message_type_text_picture)
        }
        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> { //message is mime type
            when (extractMimeType()) {
                "image/png" -> {
                    activityContext.resources.getString(R.string.chat_message_type_text_png)
                }
                "image/gif" -> {
                    activityContext.resources.getString(R.string.chat_message_type_text_gif)
                }
                else -> {
                    activityContext.resources.getString(R.string.chat_message_type_text_generic_mime)
                }
            }
        }
        else -> { //message is neither picture or mime type

            val errorMessage =
                "Invalid Reply type received when generating name for picture or mime type message reply.\n" +
                        "replyType: $replyType\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                activityContext
            )

            activityContext.resources.getString(R.string.chat_message_adapter_reply_to_message_generic)
        }
    }
}

//calculates gif height and width and stores them inside the ChatMessageViewHolder object
fun setPictureOrMimeTypeHeightAndWidth(
    deviceScreenWidth: Int,
    holder: ChatMessageAdapter.ChatMessageViewHolder,
    fileWidth: Int,
    fileHeight: Int,
    maximumWidthOrHeight: Double
) {

    if (fileWidth == 0 || fileHeight == 0) {
        holder.imageWidth = deviceScreenWidth / 2
        holder.imageHeight = deviceScreenWidth / 2
        return
    }

    //NOTE: a lot of the gifs seem to come as a smaller size, so keep them inside a static square
    if (fileWidth > maximumWidthOrHeight || fileHeight > maximumWidthOrHeight) { //if image is too large and needs resized

        val ratio =
            if (fileHeight > fileWidth) {
                maximumWidthOrHeight / fileHeight.toDouble()
            } else {
                maximumWidthOrHeight / fileWidth.toDouble()
            }

        holder.imageWidth = (fileWidth.toDouble() * ratio).toInt()
        holder.imageHeight = (fileHeight.toDouble() * ratio).toInt()

    } else { //if image is too small or the same size
        if (fileHeight > fileWidth) {
            val ratio = maximumWidthOrHeight / fileHeight.toDouble()

            holder.imageWidth = (fileWidth.toDouble() * ratio).toInt()
            holder.imageHeight = maximumWidthOrHeight.toInt()
        } else {
            val ratio = maximumWidthOrHeight / fileWidth.toDouble()

            holder.imageWidth = maximumWidthOrHeight.toInt()
            holder.imageHeight = (fileHeight.toDouble() * ratio).toInt()
        }
    }
}

fun showBlockAndReportDialog(
    context: Context,
    childFragmentManager: FragmentManager,
    onDismissAction: () -> Unit,
    languageSelected: () -> Unit,
    inappropriatePictureSelected: () -> Unit,
    advertisingSelected: () -> Unit,
    otherSelected: (String) -> Unit,
) {

    val items = arrayOf(
        Item(
            context.resources.getString(R.string.block_and_report_language_text),
            R.drawable.icon_report
        ),
        Item(
            context.resources.getString(R.string.block_and_report_inappropriate_picture_text),
            R.drawable.icon_image_
        ),
        Item(
            context.resources.getString(R.string.block_and_report_advertising_text),
            R.drawable.icon_ad
        ),
        Item(
            context.resources.getString(R.string.block_and_report_other_text),
            R.drawable.icon_question_mark
        ),
    )

    ChoicesDialog(
        R.string.block_and_report_dialog_title,
        ChoicesArrayAdapter(context, items),
        onDismissAction = onDismissAction
    ) { dialogInterface: DialogInterface, i: Int ->
        when (i) {
            0 -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.profile_screen_feedback_thank_you_toast,
                    Toast.LENGTH_LONG
                ).show()
                languageSelected()
            }
            1 -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.profile_screen_feedback_thank_you_toast,
                    Toast.LENGTH_LONG
                ).show()
                inappropriatePictureSelected()
            }
            2 -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.profile_screen_feedback_thank_you_toast,
                    Toast.LENGTH_LONG
                ).show()
                advertisingSelected()
            }
            3 -> {
                BlockAndReportTextEditDialog(
                    R.string.block_and_report_text_edit_title,
                    R.string.block_and_report_text_edit_hint
                )
                {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.profile_screen_feedback_thank_you_toast,
                        Toast.LENGTH_LONG
                    ).show()
                    otherSelected(it)
                }.show(childFragmentManager, "other_feedback")
            }
            else -> {
            } //Cancel
        }

        dialogInterface.dismiss()

    }.show(childFragmentManager, "feedback_choices")

}

class ChatRoomListOptionsMenu(
    private var itemsActions: ((DialogInterface, Int) -> Unit)?
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {

            val builder = AlertDialog.Builder(it)
            builder.setTitle(getString(R.string.chat_room_list_fragment_chat_room_options_title))
            builder.setItems(
                arrayOf(
                    getString(R.string.menu_item_names_leave_chat_room),
                    getString(android.R.string.cancel)
                ),
                itemsActions
            )
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDestroy() {
        super.onDestroy()
        itemsActions = null
    }
}

class Item(val text: String, val icon: Int) {
    override fun toString(): String {
        return text
    }
}

class ChoicesArrayAdapter(
    context: Context,
    private val items: Array<Item>,
    resource: Int = android.R.layout.select_dialog_item,
    textViewResourceId: Int = android.R.id.text1,
) : ArrayAdapter<Item>(
    context,
    resource,
    textViewResourceId,
    items
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getView(position, convertView, parent)
        val primaryTextView = v.findViewById<View>(android.R.id.text1) as TextView

        val drawable = ResourcesCompat.getDrawable(
            context.resources,
            items[position].icon,
            context.theme
        )

        val iconSize = (context.resources.getDimension(R.dimen.choices_dialog_icon_size)).toInt()

        drawable?.setBounds(
            0,
            0,
            iconSize,
            iconSize
        )

        primaryTextView.setCompoundDrawablesRelative(
            drawable, null, null, null
        )

        primaryTextView.compoundDrawablePadding =
            (context.resources.getDimension(R.dimen.choices_dialog_margin_distance)).toInt()
        return v
    }
}

/*
fun setupFeedbackAdapter(context: Context, items: Array<Item>): ArrayAdapter<Item> {

    return object : ArrayAdapter<Item>(
        context,
        android.R.layout.select_dialog_item,
        android.R.id.text1,
        items
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            //Use super class to create the View
            val v = super.getView(position, convertView, parent)
            val primaryTextView = v.findViewById<View>(android.R.id.text1) as TextView

            val drawable = ResourcesCompat.getDrawable(
                context.resources,
                items[position].icon,
                context.theme
            )

            val iconSize = (context.resources.getDimension(R.dimen.choices_dialog_icon_size)).toInt()

            drawable?.setBounds(
                0,
                0,
                iconSize,
                iconSize
            )

            primaryTextView.setCompoundDrawablesRelative(
                drawable, null, null, null
            )

            primaryTextView.compoundDrawablePadding = (context.resources.getDimension(R.dimen.choices_dialog_margin_distance)).toInt()
            return v
        }
    }
}
*/

class ChoicesDialog(
    private val titleResId: Int,
    private var adapter: ArrayAdapter<Item>? = null,
    private var onDismissAction: (() -> Unit)? = null,
    private var itemsActions: ((DialogInterface, Int) -> Unit)?
) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {

            val builder = AlertDialog.Builder(it)
            builder.setTitle(titleResId)
            builder.setAdapter(adapter, itemsActions)
            builder.setNegativeButton(R.string.Cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissAction?.let { it() }
    }

    override fun onDestroy() {
        super.onDestroy()
        onDismissAction = null
        itemsActions = null
        adapter = null
    }
}

class BlockAndReportTextEditDialog(
    private val titleResId: Int,
    private val editTextHintResId: Int,
    private var sendFeedback: ((String) -> Unit)?,
) : DialogFragment() {

    private var _binding: DialogFeedbackEditTextBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding = DialogFeedbackEditTextBinding.inflate(requireActivity().layoutInflater)

            binding.dialogFeedbackActivitySuggestionOtherEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytesUserFeedback
                )
            )

            binding.dialogFeedbackActivitySuggestionOtherEditText.setHint(editTextHintResId)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(titleResId)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                sendFeedback?.let { it1 ->
                    it1(
                        binding.dialogFeedbackActivitySuggestionOtherEditText.text.toString()
                    )
                }
            }
            builder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun dismiss() {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside a dialog.
        // https://issuetracker.google.com/issues/37064488
        binding.dialogFeedbackActivitySuggestionOtherEditText.isCursorVisible = false
        super.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        sendFeedback = null
        _binding = null
    }
}

//The parameter is a unix timestamp in milliseconds
fun formatTimestampDateStringForChatRoomInfo(
    unixTimestamp: Long,
    errorStore: StoreErrorsInterface
): String {

    return if (unixTimestamp == -1L) { //if the timeStamp is equal to -1 then it should be a start time that represents now
        "NOW"
    } else {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = unixTimestamp

        val dayOfWeekName = convertDayOfWeekToString(calendar.get(Calendar.DAY_OF_WEEK), errorStore)
        val monthName = convertMonthToThreeCharString(calendar.get(Calendar.MONTH), errorStore)
        val timeString =
            convertTimeToString(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

        //only want 1 space here for this formatting
        val newTimeString =
            if (timeString.isNotEmpty() && timeString[0] == ' ')
                timeString.subSequence(1, timeString.length)
            else
                timeString

        "last seen $dayOfWeekName $monthName ${calendar.get(Calendar.DAY_OF_MONTH)} at $newTimeString"
    }

}

//The parameter is a unix timestamp in milliseconds
fun formatTimestampDateStringForMessageInfo(
    unixTimestamp: Long,
    errorStore: StoreErrorsInterface
): String {

    return if (unixTimestamp == -1L) { //if the timeStamp is equal to -1 then it should be a start time that represents now
        "NOW"
    } else {
        val currentTime = Calendar.getInstance()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = unixTimestamp

        val displayMonthAndDay = {
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

            if (dayOfMonth < 10) {
                "${
                    convertMonthToThreeCharString(
                        calendar.get(Calendar.MONTH),
                        errorStore
                    )
                } 0$dayOfMonth"
            } else {
                "${
                    convertMonthToThreeCharString(
                        calendar.get(Calendar.MONTH),
                        errorStore
                    )
                } $dayOfMonth"
            }
        }

        when {
            currentTime.get(Calendar.YEAR) != calendar.get(Calendar.YEAR) -> { //if years do not match up
                return Calendar.YEAR.toString()
            }
            currentTime.get(Calendar.MONTH) != calendar.get(Calendar.MONTH) -> { //if months do not match up


                return displayMonthAndDay()
            }
            currentTime.get(Calendar.DAY_OF_MONTH) != calendar.get(Calendar.DAY_OF_MONTH) -> { //if day of month does not match up

                return if (currentTime.get(Calendar.DAY_OF_MONTH) - calendar.get(Calendar.DAY_OF_MONTH) < 7) { //if this is less than a week ago
                    convertDayOfWeekToString(calendar.get(Calendar.DAY_OF_WEEK), errorStore)
                } else {
                    displayMonthAndDay()
                }

            }
            else -> { //display times for same day
                return convertTimeToString(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
            }
        }
    }
}

//the parameter is a unix timestamp in milliseconds
fun formatTimestampDaysHoursMinutesForSuspendedDuration(unixTimestamp: Long): String {

    val totalHours = TimeUnit.MILLISECONDS.toHours(unixTimestamp)
    val formattedString: String

    if (totalHours > TimeUnit.DAYS.toHours(1)) { //multiple days

        val days = TimeUnit.MILLISECONDS.toDays(unixTimestamp)
        val hours = totalHours % TimeUnit.DAYS.toHours(1)

        var dayStr = "Day"
        var hourStr = "Hour"

        if (days != 1L) {
            dayStr += "s"
        }

        if (hours != 1L) {
            hourStr += "s"
        }

        formattedString = String.format("%02d $dayStr %02d $hourStr Remaining", days, hours)

    } else { //less than multiple days
        val hours = TimeUnit.MILLISECONDS.toHours(unixTimestamp)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(unixTimestamp) % TimeUnit.HOURS.toMinutes(1)

        var hourStr = "Hour"
        var minuteStr = "Minute"

        if (hours != 1L) {
            hourStr += "s"
        }

        if (minutes != 1L) {
            minuteStr += "s"
        }

        formattedString = String.format("%02d $hourStr %02d $minuteStr Remaining", hours, minutes)
    }

    return formattedString

}

fun String.isValidMongoDBOID(): Boolean {
    if (this.length != 24) {
        return false
    }

    //make sure each value is hex char
    for (c in this) {
        val lower = c.lowercaseChar()
        if (!c.isDigit() && (lower < 'a' || 'f' < lower)) {
            return false
        }
    }

    return true
}

fun String.isValidUUIDKey(): Boolean {

    //this is the size of a UUID on android
    if (this.length == 36) {
        return true
    }

    return false
}

fun isValidLocation(longitude: Double, latitude: Double): Boolean {

    if (longitude < -180.0 || 180.0 < longitude ||
        latitude <= -90.0 || 90.0 <= latitude
    ) {
        return false
    }

    return true
}

private fun tintSingleColor(rgbColorValue: String, tintAmount: Int): String {

    var colorInt =
        try {
            rgbColorValue.toInt(16) + tintAmount
        } catch (_: NumberFormatException) {
            return rgbColorValue
        }

    //force the value to be between 0 and 255
    colorInt = Ints.max(colorInt, 0)
    colorInt = Ints.min(colorInt, 255)

    return "%02x".format(colorInt)
}

fun String.generateTintedColor(tintAmount: Int): String {
    if (this.length != 7) {
        return this
    }

    val colorR = substring(1..2)
    val colorG = substring(3..4)
    val colorB = substring(5..6)

    return "#${
        tintSingleColor(colorR, tintAmount)
    }${
        tintSingleColor(colorG, tintAmount)
    }${
        tintSingleColor(colorB, tintAmount)
    }"

}

fun String.isValidChatRoomId(): Boolean {

    if (
        this.length >= 7
        && this.length <= GlobalValues.server_imported_values.maximumNumberChatRoomIdChars
    ) {
        return true
    }

    return false
}

fun String.isValidGender(): Boolean {

    if (
        this != "~"
        && this.isNotEmpty()
        && this != GlobalValues.server_imported_values.eventGenderValue
        && this.length <= GlobalValues.server_imported_values.maximumNumberAllowedBytes
    ) {
        return true
    }

    return false
}

fun String.isValidPhoneNumber(): Boolean {
    if (
        this.isEmpty()
        || this.length != 12
        || this[0] != '+'
        || this[1] != '1'
        || this[2] == '0'
        || this[2] == '1'
        || (
                this[2] == '5'
                        && this[3] == '5'
                        && this[4] == '5'
                )
    ) {
        return false
    }

    for (i in 1 until this.length) {
        if (!this[i].isDigit()) {
            return false
        }
    }

    return true
}

//will return "" if invalid phone number, otherwise will return properly
// formatter phone number
fun String.validateAndFormatPhoneNumber(): String {
    //Want to check if the first digit is a '+' to make sure someone isn't just inputting an extra
    // digit by mistake.
    val firstDigitWasPlus = this.isNotEmpty() && this[0] == '+'
    val phoneNumberDigits = this.replace(Regex("[^0-9]"), "")

    //server expects the format +1 xxx xxx xxxx (without the spaces so size 12 total)
    //NOTE: in the US the area code CANNOT start with a 0 or a 1
    //NOTE: area code of 555 are fake numbers
    return when {
        phoneNumberDigits.length == 10 -> { //length 10
            if (
                phoneNumberDigits[0] == '1'
                || phoneNumberDigits[0] == '0'
                || (phoneNumberDigits[0] == '5'
                        && phoneNumberDigits[1] == '5'
                        && phoneNumberDigits[2] == '5'
                        )
            ) {
                return ""
            }
            "+1$phoneNumberDigits"
        }
        firstDigitWasPlus && phoneNumberDigits.length == 11 -> { //length 11 and first digit was a '+' sign
            //this is possible if the autofill puts in the +1 automatically
            if (phoneNumberDigits[0] != '1'
                || phoneNumberDigits[1] == '1'
                || phoneNumberDigits[1] == '0'
                || (phoneNumberDigits[1] == '5'
                        && phoneNumberDigits[2] == '5'
                        && phoneNumberDigits[3] == '5'
                        )
            ) {
                return ""
            }
            "+$phoneNumberDigits"
        }
        else -> {
            return ""
        }
    }
}

//this will attempt to check if the login token is still valid, it is not perfect for reasons
// described on LoginFunctions.loginTokenExpirationTime variable, therefore it is called with a delay
// sometimes where this will be called then LoginFunctions.currentLoginToken
fun loginTokenIsValid(): String {
    val currentLoginToken = LoginFunctions.currentLoginToken

    if (!currentLoginToken.isValidMongoDBOID()) {
        return INVALID_LOGIN_TOKEN
    }

    if (LoginFunctions.loginTokenExpirationTime == -1L) {
        return INVALID_LOGIN_TOKEN
    }

    if (SystemClock.elapsedRealtime() > LoginFunctions.loginTokenExpirationTime) {
        return INVALID_LOGIN_TOKEN
    }

    return currentLoginToken
}

//Will write the passed byte string to the passed file. Catches any IOExceptions and stores
// an error for it. Will remove the file if it is NOT an image. Returns picture path OR
// GlobalValues.PICTURE_NOT_FOUND_ON_SERVER if error storing file occurred.
private fun writePassedImageBytesToFile(
    file: File,
    bytes: ByteString,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
): String {

    try {
        file.writeBytes(bytes.toByteArray())

        if (!file.isImage()) {
            deleteFileInterface.sendFileToWorkManager(
                file.absolutePath
            )

            return GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
        }
    } catch (ex: IOException) {
        val errorMessage =
            "Exception occurred when writing file.\n" +
                    "ex.message: ${ex.message}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            ex.stackTraceToString(),
            errorMessage
        )

        deleteFileInterface.sendFileToWorkManager(
            file.absolutePath
        )

        return GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
    }

    return file.absolutePath
}

//NOTE: This function will not retry if it receives a broken thumbnail. It will simply
// store it as PICTURE_NOT_FOUND_ON_SERVER. This is because a broken reply thumbnail will
// not effect the program. It will simply not show the thumbnail.
fun generateAndSaveMessageReplyFile(
    thumbnailInBytes: ByteString,
    thumbnailFileSize: Int,
    messageUUID: String,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface,
    transactionWrapper: TransactionWrapper? = null,
): String {

    return if (!thumbnailInBytes.isEmpty
        && thumbnailInBytes.size() == thumbnailFileSize
    ) { //If reply is not empty AND file is not corrupt.

        val file = generateReplyThumbnailFile(
            messageUUID,
            GlobalValues.applicationContext
        )

        //NOTE: Not using another coRoutine because I only want the database values stored if the file
        // is successfully saved to 'disk'.
        //save the picture to file
        val absolutePath: String
        if (transactionWrapper == null) {
            absolutePath = writePassedImageBytesToFile(
                file,
                thumbnailInBytes,
                deleteFileInterface,
                errorStore
            )
        } else {
            //NOTE: Must return the path NOW, not after the transaction completes. So using optimistic
            // checking, assuming it is successful and checking afterwards.
            absolutePath = file.absolutePath
            transactionWrapper.runAfterTransaction {
                if (writePassedImageBytesToFile(
                        file,
                        thumbnailInBytes,
                        deleteFileInterface,
                        errorStore
                    ) == GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
                ) { //write failed in some way
                    //if write failed and no file was stored, overwrite file
                    (GlobalValues.applicationContext as LetsGoApplicationClass).applicationRepository.removeReplyPathFromThumbnail(
                        messageUUID,
                        GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
                    )
                }
            }
        }

        absolutePath

    } else { //if reply was empty or file was corrupt
        GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
    }
}

//gets login info from all over da place
//NOTE: login token is expected have been checked for valid (probably by loginTokenIsValid())
/** These values are also used inside of ChatStreamObject when creating the metadata request. **/
fun getLoginInfo(loginToken: String): LoginToServerBasicInfoOuterClass.LoginToServerBasicInfo {
    return LoginToServerBasicInfoOuterClass.LoginToServerBasicInfo.newBuilder()
        .setCurrentAccountId(LoginFunctions.currentAccountOID)
        .setLoggedInToken(loginToken)
        .setLetsGoVersion(GlobalValues.Lets_GO_Version_Number)
        .setInstallationId(GlobalValues.installationId)
        .build()
}

fun displayEventAdminName(
    eventAdminName: String
): String {

    val newText: StringBuilder = StringBuilder(eventAdminName.length * 2)
    var i = 0
    for (c in eventAdminName) {
        if (i > 0 && c.isUpperCase()) {
            newText.append(' ')
        }
        newText.append(c)
        i++
    }
    return newText.toString()
}

fun extractAndSetupName(
    context: Context,
    sentByAccountOID: String,
    userFirstName: String,
    nameTextView: TextView,
    chatRoomMembers: MemberMutableListWrapper,
    errorStore: StoreErrorsInterface
) {
    nameTextView.visibility = View.VISIBLE

    val nameText =
        //set up name on reply
        if (LoginFunctions.currentAccountOID == sentByAccountOID) { //message was sent by this account
            userFirstName
        } else { //message was not sent by this account

            val replyFromMember =
                chatRoomMembers.getFromMap(sentByAccountOID)

            if (replyFromMember != null) { //if member was found inside chat room list
                val replyFromMemberName =
                    replyFromMember.otherUsersDataEntity.name

                if (replyFromMemberName != "" && replyFromMemberName != "~") { //name was set for other user
                    replyFromMemberName
                } else { //name was not set for other user
                    val errorMessage =
                        "A member did not have a name inside their respective otherUsersDataEntity.\n" +
                                "replyFromMemberName: $replyFromMemberName\n" +
                                "sentByAccountOID: $sentByAccountOID\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                    context.applicationContext.resources.getString(R.string.chat_message_adapter_default_user_name)
                }
            } else { //if member was not found inside chat room list
                val errorMessage =
                    "A member did not have a name inside their respective otherUsersDataEntity.\n" +
                            "sentByAccountOID: $sentByAccountOID\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                context.applicationContext.resources.getString(R.string.chat_message_adapter_default_user_name)
            }
        }

    nameTextView.text = nameText
}

fun displayBlockedMessage(
    sentByAccountID: String,
    messageType: Int,
): Boolean {
    val accountIsBlocked =
        GlobalValues.blockedAccounts[sentByAccountID]

    return if (accountIsBlocked) {
        checkIfBlockedMessageShouldBeDisplayed(messageType)
    } else {
        true
    }
}

fun findLayoutType(
    sentByAccountID: String,
    deletedType: Int,
    messageType: Int,
): LayoutType {

    val layoutType = if (!displayBlockedMessage(sentByAccountID, messageType)
        || deletedType == TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE
    ) { //if account is blocked or message is deleted
        LayoutType.LAYOUT_EMPTY
    } else {
        chatRoomLayoutByMessageType(messageType)
    }

    return layoutType
}

fun cancelChatStreamWorker() {
    Log.i("cancel_worker", "start cancel worker")
    ChatStreamWorker.chatStreamWorkerMutex.withLock {

        //keep the mutex locked until the operation is finished
        //NOTE: This is called from a coRoutine and so it will block a thread, however await() cannot be used
        // instead of get() inside of chatStreamWorkerMutex because it is a ReentrantLock lock NOT a coRoutine Reentrant lock.
        /** See [ChatStreamWorker.chatStreamWorkerMutex] for details on why **/
        WorkManager
            .getInstance(GlobalValues.applicationContext)
            .cancelAllWorkByTag(ChatStreamWorker.CHAT_STREAM_WORKER_TAG)
            .result
            .get()

        ChatStreamWorker.continueChatStreamWorker.set(false)
    }
    Log.i("cancel_worker", "finish cancel worker")
}

//NOTE: also update these inside the below link
/** [messagesDaoAllowedBlockedMessageTypesString] **/
fun checkIfBlockedMessageShouldBeDisplayed(messageType: Int): Boolean {
    return when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(messageType)) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        null,
        -> {
            false
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> {
            true
        }
    }
}

fun checkIfChatRoomLastActiveTimeRequiresUpdating(messageTypeInt: Int): Boolean {
    return checkIfChatRoomLastActiveTimeRequiresUpdating(
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(messageTypeInt)
    )
}

//NOTE: also update these inside the below link
/** [messagesDaoSelectChatRoomLastActiveTimeString] **/
fun checkIfChatRoomLastActiveTimeRequiresUpdating(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): Boolean {
    when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> { //these message types update the chat room updated time
            return true
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        -> {
            return false
        }
    }
}

fun checkIfUserLastActiveTimeRequiresUpdating(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): Boolean {
    when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> { //these message types update the chat room updated time
            return true
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        -> {
            return false
        }
    }
}

fun checkIfMessageTypeFitsFinalChatRoomMessage(messageType: Int): Boolean {
    return checkIfMessageTypeFitsFinalChatRoomMessage(
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
            messageType
        )
    )
}

//NOTE: also update these inside below links if changing them
/**
 * [ChatRoomListChatRoomsAdapter.onBindViewHolder]
 * [messagesDaoSelectFinalMessageString]
 * **/
fun checkIfMessageTypeFitsFinalChatRoomMessage(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): Boolean {

    when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> {  //these message types will be displayed by the chat room inside message screen fragment
            return true
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        -> {
            return false
        }
    }
}

fun chatRoomLayoutByMessageType(messageTypeInt: Int): LayoutType {
    return chatRoomLayoutByMessageType(
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(messageTypeInt)
    )
}

fun chatRoomLayoutByMessageType(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): LayoutType {

    return when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        -> {
            LayoutType.LAYOUT_MESSAGE
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> {
            LayoutType.LAYOUT_SINGLE
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        -> {
            LayoutType.LAYOUT_EMPTY
        }

    }
}

//this function will return true if the message type can reply to other messages (the types listed inside checkIfChatRoomMessageCanBeRepliedTo) and false otherwise
fun chatRoomMessageIsAbleToReply(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase?): Boolean {

    return when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        -> {  //these message types are able to reply
            true
        }
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
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        null,
        -> {
            false
        }
    }
}

fun checkIfFirstContactMessage(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): Boolean {
    return when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        -> { //these message types do NOT update the chat room out of its match made state
            false
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> {
            true
        }
    }
}

fun convertMessageTypeToReplyBodyCase(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase): TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase {
    return when (messageType) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY
        }
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
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
        -> {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET
        }
    }
}

suspend fun addAccountToBlockedList(
    accountOID: String,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    transactionWrapper: TransactionWrapper = ServiceLocator.provideTransactionWrapper(
        GlobalValues.applicationContext,
        DatabasesToRunTransactionIn.ACCOUNTS,
    )
) {
    transactionWrapper.runTransaction {
        GlobalValues.blockedAccounts.add(accountOID)

        accountInfoDataSource.addBlockedAccount(accountOID, this)
    }
}

fun generateMessageForMimeType(
    context: Context,
    mimeType: String?,
): String {
    return when (mimeType ?: "") { //check mime type
        "image/png" -> { //if this is a png mime type
            context.resources.getString(R.string.chat_message_type_text_png)
        }
        "image/gif" -> {
            context.resources.getString(R.string.chat_message_type_text_gif)
        }
        else -> {
            context.resources.getString(R.string.chat_message_type_text_generic_mime)
        }
    }
}