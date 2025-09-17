package site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** These store Grpc commands to the server, if the message fails to be sent for some reason such as a bad
 * connection then they will be stored here. When a login is successfully completed they will each be attempted
 * to be sent again ONCE. **/
@Entity(tableName = "unsent_user_match_options")
class UnsentSimpleServerCommandsDataEntity(

    //type of command to send to the server, follows UnsentMessageCommandType enum
    @ColumnInfo(name = "command_type")
    var commandType: Int,

    //protobuf request message in bytes
    @ColumnInfo(name = "protobuf_string", typeAffinity = ColumnInfo.BLOB)
    var protobufBytes: ByteArray,

    //NOTE: must be set to 0, this will tell room that it is 'not set' (could also make it nullable)
    //NOTE: indexing starts at 1, there is no index 0
    @PrimaryKey(autoGenerate = true)
    var index: Long = 0
)

enum class UnsentMessageCommandType {
    UNSET,
    USER_MATCH_OPTION;

    companion object {
        fun forNumber(value: Int): UnsentMessageCommandType {
            return when (value) {
                1 -> {
                    USER_MATCH_OPTION
                }
                else -> {
                    UNSET
                }
            }
        }
    }
}

