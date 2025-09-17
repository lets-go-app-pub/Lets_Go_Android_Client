package site.letsgoapp.letsgo.applicationActivityFragments

import account_state.AccountState
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import categorytimeframe.CategoryTimeFrame
import feedback_enum.FeedbackTypeEnum
import grpc_chat_commands.ChatRoomCommands
import kotlinx.coroutines.*
import report_enums.ReportMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MostRecentMessageDataHolder
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.convertOtherUsersDataEntityToOtherUserInfoWithChatRoom
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.ApplicationRepository
import site.letsgoapp.letsgo.repositories.SelectCategoriesRepository
import site.letsgoapp.letsgo.repositories.SelectPicturesRepository
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctionReturnValue
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginSupportFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities.ChatRoomContainer
import site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities.ChatRoomsListInfoContainer
import site.letsgoapp.letsgo.utilities.sharedApplicationViewModelUtilities.SendChatMessagesToFragments
import type_of_chat_message.TypeOfChatMessageOuterClass
import user_subscription_status.UserSubscriptionStatusOuterClass.UserSubscriptionStatus
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set

//Activity view model accessed and modified by login fragments
class SharedApplicationViewModel(
    private val repository: ApplicationRepository,
    private val chatStreamObject: ChatStreamObject,
    private val selectPicturesRepository: SelectPicturesRepository,
    private val selectCategoriesRepository: SelectCategoriesRepository,
    private val loginSupportFunctions: LoginSupportFunctions,
    private val errorStore: StoreErrorsInterface,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel(), ChatStreamSubscriberInterface {

    companion object {
        const val INVALID_CHAT_ROOM_ID = "invalid_*id^"
    }

    //used in requesting new matches from server

    val thisSharedApplicationViewModelInstanceId = generateViewModelInstanceId()

    //this will be passed to the fragment
    var matchesFragmentId = "~"

    var algorithmSearchOptions =
        AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY
        private set

    var categories: MutableList<CategoryTimeFrame.CategoryActivityMessage> = arrayListOf()
    private var categoriesTimestamp = -2L
    var userName = "~"
    private var userNameTimestamp = -2L
    var userGender = "~"
    private var userGenderTimestamp = -2L

    var userEmailAddress = "~"
    var userEmailAddressRequiresVerification = true
    private var userEmailAddressTimestamp = -2L

    var userBio = "~"
    var userCity = "~"
    var minAgeRange = GlobalValues.server_imported_values.lowestAllowedAge
    var maxAgeRange = GlobalValues.server_imported_values.highestAllowedAge
    var userMaxDistance = GlobalValues.server_imported_values.maximumAllowedDistance
    var userGenderRange = mutableListOf<String>()
    private var postLoginInfoTimestamp =
        -2L //must be less than -1L for initialization to always work

    var userAge = -1

    var subscriptionStatus = UserSubscriptionStatus.NO_SUBSCRIPTION
    var optedInToPromotionalEmails = false

    //This variable exists because APIs greater than 33 must request notification and location
    // permissions. When AppActivity requests that the FindMatchesObject start in onStart(), then
    // the location request that occurs will override the notification request from the
    // BlankLoadingFragment. This is only an issue the first time the app is run on a device.
    var blankLoadingFragmentCompleted = false

    var firstPictureInList =
        AccountPictureDataEntity(pictureIndex = GlobalValues.server_imported_values.numberPicturesStoredPerAccount + 1)
        private set

    //12/7 5:00->6:00
    //12/8 5:00->6:00

    val findMatchesObject = ServiceLocator.provideFindMatchesObject(
        applicationContext,
        thisSharedApplicationViewModelInstanceId
    )

    //this reference for chatRoomContainer is passed to sendChatMessagesToFragments, leave it as val so it cannot be changed
    val chatRoomContainer = ChatRoomContainer()

    data class MostRecentUpdatesMessageDataHolder(
        val mostRecentlyUpdatedMessageTimeStored: Long = -1L,
        val mostRecentlyUpdatedMessageChatRoomId: String = "~",
        val mostRecentlyUpdatedMessageUUID: String = "~",
        val mostRecentlyUpdatedMessageSentByOID: String = "~",
        val allMessagesHaveBeenObserved: Boolean? = null
    )

    var mostRecentUpdatesMessage = MostRecentUpdatesMessageDataHolder()
        private set

    var chatStreamInitialDownloadComplete = false
        private set

    private var subscriberWrapper = ChatStreamSubscriberWrapper(
        this,
        Dispatchers.Main
    )

    private fun <T> keyStringObserverFactory(block: (T) -> Unit): KeyStringObserver<T> {
        return KeyStringObserver(block, thisSharedApplicationViewModelInstanceId)
    }

    fun updateMostRecentUpdatesMessage(
        mostRecentlyUpdatedMessageTimeStored: Long,
        mostRecentlyUpdatedMessageChatRoomId: String,
        mostRecentlyUpdatedMessageUUID: String,
        mostRecentlyUpdatedMessageSentByOID: String,
        allMessagesHaveBeenObserved: Boolean
    ) {
        mostRecentUpdatesMessage = MostRecentUpdatesMessageDataHolder(
            mostRecentlyUpdatedMessageTimeStored,
            mostRecentlyUpdatedMessageChatRoomId,
            mostRecentlyUpdatedMessageUUID,
            mostRecentlyUpdatedMessageSentByOID,
            allMessagesHaveBeenObserved
        )
    }

    val chatRoomsListInfoContainer = ChatRoomsListInfoContainer(
        chatRoomContainer,
        getMostRecentMessageInChatRoom = { chatRoomId, chatRoomLastActiveTime, chatRoomTimeJoined ->
            withContext(ioDispatcher) {
                //accesses the database, must get off the main thread for this.
                getMostRecentMessageInChatRoom(
                    chatRoomId,
                    chatRoomLastActiveTime,
                    chatRoomTimeJoined
                )
            }
        },
        updateChatRoomsSortedType = { chatRoomSortMethodSelected ->
            CoroutineScope(ioDispatcher).launch {
                updateChatRoomsSortedType(chatRoomSortMethodSelected)
            }
        },
        errorStore
    )

    val sendChatMessagesToFragments =
        SendChatMessagesToFragments(chatRoomContainer, chatRoomsListInfoContainer)

    //used with functions
    //returnJoinedLeftChatRoom
    var currentFragmentInstanceId = "~"
        set(value) {
            repository.mostRecentFragmentInstanceID = value
            field = value
        }

    private var postLoginDisplayedConnectionError = false
    private var numberOfPostLoginInfoThatWasSet = 0
    private var setPostLoginInfoReturnedTimestamps = arrayListOf<Long>()

    //this is because when navigating, the onCreateView of the match screen is called before the onPause
    // of the set functions that modify algorithm parameters, this puts the algorithm on cool down
    var doNotRunOnCreateViewInMatchScreenFragment = false

    private data class ChatRoomAmountOfMessage(
        val chatRoomId: String,
        val amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage
    )

    // it is possible to receive the same message UUID when it does not exist in the list
    // 1) full message info is requested
    // 2) user leaves chat room then rejoins same chat room
    // 3) same message info is requested
    // 4) message info for #1 is returned and processed (and removed)
    // 5) message info for #2 is returned and processed (but doesn't exist)
    // Ideally to avoid problems like this store a marker in the shared view model instead
    // of the fragment (or both) to take care of this problem
    //NOTE: the messages being requested are also kept track of inside of chatRoomFragment using the
    // messageUpdateHasBeenRequestedFromServer field inside MessagesDataEntityWithAdditionalInfo
    private val messagesBeingRequestedSet =
        mutableMapOf<String, ChatRoomAmountOfMessage>()

    private val applicationViewModelUUID = UUID.randomUUID()

    private fun removeChatRoomFromRequestedMessages(chatRoomId: String) {

        val messageUUIDList = mutableListOf<String>()

        for (message in messagesBeingRequestedSet) {
            if (message.value.chatRoomId == chatRoomId) {
                messageUUIDList.add(message.key)
            }
        }

        for (messageUUID in messageUUIDList) {
            messagesBeingRequestedSet.remove(messageUUID)
        }
    }

    override suspend fun clientMessageToServerReturnValue(
        returnsClientMessageToServerReturnValue: ClientMessageToServerReturnValueDataHolder,
    ) {
        if (returnsClientMessageToServerReturnValue.errorStatusEnum == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            if (
                returnsClientMessageToServerReturnValue.message.chatRoomId == chatRoomContainer.chatRoom.chatRoomId
            ) { //if a matching chat room is stored and this is a first contact message for this chat room
                chatRoomContainer.removeChatRoomFromMatchingState(
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                        returnsClientMessageToServerReturnValue.message.messageType
                    )
                )
            }

            val messageType =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    returnsClientMessageToServerReturnValue.message.messageType
                )

            returnsClientMessageToServerReturnValue.message.messageObservedTime.let { observedTime ->
                if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE
                ) { //if this message updated the observed time AND the chat room observed time is less than the passed time

                    //update last observed time
                    chatRoomContainer.updateChatRoomLastObservedTime(observedTime)
                } else if (observedTime != -1L) {

                    //in chatRoomCommandsRPCs the observedTime is set to -1L at the start and if it is a message that updates
                    // the observed time and the chat room last activity time it will be set to the returned timestamp
                    chatRoomContainer.updateChatRoomLastObservedTime(observedTime)

                    chatRoomContainer.updateChatRoomLastActiveTimeForMessageType(
                        messageType,
                        observedTime
                    )
                }
            }

            sendChatMessagesToFragments.sendMessageResponsesBackToFragments(
                returnsClientMessageToServerReturnValue.message,
                returnsClientMessageToServerReturnValue.fragmentInstanceID
            )
        }

        _returnGrpcFunctionErrorStatusEnumToActivity.value =
            EventWrapperWithKeyString(
                GrpcFunctionErrorStatusReturnValues(
                    returnsClientMessageToServerReturnValue.errorStatusEnum,
                    turnOffLoading = false
                ),
                thisSharedApplicationViewModelInstanceId
            )
    }

    private val returnMessagesForChatRoomObserver =
        keyStringObserverFactory<DataHolderWrapper<ReturnMessagesForChatRoomDataHolder>> { result ->
            processMessagesForChatRoom(
                result.dataHolder,
                result.fragmentInstanceId
            )
        }

    override suspend fun returnMessagesForChatRoom(
        returnsReturnMessagesForChatRoom: ReturnMessagesForChatRoomDataHolder,
    ) {
        processMessagesForChatRoom(
            returnsReturnMessagesForChatRoom,
            currentFragmentInstanceId
        )
    }

    override suspend fun returnMessageWithMemberForChatRoom(
        returnsReturnMessageWithMemberForChatRoom: ReturnMessageWithMemberForChatRoomDataHolder,
    ) {

        val messageType =
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                returnsReturnMessageWithMemberForChatRoom.message.messageType
            )

        returnsReturnMessageWithMemberForChatRoom.otherUserInfo
        if (returnsReturnMessageWithMemberForChatRoom.message.chatRoomId == chatRoomContainer.chatRoom.chatRoomId) { //chat message is for this chat room
            when (messageType) {
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                -> {

                    //update chatRoomLastActivityTime
                    returnsReturnMessageWithMemberForChatRoom.message.messageStoredOnServerTime.apply {
                        chatRoomContainer.updateChatRoomLastActiveTime(this)
                    }

                    //NOTE: DIFFERENT_USER_LEFT_CHAT_ROOM will only return the account status and account OID

                    //NOTE: values for members can never be removed because they are needed to reference the user name and thumbnail
                    // so just changing the status
                    val insertResult =
                        chatRoomContainer.chatRoom.chatRoomMembers.updateAccountStateByAccountOID(
                            returnsReturnMessageWithMemberForChatRoom.otherUserInfo
                        )

                    if (insertResult.first) { //if update succeeded
                        _returnUpdatedChatRoomMember.value = EventWrapperWithKeyString(
                            ReturnUpdatedChatRoomMemberDataHolder(
                                returnsReturnMessageWithMemberForChatRoom.message.chatRoomId,
                                insertResult.second,
                                TypeOfUpdatedOtherUser.OTHER_USER_UPDATED
                            ),
                            chatRoomContainer.chatRoomUniqueId
                        )
                    } else {
                        val errorMessage =
                            "Failed to find userAccountOID when returning an account" +
                                    " State to be updated inside returnMessageWithMemberForChatRoom().\n" +
                                    "messageType: $messageType\n" +
                                    "accountOID: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.accountOID}\n" +
                                    "newAccountState: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.chatRoom.accountStateInChatRoom}\n" +
                                    "chatRoomId: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.chatRoom.chatRoomId}\n" +
                                    "insertResult: $insertResult\n"

                        sendSharedApplicationError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber
                        )

                        return
                    }

                }
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {

                    //update chatRoomLastActivityTime
                    returnsReturnMessageWithMemberForChatRoom.message.messageStoredOnServerTime.apply {
                        chatRoomContainer.updateChatRoomLastActiveTime(this)
                    }

                    //do not update the map if current user passed back
                    if (returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.accountOID != LoginFunctions.currentAccountOID) {
                        val upsertReturnValue =
                            chatRoomContainer.chatRoom.chatRoomMembers.upsertAnElementByAccountOID(
                                returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.accountOID,
                                returnsReturnMessageWithMemberForChatRoom.otherUserInfo
                            )

                        _returnUpdatedChatRoomMember.value = EventWrapperWithKeyString(
                            ReturnUpdatedChatRoomMemberDataHolder(
                                returnsReturnMessageWithMemberForChatRoom.message.chatRoomId,
                                upsertReturnValue.index,
                                if (upsertReturnValue.userAlreadyExisted) TypeOfUpdatedOtherUser.OTHER_USER_UPDATED else TypeOfUpdatedOtherUser.OTHER_USER_JOINED
                            ),
                            chatRoomContainer.chatRoomUniqueId
                        )
                    }
                }
                else -> {
                    val errorMessage =
                        "Invalid message type sent to returnMessageWithMemberForChatRoom().\n" +
                                "messageType: $messageType\n" +
                                "accountOID: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.otherUsersDataEntity.accountOID}\n" +
                                "newAccountState: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.chatRoom.accountStateInChatRoom}\n" +
                                "chatRoomId: ${returnsReturnMessageWithMemberForChatRoom.otherUserInfo.chatRoom.chatRoomId}\n"

                    sendSharedApplicationError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber
                    )

                    return
                }
            }

            sendChatMessagesToFragments.sendNewMessagesBackToFragments(
                ReturnMessagesForChatRoomDataHolder(
                    false,
                    returnsReturnMessageWithMemberForChatRoom.message.chatRoomId,
                    listOf(returnsReturnMessageWithMemberForChatRoom.message),
                    mutableListOf()
                ),
                currentFragmentInstanceId
            )
        } else { //chat message is not for this chat room
            chatRoomsListInfoContainer.updatedChatRoomMember(
                returnsReturnMessageWithMemberForChatRoom
            )
        }

        //update observed time if relevant
        if (
            returnsReturnMessageWithMemberForChatRoom.message.messageStoredOnServerTime != -1L
            && displayBlockedMessage(
                returnsReturnMessageWithMemberForChatRoom.message.sentByAccountID,
                returnsReturnMessageWithMemberForChatRoom.message.messageType
            )
        ) {

            Log.i("messagesStuff", "update came from observed time")
            _allChatRoomMessagesHaveBeenObservedResults.value = EventWrapper(
                AllChatRoomMessagesHaveBeenObservedHolder(
                    allMessagesHaveBeenObserved = false,
                    INVALID_CHAT_ROOM_ID,
                    returnsReturnMessageWithMemberForChatRoom.message.messageUUIDPrimaryKey,
                    returnsReturnMessageWithMemberForChatRoom.message.sentByAccountID,
                    returnsReturnMessageWithMemberForChatRoom.message.messageStoredOnServerTime,
                    returnsReturnMessageWithMemberForChatRoom.message.chatRoomId
                )
            )
        }
    }

    private val _matchRemovedOnJoinChatRoom: MutableLiveData<EventWrapperWithKeyString<ReturnMatchRemovedOnJoinChatRomDataHolder>> =
        MutableLiveData()
    val matchRemovedOnJoinChatRoom: LiveData<EventWrapperWithKeyString<ReturnMatchRemovedOnJoinChatRomDataHolder>> =
        _matchRemovedOnJoinChatRoom
    private val matchRemovedOnJoinChatRoomObserver =
        keyStringObserverFactory<ReturnMatchRemovedOnJoinChatRomDataHolder> { result ->
            CoroutineScope(ioDispatcher).launch {
                val matchExisted = findMatchesObject.removeMatch(
                    result.matchAccountOid,
                )

                if (matchExisted) {
                    withContext(Dispatchers.Main) {
                        _matchRemovedOnJoinChatRoom.value = EventWrapperWithKeyString(
                            result,
                            currentFragmentInstanceId
                        )
                    }
                }
            }
        }

    private val _returnAccountStateUpdated: MutableLiveData<EventWrapperWithKeyString<AccountStateUpdatedDataHolder>> =
        MutableLiveData()
    val returnAccountStateUpdated: LiveData<EventWrapperWithKeyString<AccountStateUpdatedDataHolder>> =
        _returnAccountStateUpdated
    private val returnAccountStateUpdatedObserver =
        keyStringObserverFactory<AccountStateUpdatedDataHolder> { result ->
            processAccountStateUpdated(result)
        }

    private val _returnChatRoomEventOidUpdated: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val returnChatRoomEventOidUpdated: LiveData<EventWrapperWithKeyString<Unit>> =
        _returnChatRoomEventOidUpdated
    private val returnChatRoomEventOidUpdatedObserver =
        keyStringObserverFactory<ReturnChatRoomEventOidUpdated> { result ->
            processEventOidUpdated(result)
        }

    private val _returnQrInfoUpdated: MutableLiveData<EventWrapperWithKeyString<ReturnQrCodeInfoUpdated>> =
        MutableLiveData()
    val returnQrInfoUpdated: LiveData<EventWrapperWithKeyString<ReturnQrCodeInfoUpdated>> =
        _returnQrInfoUpdated
    private val returnQrInfoUpdatedObserver =
        keyStringObserverFactory<ReturnQrCodeInfoUpdated> { result ->
            processQrCodeInfoUpdated(result)
        }

    override suspend fun returnAccountStateUpdated(
        accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder,
    ) {
        processAccountStateUpdated(accountStateUpdatedDataHolder)
    }

    private val _returnJoinedLeftChatRoom: MutableLiveData<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>> =
        MutableLiveData()
    val returnJoinedLeftChatRoom: LiveData<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>> =
        _returnJoinedLeftChatRoom

    override suspend fun returnJoinedLeftChatRoom(
        returnJoinedLeftChatRoomDataHolder: ReturnJoinedLeftChatRoomDataHolder,
    ) {

        if (
            returnJoinedLeftChatRoomDataHolder.chatRoomUpdateMadeEnum == ChatRoomUpdateMade.CHAT_ROOM_LEFT
            || returnJoinedLeftChatRoomDataHolder.chatRoomUpdateMadeEnum == ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED
        ) {
            //if this user left a chat room, need to re-calculate if there
            // are any messages not observed
            checkIfAllChatRoomMessagesHaveBeenObserved(returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.chatRoomId)
        }

        if (!chatRoomsListInfoContainer.chatRoomListsFragmentActive()) {

            //If an event chat room was joined, make sure to remove it if it exists as a match.
            if (
                (returnJoinedLeftChatRoomDataHolder.chatRoomUpdateMadeEnum == ChatRoomUpdateMade.CHAT_ROOM_JOINED
                || returnJoinedLeftChatRoomDataHolder.chatRoomUpdateMadeEnum == ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED)
                && returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.eventId != GlobalValues.server_imported_values.eventIdDefault
            ) {
                _matchRemovedOnJoinChatRoom.value =
                    EventWrapperWithKeyString(
                        ReturnMatchRemovedOnJoinChatRomDataHolder(
                            returnJoinedLeftChatRoomDataHolder.chatRoomWithMemberMap.eventId
                        ),
                        currentFragmentInstanceId
                    )
            }

            _returnJoinedLeftChatRoom.value =
                EventWrapperWithKeyString(
                    returnJoinedLeftChatRoomDataHolder,
                    chatRoomContainer.chatRoomUniqueId
                )
        } else {
            chatRoomsListInfoContainer.joinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder)
        }
    }

    //Pair<chatRoomId, typeOfChatMessage>
    private val _returnChatRoomInfoUpdatedData: MutableLiveData<EventWrapperWithKeyString<UpdateChatRoomInfoResultsDataHolder>> =
        MutableLiveData()
    val returnChatRoomInfoUpdatedData: LiveData<EventWrapperWithKeyString<UpdateChatRoomInfoResultsDataHolder>> =
        _returnChatRoomInfoUpdatedData

    //Triple<chatRoomId, typeOfChatMessage, newChatRoomInfo>
    private val returnChatRoomInfoUpdatedObserver =
        keyStringObserverFactory<UpdateChatRoomInfoResultsDataHolder> { result ->
            processChatRoomInfoUpdated(result)
        }

    override suspend fun returnChatRoomInfoUpdated(
        updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder,
    ) {
        processChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder)
    }

    private val _returnKickedBannedFromChatRoom: MutableLiveData<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>> =
        MutableLiveData()
    val returnKickedBannedFromChatRoom: LiveData<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>> =
        _returnKickedBannedFromChatRoom

    override suspend fun returnKickedBannedFromChatRoom(
        returnKickedBannedFromChatRoomDataHolder: ReturnKickedBannedFromChatRoomDataHolder,
    ) {

        //if this user was kicked or banned from a chat room, need to re-calculate if there
        // are any messages not observed
        checkIfAllChatRoomMessagesHaveBeenObserved(returnKickedBannedFromChatRoomDataHolder.chatRoomId)

        removeChatRoomFromRequestedMessages(returnKickedBannedFromChatRoomDataHolder.chatRoomId)

        if (!chatRoomsListInfoContainer.chatRoomListsFragmentActive()) { //if chat room lists fragment is NOT active
            _returnKickedBannedFromChatRoom.value =
                EventWrapperWithKeyString(
                    returnKickedBannedFromChatRoomDataHolder,
                    chatRoomContainer.chatRoomUniqueId
                )
        } else { //if chat room lists fragment is active
            chatRoomsListInfoContainer.kickedBannedFromChatRoom(
                returnKickedBannedFromChatRoomDataHolder
            )
        }
    }

    override suspend fun receivedMessageUpdateRequestResponse(
        returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder,
    ) {

        //remove any message updates that have completed
        if (returnMessageUpdateRequestResponseDataHolder.amountOfMessageRequested == TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO
            || messagesBeingRequestedSet[returnMessageUpdateRequestResponseDataHolder.message.messageUUIDPrimaryKey]?.amountOfMessage == returnMessageUpdateRequestResponseDataHolder.amountOfMessageRequested
        ) {
            messagesBeingRequestedSet.remove(returnMessageUpdateRequestResponseDataHolder.message.messageUUIDPrimaryKey)
        }

        sendChatMessagesToFragments.receivedMessageUpdateRequestResponse(
            returnMessageUpdateRequestResponseDataHolder,
            currentFragmentInstanceId,
        )
    }

    private val _returnChatStreamInitialDownloadsCompleted: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val returnChatStreamInitialDownloadsCompleted: LiveData<EventWrapper<Unit>> =
        _returnChatStreamInitialDownloadsCompleted

    override suspend fun chatStreamInitialDownloadsCompleted() {
        chatStreamInitialDownloadComplete = true
        _returnChatStreamInitialDownloadsCompleted.value = EventWrapper(Unit)
    }

    //NOTE: this uses the sharedApplicationInstanceId not the fragmentInstanceId
    private val _returnGrpcFunctionErrorStatusEnumToActivity: MutableLiveData<EventWrapperWithKeyString<GrpcFunctionErrorStatusReturnValues>> =
        MutableLiveData()
    val returnGrpcFunctionErrorStatusEnumToActivity: LiveData<EventWrapperWithKeyString<GrpcFunctionErrorStatusReturnValues>> =
        _returnGrpcFunctionErrorStatusEnumToActivity
    private val returnGrpcFunctionErrorStatusEnumToActivityObserver =
        keyStringObserverFactory<GrpcFunctionErrorStatusEnum> { result ->
            _returnGrpcFunctionErrorStatusEnumToActivity.value = EventWrapperWithKeyString(
                GrpcFunctionErrorStatusReturnValues(
                    result,
                    turnOffLoading = false
                ),
                thisSharedApplicationViewModelInstanceId
            )
        }

    override suspend fun gRPCErrorOccurred(
        error: GrpcFunctionErrorStatusEnum,
    ) {
        Log.i(
            "gRPCErrorOccurred",
            "gRPCErrorOccurred() inside SharedApplicationViewModel from ChatStreamObject error: $error\n${
                Log.getStackTraceString(
                    Exception()
                )
            }"
        )

        _returnGrpcFunctionErrorStatusEnumToActivity.value = EventWrapperWithKeyString(
            GrpcFunctionErrorStatusReturnValues(
                error,
                turnOffLoading = false
            ),
            thisSharedApplicationViewModelInstanceId
        )
    }

    private val _setFirstPictureReturnValue: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val setFirstPictureReturnValue: LiveData<EventWrapper<Unit>> =
        _setFirstPictureReturnValue
    private val setFirstPictureReturnValueObserver =
        keyStringObserverFactory<AccountPictureDataEntity> { result ->
            firstPictureInList = result
            _setFirstPictureReturnValue.value = EventWrapper(Unit)
        }

    private val setCategoriesUpdatedForViewModelObserver =
        keyStringObserverFactory<SetCategoriesUpdatedForViewModelDataHolder> { result ->
            if (result.categoriesTimestamp > categoriesTimestamp) {
                categories = result.updatedCategories
                categoriesTimestamp = result.categoriesTimestamp
            }
        }

    private val _modifyLocationAddressText: MutableLiveData<EventWrapperWithKeyString<String>> =
        MutableLiveData()
    val modifyLocationAddressText: LiveData<EventWrapperWithKeyString<String>> =
        _modifyLocationAddressText

    fun setLocationAddressText(address: String) {
        _modifyLocationAddressText.value =
            EventWrapperWithKeyString(address, currentFragmentInstanceId)
    }

    private val _setEmailReturnValue: MutableLiveData<EventWrapper<SetFieldReturnValues>> =
        MutableLiveData()
    val setEmailReturnValue: LiveData<EventWrapper<SetFieldReturnValues>> =
        _setEmailReturnValue
    private val setEmailReturnValueObserver =
        keyStringObserverFactory<SetEmailReturnValues> { result ->

            if (result.setFieldsReturnValues.invalidParameterPassed) { //server returned invalid
                _setEmailReturnValue.value =
                    EventWrapper(SetFieldReturnValues.INVALID_VALUE)
            } else if (result.setFieldsReturnValues.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                userEmailAddress = result.emailAddress
                userEmailAddressRequiresVerification = true
                _setEmailReturnValue.value =
                    EventWrapper(SetFieldReturnValues.SUCCESSFUL)
            }

            _returnGrpcFunctionErrorStatusEnumToActivity.value =
                EventWrapperWithKeyString(
                    GrpcFunctionErrorStatusReturnValues(
                        result.setFieldsReturnValues.errorStatus,
                        turnOffLoading = false
                    ),
                    thisSharedApplicationViewModelInstanceId
                )

        }

    private val _modifyingValueFailedError: MutableLiveData<EventWrapper<String>> =
        MutableLiveData()
    val modifyingValueFailedError: LiveData<EventWrapper<String>> =
        _modifyingValueFailedError

    private val _serverReturnedInvalidParameterError: MutableLiveData<EventWrapper<String>> =
        MutableLiveData()
    val serverReturnedInvalidParameterError: LiveData<EventWrapper<String>> =
        _serverReturnedInvalidParameterError

    private val setGenderReturnValueObserver =
        keyStringObserverFactory<SetGenderReturnValues> {

            when {
                it.invalidParameterPassed -> { //server returned invalid value
                    _serverReturnedInvalidParameterError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.gender))
                }
                it.errors == GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //successfully validated and stored value
                    userGender = it.gender
                    userGenderTimestamp = it.updatedTimestamp
                }
                it.errors != GrpcFunctionErrorStatusEnum.DO_NOTHING -> { //data failed to update
                    _modifyingValueFailedError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.gender))
                }
            }

            _returnGrpcFunctionErrorStatusEnumToActivity.value =
                EventWrapperWithKeyString(
                    GrpcFunctionErrorStatusReturnValues(
                        it.errors,
                        turnOffLoading = false
                    ),
                    thisSharedApplicationViewModelInstanceId
                )
        }

    private val setBioReturnValueObserver =
        keyStringObserverFactory<SetBioReturnValues> {
            handlePostLoginSetValues(
                it.invalidParameterPassed,
                it.errors,
                it.updatedTimestamp,
                R.string.bio
            ) {
                userBio = it.bio
            }
        }

    private val setCityReturnValueObserver =
        keyStringObserverFactory<SetCityReturnValues> {
            handlePostLoginSetValues(
                it.invalidParameterPassed,
                it.errors,
                it.updatedTimestamp,
                R.string.city
            ) {
                userCity = it.city
            }
        }

    private val setAgeRangeReturnValueObserver =
        keyStringObserverFactory<SetAgeRangeReturnValues> {
            handlePostLoginSetValues(
                it.invalidParameterPassed,
                it.errors,
                it.updatedTimestamp,
                R.string.age_range
            ) {
                minAgeRange = it.ageRange.minAgeRange

                maxAgeRange =
                    if (maxAgeRange > GlobalValues.server_imported_values.highestDisplayedAge) {
                        GlobalValues.server_imported_values.highestDisplayedAge
                    } else {
                        it.ageRange.maxAgeRange
                    }
            }
        }

    private val setMaxDistanceReturnValueObserver =
        keyStringObserverFactory<SetMaxDistanceReturnValues> {
            handlePostLoginSetValues(
                it.invalidParameterPassed,
                it.errors,
                it.updatedTimestamp,
                R.string.max_distance
            ) {
                userMaxDistance = it.maxDistance
            }
        }

    private val setGenderRangeReturnValueObserver =
        keyStringObserverFactory<SetGenderRangeReturnValue> {
            handlePostLoginSetValues(
                it.invalidParameterPassed,
                it.errors,
                it.updatedTimestamp,
                R.string.genders_to_match
            ) {
                userGenderRange = convertStringToGenderRange(it.genderRange)
            }
        }

    private val _applicationAccountInfo: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val applicationAccountInfo: LiveData<EventWrapperWithKeyString<Unit>> = _applicationAccountInfo
    private val returnApplicationAccountInfoObserver =
        keyStringObserverFactory<ApplicationAccountInfoDataHolder> { result ->
            setupAccountInfo(result)
            _applicationAccountInfo.value =
                EventWrapperWithKeyString(Unit, result.fragmentInstanceId)
        }

    private val _returnEmailVerificationReturnValue: MutableLiveData<EventWrapperWithKeyString<EmailVerificationReturnValues>> =
        MutableLiveData()
    val returnEmailVerificationReturnValue: LiveData<EventWrapperWithKeyString<EmailVerificationReturnValues>> =
        _returnEmailVerificationReturnValue
    private val returnEmailVerificationReturnValueObserver =
        keyStringObserverFactory<EmailVerificationReturnValues> { result ->
            when (result.errors) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                    if (result.response.emailAddressIsAlreadyVerified) {
                        userEmailAddressRequiresVerification = false
                    }
                }
                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
                GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE,
                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                GrpcFunctionErrorStatusEnum.SERVER_DOWN,
                GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY,
                GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED,
                GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED,
                GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION,
                -> {
                    _returnGrpcFunctionErrorStatusEnumToActivity.value =
                        EventWrapperWithKeyString(
                            GrpcFunctionErrorStatusReturnValues(
                                result.errors,
                                turnOffLoading = false
                            ),
                            thisSharedApplicationViewModelInstanceId
                        )
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    return@keyStringObserverFactory
                }
            }

            _returnEmailVerificationReturnValue.value = EventWrapperWithKeyString(
                result,
                result.callingFragmentInstanceId
            )
        }

    private val returnAllChatRoomsObserver =
        keyStringObserverFactory<ReturnAllChatRoomsDataHolder> { result ->
            chatRoomsListInfoContainer.returnAllChatRooms(
                result.chatRoomsList,
                result.typeOfFragmentCalledFrom
            )
        }

    private val _returnSingleChatRoom: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val returnSingleChatRoom: LiveData<EventWrapperWithKeyString<Unit>> = _returnSingleChatRoom
    private val returnSingleChatRoomObserver =
        keyStringObserverFactory<ReturnSingleChatRoomDataHolder> { result ->
            chatRoomContainer.setChatRoomInfo(result.chatRoomWithMemberMapDataClass)
            _returnSingleChatRoom.value = EventWrapperWithKeyString(Unit, result.fragmentInstanceID)
        }

    private val _returnSingleChatRoomNotFound: MutableLiveData<EventWrapperWithKeyString<String>> =
        MutableLiveData()
    val returnSingleChatRoomNotFound: LiveData<EventWrapperWithKeyString<String>> =
        _returnSingleChatRoomNotFound
    private val returnSingleChatRoomNotFoundObserver =
        keyStringObserverFactory<ReturnSingleChatRoomNotFoundDataHolder> { result ->
            _returnSingleChatRoomNotFound.value =
                EventWrapperWithKeyString(result.chatRoomId, result.fragmentInstanceID)
        }

    //This uses an EventWrapper instead of a FragmentIDEventWrapper because a FragmentIDEventWrapper could cause
    // 1) current user is on chatRoomMemberFragment and an update is requested for the other user
    // 2) current user uses back to navigate to chatRoomInfoFragment
    // 3) current user clicks on the same other user before the updateSingleChatRoomMemberInfo can return
    // 4) because FragmentIDEventWrapper is different the update would not be able to be received
    // also the worst that should happen is it loads an extra time because an update is received that has already
    // occurred
    private val _returnUpdatedChatRoomUser: MutableLiveData<EventWrapper<ReturnUpdatedOtherUserDataHolder>> =
        MutableLiveData()
    val returnUpdatedChatRoomUser: LiveData<EventWrapper<ReturnUpdatedOtherUserDataHolder>> =
        _returnUpdatedChatRoomUser
    private val _returnUpdatedMatchUser: MutableLiveData<EventWrapper<OtherUsersDataEntity>> =
        MutableLiveData()
    val returnUpdatedMatchUser: LiveData<EventWrapper<OtherUsersDataEntity>> =
        _returnUpdatedMatchUser
    private val returnUpdatedOtherUserObserver =
        keyStringObserverFactory<ReturnUpdatedOtherUserRepositoryDataHolder> { result ->

            //NOTE: this is returned from 2 places
            // 1) updateSingleChatRoomMemberInfo() which is called initially from UserInfoCard
            // 2) updateChatRoom() which is called when clicking on a chat room the user is already a member of

            if (!chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()) { //This means a match was requested
                _returnUpdatedMatchUser.value = EventWrapper(
                    result.otherUser
                )

                findMatchesObject.updateMatchOtherUserDataEntity(result.otherUser)
            } else {

                val otherUsersInfo = convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(
                    result.otherUser,
                    chatRoomContainer.chatRoom.chatRoomId
                )

                otherUsersInfo?.let { userInfo ->

                    //no need to check for chatRoomId, should update regardless
                    val upsertMemberReturnValue =
                        chatRoomContainer.chatRoom.chatRoomMembers.upsertAnElementByAccountOID(
                            result.otherUser.accountOID,
                            userInfo
                        )

                    //send a copy to matches object for it to check if an update is required
                    //NOTE: this is called here so that if an update to a user is requested, then the user navigates
                    // away from the findMatches screen, the matches list will still be checked
                    findMatchesObject.updateMatchOtherUserDataEntity(userInfo.otherUsersDataEntity)

                    if (upsertMemberReturnValue.index != -1) { //if the account was found and updated
                        _returnUpdatedChatRoomUser.value = EventWrapper(
                            ReturnUpdatedOtherUserDataHolder(
                                userInfo,
                                result.anExistingThumbnailWasUpdated,
                                upsertMemberReturnValue.index
                            )
                        )
                    }
                }
            }
        }

    private val _returnUpdatedChatRoomMember: MutableLiveData<EventWrapperWithKeyString<ReturnUpdatedChatRoomMemberDataHolder>> =
        MutableLiveData()
    val returnUpdatedChatRoomMember: LiveData<EventWrapperWithKeyString<ReturnUpdatedChatRoomMemberDataHolder>> =
        _returnUpdatedChatRoomMember

    //used by updateChatRoom to update members that did not directly NEED any info updated
    private val updatePicturesUpdateAttemptedTimestampByAccountOIDsObserver =
        keyStringObserverFactory<UpdatePicturesUpdateAttemptedTimestampByAccountOIDsDataHolder> { result ->
            if (result.chatRoomId == chatRoomContainer.chatRoom.chatRoomId) {
                chatRoomContainer.chatRoom.chatRoomMembers.updatePicturesUpdateAttemptedTimestampByAccountOIDs(
                    result.accountOIDs,
                    result.timestamp
                )
            }
        }

    private val _returnCreatedChatRoom: MutableLiveData<EventWrapperWithKeyString<Boolean>> =
        MutableLiveData()
    val returnCreatedChatRoom: LiveData<EventWrapperWithKeyString<Boolean>> = _returnCreatedChatRoom
    private val returnCreatedChatRoomObserver =
        keyStringObserverFactory<CreatedChatRoomReturnValueDataHolder> { result ->
            val noErrors = result.errorStatusEnum == GrpcFunctionErrorStatusEnum.NO_ERRORS
            if (noErrors) chatRoomContainer.setChatRoomInfo(result.chatRoomWithMemberMapDataClass)

            _returnCreatedChatRoom.value =
                EventWrapperWithKeyString(noErrors, result.fragmentInstanceID)

            _returnGrpcFunctionErrorStatusEnumToActivity.value =
                EventWrapperWithKeyString(
                    GrpcFunctionErrorStatusReturnValues(
                        result.errorStatusEnum,
                        turnOffLoading = false
                    ),
                    thisSharedApplicationViewModelInstanceId
                )
        }

    private val _disableLoading: MutableLiveData<EventWrapperWithKeyString<Int>> =
        MutableLiveData()
    val disableLoading: LiveData<EventWrapperWithKeyString<Int>> = _disableLoading

    private val _returnLeaveChatRoomResult: MutableLiveData<EventWrapper<String>> =
        MutableLiveData()
    val returnLeaveChatRoomResult: LiveData<EventWrapper<String>> =
        _returnLeaveChatRoomResult
    private val returnLeaveChatRoomResultObserver =
        keyStringObserverFactory<LeaveChatRoomReturnDataHolder> { result ->

            if (result.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                //Because leaveChatRoom can be called from ChatRoomsListFragment, it.chatRoomId is
                // irrelevant in the case that ChatRoomListsFragment is active.
                if (chatRoomsListInfoContainer.chatRoomListsFragmentActive()) {
                    chatRoomsListInfoContainer.leaveChatRoom(result.chatRoomId)
                    checkIfAllChatRoomMessagesHaveBeenObserved(result.chatRoomId)
                } else if (result.chatRoomId == chatRoomContainer.chatRoom.chatRoomId) {
                    _returnLeaveChatRoomResult.value =
                        EventWrapper(result.chatRoomId)
                }

                removeChatRoomFromRequestedMessages(result.chatRoomId)

            } else { //some kind of error occurred
                //remove the chat room loading symbol
                _disableLoading.value = EventWrapperWithKeyString(
                    chatRoomsListInfoContainer.removeChatRoomFromLoading(result.chatRoomId),
                    chatRoomContainer.chatRoomUniqueId
                )
            }

            _returnGrpcFunctionErrorStatusEnumToActivity.value =
                EventWrapperWithKeyString(
                    GrpcFunctionErrorStatusReturnValues(
                        result.errorStatus,
                        turnOffLoading = false
                    ),
                    thisSharedApplicationViewModelInstanceId
                )
        }

    private val _returnJoinChatRoomResult: MutableLiveData<EventWrapperWithKeyString<JoinChatRoomReturnValues>> =
        MutableLiveData()
    val returnJoinChatRoomResult: LiveData<EventWrapperWithKeyString<JoinChatRoomReturnValues>> =
        _returnJoinChatRoomResult
    private val returnJoinChatRoomResultObserver =
        keyStringObserverFactory<DataHolderWrapper<JoinChatRoomReturnValues>> { result ->
            //NOTE: this LiveData returns errors that occurred when joinChatRoom is called
            if (result.dataHolder.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {

                var unmanageableErrorOccurred = false

                when (result.dataHolder.chatRoomStatus) {
                    ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED,
                    ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST,
                    ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_ID,
                    ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_PASSWORD,
                    ChatRoomCommands.ChatRoomStatus.USER_TOO_YOUNG_FOR_CHAT_ROOM,
                    -> { /*handled in fragments*/
                    }
                    //should be handled before the liveData is called inside ApplicationRepository
                    ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED,
                    ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM,
                    ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED,
                    -> {

                        val errorString =
                            "SUCCESSFULLY_JOINED & ALREADY_IN_CHAT_ROOM should be already handled in ApplicationRepository.kt," +
                                    "and UNRECOGNIZED with NO_ERRORS should never happen"

                        sendSharedApplicationError(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber
                        )

                        unmanageableErrorOccurred = true
                    }
                }

                if (!unmanageableErrorOccurred) { //if can manage error
                    _returnJoinChatRoomResult.value =
                        EventWrapperWithKeyString(
                            result.dataHolder,
                            result.fragmentInstanceId
                        )
                } else { //if cannot manage error
                    _returnGrpcFunctionErrorStatusEnumToActivity.value =
                        EventWrapperWithKeyString(
                            GrpcFunctionErrorStatusReturnValues(
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                turnOffLoading = true
                            ),
                            thisSharedApplicationViewModelInstanceId
                        )
                }
            } else {

                //must turn off the loading dialog here, it WILL automatically cancel however it
                // takes a little while
                _returnGrpcFunctionErrorStatusEnumToActivity.value =
                    EventWrapperWithKeyString(
                        GrpcFunctionErrorStatusReturnValues(
                            result.dataHolder.errorStatus,
                            turnOffLoading = true
                        ),
                        thisSharedApplicationViewModelInstanceId
                    )
            }
        }

    private val _returnBlockReportChatRoomResult: MutableLiveData<EventWrapperWithKeyString<BlockAndReportChatRoomResultsHolder>> =
        MutableLiveData()
    val returnBlockReportChatRoomResult: LiveData<EventWrapperWithKeyString<BlockAndReportChatRoomResultsHolder>> =
        _returnBlockReportChatRoomResult
    private val returnBlockReportChatRoomResultObserver =
        keyStringObserverFactory<BlockAndReportChatRoomResultsHolder> { result ->
            if (result.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {

                //While this block is needed in theory to update any message that is blocked. This value can
                // only be returned from inside a chat room and when the user navigates out of a chat room it will
                // run repository.checkIfAllChatRoomMessagesHaveBeenObserved either way, so this will just be
                // repeating the run that is already guaranteed to happen.
                /*if(mostRecentUpdatesMessage.mostRecentlyUpdatedMessageSentByOID == it.accountOID) {
                    CoroutineScope(ioDispatcher).launch {
                        repository.checkIfAllChatRoomMessagesHaveBeenObserved(
                            INVALID_CHAT_ROOM_ID
                        )
                    }
                }*/

                _returnBlockReportChatRoomResult.value =
                    EventWrapperWithKeyString(result, chatRoomContainer.chatRoomUniqueId)
            }

            _returnGrpcFunctionErrorStatusEnumToActivity.value =
                EventWrapperWithKeyString(
                    GrpcFunctionErrorStatusReturnValues(
                        result.errorStatus,
                        turnOffLoading = false
                    ),
                    thisSharedApplicationViewModelInstanceId
                )
        }

    private val _returnSetPinnedLocationFailed: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val returnSetPinnedLocationFailed: LiveData<EventWrapperWithKeyString<Unit>> =
        _returnSetPinnedLocationFailed
    private val returnSetPinnedLocationFailedObserver =
        keyStringObserverFactory<String> { callingFragmentInstanceId ->
            _returnSetPinnedLocationFailed.value =
                EventWrapperWithKeyString(Unit, callingFragmentInstanceId)
        }

    private val returnClearHistoryFromChatRoomObserver =
        //message, fragmentInstanceId
        keyStringObserverFactory<ReturnClearHistoryFromChatRoomDataHolder> { result ->
            removeChatRoomFromRequestedMessages(result.historyClearedMessage.chatRoomId)
            sendChatMessagesToFragments.sendHistoryClearedBackToFragments(
                result.historyClearedMessage,
                result.fragmentInstanceID
            )
        }

    //the request is passed back here for completion of information in the SharedLoginViewModel
    private val _loginFunctionData: MutableLiveData<EventWrapperWithKeyString<LoginFunctionReturnValue>> =
        MutableLiveData()
    val loginFunctionData: LiveData<EventWrapperWithKeyString<LoginFunctionReturnValue>> =
        _loginFunctionData //NOTE: The response can be null, it means no internet connection or login attempt number exceeded

    //return a list of chatRoomIds matching the passed text
    private val _chatRoomSearchResults: MutableLiveData<EventWrapperWithKeyString<Set<String>>> =
        MutableLiveData()
    val chatRoomSearchResults: LiveData<EventWrapperWithKeyString<Set<String>>> =
        _chatRoomSearchResults
    private val chatRoomSearchResultsObserver =
        keyStringObserverFactory<ChatRoomSearchResultsDataHolder> { result ->
            _chatRoomSearchResults.value =
                EventWrapperWithKeyString(
                    result.matchingChatRooms,
                    result.fragmentInstanceID
                )
        }

    //returns the result to show if the little red dot should be shown on the messenger icon in the bottom menu
    private val _allChatRoomMessagesHaveBeenObservedResults: MutableLiveData<EventWrapper<AllChatRoomMessagesHaveBeenObservedHolder>> =
        MutableLiveData()
    val allChatRoomMessagesHaveBeenObservedResults: LiveData<EventWrapper<AllChatRoomMessagesHaveBeenObservedHolder>> =
        _allChatRoomMessagesHaveBeenObservedResults
    private val allChatRoomMessagesHaveBeenObservedResultsObserver =
        keyStringObserverFactory<AllChatRoomMessagesHaveBeenObservedHolder> { result ->
            _allChatRoomMessagesHaveBeenObservedResults.value = EventWrapper(result)
        }

    private val _algorithmSearchOptionsUpdatedResults: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val algorithmSearchOptionsUpdatedResults: LiveData<EventWrapperWithKeyString<Unit>> =
        _algorithmSearchOptionsUpdatedResults
    private val setAlgorithmSearchOptionsReturnValueObserver =
        keyStringObserverFactory<SetAlgorithmSearchOptionsReturnValues> {
            when {
                it.invalidParameterPassed -> { //server returned invalid value
                    _serverReturnedInvalidParameterError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.search_options))
                }
                it.errors == GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //successfully validated and stored value
                    algorithmSearchOptions = it.algorithmSearchOptions
                }
                it.errors != GrpcFunctionErrorStatusEnum.DO_NOTHING -> { //data failed to update
                    _modifyingValueFailedError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.search_options))
                }
            }

            _algorithmSearchOptionsUpdatedResults.value =
                EventWrapperWithKeyString(Unit, currentFragmentInstanceId)
        }

    private val _optedInToPromotionalEmailsUpdatedResults: MutableLiveData<EventWrapperWithKeyString<Unit>> =
        MutableLiveData()
    val optedInToPromotionalEmailsUpdatedResults: LiveData<EventWrapperWithKeyString<Unit>> =
        _optedInToPromotionalEmailsUpdatedResults
    private val setOptedInToPromotionalEmailsUpdatedObserver =
        keyStringObserverFactory<SetOptedInToPromotionalEmailsReturnValues> {
            when {
                it.invalidParameterPassed -> { //server returned invalid value
                    _serverReturnedInvalidParameterError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.opted_in_to_promotional_emails))
                }
                it.errors == GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //successfully validated and stored value
                    optedInToPromotionalEmails = it.optedInToPromotionalEmails
                }
                it.errors != GrpcFunctionErrorStatusEnum.DO_NOTHING -> { //data failed to update
                    _modifyingValueFailedError.value =
                        EventWrapper(applicationContext.resources.getString(R.string.opted_in_to_promotional_emails))
                }
            }

            _algorithmSearchOptionsUpdatedResults.value =
                EventWrapperWithKeyString(Unit, currentFragmentInstanceId)
        }

    private val _displayToastFromActivity: MutableLiveData<EventWrapper<String>> =
        MutableLiveData()
    val displayToastFromActivity: LiveData<EventWrapper<String>> =
        _displayToastFromActivity
    private val displayToastFromActivityObserver =
        keyStringObserverFactory<String> { resourceId ->
            _displayToastFromActivity.value = EventWrapper(resourceId)
        }

    //run in main thread to prevent concurrency issues
    private fun setupAccountInfo(userInfo: ApplicationAccountInfoDataHolder) {

        if (userInfo.accountInfo.algorithm_search_options != algorithmSearchOptions) {
            algorithmSearchOptions = userInfo.accountInfo.algorithm_search_options
            _algorithmSearchOptionsUpdatedResults.value =
                EventWrapperWithKeyString(Unit, currentFragmentInstanceId)
        }

        if (userInfo.accountInfo.categories_timestamp > categoriesTimestamp) {
            categories = userInfo.accountInfo.categories
            categoriesTimestamp = userInfo.accountInfo.categories_timestamp
        }

        if (userInfo.accountInfo.first_name_timestamp > userNameTimestamp) {
            userName = userInfo.accountInfo.first_name
            userNameTimestamp = userInfo.accountInfo.first_name_timestamp
        }

        if (userInfo.accountInfo.gender_timestamp > userGenderTimestamp) {
            userGender = userInfo.accountInfo.gender
            userGenderTimestamp = userInfo.accountInfo.gender_timestamp
        }

        if (userInfo.accountInfo.email_timestamp > userEmailAddressTimestamp) {
            userEmailAddress = userInfo.accountInfo.email_address
            userEmailAddressRequiresVerification =
                userInfo.accountInfo.requires_email_address_verification
            userEmailAddressTimestamp = userInfo.accountInfo.email_timestamp
        }

        if (userInfo.accountInfo.post_login_timestamp > postLoginInfoTimestamp) {
            userBio = userInfo.accountInfo.user_bio
            userCity = userInfo.accountInfo.user_city
            minAgeRange = userInfo.accountInfo.match_parameters_min_age
            maxAgeRange = userInfo.accountInfo.match_parameters_max_age
            userMaxDistance = userInfo.accountInfo.match_parameters_max_distance
            userGenderRange = convertStringToGenderRange(userInfo.accountInfo.user_gender_range)
            postLoginInfoTimestamp = userInfo.accountInfo.post_login_timestamp
        }

        if (userInfo.pictureInfo.isNotEmpty()) {

            var pictureSet = false

            for (pic in userInfo.pictureInfo) {
                if (pic.picturePath.isNotEmpty()) {


                    //NOTE: the below code can not actually work because if no pictures remain inside of
                    // the user account then it will have default values only and no timestamp
                    ////if this picture is the same index, ONLY update it if the timestamp is more current
                    //if (pic.pictureIndex != firstPictureInList.pictureIndex
                    //    || pic.pictureTimestamp < firstPictureInList.pictureTimestamp) {
                    //    firstPictureInList = pic
                    //}

                    firstPictureInList = pic

                    pictureSet = true
                    break
                }
            }

            //if no pictures for user are set
            if (!pictureSet) {
                //this should be a default picture value
                firstPictureInList = userInfo.pictureInfo.first()
            }

            _setFirstPictureReturnValue.value = EventWrapper(Unit)

        } else {
            val errorString =
                "No pictures stored inside account pictures database."

            sendSharedApplicationError(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber
            )

            firstPictureInList = AccountPictureDataEntity()
        }

        // -1 is used to mean it will not be set
        if (userInfo.accountInfo.chat_room_sort_method_selected != -1) {
            chatRoomContainer.setChatRoomSortMethodSelected(
                ChatRoomSortMethodSelected.setVal(
                    userInfo.accountInfo.chat_room_sort_method_selected
                )
            )
        }

        if (userInfo.accountInfo.age > userAge) {
            userAge = userInfo.accountInfo.age
        }

        if (userInfo.accountInfo.subscription_status != subscriptionStatus) {
            subscriptionStatus = userInfo.accountInfo.subscription_status
        }

        if (userInfo.accountInfo.opted_in_to_promotional_emails != optedInToPromotionalEmails) {
            optedInToPromotionalEmails = userInfo.accountInfo.opted_in_to_promotional_emails
        }
    }

    private fun processMessagesForChatRoom(
        returnMessagesForChatRoomDataHolder: ReturnMessagesForChatRoomDataHolder,
        fragmentInstanceID: String,
    ) {

        var latestMessage = AllChatRoomMessagesHaveBeenObservedHolder(
            allMessagesHaveBeenObserved = false,
            leftChatRoomId = INVALID_CHAT_ROOM_ID,
            mostRecentMessageUUID = "~",
            mostRecentMessageSentByOID = "~",
            mostRecentMessageTimestamp = -1L,
            mostRecentMessageChatRoomId = "~",
        )

        var deleteOccurred = false
        for (message in returnMessagesForChatRoomDataHolder.messages) {
            val typeOfMessage =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                    message.messageType
                )

            if (message.chatRoomId == chatRoomContainer.chatRoom.chatRoomId) { //if message is for this chat room

                //if this is a match made chat room
                chatRoomContainer.removeChatRoomFromMatchingState(
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(message.messageType)
                )

                //if chat room last active time needs updated
                chatRoomContainer.updateChatRoomLastActiveTimeForMessageType(
                    typeOfMessage,
                    message.messageStoredOnServerTime
                )

                if (checkIfUserLastActiveTimeRequiresUpdating(typeOfMessage)) { //if chat room last active time needs updated

                    chatRoomContainer.chatRoom.chatRoomMembers.getFromMap(message.sentByAccountID)?.chatRoom?.apply {
                        if (this.lastActiveTimeInChatRoom < message.messageStoredOnServerTime) {
                            this.lastActiveTimeInChatRoom = message.messageStoredOnServerTime
                        }
                    }
                }
            }

            if ("~" != chatRoomContainer.chatRoom.chatRoomId) {
                //this should be done whenever the chat room is 'in use' to avoid possibly repeating mime types
                if (typeOfMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE) {
                    chatRoomContainer.updateMimeTypeFilePath(
                        message.downloadUrl,
                        message.messageText
                    )
                }
            }

            //This block must be updated no matter if the chat room is set or not, it will update the
            // little red dot on the messages symbol when a new message is received.
            if (checkIfChatRoomLastActiveTimeRequiresUpdating(typeOfMessage)
                && message.sentByAccountID != LoginFunctions.currentAccountOID
                && message.messageStoredOnServerTime > latestMessage.mostRecentMessageTimestamp
                && displayBlockedMessage(message.sentByAccountID, message.messageType)
            ) {
                latestMessage = AllChatRoomMessagesHaveBeenObservedHolder(
                    allMessagesHaveBeenObserved = false,
                    INVALID_CHAT_ROOM_ID,
                    message.messageUUIDPrimaryKey,
                    message.sentByAccountID,
                    message.messageStoredOnServerTime,
                    message.chatRoomId,
                )
            } else if (typeOfMessage == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE
                && message.sentByAccountID != LoginFunctions.currentAccountOID
            ) {
                deleteOccurred = true
            }
        }

        if (deleteOccurred) {
            //if a delete occurs there are quite a few possibilities on how it could have happened and
            // so simply recalculate if messaged have been observed
            checkIfAllChatRoomMessagesHaveBeenObserved()
        } else if (
            latestMessage.mostRecentMessageTimestamp != -1L
            && !returnMessagesForChatRoomDataHolder.chatRoomInitialization
        ) {
            Log.i("messagesStuff", "update came from deleted message")
            _allChatRoomMessagesHaveBeenObservedResults.value = EventWrapper(
                latestMessage
            )
        }

        //NOTE: the chatRoomsListFragment needs messages for all chat rooms so it can display the most recent one
        sendChatMessagesToFragments.sendNewMessagesBackToFragments(
            returnMessagesForChatRoomDataHolder,
            fragmentInstanceID
        )
    }

    private fun processAccountStateUpdated(accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder) {
        if (chatRoomContainer.chatRoom.chatRoomId == accountStateUpdatedDataHolder.chatRoomId) { //if user is still in the same chat room

            if (accountStateUpdatedDataHolder.updatedAccountOID == LoginFunctions.currentAccountOID) { //if it is this account updated
                chatRoomContainer.chatRoom.accountState =
                    accountStateUpdatedDataHolder.updatedAccountState
            } else { //if it is another user updated

                val successfullyUpdate =
                    chatRoomContainer.chatRoom.chatRoomMembers.updateAccountStateByAccountOID(
                        accountStateUpdatedDataHolder.updatedAccountOID,
                        accountStateUpdatedDataHolder.updatedAccountState
                    )

                if (!successfullyUpdate) {

                    val errorMessage = "Failed to run processAccountStateUpdated().\n" +
                            "accountStateUpdatedDataHolder: $accountStateUpdatedDataHolder\n"

                    sendSharedApplicationError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber
                    )

                    return
                }

            }

            _returnAccountStateUpdated.value =
                EventWrapperWithKeyString(accountStateUpdatedDataHolder, currentFragmentInstanceId)
        }
    }

    private fun processEventOidUpdated(eventOidValues: ReturnChatRoomEventOidUpdated) {
        if (chatRoomContainer.chatRoom.chatRoomId == eventOidValues.chatRoomId
            && chatRoomContainer.chatRoom.eventId != eventOidValues.eventOid
        ) {
            chatRoomContainer.chatRoom.eventId = eventOidValues.eventOid

            _returnChatRoomEventOidUpdated.value =
                EventWrapperWithKeyString(Unit, currentFragmentInstanceId)
        }
    }

    private fun processQrCodeInfoUpdated(qrCodeValues: ReturnQrCodeInfoUpdated) {
        if (chatRoomContainer.chatRoom.chatRoomId == qrCodeValues.chatRoomId) {
            chatRoomContainer.chatRoom.qrCodePath = qrCodeValues.qRCodePath
            chatRoomContainer.chatRoom.qrCodeMessage = qrCodeValues.qRCodeMessage
            chatRoomContainer.chatRoom.qrCodeTimeUpdated = qrCodeValues.qRCodeTimeUpdated

            _returnQrInfoUpdated.value =
                EventWrapperWithKeyString(qrCodeValues, currentFragmentInstanceId)
        }
    }

    private fun processChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder) {
        when (val messageType =
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                updateChatRoomInfoResultsDataHolder.message.messageType
            )) {
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {

                if (chatRoomContainer.chatRoom.chatRoomId == updateChatRoomInfoResultsDataHolder.message.chatRoomId) { //if the chat room matches the passed chat room
                    chatRoomContainer.chatRoom.chatRoomName =
                        updateChatRoomInfoResultsDataHolder.message.messageText

                    //update chatRoomLastActivityTime
                    chatRoomContainer.updateChatRoomLastActiveTime(
                        updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                    )

                    //update otherUserLastActivityTime
                    chatRoomContainer.chatRoom.chatRoomMembers.getFromMap(
                        updateChatRoomInfoResultsDataHolder.message.sentByAccountID
                    )?.chatRoom?.apply {
                        if (this.lastActiveTimeInChatRoom < updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime) {
                            this.lastActiveTimeInChatRoom =
                                updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                        }
                    }
                }

                //NOTE: if this is received from the ApplicationRepository livedata instead of the ChatStreamObject, it will
                // not have a valid messageUUID
                if (!chatRoomsListInfoContainer.chatRoomListsFragmentActive()) {
                    _returnChatRoomInfoUpdatedData.value =
                        EventWrapperWithKeyString(
                            updateChatRoomInfoResultsDataHolder,
                            chatRoomContainer.chatRoomUniqueId
                        )
                } else {
                    chatRoomsListInfoContainer.chatRoomInfoUpdated(
                        updateChatRoomInfoResultsDataHolder
                    )
                }
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {

                if (chatRoomContainer.chatRoom.chatRoomId == updateChatRoomInfoResultsDataHolder.message.chatRoomId) { //if the chat room matches the passed chat room
                    chatRoomContainer.chatRoom.chatRoomPassword =
                        updateChatRoomInfoResultsDataHolder.message.messageText

                    //update chatRoomLastActivityTime
                    chatRoomContainer.updateChatRoomLastActiveTime(
                        updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                    )

                    //update otherUserLastActivityTime
                    chatRoomContainer.chatRoom.chatRoomMembers.getFromMap(
                        updateChatRoomInfoResultsDataHolder.message.sentByAccountID
                    )?.chatRoom?.apply {
                        if (this.lastActiveTimeInChatRoom < updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime) {
                            this.lastActiveTimeInChatRoom =
                                updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                        }
                    }
                }

                //NOTE: if this is received from the ApplicationRepository livedata instead of the ChatStreamObject, it will
                // not have a valid messageUUID
                _returnChatRoomInfoUpdatedData.value =
                    EventWrapperWithKeyString(
                        updateChatRoomInfoResultsDataHolder,
                        chatRoomContainer.chatRoomUniqueId
                    )
            }
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {

                if (chatRoomContainer.chatRoom.chatRoomId == updateChatRoomInfoResultsDataHolder.message.chatRoomId) { //if the chat room matches the passed chat room
                    chatRoomContainer.chatRoom.pinnedLocationLongitude =
                        updateChatRoomInfoResultsDataHolder.message.longitude
                    chatRoomContainer.chatRoom.pinnedLocationLatitude =
                        updateChatRoomInfoResultsDataHolder.message.latitude

                    //update chatRoomLastActivityTime
                    chatRoomContainer.updateChatRoomLastActiveTime(
                        updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                    )

                    //update otherUserLastActivityTime
                    chatRoomContainer.chatRoom.chatRoomMembers.getFromMap(
                        updateChatRoomInfoResultsDataHolder.message.sentByAccountID
                    )?.chatRoom?.apply {
                        if (this.lastActiveTimeInChatRoom < updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime) {
                            this.lastActiveTimeInChatRoom =
                                updateChatRoomInfoResultsDataHolder.message.messageStoredOnServerTime
                        }
                    }
                }

                //NOTE: if this is received from the ApplicationRepository livedata instead of the ChatStreamObject, it will
                // not have a valid messageUUID
                _returnChatRoomInfoUpdatedData.value =
                    EventWrapperWithKeyString(
                        updateChatRoomInfoResultsDataHolder,
                        chatRoomContainer.chatRoomUniqueId
                    )
            }
            else -> {
                val errorMessage =
                    "Invalid message type passed to processChatRoomInfoUpdated().\n" +
                            "messageType: $messageType\n"

                sendSharedApplicationError(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber
                )

                return
            }
        }
    }

    fun beginLoginToServerWhenReceivedInvalidToken() {
        CoroutineScope(ioDispatcher).launch {
            repository.beginLoginToServerWhenReceivedInvalidToken()
        }
    }

    fun removeChatRoomQrCode() {
        val prevQrCodePath = chatRoomContainer.chatRoom.qrCodePath
        val chatRoomId = chatRoomContainer.chatRoom.chatRoomId
        //Update these on main so that the ChatRoomInfoAdapter has them all changed before it continues.
        chatRoomContainer.chatRoom.qrCodePath = GlobalValues.server_imported_values.qrCodeDefault
        chatRoomContainer.chatRoom.qrCodeMessage =
            GlobalValues.server_imported_values.qrCodeMessageDefault
        chatRoomContainer.chatRoom.qrCodeTimeUpdated =
            GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault
        CoroutineScope(ioDispatcher).launch {
            repository.removeChatRoomQrCode(
                chatRoomId,
                prevQrCodePath,
            )
        }
    }

    private val onlyRetrieveUserAccountInfoOnce = AtomicBoolean(false)

    fun getUserAccountInfoToStoreToViewModel(fragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            if (onlyRetrieveUserAccountInfoOnce.compareAndSet(false, true)) {
                try {
                    repository.getPictureAndAccountValuesFromDatabase(
                        fragmentInstanceID,
                        thisSharedApplicationViewModelInstanceId
                    )
                } finally {
                    onlyRetrieveUserAccountInfoOnce.set(false)
                }
            }
        }
    }

    fun clearHandlers() {
        findMatchesObject.clearHandlerForViewModelCleared()
    }

    fun logoutAccount() {
        CoroutineScope(ioDispatcher).launch {
            loginSupportFunctions.runLogoutFunction()
        }
    }

    fun clearAllUserDataAndStopObjects(
        returnLiveData: Boolean = false,
        updateLoginFunctionStatus: Boolean = true
    ) {
        CoroutineScope(ioDispatcher).launch {
            loginSupportFunctions.clearAllUserDataAndStopObjects(
                returnLiveData = returnLiveData,
                updateLoginFunctionStatus = updateLoginFunctionStatus
            )
        }
    }

    fun deleteAccount() {
        CoroutineScope(ioDispatcher).launch {
            loginSupportFunctions.runDeleteFunction()
        }
    }

    fun setEmailAddress(
        emailAddress: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.setEmailAddress(emailAddress, thisSharedApplicationViewModelInstanceId)
        }
    }

    fun updateMimeTypeFileName(
        mimeTypeUrl: String,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateMimeTypeFilePath(
                mimeTypeUrl,
                mimeTypeFilePath,
                mimeTypeWidth,
                mimeTypeHeight
            )
        }
    }

    fun resetNumberOfPostLoginInfoThatWasSet() {
        numberOfPostLoginInfoThatWasSet = 0
        setPostLoginInfoReturnedTimestamps.clear()
        postLoginDisplayedConnectionError = false
    }

    fun clearFindMatchesVariables() {
        findMatchesObject.clearVariables()
    }

    suspend fun checkUserAccountStateInsideChatRoom(
        userAccountOID: String,
        chatRoomId: String,
    ): AccountState.AccountStateInChatRoom? =
        withContext(ioDispatcher) {
            return@withContext repository.checkUserStateInsideChatRoom(userAccountOID, chatRoomId)
        }

    fun setAlgorithmSearchOptions(algorithmSearchOptions: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions) =
        CoroutineScope(ioDispatcher).launch {
            repository.setAlgorithmSearchOptions(
                algorithmSearchOptions,
                thisSharedApplicationViewModelInstanceId
            )
        }

    fun setOptedInToPromotionalEmails(optedInForPromotionalEmails: Boolean) =
        CoroutineScope(ioDispatcher).launch {
            repository.setOptedInToPromotionalEmails(
                optedInForPromotionalEmails,
                thisSharedApplicationViewModelInstanceId
            )
        }

    fun <T> setPostLoginInfo(
        setType: SetTypeEnum,
        passedInfo: T,
        callingFragmentInstanceID: String,
    ) = CoroutineScope(ioDispatcher).launch {

        when (setType) {
            SetTypeEnum.SET_BIO -> {
                numberOfPostLoginInfoThatWasSet++
                repository.setUserBio(
                    passedInfo as String,
                    thisSharedApplicationViewModelInstanceId
                )
            }
            SetTypeEnum.SET_CITY -> {
                numberOfPostLoginInfoThatWasSet++
                repository.setUserCity(
                    passedInfo as String,
                    thisSharedApplicationViewModelInstanceId
                )
            }
            SetTypeEnum.SET_AGE_RANGE -> {
                numberOfPostLoginInfoThatWasSet++
                //Because this is called when updating, the location may need to be re-requested for a match (more
                // of a theoretical problem from moving locations large distances instantly)
                resetLastTimeLocationReceived()
                findMatchesObject.clearMatchesFromList()
                val errorStatus = repository.setAgeRange(
                    passedInfo as AgeRangeHolder,
                    thisSharedApplicationViewModelInstanceId
                )
                findMatchesObject.functionToUpdateAlgorithmParametersCompleted(
                    errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                )
            }
            SetTypeEnum.SET_MAX_DISTANCE -> {
                numberOfPostLoginInfoThatWasSet++
                //Because this is called when updating, the location may need to be re-requested for a match (more
                // of a theoretical problem from moving locations large distances instantly)
                resetLastTimeLocationReceived()
                findMatchesObject.clearMatchesFromList()
                val errorStatus = repository.setMaxDistance(
                    passedInfo as Int,
                    thisSharedApplicationViewModelInstanceId
                )
                findMatchesObject.functionToUpdateAlgorithmParametersCompleted(
                    errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                )
            }
            SetTypeEnum.SET_GENDER -> {
                numberOfPostLoginInfoThatWasSet++
                //Because this is called when updating, the location may need to be re-requested for a match (more
                // of a theoretical problem from moving locations large distances instantly)
                resetLastTimeLocationReceived()
                findMatchesObject.clearMatchesFromList()
                val errorStatus = repository.setGender(
                    passedInfo as String,
                    thisSharedApplicationViewModelInstanceId
                )
                findMatchesObject.functionToUpdateAlgorithmParametersCompleted(
                    errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                )
            }
            SetTypeEnum.SET_GENDER_RANGE -> {
                numberOfPostLoginInfoThatWasSet++
                //Because this is called when updating, the location may need to be re-requested for a match (more
                // of a theoretical problem from moving locations large distances instantly)
                resetLastTimeLocationReceived()
                findMatchesObject.clearMatchesFromList()
                val errorStatus = repository.setGenderRange(
                    passedInfo as String,
                    thisSharedApplicationViewModelInstanceId
                )
                findMatchesObject.functionToUpdateAlgorithmParametersCompleted(
                    errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                )
            }
            SetTypeEnum.UNKNOWN,
            SetTypeEnum.SET_BIRTHDAY,
            SetTypeEnum.SET_EMAIL,
            SetTypeEnum.SET_FIRST_NAME,
            SetTypeEnum.SET_PICTURE,
            SetTypeEnum.SET_CATEGORIES,
            SetTypeEnum.SET_ALGORITHM_SEARCH_OPTIONS,
            SetTypeEnum.SET_OPTED_IN_TO_PROMOTIONAL_EMAIL,
            -> {
                val errorString =
                    "$setType was passed to a function that should never receive it.\n" +
                            "passedInfo: $passedInfo\n" +
                            "callingFragmentInstanceID: $callingFragmentInstanceID"

                sendSharedApplicationError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber
                )
            }
        }
    }

    fun sendFeedback(
        info: String,
        feedbackType: FeedbackTypeEnum.FeedbackType,
        activityName: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.sendFeedback(
                info,
                feedbackType,
                activityName,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun requestMatchFromDatabase() {
        CoroutineScope(ioDispatcher).launch {
            findMatchesObject.requestMatchFromDatabase()
        }
    }

    fun functionToUpdateAlgorithmParametersCompleted(successful: Boolean) {
        CoroutineScope(ioDispatcher).launch {
            findMatchesObject.functionToUpdateAlgorithmParametersCompleted(successful)
        }
    }

    fun callClearMatchesFromList() {
        //Because this is called when updating, the location may need to be re-requested for a match (more
        // of a theoretical problem from moving locations large distances instantly)
        //Must be called outside the coRoutine in order to guarantee location is cleared BEFORE it is potentially
        // requested again
        resetLastTimeLocationReceived()
        CoroutineScope(ioDispatcher).launch {
            findMatchesObject.clearMatchesFromList()
        }
    }

    fun updateInviteMessageToExpired(uuidPrimaryKey: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateInviteMessageToExpired(uuidPrimaryKey)
        }
    }

    private fun handlePostLoginSetValues(
        invalidParameterPassed: Boolean,
        errorStatus: GrpcFunctionErrorStatusEnum,
        timestamp: Long,
        errorStringResource: Int,
        successLambda: (() -> Unit),
    ) {

        numberOfPostLoginInfoThatWasSet--

        when {
            invalidParameterPassed -> { //server returned invalid value
                _serverReturnedInvalidParameterError.value =
                    EventWrapper(applicationContext.resources.getString(errorStringResource))
            }
            errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //successfully validated and stored value
                setPostLoginInfoReturnedTimestamps.add(timestamp)
                successLambda()
            }
            errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING -> { //data failed to update
                _modifyingValueFailedError.value =
                    EventWrapper(applicationContext.resources.getString(errorStringResource))
            }
        }

        _returnGrpcFunctionErrorStatusEnumToActivity.value =
            EventWrapperWithKeyString(
                GrpcFunctionErrorStatusReturnValues(
                    errorStatus,
                    turnOffLoading = false
                ),
                thisSharedApplicationViewModelInstanceId
            )

        //if this is set then all post login info has finished being set
        if (numberOfPostLoginInfoThatWasSet == 0) {

            var highestTimestampValue = -1L

            for (t in setPostLoginInfoReturnedTimestamps) {
                if (t > highestTimestampValue) {
                    highestTimestampValue = t
                }
            }

            setPostLoginInfoReturnedTimestamps.clear()

            //if a new timestamp was set then save it
            if (highestTimestampValue != -1L) {
                CoroutineScope(ioDispatcher).launch {
                    repository.setPostLoginTimestamp(highestTimestampValue)
                }
            }

            resetNumberOfPostLoginInfoThatWasSet()
        }
    }

    fun userRespondedToMatch(
        match: Pair<OtherUsersDataEntity, MatchesDataEntity>,
        responseType: ReportMessages.ResponseType,
        reportReason: ReportMessages.ReportReason,
        otherInfo: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            findMatchesObject.userRespondedToMatch(
                match,
                responseType,
                reportReason,
                otherInfo
            )

            if (responseType == ReportMessages.ResponseType.USER_MATCH_OPTION_REPORT) {
                repository.addAccountToBlockedList(
                    match.first.accountOID,
                    thisSharedApplicationViewModelInstanceId
                )

                if (mostRecentUpdatesMessage.mostRecentlyUpdatedMessageSentByOID == match.first.accountOID) {
                    repository.checkIfAllChatRoomMessagesHaveBeenObserved(
                        INVALID_CHAT_ROOM_ID,
                        thisSharedApplicationViewModelInstanceId
                    )
                }
            }
        }
    }

    fun requestMatchesFromServer() {
        CoroutineScope(ioDispatcher).launch {
            findMatchesObject.requestMatchesFromServer(
                GlobalValues.lastUpdatedLocationInfo.longitude,
                GlobalValues.lastUpdatedLocationInfo.latitude
            )
        }
    }

    fun sendMimeTypeMessage(
        messageEntity: MessagesDataEntity,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        mimeType: String,
        thumbnailForReply: ByteArray,
        fragmentInstanceID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.sendMimeTypeMessage(
                messageEntity,
                mimeTypeFilePath,
                mimeTypeWidth,
                mimeTypeHeight,
                mimeType,
                thumbnailForReply,
                fragmentInstanceID,
            )
        }
    }

    fun sendMessage(
        messageEntity: MessagesDataEntity,
        thumbnailForReply: ByteArray,
        fragmentInstanceID: String
    ) {
        Log.i("chatMessageAdapter", "sendMessage()")
        CoroutineScope(ioDispatcher).launch {
            repository.sendMessage(
                messageEntity,
                thumbnailForReply,
                fragmentInstanceID
            )
        }
    }

    fun createChatRoom(chatRoomName: String, fragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.createChatRoom(
                chatRoomName,
                fragmentInstanceID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun leaveCurrentChatRoom() {
        CoroutineScope(ioDispatcher).launch {
            repository.leaveChatRoom(
                chatRoomContainer.chatRoom.chatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun leaveChatRoomById(chatRoomId: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.leaveChatRoom(chatRoomId, thisSharedApplicationViewModelInstanceId)
        }
    }

    fun unMatchChatRoom(matchAccountID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.unMatchFromChatRoom(
                chatRoomContainer.chatRoom.chatRoomId,
                matchAccountID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun blockAndReportFromChatRoom(
        matchOptionsBuilder: ReportMessages.UserMatchOptionsRequest.Builder,
        unMatch: Boolean,
    ) {
        val built = matchOptionsBuilder.build()

        built.toByteArray().toString()

        CoroutineScope(ioDispatcher).launch {
            repository.blockAndReportFromChatRoom(
                matchOptionsBuilder,
                chatRoomContainer.chatRoom.chatRoomId,
                unMatch,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun unblockOtherUser(
        userToUnblockAccountId: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.unblockOtherUser(
                userToUnblockAccountId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun requestMessageUpdate(
        chatRoomId: String,
        amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage,
        messageUUIDList: List<String>,
    ) {

        val messageUUIDListDistinct = messageUUIDList.distinct()

        /** This is run using Main thread for concurrency of messagesBeingRequestedSet container. **/
        CoroutineScope(Dispatchers.Main).launch {
            val filteredMessageUUIDList = mutableListOf<String>()

            Log.i(
                "followingUpdates",
                "SharedApplicationViewModel messageUUIDListDistinct.size(): ${messageUUIDListDistinct.size}"
            )

            //save messages that are not already being requested to the set
            for (messageUUID in messageUUIDListDistinct) {
                val extractedAmountOfMessage = messagesBeingRequestedSet[messageUUID]

                if (extractedAmountOfMessage != null) { //if message is already being updated
                    when (amountOfMessage) {
                        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO -> {
                            if (extractedAmountOfMessage.amountOfMessage == TypeOfChatMessageOuterClass.AmountOfMessage.ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE) {
                                messagesBeingRequestedSet[messageUUID] =
                                    ChatRoomAmountOfMessage(
                                        chatRoomId,
                                        amountOfMessage
                                    )
                                filteredMessageUUIDList.add(messageUUID)
                            }
                        }
                        TypeOfChatMessageOuterClass.AmountOfMessage.ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE -> {
                            //skeleton is not updated in here, so if the message uuid is found being requested at all, do nothing
                        }
                        TypeOfChatMessageOuterClass.AmountOfMessage.ONLY_SKELETON,
                        TypeOfChatMessageOuterClass.AmountOfMessage.UNRECOGNIZED,
                        -> {
                            val errorMessage =
                                "When running requestMessageUpdate(), an invalid AmountOfMessage was requested.\n" +
                                        "chatRoomId: $chatRoomId\n" +
                                        "amountOfMessage: $amountOfMessage\n" +
                                        "messageUUIDSet: $messageUUIDListDistinct\n" +
                                        "extractedAmountOfMessage: $extractedAmountOfMessage\n"

                            sendSharedApplicationError(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber
                            )

                            return@launch
                        }
                    }
                } else { //if message is not being updated
                    messagesBeingRequestedSet[messageUUID] =
                        ChatRoomAmountOfMessage(
                            chatRoomId,
                            amountOfMessage
                        )
                    //if the message was not already being requested, request it
                    filteredMessageUUIDList.add(messageUUID)
                }
            }

            Log.i(
                "followingUpdates",
                "SharedApplicationViewModel requestBuilder.messageUuidListCount: ${filteredMessageUUIDList.size}"
            )

            if (filteredMessageUUIDList.size > 0) {

                repository.requestMessageUpdate(
                    chatRoomId,
                    amountOfMessage,
                    filteredMessageUUIDList
                )
            }
        }
    }

    fun joinChatRoomFromInvite(
        uuidPrimaryKey: String,
        chatRoomId: String,
        chatRoomPassword: String,
        fragmentInstanceID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.joinChatRoomFromInvite(
                uuidPrimaryKey,
                chatRoomId,
                chatRoomPassword,
                fragmentInstanceID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun joinChatRoom(chatRoomId: String, chatRoomPassword: String, fragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.joinChatRoom(
                chatRoomId,
                chatRoomPassword,
                fragmentInstanceID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun removeUserFromChatRoom(
        kickOrBan: ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan,
        accountOIDToRemove: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.removeUserFromChatRoom(
                chatRoomContainer.chatRoom.chatRoomId,
                kickOrBan,
                accountOIDToRemove,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun setNotificationsForChatRoom(notificationsEnabled: Boolean) {
        CoroutineScope(ioDispatcher).launch {
            chatRoomContainer.chatRoom.notificationsEnabled = notificationsEnabled
            repository.setNotificationsForChatRoom(
                chatRoomContainer.chatRoom.chatRoomId,
                notificationsEnabled
            )
        }
    }

    fun promoteNewAdmin(
        promotedUserAccountOID: String,
        chatRoomId: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.promoteNewAdmin(
                promotedUserAccountOID,
                chatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun updateChatRoomInfo(
        newChatRoomInfo: String,
        typeOfInfoToUpdate: ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateChatRoomInfo(
                newChatRoomInfo,
                typeOfInfoToUpdate,
                chatRoomContainer.chatRoom.chatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun setPinnedLocation(
        longitude: Double,
        latitude: Double,
        fragmentInstanceId: String
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.setPinnedLocation(
                longitude,
                latitude,
                chatRoomContainer.chatRoom.chatRoomId,
                thisSharedApplicationViewModelInstanceId,
                fragmentInstanceId
            )
        }
    }

    fun retrieveMessagesForChatRoomId() {
        CoroutineScope(ioDispatcher).launch {
            repository.retrieveMessagesForChatRoomId(
                chatRoomContainer.chatRoom.chatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    private suspend fun getMostRecentMessageInChatRoom(
        chatRoomID: String,
        chatRoomLastActiveTime: Long,
        chatRoomTimeJoined: Long
    ): Pair<MostRecentMessageDataHolder?, Long> {
        return repository.getMostRecentMessageInChatRoom(
            chatRoomID,
            chatRoomLastActiveTime,
            chatRoomTimeJoined
        )
    }

    fun retrieveSingleChatRoom(
        chatRoomID: String,
        fragmentInstanceID: String,
        chatRoomMustExist: Boolean,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.retrieveSingleChatRoom(
                chatRoomID,
                fragmentInstanceID,
                chatRoomMustExist,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun updateChatRoom() {
        CoroutineScope(ioDispatcher).launch {
            repository.updateChatRoom(
                chatRoomContainer.chatRoom.chatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun runBeginEmailVerification(
        callingFragmentInstanceID: String
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.runBeginEmailVerification(
                callingFragmentInstanceID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    //this function cannot update matches, see updateSingleMatchMemberInfo()
    fun updateSingleChatRoomMemberInfo(userAccountOID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateSingleChatRoomMemberInfo(
                chatRoomContainer.chatRoom.chatRoomId,
                userAccountOID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    //this function is called by the match screen fragment and cannot chat room members, see updateSingleChatRoomMemberInfo()
    fun updateSingleMatchMemberInfo(
        userAccountOID: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateSingleMatchMemberInfo(
                userAccountOID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun updateMessagesObservedTimes(messageUUIDPrimaryKeys: Set<String>) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateMessagesObservedTimes(
                messageUUIDPrimaryKeys
            )
        }
    }

    fun updateMimeTypesObservedTimes(mimeTypeURLs: Set<String>) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateMimeTypesObservedTimes(
                mimeTypeURLs
            )
        }
    }

    fun updateOtherUserObservedTime(userAccountOID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.updateOtherUserObservedTime(
                userAccountOID
            )
        }
    }

    //setting the default value to something other than "~" to avoid matching the default value of
// mostRecentlyUpdatedMessageChatRoomId inside AppActivity
    fun checkIfAllChatRoomMessagesHaveBeenObserved(userLeftChatRoomId: String = INVALID_CHAT_ROOM_ID) {
        CoroutineScope(ioDispatcher).launch {
            repository.checkIfAllChatRoomMessagesHaveBeenObserved(
                userLeftChatRoomId,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun startFindMatchesHandlerIfNecessary() {
        //Skip running for the first activity onStart(). See blankLoadingFragmentCompleted variable
        // for more details.
        if (blankLoadingFragmentCompleted) {
            findMatchesObject.startHandlerIfNecessary()
        }
    }

    fun stopAllFindMatchesHandlers() {
        findMatchesObject.stopAllHandlers()
    }

    fun getChatRoomsFromDatabase(chatRoomListCalledFrom: ChatRoomListCalledFrom) {
        CoroutineScope(ioDispatcher).launch {
            repository.getChatRoomsFromDatabase(
                chatRoomListCalledFrom,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    private fun updateChatRoomsSortedType(passedChatRoomSortMethodSelected: ChatRoomSortMethodSelected) {
        chatRoomContainer.setChatRoomSortMethodSelected(passedChatRoomSortMethodSelected)
        CoroutineScope(ioDispatcher).launch {
            repository.updateChatRoomsSortedType(passedChatRoomSortMethodSelected)
        }
    }

    suspend fun beginChatRoomSearchForString(
        matchingString: String,
        thisFragmentInstanceID: String,
    ) {
        //yield before the subscription in case this was cancelled
        yield()
        //NOTE: This blocks so that the caller can cancel the coRoutine.
        repository.beginChatRoomSearchForString(
            matchingString,
            thisFragmentInstanceID,
            thisSharedApplicationViewModelInstanceId
        )
    }

    fun clearHistoryFromChatRoom(thisFragmentInstanceID: String) {
        val chatRoomId = chatRoomContainer.chatRoom.chatRoomId
        CoroutineScope(ioDispatcher).launch {
            repository.clearHistoryFromChatRoom(
                chatRoomId,
                thisFragmentInstanceID,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    private suspend fun respondToLogin(loginFunctionReturnValues: LoginFunctionReturnValue) {

        LoginFunctions.receivedMessage(loginFunctionReturnValues)

        if (loginFunctionReturnValues.loginFunctionStatus == LoginFunctionStatus.LoggedIn) {
            getUserAccountInfoToStoreToViewModel(GlobalValues.INVALID_FRAGMENT_INSTANCE_ID)
        }

        //This should already be in Main context
        withContext(Dispatchers.Main) {
            _loginFunctionData.value = EventWrapperWithKeyString(
                loginFunctionReturnValues,
                thisSharedApplicationViewModelInstanceId
            )
        }
    }

    fun startChatStreamIfNotRunning() {
        CoroutineScope(ioDispatcher).launch {
            chatStreamObject.subscribe(
                subscriberWrapper,
                ChatStreamObject.SubscriberType.APPLICATION_VIEW_MODEL_SUBSCRIBER
            )
        }
    }

    init {
        Log.i("listsFragment", "SharedApplicationViewModel init")

        CoroutineScope(ioDispatcher).launch {
            chatStreamObject.subscribe(
                subscriberWrapper,
                ChatStreamObject.SubscriberType.APPLICATION_VIEW_MODEL_SUBSCRIBER
            )
        }

        CoroutineScope(ioDispatcher).launch {
            LoginFunctions.viewModelSubscribe(applicationViewModelUUID, ::respondToLogin)
        }

        repository.returnApplicationAccountInfo.observeForever(returnApplicationAccountInfoObserver) //login observer return values

        repository.setEmailReturnValue.observeForever(setEmailReturnValueObserver) //set email return results

        repository.setAlgorithmSearchOptionsReturnValue.observeForever(
            setAlgorithmSearchOptionsReturnValueObserver
        )
        repository.setOptedInToPromotionalEmailsReturnValue.observeForever(
            setOptedInToPromotionalEmailsUpdatedObserver
        )
        repository.setBioReturnValue.observeForever(setBioReturnValueObserver) //set bio return results
        repository.setCityReturnValue.observeForever(setCityReturnValueObserver) //set city return results
        repository.setAgeRangeReturnValue.observeForever(setAgeRangeReturnValueObserver) //set age range return results
        repository.setMaxDistanceReturnValue.observeForever(setMaxDistanceReturnValueObserver) //set max distance return results
        repository.setGenderReturnValue.observeForever(setGenderReturnValueObserver) //set gender return results
        repository.setGenderRangeReturnValue.observeForever(setGenderRangeReturnValueObserver) //set gender range return results

        repository.returnAllChatRooms.observeForever(returnAllChatRoomsObserver) //get all chat rooms
        repository.returnSingleChatRoom.observeForever(returnSingleChatRoomObserver) //get single chat room
        repository.returnSingleChatRoomNotFound.observeForever(returnSingleChatRoomNotFoundObserver) //single chat room not found
        repository.returnMessagesForChatRoom.observeForever(returnMessagesForChatRoomObserver) //get all messages for a single chat room
        repository.returnCreatedChatRoom.observeForever(returnCreatedChatRoomObserver) //get return for created chat room
        repository.returnUpdatedOtherUser.observeForever(returnUpdatedOtherUserObserver) //get updates for chat room
        repository.updatePicturesUpdateAttemptedTimestampByAccountOIDs.observeForever(
            updatePicturesUpdateAttemptedTimestampByAccountOIDsObserver
        ) //updates members from update chat room to the time when pictures were attempted to be updated
        repository.returnLeaveChatRoomResult.observeForever(returnLeaveChatRoomResultObserver) //get return for leave chat room
        repository.returnJoinChatRoomResult.observeForever(returnJoinChatRoomResultObserver) //get return for join chat room
        repository.returnBlockReportChatRoomResult.observeForever(
            returnBlockReportChatRoomResultObserver
        )
        repository.returnSetPinnedLocationFailed.observeForever(
            returnSetPinnedLocationFailedObserver
        )

        repository.returnEmailVerificationReturnValue.observeForever(
            returnEmailVerificationReturnValueObserver
        ) //gets return value for email verification

        //error handling
        /** one of these is repository one is findMatchesObject **/
        repository.returnGrpcFunctionErrorStatusEnumToActivity.observeForever(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )
        findMatchesObject.returnGrpcFunctionErrorStatusEnumToActivity.observeForever(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )

        repository.matchRemovedOnJoinChatRoom.observeForever(matchRemovedOnJoinChatRoomObserver)
        repository.returnAccountStateUpdated.observeForever(returnAccountStateUpdatedObserver)
        repository.returnChatRoomInfoUpdatedData.observeForever(returnChatRoomInfoUpdatedObserver)
        repository.returnChatRoomEventOidUpdated.observeForever(
            returnChatRoomEventOidUpdatedObserver
        )
        repository.returnQrInfoUpdated.observeForever(returnQrInfoUpdatedObserver)
        repository.displayToastFromActivity.observeForever(displayToastFromActivityObserver)
        repository.returnClearHistoryFromChatRoom.observeForever(
            returnClearHistoryFromChatRoomObserver
        )

        repository.chatRoomSearchResults.observeForever(chatRoomSearchResultsObserver)
        repository.allChatRoomMessagesHaveBeenObservedResults.observeForever(
            allChatRoomMessagesHaveBeenObservedResultsObserver
        )

        selectPicturesRepository.setFirstPictureReturnValue.observeForever(
            setFirstPictureReturnValueObserver
        )
        selectPicturesRepository.returnGrpcFunctionErrorStatusEnumToActivity.observeForever(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )

        selectCategoriesRepository.setCategoriesUpdatedForViewModel.observeForever(
            setCategoriesUpdatedForViewModelObserver
        )

    }

    override fun onCleared() {

        Log.i("listsFragment", "SharedApplicationViewModel onCleared()")

        CoroutineScope(ioDispatcher).launch {
            Log.i("chatStreamSubscription", "SharedApplicationViewModel.kt unSubscribe")
            chatStreamObject.unSubscribe(
                subscriberWrapper,
                ChatStreamObject.SubscriberType.APPLICATION_VIEW_MODEL_SUBSCRIBER
            )
        }

        CoroutineScope(ioDispatcher).launch {
            LoginFunctions.viewModelUnSubscribe(applicationViewModelUUID)
        }

        subscriberWrapper.clear()

        repository.returnApplicationAccountInfo.removeObserver(returnApplicationAccountInfoObserver)

        repository.setEmailReturnValue.removeObserver(setEmailReturnValueObserver)

        repository.setAlgorithmSearchOptionsReturnValue.removeObserver(
            setAlgorithmSearchOptionsReturnValueObserver
        )
        repository.setOptedInToPromotionalEmailsReturnValue.removeObserver(
            setOptedInToPromotionalEmailsUpdatedObserver
        )
        repository.setBioReturnValue.removeObserver(setBioReturnValueObserver)
        repository.setCityReturnValue.removeObserver(setCityReturnValueObserver)
        repository.setAgeRangeReturnValue.removeObserver(setAgeRangeReturnValueObserver)
        repository.setMaxDistanceReturnValue.removeObserver(setMaxDistanceReturnValueObserver)
        repository.setGenderReturnValue.removeObserver(setGenderReturnValueObserver)
        repository.setGenderRangeReturnValue.removeObserver(setGenderRangeReturnValueObserver)

        repository.returnAllChatRooms.removeObserver(returnAllChatRoomsObserver)
        repository.returnSingleChatRoom.removeObserver(returnSingleChatRoomObserver)
        repository.returnSingleChatRoomNotFound.removeObserver(returnSingleChatRoomNotFoundObserver)
        repository.returnMessagesForChatRoom.removeObserver(returnMessagesForChatRoomObserver)
        //repository?.returnSingleChatMessageImage.removeObserver(returnSingleChatMessageImageObserver)
        repository.returnCreatedChatRoom.removeObserver(returnCreatedChatRoomObserver)
        repository.returnUpdatedOtherUser.removeObserver(returnUpdatedOtherUserObserver)
        repository.updatePicturesUpdateAttemptedTimestampByAccountOIDs.removeObserver(
            updatePicturesUpdateAttemptedTimestampByAccountOIDsObserver
        )
        repository.returnLeaveChatRoomResult.removeObserver(returnLeaveChatRoomResultObserver)
        repository.returnJoinChatRoomResult.removeObserver(returnJoinChatRoomResultObserver)
        repository.returnGrpcFunctionErrorStatusEnumToActivity.removeObserver(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )
        findMatchesObject.returnGrpcFunctionErrorStatusEnumToActivity.removeObserver(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )

        repository.returnBlockReportChatRoomResult.removeObserver(
            returnBlockReportChatRoomResultObserver
        )

        repository.returnSetPinnedLocationFailed.removeObserver(
            returnSetPinnedLocationFailedObserver
        )

        repository.returnEmailVerificationReturnValue.removeObserver(
            returnEmailVerificationReturnValueObserver
        )

        repository.matchRemovedOnJoinChatRoom.removeObserver(matchRemovedOnJoinChatRoomObserver)
        repository.returnAccountStateUpdated.removeObserver(returnAccountStateUpdatedObserver)
        repository.returnChatRoomInfoUpdatedData.removeObserver(returnChatRoomInfoUpdatedObserver)
        repository.returnChatRoomEventOidUpdated.removeObserver(
            returnChatRoomEventOidUpdatedObserver
        )
        repository.returnQrInfoUpdated.removeObserver(returnQrInfoUpdatedObserver)
        repository.displayToastFromActivity.removeObserver(displayToastFromActivityObserver)
        repository.returnClearHistoryFromChatRoom.removeObserver(
            returnClearHistoryFromChatRoomObserver
        )

        repository.chatRoomSearchResults.removeObserver(chatRoomSearchResultsObserver)
        repository.allChatRoomMessagesHaveBeenObservedResults.removeObserver(
            allChatRoomMessagesHaveBeenObservedResultsObserver
        )

        selectPicturesRepository.setFirstPictureReturnValue.removeObserver(
            setFirstPictureReturnValueObserver
        )
        selectPicturesRepository.returnGrpcFunctionErrorStatusEnumToActivity.removeObserver(
            returnGrpcFunctionErrorStatusEnumToActivityObserver
        )

        selectCategoriesRepository.setCategoriesUpdatedForViewModel.removeObserver(
            setCategoriesUpdatedForViewModelObserver
        )

        clearHandlers()

        //The object will be destroyed when this is cleared, no reason to run find matches anymore.
        findMatchesObject.findMatchesClientRunning.set(false)
    }

    fun sendSharedApplicationError(
        errorString: String,
        lineNumber: Int,
        fileName: String = Thread.currentThread().stackTrace[2].fileName,
        stackTrace: String = printStackTraceForErrors(),
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.sendApplicationError(
                errorString,
                lineNumber,
                stackTrace,
                fileName,
                errorStore,
                ioDispatcher
            )
        }
    }
}

/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 */
class SharedApplicationViewModelFactory(
    private val repository: ApplicationRepository,
    private val chatStreamObject: ChatStreamObject,
    private val selectPicturesRepository: SelectPicturesRepository,
    private val selectCategoriesRepository: SelectCategoriesRepository,
    private val loginSupportFunctions: LoginSupportFunctions,
    private val errorStore: StoreErrorsInterface = StoreErrors(),
    private val ioDispatcher: CoroutineDispatcher
) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedApplicationViewModel::class.java)) {
            return SharedApplicationViewModel(
                repository,
                chatStreamObject,
                selectPicturesRepository,
                selectCategoriesRepository,
                loginSupportFunctions,
                errorStore,
                ioDispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}