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

class RefreshLoginFunctionTokenWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val callingFragmentInstanceID =
        params.inputData.getString(LoginFunctions.WORKER_PARAM_CALLING_FRAGMENT_ID_KEY) ?: ""

    override suspend fun doWork(): Result {
        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
            Log.i("runLoginBeforeToke", "RefreshLoginFunctionTokenWorker starting")

            //See LoginFunctions.beginLoginToServerWhenReceivedInvalidToken() for more info.
            if (LoginFunctions.waitingForWorker.compareAndSet(true, false)) {
                (appContext as LetsGoApplicationClass).loginFunctions.lockLoginToServerFunction(
                    callingFragmentInstanceID
                )
            }
        }

        return Result.success()
    }
}