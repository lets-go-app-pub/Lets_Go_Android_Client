package site.letsgoapp.letsgo.gRPC.clients

import android.content.Context
import chat_message_to_client.ChatMessageToClientMessage
import grpc_chat_commands.ChatRoomCommands.*
import grpc_chat_commands.ChatRoomMessagesServiceGrpcKt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import report_enums.ReportMessages.UserMatchOptionsResponse
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithLongBackgroundRPCManagedChannel
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithSendChatMessageRPCManagedChannel
import site.letsgoapp.letsgo.globalAccess.GlobalValues.runWithShortRPCManagedChannel
import site.letsgoapp.letsgo.utilities.GrpcAndroidSideErrorsEnum
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import site.letsgoapp.letsgo.utilities.GrpcFunctionErrorStatusEnum
import site.letsgoapp.letsgo.utilities.JoinChatRoomPrimerValues
import status_enum.StatusEnum
import type_of_chat_message.TypeOfChatMessageOuterClass.MessageSpecifics
import type_of_chat_message.TypeOfChatMessageOuterClass.TypeOfChatMessage
import update_other_user_messages.UpdateOtherUserMessages
import java.util.concurrent.TimeUnit

class ChatRoomCommandsClient {

    suspend fun sendChatMessageToServer(
        request: ClientMessageToServerRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<ClientMessageToServerResponse> {

        return grpcFunctionCallTemplate(
            {
                ClientMessageToServerResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    ClientMessageToServerResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestampStored(1337)
                        .setPictureOid("")
                        .build()
                },
                {
                    runWithSendChatMessageRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Send_Message_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).clientMessageToServerRPC(request)
                    }
                }
            )
        )
    }

    suspend fun createChatRoom(
        request: CreateChatRoomRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<CreateChatRoomResponse> {

        return grpcFunctionCallTemplate(
            {
                CreateChatRoomResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    val time = System.currentTimeMillis()

                    val hello = ArrayList<String>()
                    hello.add("filler")
                    CreateChatRoomResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setChatRoomId("ChatRoom")
                        .setChatRoomPassword("abc")
                        .setLastActivityTimeTimestamp(time + 1)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).createChatRoomRPC(request)
                    }
                }
            )
        )
    }

    suspend fun joinChatRoom(
        applicationContext: Context,
        request: JoinChatRoomRequest,
        checkPrimer: (
            response: GrpcClientResponse<ChatMessageToClientMessage.ChatMessageToClient>,
            chatRoomStatus: ChatRoomStatus,
        ) -> JoinChatRoomPrimerValues,
        ioDispatcher: CoroutineDispatcher,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<JoinChatRoomPrimerValues> =
        withContext(ioDispatcher) {

            return@withContext grpcFunctionCallTemplate(
                { error ->

                    val errorResponse =
                        when (error) {
                            GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                                GrpcFunctionErrorStatusEnum.CONNECTION_ERROR
                            }
                            GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                                GrpcFunctionErrorStatusEnum.SERVER_DOWN
                            }
                            GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS, //NOTE: NO_ANDROID_ERRORS should never reach this point
                            GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION,
                            -> {
                                GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                            }
                        }

                    JoinChatRoomPrimerValues(
                        errorResponse,
                        ChatRoomStatus.UNRECOGNIZED
                    )
                },
                bakeExceptionThrowingIntoLambda(
                    testingStatus,
                    {
                        JoinChatRoomPrimerValues(
                            GrpcFunctionErrorStatusEnum.NO_ERRORS,
                            ChatRoomStatus.SUCCESSFULLY_JOINED
                        )
                    },
                    {
                        GlobalValues.findEmptyChannelOrShort { channel ->
                            var returnValue =
                                JoinChatRoomPrimerValues(
                                    GrpcFunctionErrorStatusEnum.DO_NOTHING,
                                    ChatRoomStatus.UNRECOGNIZED
                                )

                            //NOTE: The join chat room stream will not send back any picture or thumbnails. This
                            // is to (hopefully) save some time downloading. It also only
                            // returns skeletons of all the messages.
                            //NOTE: The join chat room stream will send back the DIFFERENT_USER_JOINED_MESSAGE_FIELD_NUMBER
                            // that is created with this message as well.
                            val responseIterator =
                                ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                                    channel
                                ).withDeadlineAfter(
                                    GlobalValues.gRPC_Join_Chat_Room_Deadline_Time,
                                    TimeUnit.MILLISECONDS
                                ).joinChatRoomRPC(request)

                            responseIterator.collect { nextIterator ->

                                for (message in nextIterator.messagesListList) {
                                    //NOTE: It is OK for more than 1 primer to be received here (errors can occur later in the stream).
                                    if (message.primer) {
                                        returnValue =
                                            checkPrimer(
                                                GrpcClientResponse(
                                                    message,
                                                    "",
                                                    GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                                                ),
                                                nextIterator.chatRoomStatus
                                            )
                                        if (returnValue.errorStatus != GrpcFunctionErrorStatusEnum.NO_ERRORS
                                            || returnValue.chatRoomStatus != ChatRoomStatus.SUCCESSFULLY_JOINED
                                        ) { //if join chat room failed
                                            break
                                        }
                                    }
                                    else { //if this is not the primer
                                        (applicationContext as LetsGoApplicationClass).chatStreamObject.receiveMessage(
                                            message,
                                            calledFromJoinChatRoom = true
                                        )
                                    }
                                }
                            }

                            returnValue
                        }
                    }
                )
            )
        }

    suspend fun leaveChatRoom(
        passedRequest: LeaveChatRoomRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<LeaveChatRoomResponse> {

        return grpcFunctionCallTemplate(
            {
                LeaveChatRoomResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    LeaveChatRoomResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestampStored(1337)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).leaveChatRoomRPC(passedRequest)
                    }
                }
            )
        )

    }

    suspend fun removeFromChatRoom(
        request: RemoveFromChatRoomRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<RemoveFromChatRoomResponse> {
        return grpcFunctionCallTemplate(
            {
                RemoveFromChatRoomResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    RemoveFromChatRoomResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        //.setVa(ValidRequestInfo.SUCCESS)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).removeFromChatRoomRPC(request)
                    }
                }
            )
        )
    }

    suspend fun unMatchFromChatRoom(
        request: UnMatchRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<UnMatchResponse> {

        return grpcFunctionCallTemplate(
            {
                UnMatchResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UnMatchResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).unMatchRPC(request)
                    }
                }
            )
        )
    }

    suspend fun blockAndReportChatRoom(
        request: BlockAndReportChatRoomRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<UserMatchOptionsResponse> {
        return grpcFunctionCallTemplate(
            {
                UserMatchOptionsResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UserMatchOptionsResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).blockAndReportChatRoomRPC(request)
                    }
                }
            )
        )
    }

    suspend fun unblockOtherUser(
        request: UnblockOtherUserRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<UnblockOtherUserResponse> {

        return grpcFunctionCallTemplate(
            {
                UnblockOtherUserResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UnblockOtherUserResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).unblockOtherUserRPC(request)
                    }
                }
            )
        )
    }

    /*suspend fun getSingleChatRoomImage(
        request: GetSingleChatImageRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<GetSingleChatImageResponse> {

        return grpcFunctionCallTemplate<GetSingleChatImageResponse>(
            {
                GetSingleChatImageResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    GetSingleChatImageResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .setTimestamp(1337L)
                        .build()
                },
                {
                    val client = ChatRoomMessagesServiceGrpc.newBlockingStub(provideManagedChannel())
                    client.withWaitForReady().withDeadlineAfter(
                        GlobalValues.gRPC_Unary_Call_Deadline_Time,
                        TimeUnit.MILLISECONDS
                    )
                        .getSingleChatImageRPC(request)
                }
            )
        )
    }*/

    suspend fun promoteNewAdmin(
        passedRequest: PromoteNewAdminRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<PromoteNewAdminResponse> {

        return grpcFunctionCallTemplate(
            {
                PromoteNewAdminResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    PromoteNewAdminResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).promoteNewAdminRPC(passedRequest)
                    }
                }
            )
        )
    }

    suspend fun updateChatRoomInfo(
        passedRequest: UpdateChatRoomInfoRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<UpdateChatRoomInfoResponse> {

        return grpcFunctionCallTemplate(
            {
                UpdateChatRoomInfoResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UpdateChatRoomInfoResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).updateChatRoomInfoRPC(passedRequest)
                    }
                }
            )
        )
    }

    suspend fun setPinnedLocation(
        passedRequest: SetPinnedLocationRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<SetPinnedLocationResponse> {

        return grpcFunctionCallTemplate(
            {
                SetPinnedLocationResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    SetPinnedLocationResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    runWithShortRPCManagedChannel { channel ->
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Short_Call_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).setPinnedLocationRPC(passedRequest)
                    }
                }
            )
        )
    }

    //TESTING_NOTE: a good test for this is to receive a member that joined and left in between this being called
    // so the response is a user NOT_IN_CHAT_ROOM or something similar to that
    suspend fun updateSingleChatRoomMember(
        passedRequest: UpdateSingleChatRoomMemberRequest,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ): GrpcClientResponse<UpdateOtherUserMessages.UpdateOtherUserResponse> {
        return grpcFunctionCallTemplate(
            {
                UpdateOtherUserMessages.UpdateOtherUserResponse.getDefaultInstance()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    UpdateOtherUserMessages.UpdateOtherUserResponse.newBuilder()
                        .setReturnStatus(StatusEnum.ReturnStatus.SUCCESS)
                        .build()
                },
                {
                    runWithLongBackgroundRPCManagedChannel { channel ->
                        //Using withWaitForReady() because if the user requested an update, it would be nice to finish
                        // and this is on a background channel so it shouldn't block anything.
                        ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                            channel
                        )
                            .withWaitForReady()
                            .withDeadlineAfter(
                                GlobalValues.gRPC_Long_Background_RPC_Deadline_Time,
                                TimeUnit.MILLISECONDS
                            ).updateSingleChatRoomMemberRPC(passedRequest)
                    }
                }
            )
        )
    }

    object UpdateAllChatRoomsRunSingleChatRoom {
        private val mutex = Mutex()
        private val runningChatRooms = mutableMapOf<String, Unit>()

        suspend fun tryLock(chatRoomId: String): Boolean {
            mutex.lock()

            return try {
                if (runningChatRooms.containsKey(chatRoomId)) {
                    false
                } else {
                    runningChatRooms[chatRoomId] = Unit
                    true
                }
            } finally {
                mutex.unlock()
            }
        }

        suspend fun unlock(chatRoomId: String) {
            mutex.lock()

            runningChatRooms.remove(chatRoomId)

            mutex.unlock()
        }
    }

    //NOTE: this is set up so that the returned list should never be null and the first element should always be set with primer = true
    suspend fun updateChatRoom(
        chatRoomId: String,
        request: UpdateChatRoomRequest,
        handleResponse: suspend (GrpcClientResponse<UpdateChatRoomResponse>) -> Boolean,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING,
    ) {

        //NOTE: not using flow here because I don't know that it can give a return value back from the lambda it calls

        //lock this function so that only 1 instance of the function can run PER chat room (locked around the chatRoomId)
        if (UpdateAllChatRoomsRunSingleChatRoom.tryLock(chatRoomId)) {

            val returnValue =
                grpcFunctionCallTemplate<UpdateChatRoomResponse>(
                    {
                        UpdateChatRoomResponse.getDefaultInstance()
                    },
                    bakeExceptionThrowingIntoLambda(
                        testingStatus,
                        {
                            UpdateChatRoomResponse.getDefaultInstance()
                        },
                        {
                            runWithLongBackgroundRPCManagedChannel { channel ->
                                //Using withWaitForReady() because if the user requested an update, it would be nice to finish
                                // and this is on a background channel so it shouldn't block anything.
                                ChatRoomMessagesServiceGrpcKt.ChatRoomMessagesServiceCoroutineStub(
                                    channel
                                )
                                    .withWaitForReady()
                                    .withDeadlineAfter(
                                        GlobalValues.gRPC_Long_Background_RPC_Deadline_Time,
                                        TimeUnit.MILLISECONDS
                                    ).updateChatRoomRPC(request).collect { response ->
                                        val continueLoop = handleResponse(
                                            GrpcClientResponse(
                                                response,
                                                "~",
                                                GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
                                            )
                                        )
                                        if (!continueLoop) return@collect
                                    }

                                UpdateChatRoomResponse.getDefaultInstance()
                            }
                        }
                    ),
                    {
                        UpdateAllChatRoomsRunSingleChatRoom.unlock(chatRoomId)
                    }
                )

            if (returnValue.androidErrorEnum != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) {

                //send back error message if relevant
                handleResponse(returnValue)
            }
        }
    }
}