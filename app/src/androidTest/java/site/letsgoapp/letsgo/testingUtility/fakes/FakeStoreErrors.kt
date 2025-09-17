package site.letsgoapp.letsgo.testingUtility.fakes

import android.content.Context
import android.util.Log
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface

class FakeStoreErrors(private val ioDispatcher: CoroutineDispatcher) :
    StoreErrorsInterface {

    private var errMessage = ""

    //returns true if an error occurred and false if one did not
    //returns the error message in the String variable
    //also resets the error message to ""
    fun getAndResetErrorMessage(): String {
        val returnMessage = errMessage
        errMessage = ""
        return returnMessage
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
            Log.i("fake_errors_msg", "message: $errorMessage\nfilename: $filename\nlineNumber: $lineNumber\n$stackTrace")
            errMessage = errorMessage
        }
    }
}