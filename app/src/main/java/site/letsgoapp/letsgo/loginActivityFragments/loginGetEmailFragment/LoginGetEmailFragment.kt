package site.letsgoapp.letsgo.loginActivityFragments.loginGetEmailFragment

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
import site.letsgoapp.letsgo.databinding.FragmentLoginGetEmailBinding
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.utilities.*

class LoginGetEmailFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentLoginGetEmailBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
    private lateinit var thisFragmentInstanceID: String

    private lateinit var setFieldsReturnValue: Observer<EventWrapperWithKeyString<SetFieldsReturnValues>>
    private lateinit var getEmailFromDatabaseReturnValue: Observer<EventWrapperWithKeyString<ReturnEmailFromDatabaseDataHolder>>

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginGetEmailBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        sharedLoginViewModel.navigatePastLoginSelectFragment =
            SharedLoginViewModel.NavigatePastLoginSelectFragment.NO_NAVIGATION

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

        setFieldsReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleSetEmailReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.setFieldReturnValue.observe(viewLifecycleOwner, setFieldsReturnValue)

        getEmailFromDatabaseReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleEmailReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.returnEmailFromDatabase.observe(
            viewLifecycleOwner,
            getEmailFromDatabaseReturnValue
        )

        when (sharedLoginViewModel.newAccountInfo.emailAddressStatus) {
            //if email address was 'Hard Set' (set by Facebook or Google) but not sent to the server, run this
            StatusOfClientValueEnum.HARD_SET -> {
                setLoadingState(true)
                sharedLoginViewModel.sendEmailAddressInfo(thisFragmentInstanceID)
            }
            StatusOfClientValueEnum.UNSET -> { //if the email has not been set, set initial values so this can be navigated back to
                binding.loginGetEmailErrorTextView.text = null
                sharedLoginViewModel.getEmailAddressFromDatabase(thisFragmentInstanceID)
            }
        }

        binding.getEmailEditText.setSafeEditorActionClickListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.loginGetEmailErrorTextView.text = null

                val emailAddress = binding.getEmailEditText.text.toString()

                if (sharedLoginViewModel.verifyAndSaveEmailAddress(emailAddress)) { //if email is valid
                    setLoadingState(true)
                    this.hideKeyboard()
                    sharedLoginViewModel.sendEmailAddressInfo(thisFragmentInstanceID)
                } else { //if email is invalid
                    binding.loginGetEmailErrorTextView.setText(R.string.get_email_invalid_email)
                }
                return@setSafeEditorActionClickListener true
            }
            return@setSafeEditorActionClickListener false
        }

        binding.getEmailContinueButton.setSafeOnClickListener {

            binding.loginGetEmailErrorTextView.text = null

            val emailAddress = binding.getEmailEditText.text.toString()

            if (sharedLoginViewModel.verifyAndSaveEmailAddress(emailAddress)) { //if email is valid
                setLoadingState(true)
                sharedLoginViewModel.sendEmailAddressInfo(thisFragmentInstanceID)
            } else { //if email is invalid
                binding.loginGetEmailErrorTextView.setText(R.string.get_email_invalid_email)
            }

        }

    }

    private fun handleEmailReturnValue(returnValue: ReturnEmailFromDatabaseDataHolder) {
        //first is email address
        //second is if requires verification
        //third is timestamp
        var emailAddress = returnValue.email

        emailAddress?.let {
            if (emailAddress == "~") {
                emailAddress = null
            }
        }

        binding.getEmailEditText.setText(emailAddress)

        setLoadingState(false)
    }

    private fun handleSetEmailReturnValue(returnValue: SetFieldsReturnValues) {

        binding.loginGetEmailErrorTextView.text = null
        if (returnValue.invalidParameterPassed) { //server returned invalid email passed
            binding.loginGetEmailErrorTextView.setText(R.string.get_email_invalid_email)
        } else if (returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            when (sharedLoginViewModel.newAccountInfo.emailAddressStatus) {
                StatusOfClientValueEnum.HARD_SET -> {
                    loginActivity?.navigate(
                        R.id.loginGetEmailFragment,
                        R.id.action_loginGetEmailFragment_to_loginShowRulesFragment_pop_back_stack
                    )
                }
                else -> {
                    loginActivity?.navigate(
                        R.id.loginGetEmailFragment,
                        R.id.action_loginGetEmailFragment_to_loginShowRulesFragment
                    )
                }
            }
        }

        setLoadingState(false)

        loginActivity?.handleGrpcErrorStatusReturnValues(returnValue.errorStatus)
    }

    private fun setLoadingState(loading: Boolean) {

        if (loading) {

            binding.getEmailEditText.visibility = View.GONE
            binding.loginGetEmailErrorTextView.visibility = View.GONE
            binding.getEmailContinueButton.visibility = View.GONE
            binding.loginGetEmailTitleTextView.visibility = View.GONE

            binding.loginGetEmailProgressBar.visibility = View.VISIBLE

        } else {

            binding.getEmailEditText.visibility = View.VISIBLE
            binding.loginGetEmailErrorTextView.visibility = View.VISIBLE
            binding.getEmailContinueButton.visibility = View.VISIBLE
            binding.loginGetEmailTitleTextView.visibility = View.VISIBLE

            binding.loginGetEmailProgressBar.visibility = View.GONE

        }

    }

}
