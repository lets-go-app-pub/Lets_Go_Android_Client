package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChatRoomSortMethodSelected {

    //puts messages that have not been read first and other messages second, then sorts chat rooms by chatRoomLastActivityTime in
    // descending order; chatRoomLastActivityTime changes regularly, every time a message of some kind comes from the chat room
    SORT_BY_UNREAD,

    //sorts chat rooms by chatRoomLastObservedTime in descending order; chatRoomLastObservedTime should never change on the
    // chatRoomsListFragment, this is because even though a delay is possible in an update from the server from a OTHER_USER_OBSERVED
    // when chatRoomUpdated is called the observed time will be updated and so this chat room will be put at the top of the list, in a
    // VERY rare case chat room 1 and chat room 2 could both be running updateChatRoom and so the observed times could be a little wrong
    // however the worst that will happen is two chat rooms will be switched places
    SORT_BY_VISITED,

    //sorts chat rooms by chatRoomLastActivityTime in descending order; chatRoomLastActivityTime changes regularly, every
    // time a message of some kind comes from the chat room
    //NOTE: This is NOT the same as SORT_BY_UNREAD. If the current user was the most recent
    // activity for example, then the order will be different.
    SORT_BY_RECENT,

    //sorts chat rooms by timeJoined in ascending order; timeJoined never changes
    SORT_BY_JOINED;

    companion object {

        fun setVal(value: Int): ChatRoomSortMethodSelected {
            return when (value) {
                0 -> {
                    SORT_BY_UNREAD
                }
                1 -> {
                    SORT_BY_VISITED
                }
                2 -> {
                    SORT_BY_RECENT
                }
                3 -> {
                    SORT_BY_JOINED
                }
                else -> {
                    SORT_BY_RECENT
                }
            }
        }
    }
}

//NOTE: Only 1 row should exist at any time of this table
@Entity(tableName = "account_info_table")
data class AccountInfoDataEntity(

    @PrimaryKey(autoGenerate = false)
    var phoneNumber: String,

    //currently logged in accounts primary oid
    @ColumnInfo(name = "account_oid")
    var accountOID: String,

    //currently logged in account type
    //follows AccountLoginType enum inside AccountLoginTypeEnum.proto
    @ColumnInfo(name = "account_type")
    var accountType: Int,

    //currently logged in account type
    //follows AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions enum in AlgorithmSearchOptions.proto
    @ColumnInfo(name = "algorithm_search_options")
    var algorithmSearchOptions: Int,

    @ColumnInfo(name = "requires_email_address_verification")
    var requiresEmailAddressVerification: Boolean,

    @ColumnInfo(name = "email_address")
    var emailAddress: String,

    @ColumnInfo(name = "email_timestamp")
    var emailTimestamp: Long,

    @ColumnInfo(name = "first_name")
    var firstName: String,

    @ColumnInfo(name = "first_name_timestamp")
    var firstNameTimestamp: Long,

    @ColumnInfo(name = "gender")
    var gender: String,

    @ColumnInfo(name = "gender_timestamp")
    var genderTimestamp: Long,

    //stored directly from the user, so values range from ~1901 -> present (120 years is max age)
    @ColumnInfo(name = "birth_year")
    var birthYear: Int,

    //stored directly from the user, so values range from ~1901 -> present (120 years is max age)
    @ColumnInfo(name = "birth_month")
    var birthMonth: Int,

    @ColumnInfo(name = "birth_day_of_month")
    var birthDayOfMonth: Int,

    @ColumnInfo(name = "age")
    var age: Int,

    @ColumnInfo(name = "birthday_timestamp")
    var birthdayTimestamp: Long,

    @ColumnInfo(name = "categories")
    var categories: String,

    @ColumnInfo(name = "categories_timestamp")
    var categoriesTimestamp: Long,

    @ColumnInfo(name = "user_bio")
    var userBio: String,

    @ColumnInfo(name = "user_city")
    var userCity: String,

    @ColumnInfo(name = "user_gender_range")
    var userGenderRange: String,

    @ColumnInfo(name = "match_parameters_min_age")
    var minAge: Int,

    @ColumnInfo(name = "match_parameters_max_age")
    var maxAge: Int,

    @ColumnInfo(name = "match_parameters_max_distance")
    var maxDistance: Int,

    @ColumnInfo(name = "post_login_timestamp")
    var postLoginTimestamp: Long,

    //uses convertBlockedAccountsMapToString() and convertStringToBlockedAccountsMap()
    @ColumnInfo(name = "blocked_accounts")
    var blockedAccounts: String,

    //follows ChatRoomSortMethodSelected enum, default value is SORT_BY_UNREAD
    @ColumnInfo(name = "chat_room_sort_method_selected")
    var chatRoomSortMethodSelected: Int,

    //follows UserSubscriptionStatus enum
    @ColumnInfo(name = "subscription_status")
    var subscriptionStatus: Int,

    @ColumnInfo(name = "subscription_expiration_time")
    var subscriptionExpirationTime: Long,

    @ColumnInfo(name = "opted_in_to_promotional_emails")
    var optedInToPromotionalEmails: Boolean,
) {

    override fun toString(): String {
        return "~Account Info Data Entity Values~\n" +
                "phoneNumber: $phoneNumber\n" +
                "accountOID: $accountOID\n" +
                "accountType: $accountType\n" +
                "requiresEmailAddressVerification: $requiresEmailAddressVerification]\n" +
                "emailAddress: $emailAddress\n" +
                "emailTimestamp: $emailTimestamp\n" +
                "firstName: $firstName\n" +
                "firstNameTimestamp: $firstNameTimestamp\n" +
                "gender: $gender\n" +
                "genderTimestamp: $genderTimestamp\n" +
                "birthYear: $birthYear\n" +
                "birthMonth: $birthMonth\n" +
                "birthDayOfMonth: $birthDayOfMonth\n" +
                "age: $age\n" +
                "birthdayTimestamp: $birthdayTimestamp\n" +
                "categories: $categories\n" +
                "categoriesTimestamp: $categoriesTimestamp\n" +
                "userBio: $userBio\n" +
                "userCity: $userCity\n" +
                "userGenderRange: $userGenderRange\n" +
                "minAge: $minAge\n" +
                "maxAge: $maxAge\n" +
                "maxDistance: $maxDistance\n" +
                "postLoginTimestamp: $postLoginTimestamp\n" +
                "blockedAccounts: $blockedAccounts\n" +
                "chatRoomSortMethodSelected: $chatRoomSortMethodSelected\n" +
                "subscriptionStatus: $subscriptionStatus\n" +
                "subscriptionExpirationTime: $subscriptionExpirationTime\n" +
                "optedInToPromotionalEmails: $optedInToPromotionalEmails\n"
    }
}

const val BLOCKED_ACCOUNT_OID_SEPARATOR = ","

fun convertBlockedAccountsSetToString(blockedAccounts: MutableSet<String>): String {

    var returnString = ""

    for (accountOID in blockedAccounts) {
        returnString += accountOID + BLOCKED_ACCOUNT_OID_SEPARATOR
    }

    return returnString
}

fun convertStringToBlockedAccountsMap(blockedAccounts: String): MutableSet<String> {

    val splitString = Regex(BLOCKED_ACCOUNT_OID_SEPARATOR)

    return blockedAccounts.split(splitString).filter {
        it.isNotEmpty()
    }.toMutableSet()

//    return blockedAccounts.split(splitString).filter {
//        it.isNotEmpty()
//    }.map {
//        it to Unit
//    }.toMap().toMutableMap()
}