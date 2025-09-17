package site.letsgoapp.letsgo.obsolete.gRPC
//
//import loginsupport.RefreshLoginTokenRequest
//import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.*
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.request_fields_proto.RequestFieldsGenderResponseDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.SMSVerificationProtoRequestDataClass
//import site.letsgoapp.letsgo.gRPC.proto_file_classes.SMSVerificationProtoResponseDataClass
//import site.letsgoapp.letsgo.utilities.GenderEnum
//
//class FakeClientSourceIntermediate: ClientsInterface {
//
//    var accountInfo: AccountInfoDataEntity? = null
//
//    var loginFunctionResponse: LoginFunctionProtoResponseDataClass? = null
//    var loginSupportRefreshTokenResponse: LoginSupportResponseDataClass? = null
//    var loginSupportDeleteAccountResponse: LoginSupportResponseDataClass? = null
//    var loginSupportLogoutResponse: LoginSupportResponseDataClass? = null
//    var neededVeriInfoResponse: NeededVeriInfoResponseDataClass? = null
//    var requestFieldsPhoneNumberResponse: RequestFieldsInfoFieldResponseDataClass? = null
//    var requestFieldsBirthdayResponse: RequestFieldsBirthdayResponseDataClass? = null
//    var requestFieldsEmailResponse: RequestFieldsInfoFieldResponseDataClass? = null
//    var requestFieldsGenderResponse: RequestFieldsGenderResponseDataClass? = null
//    var requestFieldsFirstNameResponse: RequestFieldsInfoFieldResponseDataClass? = null
//    var setFieldsBirthdayResponse: SetFieldsSetFieldResponseDataClass? = null
//    var setFieldsEmailResponse: SetFieldsSetFieldResponseDataClass? = null
//    var setFieldsGenderResponse: SetFieldsSetFieldResponseDataClass? = null
//    var setFieldsFirstNameResponse: SetFieldsSetFieldResponseDataClass? = null
//    var sMSVerificationResponse: SMSVerificationProtoResponseDataClass? = null
//
//    var requestFieldsPhoneNumberRequest: GenericRequestDataClass? = null
//    var requestFieldsBirthdayRequest: GenericRequestDataClass? = null
//    var requestFieldsEmailRequest: GenericRequestDataClass? = null
//    var requestFieldsGenderRequest: GenericRequestDataClass? = null
//    var requestFieldsFirstNameRequest: GenericRequestDataClass? = null
//
//    var setFieldsBirthdayRequest: SetFieldsSetBirthdayRequestDataClass? = null
//    var setFieldsEmailRequest: SetFieldsSetEmailRequestDataClass? = null
//    var setFieldsGenderRequest: SetFieldsSetGenderRequestDataClass? = null
//    var setFieldsFirstNameRequest: SetFieldsSetStringRequestDataClass? = null
//
//    fun createDummyAccount(phoneNumber: String, loginToken: String, accountType: LoginFunctionAccountTypeEnum,
//                           requiresEmailVerification: Boolean, emailAddress: String, emailTimestamp: Long,
//                           firstName: String, firstNameTimestamp: Long, gender: GenderEnum, genderOther: String,
//                           genderTimestamp: Long, birthYear: Int, birthMonth: Int, birthDayOfMonth: Int,
//                           birthdayTimestamp: Long, pictures: Int, picturesTimestamp: Long, categories: Int,
//                           categoriesTimestamp: Long) {
//
//        accountInfo =
//            AccountInfoDataEntity(
//                phoneNumber, loginToken, accountType.getVal(),
//                requiresEmailVerification, emailAddress, emailTimestamp, firstName,
//                firstNameTimestamp, gender.getVal(), genderOther, genderTimestamp,
//                birthYear, birthMonth, birthDayOfMonth, birthdayTimestamp,
//                pictures, picturesTimestamp, categories, categoriesTimestamp
//            )
//
//    }
//
//    override fun loginFunctionClientLogin(passedRequest: LoginFunctionProtoRequestDataClass): LoginFunctionProtoResponseDataClass? {
//
//        return loginFunctionResponse
//    }
//
//    override fun loginSupportClientRefreshLoginToken(passedRequest: RefreshLoginTokenRequest): LoginSupportResponseDataClass? {
//
//        return loginSupportRefreshTokenResponse
//    }
//
//    override fun loginSupportClientDeleteAccount(passedRequest: GenericRequestDataClass): LoginSupportResponseDataClass? {
//
//        return loginSupportDeleteAccountResponse
//    }
//
//    override fun loginSupportClientLogoutFunction(passedRequest: GenericRequestDataClass): LoginSupportResponseDataClass? {
//
//        return loginSupportLogoutResponse
//    }
//
//    override fun loginSupportClientNeedVeriInfo(passedRequest: GenericRequestDataClass): NeededVeriInfoResponseDataClass? {
//
//        return neededVeriInfoResponse
//    }
//
//    override fun requestFieldsClientPhoneNumber(passedRequest: GenericRequestDataClass): RequestFieldsInfoFieldResponseDataClass? {
//
//        requestFieldsPhoneNumberRequest = passedRequest
//
//        return requestFieldsPhoneNumberResponse
//    }
//
//    override fun requestFieldsClientBirthday(passedRequest: GenericRequestDataClass): RequestFieldsBirthdayResponseDataClass? {
//
//        requestFieldsBirthdayRequest = passedRequest
//
//        return requestFieldsBirthdayResponse
//    }
//
//    override fun requestFieldsClientEmail(passedRequest: GenericRequestDataClass): RequestFieldsInfoFieldResponseDataClass? {
//
//        requestFieldsEmailRequest = passedRequest
//
//        return requestFieldsEmailResponse
//    }
//
//    override fun requestFieldsClientGender(passedRequest: GenericRequestDataClass): RequestFieldsGenderResponseDataClass? {
//
//        requestFieldsGenderRequest = passedRequest
//
//        return requestFieldsGenderResponse
//    }
//
//    override fun requestFieldsClientFirstName(passedRequest: GenericRequestDataClass): RequestFieldsInfoFieldResponseDataClass? {
//
//        requestFieldsFirstNameRequest = passedRequest
//
//        return requestFieldsFirstNameResponse
//    }
//
//    override fun setFieldsClientBirthday(passedRequest: SetFieldsSetBirthdayRequestDataClass): SetFieldsSetFieldResponseDataClass? {
//
//        setFieldsBirthdayRequest = passedRequest
//
//        return setFieldsBirthdayResponse
//    }
//
//    override fun setFieldsClientEmail(passedRequest: SetFieldsSetEmailRequestDataClass): SetFieldsSetFieldResponseDataClass? {
//
//        setFieldsEmailRequest = passedRequest
//
//        return setFieldsEmailResponse
//    }
//
//    override fun setFieldsClientGender(passedRequest: SetFieldsSetGenderRequestDataClass): SetFieldsSetFieldResponseDataClass? {
//
//        setFieldsGenderRequest = passedRequest
//
//        return setFieldsGenderResponse
//    }
//
//    override fun setFieldsClientFirstName(passedRequest: SetFieldsSetStringRequestDataClass): SetFieldsSetFieldResponseDataClass? {
//
//        setFieldsFirstNameRequest = passedRequest
//
//        return setFieldsFirstNameResponse
//    }
//
//    override fun smsVerificationClientSMSVerification(passedRequest: SMSVerificationProtoRequestDataClass): SMSVerificationProtoResponseDataClass? {
//
//        return sMSVerificationResponse
//    }
//
//
//}