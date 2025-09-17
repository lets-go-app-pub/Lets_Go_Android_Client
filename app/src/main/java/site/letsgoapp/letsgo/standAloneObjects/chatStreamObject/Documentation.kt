@file:Suppress("ClassName", "unused")

package site.letsgoapp.letsgo.standAloneObjects.chatStreamObject

object updating_messages_notes
/**
 * 1) The way updates work is that they send a list of UUIDs to update. ALL UUIDS ARE EXPECTED TO
 *  BELONG TO THE SAME CHAT ROOM (its faster for the server if they are organized this way). Then
 *  a response for each UUID will be sent back from the server.
 * 2) currentlyRequestingMessageUpdates and messageQueue.requested values are NOT cleared when the
 *  chatStream stops, they are cleared after the chatStream starts and receives its initial primer.
 * 3) messageQueue was updated to store individual messages (instead of in 'chunks') for 2 reasons.
 * -1. If an entire message fails to download (ChatStream ends somehow, see 2), it will attempt to
 *  re-download the entire thing. With slow connections this could be a problem.
 * -2. When a lot of updates are set to the server, it can actually block refresh from completing.
 *  So instead of sending 10->10->10->10. It will now wait until the first 10 are back before
 *  requesting the next 10 updates. And the client is able to fit in a refresh to the ChatStream
 *  if needed.
 * **/

object chat_stream_general_loop
/**
 * This shows the general loop for how the chat stream object restarts the stream
 * and what it does when it receives various messages.
 * GrpcAndroidSideErrorsEnum (simply re-stating this enum for sake of clarity)
 * -NO_ANDROID_ERRORS,
 * -CONNECTION_ERROR,
 * -SERVER_DOWN,
 * -UNKNOWN_EXCEPTION

 * onNext() (with responseMutex)
 * -primer
 * --set up stream to refresh or restart
 * --extract and send all messages
 * --requestMutex -> send requests in queue
 * -refresh
 * --set up stream to refresh

 * onError() (do nothing if different stream instance)
 * -convert message and run chatResponseErrorHandler() (shown below)

 * onCompleted() (do nothing if different stream instance)
 * -if !STATUS::OK error & log out
 * -UNKNOWN_STREAM_STOP_REASON
 * --restart
 * -RETURN_STATUS_ATTACHED
 * --follow return status (always an error)
 * -STREAM_TIMED_OUT
 * --error & serverStart()
 * -STREAM_CANCELED_BY_ANOTHER_STREAM
 * --error if current install id, return logged in elsewhere if different user id
 * -SERVER_SHUTTING_DOWN
 * --load balance
 * ---if load balance success; start stream
 * ---else chatResponseErrorHandler()

 * serverStart()
 * -attempt to connect, if fail call chatResponseErrorHandler()

 * chatResponseErrorHandler()
 * -CONNECTION_ERROR,
 * -SERVER_DOWN,
 * --> retry after 5 seconds
 * -NO_ANDROID_ERRORS,
 * -UNKNOWN_EXCEPTION,
 * --> error & log out
 **/