package site.letsgoapp.letsgo.utilities

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowMetrics
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.multidex.MultiDexApplication
import androidx.room.withTransaction
import error_origin_enum.ErrorOriginEnum
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.Channel
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.accountInfoDatabase.AccountInfoDatabase
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.iconsDatabase.IconsDatabase
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.MessagesDatabase
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.OtherUsersDatabase
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.workers.error_handling.HandleErrorsClass
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


//Used In Repository as a way of returning data through LiveData
//NOTE: on EXPIRED I want them to go back to the main fragment and have to login again so they get logged out on occasion and recheck credentials
//NOTE: errors are handled for these in repository so line numbers have more meaning
//enum class ErrorStatusEnum {
//    NO_ERRORS, //{fragment specific}
//    DO_NOTHING, //will not return the value to the fragment through live data
//    CONNECTION_ERROR, //used for CONNECTION_ERROR,LG_ERROR and UNKNOWN {will display connection error}
//    LOGGED_IN_ELSEWHERE, //used for LOGGED_IN_ELSEWHERE {will tell them logged in elsewhere and send them back to login screen}
//    BACK_TO_LOGIN_SCREEN, //used for EXPIRED, INVALID_LOGIN_TOKEN, LOGGED_ACCOUNT_NOT_FOUND, {sends them back to login screen without any errors, used for expiration related errors}
//    UNMANAGEABLE_ERROR; //used for OUTDATED_VERSION, NO_VERIFIED_ACCOUNT, ANDROID_SIDE_ERROR and NOT_ENOUGH_INFO {displays an error and sends them back to login screen}
//}

const val GENDER_RANGE_DELIMITER = ","

//Used In Repository as a way of returning data through LiveData
enum class GrpcFunctionErrorStatusEnum {
    NO_ERRORS,
    DO_NOTHING,
    CONNECTION_ERROR,
    LOGGED_IN_ELSEWHERE,
    LOGIN_TOKEN_EXPIRED_OR_INVALID,
    FUNCTION_CALLED_TOO_QUICKLY,
    CLEAR_DATABASE_INFO,
    LOG_USER_OUT,
    SERVER_DOWN,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED,
    NO_SUBSCRIPTION;
}

data class GrpcFunctionErrorStatusReturnValues(
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val turnOffLoading: Boolean
)

enum class SetFieldReturnValues {
    SUCCESSFUL,
    INVALID_VALUE,
    SERVER_ERROR
}

enum class ModifyPropertiesSetEmailState {
    REQUIRES_VERIFICATION,
    VERIFIED,
    LOADING
}

data class SetProtoRpcReturnValue(
    val errorMessage: String,
    val invalidParameterPassed: Boolean,
    val updatedTimestamp: Long,
    val errorStatus: GrpcFunctionErrorStatusEnum
)

data class SetFieldsReturnValues(
    val invalidParameterPassed: Boolean,
    val updatedTimestamp: Long,
    val errorStatus: GrpcFunctionErrorStatusEnum
) {
    constructor(errorStatus: GrpcFunctionErrorStatusEnum, invalidParameterPassed: Boolean) : this(
        invalidParameterPassed,
        -1,
        errorStatus
    )
}

data class ReturnSinglePictureValues(
    val index: Int,
    val pictureDataEntity: AccountPictureDataEntity?
)

data class SetPictureReturnDataHolder(
    val invalidParameterPassed: Boolean,
    val updatedTimestamp: Long,
    val errorStatus: GrpcFunctionErrorStatusEnum,
    val pictureIndex: Int,
    val picturePath: String,
    val pictureSize: Int
)

//used to determine the dialog the cancel button from StoredIconDataDialog opens
enum class StartStopChooserDialogCallerEnum {
    UNKNOWN,
    SELECT_CHOICE,
    STORED_ICON_LOCATION
}

data class AgeRangeHolder(val minAgeRange: Int, val maxAgeRange: Int)

data class BirthdayHolder(
    val birth_year: Int,
    val birth_month: Int,
    val birth_day_of_month: Int,
    val birthday_timestamp: Long,
)

data class CategoriesAgeObj(val categories: String?, val age: Int?)

data class SaveBirthdayReturnValues(val successful: Boolean, val age: Int)

//NOTE: there is a flow chart inside the documentation for how the location is set up
enum class TypeOfLocationUpdate {
    NEED_VERIFIED_ACCOUNT_REQUEST,
    ALGORITHM_REQUEST,
    CHAT_LOCATION_REQUEST,
    PINNED_LOCATION_REQUEST,
    OTHER; //used for onActivityResult when the request code is something besides the 3 values

    companion object {

        fun setVal(number: Int): TypeOfLocationUpdate {
            return when (number) {
                1 -> {
                    ALGORITHM_REQUEST
                }
                2 -> {
                    CHAT_LOCATION_REQUEST
                }
                3 -> {
                    PINNED_LOCATION_REQUEST
                }
                0 -> {
                    NEED_VERIFIED_ACCOUNT_REQUEST
                }
                else -> {
                    OTHER
                }
            }
        }
    }
}

enum class LocationReturnErrorStatus {
    SUCCESSFUL,
    ACTION_LOCATION_SOURCE_SETTINGS_NOT_FOUND,
    ERROR_REQUESTING_PERMISSIONS,
    GPS_LOCATION_DENIED,
    GPS_LOCATION_RETURNED_OFF,
    LOCATION_PERMISSIONS_DENIED,
    FAILED_TO_RETRIEVE_LOCATION
}

data class LastUpdatedLocationInfo(
    @Volatile
    var longitude: Double,
    @Volatile
    var latitude: Double,
    @Volatile
    var subAdminArea: String,
    @Volatile
    var lastTimeLocationReceived: Long, //time in ms
    @Volatile
    var initialized: Boolean = true,
)

