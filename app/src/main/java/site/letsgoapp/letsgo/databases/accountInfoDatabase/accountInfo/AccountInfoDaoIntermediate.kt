package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo

import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import categorytimeframe.CategoryTimeFrame
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import user_subscription_status.UserSubscriptionStatusOuterClass.UserSubscriptionStatus

class AccountInfoDaoIntermediate(
    private val accountInfoDataSource: AccountInfoDatabaseDao
) : AccountInfoDaoIntermediateInterface {

    override suspend fun insertAccount(accountInfoDataEntity: AccountInfoDataEntity) {
        accountInfoDataSource.insertAccount(accountInfoDataEntity)
    }

    //delete all
    override suspend fun clearTable() {
        accountInfoDataSource.clearTable()
    }

    //delete all rows except the passed phoneNumber
    override suspend fun deleteAllButAccount(phoneNumber: String) {
        accountInfoDataSource.deleteAllButAccount(phoneNumber)
    }

    //gets the phone number
    override suspend fun getPhoneNumber(): String? {
        return accountInfoDataSource.getPhoneNumber()
    }

    //gets the account of the phone number
    override suspend fun getAccountInfo(phoneNumber: String): AccountInfoDataEntity? {
        return accountInfoDataSource.getAccountInfo(phoneNumber)
    }

    //sets info for login if outdated
    override suspend fun setInfoFromRunningLogin(
        algorithmSearchOptions: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions,

        birthYear: Int,
        birthMonth: Int,
        birthDayOfMonth: Int,
        age: Int,
        birthdayTimestamp: Long,

        emailAddress: String,
        requiresEmailAddressVerification: Boolean,
        emailTimestamp: Long,

        gender: String,
        genderTimestamp: Long,

        firstName: String,
        firstNameTimestamp: Long,

        userBio: String,
        userCity: String,
        genderRange: String,
        userMinAge: Int,
        userMaxAge: Int,
        maxDistance: Int,
        postLoginTimestamp: Long,

        categories: String,
        categoriesTimestamp: Long,

        subscriptionStatus: UserSubscriptionStatus,
        subscriptionExpirationTime: Long,

        optedInToPromotionalEmails: Boolean
    ) {
        accountInfoDataSource.setInfoFromRunningLogin(
            algorithmSearchOptions.number,
            birthYear,
            birthMonth,
            birthDayOfMonth,
            age,
            birthdayTimestamp,
            emailAddress,
            requiresEmailAddressVerification,
            emailTimestamp,
            gender,
            genderTimestamp,
            firstName,
            firstNameTimestamp,
            userBio,
            userCity,
            genderRange,
            userMinAge,
            userMaxAge,
            maxDistance,
            postLoginTimestamp,
            categories,
            categoriesTimestamp,
            subscriptionStatus.number,
            subscriptionExpirationTime,
            optedInToPromotionalEmails
        )
    }

    //gets the account
    override suspend fun getAccountInfoForErrors(): AccountInfoDataEntity? {
        return accountInfoDataSource.getAccountInfoForErrors()
    }

    //gets account type returns null if not found
    override suspend fun getAccountTypeAndPhoneNumber(): InitialLoginInfo? {
        return accountInfoDataSource.getAccountTypeAndLoginInfo()
    }

    override suspend fun setInfoForLoginFunction(
        accountType: Int
    ) {
        accountInfoDataSource.setInfoForLoginFunction(accountType)
    }

    //gets email address returns null if not found
    override suspend fun getEmail(): String? {
        return accountInfoDataSource.getEmail()
    }

    override suspend fun setAlgorithmMatchOptions(algorithmMatchOptions: Int) {
        accountInfoDataSource.setAlgorithmMatchOptions(algorithmMatchOptions)
    }

    override suspend fun setOptedInToPromotionalEmail(optedInToPromotionalEmails: Boolean) {
        accountInfoDataSource.setOptedInToPromotionalEmail(optedInToPromotionalEmails)
    }

    //sets all email info across all rows (there should only be 1 row)
    override suspend fun setEmailInfo(
        emailAddress: String,
        requiresEmailAddressVerification: Boolean,
        emailAddressTimestamp: Long
    ) {
        accountInfoDataSource.setEmailInfo(
            emailAddress,
            requiresEmailAddressVerification,
            emailAddressTimestamp
        )
    }

    //sets requiresEmailAddressVerification field
    override suspend fun setRequiresEmailAddressVerification(requiresEmailAddressVerification: Boolean) {
        accountInfoDataSource.setRequiresEmailAddressVerification(requiresEmailAddressVerification)
    }

    //gets all first name info across all rows (there should only be 1 row)
    override suspend fun getFirstNameInfo(): FirstNameDataHolder? {
        return accountInfoDataSource.getFirstNameInfo()
    }

    //sets all first name info across all rows (there should only be 1 row)
    override suspend fun setFirstNameInfo(firstName: String, firstNameTimestamp: Long) {
        accountInfoDataSource.setFirstNameInfo(firstName, firstNameTimestamp)
    }

    //gets all gender info across all rows (there should only be 1 row)
    override suspend fun getGenderInfo(): GenderDataHolder? {
        return accountInfoDataSource.getGenderInfo()
    }

    //sets all gender info across all rows (there should only be 1 row)
    override suspend fun setGenderInfo(gender: String, genderTimestamp: Long) {
        accountInfoDataSource.setGenderInfo(gender, genderTimestamp)
    }

    //gets all birthday info across all rows (there should only be 1 row)
    override suspend fun getBirthdayInfo(): BirthdayHolder? {
        return accountInfoDataSource.getBirthDayInfo()
    }

    //sets all birthday info across all rows (there should only be 1 row)
    override suspend fun setBirthdayInfo(
        birthYear: Int, birthMonth: Int, birthDayOfMonth: Int,
        birthdayTimestamp: Long, age: Int
    ) {
        accountInfoDataSource.setBirthdayInfo(
            birthYear,
            birthMonth,
            birthDayOfMonth,
            birthdayTimestamp,
            age
        )
    }

    //sets categories across all rows (there should only be 1 row)
    override suspend fun setCategories(categories: MutableList<CategoryTimeFrame.CategoryActivityMessage>) {
        accountInfoDataSource.setCategories(
            convertCategoryActivityMessageToStringWithErrorChecking(
                categories
            )
        )
    }

    //gets categories and age if not found
    override suspend fun getCategoriesAndAge(transactionWrapper: TransactionWrapper): ReturnUserSelectedCategoriesAndAgeDataHolder {

        var categoriesArrayList: MutableList<CategoryTimeFrame.CategoryActivityMessage>? = null
        var age: Int? = null

        transactionWrapper.runTransaction {
            val categoriesAgeObject = accountInfoDataSource.getCategoriesAndAge()

            categoriesArrayList = extractCategoriesFromString(categoriesAgeObject?.categories)

            age = categoriesAgeObject?.age ?: GlobalValues.server_imported_values.lowestAllowedAge
        }

        return ReturnUserSelectedCategoriesAndAgeDataHolder(age!!, categoriesArrayList!!)

    }

    //sets all category info across all rows (there should only be 1 row)
    override suspend fun setCategoryInfo(categories: String, categoriesTimestamp: Long) {
        accountInfoDataSource.setCategoryInfo(categories, categoriesTimestamp)
    }

    //sets all category info across all rows (there should only be 1 row)
    override suspend fun setCategoryInfo(
        categories: List<CategoryTimeFrame.CategoryActivityMessage>,
        categoriesTimestamp: Long
    ) {
        accountInfoDataSource.setCategoryInfo(
            convertCategoryActivityMessageToStringWithErrorChecking(categories),
            categoriesTimestamp
        )
    }

    //sets user bio across all rows (there should only be 1 row)
    override suspend fun setUserBio(userBio: String) {
        accountInfoDataSource.setUserBio(userBio)
    }

    //sets user city across all rows (there should only be 1 row)
    override suspend fun setUserCity(userCity: String) {
        accountInfoDataSource.setUserCity(userCity)
    }

    //sets gender range across all rows (there should only be 1 row)
    override suspend fun setGenderRange(genderRange: String) {
        accountInfoDataSource.setGenderRange(genderRange)
    }

    //sets user age range across all rows (there should only be 1 row)
    override suspend fun setUserAgeRange(userMinAge: Int, userMaxAge: Int) {
        accountInfoDataSource.setUserAgeRange(userMinAge, userMaxAge)
    }

    //sets max distance across all rows (there should only be 1 row)
    override suspend fun setMaxDistance(maxDistance: Int) {
        accountInfoDataSource.setMaxDistance(maxDistance)
    }

    //sets post login timestamp across all rows (there should only be 1 row)
    override suspend fun setPostLoginTimestamp(postLoginTimestamp: Long) {
        accountInfoDataSource.setPostLoginTimestamp(postLoginTimestamp)
    }

    //sets post login info across all rows (there should only be 1 row)
    override suspend fun setPostLoginInfo(
        userBio: String, userCity: String, genderRange: ArrayList<String>,
        userMinAge: Int, userMaxAge: Int, maxDistance: Int,
        postLoginTimestamp: Long
    ) {
        accountInfoDataSource.setPostLoginInfo(
            userBio,
            userCity,
            convertGenderRangeToString(genderRange),
            userMinAge,
            userMaxAge,
            maxDistance,
            postLoginTimestamp
        )
    }

    //gets all blocked accounts
    override suspend fun getBlockedAccounts(): MutableSet<String> {
        return convertStringToBlockedAccountsMap(accountInfoDataSource.getBlockedAccounts() ?: "")
    }

    //sets blockedAccounts across all rows (there should only be 1 row)
    override suspend fun setBlockedAccounts(blockedAccounts: MutableSet<String>) {
        accountInfoDataSource.setBlockedAccounts(convertBlockedAccountsSetToString(blockedAccounts))
    }

    //sets chatRoomSortMethodSelected across all rows (there should only be 1 row)
    override suspend fun setChatRoomSortMethodSelected(chatRoomSortMethodSelected: ChatRoomSortMethodSelected) {
        accountInfoDataSource.setChatRoomSortMethodSelected(chatRoomSortMethodSelected.ordinal)
    }

    override suspend fun addBlockedAccount(
        blockedAccount: String,
        transactionWrapper: TransactionWrapper
    ) {
        transactionWrapper.runTransaction {
            val blockedAccountString = accountInfoDataSource.getBlockedAccounts()
            val newStringValue = blockedAccount + BLOCKED_ACCOUNT_OID_SEPARATOR

            if (blockedAccountString != null) {
                accountInfoDataSource.setBlockedAccounts(blockedAccountString + newStringValue)
            } else {
                accountInfoDataSource.setBlockedAccounts(newStringValue)
            }
        }
    }

    override suspend fun removeBlockedAccount(
        blockedAccount: String,
        transactionWrapper: TransactionWrapper
    ) {
        transactionWrapper.runTransaction {
            val blockedAccountMap =
                convertStringToBlockedAccountsMap(accountInfoDataSource.getBlockedAccounts() ?: "")

            blockedAccountMap.remove(blockedAccount)

            accountInfoDataSource.setBlockedAccounts(
                convertBlockedAccountsSetToString(
                    blockedAccountMap
                )
            )
        }
    }

    //gets all info the modify profile function requires null if values are not found
    override suspend fun getApplicationAccountInfo(transactionWrapper: TransactionWrapper): ApplicationAccountInfo? {
        var returnVar: ApplicationAccountInfo? = null

        transactionWrapper.runTransaction {

            val accountInfo = accountInfoDataSource.getApplicationAccountInfo()
            if (accountInfo != null) {

                val categoriesArrayList = extractCategoriesFromString(accountInfo.categories)

                returnVar = ApplicationAccountInfo(
                    accountInfo,
                    categoriesArrayList
                )
            }

        }

        return returnVar
    }

    private suspend fun extractCategoriesFromString(
        categoriesString: String?
    ): MutableList<CategoryTimeFrame.CategoryActivityMessage> {
        val categoriesArrayList =
            convertStringToCategoryActivityMessageAndTrimTimes(categoriesString)

        //if categories need updated, update them
        if (categoriesArrayList.first) {
            setCategories(categoriesArrayList.second)
        }

        return categoriesArrayList.second
    }

}

