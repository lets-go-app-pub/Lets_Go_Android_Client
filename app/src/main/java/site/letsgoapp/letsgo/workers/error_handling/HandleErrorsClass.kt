package site.letsgoapp.letsgo.workers.error_handling

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import error_origin_enum.ErrorOriginEnum
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

//storeError function is defined in utilities
class HandleErrorsClass {

    //UTF-8 encoding                                 Byte to decimal
    //single-byte sequences    0xxxxxxx                 0 .. 127
    //double-byte sequences    110xxxxx 10xxxxxx      -64..-33
    //triple-byte sequences    1110xxxx 10xxxxxx * 2  -32..-17
    //quadruple-byte sequences 11110xxx 10xxxxxx * 3  -16..-9
    //this function will extract code points until the byte limit is reached, then convert that byte array
    // back into a string and return it
    private fun extractStringOfProperSizeFromBytes(byteArray: ByteArray): ByteArray {

        var numBytesToAdd = 0
        try {
            while (numBytesToAdd < byteArray.size) {

                val numBytesInCodePoint =
                    extractNumberBytesInCodePoint(byteArray, numBytesToAdd)

                if ((numBytesToAdd + numBytesInCodePoint) > GlobalValues.server_imported_values.maximumNumberAllowedBytesErrorMessage) { //if past byte limit
                    //don't add them, all done
                    break
                }

                numBytesToAdd += numBytesInCodePoint
            }
        } catch (e: UTF8IncorrectEncoding) {
            return "Error in function extractStringOfProperSizeFromBytes() when extracting too large of byteArray.\nmessage: ${e.message}".toByteArray()
        }

        if (numBytesToAdd == 0) {
            return "Error in function extractStringOfProperSizeFromBytes() byte array came back as size 0.\nbyteArray.size: ${byteArray.size}".toByteArray()
        }

        return byteArray.sliceArray(0 until numBytesToAdd)
    }

    private data class TrimmedStringValues(
        val string: String,
        val tooLargeToBePassed: Boolean,
        val sizeInBytes: Int
    )

    private fun trimString(
        applicationContext: Context,
        string: String,
        maxNumberBytes: Int,
        bytesRemaining: Int
    ): TrimmedStringValues {
        val stringInBytes = string.toByteArray()

        return if (stringInBytes.size > bytesRemaining) {

            //if byte array is too large for server to accept, trim it before saving to file
            val insertedByteArray =
                if (stringInBytes.size > maxNumberBytes) {
                    val startOfTooLongByteArray = "ERROR MESSAGE WAS TOO LONG\n".toByteArray()

                    extractStringOfProperSizeFromBytes(startOfTooLongByteArray + stringInBytes)
                } else {
                    stringInBytes
                }

            val errorMessageFile = generateErrorWorkerFile(applicationContext)

            try {
                errorMessageFile.writeBytes(insertedByteArray)

                TrimmedStringValues(
                    errorMessageFile.absolutePath,
                    true,
                    errorMessageFile.absolutePath.toByteArray().size
                )

            } catch (ex: IOException) {
                val errorString =
                    "An IOException occurred while attempting to write an error message to file.\n" +
                            "IOException: ${ex.message}\n" +
                            "lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                            "fileName: ${Thread.currentThread().stackTrace[2].fileName}\n"

                TrimmedStringValues(errorString, false, errorString.toByteArray().size)
            }
        } else {
            TrimmedStringValues(string, false, stringInBytes.size)
        }
    }

