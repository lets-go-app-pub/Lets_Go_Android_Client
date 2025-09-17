package site.letsgoapp.letsgo.gRPC.clients

import android.util.Log
import grpc_stream_chat.ChatMessageStream.ChatToServerRequest
import grpc_stream_chat.StreamChatServiceGrpc
import io.grpc.*
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import site.letsgoapp.letsgo.globalAccess.GlobalValues.provideChatStreamRPCManagedChannel
import site.letsgoapp.letsgo.globalAccess.GlobalValues.server_imported_values
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import java.util.concurrent.TimeUnit

//used to return trailing meta data to the client
class TrailingMetadataClientInterceptor(val saveOnCloseResultsToStreamObserver: (status: Status?, trailers: Metadata?) -> Unit) :
    ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>?,
        callOptions: CallOptions?, next: Channel
    ): ClientCall<ReqT, RespT> {

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(
                method,
                callOptions
            )
        ) {
            override fun start(responseListener: Listener<RespT>?, headers: Metadata) {
                super.start(object :
                    ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener
                    ) {

                    override fun onClose(status: Status?, trailers: Metadata?) {
                        //the status and trailers are wanted inside of StreamObserver in onCompleted and onError
                        // and so this lambda will save them to it
                        //onClose should always be called before onCompleted or onError inside the StreamObserver
                        saveOnCloseResultsToStreamObserver(status, trailers)
                        super.onClose(status, trailers)
                    }

                }, headers)
            }
        }
    }
}

object ChatMessageStreamClient {

    suspend fun startChatStream(
        metaDataHeaderParams: Metadata,
        responseObserver: ChatStreamObject.ChatMessageStreamObserver,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<StreamObserver<ChatToServerRequest>?> {

        //NOTE: This will only return the value for initially starting the stream, other errors are expected to be
        // caught inside onError in the responseObserver
        return grpcFunctionCallTemplate(
            {
                null
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    null
                },
                {
                    val interceptor = TrailingMetadataClientInterceptor { status, trailers ->
                        responseObserver.saveStatusAndMetaData(status, trailers)
                    }

                    val channelWithInterceptor =
                        ClientInterceptors.intercept(
                            provideChatStreamRPCManagedChannel(),
                            interceptor
                        )

                    //NOTE: this uses the async client implementation instead of the blocking, this means that this function finishes immediately and the
                    // StreamObserver object is used to 'watch' for the stream results
                    val asyncClient =
                        StreamChatServiceGrpc.newStub(channelWithInterceptor).withInterceptors(
                            MetadataUtils.newAttachHeadersInterceptor(metaDataHeaderParams)
                        )

                    //NOTE: Do not wrap this function with an atomic boolean. The client will reconnect and interrupt the old
                    // chat stream to prevent any loss of data. However, this means that multiple instances of this function
                    // need to be allowed to run at a time.
                    Log.i(
                        "startBiDiTest",
                        "starting chat stream; startChatStream() running chatRoomStreamDeadlineTimeInMilliseconds: ${server_imported_values.chatRoomStreamDeadlineTime}"
                    )

                    //withWaitForReady() is used here so the chat stream doesn't need to continually retry
                    // if it is down.
                    if (server_imported_values.chatRoomStreamDeadlineTime != -1L) {
                        asyncClient.withWaitForReady()
                            .withDeadlineAfter(
                                server_imported_values.chatRoomStreamDeadlineTime,
                                TimeUnit.MILLISECONDS
                            )
                            .streamChatRPC(responseObserver)
                    } else {
                        asyncClient.withWaitForReady()
                            .streamChatRPC(responseObserver)
                    }
                }
            )
        )
    }
}