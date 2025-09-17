package site.letsgoapp.letsgo.repositories

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import request_fields.*
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import status_enum.StatusEnum

//sets the categories on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun requestPicturesClient(
    applicationContext: Context,
    clientsIntermediate: ClientsInterface,
    pictureIndexNumbers: List<Int> = listOf(),
): GrpcFunctionErrorStatusEnum {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    return if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val pictureRequest = PictureRequest.newBuilder()
            .setLoginInfo(getLoginInfo(loginToken))
            .addAllRequestedIndexes(pictureIndexNumbers)
            .build()

        //NOTE: errors were already checked for here
        clientsIntermediate.requestFieldsClientPicture(applicationContext, pictureRequest)
    } else { //login token is invalid
        GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
    }

}

//template for the request functions in this file
@Suppress("unused")
private suspend fun requestFieldsTemplate(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    requestType: RequestTypeEnum,
): GrpcFunctionErrorStatusEnum {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val errors =
            LoginFunctions.accountDatabaseMutex.withSharedLock {
                //send request to server
                runRequestProtoRPCs(
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    clientsIntermediate,
                    errorHandling,
                    ioDispatcher, loginToken,
                    requestType
                )
            }

        if (errors.first != "~") {
            val errorMessage = "RequestType type $requestType.\n ${errors.first}"

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

        return errors.second
    } else { //if login token not good
        return GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
    }

}

