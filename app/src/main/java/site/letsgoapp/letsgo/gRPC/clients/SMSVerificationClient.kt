package site.letsgoapp.letsgo.gRPC.clients

import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import sms_verification.SMSVerificationRequest
import sms_verification.SMSVerificationResponse
import sms_verification.SMSVerificationServiceGrpcKt
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SMSVerificationClient {
    //the default state is false but this is explicit
    private val functionRunning = AtomicBoolean(false)

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun smsVerification(
        passedRequest: SMSVerificationRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<SMSVerificationResponse?> {

        if (!functionRunning.getAndSet(true)) { //if loginFunction is NOT running then set it to true

            return grpcFunctionCallTemplate<SMSVerificationResponse>(
                {
                    SMSVerificationResponse.getDefaultInstance()
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        SMSVerificationResponse.newBuilder()
                            .setReturnStatus(SMSVerificationResponse.Status.SUCCESS)
                            .build()
                    },
                    {
                        GlobalValues.runWithShortRPCManagedChannel { channel ->
                            SMSVerificationServiceGrpcKt.SMSVerificationServiceCoroutineStub(
                                channel
                            )
                                .withDeadlineAfter(
                                    GlobalValues.gRPC_Short_Call_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).sMSVerificationRPC(passedRequest)
                        }
                    }
                ),
                {
                    functionRunning.set(false)
                }
            )
        }

        return GrpcClientResponse(
            null,
            "~",
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        )
    }

}