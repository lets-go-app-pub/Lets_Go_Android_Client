package site.letsgoapp.letsgo.workers.error_handling


import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import error_message.ErrorMessageOuterClass
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import send_error_to_server.SendErrorRequest
import send_error_to_server.SendErrorResponse
import site.letsgoapp.letsgo.gRPC.clients.LogErrorClient
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import java.io.File
import java.io.IOException

class ErrorHandlerWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private data class ExtractedStringData(val string: String, val fileWasRemoved: Boolean)

    private fun extractStringFromFile(
        stringOrFileName: String,
        tooLargeToBePassed: Boolean
    ): ExtractedStringData {
        return if (tooLargeToBePassed) {

            /**NOTE: if changing the applicationContext.filesDir also change it inside of RepositoryUtilities.kt
             *  generateErrorMessageFile(). **/
            val stringFile = File(applicationContext.filesDir, stringOrFileName)

            if (stringFile.exists()) {
                try {
                    val stringFromFile = stringFile.readText()
                    stringFile.delete()
                    ExtractedStringData(stringFromFile, false)
                } catch (ex: IOException) {
                    val errorMessage =
                        "An IOException occurred while attempting to read an error message from file.\n" +
                                "IOException: ${ex.message}\n" +
                                "lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                                "fileName: ${Thread.currentThread().stackTrace[2].fileName}\n"

                    ExtractedStringData(errorMessage, false)
                } catch (ex: SecurityException) {
                    val errorMessage =
                        "A SecurityException occurred while attempting to read an error message from file.\n" +
                                "IOException: ${ex.message}\n" +
                                "lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                                "fileName: ${Thread.currentThread().stackTrace[2].fileName}\n"

                    ExtractedStringData(errorMessage, false)
                }
            } else {
                //this could happen if the file was cleaned (the file will be deleted after a certain amount of time to
                // prevent memory leaks)
                ExtractedStringData("", true)
            }
        } else { //!tooLargeToBePassed
            ExtractedStringData(stringOrFileName, false)
        }
    }

    override suspend fun doWork(): Result {

        Log.i("network_error_stuff", "ErrorHandlerWorker started runAttemptCount: $runAttemptCount")

        if (runAttemptCount > 3) {
            return Result.failure()
        }

        Log.i("ErrorHandlerWorker", "doWork")

        val errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel =
            ErrorOriginEnum.ErrorUrgencyLevel.forNumber(
                inputData.getInt(
                    ERROR_URGENCY_LEVEL,
                    ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_UNKNOWN_VALUE
                )
            )

        val versionNumber: Int = inputData.getInt(VERSION_NUMBER, 1)

        val fileNameOrFileName: String? = inputData.getString(FILE_NAME_OR_FILE_PATH_KEY)
        val fileNameWasTooLarge: Boolean =
            inputData.getBoolean(FILE_NAME_TOO_LARGE_TO_BE_PASSED, false)

        val lineNumber: Int = inputData.getInt(LINE_NUMBER_KEY, 0)

        val stackTraceOrFileName: String? = inputData.getString(STACK_TRACE_OR_FILE_PATH_KEY)
        val stackTraceWasTooLarge: Boolean =
            inputData.getBoolean(STACK_TRACE_TOO_LARGE_TO_BE_PASSED, false)

        val errorMessageOrFileName: String? = inputData.getString(ERROR_MESSAGE_OR_FILE_PATH_KEY)
        val errorMessageWasTooLarge: Boolean =
            inputData.getBoolean(ERROR_MESSAGE_TOO_LARGE_TO_BE_PASSED, false)

        if (
            fileNameOrFileName != null
            && stackTraceOrFileName != null
            && errorMessageOrFileName != null
        ) {

            val fileNameToSend = extractStringFromFile(
                fileNameOrFileName, fileNameWasTooLarge
            )

            if (fileNameToSend.fileWasRemoved) {
                return Result.success()
            }

            val stackTraceToSend = extractStringFromFile(
                stackTraceOrFileName, stackTraceWasTooLarge
            )

            if (stackTraceToSend.fileWasRemoved) {
                return Result.success()
            }

            val errorMessageToSend = extractStringFromFile(
                errorMessageOrFileName, errorMessageWasTooLarge
            )

            if (errorMessageToSend.fileWasRemoved) {
                return Result.success()
            }

            //NOTE: This could return 0 that is perfectly fine.
            val numberErrorsSent = numberErrorsMap.getAndClearNumberOfErrors(
                fileNameToSend.string,
                lineNumber
            )

            val errorMessageString =
                if(numberErrorsSent > 1) {
                    "$numberErrorsSent error messages of this kind were simultaneously sent.\n"
                } else {
                    ""
                } + errorMessageToSend.string

            val request = SendErrorRequest.newBuilder()
                .setMessage(
                    ErrorMessageOuterClass.ErrorMessage.newBuilder()
                        .setErrorOrigin(ErrorOriginEnum.ErrorOriginType.ERROR_ORIGIN_ANDROID)
                        .setErrorUrgencyLevel(errorUrgencyLevel)
                        .setVersionNumber(versionNumber)
                        .setFileName(fileNameToSend.string)
                        .setLineNumber(lineNumber)
                        .setStackTrace(stackTraceToSend.string)
                        .setApiNumber(Build.VERSION.SDK_INT)
                        .setDeviceName(GlobalValues.deviceName)
                        .setErrorMessage(errorMessageString)
                        .build()
                )
                .build()

            val response = LogErrorClient().logError(request)

            Log.i("network_error_stuff", "ErrorHandlerWorker response: $response")

            val successful =
                if (response.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {
                    when (response.response.returnStatus) {
                        SendErrorResponse.Status.OUTDATED_VERSION, //this will not change unless the app is updated, no reason to retry
                        SendErrorResponse.Status.SUCCESSFUL -> {
                            true //success! no need to retry
                        }
                        SendErrorResponse.Status.UNRECOGNIZED,
                        SendErrorResponse.Status.FAIL,
                        SendErrorResponse.Status.NO_VALUE_SET, //this would mean some weird type of server error where gRPC returned the default value
                        SendErrorResponse.Status.DATABASE_DOWN,
                        null -> {
                            false //success! no need to retry
                        }
                    }
                } else {
                    false
                }

            return if (successful) {
                Result.success()
            } else {
                Result.retry()
            }

        }

        return Result.failure()
    }

    companion object {

        const val ERROR_HANDLER_WORKER_TAG = "ERROR_HANDLER_WORKER_NAME"

        const val ERROR_URGENCY_LEVEL = "urgency_level" //Int

        const val VERSION_NUMBER = "v_num" //Int

        const val FILE_NAME_OR_FILE_PATH_KEY = "f_name" //String
        const val FILE_NAME_TOO_LARGE_TO_BE_PASSED = "f_name_S" //Boolean

        const val LINE_NUMBER_KEY = "line_num" //Int

        const val STACK_TRACE_OR_FILE_PATH_KEY = "stack_t" //String
        const val STACK_TRACE_TOO_LARGE_TO_BE_PASSED = "stack_t_S" //Boolean

        const val ERROR_MESSAGE_OR_FILE_PATH_KEY = "err_msg" //String
        const val ERROR_MESSAGE_TOO_LARGE_TO_BE_PASSED = "err_msg_S" //Boolean

        //workers can only take a maximum of 10240 bytes and so if the error message is larger
        // than this number if will be stored inside of a file instead
        //10240 - 512 = 9728; there isn't much reason for the number 512, however want to give a little
        // 'extra' space for the worker parameters
        const val MAXIMUM_SIZE_ERROR_MESSAGE_BEFORE_STORING = 9728

        //Time in millis, related to numberErrorsMap, it is the initial delay on the worker.
        // This will be the amount of time before the worker runs and clears the previous number
        // of workers.
        const val AMOUNT_OF_TIME_BEFORE_NUMBER_ERRORS_CLEARED = 30L * 1000L

        //Related to numberErrorsMap, it is the number of errors before new ones are no
        // longer stored within AMOUNT_OF_TIME_BEFORE_NUMBER_ERRORS_CLEARED.
        const val NUMBER_OF_ERRORS_BEFORE_ERROR_NOT_STORED = 5

        class MutexProtectedMap {
            private val map = mutableMapOf<String, Int>()
            private val mutex = Mutex()

            private fun createKeyForSentErrorsMap(fileName: String, lineNumber: Int): String {
                return "${fileName}_$lineNumber"
            }

            suspend fun getAndClearNumberOfErrors(fileName: String, lineNumber: Int): Int {
                return mutex.withLock {
                    val key = createKeyForSentErrorsMap(fileName, lineNumber)
                    return@withLock map.remove(key) ?: 0
                }
            }

            suspend fun setAndGetNumberOfErrors(fileName: String, lineNumber: Int): Int {
                return mutex.withLock {
                    val key = createKeyForSentErrorsMap(fileName, lineNumber)
                    val newValue = (map[key] ?: 0) + 1
                    map[key] = newValue
                    return@withLock newValue
                }
            }
        }

        //This is essentially to prevent a single error message from spamming, if it reaches
        // over a certain amount of the same message (NUMBER_OF_ERRORS_BEFORE_ERROR_NOT_STORED)
        // then they will no longer be stored until the first worker of that kind ran.
        val numberErrorsMap = MutexProtectedMap()
    }

}

