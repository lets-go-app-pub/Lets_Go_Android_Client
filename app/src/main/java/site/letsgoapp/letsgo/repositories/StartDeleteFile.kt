package site.letsgoapp.letsgo.repositories

import android.content.Context
import android.util.Log
import androidx.work.*
import site.letsgoapp.letsgo.utilities.StoreErrors
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors
import site.letsgoapp.letsgo.workers.deleteFileWorker.DeleteFileWorker
import java.security.PrivateKey

interface StartDeleteFileInterface {
    fun sendFileToWorkManager(pathName: String)

    fun deleteAppPrivateFile(fileName: String)
}

class StartDeleteFile(
    val applicationContext: Context,
    //NOTE: This is passed in for consistency. The implementation of this class is never meant to be used in testing.
    private val storeErrors: StoreErrorsInterface = StoreErrors()
    ) : StartDeleteFileInterface {

    //delete a file anywhere this app is allowed to do so
    //requires entire path, not just the file name
    override fun sendFileToWorkManager(pathName: String) {

        Log.i("CleanDatabaseWorker_", "running sendFileToWorkManager() for $pathName")

        if (pathName.isBlank()) {
            val errorMessage = "When attempting to delete a file, the pathname was blank.\n" +
                    "pathName: $pathName\n"

            storeErrors.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            return
        }

        //stores the data in key value pairs to pass to the 'Worker' as parameters
        val workerParams: Data = workDataOf(DeleteFileWorker.DELETE_FILE_PATHNAME_KEY to pathName)

        //builds the work request using the class extending 'Worker'
        val deleteFileWorker = OneTimeWorkRequestBuilder<DeleteFileWorker>()
            .setInputData(workerParams)
            .addTag(DeleteFileWorker.DELETE_FILE_WORKER_TAG)
            .build()

        Log.i("CleanDatabaseWorker_", "enqueue $pathName")

        //sends the request to the work manager
        WorkManager.getInstance(applicationContext).enqueue(deleteFileWorker)
    }

    //delete a file inside the apps app/files dir
    //NOTE: take ONLY the file name, no path
    override fun deleteAppPrivateFile(fileName: String) {
        applicationContext.deleteFile(fileName)
    }
}