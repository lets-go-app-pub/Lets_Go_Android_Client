package site.letsgoapp.letsgo.testingUtility

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediate
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediate
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediate
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediate
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.standAloneObjects.findMatchesObject.FindMatchesObject
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginSupportFunctions
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStartDeleteFileInterface
import site.letsgoapp.letsgo.testingUtility.fakes.FakeStoreErrors
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface

fun loginRepositorySetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher
) {
    ServiceLocator.loginRepository = LoginRepository(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        IconsDaoIntermediate(
            ServiceLocator.iconsDatabase!!.iconsDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        dispatcher
    )
}

fun loginFunctionsSetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.loginFunctions = LoginFunctions(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        IconsDaoIntermediate(
            ServiceLocator.iconsDatabase!!.iconsDatabaseDao
        ),
        UnsentSimpleServerCommandsDaoIntermediate(
            ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        fakeStartDeleteFileInterface,
        dispatcher
    )
}

fun selectPicturesRepositorySetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.selectPicturesRepository = SelectPicturesRepository(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        fakeStartDeleteFileInterface,
        dispatcher,
    )
}

fun selectCategoriesRepositorySetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher
) {
    ServiceLocator.selectCategoriesRepository = SelectCategoriesRepository(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        dispatcher
    )
}

fun applicationRepositorySetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.applicationRepository = ApplicationRepository(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        MimeTypeDaoIntermediate(
            ServiceLocator.messagesDatabase!!.mimeTypeDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        fakeStartDeleteFileInterface,
        dispatcher
    )
}

fun chatStreamObjectSetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.chatStreamObject = ChatStreamObject(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        MimeTypeDaoIntermediate(
            ServiceLocator.messagesDatabase!!.mimeTypeDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        fakeStartDeleteFileInterface,
        dispatcher
    )
}

fun loginSupportFunctionsSetup(
    applicationContext: Context,
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.loginSupportFunctions = LoginSupportFunctions(
        applicationContext,
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        MatchesDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao
        ),
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        UnsentSimpleServerCommandsDaoIntermediate(
            ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        MimeTypeDaoIntermediate(
            ServiceLocator.messagesDatabase!!.mimeTypeDatabaseDao
        ),
        FakeClientSourceIntermediate(),
        fakeStoreErrors,
        fakeStartDeleteFileInterface,
        dispatcher
    )
}

fun chatStreamWorkerRepositorySetup(
    dispatcher: CoroutineDispatcher,
    fakeStoreErrors: StoreErrorsInterface,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.chatStreamWorkerRepository = ChatStreamWorkerRepository(
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        dispatcher
    )
}

fun notificationInfoRepositorySetup(
    dispatcher: CoroutineDispatcher,
    fakeStoreErrors: StoreErrorsInterface,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.notificationInfoRepository = NotificationInfoRepository(
        ChatRoomsDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.chatRoomsDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        dispatcher
    )
}

fun cleanDatabaseWorkerRepositorySetup(
    dispatcher: CoroutineDispatcher,
    fakeStoreErrors: StoreErrorsInterface,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.cleanDatabaseWorkerRepository = CleanDatabaseWorkerRepository(
        AccountInfoDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
        ),
        AccountPictureDaoIntermediate(
            ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
        ),
        MimeTypeDaoIntermediate(
            ServiceLocator.messagesDatabase!!.mimeTypeDatabaseDao
        ),
        MessagesDaoIntermediate(
            ServiceLocator.messagesDatabase!!.messagesDatabaseDao,
            fakeStartDeleteFileInterface,
            fakeStoreErrors
        ),
        OtherUsersDaoIntermediate(
            ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
        ),
        dispatcher
    )
}

fun provideFindMatchesObjectLambdaSetup(
    fakeStoreErrors: FakeStoreErrors,
    dispatcher: CoroutineDispatcher,
    fakeStartDeleteFileInterface: FakeStartDeleteFileInterface
) {
    ServiceLocator.provideFindMatchesObjectLambda =
        { context, sharedApplicationOrLoginViewModelInstanceId ->
            FindMatchesObject(
                context,
                AccountInfoDaoIntermediate(
                    ServiceLocator.accountInfoDatabase!!.accountInfoDatabaseDao
                ),
                AccountPictureDaoIntermediate(
                    ServiceLocator.accountInfoDatabase!!.accountPictureDatabaseDao
                ),
                MatchesDaoIntermediate(
                    ServiceLocator.otherUsersDatabase!!.matchesDatabaseDao
                ),
                OtherUsersDaoIntermediate(
                    ServiceLocator.otherUsersDatabase!!.otherUsersDatabaseDao
                ),
                UnsentSimpleServerCommandsDaoIntermediate(
                    ServiceLocator.messagesDatabase!!.unsentSimpleServerCommandsDatabaseDao
                ),
                FakeClientSourceIntermediate(),
                sharedApplicationOrLoginViewModelInstanceId,
                fakeStoreErrors,
                fakeStartDeleteFileInterface,
                dispatcher
            )
        }
}
