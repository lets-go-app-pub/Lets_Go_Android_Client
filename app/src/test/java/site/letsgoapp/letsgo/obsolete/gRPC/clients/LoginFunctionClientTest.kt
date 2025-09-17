package site.letsgoapp.letsgo.obsolete.gRPC.clients
//
//import android.content.Context
//import android.os.Build
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.hamcrest.CoreMatchers.`is`
//import org.hamcrest.MatcherAssert.assertThat
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.annotation.Config
//import site.letsgoapp.letsgo.R
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginFunctionAccountTypeEnum
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginFunctionProtoRequestDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginFunctionProtoResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginFunctionStatusEnum
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class LoginFunctionClientTest {
//
//    private lateinit var requestDataClass: LoginFunctionProtoRequestDataClass
//    private lateinit var context: Context
//
//    @Before
//    fun setupClientAndRequest() {
//
//        context = ApplicationProvider.getApplicationContext()
//
//        requestDataClass =
//            LoginFunctionProtoRequestDataClass(
//                "PhoneNumber",
//                "accountID",
//                "deviceID",
//                1.0,
//                LoginFunctionAccountTypeEnum.PHONE_ACCOUNT
//            )
//    }
//
//    @Test
//    fun login_completedSUCCESSFUL_returnsLOGGED_IN() {
//
//        val responseDataClass = LoginFunctionClient.login(
//            requestDataClass, ClientExceptionTestingEnum.NO_EXCEPTION
//        )
//
//        println(Thread.currentThread().stackTrace[2].lineNumber)
//        val expectedResponse =
//            LoginFunctionProtoResponseDataClass(
//                LoginFunctionStatusEnum.LOGGED_IN,
//                "123456", -1, true,
//                LoginFunctionRequiresInfoEnum.LG_ERR, false,
//                -1L, -1L, -1L,
//                -1L, -1L, -1L
//            )
//
//        assertThat(responseDataClass, `is`(expectedResponse))
//    }
//
//    @Test
//    fun login_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//    val responseDataClass = LoginFunctionClient.login(
//        requestDataClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//    val expectedResponse =
//        LoginFunctionProtoResponseDataClass(
//            LoginFunctionStatusEnum.ANDROID_SIDE_ERROR,
//            context.getString(R.string.network_error_unknown), -1, true,
//            LoginFunctionRequiresInfoEnum.LG_ERR, false,
//            -1L, -1L, -1L,
//            -1L, -1L, -1L
//        );
//
//    assertThat(responseDataClass, `is`(expectedResponse))
//    }
//
//    @Test
//    fun login_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val responseDataClass = LoginFunctionClient.login(
//            requestDataClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            LoginFunctionProtoResponseDataClass(
//                LoginFunctionStatusEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded), -1, true,
//                LoginFunctionRequiresInfoEnum.LG_ERR, false,
//                -1L, -1L, -1L,
//                -1L, -1L, -1L
//            );
//
//        assertThat(responseDataClass, `is`(expectedResponse))
//    }
//
//    @Test
//    fun login_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val responseDataClass = LoginFunctionClient.login(
//            requestDataClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            LoginFunctionProtoResponseDataClass(
//                LoginFunctionStatusEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable), -1, true,
//                LoginFunctionRequiresInfoEnum.LG_ERR, false,
//                -1L, -1L, -1L,
//                -1L, -1L, -1L
//            );
//
//        assertThat(responseDataClass, `is`(expectedResponse))
//    }
//}