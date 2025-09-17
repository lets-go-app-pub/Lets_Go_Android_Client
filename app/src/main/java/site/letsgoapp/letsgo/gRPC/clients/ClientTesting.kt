package site.letsgoapp.letsgo.gRPC.clients

import io.grpc.Status
import io.grpc.StatusRuntimeException
import send_error_to_server.SendErrorResponse

enum class ClientExceptionTestingEnum {
    NOT_TESTING,
    NO_EXCEPTION,
    EXCEPTION_UNKNOWN,
    EXCEPTION_UNAVAILABLE,
    EXCEPTION_DEADLINE_EXCEEDED
}

enum class ErrorClientEnum {
    NO_VALUE_SET,
    SUCCESSFUL,
    OUTDATED_VERSION,
    DATABASE_DOWN,
    FAIL;
}

class GenerateClientTesting {

    @Suppress("unused")
    fun makeLogErrorReturn(errorClientEnum: ErrorClientEnum): SendErrorResponse {
        val response = SendErrorResponse.newBuilder()

        when (errorClientEnum) {
            ErrorClientEnum.NO_VALUE_SET -> {
                response.returnStatus = SendErrorResponse.Status.NO_VALUE_SET
            }
            ErrorClientEnum.SUCCESSFUL -> {
                response.returnStatus = SendErrorResponse.Status.SUCCESSFUL
            }
            ErrorClientEnum.OUTDATED_VERSION -> {
                response.returnStatus = SendErrorResponse.Status.OUTDATED_VERSION
            }
            ErrorClientEnum.DATABASE_DOWN -> {
                response.returnStatus = SendErrorResponse.Status.DATABASE_DOWN
            }
            ErrorClientEnum.FAIL -> {
                response.returnStatus = SendErrorResponse.Status.FAIL
            }
        }

        return response.build()
    }

    fun makeExceptions(testingStatus: ClientExceptionTestingEnum): StatusRuntimeException {

        return when (testingStatus) {
            ClientExceptionTestingEnum.EXCEPTION_UNKNOWN -> {
                StatusRuntimeException(Status.UNKNOWN)
            }
            ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE -> {
                StatusRuntimeException(Status.UNAVAILABLE)
            }
            ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED -> {
                StatusRuntimeException(Status.DEADLINE_EXCEEDED)
            }
            else -> {
                StatusRuntimeException(Status.INVALID_ARGUMENT)
            }
        }

    }
}