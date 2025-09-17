package site.letsgoapp.letsgo.applicationActivityFragments.modifyProfileScreenFragment

import algorithm_search_options.AlgorithmSearchOptionsOuterClass
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import email_sending_messages.EmailSentStatus
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.databinding.FragmentModifyProfileScreenBinding
import site.letsgoapp.letsgo.databinding.ViewGenderRangeGenderOtherElementBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.reUsedFragments.selectGenderFragment.SelectGenderFragment
import site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment.SelectPicturesFragment
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.utilities.editTextFilters.LettersSpaceNumbersFilter
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.roundToInt

class ModifyProfileScreenFragment : Fragment() {

    private var _binding: FragmentModifyProfileScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var selectPicturesFragment: SelectPicturesFragment

    //gender range drop down list variables
    private val listItemExpandDuration = 300L
    private var genderRangeGenderOtherOriginalHeight = -1 //will be calculated dynamically
    private var expandedHeight = -1 //will be calculated dynamically
    private var dropDownExpanded = false
    private var animationRunning = false
    private var totalNumberGendersSelected = 0
    private var mostRecentlyAddedOtherGenderEditText: EditText? = null

    private lateinit var selectGenderFragment: SelectGenderFragment

    private lateinit var setEmailReturnValue: Observer<EventWrapper<SetFieldReturnValues>>
    private lateinit var returnEmailVerificationReturnValueObserver: Observer<EventWrapperWithKeyString<EmailVerificationReturnValues>>
    private lateinit var algorithmSearchOptionsUpdatedObserver: Observer<EventWrapperWithKeyString<Unit>>
    private lateinit var optedInToPromotionalEmailsUpdatedObserver: Observer<EventWrapperWithKeyString<Unit>>

    private var deviceScreenWidth = 0

    private var applicationActivity: AppActivity? = null

    private var widthMeasureSpec: Int = 0
    private var heightMeasureSpec: Int = 0

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentModifyProfileScreenBinding.inflate(inflater, container, false)
        thisFragmentInstanceID =
            setupApplicationCurrentFragmentID(sharedApplicationViewModel, this::class.simpleName)

        //set the chatRoomUniqueId to ~
        sharedApplicationViewModel.chatRoomContainer.setChatRoomInfo(ChatRoomWithMemberMapDataClass())

        deviceScreenWidth = getScreenWidth(requireActivity())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        widthMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(deviceScreenWidth, View.MeasureSpec.AT_MOST)
        heightMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        applicationActivity = requireActivity() as AppActivity

        //Allow hyperlinks to be clicked.
        binding.modifyUserWebPageLinkTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.modifyUserPrivacyPolicyLinkTextView.movementMethod = LinkMovementMethod.getInstance()

        sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment = true

