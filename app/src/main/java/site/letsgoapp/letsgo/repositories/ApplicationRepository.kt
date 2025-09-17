package site.letsgoapp.letsgo.repositories

import account_state.AccountState
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.content.Context
import android.util.Log
import chat_message_to_client.ChatMessageToClientMessage
import feedback_enum.FeedbackTypeEnum
import grpc_chat_commands.ChatRoomCommands
import kotlinx.coroutines.*
import report_enums.ReportMessages
import setfields.SetFeedbackRequest
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ApplicationAccountInfo
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.ChatRoomSortMethodSelected
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MostRecentMessageDataHolder
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs.*
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import status_enum.StatusEnum
import type_of_chat_message.TypeOfChatMessageOuterClass
import update_other_user_messages.UpdateOtherUserMessages

class ApplicationRepository(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val chatRoomsDataSource: ChatRoomsIntermediateInterface,
    private val messagesDataSource: MessagesDaoIntermediateInterface,
    private val otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    private val mimeTypeDaoDataSource: MimeTypeDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val _returnGrpcFunctionErrorStatusEnumToActivity: KeyStringMutableLiveData<GrpcFunctionErrorStatusEnum> =
        KeyStringMutableLiveData()
    val returnGrpcFunctionErrorStatusEnumToActivity: KeyStringLiveData<GrpcFunctionErrorStatusEnum> =
        _returnGrpcFunctionErrorStatusEnumToActivity

    //return database values for user account
    private val _returnApplicationAccountInfo: KeyStringMutableLiveData<ApplicationAccountInfoDataHolder> =
        KeyStringMutableLiveData()
    val returnApplicationAccountInfo: KeyStringLiveData<ApplicationAccountInfoDataHolder> =
        _returnApplicationAccountInfo

    /** Start of SetFields return types **/
    //post login info return values, these must be set separately to account for the timestamp updating as late as possible
    //for example: Bio and City get sent to the server and Bio goes through the server first then City
    //if City comes back and the timestamp is updated first then Bio could have problems
    //results of SetEmailRPC
    private val _setEmailReturnValue: KeyStringMutableLiveData<SetEmailReturnValues> =
        KeyStringMutableLiveData()
    val setEmailReturnValue: KeyStringLiveData<SetEmailReturnValues> =
        _setEmailReturnValue

    private val _setAlgorithmSearchOptionsReturnValue: KeyStringMutableLiveData<SetAlgorithmSearchOptionsReturnValues> =
        KeyStringMutableLiveData()
    val setAlgorithmSearchOptionsReturnValue: KeyStringLiveData<SetAlgorithmSearchOptionsReturnValues> =
        _setAlgorithmSearchOptionsReturnValue

    private val _setOptedInToPromotionalEmailsReturnValue: KeyStringMutableLiveData<SetOptedInToPromotionalEmailsReturnValues> =
        KeyStringMutableLiveData()
    val setOptedInToPromotionalEmailsReturnValue: KeyStringLiveData<SetOptedInToPromotionalEmailsReturnValues> =
        _setOptedInToPromotionalEmailsReturnValue

    private val _setBioReturnValue: KeyStringMutableLiveData<SetBioReturnValues> =
        KeyStringMutableLiveData()
    val setBioReturnValue: KeyStringLiveData<SetBioReturnValues> =
        _setBioReturnValue

    private val _setCityReturnValue: KeyStringMutableLiveData<SetCityReturnValues> =
        KeyStringMutableLiveData()
    val setCityReturnValue: KeyStringLiveData<SetCityReturnValues> =
        _setCityReturnValue

    private val _setAgeRangeReturnValue: KeyStringMutableLiveData<SetAgeRangeReturnValues> =
        KeyStringMutableLiveData()
    val setAgeRangeReturnValue: KeyStringLiveData<SetAgeRangeReturnValues> =
        _setAgeRangeReturnValue

    private val _setMaxDistanceReturnValue: KeyStringMutableLiveData<SetMaxDistanceReturnValues> =
        KeyStringMutableLiveData()
    val setMaxDistanceReturnValue: KeyStringLiveData<SetMaxDistanceReturnValues> =
        _setMaxDistanceReturnValue

    private val _setGenderReturnValue: KeyStringMutableLiveData<SetGenderReturnValues> =
        KeyStringMutableLiveData()
    val setGenderReturnValue: KeyStringLiveData<SetGenderReturnValues> =
        _setGenderReturnValue

    private val _setGenderRangeReturnValue: KeyStringMutableLiveData<SetGenderRangeReturnValue> =
        KeyStringMutableLiveData()
    val setGenderRangeReturnValue: KeyStringLiveData<SetGenderRangeReturnValue> =
        _setGenderRangeReturnValue

    /** End of SetFields return types **/

    private val _returnEmailVerificationReturnValue: KeyStringMutableLiveData<EmailVerificationReturnValues> =
        KeyStringMutableLiveData()
    val returnEmailVerificationReturnValue: KeyStringLiveData<EmailVerificationReturnValues> =
        _returnEmailVerificationReturnValue

    private val _returnAllChatRooms: KeyStringMutableLiveData<ReturnAllChatRoomsDataHolder> =
        KeyStringMutableLiveData()
    val returnAllChatRooms: KeyStringLiveData<ReturnAllChatRoomsDataHolder> =
        _returnAllChatRooms

    private val _returnCreatedChatRoom: KeyStringMutableLiveData<CreatedChatRoomReturnValueDataHolder> =
        KeyStringMutableLiveData()
    val returnCreatedChatRoom: KeyStringLiveData<CreatedChatRoomReturnValueDataHolder> =
        _returnCreatedChatRoom

    private val _returnLeaveChatRoomResult: KeyStringMutableLiveData<LeaveChatRoomReturnDataHolder> =
        KeyStringMutableLiveData()
    val returnLeaveChatRoomResult: KeyStringLiveData<LeaveChatRoomReturnDataHolder> =
        _returnLeaveChatRoomResult

    //this is used to return errors to the fragment, if chat room is successfully joined _returnSingleChatRoom will be set
    private val _returnJoinChatRoomResult: KeyStringMutableLiveData<DataHolderWrapper<JoinChatRoomReturnValues>> =
        KeyStringMutableLiveData()
    val returnJoinChatRoomResult: KeyStringLiveData<DataHolderWrapper<JoinChatRoomReturnValues>> =
        _returnJoinChatRoomResult

    private val _returnBlockReportChatRoomResult: KeyStringMutableLiveData<BlockAndReportChatRoomResultsHolder> =
        KeyStringMutableLiveData()
    val returnBlockReportChatRoomResult: KeyStringLiveData<BlockAndReportChatRoomResultsHolder> =
        _returnBlockReportChatRoomResult

    private val _returnSetPinnedLocationFailed: KeyStringMutableLiveData<String> =
        KeyStringMutableLiveData()
    val returnSetPinnedLocationFailed: KeyStringLiveData<String> =
        _returnSetPinnedLocationFailed

    private val _returnChatRoomInfoUpdatedData: KeyStringMutableLiveData<UpdateChatRoomInfoResultsDataHolder> =
        KeyStringMutableLiveData()
    val returnChatRoomInfoUpdatedData: KeyStringLiveData<UpdateChatRoomInfoResultsDataHolder> =
        _returnChatRoomInfoUpdatedData

    private val _returnChatRoomEventOidUpdated: KeyStringMutableLiveData<ReturnChatRoomEventOidUpdated> =
        KeyStringMutableLiveData()
    val returnChatRoomEventOidUpdated: KeyStringLiveData<ReturnChatRoomEventOidUpdated> =
        _returnChatRoomEventOidUpdated

    private val _returnQrInfoUpdated: KeyStringMutableLiveData<ReturnQrCodeInfoUpdated> =
        KeyStringMutableLiveData()
    val returnQrInfoUpdated: KeyStringLiveData<ReturnQrCodeInfoUpdated> =
        _returnQrInfoUpdated

    private val _displayToastFromActivity: KeyStringMutableLiveData<String> =
        KeyStringMutableLiveData()
    val displayToastFromActivity: KeyStringLiveData<String> =
        _displayToastFromActivity

    private val _matchRemovedOnJoinChatRoom: KeyStringMutableLiveData<ReturnMatchRemovedOnJoinChatRomDataHolder> =
        KeyStringMutableLiveData()
    val matchRemovedOnJoinChatRoom: KeyStringLiveData<ReturnMatchRemovedOnJoinChatRomDataHolder> =
        _matchRemovedOnJoinChatRoom

    private val _returnAccountStateUpdated: KeyStringMutableLiveData<AccountStateUpdatedDataHolder> =
        KeyStringMutableLiveData()
    val returnAccountStateUpdated: KeyStringLiveData<AccountStateUpdatedDataHolder> =
        _returnAccountStateUpdated

    private val _returnSingleChatRoom: KeyStringMutableLiveData<ReturnSingleChatRoomDataHolder> =
        KeyStringMutableLiveData()
    val returnSingleChatRoom: KeyStringLiveData<ReturnSingleChatRoomDataHolder> =
        _returnSingleChatRoom

    private val _returnSingleChatRoomNotFound: KeyStringMutableLiveData<ReturnSingleChatRoomNotFoundDataHolder> =
        KeyStringMutableLiveData()
    val returnSingleChatRoomNotFound: KeyStringLiveData<ReturnSingleChatRoomNotFoundDataHolder> =
        _returnSingleChatRoomNotFound

    private val _returnMessagesForChatRoom: KeyStringMutableLiveData<DataHolderWrapper<ReturnMessagesForChatRoomDataHolder>> =
        KeyStringMutableLiveData()
    val returnMessagesForChatRoom: KeyStringLiveData<DataHolderWrapper<ReturnMessagesForChatRoomDataHolder>> =
        _returnMessagesForChatRoom

    private val _returnUpdatedOtherUser: KeyStringMutableLiveData<ReturnUpdatedOtherUserRepositoryDataHolder> =
        KeyStringMutableLiveData()
    val returnUpdatedOtherUser: KeyStringLiveData<ReturnUpdatedOtherUserRepositoryDataHolder> =
        _returnUpdatedOtherUser

    private val _updatePicturesUpdateAttemptedTimestampByAccountOIDs: KeyStringMutableLiveData<UpdatePicturesUpdateAttemptedTimestampByAccountOIDsDataHolder> =
        KeyStringMutableLiveData()
    val updatePicturesUpdateAttemptedTimestampByAccountOIDs: KeyStringLiveData<UpdatePicturesUpdateAttemptedTimestampByAccountOIDsDataHolder> =
        _updatePicturesUpdateAttemptedTimestampByAccountOIDs

    private val _returnClearHistoryFromChatRoom: KeyStringMutableLiveData<ReturnClearHistoryFromChatRoomDataHolder> =
        KeyStringMutableLiveData()
    val returnClearHistoryFromChatRoom: KeyStringLiveData<ReturnClearHistoryFromChatRoomDataHolder> =
        _returnClearHistoryFromChatRoom

    //return a list of chatRoomIds matching the passed text
    private val _chatRoomSearchResults: KeyStringMutableLiveData<ChatRoomSearchResultsDataHolder> =
        KeyStringMutableLiveData()
    val chatRoomSearchResults: KeyStringLiveData<ChatRoomSearchResultsDataHolder> =
        _chatRoomSearchResults

    //returns the result to show if the little red dot should be shown on the messenger icon in the bottom menu
    private val _allChatRoomMessagesHaveBeenObservedResults: KeyStringMutableLiveData<AllChatRoomMessagesHaveBeenObservedHolder> =
        KeyStringMutableLiveData()
    val allChatRoomMessagesHaveBeenObservedResults: KeyStringLiveData<AllChatRoomMessagesHaveBeenObservedHolder> =
        _allChatRoomMessagesHaveBeenObservedResults

    var mostRecentFragmentInstanceID: String =
        "~" //this is set when currentFragmentInstanceId is set in SharedApplicationViewModel

    private val updateSingleUserLock = LockByIdMap()
    private val updateMembersFromChatRoomLock = LockByIdMap()

    private enum class TypeOfOtherUserUpdate {
        SINGLE_CHAT_ROOM_OTHER_USER_UPDATE,
        WHOLE_CHAT_ROOM_OTHER_USER_UPDATE,
        MATCH_OTHER_USER_UPDATE
    }

    suspend fun beginLoginToServerWhenReceivedInvalidToken() =
        withContext(ioDispatcher) {
            (applicationContext as LetsGoApplicationClass)
                .loginFunctions
                .beginLoginToServerWhenReceivedInvalidToken("")
        }

    suspend fun removeChatRoomQrCode(
        chatRoomId: String,
        prevQRCodePath: String,
    ) = withContext(ioDispatcher) {
        if (prevQRCodePath != GlobalValues.server_imported_values.qrCodeDefault) {
            deleteFileInterface.sendFileToWorkManager(
                prevQRCodePath
            )
        }

        chatRoomsDataSource.setQrCodeValues(
            chatRoomId,
            GlobalValues.server_imported_values.qrCodeDefault,
            GlobalValues.server_imported_values.qrCodeMessageDefault,
            GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
        )
    }

    suspend fun getPictureAndAccountValuesFromDatabase(
        fragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        var pictureInfo: MutableList<AccountPictureDataEntity>? = null
        var accountInfo: ApplicationAccountInfo? = null

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.ACCOUNTS
        )

        transactionWrapper.runTransaction {
            pictureInfo = accountPicturesDataSource.getAllPictures()
            accountInfo = accountInfoDataSource.getApplicationAccountInfo(this)
        }

        if (accountInfo == null) {

            val errorMessage =
                "When attempting to retrieve account values from database, account did not exist.\n"

            sendApplicationError(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                    sharedApplicationViewModelInstanceId
                )
            }

        } else {
            withContext(Dispatchers.Main) {
                _returnApplicationAccountInfo.setValue(
                    ApplicationAccountInfoDataHolder(
                        pictureInfo!!,
                        accountInfo!!,
                        fragmentInstanceID
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //sets the post login timestamp
    suspend fun setPostLoginTimestamp(
        timestamp: Long,
    ) = withContext(ioDispatcher) {
        accountInfoDataSource.setPostLoginTimestamp(timestamp)
    }

    //sets the email on the server
    suspend fun setEmailAddress(
        emailAddress: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setEmailClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            emailAddress
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {
                _setEmailReturnValue.setValue(
                    SetEmailReturnValues(
                        returnVal,
                        emailAddress
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun setAlgorithmSearchOptions(
        algorithmSearchOptions: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setAlgorithmSearchOptionsClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            algorithmSearchOptions
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            withContext(Dispatchers.Main) {
                _setAlgorithmSearchOptionsReturnValue.setValue(
                    SetAlgorithmSearchOptionsReturnValues(
                        returnVal.invalidParameterPassed,
                        returnVal.errorStatus,
                        algorithmSearchOptions
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun setOptedInToPromotionalEmails(
        optedInForPromotionalEmails: Boolean,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setOptedInToPromotionalEmailsClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            optedInForPromotionalEmails
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            withContext(Dispatchers.Main) {
                _setOptedInToPromotionalEmailsReturnValue.setValue(
                    SetOptedInToPromotionalEmailsReturnValues(
                        returnVal.invalidParameterPassed,
                        returnVal.errorStatus,
                        optedInForPromotionalEmails
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //sets the user bio on the server
    suspend fun setUserBio(
        userBio: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setBioClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            userBio
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            withContext(Dispatchers.Main) {
                _setBioReturnValue.setValue(
                    SetBioReturnValues(
                        returnVal.invalidParameterPassed,
                        returnVal.errorStatus,
                        returnVal.updatedTimestamp,
                        userBio
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //sets the user city on the server
    suspend fun setUserCity(
        userCity: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = setCityClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
            userCity
        )

        if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            withContext(Dispatchers.Main) {
                _setCityReturnValue.setValue(
                    SetCityReturnValues(
                        returnVal.invalidParameterPassed,
                        returnVal.errorStatus,
                        returnVal.updatedTimestamp,
                        userCity
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //sets the age range on the server
    suspend fun setAgeRange(
        ageRange: AgeRangeHolder,
        sharedApplicationViewModelInstanceId: String
    ): GrpcFunctionErrorStatusEnum {
        return withContext(ioDispatcher) {

            val returnVal = setAgeRangeClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                ageRange
            )

            if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
                withContext(Dispatchers.Main) {
                    _setAgeRangeReturnValue.setValue(
                        SetAgeRangeReturnValues(
                            returnVal.invalidParameterPassed,
                            returnVal.errorStatus,
                            returnVal.updatedTimestamp,
                            ageRange
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            return@withContext returnVal.errorStatus
        }
    }


    //sets the max distance on the server
    suspend fun setMaxDistance(
        maxDistance: Int,
        sharedApplicationViewModelInstanceId: String
    ): GrpcFunctionErrorStatusEnum {
        return withContext(ioDispatcher) {

            val returnVal = setMaxDistanceClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                maxDistance
            )

            if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

                withContext(Dispatchers.Main) {
                    _setMaxDistanceReturnValue.setValue(
                        SetMaxDistanceReturnValues(
                            returnVal.invalidParameterPassed,
                            returnVal.errorStatus,
                            returnVal.updatedTimestamp,
                            maxDistance
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            return@withContext returnVal.errorStatus
        }
    }

    //sets the gender on the server
    suspend fun setGender(
        gender: String,
        sharedApplicationViewModelInstanceId: String
    ): GrpcFunctionErrorStatusEnum {
        return withContext(ioDispatcher) {

            val returnVal = setGenderClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                gender
            )

            if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

                withContext(Dispatchers.Main) {
                    _setGenderReturnValue.setValue(
                        SetGenderReturnValues(
                            returnVal.invalidParameterPassed,
                            returnVal.errorStatus,
                            returnVal.updatedTimestamp,
                            gender
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            return@withContext returnVal.errorStatus
        }
    }

    //sets the gender range on the server
    suspend fun setGenderRange(
        genderRange: String,
        sharedApplicationViewModelInstanceId: String
    ): GrpcFunctionErrorStatusEnum {
        return withContext(ioDispatcher) {

            val returnVal = setGenderRangeClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                genderRange
            )

            if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

                withContext(Dispatchers.Main) {
                    _setGenderRangeReturnValue.setValue(
                        SetGenderRangeReturnValue(
                            returnVal.invalidParameterPassed,
                            returnVal.errorStatus,
                            returnVal.updatedTimestamp,
                            genderRange
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            return@withContext returnVal.errorStatus
        }
    }

    suspend fun sendFeedback(
        info: String,
        feedbackType: FeedbackTypeEnum.FeedbackType,
        activityName: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            val request =
                SetFeedbackRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setFeedbackType(feedbackType)
                    .setInfo(info)
                    .setActivityName(activityName)
                    .build()

            //send request to server
            val response = clientsIntermediate.setFieldsClientFeedback(request)

            val errorReturn = checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

            if (errorReturn.first != "~") {

                val errorMessage = "Send feedback has unknown error occur.\n" +
                        "info: $info\n" +
                        "feedbackType: $feedbackType\n" +
                        "activityName: $activityName\n" +
                        "loginToken: $loginToken\n"

                errorMessageRepositoryHelper(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors(),
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    errorHandling,
                    ioDispatcher
                )
            }

            if (errorReturn.second != GrpcFunctionErrorStatusEnum.DO_NOTHING
                && errorReturn.second != GrpcFunctionErrorStatusEnum.NO_ERRORS
            ) { //if send feedback failed
                withContext(Dispatchers.Main) {
                    _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                        sharedApplicationViewModelInstanceId
                    )
                }
            }
        } else { //if login token not good

            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun sendApplicationError(
        errorString: String,
        lineNumber: Int,
        stackTrace: String,
        fileName: String = Thread.currentThread().stackTrace[2].fileName,
        errorHandlingInterface: StoreErrorsInterface = errorHandling,
        dispatcher: CoroutineDispatcher = ioDispatcher
    ) = withContext(ioDispatcher) {

        errorMessageRepositoryHelper(
            errorString,
            lineNumber,
            fileName,
            stackTrace,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            errorHandlingInterface,
            dispatcher
        )
    }

    //this is synchronized to guarantee the most recent message is sent back to the viewModel
    suspend fun getChatRoomsFromDatabase(
        chatRoomListCalledFrom: ChatRoomListCalledFrom,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.OTHER_USERS,
            DatabasesToRunTransactionIn.MESSAGES
        )

        transactionWrapper.runTransaction {

            val allChatRooms = chatRoomsDataSource.getAllChatRooms()
            val chatRoomsToRemove = mutableListOf<String>()

            for (i in allChatRooms.indices) {

                initializeMemberListForChatRoom(
                    allChatRooms[i],
                    allChatRooms[i].chatRoomId,
                    otherUsersDaoDataSource
                )

                if (allChatRooms[i].matchingChatRoomOID != "") { //if matching chat room

                    if (allChatRooms[i].chatRoomMembers.size() != 1) {

                        if (allChatRooms[i].chatRoomMembers.size() == 0) {  //if there are no members inside the chat room
                            //set this matching chat room to be removed (and will run a leave chat room on it)
                            chatRoomsToRemove.add(allChatRooms[i].chatRoomId)
                        } else { //if there are more than one members inside the chat room
                            //re-create list with only first other user inside of chat room
                            allChatRooms[i].chatRoomMembers.getFromList(0)?.let { otherUserInfo ->
                                allChatRooms[i] = ChatRoomWithMemberMapDataClass(
                                    allChatRooms[i],
                                    otherUserInfo.otherUsersDataEntity
                                )
                            }
                        }

                        val errorMessage =
                            "When retrieving a 'match made' account from the database, there was an incorrect number of members.\n" +
                                    "chatRoomMembers.size(): ${allChatRooms[i].chatRoomMembers.size()}\n" +
                                    "chatRoomId: ${allChatRooms[i].chatRoomId}\n" +
                                    "chatRoomName: ${allChatRooms[i].chatRoomName}\n" +
                                    "chatRoomPassword: ${allChatRooms[i].chatRoomPassword}\n" +
                                    "notificationsEnabled: ${allChatRooms[i].notificationsEnabled}\n" +
                                    "accountState: ${allChatRooms[i].accountState}\n" +
                                    "timeJoined: ${allChatRooms[i].timeJoined}\n" +
                                    "matchingChatRoomOID: ${allChatRooms[i].matchingChatRoomOID}\n" +
                                    "chatRoomLastObservedTime: ${allChatRooms[i].chatRoomLastObservedTime}\n" +
                                    "userLastActivityTime: ${allChatRooms[i].userLastActivityTime}\n" +
                                    "chatRoomLastActivityTime: ${allChatRooms[i].chatRoomLastActivityTime}\n" +
                                    "lastTimeUpdated: ${allChatRooms[i].lastTimeUpdated}\n" +
                                    "finalMessage: ${allChatRooms[i].finalMessage}\n" +
                                    "finalPictureMessage: ${allChatRooms[i].finalPictureMessage}\n" +
                                    "displayChatRoom: ${allChatRooms[i].displayChatRoom}\n" +
                                    "showLoading: ${allChatRooms[i].showLoading}\n"

                        sendApplicationError(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }

                } else { //if this is NOT a matching chat room
                    val finalMessageAndProperActivityTime = getMostRecentMessageInChatRoom(
                        allChatRooms[i].chatRoomId,
                        allChatRooms[i].chatRoomLastActivityTime,
                        allChatRooms[i].timeJoined
                    )

                    allChatRooms[i].chatRoomLastActivityTime =
                        finalMessageAndProperActivityTime.second
                    allChatRooms[i].hardSetNewFinalMessage(finalMessageAndProperActivityTime.first)
                }
            }

            val finalChatRoomsList =
                if (chatRoomsToRemove.isNotEmpty()) {
                    //will filter out any chat rooms that need to be removed
                    allChatRooms.filter { value ->
                        chatRoomsToRemove.find { chatRoomId ->
                            chatRoomId == value.chatRoomId
                        } == null
                    }.toMutableList()
                } else {
                    allChatRooms
                }

            this.runAfterTransaction {
                if (chatRoomsToRemove.isNotEmpty()) {
                    CoroutineScope(ioDispatcher).launch {
                        for (chatRoomId in chatRoomsToRemove) {
                            //Leave chat room has functionality built into it to handle if this is a matching chat room or not, so it will
                            // check in on the server and handle it either way.
                            leaveChatRoom(chatRoomId, sharedApplicationViewModelInstanceId)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _returnAllChatRooms.setValue(
                        ReturnAllChatRoomsDataHolder(
                            finalChatRoomsList,
                            chatRoomListCalledFrom
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }
        }
    }

    suspend fun updateChatRoomsSortedType(chatRoomSortMethodSelected: ChatRoomSortMethodSelected) =
        withContext(ioDispatcher) {
            accountInfoDataSource.setChatRoomSortMethodSelected(chatRoomSortMethodSelected)
        }

    suspend fun beginChatRoomSearchForString(
        matchingString: String,
        thisFragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) =
        withContext(ioDispatcher) {

            //returns chatRoomIds with matching chatRoomID, chatRoomName, chatRoomPassword
            val matchingChatRoomsSet =
                chatRoomsDataSource.searchForChatRoomMatches(matchingString).toMutableSet()

            //returns chatRoomIds with matching other user name
            matchingChatRoomsSet.addAll(
                otherUsersDaoDataSource.searchForChatRoomMatches(
                    matchingString
                )
            )

            //returns chatRoomIds with matching messageText(if text message), replyIsFromMessageText, and messageValueChatRoomName
            matchingChatRoomsSet.addAll(messagesDataSource.searchForChatRoomMatches(matchingString))

            withContext(Dispatchers.Main) {
                _chatRoomSearchResults.setValue(
                    ChatRoomSearchResultsDataHolder(
                        matchingChatRoomsSet,
                        thisFragmentInstanceID
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }

    suspend fun sendMimeTypeMessage(
        messageEntity: MessagesDataEntity,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        mimeType: String,
        thumbnailForReply: ByteArray,
        fragmentInstanceID: String,
    ) = withContext(ioDispatcher) {

        (applicationContext as LetsGoApplicationClass).chatStreamObject
            .sendMimeTypeMessage(
                messageEntity,
                mimeTypeFilePath,
                mimeTypeWidth,
                mimeTypeHeight,
                mimeType,
                thumbnailForReply,
                fragmentInstanceID,
            )
    }

    suspend fun sendMessage(
        messageEntity: MessagesDataEntity,
        thumbnailForReply: ByteArray,
        fragmentInstanceID: String
    ) = withContext(ioDispatcher) {
        (applicationContext as LetsGoApplicationClass).chatStreamObject
            .sendMessage(
                messageEntity,
                thumbnailForReply,
                fragmentInstanceID,
                messageAlreadyStoredInDatabase = false,
                runInsideTransaction = {}
            )
    }

    //create a new chat room that this user is admin of
    suspend fun createChatRoom(
        chatRoomName: String,
        fragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runCreateChatRoom(
            chatRoomName,
            applicationContext,
            accountInfoDataSource,
            chatRoomsDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling
        )

        if (returnVal.first != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {

                //NOTE: error message has already been stored at this point
                _returnCreatedChatRoom.setValue(
                    CreatedChatRoomReturnValueDataHolder(
                        returnVal.first,
                        returnVal.second,
                        fragmentInstanceID
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //leave the passed chat room
    suspend fun leaveChatRoom(
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runLeaveChatRoom(
            chatRoomId,
            applicationContext,
            accountInfoDataSource,
            chatRoomsDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {

                //NOTE: error message has already been stored at this point
                _returnLeaveChatRoomResult.setValue(
                    LeaveChatRoomReturnDataHolder(
                        returnVal,
                        chatRoomId,
                        ReasonForLeavingChatRoom.USER_LEFT_CHAT_ROOM
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }

    }

    suspend fun removeUserFromChatRoom(
        chatRoomId: String,
        kickOrBan: ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan,
        accountOIDToRemove: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runRemoveFromChatRoom(
            chatRoomId,
            kickOrBan,
            accountOIDToRemove,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            chatRoomsDataSource,
            clientsIntermediate,
            errorHandling,
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING
            && returnVal != GrpcFunctionErrorStatusEnum.NO_ERRORS
        ) { //if this did not return 'DO_NOTHING' and was NOT successful
            //NOTE: if this was successful then the message was handled inside runRemoveFromChatRoom(), this is only when there
            // is an error that occurred
            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    returnVal,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun runBeginEmailVerification(
        callingFragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runBeginEmailVerification(
            callingFragmentInstanceID,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher,
        )

        if (returnVal.errors != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            if (returnVal.errors == GrpcFunctionErrorStatusEnum.NO_ERRORS
                && returnVal.response.emailAddressIsAlreadyVerified
            ) {
                accountInfoDataSource.setRequiresEmailAddressVerification(false)
            }

            withContext(Dispatchers.Main) {
                _returnEmailVerificationReturnValue.setValue(
                    returnVal,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun setNotificationsForChatRoom(
        chatRoomId: String,
        notificationsEnabled: Boolean,
    ) {
        chatRoomsDataSource.setNotificationsEnabled(
            chatRoomId,
            notificationsEnabled
        )
    }

    fun removeReplyPathFromThumbnail(
        uuidPrimaryKey: String,
        replyIsFromThumbnailFilePath: String
    ) {
        CoroutineScope(ioDispatcher).launch {
            messagesDataSource.updateReplyIsFromThumbnailFilePath(
                uuidPrimaryKey,
                replyIsFromThumbnailFilePath
            )
        }
    }

    suspend fun joinChatRoomFromInvite(
        uuidPrimaryKey: String,
        chatRoomId: String,
        chatRoomPassword: String,
        fragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        if (chatRoomsDataSource.chatRoomExists(chatRoomId)) { //if chat room is found in database
            retrieveSingleChatRoom(
                chatRoomId,
                fragmentInstanceID,
                chatRoomMustExist = true,
                sharedApplicationViewModelInstanceId
            )
        } else { //if chat room is not found in database
            joinChatRoom(
                chatRoomId,
                chatRoomPassword,
                fragmentInstanceID,
                sharedApplicationViewModelInstanceId,
                true,
                uuidPrimaryKey
            )
        }
    }

    //join the passed chat room if meets requirements
    suspend fun joinChatRoom(
        chatRoomId: String,
        chatRoomPassword: String,
        fragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String,
        joinedFromInvite: Boolean = false,
        uuidPrimaryKey: String = "",
    ) = withContext(ioDispatcher) {
        val returnVal: JoinChatRoomPrimerValues

        try {
            //If messages are sent for this chat room before it successfully receives the
            // THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE message, this object will catch them.
            (applicationContext as LetsGoApplicationClass).chatStreamObject.setupTemporaryChatRoom(
                chatRoomId
            )

            returnVal = runJoinChatRoom(
                chatRoomId,
                chatRoomPassword,
                applicationContext,
                clientsIntermediate,
                accountInfoDataSource,
                chatRoomsDataSource,
                accountPicturesDataSource,
                errorHandling,
                ioDispatcher
            ) { response, chatRoomStatus ->
                handleJoinChatRoomPrimer(
                    response,
                    chatRoomStatus,
                    chatRoomId,
                    chatRoomPassword,
                )
            }
        } finally {
            //Guarantee that this object is removed even though THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE will
            // remove it if anything happens.
            (applicationContext as LetsGoApplicationClass).chatStreamObject.removeTemporaryChatRoom(
                chatRoomId
            )
        }

        if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            return@withContext
        }

        if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
            && (returnVal.chatRoomStatus == ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED
                    || returnVal.chatRoomStatus == ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM)
        ) { //if successfully joined chat room
            if (returnVal.chatRoomStatus == ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM) {
                val errorMessage = "The client did not recognize it was inside of a chat room.\n" +
                        "chatRoomId: $chatRoomId\n" +
                        "chatRoomPassword: $chatRoomPassword\n" +
                        "joinedFromInvite: $joinedFromInvite\n" +
                        "uuidPrimaryKey: $uuidPrimaryKey\n" +
                        "returnVal: $returnVal\n"

                sendApplicationError(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )
            }

            Log.i(
                "joinChatRoomTime",
                "joinChatRoom() reached retrieveSingleChatRoom() returnVal: $returnVal"
            )

            //If this is an event chat room, remove the event as a potential match.
            val eventId = chatRoomsDataSource.getEventId(chatRoomId)
            if (eventId != GlobalValues.server_imported_values.eventIdDefault) {
                withContext(Dispatchers.Main) {
                    _matchRemovedOnJoinChatRoom.setValue(
                        ReturnMatchRemovedOnJoinChatRomDataHolder(
                            eventId,
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            retrieveSingleChatRoom(
                chatRoomId,
                fragmentInstanceID,
                chatRoomMustExist = true,
                sharedApplicationViewModelInstanceId
            )
        }
        else { //if error with joining chat room
            if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS && joinedFromInvite) { //if this join returned NO_ERRORS, however the join failed
                when (returnVal.chatRoomStatus) {
                    ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED,
                    ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST,
                    ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_ID,
                    ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_PASSWORD,
                    -> {

                        if (uuidPrimaryKey.isNotEmpty()) {
                            messagesDataSource.updateInviteMessageToExpired(uuidPrimaryKey)
                        }

                        withContext(Dispatchers.Main) {

                            _returnJoinChatRoomResult.setValue(
                                DataHolderWrapper(
                                    JoinChatRoomReturnValues(
                                        returnVal.errorStatus,
                                        returnVal.chatRoomStatus,
                                        chatRoomId,
                                        uuidPrimaryKey
                                    ),
                                    fragmentInstanceID
                                ),
                                sharedApplicationViewModelInstanceId
                            )

                        }
                    }
                    //Using an else here because there is a warning that SUCCESSFULLY_JOINED and
                    // ALREADY_IN_CHAT_ROOM can never be reached. However, if they are removed there
                    // is an error that 'when' must be exhaustive.
                    else -> {

                        val errorString =
                            "SUCCESSFULLY_JOINED & ALREADY_IN_CHAT_ROOM are already handled above.\n" +
                                    " and UNRECOGNIZED with NO_ERRORS should never happen"

                        sendApplicationError(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        withContext(Dispatchers.Main) {
                            _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                sharedApplicationViewModelInstanceId
                            )
                        }
                    }
                }
            } else { //if this was not a join from chat room or returned something other than NO_ERRORS
                withContext(Dispatchers.Main) {
                    _returnJoinChatRoomResult.setValue(
                        DataHolderWrapper(
                            JoinChatRoomReturnValues(
                                returnVal.errorStatus,
                                returnVal.chatRoomStatus,
                                chatRoomId,
                                uuidPrimaryKey
                            ),
                            fragmentInstanceID
                        ),
                        sharedApplicationViewModelInstanceId
                    )

                }
            }
        }
    }

    //leave the passed chat room
    suspend fun promoteNewAdmin(
        promotedUserAccountOID: String,
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runPromoteNewAdmin(
            promotedUserAccountOID,
            chatRoomId,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            chatRoomsDataSource,
            clientsIntermediate,
            errorHandling,
        )

        if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
            && !returnVal.userAccountStatesMatched
        ) {

            val promoteFailed = applicationContext.getString(
                site.letsgoapp.letsgo.R.string.promote_new_admin_failed
            )

            //Reaching this block means the current user was not ACCOUNT_STATE_IS_ADMIN
            // or the target user was not ACCOUNT_STATE_IN_CHAT_ROOM.
            withContext(Dispatchers.Main) {
                _displayToastFromActivity.setValue(
                    promoteFailed,
                    sharedApplicationViewModelInstanceId
                )
            }
        } else if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING
            && returnVal.errorStatus != GrpcFunctionErrorStatusEnum.NO_ERRORS
        ) { //if this did not return 'DO_NOTHING' and was not successful
            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    returnVal.errorStatus,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //leave the passed chat room
    suspend fun unMatchFromChatRoom(
        chatRoomId: String,
        matchAccountID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runUnMatchFromChatRoom(
            chatRoomId,
            matchAccountID,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {

                //NOTE: error message has already been stored at this point
                _returnLeaveChatRoomResult.setValue(
                    LeaveChatRoomReturnDataHolder(
                        returnVal,
                        chatRoomId,
                        ReasonForLeavingChatRoom.UN_MATCHED_FROM_CHAT_ROOM
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //leave the passed chat room
    suspend fun blockAndReportFromChatRoom(
        matchOptionsBuilder: ReportMessages.UserMatchOptionsRequest.Builder,
        chatRoomId: String,
        unMatch: Boolean,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runBlockAndReportFromChatRoom(
            matchOptionsBuilder,
            chatRoomId,
            unMatch,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            sharedApplicationViewModelInstanceId
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {

                //NOTE: error message has already been stored at this point
                _returnBlockReportChatRoomResult.setValue(
                    BlockAndReportChatRoomResultsHolder(
                        returnVal,
                        matchOptionsBuilder.matchAccountId,
                        true,
                        unMatch
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //unblock a specific user
    suspend fun unblockOtherUser(
        userToUnblockAccountId: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val returnVal = runUnblockOtherUser(
            userToUnblockAccountId,
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
            withContext(Dispatchers.Main) {

                //NOTE: error message has already been stored at this point
                _returnBlockReportChatRoomResult.setValue(
                    BlockAndReportChatRoomResultsHolder(
                        returnVal,
                        userToUnblockAccountId,
                        false
                    ),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun requestMessageUpdate(
        chatRoomId: String,
        amountOfMessage: TypeOfChatMessageOuterClass.AmountOfMessage,
        messageUUIDList: List<String>,
    ) = withContext(ioDispatcher) {

        (applicationContext as LetsGoApplicationClass).chatStreamObject.requestMessageInfo(
            chatRoomId,
            amountOfMessage,
            messageUUIDList
        )
    }

    //leave the passed chat room
    suspend fun updateChatRoomInfo(
        newChatRoomInfo: String,
        typeOfInfoToUpdate: ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate,
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String
    ) {
        withContext(ioDispatcher) {

            val returnVal = runUpdateChatRoomInfo(
                chatRoomId,
                typeOfInfoToUpdate,
                newChatRoomInfo,
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                chatRoomsDataSource,
                clientsIntermediate,
                errorHandling,
            )

            if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                && returnVal.operationFailed
            ) {

                val updateFailed = applicationContext.getString(
                    site.letsgoapp.letsgo.R.string.update_chat_room_info_failed,
                    when (typeOfInfoToUpdate) {
                        ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_NAME -> {
                            applicationContext.getString(site.letsgoapp.letsgo.R.string.lowercase_name)
                        }
                        ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_PASSWORD -> {
                            applicationContext.getString(site.letsgoapp.letsgo.R.string.lowercase_password)
                        }
                        else -> {
                            applicationContext.getString(site.letsgoapp.letsgo.R.string.info)
                        }
                    }
                )

                //Reaching this block means the current user was not ACCOUNT_STATE_IS_ADMIN.
                withContext(Dispatchers.Main) {
                    _displayToastFromActivity.setValue(
                        updateFailed,
                        sharedApplicationViewModelInstanceId
                    )
                }
            } else if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING
                && returnVal.errorStatus != GrpcFunctionErrorStatusEnum.NO_ERRORS
            ) { //if this did not return 'DO_NOTHING' and was not successful
                withContext(Dispatchers.Main) {
                    _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                        returnVal.errorStatus,
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

        }
    }

    suspend fun setPinnedLocation(
        longitude: Double,
        latitude: Double,
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String,
        fragmentInstanceId: String
    ) {
        withContext(ioDispatcher) {

            val returnVal = runSetPinnedLocation(
                chatRoomId,
                longitude,
                latitude,
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                chatRoomsDataSource,
                clientsIntermediate,
                errorHandling,
            )

            if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS
                && returnVal.operationFailed
            ) {
                val updateFailed = applicationContext.getString(
                    site.letsgoapp.letsgo.R.string.update_chat_room_info_failed,
                    applicationContext.getString(site.letsgoapp.letsgo.R.string.pinned_location)
                )

                withContext(Dispatchers.Main) {
                    _displayToastFromActivity.setValue(
                        updateFailed,
                        sharedApplicationViewModelInstanceId
                    )
                }
            } else if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING
                && returnVal.errorStatus != GrpcFunctionErrorStatusEnum.NO_ERRORS
            ) { //if this did not return 'DO_NOTHING' and was not successful
                withContext(Dispatchers.Main) {
                    _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                        returnVal.errorStatus,
                        sharedApplicationViewModelInstanceId
                    )

                    _returnSetPinnedLocationFailed.setValue(
                        fragmentInstanceId,
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

        }
    }

    //leave the passed chat room
    suspend fun clearHistoryFromChatRoom(
        chatRoomId: String,
        thisFragmentInstanceID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        if (chatRoomId != "~" && chatRoomId != "") {

            val historyClearedMessage = buildHistoryClearedMessageMessageDataEntity(
                chatRoomId
            )

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES,
                DatabasesToRunTransactionIn.OTHER_USERS
            )
            transactionWrapper.runTransaction {
                messagesDataSource.removeAllMessagesForChatRoom(
                    chatRoomId,
                    { picturePath ->
                        deleteFileInterface.sendFileToWorkManager(
                            picturePath
                        )
                    },
                    { gifURL ->
                        deleteGif(
                            mimeTypeDaoDataSource,
                            gifURL,
                            deleteFileInterface,
                            errorStore = errorHandling
                        )
                    },
                    false,
                    this
                )

                //ORDER of times; message_cap -> history_cleared -> chat_room_updated_time
                messagesDataSource.setChatRoomCapMessageStoredTime(
                    chatRoomId,
                    historyClearedMessage.messageStoredInDatabaseTime - 1
                )

                messagesDataSource.insertMessage(historyClearedMessage)

                chatRoomsDataSource.setSingleChatRoomLastTimeUpdatedLastTimeObserved(
                    chatRoomId,
                    historyClearedMessage.messageStoredInDatabaseTime + 1
                )

                this.runAfterTransaction {

                    (applicationContext as LetsGoApplicationClass).chatStreamObject.removeAllMessagesToUpdateFromQueue(
                        chatRoomId
                    )

                    withContext(Dispatchers.Main) {
                        _returnClearHistoryFromChatRoom.setValue(
                            ReturnClearHistoryFromChatRoomDataHolder(
                                historyClearedMessage,
                                thisFragmentInstanceID
                            ),
                            sharedApplicationViewModelInstanceId
                        )
                    }
                }
            }
        }
    }

    suspend fun updateInviteMessageToExpired(
        uuidPrimaryKey: String,
    ) = withContext(ioDispatcher) {
        messagesDataSource.updateInviteMessageToExpired(uuidPrimaryKey)
    }

    suspend fun checkIfAllChatRoomMessagesHaveBeenObserved(
        userLeftChatRoomId: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.MESSAGES,
            DatabasesToRunTransactionIn.OTHER_USERS
        )

        var returnValue: AllChatRoomMessagesHaveBeenObservedHolder? = null

        transactionWrapper.runTransaction {

            //NOTE: This would be a good time to use JOIN for the database query. However it can't be done because
            // the tables are inside different databases and room doesn't support ATTACH DATABASE.
            val allChatRoomMessagesObservedTime =
                chatRoomsDataSource.getAllChatRoomLastObservedTimes()
            val allChatRoomMessagesLastActiveTime =
                messagesDataSource.getMostRecentMessageForEachChatRoomIncludingBlocking()

            Log.i(
                "obz_timz",
                "allChatRoomMessagesObservedTime: $allChatRoomMessagesObservedTime allChatRoomMessagesLastActiveTime: $allChatRoomMessagesLastActiveTime"
            )

            val mostRecentMessage =
                messagesDataSource.getMostRecentMessageStoredTimeForAllChatRooms()

            this.runAfterTransaction {

                var allChatRoomMessagesHaveBeenObserved = true

                //both lists are sorted by chat room id in descending order
                var i = 0
                var j = 0
                while (i < allChatRoomMessagesObservedTime.size && j < allChatRoomMessagesLastActiveTime.size) {
                    when {
                        allChatRoomMessagesObservedTime[i].chatRoomID < allChatRoomMessagesLastActiveTime[j].chat_room_id -> {
                            //this means that an observed time exists for the chat room, however no messages that match the criteria
                            // where found
                            i++
                        }
                        allChatRoomMessagesObservedTime[i].chatRoomID > allChatRoomMessagesLastActiveTime[j].chat_room_id -> {
                            //This means that messages were found however no relevant chat room was found inside of the chat rooms database
                            val errorString =
                                "Messages were found inside database that have no associated chat room.\n" +
                                        "chatRoomsLastObservedTime: ${allChatRoomMessagesObservedTime}\n" +
                                        "chatRoomMessagesLastActiveTime: ${allChatRoomMessagesLastActiveTime}\n"

                            sendApplicationError(
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )
                            j++
                        }
                        else -> {
                            if (allChatRoomMessagesObservedTime[i].last_observed_time < allChatRoomMessagesLastActiveTime[j].message_stored_server_time) {
                                allChatRoomMessagesHaveBeenObserved = false
                                break
                            }
                            i++
                            j++
                        }
                    }
                }

                //NOTE: Could finish i and j here, however there is no point except to find a possible error and this function
                // running faster is better.

                Log.i(
                    "messagesStuff",
                    "allChatRoomMessagesHaveBeenObserved: $allChatRoomMessagesHaveBeenObserved"
                )

                returnValue = AllChatRoomMessagesHaveBeenObservedHolder(
                    allChatRoomMessagesHaveBeenObserved,
                    userLeftChatRoomId,
                    mostRecentMessage.messageUUIDPrimaryKey,
                    mostRecentMessage.sent_by_account_id,
                    mostRecentMessage.message_stored_server_time,
                    mostRecentMessage.chat_room_id,
                )
            }
        }

        withContext(Dispatchers.Main) {
            _allChatRoomMessagesHaveBeenObservedResults.setValue(
                returnValue!!,
                sharedApplicationViewModelInstanceId
            )
        }
    }

    enum class UpdateMemberReasonEnum {
        JOINED_CHAT_ROOM,
        UPDATE_OTHER_USER_IN_CHAT_ROOM,
        UPDATE_MATCH,
        ALGORITHM_MATCH_RETURN,
        MATCH_SUCCESSFULLY_MADE,
    }

    //will update the gif file path
    suspend fun updateMimeTypeFilePath(
        mimeTypeUrl: String,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
    ) {
        mimeTypeDaoDataSource.updateFilePath(
            mimeTypeUrl,
            mimeTypeFilePath,
            mimeTypeWidth,
            mimeTypeHeight
        )
    }

    //returns true if user exists inside chat room false if not
    suspend fun checkUserStateInsideChatRoom(
        userAccountOID: String,
        chatRoomId: String,
    ): AccountState.AccountStateInChatRoom? =
        withContext(ioDispatcher) {
            return@withContext otherUsersDaoDataSource.getOtherUserAccountStateInChatRoom(
                chatRoomId,
                userAccountOID
            )
        }

    suspend fun retrieveMessagesForChatRoomId(
        currentChatRoomID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        //NOTE: because of how observed time is set up it is very important that this does not filter out messages
        val messages = messagesDataSource.retrieveAllMessagesForChatRoom(currentChatRoomID)

        //NOTE: retrieve all mime types in case a new one is added that already exists inside of the list
        val allMimeTypes =
            mimeTypeDaoDataSource.retrieveMimeTypes().toMutableList()

        withContext(Dispatchers.Main) {
            _returnMessagesForChatRoom.setValue(
                DataHolderWrapper(
                    ReturnMessagesForChatRoomDataHolder(
                        chatRoomInitialization = true,
                        currentChatRoomID,
                        messages,
                        allMimeTypes
                    ),
                    mostRecentFragmentInstanceID
                ),
                sharedApplicationViewModelInstanceId
            )
        }
    }

    suspend fun getMostRecentMessageInChatRoom(
        chatRoomId: String,
        chatRoomLastActiveTime: Long,
        chatRoomTimeJoined: Long
    ): Pair<MostRecentMessageDataHolder?, Long> {

        val mostRecentMessage = messagesDataSource.getMostRecentMessageInChatRoom(chatRoomId)

        //If the most recent message stored time is not the same as the chat room last activity
        // time, it is possible (although not guaranteed) that the last activity time is
        // incorrect. This can happen because the actual lastActivityTime does not take
        // blocked accounts into consideration.
        val chatRoomActiveTime =
            if (mostRecentMessage == null
                || mostRecentMessage.highest_value <= 0 //this will be the server/database stored time
            ) {
                //It is possible that no most recent message exists. For example in a new chat room or
                // if the only messages that are sent are blocked. In this case use the joined time as the
                // 'most recent' activity time for the chat room.
                chatRoomTimeJoined
            } else if (
                mostRecentMessage.message_stored_server_time != chatRoomLastActiveTime
                && mostRecentMessage.message_stored_server_time >= 0
            ) {
                messagesDataSource.getLastActivityTimeNotIncludingBlocked(chatRoomId)
            } else {
                //If message_stored_server_time is -1 then this message has not been sent to the server yet
                // and the chat room last active time will not be updated to reflect it. This means that if
                // highest_value (database/server stored time) is used, it will have a red dot showing that
                // not all messages have been observed.
                chatRoomLastActiveTime
            }

        return Pair(mostRecentMessage, chatRoomActiveTime)
    }

    suspend fun retrieveSingleChatRoom(
        chatRoomId: String,
        fragmentInstanceID: String,
        chatRoomMustExist: Boolean,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        val chatRoom = chatRoomsDataSource.getSingleChatRoom(chatRoomId)

        if (chatRoom.chatRoomId != chatRoomId) { //chat room was not found in database
            if (chatRoomMustExist) {

                val errorMessage =
                    "When attempting to retrieve chat room from database, chat room did not exist.\n" +
                            "chatRoomId: $chatRoomId\n" +
                            "fragmentInstanceID: $fragmentInstanceID\n" +
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

                sendApplicationError(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                withContext(Dispatchers.Main) {
                    _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                        GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                        sharedApplicationViewModelInstanceId
                    )
                }

            } else {
                withContext(Dispatchers.Main) {
                    _returnSingleChatRoomNotFound.setValue(
                        ReturnSingleChatRoomNotFoundDataHolder(
                            chatRoomId,
                            fragmentInstanceID
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }
        } else { //chat room was found in database
            initializeMemberListForChatRoom(
                chatRoom,
                chatRoomId,
                otherUsersDaoDataSource
            )

            withContext(Dispatchers.Main) {
                _returnSingleChatRoom.setValue(
                    ReturnSingleChatRoomDataHolder(chatRoom, fragmentInstanceID),
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    suspend fun updateMessagesObservedTimes(
        messageUUIDPrimaryKeys: Set<String>,
    ) = withContext(ioDispatcher) {
        messagesDataSource.updateTimestampsForPassedMessageUUIDs(
            messageUUIDPrimaryKeys,
            getCurrentTimestampInMillis()
        )
    }

    suspend fun updateMimeTypesObservedTimes(
        mimeTypeURLs: Set<String>,
    ) = withContext(ioDispatcher) {
        mimeTypeDaoDataSource.updateMimeTypesObservedTimes(
            mimeTypeURLs,
            getCurrentTimestampInMillis()
        )
    }

    suspend fun updateOtherUserObservedTime(
        accountOID: String,
    ) = withContext(ioDispatcher) {
        otherUsersDaoDataSource.updateTimestampUserInfoLastObserved(
            accountOID,
            getCurrentTimestampInMillis()
        )
    }

    //this is mostly just run to update pictures, however it can be used to update everything
    //this function cannot update matches, see updateSingleMatchMemberInfo()
    suspend fun updateSingleChatRoomMemberInfo(
        chatRoomId: String,
        accountOID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            updateSingleUserLock.runWithLock(accountOID) { wasLocked ->

                if (wasLocked) {
                    //if function was already running for requested user, user was just updated, leave the dead end
                    return@runWithLock
                }

                val chatRoomMemberInfo =
                    otherUsersDaoDataSource.getSingleMemberInfoForChatRoomUpdates(
                        chatRoomId,
                        accountOID
                    )
                if (chatRoomMemberInfo != null) { //if chat room member was successfully extracted

                    val request = ChatRoomCommands.UpdateSingleChatRoomMemberRequest.newBuilder()
                        .setLoginInfo(getLoginInfo(loginToken))
                        .setChatRoomId(chatRoomId)
                        .setChatRoomMemberInfo(chatRoomMemberInfo)
                        .build()

                    //NOTE: Technically info is requested then the client is called. However, only
                    // one transaction can run at a time and don't want to block while the server request
                    // is run.

                    val response = clientsIntermediate.updateSingleChatRoomMember(request)

                    //This must be called every time in order to update picturesUpdateAttemptedTimestamp
                    //NOTE: This can return the current user's account oid in the response if
                    // the current user is not found inside the chat room (with AccountState set to
                    // NOT_IN_CHAT_ROOM).
                    handleUpdateMembersFromChatRoom(
                        chatRoomId,
                        response,
                        TypeOfOtherUserUpdate.SINGLE_CHAT_ROOM_OTHER_USER_UPDATE,
                        sharedApplicationViewModelInstanceId
                    )

                }
//                else { //if other user did not exist OR if the other user has been updated too recently
//                      //this is another dead end inside of this function, it is OK to not return anything here, all that will happen is the user will not update
//                      // however it was updated less than GlobalValues.timeBetweenUpdatingSingleUserFunctionRunning seconds ago or the user does not exist
//                }
            }

        } else { //login token invalid
            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //this is mostly just run to update pictures, however for matches it can be used to update everything
    suspend fun updateSingleMatchMemberInfo(
        accountOID: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {
        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            updateSingleUserLock.runWithLock(accountOID) { wasLocked ->

                if (wasLocked) {
                    //If function was already running for requested user, user was just updated, leave the dead end.
                    return@runWithLock
                }

                val memberInfo =
                    otherUsersDaoDataSource.getSingleMemberInfoForMatchUpdates(accountOID)

                Log.i("pictureInfoList", "updateSingleMatchMemberInfo memberInfo: $memberInfo")

                if (memberInfo != null) { //if matching member was successfully extracted

                    val request = user_match_options.UpdateSingleMatchMemberRequest.newBuilder()
                        .setLoginInfo(getLoginInfo(loginToken))
                        .setChatRoomMemberInfo(memberInfo)
                        .build()

                    //NOTE: Technically info is requested then the client is called, however because only
                    // one transaction can run at a time, don't want to block while the server request
                    // is run.
                    val response = clientsIntermediate.updateSingleMatchMember(request)

                    Log.i(
                        "updateSingleMa",
                        "androidErrorEnum: ${response.androidErrorEnum} returnStatus: ${response.response.returnStatus} matchStillValid: ${response.response.matchStillValid}"
                    )

                    //The server returns !matchStillValid for one of two reasons.
                    // 1) The other user no longer exists inside the database (their account was deleted)
                    // 2) The match is no longer valid. However, in order to return this invalid to the user
                    // it would need to not only remove a valid from the FindMatchesObject inside the
                    // SharedApplicationViewModel I would also need to send a signal to the MatchScreenFragment
                    // to remove the match from the screen. This could be confusing to the user.
                    if (response.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                        && response.response.returnStatus == StatusEnum.ReturnStatus.SUCCESS
                        && !response.response.matchStillValid
                    ) {
                        return@runWithLock
                    }

                    //This must be called every time in order to update picturesUpdateAttemptedTimestamp
                    handleUpdateMembersFromChatRoom(
                        "",
                        response,
                        TypeOfOtherUserUpdate.MATCH_OTHER_USER_UPDATE,
                        sharedApplicationViewModelInstanceId,
                    )

                }
//                else { //if other user did not exist OR if the other user has been updated too recently
//                      //this is another dead end inside of this function, it is OK to not return anything here, all that will happen is the user will not update
//                      // however it was updated less than GlobalValues.timeBetweenUpdatingSingleUserFunctionRunning seconds ago or the user does not exist
//                }
            }

        } else { //login token invalid
            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    /** requires same locks as removeChatRoomFromDatabase() **/
    suspend fun leaveChatRoomFromError(
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String,
        transactionWrapper: TransactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.MESSAGES,
            DatabasesToRunTransactionIn.OTHER_USERS
        )
    ) {

        transactionWrapper.runTransaction {
            //remove chat room
            (applicationContext as LetsGoApplicationClass).chatStreamObject
                .removeChatRoomFromDatabase(
                    chatRoomId,
                    this
                )
        }

        withContext(Dispatchers.Main) {
            _returnLeaveChatRoomResult.setValue(
                LeaveChatRoomReturnDataHolder(
                    GrpcFunctionErrorStatusEnum.NO_ERRORS,
                    chatRoomId,
                    ReasonForLeavingChatRoom.USER_LEFT_CHAT_ROOM
                ),
                sharedApplicationViewModelInstanceId
            )
        }
    }


    //This function will be run when a chat room is clicked on and the user is already a member of it, it will update all
    // members (the server will not update picture info if more than 5(?) members in the chat room), this function runs
    // 'in the background' and so the user is taken immediately to the chatRoomFragment, it is also all right if it fizzles
    // because the user is not online, the members can be updated individually if the connection comes back, the only thing
    // is that the other users in a chat room may not have perfectly up to date thumbnails if it fizzles (they should still exist
    // however
    //The functionality is included to request message updates as well, however because there is no delay (this function
    // is still running while requests are going) the chatRoomFragment itself can handle the messages
    suspend fun updateChatRoom(
        chatRoomId: String,
        sharedApplicationViewModelInstanceId: String
    ) = withContext(ioDispatcher) {

        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            updateMembersFromChatRoomLock.runWithLock(chatRoomId) { wasLocked ->

                if (wasLocked) {
                    //the chat room was already updated, leave this dead end here, no need for 2 returns
                    return@runWithLock
                }

                val request =
                    ChatRoomCommands.UpdateChatRoomRequest.newBuilder()
                        .setLoginInfo(getLoginInfo(loginToken))
                        .setChatRoomId(chatRoomId)

                //this is used twice as well, if the databases are changed the below use
                // will need to be checked as well
                val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                    applicationContext,
                    DatabasesToRunTransactionIn.MESSAGES,
                    DatabasesToRunTransactionIn.OTHER_USERS
                )

                var memberListForUpdated: MutableList<UpdateOtherUserMessages.OtherUserInfoForUpdates>? =
                    null

                transactionWrapper.runTransaction {

                    val chatRoomInfo = chatRoomsDataSource.getUpdateChatRoomInfo(chatRoomId)

                    if (chatRoomInfo == null) { //if the chat room info was not found

                        CoroutineScope(ioDispatcher).launch {
                            val errorString =
                                "A chat room was attempted to be updated on the client that does not exist\n" +
                                        "chatRoomId: " + chatRoomId + "\n" +
                                        "currentAccountOID: " + LoginFunctions.currentAccountOID + "\n"

                            sendApplicationError(
                                errorString,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )
                        }

                        withContext(Dispatchers.Main) {
                            _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                sharedApplicationViewModelInstanceId
                            )
                        }

                        return@runTransaction
                    }

                    memberListForUpdated = otherUsersDaoDataSource.getMemberInfoForChatRoomUpdates(
                        chatRoomId
                    )

                    request
                        //add current user for updates
                        .addChatRoomMemberInfo(
                            UpdateOtherUserMessages.OtherUserInfoForUpdates.newBuilder()
                                .setAccountOid(LoginFunctions.currentAccountOID)
                                .setAccountState(
                                    AccountState.AccountStateInChatRoom.forNumber(
                                        chatRoomInfo.user_state_in_chat_room
                                    )
                                )
                                .build()
                        )
                        //get member info for requesting updates
                        .addAllChatRoomMemberInfo(memberListForUpdated)
                        .setChatRoomName(chatRoomInfo.chat_room_name)
                        .setChatRoomPassword(chatRoomInfo.chat_room_password)
                        .setChatRoomLastActivityTime(chatRoomInfo.chat_room_last_active_time)
                        .setChatRoomLastUpdatedTime(chatRoomInfo.last_time_updated)
                        .setPinnedLocationLongitude(chatRoomInfo.pinned_location_longitude)
                        .setPinnedLocationLatitude(chatRoomInfo.pinned_location_latitude)
                        .setQrCodeLastTimestamp(chatRoomInfo.qr_code_time_updated)
                        .eventOid = chatRoomInfo.event_id

                    //NOTE: not implementing updating messages here, leaving it to the chatRoomFragment to request messages
                    //get message info for requesting updates
                    /*request
                        .addAllMessageUuidList(
                            messagesDataSource.getFinalMessagesRequiringUpdatesInChatRoom(
                                chatRoomId
                            )
                        )*/

                }

                clientsIntermediate.updateChatRoom(
                    chatRoomId,
                    request.build()
                ) { updateChatRoomResponse ->

                    var successful = true

                    if (updateChatRoomResponse.androidErrorEnum != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {

                        val chatRoomLastActiveTime =
                            chatRoomsDataSource.getSingleChatRoomLastActiveTime(chatRoomId)

                        if (chatRoomLastActiveTime != -1L) {

                            //update observed time; this is so that sorted messages will always be in place, if updateChatRoom
                            // does not complete then this will update the observed time on client and server side and store
                            // the message to be sent to the server later
                            val message = buildUpdateObservedTimeMessageDataEntity(
                                chatRoomId,
                                chatRoomLastActiveTime
                            )

                            sendMessage(
                                message,
                                byteArrayOf(),
                                mostRecentFragmentInstanceID
                            )
                        }

                        val returnValues = checkApplicationReturnStatusEnum(
                            StatusEnum.ReturnStatus.UNKNOWN, //irrelevant parameter if this is not NO_ANDROID_ERRORS
                            updateChatRoomResponse
                        )

                        if (returnValues.first != "~") {
                            CoroutineScope(ioDispatcher).launch {

                                sendApplicationError(
                                    returnValues.first,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors()
                                )
                            }
                        }

                        CoroutineScope(Dispatchers.Main).launch {
                            _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                                sharedApplicationViewModelInstanceId
                            )
                        }

                        successful = false

                    } else { //NO_ANDROID_ERRORS reached

                        Log.i(
                            "update_chat_room",
                            "case: ${updateChatRoomResponse.response.messageUpdateCase}"
                        )
                        when (updateChatRoomResponse.response.messageUpdateCase) {
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.CHAT_MESSAGES_LIST -> {
                                for (message in updateChatRoomResponse.response.chatMessagesList.messagesListList) {
                                    (applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                                        message,
                                        calledFromJoinChatRoom = false
                                    )
                                }
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.CHAT_ROOM_INFO -> {
                                //This message type will only be sent back if at least one of the 3 fields was updated
                                // each field will be sent back individually, if chatRoomName or chatRoomPassword
                                // where changed they will NOT be empty, if chatRoomLastActivityTime was updated
                                // it will be greater than 0 (they are left to the default values)
                                Log.i(
                                    "updateChatRoomInfo",
                                    "name: ${updateChatRoomResponse.response.chatRoomInfo.chatRoomName}; password: ${updateChatRoomResponse.response.chatRoomInfo.chatRoomPassword}; activity time: ${updateChatRoomResponse.response.chatRoomInfo.chatRoomLastActivityTime}"
                                )

                                chatRoomsDataSource.setUpdateChatRoomFunctionReturnValues(
                                    chatRoomId,
                                    updateChatRoomResponse.response.chatRoomInfo.chatRoomName,
                                    updateChatRoomResponse.response.chatRoomInfo.chatRoomPassword,
                                    updateChatRoomResponse.response.chatRoomInfo.eventOid,
                                    updateChatRoomResponse.response.chatRoomInfo.pinnedLocationLatitude,
                                    updateChatRoomResponse.response.chatRoomInfo.pinnedLocationLongitude,
                                    updateChatRoomResponse.response.chatRoomInfo.chatRoomLastActivityTime
                                )

                                withContext(Dispatchers.Main) {
                                    _returnChatRoomInfoUpdatedData.setValue(
                                        UpdateChatRoomInfoResultsDataHolder(
                                            buildMessageDataEntityForChatRoomInfoUpdated(
                                                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
                                                chatRoomId,
                                                updateChatRoomResponse.response.chatRoomInfo.chatRoomLastActivityTime,
                                                chatRoomInfo = updateChatRoomResponse.response.chatRoomInfo.chatRoomName,
                                            )
                                        ),
                                        sharedApplicationViewModelInstanceId
                                    )

                                    _returnChatRoomInfoUpdatedData.setValue(
                                        UpdateChatRoomInfoResultsDataHolder(
                                            buildMessageDataEntityForChatRoomInfoUpdated(
                                                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
                                                chatRoomId,
                                                updateChatRoomResponse.response.chatRoomInfo.chatRoomLastActivityTime,
                                                chatRoomInfo = updateChatRoomResponse.response.chatRoomInfo.chatRoomPassword,
                                            )
                                        ),
                                        sharedApplicationViewModelInstanceId
                                    )

                                    _returnChatRoomInfoUpdatedData.setValue(
                                        UpdateChatRoomInfoResultsDataHolder(
                                            buildMessageDataEntityForChatRoomInfoUpdated(
                                                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
                                                chatRoomId,
                                                updateChatRoomResponse.response.chatRoomInfo.chatRoomLastActivityTime,
                                                pinnedLongitude = updateChatRoomResponse.response.chatRoomInfo.pinnedLocationLongitude,
                                                pinnedLatitude = updateChatRoomResponse.response.chatRoomInfo.pinnedLocationLatitude,
                                            )
                                        ),
                                        sharedApplicationViewModelInstanceId
                                    )

                                    _returnChatRoomEventOidUpdated.setValue(
                                        ReturnChatRoomEventOidUpdated(
                                            chatRoomId,
                                            updateChatRoomResponse.response.chatRoomInfo.eventOid
                                        ),
                                        sharedApplicationViewModelInstanceId
                                    )
                                }
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.USER_ACTIVITY_MESSAGE -> {

                                CoroutineScope(ioDispatcher).launch {
                                    if (updateChatRoomResponse.response.userActivityMessage.userExistsInChatRoom) { //user exists in chat room

                                        //A USER_ACTIVITY_DETECTED_MESSAGE was sent for the current user and this MessageUpdateCase reflects
                                        // the time that the message was stored. Because this message was sent by the current user, do not store
                                        // update time because messages could be out of order. Instead a NEW_UPDATE_TIME_MESSAGE will be passed
                                        // back for this message uuid to the ChatStreamObject. Also USER_ACTIVITY_DETECTED_MESSAGE do not
                                        // update the chatRoomLastActiveTime.
                                        chatRoomsDataSource.updateChatRoomObservedTimeUserLastActiveTime(
                                            chatRoomId,
                                            updateChatRoomResponse.response.userActivityMessage.timestampReturned
                                        )
                                    } else { //user does not exist in chat room

                                        //the inconsistency here is stored on the server as an error
                                        leaveChatRoomFromError(
                                            chatRoomId,
                                            sharedApplicationViewModelInstanceId,
                                            transactionWrapper
                                        )
                                    }
                                }
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.EVENT_RESPONSE,
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.MEMBER_RESPONSE -> {

                                val updateOtherUserResponse =
                                    if (updateChatRoomResponse.response.messageUpdateCase == ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.EVENT_RESPONSE) {
                                        chatRoomsDataSource.updateEventOid(chatRoomId, updateChatRoomResponse.response.eventResponse.userInfo.accountOid)
                                        updateChatRoomResponse.response.eventResponse
                                    } else {
                                        updateChatRoomResponse.response.memberResponse
                                    }
                                //remove the member from the 'member list for updated'
                                for (i in memberListForUpdated!!.indices) {
                                    if (memberListForUpdated!![i].accountOid == updateOtherUserResponse.userInfo.accountOid) {
                                        memberListForUpdated!!.removeAt(i)
                                        break
                                    }
                                }

                                //building this is a little bit redundant, however in order to check the
                                // return status of the message checkApplicationReturnStatusEnum will need to
                                // be called and so this will work out conveniently
                                val memberResponse =
                                    GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse>(
                                        updateOtherUserResponse,
                                        updateChatRoomResponse.errorMessage,
                                        updateChatRoomResponse.androidErrorEnum
                                    )

                                handleUpdateMembersFromChatRoom(
                                    chatRoomId,
                                    memberResponse,
                                    TypeOfOtherUserUpdate.WHOLE_CHAT_ROOM_OTHER_USER_UPDATE,
                                    sharedApplicationViewModelInstanceId
                                )
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.RESPONSE_ADDENDUM -> {

                                //This RESPONSE_ADDENDUM is necessary for chat room info because the stream
                                // will only send back members that require an update. It will NOT send back
                                // members that are checked and do NOT require an update. This means the observed
                                // time will need to be checked for them.
                                if (updateChatRoomResponse.response.responseAddendum.picturesCheckedForUpdates
                                    && memberListForUpdated!!.isNotEmpty()
                                ) {

                                    val usersRequiringUpdate =
                                        memberListForUpdated!!.map { it.accountOid }

                                    otherUsersDaoDataSource.updateTimestampsPicturesAttemptedUpdate(
                                        usersRequiringUpdate,
                                        updateChatRoomResponse.response.responseAddendum.currentTimestamp
                                    )

                                    CoroutineScope(Dispatchers.Main).launch {

                                        _updatePicturesUpdateAttemptedTimestampByAccountOIDs.setValue(
                                            UpdatePicturesUpdateAttemptedTimestampByAccountOIDsDataHolder(
                                                usersRequiringUpdate,
                                                updateChatRoomResponse.response.responseAddendum.currentTimestamp,
                                                chatRoomId
                                            ),
                                            sharedApplicationViewModelInstanceId
                                        )
                                    }
                                }
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.RETURN_STATUS -> {

                                val returnValues = checkApplicationReturnStatusEnum(
                                    updateChatRoomResponse.response.returnStatus,
                                    updateChatRoomResponse
                                )

                                if (returnValues.first != "~") {
                                    CoroutineScope(ioDispatcher).launch {

                                        sendApplicationError(
                                            returnValues.first,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors()
                                        )
                                    }
                                }

                                CoroutineScope(Dispatchers.Main).launch {
                                    _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                                        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                                        sharedApplicationViewModelInstanceId
                                    )
                                }

                                successful = false
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.QR_CODE -> {
                                val prevQRCodePath = chatRoomsDataSource.getQRCodePath(chatRoomId)
                                if (prevQRCodePath != GlobalValues.server_imported_values.qrCodeDefault) {
                                    deleteFileInterface.sendFileToWorkManager(
                                        prevQRCodePath
                                    )
                                }

                                val qRCodeImageFilePath =
                                    if (
                                        updateChatRoomResponse.response.qrCode.qrCodeImageBytes.toString()
                                        != GlobalValues.server_imported_values.qrCodeDefault
                                    ) {
                                        storeChatQRCodeToFile(
                                            chatRoomId,
                                            updateChatRoomResponse.response.qrCode.qrCodeImageBytes,
                                            applicationContext,
                                            { errorString, lineNumber, fileName, stackTrace ->
                                                sendApplicationError(
                                                    errorString,
                                                    lineNumber,
                                                    stackTrace,
                                                    fileName,
                                                )
                                            }
                                        )
                                    } else {
                                        GlobalValues.server_imported_values.qrCodeDefault
                                    }

                                chatRoomsDataSource.setQrCodeValues(
                                    chatRoomId,
                                    qRCodeImageFilePath,
                                    updateChatRoomResponse.response.qrCode.qrCodeMessage,
                                    updateChatRoomResponse.response.qrCode.qrCodeTimeUpdated,
                                )

                                withContext(Dispatchers.Main) {
                                    _returnQrInfoUpdated.setValue(
                                        ReturnQrCodeInfoUpdated(
                                            chatRoomId,
                                            qRCodeImageFilePath,
                                            updateChatRoomResponse.response.qrCode.qrCodeMessage,
                                            updateChatRoomResponse.response.qrCode.qrCodeTimeUpdated,
                                        ),
                                        sharedApplicationViewModelInstanceId
                                    )
                                }
                            }
                            ChatRoomCommands.UpdateChatRoomResponse.MessageUpdateCase.MESSAGEUPDATE_NOT_SET,
                            null,
                            -> {
                                val errorMessage =
                                    "When attempting to update chat room an invalid messageUpdateCase was returned.\n" +
                                            "messageUpdateCase: ${updateChatRoomResponse.response.messageUpdateCase}\n" +
                                            "updateChatRoomResponse: $updateChatRoomResponse\n"

                                sendApplicationError(
                                    errorMessage,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors()
                                )

                                successful = false
                            }
                        }
                    }

                    successful
                }
            }
        } else { //if login token invalid
            withContext(Dispatchers.Main) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    sharedApplicationViewModelInstanceId
                )
            }
        }
    }

    //NOTE: this has to access the database and return things messages without missing any,
    // this is why it is synchronized (the other functions that access the database are as well)
    //NOTE: this is built to update when a chat room is clicked, NOT to update when initial stream is started
    private suspend fun handleUpdateMembersFromChatRoom(
        chatRoomId: String,
        response: GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse>,
        singleUpdate: TypeOfOtherUserUpdate,
        sharedApplicationViewModelInstanceId: String
    ): Boolean {

        val returnValues = checkApplicationReturnStatusEnum(
            response.response.returnStatus,
            response
        )

        val returnErrorStatusEnum = returnValues.second
        val returnString =
            when (returnErrorStatusEnum) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                    handleUpdateChatRoomMembersResponse(
                        chatRoomId,
                        response.response,
                        singleUpdate,
                        sharedApplicationViewModelInstanceId
                    )

                    returnValues.first
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "DO_NOTHING should never reach this point.\n" +
                            "response: $response"
                }
                else -> {
                    returnValues.first
                }
            }

        if (returnString != "~") {
            CoroutineScope(ioDispatcher).launch {

                sendApplicationError(
                    returnString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )
            }
        }

        if (returnErrorStatusEnum != GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            CoroutineScope(Dispatchers.Main).launch {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    sharedApplicationViewModelInstanceId
                )
            }
            return false
        }

        return true
    }

    //NOTE: this is built to update when a chat room is clicked, NOT to update when initial stream is started
    private suspend fun handleUpdateChatRoomMembersResponse(
        chatRoomId: String,
        response: UpdateOtherUserMessages.UpdateOtherUserResponse,
        typeOfOtherUserUpdate: TypeOfOtherUserUpdate,
        sharedApplicationViewModelInstanceId: String
    ) {

        if (response.userInfo.accountOid == LoginFunctions.currentAccountOID) { //if the current account was the one updated

            when (response.accountState) {
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_BANNED,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_NOT_IN_CHAT_ROOM,
                -> { //current user is NOT in chat room

                    val errorMessage =
                        "Current user was returned not inside a chat room when attempting an update.\n" +
                                "NOTE: This error may already be stored on server, but no way to know.\n" +
                                "accountOid: ${response.userInfo.accountOid}\n" +
                                "accountState: ${response.accountState}\n" +
                                "response: $response\n"

                    sendApplicationError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    leaveChatRoomFromError(
                        chatRoomId,
                        sharedApplicationViewModelInstanceId
                    )
                }
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_EVENT,
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                -> { //current user is in chat room

                    when (typeOfOtherUserUpdate) {
                        TypeOfOtherUserUpdate.SINGLE_CHAT_ROOM_OTHER_USER_UPDATE -> { //if from updateSingleUser RPC response
                            //update account state, user last activity time and last time updated (the current account stuff)
                            chatRoomsDataSource.updateAccountStateUserLastActiveTimeLastTimeUpdate(
                                chatRoomId,
                                response.accountState,
                                response.timestampReturned
                            )
                        }
                        TypeOfOtherUserUpdate.WHOLE_CHAT_ROOM_OTHER_USER_UPDATE -> { //if from updateAll return stream
                            //update account state
                            chatRoomsDataSource.updateAccountState(
                                chatRoomId,
                                response.accountState
                            )
                        }
                        TypeOfOtherUserUpdate.MATCH_OTHER_USER_UPDATE -> { //if return
                            val errorMessage =
                                "When attempting to update other user from match current user was attempted to be updated.\n" +
                                        "accountOid: ${response.userInfo.accountOid}\n"

                            sendApplicationError(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            return
                        }
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        _returnAccountStateUpdated.setValue(
                            AccountStateUpdatedDataHolder(
                                chatRoomId,
                                response.userInfo.accountOid,
                                response.accountState
                            ),
                            sharedApplicationViewModelInstanceId
                        )
                    }
                }
                AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_SUPER_ADMIN,
                AccountState.AccountStateInChatRoom.UNRECOGNIZED,
                null,
                -> {
                    val errorMessage =
                        "Received an update for the current user account with an invalid account state set.\n" +
                                "Inside the server at [grpc_functions/chat_room_commands/request_information/_documentation.md] " +
                                "account_state is guaranteed to be sent back.\n" +
                                "typeOfOtherUserUpdate: ${typeOfOtherUserUpdate}\n" +
                                "accountOid: ${response.userInfo.accountOid}\n" +
                                "accountState: ${response.accountState}\n" +
                                "response: ${response}\n"

                    sendApplicationError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                            sharedApplicationViewModelInstanceId
                        )
                    }
                }
            }

        } else { //if the account updated was not the current account and was not the USER_ACTIVITY_DETECTED message

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.OTHER_USERS
            )

            transactionWrapper.runTransaction {
                if (typeOfOtherUserUpdate == TypeOfOtherUserUpdate.SINGLE_CHAT_ROOM_OTHER_USER_UPDATE) { //if from updateSingleUser RPC response
                    //when update single other user is called, a USER_ACTIVITY_DETECTED message is sent for the current user
                    // make sure to keep the client in sync with the server
                    //user_last_active_time is updated because this represents the current user's last time they performed activity
                    // on the chat room
                    //last_observed_time should not be updated here (it is not updated on the server either). That is because this
                    // function is called when a user is viewing a different users' info card on the device. updateChatRoom() on
                    // the other hand is called when the user is clicking a chat room initially and so it is updated there
                    // because the user views all messages as the message is sent.
                    chatRoomsDataSource.updateUserLastActiveTime(
                        chatRoomId,
                        response.timestampReturned
                    )
                }

                val insertOrUpdateOtherUserReturn =
                    insertOrUpdateOtherUser(
                        response.userInfo,
                        UpdateMemberReasonEnum.UPDATE_OTHER_USER_IN_CHAT_ROOM,
                        otherUsersDaoDataSource,
                        applicationContext,
                        this,
                        errorHandling,
                        deleteFileInterface,
                        chatRoomId,
                        response.accountState,
                        response.accountLastActivityTime
                    )

                if (insertOrUpdateOtherUserReturn.otherUser.accountOID.isNotEmpty()) {
                    //error for user not existing was already stored
                    this.runAfterTransaction {
                        CoroutineScope(Dispatchers.Main).launch {
                            _returnUpdatedOtherUser.setValue(
                                ReturnUpdatedOtherUserRepositoryDataHolder(
                                    insertOrUpdateOtherUserReturn.otherUser,
                                    insertOrUpdateOtherUserReturn.anExistingThumbnailWasUpdated,
                                ),
                                sharedApplicationViewModelInstanceId
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun addAccountToBlockedList(
        accountOID: String,
        sharedApplicationViewModelInstanceId: String,
        unMatch: Boolean = true,
        transactionWrapper: TransactionWrapper = ServiceLocator.provideTransactionWrapper(
            GlobalValues.applicationContext,
            DatabasesToRunTransactionIn.ACCOUNTS,
        )
    ) {
        transactionWrapper.runTransaction {
            GlobalValues.blockedAccounts.add(accountOID)

            accountInfoDataSource.addBlockedAccount(accountOID, this)

            if (unMatch) {
                val matchingChatRoomId = chatRoomsDataSource.matchingChatRoomExists(accountOID)

                if (matchingChatRoomId.isNotEmpty()) { //if un matches
                    this.runAfterTransaction {
                        //UnMatch has a network call inside it (among other things), must do it after
                        // the transaction.
                        unMatchFromChatRoom(
                            matchingChatRoomId,
                            accountOID,
                            sharedApplicationViewModelInstanceId
                        )
                    }
                }
            }
        }
    }

    //called to handle the join chat room primer, will send back any error messages
    private fun handleJoinChatRoomPrimer(
        response: GrpcClientResponse<ChatMessageToClientMessage.ChatMessageToClient>,
        chatRoomStatus: ChatRoomCommands.ChatRoomStatus,
        chatRoomId: String,
        chatRoomPassword: String,
    ): JoinChatRoomPrimerValues {

        var returnErrorStatusEnum: GrpcFunctionErrorStatusEnum
        var returnString: String

        val errorReturn =
            checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

        returnErrorStatusEnum = errorReturn.second
        returnString =
            when (returnErrorStatusEnum) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                    when (chatRoomStatus) {
                        ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED -> {
                            return JoinChatRoomPrimerValues(returnErrorStatusEnum, chatRoomStatus)
                        }
                        ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED,
                        ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM,
                        ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_ID,
                        ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_PASSWORD,
                        ChatRoomCommands.ChatRoomStatus.USER_TOO_YOUNG_FOR_CHAT_ROOM,
                        -> {
                        } //these will all be displayed to the user
                        ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST -> { //this means a chat room Id that was not 7 or 8 chars long was passed
                            returnString =
                                "Response from runJoinChatRoom sent in an invalid chat room Id.\nchat room Id: $chatRoomId\nchat room password: $chatRoomPassword\nresponse: [\n$response\n]\n"

                            //NOTE: not setting this to UNMANAGEABLE_ERROR the error will be enough, the result should be the same
                            //returnErrorStatusEnum = ErrorStatusEnum.UNMANAGEABLE_ERROR
                        }
                        ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED -> {
                            returnString =
                                "Response from runJoinChatRoom had unrecognized ChatRoomStatus.\nresponse: [\n" +
                                        "$response\n" +
                                        "]\n"
                            returnErrorStatusEnum = GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                        }
                    }
                    errorReturn.first
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "Do Nothing was reached which should never happen.\n $response \n"
                }
                else -> {
                    errorReturn.first
                }
            }

        //NOTE: the joinChatRoomFunction will return any error that it encounters, this function does not need to do it

        if (returnString != "~") {

            CoroutineScope(ioDispatcher).launch {
                sendApplicationError(
                    returnString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )
            }
        }

        return JoinChatRoomPrimerValues(returnErrorStatusEnum, chatRoomStatus)
    }

}