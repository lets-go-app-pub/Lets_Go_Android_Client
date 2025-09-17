package site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs

import account_state.AccountState
import android.content.Context
import android.util.Log
import chat_message_to_client.ChatMessageToClientMessage
import chatroominfo.ChatRoomInfoMessageOuterClass
import grpc_chat_commands.ChatRoomCommands
import grpc_chat_commands.ChatRoomCommands.ClientMessageToServerRequest
import grpc_chat_commands.ChatRoomCommands.LeaveChatRoomRequest
import kotlinx.coroutines.*
import report_enums.ReportMessages
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messageMimeTypes.MimeTypeDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.ChatMessageStoredStatus
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.convertMessageDataEntityToTypeOfChatMessage
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomsIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import status_enum.StatusEnum
import type_of_chat_message.TypeOfChatMessageOuterClass
import type_of_chat_message.TypeOfChatMessageOuterClass.TypeOfChatMessage
import java.io.File
import java.io.IOException

private val joinChatRoomLock = LockByIdMap()
private val leaveChatRoomLock = LockByIdMap()
private val promoteNewAdminLock = LockByIdMap()
private val updateChatRoomChatRoomLock = LockByIdMap()
private val removeFromChatRoomLock = LockByIdMap()
private val unMatchUserLock = LockByIdMap()
private val blockAndReportUserLock = LockByIdMap()
private val unblockUserLock = LockByIdMap()

//runs the gRPC RPC calls using request type MessagesDataEntity
//Returns
// GrpcFunctionErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runClientMessageToServer(
    thumbnailForReply: ByteArray,
    messageAlreadyStoredInDatabase: Boolean,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    messagesDataSource: MessagesDaoIntermediateInterface,
    chatRoomsDataSource: ChatRoomsIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    mimeTypeDataSource: MimeTypeDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    messageEntity: MessagesDataEntity,
    runInsideTransaction: suspend (TransactionWrapper) -> Unit,
    deleteFileInterface: StartDeleteFileInterface,
    ioDispatcher: CoroutineDispatcher,
): GrpcFunctionErrorStatusEnum =
    withContext(ioDispatcher) { //error status and the timestamp the message was stored

        val chatMessageType =
            convertMessageDataEntityToTypeOfChatMessage(
                messageEntity,
                thumbnailForReply,
                errorHandling
            )

        //extract loginToken
        var byteArrayReturnValues = StoreUnsentMessageReturnValues()
        val loginToken = loginTokenIsValid()

        val request = ClientMessageToServerRequest.newBuilder()
            .setLoginInfo(getLoginInfo(loginToken))
            .setMessageUuid(messageEntity.messageUUIDPrimaryKey)
            .setMessage(chatMessageType)
            .setTimestampObserved(messageEntity.messageObservedTime)
            .build()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            val response =
                if (chatMessageType.messageSpecifics.messageBodyCase !=
                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE
                ) { //if not picture message
                    Log.i(
                        "sendingMessage",
                        "running sendChatMessageToServer()"
                    )
                    clientsIntermediate.sendChatMessageToServer(request)
                } else { //if picture message

                    //resend picture message if server detected as corrupt
                    var tempResponse = clientsIntermediate.sendChatMessageToServer(request)

                    //resend picture message if server detected as corrupt
                    if (tempResponse.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                        && tempResponse.response.returnStatus == StatusEnum.ReturnStatus.CORRUPTED_FILE
                    ) {
                        tempResponse = clientsIntermediate.sendChatMessageToServer(request)

                        if (tempResponse.androidErrorEnum == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                            && tempResponse.response.returnStatus == StatusEnum.ReturnStatus.CORRUPTED_FILE
                        ) {
                            val errorString =
                                "ClientMessageToServer returned CORRUPTED_FILE twice in a row, meaning the server detected the picture or thumbnail file was corrupted.\n"

                            GrpcClientResponse(
                                tempResponse.response,
                                errorString,
                                tempResponse.androidErrorEnum
                            )
                        } else {
                            tempResponse
                        }

                    } else {
                        tempResponse
                    }
                }

            Log.i(
                "sendingMessage",
                "sendChatMessageToServer() response: $response"
            )

            val errorReturn =
                checkApplicationReturnStatusEnum(
                    response.response.returnStatus,
                    response
                )

            var returnErrorStatusEnum = errorReturn.second
            var returnString = ""

            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.MESSAGES,
                DatabasesToRunTransactionIn.OTHER_USERS
            )

            transactionWrapper.runTransaction {

                runInsideTransaction(this)

                returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            if (response.response.userNotInChatRoom) {
                                //NOTE: this could easily happen, because the messages are saved to be stored later a user could
                                // 1) not be connected to the internet and send a message
                                // 2) gets kicked (or leaves on a different device)
                                // 3) reconnects to the internet

                                errorReturn.first
                            } else {

                                val messageBodyCase =
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                        messageEntity.messageType
                                    )

                                if (messageBodyCase != TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE) {
                                    //see comment inside shared application view model inside runClientMessageToServerReturnValueObserver
                                    // for -1 setting value
                                    messageEntity.messageObservedTime = -1L
                                }

                                val returnTimestamp = response.response.timestampStored

                                messageEntity.messageSentStatus =
                                    ChatMessageStoredStatus.STORED_ON_SERVER.ordinal
                                messageEntity.messageStoredOnServerTime = returnTimestamp

                                when (messageBodyCase) {
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE,
                                    -> { //update both chat room last active time and user last active time

                                        //oid value will be picture oid when picture type message
                                        if (messageBodyCase == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE) {
                                            messageEntity.oidValue =
                                                response.response.pictureOid
                                        }

                                        if (messageEntity.isReply &&
                                            thumbnailForReply.isNotEmpty() &&
                                            (messageEntity.replyType == TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY.number
                                                    || messageEntity.replyType == TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY.number)
                                        ) { //if message is reply type and a thumbnail file needs to be stored
                                            byteArrayReturnValues.thumbnailFile =
                                                generateReplyThumbnailFile(
                                                    messageEntity.messageUUIDPrimaryKey,
                                                    applicationContext
                                                )

                                            byteArrayReturnValues.byteArrayRequiresStoring = true
                                        }

                                        if (messageAlreadyStoredInDatabase) { //if message was already stored in database

                                            //update that the message was properly received by the server
                                            messagesDataSource.updateMessageToReceivedByServer(
                                                messageEntity.messageUUIDPrimaryKey,
                                                messageEntity.messageStoredOnServerTime
                                            )
                                        } else { //if message was not already stored in database
                                            messagesDataSource.insertMessage(messageEntity)
                                        }

                                        /** see [update_times_for_sent_messages] for details **/
                                        chatRoomsDataSource.updateChatRoomObservedTimeUserLastActiveTimeChatRoomLastActiveTimeMatchingOid(
                                            messageEntity.chatRoomId,
                                            returnTimestamp
                                        )

                                        messageEntity.messageObservedTime = returnTimestamp

                                        errorReturn.first
                                    }
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
                                    -> { //do not update the matching oiD if just user activity was sent (this isn't actually done atm but if it ever is this will take care of it)
                                        //remove all types of this message
                                        messagesDataSource.removeAllMessagesOfPassedTypeType(
                                            messageEntity.chatRoomId,
                                            messageBodyCase.number
                                        )

                                        /** see [update_times_for_sent_messages] for details on times stored **/
                                        chatRoomsDataSource.updateChatRoomObservedTimeUserLastActiveTime(
                                            messageEntity.chatRoomId,
                                            returnTimestamp
                                        )

                                        messageEntity.messageObservedTime = returnTimestamp

                                        errorReturn.first
                                    }
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE,
                                    -> { //this is acceptable, but the timestamp was already stored (the server does not store or send this message, it is purely so the server can keep track of chatRoomLastObserverTime)

                                        //the variable was updated before it was sent and the message has no purpose after that (also not stored on server)
                                        messagesDataSource.removeAllMessagesOfPassedTypeType(
                                            messageEntity.chatRoomId,
                                            messageBodyCase.number
                                        )

                                        //returnTimestamp is the messageObservedTime error checked by the server for UPDATE_OBSERVED_TIME_MESSAGE
                                        messageEntity.messageObservedTime = returnTimestamp

                                        //update user last observed time
                                        chatRoomsDataSource.updateUserLastObservedTime(
                                            messageEntity.chatRoomId,
                                            returnTimestamp
                                        )

                                        errorReturn.first
                                    }
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE,
                                    -> { //this will need to remove the sent message then (if MESSAGE_EDITED type) update the edited message

                                        if (messageBodyCase == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE) {

                                            //remove the modified message
                                            messagesDataSource.removeSingleMessageByUUID(
                                                messageEntity.modifiedMessageUUID,
                                                { picturePath ->
                                                    deleteFileInterface.sendFileToWorkManager(
                                                        picturePath
                                                    )
                                                },
                                                { gifURL ->
                                                    deleteGif(
                                                        mimeTypeDataSource,
                                                        gifURL,
                                                        deleteFileInterface,
                                                        errorHandling
                                                    )
                                                },
                                                this
                                            )

                                        } else { //EDITED_MESSAGE

                                            //update the respective message text and that it was properly received by the server
                                            messagesDataSource.updateMessageToEditedAndSentByServer(
                                                messageEntity.modifiedMessageUUID,
                                                TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO,
                                                hasCompleteInfo = true,
                                                messageEntity.messageText,
                                                returnTimestamp,
                                                editHasBeenSent = true
                                            )

                                            messageEntity.messageEditedTime = returnTimestamp

                                        }

                                        if (messageAlreadyStoredInDatabase) { //if message was already stored in database

                                            messagesDataSource.removeSingleMessageByUUIDRaw(
                                                messageEntity.messageUUIDPrimaryKey
                                            )
                                        }


                                        /** see [update_times_for_sent_messages] for details on times stored **/
                                        chatRoomsDataSource.updateChatRoomObservedTimeUserLastActiveTimeMatchingOid(
                                            messageEntity.chatRoomId,
                                            returnTimestamp
                                        )

                                        messageEntity.messageObservedTime = returnTimestamp

                                        errorReturn.first

                                    }
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE,
                                    TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
                                    null,
                                    -> {
                                        //This logout will remove the message from the database (along with all the other messages)
                                        returnErrorStatusEnum =
                                            GrpcFunctionErrorStatusEnum.LOG_USER_OUT

                                        "Invalid message type was attempted to be passed to the server.\n" +
                                                "response: $response"
                                    }
                                }
                            }
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            //NOTE: request and response are saved below
                            "DO_NOTHING was reached which should never happen.\n"
                        }
                        else -> {

                            if (!messageAlreadyStoredInDatabase) {

                                byteArrayReturnValues =
                                    storeMessageToDatabaseAsUnsent(
                                        messageEntity,
                                        messagesDataSource,
                                        thumbnailForReply,
                                        applicationContext,
                                        mimeTypeDataSource,
                                        this,
                                        deleteFileInterface,
                                        errorHandling
                                    )

                            }

                            errorReturn.first
                        }
                    }
            }

            if (byteArrayReturnValues.byteArrayRequiresStoring) {

                //NOTE: not using another coRoutine because I only want the database values stored if the file
                // is successfully saved to 'disk'
                //save the picture to file
                saveChatMessageByteArrayToFile(
                    byteArrayReturnValues,
                    thumbnailForReply,
                    messageEntity,
                    messagesDataSource,
                    errorHandling
                )
            }

            if (returnString != "~") {
                returnString += "\n"
                returnString += convertClientMessageToServerRequestToErrorString(request)
                returnString += "$response\n"

                errorMessageRepositoryHelper(
                    returnString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors(),
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    errorHandling,
                    ioDispatcher
                )
            }

            return@withContext returnErrorStatusEnum
        } else {

            Log.i(
                "sendingMessage",
                "loginToken invalid loginToken: $loginToken GlobalValues.INVALID_LOGIN_TOKEN: ${GlobalValues.INVALID_LOGIN_TOKEN}"
            )

            if (!messageAlreadyStoredInDatabase) {

                val transactionWrapper =
                    ServiceLocator.provideTransactionWrapper(
                        applicationContext,
                        DatabasesToRunTransactionIn.MESSAGES,
                        DatabasesToRunTransactionIn.OTHER_USERS
                    )

                transactionWrapper.runTransaction {

                    byteArrayReturnValues = storeMessageToDatabaseAsUnsent(
                        messageEntity,
                        messagesDataSource,
                        thumbnailForReply,
                        applicationContext,
                        mimeTypeDataSource,
                        this,
                        deleteFileInterface,
                        errorHandling
                    )
                }
            }

            if (byteArrayReturnValues.byteArrayRequiresStoring) {

                //NOTE: not using another coRoutine because I only want the database values stored if the file
                // is successfully saved to 'disk'
                //save the picture to file
                saveChatMessageByteArrayToFile(
                    byteArrayReturnValues,
                    thumbnailForReply,
                    messageEntity,
                    messagesDataSource,
                    errorHandling
                )
            }

            return@withContext GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
        }
    }

