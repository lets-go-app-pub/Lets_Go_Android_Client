package site.letsgoapp.letsgo.gRPC.clients

import findmatches.FindMatches
import findmatches.FindMatches.FindMatchesRequest
import findmatches.FindMatches.FindMatchesResponse
import findmatches.FindMatchesServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import java.util.concurrent.TimeUnit

object FindMatchesClient {

    suspend fun findMatches(
        passedRequest: FindMatchesRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): Flow<GrpcClientResponse<FindMatchesResponse>> = flow {

        val response = grpcFunctionCallTemplate<FindMatchesResponse>(
            {
                val capMessage = FindMatches.FindMatchesCapMessage.newBuilder()

                FindMatchesResponse.newBuilder()
                    .setFindMatchesCap(capMessage)
                    .build()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    val capMessage = FindMatches.FindMatchesCapMessage.newBuilder()

                    FindMatchesResponse.newBuilder()
                        .setFindMatchesCap(capMessage)
                        .build()
                },
                {
                    GlobalValues.runWithFindMatchesRPCManagedChannel { channel ->
                        var returnValue = FindMatchesResponse.getDefaultInstance()

                        FindMatchesServiceGrpcKt.FindMatchesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Find_Matches_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).findMatchRPC(passedRequest).collect { response ->
                                returnValue = response

                                //call back to the repository to store the object
                                emit(
                                    GrpcClientResponse(
                                        returnValue,
                                        "~",
                                        GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                                    )
                                )

                                if (returnValue.hasFindMatchesCap()) {
                                    return@collect
                                }
                            }

                        returnValue
                    }
                }
            )
        )

        //NOTE: Only the last message will ever have a ReturnStatus. So only need to return the
        // final GrpcAndroidSideErrorsEnum if it is NOT NO_ANDROID_ERRORS, otherwise it is
        // handled in the function above.
        if (response.androidErrorEnum != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
            emit(response)
        }

    }
}