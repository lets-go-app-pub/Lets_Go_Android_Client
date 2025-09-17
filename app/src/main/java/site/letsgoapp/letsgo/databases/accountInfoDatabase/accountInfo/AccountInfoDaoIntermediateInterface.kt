package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo

import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import categorytimeframe.CategoryTimeFrame
import site.letsgoapp.letsgo.utilities.BirthdayHolder
import site.letsgoapp.letsgo.utilities.ReturnUserSelectedCategoriesAndAgeDataHolder
import site.letsgoapp.letsgo.utilities.TransactionWrapper
import user_subscription_status.UserSubscriptionStatusOuterClass

interface AccountInfoDaoIntermediateInterface {
    suspend fun insertAccount(accountInfoDataEntity: AccountInfoDataEntity)

    //delete all
    suspend fun clearTable()

    //delete all rows except the passed phoneNumber
    suspend fun deleteAllButAccount(phoneNumber: String)

    //gets the phone number
    suspend fun getPhoneNumber(): String?

    //gets the account of the phone number
    suspend fun getAccountInfo(phoneNumber: String): AccountInfoDataEntity?

    //sets info for login if outdated
    suspend fun setInfoFromRunningLogin(
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

        subscriptionStatus: UserSubscriptionStatusOuterClass.UserSubscriptionStatus,
        subscriptionExpirationTime: Long,

        optedInToPromotionalEmails: Boolean
    )

    //gets the account
    suspend fun getAccountInfoForErrors(): AccountInfoDataEntity?

    //gets account type returns null if not found
    suspend fun getAccountTypeAndPhoneNumber(): InitialLoginInfo?

    //sets login token, login token expiration time and account type across all rows (there should only be 1 row)
    suspend fun setInfoForLoginFunction(
        accountType: Int
    )

    //gets email address returns null if not found
    suspend fun getEmail(): String?

    suspend fun setAlgorithmMatchOptions(algorithmMatchOptions: Int)

    suspend fun setOptedInToPromotionalEmail(optedInToPromotionalEmails: Boolean)

    //sets all first name info across all rows (there should only be 1 row)
    suspend fun setEmailInfo(
        emailAddress: String,
        requiresEmailAddressVerification: Boolean,
        emailAddressTimestamp: Long
    )

    //sets requiresEmailAddressVerification field
    suspend fun setRequiresEmailAddressVerification(requiresEmailAddressVerification: Boolean)

    //gets all first name info across all rows (there should only be 1 row)
    suspend fun getFirstNameInfo(): FirstNameDataHolder?

    //sets all first name info across all rows (there should only be 1 row)
    suspend fun setFirstNameInfo(firstName: String, firstNameTimestamp: Long)

    //gets all gender info across all rows (there should only be 1 row)
    suspend fun getGenderInfo(): GenderDataHolder?

    //sets all gender info across all rows (there should only be 1 row)
    suspend fun setGenderInfo(gender: String, genderTimestamp: Long)

    //gets all birthday info across all rows (there should only be 1 row)
    suspend fun getBirthdayInfo(): BirthdayHolder?

    //sets all birthday info across all rows (there should only be 1 row)
    suspend fun setBirthdayInfo(
        birthYear: Int, birthMonth: Int, birthDayOfMonth: Int,
        birthdayTimestamp: Long, age: Int
    )

    //sets categories across all rows (there should only be 1 row)
    suspend fun setCategories(categories: MutableList<CategoryTimeFrame.CategoryActivityMessage>)

    //gets categories and age if not found
    suspend fun getCategoriesAndAge(transactionWrapper: TransactionWrapper): ReturnUserSelectedCategoriesAndAgeDataHolder

    //sets all category info across all rows (there should only be 1 row)
    suspend fun setCategoryInfo(categories: String, categoriesTimestamp: Long)

    //sets all category info across all rows (there should only be 1 row)
    suspend fun setCategoryInfo(
        categories: List<CategoryTimeFrame.CategoryActivityMessage>,
        categoriesTimestamp: Long
    )

    //sets user bio across all rows (there should only be 1 row)
    suspend fun setUserBio(userBio: String)

    //sets user city across all rows (there should only be 1 row)
    suspend fun setUserCity(userCity: String)

    //sets gender range across all rows (there should only be 1 row)
    suspend fun setGenderRange(genderRange: String)

    //sets user age range across all rows (there should only be 1 row)
    suspend fun setUserAgeRange(userMinAge: Int, userMaxAge: Int)

    //sets max distance across all rows (there should only be 1 row)
    suspend fun setMaxDistance(maxDistance: Int)

    //sets post login timestamp across all rows (there should only be 1 row)
    suspend fun setPostLoginTimestamp(postLoginTimestamp: Long)

    //sets post login info across all rows (there should only be 1 row)
    suspend fun setPostLoginInfo(
        userBio: String, userCity: String, genderRange: ArrayList<String>,
        userMinAge: Int, userMaxAge: Int, maxDistance: Int,
        postLoginTimestamp: Long
    )

    //gets all blocked accounts
    suspend fun getBlockedAccounts(): MutableSet<String>

    //sets the blocked accounts to the database
    suspend fun setBlockedAccounts(blockedAccounts: MutableSet<String>)

    //sets chatRoomSortMethodSelected across all rows (there should only be 1 row)
    suspend fun setChatRoomSortMethodSelected(chatRoomSortMethodSelected: ChatRoomSortMethodSelected)

    //adds the blocked account to the 'list' inside the database
    /** transactionWrapper requires AccountsDatabase to be locked **/
    suspend fun addBlockedAccount(blockedAccount: String, transactionWrapper: TransactionWrapper)

    //removed the blocked account from the 'list' inside the database
    /** transactionWrapper requires AccountsDatabase to be locked **/
    suspend fun removeBlockedAccount(blockedAccount: String, transactionWrapper: TransactionWrapper)

    //gets all info the modify profile function requires null if values are not found
    /** transactionWrapper requires AccountsDatabase to be locked **/
    suspend fun getApplicationAccountInfo(transactionWrapper: TransactionWrapper): ApplicationAccountInfo?

}