package site.letsgoapp.letsgo.gRPC.clients

import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass
import loginfunction.LoginRequest
import loginfunction.LoginResponse
import loginfunction.LoginServiceGrpcKt
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object LoginFunctionClient {
    //the default state is false but this is explicit
    private val loggingIn = AtomicBoolean(false)

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun login(
        passedRequest: LoginRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<LoginResponse?> {
        if (!loggingIn.getAndSet(true)) { //if loginFunction is NOT running then set it to true

            return grpcFunctionCallTemplate<LoginResponse>(
                {
                    LoginResponse.getDefaultInstance()
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        LoginResponse.newBuilder()
                            .setReturnStatus(LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.LoginAccountStatus.LOGGED_IN)
                            .build()
                    },
                    {
                        GlobalValues.runWithShortRPCManagedChannel { channel ->
                            LoginServiceGrpcKt.LoginServiceCoroutineStub(
                                channel
                            )
                                .withDeadlineAfter(
                                    GlobalValues.gRPC_Short_Call_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).loginRPC(passedRequest)
                        }
                    }
                ),
                {
                    loggingIn.set(false)
                }
            )
        } else {
            return GrpcClientResponse(null, "~", GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS)
        }

    }
}