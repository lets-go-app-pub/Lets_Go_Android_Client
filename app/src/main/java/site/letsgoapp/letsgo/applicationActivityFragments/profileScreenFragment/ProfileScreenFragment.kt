package site.letsgoapp.letsgo.applicationActivityFragments.profileScreenFragment

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.request.RequestOptions
import feedback_enum.FeedbackTypeEnum
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databinding.FragmentProfileScreenBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import site.letsgoapp.letsgo.workers.error_handling.getDeviceAndVersionInformation

class ProfileScreenFragment : Fragment() {

    private var _binding: FragmentProfileScreenBinding? = null
    private val binding get() = _binding!!

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var thisFragmentInstanceID: String

    private var applicationActivity: AppActivity? = null

    private lateinit var setFirstPictureReturnValueObserver: Observer<EventWrapper<Unit>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentProfileScreenBinding.inflate(inflater, container, false)
        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        //set the chatRoomUniqueId to ~
        sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(ChatRoomWithMemberMapDataClass())

        applicationActivity = requireActivity() as AppActivity

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.userProfileInclude.matchLayoutPictureCardView.radius = resources.getDimension(R.dimen.half_user_picture_settings_size)

        loadPictureIntoImageView()

        setFirstPictureReturnValueObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                loadPictureIntoImageView()
            }
        }

        sharedApplicationViewModel.setFirstPictureReturnValue.observe(
            viewLifecycleOwner,
            setFirstPictureReturnValueObserver
        )

        //profile on click listener
        binding.userProfileInclude.matchLayoutPictureCardView.setSafeOnClickListener {
            //navigate to modify profile
            applicationActivity?.navigate(
                R.id.profileScreenFragment,
                R.id.action_profileScreenFragment_to_modifyProfileScreenFragment
            )
        }

        //profile on click listener
        binding.userProfileEditIconImageView.setSafeOnClickListener {
            //navigate to modify profile
            applicationActivity?.navigate(
                R.id.profileScreenFragment,
                R.id.action_profileScreenFragment_to_modifyProfileScreenFragment
            )
        }

        //activities on click listener
        binding.menuActivitiesConstraintLayout.setSafeOnClickListener {
            //navigate to select categories activities page
            val argumentBundle =
                bundleOf(FRAGMENT_CALLED_FROM_KEY to SelectCategoriesFragmentCalledFrom.APPLICATION_PROFILE_FRAGMENT.ordinal)
            applicationActivity?.navigate(
                R.id.profileScreenFragment,
                R.id.action_profileScreenFragment_to_selectCategoriesFragment2,
                argumentBundle
            )
        }

        //feedback on click listener
        binding.menuFeedbackConstraintLayout.setSafeOnClickListener {

            val items = arrayOf(
                Item(
                    getString(R.string.profile_screen_feedback_type_selection_activity_suggestion),
                    R.drawable.icon_round_add_circle_outline_24
                ),
                Item(
                    getString(R.string.profile_screen_feedback_type_selection_bug_report),
                    R.drawable.icon_round_bug_report_24
                ),
                Item(
                    getString(R.string.profile_screen_feedback_type_selection_other_feedback),
                    R.drawable.icon_question_mark
                )
            )

            ChoicesDialog(
                R.string.profile_screen_feedback_type_selection_dialog_title,
                ChoicesArrayAdapter(requireContext(), items)
            ) { dialogInterface: DialogInterface, i: Int ->

                when (i) {
                    0 -> { //Activity Suggestion
                        FeedbackActivitySuggestionDialog(
                            getString(R.string.profile_screen_feedback_type_selection_activity_suggestion)
                        ) { activityNameStr, otherInfoStr ->
                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.profile_screen_feedback_thank_you_toast,
                                Toast.LENGTH_LONG
                            ).show()
                            sendFeedback(
                                otherInfoStr.trim(),
                                FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_ACTIVITY_SUGGESTION,
                                activityNameStr.trim()
                            )
                        }.show(childFragmentManager, "activity_suggestion")
                    }
                    1 -> { //Bug Report
                        FeedbackTextEditDialog(
                            getString(R.string.profile_screen_feedback_type_selection_bug_report),
                            getString(R.string.profile_screen_feedback_bug_report_body_hint)
                        ) {
                            val updatedString = it.trim()
                            if (updatedString.isNotEmpty()) {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.profile_screen_feedback_thank_you_toast,
                                    Toast.LENGTH_LONG
                                ).show()
                                sendFeedback(
                                    "$updatedString\n\n${getDeviceAndVersionInformation()}",
                                    FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_BUG_REPORT
                                )
                            }
                        }.show(childFragmentManager, "bug_report")
                    }
                    2 -> { //Other feedback
                        FeedbackTextEditDialog(
                            getString(R.string.profile_screen_feedback_type_selection_other_feedback),
                            getString(R.string.profile_screen_feedback_other_feedback_body_hint)
                        ) {
                            val updatedString = it.trim()
                            if (updatedString.isNotEmpty()) {
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.profile_screen_feedback_thank_you_toast,
                                    Toast.LENGTH_LONG
                                ).show()
                                sendFeedback(
                                    "$updatedString\n\n${getDeviceAndVersionInformation()}",
                                    FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_OTHER_FEEDBACK
                                )
                            }
                        }.show(childFragmentManager, "other_feedback")
                    }
                    else -> {}  //Cancel
                }

                dialogInterface.dismiss()
            }.show(childFragmentManager, "feedback_choices")
        }

        //share on click listener
        binding.menuShareConstraintLayout.setSafeOnClickListener {
            ShareCompat.IntentBuilder(requireContext())
                .setType("text/plain")
                .setChooserTitle(getString(R.string.profile_screen_share_title))
                .setText(GlobalValues.server_imported_values.appUrlForSharing)
                .startChooser();
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
        applicationActivity?.setupActivityMenuBars?.setupToolbarsProfileScreenFragment()
    }

    private fun sendFeedback(
        info: String,
        feedbackType: FeedbackTypeEnum.FeedbackType,
        activityName: String = ""
    ) {

        when (feedbackType) {
            FeedbackTypeEnum.FeedbackType.UNRECOGNIZED,
            FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_UNKNOWN -> {
                val errorString =
                    "feedbackType was $feedbackType when it should not be possible.\n" +
                            "info: $info\n" +
                            "feedbackType: $feedbackType\n" +
                            "activityName: $activityName"

                sharedApplicationViewModel.sendSharedApplicationError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )
            }
            FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_ACTIVITY_SUGGESTION -> {

                if (info != "" || activityName != "") {
                    sharedApplicationViewModel.sendFeedback(
                        info,
                        feedbackType,
                        activityName
                    )
                }
            }
            FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_BUG_REPORT,
            FeedbackTypeEnum.FeedbackType.FEEDBACK_TYPE_OTHER_FEEDBACK -> {
                if (info != "") {
                    sharedApplicationViewModel.sendFeedback(
                        info,
                        feedbackType,
                        activityName
                    )
                }
            }
        }
    }

    private fun loadPictureIntoImageView() {
        GlideApp.with(this)
            .load(sharedApplicationViewModel.firstPictureInList.picturePath)
            .signature(generateFileObjectKey(sharedApplicationViewModel.firstPictureInList.pictureTimestamp))
            .error(GlobalValues.defaultPictureResourceID)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.userProfileInclude.matchLayoutPictureImageView)
    }

    override fun onDestroyView() {
        _binding = null
        applicationActivity = null
        super.onDestroyView()
    }

}

