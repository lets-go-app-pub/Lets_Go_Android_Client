package site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities

import account_state.AccountState
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MostRecentMessageDataHolder
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass
import user_account_type.UserAccountTypeOuterClass.UserAccountType

//used to store chat room info in such a way that
// 1) No info is missed when the app is minimized.
// 2) Most recent info is always saved.
class ChatRoomsListInfoContainer(
    private val chatRoomContainer: ChatRoomContainer,
    private val getMostRecentMessageInChatRoom: suspend (chatRoomID: String, chatRoomLastActiveTime: Long, chatRoomTimeJoined: Long) -> Pair<MostRecentMessageDataHolder?, Long>,
    private val updateChatRoomsSortedType: (passedChatRoomSortMethodSelected: ChatRoomSortMethodSelected) -> Unit,
    private val errorStore: StoreErrorsInterface
) {

    private val _chatRoomAddedRemoved: MutableLiveData<EventWrapperWithKeyString<ReturnChatRoomsListJoinedLeftChatRoomDataHolder>> =
        MutableLiveData()
    val chatRoomAddedRemoved: LiveData<EventWrapperWithKeyString<ReturnChatRoomsListJoinedLeftChatRoomDataHolder>> =
        _chatRoomAddedRemoved

    private val _chatRoomModified: MutableLiveData<EventWrapperWithKeyString<ReturnChatRoomsListChatRoomModifiedDataHolder>> =
        MutableLiveData()
    val chatRoomModified: LiveData<EventWrapperWithKeyString<ReturnChatRoomsListChatRoomModifiedDataHolder>> =
        _chatRoomModified

    private val _chatRoomsListUpdateDataSet: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val chatRoomsListUpdateDataSet: LiveData<EventWrapper<Unit>> =
        _chatRoomsListUpdateDataSet

    private val _matchMadeRemoved: MutableLiveData<EventWrapper<ReturnMatchMadeRemovedDataHolder>> =
        MutableLiveData()
    val matchMadeRemoved: LiveData<EventWrapper<ReturnMatchMadeRemovedDataHolder>> =
        _matchMadeRemoved

    private val _matchMadeRangeInserted: MutableLiveData<EventWrapper<ReturnMatchMadeRangeInsertedDataHolder>> =
        MutableLiveData()
    val matchMadeRangeInserted: LiveData<EventWrapper<ReturnMatchMadeRangeInsertedDataHolder>> =
        _matchMadeRangeInserted

    //only used by ChatRoomsListFragment (which can be a child fragment of MessengerScreenFragment)
    //the unique values are needed if activity is destroyed and recreated during ChatRoomsListFragment
    val chatRooms = ListWithUniqueStorage<String, ChatRoomWithMemberMapDataClass> { it.chatRoomId }

    //only used by MessengerScreenFragment
    //the unique values are needed if activity is destroyed and recreated during ChatRoomsListFragment
    val matchesMade =
        ListWithUniqueStorage<String, ChatRoomWithMemberMapDataClass> { it.chatRoomId }

    private enum class ChatRoomsListFragmentsState {
        NOTHING,
        CHAT_ROOM_LIST_FRAGMENT_STARTED,
        CHAT_ROOM_LIST_FRAGMENT_STOPPED,
    }

    private enum class MessengerFragmentsState {
        NOTHING,
        MESSENGER_FRAGMENT_STARTED,
        MESSENGER_FRAGMENT_STOPPED,
    }

    data class AddChatRoomReturnValue(
        val returnChatRoomsListJoinedLeftChatRoomDataHolder: ReturnChatRoomsListJoinedLeftChatRoomDataHolder,
        val dataSetRequiresModified: Boolean
    )

    private var chatRoomsListFragmentsState = ChatRoomsListFragmentsState.NOTHING

    //this is necessary to prevent a bug when navigating to self because the new fragment onViewCreated() is
    // called before the old fragment onDestroyView()
    private var chatRoomsListFragmentInstanceId = "45"

    private var messengerFragmentsState = MessengerFragmentsState.NOTHING

    //see chatRoomsListFragmentInstanceId comment
    private var messengerFragmentInstanceId = "54"

    fun chatRoomListFragmentOnViewCreated(fragmentInstanceId: String) {
        chatRoomsListFragmentInstanceId = fragmentInstanceId
        chatRoomsListFragmentsState = ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED
    }

    fun chatRoomListFragmentOnStart() {
        chatRoomsListFragmentsState = ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED
    }

    fun chatRoomListFragmentOnStop() {
        chatRoomsListFragmentsState = ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED

        //'clear' Live Data

        _chatRoomAddedRemoved.value = EventWrapperWithKeyString(
            ReturnChatRoomsListJoinedLeftChatRoomDataHolder(),
            GlobalValues.INVALID_FRAGMENT_INSTANCE_ID
        )

        _chatRoomModified.value = EventWrapperWithKeyString(
            ReturnChatRoomsListChatRoomModifiedDataHolder(),
            GlobalValues.INVALID_FRAGMENT_INSTANCE_ID,
            true
        )

        _chatRoomsListUpdateDataSet.value = EventWrapper(
            Unit,
            true
        )
    }

    fun chatRoomListFragmentOnDestroyView(fragmentInstanceId: String) {
        if (chatRoomsListFragmentInstanceId == fragmentInstanceId) {
            chatRoomsListFragmentsState = ChatRoomsListFragmentsState.NOTHING
        }
    }

    fun messengerFragmentOnViewCreated(fragmentInstanceId: String) {
        messengerFragmentInstanceId = fragmentInstanceId
        messengerFragmentsState = MessengerFragmentsState.MESSENGER_FRAGMENT_STOPPED
    }

    fun messengerFragmentOnStart() {
        messengerFragmentsState = MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED
    }

    fun messengerFragmentOnStop() {
        messengerFragmentsState = MessengerFragmentsState.MESSENGER_FRAGMENT_STOPPED

        _matchMadeRemoved.value = EventWrapper(
            ReturnMatchMadeRemovedDataHolder(),
            true
        )

        _matchMadeRangeInserted.value = EventWrapper(
            ReturnMatchMadeRangeInsertedDataHolder(),
            true
        )
    }

    fun messengerFragmentOnDestroyView(fragmentInstanceId: String) {
        if (messengerFragmentInstanceId == fragmentInstanceId) {
            messengerFragmentsState = MessengerFragmentsState.NOTHING
        }
    }

    //returns true if ChatRoomsListsFragment will receive the passed info, false otherwise
    fun chatRoomListsFragmentActive(): Boolean {
        return when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
                false
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                true
            }
        }
    }

    suspend fun joinedLeftChatRoom(info: ReturnJoinedLeftChatRoomDataHolder) {

        //NOTE: This MUST run on the main thread in order to provide concurrency for the
        // data structures. Also it sets liveData several times.
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                val returnValue = handleJoinedLeftChatRoomReturn(info)
                returnValue?.let {
                    if (!it.dataSetRequiresModified) {
                        _chatRoomAddedRemoved.value = EventWrapperWithKeyString(
                            returnValue.returnChatRoomsListJoinedLeftChatRoomDataHolder,
                            chatRoomContainer.chatRoomUniqueId
                        )
                    } else {
                        _chatRoomsListUpdateDataSet.value = EventWrapper(Unit)
                    }
                }
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED -> {
                handleJoinedLeftChatRoomReturn(info)
            }
        }

    }

    private suspend fun handleJoinedLeftChatRoomReturn(info: ReturnJoinedLeftChatRoomDataHolder): AddChatRoomReturnValue? {

        val chatRoom = info.chatRoomWithMemberMap

        var returnVal: AddChatRoomReturnValue? = null

        when (info.chatRoomUpdateMadeEnum) {
            ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED,
            ChatRoomUpdateMade.CHAT_ROOM_JOINED -> {

                //NOTE: CHAT_ROOM_JOINED is not sent back until THIS_USER_JOINED_CHAT_ROOM_FINISHED is sent, meaning that any messages
                // are not stored here so mostRecentMessage must be extracted from the database

                //getMostRecentMessageInChatRoom() will internally remove itself from the main thread in order to run this because
                // it is a database call. Need the rest of this function to be on Main thread in order to synchronize
                // data structures.
                val finalMessageAndProperActivityTime =
                    getMostRecentMessageInChatRoom(
                        chatRoom.chatRoomId,
                        chatRoom.chatRoomLastActivityTime,
                        chatRoom.timeJoined,
                    )

                chatRoom.chatRoomLastActivityTime = finalMessageAndProperActivityTime.second
                chatRoom.hardSetNewFinalMessage(finalMessageAndProperActivityTime.first)

                returnVal = addChatRoom(chatRoom)
            }
            ChatRoomUpdateMade.CHAT_ROOM_LEFT -> {

                //NOTE: this can be sent back as a match or chat room type when the chat stream starts and returns an update
                if (chatRoom.matchingChatRoomOID.isNotEmpty()) { //if this is a match
                    handleJoinedLeftMatchMadeChatRoomReturn(info)
                } else { //if this is a chat room
                    returnVal = AddChatRoomReturnValue(
                        removeChatRoomFromAdapter(chatRoom.chatRoomId),
                        false
                    )
                }
            }
            ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH,
            ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED,
            -> {
                handleJoinedLeftMatchMadeChatRoomReturn(info)
            }
        }

        return returnVal
    }

    fun kickedBannedFromChatRoom(info: ReturnKickedBannedFromChatRoomDataHolder) {
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                _chatRoomAddedRemoved.value = EventWrapperWithKeyString(
                    removeChatRoomFromAdapter(info.chatRoomId),
                    chatRoomContainer.chatRoomUniqueId
                )
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED -> {
                removeChatRoomFromAdapter(info.chatRoomId)
            }
        }
    }

    fun removeChatRoomFromLoading(chatRoomId: String): Int {
        var indexNum = -1

        for (i in chatRooms.indices) {
            if (chatRooms[i].chatRoomId == chatRoomId) {
                chatRooms[i].showLoading = false
                indexNum = i
                break
            }
        }

        return indexNum
    }

    fun leaveChatRoom(chatRoomId: String) {
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                _chatRoomAddedRemoved.value = EventWrapperWithKeyString(
                    removeChatRoomFromAdapter(chatRoomId),
                    chatRoomContainer.chatRoomUniqueId
                )
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED -> {
                removeChatRoomFromAdapter(chatRoomId)
            }
        }
    }

    fun returnAllChatRooms(
        receivedChatRooms: MutableList<ChatRoomWithMemberMapDataClass>,
        typeOfFragmentCalledFrom: ChatRoomListCalledFrom,
    ) {
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                handleReturnAllChatRooms(receivedChatRooms, typeOfFragmentCalledFrom)
            }
        }
    }

    private fun handleReturnAllChatRooms(
        receivedChatRooms: MutableList<ChatRoomWithMemberMapDataClass>,
        typeOfFragmentCalledFrom: ChatRoomListCalledFrom,
    ) {

        Log.i("ChatRoomsListFrag", "handleReturnAllChatRooms()")
        if (receivedChatRooms.isNotEmpty()) {

            val chatRoomsStartIndex = chatRooms.size
            val matchesMadeMap = mutableListOf<ChatRoomWithMemberMapDataClass>()

            for (chatRoom in receivedChatRooms) {
                if (chatRoom.matchingChatRoomOID.isEmpty()) { //if this is not a 'matching' chat room
                    when (typeOfFragmentCalledFrom) {
                        ChatRoomListCalledFrom.MESSENGER_FRAGMENT -> {
                            chatRooms.add(chatRoom)
                        }
                        ChatRoomListCalledFrom.INVITE_FRAGMENT -> {
                            //exclude the chat room the user is currently in from the list
                            if (chatRoom.chatRoomId != chatRoomContainer.chatRoom.chatRoomId) {
                                chatRooms.add(chatRoom)
                            }
                        }
                    }
                } else {
                    matchesMadeMap.add(chatRoom)
                }
            }

            val chatRoomsNumberItemsInserted = chatRooms.size - chatRoomsStartIndex

            if (0 < chatRoomsNumberItemsInserted) { //if at least one element was inserted to chatRooms
                sortChatRooms(chatRoomContainer.chatRoomSortMethodSelected)
            }

            if (0 < matchesMadeMap.size) { //if at least one element was inserted to matchesMade
                handleReturnMatchMadeChatRooms(matchesMadeMap)
            }
        }
    }

    fun sortChatRooms(chatRoomSortMethodSelected: ChatRoomSortMethodSelected) {

        updateChatRoomsSortedType(chatRoomSortMethodSelected)

        when (chatRoomSortMethodSelected) {
            ChatRoomSortMethodSelected.SORT_BY_UNREAD -> {
                chatRooms.sortByDescending {
                    //this should never be -1, however if it WAS, the chat room would end up at the bottom because this is
                    // in descending order
                    if (it.chatRoomLastObservedTime < it.chatRoomLastActivityTime) { //unread
                        it.chatRoomLastActivityTime * 100
                    } else { //read
                        it.chatRoomLastActivityTime
                    }
                }
            }
            ChatRoomSortMethodSelected.SORT_BY_VISITED -> {
                chatRooms.sortByDescending {
                    //this might be able to be -1, if it is the chat room will end up at the bottom
                    it.chatRoomLastObservedTime
                }
            }
            ChatRoomSortMethodSelected.SORT_BY_RECENT -> {
                chatRooms.sortByDescending {
                    //this should never be -1, however if it WAS, the chat room would end up at the bottom because this is
                    // in descending order
                    it.chatRoomLastActivityTime
                }
            }
            ChatRoomSortMethodSelected.SORT_BY_JOINED -> {
                chatRooms.sortBy {
                    //this should never be -1, however if it WAS, the chat room would end up at the top
                    it.timeJoined
                }
            }
        }

        Log.i(
            "chatRoomsList",
            "messengerScreenChatRoomsAdapter lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
        )
        if (chatRoomsListFragmentsState == ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED) {
            _chatRoomsListUpdateDataSet.value = EventWrapper(Unit)
        }
    }

    //returns the number of elements successfully updated
    fun displayMatchingChatRoomIds(
        matchingChatRoomIds: Set<String>?,
    ): List<Int> {

        val chatRoomsUpdatedIndex = mutableListOf<Int>()

        for (i in chatRooms.indices) {
            if (matchingChatRoomIds != null) {
                val showChatRoomId = matchingChatRoomIds.contains(chatRooms[i].chatRoomId)

                if (showChatRoomId && !chatRooms[i].displayChatRoom) {
                    chatRooms[i].displayChatRoom = true
                    chatRoomsUpdatedIndex.add(i)
                } else if (!showChatRoomId && chatRooms[i].displayChatRoom) {
                    chatRooms[i].displayChatRoom = false
                    chatRoomsUpdatedIndex.add(i)
                }
            } else if (!chatRooms[i].displayChatRoom) {
                chatRooms[i].displayChatRoom = true
                chatRoomsUpdatedIndex.add(i)
            }
        }

        return chatRoomsUpdatedIndex
    }

    fun chatRoomInfoUpdated(info: UpdateChatRoomInfoResultsDataHolder) {
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                _chatRoomModified.value = EventWrapperWithKeyString(
                    handleChatRoomInfoUpdated(info),
                    chatRoomContainer.chatRoomUniqueId
                )
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED -> {
                handleChatRoomInfoUpdated(info)
            }
        }
    }

    private fun handleChatRoomInfoUpdated(resultsData: UpdateChatRoomInfoResultsDataHolder): ReturnChatRoomsListChatRoomModifiedDataHolder {

        var indexNum = -1

        for (i in chatRooms.indices) {
            if (chatRooms[i].chatRoomId == resultsData.message.chatRoomId) {
                indexNum = i
                break
            }
        }

        //NOTE: the chat room not being found could happen because this is called by chatRoomUniqueId and so it could be
        // called from 1 chat room fragment and implemented here
        return if (indexNum != -1) { //if chat room was found

            when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(resultsData.message.messageType)) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
                    chatRooms[indexNum].chatRoomName = resultsData.message.messageText

                    chatRooms[indexNum].setNewFinalMessageIfRelevant(resultsData.message)

                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
                    chatRooms[indexNum].chatRoomPassword = resultsData.message.messageText

                    chatRooms[indexNum].setNewFinalMessageIfRelevant(resultsData.message)
                }
                else -> {
                    val errorMessage =
                        "Invalid message type received when running handleChatRoomInfoUpdated()." +
                                "messageType: ${
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                        resultsData.message.messageType
                                    )
                                }\n" +
                                "message: ${resultsData.message}\n"

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    //can continue here
                }
            }

            //NOTE: must always update the chat room so it can show the icon for new message
            Log.i(
                "chatRoomsList",
                "messengerScreenChatRoomsAdapter lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            if (
                chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_UNREAD
                || chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_RECENT
            ) {
                ReturnChatRoomsListChatRoomModifiedDataHolder(
                    indexNum,
                    moveChatRoomToNewSortByActiveTimePosition(indexNum)
                )
            } else {
                ReturnChatRoomsListChatRoomModifiedDataHolder(indexNum)
            }
        } else {
            ReturnChatRoomsListChatRoomModifiedDataHolder()
        }
    }

    fun handleMessageUpdateOrResponse(
        message: MessagesDataEntity,
        messageUpdate: Boolean
    ) {

        var modifiedIndex = -1

        for (i in chatRooms.indices) {
            if (message.chatRoomId == chatRooms[i].chatRoomId) {
                val updateOccurred =
                    if(messageUpdate) {
                        chatRooms[i].handleMessageUpdate(message)
                    } else {
                        chatRooms[i].setNewFinalMessageIfRelevant(message).updateOccurred
                    }
                //if the update did NOT occur (say it was not the final message in the list being updated), do not send anything back
                if(updateOccurred) modifiedIndex = i
                break
            }
        }

        if (modifiedIndex != -1) {
            when (chatRoomsListFragmentsState) {
                ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                    _chatRoomModified.value = EventWrapperWithKeyString(
                        ReturnChatRoomsListChatRoomModifiedDataHolder(modifiedIndex),
                        chatRoomContainer.chatRoomUniqueId
                    )
                }
                ChatRoomsListFragmentsState.NOTHING,
                ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
                -> {
                }
            }
        }
    }

    fun newMessageReceived(
        messages: List<MessagesDataEntity>,
    ) {
        when (chatRoomsListFragmentsState) {
            ChatRoomsListFragmentsState.NOTHING -> {
            }
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED,
            ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED,
            -> {
                handleNewMessageReceived(messages)
            }
        }
    }

    private fun handleNewMessageReceived(
        messages: List<MessagesDataEntity>,
    ) {
        if (messages.isNotEmpty()) {

            val chatRoomId = messages[0].chatRoomId
            var indexNum = -1

            for (i in chatRooms.indices) {
                if (chatRooms[i].chatRoomId == chatRoomId) {
                    indexNum = i
                    break
                }
            }

            if (indexNum != -1) { //chat room was found inside the list

                Log.i(
                    "chatRoomFound", "chatRoomFound for messageType: typeOfMessage: ${
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                            messages[0].messageType
                        )
                    }"
                )

                var elementWasUpdated = false
                var lastActivityTimeWasUpdated = false

                for (i in messages.indices) {
                    val returnVal = chatRooms[indexNum].setNewFinalMessageIfRelevant(messages[i])
                    if (returnVal.updateOccurred) {
                        elementWasUpdated = true
                    }
                    if (returnVal.lastActivityTimeWasUpdated) {
                        lastActivityTimeWasUpdated = true
                    }
                }

                if (elementWasUpdated) {

                    //Other sort types
                    // SORT_BY_RECENT; sorts by chatRoomLastObservedTime in descending order, will not change the sort order, updateChatRoom
                    // should send the observed time to the server when joining a chat room and new observed times will not change unless the user
                    // is 'observing' the chat room
                    // SORT_BY_JOINED; sorts by timeJoined in ascending order, this should never change

                    val returnValue =
                        if (
                            (chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_UNREAD
                                    || chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_RECENT)
                            && lastActivityTimeWasUpdated
                        ) {
                            ReturnChatRoomsListChatRoomModifiedDataHolder(
                                indexNum,
                                moveChatRoomToNewSortByActiveTimePosition(indexNum)
                            )
                        } else {
                            ReturnChatRoomsListChatRoomModifiedDataHolder(indexNum)
                        }

                    if (chatRoomsListFragmentsState == ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED) {
                        _chatRoomModified.value = EventWrapperWithKeyString(
                            returnValue,
                            chatRoomContainer.chatRoomUniqueId
                        )
                    }
                }

            } else if (messengerFragmentsState != MessengerFragmentsState.NOTHING) { //chat room was not found inside the list and messenger fragment is active
                handleMatchMadeMessageReceived(messages)
            }
        }
    }

    private class FunctionDidNotCompleteException(message: String) : Exception(message)

    fun updatedChatRoomMember(result: ReturnMessageWithMemberForChatRoomDataHolder) {
        try {
            when (chatRoomsListFragmentsState) {
                ChatRoomsListFragmentsState.NOTHING -> {
                }
                ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED -> {
                    _chatRoomModified.value = EventWrapperWithKeyString(
                        handleUpdatedChatRoomMember(result),
                        chatRoomContainer.chatRoomUniqueId
                    )
                }
                ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STOPPED -> {
                    handleUpdatedChatRoomMember(result)
                }
            }
        } catch (e: FunctionDidNotCompleteException) {
            /**Can ignore this, error already stored.**/
        }
    }

    //Throws FunctionDidNotCompleteException
    private fun handleUpdatedChatRoomMember(result: ReturnMessageWithMemberForChatRoomDataHolder): ReturnChatRoomsListChatRoomModifiedDataHolder {
        val message = result.message
        val chatRoomId = message.chatRoomId

        var indexNum = -1

        for (i in chatRooms.indices) {
            if (chatRooms[i].chatRoomId == chatRoomId) {
                indexNum = i
                break
            }
        }

        //NOTE: the chat room not being found could happen because this is called by chatRoomUniqueId and so it could be
        // called from 1 chat room fragment and implemented here
        return if (indexNum != -1) {

            when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                message.messageType
            )) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE -> {
                    if (message.accountOID != LoginFunctions.currentAccountOID) { //if this is not the current user
                        //if a new message type was sent removing the current user there is an error
                        chatRooms[indexNum].chatRoomMembers.updateAccountStateByAccountOID(
                            message.accountOID,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM
                        )
                    } else { //if this is the current user

                        val errorMessage =
                            "Received USER_KICKED_MESSAGE for current user inside handleUpdatedChatRoomMember()." +
                                    "messageType: ${
                                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                            message.messageType
                                        )
                                    }\n" +
                                    "message: $message\n"

                        storeError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        throw FunctionDidNotCompleteException(errorMessage)
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE -> {
                    if (message.accountOID != LoginFunctions.currentAccountOID) { //if this is not the current user
                        chatRooms[indexNum].chatRoomMembers.updateAccountStateByAccountOID(
                            message.accountOID,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED
                        )
                    } else { //if this is the current user
                        val errorMessage =
                            "Received USER_BANNED_MESSAGE for current user inside handleUpdatedChatRoomMember()." +
                                    "messageType: ${
                                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                            message.messageType
                                        )
                                    }\n" +
                                    "message: $message\n"

                        storeError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        throw FunctionDidNotCompleteException(errorMessage)
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {
                    if (message.sentByAccountID != LoginFunctions.currentAccountOID) { //if this is not the current user
                        chatRooms[indexNum].chatRoomMembers.updateAccountStateByAccountOID(
                            message.sentByAccountID,
                            AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM
                        )
                    } else { //if this is the current user
                        val errorMessage =
                            "Received DIFFERENT_USER_LEFT_MESSAGE for current user inside handleUpdatedChatRoomMember()." +
                                    " Check the repository for details, however while DIFFERENT_USER_LEFT_CHAT_ROOM " +
                                    "is possible for this user it should never be returned to this point\n" +
                                    "messageType: ${
                                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                            message.messageType
                                        )
                                    }\n" +
                                    "message: $message\n"

                        storeError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        throw FunctionDidNotCompleteException(errorMessage)
                    }
                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                    //NOTE: it is technically possible for this to be about the current user
                    if (message.sentByAccountID != LoginFunctions.currentAccountOID) { //if this is not the current user
                        chatRooms[indexNum].chatRoomMembers.add(result.otherUserInfo)
                    }
                }
                else -> {
                    val errorMessage =
                        "USER_KICKED_MESSAGE, USER_BANNED_MESSAGE, DIFFERENT_USER_LEFT_MESSAGE, DIFFERENT_USER_JOINED_MESSAGE" +
                                " are the only types that should make it to handleUpdatedChatRoomMember().\n" +
                                "messageType: ${
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                        message.messageType
                                    )
                                }\n" +
                                "message: $message\n"

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    throw FunctionDidNotCompleteException(errorMessage)
                }
            }

            chatRooms[indexNum].setNewFinalMessageIfRelevant(message)

