package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

//TODO: Write Documentation for ChatStreamObject, some NOTES for it are listed below (some may be obsolete).
//DOCUMENTATION:
//-general system on how the chat stream works, including messages sent with their response as well as the received by system
//-specifics and special cases for message
//-example: EDITED_MESSAGE is never stored inside the messages list, it is just sent down to the database and back up to the fragment from recieveMessages
//-- als an EDITED_MESSAGE type inside the database means that it was sent by the current user, because when one is received from the server it is not stored, this is relevant to the chatRoomFragment in how to handle them inside new messages
//-example: in receiveMessage when onlyStoreMessage type returns chatRoomActiveTime is updated for INVITE_MESSAGE type (if its greater than stored one)
//-message stream, order is important however it may be able to do something like
//--1) message1 is sent to server
//--2) immediatly after message1 is sent chat-streamA ends
//--3) chat-streamB starts
//--4) message2 is sent through chat-streamB
//--5) message1 chat-streamA thread finishes slower than message2 chat-streamB thread
//--6) message2 is stored first
//--this could cause problems with MESSAGE_DELETED and MESSAGE_EDITED however it would require a lot to happen in order to be a problem
//-the message stream needs to restart itself and overlap, also there is a variable called initialLogin that will not re-request still except the first time
//--this allows for the chat stream to overlap and not simply end, and not get duplicates because it does no request info except on the first start
//--if the time between the GlobalValues.chatRoomStreamDeadlineTimeInSeconds and GlobalValues.timeBetweenChatRoomStreamReconnection is too small, the chat stream can start multiple instances of itself
//-MESSAGE_DELETE
//--stored on server as 2 types
//--1)DELETE_FOR_SINGLE_USER
//--2)DELETE_FOR_ALL_USERS
//--server checks to make sure user sent that message or is admin for DELETE_FOR_ALL_USERS
//--when sending back message only sends back message oid as oid_value (also message_oid is not sent back so client can not store this message, just like MESSAGE_EDITED)
//--on the client this will remove the modified message from the database and NOT store the MESSAGE_DELETED message itself, however because inside of chatRoomFragment the
//---messages list cannot be removed from the DeleteType enum in TypeOfChatMessage.proto with value DELETED_ON_CLIENT the MESSAGE_EDITED message should only ever be stored if it was
//---NOT sent to the server
//-MESSAGE_EDITED
//--on the client this will edit the modified message inside the database and update the isEdited, editedTime and editHasBeen, it will then NOT store the MESSAGE_EDITED message
//--the MESSAGE_EDITED message should only ever be stored if it was NOT sent to the server
//-MESSAGE
//-there is a block in async_server talking about the bi-di chat stream
//-it is important to call updateChatRoom before starting a new chat room (by navigating to chatRoomFragment)
//-updateMessage on client
//--in order to prevent the same message being requested multiple times by the client which messages are currently being requested is kept track of in 2 places
//---1) the fragment with a bool (it is inside MessagesDataEntityWithAdditionalInfo with the field messageUpdateHasBeenRequestedFromServer)
//---2) the shared view model inside the MutableSet messagesBeingRequestedSet
//--they are kept track of inside of the view model to avoid a situation where
//---1) fragment requests a message
//---2) user navigates away from fragment
//---3) fragment requests the same message again
class ChatStreamObjectTest {
}