//TESTING_NOTE: Make sure that
// 1) The client can properly download icons/activities/categories that were UPDATED
// 2) The client can properly download icons/activities/categories that are NEW
/** See also [IconsDataEntity.iconBasicResourceEntryName] **/
//This maps the icon name stored inside the database to the resource id. This must
// be done here because resource ids can be dynamic between devices and between
// updates.
val iconsMapTable = mapOf(
    //Category: Unknown
    //Index: 0
    Pair("a_icon_unknown_unknown", R.drawable.a_icon_unknown_unknown),

    //Category: Pick Up Sports
    //Index: [1-8]
    Pair("a_icon_pick_up_sports_baseball", R.drawable.a_icon_pick_up_sports_baseball),
    Pair("a_icon_pick_up_sports_basketball", R.drawable.a_icon_pick_up_sports_basketball),
    Pair("a_icon_pick_up_sports_football", R.drawable.a_icon_pick_up_sports_football),
    Pair("a_icon_pick_up_sports_golf", R.drawable.a_icon_pick_up_sports_golf),
    Pair("a_icon_pick_up_sports_hockey", R.drawable.a_icon_pick_up_sports_hockey),
    Pair("a_icon_pick_up_sports_soccer", R.drawable.a_icon_pick_up_sports_soccer),
    Pair(
        "a_icon_pick_up_sports_ultimate_frisbee",
        R.drawable.a_icon_pick_up_sports_ultimate_frisbee
    ),
    Pair("a_icon_pick_up_sports_volleyball", R.drawable.a_icon_pick_up_sports_volleyball),

    //Category: Working Out
    //Index: [9-17]
    Pair("a_icon_working_out_crossfit", R.drawable.a_icon_working_out_crossfit),
    Pair("a_icon_working_out_cycling", R.drawable.a_icon_working_out_cycling),
    Pair("a_icon_working_out_dance", R.drawable.a_icon_working_out_dance),
    Pair("a_icon_working_out_mma", R.drawable.a_icon_working_out_mma),
    Pair("a_icon_working_out_pilates", R.drawable.a_icon_working_out_pilates),
    Pair("a_icon_working_out_running", R.drawable.a_icon_working_out_running),
    Pair("a_icon_working_out_swimming", R.drawable.a_icon_working_out_swimming),
    Pair("a_icon_working_out_weight_training", R.drawable.a_icon_working_out_weight_training),
    Pair("a_icon_working_out_yoga", R.drawable.a_icon_working_out_yoga),

    //Category: Working Out
    //Index: [18-26]
    Pair("a_icon_food_american", R.drawable.a_icon_food_american),
    Pair("a_icon_food_brunch", R.drawable.a_icon_food_brunch),
    Pair("a_icon_food_chinese", R.drawable.a_icon_food_chinese),
    Pair("a_icon_food_indian", R.drawable.a_icon_food_indian),
    Pair("a_icon_food_italian", R.drawable.a_icon_food_italian),
    Pair("a_icon_food_mexican", R.drawable.a_icon_food_mexican),
    Pair("a_icon_food_pho", R.drawable.a_icon_food_pho),
    Pair("a_icon_food_sushi", R.drawable.a_icon_food_sushi),
    Pair("a_icon_food_thai", R.drawable.a_icon_food_thai),

    //Category: Adventures
    //Index: [27-36]
    Pair("a_icon_adventures_band_jam_session", R.drawable.a_icon_adventures_band_jam_session),
    Pair("a_icon_adventures_bar_hopping", R.drawable.a_icon_adventures_bar_hopping),
    Pair("a_icon_adventures_boating", R.drawable.a_icon_adventures_boating),
    Pair("a_icon_adventures_clubbing", R.drawable.a_icon_adventures_clubbing),
    Pair("a_icon_adventures_dancing", R.drawable.a_icon_adventures_dancing),
    Pair("a_icon_adventures_hiking", R.drawable.a_icon_adventures_hiking),
    Pair("a_icon_adventures_shopping", R.drawable.a_icon_adventures_shopping),
    Pair("a_icon_adventures_skiing", R.drawable.a_icon_adventures_skiing),
    Pair("a_icon_adventures_snowboarding", R.drawable.a_icon_adventures_snowboarding),
    Pair("a_icon_adventures_water_activities", R.drawable.a_icon_adventures_water_activities),

    //Category: Social Drinking & Tastings
    //Index: [37-39]
    Pair(
        "a_icon_social_drinking_and_tastings_beer",
        R.drawable.a_icon_social_drinking_and_tastings_beer
    ),
    Pair(
        "a_icon_social_drinking_and_tastings_spirits",
        R.drawable.a_icon_social_drinking_and_tastings_spirits
    ),
    Pair(
        "a_icon_social_drinking_and_tastings_wine",
        R.drawable.a_icon_social_drinking_and_tastings_wine
    ),

    //Category: Sporting Entertainment
    //Index: [40-41]
    Pair("a_icon_sporting_entertainment_boxing", R.drawable.a_icon_sporting_entertainment_boxing),
    Pair("a_icon_sporting_entertainment_tennis", R.drawable.a_icon_sporting_entertainment_tennis),

    //Category: Beauty
    //Index: [42-45]
    Pair("a_icon_beauty_hair", R.drawable.a_icon_beauty_hair),
    Pair("a_icon_beauty_makeup", R.drawable.a_icon_beauty_makeup),
    Pair("a_icon_beauty_mani_pedi", R.drawable.a_icon_beauty_mani_pedi),
    Pair("a_icon_beauty_skin_care", R.drawable.a_icon_beauty_skin_care),

    //Category: Play Dates
    //Index: [46-51]
    Pair("a_icon_play_dates_board_games", R.drawable.a_icon_play_dates_board_games),
    Pair("a_icon_play_dates_crafts", R.drawable.a_icon_play_dates_crafts),
    Pair("a_icon_play_dates_fun_in_the_kitchen", R.drawable.a_icon_play_dates_fun_in_the_kitchen),
    Pair("a_icon_play_dates_movies", R.drawable.a_icon_play_dates_movies),
    Pair("a_icon_play_dates_outings", R.drawable.a_icon_play_dates_outings),
    Pair("a_icon_play_dates_park_day", R.drawable.a_icon_play_dates_park_day),

    //Category: Concerts
    //Index: [52-60]
    Pair("a_icon_concerts_country", R.drawable.a_icon_concerts_country),
    Pair("a_icon_concerts_edm", R.drawable.a_icon_concerts_edm),
    Pair("a_icon_concerts_hip_hop", R.drawable.a_icon_concerts_hip_hop),
    Pair("a_icon_concerts_indie", R.drawable.a_icon_concerts_indie),
    Pair("a_icon_concerts_jazz", R.drawable.a_icon_concerts_jazz),
    Pair("a_icon_concerts_oldies", R.drawable.a_icon_concerts_oldies),
    Pair("a_icon_concerts_pop", R.drawable.a_icon_concerts_pop),
    Pair("a_icon_concerts_rap", R.drawable.a_icon_concerts_rap),
    Pair("a_icon_concerts_rock", R.drawable.a_icon_concerts_rock),

    //Category: Poker
    //Index: [61-65]
    Pair("a_icon_poker_5_card_draw", R.drawable.a_icon_poker_5_card_draw),
    Pair("a_icon_poker_7_card_stud", R.drawable.a_icon_poker_7_card_stud),
    Pair("a_icon_poker_blind_mans_bluff", R.drawable.a_icon_poker_blind_mans_bluff),
    Pair("a_icon_poker_omaha_hold_em", R.drawable.a_icon_poker_omaha_hold_em),
    Pair("a_icon_poker_texas_hold_em", R.drawable.a_icon_poker_texas_hold_em),

    //Category: Collectable Card Games
    //Index: [66-68]
    Pair(
        "a_icon_collectable_card_games_magic_the_gathering",
        R.drawable.a_icon_collectable_card_games_magic_the_gathering
    ),
    Pair("a_icon_collectable_card_games_pokemon", R.drawable.a_icon_collectable_card_games_pokemon),
    Pair(
        "a_icon_collectable_card_games_yu_gi_oh",
        R.drawable.a_icon_collectable_card_games_yu_gi_oh
    ),

    //Category: Movies And TV
    //Index: [69-73]
    Pair("a_icon_movies_and_tv_action_adventure", R.drawable.a_icon_movies_and_tv_action_adventure),
    Pair("a_icon_movies_and_tv_comedy", R.drawable.a_icon_movies_and_tv_comedy),
    Pair("a_icon_movies_and_tv_drama", R.drawable.a_icon_movies_and_tv_drama),
    Pair("a_icon_movies_and_tv_reality_tv", R.drawable.a_icon_movies_and_tv_reality_tv),
    Pair(
        "a_icon_movies_and_tv_thriller_suspense",
        R.drawable.a_icon_movies_and_tv_thriller_suspense
    ),

    //Category: Personal Development
    //Index: [74-77]
    Pair("a_icon_personal_development_emotional", R.drawable.a_icon_personal_development_emotional),
    Pair(
        "a_icon_personal_development_intellectual",
        R.drawable.a_icon_personal_development_intellectual
    ),
    Pair(
        "a_icon_personal_development_investing_and_finances",
        R.drawable.a_icon_personal_development_investing_and_finances
    ),
    Pair(
        "a_icon_personal_development_relational",
        R.drawable.a_icon_personal_development_relational
    ),

    //Category: Personal Development
    //Index: [78-84]
    Pair("a_icon_video_games_battle_royale", R.drawable.a_icon_video_games_battle_royale),
    Pair("a_icon_video_games_digital_card_games", R.drawable.a_icon_video_games_digital_card_games),
    Pair("a_icon_video_games_fps", R.drawable.a_icon_video_games_fps),
    Pair("a_icon_video_games_mmorpg", R.drawable.a_icon_video_games_mmorpg),
    Pair("a_icon_video_games_moba", R.drawable.a_icon_video_games_moba),
    Pair("a_icon_video_games_racing", R.drawable.a_icon_video_games_racing),
    Pair("a_icon_video_games_sports", R.drawable.a_icon_video_games_sports),

    //Category: DIY
    //Index: [85-88]
    Pair("a_icon_diy_cars", R.drawable.a_icon_diy_cars),
    Pair("a_icon_diy_home_care", R.drawable.a_icon_diy_home_care),
    Pair("a_icon_diy_refurbish_items", R.drawable.a_icon_diy_refurbish_items),
    Pair("a_icon_diy_restore_antiques", R.drawable.a_icon_diy_restore_antiques),

    //Category: Board Games
    //Index: [89-96]
    Pair("a_icon_board_games_catan", R.drawable.a_icon_board_games_catan),
    Pair("a_icon_board_games_checkers", R.drawable.a_icon_board_games_checkers),
    Pair("a_icon_board_games_chess", R.drawable.a_icon_board_games_chess),
    Pair("a_icon_board_games_dominoes", R.drawable.a_icon_board_games_dominoes),
    Pair(
        "a_icon_board_games_dungeons_and_dragons",
        R.drawable.a_icon_board_games_dungeons_and_dragons
    ),
    Pair("a_icon_board_games_monopoly", R.drawable.a_icon_board_games_monopoly),
    Pair("a_icon_board_games_pictionary", R.drawable.a_icon_board_games_pictionary),
    Pair("a_icon_board_games_scrabble", R.drawable.a_icon_board_games_scrabble),

    //Index 97; Category: Pick Up Sports
    Pair("a_icon_pick_up_sports_pickleball", R.drawable.a_icon_pick_up_sports_pickleball),
)

