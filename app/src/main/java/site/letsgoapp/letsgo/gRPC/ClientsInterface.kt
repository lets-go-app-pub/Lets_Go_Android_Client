package site.letsgoapp.letsgo.gRPC

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import loginfunction.LoginRequest
import loginfunction.LoginResponse
import loginsupport.*
import report_enums.ReportMessages
import report_enums.ReportMessages.UserMatchOptionsResponse
import request_fields.*
import retrieve_server_load.RetrieveServerLoad
import setfields.*
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import site.letsgoapp.letsgo.utilities.JoinChatRoomPrimerValues
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import update_other_user_messages.UpdateOtherUserMessages
import user_match_options.UpdateSingleMatchMemberRequest

interface ClientsInterface {

    suspend fun retrieveServerLoadInfo(
        channel: ManagedChannel,
        requestNumClients: Boolean
    ): GrpcClientResponse<RetrieveServerLoad.RetrieveServerLoadResponse>

    suspend fun loginFunctionClientLogin(passedRequest: LoginRequest):
            GrpcClientResponse<LoginResponse?>

    suspend fun loginSupportClientDeleteAccount(passedRequest: LoginSupportRequest):
            GrpcClientResponse<LoginSupportResponse?>

    suspend fun loginSupportClientLogoutFunction(passedRequest: LoginSupportRequest):
            GrpcClientResponse<LoginSupportResponse?>

    suspend fun loginSupportClientNeedVeriInfo(passedRequest: NeededVeriInfoRequest):
            GrpcClientResponse<NeededVeriInfoResponse?>

    suspend fun requestFieldsClientRequestServerIcons(
        applicationContext: Context,
        passedRequest: ServerIconsRequest,
    ): GrpcFunctionErrorStatusEnum

    suspend fun requestFieldsClientPhoneNumber(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?>

    suspend fun requestFieldsClientBirthday(passedRequest: InfoFieldRequest):
            GrpcClientResponse<BirthdayResponse?>

    suspend fun requestFieldsClientEmail(passedRequest: InfoFieldRequest):
            GrpcClientResponse<EmailResponse?>

    suspend fun requestFieldsClientGender(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?>

    suspend fun requestFieldsClientFirstName(passedRequest: InfoFieldRequest):
            GrpcClientResponse<InfoFieldResponse?>

    suspend fun requestFieldsClientPicture(
        applicationContext: Context,
        passedRequest: PictureRequest,
    ): GrpcFunctionErrorStatusEnum

    suspend fun requestFieldsClientCategories(passedRequest: InfoFieldRequest):
            GrpcClientResponse<CategoriesResponse?>

    suspend fun requestFieldsClientRequestPostLoginInfo(passedRequest: InfoFieldRequest):
            GrpcClientResponse<PostLoginInfoResponse?>

    suspend fun requestFieldsClientRequestTimestamp():
            GrpcClientResponse<TimestampResponse>

    suspend fun setFieldsClientAlgorithmSearchOptions(
        passedRequest: SetAlgorithmSearchOptionsRequest
    ): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientOptedInToPromotionalEmail(
        passedRequest: SetOptedInToPromotionalEmailRequest
    ): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientBirthday(passedRequest: SetBirthdayRequest):
            GrpcClientResponse<SetBirthdayResponse>

    suspend fun setFieldsClientEmail(passedRequest: SetEmailRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientGender(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientFirstName(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientPicture(passedRequest: SetPictureRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientCategories(passedRequest: SetCategoriesRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientAgeRange(passedRequest: SetAgeRangeRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientGenderRange(passedRequest: SetGenderRangeRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientUserBio(passedRequest: SetBioRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientUserCity(passedRequest: SetStringRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientMaxDistance(passedRequest: SetMaxDistanceRequest): GrpcClientResponse<SetFieldResponse>

    suspend fun setFieldsClientFeedback(passedRequest: SetFeedbackRequest): GrpcClientResponse<SetFeedbackResponse>

    suspend fun smsVerificationClientSMSVerification(passedRequest: SMSVerificationRequest):
            GrpcClientResponse<SMSVerificationResponse?>

    suspend fun findMatches(passedRequest: FindMatches.FindMatchesRequest): Flow<GrpcClientResponse<FindMatches.FindMatchesResponse>>

    suspend fun userMatchOptionsSwipe(passedRequest: ReportMessages.UserMatchOptionsRequest):
            GrpcClientResponse<UserMatchOptionsResponse>

    suspend fun updateSingleMatchMember(
        passedRequest: UpdateSingleMatchMemberRequest,
    ): GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse>

    suspend fun sendChatMessageToServer(request: ClientMessageToServerRequest): GrpcClientResponse<ClientMessageToServerResponse>

    suspend fun createChatRoom(passedRequest: CreateChatRoomRequest):
            GrpcClientResponse<CreateChatRoomResponse>

    suspend fun joinChatRoom(
        context: Context,
        request: JoinChatRoomRequest,
        checkPrimer: (
            response: GrpcClientResponse<ChatMessageToClientMessage.ChatMessageToClient>,
            chatRoomStatus: ChatRoomStatus
        ) -> JoinChatRoomPrimerValues,
    ): GrpcClientResponse<JoinChatRoomPrimerValues>

    suspend fun leaveChatRoom(passedRequest: LeaveChatRoomRequest): GrpcClientResponse<LeaveChatRoomResponse>

    suspend fun removeFromChatRoom(passedRequest: RemoveFromChatRoomRequest): GrpcClientResponse<RemoveFromChatRoomResponse>

    suspend fun unMatchFromChatRoom(passedRequest: UnMatchRequest): GrpcClientResponse<UnMatchResponse>

    suspend fun blockAndReportChatRoom(passedRequest: BlockAndReportChatRoomRequest): GrpcClientResponse<UserMatchOptionsResponse>

    suspend fun unblockOtherUser(passedRequest: UnblockOtherUserRequest): GrpcClientResponse<UnblockOtherUserResponse>

    //suspend fun getSingleChatRoomImage(passedRequest: GetSingleChatImageRequest): GrpcClientResponse<GetSingleChatImageResponse>

    suspend fun promoteNewAdmin(passedRequest: PromoteNewAdminRequest): GrpcClientResponse<PromoteNewAdminResponse>

    suspend fun updateChatRoomInfo(passedRequest: UpdateChatRoomInfoRequest): GrpcClientResponse<UpdateChatRoomInfoResponse>

    suspend fun setPinnedLocation(passedRequest: SetPinnedLocationRequest):
            GrpcClientResponse<SetPinnedLocationResponse>

    suspend fun updateSingleChatRoomMember(passedRequest: UpdateSingleChatRoomMemberRequest):
            GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse>

    suspend fun updateChatRoom(
        chatRoomId: String,
        passedRequest: UpdateChatRoomRequest,
        handleResponse: suspend (GrpcClientResponse<UpdateChatRoomResponse>) -> Boolean
    )

    suspend fun startChatStream(
        metaDataHeaderParams: Metadata,
        responseObserver: ChatStreamObject.ChatMessageStreamObserver
    ): GrpcClientResponse<StreamObserver<ChatMessageStream.ChatToServerRequest>?>

    suspend fun beginEmailVerification(
        passedRequest: EmailVerificationRequest
    ): GrpcClientResponse<EmailVerificationResponse>

    suspend fun beginAccountRecovery(
        passedRequest: AccountRecoveryRequest
    ): GrpcClientResponse<AccountRecoveryResponse>
}