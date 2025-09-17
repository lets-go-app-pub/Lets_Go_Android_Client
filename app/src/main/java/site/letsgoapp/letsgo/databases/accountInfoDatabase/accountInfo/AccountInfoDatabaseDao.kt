package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import site.letsgoapp.letsgo.utilities.BirthdayHolder
import site.letsgoapp.letsgo.utilities.CategoriesAgeObj

//NOTE: Only 1 row should exist at any time of this table
//NOTE: can add WHERE phoneNumber=:phoneNumber to set queries to be explicit but need another parameter
@Dao
interface AccountInfoDatabaseDao {

    //insert single account
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(accountInfoDataEntity: AccountInfoDataEntity)

    //delete all
    @Query("DELETE FROM account_info_table")
    suspend fun clearTable()

    //delete all rows except the passed phoneNumber
    @Query("DELETE FROM account_info_table WHERE phoneNumber NOT IN(:phoneNumber)")
    suspend fun deleteAllButAccount(phoneNumber: String)

    //gets the phone number
    @Query("SELECT phoneNumber FROM account_info_table LIMIT 1")
    suspend fun getPhoneNumber(): String?

    //gets all accounts, used for testing
    @Query("SELECT * FROM account_info_table")
    suspend fun getAll(): List<AccountInfoDataEntity>

