package site.letsgoapp.letsgo.databases.messagesDatabase.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import type_of_chat_message.TypeOfChatMessageOuterClass

/** Decision to use message data entity this way.
 * When deciding how to store messages using the room database a few things need to be considered.
 * 1) When @Entity is used to define a class it cannot be inherited from (although it can be used as a
 * member of another @Entity).
 * 2) SQLite has a defined schema and so all columns must be used.
 *
 * Ways considered to store data.
 *
 * 1) Can store mandatory info then use a specifics document parsed to a string (similar to how
 * messages are set up inside the TypeOfChatMessage.proto)
 * PROS: Memory, only the mandatory fields will ever be stored
 * CONS: SELECT queries would most likely be able to be used along with a parsing schema (could just
 * use a .proto message toByteString() method). However if any message specifics needed to be changed,
 * the entire specifics string would need to be extracted, modified and written again. There would also
 * be overhead in parsing the fields to a byte array then to a string then back again and if the message
 * contains a large amount of text for example it could begin to be expensive.
 *
 * 2) Using re-usable generic fields (the current method).
 * PROS: All fields can be accessed by the queries. (Another pro is that is was already written this way).
 * CONS: Takes up more space than just storing 'mandatory' info. Very low readability, each message needs
 * to be looked up in documentation (convertChatMessageToMessageDataEntity() in MessagesDaoIntermediate.kt
 * works for this in a sense) to see which fields it uses.
 *
 * 3) Using @Entity objects as fields inside of each other.
 * PROS: Readability, this would allow for messages to be distinct and each have their own fields.
 * CONS: Memory, in order for each member to truly have their own fields there would need to be a fair amount
 * of empty(probably null) fields. The Room documentation says that if all fields inside of a nested Entity are
 * null then the Entity field itself is set to null, however because of the SQLite having a defined schema the
 * nested fields must still exists INSIDE of the database. Also a major feature of Kotlin is null checking and
 * so staying away from nullable fields is preferred.
 *
 * 4) Can store different message types across different tables. NOTE: @Entity is final and so cannot be a parent, it will
 * also throw an error if it inherits a field from another class. However I believe it CAN inherit an empty class, so the
 * empty parent class can be used to store them all in a container together. An @Entity class could then be nested with the
 * shared fields.
 * PROS: Can have well defined message types and each one is unique. Queries when the message type is know would be much faster.
 * CONS: Queries on groups of messages would be much more difficult. Also things like sorting would have to be done outside the
 * query.
 *
 * 5) Combinations of the above.
 * - Perhaps store only values that are NOT accessed directly by queries inside a 'Specifics' string, and the rest outside.
 * PROS: Solves the CONS of problems 1) and 2) to some extent. Almost the best of both worlds
 * CONS: Extendability, this method would need to be heavily micro-managed, every time a new query was written, there is a chance
 * the entire schema would need to be changed. For later releases of the app this would be very bad.
 *
 * - Store 'Active' Messages (text, picture, invite etc...) in one table and other messages in another. 'Active' messages have shared
 * parameters between each other. Also they are overwhelmingly the messages that queries are done on.
 * PROS: Could eliminate some overhead of storing info specific to the 'Active' messages (probably not very much)
 * CONS: Separates related info, also same cons as 4).
 *
 * **/

