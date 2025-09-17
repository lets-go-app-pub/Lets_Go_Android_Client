package site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_mime_type_table")
data class MimeTypeDataEntity(

    //url of the mime type
    @PrimaryKey(autoGenerate = false)
    var mimeTypeUrl: String = "",

    //file path to the picture
    @ColumnInfo(name = "mime_type_file_path")
    var mimeTypeFilePath: String = "",

    //if it was not set it will be set to -1L
    //this value is used in memory management and it is updated whenever a user observes a mime type message
    @ColumnInfo(name = "time_user_last_observed_mime_type")
    var timeUserLastObservedMimeType: Long = -1,

    //number of objects (chat messages only atm) that reference this mime type, when it hits 0 the file this references
    // as well as this database entry is deleted
    @ColumnInfo(name = "number_references")
    var numberOfReferences: Int = 0,

    //height of mime type in pixels
    @ColumnInfo(name = "mime_type_height")
    var mimeTypeHeight: Int = 0,

    //width of mime type in pixels
    @ColumnInfo(name = "mime_type_width")
    var mimeTypeWidth: Int = 0,

    //the text representing the mime type ex: image/png
    @ColumnInfo(name = "mime_type_value")
    var mimeTypeValue: String = "",

    //timestamp mime type was stored or updated last
    @ColumnInfo(name = "mime_type_timestamp")
    var mimeTypeTimestamp: Long = -1L
)
