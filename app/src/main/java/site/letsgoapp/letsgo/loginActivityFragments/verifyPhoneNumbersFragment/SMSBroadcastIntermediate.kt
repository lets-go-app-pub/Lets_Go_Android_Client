package site.letsgoapp.letsgo.loginActivityFragments.verifyPhoneNumbersFragment

object SMSBroadcastIntermediate {

    private var verifyPhoneNumbersFragment: VerifyPhoneNumbersFragment? = null

    //these values are set when the broadcast receiver has an error
    var broadcastReceiverError = false //set to true when the broadcast receiver has an error
    var lineNumber: Int = -1
    var fileName: String = "~"

    fun setFragment(verifyFragment: VerifyPhoneNumbersFragment) {
        verifyPhoneNumbersFragment = verifyFragment
    }

    fun clearFragment() {
        verifyPhoneNumbersFragment = null
    }

    fun sendMessage(message: String) {
        verifyPhoneNumbersFragment?.manageSMSSignal(message)
    }

}