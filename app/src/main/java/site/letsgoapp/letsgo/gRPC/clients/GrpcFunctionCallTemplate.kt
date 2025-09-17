package site.letsgoapp.letsgo.gRPC.clients

import android.util.Log
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CancellationException
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import site.letsgoapp.letsgo.utilities.checkExceptionMessageForGrpcError
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors

//will return a lambda based on the current testingStatus
fun <T> bakeExceptionThrowingIntoLambda(
    testingStatus: ClientExceptionTestingEnum,
    generateTestingReturnValue: suspend () -> T,
    runFunctionBlock: suspend () -> T,
): suspend () -> T {

    return when (testingStatus) {
        ClientExceptionTestingEnum.NOT_TESTING -> {
            runFunctionBlock
        }
        ClientExceptionTestingEnum.NO_EXCEPTION -> {
            {
                generateTestingReturnValue()
            }
        }
        else -> {
            {
                throw GenerateClientTesting().makeExceptions(testingStatus)
            }
        }
    }
}

suspend fun <T> grpcFunctionCallTemplate(
    getAndroidSideErrorForResponse: suspend (androidErrorStatus: GrpcAndroidSideErrorsEnum) -> T,
    runFunctionBlock: suspend () -> T,
    finallyFunction: suspend () -> Unit = {},
    calledFromLoadBalancer: Boolean = false,
    clientsIntermediate: ClientsInterface = ClientsSourceIntermediate()
): GrpcClientResponse<T> {

    Log.i("networkConnection", "running grpcFunctionCallTemplate()")

    if(clientsIntermediate is ClientsSourceIntermediate) {
        Log.i(
            "server_not_fake",
            printStackTraceForErrors()
        )
    }

    //this function flow
    //1) try function
    //2) if function fails AND DEADLINE_EXCEEDED, load balance and try again
    //3) if function fails AND SERVER_DOWN, retry once
    //4) if function fails AND SERVER_DOWN, load balance and try again
    //5) if still exception, fail

    val startTime = System.currentTimeMillis()

    return try {
        GrpcClientResponse(
            runFunctionBlock(),
            "~",
            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
        )
    } catch (e: Exception) {

        //StatusException & CancellationException are from the Kotlin Coroutine stubs
        //StatusRuntimeException is from Java stubs
        when (e) {
            is StatusException, is CancellationException, is StatusRuntimeException -> {
                var message = e.message ?: GlobalValues.NETWORK_UNKNOWN

                if (message.startsWith(GlobalValues.NETWORK_DEADLINE_EXCEEDED)) {
                    //retry 1 more time if deadline was exceeded after load balancing
                    loadBalanceAndRetry(
                        clientsIntermediate,
                        runFunctionBlock,
                        getAndroidSideErrorForResponse,
                        calledFromLoadBalancer
                    )
                } else { //if the user did not have to wait for deadline, try again

                    Log.i(
                        "loadBalancingVal",
                        "grpcFunctionCallTemplate() exception: $message"
                    )

                    Log.i(
                        "loadBalancingVal",
                        "grpcFunctionCallTemplate()\n${printStackTraceForErrors()}"
                    )

                    //NOTE: this will retry if an unknown exception was received OR if
                    // NETWORK_UNAVAILABLE was reached, the UNAVAILABLE apparently represents
                    // a temporary unavailability and so retrying once should be fine

                    try {
                        GrpcClientResponse(
                            runFunctionBlock(),
                            "~",
                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                        )
                    } catch (e: Exception) {

                        //StatusException & CancellationException are from the Kotlin Coroutine stubs
                        //StatusRuntimeException is from Java stubs
                        when (e) {
                            is StatusException, is CancellationException, is StatusRuntimeException -> {
                                message = e.message ?: GlobalValues.NETWORK_UNKNOWN
                                val androidErrorEnum = checkExceptionMessageForGrpcError(message)

                                Log.i(
                                    "loadBalancingVal",
                                    "grpcFunctionCallTemplate() message: $message androidErrorEnum: $androidErrorEnum"
                                )
                                if (androidErrorEnum == GrpcAndroidSideErrorsEnum.SERVER_DOWN) {
                                    loadBalanceAndRetry(
                                        clientsIntermediate,
                                        runFunctionBlock,
                                        getAndroidSideErrorForResponse,
                                        calledFromLoadBalancer
                                    )
                                } else {
                                    GrpcClientResponse(
                                        getAndroidSideErrorForResponse(androidErrorEnum),
                                        message,
                                        androidErrorEnum
                                    )
                                }
                            }
                            else -> throw e
                        }
                    }
                }
            }
            else -> throw e
        }

    } finally {
        finallyFunction()
        Log.i("gRPC_function_call", "gRPC request time: ${System.currentTimeMillis() - startTime}")
    }
}

private suspend fun <T> loadBalanceAndRetry(
    clientsIntermediate: ClientsInterface,
    runFunctionBlock: suspend () -> T,
    getAndroidSideErrorForResponse: suspend (androidErrorStatus: GrpcAndroidSideErrorsEnum) -> T,
    calledFromLoadBalancer: Boolean
): GrpcClientResponse<T> {
    var androidErrorEnum = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

    // don't run load balancing if called from load balancer or could end up with infinite recursion
    if (!calledFromLoadBalancer) { //if this was called from the load balancer
        val loadBalancingResults = GlobalValues.runLoadBalancing(clientsIntermediate)
        if (loadBalancingResults != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
            return GrpcClientResponse(
                getAndroidSideErrorForResponse(loadBalancingResults),
                "~",
                loadBalancingResults
            )
        }
    }

    var message = "~"

    val t = try {
        runFunctionBlock()
    } catch (e: Exception) {

        //StatusException & CancellationException are from the Kotlin Coroutine stubs
        //StatusRuntimeException is from Java stubs
        when (e) {
            is StatusException, is CancellationException, is StatusRuntimeException -> {
                message = e.message ?: GlobalValues.NETWORK_UNKNOWN

                androidErrorEnum = checkExceptionMessageForGrpcError(message)

                getAndroidSideErrorForResponse(androidErrorEnum)
            }
            else -> throw e
        }
    }

    return GrpcClientResponse(t, message, androidErrorEnum)
}
