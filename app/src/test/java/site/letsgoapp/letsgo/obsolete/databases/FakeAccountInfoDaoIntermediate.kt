package site.letsgoapp.letsgo.obsolete.databases
//
//import kotlinx.coroutines.CoroutineDispatcher
//import kotlinx.coroutines.Dispatchers
//import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
//import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
//
//class FakeAccountInfoDaoIntermediate(
//    private val ioDispatcher: CoroutineDispatcher =
//) : AccountInfoDaoIntermediateInterface {
//
//    var databaseList: MutableList<AccountInfoDataEntity?> = ArrayList()
//
//    override fun insertAccount(accountInfoDataEntity: AccountInfoDataEntity) {
//        databaseList.add(accountInfoDataEntity)
//    }
//
//    override fun clearTable() {
//        databaseList.clear()
//    }
//
//    override fun deleteAllButAccount(phoneNumber: String) {
//        for(element in databaseList){
//            if(element != null && element.phoneNumber != phoneNumber){
//                databaseList.remove(element)
//            }
//        }
//    }
//
//    override fun getPhoneNumber(): String? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.phoneNumber
//        } else{
//            null
//        }
//
//    }
//
//    override fun getAccountInfo(phoneNumber: String): AccountInfoDataEntity? {
//        for(element in databaseList){
//            if(element != null && element.phoneNumber == phoneNumber){
//                return element
//            }
//        }
//
//        return null
//    }
//
//    override fun getAccountInfoForErrors(): AccountInfoDataEntity? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]
//        } else{
//            null
//        }
//    }
//
//    override fun getAccountType(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.accountType
//        } else{
//            null
//        }
//    }
//
//    override fun setAccountType(accountType: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.accountType = accountType
//        }
//    }
//
//    override fun getLoginToken(): String? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.loginToken
//        } else{
//            null
//        }
//    }
//
//    override fun setLoginToken(loginToken: String) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.loginToken = loginToken
//        }
//    }
//
//    override fun getRequiresEmailAddressVerification(): Boolean? {
//
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.requiresEmailAddressVerification
//        } else{
//            null
//        }
//    }
//
//    override fun setRequiresEmailAddressVerification(requiresEmailAddressVerification: Boolean) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.requiresEmailAddressVerification = requiresEmailAddressVerification
//        }
//    }
//
//    override fun getEmail(): String? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.emailAddress
//        } else{
//            null
//        }
//    }
//
//    override fun setEmail(emailAddress: String) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.emailAddress = emailAddress
//        }
//    }
//
//    override fun getEmailTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.emailTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setEmailTimestamp(emailAddressTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.emailTimestamp = emailAddressTimestamp
//        }
//    }
//
//    override fun getFirstName(): String? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.firstName
//        } else{
//            null
//        }
//    }
//
//    override fun setFirstName(firstName: String) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.firstName = firstName
//        }
//    }
//
//    override fun getFirstNameTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.firstNameTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setFirstNameTimestamp(firstNameTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.firstNameTimestamp = firstNameTimestamp
//        }
//    }
//
//    override fun getGender(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.gender
//        } else{
//            null
//        }
//    }
//
//    override fun setGender(gender: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.gender = gender
//        }
//    }
//
//    override fun getGenderOtherName(): String? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.genderOtherName
//        } else{
//            null
//        }
//    }
//
//    override fun setGenderOtherName(genderOtherName: String) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.genderOtherName = genderOtherName
//        }
//    }
//
//    override fun getGenderTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.genderTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setGenderTimestamp(genderTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.genderTimestamp = genderTimestamp
//        }
//    }
//
//    override fun getBirthYear(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.birthYear
//        } else{
//            null
//        }
//    }
//
//    override fun setBirthYear(birthYear: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.birthYear = birthYear
//        }
//    }
//
//    override fun getBirthMonth(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.birthMonth
//        } else{
//            null
//        }
//    }
//
//    override fun setBirthMonth(birthMonth: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.birthMonth = birthMonth
//        }
//    }
//
//    override fun getBirthDayOfMonth(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.birthDayOfMonth
//        } else{
//            null
//        }
//    }
//
//    override fun setBirthDayOfMonth(birthDayOfMonth: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.birthDayOfMonth = birthDayOfMonth
//        }
//    }
//
//    override fun getBirthdayTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.birthdayTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setBirthdayTimestamp(birthdayTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.birthdayTimestamp = birthdayTimestamp
//        }
//    }
//
//    override fun getPictures(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.pictures
//        } else{
//            null
//        }
//    }
//
//    override fun setPictures(pictures: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.pictures = pictures
//        }
//    }
//
//    override fun getPicturesTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.picturesTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setPicturesTimestamp(picturesTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.picturesTimestamp = picturesTimestamp
//        }
//    }
//
//    override fun getCategories(): Int? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.categories
//        } else{
//            null
//        }
//    }
//
//    override fun setCategories(categories: Int) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.categories = categories
//        }
//    }
//
//    override fun getCategoriesTimestamp(): Long? {
//        return if(databaseList.isNotEmpty()) {
//            databaseList[0]?.categoriesTimestamp
//        } else{
//            null
//        }
//    }
//
//    override fun setCategoriesTimestamp(categoriesTimestamp: Long) {
//        if(databaseList.isNotEmpty() && databaseList[0] != null) {
//            databaseList[0]!!.categoriesTimestamp = categoriesTimestamp
//        }
//    }
//
//}