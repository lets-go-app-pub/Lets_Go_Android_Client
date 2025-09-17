package site.letsgoapp.letsgo.obsolete.utility

import android.content.Context
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.utilities.NetworkingInterface
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface

class FakeStoreErrors(
    private val ioDispatcher: CoroutineDispatcher
) : StoreErrorsInterface {

    private var errMessage = ""

    //returns true if an error occurred and false if one did not
    //returns the error message in the String variable
    //also resets the error message to ""
    fun getAndResetErrorMessage(): Pair<Boolean, String> {
        val errorOccurred = errMessage != ""
        val returnMessage = errMessage
        errMessage = ""
        return Pair(errorOccurred, returnMessage)
    }

    //called from the app to store errors
    override fun storeError(
        filename: String,
        lineNumber: Int,
        stackTrace: String,
        errorMessage: String,
        applicationContext: Context,
        errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel
    ) {
        CoroutineScope(ioDispatcher).launch {
            errMessage = errorMessage
        }
    }
}

class FakeNetworking : NetworkingInterface {

    private var validNetwork = true

    override fun checkNetworkState(): Boolean {
        return validNetwork
    }
}