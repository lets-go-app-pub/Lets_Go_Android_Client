package site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate

import access_status.AccessStatusEnum
import account_state.AccountState
import android.content.Context
import android.util.Log
import chat_message_to_client.ChatMessageToClientMessage.ChatMessageToClient
import com.google.protobuf.ByteString
import email_sending_messages.AccountRecoveryRequest
import email_sending_messages.AccountRecoveryResponse
import email_sending_messages.EmailVerificationRequest
import email_sending_messages.EmailVerificationResponse
import findmatches.FindMatches.*
import grpc_chat_commands.ChatRoomCommands
import grpc_chat_commands.ChatRoomCommands.ClientMessageToServerRequest
import grpc_stream_chat.ChatMessageStream
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.LoginAccountStatus
import loginfunction.LoginRequest
import loginfunction.LoginResponse
import loginsupport.LoginSupportRequest
import loginsupport.LoginSupportResponse
import loginsupport.NeededVeriInfoRequest
import loginsupport.NeededVeriInfoResponse
import member_shared_info.MemberSharedInfoMessageOuterClass
import prelogintimestamps.PreLoginTimestamps.PreLoginTimestampsMessage
import report_enums.ReportMessages
import report_enums.ReportMessages.UserMatchOptionsRequest
import request_fields.*
import requestmessages.RequestMessages.PictureMessage
import requestmessages.RequestMessages.PostLoginMessage
import retrieve_server_load.RetrieveServerLoad
import setfields.*
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.testingUtility.*
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.utilities.*
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import status_enum.StatusEnum
import update_other_user_messages.UpdateOtherUserMessages
import user_match_options.UpdateSingleMatchMemberRequest
import java.nio.charset.Charset
import java.util.*

class FakeClientSourceIntermediate : ClientsInterface {

    //NOTE: Used for testing which simulates single threaded environments. Therefore, not worrying about
    // thread safety.
    companion object {
        var returnStatusReturn = StatusEnum.ReturnStatus.SUCCESS

        var errorMessageReturn = "~"
        var grpcAndroidSideErrorReturn = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

        var joinChatRoomStatus = ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED

        var most_recent_verification_code = "111111"
        var verification_code_expired = false

        //NOTE: LoginFunctions only expects index values returned that require updates.
        var iconIndexRequiringUpdated = emptyList<Long>()

        var globalValuesToReturn = GlobalValues.server_imported_values

        var loginFunctionReturnStatusReturn = LoginAccountStatus.LOGGED_IN
        var loginFunctionAccessStatusReturn = AccessStatusEnum.AccessStatus.ACCESS_GRANTED
        var smsVerificationRan = false

        var setupSmsForNewAccountCreation = true

        const val TIME_BETWEEN_SWIPES_UPDATED = 4L * 60L * 60L * 1000L

        var timestampReturn = System.currentTimeMillis()

        var accountStoredOnServer: AccountInfoDataEntity? = null

        //Setup inside login functions, called inside requestFieldsClientPicture().
        var picturesStoredOnServer = listOf<PictureMessage>()

        var requestIconsResponse = listOf<GrpcClientResponse<ServerIconsResponse>>()

        val userMatchOptionsRequests = mutableListOf<UserMatchOptionsRequest>()

        val matchesToReturn = mutableListOf<FindMatchesResponse>()
        var findMatchesSuccessType = FindMatchesCapMessage.SuccessTypes.UNKNOWN

        //Must be set by each test to use the respective test dispatcher.
        var fakeStoreErrors: StoreErrorsInterface = FakeStoreErrors(Dispatchers.Default)

        var numTimesUpdateSingleMatchMemberCalled = 0

        val joinChatRoomObjects = JoinChatRoomObjects()

        val dummyStreamObserver =
            object : StreamObserver<ChatMessageStream.ChatToServerRequest> {
                override fun onNext(value: ChatMessageStream.ChatToServerRequest?) {
                    //TODO("Not yet implemented")
                }

                override fun onError(t: Throwable?) {
                    //TODO("Not yet implemented")
                }

                override fun onCompleted() {
                    //called by ChatStreamObject
                    //TODO("Not yet implemented")
                }
            }

        fun setupCompleteServerAccount(
            accessStatus: AccessStatusEnum.AccessStatus = AccessStatusEnum.AccessStatus.ACCESS_GRANTED
        ) {
            loginFunctionAccessStatusReturn = accessStatus
            accountStoredOnServer = generateRandomValidAccountInfoDataEntity(
                false,
                fakeStoreErrors
            )
            picturesStoredOnServer = generateUserAccountPictures()
        }

        fun setupCompleteServerAccountIfNotSetup(
            accessStatus: AccessStatusEnum.AccessStatus = AccessStatusEnum.AccessStatus.ACCESS_GRANTED
        ) {
            if(accountStoredOnServer == null) {
                setupCompleteServerAccount(accessStatus)
            }
        }

        fun resetStaticToDefaults() {
            returnStatusReturn = StatusEnum.ReturnStatus.SUCCESS
            errorMessageReturn = "~"

            grpcAndroidSideErrorReturn = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

            joinChatRoomStatus = ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED

            most_recent_verification_code = "111111"
            verification_code_expired = false

            iconIndexRequiringUpdated = emptyList()

            globalValuesToReturn = GlobalValues.server_imported_values

            loginFunctionReturnStatusReturn = LoginAccountStatus.LOGGED_IN
            loginFunctionAccessStatusReturn = AccessStatusEnum.AccessStatus.ACCESS_GRANTED
            smsVerificationRan = false

            setupSmsForNewAccountCreation = true

            timestampReturn = System.currentTimeMillis()

            accountStoredOnServer = null

            //Setup inside login functions, called inside requestFieldsClientPicture().
            picturesStoredOnServer = emptyList()

            requestIconsResponse = emptyList()

            matchesToReturn.clear()
            findMatchesSuccessType = FindMatchesCapMessage.SuccessTypes.UNKNOWN

            userMatchOptionsRequests.clear()

            numTimesUpdateSingleMatchMemberCalled = 0

            joinChatRoomObjects.resetToDefaults()
        }
    }

