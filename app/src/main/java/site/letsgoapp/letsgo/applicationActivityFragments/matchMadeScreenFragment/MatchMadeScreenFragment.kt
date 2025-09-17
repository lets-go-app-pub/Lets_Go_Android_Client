package site.letsgoapp.letsgo.applicationActivityFragments.matchMadeScreenFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.request.RequestOptions
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersInfo
import site.letsgoapp.letsgo.databinding.FragmentMatchMadeScreenBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import user_account_type.UserAccountTypeOuterClass.UserAccountType

class MatchMadeScreenFragment : Fragment() {

    private var _binding: FragmentMatchMadeScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>

    private var otherUser: OtherUsersInfo? = null

    private var applicationActivity: AppActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentMatchMadeScreenBinding.inflate(inflater, container, false)

        binding.fragmentMatchMadeScreenThumbnailInclude.matchLayoutPictureCardView.radius = resources.getDimension(R.dimen.half_user_picture_match_made_size)

        applicationActivity = requireActivity() as AppActivity

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        otherUser = when {
            sharedApplicationViewModel.chatRoomContainer.chatRoom.eventId.isValidMongoDBOID() -> {
                binding.fragmentMatchMadeScreenTitleImageView.setImageResource(R.drawable.kashie_mercy_event_joined)
                var event: OtherUsersInfo? = null
                for (i in (0..sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size())) {
                    val member = sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(i)
                    if (member != null && member.otherUsersDataEntity.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number) {
                        event = member
                        break
                    }
                }
                event
            }
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() == 1 -> {
                binding.fragmentMatchMadeScreenTitleImageView.setImageResource(R.drawable.kashie_mercy_match_found)
                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(0)
            }
            sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size() != 0 -> {
                val errorMessage =
                    "Too many members inside chat room that should be a recent match (should have exactly 1).\n"

                storeErrorMatchMadeFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.getFromList(0)
            }
            else -> {

                val errorMessage =
                    "No members inside chat room that should be a recent match (should have exactly 1).\n"

                storeErrorMatchMadeFragment(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                GlideApp.with(requireContext())
                    .load(sharedApplicationViewModel.firstPictureInList.picturePath)
                    .error(GlobalValues.defaultPictureResourceID)
                    .signature(generateFileObjectKey(sharedApplicationViewModel.firstPictureInList.pictureTimestamp))
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.fragmentMatchMadeScreenThumbnailInclude.matchLayoutPictureImageView)

                null
            }
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fragmentMatchMadeScreenThumbnailInclude.matchLayoutPictureImageView.setSafeOnClickListener {
            sharedApplicationViewModel.chatRoomContainer.clearBetweenChatRoomFragmentInfo()
            applicationActivity?.navigate(
                R.id.matchMadeScreenFragment,
                R.id.action_matchMadeScreenFragment_to_chatRoomFragment
            )
        }

        otherUser?.let {

            var pictureFoundAndLoaded = false
            for (pic in it.picturesInfo) {

                //set picture
                if (pic.picturePath != "" && pic.picturePath != "~") {

                    pictureFoundAndLoaded = true

                    GlideApp.with(requireContext())
                        .load(pic.picturePath)
                        .error(GlobalValues.defaultPictureResourceID)
                        .signature(generateFileObjectKey(pic.timestampPictureLastUpdatedOnServer))
                        .apply(RequestOptions.circleCropTransform())
                        .into(binding.fragmentMatchMadeScreenThumbnailInclude.matchLayoutPictureImageView)

                    break
                }
            }

            if (!pictureFoundAndLoaded) {

                //NOTE: This could happen if user had their pictures deleted.

                //set picture
                if (it.otherUsersDataEntity.thumbnailPath != "" && it.otherUsersDataEntity.thumbnailPath != "~") {

                    val signatureTimestamp = it.otherUsersDataEntity.thumbnailLastTimeUpdated

                    GlideApp.with(requireContext())
                        .load(it.otherUsersDataEntity.thumbnailPath)
                        .error(GlobalValues.defaultPictureResourceID)
                        .signature(generateFileObjectKey(signatureTimestamp))
                        .apply(RequestOptions.circleCropTransform())
                        .into(binding.fragmentMatchMadeScreenThumbnailInclude.matchLayoutPictureImageView)

                } //else { //This could happen if all of users' pictures were deleted. }

            }

            //set name
            if (it.otherUsersDataEntity.name != "" && it.otherUsersDataEntity.name != "~") {
                binding.fragmentMatchMadeScreenMatchNameTextView.text = it.otherUsersDataEntity.name
            }
        }

        binding.fragmentMatchMadeScreenSendMessageButton.setSafeOnClickListener {
            sharedApplicationViewModel.chatRoomContainer.clearBetweenChatRoomFragmentInfo()
            applicationActivity?.navigate(
                R.id.matchMadeScreenFragment,
                R.id.action_matchMadeScreenFragment_to_chatRoomFragment
            )
        }

        binding.fragmentMatchMadeScreenKeepSwipingButton.setSafeOnClickListener {
            applicationActivity?.navigate(
                R.id.matchMadeScreenFragment,
                R.id.action_matchMadeScreenFragment_to_matchScreenFragment
            )
        }

        returnLeaveChatRoomResultObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled()
            result?.let {
                handleLeaveChatRoom(it)
            }
        }

