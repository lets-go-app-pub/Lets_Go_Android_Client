package site.letsgoapp.letsgo.databases.accountInfoDatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDatabaseDao
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDatabaseDao
import site.letsgoapp.letsgo.globalAccess.GlobalValues

@Database(
    entities = [AccountInfoDataEntity::class, AccountPictureDataEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AccountInfoDatabase : RoomDatabase() {

    //define more DAO here
    abstract val accountInfoDatabaseDao: AccountInfoDatabaseDao
    abstract val accountPictureDatabaseDao: AccountPictureDatabaseDao

    companion object {

        @Volatile
        private var INSTANCE: AccountInfoDatabase? = null

        fun getDatabaseInstance(context: Context): AccountInfoDatabase {

            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room
                        .databaseBuilder(
                            context.applicationContext,
                            AccountInfoDatabase::class.java,
                            GlobalValues.applicationContext.getString(R.string.database_name_account_info_database)
                        )
                        .addMigrations(ACCOUNT_INFO_DATABASE_MIGRATION_1_2)
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance

                }

                return instance
            }
        }
    }
}

val ACCOUNT_INFO_DATABASE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to the existing table
        database.execSQL("ALTER TABLE account_info_table ADD COLUMN subscription_status INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE account_info_table ADD COLUMN subscription_expiration_time INTEGER NOT NULL DEFAULT -1")
        database.execSQL("ALTER TABLE account_info_table ADD COLUMN opted_in_to_promotional_emails INTEGER NOT NULL DEFAULT 0")
    }
}
