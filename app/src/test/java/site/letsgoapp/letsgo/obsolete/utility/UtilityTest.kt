package site.letsgoapp.letsgo.obsolete.utility

import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import site.letsgoapp.letsgo.utilities.calcPersonAge

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@Config(maxSdk = Build.VERSION_CODES.P)
class UtilityTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    var testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun testCalcPersonAge() = runTest(testDispatcher) {
        val fakeErrors = FakeStoreErrors(testDispatcher)
        val context: Context = getApplicationContext()
        var age: Int = -1

        age = calcPersonAge(context, 1986, 10, 23, fakeErrors)

        assertThat(age, `is`(33))

        age = calcPersonAge(context, 2000, 1, 1, fakeErrors)

        assertThat(age, `is`(20))

        age = calcPersonAge(context, 2200, 11, 12, fakeErrors)

        var error = fakeErrors.getAndResetErrorMessage()
        assertThat(age, `is`(0))
        assertThat(error.first, `is`(false))

        age = calcPersonAge(context, 2200, -5, 12, fakeErrors)

        assertThat(age, `is`(0))
        error = fakeErrors.getAndResetErrorMessage()
        assertThat(error.first, `is`(true))

    }
}