private suspend fun saveChatMessageByteArrayToFile(
    byteArrayReturnValues: StoreUnsentMessageReturnValues,
    thumbnailForReply: ByteArray,
    messageEntity: MessagesDataEntity,
    messagesDaoDataSource: MessagesDaoIntermediateInterface,
    errorStore: StoreErrorsInterface
) {
    try {
        byteArrayReturnValues.thumbnailFile?.writeBytes(thumbnailForReply)
        val thumbnailPath = byteArrayReturnValues.thumbnailFile?.absolutePath ?: ""

        messageEntity.replyIsFromThumbnailFilePath = thumbnailPath
        messagesDaoDataSource.updateReplyIsFromThumbnailFilePath(
            messageEntity.messageUUIDPrimaryKey,
            thumbnailPath
        )
    } catch (ex: IOException) {

        val errorMessage =
            "A message was sent to the server, however then the thumbnailFile could not be written.\n" +
                    "exception: ${ex.message}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            ex.stackTraceToString(),
            errorMessage
        )
    }
}

data class StoreUnsentMessageReturnValues(
    var thumbnailFile: File? = null,
    var byteArrayRequiresStoring: Boolean = false,
)

private suspend fun storeMessageToDatabaseAsUnsent(
    messageEntity: MessagesDataEntity,
    messagesDataSource: MessagesDaoIntermediateInterface,
    thumbnailForReply: ByteArray,
    applicationContext: Context,
    mimeTypeDataSource: MimeTypeDaoIntermediateInterface,
    transactionWrapper: TransactionWrapper,
    deleteFileInterface: StartDeleteFileInterface,
    errorStore: StoreErrorsInterface
): StoreUnsentMessageReturnValues {

    val returnVal = StoreUnsentMessageReturnValues()

    transactionWrapper.runTransaction {

        val messageType =
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                messageEntity.messageType
            )

        if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE
            || messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE
        ) { //if message is of type UPDATE_CHAT_ROOM_OBSERVED_TIME or USER_ACTIVITY_DETECTED_VALUE

            //remove any previous of this message type
            messagesDataSource.removeAllMessagesOfPassedTypeType(
                messageEntity.chatRoomId,
                messageType.number
            )
        }

        if (messageEntity.isReply && chatRoomMessageIsAbleToReply(messageType) && thumbnailForReply.isNotEmpty() &&
            (messageEntity.replyType == TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY.number
                    || messageEntity.replyType == TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY.number)
        ) { //if message is reply type and a thumbnail file needs to be stored

            returnVal.thumbnailFile = generateReplyThumbnailFile(
                messageEntity.messageUUIDPrimaryKey,
                applicationContext
            )

            returnVal.byteArrayRequiresStoring = true
        }

        //NOTE: this will be a transaction so order of database events doesn't matter as long as
        // the outcome is the same (it doesn't matter if say this message is inserted before or after
        // the other message is modified)
        if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE) {
            //remove the modified message
            messagesDataSource.removeSingleMessageByUUID(
                messageEntity.modifiedMessageUUID,
                { picturePath ->
                    deleteFileInterface.sendFileToWorkManager(
                        picturePath
                    )
                },
                { gifURL ->
                    deleteGif(
                        mimeTypeDataSource,
                        gifURL,
                        deleteFileInterface,
                        errorStore
                    )
                },
                this
            )
        } else if (messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE) {

            //update the respective message text and that it was properly received by the server
            messagesDataSource.updateMessageToEditedAndSentByServer(
                messageEntity.modifiedMessageUUID,
                TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO,
                hasCompleteInfo = true,
                messageEntity.messageText,
                -1L,
                editHasBeenSent = false
            )
        }

        //insert messages here
        messagesDataSource.insertMessage(messageEntity)

        //updateUserLastObservedTime will lead to a bug when
        // 1) user goes offline
        // 2) another user sends a message
        // 3) this user sends a message (which updates the observed time)
        // 4) now the user won't see a red dot for the previous message
