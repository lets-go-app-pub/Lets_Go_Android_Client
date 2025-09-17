package site.letsgoapp.letsgo.databases.otherUsersDatabase.matches

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches_table")
class MatchesDataEntity(

    //account id
    @ColumnInfo(name = "account_oid")
    var accountOID: String = "~",

    //point value
    @ColumnInfo(name = "point_value")
    var pointValue: Double = -1.0,

    //expiration time
    @ColumnInfo(name = "expiration_time")
    var expirationTime: Long = -1L,

    //other users matched
    @ColumnInfo(name = "other_users_matched")
    var otherUserMatched: Boolean = false,

    //swipes remaining
    @ColumnInfo(name = "swipes_remaining")
    var swipesRemaining: Int = -1,

    //swipes time before reset
    @ColumnInfo(name = "swipes_time_before_reset")
    var swipesTimeBeforeReset: Long = -1L,

    //NOTE: when this is set to value 0 the index will be auto-generated, if it
    //is not set to 0 the value set will be used, it is at the end so it can be default this value
    @PrimaryKey(autoGenerate = true)
    var matchIndex: Long = 0L

)// : UserInfo(pictures, name, age, gender, cityName, bio, activities)
{
    override fun equals(other: Any?): Boolean {

        if (other?.javaClass != javaClass) {
            return false
        }

        other as MatchesDataEntity

        if (
            this.accountOID != other.accountOID ||
            this.accountOID != other.accountOID ||
            this.pointValue != other.pointValue ||
            this.expirationTime != other.expirationTime ||
            this.otherUserMatched != other.otherUserMatched ||
            this.swipesRemaining != other.swipesRemaining ||
            this.swipesTimeBeforeReset != other.swipesTimeBeforeReset ||
            this.matchIndex != other.matchIndex
        ) {
            return false
        }

        return true
    }

    //recommended to override hashCode() with operator= (equals())
    override fun hashCode(): Int {
        var result = accountOID.hashCode()
        result = 31 * result + pointValue.hashCode()
        result = 31 * result + expirationTime.hashCode()
        result = 31 * result + otherUserMatched.hashCode()
        result = 31 * result + swipesRemaining
        result = 31 * result + swipesTimeBeforeReset.hashCode()
        result = 31 * result + matchIndex.hashCode()
        return result
    }
}