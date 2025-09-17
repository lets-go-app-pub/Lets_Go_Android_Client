package site.letsgoapp.letsgo.workers.error_handling

import android.content.Context
import android.os.Looper
import android.util.Log
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.runBlocking
import site.letsgoapp.letsgo.LetsGoRuntimeException
import kotlin.system.exitProcess

//NOTE: android seems to have something in the background for CoroutineExceptionHandler, coRoutines
// will send their stack trace here and it can be extracted, no information seems to be lost even
// if the exception comes from a coRoutine
//NOTE: runBlocking does NOT seem to properly throw exceptions for some reason
class UncaughtExceptionHandler(
    private val oldExceptionHandler: Thread.UncaughtExceptionHandler?,
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {

        //If this is a LetsGoRuntimeException, the error has already been stored.
        if (e !is LetsGoRuntimeException) {
            Log.i("uncaughtExceptions", "inside if statement")
            val mainThreadId = Looper.getMainLooper().thread.id

            val errorMessage = "An uncaught exception was returned." +
                    "threadId: ${t.id} ($mainThreadId is Main thread id)\n" +
                    "throwable: ${e.message}\n"

            Log.i("uncaughtExceptions", errorMessage)
            Log.i("uncaughtExceptions", Log.getStackTraceString(e))

            //Need to wait for the Worker to fully enqueued the Error Message before continuing
            runBlocking {
                val errorsClass = HandleErrorsClass()
                errorsClass.createAndSendErrorMessage(
                    applicationContext,
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Log.getStackTraceString(e),
                    errorMessage,
                    ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_VERY_HIGH
                )
            }
        }

        throwUncaughtException(t, e)
    }

    private fun throwUncaughtException(t: Thread, e: Throwable) {
        if (oldExceptionHandler == null) {
            exitProcess(2)
        } else {
            oldExceptionHandler.uncaughtException(t, e)
        }
    }
}