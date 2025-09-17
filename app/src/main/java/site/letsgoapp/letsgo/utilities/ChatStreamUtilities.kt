package site.letsgoapp.letsgo.utilities

import account_state.AccountState
import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import member_shared_info.MemberSharedInfoMessageOuterClass
import requestmessages.RequestMessages
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.repositories.ApplicationRepository
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import java.io.File
import java.io.IOException

data class InsertOrUpdateOtherUserReturnValue(
    val otherUser: OtherUsersDataEntity,
    val anExistingThumbnailWasUpdated: Boolean
)

/**
 * The what and where of downloading 'other users' member info
 * 1) on initialize
 * --for DIFFERENT_USER_JOINED_CHAT_ROOM (chat room requires update), send back thumbnail and below
 * --for THIS_USER_JOINED_CHAT_ROOM_MEMBER (new chat room found), send back thumbnail and below
 * 2) on chat change stream sending new message
 * --DIFFERENT_USER_JOINED_CHAT_ROOM, send back thumbnail and below
 * --match made, send back full info including picture
 * 3) request new messages (this should never get DIFFERENT_USER_JOINED_CHAT_ROOM)
 * 4) joinChatRoom
 * --THIS_USER_JOINED_CHAT_ROOM_MEMBER, send back thumbnail and below if more than 5(?) members and all info if less than or equal to 5(?) members
 * --when retrieving messages, DIFFERENT_USER_JOINED_CHAT_ROOM will retrieve only the account_state and account_oid (onlyStoreMessage == true)
 * 5) updateChatRoom, has its own stream to send back members AND message updates
 * --members, send back thumbnail and below if more than 5(?) members and all info if less than or equal to 5(?) members
 * --should only request updates to existing messages and so only active messages should be requested, never DIFFERENT_USER_JOINED_CHAT_ROOM
 * 6) updateSingleUser
 * --sends back full member info including picture
 **/
