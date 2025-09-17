package site.letsgoapp.letsgo.workers.chatStreamObjectWorkers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

class RunChatStreamObjectAfterDelayWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
            Log.i("runLoginBeforeToke", "RunChatStreamObjectAfterDelayWorker starting")

            (appContext as LetsGoApplicationClass).chatStreamObject.workerFunctionRetryAfterDelay()
        }

        return Result.success()
    }
}