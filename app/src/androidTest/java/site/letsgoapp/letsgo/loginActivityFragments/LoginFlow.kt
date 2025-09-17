package site.letsgoapp.letsgo.loginActivityFragments

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.grantAllAppPermissionRules
import site.letsgoapp.letsgo.testingUtility.runLoginTest
import site.letsgoapp.letsgo.testingUtility.setupForActivityTests
import site.letsgoapp.letsgo.testingUtility.tearDownForTesting
import java.io.IOException

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginFlow {

    private lateinit var applicationContext: Context
    private lateinit var fakeStoreErrors: FakeStoreErrors

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    var permissionRules: GrantPermissionRule = grantAllAppPermissionRules()

    @Before
    fun setUp() {
        fakeStoreErrors = FakeStoreErrors(testDispatcher)

        applicationContext = setupForActivityTests(
            fakeStoreErrors,
            testDispatcher
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        tearDownForTesting(
            applicationContext
        )
    }

    @Test
    fun login_flow_happy_path() = runTest(testDispatcher) {
        runLoginTest(
            applicationContext,
        )

        Assert.assertEquals("", fakeStoreErrors.getAndResetErrorMessage())
    }
}