fun getAllPreLoadedIcons(): Pair<Long, ArrayList<String>> {
    val preLoadedIcons: ArrayList<String> = arrayListOf()
    val currentTimestamp = 1677588841000L //Tuesday, February 28, 2023 12:54:01 PM (after pickleball was updated)

    //NOTE: Add all pre loaded icons here so they will be stored in icons database the first time android is loaded.
    // This is also listed on the checklist.
    /** THESE MUST BE STORED IN THE PROPER INDEXED ORDER! **/

    //Category: Unknown
    //Index: 0
    preLoadedIcons.add("a_icon_unknown_unknown")

    //Category: Pick Up Sports
    //Index: [1-8]
    preLoadedIcons.add("a_icon_pick_up_sports_baseball")
    preLoadedIcons.add("a_icon_pick_up_sports_basketball")
    preLoadedIcons.add("a_icon_pick_up_sports_football")
    preLoadedIcons.add("a_icon_pick_up_sports_golf")
    preLoadedIcons.add("a_icon_pick_up_sports_hockey")
    preLoadedIcons.add("a_icon_pick_up_sports_soccer")
    preLoadedIcons.add("a_icon_pick_up_sports_ultimate_frisbee")
    preLoadedIcons.add("a_icon_pick_up_sports_volleyball")

    //Category: Working Out
    //Index: [9-17]
    preLoadedIcons.add("a_icon_working_out_crossfit")
    preLoadedIcons.add("a_icon_working_out_cycling")
    preLoadedIcons.add("a_icon_working_out_dance")
    preLoadedIcons.add("a_icon_working_out_mma")
    preLoadedIcons.add("a_icon_working_out_pilates")
    preLoadedIcons.add("a_icon_working_out_running")
    preLoadedIcons.add("a_icon_working_out_swimming")
    preLoadedIcons.add("a_icon_working_out_weight_training")
    preLoadedIcons.add("a_icon_working_out_yoga")

    //Category: Working Out
    //Index: [18-26]
    preLoadedIcons.add("a_icon_food_american")
    preLoadedIcons.add("a_icon_food_brunch")
    preLoadedIcons.add("a_icon_food_chinese")
    preLoadedIcons.add("a_icon_food_indian")
    preLoadedIcons.add("a_icon_food_italian")
    preLoadedIcons.add("a_icon_food_mexican")
    preLoadedIcons.add("a_icon_food_pho")
    preLoadedIcons.add("a_icon_food_sushi")
    preLoadedIcons.add("a_icon_food_thai")

    //Category: Adventures
    //Index: [27-36]
    preLoadedIcons.add("a_icon_adventures_band_jam_session")
    preLoadedIcons.add("a_icon_adventures_bar_hopping")
    preLoadedIcons.add("a_icon_adventures_boating")
    preLoadedIcons.add("a_icon_adventures_clubbing")
    preLoadedIcons.add("a_icon_adventures_dancing")
    preLoadedIcons.add("a_icon_adventures_hiking")
    preLoadedIcons.add("a_icon_adventures_shopping")
    preLoadedIcons.add("a_icon_adventures_skiing")
    preLoadedIcons.add("a_icon_adventures_snowboarding")
    preLoadedIcons.add("a_icon_adventures_water_activities")

    //Category: Social Drinking & Tastings
    //Index: [37-39]
    preLoadedIcons.add("a_icon_social_drinking_and_tastings_beer")
    preLoadedIcons.add("a_icon_social_drinking_and_tastings_spirits")
    preLoadedIcons.add("a_icon_social_drinking_and_tastings_wine")

    //Category: Sporting Entertainment
    //Index: [40-41]
    preLoadedIcons.add("a_icon_sporting_entertainment_boxing")
    preLoadedIcons.add("a_icon_sporting_entertainment_tennis")

    //Category: Beauty
    //Index: [42-45]
    preLoadedIcons.add("a_icon_beauty_hair")
    preLoadedIcons.add("a_icon_beauty_makeup")
    preLoadedIcons.add("a_icon_beauty_mani_pedi")
    preLoadedIcons.add("a_icon_beauty_skin_care")

    //Category: Play Dates
    //Index: [46-51]
    preLoadedIcons.add("a_icon_play_dates_board_games")
    preLoadedIcons.add("a_icon_play_dates_crafts")
    preLoadedIcons.add("a_icon_play_dates_fun_in_the_kitchen")
    preLoadedIcons.add("a_icon_play_dates_movies")
    preLoadedIcons.add("a_icon_play_dates_outings")
    preLoadedIcons.add("a_icon_play_dates_park_day")

    //Category: Concerts
    //Index: [52-60]
    preLoadedIcons.add("a_icon_concerts_country")
    preLoadedIcons.add("a_icon_concerts_edm")
    preLoadedIcons.add("a_icon_concerts_hip_hop")
    preLoadedIcons.add("a_icon_concerts_indie")
    preLoadedIcons.add("a_icon_concerts_jazz")
    preLoadedIcons.add("a_icon_concerts_oldies")
    preLoadedIcons.add("a_icon_concerts_pop")
    preLoadedIcons.add("a_icon_concerts_rap")
    preLoadedIcons.add("a_icon_concerts_rock")

    //Category: Poker
    //Index: [61-65]
    preLoadedIcons.add("a_icon_poker_5_card_draw")
    preLoadedIcons.add("a_icon_poker_7_card_stud")
    preLoadedIcons.add("a_icon_poker_blind_mans_bluff")
    preLoadedIcons.add("a_icon_poker_omaha_hold_em")
    preLoadedIcons.add("a_icon_poker_texas_hold_em")

    //Category: Collectable Card Games
    //Index: [66-68]
    preLoadedIcons.add("a_icon_collectable_card_games_magic_the_gathering")
    preLoadedIcons.add("a_icon_collectable_card_games_pokemon")
    preLoadedIcons.add("a_icon_collectable_card_games_yu_gi_oh")

    //Category: Movies And TV
    //Index: [69-73]
    preLoadedIcons.add("a_icon_movies_and_tv_action_adventure")
    preLoadedIcons.add("a_icon_movies_and_tv_comedy")
    preLoadedIcons.add("a_icon_movies_and_tv_drama")
    preLoadedIcons.add("a_icon_movies_and_tv_reality_tv")
    preLoadedIcons.add("a_icon_movies_and_tv_thriller_suspense")

    //Category: Personal Development
    //Index: [74-77]
    preLoadedIcons.add("a_icon_personal_development_emotional")
    preLoadedIcons.add("a_icon_personal_development_intellectual")
    preLoadedIcons.add("a_icon_personal_development_investing_and_finances")
    preLoadedIcons.add("a_icon_personal_development_relational")

    //Category: Personal Development
    //Index: [78-84]
    preLoadedIcons.add("a_icon_video_games_battle_royale")
    preLoadedIcons.add("a_icon_video_games_digital_card_games")
    preLoadedIcons.add("a_icon_video_games_fps")
    preLoadedIcons.add("a_icon_video_games_mmorpg")
    preLoadedIcons.add("a_icon_video_games_moba")
    preLoadedIcons.add("a_icon_video_games_racing")
    preLoadedIcons.add("a_icon_video_games_sports")

    //Category: DIY
    //Index: [85-88]
    preLoadedIcons.add("a_icon_diy_cars")
    preLoadedIcons.add("a_icon_diy_home_care")
    preLoadedIcons.add("a_icon_diy_refurbish_items")
    preLoadedIcons.add("a_icon_diy_restore_antiques")

    //Category: Board Games
    //Index: [89-96]
    preLoadedIcons.add("a_icon_board_games_catan")
    preLoadedIcons.add("a_icon_board_games_checkers")
    preLoadedIcons.add("a_icon_board_games_chess")
    preLoadedIcons.add("a_icon_board_games_dominoes")
    preLoadedIcons.add("a_icon_board_games_dungeons_and_dragons")
    preLoadedIcons.add("a_icon_board_games_monopoly")
    preLoadedIcons.add("a_icon_board_games_pictionary")
    preLoadedIcons.add("a_icon_board_games_scrabble")

    //Index 97; Category: Pick Up Sports
    preLoadedIcons.add("a_icon_pick_up_sports_pickleball")

    return Pair(currentTimestamp, preLoadedIcons)
}

//it returns a string formatted to represent the date of the timestamp
fun formatUnixTimeStampToDateString(
    unixTimestampInMillis: Long,
    splitLine: Boolean = false,
    errorStore: StoreErrorsInterface
): String {

    return if (unixTimestampInMillis == -1L) { //if the timeStamp is equal to -1 then it should be a start time that represents now
        "NOW"
    } else {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = unixTimestampInMillis

        val dayOfWeekName = convertDayOfWeekToString(calendar.get(Calendar.DAY_OF_WEEK), errorStore)
        val monthName = convertMonthToThreeCharString(calendar.get(Calendar.MONTH), errorStore)
        val timeString =
            convertTimeToString(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

        //only want 1 space here for this formatting
        val newTimeString = if (timeString.isNotEmpty() && timeString[0] == ' ')
            timeString.subSequence(1, timeString.length)
        else
            timeString

        if (splitLine) {
            "$dayOfWeekName, $monthName ${calendar.get(Calendar.DAY_OF_MONTH)}\n$newTimeString"
        } else {
            "$dayOfWeekName, $monthName ${calendar.get(Calendar.DAY_OF_MONTH)} $newTimeString"
        }
    }

}

//takes in the Calendar.Year Calendar.Month and Calendar.Day_Of_Month and returns a formatted string
fun convertDateToString(year: Int, month: Int, dayOfMonth: Int): String {
    return "${month + 1}/$dayOfMonth/$year"
}

//takes in the Calendar.Day_Of_Week and returns a formatted string
fun convertDayOfWeekToString(
    dayOfWeek: Int,
    errorStore: StoreErrorsInterface
): String {
    return when (dayOfWeek) {
        1 -> {
            "Sun"
        }
        2 -> {
            "Mon"
        }
        3 -> {
            "Tue"
        }
        4 -> {
            "Wed"
        }
        5 -> {
            "Thu"
        }
        6 -> {
            "Fri"
        }
        7 -> {
            "Sat"
        }
        else -> {
            val errorMessage =
                "Invalid day of week number was passed in to be converted to be string.\n" +
                        "dayOfWeek: $dayOfWeek\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            "Err"
        }
    }
}

//takes in the Calendar.Month and returns a formatted string three chars long
fun convertMonthToThreeCharString(
    month: Int,
    errorStore: StoreErrorsInterface
): String {
    return when (month) {
        0 -> {
            "Jan"
        }
        1 -> {
            "Feb"
        }
        2 -> {
            "Mar"
        }
        3 -> {
            "Apr"
        }
        4 -> {
            "May"
        }
        5 -> {
            "Jun"
        }
        6 -> {
            "Jul"
        }
        7 -> {
            "Aug"
        }
        8 -> {
            "Sep"
        }
        9 -> {
            "Oct"
        }
        10 -> {
            "Nov"
        }
        11 -> {
            "Dec"
        }
        12 -> {
            "Und"
        }
        13 -> {
            "Duo"
        }
        else -> {
            val errorMessage =
                "Invalid month number was passed in to be converted to be string.\n" +
                        "month: $month\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            "Err"
        }
    }
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION") getParcelable(key) as? T
    }
}

//takes in the Calendar.Month and Calendar.DayOfMonth returns a formatted string
fun displayDateForChatMessage(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    currentYear: Int = -1,
    currentMonth: Int = -1,
    currentDayOfMonth: Int = -1,
    errorStore: StoreErrorsInterface
): String {

    //if today display "Today"
    return if (year == currentYear && month == currentMonth && dayOfMonth == currentDayOfMonth) {
        "Today"
    } else {
        "${convertMonthToString(month, errorStore)} $dayOfMonth"
    }
}

//takes in the Calendar.Month and returns a formatted string
fun convertMonthToString(
    month: Int,
    errorStore: StoreErrorsInterface
): String {
    return when (month) {
        0 -> {
            "January"
        }
        1 -> {
            "February"
        }
        2 -> {
            "March"
        }
        3 -> {
            "April"
        }
        4 -> {
            "May"
        }
        5 -> {
            "June"
        }
        6 -> {
            "July"
        }
        7 -> {
            "August"
        }
        8 -> {
            "September"
        }
        9 -> {
            "October"
        }
        10 -> {
            "November"
        }
        11 -> {
            "December"
        }
        12 -> {
            "Undecimber"
        }
        13 -> {
            "Duodecimber"
        }
        else -> {

            val errorMessage =
                "Invalid month number was passed in to be converted to be string.\n" +
                        "month: $month\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            "Err"
        }
    }
}

