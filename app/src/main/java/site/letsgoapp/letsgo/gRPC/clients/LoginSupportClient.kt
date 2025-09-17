package site.letsgoapp.letsgo.gRPC.clients

import loginsupport.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import status_enum.StatusEnum
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object LoginSupportClient {
    //the default state is false but this is explicit
    private val refreshingLoginToken = AtomicBoolean(false)
    private val deletingAccount = AtomicBoolean(false)
    private val loggingOut = AtomicBoolean(false)
    private val requestingVeriInfo = AtomicBoolean(false)
    private val findRemainingTimeOnLoginRPC = AtomicBoolean(false)

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun deleteAccount(
        passedRequest: LoginSupportRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<LoginSupportResponse?> {

        if (!deletingAccount.getAndSet(true)) { //if function is NOT running then set it to true

            return grpcFunctionCallTemplate<LoginSupportResponse>(
                {
                    LoginSupportResponse.getDefaultInstance()
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        LoginSupportResponse.newBuilder()
                            .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                            .build()
                    },
                    {
                        GlobalValues.runWithShortRPCManagedChannel { channel ->
                            LoginSupportServiceGrpcKt.LoginSupportServiceCoroutineStub(
                                channel
                            )
                                .withDeadlineAfter(
                                    GlobalValues.gRPC_Short_Call_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).deleteAccountRPC(passedRequest)
                        }
                    }
                ),
                {
                    deletingAccount.set(false)
                }
            )
        }

        return GrpcClientResponse(
            null,
            "~",
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        )
    }

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun logoutFunction(
        passedRequest: LoginSupportRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<LoginSupportResponse?> {

        if (!loggingOut.getAndSet(true)) { //if function is NOT running then set it to true

            return grpcFunctionCallTemplate<LoginSupportResponse>(
                {
                    LoginSupportResponse.getDefaultInstance()
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        LoginSupportResponse.newBuilder()
                            .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                            .build()
                    },
                    {
                        GlobalValues.runWithShortRPCManagedChannel { channel ->
                            LoginSupportServiceGrpcKt.LoginSupportServiceCoroutineStub(
                                channel
                            )
                                .withDeadlineAfter(
                                    GlobalValues.gRPC_Short_Call_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).logoutRPC(passedRequest)
                        }
                    }
                ),
                {
                    loggingOut.set(false)
                }
            )
        }

        return GrpcClientResponse(
            null,
            "~",
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        )
    }

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun needVeriInfo(
        passedRequest: NeededVeriInfoRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<NeededVeriInfoResponse?> {

        if (!requestingVeriInfo.getAndSet(true)) { //if function is NOT running then set it to true

            return grpcFunctionCallTemplate<NeededVeriInfoResponse>(
                {
                    NeededVeriInfoResponse.getDefaultInstance()
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        NeededVeriInfoResponse.newBuilder()
                            .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                            .build()
                    },
                    {
                        GlobalValues.runWithShortRPCManagedChannel { channel ->
                            LoginSupportServiceGrpcKt.LoginSupportServiceCoroutineStub(
                                channel
                            )
                                .withDeadlineAfter(
                                    GlobalValues.gRPC_Short_Call_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).neededVeriInfoRPC(passedRequest)
                        }
                    }
                ),
                {
                    requestingVeriInfo.set(false)
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