//updates other user or adds it if does not exist, called from insertOrUpdateOtherUserReferenceObject and insertOrUpdateOtherUserForChatRoom
//returns basic OtherUsersDataEntity() if an error occurred (can check if userInfo.accountOid.isNotEmpty())
/** transactionWrapper requires OtherUsersDatabase to be locked **/
suspend fun insertOrUpdateOtherUser(
    userInfo: MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage,
    updateReason: ApplicationRepository.UpdateMemberReasonEnum,
    otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    applicationContext: Context,
    transactionWrapper: TransactionWrapper,
    errorStore: StoreErrorsInterface,
    deleteFileInterface: StartDeleteFileInterface,
    chatRoomId: String = "",
    accountState: AccountState.AccountStateInChatRoom = AccountState.AccountStateInChatRoom.UNRECOGNIZED,
    accountLastActivityTime: Long = -1L,
    referenceType: ReferencingObjectType = ReferencingObjectType.UNKNOWN,
    referenceId: String = ""
): InsertOrUpdateOtherUserReturnValue {

    if (!userInfo.accountOid.isValidMongoDBOID()) { //if userInfo.accountOID is invalid

        val errorMessage = "Invalid accountOid passed to insertOrUpdateOtherUser() function.\n" +
                "userInfo.accountOid: ${userInfo.accountOid}"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorMessage
        )

        return InsertOrUpdateOtherUserReturnValue(
            OtherUsersDataEntity(),
            false
        )
    }

    Log.i("num_users_chat", "insertOrUpdateOtherUser account_oid: ${userInfo.accountOid} account_type: ${userInfo.accountType} reference_type: $referenceType");

    var returnVal: OtherUsersDataEntity? = null
    var existingThumbnailWasUpdated = false

    transactionWrapper.runTransaction {

        val otherUser = otherUsersDaoDataSource.getSingleOtherUser(userInfo.accountOid)

        if (otherUser == null) { //if other does not already exist inside the database

            //insert other user
            val pictureInfo = mutableListOf<PictureInfo>()

            //NOTE: pictures sent back always exist, this will never receive an 'empty' picture
            // from the server
            for (pic in userInfo.pictureList) {

                if (pic.fileInBytes.size() == pic.fileSize) { //file is NOT corrupt

                    val picturePath =
                        if (!pic.fileInBytes.isEmpty) {
                            val file = generateOtherUserPictureFile(
                                userInfo.accountOid,
                                pic.indexNumber,
                                pic.timestampPictureLastUpdated,
                                applicationContext
                            )

                            this.runAfterTransaction {

                                // run write to file after transaction
                                writePictureToFile(
                                    file,
                                    userInfo.accountOid,
                                    pic.indexNumber,
                                    pic.fileInBytes,
                                    errorStore
                                )

                                if (!file.isImage()) {

                                    otherUsersDaoDataSource.updatePictureToCorrupt(
                                        userInfo.accountOid,
                                        pic.timestampPictureLastUpdated,
                                        pic.indexNumber,
                                    )

                                    //NOTE: Do not show error here or it will be spammed across ALL devices that receive this picture.
                                    // Also it is most likely that a file was simply corrupted when stored on the database.
                                }
                            }

                            file.absolutePath

                        } else {
                            ""
                        }

                    pictureInfo.add(
                        PictureInfo(
                            picturePath,
                            pic.indexNumber,
                            pic.timestampPictureLastUpdated
                        )
                    )

                }
                else { //corrupt file
                    val errorMessage =
                        "A picture inside insertOrUpdateOtherUser() was invalid and could not be stored.\n" +
                                "pic.fileInBytes.size(): ${pic.fileInBytes.size()}\n" +
                                "pic.fileSize: ${pic.fileSize}\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )
                }
            }

            val thumbnailPath =
                when {
                    userInfo.accountThumbnail.isEmpty -> {
                        ""
                    }
                    userInfo.accountThumbnail.size() == userInfo.accountThumbnailSize -> {
                        val thumbnailFile = generateOtherUserThumbnailFile(
                            userInfo.accountOid,
                            userInfo.accountThumbnailTimestamp,
                            applicationContext
                        )

                        this.runAfterTransaction {
                            writeThumbnailToFile(
                                thumbnailFile,
                                userInfo.accountOid,
                                userInfo.accountThumbnail,
                                errorStore
                            )

                            if (!thumbnailFile.isImage()) {

                                //remove file if it is NOT image
                                deleteFileInterface.sendFileToWorkManager(
                                    thumbnailFile.absolutePath
                                )

                                val errorMessage =
                                    "The thumbnail inside insertOrUpdateOtherUser() was invalid and could not be stored.\n" +
                                            "userInfo.accountThumbnail.size(): ${userInfo.accountThumbnail.size()}\n" +
                                            "userInfo.accountThumbnailSize: ${userInfo.accountThumbnailSize}\n" +
                                            "thumbnailFile.isImage(): ${thumbnailFile.isImage()}\n"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorMessage
                                )
                            }
                        }

                        thumbnailFile.absolutePath
                    }
                    else -> {
                        val errorMessage =
                            "The thumbnail inside insertOrUpdateOtherUser() was invalid and could not be stored.\n" +
                                    "userInfo.accountThumbnail.size(): ${userInfo.accountThumbnail.size()}\n" +
                                    "userInfo.accountThumbnailSize: ${userInfo.accountThumbnailSize}\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )

                        //NOTE: leave this here so I can leave thumbnailPath as a val
                        ""
                    }
                }

            val objectsRequiringInfo = mutableSetOf<ObjectRequiringInfo>()
            var chatRoomString = ""
            var distance = -1.0

            //set the respective value
            when (updateReason) {
                ApplicationRepository.UpdateMemberReasonEnum.JOINED_CHAT_ROOM,
                ApplicationRepository.UpdateMemberReasonEnum.UPDATE_OTHER_USER_IN_CHAT_ROOM,
                -> {

                    if (chatRoomId.isValidChatRoomId()) { //if user is not inside chat room already AND chat room id is valid
                        chatRoomString = convertOtherUserChatRoomInfoToString(
                            OtherUserChatRoomInfo(
                                chatRoomId,
                                accountState,
                                accountLastActivityTime
                            )
                        )
                    }
                }
                ApplicationRepository.UpdateMemberReasonEnum.ALGORITHM_MATCH_RETURN,
                ApplicationRepository.UpdateMemberReasonEnum.MATCH_SUCCESSFULLY_MADE,
                -> {

                    if (updateReason == ApplicationRepository.UpdateMemberReasonEnum.ALGORITHM_MATCH_RETURN) {
                        distance = userInfo.distance
                    }

                    objectsRequiringInfo.add(
                        ObjectRequiringInfo(
                            referenceType,
                            referenceId
                        )
                    )
                }
                ApplicationRepository.UpdateMemberReasonEnum.UPDATE_MATCH -> {}
            }

            val currentTimestamp = getCurrentTimestampInMillis()

            val picturesUpdateAttemptedTimestamp =
                if (userInfo.picturesCheckedForUpdates) {
                    //using this timestamp instead of userInfo.currentTimestamp to keep consistent with a comparison done inside
                    // of getOtherUserAccountStateInChatRoom in OtherUsersDaoIntermediate.kt
                    currentTimestamp
                } else {
                    -1
                }

            val otherUserDataEntity = OtherUsersDataEntity(
                userInfo.accountOid,
                thumbnailPath,
                userInfo.accountThumbnailIndex,
                userInfo.accountThumbnailTimestamp,
                convertObjectsRequiringInfoSetToString(objectsRequiringInfo),
                chatRoomString,
                distance,
                userInfo.timestampOtherUserInfoUpdated,
                currentTimestamp,
                convertPicturesListToString(pictureInfo),
                picturesUpdateAttemptedTimestamp,
                userInfo.accountName,
                userInfo.age,
                userInfo.gender,
                userInfo.cityName,
                userInfo.bio,
                convertCategoryActivityMessageToString(userInfo.activitiesList),
                userInfo.accountType.number,
                userInfo.letsgoEventStatus.number,
                userInfo.createdBy,
                userInfo.eventTitle
            )

            otherUsersDaoDataSource.upsertSingleOtherUser(
                otherUserDataEntity
            )

            returnVal = otherUserDataEntity

        }
        else { //if other user does exist inside the database

            //increase reference count
            //add chat room to chat room string
            //if new timestampOtherUserInfoUpdated is larger update all user info

            val currentTimestamp = getCurrentTimestampInMillis()
            var firstPictureIndexUpdated = false

            //if pictures were attempted to be updated, save the timestamp
            if (userInfo.picturesCheckedForUpdates) {
                //using this timestamp instead of userInfo.currentTimestamp to keep consistent with a comparison done inside
                // of getOtherUserAccountStateInChatRoom in OtherUsersDaoIntermediate.kt
                otherUser.picturesUpdateAttemptedTimestamp = currentTimestamp
            }
            //this could happen if a user has left a chat room and has minimal info stored, then joins
            // again with DIFFERENT_USER_JOINED_MESSAGE and no info is sent back OR if the user has a deleted picture
            // because it was invalid
            //this means no pictures are currently stored on device, no pictures were sent back, and pictures
            // were NOT checked for updates by then server
            else if (otherUser.pictures.isEmpty() && userInfo.pictureList.isEmpty()) {
                otherUser.picturesUpdateAttemptedTimestamp = -1
            }

            Log.i(
                "insertOrRemoveP",
                "userInfo.pictureList.isNotEmpty(): ${userInfo.pictureList.isNotEmpty()}"
            )

            //NOTE: if pictureList is empty, assume no updates were needed
            if (userInfo.pictureList.isNotEmpty()) { //if pictures list passed from server is not empty

                val pictureInfo = mutableListOf<PictureInfo>() //stores the new picture array

                val previouslyStoredPicturesList =
                    convertPicturesStringToList(otherUser.pictures)

                previouslyStoredPicturesList.sortBy {
                    it.indexOfPictureForUser
                }

                val previouslyStoredPicturesMap: MutableMap<Int, PictureInfo> =
                    previouslyStoredPicturesList.associateBy {
                        it.indexOfPictureForUser
                    }.toMutableMap() //stores a map representing old picture array

                //inserts the new picture and removes the old picture if necessary
                val insertOrRemovePicture: (RequestMessages.PictureMessage, PictureInfo) -> Unit =
                    { pic, previousElement ->

                        Log.i(
                            "insertOrRemoveP",
                            "pic.timestampPictureLastUpdated: ${pic.timestampPictureLastUpdated} pic.fileInBytes: ${pic.fileInBytes} previousElement.picturePath: ${previousElement.picturePath}"
                        )

                        if (pic.timestampPictureLastUpdated == -1L) { //if this picture index needs to be removed

                            if (previousElement.picturePath.isNotBlank()) { //if the previous element had a picture at that index
                                deleteFileInterface.sendFileToWorkManager(
                                    previousElement.picturePath
                                )
                            }

                        }
                        else if (pic.fileInBytes.size() == pic.fileSize) { //if picture is not corrupt

                            val file = generateOtherUserPictureFile(
                                userInfo.accountOid,
                                pic.indexNumber,
                                pic.timestampPictureLastUpdated,
                                applicationContext
                            )

                            this.runAfterTransaction {

                                // run write to file after transaction
                                writePictureToFile(
                                    file,
                                    userInfo.accountOid,
                                    pic.indexNumber,
                                    pic.fileInBytes,
                                    errorStore
                                )

                                if (!file.isImage()) { //picture is not valid

                                    deleteFileInterface.sendFileToWorkManager(
                                        file.absolutePath
                                    )

                                    otherUsersDaoDataSource.updatePictureToCorrupt(
                                        userInfo.accountOid,
                                        pic.timestampPictureLastUpdated,
                                        pic.indexNumber,
                                    )

                                    //NOTE: Do not show error here or it will be spammed across ALL devices that receive this picture.
                                    // Also it is most likely that a file was simply corrupted when stored on the database.
                                }
                            }

                            val filePath = file.absolutePath

                            pictureInfo.add(
                                PictureInfo(
                                    filePath,
                                    pic.indexNumber,
                                    pic.timestampPictureLastUpdated
                                )
                            )

                            //remove old picture file
                            if (previousElement.picturePath.isNotBlank() && previousElement.picturePath != filePath) { //if a picture is stored at this index
                                deleteFileInterface.sendFileToWorkManager(
                                    previousElement.picturePath
                                )
                            }

                        }
                        else { //corrupt file

                            val errorMessage =
                                "A picture inside insertOrUpdateOtherUser() was invalid and could not be stored.\n" +
                                        "pic.fileInBytes.size(): ${pic.fileInBytes.size()}\n" +
                                        "pic.fileSize: ${pic.fileSize}\n"

                            errorStore.storeError(
                                Thread.currentThread().stackTrace[2].fileName,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors(),
                                errorMessage
                            )
                        }

                    }

                var firstIndex = GlobalValues.server_imported_values.numberPicturesStoredPerAccount+1

                for(prevPicture in previouslyStoredPicturesMap) {
                    if(prevPicture.value.indexOfPictureForUser < firstIndex) {
                        firstIndex = prevPicture.value.indexOfPictureForUser
                    }
                }

                for (pic in userInfo.pictureList) {

                    val previousElement =
                        previouslyStoredPicturesMap[pic.indexNumber]

                    if (previousElement != null) { //if this picture index was found

                        if(firstIndex == pic.indexNumber) {
                            firstPictureIndexUpdated = true
                        }

                        previouslyStoredPicturesMap.remove(
                            pic.indexNumber
                        )

                        //NOTE: The < and not <= is very important here.
                        if (pic.timestampPictureLastUpdated == -1L  //this means delete the picture without adding a new one
                            || previousElement.timestampPictureLastUpdatedOnServer < pic.timestampPictureLastUpdated
                            || !File(previousElement.picturePath).exists() //previous picture does not exist
                        ) { //if picture requires updating
                            insertOrRemovePicture(
                                pic,
                                previousElement
                            )
                        } else { //if element does not require updating
                            pictureInfo.add(
                                previousElement
                            )
                        }

                    }
                    else { //if this picture index was not found

                        insertOrRemovePicture(
                            pic,
                            PictureInfo(
                                "",
                                -1,
                                -1
                            )
                        )
                    }
                }

                //add back any pictures that were not changed
                for (pic in previouslyStoredPicturesMap) {
                    if (pic.value.picturePath != "") {
                        pictureInfo.add(pic.value)
                    }
                }

                //sorting by index so they come out ordered
                pictureInfo.sortBy {
                    it.indexOfPictureForUser
                }

                otherUser.pictures = convertPicturesListToString(pictureInfo)
            }

            if (userInfo.accountThumbnail.size() == 1
                && userInfo.accountThumbnail == ByteString.copyFromUtf8("~")
            ) { //if the thumbnail was deleted (this should only happen if all pictures are deleted for this user)

                if (otherUser.thumbnailPath.isNotEmpty()) {
                    //remove old thumbnail file
                    deleteFileInterface.sendFileToWorkManager(
                        otherUser.thumbnailPath
                    )
                }

                existingThumbnailWasUpdated = true
                otherUser.thumbnailIndexNumber = -1 //used as a marker by the server to 'know' when last updated
                otherUser.thumbnailPath = ""
                otherUser.thumbnailLastTimeUpdated = userInfo.accountThumbnailTimestamp

            }
            else if (!userInfo.accountThumbnail.isEmpty
                && userInfo.accountThumbnail.size() == userInfo.accountThumbnailSize
                //Checking if timestamp is new will help thumbnails NOT update to old versions for members
                // NOT in the chat room anymore. This condition was added to support the manual
                // test under ADVANCED TESTING with the really long description. However, there is an
                // exception when a user is currently in the chat room and their picture is deleted. In this
                // case it will send back the timestamp of the next picture in the users picture list. This means
                // the timestamp may not be the most recent, in order to fix this it will check if the first picture
                // index was updated.
                &&
                (otherUser.thumbnailLastTimeUpdated < userInfo.accountThumbnailTimestamp
                        || firstPictureIndexUpdated)
            ) { //if the thumbnail was updated

                Log.i("thumbnailUpdated", "thumbnailUpdated")

                val thumbnailFile = generateOtherUserThumbnailFile(
                    userInfo.accountOid,
                    userInfo.accountThumbnailTimestamp,
                    applicationContext
                )

                this.runAfterTransaction {
                    writeThumbnailToFile(
                        thumbnailFile,
                        userInfo.accountOid,
                        userInfo.accountThumbnail,
                        errorStore
                    )

                    if (!thumbnailFile.isImage()) { //picture is not valid

                        deleteFileInterface.sendFileToWorkManager(
                            thumbnailFile.absolutePath
                        )

                        otherUsersDaoDataSource.updateThumbnailToCorrupt(
                            userInfo.accountOid,
                            userInfo.accountThumbnailTimestamp
                        )

                        //NOTE: Do not show error here or it will be spammed across ALL devices that receive this picture.
                        // Also it is most likely that a file was simply corrupted when stored on the database.
                    }
                }

                val thumbnailPath = thumbnailFile.absolutePath

                if (otherUser.thumbnailPath != thumbnailPath && otherUser.thumbnailPath != "") {
                    //remove old thumbnail file
                    deleteFileInterface.sendFileToWorkManager(
                        otherUser.thumbnailPath
                    )
                }

                existingThumbnailWasUpdated = true
                otherUser.thumbnailIndexNumber = userInfo.accountThumbnailIndex
                otherUser.thumbnailPath = thumbnailPath
                otherUser.thumbnailLastTimeUpdated = userInfo.accountThumbnailTimestamp

            }
            //else { } //This could happen if the thumbnail path did not need changed

            when (updateReason) {
                ApplicationRepository.UpdateMemberReasonEnum.UPDATE_OTHER_USER_IN_CHAT_ROOM -> {

                    val chatRooms =
                        convertChatRoomObjectsStringToMap(otherUser.chatRoomObjects)

                    val chatRoom = chatRooms[chatRoomId]

                    if (chatRoom != null) { //if user is already inside chat room

                        if (chatRoom.accountStateInChatRoom != accountState
                            || chatRoom.lastActiveTimeInChatRoom != accountLastActivityTime
                        ) { //if account state or last activity time need updated

                            if ((chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                                        || chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN)
                                &&
                                (accountState != AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                                        && accountState != AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN)
                            ) { //if this account is currently inside the chat room, and the updated response is NOT inside the chat room

                                val updatedChatRooms = otherUserLeavesChatRoom(
                                    otherUser,
                                    chatRoomId,
                                    accountState,
                                    deleteFileInterface,
                                    errorStore
                                )

                                //NOTE: this must be after pictures are updated
                                if (updatedChatRooms.isNotEmpty()) {
                                    otherUser.chatRoomObjects =
                                        convertChatRoomObjectsMapToString(updatedChatRooms)
                                }
                            }

                            chatRoom.accountStateInChatRoom = accountState
                            chatRoom.lastActiveTimeInChatRoom =
                                accountLastActivityTime

                            otherUser.chatRoomObjects =
                                convertChatRoomObjectsMapToString(chatRooms)

                        }
                    } else if (chatRoomId.isValidChatRoomId()) { //if user is not inside chat room already AND chat room id is valid

                        chatRooms[chatRoomId] =
                            OtherUserChatRoomInfo(
                                chatRoomId,
                                accountState,
                                accountLastActivityTime
                            )

                        otherUser.chatRoomObjects =
                            convertChatRoomObjectsMapToString(chatRooms)

                    }
                }
                ApplicationRepository.UpdateMemberReasonEnum.JOINED_CHAT_ROOM -> {

                    val chatRooms =
                        convertChatRoomObjectsStringToMap(otherUser.chatRoomObjects)
                    val chatRoom = chatRooms[chatRoomId]

                    if (chatRoom != null) { //if user is already inside chat room

                        if (chatRoom.accountStateInChatRoom != accountState
                            || chatRoom.lastActiveTimeInChatRoom != accountLastActivityTime
                        ) { //if account state or last activity time need updated

                            //this means that the used could have been not in account state 'IN_CHAT_ROOM'
                            chatRoom.accountStateInChatRoom = accountState
                            chatRoom.lastActiveTimeInChatRoom =
                                accountLastActivityTime

                            otherUser.chatRoomObjects =
                                convertChatRoomObjectsMapToString(chatRooms)

                        }

                    } else { //if user is not inside chat room already

                        chatRooms[chatRoomId] =
                            OtherUserChatRoomInfo(
                                chatRoomId,
                                accountState,
                                accountLastActivityTime
                            )

                        otherUser.chatRoomObjects =
                            convertChatRoomObjectsMapToString(chatRooms)

                    }
                }
                ApplicationRepository.UpdateMemberReasonEnum.ALGORITHM_MATCH_RETURN,
                ApplicationRepository.UpdateMemberReasonEnum.MATCH_SUCCESSFULLY_MADE,
                -> {

                    if (updateReason == ApplicationRepository.UpdateMemberReasonEnum.ALGORITHM_MATCH_RETURN) {
                        otherUser.distance = userInfo.distance
                    }

                    val objectsRequiringInfoSet =
                        convertObjectsRequiringInfoStringToSet(
                            otherUser.objectsRequiringInfo,
                            errorStore
                        )
                    val newObjectReference = ObjectRequiringInfo(
                        referenceType,
                        referenceId
                    )

                    objectsRequiringInfoSet.add(newObjectReference)

                    otherUser.objectsRequiringInfo =
                        convertObjectsRequiringInfoSetToString(objectsRequiringInfoSet)
                }
                ApplicationRepository.UpdateMemberReasonEnum.UPDATE_MATCH -> {}
            }

            if (userInfo.timestampOtherUserInfoUpdated != 0L) {  //if the user info values were set on server

                //age is a bit unique because it doesn't have a timestamp
                if (otherUser.age != userInfo.age) { //if age passed back is different than stored age
                    otherUser.age = userInfo.age
                }

                if (otherUser.timestampUserInfoLastUpdated < userInfo.timestampOtherUserInfoUpdated) { //if user info requires updated

                    otherUser.timestampUserInfoLastUpdated = userInfo.timestampOtherUserInfoUpdated
                    //otherUser.name = userInfo.accountName (updated below)
                    otherUser.gender = userInfo.gender
                    otherUser.cityName = userInfo.cityName
                    otherUser.bio = userInfo.bio
                    otherUser.activities =
                        convertCategoryActivityMessageToString(userInfo.activitiesList)
                }

                otherUser.accountType = userInfo.accountType.number

                otherUser.eventStatus = userInfo.letsgoEventStatus.number

                //Will be set to 'LetsGo' (app_human_name in strings.xml) if this was created by an
                // admin. Otherwise will be the user oid.
                otherUser.createdBy = userInfo.createdBy

                otherUser.eventTitle = userInfo.eventTitle
            }

            //This can be updated if accountState is not 'in chat room' or an admin changed the name.
            if (userInfo.accountName.isNotBlank() && userInfo.accountName != "~") {
                otherUser.name = userInfo.accountName
            }

            //Update observed time, or user info can be removed immediately afterwards by CleanDatabaseWorker.
            otherUser.timestampUserInfoLastObserved = currentTimestamp

            //NOTE: the UpdateChatRoomMembers function will only send back updates that require new info,
            // however the other functions calling this have no such guarantee
            //NOTE: every path will update something (sometimes just the chat room reference count or
            // the picturesUpdateAttemptedTimestamp)
            otherUsersDaoDataSource.upsertSingleOtherUser(
                otherUser
            )

            Log.i(
                "Synchronized",
                "${Thread.currentThread().stackTrace[7].methodName}; lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber} end"
            )

            returnVal = otherUser

        }
    }

    return InsertOrUpdateOtherUserReturnValue(
        returnVal!!,
        existingThumbnailWasUpdated
    )
}

