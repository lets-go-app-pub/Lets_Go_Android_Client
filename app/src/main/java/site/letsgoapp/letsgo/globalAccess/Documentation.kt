@file:Suppress("ClassName", "unused")

package site.letsgoapp.letsgo.globalAccess

object channel_selection_notes
/**The ManagedChannelWrapper stores the address as hostString and the port as port inside of the inet
 *  for the respective channels. All channels inside of the wrapper connect to the same server.
 * The reason this object exists is because of a bug in grpc
 * https://github.com/grpc/grpc-java/issues/9089 that can cause long and short services to run at
 * the same time.
 *
 * Channel
 * 1) shortRPCManagedChannel: Used for short rpc values, most things run on this channel.
 * 2) longBackgroundRPCManagedChannel: Used for long background downloads that the user does not need immediately.
 * 3) findMatchesLoginSupportRPCManagedChannel: Used for find matches as well as the services that can run with login (request icons and
 * request pictures). This is because find matches should be almost exclusive with these unless
 * it is a login while the program is running and then the login support services will not need
 * to run almost ever.
 * 4) chatStreamRPCManagedChannel: Used exclusively for the chat stream.
 * 5) sendChatMessageRPCManagedChannel: Used for sendMessages service.
 * Some of these don't quite follow their paradigm. Join chat room and set picture for example
 * will attempt to find an empty channel to run on.
 **/
