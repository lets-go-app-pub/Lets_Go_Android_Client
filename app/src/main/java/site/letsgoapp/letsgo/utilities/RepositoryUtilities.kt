package site.letsgoapp.letsgo.utilities

import android.content.Context
import android.os.SystemClock
import com.bumptech.glide.signature.ObjectKey
import com.google.protobuf.ByteString
import grpc_chat_commands.ChatRoomCommands
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import status_enum.StatusEnum
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext

object RepositoryUtilities {
    const val BIRTH_YEAR_BUNDLE_KEY = "birthYear"
    const val BIRTH_MONTH_BUNDLE_KEY = "birthMonth"
    const val BIRTH_DAY_OF_MONTH_BUNDLE_KEY = "birthDayOfMonth"

    //keys used in set and request gRPC client calls
    const val ALGORITHM_SEARCH_OPTIONS_KEY = "algo_search" //int
    const val OPTED_IN_TO_PROMOTIONAL_EMAIL_KEY = "promo_email" //bool
    const val EMAIL_ADDRESS_KEY = "email" //string
    const val GENDER_KEY = "gender" //string
    const val FIRST_NAME_KEY = "firstName" //string
    const val BIO_KEY = "bio" //string
    const val CITY_KEY = "city" //string
    const val MIN_AGE_RANGE_KEY = "minAgeRange" //int
    const val MAX_AGE_RANGE_KEY = "maxAgeRange" //int
    const val MAX_DISTANCE_KEY = "maxDistance" //int
    const val GENDER_RANGE_KEY = "genderRange" //string


    const val CATEGORIES_KEY = "categoriesKey"
}

//When a function returns LOGIN_TOKEN_DID_NOT_MATCH as a return value there are some issues with it.
// First of all if there are 2 clients logging each other out, this can get into a loop effectively spamming
// the server back and forth. However, this IS possible to happen if say a very large message is sent and
// the token is expired by the time the client has finished uploading and the server has finished downloading it.
// If the client reaches MAX_NUMBER_TIMES_LOGIN_TOKEN_CAN_NOT_MATCH number of returned LOGIN_TOKEN_DID_NOT_MATCH
// values within MINIMUM_TIME_BETWEEN_LOGIN_TOKEN_NOT_MATCHING, the server will log out. Otherwise it will simply
// attempt to reconnect.
object LoginTokenDidNotMatchReturn {
    private var mostRecentTimeLoginTokenStartedNotMatching = -1L
    private var numberTimesLoginTokenDidNotMatch = 0
    private const val MAX_NUMBER_TIMES_LOGIN_TOKEN_CAN_NOT_MATCH = 10 //arbitrarily chosen value
    private const val MINIMUM_TIME_BETWEEN_LOGIN_TOKEN_NOT_MATCHING = 30L * 1000L //in millis

    fun getReturnValueForLoginTokenDidNotMatch(): GrpcFunctionErrorStatusEnum {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - mostRecentTimeLoginTokenStartedNotMatching < MINIMUM_TIME_BETWEEN_LOGIN_TOKEN_NOT_MATCHING) { //LOGIN_TOKEN_DID_NOT_MATCH returned recently
            if (numberTimesLoginTokenDidNotMatch >= MAX_NUMBER_TIMES_LOGIN_TOKEN_CAN_NOT_MATCH) { //max number of returns exceeded, log user out
                mostRecentTimeLoginTokenStartedNotMatching = -1L
                numberTimesLoginTokenDidNotMatch = 0
                return GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO
            } else {
                numberTimesLoginTokenDidNotMatch++
            }
        } else { //LOGIN_TOKEN_DID_NOT_MATCH has not been returned recently
            mostRecentTimeLoginTokenStartedNotMatching = currentTime
            numberTimesLoginTokenDidNotMatch = 1
        }
        return GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
    }
}

enum class RequestTypeEnum {
    UNKNOWN,
    REQUEST_PHONE_NUMBER,
    REQUEST_BIRTHDAY,
    REQUEST_EMAIL,
    REQUEST_GENDER,
    REQUEST_NAME,
    REQUEST_CATEGORIES;
}

