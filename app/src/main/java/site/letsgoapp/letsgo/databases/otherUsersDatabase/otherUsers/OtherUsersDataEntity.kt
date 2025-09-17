package site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers

import account_state.AccountState
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import categorytimeframe.CategoryTimeFrame
import lets_go_event_status.LetsGoEventStatusOuterClass.LetsGoEventStatus
import site.letsgoapp.letsgo.utilities.*
import user_account_type.UserAccountTypeOuterClass.UserAccountType

@Entity(tableName = "other_users_table")
data class OtherUsersDataEntity(

    //account oid
    @PrimaryKey(autoGenerate = false)
    var accountOID: String = "",

    //path to this user thumbnail
    @ColumnInfo(name = "thumbnail_path")
    var thumbnailPath: String = "",

    //Will be set to -1 if deleted. This is used as a marker for the server to NOT update a deleted thumbnail.
    @ColumnInfo(name = "thumbnail_index_number")
    var thumbnailIndexNumber: Int = -1,

    //used for Glide to make sure cache changes, also used to make sure thumbnail
    // doesn't update from a chat room the other user is 'not in' to an outdated
    // thumbnail
    @ColumnInfo(name = "thumbnail_last_time_updated")
    var thumbnailLastTimeUpdated: Long = -1,

    //NOTE: objectsRequiringInfo and chatRoomObjects both represent other objects that require this
    // users info. When both lists are empty this user will be removed. If chatRoomObjects returns
    // only chat rooms that require partial info (the accountState is not inside the chat room)
    // then the pictures will be removed to save space.

    //Holds matches that reference this other user. Different possible and potential locations can
    // be seen below as ReferencingObjectType enum types.
    //This is set up to be converted to a set. convertObjectsRequiringInfoSetToString() and
    // convertObjectsRequiringInfoStringToSet() can be used to convert it back and forth. Each
    // element of the set is of type ObjectRequiringInfo (can be seen below).
    @ColumnInfo(name = "objects_requiring_full_info")
    var objectsRequiringInfo: String = "",

    //String that is meant to be converted to a map with
    // [key=chatRoomId, value=OtherUserChatRoomInfo]. The conversion can be done using
    // convertChatRoomObjectsMapToString() and convertChatRoomObjectsStringToMap().
    //NOTE: It is VERY important that nothing that could be interpreted as a chatRoomId
    // is stored inside this field (for example the chat room name should not be stored here. This
    // is because the database searches using LIKE on this field to check if a user is inside the
    // chat room.
    @ColumnInfo(name = "chat_rooms")
    var chatRoomObjects: String = "",

    //distance, only used when this is from a match
    @ColumnInfo(name = "distance")
    var distance: Double = 0.0,

    //timestamp when this user info was last updated
    @ColumnInfo(name = "user_info_last_updated")
    var timestampUserInfoLastUpdated: Long = -1,

    //the timestamp that the users info (the full info using UserInfoCard) was last observed
    //it is used in memory management
    @ColumnInfo(name = "user_info_last_observed")
    var timestampUserInfoLastObserved: Long = -1,

    //all pictures for user
    /*stores filePath, index, timestamp inside data class*/
    /**[PictureInfo]**/
    //call convertPicturesListToString or convertPicturesStringToList to convert
    //can be empty if no pictures are stored
    //can have invalid file paths if cache was cleared
    @ColumnInfo(name = "pictures")
    var pictures: String = "",

    //this is the timestamp that the users pictures were last attempted to be updated
    @ColumnInfo(name = "pictures_update_attempted_timestamp")
    var picturesUpdateAttemptedTimestamp: Long = -1,

    //user name
    @ColumnInfo(name = "name")
    var name: String = "",

    //user age
    @ColumnInfo(name = "age")
    var age: Int = -1,

    //user gender
    @ColumnInfo(name = "gender")
    var gender: String = "",

    //user city name
    @ColumnInfo(name = "city_name")
    var cityName: String = "",

    //user bio
    @ColumnInfo(name = "bio")
    var bio: String = "",

    //meant to converted to a list of CategoryActivityMessage
    //conversion functions are convertStringToCategoryActivityMessage() and
    // convertCategoryActivityMessageToString()
    @ColumnInfo(name = "activities")
    var activities: String = "",

    @ColumnInfo(name = "account_type")
    var accountType: Int = UserAccountType.USER_ACCOUNT_TYPE.number,

    @ColumnInfo(name = "event_status")
    var eventStatus: Int = LetsGoEventStatus.NOT_AN_EVENT.number,

    @ColumnInfo(name = "created_by")
    var createdBy: String = "",

    @ColumnInfo(name = "event_title")
    var eventTitle: String = "",

    )

//NOTE: this will format the strings to lists while preserving the original information
data class OtherUsersInfo(
    val otherUsersDataEntity: OtherUsersDataEntity,
    val chatRoom: OtherUserChatRoomInfo,
    val picturesInfo: MutableList<PictureInfo>,
    val activities: MutableList<CategoryTimeFrame.CategoryActivityMessage>
)

data class OtherUserChatRoomInfo(
    var chatRoomId: String,
    var accountStateInChatRoom: AccountState.AccountStateInChatRoom,
    var lastActiveTimeInChatRoom: Long
) {
    constructor() : this(
        "", AccountState.AccountStateInChatRoom.UNRECOGNIZED, -1
    )
}

