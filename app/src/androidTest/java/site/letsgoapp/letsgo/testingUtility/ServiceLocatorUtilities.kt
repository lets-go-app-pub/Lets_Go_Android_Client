package site.letsgoapp.letsgo.testingUtility

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.AccountInfoDatabase
import site.letsgoapp.letsgo.databases.iconsDatabase.IconsDatabase
import site.letsgoapp.letsgo.databases.messagesDatabase.MessagesDatabase
import site.letsgoapp.letsgo.databases.otherUsersDatabase.OtherUsersDatabase
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

fun cleanUpAccountInfoDatabase() {
    ServiceLocator.accountInfoDatabase?.close()
    ServiceLocator.accountInfoDatabase = null
}

fun cleanUpIconsDatabase() {
    ServiceLocator.iconsDatabase?.close()
    ServiceLocator.iconsDatabase = null
}

fun cleanUpMessagesDatabase() {
    ServiceLocator.messagesDatabase?.close()
    ServiceLocator.messagesDatabase = null
}

fun cleanUpOtherUsersDatabase() {
    ServiceLocator.otherUsersDatabase?.close()
    ServiceLocator.otherUsersDatabase = null
}

fun cleanServiceLocatorVariables(applicationContext: Context) {
    runBlocking {
        val accountsDb = ServiceLocator.retrieveAccountInfoDatabase(applicationContext)
        accountsDb.accountInfoDatabaseDao.clearTable()
        accountsDb.accountPictureDatabaseDao.clearTable()

        val iconsDb = ServiceLocator.retrieveIconsDatabase(applicationContext)
        iconsDb.iconsDatabaseDao.clearTable()

        val messagesDb = ServiceLocator.retrieveMessagesDatabase(applicationContext)
        messagesDb.messagesDatabaseDao.clearTable()
        messagesDb.mimeTypeDatabaseDao.clearTable()
        messagesDb.unsentSimpleServerCommandsDatabaseDao.clearTable()

        val otherUsersDb = ServiceLocator.retrieveOtherUsersDatabase(applicationContext)
        otherUsersDb.matchesDatabaseDao.clearTable()
        otherUsersDb.chatRoomsDatabaseDao.clearTable()
        otherUsersDb.otherUsersDatabaseDao.clearTable()
    }

    //If these are reset then LoginFunction or ChatStreamWorker can run and
    // create new versions of them. So do not set them between tests, the
    // next test can set them up again if necessary.
//    ServiceLocator.loginRepository = null
//    ServiceLocator.loginFunctions = null
//    ServiceLocator.loginSupportFunctions = null
//    ServiceLocator.chatStreamObject = null
//    ServiceLocator.applicationRepository = null
//    ServiceLocator.chatStreamWorkerRepository = null
//    ServiceLocator.notificationInfoRepository = null
//    ServiceLocator.cleanDatabaseWorkerRepository = null
//    ServiceLocator.selectCategoriesRepository = null
//    ServiceLocator.selectPicturesRepository = null
}

//when using this, don't forget to clean up at the end
fun setupInMemoryAccountInfoDatabase(applicationContext: Context) {
    cleanUpAccountInfoDatabase()

    // Using an in-memory database because the information stored here disappears when the
    // process is killed.
    ServiceLocator.accountInfoDatabase = Room.inMemoryDatabaseBuilder(applicationContext, AccountInfoDatabase::class.java)
        // Allowing main thread queries, just for testing.
        .allowMainThreadQueries()
        .build()
}

//when using this, don't forget to clean up at the end
fun setupInMemoryIconsDatabase(applicationContext: Context) {
    cleanUpIconsDatabase()

    // Using an in-memory database because the information stored here disappears when the
    // process is killed.
    ServiceLocator.iconsDatabase = Room.inMemoryDatabaseBuilder(applicationContext, IconsDatabase::class.java)
        // Allowing main thread queries, just for testing.
        .allowMainThreadQueries()
        .build()
}

//when using this, don't forget to clean up at the end
fun setupInMemoryMessagesDatabase(applicationContext: Context) {
    cleanUpMessagesDatabase()

    // Using an in-memory database because the information stored here disappears when the
    // process is killed.
    ServiceLocator.messagesDatabase = Room.inMemoryDatabaseBuilder(applicationContext, MessagesDatabase::class.java)
        // Allowing main thread queries, just for testing.
        .allowMainThreadQueries()
        .build()
}

//when using this, don't forget to clean up at the end
fun setupInMemoryOtherUsersDatabase(applicationContext: Context) {
    cleanUpOtherUsersDatabase()

    // Using an in-memory database because the information stored here disappears when the
    // process is killed.
    ServiceLocator.otherUsersDatabase = Room.inMemoryDatabaseBuilder(applicationContext, OtherUsersDatabase::class.java)
        // Allowing main thread queries, just for testing.
        .allowMainThreadQueries()
        .build()
}

fun clearSharedPreferences(applicationContext: Context) {
    //Uses (at least, not guaranteed to be up to date).
    // 1) Version number to determine if this is first login for this version.
    // 2) The ChatStreamWorker string to save displayed notifications.
    // 3) Installation Id.
    // 4) Error sent for CleanDatabaseWorker.
    val sharedPreferences = applicationContext.getSharedPreferences(
        applicationContext.resources.getString(R.string.shared_preferences_lets_go_key),
        Context.MODE_PRIVATE
    )
    sharedPreferences.edit().clear().commit()
}