enum class SetTypeEnum {
    UNKNOWN,
    SET_ALGORITHM_SEARCH_OPTIONS,
    SET_OPTED_IN_TO_PROMOTIONAL_EMAIL,
    SET_BIRTHDAY,
    SET_EMAIL,
    SET_GENDER,
    SET_FIRST_NAME,
    SET_PICTURE,
    SET_CATEGORIES,
    SET_BIO,
    SET_CITY,
    SET_AGE_RANGE,
    SET_MAX_DISTANCE,
    SET_GENDER_RANGE;
}

//NOTE: Because these pictures can be 'updated', including the timestamp in the name. Otherwise a situation can
// occur where StartDeleteFile(applicationContext).sendFileToWorkManager() starts the DeleteFileWorker and it
// doesn't run until AFTER the update is done. This means the order goes file overwritten->file deleted, including
// the timestamp in the file name will prevent this bug.
fun generateCurrentUserPictureFile(index: Int, timestamp: Long, applicationContext: Context): File {
    //might want to actually save these pictures, in the future could move them somewhere
    // for the user to see
    val fileName =
        "${applicationContext.getString(R.string.user_picture_file_name_prefix)}_${index}_${timestamp}_"
    return File(applicationContext.filesDir, fileName)
}

fun generatePictureMessageFile(messageUUID: String, applicationContext: Context): File {

    //using the message uuid to allow the pictures to overwrite each other for the same message
    val fileName =
        "${applicationContext.getString(R.string.user_picture_chat_message_file_prefix)}_${messageUUID}_"
    return File(applicationContext.cacheDir, fileName)
}

fun generateQRCodeImageFile(chatRoomId: String, applicationContext: Context): File {

    //using the chat room id to allow the image to overwrite each other for the same chat room
    val fileName =
        "${applicationContext.getString(R.string.user_mime_type_chat_qr_code_file_prefix)}_${chatRoomId}_"
    return File(applicationContext.filesDir, fileName)
}

//NOTE: mimeTypeUrl is expected to NOT be empty
//NOTE: Because these pictures can not be 'updated', not including the timestamp in the name.
fun generateMimeTypeFile(applicationContext: Context, mimeTypeUrl: String): File {

    val mimeTypeFileNamePrefix =
        applicationContext.getString(R.string.user_mime_type_chat_message_file_prefix)

    val tempFileNameBody = mimeTypeUrl.replace(Regex("[^a-zA-Z0-9]"), "")

    val mimeTypeFileNameBody =
        if (tempFileNameBody.length < 4) {
            //This weird little algorithm will
            // 1) assign each spot a character based on its position
            // 2) sort the newly assigned characters by the original character
            //It won't work perfectly (repeats are theoretically possible), however
            // it is a decent alternative to getting a short or empty string.
            //It will provide repeatably the same name based on the url.
            val pairs = mutableListOf<Pair<Char, Char>>()
            val charList = ('a'..'z') + ('A'..'Z') + ('0'..'9')

            for (i in mimeTypeUrl.indices) {
                pairs.add(
                    Pair(
                        charList[i % charList.size],
                        mimeTypeUrl[i]
                    )
                )
            }

            pairs.sortBy {
                it.second
            }

            String(
                pairs.map {
                    it.first
                }.toCharArray()
            )
        } else {
            tempFileNameBody
        }

    val maxFileSize = 254
    val mimeTypeFileName = mimeTypeFileNamePrefix + "_" + mimeTypeFileNameBody

    return if (mimeTypeFileName.length <= maxFileSize) { //if file name is not too large
        File(applicationContext.cacheDir, mimeTypeFileName)
    } else { //if file name is not too large
        val numCharsToTrim = mimeTypeFileName.length - maxFileSize

        val newMimeTypeFileBodyName = mimeTypeFileNameBody.removeRange(0.rangeTo(numCharsToTrim))
        val newMimeTypeFileName = mimeTypeFileNamePrefix + "_" + newMimeTypeFileBodyName

        File(applicationContext.cacheDir, newMimeTypeFileName)
    }
}

