package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import java.util.*

//This is set up so that the coroutines will always be launched in order. Because this is called inside a
// mutex inside ChatStreamObject, the messages will always be sent in an ordered fashion. However, if the
// coRoutine is started at other times it can lead to a 'gap' where coRoutines can end up out of order.
// Also SingleThreadExecutor() such as Main seem to launch coRoutines in the order they are queued.
class ChatStreamSubscriberWrapper(
    private var chatStreamSubscriberInterface: ChatStreamSubscriberInterface?,
    private val dispatcher: CoroutineDispatcher
) : ChatStreamSubscriberInterface {

    val instanceID: UUID = UUID.randomUUID()

    override suspend fun clientMessageToServerReturnValue(returnsClientMessageToServerReturnValue: ClientMessageToServerReturnValueDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.clientMessageToServerReturnValue(returnsClientMessageToServerReturnValue)
        }
    }

    override suspend fun returnMessagesForChatRoom(returnsReturnMessagesForChatRoom: ReturnMessagesForChatRoomDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnMessagesForChatRoom(returnsReturnMessagesForChatRoom)
        }
    }

    override suspend fun returnMessageWithMemberForChatRoom(
        returnsReturnMessageWithMemberForChatRoom: ReturnMessageWithMemberForChatRoomDataHolder
    ) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnMessageWithMemberForChatRoom(returnsReturnMessageWithMemberForChatRoom)
        }
    }

    override suspend fun returnAccountStateUpdated(accountStateUpdatedDataHolder: AccountStateUpdatedDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnAccountStateUpdated(accountStateUpdatedDataHolder)
        }
    }

    override suspend fun returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder: ReturnJoinedLeftChatRoomDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnJoinedLeftChatRoom(returnJoinedLeftChatRoomDataHolder)
        }
    }

    override suspend fun returnChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder: UpdateChatRoomInfoResultsDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnChatRoomInfoUpdated(updateChatRoomInfoResultsDataHolder)
        }
    }

    override suspend fun returnKickedBannedFromChatRoom(returnKickedBannedFromChatRoomDataHolder: ReturnKickedBannedFromChatRoomDataHolder) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.returnKickedBannedFromChatRoom(returnKickedBannedFromChatRoomDataHolder)
        }
    }

    override suspend fun receivedMessageUpdateRequestResponse(
        returnMessageUpdateRequestResponseDataHolder: ReturnMessageUpdateRequestResponseDataHolder
    ) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.receivedMessageUpdateRequestResponse(returnMessageUpdateRequestResponseDataHolder)
        }
    }

    override suspend fun chatStreamInitialDownloadsCompleted() {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.chatStreamInitialDownloadsCompleted()
        }
    }

    override suspend fun gRPCErrorOccurred(error: GrpcFunctionErrorStatusEnum) {
        CoroutineScope(dispatcher).launch {
            chatStreamSubscriberInterface?.gRPCErrorOccurred(error)
        }
    }

    //this will avoid the case of an object holding a reference to its parent (SharedApplicationViewModel can do this)
    fun clear() {
        chatStreamSubscriberInterface = null
    }
}