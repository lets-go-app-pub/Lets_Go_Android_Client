package site.letsgoapp.letsgo.standAloneObjects.loginFunctions

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands.UnsentSimpleServerCommandsDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.repositories.runDeleteClient
import site.letsgoapp.letsgo.repositories.runLogoutClient
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.workers.chatStreamWorker.ChatStreamWorker
import site.letsgoapp.letsgo.workers.deleteFileWorker.DeleteFileWorker
import kotlin.concurrent.withLock

class LoginSupportFunctions(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val matchesDaoDataSource: MatchesDaoIntermediateInterface,
    private val chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    private val messagesDaoDataSource: MessagesDaoIntermediateInterface,
    private val unsentSimpleServerCommandsDataSource: UnsentSimpleServerCommandsDaoIntermediateInterface,
    private val otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    private val mimeTypeDaoDataSource: MimeTypeDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    //NOTE: this returns a Unit because logout() is used when unmanageable errors occur so the function
    // must respond
    private val _logoutFunctionCompleted: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val logoutFunctionCompleted: LiveData<EventWrapper<Unit>> =
        _logoutFunctionCompleted

    private val _deleteFunctionCompleted: MutableLiveData<EventWrapper<GrpcFunctionErrorStatusEnum>> =
        MutableLiveData()
    val deleteFunctionCompleted: LiveData<EventWrapper<GrpcFunctionErrorStatusEnum>> =
        _deleteFunctionCompleted

    private val _clearAllUserDataAndStopObjectsCompleted: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val clearAllUserDataAndStopObjectsCompleted: LiveData<EventWrapper<Unit>> =
        _clearAllUserDataAndStopObjectsCompleted

    //this function will send a signal to the server to log out and clear all data in the account database
    //if the user is not logged in it will just clear the database
    suspend fun runLogoutFunction(
        calledFromLoginFunctions: Boolean = false,
        updateLiveData: Boolean = true
    ) = withContext(ioDispatcher) {

        val returnVal = runLogoutClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            //NOTE: this must be called AFTER the logout otherwise the info required to logout will be cleared
            clearAllUserDataAndStopObjects(calledFromLoginFunctions)

            if (updateLiveData) {
                withContext(Dispatchers.Main) {
                    _logoutFunctionCompleted.value = EventWrapper(Unit)
                }
            }
        }
    }

    //this function will send a signal to the server to log out and clear all data in the account database
    //if the user is not logged in it will just clear the database
    suspend fun runDeleteFunction() = withContext(ioDispatcher) {

        val returnVal = runDeleteClient(
            applicationContext,
            accountInfoDataSource,
            accountPicturesDataSource,
            clientsIntermediate,
            errorHandling,
            ioDispatcher
        )

        if (returnVal != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

            if (returnVal == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                //NOTE: this must be called AFTER delete otherwise the info required to delete will be cleared
                clearAllUserDataAndStopObjects()
            }

            withContext(Dispatchers.Main) {
                _deleteFunctionCompleted.value = EventWrapper(returnVal)
            }
        }
    }

    //Cancel all workers except for error handling.
    @VisibleForTesting
    fun cancelWorkers() {
        //permanently cancel cleaning the database until a user logs back in
        cancelCleanDatabaseWorker(applicationContext)

        //cancel the start chat stream unique work
        cancelStartChatStreamWorker(applicationContext)

        //cancel the chat stream worker
        //NOTE: Deadlock can occur if this is called here.
        //cancelChatStreamWorker()

        //Want deletes to stop so that if a file is named the same thing it won't be deleted
        // after things are re-downloaded.
        WorkManager.getInstance(applicationContext)
            .cancelAllWorkByTag(DeleteFileWorker.DELETE_FILE_WORKER_TAG)

        //Stop all ChatStreamObject workers.
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(ChatStreamObject.CHAT_STREAM_OBJECT_UNIQUE_WORKER_NAME)
    }

    //removes the account completely from client side and stops all running objects
    suspend fun clearAllUserDataAndStopObjects(
        calledFromLoginFunctions: Boolean = false,
        returnLiveData: Boolean = false,
        updateLoginFunctionStatus: Boolean = true
    ) {

        cancelWorkers()

        //cancel the chat stream and clear message queue
        (applicationContext as LetsGoApplicationClass)
            .chatStreamObject
            .apply {
                cancelChatStream()
                clearMessageQueue()
                removeTempChatRoomNoCheck()
            }

        //This condition is because cancelLoginFunctions locks a mutex that is used inside LoginFunctions and
        // can lead to deadlock.
        if (!calledFromLoginFunctions) {
            applicationContext.loginFunctions.cancelLoginFunction(
                "",
                updateLoginFunctionStatus,
                abortAttemptIfRunning = true
            )
        }

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            applicationContext,
            DatabasesToRunTransactionIn.ACCOUNTS,
            DatabasesToRunTransactionIn.MESSAGES,
            DatabasesToRunTransactionIn.OTHER_USERS
        )

        transactionWrapper.runTransaction {
            //Clear all database tables except icons table.
            //NOTE: This must be called AFTER the chat stream is canceled to avoid any new info stored.
            accountInfoDataSource.clearTable()
            accountPicturesDataSource.clearTable()
            matchesDaoDataSource.clearTable()
            chatRoomsDaoDataSource.clearTable()
            messagesDaoDataSource.clearAllMessagesButNotFiles()
            unsentSimpleServerCommandsDataSource.clearTable()
            otherUsersDaoDataSource.clearTable()
            mimeTypeDaoDataSource.clearTable()
        }

        clearAllFilesHelper(
            applicationContext,
            deleteFileInterface
        )

        GlobalValues.blockedAccounts.clear()

        val sharedPreferences = applicationContext.getSharedPreferences(
            applicationContext.resources.getString(R.string.shared_preferences_lets_go_key),
            Context.MODE_PRIVATE
        )

        //Reset tutorial to be shown next time.
        sharedPreferences.edit().putBoolean(
            applicationContext.resources.getString(R.string.shared_preferences_tutorial_shown_key),
            false
        ).apply()

        if (returnLiveData) {
            withContext(Dispatchers.Main) {
                _clearAllUserDataAndStopObjectsCompleted.value = EventWrapper(Unit)
            }
        }
    }
}