//NOTE: Because these pictures can be 'updated', including the timestamp in the name. Otherwise a situation can
// occur where StartDeleteFile(applicationContext).sendFileToWorkManager() starts the DeleteFileWorker and it
// doesn't run until AFTER the update is done. This means the order goes file overwritten->file deleted, including
// the timestamp in the file name will prevent this bug.
fun generateOtherUserPictureFile(
    accountID: String,
    indexNumber: Int,
    timestamp: Long,
    applicationContext: Context,
): File {
    val fileName =
        "${applicationContext.getString(R.string.other_user_picture_file_name_prefix)}_${accountID}_${indexNumber}_${timestamp}_"
    return File(applicationContext.cacheDir, fileName)
}

//NOTE: Because these pictures can be 'updated', including the timestamp in the name. Otherwise a situation can
// occur where StartDeleteFile(applicationContext).sendFileToWorkManager() starts the DeleteFileWorker and it
// doesn't run until AFTER the update is done. This means the order goes file overwritten->file deleted, including
// the timestamp in the file name will prevent this bug.
fun generateOtherUserThumbnailFile(
    accountID: String,
    timestamp: Long,
    applicationContext: Context
): File {
    val fileName =
        "${applicationContext.getString(R.string.other_user_thumbnail_file_name_prefix)}_${accountID}_${timestamp}_"
    return File(applicationContext.filesDir, fileName)
}

//the parameter is the messageUUID of the message containing the reply, not the UUID of
// the message being replied to
//NOTE: Because these pictures can not be 'updated', not including the timestamp in the name.
fun generateReplyThumbnailFile(messageUUID: String, applicationContext: Context): File {
    //this will guarantee that each message only has 1 thumbnail file path stored for it
    val fileName =
        "${applicationContext.getString(R.string.user_reply_thumbnail_chat_message_file_prefix)}_${messageUUID}_"
    return File(applicationContext.filesDir, fileName)
}

fun storeChatPictureToFile(
    messageUUID: String,
    pictureFileInBytes: ByteString,
    pictureFileSize: Int,
    applicationContext: Context,
    errorMessage: suspend (errorString: String, lineNumber: Int, fileName: String, stackTrace: String) -> Unit,
    ioDispatcher: CoroutineContext = ServiceLocator.globalIODispatcher,
): String {

    return if (pictureFileInBytes.size() == pictureFileSize) { //if file is proper size

        val pictureFile =
            generatePictureMessageFile(
                messageUUID,
                applicationContext
            )

        storeChatPictureToFile(
            pictureFile,
            pictureFileInBytes,
            errorMessage,
            ioDispatcher
        )
    } else { //if file is wrong size (corrupt file)
        GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
    }
}

fun storeChatPictureToFile(
    pictureFile: File,
    pictureFileInBytes: ByteString,
    errorMessage: suspend (errorString: String, lineNumber: Int, fileName: String, stackTrace: String) -> Unit,
    ioDispatcher: CoroutineContext = ServiceLocator.globalIODispatcher,
): String {

    //save the picture to file
    return try {
        pictureFile.writeBytes(pictureFileInBytes.toByteArray())

        pictureFile.absolutePath //return value
    } catch (ex: IOException) {

        val errorString =
            "IOException when receiving chat message picture message\n" +
                    "\npictureFileInBytes Size: ${pictureFileInBytes.size()}"

        CoroutineScope(ioDispatcher).launch {
            errorMessage(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors()
            )
        }

        GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
    }

}

fun storeChatQRCodeToFile(
    chatRoomId: String,
    qRCodeImageFileInBytes: ByteString,
    applicationContext: Context,
    errorMessage: suspend (errorString: String, lineNumber: Int, fileName: String, stackTrace: String) -> Unit,
    ioDispatcher: CoroutineContext = ServiceLocator.globalIODispatcher,
): String {

    val qRCodeImageFile =
        generateQRCodeImageFile(
            chatRoomId,
            applicationContext
        )

    return try {
        qRCodeImageFile.writeBytes(qRCodeImageFileInBytes.toByteArray())

        qRCodeImageFile.absolutePath //return value
    } catch (ex: IOException) {

        val errorString =
            "IOException when receiving chat qr code image\n" +
                    "\nqRCodeImageFileInBytes Size: ${qRCodeImageFileInBytes.size()}"

        CoroutineScope(ioDispatcher).launch {
            errorMessage(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors()
            )
        }

        GlobalValues.server_imported_values.qrCodeDefault
    }
}

fun generateFileObjectKey(
    timestamp: Long
): ObjectKey {
    return ObjectKey(timestamp)
}

