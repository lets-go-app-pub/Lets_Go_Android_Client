package site.letsgoapp.letsgo.workers.deleteFileWorker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors
import java.io.File
import java.io.IOException

class DeleteFileWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val errorStore: StoreErrorsInterface = ServiceLocator.globalErrorStore

    override suspend fun doWork(): Result {

        Log.i("CleanDatabaseWorker_", "starting DeleteFileWorker")

        try {
            if (runAttemptCount > 3) {
                val errorMessage =
                    "Attempted to delete file more than 3 times using " +
                            "DeleteFileWorker then gave up (possible memory leak).\n" +
                            "pathName: ${inputData.getString(DELETE_FILE_PATHNAME_KEY)}"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    appContext
                )

                return Result.failure()
            }

            val pathName: String? = inputData.getString(DELETE_FILE_PATHNAME_KEY)

            Log.i("CleanDatabaseWorker_", "extracted pathName: $pathName")

            if (pathName != null) {
                val file = File(pathName)

                if (file.exists()) {
                    try {
                        Log.i("CleanDatabaseWorker_", "attempting to delete $pathName")
                        file.delete()
                        Log.i("CleanDatabaseWorker_", "deleting $pathName")
                    } catch (e: IOException) {

                        val errorMessage =
                            "IOException occurred inside DeleteFileWorker (possible memory leak).\n" +
                                    "pathName: $pathName" +
                                    "exception message: ${e.message}"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage,
                            appContext
                        )

                        return Result.retry()
                    }
                }
                return Result.success()
            }

            return Result.failure()
        } catch (e: Exception) {

            //if coRoutine was cancelled, propagate it
            if (e is CancellationException)
                throw e

            //The UncaughtExceptionHandler doesn't seem to catch worker exceptions.

            val errorMessage =
                "An exception was thrown when DeleteFileWorker was run.\n${e.message}"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                e.stackTraceToString(),
                errorMessage,
                appContext
            )

            return Result.failure()
        }
    }

    companion object {
        const val DELETE_FILE_WORKER_TAG = "DELETE_TAG"
        const val DELETE_FILE_PATHNAME_KEY = "path"
    }
}