@Entity(tableName = "messages_table")
class MessagesDataEntity(

    //see ChatMessageStream.proto for more info on these values

    /** Static message properties; these will be set only once. **/

    //NOTE: must be set to 0, this will tell room that it is 'not set' (could also make it nullable)
    //NOTE: indexing starts at 1, there is no index 0
    @PrimaryKey(autoGenerate = false)
    var messageUUIDPrimaryKey: String,

    @ColumnInfo(name = "sent_by_account_id")
    var sentByAccountID: String,

    //NOTE: the -1 here is used to tell if messageType has been set
    // follows TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase
    // they should be the same except for the final few values (client specific values)
    @ColumnInfo(name = "message_type")
    var messageType: Int,

    @ColumnInfo(name = "chat_room_id")
    var chatRoomId: String,

    /** Dynamic message properties; these can be set many times. **/

    //will be set to timestamp from the server if messageHasBeenSent is true,
    //otherwise will be -1L
    @ColumnInfo(name = "message_stored_server_time")
    var messageStoredOnServerTime: Long,

    //will be set as message is stored in database (in ms)
    // set when a message is received from the server, also set when sending a message originally
    // used to sort messages by
    @ColumnInfo(name = "message_stored_database_timestamp")
    var messageStoredInDatabaseTime: Long,

    //follows enum ChatMessageStoredStatus
    @ColumnInfo(name = "message_sent_status")
    var messageSentStatus: Int,

    //true means that a message has been received while no activities were running
    // and the notification has not been sent to the user yet
    //false means that a message does not require a notification
    @ColumnInfo(name = "message_requires_notification")
    var messageRequiresNotification: Boolean,

    //if this message is sent by this client, this value will be set to the time the message was observed
    //if it was not set it will be set to -1L
    //this value is used in memory management and it is updated whenever a user observes a message
    @ColumnInfo(name = "time_user_last_observed_message")
    var timeUserLastObservedMessage: Long,

    //follows AmountOfMessage enum inside TypeOfChatMessage.proto
    //the MINIMUM amount of the message that has been downloaded; for example if ONLY_SKELETON and
    // ENOUGH_TO_DISPLAY_AS_FINAL_MESSAGE send back the same amount of info for this message type
    // this will be set to ONLY_SKELETON
    //works with hasCompleteInfo
    @ColumnInfo(name = "amount_of_message")
    var amountOfMessage: Int,

    //will be set to true if the message does not require any more info to be downloaded, false if
    // more info is required
    //works with amountOfMessage
    @ColumnInfo(name = "has_complete_info")
    var hasCompleteInfo: Boolean,

    /** ----------------------------------------------------------- **/
    /** ----------------------SPECIFICS---------------------------- **/
    /** ----------------------------------------------------------- **/
    /** The values below will be used only in certain messages. **/

    @ColumnInfo(name = "message_text")
    var messageText: String,

    @ColumnInfo(name = "is_reply")
    var isReply: Boolean,

    //used if isReply == true
    //the oid of the sender of the message being replied to
    @ColumnInfo(name = "reply_is_sent_from_oid")
    var replyIsSentFromOID: String,

    //used if isReply == true
    //the oid of the message being replied to
    @ColumnInfo(name = "reply_is_from_uuid")
    var replyIsFromMessageUUID: String,

    //used if isReply == true
    //the type of message being replied to, follows TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase
    // inside TypeOfChatMessage.proto
    @ColumnInfo(name = "reply_type")
    var replyType: Int,

    //used if isReply == true && replyToMessageType == CHAT_TEXT_MESSAGE_VALUE
    //the message_text of the message being replied to
    @ColumnInfo(name = "reply_is_from_message_text")
    var replyIsFromMessageText: String,

    //used if isReply == true && replyToMessageType == MIME_TYPE_MESSAGE
    //the mime type of the soft keyboard message, accepted mime types are listed on the server inside allowed_mime_types
    @ColumnInfo(name = "reply_is_from_mime_type")
    var replyIsFromMimeType: String,

    //used if isReply == true && (replyToMessageType == PICTURE_MESSAGE_VALUE || replyToMessageType == GIF_MESSAGE_VALUE)
    //the filepath of the thumbnail for the message being replied to
    //NOTE: this can be stored as "" if an error occurred on the server, so make sure to handle it properly
    @ColumnInfo(name = "reply_is_from_thumbnail_file_path")
    var replyIsFromThumbnailFilePath: String,

    //this int follows the values of DeleteType enum inside TypeOfChatMessage.proto
    //DELETE_TYPE_NOT_SET (0) means message is not deleted (used by view model)
    //DELETED_ON_CLIENT (1) means message is deleted (used by view model)
    //DELETE_FOR_SINGLE_USER (2) & DELETE_FOR_ALL_USERS (3) are used by the server
    @ColumnInfo(name = "deleted_type")
    var deletedType: Int,

    //will be set to the time the message was verified as edited by the server
    @ColumnInfo(name = "message_edited_time")
    var messageEditedTime: Long,

    //this is only used on the client, the server stores edited messages as unique messages
    @ColumnInfo(name = "is_edited")
    var isEdited: Boolean,

    //url for a download (used by mime types)
    @ColumnInfo(name = "download_url")
    var downloadUrl: String,

    //if the message requires an oid this will set it
    @ColumnInfo(name = "oid_value")
    var oidValue: String,

    //this is used to carry the index value for messages that modify other messages (DELETED and EDITED types)
    //this is a client specific value, it is never sent to the server
    @ColumnInfo(name = "modified_message_uuid")
    var modifiedMessageUUID: String,

    //height of the image in pixels
    @ColumnInfo(name = "image_height")
    var imageHeight: Int,

    //width of the image in pixels
    @ColumnInfo(name = "image_width")
    var imageWidth: Int,

    @ColumnInfo(name = "longitude")
    var longitude: Double,

    @ColumnInfo(name = "latitude")
    var latitude: Double,

    //path for things like pictures
    @ColumnInfo(name = "file_path")
    var filePath: String,

    //the account ID this message is about
    @ColumnInfo(name = "account_id")
    var accountOID: String,

    //will be set to true if invite expired, false if not
    @ColumnInfo(name = "invite_expired")
    var inviteExpired: Boolean,

    //chat room id if it is part of the message (for example invite requires a chat room id)
    @ColumnInfo(name = "message_value_chat_room_id")
    var messageValueChatRoomId: String,

    //chat room name if it is part of the message (for example invite requires a chat room id)
    @ColumnInfo(name = "message_value_chat_room_name")
    var messageValueChatRoomName: String,

    //chat room password if it is part of the message (for example invite requires a chat room id)
    @ColumnInfo(name = "message_value_chat_room_password")
    var messageValueChatRoomPassword: String,

    //only relevant if isEdited is set to true
    //false if edit for message has not been sent to server, true if message has been sent
    //this value is used for displaying if the little sent icon is show inside the chat message adapter
    @ColumnInfo(name = "edit_has_been_sent")
    var editHasBeenSent: Boolean,

    //if this message is sent by this client, this value will be set to the time the message was observed
    //if it was not set it will be set to -1L
    @ColumnInfo(name = "message_observed_time")
    var messageObservedTime: Long

) {

    //default constructor
    //note that certain things use the default values to pass that this object
    // has not been constructed yet
    constructor() :
            this(
                DefaultMessageDataEntityValues.MESSAGE_UUID_PRIMARY_KEY,
                DefaultMessageDataEntityValues.SENT_BY_ACCOUNT_ID,
                DefaultMessageDataEntityValues.MESSAGE_TYPE,
                DefaultMessageDataEntityValues.CHAT_ROOM_ID,
                DefaultMessageDataEntityValues.MESSAGE_STORED_ON_SERVER_TIME,
                DefaultMessageDataEntityValues.MESSAGE_STORED_IN_DATABASE_TIME,
                DefaultMessageDataEntityValues.MESSAGE_SENT_STATUS,
                DefaultMessageDataEntityValues.MESSAGE_REQUIRES_NOTIFICATION,
                DefaultMessageDataEntityValues.TIME_USER_LAST_OBSERVED_MESSAGE,
                DefaultMessageDataEntityValues.AMOUNT_OF_MESSAGE,
                DefaultMessageDataEntityValues.HAS_COMPLETE_INFO,
                DefaultMessageDataEntityValues.MESSAGE_TEXT,
                DefaultMessageDataEntityValues.IS_REPLY,
                DefaultMessageDataEntityValues.REPLY_IS_SENT_FROM_OID,
                DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_UUID,
                DefaultMessageDataEntityValues.REPLY_TYPE,
                DefaultMessageDataEntityValues.REPLY_IS_FROM_MESSAGE_TEXT,
                DefaultMessageDataEntityValues.REPLY_IS_FROM_MIME_TYPE,
                DefaultMessageDataEntityValues.REPLY_IS_FROM_THUMBNAIL_FILE_PATH,
                DefaultMessageDataEntityValues.DELETED_TYPE,
                DefaultMessageDataEntityValues.MESSAGE_EDITED_TIME,
                DefaultMessageDataEntityValues.IS_EDITED,
                DefaultMessageDataEntityValues.DOWNLOAD_URL,
                DefaultMessageDataEntityValues.OID_VALUE,
                DefaultMessageDataEntityValues.MODIFIED_MESSAGE_UUID,
                DefaultMessageDataEntityValues.IMAGE_HEIGHT,
                DefaultMessageDataEntityValues.IMAGE_WIDTH,
                DefaultMessageDataEntityValues.LONGITUDE,
                DefaultMessageDataEntityValues.LATITUDE,
                DefaultMessageDataEntityValues.FILE_PATH,
                DefaultMessageDataEntityValues.ACCOUNT_OID,
                DefaultMessageDataEntityValues.INVITE_EXPIRED,
                DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_ID,
                DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_NAME,
                DefaultMessageDataEntityValues.MESSAGE_VALUE_CHAT_ROOM_PASSWORD,
                DefaultMessageDataEntityValues.EDIT_HAD_BEEN_SENT,
                DefaultMessageDataEntityValues.MESSAGE_OBSERVED_TIME,
            )

    override fun toString(): String {
        return "messageUUIDPrimaryKey: $messageUUIDPrimaryKey\n" +
                "sentByAccountID: $sentByAccountID\n" +
                "messageType: $messageType\n" +
                "chatRoomId: $chatRoomId\n" +
                "messageStoredOnServerTime: $messageStoredOnServerTime\n" +
                "messageStoredInDatabaseTime: $messageStoredInDatabaseTime\n" +
                "messageSentStatus: $messageSentStatus\n" +
                "messageRequiresNotification: $messageRequiresNotification\n" +
                "timeUserLastObservedMessage: $timeUserLastObservedMessage\n" +
                "amountOfMessage: $amountOfMessage\n" +
                "hasCompleteInfo: $hasCompleteInfo\n" +
                "messageText: $messageText\n" +
                "isReply: $isReply\n" +
                "replyIsSentFromOID: $replyIsSentFromOID\n" +
                "replyIsFromMessageUUID: $replyIsFromMessageUUID\n" +
                "replyType: $replyType\n" +
                "replyIsFromMessageText: $replyIsFromMessageText\n" +
                "replyIsFromMimeType: $replyIsFromMimeType\n" +
                "replyIsFromThumbnailFilePath: $replyIsFromThumbnailFilePath\n" +
                "deletedType: $deletedType\n" +
                "messageEditedTime: $messageEditedTime\n" +
                "isEdited: $isEdited\n" +
                "downloadUrl: $downloadUrl\n" +
                "oidValue: $oidValue\n" +
                "modifiedMessageUUID: $modifiedMessageUUID\n" +
                "imageHeight: $imageHeight\n" +
                "imageWidth: $imageWidth\n" +
                "longitude: $longitude\n" +
                "latitude: $latitude\n" +
                "filePath: $filePath\n" +
                "accountOID: $accountOID\n" +
                "inviteExpired: $inviteExpired\n" +
                "messageValueChatRoomId: $messageValueChatRoomId\n" +
                "messageValueChatRoomName: $messageValueChatRoomName\n" +
                "messageValueChatRoomPassword: $messageValueChatRoomPassword\n" +
                "editHasBeenSent: $editHasBeenSent\n" +
                "messageObservedTime: $messageObservedTime\n"
    }
}

