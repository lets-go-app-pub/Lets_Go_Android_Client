package site.letsgoapp.letsgo.databases.iconsDatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDatabaseDao
import site.letsgoapp.letsgo.globalAccess.GlobalValues

@Database(entities = [IconsDataEntity::class], version = 1, exportSchema = false)
abstract class IconsDatabase : RoomDatabase() {

    //define more DAO here
    abstract val iconsDatabaseDao: IconsDatabaseDao

    companion object {

        @Volatile
        private var INSTANCE: IconsDatabase? = null

        fun getDatabaseInstance(context: Context): IconsDatabase {

            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        IconsDatabase::class.java,
                        GlobalValues.applicationContext.getString(R.string.database_name_icons_database)
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