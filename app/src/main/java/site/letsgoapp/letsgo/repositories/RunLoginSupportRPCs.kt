package site.letsgoapp.letsgo.repositories

import access_status.AccessStatusEnum
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import loginsupport.*
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*

//runs the need verification info proto function
//will repeat if a connection error occurred until loginAttemptNumber hits NUMBER_NETWORK_ATTEMPTS
//will return 3 values
//1) String; if this value is anything but "~" then an error occurred and it needs to be stored, the exception to this is if DO_NOTHING was reached
//2) ErrorStatusEnum; this will return the respective error status, all errors need to be handled elsewhere with the exception of DO_NOTHING
//3) NeededVeriInfoResponseDataClass; this is the proto response, it should be sent back and handled by a fragment and view model
suspend fun runNeedVeriInfoClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    longitude: Double, latitude: Double
): NeededVeriInfoDataHolder {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val returnVal = runNeedVeriInfoHelperClient(
            applicationContext,
            accountInfoDataSource,
            clientsIntermediate,
            loginToken,
            longitude,
            latitude
        )

        if (returnVal.first != "~") {
            errorMessageRepositoryHelper(
                returnVal.first,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors(),
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                errorHandling,
                ioDispatcher
            )
        }

        return NeededVeriInfoDataHolder(returnVal.second, returnVal.third)
    } else { //if login token not good
        return NeededVeriInfoDataHolder(
            NeededVeriInfoResponse.getDefaultInstance(),
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
        )
    }
}

private suspend fun runNeedVeriInfoHelperClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    loginToken: String,
    longitude: Double,
    latitude: Double
): Triple<String, NeededVeriInfoResponse, GrpcFunctionErrorStatusEnum> {

    //return values
    val returnErrorStatusEnum: GrpcFunctionErrorStatusEnum
    val returnString: String
    val response: GrpcClientResponse<NeededVeriInfoResponse?>

    val request =
        NeededVeriInfoRequest.newBuilder()
            .setLoginInfo(getLoginInfo(loginToken))
            .setClientLongitude(longitude)
            .setClientLatitude(latitude)
            .build()

    response = clientsIntermediate.loginSupportClientNeedVeriInfo(request)

    if (response.response == null) {
        val errorMessage = "Needed veri info returned null, meaning it was running elsewhere"
        return Triple(
            errorMessage,
            NeededVeriInfoResponse.getDefaultInstance(),
            GrpcFunctionErrorStatusEnum.DO_NOTHING
        )
    }

    val errorReturn = checkApplicationReturnStatusEnum(
        response.response.returnStatus,
        response
    )

    returnErrorStatusEnum = errorReturn.second

    returnString =
        when (returnErrorStatusEnum) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                //insert default server generated post login info
                response.response.postLoginInfo.apply {

                    accountInfoDataSource.setPostLoginInfo(
                        userBio.toByteArray().decodeToString(),
                        userCity,
                        ArrayList(genderRangeList),
                        minAge,
                        maxAge,
                        maxDistance,
                        response.response.serverTimestamp
                    )

                    if (response.response.accessStatus == AccessStatusEnum.AccessStatus.ACCESS_GRANTED) {
                        beginUniqueWorkIfNotRunning(applicationContext)
                    }

                    (applicationContext as LetsGoApplicationClass).loginFunctions.updateLoginResponseAccessStatus(
                        response.response.accessStatus
                    )
                }

                errorReturn.first
            }
            GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                "DO_NOTHING should never reach this point.\n" +
                        "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                        "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
            }
            else -> {
                errorReturn.first
            }
        }

    return Triple(returnString, response.response, returnErrorStatusEnum)

}

//runs the delete account proto function
//will repeat if a connection error occurred until loginAttemptNumber hits NUMBER_NETWORK_ATTEMPTS
//will return 2 values
//1) String; if this value is anything but "~" then an error occurred and it needs to be stored, the exception to this is if DO_NOTHING was reached
//2) ErrorStatusEnum; this will return the respective error status, all errors need to be handled elsewhere with the exception of DO_NOTHING
suspend fun runDeleteClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
): GrpcFunctionErrorStatusEnum {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found
        val request =
            LoginSupportRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .build()

        val response = clientsIntermediate.loginSupportClientDeleteAccount(request)

        val errorMessage: String
        val errorResponse: GrpcFunctionErrorStatusEnum

        if (response.response == null) {
            errorMessage = "Delete returned null, meaning it was running elsewhere"
            errorResponse = GrpcFunctionErrorStatusEnum.DO_NOTHING
        } else {
            val returnStatus = checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

            errorResponse = returnStatus.second

            errorMessage =
                if (errorResponse == GrpcFunctionErrorStatusEnum.DO_NOTHING) {
                    "DO_NOTHING should never reach this point.\n" +
                            "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                } else {
                    returnStatus.first
                }
        }

        if (errorMessage != "~") {
            errorMessageRepositoryHelper(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors(),
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                errorHandling,
                ioDispatcher
            )
        }

        return errorResponse
    } else { //if login token not good
        return GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
    }

}

//runs the logout proto function
//will repeat if a connection error occurred until loginAttemptNumber hits NUMBER_NETWORK_ATTEMPTS
//will return 2 values
//1) String; if this value is anything but "~" then an error occurred and it needs to be stored, the exception to this is if DO_NOTHING was reached
//2) ErrorStatusEnum; this will return the respective error status, all errors need to be handled elsewhere with the exception of DO_NOTHING
suspend fun runLogoutClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
): GrpcFunctionErrorStatusEnum {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val request =
            LoginSupportRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .build()

        val response = clientsIntermediate.loginSupportClientLogoutFunction(request)

        val errorMessage: String
        val errorResponse: GrpcFunctionErrorStatusEnum

        if (response.response == null) {
            errorMessage = "Logout returned null, meaning it was running elsewhere"
            errorResponse = GrpcFunctionErrorStatusEnum.DO_NOTHING
        } else {
            val returnStatus = checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

            errorResponse = returnStatus.second

            errorMessage =
                if (errorResponse == GrpcFunctionErrorStatusEnum.DO_NOTHING) {
                    "DO_NOTHING should never reach this point.\n" +
                            "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                } else {
                    returnStatus.first
                }
        }

        if (errorMessage != "~") {
            errorMessageRepositoryHelper(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors(),
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                errorHandling,
                ioDispatcher
            )
        }

        return errorResponse
    } else { //if login token not good
        //if no login token found then there is nothing to log out of
        return GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO
    }

}