//saves the picture to file and returns the file path
// will store any errors associated with exceptions error occurs and return an empty string
fun writePictureToFile(
    pictureFile: File,
    accountOid: String,
    indexNumber: Int,
    picture: ByteString,
    errorsStore: StoreErrorsInterface
): Boolean {

    try {
        pictureFile.writeBytes(picture.toByteArray())

        return true
    } catch (ex: IOException) {

        val errorString =
            "An IOException occurred while attempting to write a match picture to file.\n" +
                    "IOException: ${ex.message}\n" +
                    "accountOid: $accountOid\n" +
                    "index: $indexNumber\n"

        errorsStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorString
        )

    }

    return false
}

//saves the thumbnail to file and returns the file path
// will store any errors associated with exceptions error occurs and return an empty string
fun writeThumbnailToFile(
    thumbnailFile: File,
    accountOid: String,
    thumbnail: ByteString,
    errorStore: StoreErrorsInterface
): Boolean {

    try {
        thumbnailFile.writeBytes(thumbnail.toByteArray())

        return true
    } catch (ex: IOException) {

        val errorString =
            "An IOException occurred while attempting to write a match picture to file.\n" +
                    "IOException: ${ex.message}\n" +
                    "accountOid: $accountOid\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorString
        )
    }

    return false
}

