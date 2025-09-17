package site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import requestmessages.RequestMessages

@Entity(tableName = "account_pictures_table")
data class AccountPictureDataEntity(

    /** NOTE: The default constructor with only pictureIndex set represents a
    deleted picture. It is set at [AccountPictureDaoIntermediate.removeSinglePicture]
    and inside testing {LoginFunctionsTest.checkServerInfoMatchesDatabaseInfo}.
    This is different than the way the server indicates deleted pictures. The server
    will return a [RequestMessages.PictureMessage] with the file_in_bytes set to "~",
    the file_size set to 1 and the timestamp set to -1.**/

    @PrimaryKey(autoGenerate = false)
    var pictureIndex: Int,

    //file path to the picture
    @ColumnInfo(name = "picture_path")
    var picturePath: String = "",

    //size of the picture in bytes
    @ColumnInfo(name = "picture_size")
    var pictureSize: Int = 0,

    //timestamp picture was stored, NOTE: also use as ObjectKey for Glide
    @ColumnInfo(name = "picture_timestamp")
    var pictureTimestamp: Long = -1L
) {

    constructor() : this(
        -1, "", -1, -1L
    )

    override fun toString(): String {
        return "~Account Info Data Entity Values~\n" +
                "pictureIndex: $pictureIndex\n" +
                "picturePath: $picturePath\n" +
                "pictureSize: $pictureSize\n" +
                "pictureTimestamp: $pictureTimestamp\n"
    }

}