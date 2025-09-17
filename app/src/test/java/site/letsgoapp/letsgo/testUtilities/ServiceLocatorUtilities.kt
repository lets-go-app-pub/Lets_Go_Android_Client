package site.letsgoapp.letsgo.testUtilities

import android.content.Context
import androidx.room.Room
import site.letsgoapp.letsgo.databases.accountInfoDatabase.AccountInfoDatabase
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

fun cleanUpAccountInfoDatabase() {
    ServiceLocator.accountInfoDatabase?.close()
    ServiceLocator.accountInfoDatabase = null
}

//when using this, don't forget to clean up at the end
fun setupInMemoryAccountInfoDatabase(applicationContext: Context) {
    cleanUpAccountInfoDatabase()

    ServiceLocator.accountInfoDatabase = Room.inMemoryDatabaseBuilder(applicationContext, AccountInfoDatabase::class.java)
        // Allowing main thread queries, just for testing.
        .allowMainThreadQueries()
        .build()
}