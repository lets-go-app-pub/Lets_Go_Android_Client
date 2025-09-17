@file:Suppress("ClassName", "unused")

package site.letsgoapp.letsgo.repositories.chatRoomCommandsRPCs

object update_times_for_sent_messages
/**
 * The situation for handling the chat room and message times when sending messages is a bit odd. The chat room lastObservedTime is
 *  compared with chatRoomLastActiveTime for the user to see if they have new messages. When the message is sent to the server, the return
 *  value of that message (timestamp_stored) is the chatRoomLastActiveTime (lastObservedTime will also be stored before the message is sent
 *  so the user sees the chat room as not having new messages). If the message is not sent from the client to the server immediately because
 *  the chat stream is not running for whatever reason, then the chat messages are guaranteed to  still be sent before new messages are
 *  requested and so this will still simply update the observed time to the latest chatRoomLastActiveTime
 *
 * last_updated_time also cannot be set when a message is sent because a situation can occur (this situation is fairly common b/c all messages
 * have a delay before being received).
 *  1) Another user sends a message. However there is a delay between the message being sent back and the messages' timestamp_stored.
 *  2) Current user sends a message and gets back returnTimestamp that is LATER than the previous message timestamp_stored.
 *  3) A disconnect occurs.
 *  4) Because the current user message updated the last_updated_time, now when the chat stream reconnects it will miss the other message.
 *  So not updating last_update_time even though it leaves the potential for duplicates (they are checked for in ChatStreamObject).
 * **/