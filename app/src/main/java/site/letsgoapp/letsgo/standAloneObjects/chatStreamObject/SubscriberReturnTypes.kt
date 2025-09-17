package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

import account_state.AccountState
import grpc_chat_commands.ChatRoomCommands
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypesUrlsAndFilePaths
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersInfo
import site.letsgoapp.letsgo.utilities.ChatRoomListCalledFrom
import site.letsgoapp.letsgo.utilities.ChatRoomUpdateMade
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import site.letsgoapp.letsgo.utilities.TypeOfUpdatedOtherUser
import type_of_chat_message.TypeOfChatMessageOuterClass

data class ClientMessageToServerReturnValueDataHolder(
    val message: MessagesDataEntity,
    val errorStatusEnum: GrpcFunctionErrorStatusEnum,
    val fragmentInstanceID: String
)

data class ReturnAllChatRoomsDataHolder(
    val chatRoomsList: MutableList<ChatRoomWithMemberMapDataClass>,
    val typeOfFragmentCalledFrom: ChatRoomListCalledFrom
)

data class CreatedChatRoomReturnValueDataHolder(
    val errorStatusEnum: GrpcFunctionErrorStatusEnum,
    val chatRoomWithMemberMapDataClass: ChatRoomWithMemberMapDataClass,
    val fragmentInstanceID: String
)

data class ReturnSingleChatRoomNotFoundDataHolder(
    val chatRoomId: String,
    val fragmentInstanceID: String
)

data class ReturnMessagesForChatRoomDataHolder(
    val chatRoomInitialization: Boolean, //this will only be set to true if the chat room is requesting all of its older messages
    val chatRoomId: String,
    val messages: List<MessagesDataEntity>,
    val mimeTypes: MutableList<MimeTypesUrlsAndFilePaths>
)

data class MessageModifiedInfo(
    val indexNumber: Int,
    val messageUUIDPrimaryKey: String,
    val editedMessage: Boolean = false
)

data class ReturnUpdatedMessagesToFragment(
    val messagesModified: List<MessageModifiedInfo>,
    val accountOIDsRemoved: List<String>,
    val firstIndexAdded: Int,
    val numMessagesAdded: Int,
    val chatRoomInitialization: Boolean,
    val chatRoomId: String,
) {
    constructor() : this(
        emptyList(),
        emptyList(),
        -1,
        0,
        false,
        ""
    )

    constructor(
        _messagesModified: List<MessageModifiedInfo>,
        _chatRoomInitialization: Boolean,
        chatRoomId: String
    ) : this(
        _messagesModified,
        emptyList(),
        -1,
        0,
        _chatRoomInitialization,
        chatRoomId
    )

}

data class ReturnUpdatedOtherUserDataHolder(
    val otherUser: OtherUsersInfo,
    val anExistingThumbnailWasUpdated: Boolean,
    val index: Int
)

data class ReturnUpdatedChatRoomMemberDataHolder(
    val chatRoomId: String,
    val index: Int,
    val typeOfUpdatedOtherUser: TypeOfUpdatedOtherUser
)

data class ReturnUpdatedOtherUserRepositoryDataHolder(
    val otherUser: OtherUsersDataEntity,
    val anExistingThumbnailWasUpdated: Boolean,
)

data class ChatRoomSearchResultsDataHolder(
    val matchingChatRooms: Set<String>,
    val fragmentInstanceID: String
)

data class ReturnClearHistoryFromChatRoomDataHolder(
    val historyClearedMessage: MessagesDataEntity,
    val fragmentInstanceID: String
)

data class UpdatePicturesUpdateAttemptedTimestampByAccountOIDsDataHolder(
    val accountOIDs: List<String>,
    val timestamp: Long,
    val chatRoomId: String
)

data class ReturnMessageWithMemberForChatRoomDataHolder(
    val message: MessagesDataEntity,
    val otherUserInfo: OtherUsersInfo
)

data class AccountStateUpdatedDataHolder(
    val chatRoomId: String,
    val updatedAccountOID: String,
    val updatedAccountState: AccountState.AccountStateInChatRoom,
)
data class ReturnMatchRemovedOnJoinChatRomDataHolder(
    val matchAccountOid: String,
)

data class ReturnSingleChatRoomDataHolder(
    val chatRoomWithMemberMapDataClass: ChatRoomWithMemberMapDataClass,
    val fragmentInstanceID: String,
)

data class ChatRoomMovedDataHolder(
    val indexMoved: Boolean, //if set to true, the below 2 index values will be set
    val originalIndexNumber: Int,
    val newIndexNumber: Int
) {
    constructor() : this(
        false,
        -1,
        -1
    )
}

data class ReturnChatRoomsListChatRoomModifiedDataHolder(
    val modifiedIndex: Int, //will be set to -1 if no index was modified
    val chatRoomMoved: ChatRoomMovedDataHolder
) {
    constructor() : this(
        -1,
        ChatRoomMovedDataHolder()
    )

    constructor(_modifiedIndex: Int) : this(
        _modifiedIndex,
        ChatRoomMovedDataHolder()
    )
}

data class ReturnMatchMadeRangeInsertedDataHolder(
    val startIndex: Int,
    val numberItemsInserted: Int
) {
    constructor() : this(
        -1,
        0
    )
}

data class ReturnMatchMadeRemovedDataHolder(
    val index: Int = -1
)

enum class AddedOfRemovedIndex {
    INDEX_ADDED,
    INDEX_REMOVED,
    NEITHER_ADDED_OR_REMOVED
}

data class ReturnChatRoomsListJoinedLeftChatRoomDataHolder(
    val addedOfRemovedIndex: AddedOfRemovedIndex,
    val index: Int
) {
    constructor() : this(
        AddedOfRemovedIndex.NEITHER_ADDED_OR_REMOVED,
        -1
    )
}

data class ReturnJoinedLeftChatRoomDataHolder(
    val chatRoomWithMemberMap: ChatRoomWithMemberMapDataClass,
    val chatRoomUpdateMadeEnum: ChatRoomUpdateMade
) {
    constructor() : this(
        ChatRoomWithMemberMapDataClass(),
        ChatRoomUpdateMade.CHAT_ROOM_LEFT
    )
}

data class UpdateChatRoomInfoResultsDataHolder(
    val message: MessagesDataEntity,
//    val messageUUID: String,
//    val chatRoomId: String,
//    val sentByAccountOID: String,
//    val messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase,
//    val newChatRoomInfoMessage: String,
//    val timestampUpdated: Long
)

data class ReturnChatRoomEventOidUpdated(
    val chatRoomId: String,
    val eventOid: String,
)

data class ReturnQrCodeInfoUpdated(
    val chatRoomId: String,
    val qRCodePath: String,
    val qRCodeMessage: String,
    val qRCodeTimeUpdated: Long,
)

data class ReturnKickedBannedFromChatRoomDataHolder(
    val chatRoomId: String,
    val kickOrBanEnum: ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan
)

data class ReturnMessageUpdateRequestResponseDataHolder(
    val message: MessagesDataEntity,
    //this must be returned because the update will return the minimum amountOfMessage value
    // back with the message and so message.amountOfMessage may not be the same as
    // amountOfMessageRequested
    val amountOfMessageRequested: TypeOfChatMessageOuterClass.AmountOfMessage,
)
