package site.letsgoapp.letsgo.databases.messagesDatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDatabaseDao
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDatabaseDao
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDatabaseDao
import site.letsgoapp.letsgo.globalAccess.GlobalValues

@Database(
    entities = [MessagesDataEntity::class, MimeTypeDataEntity::class, UnsentSimpleServerCommandsDataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MessagesDatabase : RoomDatabase() {

    //define more DAO here
    abstract val messagesDatabaseDao: MessagesDatabaseDao
    abstract val mimeTypeDatabaseDao: MimeTypeDatabaseDao
    abstract val unsentSimpleServerCommandsDatabaseDao: UnsentSimpleServerCommandsDatabaseDao

    companion object {

        @Volatile
        private var INSTANCE: MessagesDatabase? = null

        fun getDatabaseInstance(context: Context): MessagesDatabase {

            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        MessagesDatabase::class.java,
                        GlobalValues.applicationContext.getString(R.string.database_name_messages_database)
                    )
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