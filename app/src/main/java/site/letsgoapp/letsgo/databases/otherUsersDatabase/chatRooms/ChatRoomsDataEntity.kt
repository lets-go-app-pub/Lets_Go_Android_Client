package site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms

import account_state.AccountState
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import chatroominfo.ChatRoomInfoMessageOuterClass
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MostRecentMessageDataHolder
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.MemberMutableListWrapper
import site.letsgoapp.letsgo.utilities.checkIfChatRoomLastActiveTimeRequiresUpdating
import site.letsgoapp.letsgo.utilities.checkIfMessageTypeFitsFinalChatRoomMessage
import site.letsgoapp.letsgo.utilities.displayBlockedMessage

@Entity(tableName = "chat_rooms_table")
class ChatRoomsDataEntity(

    //chat room OID from server
    @PrimaryKey(autoGenerate = false)
    val chatRoomID: String = "~",

    @ColumnInfo(name = "chat_room_name")
    val chatRoomName: String = "",

    @ColumnInfo(name = "chat_room_password")
    val chatRoomPassword: String = "",

    @ColumnInfo(name = "notifications_enable")
    val notificationsEnabled: Boolean = false,

    //the current users state inside the chat room
    @ColumnInfo(name = "user_state_in_chat_room")
    val userStateInChatRoom: Int = AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM_VALUE,

    //timestamp of when user joined this chat room
    @ColumnInfo(name = "time_joined")
    val timeJoined: Long = -1L,

    //if this user and another user swipe 'yes' on each other and the match is formed,
    // this will be the OID of the matching account, when a message is sent then this will be changed to
    // "" and this chat room will be set as a 'normal' chat room
    @ColumnInfo(name = "matching_chat_room_oid")
    val matchingChatRoomOID: String = "",

    //last time this user observed this chat room
    //this is compared with chatRoomLastActivityTime to measure when the chat room will show the user that it has new information
    //updated when user receives a message and is viewing the chat room
    //NOTE: when this is sent to the server to be updated, the largest time (as long as it is not in the future) will be taken the time from the server or the time from the client
    // in other words for this value the server is not considered the source of 'truth'
    @ColumnInfo(name = "last_observed_time")
    val chatRoomLastObservedTime: Long = -1L,

    //last time this user interacted with this chat room
    //this is used to show the time that other users were last seen
    //see documents for when updated
    @ColumnInfo(name = "user_last_active_time")
    val userLastActivityTime: Long = -1L,

    //last time this chat room experienced activity that the user is interested in
    //this is compared with chatRoomLastObservedTime to measure when the chat room will show the user that it has new information
    //this represents the last time any message was received from the chat room, with the following exception message types
    //MESSAGE_EDITED, MESSAGE_DELETED, DIFFERENT_USER_THUMBNAIL_UPDATED, DIFFERENT_USER_OPENED_CHAT_ROOM
    @ColumnInfo(name = "chat_room_last_active_time")
    val chatRoomLastActivityTime: Long = -1L,

    //last time this chat room received a message from the server;
    //this is sent back to the server to show at which point this chat room has been updated
    //this is updated on every message
    @ColumnInfo(name = "last_time_updated")
    val lastTimeUpdated: Long = -1L,

    @ColumnInfo(name = "event_id")
    val eventId: String = GlobalValues.server_imported_values.eventIdDefault,

    @ColumnInfo(name = "qr_code_path")
    val qrCodePath: String = GlobalValues.server_imported_values.qrCodeDefault,

    @ColumnInfo(name = "qr_code_message")
    val qrCodeMessage: String = GlobalValues.server_imported_values.qrCodeMessageDefault,

    @ColumnInfo(name = "qr_code_time_updated")
    val qrCodeTimeUpdated: Long = GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,

    @ColumnInfo(name = "pinned_location_longitude")
    val pinnedLocationLongitude: Double = GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,

    @ColumnInfo(name = "pinned_location_latitude")
    val pinnedLocationLatitude: Double = GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
)