//        chatRoomsDataSource.updateUserLastObservedTime(
//            messageEntity.chatRoomId,
//            messageEntity.messageObservedTime
//        )

    }

    return returnVal
}

private fun convertClientMessageToServerRequestToErrorString(request: ClientMessageToServerRequest): String {

    return "current_account_id: ${request.loginInfo.currentAccountId}\n" +
            "logged_in_token: ${request.loginInfo.loggedInToken}\n" +
            "lets_go_version: ${request.loginInfo.letsGoVersion}\n" +
            "installation_id: ${request.loginInfo.installationId}\n" +
            "timestamp_observed: ${request.timestampObserved}\n" +
            convertTypeOfChatMessageToErrorString(request.message, " ")
}

@Suppress("SameParameterValue")
private fun convertChatRoomMemberInfoMessageToErrorString(
    memberInfo: ChatRoomInfoMessageOuterClass.ChatRoomMemberInfoMessage,
    initialIndentation: String,
    indentation: String,
): String {

    var returnString =
        "$initialIndentation member_info\n" +
                "$initialIndentation $indentation user_info\n" +
                "$initialIndentation $indentation $indentation account_oid: ${memberInfo.userInfo.accountOid}\n" +
                "$initialIndentation $indentation $indentation account_name: ${memberInfo.userInfo.accountName}\n" +
                "$initialIndentation $indentation $indentation account_thumbnail(size): ${memberInfo.userInfo.accountThumbnail.size()}\n" +
                "$initialIndentation $indentation $indentation account_thumbnail_size: ${memberInfo.userInfo.accountThumbnailSize}\n" +
                "$initialIndentation $indentation $indentation picture\n"

    for (i in memberInfo.userInfo.pictureList.indices) {
        returnString +=
            "$initialIndentation $indentation $indentation $indentation picture_array_index: $i\n" +
                    "$initialIndentation $indentation $indentation $indentation file_in_bytes(size): ${memberInfo.userInfo.pictureList[i].fileInBytes.size()}\n" +
                    "$initialIndentation $indentation $indentation $indentation file_size: ${memberInfo.userInfo.pictureList[i].fileSize}\n" +
                    "$initialIndentation $indentation $indentation $indentation index_number: ${memberInfo.userInfo.pictureList[i].indexNumber}\n" +
                    "$initialIndentation $indentation $indentation $indentation pic_height: ${memberInfo.userInfo.pictureList[i].picHeight}\n" +
                    "$initialIndentation $indentation $indentation $indentation pic_width: ${memberInfo.userInfo.pictureList[i].picWidth}\n" +
                    "$initialIndentation $indentation $indentation $indentation timestamp_picture_last_updated: ${memberInfo.userInfo.pictureList[i].timestampPictureLastUpdated}\n"
    }

    returnString += "$initialIndentation $indentation $indentation age: ${memberInfo.userInfo.age}\n" +
            "$initialIndentation $indentation $indentation gender: ${memberInfo.userInfo.gender}\n" +
            "$initialIndentation $indentation $indentation city_name: ${memberInfo.userInfo.cityName}\n" +
            "$initialIndentation $indentation $indentation bio: ${memberInfo.userInfo.bio}\n" +
            "$initialIndentation $indentation $indentation distance: ${memberInfo.userInfo.distance}\n" +
            "$initialIndentation $indentation $indentation activities\n"

    for (i in memberInfo.userInfo.activitiesList.indices) {
        returnString +=
            "$initialIndentation $indentation $indentation $indentation activity_type: ${memberInfo.userInfo.activitiesList[i].activityIndex}\n" +
                    "$initialIndentation $indentation $indentation $indentation time_frame_array\n"

        for (j in memberInfo.userInfo.activitiesList[i].timeFrameArrayList.indices) {
            returnString +=
                "$initialIndentation $indentation $indentation $indentation $indentation start_time_frame: ${memberInfo.userInfo.activitiesList[i].timeFrameArrayList[j].startTimeFrame}\n" +
                        "$initialIndentation $indentation $indentation $indentation $indentation stop_time_frame: ${memberInfo.userInfo.activitiesList[i].timeFrameArrayList[j].stopTimeFrame}\n"
        }
    }

    return returnString
}

