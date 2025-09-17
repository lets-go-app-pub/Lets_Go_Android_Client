package site.letsgoapp.letsgo.gRPC.clients

import send_error_to_server.SendErrorRequest
import send_error_to_server.SendErrorResponse
import send_error_to_server.SendErrorServiceGrpcKt
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import java.util.concurrent.TimeUnit

class LogErrorClient {

    suspend fun logError(
        passedRequest: SendErrorRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<SendErrorResponse> {

        return grpcFunctionCallTemplate(
            {
                SendErrorResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    SendErrorResponse.newBuilder()
                        .setReturnStatus(SendErrorResponse.Status.SUCCESSFUL)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        SendErrorServiceGrpcKt.SendErrorServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).sendErrorRPC(passedRequest)
                    }
                }
            )
        )
    }
}