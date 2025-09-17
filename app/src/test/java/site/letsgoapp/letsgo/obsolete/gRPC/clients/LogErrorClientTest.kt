package site.letsgoapp.letsgo.obsolete.gRPC.clients
//
//import android.os.Build
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.hamcrest.CoreMatchers.`is`
//import org.hamcrest.MatcherAssert.assertThat
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.annotation.Config
//import site.letsgoapp.letsgo.globalAccess.GlobalValues
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.SendErrorRequestDataClass
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class LogErrorClientTest {
//
//    //NOTE: A return of false means the Worker that calls logError will retry the task.
//    private lateinit var logErrorClient: LogErrorClient
//    private lateinit var requestDataClass: SendErrorRequestDataClass
//
//    @Before
//    fun setupClientAndRequest() {
//
//        logErrorClient = LogErrorClient()
//        requestDataClass =
//            SendErrorRequestDataClass(
//                "Error Message", "Collection Name", GlobalValues.Lets_GO_Version_Number
//            )
//
//    }
//
//    @Test
//    fun logError_completedSUCCESSFUL_returnsTrue() {
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.NO_EXCEPTION, ErrorClientEnum.SUCCESSFUL
//        )
//
//        assertThat(returnBool, `is`(true))
//    }
//
//    @Test
//    fun logError_completedCONNECTION_ERROR_returnsFalse() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.NO_EXCEPTION, ErrorClientEnum.CONNECTION_ERROR)
//
//        assertThat(returnBool, `is`(false))
//    }
//
//    @Test
//    fun logError_completedOUTDATED_VERSION_returnsTrue() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.NO_EXCEPTION, ErrorClientEnum.OUTDATED_VERSION)
//
//        assertThat(returnBool, `is`(true))
//    }
//
//    @Test
//    fun logError_completedFAIL_returnsTrue() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.NO_EXCEPTION, ErrorClientEnum.SUCCESSFUL)
//
//        assertThat(returnBool, `is`(true))
//    }
//
//    @Test
//    fun logError_exceptionUNKNOWN_returnsTrue() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN, ErrorClientEnum.SUCCESSFUL)
//
//        assertThat(returnBool, `is`(false))
//    }
//
//    @Test
//    fun logError_exceptionDEADLINE_EXCEEDED_returnsTrue() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED, ErrorClientEnum.SUCCESSFUL)
//
//        assertThat(returnBool, `is`(false))
//    }
//
//    @Test
//    fun logError_exceptionUNAVAILABLE_returnsTrue() {
//
//        val returnBool = logErrorClient.logError(
//            requestDataClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE, ErrorClientEnum.SUCCESSFUL)
//
//        assertThat(returnBool, `is`(false))
//    }
//
//}