@Suppress("SameParameterValue")
private fun convertActiveMessageInfoToErrorString(
    activeMessageInfo: TypeOfChatMessageOuterClass.ActiveMessageInfo,
    initialIndentation: String,
    indentation: String,
): String {

    var returnString = "$initialIndentation active_message_info\n" +
            "$initialIndentation $indentation isDeleted: ${activeMessageInfo.isDeleted}\n" +
            "$initialIndentation $indentation isReply: ${activeMessageInfo.isReply}\n"

    if (activeMessageInfo.isReply) {
        returnString +=
            "$initialIndentation $indentation reply_info\n" +
                    "$initialIndentation $indentation $indentation reply_is_sent_from_user_oid ${activeMessageInfo.replyInfo.replyIsSentFromUserOid}\n" +
                    "$initialIndentation $indentation $indentation reply_is_to_message_uuid ${activeMessageInfo.replyInfo.replyIsToMessageUuid}\n" +
                    "$initialIndentation $indentation $indentation reply_specifics\n" +
                    "$initialIndentation $indentation $indentation $indentation reply_type ${activeMessageInfo.replyInfo.replySpecifics.replyBodyCase}\n"

        when (activeMessageInfo.replyInfo.replySpecifics.replyBodyCase) {
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {
                returnString +=
                    "$initialIndentation $indentation $indentation $indentation $indentation message_text ${activeMessageInfo.replyInfo.replySpecifics.textReply.messageText}\n"
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY -> {
                returnString +=
                    "$initialIndentation $indentation $indentation $indentation $indentation thumbnail_file_size ${activeMessageInfo.replyInfo.replySpecifics.pictureReply.thumbnailFileSize}\n"
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {
                returnString +=
                    "$initialIndentation $indentation $indentation $indentation $indentation thumbnail_file_size ${activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailFileSize}\n" +
                            "$initialIndentation $indentation $indentation $indentation $indentation thumbnail_mime_type ${activeMessageInfo.replyInfo.replySpecifics.mimeReply.thumbnailMimeType}\n"
            }
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY,
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY,
            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET,
            null,
            -> {
            }
        }
    }

    return returnString
}

//converts the TypeOfChatMessage (Grpc message) to an error string and ignores byteArrays
fun convertTypeOfChatMessageToErrorString(
    message: TypeOfChatMessage,
    initialIndentation: String = "",
): String {

    val indentation = " "

    var returnString =
        "$initialIndentation message_type: ${message.messageSpecifics.messageBodyCase}\n" +
                "$initialIndentation StandardChatMessageInfo\n" +
                "$initialIndentation $indentation chat_room_id: ${message.standardMessageInfo.chatRoomIdMessageSentFrom}\n" +
                "$initialIndentation $indentation amount_of_message: ${message.standardMessageInfo.chatRoomIdMessageSentFrom}\n" +
                "$initialIndentation $indentation message_has_complete_info: ${message.standardMessageInfo.chatRoomIdMessageSentFrom}\n" +
                "$initialIndentation $indentation do_not_update_user_state: ${message.standardMessageInfo.doNotUpdateUserState}\n" +
                "$initialIndentation message_specifics\n"

    when (message.messageSpecifics.messageBodyCase) {
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE -> {

            returnString +=
                convertActiveMessageInfoToErrorString(
                    message.messageSpecifics.textMessage.activeMessageInfo,
                    initialIndentation,
                    indentation
                )

            returnString += "$initialIndentation $indentation message_text: ${message.messageSpecifics.textMessage.messageText}\n" +
                    "$initialIndentation $indentation is_edited: ${message.messageSpecifics.textMessage.isEdited}\n" +
                    "$initialIndentation $indentation edited_time: ${message.messageSpecifics.textMessage.editedTime}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE -> {
            returnString +=
                convertActiveMessageInfoToErrorString(
                    message.messageSpecifics.pictureMessage.activeMessageInfo,
                    initialIndentation,
                    indentation
                )

            returnString += "$initialIndentation $indentation picture_file_size: ${message.messageSpecifics.pictureMessage.pictureFileSize}\n" +
                    "$initialIndentation $indentation image_height: ${message.messageSpecifics.pictureMessage.imageHeight}\n" +
                    "$initialIndentation $indentation image_width: ${message.messageSpecifics.pictureMessage.imageWidth}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOCATION_MESSAGE -> {
            returnString +=
                convertActiveMessageInfoToErrorString(
                    message.messageSpecifics.locationMessage.activeMessageInfo,
                    initialIndentation,
                    indentation
                )

            returnString += "$initialIndentation $indentation longitude: ${message.messageSpecifics.locationMessage.longitude}\n" +
                    "$initialIndentation $indentation latitude: ${message.messageSpecifics.locationMessage.latitude}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE -> {
            returnString +=
                convertActiveMessageInfoToErrorString(
                    message.messageSpecifics.mimeTypeMessage.activeMessageInfo,
                    initialIndentation,
                    indentation
                )

            returnString += "$initialIndentation $indentation image_height: ${message.messageSpecifics.mimeTypeMessage.imageHeight}\n" +
                    "$initialIndentation $indentation image_width: ${message.messageSpecifics.mimeTypeMessage.imageWidth}\n" +
                    "$initialIndentation $indentation url_of_download: ${message.messageSpecifics.mimeTypeMessage.urlOfDownload}\n" +
                    "$initialIndentation $indentation mime_type: ${message.messageSpecifics.mimeTypeMessage.mimeType}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.INVITE_MESSAGE -> {
            returnString +=
                convertActiveMessageInfoToErrorString(
                    message.messageSpecifics.inviteMessage.activeMessageInfo,
                    initialIndentation,
                    indentation
                )

            returnString += "$initialIndentation $indentation invited_user_account_oid: ${message.messageSpecifics.inviteMessage.invitedUserAccountOid}\n" +
                    "$initialIndentation $indentation invited_user_name: ${message.messageSpecifics.inviteMessage.invitedUserName}\n" +
                    "$initialIndentation $indentation chat_room_id: ${message.messageSpecifics.inviteMessage.chatRoomId}\n" +
                    "$initialIndentation $indentation chat_room_name: ${message.messageSpecifics.inviteMessage.chatRoomName}\n" +
                    "$initialIndentation $indentation chat_room_password: ${message.messageSpecifics.inviteMessage.chatRoomPassword}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.EDITED_MESSAGE -> {

            returnString += "$initialIndentation $indentation new_message: ${message.messageSpecifics.editedMessage.newMessage}\n" +
                    "$initialIndentation $indentation message_uuid: ${message.messageSpecifics.editedMessage.messageUuid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DELETED_MESSAGE -> {
            returnString += "$initialIndentation $indentation delete_type: ${message.messageSpecifics.deletedMessage.deleteType}\n" +
                    "$initialIndentation $indentation message_uuid: ${message.messageSpecifics.deletedMessage.messageUuid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_KICKED_MESSAGE -> {
            returnString += "$initialIndentation $indentation kicked_account_oid: ${message.messageSpecifics.userKickedMessage.kickedAccountOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_BANNED_MESSAGE -> {
            returnString += "$initialIndentation $indentation kicked_account_oid: ${message.messageSpecifics.userBannedMessage.bannedAccountOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_JOINED_MESSAGE -> {
            returnString = convertChatRoomMemberInfoMessageToErrorString(
                message.messageSpecifics.differentUserJoinedMessage.memberInfo,
                "$initialIndentation $indentation",
                indentation
            )
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.DIFFERENT_USER_LEFT_MESSAGE -> {
            returnString += "$initialIndentation $indentation new_admin_account_oid: ${message.messageSpecifics.differentUserLeftMessage.newAdminAccountOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.UPDATE_OBSERVED_TIME_MESSAGE -> {
            returnString += "$initialIndentation $indentation chat_room_last_observed_time: ${message.messageSpecifics.updateObservedTimeMessage.chatRoomLastObservedTime}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_START_MESSAGE -> {
            returnString +=
                "$initialIndentation $indentation chat_room\n" +
                        "$initialIndentation $indentation $indentation chat_room_id: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomId}\n" +
                        "$initialIndentation $indentation $indentation chat_room_name: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomName}\n" +
                        "$initialIndentation $indentation $indentation chat_room_password: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomPassword}\n" +
                        "$initialIndentation $indentation $indentation account_state: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.accountState}\n" +
                        "$initialIndentation $indentation $indentation user_last_activity_time: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.userLastActivityTime}\n" +
                        "$initialIndentation $indentation $indentation chat_room_last_activity_time: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomLastActivityTime}\n" +
                        "$initialIndentation $indentation $indentation chat_room_last_observed_time: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.chatRoomLastObservedTime}\n" +
                        "$initialIndentation $indentation $indentation time_joined: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.timeJoined}\n" +
                        "$initialIndentation $indentation $indentation match_made_chat_room_oid: ${message.messageSpecifics.thisUserJoinedChatRoomStartMessage.chatRoomInfo.matchMadeChatRoomOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_MEMBER_MESSAGE -> {
            returnString = convertChatRoomMemberInfoMessageToErrorString(
                message.messageSpecifics.thisUserJoinedChatRoomMemberMessage.memberInfo,
                "$initialIndentation $indentation",
                indentation
            )
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_JOINED_CHAT_ROOM_FINISHED_MESSAGE -> {
            returnString += "$initialIndentation $indentation match_made_chat_room_oid: ${message.messageSpecifics.thisUserJoinedChatRoomFinishedMessage.matchMadeChatRoomOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.THIS_USER_LEFT_CHAT_ROOM_MESSAGE -> {
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE -> {
            returnString += "$initialIndentation $indentation new_chat_room_name: ${message.messageSpecifics.chatRoomNameUpdatedMessage.newChatRoomName}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_PASSWORD_UPDATED_MESSAGE -> {
            returnString += "$initialIndentation $indentation new_chat_room_password: ${message.messageSpecifics.chatRoomPasswordUpdatedMessage.newChatRoomPassword}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_ADMIN_PROMOTED_MESSAGE -> {
            returnString += "$initialIndentation $indentation new_chat_room_password: ${message.messageSpecifics.newAdminPromotedMessage.promotedAccountOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MATCH_CANCELED_MESSAGE -> {
            returnString += "$initialIndentation $indentation matched_account_oid: ${message.messageSpecifics.matchCanceledMessage.matchedAccountOid}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_PINNED_LOCATION_MESSAGE -> {
            returnString += "$initialIndentation $indentation longitude: ${message.messageSpecifics.newPinnedLocationMessage.longitude}\n"
            returnString += "$initialIndentation $indentation latitude: ${message.messageSpecifics.newPinnedLocationMessage.latitude}\n"
        }
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.USER_ACTIVITY_DETECTED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_CAP_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.HISTORY_CLEARED_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.LOADING_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.NEW_UPDATE_TIME_MESSAGE,
        TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MESSAGEBODY_NOT_SET,
        null,
        -> {
        }
    }

    return returnString
}

//Parameter, chat room name
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runCreateChatRoom(
    chatRoomName: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): Pair<GrpcFunctionErrorStatusEnum, ChatRoomWithMemberMapDataClass> {

    //extract loginToken
    val loginToken = loginTokenIsValid()

    if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

        var chatRoomDataClass = ChatRoomDataClass()

        val request = ChatRoomCommands.CreateChatRoomRequest.newBuilder()
            .setLoginInfo(getLoginInfo(loginToken))
            .setChatRoomName(chatRoomName)
            .build()

        val response = clientsIntermediate.createChatRoom(request)

        val errorReturn =
            checkApplicationReturnStatusEnum(
                response.response.returnStatus,
                response
            )

        val returnErrorStatusEnum = errorReturn.second
        val returnString =
            when (returnErrorStatusEnum) {
                GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                    val lastActivityTimeTimestamp = response.response.lastActivityTimeTimestamp

                    chatRoomDataClass = ChatRoomDataClass(
                        response.response.chatRoomId,
                        response.response.chatRoomName,
                        response.response.chatRoomPassword,
                        notificationsEnabled = true,
                        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
                        mutableListOf(),
                        lastActivityTimeTimestamp,
                        "",
                        lastActivityTimeTimestamp, //setting this to be consistent with server
                        lastActivityTimeTimestamp,
                        lastActivityTimeTimestamp,
                        lastActivityTimeTimestamp,
                        GlobalValues.server_imported_values.eventIdDefault,
                        GlobalValues.server_imported_values.qrCodeDefault,
                        GlobalValues.server_imported_values.qrCodeMessageDefault,
                        GlobalValues.server_imported_values.qrCodeTimeUpdatedDefault,
                        GlobalValues.server_imported_values.pinnedLocationDefaultLongitude,
                        GlobalValues.server_imported_values.pinnedLocationDefaultLatitude,
                    )

                    //insert new chat room to database
                    chatRoomsDaoDataSource.insertChatRoom(chatRoomDataClass)

                    //NOTE: No transaction needed here, the chat room will be checked to exist inside receiveMessage().
                    (applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                        response.response.chatRoomCapMessage,
                        calledFromJoinChatRoom = false
                    )

                    //NOTE: No transaction needed here, the chat room will be checked to exist inside receiveMessage().
                    applicationContext.chatStreamObject.receiveMessage(
                        response.response.currentUserJoinedChatMessage,
                        calledFromJoinChatRoom = false
                    )

                    errorReturn.first
                }
                GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                    "DO_NOTHING was reached which should never happen.\n" +
                            "response: $response"
                }
                else -> {
                    errorReturn.first
                }
            }

        if (returnString != "~") {
            errorMessageRepositoryHelper(
                returnString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName,
                printStackTraceForErrors(),
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                errorHandling,
                ioDispatcher
            )
        }

        return Pair(
            returnErrorStatusEnum,
            ChatRoomWithMemberMapDataClass(chatRoomDataClass)
        )
    } else { //login token is invalid
        return Pair(
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
            ChatRoomWithMemberMapDataClass()
        )
    }
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runLeaveChatRoom(
    chatRoomId: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): GrpcFunctionErrorStatusEnum {

    var returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING

    leaveChatRoomLock.runWithLock(chatRoomId) { wasLocked ->

        if (wasLocked && !chatRoomsDaoDataSource.chatRoomExists(chatRoomId)
        ) { //if this was run while sitting at the lock & the chat room no longer exists
            returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING
            return@runWithLock
        }

        //extract loginToken
        val loginToken = loginTokenIsValid()

        if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

            val request = LeaveChatRoomRequest.newBuilder()
                .setLoginInfo(getLoginInfo(loginToken))
                .setChatRoomId(chatRoomId)
                .build()

            val response = clientsIntermediate.leaveChatRoom(request)

            val errorReturn =
                checkApplicationReturnStatusEnum(
                    response.response.returnStatus,
                    response
                )

            val returnErrorStatusEnum = errorReturn.second
            val returnString =
                when (returnErrorStatusEnum) {
                    GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                        val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                            applicationContext,
                            DatabasesToRunTransactionIn.MESSAGES,
                            DatabasesToRunTransactionIn.OTHER_USERS
                        )
                        transactionWrapper.runTransaction {
                            //remove chat room
                            (applicationContext as LetsGoApplicationClass).chatStreamObject
                                .removeChatRoomFromDatabase(
                                    chatRoomId,
                                    this
                                )
                        }

                        errorReturn.first

                    }
                    GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                        "Do Nothing was reached which should never happen.\n $response \n"
                    }
                    else -> {
                        errorReturn.first
                    }
                }

            if (returnString != "~") {

                errorMessageRepositoryHelper(
                    returnString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName,
                    printStackTraceForErrors(),
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    errorHandling,
                    ioDispatcher
                )
            }

            returnVal = returnErrorStatusEnum
        } else {
            returnVal = GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
        }

    }

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runJoinChatRoom(
    chatRoomId: String,
    chatRoomPassword: String,
    applicationContext: Context,
    clientsIntermediate: ClientsInterface,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher,
    checkPrimer: (
        response: GrpcClientResponse<ChatMessageToClientMessage.ChatMessageToClient>,
        chatRoomStatus: ChatRoomCommands.ChatRoomStatus,
    ) -> JoinChatRoomPrimerValues,
): JoinChatRoomPrimerValues {

    var returnVal =
        JoinChatRoomPrimerValues(
            GrpcFunctionErrorStatusEnum.DO_NOTHING,
            ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED
        )

    joinChatRoomLock.runWithLock(chatRoomId) { wasLocked ->

        if (
            wasLocked && chatRoomsDaoDataSource.chatRoomExists(chatRoomId)
        ) { //if this was run while sitting at the lock & the chat room exists inside the database
            returnVal = JoinChatRoomPrimerValues(
                GrpcFunctionErrorStatusEnum.DO_NOTHING,
                ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED
            )
            return@runWithLock
        }

        //extract loginToken
        val loginToken = loginTokenIsValid()

        returnVal =
            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.JoinChatRoomRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setChatRoomPassword(chatRoomPassword)
                    .build()

                val response =
                    clientsIntermediate.joinChatRoom(
                        applicationContext,
                        request,
                        checkPrimer
                    )

                Log.i("joinChatRoomTime", "joinChatRoom() response: $response")

                if (response.errorMessage != "~") {
                    val errorMessage = "An error occurred when calling joinChatRoom().\n" +
                            "chatRoomId: $chatRoomId\n" +
                            "chatRoomPassword: $chatRoomPassword"

                    CoroutineScope(ioDispatcher).launch {
                        errorMessageRepositoryHelper(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            Thread.currentThread().stackTrace[2].fileName,
                            printStackTraceForErrors(),
                            applicationContext,
                            accountInfoDataSource,
                            accountPicturesDataSource,
                            errorHandling
                        )
                    }
                }

                response.response
            } else {
                JoinChatRoomPrimerValues(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED
                )
            }
    }

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runPromoteNewAdmin(
    promotedUserAccountOID: String,
    chatRoomId: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): PromoteNewAdminReturnValues {

    var returnVal = PromoteNewAdminReturnValues(
        GrpcFunctionErrorStatusEnum.DO_NOTHING,
        false
    )

    promoteNewAdminLock.runIfNotLockedWith(
        chatRoomId + "_" + promotedUserAccountOID,
        {

            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.PromoteNewAdminRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setNewAdminAccountOid(promotedUserAccountOID)
                    .setMessageUuid(generateChatMessageUUID())
                    .build()

                val response = clientsIntermediate.promoteNewAdmin(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                var userAccountStatesMatched = false
                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {
                            userAccountStatesMatched = response.response.userAccountStatesMatched

                            if (response.response.userAccountStatesMatched) {
                                val generatedChatMessage =
                                    ChatMessageToClientMessage.ChatMessageToClient.newBuilder()
                                        .setMessageUuid(request.messageUuid)
                                        .setTimestampStored(response.response.timestampMessageStored)
                                        .setSentByAccountId(LoginFunctions.currentAccountOID)
                                        .setOnlyStoreMessage(false)
                                        .setMessage(
                                            TypeOfChatMessage.newBuilder()
                                                .setMessageSpecifics(
                                                    TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                        .setNewAdminPromotedMessage(
                                                            TypeOfChatMessageOuterClass.NewAdminPromotedChatMessage.newBuilder()
                                                                .setPromotedAccountOid(
                                                                    promotedUserAccountOID
                                                                )
                                                        )
                                                )
                                                .setStandardMessageInfo(
                                                    TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                        .setChatRoomIdMessageSentFrom(chatRoomId)
                                                )
                                        )
                                        .build()

                                val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                    applicationContext,
                                    DatabasesToRunTransactionIn.OTHER_USERS
                                )

                                transactionWrapper.runTransaction {

                                    //update last observed time
                                    //NOTE: this could be don't inside the repository with the other updates, but it is much simpler here and it
                                    // shouldn't be called much
                                    chatRoomsDaoDataSource.updateUserLastObservedTime(
                                        chatRoomId,
                                        response.response.timestampMessageStored
                                    )

                                    //store new admin message
                                    (applicationContext as LetsGoApplicationClass).chatStreamObject
                                        .handleNewAdminPromoted(
                                            promotedUserAccountOID,
                                            generatedChatMessage,
                                            LoginFunctions.currentAccountOID,
                                            this
                                        )

                                }
                            }

                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "Do Nothing was reached which should never happen.\n" +
                                    "request: $request\n" +
                                    "response: $response \n"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {

                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = PromoteNewAdminReturnValues(
                    returnErrorStatusEnum,
                    userAccountStatesMatched
                )
            } else { //invalid login token
                returnVal = PromoteNewAdminReturnValues(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    false
                )
            }
        },
        {
            returnVal = PromoteNewAdminReturnValues(
                GrpcFunctionErrorStatusEnum.DO_NOTHING,
                false
            )
        }
    )

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runUpdateChatRoomInfo(
    chatRoomId: String,
    typeOfInfoToUpdate: ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate,
    newChatRoomInfo: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
):
        UpdateChatRoomInfoReturnValues {

    var returnVal = UpdateChatRoomInfoReturnValues(
        GrpcFunctionErrorStatusEnum.DO_NOTHING,
        false
    )

    updateChatRoomChatRoomLock.runIfNotLockedWith(
        chatRoomId,
        {
            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.UpdateChatRoomInfoRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setTypeOfInfoToUpdate(typeOfInfoToUpdate)
                    .setNewChatRoomInfo(newChatRoomInfo)
                    .setMessageUuid(generateChatMessageUUID())
                    .build()

                val response = clientsIntermediate.updateChatRoomInfo(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                var operationFailed = false
                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            //If operationFailed==true it means this user was not ACCOUNT_STATE_IS_ADMIN. This
                            // could happen if messages get a little out of order.
                            operationFailed = response.response.operationFailed
                            if (!operationFailed) {

                                val typeOfChatMessage =
                                    when (typeOfInfoToUpdate) {
                                        ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_NAME -> {
                                            TypeOfChatMessage.newBuilder()
                                                .setMessageSpecifics(
                                                    TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                        .setChatRoomNameUpdatedMessage(
                                                            TypeOfChatMessageOuterClass.ChatRoomNameUpdatedChatMessage.newBuilder()
                                                                .setNewChatRoomName(newChatRoomInfo)
                                                        )
                                                )
                                                .setStandardMessageInfo(
                                                    TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                        .setChatRoomIdMessageSentFrom(chatRoomId)
                                                )
                                                .build()
                                        }
                                        ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UPDATE_CHAT_ROOM_PASSWORD -> {
                                            TypeOfChatMessage.newBuilder()
                                                .setMessageSpecifics(
                                                    TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                        .setChatRoomPasswordUpdatedMessage(
                                                            TypeOfChatMessageOuterClass.ChatRoomPasswordUpdatedChatMessage.newBuilder()
                                                                .setNewChatRoomPassword(
                                                                    newChatRoomInfo
                                                                )
                                                        )
                                                )
                                                .setStandardMessageInfo(
                                                    TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                        .setChatRoomIdMessageSentFrom(chatRoomId)
                                                )
                                                .build()
                                        }
                                        ChatRoomCommands.UpdateChatRoomInfoRequest.ChatRoomTypeOfInfoToUpdate.UNRECOGNIZED -> {
                                            returnVal = UpdateChatRoomInfoReturnValues(
                                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
                                                false
                                            )

                                            val errorMessage =
                                                "Invalid 'TypeOfInfoToUpdate' passed to runUpdateChatRoomInfo().\n" +
                                                        "typeOfInfoToUpdate: $typeOfInfoToUpdate\n" +
                                                        "chatRoomId: $chatRoomId\n" +
                                                        "newChatRoomInfo: $newChatRoomInfo\n"

                                            errorMessageRepositoryHelper(
                                                errorMessage,
                                                Thread.currentThread().stackTrace[2].lineNumber,
                                                Thread.currentThread().stackTrace[2].fileName,
                                                printStackTraceForErrors(),
                                                applicationContext,
                                                accountInfoDataSource,
                                                accountPicturesDataSource,
                                                errorHandling,
                                                ioDispatcher
                                            )

                                            return@runIfNotLockedWith
                                        }
                                    }

                                val generatedChatMessage =
                                    ChatMessageToClientMessage.ChatMessageToClient.newBuilder()
                                        .setMessageUuid(request.messageUuid)
                                        .setTimestampStored(response.response.timestampMessageStored)
                                        .setSentByAccountId(LoginFunctions.currentAccountOID)
                                        .setOnlyStoreMessage(false)
                                        .setMessage(typeOfChatMessage)
                                        .build()

                                val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                    applicationContext,
                                    DatabasesToRunTransactionIn.OTHER_USERS
                                )

                                transactionWrapper.runTransaction {

                                    //update last observed time
                                    //NOTE: this could be don't inside the repository with the other updates, but it is much simpler here and it
                                    // shouldn't be called much
                                    chatRoomsDaoDataSource.updateUserLastObservedTime(
                                        chatRoomId,
                                        response.response.timestampMessageStored
                                    )

                                    //store new admin message
                                    (applicationContext as LetsGoApplicationClass).chatStreamObject
                                        .handleUpdateChatRoomInfo(
                                            generatedChatMessage,
                                            this
                                        )

                                }

                            }

                            errorReturn.first

                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "DO_NOTHING was reached which should never happen here.\n" +
                                    "request: $request\n" +
                                    "response: $response"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {
                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = UpdateChatRoomInfoReturnValues(
                    returnErrorStatusEnum,
                    operationFailed
                )

            } else { //login token not valid
                returnVal = UpdateChatRoomInfoReturnValues(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    false
                )
            }
        },
        {
            returnVal = UpdateChatRoomInfoReturnValues(
                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                false
            )
        }
    )

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runSetPinnedLocation(
    chatRoomId: String,
    longitude: Double,
    latitude: Double,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): SetPinnedLocationReturnValues {

    var returnVal = SetPinnedLocationReturnValues(
        GrpcFunctionErrorStatusEnum.DO_NOTHING,
        false
    )

    updateChatRoomChatRoomLock.runIfNotLockedWith(
        chatRoomId,
        {
            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.SetPinnedLocationRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setNewPinnedLongitude(longitude)
                    .setNewPinnedLatitude(latitude)
                    .setMessageUuid(generateChatMessageUUID())
                    .build()

                val response = clientsIntermediate.setPinnedLocation(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                var operationFailed = false
                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            //If operationFailed==true it means this user was not ACCOUNT_STATE_IS_ADMIN. This
                            // could happen if messages get a little out of order.
                            operationFailed = response.response.operationFailed
                            if (!operationFailed) {

                                val typeOfChatMessage =
                                    TypeOfChatMessage.newBuilder()
                                        .setMessageSpecifics(
                                            TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                .setNewPinnedLocationMessage(
                                                    TypeOfChatMessageOuterClass.NewPinnedLocationMessage.newBuilder()
                                                        .setLongitude(longitude)
                                                        .setLatitude(latitude)
                                                )
                                        )
                                        .setStandardMessageInfo(
                                            TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                .setChatRoomIdMessageSentFrom(chatRoomId)
                                        )
                                        .build()

                                val generatedChatMessage =
                                    ChatMessageToClientMessage.ChatMessageToClient.newBuilder()
                                        .setMessageUuid(request.messageUuid)
                                        .setTimestampStored(response.response.timestampMessageStored)
                                        .setSentByAccountId(LoginFunctions.currentAccountOID)
                                        .setOnlyStoreMessage(false)
                                        .setMessage(typeOfChatMessage)
                                        .build()

                                val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                    applicationContext,
                                    DatabasesToRunTransactionIn.OTHER_USERS
                                )

                                transactionWrapper.runTransaction {

                                    //This could be done inside the repository with the other
                                    // updates, but it is much simpler here and it shouldn't be
                                    // called much.
                                    chatRoomsDaoDataSource.updateUserLastObservedTime(
                                        chatRoomId,
                                        response.response.timestampMessageStored
                                    )

                                    //store new admin message
                                    (applicationContext as LetsGoApplicationClass).chatStreamObject
                                        .handleUpdateChatRoomInfo(
                                            generatedChatMessage,
                                            this
                                        )
                                }

                            }

                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "DO_NOTHING was reached which should never happen here.\n" +
                                    "request: $request\n" +
                                    "response: $response"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {
                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = SetPinnedLocationReturnValues(
                    returnErrorStatusEnum,
                    operationFailed
                )

            } else { //login token not valid
                returnVal = SetPinnedLocationReturnValues(
                    GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                    false
                )
            }
        },
        {
            returnVal = SetPinnedLocationReturnValues(
                GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID,
                false
            )
        }
    )

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runRemoveFromChatRoom(
    chatRoomId: String,
    kickOrBan: ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan,
    accountOIDToRemove: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    chatRoomsDaoDataSource: ChatRoomsIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): GrpcFunctionErrorStatusEnum {

    var returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING

    removeFromChatRoomLock.runIfNotLockedWith(
        chatRoomId + "_" + accountOIDToRemove,
        {

            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.RemoveFromChatRoomRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setKickOrBan(kickOrBan)
                    .setAccountIdToRemove(accountOIDToRemove)
                    .setMessageUuid(generateChatMessageUUID())
                    .build()

                val response = clientsIntermediate.removeFromChatRoom(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            //This could mean the sending user was not admin OR if the target
                            // user was not in the chat room. These could happen if this user promoted
                            // someone else to admin while this was processing. Or if the other user
                            // left while this was processing
                            if (response.response.operationFailed) {
                                returnVal = returnErrorStatusEnum
                                return@runIfNotLockedWith
                            }

                            val typeOfChatMessage =
                                if (kickOrBan == ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK) {
                                    TypeOfChatMessage.newBuilder()
                                        .setMessageSpecifics(
                                            TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                .setUserKickedMessage(
                                                    TypeOfChatMessageOuterClass.UserKickedChatMessage.newBuilder()
                                                        .setKickedAccountOid(accountOIDToRemove)
                                                )
                                        )
                                        .setStandardMessageInfo(
                                            TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                .setChatRoomIdMessageSentFrom(chatRoomId)
                                        )
                                        .build()
                                } else {
                                    TypeOfChatMessage.newBuilder()
                                        .setMessageSpecifics(
                                            TypeOfChatMessageOuterClass.MessageSpecifics.newBuilder()
                                                .setUserBannedMessage(
                                                    TypeOfChatMessageOuterClass.UserBannedChatMessage.newBuilder()
                                                        .setBannedAccountOid(accountOIDToRemove)
                                                )
                                        )
                                        .setStandardMessageInfo(
                                            TypeOfChatMessageOuterClass.StandardChatMessageInfo.newBuilder()
                                                .setChatRoomIdMessageSentFrom(chatRoomId)
                                        )
                                        .build()
                                }

                            val generatedChatMessage =
                                ChatMessageToClientMessage.ChatMessageToClient.newBuilder()
                                    .setMessageUuid(request.messageUuid)
                                    .setTimestampStored(response.response.timestampStored)
                                    .setSentByAccountId(LoginFunctions.currentAccountOID)
                                    .setOnlyStoreMessage(false)
                                    .setMessage(typeOfChatMessage)
                                    .build()

                            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                applicationContext,
                                DatabasesToRunTransactionIn.MESSAGES,
                                DatabasesToRunTransactionIn.OTHER_USERS
                            )

                            transactionWrapper.runTransaction {
                                //update last observed time
                                chatRoomsDaoDataSource.updateUserLastObservedTime(
                                    chatRoomId,
                                    response.response.timestampStored
                                )

                                //store new admin message
                                (applicationContext as LetsGoApplicationClass).chatStreamObject
                                    .handleKickBanMessage(
                                        generatedChatMessage,
                                        this,
                                        accountOIDToRemove
                                    )
                            }
                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "DO_NOTHING was reached which should never happen here.\n" +
                                    "request: $request \n" +
                                    "response: $response \n"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {

                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = returnErrorStatusEnum
            } else {
                returnVal = GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
            }

        },
        {
            returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING
        }
    )

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
suspend fun runUnMatchFromChatRoom(
    chatRoomId: String,
    matchedAccountOID: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): GrpcFunctionErrorStatusEnum {

    var returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING

    unMatchUserLock.runIfNotLockedWith(
        matchedAccountOID,
        {

            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.UnMatchRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setChatRoomId(chatRoomId)
                    .setMatchedAccountOid(matchedAccountOID)
                    .build()

                val response = clientsIntermediate.unMatchFromChatRoom(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                applicationContext,
                                DatabasesToRunTransactionIn.MESSAGES,
                                DatabasesToRunTransactionIn.OTHER_USERS
                            )
                            transactionWrapper.runTransaction {
                                (applicationContext as LetsGoApplicationClass).chatStreamObject
                                    .removeChatRoomFromDatabase(
                                        chatRoomId,
                                        this
                                    )
                            }

                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "Do Nothing was reached which should never happen.\n" +
                                    "request: $request \n" +
                                    "response: ${response.response} \n"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {

                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = returnErrorStatusEnum

            } else {
                returnVal = GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
            }

        },
        {
            returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING
        }
    )
    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
//NOTE: chatRoomId is only necessary when unMatch is set to true
suspend fun runBlockAndReportFromChatRoom(
    matchOptionsBuilder: ReportMessages.UserMatchOptionsRequest.Builder,
    chatRoomId: String,
    unMatch: Boolean,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    sharedApplicationViewModelInstanceId: String,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): GrpcFunctionErrorStatusEnum {

    var returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING

    blockAndReportUserLock.runIfNotLockedWith(
        chatRoomId + matchOptionsBuilder.matchAccountId,
        {

            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val matchOptions = matchOptionsBuilder
                    .setLoginInfo(getLoginInfo(loginToken))
                    .build()

                val request = ChatRoomCommands.BlockAndReportChatRoomRequest.newBuilder()
                    .setMatchOptionsRequest(matchOptions)
                    .setChatRoomId(chatRoomId)
                    .setUnMatch(unMatch)
                    .build()

                val response = clientsIntermediate.blockAndReportChatRoom(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            val transactionWrapper =
                                if (unMatch) {
                                    ServiceLocator.provideTransactionWrapper(
                                        applicationContext,
                                        DatabasesToRunTransactionIn.ACCOUNTS,
                                        DatabasesToRunTransactionIn.MESSAGES,
                                        DatabasesToRunTransactionIn.OTHER_USERS
                                    )
                                } else {
                                    ServiceLocator.provideTransactionWrapper(
                                        applicationContext,
                                        DatabasesToRunTransactionIn.ACCOUNTS,
                                    )
                                }

                            transactionWrapper.runTransaction {

                                (applicationContext as LetsGoApplicationClass).applicationRepository.addAccountToBlockedList(
                                    matchOptions.matchAccountId,
                                    sharedApplicationViewModelInstanceId,
                                    !unMatch, //if already running unMatch, no need to check for it
                                    this
                                )

                                //NOTE: unMatch is passed to the server and it removes the match before this is called.
                                if (unMatch) { //if un matches
                                    //remove chat room
                                    applicationContext.chatStreamObject
                                        .removeChatRoomFromDatabase(
                                            chatRoomId,
                                            this
                                        )
                                }
                            }

                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "Do Nothing was reached which should never happen.\n" +
                                    "request: $request \n" +
                                    "response: $response \n"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {

                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = returnErrorStatusEnum

            } else {
                returnVal = GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
            }
        },
        {
            returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING
        }
    )

    return returnVal
}

//runs the gRPC RPC calls using request type GenericRequestDataClass
//Returns
// ChatRoomDataClass is the new chat room this user has joined
// ErrorStatusEnum is the message of how to handle the return status
//messageEntity will be modified if NO_ERRORS is returned
//NOTE: chatRoomId is only necessary when unMatch is set to true
suspend fun runUnblockOtherUser(
    userToUnblockAccountId: String,
    applicationContext: Context,
    accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    clientsIntermediate: ClientsInterface,
    errorHandling: StoreErrorsInterface,
    ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher,
): GrpcFunctionErrorStatusEnum {

    var returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING

    unblockUserLock.runIfNotLockedWith(
        userToUnblockAccountId,
        {

            //extract loginToken
            val loginToken = loginTokenIsValid()

            if (loginToken != GlobalValues.INVALID_LOGIN_TOKEN) { //if login token is found

                val request = ChatRoomCommands.UnblockOtherUserRequest.newBuilder()
                    .setLoginInfo(getLoginInfo(loginToken))
                    .setUserToUnblockAccountId(userToUnblockAccountId)
                    .build()

                val response = clientsIntermediate.unblockOtherUser(request)

                val errorReturn =
                    checkApplicationReturnStatusEnum(
                        response.response.returnStatus,
                        response
                    )

                val returnErrorStatusEnum = errorReturn.second
                val returnString =
                    when (returnErrorStatusEnum) {
                        GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                                applicationContext,
                                DatabasesToRunTransactionIn.ACCOUNTS
                            )

                            transactionWrapper.runTransaction {
                                accountInfoDataSource.removeBlockedAccount(
                                    userToUnblockAccountId,
                                    this
                                )

                                this.runAfterTransaction {
                                    //run this if above is successful
                                    GlobalValues.blockedAccounts.remove(userToUnblockAccountId)
                                }
                            }

                            errorReturn.first
                        }
                        GrpcFunctionErrorStatusEnum.DO_NOTHING -> {
                            "Do Nothing was reached which should never happen.\n" +
                                    "request: $request\n" +
                                    "response: $response"
                        }
                        else -> {
                            errorReturn.first
                        }
                    }

                if (returnString != "~") {

                    errorMessageRepositoryHelper(
                        returnString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName,
                        printStackTraceForErrors(),
                        applicationContext,
                        accountInfoDataSource,
                        accountPicturesDataSource,
                        errorHandling,
                        ioDispatcher
                    )
                }

                returnVal = returnErrorStatusEnum
            } else {
                returnVal = GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID
            }
        },
        {
            returnVal = GrpcFunctionErrorStatusEnum.DO_NOTHING
        }
    )

    return returnVal
}