data class ApplicationAccountInfo(
    val algorithm_search_options: AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions = AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY,
    val first_name: String = "~",
    val first_name_timestamp: Long = -1,
    val categories: MutableList<CategoryTimeFrame.CategoryActivityMessage> = mutableListOf(),
    val categories_timestamp: Long = -1,
    val gender: String = "~",
    val gender_timestamp: Long = -1L,
    val email_address: String = "~",
    val requires_email_address_verification: Boolean = false,
    val email_timestamp: Long = -1L,
    val user_bio: String = "~",
    val user_city: String = "~",
    val match_parameters_min_age: Int = -1,
    val match_parameters_max_age: Int = -1,
    val match_parameters_max_distance: Int = -1,
    val user_gender_range: String = "~",
    val post_login_timestamp: Long = -1L,
    val age: Int = -1,
    val chat_room_sort_method_selected: Int = ChatRoomSortMethodSelected.SORT_BY_RECENT.ordinal,
    val subscription_status: UserSubscriptionStatus = UserSubscriptionStatus.NO_SUBSCRIPTION,
    val opted_in_to_promotional_emails: Boolean = false,
) {
    constructor(
        info: ApplicationAccountInfoExtractFromDaoDataClass,
        _categories: MutableList<CategoryTimeFrame.CategoryActivityMessage> = mutableListOf()
    ) : this(
        AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.forNumber(info.algorithm_search_options),
        info.first_name,
        info.first_name_timestamp,
        _categories,
        info.categories_timestamp,
        info.gender,
        info.gender_timestamp,
        info.email_address,
        info.requires_email_address_verification,
        info.email_timestamp,
        info.user_bio,
        info.user_city,
        info.match_parameters_min_age,
        info.match_parameters_max_age,
        info.match_parameters_max_distance,
        info.user_gender_range,
        info.post_login_timestamp,
        info.age,
        info.chat_room_sort_method_selected,
        UserSubscriptionStatus.forNumber(info.subscription_status),
        info.opted_in_to_promotional_emails
    )

}