//NOTE: this is more safeguarding that values are properly updated between its children
open class ChatRoomDataClassParent(
    val chatRoomId: String,
    var chatRoomName: String,
    var chatRoomPassword: String,
    var notificationsEnabled: Boolean,
    var accountState: AccountState.AccountStateInChatRoom,
    val timeJoined: Long,
    var matchingChatRoomOID: String,
    var chatRoomLastObservedTime: Long,
    val userLastActivityTime: Long,
    var chatRoomLastActivityTime: Long,
    val lastTimeUpdated: Long,
    var eventId: String,
    var qrCodePath: String,
    var qrCodeMessage: String,
    var qrCodeTimeUpdated: Long,
    var pinnedLocationLongitude: Double,
    var pinnedLocationLatitude: Double,
    _finalMessage: MostRecentMessageDataHolder
) {
    //NOTE: The standard setter will be called initially when finalMessage = _finalMessage. After that
    // the custom setter will be called.
    var finalMessage = _finalMessage
        private set

    data class UpdateFinalMessageReturn(
        val updateOccurred: Boolean,
        val lastActivityTimeWasUpdated: Boolean
    )

    fun handleMessageUpdate(message: MessagesDataEntity): Boolean {
        //if the message is already the final message, update it
        return if (message.messageUUIDPrimaryKey == finalMessage.messageUUIDPrimaryKey
            && message.amountOfMessage >= finalMessage.amount_of_message
        ) {
            finalMessage = MostRecentMessageDataHolder(message)
            true
        } else {
            false
        }
    }

    fun setNewFinalMessageIfRelevant(message: MessagesDataEntity): UpdateFinalMessageReturn {
        var updateOccurred = false
        var lastActivityTimeWasUpdated = false

        //If the message was blocked do not want the last active time OR the
        if (displayBlockedMessage(message.sentByAccountID, message.messageType)) {

            if (
                checkIfChatRoomLastActiveTimeRequiresUpdating(message.messageType)
                && message.messageStoredOnServerTime >= chatRoomLastActivityTime //use >= instead of = here so booleans can be updated
            ) {
                //If this message was sent by the current user, update the observed time to match so the red dot doesn't
                // show up.
                if (message.sentByAccountID == LoginFunctions.currentAccountOID)
                    chatRoomLastObservedTime = message.messageStoredOnServerTime

                chatRoomLastActivityTime = message.messageStoredOnServerTime
                updateOccurred = true
                lastActivityTimeWasUpdated = true
            }

            //This will ignore message deleted and message edited, otherwise a request for the 'actual'
            // message would need to be requested from the database.
            if (checkIfMessageTypeFitsFinalChatRoomMessage(message.messageType)) { //If message was not blocked AND message is most recent.
                finalMessage = MostRecentMessageDataHolder(message)
                updateOccurred = true
            }
        }

        return UpdateFinalMessageReturn(updateOccurred, lastActivityTimeWasUpdated)
    }

    fun hardSetNewFinalMessage(message: MostRecentMessageDataHolder?) {
        finalMessage = message ?: MostRecentMessageDataHolder()
    }

    //NOTE: chatRoomId could be "" if an error happened here, however if it is not 8 digits there is a problem,
    // so using that to determine if this has been initialized
    constructor() : this(
        "~",
        "~",
        "~",
        false,
        AccountState.AccountStateInChatRoom.UNRECOGNIZED,
        -1L,
        "",
        -1L,
        -1L,
        -1L,
        -1L,
        GlobalValues.server_imported_values.eventIdDefault,
        GlobalValues.server_imported_values.qrCodeDefault,
        GlobalValues.server_imported_values.qrCodeMessageDefault,
        GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
        GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
        GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
        MostRecentMessageDataHolder()
    )

}

