package site.letsgoapp.letsgo.loginActivityFragments.loginGetBirthdayFragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginGetBirthdayBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.datePickerEditText.BirthdayPickerDialogWrapper


class LoginGetBirthdayFragment(
    private val initializeLoginActivity: Boolean = true,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment() {

    private var _binding: FragmentLoginGetBirthdayBinding? = null
    private val binding get() = _binding!!

    private val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer)
    private lateinit var thisFragmentInstanceID: String

    private lateinit var setFieldsReturnValue: Observer<EventWrapperWithKeyString<SetFieldsReturnValues>>
    private lateinit var getBirthdayFromDatabaseReturnValue: Observer<EventWrapperWithKeyString<BirthdayHolder>>

    private var loginActivity: LoginActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var handleBirthdayPickerDialog: BirthdayPickerDialogWrapper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginGetBirthdayBinding.inflate(inflater, container, false)
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

        handleBirthdayPickerDialog = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handleBirthdayPickerDialog = BirthdayPickerDialogWrapper(
            binding.getBirthdayEditText,
            parentFragmentManager,
            binding.loginGetBirthdayErrorTextView
        )

        setFieldsReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleSetBirthdayReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.setFieldReturnValue.observe(viewLifecycleOwner, setFieldsReturnValue)

        getBirthdayFromDatabaseReturnValue = Observer { returnValueEvent ->
            val returnValue = returnValueEvent.getContentIfNotHandled(thisFragmentInstanceID)
            if (returnValue != null) {
                handleBirthdayReturnValue(returnValue)
            }
        }

        sharedLoginViewModel.returnBirthdayFromDatabase.observe(
            viewLifecycleOwner,
            getBirthdayFromDatabaseReturnValue
        )

        when (sharedLoginViewModel.newAccountInfo.birthDayStatus) {
            //if birthday 'Hard Set' (set by Facebook or Google) but not sent to the server, run this
            StatusOfClientValueEnum.HARD_SET -> {
                setLoadingState(true)

                val age = calcPersonAge(
                    GlobalValues.applicationContext,
                    sharedLoginViewModel.newAccountInfo.birthYear,
                    sharedLoginViewModel.newAccountInfo.birthMonth,
                    sharedLoginViewModel.newAccountInfo.birthDayOfMonth,
                    errorStore
                )

                setBirthdayOnServer(age)
            }
            StatusOfClientValueEnum.UNSET -> { //if the birthday has not been set, set initial values so this can be navigated back to
                binding.loginGetBirthdayErrorTextView.text = null
                sharedLoginViewModel.getBirthdayFromDatabase(thisFragmentInstanceID)
            }
        }

        binding.getBirthdayContinueButton.setSafeOnClickListener {
            binding.loginGetBirthdayErrorTextView.text = null
            val birthday = binding.getBirthdayEditText.text.toString()

            val birthdayResult = sharedLoginViewModel.saveBirthday(
                birthday,
                true
            )

            if (birthdayResult.age > 0) { //if birthday is valid, or it is too large or too small
                setBirthdayOnServer(birthdayResult.age)
            } else { //birthday is invalid
                binding.loginGetBirthdayErrorTextView.setText(R.string.get_birthday_invalid_birthday)
            }
        }


    }

    override fun onStart() {
        super.onStart()
        //clear edit text, this will handle cases where the fragment (or activity) are destroyed
        // and re-created
        binding.getBirthdayEditText.text = null
    }

    private fun handleBirthdayReturnValue(birthday: BirthdayHolder) {
        if (birthday.birth_month != -1 && birthday.birth_day_of_month != -1 && birthday.birth_year != -1) {
            handleBirthdayPickerDialog?.setupBirthdayEditText(birthday.birth_year, birthday.birth_month - 1, birthday.birth_day_of_month)
        }

        setLoadingState(false)
    }

    private fun setBirthdayOnServer(age: Int) {

        Log.i("bDayFrag", "age: $age lowestAllowedAge: ${GlobalValues.server_imported_values.lowestAllowedAge} highestAllowedAge: ${GlobalValues.server_imported_values.highestAllowedAge}")

        if (GlobalValues.server_imported_values.lowestAllowedAge <= age && age <= GlobalValues.server_imported_values.highestAllowedAge) {
            setLoadingState(true)
            sharedLoginViewModel.sendBirthdayInfo(thisFragmentInstanceID)
        } else if (age < GlobalValues.server_imported_values.lowestAllowedAge) { //if person is younger than 13 years old, do not allow them in the app

            setLoadingState(false)
            val alertDialog =
                ErrorAlertDialogFragment(
                    getString(R.string.underage_dialog_title),
                    getString(
                        R.string.underage_dialog_body,
                        GlobalValues.server_imported_values.lowestAllowedAge
                    )
                ) { _, _ ->
                    loginActivity?.navigateToSelectMethodAndClearBackStack()
                }
            alertDialog.isCancelable = false
            alertDialog.show(childFragmentManager, "fragment_alert_birthday_error")
        } else { //if person is older than 120 years old
            setLoadingState(false)

            binding.loginGetBirthdayErrorTextView.setText(R.string.get_birthday_invalid_birthday)
        }
    }

    private fun handleSetBirthdayReturnValue(returnValue: SetFieldsReturnValues) {

        binding.loginGetBirthdayErrorTextView.text = null
        if (returnValue.invalidParameterPassed) { //server returned invalid birthday passed
            binding.loginGetBirthdayErrorTextView.setText(R.string.get_birthday_invalid_birthday)
        } else if (returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            when (sharedLoginViewModel.newAccountInfo.birthDayStatus) {
                StatusOfClientValueEnum.HARD_SET -> {
                    loginActivity?.navigate(
                        R.id.loginGetBirthdayFragment,
                        R.id.action_loginGetBirthdayFragment_to_loginGetNameFragment_pop_back_stack
                    )
                }
                else -> {
                    loginActivity?.navigate(
                        R.id.loginGetBirthdayFragment,
                        R.id.action_loginGetBirthdayFragment_to_loginGetNameFragment
                    )
                }
            }
        }

        setLoadingState(false)

        loginActivity?.handleGrpcErrorStatusReturnValues(returnValue.errorStatus)
    }

    private fun setLoadingState(loading: Boolean) {

        if (loading) {

            binding.loginGetBirthdayTitleTextView.visibility = View.GONE
            binding.getBirthdayEditText.visibility = View.GONE
            binding.loginGetBirthdayErrorTextView.visibility = View.GONE
            binding.getBirthdayContinueButton.visibility = View.GONE

            binding.loginGetBirthdayProgressBar.visibility = View.VISIBLE

        } else {

            binding.loginGetBirthdayTitleTextView.visibility = View.VISIBLE
            binding.getBirthdayEditText.visibility = View.VISIBLE
            binding.loginGetBirthdayErrorTextView.visibility = View.VISIBLE
            binding.getBirthdayContinueButton.visibility = View.VISIBLE

            binding.loginGetBirthdayProgressBar.visibility = View.GONE

        }

    }

}
