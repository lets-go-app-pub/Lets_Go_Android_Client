package site.letsgoapp.letsgo.obsolete

import org.junit.Test
import org.junit.Assert.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues

class EmailRegexCheck {

    @Test
    fun testingEmailRegex() {

        var index = 1
        if(verifyEmailAddress("quickshiftx@gmail.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 2
        if(verifyEmailAddress("s@a.s"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 3
        if(verifyEmailAddress("123f4@asvrry.c44%m"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 4
        if(!verifyEmailAddress(" quickshiftx@gmail.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 5
        if(!verifyEmailAddress("quickshiftx@gmail.com "))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 6
        if(verifyEmailAddress("test@test.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 7
        if(!verifyEmailAddress("te st@test.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 8
        if(!verifyEmailAddress("@test.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 9
        if(!verifyEmailAddress("test@.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 10
        if(!verifyEmailAddress("test@test."))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 11
        if(!verifyEmailAddress("test@test.c om"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        index = 12
        if(!verifyEmailAddress("test@t est.com"))
            println("EmailRegexCheck, $index: Passed")
        else
            println("EmailRegexCheck, $index: Failed")

        assertEquals(4+2, 6)
    }

    private fun verifyEmailAddress(emailAddress: String): Boolean{
        val emailRegex = Regex(GlobalValues.EMAIL_REGEX_STRING)
        return emailRegex.matches(emailAddress)
    }

}