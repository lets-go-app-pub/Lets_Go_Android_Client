package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment

import account_state.AccountState
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import grpc_chat_commands.ChatRoomCommands
import kotlinx.coroutines.*
import report_enums.ReportMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment.PicturePopOutDialogFragment
import site.letsgoapp.letsgo.databases.messagesDatabase.messages.MessagesDataEntity
import site.letsgoapp.letsgo.databinding.FragmentChatRoomBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.applicationContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFile
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.*
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import type_of_chat_message.TypeOfChatMessageOuterClass
import java.io.*


class ChatRoomFragment : Fragment() {

    companion object {
        //Timeout of loading dialog when using leave or un match.
        const val LOADING_DIALOG_TIMEOUT_IN_MS = 15000L

        //Timeout of loading dialog when joining.
        //NOTE: It is possible that join chat room could still be loading when this dialog is automatically closed, however
        // over a minute (2*deadline_time) of load is a long time to make the user wait.
        const val LOADING_DIALOG_TIMEOUT_JOIN_CHAT_ROOM_IN_MS =
            GlobalValues.gRPC_Join_Chat_Room_Deadline_Time
    }

    private var _binding: FragmentChatRoomBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private var recyclerViewAdapter: ChatMessageAdapter? = null

    //holds the messages that have been observed by user, these will be stored in the database and cleared when onPause is called
    private var observedMessages = mutableSetOf<String>()
    private var observedMimeTypeUrls = mutableSetOf<String>()

    private lateinit var returnMessagesForChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnUpdatedMessagesToFragment>>

    //private lateinit var clientMessageToServerReturnValueObserver: Observer<FragmentIDEventWrapper<MessagesDataEntity>>
    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnChatRoomInfoUpdatedObserverData: Observer<EventWrapperWithKeyString<UpdateChatRoomInfoResultsDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnBlockReportChatRoomResultObserver: Observer<EventWrapperWithKeyString<BlockAndReportChatRoomResultsHolder>>
    private lateinit var returnClearHistoryFromChatRoomObserver: Observer<EventWrapperWithKeyString<MessagesDataEntity>>
    private lateinit var finishedChatRoomLocationRequestObserver: Observer<EventWrapperWithKeyString<ChatRoomLocationRequestReturn>>

    //from updateSingleUser and updateChatRoom
    private lateinit var returnUpdatedOtherUserObserver: Observer<EventWrapper<ReturnUpdatedOtherUserDataHolder>>

    private lateinit var returnSingleChatRoomObserver: Observer<EventWrapperWithKeyString<Unit>>
    private lateinit var returnJoinChatRoomResultObserver: Observer<EventWrapperWithKeyString<JoinChatRoomReturnValues>>

    private lateinit var returnAccountStateUpdatedObserver: Observer<EventWrapperWithKeyString<AccountStateUpdatedDataHolder>>

    //disable loading for chat rooms in case call failed
    private lateinit var disableLoadingObserver: Observer<EventWrapperWithKeyString<Int>>

    //Setting the reference to this be nullable so it doesn't hold the reference to something in the activity.
    private var topToolBar: Toolbar? = null
    // private var optionsMenu: Menu? = null

    private var userInfoCardLogic: UserInfoCardLogic? = null

    private var deviceScreenWidth = 0

    private var matchListItemDateTimeTextViewWidth =
        -1 //used in CardStackAdapter as the textView width for dates
    private var matchListItemDateTimeTextViewHeight =
        -1 //used in CardStackAdapter as the textView height for dates

    private var applicationActivity: AppActivity? = null

    private var tempFilePath = "" //the temporary path name used to hold the picture

    //invite message copied from shared application view model for use inside this chat room
    private var inviteMessageObject = InviteMessageObject()

    //location message copied from shared application view model for use inside this chat room
    private var locationMessageObject = LocationSelectedObject()

    //this will be set to true on onResume and false on onPause
    private var fragmentIsRunning = false

    enum class FragmentMode {
        UNSET,
        STANDARD_CHAT_ROOM_MODE,
        MATCH_MADE_CHAT_ROOM_MODE
    }

    private var currentFragmentMode = FragmentMode.UNSET
    private var scrollingCurrentlyAtBottom = true

    private val navigateToMessengerFragmentResourceId =
        R.id.action_chatRoomFragment_to_messengerScreenFragment

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var receivePictureResult: ActivityResultLauncher<Intent>? =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            //result.resultCode == Activity.RESULT_OK is checked inside the function
            receivePictureResult(
                result
            )
        }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
