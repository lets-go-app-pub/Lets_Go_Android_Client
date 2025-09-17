package site.letsgoapp.letsgo.reUsedFragments.selectGenderFragment

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import site.letsgoapp.letsgo.databinding.FragmentSelectGenderBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*

class SelectGenderFragment : Fragment() {

    private var _binding: FragmentSelectGenderBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String

    private var centerViews = false

    private var onViewCreatedHasRun = false //will be set to true when onViewCreated runs

    private var setStringOnViewCreated =
        false //notifies the view to the gender to stringToSet when onViewCreated is called
    private var stringToSet =
        "" //a string that will be set onViewCreated used with setStringOnViewCreated

    private val _selectRadioButtonChanged: MutableLiveData<EventWrapper<Unit>> =
        MutableLiveData()
    val selectRadioButtonChanged: LiveData<EventWrapper<Unit>> =
        _selectRadioButtonChanged

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentSelectGenderBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginGetGenderMaleRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                _selectRadioButtonChanged.value = EventWrapper(Unit)
            }
        }

        binding.loginGetGenderFemaleRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                _selectRadioButtonChanged.value = EventWrapper(Unit)
            }
        }

        //NOTE: cannot make this a safe click listener because the values of the check boxes
        // can still change and all it will cause is for this to miss those changes
        binding.loginGetGenderOtherRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                _selectRadioButtonChanged.value = EventWrapper(Unit)
                binding.loginGetGenderEditText.visibility = View.VISIBLE
            } else {
                binding.loginGetGenderEditText.visibility = View.GONE
                hideKeyboard()
            }
        }

        if (centerViews) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }

            binding.loginGetGenderRadioGroup.layoutParams = params
            binding.loginGetGenderEditText.layoutParams = params
        }

        onViewCreatedHasRun = true

        if (setStringOnViewCreated) {
            setStringOnViewCreated = false
            setGenderByString(stringToSet)
        }

    }

    fun setGenderToCenterOfLayout() {
        centerViews = true
    }

    fun setGenderByString(gender: String) {

        if (!onViewCreatedHasRun) {
            setStringOnViewCreated = true
            stringToSet = gender
        } else {
            when (gender) {
                GlobalValues.MALE_GENDER_VALUE,
                "~" -> {
                    binding.loginGetGenderMaleRadioButton.isChecked = true
                    binding.loginGetGenderFemaleRadioButton.isChecked = false
                    binding.loginGetGenderOtherRadioButton.isChecked = false

                    binding.loginGetGenderEditText.visibility = View.GONE
                    binding.loginGetGenderEditText.text = null
                }
                GlobalValues.FEMALE_GENDER_VALUE -> {
                    binding.loginGetGenderMaleRadioButton.isChecked = false
                    binding.loginGetGenderFemaleRadioButton.isChecked = true
                    binding.loginGetGenderOtherRadioButton.isChecked = false

                    binding.loginGetGenderEditText.visibility = View.GONE
                    binding.loginGetGenderEditText.text = null
                }
                else -> {
                    binding.loginGetGenderMaleRadioButton.isChecked = false
                    binding.loginGetGenderFemaleRadioButton.isChecked = false
                    binding.loginGetGenderOtherRadioButton.isChecked = true

                    binding.loginGetGenderEditText.visibility = View.VISIBLE
                    binding.loginGetGenderEditText.setText(gender)
                }
            }
        }
    }

    fun getGenderString(): String {
        return when {
            binding.loginGetGenderMaleRadioButton.isChecked -> {
                GlobalValues.MALE_GENDER_VALUE
            }
            binding.loginGetGenderFemaleRadioButton.isChecked -> {
                GlobalValues.FEMALE_GENDER_VALUE
            }
            binding.loginGetGenderOtherRadioButton.isChecked -> {
                binding.loginGetGenderEditText.text.toString()
            }
            else -> {
                "~"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        onViewCreatedHasRun = false
        setStringOnViewCreated = false
        stringToSet = ""
    }

}