        setEmailReturnValue = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            result?.let {
                handleTimestampReturnValue(it)
            }
        }

        sharedApplicationViewModel.setEmailReturnValue.observe(
            viewLifecycleOwner,
            setEmailReturnValue
        )

        returnEmailVerificationReturnValueObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                handleEmailVerificationReturnValue(it)
            }
        }

        sharedApplicationViewModel.returnEmailVerificationReturnValue.observe(
            viewLifecycleOwner,
            returnEmailVerificationReturnValueObserver
        )

        algorithmSearchOptionsUpdatedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                updateAlgorithmSearchOptions()
            }
        }

        sharedApplicationViewModel.algorithmSearchOptionsUpdatedResults.observe(
            viewLifecycleOwner,
            algorithmSearchOptionsUpdatedObserver
        )

        optedInToPromotionalEmailsUpdatedObserver = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)
            result?.let {
                updateOptedInToPromotionalEmails()
            }
        }

        sharedApplicationViewModel.optedInToPromotionalEmailsUpdatedResults.observe(
            viewLifecycleOwner,
            optedInToPromotionalEmailsUpdatedObserver
        )

        //Setup for pictures
        selectPicturesFragment =
            childFragmentManager.findFragmentById(R.id.modifyUserPicturesFragmentContainerView) as SelectPicturesFragment
        selectPicturesFragment.setFunctions { binding.modifyUserPicturesErrorTextView.text = it }

        //Setup for bio
        binding.modifyUserBioBodyEditText.filters = arrayOf(
            ByteLengthFilter(
                GlobalValues.server_imported_values.maximumNumberAllowedBytesUserBio
            )
        )

        //Setup for city
        binding.modifyUserCityBodyEditText.filters = arrayOf(
            ByteLengthFilter(
                GlobalValues.server_imported_values.maximumNumberAllowedBytes
            ),
            LettersSpaceNumbersFilter(
                allowLowerCase = true,
                allowUpperCase = true,
                allowSpace = true,
            )
        )

        //Setup for age range
        binding.modifyUserAgeRangeRangeRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            if (values.size == 2) {
                val valueStr = createAgeRangeDisplayString(values[0], values[1])
                binding.modifyUserAgeRangeValueTextView.text = valueStr
            }
        }

        binding.modifyUserMaxDistanceRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            if (values.isNotEmpty()) {
                binding.modifyUserMaxDistanceValueTextView.text =
                    GlobalValues.applicationContext.getString(
                        R.string.modify_profile_screen_user_max_distance_text,
                        values[0].roundToInt()
                    )
            }
        }

        //Setup for email
        binding.modifyUserEmailAddressBodyTextView.setSafeOnClickListener {
            val alertDialog =
                CollectEmailDialogFragment(
                    getString(R.string.modify_profile_screen_set_email_title),
                    getString(R.string.modify_profile_screen_set_email_body),
                    { newEmailAddress ->
                        this.hideKeyboard()
                        sharedApplicationViewModel.setEmailAddress(newEmailAddress)
                    },
                    {
                        this.hideKeyboard()
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.modify_profile_screen_invalid_email,
                            Toast.LENGTH_LONG
                        ).show()
                    })

            alertDialog.show(childFragmentManager, "fragment_alert")
        }

        binding.sendVerifyEmailButton.setSafeOnClickListener {
            //NOTE: using the progress bar as a bool to tell if email is sending
            if (binding.verifyEmailProgressBar.visibility == View.GONE) {
                binding.verifyEmailProgressBar.visibility = View.VISIBLE
                sharedApplicationViewModel.runBeginEmailVerification(thisFragmentInstanceID)
            }
        }

        //Setup for gender
        selectGenderFragment =
            childFragmentManager.findFragmentById(R.id.modifyUserGenderTitleFragmentContainerView) as SelectGenderFragment

        //calculate height of gender range gender other title bar
        binding.modifyUserGenderRangeOtherTitleLinearLayout.measure(
            widthMeasureSpec,
            heightMeasureSpec
        )
        genderRangeGenderOtherOriginalHeight =
            binding.modifyUserGenderRangeOtherTitleLinearLayout.measuredHeight

        //calculate expanded height
        binding.modifyUserGenderRangeOtherExpandLinearLayout.measure(
            widthMeasureSpec,
            heightMeasureSpec
        )
        expandedHeight =
            binding.modifyUserGenderRangeOtherExpandLinearLayout.measuredHeight + genderRangeGenderOtherOriginalHeight

        expandItem(dropDownExpanded, animate = false)

        binding.modifyUserGenderRangeOtherTitleLinearLayout.setOnClickListener {
            if (!animationRunning) {
                dropDownExpanded = if (dropDownExpanded) {
                    //contract view model
                    expandItem(expand = false, animate = true)
                    false
                } else {

                    //expand view model
                    expandItem(expand = true, animate = true)
                    true
                }
            }
        }

        binding.modifyUserGenderRangeEveryoneCheckBox.setOnCheckedChangeListener { _, isChecked ->

            if (isChecked) {
                //NOTE: These will call their onCheckedChangeListener if they are changed.
                binding.modifyUserGenderRangeMaleCheckBox.isChecked = false
                binding.modifyUserGenderRangeMaleCheckBox.isEnabled = false
                binding.modifyUserGenderRangeFemaleCheckBox.isChecked = false
                binding.modifyUserGenderRangeFemaleCheckBox.isEnabled = false

                if (dropDownExpanded) {
                    //contract view model
                    expandItem(expand = false, animate = true)
                    dropDownExpanded = false
                }

                binding.modifyUserGenderRangeOtherTitleLinearLayout.isEnabled = false
                binding.modifyUserGenderRangeOtherTitleTextView.setTextColor(Color.parseColor("#d6d6d6")) //change color to show 'disabled'
                binding.modifyUserGenderRangeOtherErrorTextView.visibility = View.GONE
            } else {
                //if maximum number of genders is selected, don't enable check boxes
                if (totalNumberGendersSelected < GlobalValues.server_imported_values.numberGenderUserCanMatchWith) {
                    binding.modifyUserGenderRangeMaleCheckBox.isEnabled = true
                    binding.modifyUserGenderRangeFemaleCheckBox.isEnabled = true
                }

                binding.modifyUserGenderRangeOtherTitleLinearLayout.isEnabled = true
                binding.modifyUserGenderRangeOtherTitleTextView.setTextColor(Color.parseColor("#000000")) //change color to show 'enabled'

                setupCheckBoxes(add = false)
            }
        }

        val checkBoxListener: (CompoundButton?, Boolean) -> Unit = { _, isChecked ->

            if (isChecked) {
                modifyCheckBoxes(add = true)
            } else {
                modifyCheckBoxes(add = false)
            }
        }

        binding.modifyUserGenderRangeMaleCheckBox.setOnCheckedChangeListener(checkBoxListener)
        binding.modifyUserGenderRangeFemaleCheckBox.setOnCheckedChangeListener(checkBoxListener)

        binding.modifyUserGenderRangeOtherButton.setOnClickListener {
            modifyGenderRangeGenderOtherOnClickListener()
        }

        val weakApplicationActivity = WeakReference(applicationActivity)
        val weakSharedApplicationViewModel = WeakReference(sharedApplicationViewModel)

        //Log out button
        binding.modifyUserLogoutButton.setSafeOnClickListener {
            val alertDialog =
                WarningDialogFragment(
                    getString(R.string.modify_profile_screen_log_out_warning_title),
                    getString(R.string.modify_profile_screen_log_out_warning_body)
                ) { _, _ ->
                    //NOTE: the results here will be returned to the activity
                    weakApplicationActivity.get()?.logUserOutAndBackToMainWithoutError()
                }

            alertDialog.show(childFragmentManager, "fragment_alert_logout")
        }

        //Delete button
        binding.modifyUserDeleteButton.setSafeOnClickListener {
            val alertDialog =
                WarningDialogFragment(
                    getString(R.string.modify_profile_screen_delete_warning_title),
                    getString(R.string.modify_profile_screen_delete_warning_body)
                ) { _, _ ->
                    weakApplicationActivity.get()?.let { appActivity ->
                        weakSharedApplicationViewModel.get()?.let { appViewModel ->
                            //Make sure both references are still good before running either value.
                            appActivity.deleteAccountInitialization()
                            //NOTE: the results here will be returned to the activity
                            appViewModel.deleteAccount()
                        }
                    }
                }

            alertDialog.show(childFragmentManager, "fragment_alert_delete")
        }

        //Delete button
        binding.modifyAlgorithmMatchOptionsStateSwitchCompat.setOnCheckedChangeListener { _, isChecked ->
            setupAlgorithmSearchOptionsStateText(isChecked)
        }

        binding.modifyAlgorithmMatchOptionsTitleTextView.setOnClickListener {
            val alertDialog = InfoAlertDialogFragment(
                "",
                resources.getText(R.string.modify_profile_screen_algorithm_match_options_tooltip)
            )

            alertDialog.show(
                childFragmentManager,
                "matching_by_info"
            )
        }

        setStoredAccountInfo()
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsModifyProfileScreenFragment(
            viewLifecycleOwner
        )
    }

    private fun setStoredAccountInfo() {

        updateAlgorithmSearchOptions()

        updateOptedInToPromotionalEmails();

        if (sharedApplicationViewModel.userBio != "~") {
            binding.modifyUserBioBodyEditText.setText(sharedApplicationViewModel.userBio)
        }

        if (sharedApplicationViewModel.userCity != "~") {
            binding.modifyUserCityBodyEditText.setText(sharedApplicationViewModel.userCity)
        }

        val userMatchableAgeRange = getMinAndMaxMatchableAges(
            sharedApplicationViewModel.userAge,
            errorStore
        )

        binding.modifyUserAgeRangeRangeRangeSlider.valueFrom =
            userMatchableAgeRange.minAge.toFloat()
        binding.modifyUserAgeRangeRangeRangeSlider.valueTo = userMatchableAgeRange.maxAge.toFloat()

        binding.modifyUserAgeRangeRangeRangeSlider.setValues(
            userMatchableAgeRange.minAge.toFloat(),
            userMatchableAgeRange.maxAge.toFloat()
        )

        if (sharedApplicationViewModel.maxAgeRange > userMatchableAgeRange.maxAge) {
            sharedApplicationViewModel.maxAgeRange = userMatchableAgeRange.maxAge
        }

        if (sharedApplicationViewModel.minAgeRange < userMatchableAgeRange.minAge) {
            sharedApplicationViewModel.minAgeRange = userMatchableAgeRange.minAge

            val errorString = "Min age range was smaller than should be possible.\n" +
                    "minAgeRange: ${sharedApplicationViewModel.minAgeRange}\n" +
                    "minAgeMatchable: ${userMatchableAgeRange.minAge}"

            sharedApplicationViewModel.sendSharedApplicationError(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName
            )
        }

        val checkedMinAgeRange = when {
            sharedApplicationViewModel.minAgeRange < GlobalValues.server_imported_values.lowestAllowedAge -> {
                GlobalValues.server_imported_values.lowestAllowedAge
            }
            sharedApplicationViewModel.minAgeRange > GlobalValues.server_imported_values.highestDisplayedAge -> {
                GlobalValues.server_imported_values.highestDisplayedAge
            }
            else -> {
                sharedApplicationViewModel.minAgeRange
            }
        }

        val checkedMaxAgeRange = when {
            sharedApplicationViewModel.maxAgeRange < GlobalValues.server_imported_values.lowestAllowedAge -> {
                GlobalValues.server_imported_values.lowestAllowedAge
            }
            sharedApplicationViewModel.maxAgeRange > GlobalValues.server_imported_values.highestDisplayedAge -> {
                GlobalValues.server_imported_values.highestDisplayedAge
            }
            else -> {
                sharedApplicationViewModel.maxAgeRange
            }
        }

        //NOTE: Because age range on the client doesn't necessarily adhere to highestDisplayedAge and the lowest
        // and highest values can change over time, it is better to check both age ranges before displaying them.
        binding.modifyUserAgeRangeRangeRangeSlider.setValues(
            checkedMinAgeRange.toFloat(),
            checkedMaxAgeRange.toFloat()
        )

        binding.modifyUserAgeRangeValueTextView.text = createAgeRangeDisplayString(
            sharedApplicationViewModel.minAgeRange.toFloat(),
            sharedApplicationViewModel.maxAgeRange.toFloat()
        )

        //Setup smallest and largest values for max distance.
        binding.modifyUserMaxDistanceRangeSlider.valueFrom =
            GlobalValues.server_imported_values.minimumAllowedDistance.toFloat()
        binding.modifyUserMaxDistanceRangeSlider.valueTo =
            GlobalValues.server_imported_values.maximumAllowedDistance.toFloat()

        //Set max distance to user current max distance.
        binding.modifyUserMaxDistanceRangeSlider.setValues(
            sharedApplicationViewModel.userMaxDistance.toFloat()
        )

        binding.modifyUserMaxDistanceValueTextView.text = getString(
            R.string.modify_profile_screen_user_max_distance_text,
            sharedApplicationViewModel.userMaxDistance
        )

        //set email value
        if (sharedApplicationViewModel.userEmailAddress != "~") {

            binding.modifyUserEmailAddressBodyTextView.text =
                sharedApplicationViewModel.userEmailAddress

            setEmailState()
        } else { //if email is equal to "~"
            val errorString = "email address returned '~' after the user was logged in"

            sharedApplicationViewModel.sendSharedApplicationError(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName
            )
        }

        //set gender value
        selectGenderFragment.setGenderByString(sharedApplicationViewModel.userGender)

        //set gender range
        if (sharedApplicationViewModel.userGenderRange.size == 1
            && sharedApplicationViewModel.userGenderRange[0] == GlobalValues.EVERYONE_GENDER_VALUE
        ) { //if gender range is "Everyone"

            //the on checked listener will handle the rest
            //binding.modifyUserGenderRangeEveryoneCheckBox.isChecked = true

            binding.modifyUserGenderRangeMaleCheckBox.isChecked = true
            binding.modifyUserGenderRangeFemaleCheckBox.isChecked = true
        } else {
            for (g in sharedApplicationViewModel.userGenderRange) {
                when (g) {
                    GlobalValues.MALE_GENDER_VALUE -> {
                        binding.modifyUserGenderRangeMaleCheckBox.isChecked = true
                    }
                    GlobalValues.FEMALE_GENDER_VALUE -> {
                        binding.modifyUserGenderRangeFemaleCheckBox.isChecked = true
                    }
                    else -> {
                        //expand view model
                        if (!dropDownExpanded) {
                            expandItem(expand = true, animate = true)
                            dropDownExpanded = true
                        }

                        //add a new layout to hold gender other
                        modifyGenderRangeGenderOtherOnClickListener()

                        //this edit text will be the previously set lambda the edit text to the gender
                        mostRecentlyAddedOtherGenderEditText?.setText(g)
                    }
                }
            }
        }
    }

    private fun modifyGenderRangeGenderOtherOnClickListener() {
        if (totalNumberGendersSelected < GlobalValues.server_imported_values.numberGenderUserCanMatchWith) {

            val newViewBinding =
                ViewGenderRangeGenderOtherElementBinding.inflate(layoutInflater)

            binding.modifyUserGenderRangeOtherExpandLinearLayout.addView(
                newViewBinding.root,
                binding.modifyUserGenderRangeOtherExpandLinearLayout.childCount - 1
            )

            newViewBinding.genderRangeGenderOtherEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytes
                )
            )

            newViewBinding.root.measure(widthMeasureSpec, heightMeasureSpec)
            expandedHeight += newViewBinding.root.measuredHeight
            binding.modifyUserGenderRangeOtherLinearLayout.layoutParams.height = expandedHeight

            newViewBinding.root.findViewById<ImageButton>(R.id.genderRangeGenderOtherImageButton)
                .setSafeOnClickListener {

                    binding.modifyUserGenderRangeOtherExpandLinearLayout.removeView(
                        newViewBinding.root
                    )

                    expandedHeight -= newViewBinding.root.measuredHeight
                    binding.modifyUserGenderRangeOtherLinearLayout.layoutParams.height =
                        expandedHeight

                    modifyCheckBoxes(add = false)
                }

            modifyCheckBoxes(add = true)

            mostRecentlyAddedOtherGenderEditText = newViewBinding.genderRangeGenderOtherEditText
        }
    }

    private fun setEmailState() {
        if (sharedApplicationViewModel.userEmailAddressRequiresVerification) { //if email requires verification
            setEmailState(ModifyPropertiesSetEmailState.REQUIRES_VERIFICATION)
        } else { //if email is verified
            setEmailState(ModifyPropertiesSetEmailState.VERIFIED)
        }
    }

    private fun setEmailState(state: ModifyPropertiesSetEmailState) {
        when (state) {
            ModifyPropertiesSetEmailState.REQUIRES_VERIFICATION -> {

                binding.modifyUserEmailAddressVerificationTextView.setBackgroundResource(R.drawable.background_modify_profile_email_not_verified_message)
                //binding.modifyUserEmailAddressVerificationTextView.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                binding.modifyUserEmailAddressVerificationTextView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.colorWhite
                    )
                )
                binding.modifyUserEmailAddressVerificationTextView.setText(R.string.modify_profile_screen_verify_email_requires_verification)

                binding.modifyUserEmailAddressProgressBar.visibility = View.GONE
                binding.sendVerifyEmailButton.visibility = View.VISIBLE
            }
            ModifyPropertiesSetEmailState.VERIFIED -> {
                binding.modifyUserEmailAddressVerificationTextView.setBackgroundResource(R.drawable.background_modify_profile_email_is_verified_message)
                //binding.modifyUserEmailAddressVerificationTextView.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                binding.modifyUserEmailAddressVerificationTextView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.colorBlack
                    )
                )
                binding.modifyUserEmailAddressVerificationTextView.setText(R.string.modify_profile_screen_verify_email_verified)

                binding.modifyUserEmailAddressProgressBar.visibility = View.GONE
                binding.sendVerifyEmailButton.visibility = View.GONE
            }
            ModifyPropertiesSetEmailState.LOADING -> {
                binding.modifyUserEmailAddressBodyTextView.visibility = View.GONE
                binding.modifyUserEmailVerificationConstraintLayout.visibility = View.GONE

                binding.modifyUserEmailAddressProgressBar.visibility = View.VISIBLE
            }
        }
    }

    private fun handleTimestampReturnValue(returnValue: SetFieldReturnValues) {

        binding.modifyUserEmailAddressErrorTextView.text = null
        binding.modifyUserEmailAddressErrorTextView.visibility = View.GONE

        when (returnValue) {
            SetFieldReturnValues.INVALID_VALUE -> {
                binding.modifyUserEmailAddressErrorTextView.visibility = View.VISIBLE
                binding.modifyUserEmailAddressErrorTextView.setText(R.string.get_email_invalid_email)
            }
            SetFieldReturnValues.SERVER_ERROR -> {
                //if it was a server problem from SUCCESS that returned -1 then the error is stored on the server
                binding.modifyUserEmailAddressErrorTextView.visibility = View.VISIBLE
                binding.modifyUserEmailAddressErrorTextView.setText(R.string.general_error)
            }
            SetFieldReturnValues.SUCCESSFUL -> {
                binding.modifyUserEmailAddressBodyTextView.text =
                    sharedApplicationViewModel.userEmailAddress
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.modify_profile_screen_email_address_successfully_saved,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        setEmailState()

    }

    private fun handleEmailVerificationReturnValue(emailVerificationReturnValues: EmailVerificationReturnValues) {

        binding.verifyEmailProgressBar.visibility = View.GONE

        when (emailVerificationReturnValues.errors) {
            GrpcFunctionErrorStatusEnum.NO_ERRORS -> {

                if (emailVerificationReturnValues.response.emailAddressIsAlreadyVerified) {
                    setEmailState(ModifyPropertiesSetEmailState.VERIFIED)

                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.modify_profile_layout_email_email_already_verified,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    when (emailVerificationReturnValues.response.emailSentStatus) {
                        EmailSentStatus.EMAIL_SUCCESS -> {
                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.modify_profile_layout_email_successfully_sent,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        EmailSentStatus.EMAIL_ON_COOL_DOWN -> {
                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.modify_profile_layout_email_on_cool_down,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        EmailSentStatus.EMAIL_VALUE_NOT_SET,
                        EmailSentStatus.EMAIL_FAILED_TO_BE_SENT,
                        EmailSentStatus.UNRECOGNIZED,
                        null -> {
                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.modify_profile_layout_email_error_sending_email,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            GrpcFunctionErrorStatusEnum.DO_NOTHING,
            GrpcFunctionErrorStatusEnum.CONNECTION_ERROR,
            GrpcFunctionErrorStatusEnum.SERVER_DOWN,
            GrpcFunctionErrorStatusEnum.FUNCTION_CALLED_TOO_QUICKLY,
            GrpcFunctionErrorStatusEnum.LOGIN_TOKEN_EXPIRED_OR_INVALID -> {
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.modify_profile_layout_email_error_sending_email,
                    Toast.LENGTH_SHORT
                ).show()
            }
            GrpcFunctionErrorStatusEnum.LOGGED_IN_ELSEWHERE,
            GrpcFunctionErrorStatusEnum.CLEAR_DATABASE_INFO,
            GrpcFunctionErrorStatusEnum.LOG_USER_OUT,
            GrpcFunctionErrorStatusEnum.ACCOUNT_SUSPENDED,
            GrpcFunctionErrorStatusEnum.ACCOUNT_BANNED,
            GrpcFunctionErrorStatusEnum.NO_SUBSCRIPTION -> {
                //Show nothing to the user, the activity will send the user back to the login screen
            }
        }
    }

    private fun updateAlgorithmSearchOptions() {
        binding.modifyAlgorithmMatchOptionsStateSwitchCompat.isChecked =
            sharedApplicationViewModel.algorithmSearchOptions != AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_ACTIVITY
        setupAlgorithmSearchOptionsStateText(binding.modifyAlgorithmMatchOptionsStateSwitchCompat.isChecked)
    }

    private fun updateOptedInToPromotionalEmails() {
        binding.optedInToPromotionalEmailsCheckBox.isChecked = sharedApplicationViewModel.optedInToPromotionalEmails
    }

    private fun setupAlgorithmSearchOptionsStateText(isChecked: Boolean) {
        binding.modifyAlgorithmMatchOptionsStateTextView.text =
            if (isChecked) {
                resources.getString(R.string.modify_profile_screen_categories_and_activities)
            } else {
                resources.getString(R.string.modify_profile_screen_only_activities)
            }
    }

    override fun onPause() {
        super.onPause()

        sharedApplicationViewModel.resetNumberOfPostLoginInfoThatWasSet()

        setChangedValuesOnPause()
    }

    private fun setChangedValuesOnPause() {

        var algorithmDataWasModified = false

        //Filters attached to respective EditText listed below
        //Bio
        // 1) make sure under maximumNumberAllowedBytesUserBio
        //City
        // 1) make sure under maximumNumberAllowedBytes
        // 2) chars can only be lower,upper,space
        //Age Range
        // 1) between minAgeRange and maxAgeRange
        //Max Distance
        // 1) between minimumAllowedDistance and maximumAllowedDistance
        //Gender
        // None
        //Gender Range
        // None

        var bio = binding.modifyUserBioBodyEditText.text.toString().trimEnd()

        if (bio.isEmpty()) {
            bio = "~"
        }

        if (bio != sharedApplicationViewModel.userBio) {
            sharedApplicationViewModel.setPostLoginInfo(
                SetTypeEnum.SET_BIO,
                bio,
                thisFragmentInstanceID
            )
        }

        //city
        //The modifyUserCityBodyEditText is set up above to only allow upper case, lower case and spaces
        var city = binding.modifyUserCityBodyEditText.text.toString().trim()

        if (city.isEmpty()) {
            city = "~"
        }

        if (city != sharedApplicationViewModel.userCity) {

            //capitalize the first letter of each word
            var modifiedCity = ""
            city.lowercase().split(' ').forEach { str ->
                modifiedCity += "${str.replaceFirstChar { it.titlecase(Locale.getDefault()) }} "
            }
            modifiedCity = modifiedCity.trimEnd()

            sharedApplicationViewModel.setPostLoginInfo(
                SetTypeEnum.SET_CITY,
                modifiedCity,
                thisFragmentInstanceID
            )
        }

        //age range
        //NOTE: These are stored as floats so for example 51 can return as 50.999996. So they are
        // rounded not truncated.
        val ageRangeValues = binding.modifyUserAgeRangeRangeRangeSlider.values
        var (minAgeRange, maxAgeRange) =
            if (ageRangeValues.size == 2) {
                Pair(ageRangeValues[0].roundToInt(), ageRangeValues[1].roundToInt())
            } else {
                val errorString = "Min age range was smaller than should be possible.\n" +
                        "previous minAgeRange: ${sharedApplicationViewModel.minAgeRange}\n" +
                        "previous maxAgeRange: ${sharedApplicationViewModel.maxAgeRange}\n" +
                        "ageRangeValues: $ageRangeValues\n" +
                        "lifecycleState: ${this.viewLifecycleOwner.lifecycle.currentState}"

                sharedApplicationViewModel.sendSharedApplicationError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )

                Pair(sharedApplicationViewModel.minAgeRange, sharedApplicationViewModel.maxAgeRange)
            }

        if (minAgeRange != sharedApplicationViewModel.minAgeRange
            || maxAgeRange != sharedApplicationViewModel.maxAgeRange
        ) {
            //if age range is at max value just set it to 120
            if (maxAgeRange >= GlobalValues.server_imported_values.highestDisplayedAge) {
                maxAgeRange = GlobalValues.server_imported_values.highestAllowedAge
            }

            algorithmDataWasModified = true
            sharedApplicationViewModel.setPostLoginInfo(
                SetTypeEnum.SET_AGE_RANGE,
                AgeRangeHolder(minAgeRange, maxAgeRange), thisFragmentInstanceID
            )
        }

        //max distance
        //NOTE: This is stored as a float so for example 51 will return as 50.999996. So it must be
        // rounded not truncated.
        val maxDistanceValues = binding.modifyUserMaxDistanceRangeSlider.values
        val maxDistance =
            if (maxDistanceValues.isNotEmpty()) {
                maxDistanceValues[0].roundToInt()
            } else {

                val errorString = ".\n" +
                        "previous maxDistance: ${sharedApplicationViewModel.userMaxDistance}\n" +
                        "maxDistanceValues: $maxDistanceValues\n" +
                        "lifecycleState: ${this.viewLifecycleOwner.lifecycle.currentState}"

                sharedApplicationViewModel.sendSharedApplicationError(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    Thread.currentThread().stackTrace[2].fileName
                )

                sharedApplicationViewModel.userMaxDistance
            }

        if (maxDistance != sharedApplicationViewModel.userMaxDistance) {
            algorithmDataWasModified = true
            sharedApplicationViewModel.setPostLoginInfo(
                SetTypeEnum.SET_MAX_DISTANCE,
                maxDistance, thisFragmentInstanceID
            )
        }

        //gender
        val gender = selectGenderFragment.getGenderString()

        if (gender != sharedApplicationViewModel.userGender && gender.isValidGender()) {
            algorithmDataWasModified = true
            sharedApplicationViewModel.setPostLoginInfo(
                SetTypeEnum.SET_GENDER,
                gender, thisFragmentInstanceID
            )
        }

        //A list is used to initially store the genders to preserve order. There is a little
        // bit of a 'bug' with this where if a user enters gender A then gender B and attempts
        // to switch them to gender B then gender A it will not change the order. However, the
        // alternative is to make a server call which will re-run the algorithm. So unless
        // it becomes an issue will leave it like this.
        val genderRangeList = mutableListOf<String>()

        //Building the genderRange to be matched with the genderRange stored in sharedViewModel.
        if (binding.modifyUserGenderRangeEveryoneCheckBox.isChecked) {
            genderRangeList.add(GlobalValues.EVERYONE_GENDER_VALUE)
        } else {
            if (binding.modifyUserGenderRangeMaleCheckBox.isChecked) {
                genderRangeList.add(GlobalValues.MALE_GENDER_VALUE)
            }

            if (binding.modifyUserGenderRangeFemaleCheckBox.isChecked) {
                genderRangeList.add(GlobalValues.FEMALE_GENDER_VALUE)
            }

            //look through all children except the button object
            val numTimesToLoop = binding.modifyUserGenderRangeOtherExpandLinearLayout.childCount - 1
            for (i in 0 until numTimesToLoop) {
                val genderOtherVal =
                    binding.modifyUserGenderRangeOtherExpandLinearLayout.getChildAt(i)
                val genderString =
                    genderOtherVal.findViewById<EditText>(R.id.genderRangeGenderOtherEditText).text.toString()

                if (genderString.isNotEmpty()) {
                    genderRangeList.add(genderString)
                }
            }
        }

        //This is used to avoid repeats.
        val genderRangeSet = genderRangeList.toSet()

        //If gender range is not empty, check if needs updated.
        if (genderRangeSet.isNotEmpty()) {
            var genderRangeRequiresUpdate = false
            if (sharedApplicationViewModel.userGenderRange.size == genderRangeSet.size) {
                val originalGendersSet = sharedApplicationViewModel.userGenderRange.toSet()

                for (g in genderRangeSet) {
                    if (!originalGendersSet.contains(g)) {
                        genderRangeRequiresUpdate = true
                        break
                    }
                }
            } else {
                genderRangeRequiresUpdate = true
            }

            if (genderRangeRequiresUpdate) {

                algorithmDataWasModified = true
                sharedApplicationViewModel.setPostLoginInfo(
                    SetTypeEnum.SET_GENDER_RANGE,
                    convertGenderRangeToString(genderRangeList), thisFragmentInstanceID
                )
            }
        }

        val selectedAlgorithmSearchOptions =
            if (binding.modifyAlgorithmMatchOptionsStateSwitchCompat.isChecked) {
                AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_CATEGORY_AND_ACTIVITY
            } else {
                AlgorithmSearchOptionsOuterClass.AlgorithmSearchOptions.USER_MATCHING_BY_ACTIVITY
            }

        if (selectedAlgorithmSearchOptions != sharedApplicationViewModel.algorithmSearchOptions) {
            sharedApplicationViewModel.setAlgorithmSearchOptions(selectedAlgorithmSearchOptions)
        }

        val optedInToPromotionalEmails = binding.optedInToPromotionalEmailsCheckBox.isChecked

        if (optedInToPromotionalEmails != sharedApplicationViewModel.optedInToPromotionalEmails) {
            sharedApplicationViewModel.setOptedInToPromotionalEmails(optedInToPromotionalEmails)
        }

        sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment = false

        //This Log statement is to make sure algorithmDataWasModified is used somewhere and avoid the warning.
        Log.i("modifyProfileScreen", "algorithmDataWasModified: $algorithmDataWasModified")

        //if(algorithmDataWasModified) {
        //  sharedApplicationViewModel.clearMatchesFromDatabase()
        //}
    }

    override fun onDestroyView() {
        applicationActivity = null
        _binding = null
        mostRecentlyAddedOtherGenderEditText = null

        super.onDestroyView()
    }

    private fun modifyCheckBoxes(add: Boolean) {

        if (add) {
            totalNumberGendersSelected++
        } else {
            totalNumberGendersSelected--
        }

        setupCheckBoxes(add)
    }

    private fun setupCheckBoxes(add: Boolean) {
        if (totalNumberGendersSelected == GlobalValues.server_imported_values.numberGenderUserCanMatchWith) {
            binding.modifyUserGenderRangeOtherErrorTextView.visibility = View.VISIBLE

            if (!binding.modifyUserGenderRangeMaleCheckBox.isChecked) {
                binding.modifyUserGenderRangeMaleCheckBox.isEnabled = false
            }
            if (!binding.modifyUserGenderRangeFemaleCheckBox.isChecked) {
                binding.modifyUserGenderRangeFemaleCheckBox.isEnabled = false
            }
        } else if (
            !add
            && totalNumberGendersSelected == GlobalValues.server_imported_values.numberGenderUserCanMatchWith - 1
            && !binding.modifyUserGenderRangeEveryoneCheckBox.isChecked
        ) {
            binding.modifyUserGenderRangeOtherErrorTextView.visibility = View.GONE

            binding.modifyUserGenderRangeMaleCheckBox.isEnabled = true
            binding.modifyUserGenderRangeFemaleCheckBox.isEnabled = true
        }
    }

    private fun expandItem(expand: Boolean, animate: Boolean) {
        if (animate) {
            val animator = getValueAnimator(
                expand,
                listItemExpandDuration,
                AccelerateDecelerateInterpolator()
            ) { progress -> setExpandProgress(expand, progress) }
            if (expand)
                animator.doOnStart {
                    binding.modifyUserGenderRangeOtherExpandLinearLayout.isVisible = true
                    animationRunning = false
                }
            else
                animator.doOnEnd {
                    binding.modifyUserGenderRangeOtherExpandLinearLayout.isVisible = false
                    animationRunning = false
                }

            animationRunning = true
            animator.start()
        } else {

            // show expandView only if we have expandedHeight (onViewAttached)
            binding.modifyUserGenderRangeOtherExpandLinearLayout.isVisible =
                expand && expandedHeight >= 0
            setExpandProgress(expand, if (expand) 1f else 0f)
        }
    }

    //this function will set the width, height color and rotation of the chevron to a % based on 'progress' variable
    //if the expandedHeight or originalHeight have not been initialized, it will not change height
    private fun setExpandProgress(expand: Boolean, progress: Float) {

        binding.modifyUserGenderRangeOtherLinearLayout.apply {
            if (expandedHeight > 0 && genderRangeGenderOtherOriginalHeight > 0) {
                val nextHeight =
                    (genderRangeGenderOtherOriginalHeight + (expandedHeight - genderRangeGenderOtherOriginalHeight) * progress).toInt()

                layoutParams.height = nextHeight

                this.doOnPreDraw {
                    //Make sure to bring the view fully into to the screen.
                    if (expand) {
                        val rect = Rect(0, 0, width, nextHeight)
                        requestRectangleOnScreen(rect, false)
                    }
                }
            }

            requestLayout()
        }

        binding.modifyUserGenderRangeOtherChevronImageView.rotation = 180 * progress
    }

}

