package site.letsgoapp.letsgo.loginActivityFragments.loginGetGenderFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.GenderDataHolder
import site.letsgoapp.letsgo.databinding.FragmentLoginGetGenderBinding
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.reUsedFragments.selectGenderFragment.SelectGenderFragment
import site.letsgoapp.letsgo.utilities.*

class LoginGetGenderFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentLoginGetGenderBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
    private lateinit var thisFragmentInstanceID: String

    private lateinit var setFieldsReturnValue: Observer<EventWrapperWithKeyString<SetFieldsReturnValues>>
    private lateinit var getGenderFromDatabaseReturnValue: Observer<EventWrapperWithKeyString<GenderDataHolder>>

    private lateinit var selectGenderRadioButtonChanged: Observer<EventWrapper<Unit>>

    private lateinit var selectGenderFragment: SelectGenderFragment

    private var loginActivity: LoginActivity? = null

    private lateinit var invalidGenderErrorMessage: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginGetGenderBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)
        invalidGenderErrorMessage = resources.getString(R.string.get_gender_invalid_gender)
        loginActivity?.setHalfGlobeImagesDisplayed(true)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        loginActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(initializeLoginActivity) {
            loginActivity = requireActivity() as LoginActivity
        }

        setFieldsReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleSetGenderReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.setFieldReturnValue.observe(viewLifecycleOwner, setFieldsReturnValue)

        getGenderFromDatabaseReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleGenderReturnValue(returnValue)
            }
        }

        selectGenderFragment =
            childFragmentManager.findFragmentById(R.id.loginGetGenderSelectGenderFragment) as SelectGenderFragment
        selectGenderFragment.setGenderToCenterOfLayout()

        binding.getGenderContinueButton.setSafeOnClickListener {
            val verificationReturnValues =
                sharedLoginViewModel.verifyAndSaveGender(selectGenderFragment.getGenderString())
            if (verificationReturnValues) { //if gender is valid
                setLoadingState(true)
                sharedLoginViewModel.sendGenderInfo(thisFragmentInstanceID)
            } else {
                binding.loginGetGenderErrorTextView.text = invalidGenderErrorMessage
            }
        }

        sharedLoginViewModel.returnGenderFromDatabase.observe(
            viewLifecycleOwner,
            getGenderFromDatabaseReturnValue
        )

        selectGenderRadioButtonChanged = Observer { wrapper ->
            val result = wrapper.getContentIfNotHandled()
            if (result != null
                && invalidGenderErrorMessage == binding.loginGetGenderErrorTextView.text
            ) {
                //if invalidGenderErrorMessage was displayed, remove it when new radio button is selected
                binding.loginGetGenderErrorTextView.text = null
            }
        }

        selectGenderFragment.selectRadioButtonChanged.observe(
            viewLifecycleOwner,
            selectGenderRadioButtonChanged
        )

        when (sharedLoginViewModel.newAccountInfo.genderStatus) {
            //if birthday 'Hard Set' (set by Facebook or Google) but not sent to the server, run this
            StatusOfClientValueEnum.HARD_SET -> {
                setLoadingState(true)
                sharedLoginViewModel.sendGenderInfo(thisFragmentInstanceID)
            }
            StatusOfClientValueEnum.UNSET -> { //if the email has not been set, set initial values so this can be navigated back to
                binding.loginGetGenderErrorTextView.text = null
                sharedLoginViewModel.getGenderFromDatabase(thisFragmentInstanceID)
            }
        }
    }

    private fun handleGenderReturnValue(genderDataHolder: GenderDataHolder) {
        selectGenderFragment.setGenderByString(genderDataHolder.gender)
        setLoadingState(false)
    }

    private fun handleSetGenderReturnValue(returnValue: SetFieldsReturnValues) {

        binding.loginGetGenderErrorTextView.text = null
        if (returnValue.invalidParameterPassed) { //server returned invalid gender passed
            binding.loginGetGenderErrorTextView.setText(R.string.get_gender_invalid_gender)
        } else if (returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            when (sharedLoginViewModel.newAccountInfo.genderStatus) {
                StatusOfClientValueEnum.HARD_SET -> {
                    loginActivity?.navigate(
                        R.id.loginGetGenderFragment,
                        R.id.action_loginGetGenderFragment_to_loginGetPicturesFragment_pop_back_stack
                    )
                }
                else -> {
                    loginActivity?.navigate(
                        R.id.loginGetGenderFragment,
                        R.id.action_loginGetGenderFragment_to_loginGetPicturesFragment
                    )
                }
            }
        }

        setLoadingState(false)

        loginActivity?.handleGrpcErrorStatusReturnValues(returnValue.errorStatus)
    }

    private fun setLoadingState(loading: Boolean) {

        if (loading) {

            binding.loginGetGenderTitleTextView.visibility = View.GONE
            binding.loginGetGenderSelectGenderFragment.visibility = View.GONE
            binding.getGenderContinueButton.visibility = View.GONE

            binding.loginGetGenderProgressBar.visibility = View.VISIBLE

        } else {

            binding.loginGetGenderTitleTextView.visibility = View.VISIBLE
            binding.loginGetGenderSelectGenderFragment.visibility = View.VISIBLE
            binding.getGenderContinueButton.visibility = View.VISIBLE

            binding.loginGetGenderProgressBar.visibility = View.GONE

        }

    }

}
