package site.letsgoapp.letsgo.globalAccess

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModelFactory
import site.letsgoapp.letsgo.databases.accountInfoDatabase.AccountInfoDatabase
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediate
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediate
import site.letsgoapp.letsgo.databases.iconsDatabase.IconsDatabase
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.MessagesDatabase
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.OtherUsersDatabase
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediate
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModelFactory
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamSubscriberWrapper
import site.letsgoapp.letsgo.standAloneObjects.findMatchesObject.FindMatchesObject
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginSupportFunctions
import site.letsgoapp.letsgo.utilities.*

object ServiceLocator {
    @Volatile
    var accountInfoDatabase: AccountInfoDatabase? = null
        @VisibleForTesting set

    @Volatile
    var iconsDatabase: IconsDatabase? = null
        @VisibleForTesting set

    @Volatile
    var messagesDatabase: MessagesDatabase? = null
        @VisibleForTesting set

    @Volatile
    var otherUsersDatabase: OtherUsersDatabase? = null
        @VisibleForTesting set

    @Volatile
    var loginRepository: LoginRepository? = null
        @VisibleForTesting set

    @Volatile
    var loginFunctions: LoginFunctions? = null
        @VisibleForTesting set

    @Volatile
    var loginSupportFunctions: LoginSupportFunctions? = null
        @VisibleForTesting set

    @Volatile
    var chatStreamObject: ChatStreamObject? = null
        @VisibleForTesting set

    @Volatile
    var applicationRepository: ApplicationRepository? = null
        @VisibleForTesting set

    @Volatile
    var chatStreamWorkerRepository: ChatStreamWorkerRepository? = null
        @VisibleForTesting set

    @Volatile
    var notificationInfoRepository: NotificationInfoRepository? = null
        @VisibleForTesting set

    @Volatile
    var cleanDatabaseWorkerRepository: CleanDatabaseWorkerRepository? = null
        @VisibleForTesting set

    @Volatile
    var selectCategoriesRepository: SelectCategoriesRepository? = null
        @VisibleForTesting set

    @Volatile
    var selectPicturesRepository: SelectPicturesRepository? = null
        @VisibleForTesting set