//takes in the Calendar.Hour_Of_Day and Calendar.Minute and returns a formatted string
fun convertTimeToString(hour: Int, minute: Int): String {

    val minuteString = if (minute < 10) {
        "0$minute"
    } else {
        "$minute"
    }

    var hourType = "AM"
    val hourValue = when {
        hour == 0 -> {
            12
        }
        hour == 12 -> {
            hourType = "PM"
            12
        }
        hour > 12 -> {
            hourType = "PM"
            hour - 12
        }
        else -> {
            hour
        }
    }

    val hourString = if (hourValue < 10) {
        " $hourValue"
    } else {
        "$hourValue"
    }

    return "$hourString:$minuteString $hourType"

}

private fun convertStringToGenderRangeError(
    genderRangeString: String
) {
    val errorMessage = "Invalid string saved inside gender range list.\n" +
            "gender range string: $genderRangeString"

    ServiceLocator.globalErrorStore.storeError(
        Thread.currentThread().stackTrace[2].fileName,
        Thread.currentThread().stackTrace[2].lineNumber,
        printStackTraceForErrors(),
        errorMessage
    )
}

fun convertStringToGenderRange(genderRange: String?): ArrayList<String> {

    val genderRangeList = arrayListOf<String>()
    genderRange?.let {
        val currentString = StringBuilder()

        try {
            var i = 0
            while (i < it.length) {
                if (it[i] == ',') { //end of number reached
                    if (currentString.isNotEmpty()) {
                        var numberChars = currentString.toString().toInt()
                        currentString.clear()
                        i++

                        while (numberChars > 0) {
                            currentString.append(it[i])
                            i++
                            numberChars--
                        }

                        genderRangeList.add(currentString.toString())
                        currentString.clear()
                    } else {
                        convertStringToGenderRangeError(it)
                    }
                } else { //calculating number
                    currentString.append(it[i])
                }
                i++
            }
        } catch (e: NumberFormatException) {
            convertStringToGenderRangeError(it)
            genderRangeList.clear()
        } catch (e: IndexOutOfBoundsException) {
            convertStringToGenderRangeError(it)
            genderRangeList.clear()
        }

    }

    return genderRangeList
}

fun convertGenderRangeToString(genderRange: MutableList<String>): String {
    var genderRangeString = ""

    for (gen in genderRange) {
        genderRangeString += "${gen.length}$GENDER_RANGE_DELIMITER$gen$GENDER_RANGE_DELIMITER"
    }

    return genderRangeString
}

//calcPersonAge USE
//using my birthday of 10-23-1986 as an example
//yearOfBirth is simply the year for my birthday it would be 1986
//monthOfBirth is a value 1-12 for my birthday it would be 10
//dayOfBirth is the day of the month so for my birthday it would be 23
fun calcPersonAgeNoError(
    yearOfBirth: Int,
    monthOfBirth: Int,
    dayOfBirth: Int,
    calculateTimestamp: () -> Calendar = { getCalendarFromServerTimestamp() }
): Int {
    val birthDay = Calendar.getInstance()
    val today = calculateTimestamp()

    birthDay.set(yearOfBirth, (monthOfBirth - 1), dayOfBirth)

    var age = today.get(Calendar.YEAR) - birthDay.get(Calendar.YEAR)

    //NOTE: Calendar.DAY_OF_YEAR starts at 1 when the equivalent variable tm->tm_yday in C++ starts
    // at 0, (it isn't relevant with the way things are done here however)
    if (0 > today.get(Calendar.DAY_OF_YEAR) - birthDay.get(Calendar.DAY_OF_YEAR)) {
        age--
    }

    return age
}

//calcPersonAge USE
//using my birthday of 10-23-1986 as an example
//yearOfBirth is simply the year for my birthday it would be 1986
//monthOfBirth is a value 1-12 for my birthday it would be 10
//dayOfBirth is the day of the month so for my birthday it would be 23
fun calcPersonAge(
    applicationContext: Context,
    yearOfBirth: Int,
    monthOfBirth: Int,
    dayOfBirth: Int,
    errorHandling: StoreErrorsInterface
): Int {

    //age value to be returned
    var age = 0

    if (yearOfBirth > 0 && monthOfBirth > 0 && dayOfBirth > 0) {
        age = calcPersonAgeNoError(
            yearOfBirth,
            monthOfBirth,
            dayOfBirth
        )
    } else {
        var errorString = "A birthdate was found to be less than or equal to 0.\n"
        errorString += "Year of Birth: $yearOfBirth\n"
        errorString += "Month of Birth: $monthOfBirth\n"
        errorString += "Day of Birth: $dayOfBirth\n"

        errorHandling.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorString,
            applicationContext
        )
    }

    return if (age < 0) 0 else age
}

