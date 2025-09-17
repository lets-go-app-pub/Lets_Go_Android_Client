package site.letsgoapp.letsgo.gRPC.clients

import android.content.Context
import request_fields.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithFindMatchesRPCManagedChannel
import site.letsgoapp.letsgo.globalAccess.ServiceLocator.provideLoginFunctions
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import status_enum.StatusEnum
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RequestFieldsClient {

    //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
    //I will put an atomic bool to block so the other functions will not complete they will move on and fail
    suspend fun requestPictures(
        applicationContext: Context,
        passedRequest: PictureRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcFunctionErrorStatusEnum {

        val response =
            grpcFunctionCallTemplate(
                { androidErrorStatus ->

                    val returnVal =
                        when (androidErrorStatus) {
                            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
                            }
                            GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                                GrpcFunctionErrorStatusEnum.SERVER_DOWN
                            }
                            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION,
                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS,
                            -> {
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                            }
                        }

                    returnVal

                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS
                    },
                    {
                        runWithFindMatchesRPCManagedChannel { channel ->
                            var returnValue = GrpcFunctionErrorStatusEnum.NO_ERRORS

                            //This should not overlap with find matches on initial login (because find matches will not run
                            // until initial login has completed). And after that it may take longer, however it should not
                            // inconvenience the user. ALSO this function only runs if the user pictures are out of date so
                            // it will rarely ever run.
                            val client =
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )

                            client.withDeadlineAfter(
                                GlobalValues.gRPC_Request_User_Pictures_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).requestPicturesRPC(passedRequest).collect { response ->

                                //call back to the repository to store the object
                                returnValue = provideLoginFunctions(applicationContext)
                                    .runRequestPicturesProtoRPC(
                                        GrpcClientResponse(
                                            response,
                                            "~",
                                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                                        )
                                    )

                                if (returnValue != GrpcFunctionErrorStatusEnum.NO_ERRORS
                                ) { //if request picture failed
                                    return@collect
                                }
                            }

                            returnValue
                        }
                    }
                )
            )

        return response.response
    }

    suspend fun requestTimestamp(testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING):
            GrpcClientResponse<TimestampResponse> {

        return grpcFunctionCallTemplate(
            {
                TimestampResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    TimestampResponse.newBuilder()
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        val request = TimestampRequest.newBuilder().build()
                        RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).requestTimestampRPC(request)
                    }
                }
            )
        )
    }

    companion object {

        //the default state is false but this is explicit
        private val requestingPhoneNumber = AtomicBoolean(false)
        private val requestingBirthday = AtomicBoolean(false)
        private val requestingEmail = AtomicBoolean(false)
        private val requestingGender = AtomicBoolean(false)
        private val requestingFirstName = AtomicBoolean(false)
        private val requestingCategories = AtomicBoolean(false)
        private val requestingIcons = AtomicBoolean(false)
        private val requestingPostLoginInfo = AtomicBoolean(false)

        //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
        //I will put an atomic bool to block so the other functions will not complete they will move on and fail
        suspend fun requestServerIcons(
            applicationContext: Context,
            passedRequest: ServerIconsRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcFunctionErrorStatusEnum {

            var returnVar = GrpcFunctionErrorStatusEnum.DO_NOTHING

            if (!requestingIcons.getAndSet(true)) { //if function is NOT running then set it to true
                val response =
                    grpcFunctionCallTemplate(
                        { androidErrorStatus ->

                            val returnVal =
                                when (androidErrorStatus) {
                                    GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                                        GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
                                    }
                                    GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                                        GrpcFunctionErrorStatusEnum.SERVER_DOWN
                                    }
                                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION,
                                    GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS,
                                    -> {
                                        GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                                    }
                                }

                            returnVal

                        },
                        bakeExceptionThrowingIntoLambda(
                            testingStatus,
                            {
                                GrpcFunctionErrorStatusEnum.NO_ERRORS
                            },
                            {
                                runWithFindMatchesRPCManagedChannel { channel ->
                                    var returnValue = GrpcFunctionErrorStatusEnum.NO_ERRORS

                                    //This should not overlap with find matches on initial login (because find matches will not run
                                    // until initial login has completed). And after that it may take longer, however it should not
                                    // inconvenience the user. ALSO this function only runs if the user icons are out of date so
                                    // it will rarely ever run.
                                    val client =
                                        RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                            channel
                                        )

                                    client.withDeadlineAfter(
                                        GlobalValues.gRPC_Request_Icons_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestServerIconsRPC(passedRequest).collect { response ->
                                        val nextResponse = GrpcClientResponse(
                                            response,
                                            "~",
                                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                                        )

                                        //call back to the repository to store the object
                                        returnValue = provideLoginFunctions(applicationContext)
                                            .handleRequestIconsResponse(nextResponse)

                                        if (returnValue != GrpcFunctionErrorStatusEnum.NO_ERRORS
                                        ) { //if request icon failed
                                            return@collect
                                        }
                                    }

                                    returnValue
                                }
                            }
                        ),
                        {
                            requestingIcons.set(false)
                        }
                    )

                returnVar = response.response

            }

            return returnVar
        }

        //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
        //I will put an atomic bool to block so the other functions will not complete they will move on and fail
        suspend fun requestPhoneNumber(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<InfoFieldResponse?> {

            if (!requestingPhoneNumber.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<InfoFieldResponse>(
                    {
                        InfoFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            InfoFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestPhoneNumberRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingPhoneNumber.set(false)
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
        suspend fun requestBirthday(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<BirthdayResponse?> {

            if (!requestingBirthday.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<BirthdayResponse>(
                    {
                        BirthdayResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            BirthdayResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestBirthdayRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingBirthday.set(false)
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
        suspend fun requestEmail(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<EmailResponse?> {

            if (!requestingEmail.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<EmailResponse>(
                    {
                        EmailResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            EmailResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestEmailRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingEmail.set(false)
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
        suspend fun requestGender(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<InfoFieldResponse?> {

            if (!requestingGender.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<InfoFieldResponse>(
                    {
                        InfoFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            InfoFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestGenderRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingGender.set(false)
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
        suspend fun requestFirstName(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<InfoFieldResponse?> {

            if (!requestingFirstName.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<InfoFieldResponse>(
                    {
                        InfoFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            InfoFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestNameRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingFirstName.set(false)
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
        suspend fun requestCategories(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<CategoriesResponse?> {

            if (!requestingCategories.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<CategoriesResponse>(
                    {
                        CategoriesResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            CategoriesResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestCategoriesRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingCategories.set(false)
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
        suspend fun requestPostLoginInfo(
            passedRequest: InfoFieldRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
        ): GrpcClientResponse<PostLoginInfoResponse?> {

            if (!requestingPostLoginInfo.getAndSet(true)) { //if function is NOT running then set it to true

                return grpcFunctionCallTemplate<PostLoginInfoResponse>(
                    {
                        PostLoginInfoResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            PostLoginInfoResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                RequestFieldsServiceGrpcKt.RequestFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).requestPostLoginInfoRPC(passedRequest)
                            }
                        }
                    ),
                    {
                        requestingPostLoginInfo.set(false)
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
}