//used for the return values to getModifyProfileScreenInfo() in AccountInfoDatabaseDao
data class ApplicationAccountInfoExtractFromDaoDataClass(
    val algorithm_search_options: Int = -1,
    val first_name: String = "~",
    val first_name_timestamp: Long = -1L,
    val categories: String = "~",
    val categories_timestamp: Long = -1L,
    val gender: String = "~",
    val gender_timestamp: Long = -1L,
    val email_address: String = "~",
    val requires_email_address_verification: Boolean = false,
    val email_timestamp: Long = -1L,
    val user_bio: String = "~",
    val user_city: String = "~",
    val match_parameters_min_age: Int = -1,
    val match_parameters_max_age: Int = -1,
    val match_parameters_max_distance: Int = -1,
    val user_gender_range: String = "~",
    val post_login_timestamp: Long = -1L,
    val age: Int = -1,
    val chat_room_sort_method_selected: Int = ChatRoomSortMethodSelected.SORT_BY_RECENT.ordinal,
    val subscription_status: Int = UserSubscriptionStatus.NO_SUBSCRIPTION.number,
    val opted_in_to_promotional_emails: Boolean = false,
) {
//    override fun toString(): String {
//
//        return "first_name: $first_name\n" +
//                "categories: $categories\n" +
//                "user_bio: $user_bio\n" +
//                "user_city: $user_city\n" +
//                "match_parameters_min_age: $match_parameters_min_age\n" +
//                "match_parameters_max_age: $match_parameters_max_age\n" +
//                "match_parameters_max_distance: $match_parameters_max_distance\n" +
//                "email_address: $email_address\n" +
//                "requires_email_address_verification: $requires_email_address_verification\n" +
//                "gender: $gender\n" +
//                "user_gender_range: $user_gender_range\n" +
//                "age: $age\n"
//    }
}

data class FirstNameDataHolder(val first_name: String, val first_name_timestamp: Long)

data class GenderDataHolder(val gender: String, val gender_timestamp: Long)