package site.letsgoapp.letsgo.utilities.editTextFilters

import android.text.InputFilter
import android.text.Spanned

class LettersSpaceNumbersFilter(
    allowLowerCase: Boolean = false,
    allowUpperCase: Boolean = false,
    allowNumbers: Boolean = false,
    allowSpace: Boolean = false,
) : InputFilter {

    private val conditionsList = mutableListOf<(Int) -> Boolean>()

    init {
        if (allowLowerCase) {
            conditionsList.add { charCode ->
                charCode in 'a'.code..'z'.code
            }
        }

        if (allowUpperCase) {
            conditionsList.add { charCode ->
                charCode in 'A'.code..'Z'.code
            }
        }

        if (allowNumbers) {
            conditionsList.add { charCode ->
                charCode in '0'.code..'9'.code
            }
        }

        if (allowSpace) {
            conditionsList.add { charCode ->
                charCode == ' '.code
            }
        }
    }

    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int,
    ): CharSequence? {

        //NOTE: the function Char.isLetter() is useful for this however it checks for anything
        // marked as a letter in unicode and the server does not do that, so these must be restricted
        // to the latin alphabet

        source?.let {
            //make it a string to guarantee that the small Char codes will match ascii
            val sourceString = source.toString()

            var keepOriginal = true
            val properString = StringBuilder(end - start)
            for (ele in sourceString) {
                var atLeastOneCaseTrue = false
                for (case in conditionsList) {
                    if (case(ele.code)) {
                        atLeastOneCaseTrue = true
                        break
                    }
                }

                if (atLeastOneCaseTrue) {
                    properString.append(ele)
                } else {
                    keepOriginal = false
                }
            }

            return if (keepOriginal)
                null
            else
                properString
        }

        return ""
    }

}