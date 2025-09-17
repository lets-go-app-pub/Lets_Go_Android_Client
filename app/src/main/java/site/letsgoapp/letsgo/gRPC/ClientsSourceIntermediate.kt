package site.letsgoapp.letsgo.gRPC

import android.content.Context
import android.util.Log
import chat_message_to_client.ChatMessageToClientMessage
import email_sending_messages.AccountRecoveryRequest
import email_sending_messages.AccountRecoveryResponse
import email_sending_messages.EmailVerificationRequest
import email_sending_messages.EmailVerificationResponse
import findmatches.FindMatches
import grpc_chat_commands.ChatRoomCommands.*
import grpc_stream_chat.ChatMessageStream
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import loginfunction.LoginRequest
import loginfunction.LoginResponse
import loginsupport.LoginSupportRequest
import loginsupport.LoginSupportResponse
import loginsupport.NeededVeriInfoRequest
import loginsupport.NeededVeriInfoResponse
import report_enums.ReportMessages
import request_fields.*
import retrieve_server_load.RetrieveServerLoad
import setfields.*
import site.letsgoapp.letsgo.gRPC.clients.*
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import site.letsgoapp.letsgo.utilities.JoinChatRoomPrimerValues
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import update_other_user_messages.UpdateOtherUserMessages
import user_match_options.UpdateSingleMatchMemberRequest

