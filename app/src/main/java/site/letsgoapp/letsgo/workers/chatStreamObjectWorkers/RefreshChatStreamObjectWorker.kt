package site.letsgoapp.letsgo.workers.chatStreamObjectWorkers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject

class RefreshChatStreamObjectWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val sentFrom =
        params.inputData.getString(ChatStreamObject.WORKER_PARAM_SENT_FROM_KEY) ?: ""

    override suspend fun doWork(): Result {
        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
            Log.i("runLoginBeforeToke", "RefreshChatStreamObjectWorker starting")

            (appContext as LetsGoApplicationClass).chatStreamObject.workerFunctionSetUpRefresh(
                sentFrom
            )
        }

        return Result.success()
    }
}