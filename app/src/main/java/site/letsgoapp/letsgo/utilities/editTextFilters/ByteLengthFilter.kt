package site.letsgoapp.letsgo.utilities.editTextFilters

import android.text.InputFilter
import android.text.Spanned
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.UTF8IncorrectEncoding
import site.letsgoapp.letsgo.utilities.extractNumberBytesInCodePoint

//this filter will not allow the EditText to get over the passed number of bytes in utf8 encoding
class ByteLengthFilter(
    private val maxNumBytes: Int
) : InputFilter {

    companion object {

        //This function will extract code points until the byte limit is reached, then convert
        // that byte array back into a string and return it.
        //Only works on utf8 strings.
        fun extractStringOfProperSizeFromBytes(
            numberBytesUsedInDest: Int,
            nextBytes: ByteArray,
            maxNumBytes: Int
        ): String {
            var numBytesToAdd = 0
            try {
                while (numBytesToAdd < nextBytes.size) {
                    val numBytesInCodePoint =
                        extractNumberBytesInCodePoint(nextBytes, numBytesToAdd)

                    if ((numBytesToAdd + numBytesInCodePoint + numberBytesUsedInDest) > maxNumBytes) { //if past byte limit
                        //don't add them, all done
                        break
                    }

                    numBytesToAdd += numBytesInCodePoint
                }
            } catch (e: UTF8IncorrectEncoding) {

                val errorMessage = "Received an incorrectly formatted UTF8 encoding error.\n" +
                        "exception msg: ${e.message}\n"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    e.stackTraceToString(),
                    errorMessage
                )

                return ""
            }

            if (numBytesToAdd == 0) {
                return ""
            }

            return String(nextBytes.sliceArray(0 until numBytesToAdd))
        }
    }

    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        source?.let {
            dest?.let {

                //NOTE: this was loosely copied from InputFilter.LengthFilter() (it must do bytes instead of chars so it isn't exact)
                // because there are several cases to take into account (can look at InputFilter documentation)
                val destString = dest.toString()

                val destTotalNumBytes = destString.toByteArray(Charsets.UTF_8).size
                val numRemovedFromDest =
                    destString.substring(dstart until dend).toByteArray(Charsets.UTF_8).size

                val numberBytesRemainingInDest = destTotalNumBytes - numRemovedFromDest

                val numBytesAvailableToUse = maxNumBytes - numberBytesRemainingInDest

                if (numBytesAvailableToUse <= 0) { //keep no bytes
                    return ""
                }

                val bytesAddedToDest =
                    source.toString().substring(start until end).toByteArray(Charsets.UTF_8)

                return if (numBytesAvailableToUse >= bytesAddedToDest.size) { //keep all bytes
                    //returning null means continue with the normal operation (NOTE: it is NOT the same as returning source here)
                    null
                } else {
                    //TESTING_NOTE: cases to be aware of (also do the 2 above in the when statement)
                    // 12|3456|  |123456|  |1234|56  12|34|56 123456|| ||123456
                    // will need to try pasting different types to the given spots
                    extractStringOfProperSizeFromBytes(
                        numberBytesRemainingInDest,
                        bytesAddedToDest,
                        maxNumBytes
                    )
                }
            }
        }

        return ""
    }
}