enum class ChatMessageStoredStatus {
    NOT_YET_STORED, //message has not been stored at all yet
    STORED_IN_DATABASE_ONLY, //message has only been stored inside database, server has no yet accepted it
    STORED_ON_SERVER; //message has been stored in database and server has accepted it

    companion object {
        fun setVal(value: Int?): ChatMessageStoredStatus {
            return when (value) {
                1 -> {
                    STORED_IN_DATABASE_ONLY
                }
                2 -> {
                    STORED_ON_SERVER
                }
                else -> {
                    NOT_YET_STORED
                }
            }
        }
    }
}

object DefaultMessageDataEntityValues {
    const val MESSAGE_UUID_PRIMARY_KEY = ""
    const val SENT_BY_ACCOUNT_ID = ""
    const val MESSAGE_TYPE = -1
    const val CHAT_ROOM_ID = ""
    const val MESSAGE_STORED_ON_SERVER_TIME = -1L
    const val MESSAGE_STORED_IN_DATABASE_TIME = -1L
    const val MESSAGE_SENT_STATUS = -1
    const val MESSAGE_REQUIRES_NOTIFICATION = false
    const val TIME_USER_LAST_OBSERVED_MESSAGE = -1L
    const val AMOUNT_OF_MESSAGE = -1
    const val HAS_COMPLETE_INFO = false
    const val MESSAGE_TEXT = ""
    const val IS_REPLY = false
    const val REPLY_IS_SENT_FROM_OID = ""
    const val REPLY_IS_FROM_MESSAGE_UUID = ""
    const val REPLY_TYPE =
        0 //TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET.number
    const val REPLY_IS_FROM_MESSAGE_TEXT = ""
    const val REPLY_IS_FROM_MIME_TYPE = ""
    const val REPLY_IS_FROM_THUMBNAIL_FILE_PATH = ""
    const val DELETED_TYPE = TypeOfChatMessageOuterClass.DeleteType.DELETE_TYPE_NOT_SET_VALUE
    const val MESSAGE_EDITED_TIME = -1L
    const val IS_EDITED = false
    const val DOWNLOAD_URL = ""
    const val OID_VALUE = ""
    const val MODIFIED_MESSAGE_UUID = ""
    const val IMAGE_HEIGHT = 0
    const val IMAGE_WIDTH = 0
    const val LONGITUDE = -1.0
    const val LATITUDE = -1.0
    const val FILE_PATH = ""
    const val ACCOUNT_OID = ""
    const val INVITE_EXPIRED = false
    const val MESSAGE_VALUE_CHAT_ROOM_ID = ""
    const val MESSAGE_VALUE_CHAT_ROOM_NAME = ""
    const val MESSAGE_VALUE_CHAT_ROOM_PASSWORD = ""
    const val EDIT_HAD_BEEN_SENT = false
    const val MESSAGE_OBSERVED_TIME = -1L
}
