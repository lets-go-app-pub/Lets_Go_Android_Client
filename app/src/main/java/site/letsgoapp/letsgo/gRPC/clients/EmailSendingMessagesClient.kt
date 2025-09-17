package site.letsgoapp.letsgo.gRPC.clients

import email_sending_messages.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import status_enum.StatusEnum
import java.util.concurrent.TimeUnit

class EmailSendingMessagesClient {

    suspend fun beginEmailVerification(
        passedRequest: EmailVerificationRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<EmailVerificationResponse> {
        return grpcFunctionCallTemplate(
            {
                EmailVerificationResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    EmailVerificationResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        EmailSendingMessagesServiceGrpcKt.EmailSendingMessagesServiceCoroutineStub(
                            channel
                        ).withDeadlineAfter(
                            GlobalValues.gRPC_Short_Call_Deadline_Time,
                            TimeUnit.MILLISECONDS
                        ).emailVerificationRPC(passedRequest)
                    }
                }
            )
        )
    }

    suspend fun beginAccountRecovery(
        passedRequest: AccountRecoveryRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<AccountRecoveryResponse> {
        return grpcFunctionCallTemplate(
            {
                AccountRecoveryResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    AccountRecoveryResponse.newBuilder()
                        .setAccountRecoveryStatus(AccountRecoveryResponse.AccountRecoveryStatus.SUCCESS)
                        .setEmailSentStatus(EmailSentStatus.EMAIL_SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        EmailSendingMessagesServiceGrpcKt.EmailSendingMessagesServiceCoroutineStub(
                            channel
                        ).withDeadlineAfter(
                            GlobalValues.gRPC_Short_Call_Deadline_Time,
                            TimeUnit.MILLISECONDS
                        ).accountRecoveryRPC(passedRequest)
                    }
                }
            )
        )
    }

}