//modifies a reference to an other user and sets them to the new account state inside the chat room
//will delete pictures if only partial info is required
//returns an empty chat room map if the passed chat room Id is not found
//NOTE: does not actually remove the reference, only modifies it
fun otherUserLeavesChatRoom(
    otherUser: OtherUsersDataEntity,
    chatRoomId: String,
    newAccountState: AccountState.AccountStateInChatRoom,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
): MutableMap<String, OtherUserChatRoomInfo> {

    val chatRoomInfoMap =
        convertChatRoomObjectsStringToMap(otherUser.chatRoomObjects)

    val chatRoom = chatRoomInfoMap[chatRoomId]

    if (chatRoom != null) {

        //setting this to -1 is not necessary, but it will make the string shorter and
        //it will be updated if the user rejoins anyway, plus it shouldn't but used anywhere
        chatRoom.lastActiveTimeInChatRoom = -1
        chatRoom.accountStateInChatRoom = newAccountState

        removePicturesIfOnlyPartialInfoRequired(
            otherUser,
            chatRoomInfoMap,
            deleteFileInterface,
            errorStore
        )

    } else {
        val errorMessage = "Chat room was not.\n" +
                "chatRoomInfoMap: $chatRoomInfoMap\n" +
                "chatRoomId: $chatRoomId\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorMessage
        )

        chatRoomInfoMap.clear()
    }

    return chatRoomInfoMap
}

