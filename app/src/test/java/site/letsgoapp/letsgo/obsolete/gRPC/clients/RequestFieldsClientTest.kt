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
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.GenericRequestDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.RequestFieldsBirthdayResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.request_fields_proto.RequestFieldsGenderResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.RequestFieldsInfoFieldResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.ReturnStatusProtoEnum
//import site.letsgoapp.letsgo.utilities.GenderEnum
//
//@RunWith(AndroidJUnit4::class)
//@Config(maxSdk = Build.VERSION_CODES.P)
////@Config(sdk = [Build.VERSION_CODES.P])
//class RequestFieldsClientTest {
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
//    fun requestBirthday_completedSUCCESS_returnsSUCCESS() {
//
//        val response = RequestFieldsClient.requestBirthday(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            RequestFieldsBirthdayResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                1986, 10, 23, 1337, ""
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestBirthday_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestBirthday(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            RequestFieldsBirthdayResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, -1,
//                -1, -1, -1L, context.getString(R.string.network_error_unknown)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestBirthday_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestBirthday(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            RequestFieldsBirthdayResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, -1,
//                -1, -1, -1L, context.getString(R.string.network_error_deadline_exceeded)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestBirthday_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestBirthday(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            RequestFieldsBirthdayResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, -1,
//                -1, -1, -1L, context.getString(R.string.network_error_unavailable)
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun requestPhoneNumber_completedSUCCESS_returnsSUCCESS() {
//
//        val response = RequestFieldsClient.requestPhoneNumber(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                "returnString", -1, -1
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestPhoneNumber_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestPhoneNumber(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown), -1, -1L
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestPhoneNumber_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestPhoneNumber(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded), -1, -1L
//            )
//
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestPhoneNumber_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestPhoneNumber(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable), -1, -1L
//            )
//
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun requestEmail_completedSUCCESS_returnsSUCCESS() {
//
//        val response = RequestFieldsClient.requestEmail(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                "requestEmail", -1, 1337
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestEmail_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestEmail(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown), -1, -1L
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestEmail_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestEmail(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded), -1, -1L
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestEmail_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestEmail(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable), -1, -1L
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun requestGender_completedSUCCESS_returnsSUCCESS() {
//
//        val response = RequestFieldsClient.requestGender(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse = RequestFieldsGenderResponseDataClass(
//            ReturnStatusProtoEnum.SUCCESS,
//            GenderEnum.GENDER_OTHER, "Dragon", 1337);
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestGender_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestGender(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse = RequestFieldsGenderResponseDataClass(
//            ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, GenderEnum.GENDER_UNKNOWN,
//            context.getString(R.string.network_error_unknown), -1L)
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestGender_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestGender(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse = RequestFieldsGenderResponseDataClass(
//            ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, GenderEnum.GENDER_UNKNOWN,
//            context.getString(R.string.network_error_deadline_exceeded), -1L)
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestGender_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestGender(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse = RequestFieldsGenderResponseDataClass(
//            ReturnStatusProtoEnum.ANDROID_SIDE_ERROR, GenderEnum.GENDER_UNKNOWN,
//            context.getString(R.string.network_error_unavailable), -1L)
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    /**----------------------------------------------------------------------**/
//
//    @Test
//    fun requestFirstName_completedSUCCESS_returnsSUCCESS() {
//
//        val response = RequestFieldsClient.requestFirstName(genericRequestClass, ClientExceptionTestingEnum.NO_EXCEPTION)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.SUCCESS,
//                "firstName", -1, 1337
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestFirstName_exceptionUNKNOWN_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestFirstName(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNKNOWN)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unknown), -1, -1L
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestFirstName_exceptionDEADLINE_EXCEEDED_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestFirstName(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_DEADLINE_EXCEEDED)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_deadline_exceeded), -1, -1L
//            )
//
//        assertThat(response, `is`(expectedResponse))
//    }
//
//    @Test
//    fun requestFirstName_exceptionUNAVAILABLE_returnsANDROID_SIDE_ERROR() {
//
//        val response = RequestFieldsClient.requestFirstName(genericRequestClass, ClientExceptionTestingEnum.EXCEPTION_UNAVAILABLE)
//
//        val expectedResponse =
//            RequestFieldsInfoFieldResponseDataClass(
//                ReturnStatusProtoEnum.ANDROID_SIDE_ERROR,
//                context.getString(R.string.network_error_unavailable), -1, -1L
//            );
//
//        assertThat(response, `is`(expectedResponse))
//    }
//}