class ChatRoomDataClass(
    chatRoomID: String,
    chatRoomName: String,
    chatRoomPassword: String,
    notificationsEnabled: Boolean,
    accountState: AccountState.AccountStateInChatRoom,
    val chatRoomMembers: MutableList<OtherUsersDataEntity>,
    timeJoined: Long,
    matchingChatRoomOID: String,
    chatRoomLastObservedTime: Long,
    userLastActivityTime: Long,
    chatRoomLastActivityTime: Long,
    lastTimeUpdated: Long,
    eventId: String,
    qrCodePath: String,
    qrCodeMessage: String,
    qrCodeTimeUpdated: Long,
    pinnedLocationLongitude: Double,
    pinnedLocationLatitude: Double,
    finalMessage: MostRecentMessageDataHolder = MostRecentMessageDataHolder()
) : ChatRoomDataClassParent(
    chatRoomID,
    chatRoomName,
    chatRoomPassword,
    notificationsEnabled,
    accountState,
    timeJoined,
    matchingChatRoomOID,
    chatRoomLastObservedTime,
    userLastActivityTime,
    chatRoomLastActivityTime,
    lastTimeUpdated,
    eventId,
    qrCodePath,
    qrCodeMessage,
    qrCodeTimeUpdated,
    pinnedLocationLongitude,
    pinnedLocationLatitude,
    finalMessage,
) {

    //NOTE: chatRoomId could be "" if an error happened here, however if it is not 8 digits there is a problem,
    // so using that to determine if this has been initialized
    constructor() : this(
        "~",
        "~",
        "~",
        false,
        AccountState.AccountStateInChatRoom.UNRECOGNIZED,
        mutableListOf(),
        -1L,
        "",
        -1L,
        -1L,
        -1L,
        -1L,
        GlobalValues.server_imported_values.eventIdDefault,
        GlobalValues.server_imported_values.qrCodeDefault,
        GlobalValues.server_imported_values.qrCodeMessageDefault,
        GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
        GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
        GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
    )

    //NOTE: this constructor is used in a very specific place, only with MATCH_CANCELED passed back and
    // the chatRoomName is switched to the passed accountOID so the fragment can dynamically remove it
    constructor(_chatRoomId: String, _accountOID: String) : this(
        _chatRoomId,
        _accountOID,
        "~",
        false,
        AccountState.AccountStateInChatRoom.UNRECOGNIZED,
        mutableListOf(),
        -1L,
        "",
        -1L,
        -1L,
        -1L,
        -1L,
        GlobalValues.server_imported_values.eventIdDefault,
        GlobalValues.server_imported_values.qrCodeDefault,
        GlobalValues.server_imported_values.qrCodeMessageDefault,
        GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
        GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
        GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
    )

    constructor(chatRoomWithMemberMapDataClass: ChatRoomWithMemberMapDataClass) : this(
        chatRoomWithMemberMapDataClass.chatRoomId,
        chatRoomWithMemberMapDataClass.chatRoomName,
        chatRoomWithMemberMapDataClass.chatRoomPassword,
        chatRoomWithMemberMapDataClass.notificationsEnabled,
        chatRoomWithMemberMapDataClass.accountState,
        mutableListOf<OtherUsersDataEntity>(),
        chatRoomWithMemberMapDataClass.timeJoined,
        chatRoomWithMemberMapDataClass.matchingChatRoomOID,
        chatRoomWithMemberMapDataClass.chatRoomLastObservedTime,
        chatRoomWithMemberMapDataClass.userLastActivityTime,
        chatRoomWithMemberMapDataClass.chatRoomLastActivityTime,
        chatRoomWithMemberMapDataClass.lastTimeUpdated,
        chatRoomWithMemberMapDataClass.eventId,
        chatRoomWithMemberMapDataClass.qrCodePath,
        chatRoomWithMemberMapDataClass.qrCodeMessage,
        chatRoomWithMemberMapDataClass.qrCodeTimeUpdated,
        chatRoomWithMemberMapDataClass.pinnedLocationLongitude,
        chatRoomWithMemberMapDataClass.pinnedLocationLatitude,
        chatRoomWithMemberMapDataClass.finalMessage
    ) {
        for (i in 0 until chatRoomWithMemberMapDataClass.chatRoomMembers.size()) {
            chatRoomWithMemberMapDataClass.chatRoomMembers.getFromList(i)?.let {
                chatRoomMembers.add(it.otherUsersDataEntity)
            }
        }
    }
}

