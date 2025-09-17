package site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate

import access_status.AccessStatusEnum
import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking
import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass
import loginfunction.LoginResponse
import prelogintimestamps.PreLoginTimestamps
import requestmessages.RequestMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.convertStringToBlockedAccountsMap
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment.SelectPicturesFragment
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate.Companion.globalValuesToReturn
import site.letsgoapp.letsgo.testingUtility.generateRandomOidForTesting
import site.letsgoapp.letsgo.testingUtility.generateRandomString
import site.letsgoapp.letsgo.testingUtility.generateRandomTimestampForTesting
import site.letsgoapp.letsgo.utilities.convertStringToCategoryActivityMessageAndTrimTimes
import site.letsgoapp.letsgo.utilities.convertStringToGenderRange
import java.io.ByteArrayOutputStream

//If an empty byte array is used, it will generate a random string. Otherwise
// it will used the empty byte array.
fun generateRandomExistingPictureFromServer(
    index: Int,
    timestamp: Long,
    compressedByteArray: ByteArray = byteArrayOf()
): RequestMessages.PictureMessage {
    val usedByteArray =
        if (compressedByteArray.isEmpty()) {
            generateRandomString(
                (1024..1024 * 1024).random(),
                "abcdefghijklmnopqrstuvwxyz"
            ).toByteArray()
        } else {
            compressedByteArray
        }

    return RequestMessages.PictureMessage.newBuilder()
        .setFileInBytes(ByteString.copyFrom(usedByteArray))
        .setFileSize(usedByteArray.size)
        .setIndexNumber(index)
        .setPicHeight(GlobalValues.server_imported_values.pictureMaximumCroppedSizePx)
        .setPicWidth(GlobalValues.server_imported_values.pictureMaximumCroppedSizePx)
        .setTimestampPictureLastUpdated(timestamp)
        .setPictureOid(generateRandomOidForTesting())
        .build()
}

fun generateDeletedPictureFromServer(index: Int) : RequestMessages.PictureMessage {
    return RequestMessages.PictureMessage.newBuilder()
        .setFileInBytes(ByteString.copyFrom("~".toByteArray()))
        .setFileSize(1)
        .setIndexNumber(index)
        .setTimestampPictureLastUpdated(-1)
        .build()
}

fun generateUserAccountPictures(): List<RequestMessages.PictureMessage> {

    val picturesList = mutableListOf<RequestMessages.PictureMessage>()

    val pictureBitmapRaw = BitmapFactory.decodeResource(
        GlobalValues.applicationContext.resources,
        R.drawable.lets_go_logo
    )

    val (pictureHeight, pictureWidth) = SelectPicturesFragment.calculateHeightAndWidthForBitmap(
        GlobalValues.server_imported_values.pictureMaximumCroppedSizePx,
        pictureBitmapRaw.height,
        pictureBitmapRaw.width
    )

    val croppedPictureBitmap = ThumbnailUtils.extractThumbnail(
        pictureBitmapRaw,
        pictureWidth,
        pictureHeight
    )

    val stream = ByteArrayOutputStream()
    croppedPictureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val compressedByteArray: ByteArray = stream.toByteArray()
    croppedPictureBitmap.recycle()

    for (i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
        if ((0..1).random() == 1) {
            picturesList.add(
                generateRandomExistingPictureFromServer(
                    i,
                    generateRandomTimestampForTesting(),
                    compressedByteArray
                )
            )
        } else {
            picturesList.add(
                generateDeletedPictureFromServer(i)
            )
        }
    }

    return picturesList
}

