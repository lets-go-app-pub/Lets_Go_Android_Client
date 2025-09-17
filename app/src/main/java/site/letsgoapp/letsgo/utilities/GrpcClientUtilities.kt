package site.letsgoapp.letsgo.utilities

import site.letsgoapp.letsgo.globalAccess.GlobalValues

enum class GrpcAndroidSideErrorsEnum {
    NO_ANDROID_ERRORS,
    CONNECTION_ERROR,
    SERVER_DOWN,
    UNKNOWN_EXCEPTION
}

//used with Grpc Function calls to return an error message (usually with a return status of ANDROID_SIDE_ERROR)
data class GrpcClientResponse<out T>(
    val response: T,
    val errorMessage: String, // NO_ERRORS is represented by "~"
    val androidErrorEnum: GrpcAndroidSideErrorsEnum
)

fun checkExceptionMessageForGrpcError(message: String): GrpcAndroidSideErrorsEnum {

    // known possibilities UNAVAILABLE
    // server was taken down (probably temporarily)
    // client internet is down
    return if (message.startsWith(GlobalValues.NETWORK_UNAVAILABLE)) {
        val networkIsUp = Networking().checkNetworkState()
        if (networkIsUp) { //if the network is up
            //server was taken down
            GrpcAndroidSideErrorsEnum.SERVER_DOWN
        } else { //if the network is not up
            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR
        }
    }
    // known possibilities DEADLINE_EXCEEDED
    // ping is too high
    // server internet is down
    // device is in idle mode (Light Doze or Deep Doze)
    else if (message.startsWith(GlobalValues.NETWORK_DEADLINE_EXCEEDED)) {
        GrpcAndroidSideErrorsEnum.SERVER_DOWN
    }
    //unknown
    else {
        GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
    }

}