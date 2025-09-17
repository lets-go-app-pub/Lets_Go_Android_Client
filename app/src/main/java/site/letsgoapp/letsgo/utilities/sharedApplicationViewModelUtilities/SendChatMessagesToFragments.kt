package site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.MessagesDataEntityWithAdditionalInfo
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypesUrlsAndFilePaths
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.ChatMessageStoredStatus
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.MessageModifiedInfo
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnMessageUpdateRequestResponseDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnMessagesForChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnUpdatedMessagesToFragment
import site.letsgoapp.letsgo.utilities.EventWrapperWithKeyString
import site.letsgoapp.letsgo.utilities.updateMessageToEditedAndSent
import type_of_chat_message.TypeOfChatMessageOuterClass

class SendChatMessagesToFragments(
    private val chatRoomContainer: ChatRoomContainer,
    private val chatRoomsListInfoContainer: ChatRoomsListInfoContainer,
) {

    //will return new message as well as message response updates
    private val _returnMessageUpdatesForChatRoom: MutableLiveData<EventWrapperWithKeyString<ReturnUpdatedMessagesToFragment>> =
        MutableLiveData()
    val returnMessageUpdatedForChatRoom: LiveData<EventWrapperWithKeyString<ReturnUpdatedMessagesToFragment>> =
        _returnMessageUpdatesForChatRoom

    private val _returnClearHistoryFromChatRoom: MutableLiveData<EventWrapperWithKeyString<MessagesDataEntity>> =
        MutableLiveData()
    val returnClearHistoryFromChatRoom: LiveData<EventWrapperWithKeyString<MessagesDataEntity>> =
        _returnClearHistoryFromChatRoom

    var chatRoomFragmentInitializationCallbackReceived = false
        private set

    //this is necessary to prevent a bug when navigating to self because the new fragment onViewCreated() is
    // called before the old fragment onDestroyView()
    private var currentFragmentInstanceId = "21"

    private enum class MessageFragmentsState {
        NOTHING,
        CHAT_ROOM_LIST_FRAGMENT_STARTED,
        CHAT_ROOM_LIST_FRAGMENT_STOPPED,
        CHAT_ROOM_FRAGMENT_STARTED,
        CHAT_ROOM_FRAGMENT_STOPPED,
    }

    private var messageFragmentsState = MessageFragmentsState.NOTHING

    private fun setMessageFragmentsStateToNothing() {
        messageFragmentsState = MessageFragmentsState.NOTHING
    }

    fun chatRoomListFragmentOnViewCreated(fragmentInstanceId: String) {
        currentFragmentInstanceId = fragmentInstanceId
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED
    }

    fun chatRoomListFragmentOnStart() {
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED
    }

    fun chatRoomListFragmentOnStop() {
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED
    }

    fun chatRoomListFragmentOnDestroyView(fragmentInstanceId: String) {
        if (currentFragmentInstanceId == fragmentInstanceId) {
            setMessageFragmentsStateToNothing()
        }
    }

    fun chatRoomFragmentOnViewCreated(fragmentInstanceId: String) {
        currentFragmentInstanceId = fragmentInstanceId
        chatRoomFragmentInitializationCallbackReceived = false
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED
    }

    fun chatRoomFragmentOnStart() {
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_FRAGMENT_STARTED
    }

    /** This is not a suspend function so it does not use withContext(Main) with the LiveData. Therefore it is assumed that it is being called
     *  from the Main thread. **/
    fun chatRoomFragmentOnStop() {
        messageFragmentsState = MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED

        //send a new message out that the live data of the ChatRoomFragment (or anything else for that matter)
        // will never be able to get
        _returnMessageUpdatesForChatRoom.value = EventWrapperWithKeyString(
            ReturnUpdatedMessagesToFragment(),
            GlobalValues.INVALID_FRAGMENT_INSTANCE_ID,
            true
        )

        _returnClearHistoryFromChatRoom.value = EventWrapperWithKeyString(
            MessagesDataEntity(),
            GlobalValues.INVALID_FRAGMENT_INSTANCE_ID,
            true
        )
    }

    fun chatRoomFragmentOnDestroyView(fragmentInstanceId: String) {
        Log.i("chatMessageState", "chatRoomFragmentOnDestroyView()")
        //when navigating between fragments onDestroyView is called AFTER onViewCreated of the destination
        // and so only allow this to change the fragment state to nothing if it still has 'control' of the variable
        if (currentFragmentInstanceId == fragmentInstanceId) {
            setMessageFragmentsStateToNothing()
        }
    }

    fun sendHistoryClearedBackToFragments(
        message: MessagesDataEntity,
        fragmentInstanceId: String,
    ) {

        when (messageFragmentsState) {
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STARTED -> {
                chatRoomContainer.messages.clear()
                chatRoomContainer.messages.add(MessagesDataEntityWithAdditionalInfo(message))

                chatRoomContainer.clearBetweenChatRoomFragmentInfo()

                //send messages like normal
                _returnClearHistoryFromChatRoom.value =
                    EventWrapperWithKeyString(
                        message,
                        fragmentInstanceId
                    )
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED -> {
                //no need to send anything back, if the ChatRoomFragment goes back to the onStart() it will update
                // the entire recyclerView (as well as the rest of the fragment)
                chatRoomContainer.messages.clear()
                chatRoomContainer.messages.add(MessagesDataEntityWithAdditionalInfo(message))
            }
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                handleMessageUpdate(
                    message,
                    messageUpdate = false
                )
            }
            MessageFragmentsState.NOTHING -> {} //nothing here
        }
    }

    fun sendMessageResponsesBackToFragments(
        message: MessagesDataEntity,
        fragmentInstanceId: String,
    ) {
        when (messageFragmentsState) {

            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STARTED -> {
                //send messages like normal
                val returnValue = setupChatRoomFragmentMessageResponses(message)

                if (returnValue.messagesModified.isNotEmpty()) {
                    _returnMessageUpdatesForChatRoom.value =
                        EventWrapperWithKeyString(
                            returnValue,
                            fragmentInstanceId
                        )
                }
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED -> {
                //no need to send anything back, if the ChatRoomFragment goes back to the onStart() it will update
                // the entire recyclerView (as well as the rest of the fragment)
                setupChatRoomFragmentMessageResponses(message)
            }
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                handleMessageUpdate(
                    message,
                    messageUpdate = false
                )
            }
            MessageFragmentsState.NOTHING -> {} //nothing here
        }
    }

    fun receivedMessageUpdateRequestResponse(
        returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder,
        fragmentInstanceId: String,
    ) {
        when (messageFragmentsState) {
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                handleMessageUpdate(
                    returnMessageUpdateRequestResponseDataHolder.message,
                    messageUpdate = true
                )
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STARTED -> {
                //send messages like normal
                val returnValue = handleMessageUpdateForChatRoomFragment(
                    returnMessageUpdateRequestResponseDataHolder.message
                )

                if (returnValue.messagesModified.isNotEmpty()) {
                    _returnMessageUpdatesForChatRoom.value =
                        EventWrapperWithKeyString(
                            returnValue,
                            fragmentInstanceId
                        )
                }
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED -> {
                //no need to send anything back, if the ChatRoomFragment goes back to the onStart() it will update
                // the entire recyclerView (as well as the rest of the fragment)
                handleMessageUpdateForChatRoomFragment(
                    returnMessageUpdateRequestResponseDataHolder.message
                )
            }
            MessageFragmentsState.NOTHING -> {
            }
        }
    }

    private fun handleMessageUpdateForChatRoomFragment(message: MessagesDataEntity): ReturnUpdatedMessagesToFragment {

        val messagesModified = mutableListOf<MessageModifiedInfo>()

        if (message.chatRoomId == chatRoomContainer.chatRoom.chatRoomId) {
            //if messageType == 0 then the message has an error when processed on the server or was deleted,
            // remove it from the list of messages
            if (message.messageType == 0) {
                for (i in chatRoomContainer.messages.lastIndex downTo 0) {
                    if (message.messageUUIDPrimaryKey == chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey) {
                        chatRoomContainer.messages[i].messageDataEntity.deletedType =
                            TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE

                        messagesModified.add(
                            MessageModifiedInfo(
                                i,
                                chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey
                            )
                        )

                        //not removing it from the requested list here because don't want to request it again
                        break
                    }
                }
            } else { //if message is valid type
                //update message
                for (i in chatRoomContainer.messages.lastIndex downTo 0) {
                    if (message.messageUUIDPrimaryKey == chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey) {
                        chatRoomContainer.messages[i].messageDataEntity =
                            message
                        chatRoomContainer.messages[i].messageUpdateHasBeenRequestedFromServer =
                            false

                        messagesModified.add(
                            MessageModifiedInfo(
                                i,
                                chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey
                            )
                        )

                        break
                    }
                }
            }
        }

        return ReturnUpdatedMessagesToFragment(messagesModified, false, message.chatRoomId)
    }

    private fun handleMessageUpdate(
        message: MessagesDataEntity,
        messageUpdate: Boolean
    ) {
        chatRoomsListInfoContainer.handleMessageUpdateOrResponse(
            message,
            messageUpdate
        )
    }

    /** This is not a suspend function so it does not use withContext(Main) with the LiveData. Therefore it is assumed that it is being called
     *  from the Main thread. **/
    fun sendNewMessagesBackToFragments(
        messageToReturn: ReturnMessagesForChatRoomDataHolder,
        fragmentInstanceId: String,
    ) {

        when (messageFragmentsState) {
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            MessageFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                setupChatRoomListsFragmentNewMessages(
                    messageToReturn.messages
                )
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STARTED -> {
                //send messages like normal
                val returnValue = setupChatRoomFragmentNewMessages(
                    messageToReturn.messages,
                    messageToReturn.mimeTypes,
                    messageToReturn.chatRoomId,
                    messageToReturn.chatRoomInitialization,
                )

                if (
                    returnValue.messagesModified.isNotEmpty()
                    || returnValue.accountOIDsRemoved.isNotEmpty()
                    || (returnValue.numMessagesAdded > 0 && returnValue.firstIndexAdded != -1)
                    || messageToReturn.chatRoomInitialization
                ) {
                    //NOTE: With setValue livedata messages (such as the initialization) should
                    // never be missed.
                    _returnMessageUpdatesForChatRoom.value =
                        EventWrapperWithKeyString(
                            returnValue,
                            fragmentInstanceId
                        )
                }
            }
            MessageFragmentsState.CHAT_ROOM_FRAGMENT_STOPPED -> {
                //no need to send anything back, if the ChatRoomFragment goes back to the onStart() it will update
                // the entire recyclerView (as well as the rest of the fragment)
                setupChatRoomFragmentNewMessages(
                    messageToReturn.messages,
                    messageToReturn.mimeTypes,
                    messageToReturn.chatRoomId,
                    messageToReturn.chatRoomInitialization
                )
            }
            MessageFragmentsState.NOTHING -> {
            }
        }
    }

    private fun setupChatRoomListsFragmentNewMessages(
        newMessages: List<MessagesDataEntity>,
    ) {
        chatRoomsListInfoContainer.newMessageReceived(newMessages)
    }

    private fun setupChatRoomFragmentNewMessages(
        newMessages: List<MessagesDataEntity>,
        newMimeTypesFilePaths: MutableList<MimeTypesUrlsAndFilePaths>,
        chatRoomId: String,
        chatRoomInitialization: Boolean
    ): ReturnUpdatedMessagesToFragment {

        if(chatRoomInitialization) {
            chatRoomFragmentInitializationCallbackReceived = true
        }

        val messageModifiedInfoList = mutableListOf<MessageModifiedInfo>()

        val messagesToAdd = newMessages.filter {

            //if this is an edited message, don't store it inside the new messages, simply update the older message
            if (it.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE.number
                && it.chatRoomId == chatRoomContainer.chatRoom.chatRoomId
            ) { //if edited message and chat room matches

                if (it.modifiedMessageUUID != "" && it.modifiedMessageUUID != "~") {
                    for (i in chatRoomContainer.messages.indices)
                        if (chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey == it.modifiedMessageUUID) {

                            messageModifiedInfoList.add(
                                MessageModifiedInfo(
                                    i,
                                    it.modifiedMessageUUID,
                                    editedMessage = true
                                )
                            )

                            updateMessageToEditedAndSent(
                                chatRoomContainer.messages[i].messageDataEntity,
                                it
                            )

                            break
                        }
                }

                false
            } else if (it.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE.number
                && it.chatRoomId == chatRoomContainer.chatRoom.chatRoomId
            ) { //if deleted message and chat room matches
                //search here by oidValue instead of modifiedMessageIndexValue, messages that come from the server should always have oidValue set
                if (it.modifiedMessageUUID != "" && it.modifiedMessageUUID != "~") {
                    for (i in chatRoomContainer.messages.indices) {
                        if (chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey == it.modifiedMessageUUID) {

                            messageModifiedInfoList.add(
                                MessageModifiedInfo(
                                    i,
                                    it.modifiedMessageUUID,
                                )
                            )

                            chatRoomContainer.messages[i].messageDataEntity.deletedType =
                                TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE

                            break
                        }
                    }
                }
                false
            } else {
                //if this is an observed time message type sent by this account, ignore it
                //if this message is not for this chat room, ignore it
                !(it.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE.number
                        || it.chatRoomId != chatRoomContainer.chatRoom.chatRoomId)
            }
        }

        if (newMimeTypesFilePaths.isNotEmpty()) { //if there are mime type file paths inside

            //add each element of the file path to the mime type
            for (mimeTypeFile in newMimeTypesFilePaths) {

                chatRoomContainer.updateMimeTypeFilePath(
                    mimeTypeFile.mimeTypeUrl,
                    mimeTypeFile.mime_type_value,
                    mimeTypeFile.mime_type_file_path,
                    mimeTypeFile.mime_type_width,
                    mimeTypeFile.mime_type_height,
                )
            }
        }

        val accountOIDsRemoves = mutableListOf<String>()
        val firstIndexAdded: Int
        val numMessagesAdded: Int

        if (messagesToAdd.isNotEmpty()) { //if there are messages inside

            Log.i(
                "getSingleChatImage",
                "messagesToAdd[0].indexNum ${messagesToAdd[0].messageUUIDPrimaryKey}"
            )

            //It is possible for the ChatRoomFragment to receive message from either updateChatRoom()
            // or as a new message before the initialization has completed. This will sort them in
            // such a way that they will always be ordered.
            val sortAfter: Boolean
            if(chatRoomInitialization) {
                firstIndexAdded = 0
                numMessagesAdded = messagesToAdd.size + chatRoomContainer.messages.size
                sortAfter = chatRoomContainer.messages.isNotEmpty()
            } else {
                firstIndexAdded = chatRoomContainer.messages.size
                numMessagesAdded = messagesToAdd.size
                sortAfter = false
            }

            for (message in messagesToAdd) {
                chatRoomContainer.messages.add(
                    MessagesDataEntityWithAdditionalInfo(message)
                )
            }

            if(sortAfter) {
                chatRoomContainer.messages.sortBy {
                    it.messageDataEntity.messageStoredOnServerTime
                }
            }

            for (message in messagesToAdd) {
                when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    message.messageType
                )) {
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
                    -> {
                        accountOIDsRemoves.add(message.sentByAccountID)
                    }
                    else -> {
                    }
                }
            }

        } else {
            firstIndexAdded = -1
            numMessagesAdded = 0
        }

        return ReturnUpdatedMessagesToFragment(
            messageModifiedInfoList,
            accountOIDsRemoves,
            firstIndexAdded,
            numMessagesAdded,
            chatRoomInitialization,
            chatRoomId
        )
    }

    private fun setupChatRoomFragmentMessageResponses(responseToMessage: MessagesDataEntity): ReturnUpdatedMessagesToFragment {

        Log.i("chatMessageAdapter", "setupChatRoomFragmentNewMessages()")

        val messageModifiedInfoList = mutableListOf<MessageModifiedInfo>()

        if (responseToMessage.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE.number) { //if message edited value

            //this must be done to update {messages[i].editHasBeenSent = true} even though the message was already updated before being sent

            if (responseToMessage.messageSentStatus == ChatMessageStoredStatus.STORED_ON_SERVER.ordinal) {
                for (i in chatRoomContainer.messages.indices)
                    if (chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey == responseToMessage.modifiedMessageUUID) {

                        messageModifiedInfoList.add(
                            MessageModifiedInfo(
                                i,
                                responseToMessage.modifiedMessageUUID,
                                editedMessage = true
                            )
                        )

                        updateMessageToEditedAndSent(
                            chatRoomContainer.messages[i].messageDataEntity,
                            responseToMessage
                        )

                        break
                    }
            }

        } else if (responseToMessage.messageType != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE.number
            && responseToMessage.messageType != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE.number
        ) { //if this is an observed time or message deleted sent by this account, ignore it

            //iterate through the list and check if this returned element exists inside of it, if it does
            // update the element
            for (i in chatRoomContainer.messages.size - 1 downTo 0) {
                if (chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey == responseToMessage.messageUUIDPrimaryKey) {

                    //dismiss menu if opened for this message
                    messageModifiedInfoList.add(
                        MessageModifiedInfo(
                            i,
                            responseToMessage.messageUUIDPrimaryKey
                        )
                    )

                    chatRoomContainer.messages[i].messageDataEntity =
                        responseToMessage

                    break
                }
            }
        }

        return ReturnUpdatedMessagesToFragment(
            messageModifiedInfoList,
            false,
            responseToMessage.chatRoomId
        )
    }

}