//modifies a reference to an other user and sets them to the new account state inside the chat room
//will delete pictures if only partial info is required
//NOTE: does not actually remove the reference, only modifies it
/** transactionWrapper requires OtherUsersDatabase and MessagesDatabase to be locked **/
suspend fun otherUserLeavesChatRoom(
    otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    accountOid: String,
    chatRoomId: String,
    newAccountState: AccountState.AccountStateInChatRoom,
    transactionWrapper: TransactionWrapper,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface,
    lastActiveTime: Long = -1L, //if this is set to anything besides -1 then the the user 'last active time' will be updated
): OtherUsersDataEntity? {

    var otherUser: OtherUsersDataEntity? = null

    transactionWrapper.runTransaction {

        otherUser = otherUsersDaoDataSource.getSingleOtherUser(accountOid)

        if (otherUser != null) { //if other user exists in other users database
            otherUser?.let { otherUserNonNull ->
                val chatRooms =
                    otherUserLeavesChatRoom(
                        otherUserNonNull,
                        chatRoomId,
                        newAccountState,
                        deleteFileInterface,
                        errorStore
                    )

                if (chatRooms.isNotEmpty()) { //if empty, an error occurred inside otherUserLeavesChatRoom

                    if (lastActiveTime != -1L) { //if this last active time should be updated
                        chatRooms[chatRoomId]?.let {
                            if (it.lastActiveTimeInChatRoom < lastActiveTime) {
                                it.lastActiveTimeInChatRoom = lastActiveTime
                            }
                        }
                    }

                    otherUserNonNull.chatRoomObjects =
                        convertChatRoomObjectsMapToString(chatRooms)

                    otherUsersDaoDataSource.upsertSingleOtherUser(otherUserNonNull)
                }
            }

        } else { //if other user does not exist in other users database, however a message was sent that they were removed
            otherUser = null

            val errorMessage =
                "Other user does not exist in other users database, however a message was sent that they were removed.\n" +
                        "accountOid: $accountOid\n" +
                        "chatRoomId: $chatRoomId\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )
        }

    }

    return otherUser
}