//used for verify first name
fun verifyFirstName(firstName: String): Pair<Boolean, String> {

    //NOTE: the edit text itself will take care of max size because it is based on bytes not on number
    // of chars, the edit text will also take care of only alphabet allowed to be entered
    if (firstName.length < 2) {
        return Pair(false, "")
    }

    val returnString = "${firstName[0].uppercaseChar()}${firstName.substring(1).lowercase()}"

    return Pair(true, returnString)
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

@Suppress("unused")
fun Activity.hideKeyboard() {
    hideKeyboard(currentFocus ?: View(this))
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

enum class DeviceIdleOrConnectionDownEnum {
    DEVICE_NETWORK_AVAILABLE,
    DEVICE_NETWORK_UNAVAILABLE,
    DEVICE_IN_LIGHT_DOZE,
    DEVICE_IN_DEEP_DOZE,
}

interface DeviceIdleOrConnectionDownCheckerInterface {
    fun deviceIdleOrConnectionDown(context: Context): DeviceIdleOrConnectionDownEnum
}

class DeviceIdleOrConnectionDownChecker : DeviceIdleOrConnectionDownCheckerInterface {

    private val errorStore: StoreErrorsInterface = ServiceLocator.globalErrorStore

    override fun deviceIdleOrConnectionDown(context: Context): DeviceIdleOrConnectionDownEnum {
        // NOTE: during LightDoze and DeepDoze maintenance these will both return false
        val networkState = when {
            isDeviceIdleMode(context) -> {
                DeviceIdleOrConnectionDownEnum.DEVICE_IN_DEEP_DOZE
            }
            // NOTE: isLightDeviceIdleMode() could fail (look at exceptions inside function) but this is
            // just an optimization for the users sake so the app should continue to work correctly
            isLightDeviceIdleMode(context, errorStore) -> {
                DeviceIdleOrConnectionDownEnum.DEVICE_IN_LIGHT_DOZE
            }
            !Networking().checkNetworkState() -> {
                DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_UNAVAILABLE
            }
            else -> {
                DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE
            }
        }

        Log.i("testingDoze", "raw networkState: $networkState")

        return networkState
    }
}

interface NetworkingInterface {
    fun checkNetworkState(): Boolean
}

class Networking : NetworkingInterface {

    override fun checkNetworkState(): Boolean {
        Log.i("networkConnection", "running checkNetworkState()")
        return connectToGoogle()
    }

    private fun connectToGoogle(): Boolean {
        //NOTE: This will block the thread and it is generally run from a coRoutine as well.
        // A solution might be to use the AsynchronousSocketChannel, however it doesn't seem to
        // be available until API 26 either way.
        return try {
            // Connect to Google DNS to check for connection
            val timeoutMs = 1500
            val socket = Socket()
            //google I believe
            val socketAddress = InetSocketAddress("8.8.8.8", 53)

            socket.connect(socketAddress, timeoutMs)
            socket.close()

            true
        } catch (e: IOException) {
            false
        }
    }

}

fun createAgeRangeDisplayString(leftValue: Float, rightValue: Float): String {
    return if (rightValue < GlobalValues.server_imported_values.highestDisplayedAge) {
        "${leftValue.roundToInt()} - ${rightValue.roundToInt()}"
    } else {
        "${leftValue.roundToInt()} - ${rightValue.roundToInt()}+"
    }
}

interface StoreErrorsInterface {
    //called from the app to store errors
    //NOTE: For fragments this is a useful default value for applicationContext because there is a chance that they
    // could be called after being detached from the activity. However, workers for example will still need to provide their
    // own application context.
    fun storeError(
        filename: String,
        lineNumber: Int,
        stackTrace: String,
        errorMessage: String,
        applicationContext: Context = GlobalValues.applicationContext,
        errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel = ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_UNKNOWN
    )
}

class StoreErrors(
    //NOTE: StoreErrors is initialized at ServiceLocator.globalErrorStore. There is
    // no guarantee that ServiceLocator.globalIODispatcher is also initialized so not
    // using it as the default value here.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StoreErrorsInterface {
    //called from the app to store errors
    override fun storeError(
        filename: String,
        lineNumber: Int,
        stackTrace: String,
        errorMessage: String,
        applicationContext: Context,
        errorUrgencyLevel: ErrorOriginEnum.ErrorUrgencyLevel,
    ) {
        Log.i(
            "server_not_fake",
            printStackTraceForErrors()
        )
        CoroutineScope(ioDispatcher).launch {
            //don't need to check for internet connection here because sending it to WorkManager
            val errorsClass = HandleErrorsClass()
            errorsClass.createAndSendErrorMessage(
                applicationContext,
                filename,
                lineNumber,
                stackTrace,
                errorMessage,
                errorUrgencyLevel
            )
        }

//        if (BuildConfig.DEBUG) {
//            Thread.sleep(1000) //sleep so that worker will be stored and sent
//            //NOTE: uncomment line below and fix to-do statement for errors to crash in android
//            TO DO("file: $filename\n lineNum: $lineNumber\n err: $errorMessage\n$stackTrace")
//        }
    }
}

fun generateFragmentInstanceID(fragmentName: String?): String {
    return "$fragmentName ${System.currentTimeMillis()}"
}

//Generate unique fragment ID for the passed fragment instance and set the application view model to know which fragment is currently active
//NOTE: this should be called from onCreateView() and not onCreate(), onCreateView() will not be called when
// the app is minimized or things along those lines which is convenient
fun setupApplicationCurrentFragmentID(
    sharedApplicationViewModel: SharedApplicationViewModel,
    fragmentName: String?,
): String {
    val returnString = generateFragmentInstanceID(fragmentName)
    sharedApplicationViewModel.currentFragmentInstanceId = returnString
    return returnString
}

//Generate unique fragment ID for the passed fragment instance (this will work for the view model instance)
fun buildCurrentFragmentID(fragmentName: String?): String {
    return "$fragmentName ${System.currentTimeMillis()}"
}

fun updateMessageToEditedAndSent(
    messageToUpdate: MessagesDataEntity,
    receivedMessage: MessagesDataEntity,
) {
    Log.i("updateEdited", "response newMessage ${receivedMessage.messageText}")
    messageToUpdate.messageText = receivedMessage.messageText
    messageToUpdate.isEdited = true
    messageToUpdate.editHasBeenSent = true
    messageToUpdate.messageEditedTime = receivedMessage.messageEditedTime
    messageToUpdate.amountOfMessage = receivedMessage.amountOfMessage
    Log.i("followingUpdates", "updateMessageToEditedAndSent()")
    messageToUpdate.hasCompleteInfo = receivedMessage.hasCompleteInfo
}

//this wrapper only allows access to the data that knows the specific string to access it and it can only be accessed once
class EventWrapperWithKeyString<out T>(
    private val content: T,
    private val keyString: String,
    _hasBeenHandled: Boolean = false,
) {

    @Suppress("MemberVisibilityCanBePrivate")
    var hasBeenHandled = _hasBeenHandled
        private set // Allow external read but not write

    fun getContentIfNotHandled(keyString: String): T? {
        return if (hasBeenHandled || keyString != this.keyString) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    @Suppress("unused")
    fun getContentIfNotHandledNoKeyString(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): Pair<T, String> = Pair(content, keyString)

    fun getKeyString(): String = keyString
}

//this wrapper only allows the data to be accessed once
class EventWrapper<out T>(private val content: T, _hasBeenHandled: Boolean = false) {

    @Suppress("MemberVisibilityCanBePrivate")
    var hasBeenHandled = _hasBeenHandled
        private set // Allow external read but not write

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    @Suppress("unused")
    fun peekContent(): T = content

}

inline fun getValueAnimator(
    forward: Boolean = true,
    duration: Long,
    interpolator: TimeInterpolator,
    crossinline updateListener: (progress: Float) -> Unit,
): ValueAnimator {
    val a =
        if (forward) ValueAnimator.ofFloat(0f, 1f)
        else ValueAnimator.ofFloat(1f, 0f)
    a.addUpdateListener {
        updateListener(it.animatedValue as Float)
    }
    a.duration = duration
    a.interpolator = interpolator
    return a
}

fun addStartAndStopTimeToActivityTimes(calendar: Calendar) {
    //This converts to seconds to prevent the long from possibly overflowing the int
    //Add 5 minutes so that the latency between the server and client don't make the timeframe deleted
    calendar.add(
        Calendar.SECOND,
        (GlobalValues.server_imported_values.timeBeforeExpiredTimeMatchWillBeReturned / 1000).toInt() + 5 * 60
    )
}

fun getCalendarFromServerTimestamp(): Calendar {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = getCurrentTimestampInMillis()
    return calendar
}

//this returns the timestamp calculated from the device elapsed real time in milliseconds
//this is NOT made for storing time stamps (if the client ends up with a timestamp behind the server
// then things can be spam requested), it is made for checking categories time for the user and
// expiration times of matches
fun getCurrentTimestampInMillis(): Long {
    return if (checkIfTimestampsWereInitialized()) { //if all values have been set
        GlobalValues.serverTimestampStartTimeMilliseconds + (SystemClock.elapsedRealtime() - GlobalValues.clientElapsedRealTimeStartTimeMilliseconds)
    } else { //if any of the values were not set
        -1L
    }
}

fun checkIfTimestampsWereInitialized(): Boolean {
    return (GlobalValues.serverTimestampStartTimeMilliseconds != -1L
            && GlobalValues.clientElapsedRealTimeStartTimeMilliseconds != -1L
            )
}

//returns the screen width including insets
fun getScreenWidth(activity: Activity): Int {
    return if (Build.VERSION.SDK_INT > 29) {
        val metrics: WindowMetrics = activity.windowManager.currentWindowMetrics
        metrics.bounds.width()
    } else {

        //deprecated in API 30
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        displayMetrics.widthPixels
    }
}

//if the location service is turned off, will show a dialog
fun locationStatusCheck(
    activityContext: Context,
    callBackIfDisabled: () -> Unit,
    callBackIfEnabled: () -> Unit,
) {
    val manager = activityContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { //if location service is off
        callBackIfDisabled()
    } else { //if location service is on
        callBackIfEnabled()
    }
}

fun Geocoder.getLocation(
    latitude: Double,
    longitude: Double,
    listener: (MutableList<Address>) -> Unit,
    errorStore: StoreErrorsInterface
): List<Address> {
    val addresses = mutableListOf<Address>()
    var errorMessage = ""
    var stackTrace = ""
    try {
        if (Build.VERSION.SDK_INT >= 33) {
            getFromLocation(
                latitude,
                longitude,
                1
            ) {
                listener(it)
            }
        } else {
            @Suppress("DEPRECATION")
            addresses.addAll(
                getFromLocation(
                    latitude,
                    longitude,
                    1
                ) ?: emptyList()
            )
            listener(addresses)
        }
    } catch (exception: IllegalArgumentException) {
        errorMessage = "IllegalArgumentException: ${exception.message}"
        stackTrace = exception.stackTraceToString()
    } catch (exception: RuntimeException) {
        errorMessage = "RuntimeException: ${exception.message}"
        stackTrace = exception.stackTraceToString()
    } catch (exception: IOException) {
        //This can happen if the connection is down
        errorMessage = "IOException: ${exception.message}"
        stackTrace = exception.stackTraceToString()
    }

    if (errorMessage.isNotEmpty()) {
        errorMessage += "Failed to get location from geocoder\n" +
                "latitude: $latitude\n" +
                "longitude: $longitude\n" +
                "GlobalValues.lastUpdatedLocationInfo: ${GlobalValues.lastUpdatedLocationInfo}\n"
        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            stackTrace,
            errorMessage,
            GlobalValues.applicationContext
        )
    }

    return addresses
}

//This function will extract the subAdminArea from the location and store it as well as the location
// inside GlobalValues.lastUpdatedLocationInfo. If it fails to extract the subAdminArea, it will simply
// update it to empty.
//GeoCoder.getFromLocation will block the coRoutine here, however Android does not seem to
// offer a coRoutine alternative (make sure it is run on a UI Thread).
suspend fun getAddressFromLocation(
    location: Location,
    geoCoder: Geocoder,
    getAddressComplete: () -> Unit,
    errorStore: StoreErrorsInterface
) {
    geoCoder.getLocation(
        location.latitude,
        location.longitude,
        { addresses ->
            if (addresses.isEmpty()) {
                val errorMessage = "No address found." + "\n" +
                        "location: $location\n" +
                        "GlobalValues.lastUpdatedLocationInfo: ${GlobalValues.lastUpdatedLocationInfo}\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    GlobalValues.applicationContext
                )
            } else {
                // If an address is found, read it into resultMessage
                val address = addresses[0]

                // Fetch the address lines using getAddressLine,
                // join them, and send them to the thread
                for (i in 0..address.maxAddressLineIndex) {
                    address.subAdminArea?.let { area ->
                        GlobalValues.lastUpdatedLocationInfo.subAdminArea = area
                    }
                }
            }
        },
        errorStore
    )

    GlobalValues.lastUpdatedLocationInfo.longitude = location.longitude
    GlobalValues.lastUpdatedLocationInfo.latitude = location.latitude
    GlobalValues.lastUpdatedLocationInfo.lastTimeLocationReceived =
        getCurrentTimestampInMillis()
    GlobalValues.lastUpdatedLocationInfo.initialized = true

    withContext(Main) {
        getAddressComplete()
    }
}

fun resetLastTimeLocationReceived() {
    GlobalValues.lastUpdatedLocationInfo.lastTimeLocationReceived = -1L
}

fun Array<String>.concatenateStrings(separator: String): String {
    return this.reduce { acc, s ->
        if (acc.isEmpty()) s else "$acc$separator$s"
    }
}

fun TextView.clearDrawables() {
    this.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
}

//TESTING_NOTE: make sure to check with API 33 and API 21, this means need to get Google Play on API 21 and
// figure out how to use the camera (and possibly file system) on API 33
fun selectPicturesWithIntent(
    activity: Activity,
    childFragmentManager: FragmentManager,
    createImageFile: () -> File?,
    startActivityIntent: (Intent) -> Unit,
) {

    //The getIntent and fileIntent may be able to be stored above and reused, but because the camera intent holds
    //extra info it would have to be re created every time and there doesn't seem to be much overhead to creating intents
    //so not going to worry about it

    //If the individual image types are passed in, the chooser does not seem to work properly on older APIs.
    // val mimeTypes = arrayOf("images/png", "images/jpg", "images/jpeg")
    val mimeTypes = arrayOf("image/*")

    //photos intent
    val getIntent = Intent(Intent.ACTION_GET_CONTENT)
    getIntent.type = mimeTypes.concatenateStrings("|")

    //file intent
    val fileIntent = Intent(
        Intent.ACTION_PICK,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    )

    //Calling setType after setting URI in Intent constructor will clear the data. So
    // must call setDataAndType instead of setType.
    fileIntent.setDataAndType(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        mimeTypes.concatenateStrings("|")
    )

    fileIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    // Ensure that there's a camera activity to handle the intent
    if (takePictureIntent.resolveActivity(activity.packageManager) != null) {

        // Create the File where the photo should go
        var photoFile: File? = null
        try {
            photoFile = createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(
                GlobalValues.applicationContext,
                R.string.get_pictures_error_loading_camera,
                Toast.LENGTH_SHORT
            ).show()
        }

        // Continue only if the File was successfully created
        if (photoFile != null) {
            val photoURI = FileProvider.getUriForFile(
                activity.applicationContext,
                "site.letsgoapp.letsgo.fileprovider",
                photoFile
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        }
    }

    val intentList = arrayListOf<Intent>()

    if (takePictureIntent.resolveActivity(activity.packageManager) != null) {
        intentList.add(takePictureIntent)
    }

    if (getIntent.resolveActivity(activity.packageManager) != null) {
        intentList.add(getIntent)
    }

    if (fileIntent.resolveActivity(activity.packageManager) != null) {
        intentList.add(fileIntent)
    }

    var properlyLaunchedIntent = true

    if (intentList.isNotEmpty()) {

        val chooserIntent: Intent = Intent.createChooser(intentList[0], "Select Image")
        intentList.removeAt(0)

        if (intentList.isNotEmpty()) {
            chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                intentList.toTypedArray()
            )
        }

        //don't allow for double clicking the items on the intent list
        chooserIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        try {
            startActivityIntent(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            properlyLaunchedIntent = false
        }
    } else { //if intent list is empty
        properlyLaunchedIntent = false
    }

    if (!properlyLaunchedIntent) {
        val alertDialog =
            ErrorAlertDialogFragment(
                activity.resources.getString(R.string.no_valid_intents_title),
                activity.resources.getString(R.string.no_valid_intents_body)
            ) { _, _ ->
            }
        alertDialog.isCancelable = true
        alertDialog.show(childFragmentManager, "fragment_alert")
    }
}

@Throws(IOException::class, IllegalArgumentException::class, SecurityException::class)
fun createTemporaryImageFile(fileNamePrefix: String, context: Context): File? {

    // Create an image file name
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName =
        fileNamePrefix + "_JPEG_" + timeStamp + "_"
    val storageDir =
        context.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val image = File.createTempFile(
        imageFileName,  /* prefix */
        ".jpg",  /* suffix */
        storageDir /* directory */
    )

    //delete temp file when 'virtual machine' exits (I believe this means when the app terminates)
    //file is also deleted inside registerForActivityResult(), this is just a precaution
    image.deleteOnExit()

    return image

}

/** Will return true if the File exists and is an image.
 * This will also return true for .gif files. Anything that can
 * be converted into a bitmap is viable.
 **/
fun File.isImage(): Boolean {
    if (!this.exists()) return false
    try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(this.absolutePath, options)
        return options.outWidth != -1 && options.outHeight != -1
    } catch (_: IllegalArgumentException) {
    }
    return false
}

class MakeshiftCoroutineConditionVariable {

    private val channel: Channel<Unit> = Channel(0)

    suspend fun wait() {
        channel.receive()
    }

    suspend fun wait(
        timeInMillis: Long,
        coRoutineContext: CoroutineDispatcher = ServiceLocator.globalIODispatcher
    ) {
        val job = CoroutineScope(coRoutineContext).launch {
            delay(timeInMillis)
            channel.trySend(Unit)
        }

        channel.receive()
        job.cancel("no_longer_needed", null)
    }

    fun notifyOne() {
        channel.trySend(Unit)
    }
}

fun printStackTraceForErrors(): String {
    return Throwable().stackTraceToString()
}

fun runCheckConnectionStatus(
    context: Context,
    clientsIntermediate: ClientsInterface,
    errorStore: StoreErrorsInterface,
    error: GrpcFunctionErrorStatusEnum,
    connectionErrorHandler: Handler,
    connectionErrorToken: String,
    handleGrpcFunctionError: (error: GrpcFunctionErrorStatusEnum) -> Unit,
    displayErrorMessage: (String) -> Unit,
) {

    connectionErrorHandler.removeCallbacksAndMessages(connectionErrorToken)

    connectionErrorHandler.postAtTime(
        {
            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                checkIfNetworkStatus(
                    context.applicationContext,
                    clientsIntermediate,
                    errorStore,
                    error,
                ) { errorParam ->
                    //needs to be run in main because handleGrpcFunctionError modifies views
                    withContext(Main) {
                        handleGrpcFunctionError(errorParam)
                    }
                }
            }
        },
        connectionErrorToken,
        SystemClock.uptimeMillis() + 5000
    )

    val errorString =
        if (error == GrpcFunctionErrorStatusEnum.SERVER_DOWN) {
            GlobalValues.applicationContext.resources.getString(R.string.server_down_error)
        } else {
            GlobalValues.applicationContext.resources.getString(R.string.general_connection_error)
        }

    //needs to be run in main because displayErrorMessage modifies views
    displayErrorMessage(errorString)
}