    override suspend fun retrieveServerLoadInfo(
        channel: ManagedChannel,
        requestNumClients: Boolean
    ): GrpcClientResponse<RetrieveServerLoad.RetrieveServerLoadResponse> {
        return GrpcClientResponse(
            RetrieveServerLoad.RetrieveServerLoadResponse.newBuilder()
                .setAcceptingConnections(true)
                .setNumClients((0..1000).random())
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun loginFunctionClientLogin(passedRequest: LoginRequest): GrpcClientResponse<LoginResponse?> {
        if (!smsVerificationRan && accountStoredOnServer == null) {
            //Generate verification code to be sent.
            most_recent_verification_code = generateRandomVerificationCodeForTesting()
        }

        return GrpcClientResponse(
            generateLoginResponse(
                passedRequest.phoneNumber,
                timestampReturn,
            ),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun loginSupportClientDeleteAccount(passedRequest: LoginSupportRequest): GrpcClientResponse<LoginSupportResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun loginSupportClientLogoutFunction(passedRequest: LoginSupportRequest): GrpcClientResponse<LoginSupportResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun loginSupportClientNeedVeriInfo(passedRequest: NeededVeriInfoRequest): GrpcClientResponse<NeededVeriInfoResponse?> {

        val userAccount =
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()
                ?: throw IllegalArgumentException("User account was null or not found.")

        val userPictures =
            ServiceLocator.accountInfoDatabase?.accountPictureDatabaseDao?.getAllAccountPictures()
                ?: throw IllegalArgumentException("User pictures were null or not found.")

        val ageRangeObject = generateDefaultAgeRange(userAccount.age)

        return GrpcClientResponse(
            NeededVeriInfoResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setAccessStatus(AccessStatusEnum.AccessStatus.ACCESS_GRANTED)
                .setPreLoginTimestamps(
                    PreLoginTimestampsMessage.newBuilder()
                        .setBirthdayTimestamp(userAccount.birthdayTimestamp)
                        .setEmailTimestamp(userAccount.emailTimestamp)
                        .setGenderTimestamp(userAccount.genderTimestamp)
                        .setNameTimestamp(userAccount.firstNameTimestamp)
                        .setCategoriesTimestamp(userAccount.categoriesTimestamp)
                        .build()
                )
                .addAllPictureTimestamps(
                    userPictures.map {
                        it.pictureTimestamp
                    }
                )
                .setPostLoginInfo(
                    PostLoginMessage.newBuilder()
                        .setUserBio("")
                        .setUserCity("")
                        .addGenderRange(userAccount.gender)
                        .setMinAge(ageRangeObject.minAge)
                        .setMaxAge(ageRangeObject.maxAge)
                        .setMaxDistance(30)
                        .build()
                )
                .setServerTimestamp(timestampReturn)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun requestFieldsClientRequestServerIcons(
        applicationContext: Context,
        passedRequest: ServerIconsRequest
    ): GrpcFunctionErrorStatusEnum {
        for (response in requestIconsResponse) {
            //call back to the repository to store the object
            ServiceLocator.provideLoginFunctions(applicationContext)
                .handleRequestIconsResponse(response)
        }

        return GrpcFunctionErrorStatusEnum.NO_ERRORS
    }

    override suspend fun requestFieldsClientPhoneNumber(passedRequest: InfoFieldRequest): GrpcClientResponse<InfoFieldResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientBirthday(passedRequest: InfoFieldRequest): GrpcClientResponse<BirthdayResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientEmail(passedRequest: InfoFieldRequest): GrpcClientResponse<EmailResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientGender(passedRequest: InfoFieldRequest): GrpcClientResponse<InfoFieldResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientFirstName(passedRequest: InfoFieldRequest): GrpcClientResponse<InfoFieldResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientPicture(
        applicationContext: Context,
        passedRequest: PictureRequest
    ): GrpcFunctionErrorStatusEnum {
        for (pic in picturesStoredOnServer) {
            if (passedRequest.requestedIndexesList.contains(pic.indexNumber)) {
                //call back to the repository to store the object
                ServiceLocator.provideLoginFunctions(applicationContext)
                    .runRequestPicturesProtoRPC(
                        GrpcClientResponse(
                            PictureResponse.newBuilder()
                                .setReturnStatus(returnStatusReturn)
                                .setPictureInfo(pic)
                                .setTimestamp(timestampReturn)
                                .build(),
                            "~",
                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                        )
                    )
            }
        }

        return if (picturesStoredOnServer.isEmpty()) {
            throw IllegalArgumentException("picturesStoredOnServer should always have at least one value")
        } else {
            GrpcFunctionErrorStatusEnum.NO_ERRORS
        }
    }

    override suspend fun requestFieldsClientCategories(passedRequest: InfoFieldRequest): GrpcClientResponse<CategoriesResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientRequestPostLoginInfo(passedRequest: InfoFieldRequest): GrpcClientResponse<PostLoginInfoResponse?> {
        TODO("Not yet implemented")
    }

    override suspend fun requestFieldsClientRequestTimestamp(): GrpcClientResponse<TimestampResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun setFieldsClientAlgorithmSearchOptions(passedRequest: SetAlgorithmSearchOptionsRequest): GrpcClientResponse<SetFieldResponse> {
        if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
            accountStoredOnServer?.algorithmSearchOptions = passedRequest.matchingStatus.number
        }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(timestampReturn)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientBirthday(passedRequest: SetBirthdayRequest): GrpcClientResponse<SetBirthdayResponse> {
        var age = 0

        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                age = calcPersonAgeNoError(
                    passedRequest.birthYear,
                    passedRequest.birthMonth,
                    passedRequest.birthDayOfMonth
                ) {
                    val calendar = Calendar.getInstance()
                    calendar
                }
                accountStoredOnServer?.birthYear = passedRequest.birthYear
                accountStoredOnServer?.birthMonth = passedRequest.birthMonth
                accountStoredOnServer?.birthDayOfMonth = passedRequest.birthDayOfMonth
                accountStoredOnServer?.age = age
                accountStoredOnServer?.birthdayTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.birthdayTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetBirthdayResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setAge(age)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientEmail(passedRequest: SetEmailRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.emailAddress = passedRequest.setEmail
                accountStoredOnServer?.requiresEmailAddressVerification = true
                accountStoredOnServer?.emailTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.emailTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientGender(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.gender = passedRequest.setString
                accountStoredOnServer?.genderTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.genderTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientFirstName(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.firstName = passedRequest.setString
                accountStoredOnServer?.firstNameTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.firstNameTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientPicture(passedRequest: SetPictureRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {

                val mutableCopy = picturesStoredOnServer.toMutableList()
                while(mutableCopy.size < GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
                    mutableCopy.add(
                        PictureMessage.newBuilder()
                            .setTimestampPictureLastUpdated(-1)
                            .setFileInBytes(ByteString.copyFrom("~".toByteArray()))
                            .setFileSize(1)
                            .setIndexNumber(mutableCopy.size - 1)
                            .build()
                    )
                }

                val timestamp = System.currentTimeMillis()
                mutableCopy[passedRequest.pictureArrayIndex] = PictureMessage.newBuilder()
                    .setFileInBytes(passedRequest.fileInBytes)
                    .setFileSize(passedRequest.fileSize)
                    .setIndexNumber(passedRequest.pictureArrayIndex)
                    .setTimestampPictureLastUpdated(timestamp)
                    .setPictureOid(generateRandomOidForTesting())
                    .build()

                picturesStoredOnServer = mutableCopy
                timestamp
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientCategories(passedRequest: SetCategoriesRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.categories =
                    convertCategoryActivityMessageToString(
                        passedRequest.categoryList
                    )
                accountStoredOnServer?.categoriesTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.categoriesTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientAgeRange(passedRequest: SetAgeRangeRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.maxAge = passedRequest.maxAge
                accountStoredOnServer?.minAge = passedRequest.minAge
                accountStoredOnServer?.postLoginTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.postLoginTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientGenderRange(passedRequest: SetGenderRangeRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.userGenderRange =
                    convertGenderRangeToString(
                        passedRequest.genderRangeList
                    )
                accountStoredOnServer?.postLoginTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.postLoginTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientUserBio(passedRequest: SetBioRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.userBio =
                    passedRequest.setString.toString(Charset.defaultCharset())
                accountStoredOnServer?.postLoginTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.postLoginTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientUserCity(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.userCity = passedRequest.setString
                accountStoredOnServer?.postLoginTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.postLoginTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientMaxDistance(passedRequest: SetMaxDistanceRequest): GrpcClientResponse<SetFieldResponse> {
        val currentTimestamp =
            if (returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
                accountStoredOnServer?.maxDistance = passedRequest.maxDistance
                accountStoredOnServer?.postLoginTimestamp = System.currentTimeMillis()
                accountStoredOnServer?.postLoginTimestamp ?: -1
            } else {
                timestampReturn
            }

        return GrpcClientResponse(
            SetFieldResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(currentTimestamp)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun setFieldsClientFeedback(passedRequest: SetFeedbackRequest): GrpcClientResponse<SetFeedbackResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun smsVerificationClientSMSVerification(passedRequest: SMSVerificationRequest): GrpcClientResponse<SMSVerificationResponse?> {
        return GrpcClientResponse(
            if (verification_code_expired) {
                verification_code_expired = false
                SMSVerificationResponse.newBuilder()
                    .setReturnStatus(SMSVerificationResponse.Status.VERIFICATION_CODE_EXPIRED)
                    .build()
            } else if (passedRequest.verificationCode != most_recent_verification_code) { //Verification code does NOT matches.
                SMSVerificationResponse.newBuilder()
                    .setReturnStatus(SMSVerificationResponse.Status.INVALID_VERIFICATION_CODE)
                    .build()
            } else { //Verification code matches.

                if (setupSmsForNewAccountCreation) {
                    accountStoredOnServer = generateRandomValidAccountInfoDataEntity(
                        true,
                        fakeStoreErrors
                    )
                    accountStoredOnServer?.phoneNumber = passedRequest.phoneNumberOrAccountId
                    loginFunctionAccessStatusReturn = AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO

                    //loginFunction() runs directly after smsVerification() so this sets
                    // verificationRan to true allowing loginFunction() can 'know' to create an account.
                    smsVerificationRan = true
                }

                SMSVerificationResponse.newBuilder()
                    .setReturnStatus(SMSVerificationResponse.Status.SUCCESS)
                    .build()
            },
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun findMatches(
        passedRequest: FindMatchesRequest
    ): Flow<GrpcClientResponse<FindMatchesResponse>> = flow {

        Log.i(
            "number_matches",
            "findMatches() number messages requested: ${passedRequest.numberMessages}"
        )

        var successType: FindMatchesCapMessage.SuccessTypes

        if (findMatchesSuccessType == FindMatchesCapMessage.SuccessTypes.UNKNOWN) { //Simulate server returns

            successType = FindMatchesCapMessage.SuccessTypes.SUCCESSFULLY_EXTRACTED

            if (
                passedRequest.numberMessages < 1
                || passedRequest.numberMessages > GlobalValues.server_imported_values.maximumNumberResponseMessages
            ) {
                throw IllegalArgumentException("numberMessages requested too large or too small ${passedRequest.numberMessages}, should be 0 < x <= ${GlobalValues.server_imported_values.maximumNumberResponseMessages}.")
            }

            for (i in 0 until passedRequest.numberMessages) {
                if (matchesToReturn.isEmpty()) {
                    successType = FindMatchesCapMessage.SuccessTypes.NO_MATCHES_FOUND
                    break
                }

                emit(
                    GrpcClientResponse(
                        matchesToReturn.first(),
                        errorMessageReturn,
                        GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                    )
                )

                matchesToReturn.removeFirst()
            }

        } else { //Manually handle return type.
            successType = findMatchesSuccessType
        }

        val currentTimestamp = System.currentTimeMillis()
        emit(
            GrpcClientResponse(
                FindMatchesResponse.newBuilder()
                    .setFindMatchesCap(
                        FindMatchesCapMessage.newBuilder()
                            .setReturnStatus(returnStatusReturn)
                            .setSuccessType(successType)
                            .setSwipesTimeBeforeReset(TIME_BETWEEN_SWIPES_UPDATED - currentTimestamp % TIME_BETWEEN_SWIPES_UPDATED)
                            .setCoolDownOnMatchAlgorithm(60L * 60L * 1000L)
                            .setTimestamp(currentTimestamp)
                            .build()
                    )
                    .build(),
                errorMessageReturn,
                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            )
        )
    }

    override suspend fun userMatchOptionsSwipe(passedRequest: UserMatchOptionsRequest): GrpcClientResponse<ReportMessages.UserMatchOptionsResponse> {
        if(grpcAndroidSideErrorReturn == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS && returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS) {
            userMatchOptionsRequests.add(passedRequest)
        }
        return GrpcClientResponse(
            ReportMessages.UserMatchOptionsResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestamp(timestampReturn)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun updateSingleMatchMember(passedRequest: UpdateSingleMatchMemberRequest): GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {

        numTimesUpdateSingleMatchMemberCalled++

        if(
            grpcAndroidSideErrorReturn != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            || returnStatusReturn != StatusEnum.ReturnStatus.SUCCESS
        ) {
            return GrpcClientResponse(
                UpdateOtherUserMessages.UpdateOtherUserResponse.newBuilder()
                    .setReturnStatus(returnStatusReturn)
                    .setTimestampReturned(timestampReturn)
                    .build(),
                errorMessageReturn,
                grpcAndroidSideErrorReturn
            )
        }

        ServiceLocator.otherUsersDatabase?.otherUsersDatabaseDao?.getSingleOtherUser(
            passedRequest.chatRoomMemberInfo.accountOid
        )?.let {
            //Returning minimal info here, fields that are not returned represent fields
            // that did not require updating.
            return GrpcClientResponse(
                UpdateOtherUserMessages.UpdateOtherUserResponse.newBuilder()
                    .setReturnStatus(returnStatusReturn)
                    .setTimestampReturned(timestampReturn)
                    .setUserInfo(
                        MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage.newBuilder()
                            .setAccountOid(it.accountOID)
                            .build()
                    )
                    .build(),
                errorMessageReturn,
                grpcAndroidSideErrorReturn
            )
        }

        return GrpcClientResponse(
            UpdateOtherUserMessages.UpdateOtherUserResponse.newBuilder()
                .setReturnStatus(returnStatusReturn)
                .setTimestampReturned(timestampReturn)
                .setMatchStillValid(false)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun sendChatMessageToServer(request: ClientMessageToServerRequest): GrpcClientResponse<ChatRoomCommands.ClientMessageToServerResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun createChatRoom(passedRequest: ChatRoomCommands.CreateChatRoomRequest): GrpcClientResponse<ChatRoomCommands.CreateChatRoomResponse> {
        if(
            grpcAndroidSideErrorReturn != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            || returnStatusReturn != StatusEnum.ReturnStatus.SUCCESS
        ) {
            return GrpcClientResponse(
                ChatRoomCommands.CreateChatRoomResponse.newBuilder()
                    .setReturnStatus(returnStatusReturn)
                    .build(),
                errorMessageReturn,
                grpcAndroidSideErrorReturn
            )
        }

        val chatRoomName =
            passedRequest.chatRoomName.ifEmpty {
                "${accountStoredOnServer?.firstName}'s chat room"
            }

        val chatRoomId = generateRandomChatRoomIdForTesting()
        val timestampChatRoomCreated = System.currentTimeMillis()
        val timestampCapMessage = timestampChatRoomCreated + 1
        val timestampLastActiveTime = timestampChatRoomCreated + 2

        return GrpcClientResponse(
            ChatRoomCommands.CreateChatRoomResponse.newBuilder()
                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                .setChatRoomId(chatRoomId)
                .setChatRoomName(chatRoomName)
                .setChatRoomPassword(generateRandomChatRoomPasswordForTesting())
                .setChatRoomCapMessage(
                    generateCapMessage(
                        GenericMessageParameters(
                            sentByAccountOID = passedRequest.loginInfo.currentAccountId,
                            timestampStored = timestampCapMessage,
                            onlyStoreMessage = true,
                            chatRoomIdSentFrom = chatRoomId,
                        )
                    )
                )
                .setCurrentUserJoinedChatMessage(
                    generateDifferentUserJoinedMessage(
                        GenericMessageParameters(
                            sentByAccountOID = passedRequest.loginInfo.currentAccountId,
                            timestampStored = timestampLastActiveTime,
                            onlyStoreMessage = true,
                            chatRoomIdSentFrom = chatRoomId,
                        ),
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                        timestampLastActiveTime
                    )
                )
                .setLastActivityTimeTimestamp(timestampLastActiveTime)
                .build(),
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun joinChatRoom(
        context: Context,
        request: ChatRoomCommands.JoinChatRoomRequest,
        checkPrimer: (response: GrpcClientResponse<ChatMessageToClient>, chatRoomStatus: ChatRoomCommands.ChatRoomStatus) -> JoinChatRoomPrimerValues
    ): GrpcClientResponse<JoinChatRoomPrimerValues> {

        val returnValue = checkPrimer(
            GrpcClientResponse(
                ChatMessageToClient.newBuilder()
                    .setPrimer(true)
                    .setReturnStatus(returnStatusReturn)
                    .build(),
                errorMessageReturn,
                grpcAndroidSideErrorReturn
            ),
            joinChatRoomStatus
        )

        if(returnStatusReturn == StatusEnum.ReturnStatus.SUCCESS
            && grpcAndroidSideErrorReturn == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
            && joinChatRoomStatus == ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED) {

            val thisUserJoinedChatRoomStart = generateThisUserJoinedChatRoomStart(
                accountStoredOnServer!!.accountOID,
                joinChatRoomObjects.chatRoomInfoMessage
            ).build()

            (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                thisUserJoinedChatRoomStart,
                calledFromJoinChatRoom = true
            )

            for(memberMsg in joinChatRoomObjects.usersToSendBackOnJoinChatRoom) {
                val thisUserJoinedChatRoomMember = generateThisUserJoinedChatRoomMember(
                    accountStoredOnServer!!.accountOID,
                    joinChatRoomObjects.chatRoomId,
                    memberMsg.memberInfoMessage
                ).build()

                (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                    thisUserJoinedChatRoomMember,
                    calledFromJoinChatRoom = true
                )
            }

            //NOTE: Technically, there should always be a few messages sent back. At least the cap message
            // and a DIFFERENT_USER_JOINED_MESSAGE message for the creator. And then with only a single
            // user, the chat room creator would have had to leave. However, this is not technically mandatory
            // to have, so only worrying about the cap message and the DIFFERENT_USER_JOINED_MESSAGE sent
            // back by joinChatRoom() for this user.

            //The cap message must be BEFORE anything else happened (before this user joined the chat room).
            val capMessage = generateCapMessage(
                GenericMessageParameters(
                    timestampStored = ((joinChatRoomObjects.chatRoomInfoMessage.timeJoined - 10000) until joinChatRoomObjects.chatRoomInfoMessage.timeJoined).random(),
                    onlyStoreMessage = true,
                    chatRoomIdSentFrom = joinChatRoomObjects.chatRoomId,
                    doNotUpdateUserState = false,
                )
            ).build()

            //Send cap message
            (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                capMessage,
                calledFromJoinChatRoom = true
            )

            //Send any stored messages
            for(message in joinChatRoomObjects.messagesToSendBackOnJoinChatRoom) {
                //Send this user joined message
                (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                    message,
                    calledFromJoinChatRoom = true
                )
            }

            val differentUserJoinedMessage = generateDifferentUserJoinedMessage(
                GenericMessageParameters(
                    sentByAccountOID = accountStoredOnServer!!.accountOID,
                    timestampStored = joinChatRoomObjects.chatRoomInfoMessage.userLastActivityTime,
                    onlyStoreMessage = true,
                    chatRoomIdSentFrom = joinChatRoomObjects.chatRoomId,
                ),
                joinChatRoomObjects.chatRoomInfoMessage.accountState,
                joinChatRoomObjects.chatRoomInfoMessage.userLastActivityTime
            ).build()

            //Send this user joined message
            (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                differentUserJoinedMessage,
                calledFromJoinChatRoom = true
            )

            val thisUserJoinedChatRoomFinished = generateThisUserJoinedChatRoomFinished(
                accountStoredOnServer!!.accountOID,
                joinChatRoomObjects.chatRoomId
            ).build()

            //Send join chat room finished message
            (context.applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                thisUserJoinedChatRoomFinished,
                calledFromJoinChatRoom = true
            )
        }

        return GrpcClientResponse(
            returnValue,
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun leaveChatRoom(passedRequest: ChatRoomCommands.LeaveChatRoomRequest): GrpcClientResponse<ChatRoomCommands.LeaveChatRoomResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun removeFromChatRoom(passedRequest: ChatRoomCommands.RemoveFromChatRoomRequest): GrpcClientResponse<ChatRoomCommands.RemoveFromChatRoomResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun unMatchFromChatRoom(passedRequest: ChatRoomCommands.UnMatchRequest): GrpcClientResponse<ChatRoomCommands.UnMatchResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun blockAndReportChatRoom(passedRequest: ChatRoomCommands.BlockAndReportChatRoomRequest): GrpcClientResponse<ReportMessages.UserMatchOptionsResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun unblockOtherUser(passedRequest: ChatRoomCommands.UnblockOtherUserRequest): GrpcClientResponse<ChatRoomCommands.UnblockOtherUserResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun promoteNewAdmin(passedRequest: ChatRoomCommands.PromoteNewAdminRequest): GrpcClientResponse<ChatRoomCommands.PromoteNewAdminResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun updateChatRoomInfo(passedRequest: ChatRoomCommands.UpdateChatRoomInfoRequest): GrpcClientResponse<ChatRoomCommands.UpdateChatRoomInfoResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun updateSingleChatRoomMember(passedRequest: ChatRoomCommands.UpdateSingleChatRoomMemberRequest): GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun updateChatRoom(
        chatRoomId: String,
        passedRequest: ChatRoomCommands.UpdateChatRoomRequest,
        handleResponse: suspend (GrpcClientResponse<ChatRoomCommands.UpdateChatRoomResponse>) -> Boolean
    ) {

    }

    override suspend fun startChatStream(
        metaDataHeaderParams: Metadata,
        responseObserver: ChatStreamObject.ChatMessageStreamObserver
    ): GrpcClientResponse<StreamObserver<ChatMessageStream.ChatToServerRequest>?> {
        return GrpcClientResponse(
            dummyStreamObserver,
            errorMessageReturn,
            grpcAndroidSideErrorReturn
        )
    }

    override suspend fun beginEmailVerification(passedRequest: EmailVerificationRequest): GrpcClientResponse<EmailVerificationResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun beginAccountRecovery(passedRequest: AccountRecoveryRequest): GrpcClientResponse<AccountRecoveryResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun setFieldsClientOptedInToPromotionalEmail(passedRequest: SetOptedInToPromotionalEmailRequest): GrpcClientResponse<SetFieldResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun setPinnedLocation(passedRequest: ChatRoomCommands.SetPinnedLocationRequest): GrpcClientResponse<ChatRoomCommands.SetPinnedLocationResponse> {
        TODO("Not yet implemented")
    }
}