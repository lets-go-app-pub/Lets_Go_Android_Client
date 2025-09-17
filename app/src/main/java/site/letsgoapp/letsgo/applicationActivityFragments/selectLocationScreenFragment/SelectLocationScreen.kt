package site.letsgoapp.letsgo.applicationActivityFragments.selectLocationScreenFragment

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databinding.FragmentSelectLocationScreenBinding
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.utilities.*

class SelectLocationScreen : Fragment() {

    private var _binding: FragmentSelectLocationScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var addressObserver: Observer<EventWrapperWithKeyString<String>>

    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>

    private val navigateToMessengerFragmentResourceId =
        R.id.action_selectLocationScreen_to_messengerScreenFragment

    private var applicationActivity: AppActivity? = null

    private var locationCalledFor: ReasonSelectLocationCalled? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        applicationActivity = requireActivity() as AppActivity

        // Inflate the layout for this fragment
        _binding = FragmentSelectLocationScreenBinding.inflate(inflater, container, false)

        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        returnLeaveChatRoomResultObserver = Observer { eventWrapper ->
            val result = eventWrapper.getContentIfNotHandled()
            result?.let {
                applicationActivity?.handleLeaveChatRoom(it, navigateToMessengerFragmentResourceId)
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

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (!sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()) {
            applicationActivity?.navigate(
                R.id.selectLocationScreen,
                navigateToMessengerFragmentResourceId
            )
            return
        }

        arguments?.let {
            val args = SelectLocationScreenArgs.fromBundle(it)
            locationCalledFor = args.locationCalledFor
        }

        addressObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                binding.selectLocationScreenAddressTextView.text = it
            }
        }

        sharedApplicationViewModel.modifyLocationAddressText.observe(
            viewLifecycleOwner,
            addressObserver
        )

        binding.selectLocationScreenSendLocationConstraintLayout.setSafeOnClickListener {

            when (locationCalledFor) {
                ReasonSelectLocationCalled.CALLED_FOR_PINNED_LOCATION -> {
                    sharedApplicationViewModel.chatRoomContainer.pinnedLocationObject.sendLocationMessage =
                        true
                    applicationActivity?.navigate(
                        R.id.selectLocationScreen,
                        R.id.action_selectLocationScreen_to_chatRoomInfoFragment
                    )
                }
                ReasonSelectLocationCalled.CALLED_FOR_LOCATION_MESSAGE -> {
                    sharedApplicationViewModel.chatRoomContainer.locationMessageObject.sendLocationMessage =
                        true
                    applicationActivity?.navigate(
                        R.id.selectLocationScreen,
                        R.id.action_selectLocationScreen_to_chatRoomFragment
                    )
                }
                null -> {
                    val errorMessage =
                        "ReasonSelectLocationCalled was set to null. This should never happen.\n" +
                                "locationMessageObject: " + sharedApplicationViewModel.chatRoomContainer.locationMessageObject.toString() + "\n" +
                                "pinnedLocationObject: " + sharedApplicationViewModel.chatRoomContainer.pinnedLocationObject.toString() + "\n"

                    errorStore.storeError(
                        Thread.currentThread().stackTrace[2].fileName,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        errorMessage
                    )

                    applicationActivity?.navigate(
                        R.id.selectLocationScreen,
                        R.id.action_selectLocationScreen_to_chatRoomFragment
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsSelectLocationScreenFragment(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        _binding = null
        applicationActivity = null
        super.onDestroyView()
    }
}