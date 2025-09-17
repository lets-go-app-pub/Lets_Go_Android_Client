package site.letsgoapp.letsgo.loginActivityFragments.loginGetNameFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.FirstNameDataHolder
import site.letsgoapp.letsgo.databinding.FragmentLoginGetNameBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.utilities.editTextFilters.LettersSpaceNumbersFilter

class LoginGetNameFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentLoginGetNameBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
    private lateinit var thisFragmentInstanceID: String

    private lateinit var setFieldsReturnValue: Observer<EventWrapperWithKeyString<SetFieldsReturnValues>>
    private lateinit var getNameFromDatabaseReturnValue: Observer<EventWrapperWithKeyString<FirstNameDataHolder>>

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginGetNameBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        if(initializeLoginActivity) {
            loginActivity = requireActivity() as LoginActivity
        }

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

        binding.getNameEditText.filters = arrayOf(
            ByteLengthFilter(GlobalValues.server_imported_values.maximumNumberAllowedBytesFirstName),
            LettersSpaceNumbersFilter(
                allowLowerCase = true,
                allowUpperCase = true
            )
        )

        setFieldsReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleSetNameReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.setFieldReturnValue.observe(viewLifecycleOwner, setFieldsReturnValue)

        getNameFromDatabaseReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleNameReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.returnFirstNameFromDatabase.observe(
            viewLifecycleOwner,
            getNameFromDatabaseReturnValue
        )

        when (sharedLoginViewModel.newAccountInfo.firstNameStatus) {
            //if first name 'Hard Set' (set by Facebook or Google) but not sent to the server, run this
            StatusOfClientValueEnum.HARD_SET -> {
                setLoadingState(true)
                sharedLoginViewModel.sendFirstName(thisFragmentInstanceID)
            }
            StatusOfClientValueEnum.UNSET -> { //if the email has not been set, set initial values so this can be navigated back to
                binding.loginGetNameErrorTextView.text = null
                sharedLoginViewModel.getNameFromDatabase(thisFragmentInstanceID)
            }
        }

        binding.getNameEditText.setSafeEditorActionClickListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val firstName = binding.getNameEditText.text.toString()
                val verificationReturnValues =
                    sharedLoginViewModel.verifyAndSaveFirstName(firstName)

                if (verificationReturnValues.first) { //if first name is valid
                    setLoadingState(true)
                    binding.getNameEditText.setText(verificationReturnValues.second)
                    this.hideKeyboard()
                    sharedLoginViewModel.sendFirstName(thisFragmentInstanceID)
                } else {
                    binding.loginGetNameErrorTextView.setText(R.string.get_first_name_invalid_name)
                }
                return@setSafeEditorActionClickListener true
            }
            return@setSafeEditorActionClickListener false
        }

        binding.getNameContinueButton.setSafeOnClickListener {
            val firstName = binding.getNameEditText.text.toString()
            val verificationReturnValues = sharedLoginViewModel.verifyAndSaveFirstName(firstName)

            if (verificationReturnValues.first) { //if first name is valid
                binding.getNameEditText.setText(verificationReturnValues.second)
                setLoadingState(true)
                sharedLoginViewModel.sendFirstName(thisFragmentInstanceID)
            } else {
                binding.loginGetNameErrorTextView.setText(R.string.get_first_name_invalid_name)
            }
        }
    }

    private fun handleNameReturnValue(firstNameInfo: FirstNameDataHolder) {

        val name =
            if (firstNameInfo.first_name_timestamp < 1L
                || firstNameInfo.first_name == "~"
            ) { //if the timestamp is -1 or the name is "~" set the text field to null
                null
            } else {
                firstNameInfo.first_name
            }

        binding.getNameEditText.setText(name)

        setLoadingState(false)
    }

    private fun handleSetNameReturnValue(returnValue: SetFieldsReturnValues) {

        binding.loginGetNameErrorTextView.text = null
        if (returnValue.invalidParameterPassed) { //server returned invalid name passed
            binding.loginGetNameErrorTextView.setText(R.string.get_first_name_invalid_name)
        } else if (returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            when (sharedLoginViewModel.newAccountInfo.firstNameStatus) {
                StatusOfClientValueEnum.HARD_SET -> {
                    loginActivity?.navigate(
                        R.id.loginGetNameFragment,
                        R.id.action_loginGetNameFragment_to_loginGetGenderFragment_pop_back_stack
                    )
                }
                else -> {
                    loginActivity?.navigate(
                        R.id.loginGetNameFragment,
                        R.id.action_loginGetNameFragment_to_loginGetGenderFragment
                    )
                }
            }
        }

        setLoadingState(false)

        loginActivity?.handleGrpcErrorStatusReturnValues(returnValue.errorStatus)
    }

    private fun setLoadingState(loading: Boolean) {

        if (loading) {

            binding.loginGetNameTitleTextView.visibility = View.GONE
            binding.getNameEditText.visibility = View.GONE
            binding.loginGetNameErrorTextView.visibility = View.GONE
            binding.getNameContinueButton.visibility = View.GONE

            binding.loginGetNameProgressBar.visibility = View.VISIBLE

        } else {

            binding.loginGetNameTitleTextView.visibility = View.VISIBLE
            binding.getNameEditText.visibility = View.VISIBLE
            binding.loginGetNameErrorTextView.visibility = View.VISIBLE
            binding.getNameContinueButton.visibility = View.VISIBLE

            binding.loginGetNameProgressBar.visibility = View.GONE

        }

    }

}