        sharedApplicationViewModel.returnLeaveChatRoomResult.observe(
            viewLifecycleOwner,
            returnLeaveChatRoomResultObserver
        )

        returnJoinedLeftChatRoomObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleJoinedLeftChatRoomReturn(it)
            }
        }

        sharedApplicationViewModel.returnJoinedLeftChatRoom.observe(
            viewLifecycleOwner,
            returnJoinedLeftChatRoomObserver
        )

        returnKickedBannedFromChatRoomObserver = Observer { wrapper ->
            val result =
                wrapper.getContentIfNotHandled(sharedApplicationViewModel.chatRoomContainer.chatRoomUniqueId)
            result?.let {
                handleKickedBannedFromChatRoom(it)
            }
        }

        sharedApplicationViewModel.returnKickedBannedFromChatRoom.observe(
            viewLifecycleOwner,
            returnKickedBannedFromChatRoomObserver
        )

    }

    override fun onStart() {
        super.onStart()
        //This is here for consistency with similar functions
        applicationActivity?.setupActivityMenuBars?.setupToolbarsMatchMadeFragment()
    }

    private fun handleJoinedLeftChatRoomReturn(info: ReturnJoinedLeftChatRoomDataHolder) {
        val chatRoom = info.chatRoomWithMemberMap

        when (info.chatRoomUpdateMadeEnum) {
            ChatRoomUpdateMade.CHAT_ROOM_LEFT -> {} //continue but don't show toast
            ChatRoomUpdateMade.CHAT_ROOM_MATCH_CANCELED -> {
                Toast.makeText(GlobalValues.applicationContext, "Match has been canceled.", Toast.LENGTH_SHORT)
                    .show()
                //ReasonForLeavingChatRoom.UN_MATCHED_FROM_CHAT_ROOM
            }
            ChatRoomUpdateMade.CHAT_ROOM_JOINED,
            ChatRoomUpdateMade.CHAT_ROOM_NEW_MATCH,
            ChatRoomUpdateMade.CHAT_ROOM_EVENT_JOINED -> {}
        }

        handleLeaveChatRoom(chatRoom.chatRoomId)
    }

    private fun handleLeaveChatRoom(chatRoomId: String) {

        if (chatRoomId == sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId) {
            applicationActivity?.navigate(
                R.id.matchMadeScreenFragment,
                R.id.action_matchMadeScreenFragment_to_matchScreenFragment
            )
        }
    }

    private fun handleKickedBannedFromChatRoom(info: ReturnKickedBannedFromChatRoomDataHolder) {
        val chatRoomId = info.chatRoomId

        Toast.makeText(GlobalValues.applicationContext, "Match has been canceled.", Toast.LENGTH_SHORT)
            .show()

        handleLeaveChatRoom(chatRoomId)
    }

    private fun storeErrorMatchMadeFragment(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String
    ) {
        val errorMessage = passedErrMsg + "\n" +
                "chatRoomId: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId}\n" +
                "chatRoomName: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomName}\n" +
                "chatRoomPassword: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomPassword}\n" +
                "notificationsEnabled: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.notificationsEnabled}\n" +
                "accountState: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.accountState}\n" +
                "chatRoomMembers.size: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomMembers.size()}\n" +
                "timeJoined: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.timeJoined}\n" +
                "matchingChatRoomOID: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.matchingChatRoomOID}\n" +
                "chatRoomLastObservedTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastObservedTime}\n" +
                "userLastActivityTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.userLastActivityTime}\n" +
                "chatRoomLastActivityTime: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomLastActivityTime}\n" +
                "lastTimeUpdated: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.lastTimeUpdated}\n" +
                "finalMessage: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.finalMessage}\n" +
                "finalPictureMessage: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.finalPictureMessage}\n" +
                "displayChatRoom: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.displayChatRoom}\n" +
                "showLoading: ${sharedApplicationViewModel.chatRoomContainer.chatRoom.showLoading}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorMessage
        )
    }

    override fun onDestroyView() {
        otherUser = null
        _binding = null
        applicationActivity = null

        super.onDestroyView()
    }
}