    @Volatile
    var provideFindMatchesObjectLambda: (Context, String) -> FindMatchesObject =
        { context, sharedApplicationOrLoginViewModelInstanceId ->
            FindMatchesObject(
                context,
                createAccountInfoDataSource(context),
                createAccountPicturesDataSource(context),
                createMatchesDataSource(context),
                createOtherUsersDataSource(context),
                createUnsentSimpleServerCommandsDataSource(context),
                ClientsSourceIntermediate(),
                sharedApplicationOrLoginViewModelInstanceId,
                StoreErrors(),
                testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext)
            )
        }
        @VisibleForTesting set

    @Volatile
    var globalIODispatcher: CoroutineDispatcher = Dispatchers.IO
        @VisibleForTesting set

    @Volatile
    var testingDeleteFileInterface: StartDeleteFileInterface? = null
        @VisibleForTesting set

    @Volatile
    var globalErrorStore: StoreErrorsInterface = StoreErrors()
        @VisibleForTesting set

    @Volatile
    var deviceIdleOrConnectionDown: DeviceIdleOrConnectionDownCheckerInterface =
        DeviceIdleOrConnectionDownChecker()
        @VisibleForTesting set

    fun provideSharedLoginViewModelFactory(applicationContext: Context): SharedLoginViewModelFactory {
        synchronized(this) {
            return SharedLoginViewModelFactory(
                provideLoginRepository(applicationContext),
                provideLoginSupportFunctions(applicationContext),
                provideSelectPicturesRepository(applicationContext),
                setupStoreErrorsInterface(),
                globalIODispatcher
            )
        }
    }

    fun provideSharedApplicationViewModelFactory(applicationContext: Context): SharedApplicationViewModelFactory {
        synchronized(this) {
            return SharedApplicationViewModelFactory(
                provideApplicationRepository(applicationContext),
                provideChatStreamObject(applicationContext),
                provideSelectPicturesRepository(applicationContext),
                provideSelectCategoriesRepository(applicationContext),
                provideLoginSupportFunctions(applicationContext),
                setupStoreErrorsInterface(),
                globalIODispatcher,
            )
        }
    }

    fun provideLoginRepository(context: Context): LoginRepository {
        synchronized(this) {
            return loginRepository ?: createLoginRepository(context)
        }
    }

    private fun createLoginRepository(context: Context): LoginRepository {
        val newRepo = LoginRepository(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createIconsDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            globalIODispatcher
        )
        loginRepository = newRepo
        return newRepo
    }

    fun provideLoginFunctions(context: Context): LoginFunctions {
        synchronized(this) {
            Log.i("load_balance_stuff", "provideLoginFunctions() isNull: ${loginFunctions == null}")
            return loginFunctions ?: createLoginFunctions(context)
        }
    }

    private fun createLoginFunctions(context: Context): LoginFunctions {
        val newRepo = LoginFunctions(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createIconsDataSource(context),
            createUnsentSimpleServerCommandsDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            globalIODispatcher
        )
        loginFunctions = newRepo
        return newRepo
    }

    fun provideFindMatchesObject(
        context: Context,
        sharedApplicationOrLoginViewModelInstanceId: String
    ): FindMatchesObject {
        return provideFindMatchesObjectLambda(
            context,
            sharedApplicationOrLoginViewModelInstanceId
        )
    }

    fun provideLoginSupportFunctions(context: Context): LoginSupportFunctions {
        synchronized(this) {
            return loginSupportFunctions ?: createLoginSupportFunctions(context)
        }
    }

    private fun createLoginSupportFunctions(context: Context): LoginSupportFunctions {
        val newRepo = LoginSupportFunctions(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createMatchesDataSource(context),
            createChatRoomsDataSource(context),
            createMessagesDataSource(context),
            createUnsentSimpleServerCommandsDataSource(context),
            createOtherUsersDataSource(context),
            createMimeTypeDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            globalIODispatcher
        )
        loginSupportFunctions = newRepo
        return newRepo
    }

    fun chatStreamWorkerSubscribeToChatStreamObject(
        context: Context,
        initialSubscriber: ChatStreamSubscriberWrapper? = null,
    ) {
        synchronized(this) {
            if (chatStreamObject == null) {
                createChatStreamObject(context)
            }

            CoroutineScope(globalIODispatcher).launch {
                chatStreamObject!!.subscribe(
                    initialSubscriber,
                    ChatStreamObject.SubscriberType.CHAT_STREAM_WORKER_SUBSCRIBER
                )
            }
        }
    }

    fun provideChatStreamObjectOrNull(): ChatStreamObject? {
        return chatStreamObject
    }

    fun provideChatStreamObject(context: Context): ChatStreamObject {
        synchronized(this) {
            return chatStreamObject ?: createChatStreamObject(context)
        }
    }

    private fun createChatStreamObject(context: Context): ChatStreamObject {

        Log.i(
            "server_not_fake",
            printStackTraceForErrors()
        )

        val newRepo = ChatStreamObject(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createChatRoomsDataSource(context),
            createMessagesDataSource(context),
            createOtherUsersDataSource(context),
            createMimeTypeDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            globalIODispatcher
        )

        chatStreamObject = newRepo
        return newRepo
    }

    fun provideApplicationRepository(context: Context): ApplicationRepository {
        synchronized(this) {
            return applicationRepository ?: createApplicationRepository(context)
        }
    }

    private fun createApplicationRepository(context: Context): ApplicationRepository {
        val newRepo = ApplicationRepository(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createChatRoomsDataSource(context),
            createMessagesDataSource(context),
            createOtherUsersDataSource(context),
            createMimeTypeDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            globalIODispatcher
        )
        applicationRepository = newRepo
        return newRepo
    }

    fun provideChatStreamWorkerRepository(context: Context): ChatStreamWorkerRepository {
        synchronized(this) {
            return chatStreamWorkerRepository ?: createChatStreamWorkerRepository(
                context
            )
        }
    }

    fun provideNotificationInfoRepository(context: Context): NotificationInfoRepository {
        synchronized(this) {
            return notificationInfoRepository ?: createNotificationInfoRepository(
                context
            )
        }
    }

    fun provideCleanDatabaseWorkerRepository(context: Context): CleanDatabaseWorkerRepository {
        synchronized(this) {
            return cleanDatabaseWorkerRepository ?: createCleanDatabaseWorkerRepository(
                context
            )
        }
    }

    private fun createChatStreamWorkerRepository(context: Context): ChatStreamWorkerRepository {
        val newRepo = ChatStreamWorkerRepository(
            createChatRoomsDataSource(context),
            createMessagesDataSource(context),
            createOtherUsersDataSource(context),
            globalIODispatcher
        )
        chatStreamWorkerRepository = newRepo
        return newRepo
    }

    private fun createNotificationInfoRepository(context: Context): NotificationInfoRepository {
        val newRepo = NotificationInfoRepository(
            createChatRoomsDataSource(context),
            createMessagesDataSource(context),
            createOtherUsersDataSource(context),
            globalIODispatcher
        )
        notificationInfoRepository = newRepo
        return newRepo
    }

    private fun createCleanDatabaseWorkerRepository(context: Context): CleanDatabaseWorkerRepository {
        val newRepo = CleanDatabaseWorkerRepository(
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            createMimeTypeDataSource(context),
            createMessagesDataSource(context),
            createOtherUsersDataSource(context),
            createChatRoomsDataSource(context),
            globalIODispatcher
        )
        cleanDatabaseWorkerRepository = newRepo
        return newRepo
    }

    fun provideSelectCategoriesRepository(context: Context): SelectCategoriesRepository {
        synchronized(this) {
            return selectCategoriesRepository ?: createSelectCategoriesRepository(context)
        }
    }

    private fun createSelectCategoriesRepository(context: Context): SelectCategoriesRepository {
        val newRepo = SelectCategoriesRepository(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            globalIODispatcher
        )
        selectCategoriesRepository = newRepo
        return newRepo
    }

    fun provideSelectPicturesRepository(context: Context): SelectPicturesRepository {
        synchronized(this) {
            return selectPicturesRepository ?: createSelectPicturesRepository(context)
        }
    }

    private fun createSelectPicturesRepository(context: Context): SelectPicturesRepository {
        val newRepo = SelectPicturesRepository(
            context,
            createAccountInfoDataSource(context),
            createAccountPicturesDataSource(context),
            ClientsSourceIntermediate(),
            StoreErrors(),
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            globalIODispatcher
        )
        selectPicturesRepository = newRepo
        return newRepo
    }

    fun provideTransactionWrapper(
        context: Context,
        vararg databases: DatabasesToRunTransactionIn
    ): TransactionWrapper {
        return TransactionWrapper(
            retrieveAccountInfoDatabase(context),
            retrieveIconsDatabase(context),
            retrieveMessagesDatabase(context),
            retrieveOtherUsersDatabase(context),
            *databases
        )
    }

    @VisibleForTesting
    fun retrieveAccountInfoDatabase(context: Context): AccountInfoDatabase {
        if (accountInfoDatabase == null) {
            accountInfoDatabase = AccountInfoDatabase.getDatabaseInstance(context)
        }

        return accountInfoDatabase!!
    }

    @VisibleForTesting
    fun retrieveIconsDatabase(context: Context): IconsDatabase {
        if (iconsDatabase == null) {
            iconsDatabase = IconsDatabase.getDatabaseInstance(context)
        }

        return iconsDatabase!!
    }

    @VisibleForTesting
    fun retrieveMessagesDatabase(context: Context): MessagesDatabase {
        if (messagesDatabase == null) {
            messagesDatabase = MessagesDatabase.getDatabaseInstance(context)
        }

        return messagesDatabase!!
    }

    @VisibleForTesting
    fun retrieveOtherUsersDatabase(context: Context): OtherUsersDatabase {
        if (otherUsersDatabase == null) {
            otherUsersDatabase = OtherUsersDatabase.getDatabaseInstance(context)
        }

        return otherUsersDatabase!!
    }

    private fun createAccountInfoDataSource(context: Context): AccountInfoDaoIntermediate {
        return AccountInfoDaoIntermediate(
            retrieveAccountInfoDatabase(context).accountInfoDatabaseDao
        )
    }

    private fun createAccountPicturesDataSource(context: Context): AccountPictureDaoIntermediate {
        retrieveOtherUsersDatabase(context)
        return AccountPictureDaoIntermediate(
            retrieveAccountInfoDatabase(context).accountPictureDatabaseDao
        )
    }

    private fun createIconsDataSource(context: Context): IconsDaoIntermediate {
        return IconsDaoIntermediate(
            retrieveIconsDatabase(context).iconsDatabaseDao,
            globalIODispatcher
        )
    }

    private fun createMatchesDataSource(context: Context): MatchesDaoIntermediate {
        return MatchesDaoIntermediate(
            retrieveOtherUsersDatabase(context).matchesDatabaseDao
        )
    }

    private fun createChatRoomsDataSource(context: Context): ChatRoomsDaoIntermediate {
        return ChatRoomsDaoIntermediate(
            retrieveOtherUsersDatabase(context).chatRoomsDatabaseDao
        )
    }

    private fun createMessagesDataSource(context: Context): MessagesDaoIntermediate {
        return MessagesDaoIntermediate(
            retrieveMessagesDatabase(context).messagesDatabaseDao,
            testingDeleteFileInterface ?: StartDeleteFile(context.applicationContext),
            StoreErrors(),
            globalIODispatcher
        )
    }

    private fun createOtherUsersDataSource(context: Context): OtherUsersDaoIntermediate {
        return OtherUsersDaoIntermediate(
            retrieveOtherUsersDatabase(context).otherUsersDatabaseDao
        )
    }

    private fun createMimeTypeDataSource(context: Context): MimeTypeDaoIntermediate {
        return MimeTypeDaoIntermediate(
            retrieveMessagesDatabase(context).mimeTypeDatabaseDao
        )
    }

    private fun createUnsentSimpleServerCommandsDataSource(context: Context): UnsentSimpleServerCommandsDaoIntermediate {
        return UnsentSimpleServerCommandsDaoIntermediate(
            retrieveMessagesDatabase(context).unsentSimpleServerCommandsDatabaseDao
        )
    }

}