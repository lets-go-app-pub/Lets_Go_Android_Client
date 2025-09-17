package site.letsgoapp.letsgo.obsolete

import kotlinx.coroutines.delay
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class ExampleUnitTest {
    @Test
    fun test_function() {
    }

    //@Test
    suspend fun myFirstCoroutine(booga: String) {
    }

    //@Test
    suspend fun nestedCoroutine() {

        for (i in 1..15) {
            delay(100)
            println("TAG: nestedCoroutine; Looping")
        }

    }


}