package site.letsgoapp.letsgo.globalAccess

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.model.LatLng
import io.grpc.*
import io.grpc.Metadata.ASCII_STRING_MARSHALLER
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import login_values_to_return_to_client.LoginValuesToReturnToClientOuterClass
import site.letsgoapp.letsgo.LetsGoRuntimeException
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomsListFragment.ChatRoomListChatRoomsAdapter
import site.letsgoapp.letsgo.databases.iconsDatabase.icons.IconsDataEntity
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.gRPC.ClientsSourceIntermediate
import site.letsgoapp.letsgo.utilities.*
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object GlobalValues {

    /** General Purpose **/
    const val Lets_GO_Version_Number: Int = 3 //app version number
    const val POLLING_DELAY_BETWEEN_FIND_MATCHES_ATTEMPTS: Long =
        2L * 1000L //this is the the time between the match algorithm attempting to request matches

    const val MILLIS_IN_TWENTY_FOUR_HOURS: Long = 24L * 60L * 60L * 1000L
    const val TWENTY_TWENTY_ONE_START_TIMESTAMP =
        51L * 365L * 24L * 60L * 60L * 1000L //this should be 51 years after 1970 so the start of 2021 (in millis)

    const val MAP_VIEW_INITIAL_ZOOM = 14f //used for Google map view to set initial zoom

    const val INVALID_LOGIN_TOKEN = "INVALID_TOKEN"
    const val MAIN_ACTIVITY = "MAIN_ACTIVITY"

    const val GRADIENT_TINT_AMOUNT = 75

    //TLS certificate CN
    private const val OVERRIDDEN_AUTHORITY = "LetsGo"

    val deviceName: String = getDeviceInfoCustom()

    var installationId = ""
        private set

    //Set if the AppActivity should log in then navigate to a specific chat room (called from a notification).
    var loginToChatRoomId = ""

    /** Chat Room Values**/
    //this is never set to false, it is just used for workers to tell if the application was forcefully terminated
    var anActivityWasCreated = false

    //set to false when 0 activities running true if at least 1 is running
    @Volatile
    var anActivityCurrentlyRunning = false

    const val INVALID_FRAGMENT_INSTANCE_ID = "1-2:3"

    /** Gender Values **/ //NOTE: putting these here instead of strings because they are constant and application context will not be required
    const val MALE_GENDER_VALUE = "Male"
    const val FEMALE_GENDER_VALUE = "Female"
    const val EVERYONE_GENDER_VALUE = "Everyone"

    /** Messages Dao Database **/
    //NOTE: also update these inside below links if changing them
    /**
     * [ChatRoomListChatRoomsAdapter.onBindViewHolder]
     * [checkIfMessageTypeFitsFinalChatRoomMessage]
     * **/
    const val messagesDaoSelectFinalMessageString: String = """(""" +
            """1, """ + // TEXT_MESSAGE
            """2, """ + // PICTURE_MESSAGE
            """3, """ + // LOCATION_MESSAGE
            """4, """ + // MIME_TYPE_MESSAGE
            """5, """ + // INVITE_MESSAGE
            """8, """ + // USER_KICKED_MESSAGE
            """9, """ + // USER_BANNED_MESSAGE
            """10, """ + // DIFFERENT_USER_JOINED_MESSAGE
            """11, """ + // DIFFERENT_USER_LEFT_MESSAGE
            """18, """ + // CHAT_ROOM_NAME_UPDATED_MESSAGE
            """19, """ + // CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
            """20, """ + // NEW_ADMIN_PROMOTED_MESSAGE
            """22, """ + // NEW_PINNED_LOCATION_MESSAGE
            """500, """ + // MATCH_CANCELED_MESSAGE
            """1000)""" // HISTORY_CLEARED_MESSAGE

    //NOTE: also update these inside below link
    /** [checkIfChatRoomLastActiveTimeRequiresUpdating] **/
    const val messagesDaoSelectChatRoomLastActiveTimeString: String = """(""" +
            """1, """ + // TEXT_MESSAGE
            """2, """ + // PICTURE_MESSAGE
            """3, """ + // LOCATION_MESSAGE
            """4, """ + // MIME_TYPE_MESSAGE
            """5, """ + // INVITE_MESSAGE
            """8, """ + // USER_KICKED_MESSAGE
            """9, """ + // USER_BANNED_MESSAGE
            """10, """ + // DIFFERENT_USER_JOINED_MESSAGE
            """11, """ + // DIFFERENT_USER_LEFT_MESSAGE
            """18, """ + // CHAT_ROOM_NAME_UPDATED_MESSAGE
            """19, """ + // CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
            """20,""" + // NEW_ADMIN_PROMOTED_MESSAGE
            """21,""" + // CHAT_ROOM_CAP_MESSAGE
            """22)""" // NEW_PINNED_LOCATION_MESSAGE

    //NOTE: also update these inside below link
    /** [checkIfBlockedMessageShouldBeDisplayed] **/
    const val messagesDaoAllowedBlockedMessageTypesString: String = """(""" +
            """8, """ + // USER_KICKED_MESSAGE
            """9, """ + // USER_BANNED_MESSAGE
            """18, """ + // CHAT_ROOM_NAME_UPDATED_MESSAGE
            """19, """ + // CHAT_ROOM_PASSWORD_UPDATED_MESSAGE
            """20,""" + // NEW_ADMIN_PROMOTED_MESSAGE
            """22)""" // NEW_PINNED_LOCATION_MESSAGE

    class BlockedAccountsWrapper {
        private var blockedAccountsSet = mutableSetOf<String>()
        private var blockedAccountsList = mutableListOf<String>()
        private val readWriteLock = ReentrantReadWriteLock()

        fun initializeBlockedAccounts(passedBlockedAccounts: List<String>) {
            readWriteLock.write {
                blockedAccountsSet = passedBlockedAccounts.toMutableSet()
                blockedAccountsList = passedBlockedAccounts.toMutableList()
            }
        }

        operator fun get(key: String?): Boolean {
            return readWriteLock.read {
                blockedAccountsSet.contains(key)
            }
        }

        fun add(key: String) {
            readWriteLock.write {
                val blockedAccount = blockedAccountsSet.contains(key)

                if (!blockedAccount) {
                    blockedAccountsSet.add(key)
                    blockedAccountsList.add(key)
                }
            }
        }

        fun getMutableList(): MutableList<String> {
            return readWriteLock.read {
                //return a copy not the reference because it will not be protected by the lock
                blockedAccountsList.toMutableList()
            }
        }

        fun getMutableSet(): MutableSet<String> {
            return readWriteLock.read {
                //return a copy not the reference because it will not be protected by the lock
                blockedAccountsSet.toMutableSet()
            }
        }

        fun remove(key: String) {
            return readWriteLock.write {
                blockedAccountsSet.remove(key)
                blockedAccountsList.remove(key)
            }
        }

        fun clear() {
            return readWriteLock.write {
                blockedAccountsSet.clear()
                blockedAccountsList.clear()
            }
        }
    }

    /** Imported from server, these will be set on login they just have temp values set now **/
    //this is the current accounts OID, it is stored here because it never changes and is used quite often with chat messaging
    //the currentAccountOID is also used as a marker that an account is currently logged in, it will be reset on logout or delete account
    @Volatile
    var blockedAccounts = BlockedAccountsWrapper()

    //NOTE: if timeBetweenChatRoomStreamReconnection gets too close to chatRoomStreamDeadlineTimeInSeconds because of the delay before the reconnection is set
    // they can run out of order and the deadline will be exceeded every time

    const val NUMBER_BIGGER_THAN_UNIX_TIMESTAMP: Long =
        100000000000000L //a number larger than any practical unix timestamp in millis

    //NOTE: This value cannot be imported from the server.
    //NOTE: If updating this value, also change it inside fragment_verify_phone_numbers.xml under R.id.verifyNumEditText.
    const val verificationCodeNumberOfDigits =
        6 //the number of digits in the login SMS verification code

    const val maximumNumberMatchesStoredInViewModel =
        3 //this is the maximum number of matches allowed to be stored inside the application view model

    @Volatile
    var server_imported_values = createDefaultGlobals()

    //variables for calculating a correct timestamp
    //NOTE: Make sure that these timestamp variables are never 'unset' after login sets them (for example logout),
    // otherwise CleanDatabaseWorker could have problems getting its timestamp, also make this a comment.
    //NOTE: the timestamp could be slightly behind the server but it shouldn't hurt, this timestamp is used
    // to show categories times and to check match expiration times
    @Volatile
    var serverTimestampStartTimeMilliseconds =
        -1L //server timestamp requested at login

    @Volatile
    var clientElapsedRealTimeStartTimeMilliseconds =
        -1L //number of milliseconds device has been active, requested at time serverTimestampStartTimeSeconds is stored

    //NOTE: This list can never have elements removed from it. This will allow it to not have a mutex.
    // Elements should only be added BEFORE server_imported_values.numberPicturesStoredPerAccount is
    // updated.
    class SetPicturesBools {
        private val settingPicturesList = mutableListOf<AtomicBoolean>()

        fun size(): Int {
            return settingPicturesList.size
        }

        operator fun get(idx: Int): AtomicBoolean {
            return settingPicturesList[idx]
        }

        fun add() {
            settingPicturesList.add(AtomicBoolean(false))
        }
    }

    //Used for setting user pictures, each boolean represents an index for the picture. The object is
    // set up to never get shorter, and only add elements in a thread safe way.
    val setPicturesBools = SetPicturesBools()

    //chat stream meta data keys
    //If these are brought from the server, then if a user has NOT logged in recently they will
    // crash with invalid parameters. So not importing these.
    val chatStreamLoginMetadataCurrentAccountIdKey: Metadata.Key<String> =
        Metadata.Key.of("current_account_id", ASCII_STRING_MARSHALLER)
    val chatStreamLoginMetadataLoggedInTokenKey: Metadata.Key<String> =
        Metadata.Key.of("logged_in_token", ASCII_STRING_MARSHALLER)
    val chatStreamLoginMetadataLetsGoVersionKey: Metadata.Key<String> =
        Metadata.Key.of("lets_go_version", ASCII_STRING_MARSHALLER)
    val chatStreamLoginMetadataInstallationIdKey: Metadata.Key<String> =
        Metadata.Key.of("installation_id", ASCII_STRING_MARSHALLER)
    val chatStreamLoginMetadataChatRoomValuesKey: Metadata.Key<String> =
        Metadata.Key.of("chat_room_values", ASCII_STRING_MARSHALLER)
    const val chatStreamLoginMetaDataChatRoomValuesDelimiter = "::"

    val REASON_STREAM_SHUT_DOWN_KEY: Metadata.Key<String> =
        Metadata.Key.of("stream_down_reason", ASCII_STRING_MARSHALLER)
    val RETURN_STATUS_KEY: Metadata.Key<String> =
        Metadata.Key.of("return_status", ASCII_STRING_MARSHALLER)
    val OPTIONAL_INFO_OF_CANCELLING_STREAM: Metadata.Key<String> =
        Metadata.Key.of("optional_info_cancel", ASCII_STRING_MARSHALLER)

    lateinit var applicationContext: Context //NOTE: this is initialized on LetsGoApplicationClass initialization

    /** The maximum time that each gRPC function can take is Deadline_time + 3*gRPC_Load_Balancer_Deadline_Time **/

    //NOTE: The messages that sent bitmaps can take some time to send (seems to have started happening in API 31). So
    // this is longer than it 'could' be.

    //in milliseconds, The time before a gRPC request times out that uses the 'short' channel. This
    // is set a bit longer because joinChatRoom is on the channel and it may take a few seconds
    // if the chat room is large.
    const val gRPC_Short_Call_Deadline_Time: Long = 10L * 1000L

    //in milliseconds, The time before a gRPC request on the 'long background' channel times out. It is a long time because this
    // channel is specifically set up to handle 'large' messages.
    const val gRPC_Long_Background_RPC_Deadline_Time: Long = 90L * 1000L

    //in milliseconds, The time before a gRPC request on the 'send chat message' channel times out. It is a long time because this
    // channel is specifically set up to handle 'large' messages.
    const val gRPC_Send_Message_Deadline_Time: Long = 90L * 1000L

    //In milliseconds, the time is long because it shares the find matches channel and so may need to wait
    // for (find matches)/(request icons) to complete.
    const val gRPC_Request_User_Pictures_Deadline_Time: Long = 30L * 1000L

    //In milliseconds, the time is long because it shares the find matches channel and so may need to wait
    // for find matches to complete.
    const val gRPC_Request_Icons_Deadline_Time: Long = 30L * 1000L

    //in milliseconds, this technically shares a channel with requestIcons and requestPictures (see notes inside
    // requestServerIcons()) and so the deadline time needs to be long in case it gets stuck behind them during
    // a login (it really shouldn't happen except in fringe cases).
    const val gRPC_Find_Matches_Deadline_Time: Long = 40L * 1000L

    //in milliseconds, this deadline is for join chat room which can be run on a variety of channels. If it runs on
    // the short channel there is a possibility of other RPCs deadlines being reached.
    const val gRPC_Join_Chat_Room_Deadline_Time: Long = 30L * 1000L

    //in milliseconds, the time before a gRPC load balancer request times out (essentially the max tolerable latency for the connection)
    //NOTE: This is the time for the load balancer to run and so the user will have to wait (at least) this amount of time on
    // the load screen if they are logging in and any one of the servers is UNAVAILABLE.
    const val gRPC_Load_Balancer_Deadline_Time: Long = 3L * 1000L

    /** This should be longer than any of the deadline times **/
    const val lockRPCTimeoutTime: Long = 120L * 1000L

    //used with load balancing to tell how many times to ping each server
    private const val NUMBER_TIMES_TO_PING_FOR_LATENCY = 3

    /** Email Address Validation **/
    const val EMAIL_REGEX_STRING = "^[^@\\s]+@[^@\\s\\.]+\\.[^@\\.\\s]+\$"

    /** Network Errors **/
    const val NETWORK_UNKNOWN = "UNKNOWN"

    //NOTE: this can happen when (there may be other cases)
    // 1) the client internet connection is down
    // 2) the client connects to a server w/o the necessary grpc function receiving (this is important if I will be taking the server down)
    const val NETWORK_UNAVAILABLE = "UNAVAILABLE"

    //NOTE: this can happen when (there may be other cases)
    // 1) the RPC itself takes too long
    // 2) the client is attempting to connect to a server and the servers internet connection is down
    // 3) the client is attempting to connect to a server with the wrong port
    const val NETWORK_DEADLINE_EXCEEDED = "DEADLINE_EXCEEDED"
    //const val GRPC_CANCELLED = "CANCELLED"

    /** PICTURE_NOT_FOUND values **/
    const val PICTURE_NOT_FOUND_ON_SERVER: String =
        "PIC_NOT_FOUND" //default value for when a picture returns PICTURE_NOT_FOUND from server OR if picture sent back is corrupt

    /** Location **/
    //The time between the device requesting the user's location to send it to the server.
    const val timeBetweenUpdatesInMs = 2L * 60L * 1000L

    //NOTE: withTimeout() will finish immediately when running tests and a context that is
    // different from the current context is called. This is a problem specifically because
    // when a CoroutineReentrantLock runs at the same time, it will setup a new context.
    // Can see here for more info
    // https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md#using-withtimeout-inside-runtest
    var setupForTesting = false
        @VisibleForTesting set

    val defaultLocation = LatLng(
        25.0,
        -90.0
    ) //default location (somewhere in the gulf of mexico) used with selectLocationCurrentLocation in side SharedApplicationViewModel.kt

    var lastUpdatedLocationInfo = LastUpdatedLocationInfo(
        defaultLocation.longitude,
        defaultLocation.latitude,
        "",
        -1L,
        false
    )

    //This is volatile so that if it is written inside LoginFunctions.kt then it will be immediately visible to any
    // fragments using it.
    @Volatile
    var allIcons = listOf<IconsDataEntity>()
    const val defaultIconImageID: Int =
        R.drawable.icon_question_mark
    const val defaultPictureResourceID: Int =
        R.drawable.icon_round_person_large_24

    /** gRPC Values **/
    private val managedChannelMutex = Mutex()

    private data class TempManagedChannelWrapper(
        val managedChannel: ManagedChannel,
        //NOTE: Do not use hostName for an InetSocketAddress object if just running a comparison.
        // It can trigger a network call and.
        // Instead use hostString.
        val inetAddress: InetSocketAddress
    )

    /** Wraps all channels as well as the host name and the port.
     * For more info see [channel_selection_notes].
     **/
    private data class ManagedChannelWrapper(
        val shortRPCManagedChannel: ManagedChannel,
        val longBackgroundRPCManagedChannel: ManagedChannel,
        val findMatchesLoginSupportRPCManagedChannel: ManagedChannel,
        val chatStreamRPCManagedChannel: ManagedChannel,
        val sendChatMessageRPCManagedChannel: ManagedChannel,
        val inetAddress: InetSocketAddress
    )

    //NOTE: When the app is closed from the task manager the ManagedChannels will be closed as
    // well. The companion object of a worker is not an exception, so this will need to be
    // recreated no matter what I do between workers.
    @Volatile
    private var managedChannelWrapper: ManagedChannelWrapper? = null
        private set(value) {
            synchronized(this) {
                val temp = field
                field = value
                Log.i("loadBalancingVal", "shutting down ALL Channels managedChannelWrapper setter")
                temp?.let {
                    it.shortRPCManagedChannel.shutdown()
                    it.longBackgroundRPCManagedChannel.shutdown()
                    it.findMatchesLoginSupportRPCManagedChannel.shutdown()
                    it.chatStreamRPCManagedChannel.shutdown()
                    it.sendChatMessageRPCManagedChannel.shutdown()
                }
            }
        }

    fun getCurrentManagedChannelAddressPort(): String {
        if (managedChannelWrapper == null) {
            return ""
        }
        return "${managedChannelWrapper?.inetAddress?.hostString}:${managedChannelWrapper?.inetAddress?.port}"
    }

    //These values are meant to be approximates. There is a chance that by the time they are returned
    // they could be different.
    private val numRPCOnShortChannel = AtomicInteger(0)
    private val numRPCOnLongBackgroundChannel = AtomicInteger(0)
    private val numRPCOnFindMatchesChannel = AtomicInteger(0)
    private val numRPCOnSendMessagesChannel = AtomicInteger(0)

    //Attempts to run on an empty channel, if none are available will run on short. NOTE that this
    // is not as ideal as it looks. If the call is short then it is better to just use the SHORT
    // channel. Some of these channels may not even be maintaining a connection and so one will
    // have to be made (behind the scenes by the channel, but it is overhead).
    suspend fun <T> findEmptyChannelOrShort(runJoinChatRoom: suspend (ManagedChannel) -> T): T {
        return when {
            numRPCOnFindMatchesChannel.get() == 0 -> {
                runWithFindMatchesRPCManagedChannel(runJoinChatRoom)
            }
            numRPCOnSendMessagesChannel.get() == 0 -> {
                runWithSendChatMessageRPCManagedChannel(runJoinChatRoom)
            }
            numRPCOnLongBackgroundChannel.get() == 0 -> {
                runWithLongBackgroundRPCManagedChannel(runJoinChatRoom)
            }
            else -> {
                runWithShortRPCManagedChannel(runJoinChatRoom)
            }
        }
    }

    //Attempts to run on an empty channel, if none are available will run on long. NOTE that this
    // is not as ideal as it looks. If the call is short then it is better to just use the SHORT
    // channel. Some of these channels may not even be maintaining a connection and so one will
    // have to be made (behind the scenes by the channel, but it is overhead).
    suspend fun <T> findEmptyChannelOrLong(runJoinChatRoom: suspend (ManagedChannel) -> T): T {
        return when {
            numRPCOnFindMatchesChannel.get() == 0 -> {
                runWithFindMatchesRPCManagedChannel(runJoinChatRoom)
            }
            numRPCOnSendMessagesChannel.get() == 0 -> {
                runWithSendChatMessageRPCManagedChannel(runJoinChatRoom)
            }
            else -> {
                runWithLongBackgroundRPCManagedChannel(runJoinChatRoom)
            }
        }
    }

    private fun provideShortRPCManagedChannel(): ManagedChannel {
        val tempManagedChannelWrapper = managedChannelWrapper
        if (tempManagedChannelWrapper != null) {
            return tempManagedChannelWrapper.shortRPCManagedChannel
        } else {
            synchronized(this) {
                val nextTempManagedChannelWrapper = managedChannelWrapper
                //in case threads are 'backed up' waiting at sync lock
                return if (nextTempManagedChannelWrapper != null) {
                    nextTempManagedChannelWrapper.shortRPCManagedChannel
                } else {
                    //NOTE: this should not be called before login and so the managedChannel should be
                    // set up already, however if it is for some reason, simply add the index [0] element
                    // to here if the load balancer is called afterwards then it will re-use this channel
                    // when selecting the server to connect to
                    val createdChannelWrapper = createFullManagedChannelWrapper(
                        server_imported_values.serverInfoList[0].address,
                        server_imported_values.serverInfoList[0].port,
                    )
                    managedChannelWrapper = createdChannelWrapper
                    createdChannelWrapper.shortRPCManagedChannel
                }
            }
        }
    }

    suspend fun <T> runWithShortRPCManagedChannel(block: suspend (ManagedChannel) -> T): T {
        numRPCOnShortChannel.incrementAndGet()
        val returnVal = block(provideShortRPCManagedChannel())
        numRPCOnShortChannel.decrementAndGet()
        return returnVal
    }

    private fun provideLongBackgroundRPCManagedChannel(): ManagedChannel {
        val tempManagedChannelWrapper = managedChannelWrapper
        if (tempManagedChannelWrapper != null) {
            return tempManagedChannelWrapper.longBackgroundRPCManagedChannel
        } else {
            synchronized(this) {
                val nextTempManagedChannelWrapper = managedChannelWrapper
                //in case threads are 'backed up' waiting at sync lock
                return if (nextTempManagedChannelWrapper != null) {
                    nextTempManagedChannelWrapper.longBackgroundRPCManagedChannel
                } else {
                    //NOTE: this should not be called before login and so the managedChannel should be
                    // set up already, however if it is for some reason, simply add the index [0] element
                    // to here if the load balancer is called afterwards then it will re-use this channel
                    // when selecting the server to connect to
                    val createdChannelWrapper = createFullManagedChannelWrapper(
                        server_imported_values.serverInfoList[0].address,
                        server_imported_values.serverInfoList[0].port,
                    )
                    managedChannelWrapper = createdChannelWrapper
                    createdChannelWrapper.longBackgroundRPCManagedChannel
                }
            }
        }
    }

    suspend fun <T> runWithLongBackgroundRPCManagedChannel(block: suspend (ManagedChannel) -> T): T {
        numRPCOnLongBackgroundChannel.incrementAndGet()
        val returnVal = block(provideLongBackgroundRPCManagedChannel())
        numRPCOnLongBackgroundChannel.decrementAndGet()
        return returnVal
    }

    private fun provideFindMatchesLoginSupportRPCManagedChannel(): ManagedChannel {
        val tempManagedChannelWrapper = managedChannelWrapper
        if (tempManagedChannelWrapper != null) {
            return tempManagedChannelWrapper.findMatchesLoginSupportRPCManagedChannel
        } else {
            synchronized(this) {
                val nextTempManagedChannelWrapper = managedChannelWrapper
                //in case threads are 'backed up' waiting at sync lock
                return if (nextTempManagedChannelWrapper != null) {
                    nextTempManagedChannelWrapper.findMatchesLoginSupportRPCManagedChannel
                } else {
                    //NOTE: this should not be called before login and so the managedChannel should be
                    // set up already, however if it is for some reason, simply add the index [0] element
                    // to here if the load balancer is called afterwards then it will re-use this channel
                    // when selecting the server to connect to
                    val createdChannelWrapper = createFullManagedChannelWrapper(
                        server_imported_values.serverInfoList[0].address,
                        server_imported_values.serverInfoList[0].port,
                    )
                    managedChannelWrapper = createdChannelWrapper
                    createdChannelWrapper.findMatchesLoginSupportRPCManagedChannel
                }
            }
        }
    }

    suspend fun <T> runWithFindMatchesRPCManagedChannel(block: suspend (ManagedChannel) -> T): T {
        numRPCOnFindMatchesChannel.incrementAndGet()
        val returnVal = block(provideFindMatchesLoginSupportRPCManagedChannel())
        numRPCOnFindMatchesChannel.decrementAndGet()
        return returnVal
    }

    //NOTE: Chat stream channel uses an async stub. So the other format of keeping track of RPC with it
    // will not work. When the lambda ends the stream will still be running.
    fun provideChatStreamRPCManagedChannel(): ManagedChannel {
        val tempManagedChannelWrapper = managedChannelWrapper
        if (tempManagedChannelWrapper != null) {
            return tempManagedChannelWrapper.chatStreamRPCManagedChannel
        } else {
            synchronized(this) {
                val nextTempManagedChannelWrapper = managedChannelWrapper
                //in case threads are 'backed up' waiting at sync lock
                return if (nextTempManagedChannelWrapper != null) {
                    nextTempManagedChannelWrapper.chatStreamRPCManagedChannel
                } else {
                    //NOTE: this should not be called before login and so the managedChannel should be
                    // set up already, however if it is for some reason, simply add the index [0] element
                    // to here if the load balancer is called afterwards then it will re-use this channel
                    // when selecting the server to connect to
                    val createdChannelWrapper = createFullManagedChannelWrapper(
                        server_imported_values.serverInfoList[0].address,
                        server_imported_values.serverInfoList[0].port,
                    )
                    managedChannelWrapper = createdChannelWrapper
                    createdChannelWrapper.chatStreamRPCManagedChannel
                }
            }
        }
    }

    private fun provideSendChatMessageRPCManagedChannel(): ManagedChannel {
        val tempManagedChannelWrapper = managedChannelWrapper
        if (tempManagedChannelWrapper != null) {
            return tempManagedChannelWrapper.sendChatMessageRPCManagedChannel
        } else {
            synchronized(this) {
                val nextTempManagedChannelWrapper = managedChannelWrapper
                //in case threads are 'backed up' waiting at sync lock
                return if (nextTempManagedChannelWrapper != null) {
                    nextTempManagedChannelWrapper.sendChatMessageRPCManagedChannel
                } else {
                    //NOTE: this should not be called before login and so the managedChannel should be
                    // set up already, however if it is for some reason, simply add the index [0] element
                    // to here if the load balancer is called afterwards then it will re-use this channel
                    // when selecting the server to connect to
                    val createdChannelWrapper = createFullManagedChannelWrapper(
                        server_imported_values.serverInfoList[0].address,
                        server_imported_values.serverInfoList[0].port,
                    )
                    managedChannelWrapper = createdChannelWrapper
                    createdChannelWrapper.sendChatMessageRPCManagedChannel
                }
            }
        }
    }

    suspend fun <T> runWithSendChatMessageRPCManagedChannel(block: suspend (ManagedChannel) -> T): T {
        numRPCOnSendMessagesChannel.incrementAndGet()
        val returnVal = block(provideSendChatMessageRPCManagedChannel())
        numRPCOnSendMessagesChannel.decrementAndGet()
        return returnVal
    }

    //Runtime.getRuntime().availableProcessors() will get the number of cores on the device. However if a thread
    // is put to sleep while it waits for a network call, not sure that it can be 'used' anymore. So going to go
    // to 16, this should cover most if not all use cases for the app.
    private val managedChannelExecutor = Executors.newFixedThreadPool(16)

    private fun createSingleManagedChannel(
        address: String,
        port: Int,
        keepAliveTimeInMs: Long = -1
    ): ManagedChannel {

        val channelCredentials = TlsChannelCredentials.newBuilder()
            .trustManager(applicationContext.resources.openRawResource(R.raw.public_cert))
            //.trustManager(applicationContext.resources.openRawResource(R.raw.testing_public_cert))
            .build()

        val channel = Grpc.newChannelBuilderForAddress(address, port, channelCredentials)
            .overrideAuthority(OVERRIDDEN_AUTHORITY)
            .maxInboundMessageSize(server_imported_values.maxServerOutboundMessageSizeInBytes)
            .executor(managedChannelExecutor)
            .idleTimeout(
                server_imported_values.connectionIdleTimeoutInMs,
                TimeUnit.MILLISECONDS
            ) //This will allow the connection to timeout from inactivity (this will prevent inadvertently 'leaking' TCP ports, at 100 at exception will be thrown)

        //NOTE: AndroidChannelBuilder has a bug that occurs when the app attempts a lot of network connections while the
        // device is in doze. The bug will cause the exception 'IllegalArgumentException: Too many NetworkRequests filed'
        // when the device comes out of doze (gets its connection back). This seems to be related to the designer not using
        // ConnectivityManager.unregisterNetworkCallback() correctly.
//        val channel = AndroidChannelBuilder.usingBuilder(sslChannel)
//            .context(applicationContext)

        if (keepAliveTimeInMs > -1) {
            //This will have Grpc send pings to the server to keep the connection alive (and check
            // network state). The minimum time is 10 seconds, and this is more overhead, so use it
            // sparingly. It can be used to detect network failures during long streams. However none of the
            // streams on this are terribly long except for the bi-di stream. And it is essentially always
            // connected to the server, so this may be a lot of overhead on the server end even to keep the
            // streams alive. Leaving it as more of a test
            channel.keepAliveTime(keepAliveTimeInMs, TimeUnit.MILLISECONDS)
        }

        return channel.build()
    }

    //Throws IllegalArgumentException if an invalid address and/or port is used.
    private fun createTempManagedChannelWrapper(
        address: String,
        port: Int,
    ): TempManagedChannelWrapper {
        return TempManagedChannelWrapper(
            createSingleManagedChannel(address, port),
            InetSocketAddress(address, port)
        )
    }

    //Throws IllegalArgumentException if an invalid address and/or port is used.
    //NOTE: This will create a total of 4 managed channels, it should only be used AFTER any load
    // balancing has taken place and a connection has been established to be complete.
    private fun createFullManagedChannelWrapper(
        address: String,
        port: Int,
        tempManagedChannelWrapper: TempManagedChannelWrapper? = null
    ): ManagedChannelWrapper {
        return ManagedChannelWrapper(
            tempManagedChannelWrapper?.managedChannel ?: createSingleManagedChannel(
                address,
                port
            ),
            createSingleManagedChannel(address, port),
            createSingleManagedChannel(address, port),
            createSingleManagedChannel(address, port, 5L * 60L * 1000L),
            createSingleManagedChannel(address, port),
            tempManagedChannelWrapper?.inetAddress ?: InetSocketAddress(address, port)
        )
    }

    @Volatile
    private var mostRecentLoadBalancingReturnValue = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

    private const val timeBetweenLoadBalancingRuns: Long =
        5L * 60L * 1000L //time between load balancing runs in millis

    @Volatile
    private var nextTimeLoadBalancingCanRun: Long =
        0 //the next time load balancing can be run based on SystemClock.elapsedRealTime

    private data class ManagedChannelWithLoadStatistics(
        val channelWrapper: TempManagedChannelWrapper,
        var androidSideErrors: GrpcAndroidSideErrorsEnum = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS,
        var numClients: Int = -1,
        var sumOfLatencyValues: Long = 0,
        var getServerLoadJob: Job? = null,
    )

    suspend fun runLoadBalancing(
        clientsIntermediate: ClientsInterface,
        biPassRestrictions: Boolean = false,
        errorStore: StoreErrorsInterface = setupStoreErrorsInterface()
    ): GrpcAndroidSideErrorsEnum {

        if (clientsIntermediate is ClientsSourceIntermediate) {
            Log.i(
                "server_not_fake",
                printStackTraceForErrors()
            )
        }

        var locked = false
        val returnValue: GrpcAndroidSideErrorsEnum =
            try {

                locked = managedChannelMutex.tryLock(currentCoroutineContext())

                val loadBalancingCanRun =
                    when {
                        biPassRestrictions && !locked -> {
                            managedChannelMutex.lock(currentCoroutineContext())
                            locked = true
                            true
                        }
                        biPassRestrictions -> {
                            true
                        }
                        else -> {
                            SystemClock.elapsedRealtime() > nextTimeLoadBalancingCanRun
                        }
                    }

                if (locked && loadBalancingCanRun) {

                    if (!biPassRestrictions) {

                        //only set this if the time restriction is relevant, otherwise for timeBetweenLoadBalancingRuns
                        // the server will not be able to load balance
                        nextTimeLoadBalancingCanRun =
                            SystemClock.elapsedRealtime() + timeBetweenLoadBalancingRuns
                    }

                    runLoadBalancingImplementation(clientsIntermediate, errorStore)
                } else {
                    mostRecentLoadBalancingReturnValue
                }
            } catch (e: CancellationException) {
                mostRecentLoadBalancingReturnValue
            } finally {
                //managedChannelMutex.holdsLock(currentCoroutineContext()) is needed in case of cancellation
                if (locked && managedChannelMutex.holdsLock(currentCoroutineContext())) {
                    managedChannelMutex.unlock()
                }
            }

        Log.i("loadBalancingVal", "returnValue: $returnValue")
        return returnValue
    }

    private suspend fun runLoadBalancingImplementation(
        clientsIntermediate: ClientsInterface,
        errorStore: StoreErrorsInterface
    ): GrpcAndroidSideErrorsEnum {

        val managedChannelsWithLoadStats = mutableListOf<ManagedChannelWithLoadStatistics>()
        val primaryManagedChannelWrapper = managedChannelWrapper?.let {
            TempManagedChannelWrapper(
                it.shortRPCManagedChannel,
                it.inetAddress
            )
        }

        Log.i(
            "loadBalancingVal",
            "number servers stored: ${server_imported_values.serverInfoList.size}"
        )

        //load balancing
        for (i in server_imported_values.serverInfoList.indices) {
            Log.i(
                "loadBalancingVal",
                "server address: ${server_imported_values.serverInfoList[i].address}"
            )
            if (primaryManagedChannelWrapper != null
                && !primaryManagedChannelWrapper.managedChannel.isShutdown
                && !primaryManagedChannelWrapper.managedChannel.isTerminated
                && server_imported_values.serverInfoList[i].port == primaryManagedChannelWrapper.inetAddress.port
                && server_imported_values.serverInfoList[i].address == primaryManagedChannelWrapper.inetAddress.hostString
            ) { //if this address is in use by the primary managed channel
                managedChannelsWithLoadStats.add(
                    ManagedChannelWithLoadStatistics(
                        primaryManagedChannelWrapper
                    )
                )
            } else { //if this address is not in use by the primary managed channel

                try {
                    //create new managed channel
                    managedChannelsWithLoadStats.add(
                        ManagedChannelWithLoadStatistics(
                            createTempManagedChannelWrapper(
                                server_imported_values.serverInfoList[i].address,
                                server_imported_values.serverInfoList[i].port,
                            )
                        )
                    )

                } catch (e: IllegalArgumentException) {
                    val errorMessage =
                        "IllegalArgumentException was returned when attempting to build a channel.\n" +
                                "exception: ${e.message}" +
                                "address: ${server_imported_values.serverInfoList[i].address}" +
                                "port: ${server_imported_values.serverInfoList[i].port}"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage,
                        applicationContext
                    )

                    Log.i(
                        "loadBalancingVal",
                        "IllegalArgumentException when creating channel, address: ${server_imported_values.serverInfoList[i].address} port: ${server_imported_values.serverInfoList[i].port}"
                    )
                }
            }
        }

        if (managedChannelsWithLoadStats.isEmpty()) {
            val errorMessage =
                "No valid servers could be build using the info passed.\n" +
                        "server_imported_values: $server_imported_values"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                applicationContext
            )

            //end the program
            throw LetsGoRuntimeException(errorMessage)
        }

        var currentManagedChannelSaved = false

        for (channelWithLoadStats in managedChannelsWithLoadStats) {
            if (primaryManagedChannelWrapper != null
                && channelWithLoadStats.channelWrapper.inetAddress.hostString == primaryManagedChannelWrapper.inetAddress.hostString
                && channelWithLoadStats.channelWrapper.inetAddress.port == primaryManagedChannelWrapper.inetAddress.port
            ) {
                currentManagedChannelSaved = true
            }
            channelWithLoadStats.getServerLoadJob =
                CoroutineScope(ServiceLocator.globalIODispatcher).launch {

                    val runAtEnd =
                        if (primaryManagedChannelWrapper != null
                            && channelWithLoadStats.channelWrapper.inetAddress.hostString == primaryManagedChannelWrapper.inetAddress.hostString
                            && channelWithLoadStats.channelWrapper.inetAddress.port == primaryManagedChannelWrapper.inetAddress.port
                        ) {
                            numRPCOnShortChannel.getAndIncrement();
                            { numRPCOnShortChannel.getAndDecrement() }
                        } else {
                            { }
                        }

                    for (i in 0 until NUMBER_TIMES_TO_PING_FOR_LATENCY) {
                        Log.i(
                            "androidSideErrors",
                            "channelWithLoadStats.androidSideErrors: ${channelWithLoadStats.androidSideErrors}"
                        )

                        if (channelWithLoadStats.androidSideErrors == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS) { //if the server is accepting connections

                            yield()

                            val start = SystemClock.uptimeMillis()

                            val response =
                                clientsIntermediate.retrieveServerLoadInfo(
                                    channelWithLoadStats.channelWrapper.managedChannel,
                                    channelWithLoadStats.numClients == -1
                                )

                            val stop = SystemClock.uptimeMillis()

                            channelWithLoadStats.androidSideErrors = response.androidErrorEnum

                            when {
                                //if an exception occurred
                                response.androidErrorEnum != GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {

                                    channelWithLoadStats.androidSideErrors =
                                        response.androidErrorEnum

                                    if (response.androidErrorEnum == GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION) {
                                        val errorMessage =
                                            "Unknown exception occurred when trying to load balance.\n" +
                                                    "server_imported_values: $server_imported_values" +
                                                    "managedChannelsWithLoadStats: $managedChannelsWithLoadStats" +
                                                    "response: $response"

                                        errorStore.storeError(
                                            Thread.currentThread().stackTrace[2].fileName,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors(),
                                            errorMessage,
                                            applicationContext
                                        )
                                    }
                                }
                                response.response.acceptingConnections -> { //if the server is accepting connections
                                    val pingTime = stop - start

                                    channelWithLoadStats.sumOfLatencyValues += pingTime

                                    if (response.response.numClients > -1) {
                                        channelWithLoadStats.numClients =
                                            response.response.numClients
                                    }
                                }
                                else -> { //if the server is not accepting connections
                                    channelWithLoadStats.androidSideErrors =
                                        GrpcAndroidSideErrorsEnum.SERVER_DOWN
                                }
                            }
                        }
                    }

                    runAtEnd()

                }
        }

        if (primaryManagedChannelWrapper != null && !currentManagedChannelSaved) {

            val errorMessage =
                "The primary managed channel was not saved to the array to be checked when load " +
                        "balancing. Potentially leaking channel if was not shut down\n" +
                        "managedChannelsWithLoadStats: $managedChannelsWithLoadStats\n"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                applicationContext
            )

            Log.i("loadBalancingVal", "shutting down ALL Channels")
            managedChannelWrapper?.shortRPCManagedChannel?.shutdown()
            managedChannelWrapper?.longBackgroundRPCManagedChannel?.shutdown()
            managedChannelWrapper?.findMatchesLoginSupportRPCManagedChannel?.shutdown()
            managedChannelWrapper?.chatStreamRPCManagedChannel?.shutdown()
            managedChannelWrapper?.sendChatMessageRPCManagedChannel?.shutdown()
        }

        //wait for load balancing to finish
        for (channelWithLoadStats in managedChannelsWithLoadStats) {
            channelWithLoadStats.getServerLoadJob?.join()
        }

        //only keep servers that are accepting connections
        val filteredChannels = managedChannelsWithLoadStats.filter {
            it.androidSideErrors == GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS
        }.toMutableList()

        var returnLoadBalancerValue = GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS

        if (filteredChannels.isEmpty()) { //no servers available

            Log.i("loadBalancingVal", "filteredChannels.isEmpty()")

            var numConnectionError = 0
            var numExceptionThrown = 0
            var numServerDown = 0

            for (channelWithLoadStats in managedChannelsWithLoadStats) {
                when (channelWithLoadStats.androidSideErrors) {
                    GrpcAndroidSideErrorsEnum.NO_ANDROID_ERRORS -> {
                        numExceptionThrown++

                        val errorMessage =
                            "During load balancing the filteredChannels returned empty when a value of NO_ANDROID_ERRORS was returned." +
                                    "managedChannelsWithLoadStats: $managedChannelsWithLoadStats\n" +
                                    "filteredChannels: $filteredChannels\n"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage,
                            applicationContext
                        )
                    }
                    GrpcAndroidSideErrorsEnum.CONNECTION_ERROR -> {
                        numConnectionError++
                    }
                    GrpcAndroidSideErrorsEnum.SERVER_DOWN -> {
                        numServerDown++
                    }
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION -> {
                        numExceptionThrown++
                    }
                }
                Log.i(
                    "loadBalancingVal",
                    "channelWithLoadStats.androidSideErrors: ${channelWithLoadStats.androidSideErrors}"
                )
            }

            Log.i(
                "loadBalancingVal",
                "numConnectionError: $numConnectionError numExceptionThrown: $numExceptionThrown numServerDown: $numServerDown"
            )
            returnLoadBalancerValue = when {
                numConnectionError > 0 -> { //internet was not detected at some point on the client
                    GrpcAndroidSideErrorsEnum.CONNECTION_ERROR
                }
                numExceptionThrown == managedChannelsWithLoadStats.size -> { //if every return value was an exception
                    GrpcAndroidSideErrorsEnum.UNKNOWN_EXCEPTION
                }
                else -> { //if any other case, use server down

                    //LoadBalancerInternalValue.CONNECTION_ERROR //client internet connection is down - SHOW CONNECTION ERROR
                    //LoadBalancerInternalValue.NOT_ACCEPTING_CONNECTIONS //server itself refused connections - SHOW SERVER DOWN
                    //LoadBalancerInternalValue.EXCEPTION_THROWN //unmanageable exception throw from a server - LG_ERROR
                    //LoadBalancerInternalValue.SERVER_DOWN //the server was taken down - SHOW SERVER DOWN
                    //LoadBalancerInternalValue.DEADLINE_EXCEEDED //ping too high || server internet down - SHOW SERVER DOWN (if ping is too high want a new server anyway)

                    GrpcAndroidSideErrorsEnum.SERVER_DOWN
                }
            }
        } else if (filteredChannels.size == 1) { //if only 1 server is available
            if (managedChannelWrapper == null
                || filteredChannels.first().channelWrapper.inetAddress.hostString != managedChannelWrapper?.inetAddress?.hostString
                || filteredChannels.first().channelWrapper.inetAddress.port != managedChannelWrapper?.inetAddress?.port
            ) { //new channel is different than previous channel
                managedChannelWrapper =
                    createFullManagedChannelWrapper(
                        filteredChannels.first().channelWrapper.inetAddress.hostString,
                        filteredChannels.first().channelWrapper.inetAddress.port,
                        filteredChannels.first().channelWrapper
                    )
                //This will force the chat stream to end itself. It will automatically retry after this.
                ServiceLocator.provideChatStreamObjectOrNull()
                    ?.sendNetworkUnavailableToChatStream()
            }
        } else { //at least one server is available

            //sort by latency
            filteredChannels.sortBy {
                it.sumOfLatencyValues
            }

            //highest latency acceptable (giving 10% over right now)
            val highestLatencySum = filteredChannels[0].sumOfLatencyValues * 1.1

            //only keep servers that are lowest latency
            filteredChannels.retainAll {
                it.sumOfLatencyValues <= highestLatencySum
            }

            //pick the managed channel
            if (saveNewManagedChannel(filteredChannels) && managedChannelWrapper != null) {
                //This will force the chat stream to end itself. It will automatically retry after this.
                ServiceLocator.provideChatStreamObjectOrNull()
                    ?.sendNetworkUnavailableToChatStream()
            }
        }

        Log.i(
            "loadBalancetrace",
            "created filtered channels"
        )

        //NOTE: The channel that this user is currently connected to could very well be shut down here
        // however the shutdown() function will allow the streams to continue.
        for (channelWithLoadStats in managedChannelsWithLoadStats) {
            if (
                channelWithLoadStats.channelWrapper.inetAddress.hostString != managedChannelWrapper?.inetAddress?.hostString
                || channelWithLoadStats.channelWrapper.inetAddress.port != managedChannelWrapper?.inetAddress?.port
            ) {
                channelWithLoadStats.channelWrapper.managedChannel.shutdown()
            }
        }

        mostRecentLoadBalancingReturnValue = returnLoadBalancerValue
        return returnLoadBalancerValue
    }

    //this function assumes a NON-EMPTY list has been passed as a parameter
    //returns true if managed channel was changed to a new channel, false otherwise
    private fun saveNewManagedChannel(filteredChannels: MutableList<ManagedChannelWithLoadStatistics>): Boolean {

        //if managedChannel is already active and it is a viable channel for connections, leave it
        // this is done so that the chatStream can simply refresh instead of restarting, it will also have an
        // 'orderly' shutdown done to preserve streams
        Log.i(
            "loadBalancingVal",
            "saveNewManagedChannel() managedChannel.state: ${
                managedChannelWrapper?.shortRPCManagedChannel?.getState(
                    false
                )
            }"
        )
        if (managedChannelWrapper?.shortRPCManagedChannel?.getState(false) == ConnectivityState.READY) {
            for (channel in filteredChannels) {
                if (
                    channel.channelWrapper.inetAddress.hostString == managedChannelWrapper?.inetAddress?.hostString
                    && channel.channelWrapper.inetAddress.port == managedChannelWrapper?.inetAddress?.port
                ) {
                    Log.i("loadBalancingVal", "managedChannelFound, returning")
                    return false
                }
            }
        }

        //put the server with acceptable latency and the lowest connections at the front
        filteredChannels.sortBy {
            it.numClients
        }

        managedChannelWrapper = createFullManagedChannelWrapper(
            filteredChannels[0].channelWrapper.inetAddress.hostString,
            filteredChannels[0].channelWrapper.inetAddress.port,
            filteredChannels[0].channelWrapper
        )

        return true
    }

    @VisibleForTesting
    fun setDefaultGlobals() {
        server_imported_values = createDefaultGlobals()
    }

    private fun createDefaultGlobals(): LoginValuesToReturnToClientOuterClass.GlobalConstantsMessage {

        val numberActivitiesStoredPerAccount = 5

        val activityMatchWeight = 10000
        val categoryMatchWeight = activityMatchWeight / 100

        val overlappingActivityTimesWeight =
            activityMatchWeight * numberActivitiesStoredPerAccount
        val overlappingCategoryTimesWeight = overlappingActivityTimesWeight / 100

        val betweenActivityTimesWeight = 1000
        val betweenCategoryTimesWeight = betweenActivityTimesWeight / 100

        //this is the time until the logged in account expires in seconds
        val globalConstants =
            LoginValuesToReturnToClientOuterClass.GlobalConstantsMessage.newBuilder()
                .setTimeBetweenLoginTokenVerification(30L * 60L * 1000L) //This default value will be used initially because it has not been requested from the server yet.

                .setTimeAvailableToSelectTimeFrames(3L * 7L * 24L * 60L * 60L * 1000L)
                .setMaximumPictureSizeInBytes(3 * 1024 * 1024)
                .setMaximumPictureThumbnailSizeInBytes(512 * 1024)
                .setTimeBetweenSendingSms(30L * 1000L)

                .setMaxBetweenTime(2.0 * 60.0 * 60.0 * 1000.0)
                .setActivityMatchWeight(activityMatchWeight)
                .setCategoriesMatchWeight(categoryMatchWeight)
                .setOverlappingActivityTimesWeight(overlappingActivityTimesWeight)
                .setOverlappingCategoryTimesWeight(overlappingCategoryTimesWeight)
                .setBetweenActivityTimesWeight(betweenActivityTimesWeight)
                .setBetweenCategoryTimesWeight(betweenCategoryTimesWeight)

                .setNumberPicturesStoredPerAccount(3)
                .setNumberActivitiesStoredPerAccount(numberActivitiesStoredPerAccount)
                .setNumberTimeFramesStoredPerAccount(5)
                .setNumberGenderUserCanMatchWith(4)
                .setTimeBeforeExpiredTimeMatchWillBeReturned(5L * 60L * 1000L)
                //ex: currentTime = 400, expiredTime = 430, variable = 45; currentTime+variable > expiredTime; so this match will be deleted and not returned to the user

                .setMaximumNumberBytesTrimmedTextMessage(255)
                .setMaximumNumberAllowedBytes(1023)
                .setMaximumNumberAllowedBytesUserBio(400)
                .setMaximumNumberAllowedBytesFirstName(24)
                .setMaximumNumberAllowedBytesUserFeedback(400)
                .setMaximumNumberAllowedBytesErrorMessage(25000)
                .setMaximumNumberAllowedBytesTextMessage(10000)

                .setHighestDisplayedAge(80)
                .setHighestAllowedAge(120)
                .setLowestAllowedAge(13)
                .setMaximumTimeMatchWillStayOnDevice(24L * 60L * 60L * 1000L)
                .setMinimumAllowedDistance(1)
                .setMaximumAllowedDistance(100)

                .setMaximumNumberResponseMessages(5)

                .setMaximumNumberChatRoomIdChars(8)
                .setMaximumChatMessageSizeInBytes(3 * 1024 * 1024)
                .setMaximumChatMessageThumbnailSizeInBytes(1024 * 1024)
                .setTimeBetweenChatMessageInviteExpiration(2L * 24L * 60L * 60L * 1000L)

                .setChatRoomStreamNumberTimesToRefreshBeforeRestart(3)
                .setChatRoomStreamDeadlineTime(-1L)
                .setMaxNumberMessagesToRequest(10)
                .setTimeInfoHasNotBeenObservedBeforeCleaned(7L * 24L * 60L * 60L * 1000L)

                .setActivityIconWidthInPixels(256)
                .setActivityIconHeightInPixels(256)
                .setActivityIconBorderWidth(8)
                .setActivityIconPadding(40)
                .setActivityIconColor("#4890E1")
                .setActivityIconBackgroundColor("#E6EBEF")

                .setImageQualityValue(50)
                .setPictureMaximumCroppedSizePx(2048)
                .setPictureThumbnailMaximumCroppedSizePx(256)

                .setTimeBetweenUpdatingSingleUser(10L * 60L * 1000L)
                .setTimeBetweenUpdatingSingleUserFunctionRunning(60L * 1000L)

                .setChatMessageImageThumbnailWidth(256)
                .setChatMessageImageThumbnailHeight(256)

                .setConnectionIdleTimeoutInMs(15L * 60L * 1000L)

        globalConstants.addMimeTypesAcceptedByServer("image/gif")
        globalConstants.addMimeTypesAcceptedByServer("image/png")

        //NOTE: Make sure that array index [0] in server info is always the 'best' server
        // to connect to. The most reliable probably. This is because there is a place that
        // could (even though it probably never will) access index [0] directly.
        globalConstants.addServerInfo(
            LoginValuesToReturnToClientOuterClass.IndividualServerInfo.newBuilder()
                .setAddress("{redacted}")
                .setPort(50051)
                .build()
        )

        globalConstants.addServerInfo(
            LoginValuesToReturnToClientOuterClass.IndividualServerInfo.newBuilder()
                .setAddress("{redacted}")
                .setPort(50051)
                .build()
        )

//        globalConstants.addServerInfo(
//            LoginValuesToReturnToClientOuterClass.IndividualServerInfo.newBuilder()
//                .setAddress("{redacted}")
//                .setPort(50051)
//                .build()
//        )
//
//        globalConstants.addServerInfo(
//            LoginValuesToReturnToClientOuterClass.IndividualServerInfo.newBuilder()
//                .setAddress("10.0.2.2")
//                .setPort(50052)
//                .build()
//        )

        globalConstants.appUrlForSharing = "EMPTY"

        globalConstants.maxServerOutboundMessageSizeInBytes =
            (globalConstants.maximumPictureSizeInBytes + globalConstants.maximumPictureThumbnailSizeInBytes) * globalConstants.numberPicturesStoredPerAccount + 2 * 1024 * 1024

        globalConstants.timeToRequestPreviousMessages = 20L * 1000L

        globalConstants.maximumMetaDataSizeToSendFromClient = (1024 * 1024) - 1024

        globalConstants.timeAfterExpiredMatchRemovedFromDevice = 0

        globalConstants.eventIdDefault = ""
        globalConstants.pinnedLocationDefaultLongitude = 181.0
        globalConstants.pinnedLocationDefaultLatitude = 91.0
        globalConstants.qrCodeDefault = "~"
        globalConstants.qrCodeMessageDefault = "~"
        globalConstants.qrCodeTimeUpdatedDefault = -5
        globalConstants.eventUserLastActivityTimeDefault = -1
        globalConstants.maximumNumberAllowedBytesEventTitle = 48

        globalConstants.eventGenderValue = "3vnT~{"
        globalConstants.eventAgeValue = 98000

        globalConstants.adminFirstName = "EventAdmin"

        return globalConstants.build()
    }

    fun initialSetInstallationId(_installationId: String) {
        installationId = _installationId
    }

    private fun capitalize(str: String): String {
        if (TextUtils.isEmpty(str)) {
            return str
        }
        val arr = str.toCharArray()
        var capitalizeNext = true
        val phrase = StringBuilder()
        for (c in arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c))
                capitalizeNext = false
                continue
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true
            }
            phrase.append(c)
        }
        return phrase.toString()
    }

    /** Returns the consumer friendly device name  */
    private fun getDeviceInfoCustom(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            capitalize(model)
        } else capitalize(manufacturer) + " " + model
    }

    fun getIconDrawable(
        iconIndex: Int,
        context: Context,
        errorStore: StoreErrorsInterface
    ): Drawable? {

        if (iconIndex > allIcons.lastIndex) {
            val errorMessage =
                "When requesting an icon drawable, an out of range icon index was passed.\n" +
                        "iconIndex: $iconIndex\n" +
                        "allIcons: $allIcons"

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage,
                applicationContext
            )

            return ResourcesCompat.getDrawable(
                context.resources,
                defaultIconImageID,
                context.theme
            )?.mutate()
        }

        //get icon position
        val icon = allIcons[iconIndex]

        return when {
            !icon.iconActive -> {
                val errorMessage =
                    "When requesting an icon drawable, a deleted index was attempted to be accessed.\n" +
                            "iconIndex: $iconIndex" +
                            "allIcons: $allIcons"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage,
                    applicationContext
                )

                //set drawable as textView drawable
                ResourcesCompat.getDrawable(
                    context.resources,
                    defaultIconImageID,
                    context.theme
                )
            }
            icon.iconIsDownloaded -> { //icon was downloaded
                val returnBitmap = BitmapFactory.decodeFile(icon.iconFilePath)
                BitmapDrawable(context.resources, returnBitmap)
            }
            else -> { //icon was not downloaded (default)
                ResourcesCompat.getDrawable(
                    context.resources,
                    iconsMapTable[icon.iconBasicResourceEntryName] ?: defaultIconImageID,
                    context.theme
                )
            }
        }?.mutate()
    }
}
