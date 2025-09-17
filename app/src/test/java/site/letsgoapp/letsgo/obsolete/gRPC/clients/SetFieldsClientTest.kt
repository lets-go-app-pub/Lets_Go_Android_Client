package site.letsgoapp.letsgo.obsolete.gRPC.clients
//
//import android.content.Context
//import android.os.Build
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.hamcrest.CoreMatchers
//import org.hamcrest.CoreMatchers.`is`
//import org.hamcrest.MatcherAssert.assertThat
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.annotation.Config
//import site.letsgoapp.letsgo.R
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.*
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.ReturnStatusProtoEnum
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class SetFieldsClientTest {
//
//    private lateinit var context: Context
//
//    @Before
//    fun setupClientAndRequest() {
//        context = ApplicationProvider.getApplicationContext()
//    }
//
//    @Test
//    fun setBirthday_completedSUCCESS_returnsSUCCESS() {
//        val request =
//            SetFieldsSetBirthdayRequestDataClass(
//                "loginToken", 1.0, 1986, 10, 23, 33
//            )
//
//        val response = SetFieldsClient.setBirthday(request, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                1337, ""
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setBirthday_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetBirthdayRequestDataClass(
//                "loginToken", 1.0, 1986, 10, 23, 33
//            )
//
//        val response = SetFieldsClient.setBirthday(request, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unknown)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setBirthday_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetBirthdayRequestDataClass(
//                "loginToken", 1.0, 1986, 10, 23, 33
//            )
//
//        val response = SetFieldsClient.setBirthday(request, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setBirthday_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetBirthdayRequestDataClass(
//                "loginToken", 1.0, 1986, 10, 23, 33
//            )
//
//        val response = SetFieldsClient.setBirthday(request, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unavailable)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun setEmail_completedSUCCESS_returnsSUCCESS() {
//        val request =
//            SetFieldsSetEmailRequestDataClass(
//                "loginToken", 1.0, "email@email.test", false
//            )
//
//        val response = SetFieldsClient.setEmail(request, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                1337, ""
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setEmail_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetEmailRequestDataClass(
//                "loginToken", 1.0, "email@email.test", false
//            )
//
//        val response = SetFieldsClient.setEmail(request, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unknown)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setEmail_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetEmailRequestDataClass(
//                "loginToken", 1.0, "email@email.test", false
//            )
//
//        val response = SetFieldsClient.setEmail(request, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setEmail_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetEmailRequestDataClass(
//                "loginToken", 1.0, "email@email.test", false
//            )
//
//        val response = SetFieldsClient.setEmail(request, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unavailable)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun setGender_completedSUCCESS_returnsSUCCESS() {
//        val request =
//            SetFieldsSetGenderRequestDataClass(
//                "loginToken", 1.0, 3, "Dragon"
//            )
//
//        val response = SetFieldsClient.setGender(request, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                1337, ""
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setGender_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetGenderRequestDataClass(
//                "loginToken", 1.0, 3, "Dragon"
//            )
//
//        val response = SetFieldsClient.setGender(request, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unknown)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setGender_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetGenderRequestDataClass(
//                "loginToken", 1.0, 3, "Dragon"
//            )
//
//        val response = SetFieldsClient.setGender(request, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//    @Test
//    fun setGender_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetGenderRequestDataClass(
//                "loginToken", 1.0, 3, "Dragon"
//            )
//
//        val response = SetFieldsClient.setGender(request, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unavailable)
//            )
//
//        assertThat(response, CoreMatchers.`is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun setFirstName_completedSUCCESS_returnsSUCCESS() {
//        val request =
//            SetFieldsSetStringRequestDataClass(
//                "loginToken", 1.0, "setString"
//            )
//
//        val response = SetFieldsClient.setFirstName(request, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                1337, ""
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setFirstName_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetStringRequestDataClass(
//                "loginToken", 1.0, "setString"
//            )
//
//        val response = SetFieldsClient.setFirstName(request, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unknown)
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setFirstName_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetStringRequestDataClass(
//                "loginToken", 1.0, "setString"
//            )
//
//        val response = SetFieldsClient.setFirstName(request, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_deadline_exceeded)
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun setFirstName_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//        val request =
//            SetFieldsSetStringRequestDataClass(
//                "loginToken", 1.0, "setString"
//            )
//
//        val response = SetFieldsClient.setFirstName(request, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            SetFieldsSetFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                -1, context.getString(R.string.network_error_unavailable)
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//}