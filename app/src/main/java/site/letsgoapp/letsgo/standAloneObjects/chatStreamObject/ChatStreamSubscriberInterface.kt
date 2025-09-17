package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum

interface ChatStreamSubscriberInterface {

    /** All of these are called from Dispatchers.IO. So either need to make sure only 1 thread can touch
     * each variable (this is done in SharedApplicationViewModel by setting withContext to Main) OR use the thread
     * tools to protect them (this is done when NotificationInfo is called from ChatStreamWorker). **/

    //response from sendMessage()
    suspend fun clientMessageToServerReturnValue(returnsClientMessageToServerReturnValue: ClientMessageToServerReturnValueDataHolder)

    //used with message types
    // CHAT_TEXT_MESSAGE, LOCATION_MESSAGE, MIME_TYPE_MESSAGE, INVITED_TO_CHAT_ROOM, PICTURE_MESSAGE,
    // MESSAGE_DELETED, MESSAGE_EDITED, USER_ACTIVITY_DETECTED
    // NEW_ADMIN_PROMOTED (also sends back a returnAccountStateUpdated())
    suspend fun returnMessagesForChatRoom(returnsReturnMessagesForChatRoom: ReturnMessagesForChatRoomDataHolder)

    //used with message types
    // DIFFERENT_USER_JOINED_CHAT_ROOM, DIFFERENT_USER_LEFT_CHAT_ROOM
    // handles message pertaining to other users of type DIFFERENT_USER_KICKED_FROM_CHAT_ROOM, DIFFERENT_USER_BANNED_FROM_CHAT_ROOM
    suspend fun returnMessageWithMemberForChatRoom(returnsReturnMessageWithMemberForChatRoom: ReturnMessageWithMemberForChatRoomDataHolder)

    //used with message types
    // NEW_ADMIN_PROMOTED (also sends back a returnMessagesForChatRoom())
    // DIFFERENT_USER_LEFT_CHAT_ROOM if the user leaving was admin
    suspend fun returnAccountStateUpdated(accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder)

    //used with message types
    // THIS_USER_JOINED_CHAT_ROOM_FINISHED, THIS_USER_LEFT_CHAT_ROOM
    suspend fun returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder: ReturnJoinedLeftChatRoomDataHolder)

    //used with message types
    // CHAT_ROOM_NAME_UPDATED, CHAT_ROOM_PASSWORD_UPDATED, NEW_PINNED_LOCATION_MESSAGE
    suspend fun returnChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder)

    //used with message types
    // handles message pertaining to the current user of type USER_KICKED_FROM_CHAT_ROOM, USER_BANNED_FROM_CHAT_ROOM
    suspend fun returnKickedBannedFromChatRoom(returnKickedBannedFromChatRoomDataHolder: ReturnKickedBannedFromChatRoomDataHolder)

    //used for a response when requestFullMessage is used by application repository
    suspend fun receivedMessageUpdateRequestResponse(returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder)

    //used when the initial info from the chat stream has been downloaded
    suspend fun chatStreamInitialDownloadsCompleted()

    //called by ChatRoomObject when an error occurs that it cannot handle, such as the login token being invalid or an unknown error happening
    // with the stream
    //NOTE: error was already stored
    suspend fun gRPCErrorOccurred(error: GrpcFunctionErrorStatusEnum)
}