//will check if CONNECTION_ERROR or SERVER_DOWN are true
suspend fun checkIfNetworkStatus(
    applicationContext: Context,
    clientsIntermediate: ClientsInterface,
    errorStore: StoreErrorsInterface,
    error: GrpcFunctionErrorStatusEnum,
    handleGrpcFunctionError: suspend (error: GrpcFunctionErrorStatusEnum) -> Unit,
) = withContext(ServiceLocator.globalIODispatcher) {

    val networkState = ServiceLocator.deviceIdleOrConnectionDown.deviceIdleOrConnectionDown(
        applicationContext
    )

    Log.i("testingDoze", "Utility networkState: $networkState")

    val returnError =
        when (networkState) {
            DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE -> {
                if (error == GrpcFunctionErrorStatusEnum.SERVER_DOWN) {
                    when (GlobalValues.runLoadBalancing(
                        clientsIntermediate,
                        errorStore = errorStore
                    )) {
                        GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> GrpcFunctionErrorStatusEnum.NO_ERRORS
                        GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
                        GrpcAndroidSideErrorsEnum.SERVER_DOWN -> GrpcFunctionErrorStatusEnum.SERVER_DOWN
                        GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> error
                    }
                } else {
                    GrpcFunctionErrorStatusEnum.NO_ERRORS
                }
            }
            DeviceIdleOrConnectionDownEnum.DEVICE_IN_LIGHT_DOZE,
            DeviceIdleOrConnectionDownEnum.DEVICE_IN_DEEP_DOZE,
            DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_UNAVAILABLE -> {
                error
            }
        }

    Log.i("checkIfNetworkStatus", "checkIfNetworkStatus() running")

    handleGrpcFunctionError(returnError)
}

fun reGenerateInstallationId(applicationContext: Context) {

    val sharedPreferences = applicationContext.getSharedPreferences(
        applicationContext.getString(R.string.shared_preferences_lets_go_key),
        MultiDexApplication.MODE_PRIVATE
    )

    generateNewInstallationId(applicationContext, sharedPreferences)
}

fun generateNewInstallationId(applicationContext: Context, sharedPreferences: SharedPreferences) {
    val newInstallationId = UUID.randomUUID().toString()

    sharedPreferences.edit().putString(
        applicationContext.getString(R.string.shared_preferences_installation_id),
        newInstallationId
    ).apply()

    GlobalValues.initialSetInstallationId(newInstallationId)
}

fun generateViewModelInstanceId(): String {
    return UUID.randomUUID().toString()
}

fun generateChatMessageUUID(): String {
    return UUID.randomUUID().toString()
}

//will automatically place an EventWrapperWithKeyString around the livedata value,
open class KeyStringLiveData<T> : LiveData<EventWrapperWithKeyString<T>>()

//will automatically place an EventWrapperWithKeyString around the livedata value
//NOTE: The idea behind this is that if a LiveData is inside a repository, it has the potential to
// send back a message to a different view model than was intended. This (along with KeyStringLiveData
// and KeyStringObserver) will allow a view model id to be stored with an event wrapper
// by default.
class KeyStringMutableLiveData<T> : KeyStringLiveData<T>() {
    fun setValue(value: T, keyString: String) {
        super.setValue(
            EventWrapperWithKeyString(
                value,
                keyString
            )
        )
    }
}

//automatically unwraps livedata with the passed string, or does not run it
class KeyStringObserver<T>(
    private var block: (T) -> Unit,
    private var instanceId: String
) : Observer<EventWrapperWithKeyString<T>> {
    override fun onChanged(value: EventWrapperWithKeyString<T>) {
        value.getContentIfNotHandled(instanceId)?.let {
            block(it)
        }
    }
}

enum class DatabasesToRunTransactionIn {
    ACCOUNTS,
    ICONS,
    MESSAGES,
    OTHER_USERS
}

/** Nested transactions for different databases are atomic as described in the SqLite documentation
 * (https://sqlite.org/isolation.html) text taken from link is shown below.
 *
 ** Transactions involving multiple attached databases are atomic, assuming that the main
 * database is not ":memory:" and the journal_mode is not WAL. If the main database is ":memory:"
 * or if the journal_mode is WAL, then transactions continue to be atomic within each individual
 * database file. But if the host computer crashes in the middle of a COMMIT where two or more
 * database files are updated, some of those files might get the changes where others might not.
 *
 ** However the databases should be locked in a specific order (the order itself doesn't matter
 * as long as it is always the same) in order to avoid a form of deadlock.
 *
 ** This class is NOT multi thread safe **/