//            chatRooms[indexNum].chatRoomLastActivityTime = message.messageStoredOnServerTime

            if (
                chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_UNREAD
                && chatRoomContainer.chatRoomSortMethodSelected == ChatRoomSortMethodSelected.SORT_BY_RECENT
            ) {
                ReturnChatRoomsListChatRoomModifiedDataHolder(
                    indexNum,
                    moveChatRoomToNewSortByActiveTimePosition(indexNum)
                )
            } else {
                ReturnChatRoomsListChatRoomModifiedDataHolder(indexNum)
            }

        } else {
            ReturnChatRoomsListChatRoomModifiedDataHolder()
        }
    }

    private fun removeChatRoomFromAdapter(chatRoomId: String): ReturnChatRoomsListJoinedLeftChatRoomDataHolder {

        if (chatRoomId == "~") {
            return ReturnChatRoomsListJoinedLeftChatRoomDataHolder()
        }

        var indexNum = -1

        for (i in chatRooms.indices) {
            if (chatRooms[i].chatRoomId == chatRoomId) {
                indexNum = i
                break
            }
        }

        val addedOfRemovedIndex: AddedOfRemovedIndex

        //NOTE: the chat room not being found in the list could happen because this is called by chatRoomUniqueId and
        // so it could be called from 1 chat room fragment and implemented here
        if (indexNum != -1) {
            addedOfRemovedIndex = AddedOfRemovedIndex.INDEX_REMOVED
            chatRooms.removeAt(indexNum)
        } else {
            addedOfRemovedIndex = AddedOfRemovedIndex.NEITHER_ADDED_OR_REMOVED
        }

        return ReturnChatRoomsListJoinedLeftChatRoomDataHolder(
            addedOfRemovedIndex,
            indexNum
        )
    }

    //called when chatRoomLastActiveTime is updated
    private fun moveChatRoomToNewSortByActiveTimePosition(currentIndexNumber: Int): ChatRoomMovedDataHolder {

        //SORT_BY_UNREAD & SORT_BY_RECENT sorts by chatRoomLastActivityTime, and so if chatRoomLastActivityTime was updated, then
        // this element will need to be updated, chatRoomLastActivityTime should only ever increase so only
        // chat rooms before this elements need to be checked (they are sorted in descending order)

        var newLocationIndexNum = currentIndexNumber

        for (i in 0 until currentIndexNumber) {
            if (chatRooms[currentIndexNumber].chatRoomLastActivityTime >= chatRooms[i].chatRoomLastActivityTime) {
                //move new index to chat room i position
                newLocationIndexNum = i
                break
            }
        }

        return if (newLocationIndexNum != currentIndexNumber) { //if chatRoom moved positions

            chatRooms.move(currentIndexNumber, newLocationIndexNum)

            ChatRoomMovedDataHolder(
                true,
                currentIndexNumber,
                newLocationIndexNum
            )
        } else {
            ChatRoomMovedDataHolder()
        }
    }

    private fun handleReturnMatchMadeChatRooms(receivedChatRooms: MutableList<ChatRoomWithMemberMapDataClass>) {
        if (messengerFragmentsState != MessengerFragmentsState.NOTHING && receivedChatRooms.isNotEmpty()) {

            val matchMadeStartIndex = matchesMade.size

            for (chatRoom in receivedChatRooms) {
                matchesMade.add(chatRoom)
            }

            val matchMadeNumberItemsInserted = matchesMade.size - matchMadeStartIndex

            if (0 < matchMadeNumberItemsInserted) { //if at least one element was inserted to matchesMade
                if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                    _matchMadeRangeInserted.value = EventWrapper(
                        ReturnMatchMadeRangeInsertedDataHolder(
                            matchMadeStartIndex,
                            matchMadeNumberItemsInserted
                        )
                    )
                }
            }
        }
    }

    private fun handleMatchMadeMessageReceived(
        messages: List<MessagesDataEntity>,
    ) {
        if (messages.isNotEmpty()) {

            val chatRoomId = messages[0].chatRoomId
            var indexNum = -1

            //could be a message from initial contact with match
            for (i in matchesMade.indices) {
                if (matchesMade[i].chatRoomId == chatRoomId) {
                    indexNum = i
                    break
                }
            }

            if (indexNum != -1) { //found 'matches made' chat room

                val typeOfMessage =
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                        messages[0].messageType
                    )

                if (checkIfFirstContactMessage(typeOfMessage)) { //if first contact type message
                    val removedChatRoom = matchesMade.removeAt(indexNum)

                    if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                        _matchMadeRemoved.value = EventWrapper(
                            ReturnMatchMadeRemovedDataHolder(indexNum)
                        )
                    }

                    //update the chat room before it is placed inside the new chat room
                    removedChatRoom.setNewFinalMessageIfRelevant(messages[0])

                    if (chatRoomsListFragmentsState == ChatRoomsListFragmentsState.CHAT_ROOM_LIST_FRAGMENT_STARTED) {

                        val addChatRoomReturnValue = addChatRoom(removedChatRoom)

                        if (!addChatRoomReturnValue.dataSetRequiresModified) {
                            _chatRoomAddedRemoved.value = EventWrapperWithKeyString(
                                addChatRoomReturnValue.returnChatRoomsListJoinedLeftChatRoomDataHolder,
                                chatRoomContainer.chatRoomUniqueId
                            )
                        } else {
                            _chatRoomsListUpdateDataSet.value = EventWrapper(Unit)
                        }
                    }

                } else {
                    //NOTE: USER_ACTIVITY_DETECTED is a valid message that could occur here
                }

                if (messages.size > 1) { //if more than 1 message stored (checked above to make sure it isn't empty)
                    val errorMessage =
                        "More than one message found inside of a 'match made' chat room. " +
                                "messages.size: ${messages.size}\n"

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }

            }
            //else; NOTE: the else can happen here if a message is sent to the
            // chat room between THIS_USER_JOINED_CHAT_ROOM and THIS_USER_JOINED_CHAT_ROOM_FINISHED, so no error

        }
    }

    private fun handleJoinedLeftMatchMadeChatRoomReturn(info: ReturnJoinedLeftChatRoomDataHolder) {

        if (messengerFragmentsState != MessengerFragmentsState.NOTHING) {

            val chatRoom = info.chatRoomWithMemberMap

            when (info.chatRoomUpdateMadeEnum) {
                ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED,
                ChatRoomUpdateMade.CHAT_ROOM_JOINED -> {
                    val errorMessage =
                        "Incorrect type received of " + info.chatRoomUpdateMadeEnum.toString() + " inside handleJoinedLeftMatchMadeChatRoomReturn(). " +
                                "This should be taken care of inside ChatRoomsListFragment.kt.\n" +
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

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
                ChatRoomUpdateMade.CHAT_ROOM_LEFT -> {

                    //NOTE: this can be sent back as a match or chat room type when the chat stream starts and returns an update
                    if (chatRoom.matchingChatRoomOID != "") { //if this is a match

                        var indexNum = -1

                        //NOTE: this next check in not necessary here because the chat room is sent back without members when leaving
                        //extractMemberFromChatRoomForMatchMade(chatRoom) //if this chat room has at least 1 member
                        for (i in matchesMade.indices) {

                            if (matchesMade[i].matchingChatRoomOID == chatRoom.matchingChatRoomOID) {
                                indexNum = i
                                break
                            }
                        }

                        //NOTE: the chat room not being found in the list could happen because this is called by chatRoomUniqueId and
                        // so it could be called from 1 chat room fragment and implemented here
                        if (indexNum != -1) {
                            matchesMade.removeAt(indexNum)

                            if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                                _matchMadeRemoved.value = EventWrapper(
                                    ReturnMatchMadeRemovedDataHolder(indexNum)
                                )
                            }
                        }

                    } else { //if this is a chat room
                        val errorMessage =
                            "Incorrect type received of ChatRoomUpdateMade.CHAT_ROOM_LEFT inside handleJoinedLeftMatchMadeChatRoomReturn(). " +
                                    "This should be taken care of inside ChatRoomsListFragment.kt.\n" +
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

                        storeError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }
                }
                ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH -> {

                    var indexToRemove = -1
                    for (i in matchesMade.indices) {
                        if (matchesMade[i].matchingChatRoomOID == chatRoom.matchingChatRoomOID) {

                            indexToRemove = i
                            break
                        }
                    }

                    if (indexToRemove != -1) {
                        matchesMade.removeAt(indexToRemove)

                        if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                            _matchMadeRemoved.value = EventWrapper(
                                ReturnMatchMadeRemovedDataHolder(indexToRemove)
                            )
                        }
                    } //else NOTE: else is possible if the user is say offline and the other user both matches and initiates first contact this might be able to happen

                    matchesMade.add(chatRoom)
                    if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                        _matchMadeRangeInserted.value = EventWrapper(
                            ReturnMatchMadeRangeInsertedDataHolder(
                                matchesMade.lastIndex,
                                1
                            )
                        )
                    }
                }
                ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED -> {

                    //NOTE: the chat room not being found in the list could happen because this is called by chatRoomUniqueId and
                    // so it could be called from 1 chat room fragment and implemented here
                    for (i in matchesMade.indices) {
                        if (matchesMade[i].matchingChatRoomOID == chatRoom.matchingChatRoomOID) {
                            matchesMade.removeAt(i)
                            if (messengerFragmentsState == MessengerFragmentsState.MESSENGER_FRAGMENT_STARTED) {
                                _matchMadeRemoved.value = EventWrapper(
                                    ReturnMatchMadeRemovedDataHolder(i)
                                )
                            }

                            break
                        }
                    }
                }
            }
        }
    }

    //returns index this chat room was added at
    private fun addChatRoom(chatRoomToAdd: ChatRoomWithMemberMapDataClass): AddChatRoomReturnValue {

        val duplicateChatRooms = chatRooms.filter {
            it.chatRoomId == chatRoomToAdd.chatRoomId
        }

        var dataSetModified = false

        //NOTE: this could happen because this is called by chatRoomUniqueId and so it could be
        // called from 1 chat room fragment and implemented here
        for (duplicate in duplicateChatRooms) {
            dataSetModified = true
            chatRooms.remove(duplicate)
        }

        var indexToAddAt = -1

        when (chatRoomContainer.chatRoomSortMethodSelected) {
            ChatRoomSortMethodSelected.SORT_BY_UNREAD,
            ChatRoomSortMethodSelected.SORT_BY_RECENT,
            -> {
                //sorted by chatRoomLastActivityTime in descending order
                for (i in chatRooms.indices) {
                    if (chatRooms[i].chatRoomLastActivityTime < chatRoomToAdd.chatRoomLastActivityTime) {
                        indexToAddAt = i
                        break
                    }
                }
            }
            ChatRoomSortMethodSelected.SORT_BY_VISITED -> {
                //sorted by chatRoomLastActivityTime in descending order
                for (i in chatRooms.indices) {
                    if (chatRooms[i].chatRoomLastObservedTime < chatRoomToAdd.chatRoomLastObservedTime) {
                        indexToAddAt = i
                        break
                    }
                }
            }
            ChatRoomSortMethodSelected.SORT_BY_JOINED -> {
                //sorted by timeJoined in ascending order
                for (i in chatRooms.indices) {
                    if (chatRooms[i].chatRoomLastActivityTime >= chatRoomToAdd.chatRoomLastActivityTime) {
                        indexToAddAt = i
                        break
                    }
                }
            }
        }

        if (indexToAddAt == -1) {
            chatRooms.add(chatRoomToAdd)
            indexToAddAt = chatRooms.lastIndex
        } else {
            dataSetModified = true
            chatRooms.add(indexToAddAt, chatRoomToAdd)
        }

        return AddChatRoomReturnValue(
            ReturnChatRoomsListJoinedLeftChatRoomDataHolder(
                AddedOfRemovedIndex.INDEX_ADDED,
                indexToAddAt
            ),
            dataSetModified
        )
    }

    fun checkIfUserIsInPassedChatRoom(chatRoomId: String): Boolean {
        for (chatRoom in chatRooms) {
            if (chatRoom.chatRoomId == chatRoomId) {
                return true
            }
        }

        return false
    }

    private fun storeError(
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

}