fun generateErrorWorkerFile(
    applicationContext: Context,
): File {
    /**NOTE: if changing the applicationContext.filesDir also change it inside of ErrorHandlerWorker.kt. **/
    val fileName =
        "${applicationContext.getString(R.string.error_worker_file_name_prefix)}_${generateChatMessageUUID()}_"
    return File(applicationContext.filesDir, fileName)
}

//this will 'convert' a GrpcAndroidSideErrorsEnum into an ErrorStatusEnum OR if no errors, run the lambda
fun checkApplicationAndroidErrorEnum(
    androidErrorEnum: GrpcAndroidSideErrorsEnum,
    errorMessage: String,
    noAndroidErrorsLambda: () -> Pair<String, GrpcFunctionErrorStatusEnum>,
): Pair<String, GrpcFunctionErrorStatusEnum> {

    var returnString = "~"

    val returnErrorStatusEnum: GrpcFunctionErrorStatusEnum =
        when (androidErrorEnum) {
            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {
                return noAndroidErrorsLambda()
            }
            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
            }
            GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                GrpcFunctionErrorStatusEnum.SERVER_DOWN
            }
            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                returnString = errorMessage
                GrpcFunctionErrorStatusEnum.LOG_USER_OUT
            }
        }

    return Pair(returnString, returnErrorStatusEnum)
}

//this will 'convert' a StatusEnum.ReturnStatus into an ErrorStatusEnum
fun <T> checkApplicationReturnStatusEnum(
    returnStatus: StatusEnum.ReturnStatus,
    response: GrpcClientResponse<T>
): Pair<String, GrpcFunctionErrorStatusEnum> {
    return checkApplicationAndroidErrorEnum(
        response.androidErrorEnum,
        response.errorMessage
    )
    {
        checkApplicationReturnStatusEnum(
            returnStatus,
            false
        )
    }
}

//this will 'convert' a StatusEnum.ReturnStatus into an ErrorStatusEnum
fun checkApplicationReturnStatusEnum(
    returnStatus: StatusEnum.ReturnStatus,
    androidSideErrorStatus: GrpcAndroidSideErrorsEnum,
    extractedErrorMessage: String,
    invalidParameterAcceptable: Boolean
): Pair<String, GrpcFunctionErrorStatusEnum> {
    return checkApplicationAndroidErrorEnum(
        androidSideErrorStatus,
        extractedErrorMessage
    )
    {
        checkApplicationReturnStatusEnum(
            returnStatus,
            invalidParameterAcceptable
        )
    }
}