    //gets the account of the phone number
    @Query("SELECT * FROM account_info_table WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getAccountInfo(phoneNumber: String): AccountInfoDataEntity?

    //gets the account
    @Query("SELECT * FROM account_info_table LIMIT 1")
    suspend fun getAccountInfoForErrors(): AccountInfoDataEntity?

    //gets account type returns null if not found
    @Query("SELECT phoneNumber, account_type FROM account_info_table LIMIT 1")
    suspend fun getAccountTypeAndLoginInfo(): InitialLoginInfo?

    //sets info for login if outdated
    @Query(
        """
        UPDATE account_info_table SET
        
            algorithm_search_options = :algorithmSearchOptions,
        
            birth_year = CASE WHEN birthday_timestamp < :birthdayTimestamp THEN :birthYear ELSE birth_year END,
            birth_month = CASE WHEN birthday_timestamp < :birthdayTimestamp THEN :birthMonth ELSE birth_month END,
            birth_day_of_month = CASE WHEN birthday_timestamp < :birthdayTimestamp THEN :birthDayOfMonth ELSE birth_day_of_month END,
            birthday_timestamp = CASE WHEN birthday_timestamp < :birthdayTimestamp THEN :birthdayTimestamp ELSE birthday_timestamp END,
            age = :age,
            
            email_address = CASE WHEN email_timestamp < :emailTimestamp THEN :emailAddress ELSE email_address END,
            requires_email_address_verification = CASE WHEN email_timestamp < :emailTimestamp THEN :requiresEmailAddressVerification ELSE requires_email_address_verification END,
            email_timestamp = CASE WHEN email_timestamp < :emailTimestamp THEN :emailTimestamp ELSE email_timestamp END,
            
            gender = CASE WHEN gender_timestamp < :genderTimestamp THEN :gender ELSE gender END,
            gender_timestamp = CASE WHEN gender_timestamp < :genderTimestamp THEN :genderTimestamp ELSE gender_timestamp END,

            first_name = CASE WHEN first_name_timestamp < :firstNameTimestamp THEN :firstName ELSE first_name END,
            first_name_timestamp = CASE WHEN first_name_timestamp < :firstNameTimestamp THEN :firstNameTimestamp ELSE first_name_timestamp END,
            
            user_bio = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :userBio ELSE user_bio END,
            user_city = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :userCity ELSE user_city END,
            user_gender_range = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :genderRange ELSE user_gender_range END,
            match_parameters_min_age = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :userMinAge ELSE match_parameters_min_age END,
            match_parameters_max_age = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :userMaxAge ELSE match_parameters_max_age END,
            match_parameters_max_distance = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :maxDistance ELSE match_parameters_max_distance END,
            post_login_timestamp = CASE WHEN post_login_timestamp < :postLoginTimestamp THEN :postLoginTimestamp ELSE post_login_timestamp END,
            
            categories = CASE WHEN categories_timestamp < :categoriesTimestamp THEN :categories ELSE categories END,
            categories_timestamp = CASE WHEN categories_timestamp < :categoriesTimestamp THEN :categoriesTimestamp ELSE categories_timestamp END,
            
            subscription_status = :subscriptionStatus,
            subscription_expiration_time = :subscriptionExpirationTime,
            
            opted_in_to_promotional_emails = :optedInToPromotionalEmails
            
        """
    )
    suspend fun setInfoFromRunningLogin(

        algorithmSearchOptions: Int,

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

        subscriptionStatus: Int,
        subscriptionExpirationTime: Long,

        optedInToPromotionalEmails: Boolean
    )

    //sets account type across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET account_type = :accountType")
    suspend fun setInfoForLoginFunction(accountType: Int)

    //gets email address returns null if not found
    @Query("SELECT email_address FROM account_info_table LIMIT 1")
    suspend fun getEmail(): String?

    //sets all algorithm search options info across all rows (there should only be 1 row)
    @Query(
        """
        UPDATE account_info_table SET algorithm_search_options = :algorithmSearchOptions
    """
    )
    suspend fun setAlgorithmMatchOptions(
        algorithmSearchOptions: Int
    )

    @Query(
        """
        UPDATE account_info_table SET opted_in_to_promotional_emails = :optedInToPromotionalEmails
    """
    )
    suspend fun setOptedInToPromotionalEmail(
        optedInToPromotionalEmails: Boolean
    )

    //sets all first name info across all rows (there should only be 1 row)
    @Query(
        """
        UPDATE account_info_table SET
            email_address = :emailAddress,
            requires_email_address_verification = :requiresEmailAddressVerification,
            email_timestamp = :emailAddressTimestamp
    """
    )
    suspend fun setEmailInfo(
        emailAddress: String,
        requiresEmailAddressVerification: Boolean,
        emailAddressTimestamp: Long,
    )

    //sets all requiresEmailAddressVerification (there should only be 1 row)
    @Query(
        """
        UPDATE account_info_table SET
            requires_email_address_verification = :requiresEmailAddressVerification
    """
    )
    suspend fun setRequiresEmailAddressVerification(requiresEmailAddressVerification: Boolean)

    //gets first name and first name timestamp
    @Query("SELECT first_name, first_name_timestamp FROM account_info_table LIMIT 1")
    suspend fun getFirstNameInfo(): FirstNameDataHolder?

    //sets all first name info across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET first_name = :firstName, first_name_timestamp = :firstNameTimestamp")
    suspend fun setFirstNameInfo(firstName: String, firstNameTimestamp: Long)

    //gets gender and gender timestamp
    @Query("SELECT gender, gender_timestamp FROM account_info_table LIMIT 1")
    suspend fun getGenderInfo(): GenderDataHolder?

    //sets all gender info across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET gender = :gender, gender_timestamp = :genderTimestamp")
    suspend fun setGenderInfo(gender: String, genderTimestamp: Long)

    //gets birth_year returns null if not found
    @Query("SELECT birth_year, birth_month, birth_day_of_month, birthday_timestamp FROM account_info_table LIMIT 1")
    suspend fun getBirthDayInfo(): BirthdayHolder?

    //sets all birthday info across all rows (there should only be 1 row)
    @Query(
        """
        UPDATE account_info_table SET
            birth_year = :birthYear,
            birth_month = :birthMonth,
            birth_day_of_month = :birthDayOfMonth,
            birthday_timestamp = :birthdayTimestamp,
            age = :age
    """
    )
    suspend fun setBirthdayInfo(
        birthYear: Int, birthMonth: Int, birthDayOfMonth: Int,
        birthdayTimestamp: Long, age: Int,
    )

    //sets categories across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET categories = :categories")
    suspend fun setCategories(categories: String)

    //gets categories and age info across all rows (there should only be 1 row)
    @Query("SELECT categories, age FROM account_info_table LIMIT 1")
    suspend fun getCategoriesAndAge(): CategoriesAgeObj?

    //sets all categories info across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET categories = :categories, categories_timestamp = :categoriesTimestamp")
    suspend fun setCategoryInfo(categories: String, categoriesTimestamp: Long)

    //sets user bio across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET user_bio = :userBio")
    suspend fun setUserBio(userBio: String)

    //sets userCity across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET user_city = :userCity")
    suspend fun setUserCity(userCity: String)

    //sets genderRange across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET user_gender_range = :genderRange")
    suspend fun setGenderRange(genderRange: String)

    @Query("UPDATE account_info_table SET match_parameters_min_age = :userMinAge, match_parameters_max_age = :userMaxAge")
    suspend fun setUserAgeRange(userMinAge: Int, userMaxAge: Int)

    //sets maxDistance across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET match_parameters_max_distance = :maxDistance")
    suspend fun setMaxDistance(maxDistance: Int)

    //sets postLoginTimestamp across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET post_login_timestamp = :postLoginTimestamp")
    suspend fun setPostLoginTimestamp(postLoginTimestamp: Long)

    //sets post login info across all rows (there should only be 1 row)
    @Query(
        """
        UPDATE account_info_table SET
            user_bio = :userBio,
            user_city = :userCity,
            user_gender_range = :genderRange,
            match_parameters_min_age = :userMinAge,
            match_parameters_max_age = :userMaxAge,
            match_parameters_max_distance = :maxDistance,
            post_login_timestamp = :postLoginTimestamp
    """
    )
    suspend fun setPostLoginInfo(
        userBio: String,
        userCity: String,
        genderRange: String,
        userMinAge: Int,
        userMaxAge: Int,
        maxDistance: Int,
        postLoginTimestamp: Long,
    )

    //gets blockedAccounts 'list' in string format, returns null if not found
    @Query("SELECT blocked_accounts FROM account_info_table LIMIT 1")
    suspend fun getBlockedAccounts(): String?

    //sets blockedAccounts across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET blocked_accounts = :blockedAccounts")
    suspend fun setBlockedAccounts(blockedAccounts: String)

    //sets chatRoomSortMethodSelected across all rows (there should only be 1 row)
    @Query("UPDATE account_info_table SET chat_room_sort_method_selected = :chatRoomSortMethodSelected")
    suspend fun setChatRoomSortMethodSelected(chatRoomSortMethodSelected: Int)

    //gets info for application across all rows (there should only be 1 row)
    @Query(
        """
        SELECT
            algorithm_search_options,
        
            first_name,
            first_name_timestamp,
            
            categories,
            categories_timestamp,
            
            gender,
            gender_timestamp,
            
            email_address,
            requires_email_address_verification,
            email_timestamp,
            
            user_bio,
            user_city,
            match_parameters_min_age,
            match_parameters_max_age,
            match_parameters_max_distance,
            user_gender_range,
            post_login_timestamp,
            
            age,
            chat_room_sort_method_selected,
            
            subscription_status,
            opted_in_to_promotional_emails
        FROM account_info_table LIMIT 1
    """
    )
    suspend fun getApplicationAccountInfo(): ApplicationAccountInfoExtractFromDaoDataClass?

}

data class InitialLoginInfo(val phoneNumber: String, val account_type: Int)
//data class AccountTypeAndMandatoryInfoBool(val account_type: Int?, val mandatory_info_collected: Boolean?)