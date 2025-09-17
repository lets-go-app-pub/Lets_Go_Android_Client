package site.letsgoapp.letsgo.workers.loginFunctionWorkers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions

class RunLoginFunctionAfterDelayWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val callingFragmentInstanceID =
        params.inputData.getString(LoginFunctions.WORKER_PARAM_CALLING_FRAGMENT_ID_KEY) ?: ""
    private val manualLoginInfo =
        params.inputData.getBoolean(LoginFunctions.WORKER_PARAM_MANUAL_LOGIN_INFO_KEY, true)

    override suspend fun doWork(): Result {
        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
            Log.i("runLoginBeforeToke", "RunLoginFunctionAfterDelayWorker starting")

            //See LoginFunctions.beginLoginToServerWhenReceivedInvalidToken() for more info.
            if (LoginFunctions.waitingForWorker.compareAndSet(true, false)) {
                val applicationClass = appContext as LetsGoApplicationClass
                applicationClass.loginFunctions.runAfterDelay(
                    applicationClass,
                    manualLoginInfo,
                    callingFragmentInstanceID
                )
            }
        }

        return Result.success()
    }
}