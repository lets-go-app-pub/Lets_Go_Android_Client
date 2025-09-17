package site.letsgoapp.letsgo.gRPC.clients

import report_enums.ReportMessages
import report_enums.ReportMessages.UserMatchOptionsRequest
import report_enums.ReportMessages.UserMatchOptionsResponse
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithLongBackgroundRPCManagedChannel
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import status_enum.StatusEnum
import update_other_user_messages.UpdateOtherUserMessages
import user_match_options.UpdateSingleMatchMemberRequest
import user_match_options.UserMatchOptionsServiceGrpcKt
import java.util.concurrent.TimeUnit

class UserMatchOptionsClient {

    //Only one of these should be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun userMatchOptionsSwipe(
        passedRequest: UserMatchOptionsRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<UserMatchOptionsResponse> {

        return grpcFunctionCallTemplate(
            {
                UserMatchOptionsResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UserMatchOptionsResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        val client =
                            UserMatchOptionsServiceGrpcKt.UserMatchOptionsServiceCoroutineStub(
                                channel
                            )

                        if (passedRequest.responseType == ReportMessages.ResponseType.USER_MATCH_OPTION_YES) {
                            client.withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).yesRPC(passedRequest)
                        } else {
                            client.withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).noRPC(passedRequest)
                        }
                    }
                }
            )
        )
    }

    suspend fun updateSingleMatchMember(
        passedRequest: UpdateSingleMatchMemberRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {
        return grpcFunctionCallTemplate(
            {
                UpdateOtherUserMessages.UpdateOtherUserResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UpdateOtherUserMessages.UpdateOtherUserResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    runWithLongBackgroundRPCManagedChannel { channel ->
                        UserMatchOptionsServiceGrpcKt.UserMatchOptionsServiceCoroutineStub(
                            channel
                        ).withDeadlineAfter(
                            GlobalValues.gRPC_Long_Background_RPC_Deadline_Time,
                            TimeUnit.MILLISECONDS
                        ).updateSingleMatchMember(passedRequest)
                    }
                }
            )
        )
    }
}