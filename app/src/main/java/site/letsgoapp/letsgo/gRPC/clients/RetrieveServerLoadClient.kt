package site.letsgoapp.letsgo.gRPC.clients

import android.util.Log
import io.grpc.ManagedChannel
import retrieve_server_load.RetrieveServerLoad
import retrieve_server_load.RetrieveServerLoadServiceGrpcKt
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.GrpcClientResponse
import java.util.concurrent.TimeUnit

class RetrieveServerLoadClient {

    suspend fun retrieveServerLoad(
        channel: ManagedChannel,
        requestNumClients: Boolean,
        testingStatus: ClientExceptionTestingEnum = ClientExceptionTestingEnum.NOT_TESTING
    ): GrpcClientResponse<RetrieveServerLoad.RetrieveServerLoadResponse> {

        return grpcFunctionCallTemplate(
            {
                Log.i("androidSideErrors", "getAndroidSideErrorForResponse")

                RetrieveServerLoad.RetrieveServerLoadResponse.newBuilder()
                    .setAcceptingConnections(false)
                    .build()
            },
            bakeExceptionThrowingIntoLambda(
                testingStatus,
                {
                    RetrieveServerLoad.RetrieveServerLoadResponse.newBuilder()
                        .setAcceptingConnections(true)
                        .setNumClients(1337)
                        .build()
                },
                {
                    val request = RetrieveServerLoad.RetrieveServerLoadRequest.newBuilder()
                        .setRequestNumClients(requestNumClients)
                        .build()

                    RetrieveServerLoadServiceGrpcKt.RetrieveServerLoadServiceCoroutineStub(
                        channel
                    ).withDeadlineAfter(
                        GlobalValues.gRPC_Load_Balancer_Deadline_Time,
                        TimeUnit.MILLISECONDS
                    ).retrieveServerLoadRPC(request)
                }
            ),
            calledFromLoadBalancer = true
        )
    }
}