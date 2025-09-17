package site.letsgoapp.letsgo.repositories

import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.content.Context
import android.os.Bundle
import categorytimeframe.CategoryTimeFrame
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import setfields.*
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import status_enum.StatusEnum


//sets the birthday on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setBirthdayClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, birthYear: Int, birthMonth: Int, birthDayOfMonth: Int,
): SetFieldsReturnValues {

    val birthdayBundle = Bundle()
    birthdayBundle.putInt(RepositoryUtilities.BIRTH_YEAR_BUNDLE_KEY, birthYear)
    birthdayBundle.putInt(RepositoryUtilities.BIRTH_MONTH_BUNDLE_KEY, birthMonth)
    birthdayBundle.putInt(RepositoryUtilities.BIRTH_DAY_OF_MONTH_BUNDLE_KEY, birthDayOfMonth)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        birthdayBundle,
        SetTypeEnum.SET_BIRTHDAY
    )

}

//sets the email on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setEmailClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, emailAddress: String,
): SetFieldsReturnValues {

    val emailBundle = Bundle()
    emailBundle.putString(RepositoryUtilities.EMAIL_ADDRESS_KEY, emailAddress)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        emailBundle,
        SetTypeEnum.SET_EMAIL
    )

}

//sets the first name on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setFirstNameClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, firstName: String,
): SetFieldsReturnValues {

    val firstNameBundle = Bundle()
    firstNameBundle.putString(RepositoryUtilities.FIRST_NAME_KEY, firstName)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        firstNameBundle,
        SetTypeEnum.SET_FIRST_NAME
    )

}

//sets the gender on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setGenderClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, gender: String,
): SetFieldsReturnValues {

    val genderBundle = Bundle()
    genderBundle.putString(RepositoryUtilities.GENDER_KEY, gender)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        genderBundle,
        SetTypeEnum.SET_GENDER
    )

}

//sets the categories on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setCategoriesClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    categoriesList: ArrayList<CategoryTimeFrame.CategoryActivityMessage>,
): SetFieldsReturnValues {

    val categoriesBundle = Bundle()
    categoriesBundle.putString(
        RepositoryUtilities.CATEGORIES_KEY,
        convertCategoryActivityMessageToString(categoriesList)
    )

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        categoriesBundle,
        SetTypeEnum.SET_CATEGORIES
    )

}

//sets the algorithm match options on the server
suspend fun setAlgorithmSearchOptionsClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    algorithmSearchOptions: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions
): SetFieldsReturnValues {

    val algorithmSearchOptionsBundle = Bundle()
    algorithmSearchOptionsBundle.putInt(
        RepositoryUtilities.ALGORITHM_SEARCH_OPTIONS_KEY,
        algorithmSearchOptions.number
    )

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        algorithmSearchOptionsBundle,
        SetTypeEnum.SET_ALGORITHM_SEARCH_OPTIONS
    )

}

suspend fun setOptedInToPromotionalEmailsClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    optedInForPromotionalEmails: Boolean
): SetFieldsReturnValues {

    val optedInForPromotionalEmailsBundle = Bundle()
    optedInForPromotionalEmailsBundle.putBoolean(
        RepositoryUtilities.OPTED_IN_TO_PROMOTIONAL_EMAIL_KEY,
        optedInForPromotionalEmails
    )

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        optedInForPromotionalEmailsBundle,
        SetTypeEnum.SET_OPTED_IN_TO_PROMOTIONAL_EMAIL
    )

}

//sets the bio on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setBioClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, bio: String,
): SetFieldsReturnValues {

    val bioBundle = Bundle()
    bioBundle.putString(RepositoryUtilities.BIO_KEY, bio)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        bioBundle,
        SetTypeEnum.SET_BIO
    )

}

//sets the city on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setCityClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, city: String,
): SetFieldsReturnValues {

    val cityBundle = Bundle()
    cityBundle.putString(RepositoryUtilities.CITY_KEY, city)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        cityBundle,
        SetTypeEnum.SET_CITY
    )

}

