package site.letsgoapp.letsgo.workers.cleanDatabaseWorker

import android.content.Context
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypesFilePathsAndObservedTime
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessageFieldsForTrimming
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUserFieldsForTrimming
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.CleanDatabaseWorkerRepository
import site.letsgoapp.letsgo.repositories.StartDeleteFile
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import type_of_chat_message.TypeOfChatMessageOuterClass
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

//TODO: do documentation for this file, some NOTES for it are listed below (some may be obsolete).
//-MEMORY MANAGEMENT
//--NOTE: not incorporating checking remaining storage (for example only 1Mb of free space left in android storage) into my calculation for cleaning because the
//-- cache has already been cleared at that point which contains anything I might remove from the file system during cleanup
//--CleanDatabaseWorker should be able to run WHILE the user is working in the app; UserInfoCard and ChatRoomFragment store their own copies of info and Glide
//-- stores copies of pictures, so even if something is removed while the user is viewing it, it shouldn't crash or anything (there is a fairly long running transaction
//-- inside of it so it make slow down the app a bit)
//--Check for memory leak
//-- 1) extract all file paths from database (there are 6 in total not including the temporary files)
//-- 2) check if any files exist inside the file system that do not exist inside the database, if they do remove them
//--Cleaning
//---1) find all blocked accountOIDS
//--2) get all blocked accounts (other users) and messages sent by blocked accounts and save them for trimming
//--3) get all other users, members and mime types that have not been observed in a specific amount of time and save them for trimming
//--4) calculate max storage app should take up (if (10% of total storage) < 124Mb, allocate 124Mb; else if (10% of total storage) > 5Gb, allocate 5Gb; else allocate 10% of total storage)
//--5) check if the amount of storage taken up by the app is greater than calculated max storage, if it is clear messages, other users and mimetypes (by oldest observed time first) until
//-- they have all been cleared OR 10% below the calculated max storage)
//--6) send database commands to trim other users, messages and mimetypes then delete files
//--Client memory design
//---Messages, other users and mime types can all have specific values (files stored in the file system and message_text for TEXT_MESSAGE types) be cleared either when
//-- 1) data has not been observed in a certain amount of time (2 weeks I believe is default)
//-- 2) the file system is getting too large
//---Other users can remove the pictures (the string from the database and the individual pictures)
//---Mime types can remove the file path (this is downloaded from a url through Glide in the ChatMessageAdapter)
//---Messages are a bit more complex however they have 2 fields that can be removed file_path and message_text and all reply fields (except is_reply) can be removed as well
//----the system works with amountOfMessage (used for trimming existing messages) and hasCompleteInfo (used for downloading messages w/o complete info)
//----amountOfMessage will be set to the LOWEST possible value, this way the message can just be checked to see if amountOfMessage != SKELETON_ONLY to see if it can be trimmed
//----hasCompleteInfo is set to true
//----in reality only the 'active' messages (text, picture, invite, location and mime type) have other values of amountOfMessage then SKELETON_ONLY, and of those
//-- invite, location and mime type only have the reply which can be trimmed and downloaded
//----is_reply is an important field because on invite, location and mime type
//-- if a message is a reply and not fully downloaded; then it will set the amountOfMessage == SKELETON_ONLY and hasCompleteInfo == false
//-- if a message is a reply and fully downloaded; then it will set amountOfMessage == HAS_COMPLETE_INFO and hasCompleteInfo == true
//-- if a message is not a reply and not fully downloaded; then it will set amountOfMessage == SKELETON_ONLY and hasCompleteInfo == false
//-- if a message is not a reply and fully downloaded; then it will set amountOfMessage == SKELETON_ONLY and hasCompleteInfo == true
//-- -the point of this is that when the message does not have a reply the highest amountOfMessage can reach is SKELETON_ONLY if it DOES have a reply it can
//--  reach HAS_COMPLETE_INFO
//--doing nothing when 'write failed: ENOSPC (No space left on device)', if internal storage is full the device isn't gonna work well anyway, just
//-- logging out
//-SERVER CHAT STREAM SKELETON NOTES
//--the server will send back the InitialConnectionPrimerResponse with the expiration time, followed by messages inside of InitialConnectionPrimerMessages
//--the server will send back trailing metadata when the stream ends containing StreamDownReasons enum, as well as optionally a ReturnStatus or INSTALLATION_ID_OF_CANCELLING_STREAM
//--if the chat stream is shutdown by a different chat stream starting, it will send back a specific
//-- code, this code(stored in StreamDownReasons) means that the user was logged in somewhere else, in order to enforce this, MAKE SURE
//-- NOT TO RECEIVE ANYTHING FROM OLD STREAM IF RESTARTING IT
//--note that if CANCEL is used from the server the message info itself does not seem to be set (not sure if this is relevant for streams)
//--the server will make sure all messages are returned, it is OK if there is a break between client 1 and client 2 running (if say
//-- the internet goes down or the user just needs to refresh the stream)
//--when receiving messages check is_deleted first if the message can be deleted
//--with extremely high delay it is possible for the server to send back time out even if the client refreshed it, make sure to 'allow' for this, and probably
//-- run load balancing again
//-LOCATION MESSAGES
//-Older API setup (for testing and other things as well)
//--(Not sure this works anymore, the app might be out of date that comes with the emulator)
//--Open Google Maps at least once.
//--Turn Location on High Accuracy.
//-if making a lot of location messages inside of a chat room, then quickly scroll up and down (takes ~15-20 seconds) it will crash with
//the below error, however this is the recommended way of implementing the map in the recycler view using lite mode (see links below)
//--https://developers.google.com/maps/documentation/android-sdk/lite#maps_android_lite_mode_options-kotlin
//--https://github.com/googlemaps/android-samples/blob/master/ApiDemos/kotlin/app/src/gms/java/com/example/kotlindemos/LiteListDemoActivity.kt#L178
//--remember that even though it isn't inside of the example the MapView item inside the XML file must have map:liteMode="true" set
//--E/JavaBinder: !!! FAILED BINDER TRANSACTION !!!  (parcel size = 144)
//--E/AndroidRuntime: FATAL EXCEPTION: androidmapsapi-ula-1
//DeadSystemException: The system died; earlier logs will point to the root cause
//-old way used fragments, NOTES
//--NOTE: because I use onViewAttachedToWindow and onViewDetachedFromWindow if the element is updated too quickly there can be UI issues
//where (view A is attached) (view B is attached) (view B is detached) causing whatever was in view A to still be stored
//--if I do not use a unique ID then I will not be able to put the fragment back inside the layout
//however if I use a unique ID then it crashes if it scrolls too fast saying View ID not found, probably because the view is
//reused with the original ID or something and commit takes longer than scrolling and re-using the view
//if I use commitNow it will work but there is a delay, I would LIKE to use commit() however I get problems with this
//if I use another FrameLayout inside bodyFrameLayout then it isn't actually added to the view in time and the new ID is
//again not found ALSO I need to try to avoid commitWithIgnoringStateLoss because the View can go out of the recycle view
//bounds without being destroyed then lose the fragment
//--holder.bodyFrameLayout.id = holder.fragmentContainerID
//--commit may reduce the delay when the location fragment is used however commitNow avoids a bug
//when scrolling past a location message too quickly (for example when a reply is clicked and it navigates
//past this message)
//-childFragmentManager.beginTransaction()
//.add(holder.fragmentContainerID, mapFragment)
//.commitNow()
//-THIS_USER_JOINED_CHAT_ROOM_ The server must always send back a new chat room before sending back the messages (it already does when joining and order is guaranteed with gRPC streams)
//-THIS_USER_JOINED_CHAT_ROOM_FINISHED, THIS_USER_LEFT_CHAT_ROOM & MATCH_CANCELED
//--these message types have a field for 'initial_stream_return' where they will not update the UI in real time if they are changed in any way
//--this can cause a 'problem' where if something happens where the user loses a connection to the server and the connection must be re-established, then the info will not be updated in real time when the chat stream initially connects, this is expected (mostly applicable to ChatRoomListsFragment)
//-RECEIVING MESSAGES WHILE MINIMIZED
//--because liveData is only update when the fragment onStart is called, the messages queue was not getting updated for the chat room when the user would minimize the app
//--in order to fix this the messages list was moved out from inside of the fragment to a new object inside of the SharedApplicationViewModel named ChatRoomContainer
//--messages themselves are received and update the message queue by an object named SendChatMessagesToFragments
//--ChatRoomListsFragment and ChatRoomFragment use this object to receive messages
//--the idea behind this is that the live data inside of this object will not be updated unless the fragment has passed the onStart state, ALSO the messages will only be handled
//-- if only of the fragments has been created
//--a similar object was created for ChatRoomListsFragment and MessengerFragment named ChatRoomsListInfoContainer, however it contains the lists chatRooms & matchesMade
//--the principle of ChatRoomsListInfoContainer is the same, however ChatRoomListsFragment and MessengerFragment can exist together not in a mutual exclusive way
class CleanDatabaseWorker(private val appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val applicationClass: LetsGoApplicationClass = appContext as LetsGoApplicationClass

    //This must be initialized after onCreate is called inside LetsGoApplicationClass (the variable initialization
    // seems to be able to be called for this class before Application onCreate) otherwise
    // GlobalValues.applicationContext will not be initialized.
    private lateinit var cleanDatabaseWorkerRepository: CleanDatabaseWorkerRepository

    private val errorHandler: StoreErrorsInterface = ServiceLocator.globalErrorStore

    private val deleteFileInterface: StartDeleteFileInterface =
        ServiceLocator.testingDeleteFileInterface ?: StartDeleteFile(appContext)

    private var retryingLogin = AtomicBoolean(false)

    //Certain options inside workerRespondToLogin() can run another login causing
    // it to endlessly cycle. This boolean will be set to true if the failedToLogin
    // lambda was called and the worker is waiting to cancel.
    private val cleanDatabaseWorkerCanceled = AtomicBoolean(false)

    private val cleanDatabaseWorkerUUID = UUID.randomUUID()

    override suspend fun doWork(): Result {
        Log.i("CleanDatabaseWorker_", "Started")

        try {

            cleanDatabaseWorkerRepository = applicationClass.cleanDatabaseWorkerRepository

            Log.i(
                "CleanDatabaseWorker_",
                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            var continueClearDatabaseWorker = true
            val coroutineConditionVariable = MakeshiftCoroutineConditionVariable()

            applicationClass.loginFunctions.beginLoginToServerIfNotAlreadyRunning()

            Log.i(
                "CleanDatabaseWorker_",
                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            LoginFunctions.cleanDatabaseWorkerSubscribe(cleanDatabaseWorkerUUID) {

                LoginFunctions.receivedMessage(it)

                if (!cleanDatabaseWorkerCanceled.get()) {
                    workerRespondToLogin(
                        appContext,
                        it,
                        successfullyLoggedIn = {
                            Log.i(
                                "CleanDatabaseWorker_",
                                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
                            )

                            cleanDatabaseWorkerCanceled.set(true)
                            retryingLogin.set(false)

                            try {

                                //only need to be logged in for time, no need to receive any more updates from
                                // LoginFunctions (receiving another LoginFunctionStatus.LoggedIn for example will
                                // cause this to run twice)
                                LoginFunctions.cleanDatabaseWorkerUnsubscribe(
                                    cleanDatabaseWorkerUUID
                                )
                                cleanDatabase()
                            } finally {
                                coroutineConditionVariable.notifyOne()
                            }
                        },
                        failedToLogin = {
                            Log.i(
                                "CleanDatabaseWorker_",
                                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
                            )

                            retryingLogin.set(false)

                            //NOTE: this does NOT continue the clear database worker, however it should be ok.
                            // All of the error messages that failedToLogin() are called for should require this
                            // to fail.
                            continueClearDatabaseWorker = false

                            LoginFunctions.cleanDatabaseWorkerUnsubscribe(cleanDatabaseWorkerUUID)
                            coroutineConditionVariable.notifyOne()
                        },
                        loginFunctionsRetrying = {
                            retryingLogin.set(true)
                        }
                    )
                }
            }

            Log.i(
                "CleanDatabaseWorker_",
                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            //NOTE: Because this condition variable implements a channel.receive() (according to the
            // documentation) to suspend operation here a coRoutine cancel will break this just
            // like it would with delay() or yield().
            //NOTE: It is technically possible for a coroutineConditionVariable.notifyOne() to run BEFORE this
            // wait() is called, however it is unlikely and the MAX TIME will prevent any permanent problems.
            coroutineConditionVariable.wait(MAXIMUM_TIME_CLEAN_DATABASE_WORKER_CAN_RUN)

            Log.i(
                "CleanDatabaseWorker_",
                "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            //start the clean database worker again
            if (continueClearDatabaseWorker) {

                //ExistingWorkPolicy.REPLACE is very important here, when this function is called from the
                // CleanDatabaseWorker it  allows the work the be replaced so that the Unique Work feature
                // can be used
                startCleanDatabaseWorker(applicationContext, ExistingWorkPolicy.REPLACE)
            }

            Log.i("CleanDatabaseWorker_", "Completed")

            return Result.success()
        } catch (e: Exception) {

            Log.i(
                "CleanDatabaseWorker_",
                "finally block line_number: ${Thread.currentThread().stackTrace[2].lineNumber}\nmessage: ${e.message}"
            )

            //if coRoutine was cancelled, propagate it
            if (e is CancellationException)
                throw e

            //The UncaughtExceptionHandler doesn't seem to catch worker exceptions.
            //This will be started inside LoginFunctions using beginUniqueWorkIfNotRunning() if
            // it results in failure.

            val errorMessage =
                "An exception was thrown when CleanDatabaseWorker was run.\n${e.message}"

            errorHandler.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                e.stackTraceToString(),
                errorMessage,
                appContext
            )

            return Result.failure()

        } finally {
            //guarantee that the worker is unsubscribed, otherwise could leak reference
            LoginFunctions.cleanDatabaseWorkerUnsubscribe(cleanDatabaseWorkerUUID)
        }
    }

    enum class FilePathType {
        USER_PICTURE_FILE_PATH,
        PICTURE_MESSAGE_FILE_PATH,
        MESSAGE_REPLY_FILE_PATH,
        MIME_TYPE_FILE_PATH,
        OTHER_USER_PICTURE_FILE_PATH,
        OTHER_USER_THUMBNAIL_FILE_PATH,
        QR_CODE_FILE_PATH,
    }

    data class DatabaseFilePathsList(
        val primaryKey: String,
        val filePath: String,
        val type: FilePathType,
    )

    private suspend fun cleanDatabase() {

        Log.i(
            "CleanDatabaseWorker_",
            "line_number: ${Thread.currentThread().stackTrace[2].lineNumber}"
        )

        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
            appContext,
            DatabasesToRunTransactionIn.ACCOUNTS,
            DatabasesToRunTransactionIn.OTHER_USERS,
            DatabasesToRunTransactionIn.MESSAGES
        )

        transactionWrapper.runTransaction {

            val fileSpaceUsedForAppStorage = checkForMemoryLeaks(this)

            val blockedAccountsList = cleanDatabaseWorkerRepository.getAllBlockedAccounts().toList()

            val otherUsersToCleanMap: MutableMap<String, OtherUserFieldsForTrimming> =
                cleanDatabaseWorkerRepository.getOtherUsersInListThatCanBeTrimmed(
                    blockedAccountsList
                ).associateBy {
                    it.accountOID
                }
                    .toMutableMap()

            val messagesToCleanMap: MutableMap<String, MessageFieldsForTrimming> =
                cleanDatabaseWorkerRepository.getAllMessagesSentByAccountOIDsThatCanBeTrimmed(
                    blockedAccountsList
                ).associateBy {
                    it.messageUUIDPrimaryKey
                }
                    .toMutableMap()

            val mimeTypesToCleanMap = mutableMapOf<String, MimeTypesFilePathsAndObservedTime>()

            //find messages, members and mime types that have not been observed in some time
            val oldMessages = cleanDatabaseWorkerRepository.retrieveMessagesNotObservedRecently()

            val oldMembers =
                cleanDatabaseWorkerRepository.getUsersNotObservedRecentlyThatCanBeTrimmed()

            val oldMimeTypes =
                cleanDatabaseWorkerRepository.getMimeTypesNotObservedRecentlyThatCanBeTrimmed()

            Log.i(
                "CleanDatabaseWorker_",
                "Removing; oldMessages: ${oldMessages.size} oldMembers: ${oldMembers.size} oldMimeTypes: ${oldMimeTypes.size} $"
            )

            //TESTING_NOTE: make sure to test that each of these types can be removed AND downloaded again
            for (message in oldMessages) {
                messagesToCleanMap[message.messageUUIDPrimaryKey] = message
            }

            for (member in oldMembers) {
                otherUsersToCleanMap[member.accountOID] = member
            }

            for (mimeType in oldMimeTypes) {
                mimeTypesToCleanMap[mimeType.mimeTypeUrl] = mimeType
            }

            val percentageOfTotalSpaceInBytes = (appContext.filesDir.totalSpace * .05).toLong()
            val maximumSpaceAllocated =
                when {
                    percentageOfTotalSpaceInBytes <= MINIMUM_STORAGE_SPACE_TO_ALLOCATE -> {
                        MINIMUM_STORAGE_SPACE_TO_ALLOCATE
                    }
                    percentageOfTotalSpaceInBytes >= MAXIMUM_STORAGE_SPACE_TO_ALLOCATE -> {
                        MAXIMUM_STORAGE_SPACE_TO_ALLOCATE
                    }
                    else -> {
                        percentageOfTotalSpaceInBytes
                    }
                }

            //this is approximately the number of bytes slated to be removed, it doesn't take into account things such
            // as 'message.value.replyIsFromMessageText' because all such values should be relatively small
            var approximateTotalBytesBeingRemoved = 0L

            for (message in messagesToCleanMap) {

                message.value.let { messageDataEntity ->
                    //LOCATION_MESSAGE, MIME_TYPE_MESSAGE & INVITE_MESSAGE only involve the reply
                    if (messageDataEntity.reply_is_from_thumbnail_file_path != "") {
                        approximateTotalBytesBeingRemoved += File(messageDataEntity.reply_is_from_thumbnail_file_path).length()
                    }

                    when (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                        messageDataEntity.message_type
                    )) {
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {
                            approximateTotalBytesBeingRemoved += messageDataEntity.message_text.length
                        }
                        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
                            approximateTotalBytesBeingRemoved += File(messageDataEntity.file_path).length()
                        }
                        else -> {
                        }
                    }
                }
            }

            for (member in otherUsersToCleanMap) {
                member.value.let { otherUsersDataEntity ->
                    val previouslyStoredPicturesList =
                        convertPicturesStringToList(otherUsersDataEntity.pictures)

                    for (picture in previouslyStoredPicturesList) {
                        approximateTotalBytesBeingRemoved += File(picture.picturePath).length()
                    }

                    approximateTotalBytesBeingRemoved += otherUsersDataEntity.pictures.length
                }
            }

            for (mimeType in mimeTypesToCleanMap) {
                mimeType.value.let { mimeTypesFilePaths ->
                    approximateTotalBytesBeingRemoved += File(mimeTypesFilePaths.mime_type_file_path).length()
                    approximateTotalBytesBeingRemoved += mimeTypesFilePaths.mime_type_file_path.length
                }
            }

            val approximateFileSpaceUsedAfterFilesRemoved =
                fileSpaceUsedForAppStorage - approximateTotalBytesBeingRemoved

            if (maximumSpaceAllocated < approximateFileSpaceUsedAfterFilesRemoved) { //if app is taking up too much space

                //ideally removing down to ~10% under the maximum amount of memory allocated
                var totalSizeRequiredToBeRemoved =
                    (approximateFileSpaceUsedAfterFilesRemoved - maximumSpaceAllocated) - (maximumSpaceAllocated * .1).toLong()

                Log.i(
                    "CleanDatabaseWorker_",
                    "totalSizeRequiredToBeRemoved: $totalSizeRequiredToBeRemoved"
                )

                val messagesThatCanBeTrimmed =
                    cleanDatabaseWorkerRepository.retrieveMessagesThatCanBeTrimmed(
                        messagesToCleanMap.map {
                            it.key
                        }
                    )

                val otherUsersThatCanBeTrimmed =
                    cleanDatabaseWorkerRepository.getUsersThatCanBeTrimmed(
                        otherUsersToCleanMap.map {
                            it.key
                        }
                    )

                val mimeTypesThatCanBeTrimmed =
                    cleanDatabaseWorkerRepository.getMimeTypesThatCanBeTrimmed(
                        mimeTypesToCleanMap.map {
                            it.key
                        }
                    )

                var messagesIndex = 0
                var otherUsersIndex = 0
                var mimeTypeIndex = 0
                var collectedEnoughInfoToRemove = false

                val setMessageToBeRemoved: (MessageFieldsForTrimming) -> Unit =
                    { messageFieldsForTrimming ->
                        messagesToCleanMap[messageFieldsForTrimming.messageUUIDPrimaryKey] =
                            messageFieldsForTrimming

                        totalSizeRequiredToBeRemoved -= messageFieldsForTrimming.message_text.length

                        if (messageFieldsForTrimming.file_path != "") {
                            totalSizeRequiredToBeRemoved -= File(messageFieldsForTrimming.file_path).length()
                        }

                        if (messageFieldsForTrimming.reply_is_from_thumbnail_file_path != "") {
                            totalSizeRequiredToBeRemoved -= File(messageFieldsForTrimming.reply_is_from_thumbnail_file_path).length()
                        }
                    }

                val setOtherUserToBeRemoved: (OtherUserFieldsForTrimming) -> Unit =
                    { otherUserFieldsForTrimming ->

                        otherUsersToCleanMap[otherUserFieldsForTrimming.accountOID] =
                            otherUserFieldsForTrimming

                        val previouslyStoredPicturesList =
                            convertPicturesStringToList(otherUserFieldsForTrimming.pictures)

                        for (picture in previouslyStoredPicturesList) {
                            totalSizeRequiredToBeRemoved -= File(picture.picturePath).length()
                        }

                        totalSizeRequiredToBeRemoved -= otherUserFieldsForTrimming.pictures.length
                    }

                val setMimeTypeToBeRemoved: (MimeTypesFilePathsAndObservedTime) -> Unit =
                    { mimeTypesFilePathsAndObservedTime ->

                        mimeTypesToCleanMap[mimeTypesFilePathsAndObservedTime.mimeTypeUrl] =
                            mimeTypesFilePathsAndObservedTime

                        if (mimeTypesFilePathsAndObservedTime.mime_type_file_path != "") {
                            totalSizeRequiredToBeRemoved -= File(mimeTypesFilePathsAndObservedTime.mime_type_file_path).length()
                        }
                    }

                //select oldest message, other user or mime type for removal
                while (!collectedEnoughInfoToRemove) {

                    val messageLastObservedTime =
                        if (messagesIndex == messagesThatCanBeTrimmed.size) {
                            GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                        } else {
                            messagesThatCanBeTrimmed[messagesIndex].time_user_last_observed_message
                        }

                    val otherUserLastObservedTime =
                        if (otherUsersIndex == otherUsersThatCanBeTrimmed.size) {
                            GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                        } else {
                            otherUsersThatCanBeTrimmed[otherUsersIndex].user_info_last_observed
                        }

                    val mimeTypeLastObservedTime =
                        if (mimeTypeIndex == mimeTypesThatCanBeTrimmed.size) {
                            GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                        } else {
                            mimeTypesThatCanBeTrimmed[mimeTypeIndex].time_user_last_observed_mime_type
                        }

                    when {
                        messageLastObservedTime == GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                                && otherUserLastObservedTime == GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                                && mimeTypeLastObservedTime == GlobalValues.NUMBER_BIGGER_THAN_UNIX_TIMESTAMP
                        -> { //nothing to remove
                            break
                        }
                        messageLastObservedTime <= otherUserLastObservedTime
                                && messageLastObservedTime <= mimeTypeLastObservedTime
                        -> { //messages is the smallest value
                            setMessageToBeRemoved(messagesThatCanBeTrimmed[messagesIndex])

                            messagesIndex++
                        }
                        otherUserLastObservedTime <= messageLastObservedTime
                                && otherUserLastObservedTime <= mimeTypeLastObservedTime
                        -> { //other users is the smallest value

                            setOtherUserToBeRemoved(otherUsersThatCanBeTrimmed[otherUsersIndex])

                            otherUsersIndex++
                        }
                        else -> { //mime type is the smallest value

                            setMimeTypeToBeRemoved(mimeTypesThatCanBeTrimmed[mimeTypeIndex])

                            mimeTypeIndex++
                        }

                    }

                    if (totalSizeRequiredToBeRemoved <= 0) {
                        collectedEnoughInfoToRemove = true
                    }
                }

                val sharedPreferences = appContext.getSharedPreferences(
                    appContext.getString(R.string.shared_preferences_lets_go_key),
                    MultiDexApplication.MODE_PRIVATE
                )

                val errorForStorageTooFullHasBeenSent =
                    sharedPreferences.getBoolean(
                        appContext.getString(R.string.shared_preferences_storage_error_for_too_full_key),
                        false
                    )

                Log.i(
                    "CleanDatabaseWorker_",
                    "AT END totalSizeRequiredToBeRemoved: $totalSizeRequiredToBeRemoved"
                )
                Log.i(
                    "CleanDatabaseWorker_",
                    "collectedEnoughInfoToRemove: $collectedEnoughInfoToRemove"
                )
                Log.i(
                    "CleanDatabaseWorker_",
                    "errorForStorageTooFullHasBeenSent: $errorForStorageTooFullHasBeenSent"
                )

                if (!collectedEnoughInfoToRemove && !errorForStorageTooFullHasBeenSent) {

                    //This is possible because the current user could have enough information to fill up
                    // their entire allocated storage, however it should be very rare
                    val errorMessage =
                        "Storage was full to the point that it cannot be cleared.\n" +
                                "approximateFileSpaceUsedAfterFilesRemoved: ${approximateFileSpaceUsedAfterFilesRemoved}\n" +
                                "approximateTotalBytesBeingRemoved: ${approximateTotalBytesBeingRemoved}\n" +
                                "maximumSpaceAllocated: ${maximumSpaceAllocated}\n" +
                                "totalSizeRequiredToBeRemoved: ${totalSizeRequiredToBeRemoved}\n"

                    errorHandler.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        appContext
                    )

                    //set this to true, this way until the boolean collectedEnoughInfoToRemove becomes true
                    // the error will not be sent again which will avoid spamming the server
                    sharedPreferences.edit().putBoolean(
                        appContext.getString(R.string.shared_preferences_storage_error_for_too_full_key),
                        true
                    ).apply()
                } else if (collectedEnoughInfoToRemove && errorForStorageTooFullHasBeenSent) {

                    //set this to false, this way until the boolean collectedEnoughInfoToRemove becomes true
                    // the error will not be sent again which will avoid spamming the server
                    sharedPreferences.edit().putBoolean(
                        appContext.getString(R.string.shared_preferences_storage_error_for_too_full_key),
                        false
                    ).apply()
                }
            }

            //TESTING_NOTE: make sure each of the different cases works
            cleanDatabaseWorkerRepository.setMessagesInListToTrimmed(messagesToCleanMap.map { it.key })
            cleanDatabaseWorkerRepository.setOtherUsersInListToTrimmed(otherUsersToCleanMap.map { it.key })
            cleanDatabaseWorkerRepository.setMimeTypesInListToTrimmed(mimeTypesToCleanMap.map { it.key })

            for (otherUser in otherUsersToCleanMap) {

                val previouslyStoredPicturesList =
                    convertPicturesStringToList(otherUser.value.pictures)

                for (picture in previouslyStoredPicturesList) {
                    deleteFileInterface.sendFileToWorkManager(picture.picturePath)
                }
            }

            for (message in messagesToCleanMap) {

                //LOCATION_MESSAGE, MIME_TYPE_MESSAGE & INVITE_MESSAGE only involve the reply
                if (message.value.reply_is_from_thumbnail_file_path != "") {
                    this.runAfterTransaction {
                        deleteFileInterface.sendFileToWorkManager(message.value.reply_is_from_thumbnail_file_path)
                    }
                }

                if (TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                        message.value.message_type
                    ) ==
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE
                ) {
                    this.runAfterTransaction {
                        deleteFileInterface.sendFileToWorkManager(message.value.file_path)
                    }
                }
            }

            for (mimeType in mimeTypesToCleanMap) {

                if (mimeType.value.mimeTypeUrl != "") {
                    this.runAfterTransaction {
                        deleteFileInterface.sendFileToWorkManager(mimeType.value.mime_type_file_path)
                    }
                }
            }

        }

    }

    //checks for files that exist inside of the file system however have no reference to their file path
    // path inside of the database
    //returns the approximateAmountOfStorageUsedByApp (the total file storage used)
    private suspend fun checkForMemoryLeaks(transactionWrapper: TransactionWrapper): Long {

        var fileSpaceUsedForAppStorage = 0L

        transactionWrapper.runTransaction {

            val fileChatPicPrefix =
                applicationContext.getString(R.string.user_picture_chat_message_file_prefix)
            val fileChatGifPrefix =
                applicationContext.getString(R.string.user_mime_type_chat_message_file_prefix)

            val filePicturePrefix =
                applicationContext.getString(R.string.user_picture_file_name_prefix)
            val fileReplyThumbnailPrefix =
                applicationContext.getString(R.string.user_reply_thumbnail_chat_message_file_prefix)
            val fileChatQRCodePrefix =
                applicationContext.getString(R.string.user_mime_type_chat_qr_code_file_prefix)

            val fileOtherUserPicturePrefix =
                applicationContext.getString(R.string.other_user_picture_file_name_prefix)
            val fileOtherUserThumbnailPrefix =
                applicationContext.getString(R.string.other_user_thumbnail_file_name_prefix)

            val fileErrorMessagePrefix =
                applicationContext.getString(R.string.error_worker_file_name_prefix)

            val listOfImageFilePathsFromDirectories = mutableListOf<String>()
            val listOfErrorMessageFilePaths = mutableListOf<String>()

            val fileListNullable = appContext.filesDir.listFiles()

            fileListNullable?.let { filesList ->
                for (file in filesList) {
                    if (file?.exists() == true) {

                        //various other file types exist inside of the directories for example Glide
                        // will occasionally store things
                        if (
                            file.name.matchesPrefix(fileChatPicPrefix)
                            || file.name.matchesPrefix(fileChatQRCodePrefix)
                            || file.name.matchesPrefix(fileChatGifPrefix)
                            || file.name.matchesPrefix(filePicturePrefix)
                            || file.name.matchesPrefix(fileReplyThumbnailPrefix)
                            || file.name.matchesPrefix(fileOtherUserPicturePrefix)
                            || file.name.matchesPrefix(fileOtherUserThumbnailPrefix)
                        ) {
                            listOfImageFilePathsFromDirectories.add(file.absolutePath)
                        } else if (file.name.matchesPrefix(fileErrorMessagePrefix)) {
                            listOfErrorMessageFilePaths.add(file.absolutePath)
                        }

                        fileSpaceUsedForAppStorage += file.length()
                    }
                }
            }

            val cacheListNullable = appContext.cacheDir.listFiles()

            cacheListNullable?.let { cacheList ->
                for (file in cacheList) {
                    if (file?.exists() == true) {

                        //various other file types exist inside of the directories for example Glide
                        // will occasionally store things
                        if (
                            file.name.matchesPrefix(fileChatPicPrefix)
                            || file.name.matchesPrefix(fileChatGifPrefix)
                            || file.name.matchesPrefix(filePicturePrefix)
                            || file.name.matchesPrefix(fileReplyThumbnailPrefix)
                            || file.name.matchesPrefix(fileOtherUserPicturePrefix)
                            || file.name.matchesPrefix(fileOtherUserThumbnailPrefix)
                        ) {
                            listOfImageFilePathsFromDirectories.add(file.absolutePath)
                        } else if (file.name.matchesPrefix(fileErrorMessagePrefix)) {
                            listOfErrorMessageFilePaths.add(file.absolutePath)
                        }

                        fileSpaceUsedForAppStorage += file.length()
                    }
                }
            }

            removeOldUnsentErrorMessages(listOfErrorMessageFilePaths)

            //calculate size of databases
            val databases = appContext.databaseList()

            for (databaseName in databases) {
                fileSpaceUsedForAppStorage += appContext.getDatabasePath(databaseName).length()
            }

            val listOfFilePathsFromDatabase = extractListOfFilePathsFromDatabase()

            listOfImageFilePathsFromDirectories.sort()

            listOfFilePathsFromDatabase.sortBy {
                it.filePath
            }

            var fileNamesForDebugging = ""

            val fileExistsOnlyInDatabase: (DatabaseFilePathsList) -> Unit =
                { databaseFilePathsList ->
                    when (databaseFilePathsList.type) {
                        FilePathType.USER_PICTURE_FILE_PATH,
                        FilePathType.MESSAGE_REPLY_FILE_PATH,
                        -> {
                            if (fileNamesForDebugging.isEmpty()) {
                                fileNamesForDebugging += "Files From Directory\n"
                                for (path in listOfImageFilePathsFromDirectories) fileNamesForDebugging += "$path\n"

                                fileNamesForDebugging += "\nFiles From Database\n"
                                for (path in listOfFilePathsFromDatabase) fileNamesForDebugging += "${path.filePath}\n"
                            }

                            //This is possible because the database is updated before the pictures are saved to file
                            // and so this could have been checked inside of that gap, however in most cases
                            // these types should exist, but it will just display an error image for the user if
                            // it does not
                            val errorMessage =
                                "A file path existed inside of the repository that does not exist inside of its" +
                                        "respective directory\n" +
                                        "primaryKey: ${databaseFilePathsList.primaryKey}\n" +
                                        "filePath: ${databaseFilePathsList.filePath}\n" +
                                        "FilePathType: ${databaseFilePathsList.type}\n" +
                                        fileNamesForDebugging

                            errorHandler.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage,
                                appContext
                            )
                        }
                        FilePathType.PICTURE_MESSAGE_FILE_PATH,
                        FilePathType.MIME_TYPE_FILE_PATH,
                        FilePathType.OTHER_USER_PICTURE_FILE_PATH,
                        FilePathType.OTHER_USER_THUMBNAIL_FILE_PATH,
                        FilePathType.QR_CODE_FILE_PATH,
                        -> {

                            //This is possible because the database is updated before the pictures are saved to file
                            // and so this could have been checked inside of that gap, do not remove these files
                            //these types can be re-downloaded when they are accessed, they are stored inside the cacheDir
                            // so they may be removed at times
                        }
                    }
                }

            val fileExistsOnlyInDirectory: (String) -> Unit = { filePath ->

                if (fileNamesForDebugging.isEmpty()) {
                    fileNamesForDebugging += "Files From Directory\n"
                    for (path in listOfImageFilePathsFromDirectories) fileNamesForDebugging += "$path\n"

                    fileNamesForDebugging += "\nFiles From Database\n"
                    for (path in listOfFilePathsFromDatabase) fileNamesForDebugging += "${path.filePath}\n"
                }

                //This means a a memory leak occurred.
                //NOTE: Because files are deleted using a worker it IS possible that a file has been removed from
                // the database and is simply waiting to be removed by the Worker

                val errorMessage =
                    "When cleaning a file was found to exist inside of the directory, however did not exist inside of the database (memory leak).\n" +
                            "It is possible for this to happen because files are generally deleted AFTER the database is " +
                            "updated however if this is happening on a regular basis there is a problem.\n" +
                            "filePath: ${filePath}\n\n" +
                            fileNamesForDebugging

                errorHandler.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    appContext
                )

                //delete the file
                this.runAfterTransaction {
                    deleteFileInterface.sendFileToWorkManager(filePath)
                }
            }

            var filesFromDirectoriesIndex = 0
            var filesFromDatabaseIndex = 0

            while (filesFromDirectoriesIndex < listOfImageFilePathsFromDirectories.size && filesFromDatabaseIndex < listOfFilePathsFromDatabase.size) {
                when {
                    listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex] ==
                            listOfFilePathsFromDatabase[filesFromDatabaseIndex].filePath -> { //file names are the same

                        //If file is corrupt, delete it.
                        if (!File(listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex]).isImage()) {
                            //create a variable to send to lambda
                            val filePath =
                                listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex]
                            //delete the file
                            this.runAfterTransaction {
                                deleteFileInterface.sendFileToWorkManager(filePath)
                            }
                        }

                        //this is expected
                        filesFromDirectoriesIndex++
                        filesFromDatabaseIndex++
                    }
                    listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex] <
                            listOfFilePathsFromDatabase[filesFromDatabaseIndex].filePath -> { //a file exists in directory that does not exist in database

                        //This is a memory leak
                        fileExistsOnlyInDirectory(listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex])

                        //file is already deleted here

                        filesFromDirectoriesIndex++
                    }
                    else -> { //a file exists in database that does not exist in directory

                        fileExistsOnlyInDatabase(listOfFilePathsFromDatabase[filesFromDatabaseIndex])

                        filesFromDatabaseIndex++
                    }
                }
            }

            for (i in filesFromDirectoriesIndex until listOfImageFilePathsFromDirectories.size) {
                fileExistsOnlyInDirectory(listOfImageFilePathsFromDirectories[filesFromDirectoriesIndex])
            }

            for (i in filesFromDatabaseIndex until listOfFilePathsFromDatabase.size) {
                fileExistsOnlyInDatabase(listOfFilePathsFromDatabase[filesFromDatabaseIndex])
            }

        }

        return fileSpaceUsedForAppStorage
    }

    //remove any error messages stored as files that have not been modified (they are only modified on
    // creation) in a certain amount of time
    private fun removeOldUnsentErrorMessages(listOfErrorMessageFilePaths: List<String>) {

        val oldestAllowedTimestamp =
            getCurrentTimestampInMillis() - OLDEST_ALLOWED_ERROR_MESSAGE_FILE
        for (filePath in listOfErrorMessageFilePaths) {
            val fileLastModifiedTime = File(filePath).lastModified()

            //if time has expired
            if (fileLastModifiedTime < oldestAllowedTimestamp) {
                deleteFileInterface.sendFileToWorkManager(filePath)
            }
        }
    }

    private suspend fun extractListOfFilePathsFromDatabase(): MutableList<DatabaseFilePathsList> {

        val listOfFileNamesFromDatabase = mutableListOf<DatabaseFilePathsList>()

        saveAccountPictureFileNamesToList(listOfFileNamesFromDatabase)

        saveMessageFileNamesToList(listOfFileNamesFromDatabase)

        saveMimeTypeFileNamesToList(listOfFileNamesFromDatabase)

        saveOtherUserFileNamesToList(listOfFileNamesFromDatabase)

        saveQRCodeTypeFileNamesToList(listOfFileNamesFromDatabase)

        return listOfFileNamesFromDatabase
    }

    private suspend fun saveAccountPictureFileNamesToList(listOfFileNamesFromDatabase: MutableList<DatabaseFilePathsList>) {
        val accountPictureFilePaths =
            cleanDatabaseWorkerRepository.retrieveAllAccountPictureFilePaths()

        for (picture in accountPictureFilePaths) {
            if (picture.picturePath.isNotBlank()
                && picture.picturePath != "~"
                && picture.picturePath != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        picture.pictureIndex.toString(),
                        picture.picturePath,
                        FilePathType.USER_PICTURE_FILE_PATH
                    )
                )
            }
        }
    }

    private suspend fun saveMessageFileNamesToList(listOfFileNamesFromDatabase: MutableList<DatabaseFilePathsList>) {

        val messageFilePaths = cleanDatabaseWorkerRepository.retrieveMessageFilePaths()

        for (message in messageFilePaths) {
            if (message.file_path.isNotBlank()
                && message.file_path != "~"
                && message.file_path != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        message.messageUUIDPrimaryKey,
                        message.file_path,
                        FilePathType.PICTURE_MESSAGE_FILE_PATH
                    )
                )
            }

            if (message.reply_is_from_thumbnail_file_path.isNotBlank()
                && message.reply_is_from_thumbnail_file_path != "~"
                && message.reply_is_from_thumbnail_file_path != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        message.messageUUIDPrimaryKey,
                        message.reply_is_from_thumbnail_file_path,
                        FilePathType.MESSAGE_REPLY_FILE_PATH
                    )
                )
            }
        }
    }

    private suspend fun saveMimeTypeFileNamesToList(listOfFileNamesFromDatabase: MutableList<DatabaseFilePathsList>) {

        val mimeTypeFilePaths = cleanDatabaseWorkerRepository.retrieveMimeTypesAllFilePaths()

        for (mimeTypes in mimeTypeFilePaths) {
            if (mimeTypes.mime_type_file_path.isNotBlank()
                && mimeTypes.mime_type_file_path != "~"
                && mimeTypes.mime_type_file_path != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        mimeTypes.mimeTypeUrl,
                        mimeTypes.mime_type_file_path,
                        FilePathType.MIME_TYPE_FILE_PATH
                    )
                )
            }
        }
    }

    private suspend fun saveOtherUserFileNamesToList(listOfFileNamesFromDatabase: MutableList<DatabaseFilePathsList>) {

        val otherUserFilePaths = cleanDatabaseWorkerRepository.retrieveOtherUserAllFilePaths()

        for (otherUser in otherUserFilePaths) {
            if (otherUser.pictures.isNotBlank()) {
                val picturesList = convertPicturesStringToList(otherUser.pictures)

                for (pic in picturesList) {
                    if (pic.picturePath.isNotBlank()
                        && pic.picturePath != "~"
                        && pic.picturePath != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
                    ) {
                        listOfFileNamesFromDatabase.add(
                            DatabaseFilePathsList(
                                otherUser.accountOID,
                                pic.picturePath,
                                FilePathType.OTHER_USER_PICTURE_FILE_PATH
                            )
                        )
                    }
                }
            }

            if (otherUser.thumbnail_path.isNotEmpty()
                && otherUser.thumbnail_path != "~"
                && otherUser.thumbnail_path != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        otherUser.accountOID,
                        otherUser.thumbnail_path,
                        FilePathType.OTHER_USER_THUMBNAIL_FILE_PATH
                    )
                )
            }
        }
    }

    private suspend fun saveQRCodeTypeFileNamesToList(listOfFileNamesFromDatabase: MutableList<DatabaseFilePathsList>) {

        val chatRoomFiles = cleanDatabaseWorkerRepository.retrieveChatRoomFilePaths()

        for (chatRoomTypes in chatRoomFiles) {
            if (chatRoomTypes.qr_code_path.isNotBlank()
                && chatRoomTypes.qr_code_path != "~"
                && chatRoomTypes.qr_code_path != GlobalValues.server_imported_values.qrCodeDefault
            ) {
                listOfFileNamesFromDatabase.add(
                    DatabaseFilePathsList(
                        chatRoomTypes.chatRoomID,
                        chatRoomTypes.qr_code_path,
                        FilePathType.QR_CODE_FILE_PATH
                    )
                )
            }
        }
    }

    companion object {

        //unique worker identification
        const val CLEAN_DATABASE_WORKER_UNIQUE_WORK_NAME = "clean_database_work_name"

        //min and max space to allocate for total memory
        private const val MINIMUM_STORAGE_SPACE_TO_ALLOCATE: Long = 124L * 1024L * 1024L //124Mb
        private const val MAXIMUM_STORAGE_SPACE_TO_ALLOCATE: Long = 8L * 1024L * 1024L * 1024L //8Gb

        //oldest allowed error message file
        private const val OLDEST_ALLOWED_ERROR_MESSAGE_FILE: Long = 1000L * 60L * 60L * 24L * 7L

        //values for progress loading
       private const val TOTAL_TIME_FOREGROUND_WORKER_RUNS_IN_MILLIS = 60 * 1000 //1 minute
//        private const val DELAY_BETWEEN_UPDATING_NOTIFICATION_PROGRESS_BAR_IN_MILLIS: Long =
//            3L * 1000L //3 seconds
//        private const val DELAY_BETWEEN_UPDATING_NOTIFICATION_PROGRESS_BAR_IN_MILLIS_INT: Int =
//            DELAY_BETWEEN_UPDATING_NOTIFICATION_PROGRESS_BAR_IN_MILLIS.toInt()

        //the total time the clean database worker will run in milliSeconds
        //must be less than 10 minutes where the stream is terminated
        const val MAXIMUM_TIME_CLEAN_DATABASE_WORKER_CAN_RUN: Long =
            TOTAL_TIME_FOREGROUND_WORKER_RUNS_IN_MILLIS.toLong() * 2L
    }

}