fun generateLoginResponse(
    phoneNumber: String,
    timestamp: Long
): LoginResponse {

    if (
        FakeClientSourceIntermediate.accountStoredOnServer == null
        || FakeClientSourceIntermediate.accountStoredOnServer?.phoneNumber != phoneNumber
    ) { //INITIAL_LOGIN_ACCOUNT_DOES_NOT_EXIST
        return LoginResponse.newBuilder()
            .setReturnStatus(LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.LoginAccountStatus.REQUIRES_AUTHENTICATION)
            .setLoginToken("~")
            .setSmsCoolDown(-1)
            .setAccessStatus(AccessStatusEnum.AccessStatus.STATUS_NOT_SET)
            .setBirthdayNotNeeded(true)
            .setServerTimestamp(timestamp)
            .build()
    }

    val databaseAccountInfoDataEntity =
        runBlocking {
            ServiceLocator.accountInfoDatabase?.accountInfoDatabaseDao?.getAccountInfoForErrors()
        }

    val tempAccountStoredOnServer = FakeClientSourceIntermediate.accountStoredOnServer!!

    val responseBuilder = LoginResponse.newBuilder()
        .setReturnStatus(FakeClientSourceIntermediate.loginFunctionReturnStatusReturn)
        .setAccessStatus(FakeClientSourceIntermediate.loginFunctionAccessStatusReturn)
        .setLoginToken(generateRandomOidForTesting())

        .setSmsCoolDown(-1)
        .setBirthdayNotNeeded(true)
        .setServerTimestamp(timestamp)

        .setPhoneNumber(tempAccountStoredOnServer.phoneNumber)
        .setAccountOid(tempAccountStoredOnServer.accountOID)
        .setAlgorithmSearchOptions(
            AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.forNumber(
                tempAccountStoredOnServer.algorithmSearchOptions
            )
        )

        .setLoginValuesToReturnToClient(
            //NOTE: An empty IconsIndex list means none need updated.
            LoginValuesToReturnToClientOuterClass.LoginValuesToReturnToClient.newBuilder()
                .setGlobalConstantValues(globalValuesToReturn)
                .addAllServerCategories(setupLoginCategoriesList())
                .addAllServerActivities(setupLoginActivitiesList())
                .addAllIconsIndex(FakeClientSourceIntermediate.iconIndexRequiringUpdated)
                .build()
        )

    if (FakeClientSourceIntermediate.smsVerificationRan) { //FIRST_LOGIN_ACCOUNT_EXISTS
        FakeClientSourceIntermediate.smsVerificationRan = false
        return responseBuilder.build()
    }

    responseBuilder
        .setPreLoginTimestamps(
            PreLoginTimestamps.PreLoginTimestampsMessage.newBuilder()
                .setCategoriesTimestamp(tempAccountStoredOnServer.categoriesTimestamp)
                .setNameTimestamp(tempAccountStoredOnServer.firstNameTimestamp)
                .setBirthdayTimestamp(tempAccountStoredOnServer.birthdayTimestamp)
                .setGenderTimestamp(tempAccountStoredOnServer.genderTimestamp)
                .setEmailTimestamp(tempAccountStoredOnServer.emailTimestamp)
                .build()
        )
        .addAllPicturesTimestamps(
            FakeClientSourceIntermediate.picturesStoredOnServer.map {
                it.timestampPictureLastUpdated
            }
        )
        .addAllBlockedAccounts(
            convertStringToBlockedAccountsMap(
                tempAccountStoredOnServer.blockedAccounts
            ).toList()
        )

    if (databaseAccountInfoDataEntity == null
        || databaseAccountInfoDataEntity.birthdayTimestamp < tempAccountStoredOnServer.birthdayTimestamp
    ) {
        responseBuilder.birthdayInfo = RequestMessages.BirthdayMessage.newBuilder()
            .setBirthYear(tempAccountStoredOnServer.birthYear)
            .setBirthMonth(tempAccountStoredOnServer.birthMonth)
            .setBirthDayOfMonth(tempAccountStoredOnServer.birthDayOfMonth)
            .setAge(tempAccountStoredOnServer.age)
            .build()
    }

    if (databaseAccountInfoDataEntity == null
        || databaseAccountInfoDataEntity.emailTimestamp < tempAccountStoredOnServer.emailTimestamp
    ) {
        responseBuilder.emailInfo = RequestMessages.EmailMessage.newBuilder()
            .setEmail(tempAccountStoredOnServer.emailAddress)
            .setRequiresEmailVerification(tempAccountStoredOnServer.requiresEmailAddressVerification)
            .build()
    }

    if (databaseAccountInfoDataEntity == null
        || databaseAccountInfoDataEntity.genderTimestamp < tempAccountStoredOnServer.genderTimestamp
    ) {
        responseBuilder.gender = tempAccountStoredOnServer.gender
    }

    if (databaseAccountInfoDataEntity == null
        || databaseAccountInfoDataEntity.firstNameTimestamp < tempAccountStoredOnServer.firstNameTimestamp
    ) {
        responseBuilder.name = tempAccountStoredOnServer.firstName
    }

    if (databaseAccountInfoDataEntity == null
        || databaseAccountInfoDataEntity.categoriesTimestamp < tempAccountStoredOnServer.categoriesTimestamp
    ) {
        responseBuilder
            .addAllCategoriesArray(
                convertStringToCategoryActivityMessageAndTrimTimes(tempAccountStoredOnServer.categories).second
            )
    }

    if (FakeClientSourceIntermediate.loginFunctionAccessStatusReturn != AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO
        && (databaseAccountInfoDataEntity == null
                || databaseAccountInfoDataEntity.categoriesTimestamp < tempAccountStoredOnServer.categoriesTimestamp)
    ) {
        responseBuilder
            .setPostLoginTimestamp(tempAccountStoredOnServer.postLoginTimestamp)
            .postLoginInfo = RequestMessages.PostLoginMessage.newBuilder()
            .setUserBio(tempAccountStoredOnServer.userBio)
            .setMinAge(tempAccountStoredOnServer.minAge)
            .setMaxAge(tempAccountStoredOnServer.maxAge)
            .setUserCity(tempAccountStoredOnServer.userCity)
            .setMaxDistance(tempAccountStoredOnServer.maxDistance)
            .addAllGenderRange(convertStringToGenderRange(tempAccountStoredOnServer.userGenderRange))
            .build()
    }

    return responseBuilder.build()
}