//sets the age range on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setAgeRangeClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, ageRange: AgeRangeHolder,
): SetFieldsReturnValues {

    val ageRangeBundle = Bundle()
    ageRangeBundle.putInt(RepositoryUtilities.MIN_AGE_RANGE_KEY, ageRange.minAgeRange)
    ageRangeBundle.putInt(RepositoryUtilities.MAX_AGE_RANGE_KEY, ageRange.maxAgeRange)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        ageRangeBundle,
        SetTypeEnum.SET_AGE_RANGE
    )

}

//sets the max distance on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setMaxDistanceClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, maxDistance: Int,
): SetFieldsReturnValues {

    val maxDistanceBundle = Bundle()
    maxDistanceBundle.putInt(RepositoryUtilities.MAX_DISTANCE_KEY, maxDistance)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        maxDistanceBundle,
        SetTypeEnum.SET_MAX_DISTANCE
    )

}

//sets the gender range on the server
//returns two values
//1) Long; the timestamp the server returned (can be used in determining error results)
//2) ErrorStatusEnum; the message for the View Model or Fragment to handle
suspend fun setGenderRangeClient(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher, genderRange: String,
): SetFieldsReturnValues {


    val genderRangeBundle = Bundle()
    genderRangeBundle.putString(RepositoryUtilities.GENDER_RANGE_KEY, genderRange)

    return setFieldsTemplate(
        applicationContext,
        accountInfoDataSource,
        accountPicturesDataSource,
        clientsIntermediate,
        errorHandling,
        ioDispatcher,
        genderRangeBundle,
        SetTypeEnum.SET_GENDER_RANGE
    )

}