class ClientsSourceIntermediate(
    private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : ClientsInterface {

    override suspend fun retrieveServerLoadInfo(
        channel: ManagedChannel,
        requestNumClients: Boolean
    ): GrpcClientResponse<RetrieveServerLoad.RetrieveServerLoadResponse> {
        Log.i("client_source_inter", "retrieveServerLoadInfo()")
        return RetrieveServerLoadClient().retrieveServerLoad(channel, requestNumClients)
    }

    //NOTE: LogErrorClient is an exception because it is not called in the repository
    override suspend fun loginFunctionClientLogin(passedRequest: LoginRequest):
            GrpcClientResponse<LoginResponse?> {
        Log.i("client_source_inter", "loginFunctionClientLogin()")
        return LoginFunctionClient.login(passedRequest)
    }

    override suspend fun loginSupportClientDeleteAccount(passedRequest: LoginSupportRequest):
            GrpcClientResponse<LoginSupportResponse?> {
        Log.i("client_source_inter", "loginSupportClientDeleteAccount()")
        return LoginSupportClient.deleteAccount(passedRequest)
    }

    override suspend fun loginSupportClientLogoutFunction(passedRequest: LoginSupportRequest):
            GrpcClientResponse<LoginSupportResponse?> {
        Log.i("client_source_inter", "loginSupportClientLogoutFunction()")
        return LoginSupportClient.logoutFunction(passedRequest)
    }

    override suspend fun loginSupportClientNeedVeriInfo(passedRequest: NeededVeriInfoRequest):
            GrpcClientResponse<NeededVeriInfoResponse?> {
        Log.i("client_source_inter", "loginSupportClientNeedVeriInfo()")
        return LoginSupportClient.needVeriInfo(passedRequest)
    }

    override suspend fun requestFieldsClientRequestServerIcons(
        applicationContext: Context,
        passedRequest: ServerIconsRequest
    ): GrpcFunctionErrorStatusEnum {
        Log.i("client_source_inter", "requestFieldsClientRequestServerIcons()")
        return RequestFieldsClient.requestServerIcons(applicationContext, passedRequest)
    }

    override suspend fun requestFieldsClientPhoneNumber(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?> {
        Log.i("client_source_inter", "requestFieldsClientPhoneNumber()")
        return RequestFieldsClient.requestPhoneNumber(passedRequest)
    }

    override suspend fun requestFieldsClientBirthday(passedRequest: InfoFieldRequest):
            GrpcClientResponse<BirthdayResponse?> {
        Log.i("client_source_inter", "requestFieldsClientBirthday()")
        return RequestFieldsClient.requestBirthday(passedRequest)
    }

    override suspend fun requestFieldsClientEmail(passedRequest: InfoFieldRequest):
            GrpcClientResponse<EmailResponse?> {
        Log.i("client_source_inter", "requestFieldsClientEmail()")
        return RequestFieldsClient.requestEmail(passedRequest)
    }

    override suspend fun requestFieldsClientGender(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?> {
        Log.i("client_source_inter", "requestFieldsClientGender()")
        return RequestFieldsClient.requestGender(passedRequest)
    }

    override suspend fun requestFieldsClientFirstName(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?> {
        Log.i("client_source_inter", "requestFieldsClientFirstName()")
        return RequestFieldsClient.requestFirstName(passedRequest)
    }

    override suspend fun requestFieldsClientPicture(
        applicationContext: Context,
        passedRequest: PictureRequest
    ): GrpcFunctionErrorStatusEnum {
        Log.i("client_source_inter", "requestFieldsClientPicture()")
        return RequestFieldsClient().requestPictures(applicationContext, passedRequest)
    }

    override suspend fun requestFieldsClientCategories(passedRequest: InfoFieldRequest):
            GrpcClientResponse<CategoriesResponse?> {
        Log.i("client_source_inter", "requestFieldsClientCategories()")
        return RequestFieldsClient.requestCategories(passedRequest)
    }

    override suspend fun requestFieldsClientRequestPostLoginInfo(passedRequest: InfoFieldRequest):
            GrpcClientResponse<PostLoginInfoResponse?> {
        Log.i("client_source_inter", "requestFieldsClientRequestPostLoginInfo()")
        return RequestFieldsClient.requestPostLoginInfo(passedRequest)
    }

    override suspend fun requestFieldsClientRequestTimestamp(): GrpcClientResponse<TimestampResponse> {
        Log.i("client_source_inter", "requestFieldsClientRequestTimestamp()")
        return RequestFieldsClient().requestTimestamp()
    }

    override suspend fun setFieldsClientAlgorithmSearchOptions(passedRequest: SetAlgorithmSearchOptionsRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientAlgorithmSearchOptions()")
        return SetFieldsClient.setAlgorithmSearchOptions(passedRequest)
    }

    override suspend fun setFieldsClientOptedInToPromotionalEmail(
        passedRequest: SetOptedInToPromotionalEmailRequest
    ): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsOptedInToPromotionalEmail()")
        return SetFieldsClient.setOptedInToPromotionalEmail(passedRequest)
    }

    override suspend fun setFieldsClientBirthday(passedRequest: SetBirthdayRequest):
            GrpcClientResponse<SetBirthdayResponse> {
        Log.i("client_source_inter", "setFieldsClientBirthday()")
        return SetFieldsClient.setBirthday(passedRequest)
    }

    override suspend fun setFieldsClientEmail(passedRequest: SetEmailRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientEmail()")
        return SetFieldsClient.setEmail(passedRequest)
    }

    override suspend fun setFieldsClientGender(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientGender()")
        return SetFieldsClient.setGender(passedRequest)
    }

    override suspend fun setFieldsClientFirstName(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientFirstName()")
        return SetFieldsClient.setFirstName(passedRequest)
    }

    override suspend fun setFieldsClientPicture(passedRequest: SetPictureRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientPicture()")
        return SetFieldsClient().setPicture(passedRequest)
    }

    override suspend fun setFieldsClientCategories(passedRequest: SetCategoriesRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientCategories()")
        return SetFieldsClient.setCategories(passedRequest)
    }

    override suspend fun setFieldsClientAgeRange(passedRequest: SetAgeRangeRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientAgeRange()")
        return SetFieldsClient.setAgeRange(passedRequest)
    }

    override suspend fun setFieldsClientGenderRange(passedRequest: SetGenderRangeRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientGenderRange()")
        return SetFieldsClient.setGenderRange(passedRequest)
    }

    override suspend fun setFieldsClientUserBio(passedRequest: SetBioRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientUserBio()")
        return SetFieldsClient.setUserBio(passedRequest)
    }

    override suspend fun setFieldsClientUserCity(passedRequest: SetStringRequest):
            GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientUserCity()")
        return SetFieldsClient.setUserCity(passedRequest)
    }

    override suspend fun setFieldsClientMaxDistance(passedRequest: SetMaxDistanceRequest): GrpcClientResponse<SetFieldResponse> {
        Log.i("client_source_inter", "setFieldsClientMaxDistance()")
        return SetFieldsClient.setMaxDistance(passedRequest)
    }

    override suspend fun setFieldsClientFeedback(passedRequest: SetFeedbackRequest): GrpcClientResponse<SetFeedbackResponse> {
        Log.i("client_source_inter", "setFieldsClientFeedback()")
        return SetFieldsClient().setFeedback(passedRequest)
    }

    override suspend fun smsVerificationClientSMSVerification(passedRequest: SMSVerificationRequest):
            GrpcClientResponse<SMSVerificationResponse?> {
        Log.i("client_source_inter", "smsVerificationClientSMSVerification()")
        return SMSVerificationClient.smsVerification(passedRequest)
    }

    override suspend fun findMatches(passedRequest: FindMatches.FindMatchesRequest):
            Flow<GrpcClientResponse<FindMatches.FindMatchesResponse>> {
        Log.i("client_source_inter", "findMatches()")
        return FindMatchesClient.findMatches(passedRequest)
    }

    override suspend fun userMatchOptionsSwipe(passedRequest: ReportMessages.UserMatchOptionsRequest):
            GrpcClientResponse<ReportMessages.UserMatchOptionsResponse> {
        Log.i("client_source_inter", "userMatchOptionsSwipe()")
        return UserMatchOptionsClient().userMatchOptionsSwipe(passedRequest)
    }

    override suspend fun updateSingleMatchMember(passedRequest: UpdateSingleMatchMemberRequest):
            GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {
        Log.i("client_source_inter", "updateSingleMatchMember()")
        return UserMatchOptionsClient().updateSingleMatchMember(passedRequest)
    }

    override suspend fun sendChatMessageToServer(
        request: ClientMessageToServerRequest
    ): GrpcClientResponse<ClientMessageToServerResponse> {
        Log.i("client_source_inter", "sendChatMessageToServer()")
        return ChatRoomCommandsClient().sendChatMessageToServer(request)
    }

    override suspend fun createChatRoom(passedRequest: CreateChatRoomRequest):
            GrpcClientResponse<CreateChatRoomResponse> {
        Log.i("client_source_inter", "createChatRoom()")
        return ChatRoomCommandsClient().createChatRoom(passedRequest)
    }

    override suspend fun joinChatRoom(
        context: Context,
        request: JoinChatRoomRequest,
        checkPrimer: (
            response: GrpcClientResponse<ChatMessageToClientMessage.ChatMessageToClient>,
            chatRoomStatus: ChatRoomStatus
        ) -> JoinChatRoomPrimerValues,
    ): GrpcClientResponse<JoinChatRoomPrimerValues> {
        Log.i("client_source_inter", "joinChatRoom()")
        return ChatRoomCommandsClient().joinChatRoom(
            context,
            request,
            checkPrimer,
            ioDispatcher
        )
    }

    override suspend fun leaveChatRoom(passedRequest: LeaveChatRoomRequest):
            GrpcClientResponse<LeaveChatRoomResponse> {
        Log.i("client_source_inter", "leaveChatRoom()")
        return ChatRoomCommandsClient().leaveChatRoom(passedRequest)
    }

    override suspend fun removeFromChatRoom(passedRequest: RemoveFromChatRoomRequest):
            GrpcClientResponse<RemoveFromChatRoomResponse> {
        Log.i("client_source_inter", "removeFromChatRoom()")
        return ChatRoomCommandsClient().removeFromChatRoom(passedRequest)
    }

    override suspend fun unMatchFromChatRoom(passedRequest: UnMatchRequest):
            GrpcClientResponse<UnMatchResponse> {
        Log.i("client_source_inter", "unMatchFromChatRoom()")
        return ChatRoomCommandsClient().unMatchFromChatRoom(passedRequest)
    }

    override suspend fun blockAndReportChatRoom(passedRequest: BlockAndReportChatRoomRequest):
            GrpcClientResponse<ReportMessages.UserMatchOptionsResponse> {
        Log.i("client_source_inter", "blockAndReportChatRoom()")
        return ChatRoomCommandsClient().blockAndReportChatRoom(passedRequest)
    }

    override suspend fun unblockOtherUser(passedRequest: UnblockOtherUserRequest):
            GrpcClientResponse<UnblockOtherUserResponse> {
        Log.i("client_source_inter", "unblockOtherUser()")
        return ChatRoomCommandsClient().unblockOtherUser(passedRequest)
    }

/*    override suspend fun getSingleChatRoomImage(passedRequest: GetSingleChatImageRequest):
            GrpcClientResponse<GetSingleChatImageResponse> {
        return ChatRoomCommandsClient().getSingleChatRoomImage(passedRequest)
    }*/

    override suspend fun promoteNewAdmin(passedRequest: PromoteNewAdminRequest):
            GrpcClientResponse<PromoteNewAdminResponse> {
        Log.i("client_source_inter", "promoteNewAdmin()")
        return ChatRoomCommandsClient().promoteNewAdmin(passedRequest)
    }

    override suspend fun updateChatRoomInfo(passedRequest: UpdateChatRoomInfoRequest):
            GrpcClientResponse<UpdateChatRoomInfoResponse> {
        Log.i("client_source_inter", "updateChatRoomInfo()")
        return ChatRoomCommandsClient().updateChatRoomInfo(passedRequest)
    }

    override suspend fun setPinnedLocation(passedRequest: SetPinnedLocationRequest):
            GrpcClientResponse<SetPinnedLocationResponse> {
        return ChatRoomCommandsClient().setPinnedLocation(passedRequest)
    }

    override suspend fun updateSingleChatRoomMember(passedRequest: UpdateSingleChatRoomMemberRequest):
            GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {
        Log.i("client_source_inter", "updateSingleChatRoomMember()")
        return ChatRoomCommandsClient().updateSingleChatRoomMember(passedRequest)
    }

    override suspend fun updateChatRoom(
        chatRoomId: String,
        passedRequest: UpdateChatRoomRequest,
        handleResponse: suspend (GrpcClientResponse<UpdateChatRoomResponse>) -> Boolean
    ) {
        Log.i("client_source_inter", "updateChatRoom()")
        ChatRoomCommandsClient().updateChatRoom(
            chatRoomId,
            passedRequest,
            handleResponse
        )
    }

    override suspend fun startChatStream(
        metaDataHeaderParams: Metadata,
        responseObserver: ChatStreamObject.ChatMessageStreamObserver
    ): GrpcClientResponse<StreamObserver<ChatMessageStream.ChatToServerRequest>?> {
        Log.i("client_source_inter", "startChatStream()")
        return ChatMessageStreamClient.startChatStream(metaDataHeaderParams, responseObserver)
    }

    override suspend fun beginEmailVerification(
        passedRequest: EmailVerificationRequest
    ): GrpcClientResponse<EmailVerificationResponse> {
        Log.i("client_source_inter", "beginEmailVerification()")
        return EmailSendingMessagesClient().beginEmailVerification(passedRequest)
    }

    override suspend fun beginAccountRecovery(
        passedRequest: AccountRecoveryRequest
    ): GrpcClientResponse<AccountRecoveryResponse> {
        Log.i("client_source_inter", "beginAccountRecovery()")
        return EmailSendingMessagesClient().beginAccountRecovery(passedRequest)
    }
}