//TESTING_NOTE: this has not been tested
class TransactionWrapper(
    private var accountInfoDatabase: AccountInfoDatabase,
    private var iconsDatabase: IconsDatabase,
    private var messagesDatabase: MessagesDatabase,
    private var otherUsersDatabase: OtherUsersDatabase,
    vararg databases: DatabasesToRunTransactionIn,
) {

    private val lambdaToRunAfter = mutableListOf<suspend () -> Any>()
    private var started = false

    //NOTE: the set here accomplishes 2 things
    //1) it guarantees that only one of each enum type can be sent in
    //2) it guarantees that when iterated through the set will be in a pre-determined order, otherwise
    // deadlock could occur with nested transactions (say a.lock() b.lock() in thread1 then b.lock() a.lock()
    // in thread2)
    private val databaseTransactionInstructions = sortedSetOf<DatabasesToRunTransactionIn>()

    init {
        for (database in databases) {
            databaseTransactionInstructions.add(database)
        }
    }

    private suspend fun runNestedTransactions(
        block: suspend TransactionWrapper.() -> Unit,
        setOfTransaction: SortedSet<DatabasesToRunTransactionIn>,
    ) {

        if (setOfTransaction.isNotEmpty()) {
            when (val firstElement = setOfTransaction.first()) {
                DatabasesToRunTransactionIn.ACCOUNTS -> {
                    setOfTransaction.remove(firstElement)
                    accountInfoDatabase.withTransaction {
                        runNestedTransactions(block, setOfTransaction)
                    }
                }
                DatabasesToRunTransactionIn.ICONS -> {
                    setOfTransaction.remove(firstElement)
                    iconsDatabase.withTransaction {
                        runNestedTransactions(block, setOfTransaction)
                    }
                }
                DatabasesToRunTransactionIn.MESSAGES -> {
                    setOfTransaction.remove(firstElement)
                    messagesDatabase.withTransaction {
                        runNestedTransactions(block, setOfTransaction)
                    }
                }
                DatabasesToRunTransactionIn.OTHER_USERS -> {
                    setOfTransaction.remove(firstElement)
                    otherUsersDatabase.withTransaction {
                        runNestedTransactions(block, setOfTransaction)
                    }
                }
            }
        } else { //transactions have all been nested
            block()
        }
    }

    suspend fun runTransaction(block: suspend TransactionWrapper.() -> Unit) {

        var currentFunctionStartedTransaction = false
        try {
            if (started) { //already inside transaction
                block()
            } else {
                currentFunctionStartedTransaction = true
                started = true

                runNestedTransactions(block, HashSet(databaseTransactionInstructions).toSortedSet())

                //only run lambda(s) if transaction was successful
                for (lambda in lambdaToRunAfter) {
                    lambda()
                }
            }
        } finally {
            if (currentFunctionStartedTransaction) {
                started = false
            }
        }
    }

    //NOTE: this will not run it after a specific block of runTransaction(), it will
    // run the function at the end of the entire transaction (the end of the outermost runTransaction
    // for this class instance)
    fun runAfterTransaction(lambda: suspend () -> Unit) {
        lambdaToRunAfter.add(lambda)
    }
}

fun Boolean.toInt() = if (this) 1 else 0

class UTF8IncorrectEncoding(
    message: String =
        "UTF8IncorrectEncoding exception thrown\n" +
                "lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}\n" +
                "fileName: ${Thread.currentThread().stackTrace[2].fileName}\n",
) : Exception(message)

/**
 * UTF-8 encoding                                 Byte to decimal
 * single-byte sequences    0xxxxxxx                 0 .. 127
 * double-byte sequences    110xxxxx 10xxxxxx      -64..-33
 * triple-byte sequences    1110xxxx 10xxxxxx * 2  -32..-17
 * quadruple-byte sequences 11110xxx 10xxxxxx * 3  -16..-9
 *
 * Throws UTF8IncorrectEncoding if the byte array is not properly formatted
 **/
fun extractNumberBytesInCodePoint(byteArray: ByteArray, index: Int): Int {
    return when (byteArray[index].toInt()) {
        in 0..127 -> { //single-byte sequence
            1
        }
        in -64..-33 -> { //double-byte sequence
            2
        }
        in -32..-17 -> { //triple-byte sequence
            3
        }
        in -16..-9 -> { //quadruple-byte sequence
            4
        }
        else -> { //invalid-byte sequence
            throw UTF8IncorrectEncoding()
        }
    }
}

//~FACEBOOK TAG~
/*fun facebookLogout() {
    //if Facebook is signed in, sign out
    val accessToken: AccessToken? = AccessToken.getCurrentAccessToken()
    val isLoggedIn = accessToken != null && !accessToken.isExpired
    if (isLoggedIn) {
        LoginManager.getInstance().logOut()
    }
}*/

@Keep
@SuppressLint("DiscouragedPrivateApi")
fun isLightDeviceIdleMode(
    context: Context,
    errorStore: StoreErrorsInterface
): Boolean {
    var result = false
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        result = pm.isDeviceLightIdleMode
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            val isLightDeviceIdleModeMethod: Method =
                pm.javaClass.getDeclaredMethod("isLightDeviceIdleMode")
            result = isLightDeviceIdleModeMethod.invoke(pm) as Boolean
        } catch (e: Exception) {
            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                //This is not so much an error as information gathering to see if this is ever called.
                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    e.stackTraceToString(),
                    "Exception occurred when requesting isLightDeviceIdleMode\n$e",
                    context.applicationContext,
                    ErrorOriginEnum.ErrorUrgencyLevel.ERROR_URGENCY_LEVEL_VERY_LOW
                )
            }
        }
    }
    return result
}

fun isDeviceIdleMode(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isDeviceIdleMode
    } else {
        false
    }
}

fun dismissAllDialogs(manager: FragmentManager) {
    val fragments = manager.fragments

    for (fragment in fragments) {
        if (fragment is DialogFragment) {
            val dialogFragment: DialogFragment = fragment
            dialogFragment.dismissAllowingStateLoss()
            manager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
        val childFragmentManager = fragment.childFragmentManager
        dismissAllDialogs(childFragmentManager)
    }
}

fun setupStoreErrorsInterface(): StoreErrorsInterface {
    return if (GlobalValues.setupForTesting) ServiceLocator.globalErrorStore else StoreErrors()
}

/*
//FACEBOOK DEVELOPMENT KEY HASH
fun printHashKey(pContext: Context) {
    val TAG = "printHashKey"
    try {
        val info = pContext.packageManager.getPackageInfo(
            pContext.packageName,
            PackageManager.GET_SIGNATURES
        )
        for (signature in info.signatures) {
            val md: MessageDigest = MessageDigest.getInstance("SHA")
            md.update(signature.toByteArray())
            val hashKey: String = String(Base64.getEncoder().encode(md.digest()))
            Log.i(TAG, "printHashKey() Hash Key: $hashKey")
        }
    } catch (e: NoSuchAlgorithmException) {
        Log.e(TAG, "printHashKey()", e)
    } catch (e: Exception) {
        Log.e(TAG, "printHashKey()", e)
    }
}*/


/**
 * This class is needed to statically generate the hash string that the server will send back
 * in the SMS messages and the Broadcast Receiver will pick up.
 **/
/*
class AppSignatureHelper(context: Context?) :
    ContextWrapper(context) {// Get all package signatures for the current package

    // For each signature create a compatible hash
    /**
     * Get all the app signatures for the current package
     * @return
     */
    val appSignatures: ArrayList<String>
        get() {
            val appCodes: ArrayList<String> = ArrayList()
            try {
                // Get all package signatures for the current package
                val packageName = packageName
                val packageManager = packageManager
                val signatures: Array<Signature> = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES).signatures

                // For each signature create a compatible hash
                for (signature in signatures) {
                    val hash = hash(packageName, signature.toCharsString())
                    if (hash != null) {
                        appCodes.add(String.format("%s", hash))
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Unable to find package to obtain hash.", e)
            }
            return appCodes
        }

    companion object {
        val TAG = AppSignatureHelper::class.java.simpleName
        private const val HASH_TYPE = "SHA-256"
        const val NUM_HASHED_BYTES = 9
        const val NUM_BASE64_CHAR = 11
        private fun hash(packageName: String, signature: String): String? {
            val appInfo = "$packageName $signature"
            try {
                val messageDigest: MessageDigest = MessageDigest.getInstance(HASH_TYPE)
                messageDigest.update(appInfo.toByteArray(Charsets.UTF_8))
                var hashSignature: ByteArray = messageDigest.digest()

                // truncated into NUM_HASHED_BYTES
                hashSignature = hashSignature.copyOfRange(0, NUM_HASHED_BYTES)
                // encode into Base64
                var base64Hash: String = Base64.getEncoder().encodeToString(hashSignature)

                    //Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
                base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)
                Log.d(TAG, String.format("pkg: %s -- hash: %s", packageName, base64Hash))
                return base64Hash
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "hash:NoSuchAlgorithm", e)
            }
            return null
        }
    }
}
*/