//template for the set functions in this file
private suspend fun setFieldsTemplate(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    parametersBundle: Bundle,
    setType: SetTypeEnum,
): SetFieldsReturnValues {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        val returnVal =
            LoginFunctions.accountDatabaseMutex.withSharedLock {
                //send request to server
                runSetProtoRPCs(
                    accountInfoDataSource,
                    clientsIntermediate,
                    loginToken, parametersBundle,
                    setType
                )
            }

        if (returnVal.errorMessage != "~") {

            var errorMessage =
                "SetType type $setType.\nerrorMessage: ${returnVal.errorMessage}.\n"

            errorMessage +=
                when (setType) {
                    SetTypeEnum.SET_PICTURE,
                    SetTypeEnum.UNKNOWN,
                    -> {
                    }
                    SetTypeEnum.SET_ALGORITHM_SEARCH_OPTIONS -> {
                        val algorithmSearchOptions =
                            parametersBundle.getInt(
                                RepositoryUtilities.ALGORITHM_SEARCH_OPTIONS_KEY,
                                -1
                            )

                        "algorithmSearchOptions: $algorithmSearchOptions\n"
                    }
                    SetTypeEnum.SET_OPTED_IN_TO_PROMOTIONAL_EMAIL -> {
                        val optedInForPromotionalEmails =
                            parametersBundle.getBoolean(
                                RepositoryUtilities.OPTED_IN_TO_PROMOTIONAL_EMAIL_KEY,
                            )

                        "optedInForPromotionalEmails: $optedInForPromotionalEmails\n"
                    }
                    SetTypeEnum.SET_BIRTHDAY -> {
                        val birthYear =
                            parametersBundle.getInt(RepositoryUtilities.BIRTH_YEAR_BUNDLE_KEY)
                        val birthMonth =
                            parametersBundle.getInt(RepositoryUtilities.BIRTH_MONTH_BUNDLE_KEY)
                        val birthDayOfMonth =
                            parametersBundle.getInt(RepositoryUtilities.BIRTH_DAY_OF_MONTH_BUNDLE_KEY)

                        "birthYear: $birthYear\n" +
                                "birthMonth: $birthMonth\n" +
                                "birthDayOfMonth: $birthDayOfMonth\n"
                    }
                    SetTypeEnum.SET_EMAIL -> {
                        val emailAddress =
                            parametersBundle.getString(RepositoryUtilities.EMAIL_ADDRESS_KEY)

                        "emailAddress: $emailAddress\n"
                    }
                    SetTypeEnum.SET_GENDER -> {
                        val gender = parametersBundle.getString(RepositoryUtilities.GENDER_KEY)

                        "gender: $gender\n"
                    }
                    SetTypeEnum.SET_FIRST_NAME -> {
                        val firstName =
                            parametersBundle.getString(RepositoryUtilities.FIRST_NAME_KEY)

                        "firstName: $firstName\n"
                    }
                    SetTypeEnum.SET_CATEGORIES -> {
                        val setCategoriesString =
                            parametersBundle.getString(RepositoryUtilities.CATEGORIES_KEY)

                        "setCategoriesString: $setCategoriesString\n"
                    }
                    SetTypeEnum.SET_BIO -> {
                        val bio = parametersBundle.getString(RepositoryUtilities.BIO_KEY)

                        "bio: $bio\n"
                    }
                    SetTypeEnum.SET_CITY -> {
                        val city = parametersBundle.getString(RepositoryUtilities.CITY_KEY)

                        "city: $city\n"
                    }
                    SetTypeEnum.SET_AGE_RANGE -> {
                        val minAgeRange =
                            parametersBundle.getInt(RepositoryUtilities.MIN_AGE_RANGE_KEY)
                        val maxAgeRange =
                            parametersBundle.getInt(RepositoryUtilities.MAX_AGE_RANGE_KEY)

                        "minAgeRange: $minAgeRange\n" +
                                "maxAgeRange: $maxAgeRange\n"
                    }
                    SetTypeEnum.SET_MAX_DISTANCE -> {
                        val maxDistance =
                            parametersBundle.getInt(RepositoryUtilities.MAX_DISTANCE_KEY)

                        "maxDistance: $maxDistance\n"
                    }
                    SetTypeEnum.SET_GENDER_RANGE -> {
                        val genderRange =
                            parametersBundle.getString(RepositoryUtilities.GENDER_RANGE_KEY)

                        "genderRange: $genderRange\n"
                    }
                }

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

        return SetFieldsReturnValues(
            returnVal.invalidParameterPassed,
            returnVal.updatedTimestamp,
            returnVal.errorStatus
        )
    } else { //if login token not good
        return SetFieldsReturnValues(
            false,
            -1,
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
        )
    }

}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// String is the error message "~" means no error
// Long is the timestamp it was set on the server
// ErrorStatusEnum is the message of how to handle the return status
private suspend fun runSetProtoRPCs(
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    loginToken: String,
    parameters: Bundle,
    setType: SetTypeEnum,
): SetProtoRpcReturnValue {

    //same response, different requests
    //return values
    val returnErrorStatusEnum: GrpcFunctionErrorStatusEnum
    var returnString: String

    val response: GrpcClientResponse<SetFieldResponse>
    var birthdayResponse: GrpcClientResponse<SetBirthdayResponse> =
        GrpcClientResponse(
            SetBirthdayResponse.getDefaultInstance(),
            "error never initialized",
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        )

    val responseReturnStatus: StatusEnum.ReturnStatus //return status for whichever set request is called
    val errorMessage: String //error message for whichever set request is called
    var responseTimestamp: Long = -1L //timestamp for whichever set request is called
    var androidSideErrorsEnum = GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION

    when (setType) {
        SetTypeEnum.SET_ALGORITHM_SEARCH_OPTIONS -> {
            val algorithmSearchOptionsValue = parameters.getInt(
                RepositoryUtilities.ALGORITHM_SEARCH_OPTIONS_KEY,
                -1
            )

            response = if (algorithmSearchOptionsValue != -1) {

                val request = SetAlgorithmSearchOptionsRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setMatchingStatusValue(algorithmSearchOptionsValue)
                    .build()

                clientsIntermediate.setFieldsClientAlgorithmSearchOptions(request)

            } else {
                val errorString =
                    "Algorithm search options bundle returned -1 extracting '${RepositoryUtilities.ALGORITHM_SEARCH_OPTIONS_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_OPTED_IN_TO_PROMOTIONAL_EMAIL -> {
            val optedInToPromotionalEmailValue = parameters.getBoolean(
                RepositoryUtilities.OPTED_IN_TO_PROMOTIONAL_EMAIL_KEY
            )

            val request = SetOptedInToPromotionalEmailRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .setOptedInToPromotionalEmail(optedInToPromotionalEmailValue)
                .build()

            response = clientsIntermediate.setFieldsClientOptedInToPromotionalEmail(request)

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_BIRTHDAY -> {

            //the possible values for these are defined above calcPersonAge in Utility.kt, essentially they can not be 0
            val birthYear = parameters.getInt(RepositoryUtilities.BIRTH_YEAR_BUNDLE_KEY)
            val birthMonth = parameters.getInt(RepositoryUtilities.BIRTH_MONTH_BUNDLE_KEY)
            val birthDayOfMonth =
                parameters.getInt(RepositoryUtilities.BIRTH_DAY_OF_MONTH_BUNDLE_KEY)

            birthdayResponse = if (birthYear != 0 && birthMonth != 0 && birthDayOfMonth != 0) {

                val request = SetBirthdayRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setBirthYear(birthYear)
                    .setBirthMonth(birthMonth)
                    .setBirthDayOfMonth(birthDayOfMonth)
                    .build()

                clientsIntermediate.setFieldsClientBirthday(request)

            } else {
                val errorString =
                    "Birthday bundle returned null extracting.\n" +
                            "birthYear: '${RepositoryUtilities.BIRTH_YEAR_BUNDLE_KEY}'\n" +
                            "birthMonth: '${RepositoryUtilities.BIRTH_MONTH_BUNDLE_KEY}'\n" +
                            "birthDayOfMonth: '${RepositoryUtilities.BIRTH_DAY_OF_MONTH_BUNDLE_KEY}'\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetBirthdayResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = birthdayResponse.response.returnStatus
            errorMessage = birthdayResponse.errorMessage
            responseTimestamp = birthdayResponse.response.timestamp
            androidSideErrorsEnum = birthdayResponse.androidErrorEnum

        }
        SetTypeEnum.SET_EMAIL -> {

            val emailAddress = parameters.getString(RepositoryUtilities.EMAIL_ADDRESS_KEY)

            response = if (emailAddress != null) {

                val request = SetEmailRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setSetEmail(emailAddress)
                    .build()

                clientsIntermediate.setFieldsClientEmail(request)

            } else {
                val errorString =
                    "Email Address bundle returned null extracting '${RepositoryUtilities.EMAIL_ADDRESS_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum

        }
        SetTypeEnum.SET_GENDER -> {

            val gender = parameters.getString(RepositoryUtilities.GENDER_KEY)

            response = if (gender != null) {

                val request = SetStringRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setSetString(gender)
                    .build()

                clientsIntermediate.setFieldsClientGender(request)

            } else {
                val errorString =
                    "Gender Other bundle returned null extracting '${RepositoryUtilities.GENDER_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_FIRST_NAME -> {

            val firstName = parameters.getString(RepositoryUtilities.FIRST_NAME_KEY)

            response = if (firstName != null) {

                val request = SetStringRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setSetString(firstName)
                    .build()

                clientsIntermediate.setFieldsClientFirstName(request)

            } else {
                val errorString =
                    "First Name bundle returned null extracting '${RepositoryUtilities.FIRST_NAME_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_CATEGORIES -> {

            val setCategoriesString =
                parameters.getString(RepositoryUtilities.CATEGORIES_KEY)

            response = if (setCategoriesString != null) {

                val request = SetCategoriesRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .addAllCategory(convertStringToCategoryActivityMessageAndTrimTimes(setCategoriesString).second)
                    .build()

                clientsIntermediate.setFieldsClientCategories(request)

            } else {
                val errorString =
                    "Categories bundle returned null extracting '${RepositoryUtilities.CATEGORIES_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_BIO -> {

            val bio = parameters.getString(RepositoryUtilities.BIO_KEY)

            response = if (bio != null) {

                val request = SetBioRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setSetString(ByteString.copyFrom(bio.toByteArray()))
                    .build()

                clientsIntermediate.setFieldsClientUserBio(request)

            } else {
                val errorString =
                    "Bio bundle returned null extracting '${RepositoryUtilities.BIO_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_CITY -> {

            val city = parameters.getString(RepositoryUtilities.CITY_KEY)

            response = if (city != null) {

                val request = SetStringRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setSetString(city)
                    .build()

                clientsIntermediate.setFieldsClientUserCity(request)

            } else {
                val errorString =
                    "City bundle returned null extracting '${RepositoryUtilities.CITY_KEY}'.\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_AGE_RANGE -> {

            val minAgeRange = parameters.getInt(RepositoryUtilities.MIN_AGE_RANGE_KEY)
            val maxAgeRange = parameters.getInt(RepositoryUtilities.MAX_AGE_RANGE_KEY)

            response = if (minAgeRange != 0 && maxAgeRange != 0) {

                val request = SetAgeRangeRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setMinAge(minAgeRange)
                    .setMaxAge(maxAgeRange)
                    .build()

                clientsIntermediate.setFieldsClientAgeRange(request)

            } else {
                val errorString =
                    "At least one age range bundle returned 0\n" +
                            "minAgeRange: '${RepositoryUtilities.MIN_AGE_RANGE_KEY}'\n" +
                            "maxAgeRange: '${RepositoryUtilities.MAX_AGE_RANGE_KEY}'\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_MAX_DISTANCE -> {

            val maxDistance = parameters.getInt(RepositoryUtilities.MAX_DISTANCE_KEY)

            response = if (maxDistance != 0) {

                val request = SetMaxDistanceRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setMaxDistance(maxDistance)
                    .build()

                clientsIntermediate.setFieldsClientMaxDistance(request)

            } else {
                val errorString =
                    "Max Distance bundle returned 0 '${RepositoryUtilities.MAX_DISTANCE_KEY}'\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_GENDER_RANGE -> {

            val genderRange = parameters.getString(RepositoryUtilities.GENDER_RANGE_KEY)

            response = if (genderRange != null) {

                val request = SetGenderRangeRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .addAllGenderRange(convertStringToGenderRange(genderRange))
                    .build()

                clientsIntermediate.setFieldsClientGenderRange(request)

            } else {
                val errorString =
                    "GenderRange bundle returned null '${RepositoryUtilities.GENDER_RANGE_KEY}'\n" +
                            "Line Failed At: ${Thread.currentThread().stackTrace[2].lineNumber}"

                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )
            }

            responseReturnStatus = response.response.returnStatus
            errorMessage = response.errorMessage
            responseTimestamp = response.response.timestamp
            androidSideErrorsEnum = response.androidErrorEnum
        }
        SetTypeEnum.SET_PICTURE,
        SetTypeEnum.UNKNOWN,
        -> {
            val errorString =
                "Reached enum $setType when it shouldn't be possible."

            //making this an ANDROID_SIDE_ERROR should make it print on return
            response =
                GrpcClientResponse(
                    SetFieldResponse.getDefaultInstance(),
                    errorString,
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                )

            responseReturnStatus = StatusEnum.ReturnStatus.UNKNOWN
            errorMessage = response.errorMessage
            responseTimestamp = -1L
            androidSideErrorsEnum = GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        }
    }

    val errorReturn = checkApplicationReturnStatusEnum(
        responseReturnStatus,
        androidSideErrorsEnum,
        errorMessage,
        true
    )

    returnErrorStatusEnum = errorReturn.second
    returnString = errorReturn.first

    when (returnErrorStatusEnum) {
        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
            when (setType) {
                SetTypeEnum.SET_ALGORITHM_SEARCH_OPTIONS -> {
                    accountInfoDataSource.setAlgorithmMatchOptions(
                        parameters.getInt(
                            RepositoryUtilities.ALGORITHM_SEARCH_OPTIONS_KEY,
                            AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY_VALUE
                        )
                    )
                }
                SetTypeEnum.SET_OPTED_IN_TO_PROMOTIONAL_EMAIL -> {
                    accountInfoDataSource.setOptedInToPromotionalEmail(
                        parameters.getBoolean(
                            RepositoryUtilities.OPTED_IN_TO_PROMOTIONAL_EMAIL_KEY,
                        )
                    )
                }
                SetTypeEnum.SET_BIRTHDAY -> {
                    accountInfoDataSource.setBirthdayInfo(
                        parameters.getInt(RepositoryUtilities.BIRTH_YEAR_BUNDLE_KEY),
                        parameters.getInt(RepositoryUtilities.BIRTH_MONTH_BUNDLE_KEY),
                        parameters.getInt(RepositoryUtilities.BIRTH_DAY_OF_MONTH_BUNDLE_KEY),
                        responseTimestamp,
                        birthdayResponse.response.age
                    )
                }
                SetTypeEnum.SET_EMAIL -> {
                    accountInfoDataSource.setEmailInfo(
                        parameters.getString(RepositoryUtilities.EMAIL_ADDRESS_KEY)!!,
                        true,
                        responseTimestamp
                    )
                }
                SetTypeEnum.SET_GENDER -> {
                    accountInfoDataSource.setGenderInfo(
                        parameters.getString(RepositoryUtilities.GENDER_KEY)!!,
                        responseTimestamp
                    )
                }
                SetTypeEnum.SET_FIRST_NAME -> {
                    accountInfoDataSource.setFirstNameInfo(
                        parameters.getString(RepositoryUtilities.FIRST_NAME_KEY)!!,
                        responseTimestamp
                    )
                }
                SetTypeEnum.SET_CATEGORIES -> {
                    accountInfoDataSource.setCategoryInfo(
                        parameters.getString(RepositoryUtilities.CATEGORIES_KEY)!!,
                        responseTimestamp
                    )
                }
                SetTypeEnum.SET_BIO -> {
                    accountInfoDataSource.setUserBio(
                        parameters.getString(RepositoryUtilities.BIO_KEY)!!
                    )
                }
                SetTypeEnum.SET_CITY -> {
                    accountInfoDataSource.setUserCity(
                        parameters.getString(RepositoryUtilities.CITY_KEY)!!
                    )
                }
                SetTypeEnum.SET_AGE_RANGE -> {
                    accountInfoDataSource.setUserAgeRange(
                        parameters.getInt(RepositoryUtilities.MIN_AGE_RANGE_KEY),
                        parameters.getInt(RepositoryUtilities.MAX_AGE_RANGE_KEY)
                    )
                }
                SetTypeEnum.SET_MAX_DISTANCE -> {
                    accountInfoDataSource.setMaxDistance(
                        parameters.getInt(RepositoryUtilities.MAX_DISTANCE_KEY)
                    )
                }
                SetTypeEnum.SET_GENDER_RANGE -> {
                    accountInfoDataSource.setGenderRange(
                        parameters.getString(RepositoryUtilities.GENDER_RANGE_KEY)!!
                    )
                }
                SetTypeEnum.SET_PICTURE,
                SetTypeEnum.UNKNOWN,
                -> {
                    //this shouldn't be reachable, just here to be exhaustive
                    returnString =
                        "Reached enum $setType when it shouldn't be possible.\n" +
                                "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                                "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                }
            }
        }
        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
            returnString = "DO_NOTHING should never reach this point.\n" +
                    "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                    "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
        }
        else -> {
            //Error message was already saved in returnString for anything relevant
        }
    }

    return SetProtoRpcReturnValue(
        returnString,
        responseReturnStatus == StatusEnum.ReturnStatus.INVALID_PARAMETER_PASSED,
        responseTimestamp,
        returnErrorStatusEnum
    )

}

//runs the gRPC RPC calls using request type GenericRequestDataClass
suspend fun runSetPictureRPC(
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    deleteFileInterface: StartDeleteFileInterface,
    ioDispatcher: CoroutineDispatcher,
    picture: ByteArray,
    thumbnail: ByteArray,
    pictureIndex: Int
): SetPictureReturnDataHolder {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        //same response, different requests
        //return values
        var returnErrorStatusEnum = GrpcFunctionErrorStatusEnum.LOG_USER_OUT
        var errorString = "~"
        var picturePath = ""

        var response = GrpcClientResponse<SetFieldResponse>(
            SetFieldResponse.getDefaultInstance(),
            "failed to initialize response in setPictures",
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
        )

        LoginFunctions.accountDatabaseMutex.withSharedLock {

            val request = SetPictureRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .setFileInBytes(ByteString.copyFrom(picture))
                .setFileSize(picture.size)
                .setThumbnailInBytes(ByteString.copyFrom(thumbnail))
                .setThumbnailSize(thumbnail.size)
                .setPictureArrayIndex(pictureIndex)
                .build()

            var tempResponse = clientsIntermediate.setFieldsClientPicture(request)

            response =
                if (tempResponse.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                    && tempResponse.response.returnStatus == StatusEnum.ReturnStatus.CORRUPTED_FILE
                ) { //if a picture file was corrupt

                    //try again, NOTE: all values are calculated above so re-calculating them will do nothing
                    tempResponse = clientsIntermediate.setFieldsClientPicture(request)

                    if (tempResponse.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                        && tempResponse.response.returnStatus == StatusEnum.ReturnStatus.CORRUPTED_FILE
                    ) { //if a picture file was corrupt

                        //Need to continue here regardless
                        errorString =
                            "SetPicture returned CORRUPTED_FILE twice in a row, meaning the server detected the picture or thumbnail file was corrupted."

                        GrpcClientResponse(
                            tempResponse.response,
                            errorString,
                            tempResponse.androidErrorEnum
                        )
                    } else {
                        tempResponse
                    }
                } else {
                    tempResponse
                }

            val errorReturn = checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response.androidErrorEnum,
                response.errorMessage,
                true
            )

            returnErrorStatusEnum = errorReturn.second
            errorString = errorReturn.first

            when (returnErrorStatusEnum) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                    //NOTE: not using another coRoutine because I only want the database values stored if the file
                    // is successfully saved to 'disk'
                    //save the picture to file
                    val returnVal = saveUserPictureToFileAndDatabase(
                        applicationContext,
                        accountPicturesDataSource,
                        pictureIndex,
                        picture,
                        picture.size,
                        tempResponse.response.timestamp,
                        deleteFileInterface
                    )

                    if (returnVal.errorString != "~") {
                        errorString = returnVal.errorString
                    } else {
                        picturePath = returnVal.picturePath
                    }
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    errorString = "DO_NOTHING should never reach this point.\n" +
                            "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                }
                else -> {
                    //Error message was already saved in returnString for anything relevant
                }
            }

        }

        if (errorString != "~") {

            val errorMessage =
                "Error running setPictureRPC().\nerrorMessage: $errorString.\n" +
                        "pictureSize: ${picture.size}\n" +
                        "thumbnailSize: ${thumbnail.size}\n" +
                        "pictureIndex: $pictureIndex\n"

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

        return SetPictureReturnDataHolder(
            response.response.returnStatus == StatusEnum.ReturnStatus.INVALID_PARAMETER_PASSED,
            response.response.timestamp,
            returnErrorStatusEnum,
            pictureIndex,
            picturePath,
            picture.size
        )

    } else { //if login token not good
        return SetPictureReturnDataHolder(
            false,
            -1,
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
            pictureIndex,
            "",
            0
        )
    }
}