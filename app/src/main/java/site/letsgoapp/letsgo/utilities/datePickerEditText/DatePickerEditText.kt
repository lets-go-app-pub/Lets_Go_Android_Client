package site.letsgoapp.letsgo.utilities.datePickerEditText

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnAttach
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentDatePickerSpinnerBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.getCurrentTimestampInMillis
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors
import java.text.SimpleDateFormat
import java.util.*

/** This class will set minDate and maxDate on the DialogFragmentDatePickerSpinnerBinding when inflated. It will
 * be possible that this is called from the VerifyPhoneNumbersFragment, so the time may not be initialized through
 * logging in. When this situation occurs, it will fall back on the device time to set min and max times. **/
//Uses a DatePicker as a spinner. Meant to be attached to selections which need to choose a birthday.
//Default values for initialize times expected to be -1.
class BirthdayDatePickerDialog(
    private var initializeYear: Int,
    private var initializeMonth: Int,
    private var initializeDayOfMonth: Int,
    private var positiveButtonLambda: ((Int, Int, Int) -> Unit)?
) : DialogFragment() {

    var minDayOfMonth: Int = -1
        @VisibleForTesting set
    var minMonth: Int = -1
        @VisibleForTesting set
    var minYear: Int = -1
        @VisibleForTesting set

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    private fun calculateMinMaxForCalendar(): Pair<Long, Long> {
        val possibleCurrentTime = getCurrentTimestampInMillis()
        val currentTime = if (possibleCurrentTime < 1) Date().time else possibleCurrentTime

        val currentDateCalendar = Calendar.getInstance()
        currentDateCalendar.timeInMillis = currentTime

        if (initializeYear == -1) {
            initializeYear = currentDateCalendar[Calendar.YEAR]
            initializeMonth = currentDateCalendar[Calendar.MONTH]
            initializeDayOfMonth = currentDateCalendar[Calendar.DAY_OF_MONTH]
        }

        currentDateCalendar.add(Calendar.DAY_OF_MONTH, 1)
        currentDateCalendar.add(Calendar.SECOND, 1)
        //The +1 is because the age 120 is allowed, so the day the would be 121 is disallowed.
        currentDateCalendar.add(
            Calendar.YEAR,
            -1 * (GlobalValues.server_imported_values.highestAllowedAge + 1)
        )

        minDayOfMonth = currentDateCalendar.get(Calendar.DAY_OF_MONTH)
        minMonth = currentDateCalendar.get(Calendar.MONTH)
        minYear = currentDateCalendar.get(Calendar.YEAR)

        return Pair(currentDateCalendar.timeInMillis, currentTime)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            val binding =
                DialogFragmentDatePickerSpinnerBinding.inflate(requireActivity().layoutInflater)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(R.string.set_phone_dialog_select_birthday_title)
            builder.setPositiveButton(getString(R.string.my_date_picker_positive_button)) { _, _ ->
                positiveButtonLambda?.invoke(
                    binding.datePickerDatePicker.year,
                    binding.datePickerDatePicker.month,
                    binding.datePickerDatePicker.dayOfMonth
                )
            }
            builder.setNegativeButton(getString(R.string.my_date_picker_negative_button)) { _, _ ->
            }

            binding.root.doOnAttach { view ->
                val viewBinding = DialogFragmentDatePickerSpinnerBinding.bind(view)

                //Must be done before updateDate(), because this function calculates initialize times if they are not set.
                val (minTime, maxTime) = calculateMinMaxForCalendar()

                //initialize calendar to the selected day
                //do this before setting min and max days
                viewBinding.datePickerDatePicker.updateDate(
                    initializeYear,
                    initializeMonth,
                    initializeDayOfMonth
                )

                viewBinding.datePickerDatePicker.minDate = minTime
                viewBinding.datePickerDatePicker.maxDate = maxTime
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDestroy() {
        super.onDestroy()
        positiveButtonLambda = null
    }
}

//Wrapper for handling BirthdayDatePickerDialog().
class BirthdayPickerDialogWrapper(
    private var birthdayEditText: EditText,
    private val fragmentManager: FragmentManager,
    private val birthdayErrorTextView: TextView? = null
) {

    private val mSimpleDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    private var initializeYear: Int = -1
    private var initializeMonth: Int = -1
    private var initializeDayOfMonth: Int = -1

    init {
        birthdayEditText.inputType = InputType.TYPE_NULL
        birthdayEditText.isFocusable = false
        birthdayEditText.isLongClickable = false
        birthdayEditText.onFocusChangeListener =
            OnFocusChangeListener { _: View?, b: Boolean ->
                if (b) {
                    setupAndShowBirthdayPickerDialog()
                }
            }
        birthdayEditText.setOnClickListener {
            setupAndShowBirthdayPickerDialog()
        }
    }

    private fun setupAndShowBirthdayPickerDialog() {
        val birthdayDatePickerDialog = BirthdayDatePickerDialog(
            initializeYear,
            initializeMonth,
            initializeDayOfMonth,
        ) { year, month, dayOfMonth ->
            if(year > -1 && month > -1 && dayOfMonth > -1) {
                birthdayErrorTextView?.text = null
                setupBirthdayEditText(year, month, dayOfMonth)
            } else {
                val errorMessage = "Invalid year month and dayOfMonth passed back from BirthdayDatePickerDialog()\n" +
                        "year: $year" +
                        "month: $month" +
                        "dayOfMonth: $dayOfMonth" +
                        "initializeYear: $initializeYear" +
                        "initializeMonth: $initializeMonth" +
                        "initializeDayOfMonth: $initializeDayOfMonth"

                ServiceLocator.globalErrorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
                birthdayErrorTextView?.setText(R.string.get_birthday_invalid_birthday)
            }
        }
        birthdayDatePickerDialog.show(fragmentManager, "birthday_date_picker_dialog")
    }

    //Values expected to be valid.
    fun setupBirthdayEditText(year: Int, month: Int, dayOfMonth: Int) {
        initializeYear = year
        initializeMonth = month
        initializeDayOfMonth = dayOfMonth

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        birthdayEditText.setText(mSimpleDateFormat.format(calendar.time))
    }
}