/*
private suspend fun showAllFilesOnDevice() {
    val fileChatPicPrefix =
        applicationContext.getString(R.string.user_picture_chat_message_file_prefix)
    val fileChatGifPrefix =
        applicationContext.getString(R.string.user_mime_type_chat_message_file_prefix)

    val filePicturePrefix =
        applicationContext.getString(R.string.user_picture_file_name_prefix)
    val fileReplyThumbnailPrefix =
        applicationContext.getString(R.string.user_reply_thumbnail_chat_message_file_prefix)

    val fileOtherUserPicturePrefix =
        applicationContext.getString(R.string.other_user_picture_file_name_prefix)
    val fileOtherUserThumbnailPrefix =
        applicationContext.getString(R.string.other_user_thumbnail_file_name_prefix)

    val fileErrorMessagePrefix =
        applicationContext.getString(R.string.error_worker_file_name_prefix)

    val listOfFilePathsFromDirectories = mutableListOf<String>()
    val listOfErrorMessageFilePaths = mutableListOf<String>()

    val fileListNullable = applicationContext.filesDir.listFiles()

    fileListNullable?.let { filesList ->
        for (file in filesList) {
            if (file?.exists() == true) {

                //various other file types exist inside of the directories for example Glide
                // will occasionally store things
                if (
                    file.name.matchesPrefix(fileChatPicPrefix)
                    || file.name.matchesPrefix(fileChatGifPrefix)
                    || file.name.matchesPrefix(filePicturePrefix)
                    || file.name.matchesPrefix(fileReplyThumbnailPrefix)
                    || file.name.matchesPrefix(fileOtherUserPicturePrefix)
                    || file.name.matchesPrefix(fileOtherUserThumbnailPrefix)
                ) {
                    listOfFilePathsFromDirectories.add(file.absolutePath)
                } else if (file.name.matchesPrefix(fileErrorMessagePrefix)) {
                    listOfErrorMessageFilePaths.add(file.absolutePath)
                }
            }
        }
    }

    val cacheListNullable = applicationContext.cacheDir.listFiles()

    cacheListNullable?.let { cacheList ->
        for (file in cacheList) {
            if (file?.exists() == true) {

                //various other file types exist inside of the directories for example Glide
                // will occasionally store things
                if (
                    file.name.matchesPrefix(fileChatPicPrefix)
                    || file.name.matchesPrefix(fileChatGifPrefix)
                    || file.name.matchesPrefix(filePicturePrefix)
                    || file.name.matchesPrefix(fileReplyThumbnailPrefix)
                    || file.name.matchesPrefix(fileOtherUserPicturePrefix)
                    || file.name.matchesPrefix(fileOtherUserThumbnailPrefix)
                ) {
                    listOfFilePathsFromDirectories.add(file.absolutePath)
                } else if (file.name.matchesPrefix(fileErrorMessagePrefix)) {
                    listOfErrorMessageFilePaths.add(file.absolutePath)
                }
            }
        }
    }

    val saveAccountPictureFileNamesToList: suspend (MutableList<CleanDatabaseWorker.DatabaseFilePathsList>)->Unit =  { listOfFileNamesFromDatabase ->
        val accountPictureFilePaths =
            cleanDatabaseWorkerRepository.retrieveAllAccountPictureFilePaths()

        for (picture in accountPictureFilePaths) {
            if (picture.picturePath.isNotEmpty()) {
                listOfFileNamesFromDatabase.add(
                    CleanDatabaseWorker.DatabaseFilePathsList(
                        picture.pictureIndex.toString(),
                        picture.picturePath,
                        CleanDatabaseWorker.FilePathType.USER_PICTURE_FILE_PATH
                    )
                )
            }
        }
    }

    val saveMessageFileNamesToList: suspend (MutableList<CleanDatabaseWorker.DatabaseFilePathsList>) -> Unit = { listOfFileNamesFromDatabase ->

        val messageFilePaths = cleanDatabaseWorkerRepository.retrieveMessageFilePaths()

        for (message in messageFilePaths) {
            if (message.file_path.isNotEmpty()) {
                listOfFileNamesFromDatabase.add(
                    CleanDatabaseWorker.DatabaseFilePathsList(
                        message.messageUUIDPrimaryKey,
                        message.file_path,
                        CleanDatabaseWorker.FilePathType.PICTURE_MESSAGE_FILE_PATH
                    )
                )
            }

            if (message.reply_is_from_thumbnail_file_path.isNotEmpty()) {
                listOfFileNamesFromDatabase.add(
                    CleanDatabaseWorker.DatabaseFilePathsList(
                        message.messageUUIDPrimaryKey,
                        message.reply_is_from_thumbnail_file_path,
                        CleanDatabaseWorker.FilePathType.MESSAGE_REPLY_FILE_PATH
                    )
                )
            }
        }
    }

    val saveMimeTypeFileNamesToList: suspend (MutableList<CleanDatabaseWorker.DatabaseFilePathsList>) -> Unit = { listOfFileNamesFromDatabase ->

        val mimeTypeFilePaths = cleanDatabaseWorkerRepository.retrieveMimeTypesAllFilePaths()

        for (mimeTypes in mimeTypeFilePaths) {
            if (mimeTypes.mime_type_file_path.isNotEmpty()) {
                listOfFileNamesFromDatabase.add(
                    CleanDatabaseWorker.DatabaseFilePathsList(
                        mimeTypes.mimeTypeUrl,
                        mimeTypes.mime_type_file_path,
                        CleanDatabaseWorker.FilePathType.MIME_TYPE_FILE_PATH
                    )
                )
            }
        }
    }

    val saveOtherUserFileNamesToList: suspend (MutableList<CleanDatabaseWorker.DatabaseFilePathsList>) -> Unit = { listOfFileNamesFromDatabase ->

        val otherUserFilePaths = cleanDatabaseWorkerRepository.retrieveOtherUserAllFilePaths()

        for (otherUser in otherUserFilePaths) {
            if (otherUser.pictures.isNotEmpty()) {

                val picturesList = convertPicturesStringToList(otherUser.pictures)

                for (pic in picturesList) {
                    listOfFileNamesFromDatabase.add(
                        CleanDatabaseWorker.DatabaseFilePathsList(
                            otherUser.accountOID,
                            pic.picturePath,
                            CleanDatabaseWorker.FilePathType.OTHER_USER_PICTURE_FILE_PATH
                        )
                    )
                }
            }

            if (otherUser.thumbnail_path.isNotEmpty()) {
                listOfFileNamesFromDatabase.add(
                    CleanDatabaseWorker.DatabaseFilePathsList(
                        otherUser.accountOID,
                        otherUser.thumbnail_path,
                        CleanDatabaseWorker.FilePathType.OTHER_USER_THUMBNAIL_FILE_PATH
                    )
                )
            }
        }
    }

    val extractListOfFilePathsFromDatabase: suspend ()-> MutableList<CleanDatabaseWorker.DatabaseFilePathsList> = {

        val listOfFileNamesFromDatabase = mutableListOf<CleanDatabaseWorker.DatabaseFilePathsList>()

        saveAccountPictureFileNamesToList(listOfFileNamesFromDatabase)

        saveMessageFileNamesToList(listOfFileNamesFromDatabase)

        saveMimeTypeFileNamesToList(listOfFileNamesFromDatabase)

        saveOtherUserFileNamesToList(listOfFileNamesFromDatabase)

        listOfFileNamesFromDatabase
    }

    val listOfFilePathsFromDatabase = extractListOfFilePathsFromDatabase()

    listOfFilePathsFromDirectories.sort()

    listOfFilePathsFromDatabase.sortBy {
        it.filePath
    }

    var fileNamesForDebugging = ""

    fileNamesForDebugging += "\nFiles From Directory\n"
    for (path in listOfFilePathsFromDirectories) fileNamesForDebugging += "$path\n"

    fileNamesForDebugging += "\nFiles From Database\n"
    for (path in listOfFilePathsFromDatabase) fileNamesForDebugging += "${path.filePath}\n"

    Log.i("print_files", "number directory files: ${listOfFilePathsFromDirectories.size} number database files: ${listOfFilePathsFromDatabase.size}")
    Log.i("print_files", fileNamesForDebugging)

    val fileExistsOnlyInDatabase: (CleanDatabaseWorker.DatabaseFilePathsList) -> Unit =
        { databaseFilePathsList ->
            when (databaseFilePathsList.type) {
                CleanDatabaseWorker.FilePathType.USER_PICTURE_FILE_PATH,
                CleanDatabaseWorker.FilePathType.MESSAGE_REPLY_FILE_PATH,
                CleanDatabaseWorker.FilePathType.OTHER_USER_THUMBNAIL_FILE_PATH,
                -> {
                    Log.i("print_files", "${databaseFilePathsList.filePath} exists only in database; key: ${databaseFilePathsList.primaryKey} type: ${databaseFilePathsList.type}")
                }
                CleanDatabaseWorker.FilePathType.PICTURE_MESSAGE_FILE_PATH,
                CleanDatabaseWorker.FilePathType.MIME_TYPE_FILE_PATH,
                CleanDatabaseWorker.FilePathType.OTHER_USER_PICTURE_FILE_PATH,
                -> {

                    //This is possible because the database is updated before the pictures are saved to file
                    // and so this could have been checked inside of that gap, so do not remove these files
                    //these types can be re-downloaded when they are accessed, they are stored inside the cacheDir
                    // so they may be removed at times
                }
            }
        }


    val fileExistsOnlyInDirectory: (String) -> Unit = { filePath ->

        //This means a a memory leak occurred.
        //NOTE: Because files are deleted using a worker it IS possible that a file has been removed from
        // the database and is simply waiting to be removed by the Worker

        Log.i("print_files", "$filePath exists only in directory")
    }

    var filesFromDirectoriesIndex = 0
    var filesFromDatabaseIndex = 0

    while (filesFromDirectoriesIndex < listOfFilePathsFromDirectories.size && filesFromDatabaseIndex < listOfFilePathsFromDatabase.size) {
        when {
            listOfFilePathsFromDirectories[filesFromDirectoriesIndex] ==
                    listOfFilePathsFromDatabase[filesFromDatabaseIndex].filePath -> { //file names are the same

                //this is expected
                filesFromDirectoriesIndex++
                filesFromDatabaseIndex++
            }
            listOfFilePathsFromDirectories[filesFromDirectoriesIndex] <
                    listOfFilePathsFromDatabase[filesFromDatabaseIndex].filePath -> { //a file exists in directory that does not exist in database

                //This is a memory leak
                fileExistsOnlyInDirectory(listOfFilePathsFromDirectories[filesFromDirectoriesIndex])

                filesFromDirectoriesIndex++
            }
            else -> { //a file exists in database that does not exist in directory

                fileExistsOnlyInDatabase(listOfFilePathsFromDatabase[filesFromDatabaseIndex])

                filesFromDatabaseIndex++
            }
        }
    }

    for (i in filesFromDirectoriesIndex until listOfFilePathsFromDirectories.size) {
        fileExistsOnlyInDirectory(listOfFilePathsFromDirectories[filesFromDirectoriesIndex])
    }

    for (i in filesFromDatabaseIndex until listOfFilePathsFromDatabase.size) {
        fileExistsOnlyInDatabase(listOfFilePathsFromDatabase[filesFromDatabaseIndex])
    }

}
 */