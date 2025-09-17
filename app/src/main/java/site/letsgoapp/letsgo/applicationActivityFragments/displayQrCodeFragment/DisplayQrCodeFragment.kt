package site.letsgoapp.letsgo.applicationActivityFragments.displayQrCodeFragment

import android.os.Bundle
import android.provider.Settings.Global
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databinding.FragmentDisplayQrCodeBinding
import site.letsgoapp.letsgo.databinding.FragmentSelectChatRoomForInviteBinding
import site.letsgoapp.letsgo.databinding.FragmentSelectLocationScreenBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnJoinedLeftChatRoomDataHolder
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ReturnKickedBannedFromChatRoomDataHolder
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp

class DisplayQrCodeFragment : Fragment() {

    private var _binding: FragmentDisplayQrCodeBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String
    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var returnLeaveChatRoomResultObserver: Observer<EventWrapper<String>>
    private lateinit var returnJoinedLeftChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnJoinedLeftChatRoomDataHolder>>
    private lateinit var returnKickedBannedFromChatRoomObserver: Observer<EventWrapperWithKeyString<ReturnKickedBannedFromChatRoomDataHolder>>

    private val navigateToMessengerFragmentResourceId =
        R.id.action_displayQrCodeFragment_to_messengerScreenFragment

    private var applicationActivity: AppActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplayQrCodeBinding.inflate(inflater, container, false)

        applicationActivity = requireActivity() as AppActivity

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val qrCodePath = sharedApplicationViewModel.chatRoomContainer.chatRoom.qrCodePath
        val qrCodeMessage = sharedApplicationViewModel.chatRoomContainer.chatRoom.qrCodeMessage

        if (!sharedApplicationViewModel.chatRoomContainer.chatRoom.chatRoomId.isValidChatRoomId()
            || qrCodePath == GlobalValues.server_imported_values.qrCodeDefault) {
            applicationActivity?.navigate(
                R.id.selectChatRoomForInviteFragment,
                navigateToMessengerFragmentResourceId
            )
            return
        }

        GlideApp.with(this)
            .load(qrCodePath)
            .error(GlobalValues.defaultPictureResourceID)
            .signature(generateFileObjectKey(sharedApplicationViewModel.chatRoomContainer.chatRoom.qrCodeTimeUpdated))
            .into(binding.displayQrCodeImageView)

        if (qrCodeMessage == GlobalValues.server_imported_values.qrCodeMessageDefault) {
            binding.displayQrCodeMessageTextView.visibility = View.GONE
        } else {
            binding.displayQrCodeMessageTextView.visibility = View.VISIBLE
            binding.displayQrCodeMessageTextView.text = qrCodeMessage
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
        applicationActivity?.setupActivityMenuBars?.setupToolbarsDisplayQrCodeFragment(viewLifecycleOwner)
    }

    private fun storeError(
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

    override fun onDestroyView() {
        _binding = null
        applicationActivity = null
        super.onDestroyView()
    }
}