//will remove pictures if not required
//returns true if otherUser was updated, false if not
fun removePicturesIfOnlyPartialInfoRequired(
    otherUser: OtherUsersDataEntity,
    passedChatRoomInfoMap: MutableMap<String, OtherUserChatRoomInfo>?,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
) {

    if (otherUser.pictures.isNotBlank() && otherUser.objectsRequiringInfo == "") { //if the other user is storing full info and only chat room references remain

        val chatRoomInfoMap =
            passedChatRoomInfoMap
                ?: convertChatRoomObjectsStringToMap(otherUser.chatRoomObjects)

        //check if full info can be changed to partial info
        var fullReferenceNeeded = false
        for (chatRoom in chatRoomInfoMap) {
            if (chatRoom.value.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                || chatRoom.value.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN
            ) {
                fullReferenceNeeded = true
                break
            }
        }

        if (!fullReferenceNeeded) { //if only partial reference is needed
            removeOtherUserPictures(
                otherUser.pictures,
                deleteFileInterface,
                errorStore
            )

            otherUser.pictures = ""
        }
    }
}

fun removeOtherUserPictures(
    otherUsersPicturesString: String,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
) {

    val picturesList =
        convertPicturesStringToList(otherUsersPicturesString)

    for (picture in picturesList) {
        if (picture.picturePath.isNotBlank()) { //if picture path is not empty

            //remove old picture file
            deleteFileInterface.sendFileToWorkManager(
                picture.picturePath
            )

        } else { //if picture path is empty

            val errorMessage =
                "A picture path was empty for an 'otherUsersPicturesString' database value.\n" +
                        "picturesList: $picturesList\n" +
                        "picture: $picture"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )
        }
    }
}

