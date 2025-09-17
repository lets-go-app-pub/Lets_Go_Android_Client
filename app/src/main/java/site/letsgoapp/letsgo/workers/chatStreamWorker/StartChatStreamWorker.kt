package site.letsgoapp.letsgo.workers.chatStreamWorker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors
import site.letsgoapp.letsgo.utilities.startChatStreamWorker
import kotlin.concurrent.withLock

class StartChatStreamWorker(private val appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val errorHandler: StoreErrorsInterface = ServiceLocator.globalErrorStore

    private fun doStartChatStreamWorkerWork(): Result {
        val applicationIsRunning = GlobalValues.anActivityWasCreated

        if (applicationIsRunning) {
            Log.i("StreamWorker", "StartChatStreamWorker: applicationIsRunning == true")
            return Result.success()
        }

        //check if a worker is in queue for this
        //NOTE: This is called from a CoRoutine and so it will block a thread, however await() cannot be used
        // instead of get() inside of chatStreamWorkerMutex because it is a ReentrantLock lock NOT a CoRoutine Reentrant lock.
        /** See [site.letsgoapp.letsgo.workers.chatStreamWorker.ChatStreamWorker.chatStreamWorkerMutex] for details of why **/
        val workers =
            WorkManager.getInstance(applicationContext)
                .getWorkInfosByTag(ChatStreamWorker.CHAT_STREAM_WORKER_TAG)
                .get()

        //State notes
        //WorkInfo.State.CANCELLED; this state will not be returned if the work has been cancelled and is
        // waiting to complete (coRoutine was cancelled but has not hit a suspend point),
        // WorkInfo.State.RUNNING will be returned instead
        var workerExists = false
        for (worker in workers) {

            //remember that ChatStreamWorker.instanceRunning was checked above
            if (worker.state == WorkInfo.State.BLOCKED
                || worker.state == WorkInfo.State.ENQUEUED
                || worker.state == WorkInfo.State.RUNNING
            ) {
                workerExists = true
                break
            }
        }

        //Cases
        //workerExists == false;  chatStreamWorkerInstanceRunning == false; start,
        //workerExists == false;  chatStreamWorkerInstanceRunning == true; start, set bool to false
        //workerExists == true;  chatStreamWorkerInstanceRunning == false; ignore this; (ChatStreamWorker could have just started or be in queue)
        //workerExists == true;  chatStreamWorkerInstanceRunning == true; ignore this

        if (!workerExists) {

            //NOTE: This may be possible if 'Force Stop' is used by the user inside of the Settings.

            val errorMessage =
                "When StartChatStreamWorker was running, the worker did not exist.\n" +
                        "workers: $workers\n"

            errorHandler.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                appContext,
                ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_VERY_LOW
            )

            Log.i("StreamWorker", "StartChatStreamWorker: workerExists == false")
            ChatStreamWorker.chatStreamWorkerInstanceRunning.set(false)
            //start chat stream worker
            startChatStreamWorker(appContext)
        }

        Log.i("StreamWorker", "finishing StartChatStreamWorker")
        return Result.success()
    }

    /** His worker is intended to start the chat stream in case the app was terminated and so the stream
     * was not started.
     * The lifetime of this worker is that it will be started as UniquePeriodicWork on initial user login. It will
     * then be stopped on log out or delete. **/
    override suspend fun doWork(): Result = withContext(ServiceLocator.globalIODispatcher) {

        Log.i("StreamWorker", "starting StartChatStreamWorker")
        Log.i(
            "chatStreamSubscription",
            "starting StartChatStreamWorker; about to lock chatStreamWorkerMutex"
        )
        return@withContext ChatStreamWorker.chatStreamWorkerMutex.withLock {
            return@withLock try {
                doStartChatStreamWorkerWork()
            } catch (e: Exception) {

                //if coRoutine was cancelled, propagate it
                if (e is CancellationException)
                    throw e

                //The UncaughtExceptionHandler doesn't seem to catch worker exceptions.

                val errorMessage =
                    "An exception was thrown when CleanDatabaseWorker was run.\n${e.message}"

                errorHandler.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    e.stackTraceToString(),
                    errorMessage,
                    appContext
                )

                Result.failure()
            }
        }
    }

    companion object {
        const val START_CHAT_STREAM_UNIQUE_WORK_NAME = "start_chat_stream_worker_name"

        //In minutes; must be >= 15 minutes (15 minutes is the smallest time PeriodicWorkRequestBuilder will accept).
        const val TIME_BETWEEN_START_CHAT_STREAM_WORKER_RUNNING: Long = 30
    }
}