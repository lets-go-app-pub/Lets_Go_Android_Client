package site.letsgoapp.letsgo.databases.otherUsersDatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lets_go_event_status.LetsGoEventStatusOuterClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDatabaseDao
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDatabaseDao
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDatabaseDao
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import user_account_type.UserAccountTypeOuterClass.UserAccountType

@Database(
    entities = [MatchesDataEntity::class, ChatRoomsDataEntity::class, OtherUsersDataEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OtherUsersDatabase : RoomDatabase() {

    //define more DAO here
    abstract val matchesDatabaseDao: MatchesDatabaseDao
    abstract val chatRoomsDatabaseDao: ChatRoomsDatabaseDao
    abstract val otherUsersDatabaseDao: OtherUsersDatabaseDao

    companion object {

        @Volatile
        private var INSTANCE: OtherUsersDatabase? = null

        fun getDatabaseInstance(context: Context): OtherUsersDatabase {

            synchronized(this) {
                var instance = INSTANCE

                GlobalValues.applicationContext.getString(R.string.chat_room_info_fragment_id)
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        OtherUsersDatabase::class.java,
                        GlobalValues.applicationContext.getString(R.string.database_name_other_users_database)
                    )
                        .addMigrations(OTHER_USERS_DATABASE_MIGRATION_1_2)
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance

                    //migrations https://developer.android.com/training/data-storage/room/migrating-db-versions
                    //testing migrations: https://medium.com/androiddevelopers/testing-room-migrations-be93cdb0d975

                }

                return instance
            }
        }
    }
}

val OTHER_USERS_DATABASE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to other_users_table
        database.execSQL("ALTER TABLE other_users_table ADD COLUMN account_type INTEGER NOT NULL DEFAULT ${UserAccountType.USER_ACCOUNT_TYPE.number}")
        database.execSQL("ALTER TABLE other_users_table ADD COLUMN event_status INTEGER NOT NULL DEFAULT ${LetsGoEventStatusOuterClass.LetsGoEventStatus.NOT_AN_EVENT.number}")
        database.execSQL("ALTER TABLE other_users_table ADD COLUMN created_by TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE other_users_table ADD COLUMN event_title TEXT NOT NULL DEFAULT ''")

        // Add new columns to chat_rooms_table
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN event_id TEXT NOT NULL DEFAULT '${GlobalValues.server_imported_values.eventIdDefault}'")
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN qr_code_path TEXT NOT NULL DEFAULT '${GlobalValues.server_imported_values.qrCodeDefault}'")
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN qr_code_message TEXT NOT NULL DEFAULT '${GlobalValues.server_imported_values.qrCodeMessageDefault}'")
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN qr_code_time_updated INTEGER NOT NULL DEFAULT ${GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault}")
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN pinned_location_longitude REAL NOT NULL DEFAULT ${GlobalValues.server_imported_values.pinnedLocationDefaultLongitude}")
        database.execSQL("ALTER TABLE chat_rooms_table ADD COLUMN pinned_location_latitude REAL NOT NULL DEFAULT ${GlobalValues.server_imported_values.pinnedLocationDefaultLatitude}")
    }
}
