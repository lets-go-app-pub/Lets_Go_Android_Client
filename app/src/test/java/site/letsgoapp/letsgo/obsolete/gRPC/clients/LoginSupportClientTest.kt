package site.letsgoapp.letsgo.obsolete.gRPC.clients
//
//import android.content.Context
//import android.os.Build
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.hamcrest.CoreMatchers
//import org.hamcrest.MatcherAssert
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.annotation.Config
//import site.letsgoapp.letsgo.R
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.login_function_proto.LoginFunctionRequiresInfoEnum
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.GenericRequestDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginSupportResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.NeededVeriInfoResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.ReturnStatusProtoEnum
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class LoginSupportClientTest {
//
//    private lateinit var genericRequestClass: GenericRequestDataClass
//    private lateinit var context: Context
//
//    @Before
//    fun setupClientAndRequest() {
//
//        context = ApplicationProvider.getApplicationContext()
//
//        genericRequestClass =
//            GenericRequestDataClass(
//                "loginToken", 1.0
//            )
//
//    }
//
//    @Test
//    fun refreshLoginToken_completedSUCCESS_returnsSUCCESS() {
//        val response = LoginSupportClient.refreshLoginToken(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS, ""
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun refreshLoginToken_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.refreshLoginToken(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun refreshLoginToken_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.refreshLoginToken(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun refreshLoginToken_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.refreshLoginToken(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun deleteAccount_completedSUCCESS_returnsSUCCESS() {
//        val response = LoginSupportClient.deleteAccount(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS, ""
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun deleteAccount_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.deleteAccount(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun deleteAccount_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.deleteAccount(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun deleteAccount_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.deleteAccount(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun logoutFunction_completedSUCCESS_returnsSUCCESS() {
//        val response = LoginSupportClient.logoutFunction(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS, ""
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun logoutFunction_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.logoutFunction(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun logoutFunction_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.logoutFunction(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun logoutFunction_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.deleteAccount(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            LoginSupportResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun needVeriInfo_completedSUCCESS_returnsSUCCESS() {
//        val response = LoginSupportClient.needVeriInfo(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            NeededVeriInfoResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                LoginFunctionRequiresInfoEnum.HAS_ALL_INFO, false,
//                -1L, -1L, -1L, -1L,
//                -1L, -1L, ""
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun needVeriInfo_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.needVeriInfo(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            NeededVeriInfoResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                LoginFunctionRequiresInfoEnum.CONNECTION_ERR,
//                false,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                context.getString(R.string.network_error_unknown)
//            );
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun needVeriInfo_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.needVeriInfo(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            NeededVeriInfoResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                LoginFunctionRequiresInfoEnum.CONNECTION_ERR,
//                false,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                context.getString(R.string.network_error_deadline_exceeded)
//            );
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//    @Test
//    fun needVeriInfo_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val response = LoginSupportClient.needVeriInfo(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            NeededVeriInfoResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                LoginFunctionRequiresInfoEnum.CONNECTION_ERR,
//                false,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                -1L,
//                context.getString(R.string.network_error_unavailable)
//            );
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//
//    }
//
//}