//this will 'convert' a StatusEnum.ReturnStatus into an ErrorStatusEnum
private fun checkApplicationReturnStatusEnum(
    returnStatus: StatusEnum.ReturnStatus,
    invalidParameterAcceptable: Boolean
): Pair<String, GrpcFunctionErrorStatusEnum> {

    var returnString = "~"

    //INVALID_PARAMETER_PASSED is acceptable in several of the 'set' functions. This is because if the server is updated
    // the credentials could end up different than the client and it should NOT crash because of it.
    val updatedReturnStatus =
        if (invalidParameterAcceptable && returnStatus == StatusEnum.ReturnStatus.INVALID_PARAMETER_PASSED) {
            StatusEnum.ReturnStatus.SUCCESS
        } else {
            returnStatus
        }

    val returnErrorStatusEnum: GrpcFunctionErrorStatusEnum =
        when (updatedReturnStatus) {
            StatusEnum.ReturnStatus.SUCCESS -> {
                GrpcFunctionErrorStatusEnum.NO_ERRORS
            }
            StatusEnum.ReturnStatus.LOGIN_TOKEN_EXPIRED -> //possibilities where this could happen
            // 1) deep sleep was entered and the app wasn't re-logging
            // 2) no connection and the token timed out
            {
                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
            }
            StatusEnum.ReturnStatus.LOGGED_IN_ELSEWHERE -> { //this will need to display an error message to the user before clearing the database
                GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE
            }
            StatusEnum.ReturnStatus.LOGIN_TOKEN_DID_NOT_MATCH, //possibilities where this could happen
            // 1) logged in elsewhere was returned to a different function (maybe even a different device) and then this was found
            // -if I try to log back in here, I could get caught inside a loop where 2 devices are trying to reconnect, so not going to do that
            // 2) login token expired so long ago that it was deleted by the server
            // 3) user logged out on a different device, then this function ran
            // 4) a large rpc takes so long to download or upload that the token it carried is expired
            -> {
                LoginTokenDidNotMatchReturn.getReturnValueForLoginTokenDidNotMatch()
            }
            StatusEnum.ReturnStatus.INVALID_LOGIN_TOKEN, //this should never happen, however if it does there will be no token to perform the logout
            StatusEnum.ReturnStatus.OUTDATED_VERSION, //this means the server was updated when the client was logged in, cannot log out because wrong version
            StatusEnum.ReturnStatus.NO_VERIFIED_ACCOUNT,
            -> { //this could happen if account was deleted on a different device
                if (returnStatus == StatusEnum.ReturnStatus.INVALID_LOGIN_TOKEN) {
                    returnString = "Account received status '$returnStatus' while logged in.\n" +
                            "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                }
                GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO
            }
            StatusEnum.ReturnStatus.CORRUPTED_FILE, //used with picture messages to signal the file sent to the server was received corrupted, this means was still corrupt after retrying
            StatusEnum.ReturnStatus.INVALID_USER_OID, //user OID was incorrect length
            StatusEnum.ReturnStatus.INVALID_INSTALLATION_ID, //installation ID was invalid
            StatusEnum.ReturnStatus.INVALID_PARAMETER_PASSED, //this means a parameter was not valid (such as a chat room Id was too short or long)
            StatusEnum.ReturnStatus.VALUE_NOT_SET, //this is the default value, it means the response was not set by the server
            StatusEnum.ReturnStatus.LG_ERROR, //an error occurred on server (clearing database and cleaning up account a bit by logging out)
            StatusEnum.ReturnStatus.UNKNOWN, //an error occurred on server (clearing database and cleaning up account a bit by logging out)
            StatusEnum.ReturnStatus.NOT_ENOUGH_INFO, //this should never occur after initial info is collected
            StatusEnum.ReturnStatus.UNRECOGNIZED,
            -> { //an invalid valid was used to set this enum
                if (returnStatus == StatusEnum.ReturnStatus.INVALID_INSTALLATION_ID) {
                    reGenerateInstallationId(GlobalValues.applicationContext)
                } else if (returnStatus != StatusEnum.ReturnStatus.LG_ERROR) { //any LG_ERROR should have been stored on server side
                    returnString = "Account received status '$returnStatus' while logged in.\n" +
                            "Actual Line Number: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "Actual File Name: ${Thread.currentThread().stackTrace[2].fileName}\n"
                }

                GrpcFunctionErrorStatusEnum.LOG_USER_OUT
            }
            StatusEnum.ReturnStatus.FUNCTION_CALLED_TOO_QUICKLY -> { //Some functions are not meant to be called repeatedly too quickly, if they are this will be returned.
                GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY
            }
            StatusEnum.ReturnStatus.ACCOUNT_SUSPENDED -> {
                GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED
            }
            StatusEnum.ReturnStatus.ACCOUNT_BANNED -> {
                GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED
            }
            StatusEnum.ReturnStatus.DATABASE_DOWN -> { //means the server received a database down error
                GrpcFunctionErrorStatusEnum.SERVER_DOWN
            }
            StatusEnum.ReturnStatus.SUBSCRIPTION_REQUIRED -> { //means the server received a database down error
                GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION
            }
        }

    return Pair(returnString, returnErrorStatusEnum)
}

suspend fun errorMessageRepositoryHelper(
    errorString: String,
    lineNumber: Int,
    fileName: String,
    stackTrace: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
) {
    withContext(ioDispatcher) {
        //NOTE: Could use a join around these, however it isn't really very important
        // this is purely for errors and this function can be called inside a transaction
        // already (although this isn't actually a problem because android will ignore
        // nested transactions)

        //get the user account information from database if present
        val accountData =
            accountInfoDataSource.getAccountInfoForErrors()
        val accountPictures =
            accountPicturesDataSource.getAllPictures()

        var errorString1 = errorString + "\n"

        errorString1 += accountData?.toString() ?: "User account requested is null.\n"

        for (i in accountPictures.indices) {
            errorString1 += "Extracted Index: i\n"
            errorString1 += accountPictures[i].toString()
        }

        errorHandling.storeError(
            fileName,
            lineNumber,
            stackTrace,
            errorString1,
            applicationContext
        )
    }
}

//clears all files except for icons
fun clearAllFilesHelper(
    applicationContext: Context,
    deleteFileInterface: StartDeleteFileInterface
) {

    val fileNames: Array<String> = applicationContext.fileList()

    //clears the disk cache from glide
    GlideApp.get(applicationContext).clearDiskCache()

    CoroutineScope(Dispatchers.Main).launch {
        GlideApp.get(applicationContext).clearMemory()
    }

    //prefixes for files stored inside cache dir
    val fileChatPicPrefix =
        applicationContext.getString(R.string.user_picture_chat_message_file_prefix)
    val fileChatGifPrefix =
        applicationContext.getString(R.string.user_mime_type_chat_message_file_prefix)

    //prefixes for files stored inside files dir
    val filePicturePrefix = applicationContext.getString(R.string.user_picture_file_name_prefix)
    val fileSelfiePrefix =
        applicationContext.getString(R.string.user_pictures_from_camera_file_prefix)
    val fileTempChatPicPrefix =
        applicationContext.getString(R.string.user_temporary_pictures_chat_message_file_prefix)
    val fileReplyThumbnailPrefix =
        applicationContext.getString(R.string.user_reply_thumbnail_chat_message_file_prefix)
    val fileChatQRCodePrefix =
        applicationContext.getString(R.string.user_mime_type_chat_qr_code_file_prefix)

    val fileMatchPicturePrefix =
        applicationContext.getString(R.string.other_user_picture_file_name_prefix)
    val fileOtherUserThumbnailPrefix =
        applicationContext.getString(R.string.other_user_thumbnail_file_name_prefix)

    val fileErrorMessagePrefix =
        applicationContext.getString(R.string.error_worker_file_name_prefix)

    //NOTE: could separate between cache and files dirs and only check for the relevant prefixes
    // however this function should not be run very much and this way should guarantee that they will
    // all be deleted even if stored inside the wrong dir

    //iterate through the app/files directory
    for (fileName in fileNames) {

        if (fileName.matchesPrefix(fileChatPicPrefix)
            || fileName.matchesPrefix(fileChatQRCodePrefix)
            || fileName.matchesPrefix(fileChatGifPrefix)

            || fileName.matchesPrefix(filePicturePrefix)
            || fileName.matchesPrefix(fileSelfiePrefix)
            || fileName.matchesPrefix(fileTempChatPicPrefix)
            || fileName.matchesPrefix(fileReplyThumbnailPrefix)

            || fileName.matchesPrefix(fileMatchPicturePrefix)
            || fileName.matchesPrefix(fileOtherUserThumbnailPrefix)

            || fileName.matchesPrefix(fileErrorMessagePrefix)
        ) {
            //delete the file
            deleteFileInterface.deleteAppPrivateFile(fileName)
        }
    }

    //NOTE: Decided to simply clear the entire cache. Everything should be set up for it anyway.
    applicationContext.cacheDir.deleteRecursively()
}

fun String.matchesPrefix(prefix: String): Boolean {
    if (this.length >= prefix.length) {

        val subString = this.substring(0, prefix.length)
        if (subString == prefix) {
            return true
        }
    }

    return false
}

//NOTE: messageIndexNum will be set to -1 unless joinChatRoom was called from an invite message
data class JoinChatRoomReturnValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val chatRoomStatus: ChatRoomCommands.ChatRoomStatus,
    val chatRoomId: String,
    val uuidPrimaryKey: String,
)

data class JoinChatRoomPrimerValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val chatRoomStatus: ChatRoomCommands.ChatRoomStatus
)

data class PromoteNewAdminReturnValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val userAccountStatesMatched: Boolean
)

data class UpdateChatRoomInfoReturnValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val operationFailed: Boolean
)

data class SetPinnedLocationReturnValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val operationFailed: Boolean
)

data class ReturnAllPicturesDataValues(
    val allPictures: MutableList<AccountPictureDataEntity>,
)