////        setHasOptionsMenu(true)
//    }
//
//    //this is called AFTER the fragment onResume()
//    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
//        super.onCreateOptionsMenu(menu, inflater)
//        Log.i("ApplicationActStuff", "ChatRoomFragment onCreateOptionsMenu()")
//        initializeOnCreateOptionsMenu(menu)
//    }
//
//    private fun initializeOnCreateOptionsMenu() {
//        when (currentFragmentMode) {
//            FragmentMode.UNSET -> {
//            } //Do nothing here, this means chat room is not yet initialized
//            FragmentMode.STANDARD_CHAT_ROOM_MODE -> {
//                setupStandardOptionsMenu()
//            }
//            FragmentMode.MATCH_MADE_CHAT_ROOM_MODE -> {
//                setupMatchMadeOptionsMenu()
//            }
//        }
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        Log.i("ApplicationActStuff", "ChatRoomFragment onCreateView()")

        applicationActivity = requireActivity() as AppActivity

        //need this so that the recycler view location type message will not crash
        for (fragment in childFragmentManager.fragments) {
            childFragmentManager.beginTransaction().remove(fragment).commitNow()
        }

        // Inflate the layout for this fragment
        _binding = FragmentChatRoomBinding.inflate(inflater, container, false)

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        deviceScreenWidth = getScreenWidth(requireActivity())

        //if location message needs sent
        if (sharedApplicationViewModel.chatRoomContainer.locationMessageObject.sendLocationMessage) {
            locationMessageObject =
                sharedApplicationViewModel.chatRoomContainer.locationMessageObject
            sharedApplicationViewModel.chatRoomContainer.clearChatRoomLocation()
        }

        //if invite message needs sent
        if (sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.sendInviteMessage) {
            inviteMessageObject = sharedApplicationViewModel.chatRoomContainer.inviteMessageObject
            sharedApplicationViewModel.chatRoomContainer.clearChatRoomInvite()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.i("ApplicationActStuff", "ChatRoomFragment onViewCreated()")
        //if activity binding is currently being inflated, delay the initialization (this means the
        // activity was re-created for some reason)
        if (applicationActivity?.fragmentBinding == null) {
            applicationActivity?.addLambdaToBeCalledAtEndOfOnCreate(thisFragmentInstanceID) {
                initializeChatRoomFragment()
            }
        } else {
            initializeChatRoomFragment()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initializeChatRoomFragment() {

        topToolBar = applicationActivity!!.fragmentBinding!!.activityAppTopToolbar.root
        val fragment = this

        //NOTE: anything that requires the recycler view to be initialized must be put in recyclerViewInitialization
        binding.chatRoomChatScrollToBottomImageView.visibility = View.GONE

        //set object go to bottom of recycler view when clicked
        binding.chatRoomChatScrollToBottomImageView.setSafeOnClickListener(300) {
            scrollToBottomOfRecyclerView()
        }

        //initialize the views and onClickListeners for either 'match made mode' or 'standard chat room' mode
        setUpChatRoomViews()

        //set up on scroll listener
        binding.chatRoomChatRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                //want this to be 'SCROLLING_UP' when dy is 0 so the recycler view will
                // request messages above the current message
                if (dy <= 0) {
                    recyclerViewAdapter?.setScrollingUpOrDown(ScrollingUpOrDown.SCROLLING_UP)
                } else {
                    recyclerViewAdapter?.setScrollingUpOrDown(ScrollingUpOrDown.SCROLLING_DOWN)
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                //NOTE: this code block will only make glide run when the scroll state is idle, however it looks bad
                /*if(newState == RecyclerView.SCROLL_STATE_IDLE) {
                    GlideApp.with(fragment).resumeRequests()
                } else { //if dragging or settling resume requests
                    GlideApp.with(fragment).pauseRequests()
                }*/

                //NOTE: this is NOT called when using scrollToPosition() and so post is used (see scrollToSelectedMessage() lambda
                // sent to ChatMessageAdapter) it would be called when using smoothScrollToPosition() however that would also
                // 1) take longer (could be much longer if lots of messages)
                // 2) download all the messages on the way
                setScrollState()
            }
        }
        )

        returnMessagesForChatRoomObserver = Observer { eventWrapper ->
            var result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)

            //A situation could occur where the user sends a message while the connection is down and then the
            // ChatStreamObject sends it when it comes back up. However the message result will not be sent
            // back with the original chatRoomFragmentId but instead with the CHAT_MESSAGE_STREAM_INIT in place
            // of it.
            if (result == null
                && eventWrapper.getKeyString() == ChatStreamObject.CHAT_MESSAGE_STREAM_INIT
                && eventWrapper.peekContent().first.chatRoomId == sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
            ) {
                result =
                    eventWrapper.getContentIfNotHandled(ChatStreamObject.CHAT_MESSAGE_STREAM_INIT)
            }
            Log.i("chatMessageState", "returnMessagesForChatRoomObserver result: $result")
            result?.let {
                handleUpdatedMessages(it)
            }
        }

        sharedApplicationViewModel.sendChatMessagesToFragments.returnMessageUpdatedForChatRoom.observe(
            viewLifecycleOwner,
            returnMessagesForChatRoomObserver
        )

        returnLeaveChatRoomResultObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled()
            result?.let {
                applicationActivity?.handleLeaveChatRoom(it, navigateToMessengerFragmentResourceId)
            }
        }

        sharedApplicationViewModel.returnLeaveChatRoomResult.observe(
            viewLifecycleOwner,
            returnLeaveChatRoomResultObserver
        )

        returnChatRoomInfoUpdatedObserverData = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleChatRoomInfoUpdated(it)
            }
        }

        //Listener to handle 'rich content' for the edit text.
        //NOTE: Can read about it here https://developer.android.com/guide/topics/input/receive-rich-content
        //NOTE: An example app here https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:appcompat/integration-tests/receive-content-testapp/
        val editTextOnReceiveContentListener =
            androidx.core.view.OnReceiveContentListener { editText, contentInfoCompat ->

                val split = contentInfoCompat.partition { it.uri != null }
                val uriContent = split.first
                val remaining = split.second

                if (uriContent != null && uriContent.linkUri != null) {
                    val clipData = uriContent.clip
                    var imageSaved = false
                    var errorStored = false

                    if (clipData.itemCount != 1) {

                        //Not sure if this is possible or not
                        val errorMessage = StringBuilder()
                        errorMessage.append(
                            "clipData for rich content contained more or less than 1 item.\n" +
                                    "clipData.itemCount: ${clipData.itemCount}\n" +
                                    "uriContent.linkUri: ${uriContent.linkUri}\n" +
                                    "clipData.description: ${clipData.description}\n"

                        )

                        for (i in 0 until clipData.itemCount) {
                            errorMessage.append(
                                "uri $i ${clipData.getItemAt(i).uri}\n"
                            )
                        }

                        errorMessage.append(
                            "uriContent: $uriContent"
                        )

                        storeErrorChatRoomFragment(
                            errorMessage.toString(),
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }

                    //NOTE: This will only send a single image. After it sends one, break is called.
                    // This is because it sends the link from linkUri and so each clip individually is
                    // not needed.
                    for (i in 0 until clipData.itemCount) {
                        val clipItem: ClipData.Item = clipData.getItemAt(i)
                        val mimeType =
                            editText.context.applicationContext.contentResolver.getType(clipItem.uri)
                                ?: ""
                        if (mimeTypeIsValid(mimeType)) {

                            Log.i("gifCallback", "starting")

                            //linkUri is check to be non-null above
                            val contentUrl = uriContent.linkUri.toString()
                            if (contentUrl.isNotBlank()) { //if the url is set to a value

                                val replyValuesForMessageDataEntity = checkIfReplyTypeMessage()

                                val generatedMessage = buildLoadingMessageMessageDataEntity(
                                    generateChatMessageUUID(),
                                    sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
                                    replyValuesForMessageDataEntity
                                )

                                sharedApplicationViewModel.chatRoomContainer.messages.add(
                                    MessagesDataEntityWithAdditionalInfo(generatedMessage)
                                )
                                val messageIndex =
                                    sharedApplicationViewModel.chatRoomContainer.messages.lastIndex

                                notifyMessageSent(messageIndex)

                                val replyByteArray =
                                    sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.byteArray

                                clearReplyAndHideViews()

                                val previousFileObject: MimeTypeHolderObject? =
                                    sharedApplicationViewModel.chatRoomContainer.mimeTypesFilePaths[contentUrl]

                                if ((previousFileObject == null) || !File(previousFileObject.filePath).isImage()) { //if a file for the gif does not already exist or is not an image

                                    if (mimeType == "image/png") { //if this is a png mime type

                                        Log.i(
                                            "chatMsgGlide",
                                            "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                        )
                                        GlideApp.with(fragment)
                                            .asBitmap()
                                            .load(uriContent.linkUri)
                                            .into(object : CustomTarget<Bitmap>() {
                                                override fun onResourceReady(
                                                    pngBitmap: Bitmap,
                                                    transition: Transition<in Bitmap>?,
                                                ) {

                                                    val generatedPngFile =
                                                        generateMimeTypeFile(
                                                            applicationContext,
                                                            contentUrl
                                                        )

                                                    val pngFileOutputStream =
                                                        FileOutputStream(generatedPngFile)

                                                    //NOTE: PNG are loss less, however they can be compressed to take up less space and take longer to encode/decode
                                                    // that being said, it says inside the PNG (Ctrl+Q) quality is ignored
                                                    pngBitmap.compress(
                                                        Bitmap.CompressFormat.PNG,
                                                        100,
                                                        pngFileOutputStream
                                                    )

                                                    CoroutineScope(ServiceLocator.globalIODispatcher).launch {

                                                        val mimeTypeUrl =
                                                            uriContent.linkUri.toString()

                                                        if (pngBitmap.width == 0
                                                            || pngBitmap.height == 0
                                                            || mimeTypeUrl.isBlank()
                                                            || mimeTypeUrl.length > GlobalValues.server_imported_values.maximumNumberAllowedBytes
                                                        ) {
                                                            Toast.makeText(
                                                                applicationContext,
                                                                R.string.chat_room_fragment_image_failed_to_load_image,
                                                                Toast.LENGTH_SHORT
                                                            ).show()

                                                            updateMessageInRecyclerViewToDeleted(
                                                                messageIndex
                                                            )
                                                        } else {
                                                            //NOTE: It is important to keep uriContent alive until this has
                                                            // completed otherwise permissions could be "revoked prematurely by
                                                            // the platform".
                                                            updateAndSendMimeTypeMessage(
                                                                generatedMessage,
                                                                messageIndex,
                                                                replyByteArray,
                                                                uriContent.linkUri.toString(),
                                                                generatedPngFile.absolutePath,
                                                                pngBitmap.width,
                                                                pngBitmap.height,
                                                                mimeType,
                                                                mimeTypeExists = false,
                                                            )
                                                        }
                                                    }
                                                }

                                                override fun onLoadCleared(placeholder: Drawable?) {
                                                    // this is called when imageView is cleared on lifecycle call or for
                                                    // some other reason.
                                                    // if you are referencing the bitmap somewhere else too other than this imageView
                                                    // clear it here as you can no longer have the bitmap
                                                    updateMessageInRecyclerViewToDeleted(
                                                        messageIndex
                                                    )
                                                }

                                                override fun onLoadFailed(errorDrawable: Drawable?) {

                                                    //This can happen when the connection is down and a sticker is sent.
                                                    Toast.makeText(
                                                        applicationContext,
                                                        R.string.chat_room_fragment_image_failed_to_load_image,
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    updateMessageInRecyclerViewToDeleted(
                                                        messageIndex
                                                    )
                                                }
                                            })
                                    } else { //if this is not a png mime type

                                        Log.i(
                                            "chatMsgGlide",
                                            "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                        )

                                        //download file and send message
                                        GlideApp.with(fragment)
                                            .download(uriContent.linkUri)
                                            .listener(object : RequestListener<File?> {
                                                override fun onLoadFailed(
                                                    e: GlideException?,
                                                    model: Any?,
                                                    target: Target<File?>,
                                                    isFirstResource: Boolean,
                                                ): Boolean {

                                                    //This can happen when the connection is down and a sticker is sent.
                                                    Toast.makeText(
                                                        applicationContext,
                                                        R.string.chat_room_fragment_image_failed_to_load_image,
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    updateMessageInRecyclerViewToDeleted(
                                                        messageIndex
                                                    )

                                                    return false
                                                }

                                                override fun onResourceReady(
                                                    gifFile: File?,
                                                    model: Any?,
                                                    target: Target<File?>,
                                                    dataSource: DataSource,
                                                    isFirstResource: Boolean,
                                                ): Boolean {

                                                    Log.i(
                                                        "chatMsgGlide",
                                                        "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                    )

                                                    if (gifFile != null && gifFile.isImage()) { //resource is not null

                                                        Log.i(
                                                            "chatMsgGlide",
                                                            "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                        )

                                                        val generatedGifFile =
                                                            generateMimeTypeFile(
                                                                requireActivity().applicationContext,
                                                                contentUrl
                                                            )

                                                        Log.i(
                                                            "chatMsgGlide",
                                                            "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                        )

                                                        FileInputStream(gifFile).use { inputStream ->

                                                            Log.i(
                                                                "chatMsgGlide",
                                                                "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                            )

                                                            FileOutputStream(generatedGifFile).use { outputStream ->

                                                                Log.i(
                                                                    "chatMsgGlide",
                                                                    "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                                )

                                                                val buffer = ByteArray(1024)
                                                                var len: Int

                                                                Log.i(
                                                                    "chatMsgGlide",
                                                                    "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                                )

                                                                while (inputStream.read(buffer)
                                                                        .also {
                                                                            len = it
                                                                        } > 0
                                                                ) {
                                                                    outputStream.write(
                                                                        buffer,
                                                                        0,
                                                                        len
                                                                    )
                                                                }

                                                                Log.i(
                                                                    "chatMsgGlide",
                                                                    "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                                )

                                                                val mimeTypeObject =
                                                                    getFileWidthAndHeightFromBitmap(
                                                                        generatedGifFile.absolutePath,
                                                                        mimeType
                                                                    )

                                                                Log.i(
                                                                    "chatMsgGlide",
                                                                    "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                                )

                                                                CoroutineScope(ServiceLocator.globalIODispatcher).launch {

                                                                    Log.i(
                                                                        "chatMsgGlide",
                                                                        "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                                    )

                                                                    val mimeTypeUrl =
                                                                        uriContent.linkUri.toString()

                                                                    if (mimeTypeObject.width == 0
                                                                        || mimeTypeObject.height == 0
                                                                        || mimeTypeUrl.isBlank()
                                                                        || mimeTypeUrl.length > GlobalValues.server_imported_values.maximumNumberAllowedBytes
                                                                    ) {
                                                                        Toast.makeText(
                                                                            applicationContext,
                                                                            R.string.chat_room_fragment_image_failed_to_load_image,
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()

                                                                        updateMessageInRecyclerViewToDeleted(
                                                                            messageIndex
                                                                        )
                                                                    } else {
                                                                        //NOTE: It is important to keep uriContent alive until this has
                                                                        // completed otherwise permissions could be "revoked prematurely by
                                                                        // the platform".
                                                                        updateAndSendMimeTypeMessage(
                                                                            generatedMessage,
                                                                            messageIndex,
                                                                            replyByteArray,
                                                                            uriContent.linkUri.toString(),
                                                                            generatedGifFile.absolutePath,
                                                                            mimeTypeObject.width,
                                                                            mimeTypeObject.height,
                                                                            mimeType,
                                                                            mimeTypeExists = false,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        Log.i(
                                                            "chatMsgGlide",
                                                            "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                                        )

                                                        gifFile.delete()

                                                    } else { //resource is null
                                                        Toast.makeText(
                                                            applicationContext,
                                                            R.string.chat_room_fragment_image_failed_to_load_image,
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        updateMessageInRecyclerViewToDeleted(
                                                            messageIndex
                                                        )

                                                        val errorMessage =
                                                            "Resource returned null when downloading MIME_TYPE.\n" +
                                                                    "uriContent.linkUri: ${uriContent.linkUri}\n" +
                                                                    "inputContentInfo.description: ${clipData.description}\n" +
                                                                    "uriContent: $uriContent"

                                                        storeErrorChatRoomFragment(
                                                            errorMessage,
                                                            Thread.currentThread().stackTrace[2].lineNumber,
                                                            printStackTraceForErrors()
                                                        )
                                                    }

                                                    return false
                                                }

                                            })
                                            .submit()

                                    }

                                } else { //if the passed gif exists and the file path is valid

                                    if ((previousFileObject.width == 0)
                                        || (previousFileObject.height == 0)
                                        || previousFileObject.filePath.isBlank()
                                        || (previousFileObject.filePath.length > GlobalValues.server_imported_values.maximumNumberAllowedBytes)
                                    ) {

                                        val errorMessage =
                                            "Invalid url found url already existed.\n" +
                                                    "previousFileObject: ${previousFileObject}\n"

                                        storeErrorChatRoomFragment(
                                            errorMessage,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors()
                                        )

                                        Toast.makeText(
                                            applicationContext,
                                            R.string.chat_room_fragment_image_failed_to_load_image,
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        updateMessageInRecyclerViewToDeleted(
                                            messageIndex
                                        )
                                    } else {
                                        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                                            updateAndSendMimeTypeMessage(
                                                generatedMessage,
                                                messageIndex,
                                                replyByteArray,
                                                contentUrl,
                                                previousFileObject.filePath,
                                                previousFileObject.width,
                                                previousFileObject.height,
                                                mimeType,
                                                mimeTypeExists = true,
                                            )
                                        }
                                    }
                                }

                                //only send one (first valid) result to server, ignore all others
                                imageSaved = true
                                break

                            } else {  //if the url (from linkUri) is not set to a value

                                errorStored = true

                                //Honestly not sure if everything on the soft keyboard has a url, these
                                // errors should let me know though
                                val errorMessage =
                                    "Invalid url found from uri when receiving 'rich content' for EditText.\n" +
                                            "uriContent.linkUri: ${uriContent.linkUri}\n" +
                                            "clipData.description: ${clipData.description}\n" +
                                            "uriContent: $uriContent"

                                storeErrorChatRoomFragment(
                                    errorMessage,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors()
                                )
                            }
                        }
                    }

                    if (!imageSaved && clipData.itemCount > 0) {

                        //Only want to store each error once
                        if (!errorStored) {
                            val errorMessage =
                                "No image was saved when 'rich content' was sent through OnReceiveContentListener.\n" +
                                        "uriContent.linkUri: ${uriContent.linkUri}\n" +
                                        "clipData.description: ${clipData.description}\n" +
                                        "uriContent: $uriContent"

                            storeErrorChatRoomFragment(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )
                        }

                        Toast.makeText(
                            applicationContext,
                            R.string.chat_room_fragment_image_failed_to_load_image,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                Log.i("receiveContentList", "uriContent: $uriContent")
                Log.i("receiveContentList", "remaining: $remaining")

                //Quoted from Android Documentation.
                // "Return anything that your app didn't handle. This preserves the default platform
                // behavior for text and anything else that you aren't implementing custom handling for."
                remaining
            }

        ViewCompat.setOnReceiveContentListener(
            binding.chatRoomSendMessageEditText,
            GlobalValues.server_imported_values.mimeTypesAcceptedByServerList.toTypedArray(),
            editTextOnReceiveContentListener
        )

        binding.chatRoomSendMessageEditText.apply {
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        s?.let {

                            val noWhiteSPace = s.replace("\\s".toRegex(), "")
                            if (noWhiteSPace.isNotEmpty()) { //text is in text view
                                binding.chatRoomSendLocationFrameLayout.visibility =
                                    if (binding.chatRoomSendLocationImageView.visibility == View.VISIBLE) {
                                        View.GONE
                                    } else {
                                        View.VISIBLE
                                    }
                                binding.chatRoomSendPictureImageView.visibility = View.GONE
                                binding.chatRoomSendMessageImageView.visibility = View.VISIBLE
                            } else { //text is empty
                                binding.chatRoomSendLocationFrameLayout.visibility = View.VISIBLE
                                binding.chatRoomSendPictureImageView.visibility = View.VISIBLE
                                binding.chatRoomSendMessageImageView.visibility = View.GONE
                            }
                        }
                    }

                    override fun afterTextChanged(s: Editable?) {}
                }
            )
            setHint(R.string.chat_room_fragment_edit_text_hint)
            maxLines = 6
            setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.transparent
                )
            )
            filters =
                arrayOf(ByteLengthFilter(GlobalValues.server_imported_values.maximumNumberAllowedBytesTextMessage))
        }

        sharedApplicationViewModel.returnChatRoomInfoUpdatedData.observe(
            viewLifecycleOwner,
            returnChatRoomInfoUpdatedObserverData
        )

        returnJoinedLeftChatRoomObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                applicationActivity?.handleJoinedLeftChatRoomReturn(
                    it,
                    navigateToMessengerFragmentResourceId
                )
            }
        }

        sharedApplicationViewModel.returnJoinedLeftChatRoom.observe(
            viewLifecycleOwner,
            returnJoinedLeftChatRoomObserver
        )

        returnKickedBannedFromChatRoomObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                applicationActivity?.handleKickedBanned(it, navigateToMessengerFragmentResourceId)
            }
        }

        sharedApplicationViewModel.returnKickedBannedFromChatRoom.observe(
            viewLifecycleOwner,
            returnKickedBannedFromChatRoomObserver
        )

        returnBlockReportChatRoomResultObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleBlockReportChatRoomResult(it)
            }
        }

        sharedApplicationViewModel.returnBlockReportChatRoomResult.observe(
            viewLifecycleOwner,
            returnBlockReportChatRoomResultObserver
        )

        returnClearHistoryFromChatRoomObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {

                //This is done because a menu for a message that was 'cleared' could still be shown.
                applicationActivity?.hideMenus()
                clearReplyAndHideViews()
                applicationActivity?.setLoadingDialogState(false)

                recyclerViewAdapter?.notifyDataSetChanged()
            }
        }

        sharedApplicationViewModel.sendChatMessagesToFragments.returnClearHistoryFromChatRoom.observe(
            viewLifecycleOwner,
            returnClearHistoryFromChatRoomObserver
        )

        finishedChatRoomLocationRequestObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                if (it.typeOfLocationUpdate == TypeOfLocationUpdate.CHAT_LOCATION_REQUEST) {
                    if (it.successful) {
                        applicationActivity?.navigate(
                            R.id.chatRoomFragment,
                            ChatRoomFragmentDirections.actionChatRoomFragmentToSelectLocationScreen(
                                ReasonSelectLocationCalled.CALLED_FOR_LOCATION_MESSAGE
                            )
                        )
                    } else { //failed to request location
                        //only set the location image view if send message is visible
                        binding.chatRoomSendLocationFrameLayout.visibility =
                            if (binding.chatRoomSendMessageImageView.visibility == View.GONE) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }

                        binding.chatRoomSendLocationImageView.visibility = View.VISIBLE
                        binding.chatRoomSendLocationProgressBar.visibility = View.GONE
                    }
                }
            }
        }

        applicationActivity?.finishedChatRoomLocationRequest?.observe(
            viewLifecycleOwner,
            finishedChatRoomLocationRequestObserver
        )

        returnUpdatedOtherUserObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleUpdateOtherUser(result)
            }
        }

        sharedApplicationViewModel.returnUpdatedChatRoomUser.observe(
            viewLifecycleOwner,
            returnUpdatedOtherUserObserver
        )

        binding.chatRoomSendLocationImageView.setSafeOnClickListener {
            binding.chatRoomSendLocationImageView.visibility = View.GONE
            binding.chatRoomSendLocationProgressBar.visibility = View.VISIBLE

            applicationActivity?.currentChatRoomFragmentInstanceID = thisFragmentInstanceID

            applicationActivity?.getCurrentLocation(TypeOfLocationUpdate.CHAT_LOCATION_REQUEST)
        }

        binding.chatRoomSendPictureImageView.setSafeOnClickListener {

            //this is re-enabled on onResume
            it.isEnabled = false

            selectPicturesWithIntent(
                requireActivity(),
                childFragmentManager,
                {
                    try {
                        val imageFile = createTemporaryImageFile(
                            getString(R.string.user_temporary_pictures_chat_message_file_prefix),
                            requireContext()
                        )
                        tempFilePath = imageFile?.absolutePath.toString()
                        imageFile
                    } catch (e: Exception) {
                        when (e) {
                            is IOException, is IllegalArgumentException, is SecurityException -> {
                                val errorMessage =
                                    "An exception was thrown when attempting to select a picture in chat room.\n" +
                                            "exception: ${e.message}\n"

                                storeErrorChatRoomFragment(
                                    errorMessage,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors()
                                )

                                null
                            }
                            else -> throw e
                        }
                    }
                },
                { intent ->
                    receivePictureResult?.launch(intent)
                }
            )
        }

        returnSingleChatRoomObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleClickOnChatRoomResult()
            }
        }

        sharedApplicationViewModel.returnSingleChatRoom.observe(
            viewLifecycleOwner,
            returnSingleChatRoomObserver
        )

        returnJoinChatRoomResultObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleJoinChatRoom(it)
            }
        }

        sharedApplicationViewModel.returnJoinChatRoomResult.observe(
            viewLifecycleOwner,
            returnJoinChatRoomResultObserver
        )

        binding.chatRoomSendMessageImageView.setSafeOnClickListener(300) {
            val text = binding.chatRoomSendMessageEditText.text.toString().trimEnd()

            if (text.isNotEmpty()) { //if text has something besides white space, send it (trimEnd was used above)
                sendTextMessage(text)
            }
        }

        binding.chatRoomCloseReplyImageView.setSafeOnClickListener {
            clearReplyAndHideViews()
        }

        returnAccountStateUpdatedObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleAccountStateUpdated(it)
            }
        }

        sharedApplicationViewModel.returnAccountStateUpdated.observe(
            viewLifecycleOwner,
            returnAccountStateUpdatedObserver
        )

        disableLoadingObserver = Observer { eventWrapper ->
            val result =
                eventWrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                applicationActivity?.setLoadingDialogState(false)
            }
        }

        sharedApplicationViewModel.disableLoading.observe(
            viewLifecycleOwner,
            disableLoadingObserver
        )

        sharedApplicationViewModel.chatRoomContainer.messages.clear()
        sharedApplicationViewModel.chatRoomContainer.mimeTypesFilePaths.clear()

        //this should be initialized before retrieveMessagesForChatRoomId() and after the live data is set up
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomFragmentOnViewCreated(
            thisFragmentInstanceID
        )

        //NOTE: this initializes the recycler view which will need to happen either way
        sharedApplicationViewModel.retrieveMessagesForChatRoomId()
    }

    private fun receivePictureResult(result: ActivityResult?) {
        val deleteTempPictureFile: () -> Unit = {
            try {
                //delete the file if the camera was not used to avoid leaving an empty file around
                File(tempFilePath).delete()
            } catch (e: Exception) {
                when (e) {
                    is IOException, is SecurityException -> {
                        val errorMessage =
                            "An exception was thrown when attempting to delete a previous temporary file.\n" +
                                    "exception: ${e.message}\n"

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }
                    else -> throw e
                }
            }
        }

        if (result?.resultCode == Activity.RESULT_OK) {

            val replyValuesForMessageDataEntity = checkIfReplyTypeMessage()
            val pictureMessageUUID = generateChatMessageUUID()

            val generatedMessage = buildLoadingMessageMessageDataEntity(
                pictureMessageUUID,
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
                replyValuesForMessageDataEntity
            )

            sharedApplicationViewModel.chatRoomContainer.messages.add(
                MessagesDataEntityWithAdditionalInfo(generatedMessage)
            )
            val messageIndex = sharedApplicationViewModel.chatRoomContainer.messages.lastIndex

            notifyMessageSent(messageIndex)

            val imageUri: Uri =
                if (result.data?.data != null) { //this means file or photo chooser was used
                    result.data!!.data!!
                } else { //this means the camera was used
                    Uri.fromFile(File(tempFilePath))
                }

            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                extractByteArrayForPictureMessage(
                    generatedMessage,
                    messageIndex,
                    pictureMessageUUID,
                    imageUri,
                    deleteTempPictureFile
                )
            }

        } else { //getting picture failed

            //NOTE: this will be called when the user presses the 'back' button from selecting stuff so don't store an error
            // it will also be called if something else goes wrong but there are only 3 results and I don't see anything like error
            deleteTempPictureFile()
        }
    }

    //NOTE: It is this functions responsibility to clean up by calling deleteTempPicLambda().
    private suspend fun extractByteArrayForPictureMessage(
        generatedMessage: MessagesDataEntity,
        messageIndex: Int,
        pictureMessageUUID: String,
        imageUri: Uri,
        deleteTempPicLambda: () -> Unit
    ) =
        withContext(ServiceLocator.globalIODispatcher) {

            //NOTE: Glide was not used originally here. However, it does a good job handling
            // that the camera rotates the picture on certain devices.
            GlideApp.with(requireContext())
                .asBitmap()
                .load(imageUri)
                .listener(object : RequestListener<Bitmap?> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap?>?,
                        isFirstResource: Boolean,
                    ): Boolean {
                        deleteTempPicLambda()

                        val errorMessage =
                            "An error occurred when extracting a bitmap from a uri using Glide.\n" +
                                    "GlideException: ${e?.message}\n" +
                                    "isFirstResource: $isFirstResource" +
                                    "ImageURI: $imageUri\n"

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        CoroutineScope(Dispatchers.Main).launch {
                            updateMessageInRecyclerViewToDeleted(messageIndex)

                            Toast.makeText(
                                applicationContext,
                                R.string.get_pictures_error_loading_picture,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return false
                    }

                    override fun onResourceReady(
                        pictureBitmap: Bitmap?,
                        model: Any?,
                        target: Target<Bitmap?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean,
                    ): Boolean {
                        if (pictureBitmap != null) {
                            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                                formatInfoForPictureMessage(
                                    pictureBitmap,
                                    generatedMessage,
                                    messageIndex,
                                    pictureMessageUUID
                                )

                                deleteTempPicLambda()
                            }
                        } else {

                            deleteTempPicLambda()

                            //NOTE: This seems to be possible when.
                            // 1) User clicks 'Files' when device is offline.
                            // 2) User selects a file that has not been downloaded by the device.

                            val errorMessage =
                                "An input stream extracting a Uri returned an exception.\n" +
                                        "dataSource: $dataSource\n" +
                                        "ImageURI: $imageUri\n" +
                                        "isFirstResource: $isFirstResource\n"

                            storeErrorChatRoomFragment(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            CoroutineScope(Dispatchers.Main).launch {
                                updateMessageInRecyclerViewToDeleted(messageIndex)

                                Toast.makeText(
                                    applicationContext,
                                    R.string.get_pictures_error_loading_picture,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        return false
                    }
                })
                .submit()
        }

    private suspend fun formatInfoForPictureMessage(
        pictureBitmapRaw: Bitmap,
        generatedMessage: MessagesDataEntity,
        messageIndex: Int,
        pictureMessageUUID: String
    ) = withContext(ServiceLocator.globalIODispatcher) {

        val pictureOStream = ByteArrayOutputStream()

        var newBitmapHeight = minOf(
            pictureBitmapRaw.height,
            GlobalValues.server_imported_values.pictureMaximumCroppedSizePx
        )

        var newBitmapWidth = minOf(
            pictureBitmapRaw.width,
            GlobalValues.server_imported_values.pictureMaximumCroppedSizePx
        )

        val scaledBitmap =
            if (pictureBitmapRaw.height != newBitmapHeight
                || pictureBitmapRaw.width != newBitmapWidth
            ) {
                if (pictureBitmapRaw.height > pictureBitmapRaw.width) {
                    //calculate new width
                    newBitmapWidth =
                        (newBitmapHeight.toFloat() * pictureBitmapRaw.width.toFloat() / pictureBitmapRaw.height.toFloat()).toInt()
                } else {
                    //calculate new height
                    newBitmapHeight =
                        (newBitmapWidth.toFloat() * pictureBitmapRaw.height.toFloat() / pictureBitmapRaw.width.toFloat()).toInt()
                }

                ThumbnailUtils.extractThumbnail(
                    pictureBitmapRaw,
                    newBitmapWidth,
                    newBitmapHeight
                )
            } else {
                pictureBitmapRaw
            }

        scaledBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            GlobalValues.server_imported_values.imageQualityValue,
            pictureOStream
        )

        val pictureByteArray = pictureOStream.toByteArray()

        Log.i(
            "picture_size",
            "scaledBitmap.width ${scaledBitmap.width} scaledBitmap.height ${scaledBitmap.height} pictureByteArray.size ${pictureByteArray.size}"
        )
        Log.i(
            "picture_size",
            "pictureBitmapRaw.height ${pictureBitmapRaw.height} pictureBitmapRaw.width ${pictureBitmapRaw.width}"
        )

        if (pictureByteArray.size <= GlobalValues.server_imported_values.maximumChatMessageSizeInBytes
            && pictureByteArray.isNotEmpty()
            && newBitmapWidth > 0
            && newBitmapHeight > 0
        ) { //if the file is not too big and has valid parameters

            val file = generatePictureMessageFile(
                pictureMessageUUID,
                applicationContext
            )

            try {
                file.writeBytes(pictureByteArray)
                updateAndSendPictureMessage(
                    generatedMessage,
                    messageIndex,
                    file.absolutePath,
                    newBitmapHeight,
                    newBitmapWidth
                )
            } catch (ex: IOException) {

                withContext(Dispatchers.Main) {
                    updateMessageInRecyclerViewToDeleted(messageIndex)

                    val errorMessage =
                        "When sending picture in chat room, writing picture to file failed.\n" +
                                "file.absolutePath: ${file.absolutePath}\n" +
                                "pictureByteArray.size: ${pictureByteArray.size}\n"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                }
            }
        } else { //if file is too big

            withContext(Dispatchers.Main) {
                updateMessageInRecyclerViewToDeleted(messageIndex)

                //These are the same for now but they may change
                val toastMessage =
                    if (pictureByteArray.size > GlobalValues.server_imported_values.maximumChatMessageSizeInBytes) {
                        getString(R.string.get_pictures_picture_incompatible)
                    } else if (newBitmapWidth > 0 || newBitmapHeight > 0) {
                        getString(R.string.get_pictures_picture_incompatible)
                    } else {
                        getString(R.string.get_pictures_picture_incompatible)
                    }

                Toast.makeText(
                    applicationContext,
                    toastMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleUpdatedMessages(
        updatedMessages: ReturnUpdatedMessagesToFragment,
    ) {
        if (updatedMessages.chatRoomInitialization) {
            if (recyclerViewAdapter == null) {
                recyclerViewInitialization()
                //if messages were added here, update observed time
                //NOTE: updateChatRoom will update the observed time, however that is not the only
                // path to this fragment (back from chatRoomInfo for example), so update observed time
                // on initialization if relevant
                if (updatedMessages.numMessagesAdded > 0) {
                    sendUpdateUserObservedChatRoom()
                }
            } else {
                val errorMessage =
                    "Should never get an initialization message when the recycler view is already initialized.\n" +
                            "updatedMessages: $updatedMessages"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )
            }
        } else if (recyclerViewAdapter != null) { //NOT a chatRoomInitialization message

            for (message in updatedMessages.messagesModified) {
                //dismiss menu if opened for this message
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForUUID(message.messageUUIDPrimaryKey)
                applicationActivity?.chatRoomActiveMessagePopupMenu?.dismissForUUID(message.messageUUIDPrimaryKey)

                if (recyclerViewAdapter != null) {
                    //NOTE: sendUpdateUserObservedChatRoom() is not needed, because the observed time is compared to the
                    // chatRoomActiveTime and so it isn't relevant that it is updated here because chatRoomActiveTime is not
                    // updated with a MESSAGE_EDITED type, also the observed chat room time does not need to be updated every time
                    // this function is called

                    Log.i("chatMsgGlide", "handleUpdatedMessages() notifyItemChanged")
                    recyclerViewAdapter?.notifyItemChanged(message.indexNumber)
                } else {
                    val errorMessage =
                        "Received other messages before recycler view was initialized.\n" +
                                "updatedMessages: $updatedMessages"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
            }

            for (accountOID in updatedMessages.accountOIDsRemoved) {
                //dismiss options for user if relevant
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(
                    accountOID
                )
            }

            if (updatedMessages.accountOIDsRemoved.isNotEmpty()) { //if a user was added or removed, check if the members list needs to be updated
                //NOTE: sometimes a message will be sent back that is one of the above message types and the message string will NOT need updated
                setNumberOfMembersInChatRoom()
            }

            if (updatedMessages.numMessagesAdded > 0) {

                Log.i(
                    "matchingChatRoomOID",
                    "currentFragmentMode: $currentFragmentMode; matchingChatRoomOID: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID}"
                )

                if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE
                    && sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID == ""
                ) {
                    setUpStandardChatRoomMode()
                }

                val notAtBottom = binding.chatRoomChatRecyclerView.canScrollVertically(1)
                val scrollState = binding.chatRoomChatRecyclerView.scrollState

                //update that the user observed this message
                sendUpdateUserObservedChatRoom()

                Log.i(
                    "itemRangeInserted",
                    "firstIndexAdded: ${updatedMessages.firstIndexAdded};" +
                            " numMessagesAdded: ${updatedMessages.numMessagesAdded};" +
                            " messages.size: ${sharedApplicationViewModel.chatRoomContainer.messages.size}" +
                            " message.text ${sharedApplicationViewModel.chatRoomContainer.messages.last().messageDataEntity.messageText}"
                )

                recyclerViewAdapter?.notifyItemRangeInserted(
                    updatedMessages.firstIndexAdded,
                    updatedMessages.numMessagesAdded
                )

                if (!notAtBottom && scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    binding.chatRoomChatRecyclerView.scrollToPosition(sharedApplicationViewModel.chatRoomContainer.messages.size - 1)
                }
            }
        }
        // else {} This case means the recycler view adapter was not initialized and this is not
        // an initialization message. The case is handled inside setupChatRoomFragmentNewMessages()
        // by sorting the messages list when the initialization message is received.
    }

    //this will be run after the chat room result and the messages results are returned from the database
    private fun recyclerViewInitialization() {

        if (recyclerViewAdapter != null) {
            val errorMessage =
                "recyclerViewInitialization() called when recyclerViewAdapter was already initialized.\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
            return
        }

        if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId != "~") {

            recyclerViewAdapter = ChatMessageAdapter(
                requireActivity().applicationContext,
                requireActivity(),
                GlideApp.with(this),
                sharedApplicationViewModel.chatRoomContainer.messages,
                observedMessages,
                observedMimeTypeUrls,
                sharedApplicationViewModel.chatRoomContainer.mimeTypesFilePaths,
                sharedApplicationViewModel.chatRoomContainer.chatRoom,
                deviceScreenWidth,
                sharedApplicationViewModel.userName,
                hideSoftKeyboard = {
                    this.hideKeyboard()
                },
                saveNewMimeTypeFileToDatabase = { mimeTypeUrl, fileName, mimeTypeWidth, mimeTypeHeight ->

                    //NOTE: the way this works is the mime type will already have a database slot, whenever it is initially received it is saved
                    // or the reference count is increased, the only thing that will need to be done is to update the fileName
                    sharedApplicationViewModel.updateMimeTypeFileName(
                        mimeTypeUrl,
                        fileName,
                        mimeTypeWidth,
                        mimeTypeHeight
                    )
                },
                requestPictureFromServer = { chatRoomId, messageUUID ->

                    sharedApplicationViewModel.requestMessageUpdate(
                        chatRoomId,
                        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO,
                        listOf(messageUUID)
                    )
                },
                requestMessagesInfoFromServer = { chatRoomId, messageUUIDs ->
                    Log.i(
                        "followingUpdates",
                        "ChatRoomFragment messageUUIDS.size(): ${messageUUIDs.size}"
                    )
                    sharedApplicationViewModel.requestMessageUpdate(
                        chatRoomId,
                        TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO,
                        messageUUIDs
                    )
                },
                moveMenuDisplayView = {

                    /**
                     * The rawHeight and rawWidth from the view is extracted and stored inside
                     * ChatMessageAdapter.coordinates. However X and Y for chatRoomChatFloatingView
                     * do not take take the top toolbar into account (because they are local
                     * coordinates from activity_app.xml). Therefore the top bar height is subtracted
                     * off before the touch location is calculated.
                     * See [chat_message_popup] for additional details.
                     * **/
                    val theHeight =
                        applicationActivity?.fragmentBinding?.activityAppTopToolbar?.toolbarApplicationTopPrimaryToolbar?.height
                            ?: 0
                    binding.chatRoomChatFloatingView.x = ChatMessageAdapter.coordinates[0]
                    binding.chatRoomChatFloatingView.y =
                        ChatMessageAdapter.coordinates[1] - theHeight
                    binding.chatRoomChatFloatingView
                },
                showPictureMessagePopup = { pictureFilePath ->

                    val pictureString = convertPicturesListToString(
                        mutableListOf(
                            PictureInfo(
                                pictureFilePath,
                                0,
                                0
                            )
                        )
                    )

                    //NOTE: I believe that when this view is destroyed this dialog fragment
                    // will be cleared because it goes with the pictureImageView
                    val dialogFragment = PicturePopOutDialogFragment()

                    val bundle = Bundle()
                    bundle.putString(PICTURE_STRING_FRAGMENT_ARGUMENT_KEY, pictureString)
                    bundle.putInt(
                        PICTURE_INDEX_NUMBER_FRAGMENT_ARGUMENT_KEY,
                        0
                    )
                    dialogFragment.arguments = bundle

                    dialogFragment.show(childFragmentManager, "DialogFragment")
                },
                showChatMessagePopup = { view, editSelectedLambda, editable, copyText, passedReplyMessageInfoObject, deleteMessageInfoObject ->

                    applicationActivity?.chatRoomActiveMessagePopupMenu?.showChatRoomTextMessagePopupMenu(
                        view,
                        editSelected = editSelectedLambda,
                        replySelected = {
                            setupNextMessageAsReply(
                                passedReplyMessageInfoObject,
                                saveReply = true
                            )
                        },
                        deleteSelected = {
                            deleteMessage(deleteMessageInfoObject)
                        },
                        editable,
                        copyText,
                        passedReplyMessageInfoObject.messageBeingRepliedToUUID
                    )
                },
                updateInviteMessageToExpired = { uuidPrimaryKey ->
                    sharedApplicationViewModel.updateInviteMessageToExpired(uuidPrimaryKey)
                },
                joinChatRoomFromInvite = { uuidPrimaryKey, chatRoomId, chatRoomPassword ->

                    applicationActivity?.setLoadingDialogState(
                        true,
                        LOADING_DIALOG_TIMEOUT_JOIN_CHAT_ROOM_IN_MS
                    )
                    sharedApplicationViewModel.joinChatRoomFromInvite(
                        uuidPrimaryKey,
                        chatRoomId,
                        chatRoomPassword,
                        thisFragmentInstanceID
                    )
                },
                sendEditedTextMessage = { uuidPrimaryKey, newMessageText ->

                    //NOTE: by here, newMessageText is expected to be more than whitespace, not be empty and have changed from the
                    // previous message
                    var messageIndex = -1

                    for (i in sharedApplicationViewModel.chatRoomContainer.messages.indices) {
                        if (uuidPrimaryKey == sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey) {
                            messageIndex = i

                            //update instance of message that is read by the chat room fragment and chat message adapter
                            Log.i(
                                "updateEdited",
                                "sendEditedTextMessage newMessage $newMessageText"
                            )
                            sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.messageText =
                                newMessageText
                            sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.isEdited =
                                true
                            sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.editHasBeenSent =
                                false
                            recyclerViewAdapter?.notifyItemChanged(i)
                        }
                    }

                    if (messageIndex != -1) { //message found

                        if (sharedApplicationViewModel.chatRoomContainer.messages[messageIndex].messageDataEntity.messageType == TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.TEXT_MESSAGE.number) {

                            updateNextMessageInRecyclerView(messageIndex)

                            sendMessageEditedMessage(
                                uuidPrimaryKey,
                                newMessageText
                            )
                        } else { //wrong type of message was attempted to be edited
                            val errorMessage =
                                "An edit was requested on a message that was not type text message got the value.\n" +
                                        "messageType: ${
                                            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(
                                                sharedApplicationViewModel.chatRoomContainer.messages[messageIndex].messageDataEntity.messageType
                                            )
                                        }\n" +
                                        "messageDataEntity: ${sharedApplicationViewModel.chatRoomContainer.messages[messageIndex].messageDataEntity}"

                            storeErrorChatRoomFragment(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            Toast.makeText(
                                applicationContext,
                                R.string.chat_room_fragment_error_sending_edited_message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else { //message not found

                        val errorMessage =
                            "Message was not found when attempting to send 'Edited Message'. This should be checked for before this is ever" +
                                    " called (and the option shouldn't even be shown).\n" +
                                    "messageIndex: $messageIndex" +
                                    "messageType: $uuidPrimaryKey\n" +
                                    "newMessageText: $newMessageText"

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        Toast.makeText(
                            applicationContext,
                            R.string.chat_room_fragment_error_sending_edited_message,
                            Toast.LENGTH_SHORT
                        ).show()

                    }
                },
                sendDeletedMessage = { deleteMessageInfoObject ->
                    deleteMessage(deleteMessageInfoObject)
                },
                scrollToSelectedMessage = { indexOfMessageInsideList ->
                    if (recyclerViewAdapter != null
                        && indexOfMessageInsideList < (recyclerViewAdapter?.itemCount ?: 0)
                    ) {

                        //NOTE: smoothScrollToPosition has the potential to cause a lot of problems if the list item is a long
                        // ways away or there are a lot of things to download in between
                        binding.chatRoomChatRecyclerView.scrollToPosition(
                            indexOfMessageInsideList
                        )

                        //add runnable to check the scroll state to after scrollToPosition() completes
                        binding.chatRoomChatRecyclerView.post {
                            setScrollState()
                        }
                    }
                },
                showOtherUserNotInChatRoomOnlyBlockAndReportPopupMenu = { viewMenuIsBoundTo, accountOID, chatRoomId, messageUUID ->
                    applicationActivity?.adminUserOptionsFragmentPopupMenu?.showOtherUserNotInChatRoomOnlyBlockAndReportPopupMenu(
                        viewMenuIsBoundTo,
                        blockAndReportMember = {
                            applicationActivity?.blockAndReportUserFromChatRoom(
                                childFragmentManager,
                                accountOID,
                                false,
                                ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_MESSAGE,
                                chatRoomId,
                                messageUUID,
                            )
                        },
                        accountOID,
                        messageUUID
                    )
                },
                showUserNoAdminBlockAndReportPopupMenu = { viewMenuIsBoundTo, accountOID, userName, chatRoomId, messageUUID ->
                    applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserNoAdminBlockAndReportPopupMenu(
                        viewMenuIsBoundTo,
                        blockAndReportMember = {
                            applicationActivity?.blockAndReportUserFromChatRoom(
                                childFragmentManager,
                                accountOID,
                                false,
                                ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_MESSAGE,
                                chatRoomId,
                                messageUUID,
                            )
                        },
                        inviteMember = {
                            startInviteMember(accountOID, userName)
                        },
                        accountOID,
                        messageUUID
                    )
                },
                showUserNoAdminUnblockPopupMenu = { viewMenuIsBoundTo, accountOID, messageUUID, userName ->
                    applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserNoAdminUnblockPopupMenu(
                        viewMenuIsBoundTo,
                        unblockMember = {
                            sharedApplicationViewModel.unblockOtherUser(
                                accountOID
                            )
                        },
                        inviteMember = {
                            startInviteMember(accountOID, userName)
                        },
                        accountOID,
                        messageUUID
                    )
                },
                showUserAdminBlockAndReportPopupMenu = { viewMenuIsBoundTo, accountOID, userName, chatRoomId, messageUUID ->
                    applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserAdminBlockAndReportPopupMenu(
                        viewMenuIsBoundTo,
                        promoteNewAdmin = {
                            sharedApplicationViewModel.promoteNewAdmin(
                                accountOID,
                                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                            )
                        },
                        kickMember = {
                            sharedApplicationViewModel.removeUserFromChatRoom(
                                ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK,
                                accountOID
                            )
                        },
                        banMember = {
                            sharedApplicationViewModel.removeUserFromChatRoom(
                                ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.BAN,
                                accountOID
                            )
                        },
                        blockAndReportMember = {
                            applicationActivity?.blockAndReportUserFromChatRoom(
                                childFragmentManager,
                                accountOID,
                                false,
                                ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_MESSAGE,
                                chatRoomId,
                                messageUUID
                            )
                        },
                        inviteMember = {
                            startInviteMember(accountOID, userName)
                        },
                        accountOID,
                        messageUUID
                    )
                },
                showUserAdminUnblockPopupMenu = { viewMenuIsBoundTo, accountOID, messageUUID, userName ->
                    applicationActivity?.adminUserOptionsFragmentPopupMenu?.showUserAdminUnblockPopupMenu(
                        viewMenuIsBoundTo,
                        promoteNewAdmin = {
                            sharedApplicationViewModel.promoteNewAdmin(
                                accountOID,
                                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                            )
                        },
                        kickMember = {
                            sharedApplicationViewModel.removeUserFromChatRoom(
                                ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.KICK,
                                accountOID
                            )
                        },
                        banMember = {
                            sharedApplicationViewModel.removeUserFromChatRoom(
                                ChatRoomCommands.RemoveFromChatRoomRequest.KickOrBan.BAN,
                                accountOID
                            )
                        },
                        unblockMember = {
                            sharedApplicationViewModel.unblockOtherUser(
                                accountOID
                            )
                        },
                        inviteMember = {
                            startInviteMember(accountOID, userName)
                        },
                        accountOID,
                        messageUUID
                    )
                },
                sendErrorToActivity = { error ->
                    applicationActivity?.handleGrpcFunctionError(error)
                },
                if (GlobalValues.setupForTesting) ServiceLocator.testingDeleteFileInterface!! else StartDeleteFile(
                    applicationContext
                ),
                errorStore
            )

            binding.chatRoomChatRecyclerView.apply {
                adapter = recyclerViewAdapter
                val linearLayoutManager = NoPredictiveAnimLinearLayoutManager(requireContext(), 1)
                linearLayoutManager.stackFromEnd = true
                layoutManager = linearLayoutManager
                setHasFixedSize(false)
            }

            //if location message or invite message need to be sent, they will 'consume' the reply so no reason to initialize
            // the reply views
            if (!locationMessageObject.sendLocationMessage && !inviteMessageObject.sendInviteMessage) { //if location message object and invite message object are not set
                if (sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.chatRoomId == sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId) { //if the reply is for this chat room
                    //set up the reply to be shown
                    setupNextMessageAsReply(
                        sharedApplicationViewModel.chatRoomContainer.replyMessageInfo,
                        saveReply = false
                    )
                } else if (sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.chatRoomId != "") { //if this reply is not for the current chat room however it is set up

                    //NOTE: The Join Chat Room button on an invite object will clear the reply, location and
                    // invite objects. So this should never happen.

                    val errorMessage =
                        "A chatRoomId was setup for a different chat room inside a reply object.\n" +
                                "replyMessageInfo: ${sharedApplicationViewModel.chatRoomContainer.replyMessageInfo}"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    sharedApplicationViewModel.chatRoomContainer.clearReply()
                }
            }

            //NOTE: The below messages are sent at the end of the block so that they end up at
            // the END of the recycler view.
            if (locationMessageObject.sendLocationMessage) {
                sendLocationMessage()
                locationMessageObject = LocationSelectedObject()
            }

            //if a location message needs to be sent, send it
            if (inviteMessageObject.sendInviteMessage) {
                sendInviteMessage()
                inviteMessageObject = InviteMessageObject()
            }
        } else {
            //This could happen if for some reason a user clicks a chat room
            // inside of the messenger screen that was not properly removed when they were kicked.
            val errorMessage =
                "Chat room was never set up when navigating to ChatRoomFragment.\n" +
                        "sharedApplicationViewModel.chatRoomContainer.chatRoom: ${sharedApplicationViewModel.chatRoomContainer.chatRoom}"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            Toast.makeText(
                applicationContext,
                R.string.chat_room_fragment_image_error_loading_chat_room,
                Toast.LENGTH_SHORT
            ).show()

            //navigate away, this cannot do anything w/o a set up chat room
            applicationActivity?.hideMenus()
            applicationActivity?.navigate(
                R.id.chatRoomFragment,
                navigateToMessengerFragmentResourceId
            )
        }
    }

    private fun updateNextMessageInRecyclerView(messageIndex: Int) {

        var nextPositionToBeUpdated = -1

        //find next value that is a message
        for (i in messageIndex + 1 until sharedApplicationViewModel.chatRoomContainer.messages.size) {
            if (sharedApplicationViewModel.chatRoomContainer.messages[i].messageLayoutType != LayoutType.LAYOUT_EMPTY) {
                nextPositionToBeUpdated = i
                break
            }
        }

        Log.i("updateNextMsgRcy", "nextPositionToBeUpdated: $nextPositionToBeUpdated")

        //the date can be different on the next message because the isEdited time is not counted towards the date
        if (nextPositionToBeUpdated != -1) {
            recyclerViewAdapter?.notifyItemChanged(nextPositionToBeUpdated)
        }
    }

    private fun setupNextMessageAsReply(
        passedReplyMessageInfoObject: ReplyMessageInfoObject,
        saveReply: Boolean,
    ) {

        if (passedReplyMessageInfoObject.messageBeingRepliedToUUID.isValidUUIDKey()) {

            if (passedReplyMessageInfoObject.sentByAccountOID.isValidMongoDBOID()
                && passedReplyMessageInfoObject.replyType != TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET
            ) { //sentByAccountOID is valid & proper reply message

                //set up name on reply
                extractAndSetupName(
                    requireContext().applicationContext,
                    passedReplyMessageInfoObject.sentByAccountOID,
                    sharedApplicationViewModel.userName,
                    binding.chatRoomTextReplyInclude.chatMessageListItemReplyNameTextView,
                    sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers,
                    errorStore
                )

                when (passedReplyMessageInfoObject.replyType) {
                    TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {

                        if (passedReplyMessageInfoObject.messageText != "") {

                            binding.chatRoomTextReplyInclude.chatMessageListItemReplyMessageTypeTextView.text =
                                passedReplyMessageInfoObject.messageText

                            setReplyAndShowViews(
                                showImageView = false,
                                passedReplyMessageInfoObject,
                                saveReply
                            )
                        } else {

                            val errorMessage =
                                "A text message reply was passed with no text attached.\n" +
                                        "passedReplyMessageInfoObject: $passedReplyMessageInfoObject"

                            storeErrorChatRoomFragment(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            //clears the reply, error must be sent first or will not be able to extract any info from it
                            clearReplyAndHideViews()

                            Toast.makeText(
                                applicationContext,
                                R.string.chat_room_fragment_error_sending_reply_message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    }
                    TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY -> {
                        binding.chatRoomTextReplyInclude.chatMessageListItemReplyMessageTypeTextView.setText(
                            R.string.chat_message_type_text_location
                        )

                        setReplyAndShowViews(
                            showImageView = false,
                            passedReplyMessageInfoObject,
                            saveReply
                        )
                    }
                    TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY -> {
                        binding.chatRoomTextReplyInclude.chatMessageListItemReplyMessageTypeTextView.setText(
                            R.string.chat_message_type_text_invite
                        )

                        setReplyAndShowViews(
                            showImageView = false,
                            passedReplyMessageInfoObject,
                            saveReply
                        )
                    }
                    TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY,
                    TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY,
                    -> {

                        if (!saveReply) { //if byteArray does not need to be saved

                            Log.i(
                                "chatMsgGlide",
                                "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                            )
                            //load image into glide
                            GlideApp.with(this)
                                .load(passedReplyMessageInfoObject.byteArray)
                                .error(GlobalValues.defaultPictureResourceID)
                                .listener(
                                    object : RequestListener<Drawable?> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<Drawable?>,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            setReplyAndShowViews(
                                                showImageView = false,
                                                passedReplyMessageInfoObject,
                                                saveReply
                                            )
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: Drawable?,
                                            model: Any?,
                                            target: Target<Drawable?>,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            setReplyAndShowViews(
                                                showImageView = true,
                                                passedReplyMessageInfoObject,
                                                saveReply
                                            )
                                            return false
                                        }
                                    }
                                )
                                .into(binding.chatRoomTextReplyInclude.chatMessageListItemReplyImageView)

                            val replyIsFromMessage =
                                generateNameForPictureOrMimeTypeMessageReply(
                                    passedReplyMessageInfoObject.replyType,
                                    requireContext(),
                                    errorStore
                                ) {
                                    passedReplyMessageInfoObject.mimeType
                                }

                            binding.chatRoomTextReplyInclude.chatMessageListItemReplyMessageTypeTextView.text =
                                replyIsFromMessage

                        } else { //if byteArray has not been saved yet

                            if (passedReplyMessageInfoObject.imageFilePath != ""
                                && passedReplyMessageInfoObject.imageFilePath != "~"
                            ) { //if file path is valid (at least it is not empty)

                                val fragmentContext = this

                                Log.i(
                                    "chatMsgGlide",
                                    "line: ${Thread.currentThread().stackTrace[2].lineNumber}"
                                )
                                GlideApp.with(this)
                                    .asBitmap()
                                    .load(passedReplyMessageInfoObject.imageFilePath)
                                    .into(object : CustomTarget<Bitmap>(
                                        GlobalValues.server_imported_values.chatMessageImageThumbnailWidth,
                                        GlobalValues.server_imported_values.chatMessageImageThumbnailHeight
                                    ) {
                                        override fun onResourceReady(
                                            resource: Bitmap,
                                            transition: Transition<in Bitmap>?,
                                        ) {

                                            val byteArrayBitmapStream = ByteArrayOutputStream()

                                            if (passedReplyMessageInfoObject.mimeType == "image/png") {
                                                //quality value is ignored for PNG types
                                                resource.compress(
                                                    Bitmap.CompressFormat.PNG,
                                                    100,
                                                    byteArrayBitmapStream
                                                )
                                            } else {
                                                resource.compress(
                                                    Bitmap.CompressFormat.JPEG,
                                                    GlobalValues.server_imported_values.imageQualityValue,
                                                    byteArrayBitmapStream
                                                )
                                            }

                                            passedReplyMessageInfoObject.byteArray =
                                                byteArrayBitmapStream.toByteArray()

                                            if (passedReplyMessageInfoObject.byteArray.isNotEmpty()
                                                && passedReplyMessageInfoObject.byteArray.size < GlobalValues.server_imported_values.maximumChatMessageThumbnailSizeInBytes
                                            ) { //generated byteArray is valid

                                                CoroutineScope(Dispatchers.Main).launch {
                                                    //an exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                                                    // if this is called from inside another glide context
                                                    GlideApp.with(fragmentContext)
                                                        .load(resource)
                                                        .error(GlobalValues.defaultPictureResourceID)
                                                        .into(binding.chatRoomTextReplyInclude.chatMessageListItemReplyImageView)
                                                }

                                                setReplyAndShowViews(
                                                    showImageView = true,
                                                    passedReplyMessageInfoObject,
                                                    saveReply
                                                )
                                            } else { //generated byteArray is invalid

                                                val errorMessage =
                                                    "A generated byteArray for a reply is invalid (too large or empty).\n" +
                                                            "passedReplyMessageInfoObject.byteArray.size: ${passedReplyMessageInfoObject.byteArray.size}"

                                                storeErrorChatRoomFragment(
                                                    errorMessage,
                                                    Thread.currentThread().stackTrace[2].lineNumber,
                                                    printStackTraceForErrors()
                                                )

                                                //clears the reply, error must be sent first or will not be able to extract any info from it
                                                clearReplyAndHideViews()

                                                Toast.makeText(
                                                    applicationContext,
                                                    R.string.chat_room_fragment_error_sending_reply_message,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }

                                        override fun onLoadCleared(placeholder: Drawable?) {
                                            // this is called when imageView is cleared on lifecycle call or for
                                            // some other reason.
                                            // if you are referencing the bitmap somewhere else too other than this imageView
                                            // clear it here as you can no longer have the bitmap
                                        }
                                    })

                                val replyIsFromMessage =
                                    generateNameForPictureOrMimeTypeMessageReply(
                                        passedReplyMessageInfoObject.replyType,
                                        requireContext(),
                                        errorStore
                                    ) {
                                        passedReplyMessageInfoObject.mimeType
                                    }

                                binding.chatRoomTextReplyInclude.chatMessageListItemReplyMessageTypeTextView.text =
                                    replyIsFromMessage

                            } else { //file path for a this picture or gif is invalid
                                //NOTE: this is possible if the image is still downloading and the user attempts to reply to it
                                Toast.makeText(
                                    applicationContext,
                                    R.string.chat_room_fragment_wait_for_download_to_complete_reply,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    else -> { //replyType is an invalid type
                        val errorMessage =
                            "A generated byteArray for a reply is invalid (too large or empty).\n" +
                                    "passedReplyMessageInfoObject.chatRoomId: ${passedReplyMessageInfoObject.chatRoomId}\n" +
                                    "passedReplyMessageInfoObject.sentByAccountOID: ${passedReplyMessageInfoObject.sentByAccountOID}\n" +
                                    "passedReplyMessageInfoObject.messageBeingRepliedToUUID: ${passedReplyMessageInfoObject.messageBeingRepliedToUUID}\n" +
                                    "passedReplyMessageInfoObject.replyType: ${passedReplyMessageInfoObject.replyType}\n" +
                                    "passedReplyMessageInfoObject.messageText: ${passedReplyMessageInfoObject.messageText}\n" +
                                    "passedReplyMessageInfoObject.mimeType: ${passedReplyMessageInfoObject.mimeType}\n" +
                                    "passedReplyMessageInfoObject.imageFilePath: ${passedReplyMessageInfoObject.imageFilePath}\n" +
                                    "passedReplyMessageInfoObject.byteArray.size: ${passedReplyMessageInfoObject.byteArray.size}\n"

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        //clears the reply, error must be sent first or will not be able to extract any info from it
                        clearReplyAndHideViews()

                        Toast.makeText(
                            applicationContext,
                            R.string.chat_room_fragment_error_sending_reply_message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } else { //invalid sentByAccountOID
                val errorMessage =
                    "sentByAccountOID or replyType was invalid when trying to set up a reply to a message.\n" +
                            "passedReplyMessageInfoObject.chatRoomId: ${passedReplyMessageInfoObject.chatRoomId}\n" +
                            "passedReplyMessageInfoObject.sentByAccountOID: ${passedReplyMessageInfoObject.sentByAccountOID}\n" +
                            "passedReplyMessageInfoObject.messageBeingRepliedToUUID: ${passedReplyMessageInfoObject.messageBeingRepliedToUUID}\n" +
                            "passedReplyMessageInfoObject.replyType: ${passedReplyMessageInfoObject.replyType}\n" +
                            "passedReplyMessageInfoObject.messageText: ${passedReplyMessageInfoObject.messageText}\n" +
                            "passedReplyMessageInfoObject.mimeType: ${passedReplyMessageInfoObject.mimeType}\n" +
                            "passedReplyMessageInfoObject.imageFilePath: ${passedReplyMessageInfoObject.imageFilePath}\n" +
                            "passedReplyMessageInfoObject.byteArray.size: ${passedReplyMessageInfoObject.byteArray.size}\n"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //clears the reply, error must be sent first or will not be able to extract any info from it
                clearReplyAndHideViews()

                Toast.makeText(
                    applicationContext,
                    R.string.chat_room_fragment_error_sending_reply_message,
                    Toast.LENGTH_SHORT
                ).show()
            }

        } else { //if invalid or unset mongoDB OID

            clearReplyAndHideViews()

            Toast.makeText(
                applicationContext,
                R.string.chat_room_fragment_message_must_be_sent_to_server_reply,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteMessage(
        deleteMessageInfo: DeleteMessageInfoObject,
    ) {
        if (deleteMessageInfo.possibleDeleteTypes != DeleteMessageTypes.CAN_ONLY_DELETE_FOR_SELF
            && deleteMessageInfo.possibleDeleteTypes != DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS
        ) { //if possible delete types is invalid

            val errorMessage =
                "An invalid DeleteMessageTypes was passed when attempting to delete a message.\n" +
                        "deleteMessageInfo: ${deleteMessageInfo}\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            Toast.makeText(
                applicationContext,
                R.string.chat_room_fragment_error_sending_delete_message,
                Toast.LENGTH_SHORT
            ).show()

        } else { //if info valid

            val setMessageToDeleted: () -> Int = {
                //NOTE: by here, newMessageText is expected to be more than whitespace, not be empty and have changed from the
                // previous message
                var messageIndex = -1

                for (i in sharedApplicationViewModel.chatRoomContainer.messages.indices) {
                    if (deleteMessageInfo.deletedMessageUUIDPrimaryKey == sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey) {
                        messageIndex = i

                        //update instance of message that is read by the chat room fragment and chat message adapter
                        break
                    }
                }

                if (messageIndex != -1) {
                    sharedApplicationViewModel.chatRoomContainer.messages[messageIndex].messageDataEntity.deletedType =
                        TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE
                    recyclerViewAdapter?.notifyItemChanged(messageIndex)
                } else {
                    val errorMessage =
                        "Message was not found immediately after it was passed to deleteMessage().\n" +
                                "deleteMessageInfo: ${deleteMessageInfo}\n"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    Toast.makeText(
                        applicationContext,
                        R.string.chat_room_fragment_error_sending_delete_message,
                        Toast.LENGTH_SHORT
                    ).show()

                }

                messageIndex
            }

            if (deleteMessageInfo.showDialogs) {
                if (deleteMessageInfo.possibleDeleteTypes == DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS) {

                    val options = arrayOf(
                        resources.getString(R.string.chat_room_fragment_delete_message_for_all_users),
                        resources.getString(R.string.chat_room_fragment_delete_message_for_self),
                        resources.getString(R.string.Cancel)
                    )

                    val alertDialog = DeleteMessageForAllAlertDialogFragment(
                        options
                    ) { dialog, position: Int ->
                        when (position) {
                            0 -> {
                                val indexValue = setMessageToDeleted()

                                if (indexValue != -1)
                                    sendMessageDeletedMessage(
                                        deleteMessageInfo.deletedMessageUUIDPrimaryKey,
                                        TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS
                                    )
                            }
                            1 -> {
                                val indexValue = setMessageToDeleted()

                                if (indexValue != -1)
                                    sendMessageDeletedMessage(
                                        deleteMessageInfo.deletedMessageUUIDPrimaryKey,
                                        TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_SINGLE_USER
                                    )
                            }
                            else -> {
                                dialog.dismiss()
                            }
                        }
                    }

                    alertDialog.show(childFragmentManager, "fragment_delete_msg_for_all")

                } else { //user can only delete message for self

                    val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                        resources.getString(R.string.chat_room_fragment_permanently_delete_message_check_title),
                        resources.getString(R.string.chat_room_fragment_permanently_delete_message_check),
                    ) { _: DialogInterface, _: Int ->

                        val indexValue = setMessageToDeleted()

                        if (indexValue != -1)
                            sendMessageDeletedMessage(
                                deleteMessageInfo.deletedMessageUUIDPrimaryKey,
                                TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_SINGLE_USER
                            )
                    }

                    alertDialog.show(childFragmentManager, "fragment_delete_msg_for_self")

                }
            } else { //do not show dialogs

                val deleteType =
                    if (deleteMessageInfo.possibleDeleteTypes == DeleteMessageTypes.CAN_DELETE_FOR_ALL_USERS) {
                        TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_ALL_USERS
                    } else {
                        TypeOfChatMessageOuterClass.DeleteType.DELETE_FOR_SINGLE_USER
                    }

                val indexValue = setMessageToDeleted()

                if (indexValue != -1)
                    sendMessageDeletedMessage(
                        deleteMessageInfo.deletedMessageUUIDPrimaryKey,
                        deleteType
                    )
            }
        }
    }

    private fun setReplyAndShowViews(
        showImageView: Boolean,
        passedReplyMessageInfoObject: ReplyMessageInfoObject,
        saveReply: Boolean,
    ) {

        if (saveReply) {
            sharedApplicationViewModel.chatRoomContainer.setReply(passedReplyMessageInfoObject)
        }

        binding.chatRoomCloseReplyImageView.visibility = View.VISIBLE
        binding.chatRoomMessagesVerticalLineView.visibility = View.VISIBLE
        binding.chatRoomTextReplyInclude.chatMessageListItemReplyRelativeLayout.visibility =
            View.VISIBLE

        //never show indentation line for reply
        binding.chatRoomTextReplyInclude.chatRoomMessagesIndentationLineView.visibility = View.GONE

        if (showImageView) {
            binding.chatRoomTextReplyInclude.chatMessageListItemReplyImageView.visibility =
                View.VISIBLE
        } else {
            binding.chatRoomTextReplyInclude.chatMessageListItemReplyImageView.visibility =
                View.GONE
        }

    }

    private fun clearReplyAndHideViews() {

        //This will guarantee that the binding is still alive if this is called from
        // a coRoutine.
        val bindingCopy = _binding
        val fragment = this

        if (bindingCopy != null) {
            bindingCopy.chatRoomCloseReplyImageView.visibility =
                View.GONE
            bindingCopy.chatRoomMessagesVerticalLineView.visibility =
                View.GONE
            bindingCopy.chatRoomTextReplyInclude.chatMessageListItemReplyRelativeLayout.visibility =
                View.GONE

            CoroutineScope(Dispatchers.Main).launch {
                //an exception can be thrown stating "You can't start or clear loads in RequestListener or Target callbacks."
                // if this is called from inside another glide context
                GlideApp.with(fragment)
                    .load(GlobalValues.defaultPictureResourceID)
                    .error(GlobalValues.defaultPictureResourceID)
                    .into(bindingCopy.chatRoomTextReplyInclude.chatMessageListItemReplyImageView)
            }

            sharedApplicationViewModel.chatRoomContainer.clearReply()
        }
    }

    private fun checkIfReplyTypeMessage(): ReplyValuesForMessageDataEntity {

        val returnVal = ReplyValuesForMessageDataEntity()
        val replyMessageInfo = sharedApplicationViewModel.chatRoomContainer.replyMessageInfo

        if (replyMessageInfo.sentByAccountOID.isValidMongoDBOID()
            && replyMessageInfo.messageBeingRepliedToUUID.isValidUUIDKey()
            && replyMessageInfo.replyType != TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET
        ) { //mandatory values have been set for reply

            returnVal.isReply = true
            returnVal.replyIsSentFromOid = replyMessageInfo.sentByAccountOID
            returnVal.replyIsFromMessageUUID = replyMessageInfo.messageBeingRepliedToUUID
            returnVal.replyType = replyMessageInfo.replyType

            when (replyMessageInfo.replyType) {
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.TEXT_REPLY -> {
                    if (replyMessageInfo.messageText != "") {
                        returnVal.replyIsFromMessageText = replyMessageInfo.messageText
                    }
                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.MIME_REPLY -> {
                    if (replyMessageInfo.mimeType != "") {
                        returnVal.replyIsFromMimeType = replyMessageInfo.mimeType
                    } else { //if mime type is not set
                        returnVal.isReply = false
                        returnVal.replyIsSentFromOid = ""
                        returnVal.replyIsFromMessageUUID = ""
                        returnVal.replyType =
                            TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET

                        val errorMessage =
                            "Mime type value should always be set for MIME_REPLY type.\n" +
                                    "isReply: false\n" +
                                    "replyIsSentFromOid: ${returnVal.replyIsSentFromOid}\n" +
                                    "replyIsFromMessageUUID: ${returnVal.replyIsFromMessageUUID}\n" +
                                    "replyType: ${returnVal.replyType}\n" +
                                    "replyIsFromMessageText: ${returnVal.replyIsFromMessageText}\n" +
                                    "replyIsFromMimeType: ${returnVal.replyIsFromMimeType}\n" +
                                    "replyMessageInfo.chatRoomId: ${replyMessageInfo.chatRoomId}\n" +
                                    "replyMessageInfo.sentByAccountOID: ${replyMessageInfo.sentByAccountOID}\n" +
                                    "replyMessageInfo.messageBeingRepliedToUUID: ${replyMessageInfo.messageBeingRepliedToUUID}\n" +
                                    "replyMessageInfo.replyType: ${replyMessageInfo.replyType}\n" +
                                    "replyMessageInfo.messageText: ${replyMessageInfo.messageText}\n" +
                                    "replyMessageInfo.mimeType: ${replyMessageInfo.mimeType}\n" +
                                    "replyMessageInfo.imageFilePath: ${replyMessageInfo.imageFilePath}\n" +
                                    "replyMessageInfo.byteArray.size: ${replyMessageInfo.byteArray.size}\n"

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        //NOTE: this needs to be cleared to clear the byte array
                        sharedApplicationViewModel.chatRoomContainer.clearReply()

                        Toast.makeText(
                            applicationContext,
                            R.string.chat_room_fragment_error_sending_reply_message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.PICTURE_REPLY,
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.LOCATION_REPLY,
                TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.INVITE_REPLY,
                -> {
                }
                else -> {
                    returnVal.isReply = false
                    returnVal.replyIsSentFromOid = ""
                    returnVal.replyIsFromMessageUUID = ""
                    returnVal.replyType =
                        TypeOfChatMessageOuterClass.ReplySpecifics.ReplyBodyCase.REPLYBODY_NOT_SET

                    val errorMessage =
                        "Invalid ReplyBodyCase was passed when checking for a reply.\n" +
                                "isReply: false\n" +
                                "replyIsSentFromOid: ${returnVal.replyIsSentFromOid}\n" +
                                "replyIsFromMessageUUID: ${returnVal.replyIsFromMessageUUID}\n" +
                                "replyType: ${returnVal.replyType}\n" +
                                "replyIsFromMessageText: ${returnVal.replyIsFromMessageText}\n" +
                                "replyIsFromMimeType: ${returnVal.replyIsFromMimeType}\n" +
                                "replyMessageInfo.chatRoomId: ${replyMessageInfo.chatRoomId}\n" +
                                "replyMessageInfo.sentByAccountOID: ${replyMessageInfo.sentByAccountOID}\n" +
                                "replyMessageInfo.messageBeingRepliedToUUID: ${replyMessageInfo.messageBeingRepliedToUUID}\n" +
                                "replyMessageInfo.replyType: ${replyMessageInfo.replyType}\n" +
                                "replyMessageInfo.messageText: ${replyMessageInfo.messageText}\n" +
                                "replyMessageInfo.mimeType: ${replyMessageInfo.mimeType}\n" +
                                "replyMessageInfo.imageFilePath: ${replyMessageInfo.imageFilePath}\n" +
                                "replyMessageInfo.byteArray.size: ${replyMessageInfo.byteArray.size}\n"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    //NOTE: this needs to be cleared to clear the byte array
                    sharedApplicationViewModel.chatRoomContainer.clearReply()
                }
            }
        } else if (replyMessageInfo.byteArray.isNotEmpty()) {

            //NOTE: this needs to be cleared to clear the byte array
            sharedApplicationViewModel.chatRoomContainer.clearReply()
        }

        return returnVal
    }

    private fun notifyMessageSent(messageIndex: Int) {
        if (recyclerViewAdapter != null && (recyclerViewAdapter?.itemCount ?: 0) > messageIndex) {
            //NOTE: this had a bug that seemingly went away? it was throwing an exception
            // when being called from the repository
            Log.i(
                "chatMessageAdapter",
                "notifyMessageSent for index: $messageIndex messages.lastIndex: ${sharedApplicationViewModel.chatRoomContainer.messages.lastIndex}"
            )

            Log.i("chatMsgGlide", "notifyMessageSent()")
            recyclerViewAdapter?.notifyItemRangeInserted(messageIndex, 1)
            //recyclerViewAdapter.notifyItemInserted(messageIndex)
            binding.chatRoomChatRecyclerView.scrollToPosition(messageIndex)
            binding.chatRoomChatScrollToBottomImageView.visibility = View.GONE
        }
    }

    private fun sendTextMessage(textMessage: String) {

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {
            setUpStandardChatRoomMode()
        }

        val replyValuesForMessageDataEntity = checkIfReplyTypeMessage()

        val message = buildTextMessageMessageDataEntity(
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
            replyValuesForMessageDataEntity,
            textMessage
        )

        sharedApplicationViewModel.chatRoomContainer.messages.add(
            MessagesDataEntityWithAdditionalInfo(message)
        )
        val messageIndex = sharedApplicationViewModel.chatRoomContainer.messages.lastIndex

        binding.chatRoomSendMessageEditText.text = null

        notifyMessageSent(messageIndex)

        sharedApplicationViewModel.sendMessage(
            message,
            sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.byteArray,
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        clearReplyAndHideViews()
    }

    private fun sendLocationMessage() {

        if (locationMessageObject.selectLocationCurrentLocation == GlobalValues.defaultLocation) { //if a variable is not set up for the location message

            sharedApplicationViewModel.chatRoomContainer.clearChatRoomLocation()

            val errorMessage =
                "When attempting to send a location message, the location data was cleared before the message could be sent.\n" +
                        "selectLocationCurrentLocation: ${locationMessageObject.selectLocationCurrentLocation}\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            return
        }

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {
            setUpStandardChatRoomMode()
        }

        val replyValuesForMessageDataEntity = checkIfReplyTypeMessage()

        val message = buildLocationMessageMessageDataEntity(
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
            replyValuesForMessageDataEntity,
            locationMessageObject
        )

        sharedApplicationViewModel.chatRoomContainer.messages.add(
            MessagesDataEntityWithAdditionalInfo(message)
        )

        val messageIndex = sharedApplicationViewModel.chatRoomContainer.messages.lastIndex

        notifyMessageSent(messageIndex)

        sharedApplicationViewModel.sendMessage(
            message,
            sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.byteArray,
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        clearReplyAndHideViews()
    }

    private suspend fun updateAndSendPictureMessage(
        message: MessagesDataEntity,
        messageIndex: Int,
        pictureFilePath: String,
        pictureHeight: Int,
        pictureWidth: Int
    ) {

        Log.i(
            "gifCallback",
            "updateAndSendMimeTypeMessage, timestamp: ${message.messageStoredInDatabaseTime}"
        )

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {
            withContext(Dispatchers.Main) {
                setUpStandardChatRoomMode()
            }
        }

        message.apply {
            this.messageType =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.PICTURE_MESSAGE.number
            this.filePath = pictureFilePath
            this.imageHeight = pictureHeight
            this.imageWidth = pictureWidth
            this.amountOfMessage =
                TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO_VALUE
            this.hasCompleteInfo = true
        }

        if ((recyclerViewAdapter?.itemCount ?: 0) > messageIndex) {
            //NOTE: the recycler view does not need to be scrolled because it was already set up before this function was called
            withContext(Dispatchers.Main) {
                recyclerViewAdapter?.notifyItemChanged(messageIndex)
            }
        }

        sharedApplicationViewModel.sendMessage(
            message,
            sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.byteArray,
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        withContext(Dispatchers.Main) {
            clearReplyAndHideViews()
        }
    }

    private suspend fun updateAndSendMimeTypeMessage(
        message: MessagesDataEntity,
        messageIndex: Int,
        replyByteArray: ByteArray,
        mimeTypeUrl: String,
        mimeTypeFilePath: String,
        mimeTypeWidth: Int,
        mimeTypeHeight: Int,
        mimeTypeValue: String,
        mimeTypeExists: Boolean
    ) {

        Log.i(
            "gifCallback",
            "updateAndSendMimeTypeMessage, timestamp: ${message.messageStoredInDatabaseTime}"
        )

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {
            withContext(Dispatchers.Main) {
                setUpStandardChatRoomMode()
            }
        }

        message.apply {
            this.messageType =
                TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.MIME_TYPE_MESSAGE.number
            this.messageText = mimeTypeValue
            this.downloadUrl = mimeTypeUrl
            this.imageHeight = mimeTypeHeight
            this.imageWidth = mimeTypeWidth
            this.amountOfMessage =
                TypeOfChatMessageOuterClass.AmountOfMessage.COMPLETE_MESSAGE_INFO_VALUE
            this.hasCompleteInfo = true
        }

        if (!mimeTypeExists) { //if gif does not exist in database yet
            sharedApplicationViewModel.chatRoomContainer.mimeTypesFilePaths[mimeTypeUrl] =
                MimeTypeHolderObject(
                    mimeTypeFilePath,
                    mimeTypeWidth,
                    mimeTypeHeight,
                    mimeTypeValue
                )
        }

        if ((recyclerViewAdapter?.itemCount ?: 0) > messageIndex) {
            //NOTE: the recycler view does not need to be scrolled because it was already set up before this function was called
            withContext(Dispatchers.Main) {
                recyclerViewAdapter?.notifyItemChanged(messageIndex)
            }
        }

        sharedApplicationViewModel.sendMimeTypeMessage(
            message,
            mimeTypeFilePath,
            mimeTypeWidth,
            mimeTypeHeight,
            mimeTypeValue,
            replyByteArray,
            thisFragmentInstanceID
        )

        //NOTE: OnReceiveContentListener will call clearReplyAndHideViews() before calling this function, so it does not need to be called here.

    }

    private fun sendInviteMessage() {

        if (!inviteMessageObject.inviteMessageChatRoomBasicInfo.chatRoomId.isValidChatRoomId()
            || !inviteMessageObject.inviteMessageUserOid.isValidMongoDBOID()
        ) { //if a variable is not set up for the invite message (NOTE: the other variable CAN be empty)

            sharedApplicationViewModel.chatRoomContainer.clearChatRoomInvite()

            val errorMessage =
                "When attempting to send an invite message, some of the invite data was cleared before the invite was sent.\n" +
                        "inviteMessageChatRoomBasicInfo: ${inviteMessageObject.inviteMessageChatRoomBasicInfo}\n" +
                        "inviteMessageUserOid: ${inviteMessageObject.inviteMessageUserOid}\n" +
                        "inviteMessageUserName: ${inviteMessageObject.inviteMessageUserName}\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            return
        }

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {
            setUpStandardChatRoomMode()
        }

        val replyValuesForMessageDataEntity = checkIfReplyTypeMessage()

        val message = buildInviteMessageMessageDataEntity(
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
            replyValuesForMessageDataEntity,
            inviteMessageObject,
        )

        sharedApplicationViewModel.chatRoomContainer.messages.add(
            MessagesDataEntityWithAdditionalInfo(message)
        )

        val messageIndex = sharedApplicationViewModel.chatRoomContainer.messages.lastIndex
//        recyclerViewAdapter.notifyItemInserted(messages.lastIndex)
//        binding.chatRoomChatRecyclerView.scrollToPosition(messages.lastIndex)

        notifyMessageSent(messageIndex)

        sharedApplicationViewModel.sendMessage(
            message,
            sharedApplicationViewModel.chatRoomContainer.replyMessageInfo.byteArray,
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        clearReplyAndHideViews()
    }

    private fun sendMessageEditedMessage(
        modifiedMessageUUIDPrimaryKey: String,
        newMessageText: String,
    ) {

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {

            val errorMessage =
                "A text message existed to be edited before the chat room was taken out of 'MATCH_MADE_CHAT_ROOM_MODE'.\n" +
                        "modifiedMessageUUIDPrimaryKey: $modifiedMessageUUIDPrimaryKey\n" +
                        "newMessageText: $newMessageText\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            return
        }

        val message = buildEditedMessageMessageDataEntity(
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
            newMessageText,
            modifiedMessageUUIDPrimaryKey,
        )

        //NOTE: not adding this to the recycler view on purpose, the edited message was already updated

        sharedApplicationViewModel.sendMessage(
            message,
            byteArrayOf(),
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        clearReplyAndHideViews()
    }

    private fun sendMessageDeletedMessage(
        modifiedMessageUUIDPrimaryKey: String,
        deleteType: TypeOfChatMessageOuterClass.DeleteType,
    ) {

        //if this chat room is still 'match made' mode, swap it when a message is manually sent
        if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE) {

            val errorMessage =
                "A text message existed to be deleted before the chat room was taken out of 'MATCH_MADE_CHAT_ROOM_MODE'.\n" +
                        "modifiedMessageUUIDPrimaryKey: $modifiedMessageUUIDPrimaryKey\n" +
                        "deleteType: $deleteType\n"

            storeErrorChatRoomFragment(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            return
        }

        val message = buildDeletedMessageMessageDataEntity(
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
            deleteType,
            modifiedMessageUUIDPrimaryKey
        )

        //NOTE: not adding this to the recycler view on purpose, the edited message was already updated

        sharedApplicationViewModel.sendMessage(
            message,
            byteArrayOf(),
            thisFragmentInstanceID
        )

        //this must go after the message is sent to keep the byteArray object referenced somewhere
        clearReplyAndHideViews()
    }

    private fun sendUpdateUserObservedChatRoom() {

        //NOTE: observed time has nothing to do with how the client requests messages from the server, that is why messageEdited
        // and messageDeleted (anything that does not update chatRoomLastActivityTime) do not need to be set

        if (
            fragmentIsRunning
            && sharedApplicationViewModel.chatRoomContainer.messages.isNotEmpty()
            && sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastObservedTime < sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastActivityTime
        ) { //if fragment is active (see fragmentIsRunning) and messages array has at least 1 message AND observed time requires updating

            val message = buildUpdateObservedTimeMessageDataEntity(
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastActivityTime
            )

            //NOTE: this is not stored because the messages do not need this user's observed times
            sharedApplicationViewModel.sendMessage(
                message,
                byteArrayOf(),
                thisFragmentInstanceID
            )
        }
    }

    private fun handleChatRoomInfoUpdated(result: UpdateChatRoomInfoResultsDataHolder) {

        if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId == result.message.chatRoomId
            && TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.forNumber(result.message.messageType) ==
            TypeOfChatMessageOuterClass.MessageSpecifics.MessageBodyCase.CHAT_ROOM_NAME_UPDATED_MESSAGE
        ) { //if the chat room matches the passed chat room
            applicationActivity?.setupActivityMenuBars?.setTopToolbarChatRoomName()
        }
    }

    private fun handleBlockReportChatRoomResult(result: BlockAndReportChatRoomResultsHolder) {

        if (recyclerViewAdapter != null && result.accountBlocked) { //if recycler view has been initialized and account was blocked

            if (result.unMatch) {
                applicationActivity?.handleLeaveChatRoom(
                    sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId,
                    navigateToMessengerFragmentResourceId
                )
            } else if (sharedApplicationViewModel.chatRoomContainer.messages.isNotEmpty()) {
                //remove any menus for the blocked user
                applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(result.accountOID)

                for (i in sharedApplicationViewModel.chatRoomContainer.messages.lastIndex downTo 0) {
                    if (sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.sentByAccountID == result.accountOID) {
                        //remove any menus for the blocked user's messages
                        applicationActivity?.chatRoomActiveMessagePopupMenu?.dismissForUUID(
                            sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey
                        )
                        recyclerViewAdapter?.notifyItemChanged(i)
                    }
                }
            }
        }
    }

    private fun mimeTypeIsValid(mimeType: String?): Boolean {

        for (acceptedMimeType in GlobalValues.server_imported_values.mimeTypesAcceptedByServerList) {
            if (ClipDescription.compareMimeTypes(mimeType, acceptedMimeType)) return true
        }

        return false
    }

    private fun setUpChatRoomViews() {

        if (sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID != ""
            && sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() > 0
        ) { //if this is a matching chat room with at least one member

            if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() != 1) {
                val errorMessage =
                    "The chat room is a match however it has more than 1 member inside of it\n" +
                            "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //can continue here
            }

            currentFragmentMode = FragmentMode.MATCH_MADE_CHAT_ROOM_MODE

            applicationActivity?.setupActivityMenuBars?.setupToolbarsChatRoomFragmentMatchMade()

            topToolBar?.findViewById<RelativeLayout>(R.id.toolbarApplicationTopLayoutRelativeLayout)
                ?.setOnClickListener(null)
            topToolBar?.isClickable = false

            binding.chatRoomChatRecyclerView.visibility = View.GONE
            binding.chatRoomFragmentMatchItem.root.visibility = View.VISIBLE
            binding.chatRoomSendMessageEditText.hint = "Introduce yourself..."

            setupMatchMadeOptionsMenu()

            val otherUser =
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(0)

            //set up user info
            if (otherUser != null) {

                //add this view to the root view temporarily to calculate the size the textView needs to be
                val tempMatchListItem =
                    View.inflate(
                        context,
                        R.layout.view_user_info_card_time_frame,
                        null
                    ) as LinearLayout
                binding.root.addView(tempMatchListItem)
                val tempDateItem =
                    tempMatchListItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStartTimeTextView)

                val widthMeasureSpec: Int =
                    View.MeasureSpec.makeMeasureSpec(
                        getScreenWidth(requireActivity()),
                        View.MeasureSpec.AT_MOST
                    )
                val heightMeasureSpec: Int =
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                tempDateItem.measure(widthMeasureSpec, heightMeasureSpec)
                matchListItemDateTimeTextViewWidth = tempDateItem.measuredWidth
                matchListItemDateTimeTextViewHeight = tempDateItem.measuredHeight
                binding.root.removeView(tempMatchListItem)

                userInfoCardLogic = UserInfoCardLogic(
                    requireContext().applicationContext,
                    GlideApp.with(this),
                    true,
                    binding.chatRoomFragmentMatchItem.root,
                    deviceScreenWidth,
                    sharedApplicationViewModel.userAge,
                    sharedApplicationViewModel.categories,
                    matchListItemDateTimeTextViewWidth,
                    matchListItemDateTimeTextViewHeight,
                    childFragmentManager,
                    requestUpdateSingleUser = { userAccountOID ->
                        sharedApplicationViewModel.updateSingleChatRoomMemberInfo(
                            userAccountOID
                        )
                    },
                    updateOtherUserToObserved = { userAccountOID ->
                        sharedApplicationViewModel.updateOtherUserObservedTime(
                            userAccountOID
                        )
                    },
                    errorStore
                )

                userInfoCardLogic?.initializeInfo(
                    otherUser.otherUsersDataEntity,
                    hideUserMatchingInfo = true
                )

            } else {
                val errorMessage =
                    "More than 1 member existed inside the chat room. However there was no index 0 to extract?\n" +
                            "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                Toast.makeText(
                    applicationContext,
                    R.string.chat_room_fragment_image_error_loading_chat_room,
                    Toast.LENGTH_LONG
                ).show()

                //Leave chat room has functionality built into it to handle if this is a matching chat room or not, so it will
                // check in on the server and handle it either way.
                applicationActivity?.setLoadingDialogState(true, LOADING_DIALOG_TIMEOUT_IN_MS)
                sharedApplicationViewModel.leaveChatRoomById(sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId)
            }

        } else { //if this is not matching chat room or is an empty matching chat room

            if (sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID != "") {
                val errorMessage =
                    "A 'MATCH_MADE_CHAT_ROOM_MODE' chat room was found containing no members.\n" +
                            "modifiedMessageUUIDPrimaryKey: ${sharedApplicationViewModel.chatRoomContainer.chatRoom}\n"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                Toast.makeText(
                    applicationContext,
                    R.string.chat_room_fragment_image_error_loading_chat_room,
                    Toast.LENGTH_LONG
                ).show()

                //Leave chat room has functionality built into it to handle if this is a matching chat room or not, so it will
                // check in on the server and handle it either way.
                applicationActivity?.setLoadingDialogState(true, LOADING_DIALOG_TIMEOUT_IN_MS)
                sharedApplicationViewModel.leaveChatRoomById(sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId)
            }

            setUpStandardChatRoomMode()
        }

    }

    private fun handleUpdateOtherUser(result: ReturnUpdatedOtherUserDataHolder) {
        if (result.index < sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()) {
            if (userInfoCardLogic != null
                && (result.otherUser.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
                        || result.otherUser.chatRoom.accountStateInChatRoom == AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN)
            ) { //this is only relevant if this is a match made chat room and the passed user is inside the chat room (leaving will be handled elsewhere)

                userInfoCardLogic?.initializationIfMatchingAccountOID(
                    result.otherUser.otherUsersDataEntity,
                    hideMatchingInfo = true
                )

                setNumberOfMembersInChatRoom()
            } else if (userInfoCardLogic == null
                && result.anExistingThumbnailWasUpdated
                && LoginFunctions.currentAccountOID != result.otherUser.otherUsersDataEntity.accountOID
            ) { //only relevant if a user's thumbnail was updated and was not current user

                val numMessages = binding.chatRoomChatRecyclerView.adapter?.itemCount ?: 0

                //Update either all messages up to the first 1000, this is done so that if a large number of messages
                // are present it will not delay too much. There is the problem that if the user is viewing an old enough
                // message, they will not see the update. However this situation should be rare.
                val offset = if (numMessages > 1000) 1000 else numMessages

                for (i in numMessages - 1 downTo numMessages - offset) {
                    val holder =
                        binding.chatRoomChatRecyclerView.findViewHolderForAdapterPosition(i)
                    if (holder != null
                        && holder is ChatMessageAdapter.ChatMessageViewHolder
                    ) {
                        holder.updateThumbnailPictureIfCorrectUserOID(result.otherUser)
                    }
                }
            }
        }
    }

    private fun setUpStandardChatRoomMode() {

        currentFragmentMode = FragmentMode.STANDARD_CHAT_ROOM_MODE

        applicationActivity?.setupActivityMenuBars?.setupToolbarsChatRoomFragments()

        applicationActivity?.setupActivityMenuBars?.setTopToolbarChatRoomName()
        setNumberOfMembersInChatRoom()

        setUpTopToolbarOnClickListener()

        binding.chatRoomChatRecyclerView.visibility = View.VISIBLE
        binding.chatRoomFragmentMatchItem.root.visibility = View.GONE
        binding.chatRoomSendMessageEditText.hint = "Message"

        setupStandardOptionsMenu()
    }

    private fun setUpTopToolbarOnClickListener() {
        //NOTE: want to run under the assumption that the messages have been processed before the user can navigate to the info fragment
        //not accessing this from the activity to avoid holding onto a reference
        val toolbarLayout =
            topToolBar?.findViewById<RelativeLayout>(R.id.toolbarApplicationTopLayoutRelativeLayout)

        toolbarLayout?.setOnClickListener(null)
        toolbarLayout?.setSafeOnClickListener {

            it.setOnClickListener(null)
            applicationActivity?.hideMenus()
            applicationActivity?.navigate(
                R.id.chatRoomFragment,
                R.id.action_chatRoomFragment_to_chatRoomInfoFragment
            )
        }
    }

    private fun setupStandardOptionsMenu() {

        Log.i("setup_menu_s", "setupStandardOptionsMenu()")

        applicationActivity?.setupActivityMenuBars?.addMenuProviderWithMenuItems(
            viewLifecycleOwner,
            leaveChatRoomLambda = {
                val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                    resources.getString(R.string.chat_room_general_double_check_leave_chat_room_title),
                    resources.getString(R.string.chat_room_general_double_check_leave_chat_room_message)
                ) { _: DialogInterface, _: Int ->
                    applicationActivity?.setLoadingDialogState(
                        true,
                        LOADING_DIALOG_TIMEOUT_IN_MS
                    )
                    sharedApplicationViewModel.leaveCurrentChatRoom()
                }

                alertDialog.show(
                    childFragmentManager,
                    "chat_fragment_double_check_leave_chat_room"
                )
            },
            clearHistoryLambda = {
                val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                    resources.getString(R.string.chat_room_general_double_check_clear_history_title),
                    resources.getString(R.string.chat_room_general_double_check_clear_history_message)
                ) { _: DialogInterface, _: Int ->
                    applicationActivity?.setLoadingDialogState(
                        true,
                        LOADING_DIALOG_TIMEOUT_IN_MS
                    )
                    sharedApplicationViewModel.clearHistoryFromChatRoom(thisFragmentInstanceID)
                }

                alertDialog.show(
                    childFragmentManager,
                    "chat_fragment_double_check_clear_history"
                )
            },
            fragmentInstanceID = thisFragmentInstanceID
        )
    }

    private fun setupMatchMadeOptionsMenu() {

        Log.i("setup_menu_s", "setupMatchMadeOptionsMenu()")

        applicationActivity?.setupActivityMenuBars?.addMenuProviderWithMenuItems(
            viewLifecycleOwner,
            unMatchLambda = {
                if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() > 0) { //if chat room has at least 1 member

                    if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() != 1) {
                        var errorMessage =
                            "Chat room that is being un matched should only have one member.\n" +
                                    "chatRoomId: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId}\n" +
                                    "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n"

                        for (i in 0 until sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()) {
                            errorMessage += "chatRoomMember $i: ${
                                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                                    i
                                )?.otherUsersDataEntity?.accountOID
                            }"
                        }

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        //OK to continue here
                    }

                    sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                        0
                    )?.let { otherUsersInfo ->
                        val alertDialog = BasicAlertDialogFragmentWithRoundedCorners(
                            resources.getString(R.string.chat_room_general_double_check_un_match_title),
                            resources.getString(R.string.chat_room_fragment_double_check_un_match_message)
                        ) { _: DialogInterface, _: Int ->
                            applicationActivity?.setLoadingDialogState(
                                true,
                                LOADING_DIALOG_TIMEOUT_IN_MS
                            )
                            sharedApplicationViewModel.unMatchChatRoom(
                                otherUsersInfo.otherUsersDataEntity.accountOID
                            )
                        }

                        alertDialog.show(
                            childFragmentManager,
                            "fragment_double_check_un_match"
                        )
                    }
                }
            },
            blockReportLambda = {
                if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() > 0) { //if chat room has at least 1 member

                    if (sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() != 1) {
                        var errorMessage =
                            "Chat room that is type 'MATCH_MADE_CHAT_ROOM_MODE' has more than one other member inside of it.\n" +
                                    "chatRoomId: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId}\n" +
                                    "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n"

                        for (i in 0 until sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()) {
                            errorMessage += "chatRoomMember $i: ${
                                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                                    i
                                )?.otherUsersDataEntity?.accountOID
                            }"
                        }

                        storeErrorChatRoomFragment(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }

                    sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(
                        0
                    )?.let { otherUsersInfo ->
                        applicationActivity?.blockAndReportUserFromChatRoom(
                            childFragmentManager,
                            otherUsersInfo.otherUsersDataEntity.accountOID,
                            true,
                            ReportMessages.ReportOriginType.REPORT_ORIGIN_CHAT_ROOM_MATCH_MADE,
                            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId
                        )
                    }
                }
            },
            fragmentInstanceID = thisFragmentInstanceID,
        )
    }

    private fun setNumberOfMembersInChatRoom() {
        applicationActivity?.setNumberOfMembersInChatRoom()
    }

    //NOTE: error status was handled inside view model, it should always be NO_ERRORS by this point
    //possibly called when a user clicks 'Join Chat Room' on an invite message (leads to handleClickOnChatRoomResult())
    private fun handleJoinChatRoom(result: JoinChatRoomReturnValues) {

        when (result.chatRoomStatus) {
            ChatRoomCommands.ChatRoomStatus.ALREADY_IN_CHAT_ROOM -> {

                val errorMessage =
                    "ChatRoomStatus.ALREADY_IN_CHAT_ROOM made it back to the ChatRoomFragment, this should never happen.\n" +
                            "chatRoomId: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId}\n"

                storeErrorChatRoomFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //Can continue by calling SUCCESSFULLY_JOINED
                sharedApplicationViewModel.retrieveSingleChatRoom(
                    result.chatRoomId,
                    thisFragmentInstanceID,
                    chatRoomMustExist = true
                )
            }
            ChatRoomCommands.ChatRoomStatus.SUCCESSFULLY_JOINED -> {
                //NOTE: At this point, chat room was already set up inside application view model.
                sharedApplicationViewModel.retrieveSingleChatRoom(
                    result.chatRoomId,
                    thisFragmentInstanceID,
                    chatRoomMustExist = true
                )
            }
            ChatRoomCommands.ChatRoomStatus.ACCOUNT_WAS_BANNED,
            ChatRoomCommands.ChatRoomStatus.CHAT_ROOM_DOES_NOT_EXIST,
            ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_ID,
            ChatRoomCommands.ChatRoomStatus.INVALID_CHAT_ROOM_PASSWORD,
            ChatRoomCommands.ChatRoomStatus.USER_TOO_YOUNG_FOR_CHAT_ROOM,
            ChatRoomCommands.ChatRoomStatus.UNRECOGNIZED,
            -> {

                applicationActivity?.setLoadingDialogState(false)

                var indexToBeUpdated = -1

                for (i in sharedApplicationViewModel.chatRoomContainer.messages.indices) {
                    if (sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.messageUUIDPrimaryKey == result.uuidPrimaryKey) {
                        sharedApplicationViewModel.chatRoomContainer.messages[i].messageDataEntity.inviteExpired =
                            true
                        indexToBeUpdated = i
                        break
                    }
                }

                if (indexToBeUpdated != -1) {
                    recyclerViewAdapter?.notifyItemChanged(indexToBeUpdated)
                } else {
                    //may be able to happen if a message was deleted? or if history was cleared?
                    val errorMessage =
                        "A message was returned that does not exist inside the messages list.\n" +
                                "chatRoomContainer.messages.size: ${sharedApplicationViewModel.chatRoomContainer.messages.size}\n" +
                                "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n"

                    storeErrorChatRoomFragment(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }

                Toast.makeText(
                    requireContext(),
                    R.string.chat_room_fragment_invite_expired,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //possibly called when a user clicks 'Join Chat Room' on an invite message (see also handleJoinChatRoom())
    private fun handleClickOnChatRoomResult() {

        //NOTE: updateChatRoom() must be run even if this call comes from joinChatRoom(). This is
        // because joinChatRoom() only sends back minimal data for a lot of things.
        sharedApplicationViewModel.updateChatRoom()

        //clear the reply, location and invite before navigating to self
        sharedApplicationViewModel.chatRoomContainer.clearBetweenChatRoomFragmentInfo()

        applicationActivity?.setLoadingDialogState(false)

        applicationActivity?.navigate(R.id.chatRoomFragment, R.id.action_chatRoomFragment_self)
    }

    private fun handleAccountStateUpdated(accountStateInfo: AccountStateUpdatedDataHolder) {

        if (accountStateInfo.updatedAccountOID == LoginFunctions.currentAccountOID) { //if admin was changed and it involves this user

            //the only 2 transitions this account can be in is ACCOUNT_STATE_IS_ADMIN -> ACCOUNT_STATE_IN_CHAT_ROOM
            // and ACCOUNT_STATE_IN_CHAT_ROOM -> ACCOUNT_STATE_IS_ADMIN
            //hide menus because options will have changed for the user
            applicationActivity?.hideMenus()
        } else {

            //dismiss menu if opened for passed account OID
            applicationActivity?.adminUserOptionsFragmentPopupMenu?.dismissForAccountOID(
                accountStateInfo.updatedAccountOID
            )
        }
    }

    private fun startInviteMember(accountOID: String, userName: String) {
        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserOid =
            accountOID
        sharedApplicationViewModel.chatRoomContainer.inviteMessageObject.inviteMessageUserName =
            userName
        applicationActivity?.navigate(
            R.id.chatRoomFragment,
            R.id.action_chatRoomFragment_to_selectChatRoomForInviteFragment
        )
    }

    private fun updateMessageInRecyclerViewToDeleted(messageIndex: Int) {

        if (messageIndex < sharedApplicationViewModel.chatRoomContainer.messages.size) {
            sharedApplicationViewModel.chatRoomContainer.messages[messageIndex].messageDataEntity.deletedType =
                TypeOfChatMessageOuterClass.DeleteType.DELETED_ON_CLIENT_VALUE

            recyclerViewAdapter?.notifyItemChanged(
                messageIndex
            )
        }
    }

    private fun setScrollState() {
        if (!binding.chatRoomChatRecyclerView.canScrollVertically(1)
        //&& newState == RecyclerView.SCROLL_STATE_IDLE
        ) {
            binding.chatRoomChatScrollToBottomImageView.visibility = View.GONE
        } else {
            binding.chatRoomChatScrollToBottomImageView.visibility = View.VISIBLE
        }
    }

    private fun scrollToBottomOfRecyclerView() {
        binding.chatRoomChatRecyclerView.scrollToPosition(sharedApplicationViewModel.chatRoomContainer.messages.size - 1)
        binding.chatRoomChatScrollToBottomImageView.visibility = View.GONE
    }

    private fun storeErrorChatRoomFragment(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String
    ) {
        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            passedErrMsg
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onStart() {
        super.onStart()

        Log.i("ApplicationActStuff", "ChatRoomFragment onStart()")

        if (currentFragmentMode != FragmentMode.MATCH_MADE_CHAT_ROOM_MODE
            || sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID == ""
        ) { //If fragment should not be in MATCH_MADE_CHAT_ROOM_MODE.

            //update top bar if anything changed while minimized
            //update the recycler view adapter
            if (recyclerViewAdapter != null) {

                applicationActivity?.setupActivityMenuBars?.setupToolbarsChatRoomFragments()
                recyclerViewAdapter?.notifyDataSetChanged()

                setUpTopToolbarOnClickListener()

                if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE
                    && sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID == ""
                ) {
                    setUpStandardChatRoomMode()
                } else if (currentFragmentMode == FragmentMode.STANDARD_CHAT_ROOM_MODE) {
                    setUpStandardChatRoomMode()
                }

                if (scrollingCurrentlyAtBottom) {
                    scrollToBottomOfRecyclerView()
                }
            } else if (
                sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomFragmentInitializationCallbackReceived
            ) { //if !recyclerViewInitialized && a message has been returned (first message list is from sharedApplicationViewModel.retrieveMessagesForChatRoomId())

                if (currentFragmentMode == FragmentMode.MATCH_MADE_CHAT_ROOM_MODE
                    && sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID == ""
                ) {
                    setUpStandardChatRoomMode()
                }

                //this could happen if sharedApplicationViewModel.retrieveMessagesForChatRoomId() is called and then the app is immediately minimized
                // before the return value can be handled causing this fragment to miss the first message (the SendChatMessagesToFragments class won't
                // send things back to this fragment when the app is minimized)
                recyclerViewInitialization()
            }
        }

        //used in making sure the chat room can get updates if app is minimized
        //make sure to do this after notifyDataSetChanged(), or this could attempt to modify data that has
        // not been inserted into the recycler view
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomFragmentOnStart()
    }

    override fun onStop() {

        //used in making sure the chat room can get updates if app is minimized
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomFragmentOnStop()

        if (recyclerViewAdapter != null) {
            scrollingCurrentlyAtBottom = !binding.chatRoomChatRecyclerView.canScrollVertically(1)
        }

        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        Log.i("ApplicationActStuff", "ChatRoomFragment onResume()")

        fragmentIsRunning = true

        binding.chatRoomSendPictureImageView.isEnabled = true

        //this is because when the fragment is paused, messages will be responded to as
        // 'observed' and so this will handle that
        sendUpdateUserObservedChatRoom()
    }

    override fun onPause() {

        //HashSet will create a copy of the set so it is not cleared after the Coroutine is launched
        sharedApplicationViewModel.updateMessagesObservedTimes(HashSet(observedMessages))
        observedMessages.clear()

        //HashSet will create a copy of the set so it is not cleared after the Coroutine is launched
        sharedApplicationViewModel.updateMimeTypesObservedTimes(HashSet(observedMimeTypeUrls))
        observedMimeTypeUrls.clear()

        fragmentIsRunning = false

        super.onPause()
    }

    override fun onDestroyView() {

        Log.i("ApplicationActStuff", "ChatRoomFragment onDestroyView()")
        //These must be cleared because the onClickListeners will stop lambdas referencing this view
        applicationActivity?.hideMenus()

        //NOTE: when calling navigate(R.id.action_chatRoomFragment_self) onDestroyView for the last fragment is
        // called AFTER onCreateView for this fragment, this means don't do anything like setting things outside this fragment instance such as
        // setting the topToolbar onClickListener to null
        recyclerViewAdapter = null

        applicationActivity?.removeLambdaCalledAtEndOfOnCreate(thisFragmentInstanceID)

        _binding = null
        applicationActivity = null

        //do this before the lists are cleared (on the Main thread) so that they will stay empty
        sharedApplicationViewModel.sendChatMessagesToFragments.chatRoomFragmentOnDestroyView(
            thisFragmentInstanceID
        )

        sharedApplicationViewModel.chatRoomContainer.messages.clear()
        sharedApplicationViewModel.chatRoomContainer.mimeTypesFilePaths.clear()

        //NOTE: don't clear sharedApplicationViewModel.chatRoom because the members list will be used in other places
        currentFragmentMode = FragmentMode.UNSET
        topToolBar = null
//        optionsMenu = null

        userInfoCardLogic = null

        locationMessageObject = LocationSelectedObject()
        inviteMessageObject = InviteMessageObject()

        super.onDestroyView()

        //Sometimes when clearReplyAndHideViews() is called (from a lambda such as receivePictureResult)
        // the GlideApp.with(this) will cause the view to leak into ChatRoomInfoFragment. The onDestroy() should
        // prevent that.  If Glide is not initialized for this fragment then an IllegalStateException will
        // be thrown when onDestroy() is called.
        try {
            GlideApp.with(this).onDestroy()
        } catch (_: IllegalStateException) {
        }
    }

    override fun onDestroy() {

        Log.i("ApplicationActStuff", "ChatRoomFragment onDestroy()")

        //This is set on fragment initialization, it cannot be destroyed with the view.
        receivePictureResult = null

        super.onDestroy()
    }
}