//NOTE: Only MATCH_REFERENCE is currently used. USERS_SWIPED_YES_NO_MATCH_MADE_YET and
// MATCH_MADE_NO_CHAT_ROOM_YET have potential for caching purposes.
enum class ReferencingObjectType {
    UNKNOWN, //used as default value
    MATCH_REFERENCE, //ID is MatchIndex converted to a string
    USERS_SWIPED_YES_NO_MATCH_MADE_YET, //ID is accountOID
    MATCH_MADE_NO_CHAT_ROOM_YET //ID is accountOID
}

const val NUMBER_ITEMS_IN_OBJECTS_REQUIRING_INFO = 2

//NOTE: ID varies in what it is base on, see ReferencingObjectType for more info
data class ObjectRequiringInfo(val type: ReferencingObjectType, val ID: String)

const val OBJECTS_REQUIRING_INFO_BETWEEN_ITEM_DELIMITER = ","
const val OBJECTS_REQUIRING_INFO_BETWEEN_OBJECT_DELIMITER = ";"

fun convertObjectsRequiringInfoSetToString(objectsRequiringInfo: MutableSet<ObjectRequiringInfo>): String {

    var returnString = ""

    for (objectRequiringInfo in objectsRequiringInfo) {
        returnString += objectRequiringInfo.type.toString() + OBJECTS_REQUIRING_INFO_BETWEEN_ITEM_DELIMITER + objectRequiringInfo.ID + OBJECTS_REQUIRING_INFO_BETWEEN_OBJECT_DELIMITER
    }

    return returnString
}

fun convertObjectsRequiringInfoStringToSet(
    objectsRequiringInfo: String,
    errorStore: StoreErrorsInterface
): MutableSet<ObjectRequiringInfo> {

    val returnSet = mutableSetOf<ObjectRequiringInfo>()

    val regexBetweenObjects = Regex(OBJECTS_REQUIRING_INFO_BETWEEN_OBJECT_DELIMITER)
    val allObjects = objectsRequiringInfo.split(regexBetweenObjects)

    val regexBetweenItems = Regex(OBJECTS_REQUIRING_INFO_BETWEEN_ITEM_DELIMITER)

    for (i in allObjects.indices) {
        if (i != allObjects.lastIndex) {

            val items = allObjects[i].split(regexBetweenItems)

            if (items.size == NUMBER_ITEMS_IN_OBJECTS_REQUIRING_INFO) {
                returnSet.add(
                    ObjectRequiringInfo(
                        ReferencingObjectType.valueOf(items[0]),
                        items[1]
                    )
                )
            } else {
                val errorMessage =
                    "Invalid item inside of ObjectsRequiringInfo String, unable to convert a portion to map.\n" +
                            "objectsRequiringInfo: $objectsRequiringInfo\n" +
                            "items: $items\n" +
                            "number_items_in_ObjectsRequiringInfo: $NUMBER_ITEMS_IN_OBJECTS_REQUIRING_INFO\n" +
                            "returnMap: $returnSet\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
        }
    }

    return returnSet
}

//NOTE: very similar to convertOtherUsersDataEntityToOtherUserInfoWithChatRoom
fun convertOtherUsersDataEntityToOtherUserInfoWithChatRoom(
    dataEntity: OtherUsersDataEntity,
    chatRoomId: String
): OtherUsersInfo? {

    val chatRoomMap = convertChatRoomObjectsStringToMap(dataEntity.chatRoomObjects)

    val finalChatRoom = chatRoomMap[chatRoomId]

    finalChatRoom?.let {
        Log.i("activities_stuff", "trim activities ${dataEntity.activities}")

        return OtherUsersInfo(
            dataEntity,
            finalChatRoom,
            convertPicturesStringToList(dataEntity.pictures),
            convertStringToCategoryActivityMessageAndTrimTimes(dataEntity.activities).second
        )
    }

    //if chat room was never initialized or user is not inside passed chat room
    return null
}

const val OTHER_USER_CHAT_ROOM_INFO_DELIMITER = ","

//NOTE: this is nearly identical to convertOtherUserChatRoomInfoToString
fun convertChatRoomObjectsMapToString(otherUserChatRoomInfo: MutableMap<String, OtherUserChatRoomInfo>): String {
    var returnString = ""

    for (info in otherUserChatRoomInfo) {
        returnString += convertOtherUserChatRoomInfoToString(info.value)
    }

    return returnString
}

fun convertOtherUserChatRoomInfoToString(otherUserChatRoomInfo: OtherUserChatRoomInfo): String {
    var returnString = ""

    returnString += otherUserChatRoomInfo.chatRoomId + OTHER_USER_CHAT_ROOM_INFO_DELIMITER
    returnString += otherUserChatRoomInfo.accountStateInChatRoom.number.toString() + OTHER_USER_CHAT_ROOM_INFO_DELIMITER
    returnString += otherUserChatRoomInfo.lastActiveTimeInChatRoom.toString() + OTHER_USER_CHAT_ROOM_INFO_DELIMITER

    return returnString
}

fun convertChatRoomObjectsStringToMap(otherUserChatRoomInfo: String): MutableMap<String, OtherUserChatRoomInfo> {
    val returnMap = mutableMapOf<String, OtherUserChatRoomInfo>()

    val regex = Regex(OTHER_USER_CHAT_ROOM_INFO_DELIMITER)
    val splitString = otherUserChatRoomInfo.split(regex)

    for (i in splitString.indices step 3) { //these should always be stored in pairs of 3
        if (i != splitString.lastIndex) { //last index will be empty because this ends with a delimiter value
            returnMap[splitString[i]] = (
                    OtherUserChatRoomInfo(
                        splitString[i],
                        AccountState.AccountStateInChatRoom.forNumber(splitString[i + 1].toInt()),
                        splitString[i + 2].toLong()
                    )
                    )
        }
    }

    return returnMap
}