suspend fun initializeMemberListForChatRoom(
    chatRoom: ChatRoomWithMemberMapDataClass,
    chatRoomID: String,
    otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface
) {
    chatRoom.chatRoomMembers.initializeList(
        otherUsersDaoDataSource.getAllOtherUsersForChatRoom(chatRoomID),
        chatRoomID
    )
}

suspend fun deleteGif(
    mimeTypeDaoDataSource: MimeTypeDaoIntermediateInterface,
    gifUrl: String,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
) {
    val gifDatabaseRow = mimeTypeDaoDataSource.getMimeType(gifUrl)

    if (gifDatabaseRow != null) { //if gif file exists in database

        val numberOfReferences = gifDatabaseRow.numberOfReferences

        if (numberOfReferences <= 1) { //if this is the only reference to the file
            if (gifDatabaseRow.mimeTypeFilePath.isNotBlank()) {
                deleteFileInterface.sendFileToWorkManager(
                    gifDatabaseRow.mimeTypeFilePath
                )
            }
            mimeTypeDaoDataSource.removeSingleMimeTypeByURL(gifUrl)
        } else { //if more references require the file
            mimeTypeDaoDataSource.decrementReferenceCount(gifUrl)
        }

    } else { //if gif file does not exist

        val errorMessage =
            "The database entry for an existing gif was either not properly added or deleted early.\n" +
                    "gifUrl: $gifUrl\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorMessage
        )
    }
}

suspend fun deleteOtherUser(
    otherUsersDaoDataSource: OtherUsersDaoIntermediateInterface,
    otherUser: OtherUsersDataEntity,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface,
) {
    removeOtherUserPictures(
        otherUser.pictures,
        deleteFileInterface,
        errorStore
    )

    if (otherUser.thumbnailPath.isNotBlank()) { //if thumbnail path is not empty
        //remove old picture file
        deleteFileInterface.sendFileToWorkManager(
            otherUser.thumbnailPath
        )
    }
    //else {} //thumbnail path is empty, this can happen if a user's picture was deleted

    otherUsersDaoDataSource.deleteOtherUser(otherUser.accountOID)
}



