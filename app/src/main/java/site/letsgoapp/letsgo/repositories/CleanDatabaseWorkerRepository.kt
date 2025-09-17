package site.letsgoapp.letsgo.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypesFilePathsAndObservedTime
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessageFieldsForTrimming
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessageFieldsWithFileNames
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomFileInfo
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsDaoIntermediate
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUserFieldsForTrimming
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUserFilePaths
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDaoIntermediateInterface

class CleanDatabaseWorkerRepository(
    private val accountInfoDaoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDaoDataSource: AccountPictureDaoIntermediateInterface,
    private val mimeTypeDaoDataSource: MimeTypeDaoIntermediateInterface,
    private val messagesDaoDataSource: MessagesDaoIntermediateInterface,
    private val otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    private val chatRoomDaoDataSource: ChatRoomsDaoIntermediate,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getAllBlockedAccounts(): MutableSet<String> {
        return accountInfoDaoDataSource.getBlockedAccounts()
    }

    /* ------------ GET DATA THAT WAS SENT BY THE ACCOUNT OIDs AND CAN BE TRIMMED ------------ */

    suspend fun getAllMessagesSentByAccountOIDsThatCanBeTrimmed(sentByAccountOIDS: List<String>): List<MessageFieldsForTrimming> {
        return messagesDaoDataSource.retrieveMessagesSentByUsersThatCanBeTrimmed(sentByAccountOIDS)
    }

    suspend fun getOtherUsersInListThatCanBeTrimmed(sentByAccountOIDS: List<String>): List<OtherUserFieldsForTrimming> {
        return otherUsersDaoDataSource.getOtherUsersInListThatCanBeTrimmed(sentByAccountOIDS)
    }

    /* ------------ GET DATA THAT WAS SENT BY ANYTHING EXCEPT THE ACCOUNT OIDs AND CAN BE TRIMMED ------------ */

    suspend fun retrieveMessagesThatCanBeTrimmed(notMessageUUIDs: List<String>): List<MessageFieldsForTrimming> {
        return messagesDaoDataSource.retrieveMessagesThatCanBeTrimmed(notMessageUUIDs)
    }

    suspend fun getUsersThatCanBeTrimmed(notSentByAccountOIDS: List<String>): List<OtherUserFieldsForTrimming> {
        return otherUsersDaoDataSource.getUsersThatCanBeTrimmed(notSentByAccountOIDS)
    }

    suspend fun getMimeTypesThatCanBeTrimmed(notMimeTypeUrls: List<String>): List<MimeTypesFilePathsAndObservedTime> {
        return mimeTypeDaoDataSource.getMimeTypesThatCanBeTrimmed(notMimeTypeUrls)
    }

    /* ------------ GET DATA THAT HAS NOT BEEN OBSERVED RECENTLY ------------ */

    suspend fun retrieveMessagesNotObservedRecently(): List<MessageFieldsForTrimming> {
        return messagesDaoDataSource.retrieveMessagesNotObservedRecently()
    }

    suspend fun getUsersNotObservedRecentlyThatCanBeTrimmed(): List<OtherUserFieldsForTrimming> {
        return otherUsersDaoDataSource.getUsersNotObservedRecentlyThatCanBeTrimmed()
    }

    suspend fun getMimeTypesNotObservedRecentlyThatCanBeTrimmed(): List<MimeTypesFilePathsAndObservedTime> {
        return mimeTypeDaoDataSource.getMimeTypesNotObservedRecentlyThatCanBeTrimmed()
    }

    /* ------------ TRIM DATA ------------ */

    suspend fun setMessagesInListToTrimmed(primaryMessageUUIDs: List<String>) {
        messagesDaoDataSource.setMessagesInListToTrimmed(primaryMessageUUIDs)
    }

    suspend fun setOtherUsersInListToTrimmed(accountOIDs: List<String>) {
        return otherUsersDaoDataSource.setOtherUsersInListToTrimmed(accountOIDs)
    }

    suspend fun setMimeTypesInListToTrimmed(mimeTypeUrls: List<String>) {
        return mimeTypeDaoDataSource.setMimeTypesInListToTrimmed(mimeTypeUrls)
    }

    /* ------------ GET FILES FOR CHECKING FOR MEMORY LEAKS ------------ */

    suspend fun retrieveAllAccountPictureFilePaths(): MutableList<AccountPictureDataEntity> {
        return accountPicturesDaoDataSource.getAllPictures()
    }

    suspend fun retrieveMessageFilePaths(): List<MessageFieldsWithFileNames> {
        return messagesDaoDataSource.retrieveMessageFilePaths()
    }

    suspend fun retrieveMimeTypesAllFilePaths(): List<MimeTypesFilePathsAndObservedTime> {
        return mimeTypeDaoDataSource.retrieveAllFilePaths()
    }

    suspend fun retrieveOtherUserAllFilePaths(): List<OtherUserFilePaths> {
        return otherUsersDaoDataSource.getOtherUsersFilePaths()
    }

    suspend fun retrieveChatRoomFilePaths(): List<ChatRoomFileInfo> {
        return chatRoomDaoDataSource.getAllChatRoomFiles()
    }
}