class ChatRoomWithMemberMapDataClass(
    chatRoomID: String,
    chatRoomName: String,
    chatRoomPassword: String,
    notificationsEnabled: Boolean,
    accountState: AccountState.AccountStateInChatRoom,
    val chatRoomMembers: MemberMutableListWrapper,
    timeJoined: Long,
    matchingChatRoomOID: String,
    chatRoomLastObservedTime: Long,
    userLastActivityTime: Long,
    chatRoomLastActivityTime: Long,
    lastTimeUpdated: Long,
    eventId: String,
    qrCodePath: String,
    qrCodeMessage: String,
    qrCodeTimeUpdated: Long,
    pinnedLocationLongitude: Double,
    pinnedLocationLatitude: Double,
    finalMessage: MostRecentMessageDataHolder = MostRecentMessageDataHolder(),
    var finalPictureMessage: MessagesDataEntity? = null,
    var displayChatRoom: Boolean = true,
    var showLoading: Boolean = false,
) : ChatRoomDataClassParent(
    chatRoomID,
    chatRoomName,
    chatRoomPassword,
    notificationsEnabled,
    accountState,
    timeJoined,
    matchingChatRoomOID,
    chatRoomLastObservedTime,
    userLastActivityTime,
    chatRoomLastActivityTime,
    lastTimeUpdated,
    eventId,
    qrCodePath,
    qrCodeMessage,
    qrCodeTimeUpdated,
    pinnedLocationLongitude,
    pinnedLocationLatitude,
    finalMessage
) {

    //NOTE: chatRoomId could be "" if an error happened here, however if it is not 8 digits there is a problem,
    // so using that to determine if this has been initialized
    //NOTE: chatRoomId could be "" if an error happened here, however if it is not 8 digits there is a problem,
    // so using that to determine if this has been initialized
    constructor() : this(
        "~",
        "~",
        "~",
        false,
        AccountState.AccountStateInChatRoom.UNRECOGNIZED,
        MemberMutableListWrapper(),
        -1L,
        "",
        -1L,
        -1L,
        -1L,
        -1L,
        GlobalValues.server_imported_values.eventIdDefault,
        GlobalValues.server_imported_values.qrCodeDefault,
        GlobalValues.server_imported_values.qrCodeMessageDefault,
        GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
        GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
        GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
    )

    constructor(chatRoomDataClass: ChatRoomDataClass) : this(
        chatRoomDataClass.chatRoomId,
        chatRoomDataClass.chatRoomName,
        chatRoomDataClass.chatRoomPassword,
        chatRoomDataClass.notificationsEnabled,
        chatRoomDataClass.accountState,
        MemberMutableListWrapper(chatRoomDataClass.chatRoomMembers, chatRoomDataClass.chatRoomId),
        chatRoomDataClass.timeJoined,
        chatRoomDataClass.matchingChatRoomOID,
        chatRoomDataClass.chatRoomLastObservedTime,
        chatRoomDataClass.userLastActivityTime,
        chatRoomDataClass.chatRoomLastActivityTime,
        chatRoomDataClass.lastTimeUpdated,
        chatRoomDataClass.eventId,
        chatRoomDataClass.qrCodePath,
        chatRoomDataClass.qrCodeMessage,
        chatRoomDataClass.qrCodeTimeUpdated,
        chatRoomDataClass.pinnedLocationLongitude,
        chatRoomDataClass.pinnedLocationLatitude,
        chatRoomDataClass.finalMessage
    )

    //creates a copy of the ChatRoomWithMemberMapDataClass ONLY containing a single member
    constructor(
        chatRoomWithMemberMapDataClass: ChatRoomWithMemberMapDataClass,
        singleOtherUser: OtherUsersDataEntity
    ) : this(
        chatRoomWithMemberMapDataClass.chatRoomId,
        chatRoomWithMemberMapDataClass.chatRoomName,
        chatRoomWithMemberMapDataClass.chatRoomPassword,
        chatRoomWithMemberMapDataClass.notificationsEnabled,
        chatRoomWithMemberMapDataClass.accountState,
        MemberMutableListWrapper(
            mutableListOf(singleOtherUser),
            chatRoomWithMemberMapDataClass.chatRoomId
        ),
        chatRoomWithMemberMapDataClass.timeJoined,
        chatRoomWithMemberMapDataClass.matchingChatRoomOID,
        chatRoomWithMemberMapDataClass.chatRoomLastObservedTime,
        chatRoomWithMemberMapDataClass.userLastActivityTime,
        chatRoomWithMemberMapDataClass.chatRoomLastActivityTime,
        chatRoomWithMemberMapDataClass.lastTimeUpdated,
        chatRoomWithMemberMapDataClass.eventId,
        chatRoomWithMemberMapDataClass.qrCodePath,
        chatRoomWithMemberMapDataClass.qrCodeMessage,
        chatRoomWithMemberMapDataClass.qrCodeTimeUpdated,
        chatRoomWithMemberMapDataClass.pinnedLocationLongitude,
        chatRoomWithMemberMapDataClass.pinnedLocationLatitude,
        chatRoomWithMemberMapDataClass.finalMessage,
        chatRoomWithMemberMapDataClass.finalPictureMessage,
        chatRoomWithMemberMapDataClass.displayChatRoom,
        chatRoomWithMemberMapDataClass.showLoading
    )
}

