package site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings.Global
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.parcelable

class SMSBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {

        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
            val extras = intent.extras
            val status =
                try {
                    //There is a known issue with getParcelable() in API 33 at the links below.
                    // When it is fixed can remove the try catch block for the NPE.
                    //https://issuetracker.google.com/issues/240585930
                    //https://issuetracker.google.com/issues/254502846
                    //NOTE: While the deprecated function inside parcelable() could be used in place
                    // of this method, catching the exception instead will allow the code to
                    // automatically work when the fix is released. The worst that will happen is
                    // that API 33 devices will not automatically enter SMS codes.
                    extras?.parcelable<Status>(SmsRetriever.EXTRA_STATUS)
                } catch (e: NullPointerException) {
                    Log.i("broadcast_receiver", "exception: ${e.message}")
                    return
                }

            when (status?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    // Get SMS message contents
                    var message = extras?.getString(SmsRetriever.EXTRA_SMS_MESSAGE)

                    //extract 6 digit code
                    if (message != null && message.length >= 6) {

                        message = message.substring(0,6)

                        SMSBroadcastIntermediate.broadcastReceiverError = false
                        SMSBroadcastIntermediate.sendMessage(message)

                    } else if (message != null) { //send error

                        val errorString =
                            "Message received by broadcast receiver does not match proper messaging format./n Message: $message"

                        SMSBroadcastIntermediate.broadcastReceiverError = true
                        SMSBroadcastIntermediate.lineNumber =
                            Thread.currentThread().stackTrace[2].lineNumber
                        SMSBroadcastIntermediate.fileName =
                            Thread.currentThread().stackTrace[2].fileName

                        SMSBroadcastIntermediate.sendMessage(errorString)
                    }
                }
                CommonStatusCodes.TIMEOUT -> {
                    // Waiting for SMS timed out (5 minutes)
                }
            }
        }
    }

}