package site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities

import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.MessagesDataEntityWithAdditionalInfo
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.ChatMessageAdapter

class ChatRoomContainer {

    class MessagesList : ArrayList<MessagesDataEntityWithAdditionalInfo>() {
        override fun add(element: MessagesDataEntityWithAdditionalInfo): Boolean {

            //set up layout type for element whenever it is added
            //NOTE: The layout type is dynamic (for example a message can be deleted or
            // a user can be blocked) however it is still important to set it as this
            // value is inserted into the list in order for updates to work properly
            // (see init in ChatMessageAdapter for details)
            /** [ChatMessageAdapter] **/
            //NOTE: There is a (admittedly small) gap between this layout type being set
            // and the recycler view being notified, however it should be acceptable because
            // the recycler view will update it again if it is displayed on screen, this
            // is more for the purpose of update working properly
            element.messageLayoutType = findLayoutType(
                element.messageDataEntity.sentByAccountID,
                element.messageDataEntity.deletedType,
                element.messageDataEntity.messageType
            )
            return super.add(element)
        }
    }

    /** Never remove elements from the messages array here, only add to it and edit existing elements (although
     * clear is used when history is cleared). **/
    //only used in ChatRoomFragment and ChatMessageAdapter
    val messages = MessagesList()

    //only used in ChatRoomFragment and ChatMessageAdapter
    val mimeTypesFilePaths =
        mutableMapOf<String, MimeTypeHolderObject>() //url is the key, fileName is the value

    //this should only be set in MessengerScreenFragment when navigating to a chat room, in MatchMadeScreenFragment or when
    // an invite is clicked
    //liveData actually updates it returnSingleChatRoomObserver, and returnCreatedChatRoomObserver call updateChatRoom
    //NOTE: the chatRoom.chatRoomID will be set to "~" if this has not been set
    var chatRoom = ChatRoomWithMemberMapDataClass()
        private set

    //an ID that is unique to the chat room, it is the chat room id mixed with a timestamp, used with below functions
    //returnLeaveChatRoomResult
    //returnChatRoomInfoUpdatedData
    //returnKickedBannedFromChatRoom
    //returnBlockReportChatRoomResult
    //returnUpdatedOtherUser
    //returnPromoteNewAdminFailed
    var chatRoomUniqueId = chatRoom.chatRoomId
        private set

    //chat room sort method selected
    var chatRoomSortMethodSelected = ChatRoomSortMethodSelected.SORT_BY_UNREAD
        private set

    //used when selecting location chat message to pass info between fragment
    //NOTE: technically the user cannot select the location stored inside GlobalValues.defaultLocation
    var locationMessageObject = LocationSelectedObject()

    var pinnedLocationObject = LocationSelectedObject()

    //used when selecting invite chat message to pass info between fragments
    var inviteMessageObject = InviteMessageObject()

    //used to store the info for a reply to a chat room
    //NOTE: This should be cleared when clicking on a chat room, joining a new chat room or creating a chat room.
    var replyMessageInfo = ReplyMessageInfoObject()
        private set

    fun setReply(passedReplyMessageInfoObject: ReplyMessageInfoObject) {
        //set to default (NOTE: this can be a reference to an object inside the chatMessageAdapter recycler view, so
        // need to set it to a new reference instead of changing values)
        replyMessageInfo = passedReplyMessageInfoObject
    }

    fun clearReply() {
        //set to default (NOTE: this can be a reference to an object inside the chatMessageAdapter recycler view, so
        // need to set it to a new reference instead of changing values)
        replyMessageInfo = ReplyMessageInfoObject()
    }

    fun clearChatRoomInvite() {
        inviteMessageObject = InviteMessageObject()
    }

    fun clearChatRoomLocation() {
        locationMessageObject = LocationSelectedObject()
        pinnedLocationObject = LocationSelectedObject()
    }

    //NOTE: clears all the stuff for the chat room fragment messages between fragments
    fun clearBetweenChatRoomFragmentInfo() {
        clearReply()
        clearChatRoomInvite()
        clearChatRoomLocation()
    }

    fun removeChatRoomFromMatchingState(messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase) {
        if (chatRoom.matchingChatRoomOID != "" && checkIfFirstContactMessage(messageType)) { //if a matching chat room is stored

            //NOTE: when a message is sent this will be handled inside the client
            chatRoom.matchingChatRoomOID = ""
        }
    }

    fun updateChatRoomLastObservedTime(observedTime: Long) {
        if (observedTime > chatRoom.chatRoomLastObservedTime) {
            //update last observed time
            chatRoom.chatRoomLastObservedTime = observedTime
        }
    }

    fun updateChatRoomLastActiveTimeForMessageType(
        messageType: TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase,
        observedTime: Long
    ) {
        if (checkIfChatRoomLastActiveTimeRequiresUpdating(messageType)) {
            updateChatRoomLastActiveTime(observedTime)
        }
    }

    fun updateChatRoomLastActiveTime(lastActivityTime: Long) {
        if (lastActivityTime > chatRoom.chatRoomLastActivityTime) {
            //update last observed time
            chatRoom.chatRoomLastActivityTime = lastActivityTime
        }
    }

    fun setChatRoomSortMethodSelected(value: ChatRoomSortMethodSelected) {
        chatRoomSortMethodSelected = value
    }

    fun setChatRoomInfo(newChatRoom: ChatRoomWithMemberMapDataClass) {
        chatRoomUniqueId = newChatRoom.chatRoomId + getCurrentTimestampInMillis()
        chatRoom = newChatRoom

        //these are cleared here to avoid sending invite or location messages as well as setting up a reply
        // from a false chat room
        clearBetweenChatRoomFragmentInfo()
    }

    fun updateMimeTypeFilePath(
        mimeTypeUrl: String,
        mimeType: String,
        mimeTypeFilePath: String = "",
        mimeTypeWidth: Int = 0,
        mimeTypeHeight: Int = 0,
    ) {
        val previousFileObject =
            mimeTypesFilePaths[mimeTypeUrl]

        if (previousFileObject == null) { //if file does not exist
            mimeTypesFilePaths[mimeTypeUrl] =
                MimeTypeHolderObject(
                    mimeTypeFilePath,
                    mimeTypeWidth,
                    mimeTypeHeight,
                    mimeType
                )
        } else if (mimeTypeFilePath != "" && previousFileObject.filePath != mimeTypeFilePath) { //if the passed mime type file path is valid and it is not equal to the previous file path
            mimeTypesFilePaths[mimeTypeUrl] =
                MimeTypeHolderObject(
                    mimeTypeFilePath,
                    mimeTypeWidth,
                    mimeTypeHeight,
                    mimeType
                )
        }
    }
}