fun convertChatRoomDataClassToChatRoomsDataEntity(chatRoomDataClass: ChatRoomDataClass): ChatRoomsDataEntity {
    return ChatRoomsDataEntity(
        chatRoomDataClass.chatRoomId,
        chatRoomDataClass.chatRoomName,
        chatRoomDataClass.chatRoomPassword,
        chatRoomDataClass.notificationsEnabled,
        chatRoomDataClass.accountState.number,
        chatRoomDataClass.timeJoined,
        chatRoomDataClass.matchingChatRoomOID,
        chatRoomDataClass.chatRoomLastObservedTime,
        chatRoomDataClass.userLastActivityTime,
        chatRoomDataClass.chatRoomLastActivityTime,
        chatRoomDataClass.lastTimeUpdated,
        chatRoomDataClass.eventId,
        chatRoomDataClass.qrCodePath,
        chatRoomDataClass.qrCodeMessage,
        chatRoomDataClass.qrCodeTimeUpdated,
        chatRoomDataClass.pinnedLocationLongitude,
        chatRoomDataClass.pinnedLocationLatitude,
    )
}

fun convertChatRoomsDataEntityToChatRoomWithMemberMapDataClass(chatRoomsDataEntity: ChatRoomsDataEntity): ChatRoomWithMemberMapDataClass {

    return ChatRoomWithMemberMapDataClass(
        chatRoomsDataEntity.chatRoomID,
        chatRoomsDataEntity.chatRoomName,
        chatRoomsDataEntity.chatRoomPassword,
        chatRoomsDataEntity.notificationsEnabled,
        AccountState.AccountStateInChatRoom.forNumber(chatRoomsDataEntity.userStateInChatRoom),
        MemberMutableListWrapper(),
        chatRoomsDataEntity.timeJoined,
        chatRoomsDataEntity.matchingChatRoomOID,
        chatRoomsDataEntity.chatRoomLastObservedTime,
        chatRoomsDataEntity.userLastActivityTime,
        chatRoomsDataEntity.chatRoomLastActivityTime,
        chatRoomsDataEntity.lastTimeUpdated,
        chatRoomsDataEntity.eventId,
        chatRoomsDataEntity.qrCodePath,
        chatRoomsDataEntity.qrCodeMessage,
        chatRoomsDataEntity.qrCodeTimeUpdated,
        chatRoomsDataEntity.pinnedLocationLongitude,
        chatRoomsDataEntity.pinnedLocationLatitude,
    )
}

fun convertTypeOfChatMessageToNewChatRoom(
    chatRoomID: String,
    chatRoomInfo: ChatRoomInfoMessageOuterClass.ChatRoomInfoMessage,
    qRCodeImageFilePath: String,
    timeLastObserved: Long,
    timeLastUpdated: Long
): ChatRoomDataClass {

    chatRoomInfo.eventOid

    return ChatRoomDataClass(
        chatRoomID,
        chatRoomInfo.chatRoomName,
        chatRoomInfo.chatRoomPassword,
        notificationsEnabled = true,
        chatRoomInfo.accountState,
        mutableListOf(),
        chatRoomInfo.timeJoined,
        chatRoomInfo.matchMadeChatRoomOid,
        timeLastObserved,
        chatRoomInfo.userLastActivityTime,
        chatRoomInfo.chatRoomLastActivityTime,
        timeLastUpdated,
        chatRoomInfo.eventOid,
        qRCodeImageFilePath,
        chatRoomInfo.qrCodeMessage,
        chatRoomInfo.qrCodeTimestamp,
        chatRoomInfo.longitudePinnedLocation,
        chatRoomInfo.latitudePinnedLocation,
    )
}

//used for the return values to getModifyProfileScreenInfo() in AccountInfoDatabaseDao
data class ChatRoomIdAndTimeLastUpdated(
    val chatRoomID: String = "",
    val last_time_updated: Long = -1L,
    val last_observed_time: Long = -1L
)

//used for the return values to getModifyProfileScreenInfo() in AccountInfoDatabaseDao
data class ChatRoomIdAndTimeJoined(
    val chatRoomID: String = "",
    val time_joined: Long = -1L
)