    suspend fun createAndSendErrorMessage(
        applicationContext: Context,
        fileName: String,
        lineNumber: Int,
        stackTrace: String,
        errorMessage: String,
        errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel,
    ) {

        val recentNumberErrorsOfThisType =
            ErrorHandlerWorker.numberErrorsMap.setAndGetNumberOfErrors(
                fileName,
                lineNumber
            )

        Log.i("handle_errors_", "file_name: $fileName\nline_num: $lineNumber\nmessage: $errorMessage")

        //In order to prevent spamming of errors, if this error was sent more than a certain number of times,
        // don't send it to the server.
        if (recentNumberErrorsOfThisType <= ErrorHandlerWorker.NUMBER_OF_ERRORS_BEFORE_ERROR_NOT_STORED) {

            var bytesRemaining = ErrorHandlerWorker.MAXIMUM_SIZE_ERROR_MESSAGE_BEFORE_STORING

            val trimmedFileName = trimString(
                applicationContext,
                fileName,
                GlobalValues.server_imported_values.maximumNumberAllowedBytes,
                bytesRemaining
            )

            bytesRemaining -= trimmedFileName.sizeInBytes

            val trimmedStackTrace = trimString(
                applicationContext,
                stackTrace,
                GlobalValues.server_imported_values.maximumNumberAllowedBytesErrorMessage,
                bytesRemaining
            )

            bytesRemaining -= trimmedStackTrace.sizeInBytes

            val trimmedErrorMessage = trimString(
                applicationContext,
                errorMessage,
                GlobalValues.server_imported_values.maximumNumberAllowedBytesErrorMessage,
                bytesRemaining
            )

            //stores the data in key value pairs to pass to the 'Worker' as parameters
            val workerParams: Data = workDataOf(
                ErrorHandlerWorker.ERROR_URGENCY_LEVEL to errorUrgencyLevel.number, //sending this here just in case app is updated
                ErrorHandlerWorker.VERSION_NUMBER to GlobalValues.Lets_GO_Version_Number, //sending this here just in case app is updated
                ErrorHandlerWorker.FILE_NAME_OR_FILE_PATH_KEY to trimmedFileName.string,
                ErrorHandlerWorker.FILE_NAME_TOO_LARGE_TO_BE_PASSED to trimmedFileName.tooLargeToBePassed,
                ErrorHandlerWorker.LINE_NUMBER_KEY to lineNumber,
                ErrorHandlerWorker.STACK_TRACE_OR_FILE_PATH_KEY to trimmedStackTrace.string,
                ErrorHandlerWorker.STACK_TRACE_TOO_LARGE_TO_BE_PASSED to trimmedStackTrace.tooLargeToBePassed,
                ErrorHandlerWorker.ERROR_MESSAGE_OR_FILE_PATH_KEY to trimmedErrorMessage.string,
                ErrorHandlerWorker.ERROR_MESSAGE_TOO_LARGE_TO_BE_PASSED to trimmedErrorMessage.tooLargeToBePassed,
            )

            Log.i(
                "server_not_fake",
                printStackTraceForErrors()
            )

            //builds the work request using the class extending 'Worker'
            val errorWorkRequest = OneTimeWorkRequestBuilder<ErrorHandlerWorker>()
                .setInputData(workerParams)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(ErrorHandlerWorker.ERROR_HANDLER_WORKER_TAG)
                .setInitialDelay(
                    ErrorHandlerWorker.AMOUNT_OF_TIME_BEFORE_NUMBER_ERRORS_CLEARED,
                    TimeUnit.MILLISECONDS
                )
                .build()

            Log.i("network_error_stuff", "enqueue work request")
            //sends the request to the work manager
            //want the await here for UncaughtExceptionHandler (it won't matter for StoreErrorsInterface)
            WorkManager.getInstance(applicationContext).enqueue(errorWorkRequest).await()
        }
    }
}

fun getDeviceAndVersionInformation(): String {
    var errorString = ""

    val calendar = if (GlobalValues.serverTimestampStartTimeMilliseconds == -1L) {
        errorString += "NOTE: had to use system time to set calendar.\n"
        Calendar.getInstance()
    } else {
        getCalendarFromServerTimestamp()
    }

    errorString += calendar.time.toString() + "\n"
    errorString += "Device Name: ${GlobalValues.deviceName}\n"
    errorString += "Android API: ${Build.VERSION.SDK_INT}\n"
    errorString += "Request Let's Go Version: ${GlobalValues.Lets_GO_Version_Number}\n"

    return errorString
}