//runs the gRPC RPC calls for RequestField
//NOTE: at the moment this function is set up to only call requests immediately after login
private suspend fun runRequestProtoRPCs(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    loginToken: String, responseType: RequestTypeEnum,
): Pair<String, GrpcFunctionErrorStatusEnum> {

    //return values
    val returnErrorStatusEnum: GrpcFunctionErrorStatusEnum
    var returnString: String

    //set possible response types
    //these are initialized to null because the compiler can't see that they will always be set
    var infoFieldResponse: GrpcClientResponse<InfoFieldResponse?>? = null
    var birthdayResponse: GrpcClientResponse<BirthdayResponse?>? = null
    var emailResponse: GrpcClientResponse<EmailResponse?>? = null
    var categoriesResponse: GrpcClientResponse<CategoriesResponse?>? = null

    var returnStatus =
        StatusEnum.ReturnStatus.UNKNOWN //return status enum extracted from the gRPC response
    var extractedErrorMessage =
        "" //this is a little overhead but it is used to save a 'when' statement in ANDROID_SIDE_ERROR
    var androidSideErrorStatus = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

    val request = InfoFieldRequest.newBuilder()
        .setLoginInfo(getLoginInfo(loginToken))
        .build()

    when (responseType) {
        RequestTypeEnum.REQUEST_PHONE_NUMBER -> {
            infoFieldResponse = clientsIntermediate.requestFieldsClientPhoneNumber(request)

            if (infoFieldResponse.response != null) {
                returnStatus = infoFieldResponse.response!!.returnStatus
                extractedErrorMessage = infoFieldResponse.response!!.returnString
                androidSideErrorStatus = infoFieldResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestPhoneNumber function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }
        }
        RequestTypeEnum.REQUEST_BIRTHDAY -> {
            birthdayResponse = clientsIntermediate.requestFieldsClientBirthday(request)

            if (birthdayResponse.response != null) {
                returnStatus = birthdayResponse.response!!.returnStatus
                extractedErrorMessage = birthdayResponse.errorMessage
                androidSideErrorStatus = birthdayResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestBirthday function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }
        }
        RequestTypeEnum.REQUEST_EMAIL -> {
            emailResponse = clientsIntermediate.requestFieldsClientEmail(request)

            if (emailResponse.response != null) {
                returnStatus = emailResponse.response!!.returnStatus
                extractedErrorMessage = emailResponse.errorMessage
                androidSideErrorStatus = emailResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestEmail function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }
        }
        RequestTypeEnum.REQUEST_GENDER -> {
            infoFieldResponse = clientsIntermediate.requestFieldsClientGender(request)

            if (infoFieldResponse.response != null) {
                returnStatus = infoFieldResponse.response!!.returnStatus
                extractedErrorMessage = infoFieldResponse.errorMessage
                androidSideErrorStatus = infoFieldResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestGender function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }
        }
        RequestTypeEnum.REQUEST_NAME -> {
            infoFieldResponse = clientsIntermediate.requestFieldsClientFirstName(request)

            if (infoFieldResponse.response != null) {
                returnStatus = infoFieldResponse.response!!.returnStatus
                extractedErrorMessage = infoFieldResponse.errorMessage
                androidSideErrorStatus = infoFieldResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestName function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }

        }
        RequestTypeEnum.REQUEST_CATEGORIES -> {
            categoriesResponse = clientsIntermediate.requestFieldsClientCategories(request)

            if (categoriesResponse.response != null) {
                returnStatus = categoriesResponse.response!!.returnStatus
                extractedErrorMessage = categoriesResponse.errorMessage
                androidSideErrorStatus = categoriesResponse.androidErrorEnum
            } else {
                val errorString =
                    "Response from Android was null meaning the requestCategories function was already running" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                return Pair(errorString, GrpcFunctionErrorStatusEnum.DO_NOTHING)
            }

        }
        RequestTypeEnum.UNKNOWN -> {
            extractedErrorMessage =
                "Reached enum 'RequestTypeEnum.UNKNOWN' when it shouldn't be possible."
            returnStatus = StatusEnum.ReturnStatus.UNKNOWN
        }
    }

    val errorReturn = checkApplicationReturnStatusEnum(
        returnStatus,
        androidSideErrorStatus,
        extractedErrorMessage,
        false
    )

    returnErrorStatusEnum = errorReturn.second
    returnString = errorReturn.first

    when (returnErrorStatusEnum) {
        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
            when (responseType) {
                RequestTypeEnum.REQUEST_PHONE_NUMBER -> {
                    //NOTE: works a little different than other requests, the return string here is the phone number
                    returnString = infoFieldResponse?.response?.returnString ?: ""
                }
                RequestTypeEnum.REQUEST_BIRTHDAY -> {
                    accountInfoDataSource.setBirthdayInfo(
                        birthdayResponse!!.response!!.birthdayInfo.birthYear,
                        birthdayResponse.response!!.birthdayInfo.birthMonth,
                        birthdayResponse.response!!.birthdayInfo.birthDayOfMonth,
                        birthdayResponse.response!!.timestamp,
                        birthdayResponse.response!!.birthdayInfo.age
                    )
                }
                RequestTypeEnum.REQUEST_EMAIL -> {
                    accountInfoDataSource.setEmailInfo(
                        emailResponse!!.response!!.emailInfo.email,
                        emailResponse.response!!.emailInfo.requiresEmailVerification,
                        emailResponse.response!!.timestamp
                    )
                }
                RequestTypeEnum.REQUEST_GENDER -> {
                    accountInfoDataSource.setGenderInfo(
                        infoFieldResponse!!.response!!.returnString,
                        infoFieldResponse.response!!.timestamp
                    )
                }
                RequestTypeEnum.REQUEST_NAME -> {
                    accountInfoDataSource.setFirstNameInfo(
                        infoFieldResponse!!.response!!.returnString,
                        infoFieldResponse.response!!.timestamp
                    )
                }
                RequestTypeEnum.REQUEST_CATEGORIES -> {
                    accountInfoDataSource.setCategoryInfo(
                        categoriesResponse!!.response!!.categoriesArrayList,
                        categoriesResponse.response!!.timestamp
                    )
                }
                RequestTypeEnum.UNKNOWN -> {
                    returnString =
                        "Reached enum 'RequestTypeEnum.UNKNOWN' when it shouldn't be possible.\n" +
                                " Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"
                }
            }
        }
        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
            val errorString = "DO_NOTHING should never reach this point.\n" +
                    "Request Proto Type: $responseType"
            errorMessageRepositoryHelper(
                errorString,
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
        else -> {
        }
    }

    return Pair(returnString, returnErrorStatusEnum)
}