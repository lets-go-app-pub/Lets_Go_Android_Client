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
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class SMSVerificationClientTest {
//
//    private lateinit var context: Context
//    private lateinit var request: SMSVerificationProtoRequestDataClass
//
//    @Before
//    fun setupClientAndRequest() {
//        context = ApplicationProvider.getApplicationContext()
//        request =
//            SMSVerificationProtoRequestDataClass(
//                "phoneNumber",
//                "verificationCode",
//                1.0,
//                SMSVerificationUpdateAccountEnum.UNKNOWN,
//                -1,
//                -1,
//                -1
//            )
//    }
//
//    @Test
//    fun smsVerification_completedSUCCESS_returnsSUCCESS() {
//
//        val response = SMSVerificationClient.smsVerification(request, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            SMSVerificationProtoResponseDataClass(
//                SMSVerificationStatusEnum.SUCCESS,
//                ""
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//    @Test
//    fun smsVerification_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = SMSVerificationClient.smsVerification(request, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            SMSVerificationProtoResponseDataClass(
//                SMSVerificationStatusEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//    @Test
//    fun smsVerification_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = SMSVerificationClient.smsVerification(request, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            SMSVerificationProtoResponseDataClass(
//                SMSVerificationStatusEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//    @Test
//    fun smsVerification_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = SMSVerificationClient.smsVerification(request, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            SMSVerificationProtoResponseDataClass(
//                SMSVerificationStatusEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable)
//            )
//
//        MatcherAssert.assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//}