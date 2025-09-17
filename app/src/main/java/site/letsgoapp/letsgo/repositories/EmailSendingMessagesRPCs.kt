package site.letsgoapp.letsgo.repositories

import android.content.Context
import email_sending_messages.AccountRecoveryRequest
import email_sending_messages.EmailVerificationRequest
import email_sending_messages.EmailVerificationResponse
import kotlinx.coroutines.CoroutineDispatcher
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.*

suspend fun runBeginEmailVerification(
    callingFragmentInstanceID: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
): EmailVerificationReturnValues {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val request = EmailVerificationRequest.newBuilder()
            .setLoginInfo(getLoginInfo(loginToken))
            .build()

        val response = clientsIntermediate.beginEmailVerification(request)

        val errorReturn =
            checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

        val returnErrorStatusEnum = errorReturn.second
        val returnString =
            when (returnErrorStatusEnum) {
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "DO_NOTHING was reached which should never happen here.\n" +
                            "request: $request \n" +
                            "response: $response \n"
                }
                else -> {
                    errorReturn.first
                }
            }

        if (returnString != "~") {

            errorMessageRepositoryHelper(
                returnString,
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

        return EmailVerificationReturnValues(
            returnErrorStatusEnum,
            response.response,
            callingFragmentInstanceID
        )
    } else {
        return EmailVerificationReturnValues(
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
            EmailVerificationResponse.getDefaultInstance(),
            callingFragmentInstanceID
        )
    }
}

suspend fun runBeginAccountRecovery(
    phoneNumber: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
): AccountRecoveryReturnValues {

    val request = AccountRecoveryRequest.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setLetsGoVersion(GlobalValues.Lets_GO_Version_Number)
        .build()

    val response = clientsIntermediate.beginAccountRecovery(request)

    val errorReturn = checkApplicationAndroidErrorEnum(
        response.androidErrorEnum,
        response.errorMessage
    ) {
        Pair(response.errorMessage, GrpcFunctionErrorStatusEnum.NO_ERRORS)
    }

    val returnErrorStatusEnum = errorReturn.second
    val returnString =
        when (returnErrorStatusEnum) {
            GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                "DO_NOTHING was reached which should never happen here.\n" +
                        "request: $request \n" +
                        "response: $response \n"
            }
            else -> {
                errorReturn.first
            }
        }

    if (returnString != "~") {

        errorMessageRepositoryHelper(
            returnString,
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

    return AccountRecoveryReturnValues(returnErrorStatusEnum, response.response, phoneNumber)
}