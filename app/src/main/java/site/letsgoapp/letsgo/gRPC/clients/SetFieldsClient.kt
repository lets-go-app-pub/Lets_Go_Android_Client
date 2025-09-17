package site.letsgoapp.letsgo.gRPC.clients

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import setfields.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import status_enum.StatusEnum
import java.util.concurrent.TimeUnit

class SetFieldsClient {

    suspend fun setPicture(
        passedRequest: SetPictureRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<SetFieldResponse> {
        return grpcFunctionCallTemplate(
            {
                SetFieldResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    SetFieldResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    GlobalValues.findEmptyChannelOrLong { channel ->
                        SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                            channel
                        ).withDeadlineAfter(
                            GlobalValues.gRPC_Long_Background_RPC_Deadline_Time,
                            TimeUnit.MILLISECONDS
                        ).setPicturesRPC(passedRequest)
                    }
                }
            )
        )
    }

    suspend fun setFeedback(
        passedRequest: SetFeedbackRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<SetFeedbackResponse> {
        return grpcFunctionCallTemplate(
            {
                SetFeedbackResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    SetFeedbackResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    GlobalValues.runWithShortRPCManagedChannel { channel ->
                        SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).setFeedbackRPC(passedRequest)
                    }
                }
            )
        )
    }

    companion object {
        //these mutexes will only allow 1 function to run at a time, if an atomic variable is used
        // there is the chance the user could navigate fast enough where a function could be double
        // called, especially if their connection is slow, so this method will send both in order
        private val settingAlgorithmSearchOptionsMutex = Mutex()
        private val settingOptedInToPromotionalEmailMutex = Mutex()
        private val settingBirthdayMutex = Mutex()
        private val settingEmailMutex = Mutex()
        private val settingGenderMutex = Mutex()
        private val settingFirstNameMutex = Mutex()
        private val settingCategoriesMutex = Mutex()
        private val settingAgeRangeMutex = Mutex()
        private val settingGenderRangeMutex = Mutex()
        private val settingUserBioMutex = Mutex()
        private val settingUserCityMutex = Mutex()
        private val settingMaxDistanceMutex = Mutex()

        suspend fun setAlgorithmSearchOptions(
            passedRequest: SetAlgorithmSearchOptionsRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingAlgorithmSearchOptionsMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setAlgorithmSearchOptionsRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setOptedInToPromotionalEmail(
            passedRequest: SetOptedInToPromotionalEmailRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingOptedInToPromotionalEmailMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setOptedInToPromotionalEmailRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }


        suspend fun setBirthday(
            passedRequest: SetBirthdayRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetBirthdayResponse> {
            settingBirthdayMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetBirthdayResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetBirthdayResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setBirthdayRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setEmail(
            passedRequest: SetEmailRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingEmailMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setEmailRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setGender(
            passedRequest: SetStringRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingGenderMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setGenderRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setFirstName(
            passedRequest: SetStringRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingFirstNameMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setFirstNameRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setCategories(
            passedRequest: SetCategoriesRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingCategoriesMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setCategoriesRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
        //I will put an atomic bool to block so the other functions will not complete they will move on and fail
        suspend fun setAgeRange(
            passedRequest: SetAgeRangeRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingAgeRangeMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setAgeRangeRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
        //I will put an atomic bool to block so the other functions will not complete they will move on and fail
        suspend fun setGenderRange(
            passedRequest: SetGenderRangeRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingGenderRangeMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setGenderRangeRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        //I only want 1 of these to be running at a time, so instead of using say a mutex lock or the 'synchronized' keyword
        //I will put an atomic bool to block so the other functions will not complete they will move on and fail
        suspend fun setUserBio(
            passedRequest: SetBioRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingUserBioMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setUserBioRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setUserCity(
            passedRequest: SetStringRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingUserCityMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setUserCityRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }

        suspend fun setMaxDistance(
            passedRequest: SetMaxDistanceRequest,
            testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
        ): GrpcClientResponse<SetFieldResponse> {
            settingMaxDistanceMutex.withLock {
                return grpcFunctionCallTemplate(
                    {
                        SetFieldResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            SetFieldResponse.newBuilder()
                                .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                                .build()
                        },
                        {
                            GlobalValues.runWithShortRPCManagedChannel { channel ->
                                SetFieldsServiceGrpcKt.SetFieldsServiceCoroutineStub(
                                    channel
                                )
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Short_Call_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).setMaxDistanceRPC(passedRequest)
                            }
                        }
                    )
                )
            }
        }
    }
}