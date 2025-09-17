package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnAttach
import androidx.fragment.app.DialogFragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentDatePickerCalendarBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.addStartAndStopTimeToActivityTimes
import site.letsgoapp.letsgo.utilities.getCalendarFromServerTimestamp
import java.util.*

class MyDatePickerDialog(
    private val title: String,
    private val isStartDate: Boolean,
    private val initializeYear: Int,
    private val initializeMonth: Int,
    private val initializeDayOfMonth: Int,
    private var fragment: MyDatePickerDialogListener?
) : DialogFragment() {

    private var listener: MyDatePickerDialogListener? = null

    interface MyDatePickerDialogListener {
        fun onDateDialogPositiveClick(datePickerBinding: DialogFragmentDatePickerCalendarBinding, isStartDate: Boolean)
        fun onDateDialogNegativeClick()
        fun onDateDialogDismissed()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = fragment
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException((context.toString() +
                    " must implement NoticeDialogListener"))
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            val binding = DialogFragmentDatePickerCalendarBinding.inflate(requireActivity().layoutInflater)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(getString(R.string.my_date_picker_positive_button)) { _, _ ->
                listener?.onDateDialogPositiveClick(binding, isStartDate)
            }
            builder.setNegativeButton(getString(R.string.my_date_picker_negative_button)) { _, _ ->
                listener?.onDateDialogNegativeClick()
            }

            binding.root.doOnAttach { view ->
                val viewBinding = DialogFragmentDatePickerCalendarBinding.bind(view)

                //initialize calendar to the selected day
                //do this before setting min and max days
                viewBinding.datePickerDatePicker.updateDate(initializeYear, initializeMonth, initializeDayOfMonth)

                val calendar = getCalendarFromServerTimestamp()

                //add the minimum start time for matching (this is the value that the server will ignore)
                addStartAndStopTimeToActivityTimes(calendar)

                calendar.set(Calendar.HOUR_OF_DAY, calendar.getMinimum(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, calendar.getMinimum(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, calendar.getMinimum(Calendar.SECOND))
                calendar.set(Calendar.MILLISECOND, calendar.getMinimum(Calendar.MILLISECOND))
                viewBinding.datePickerDatePicker.minDate = calendar.timeInMillis
                //This converts to seconds to prevent the long from possibly overflowing the int
                calendar.add(Calendar.SECOND,
                    (GlobalValues.server_imported_values.timeAvailableToSelectTimeFrames/1000).toInt())
                viewBinding.datePickerDatePicker.maxDate = calendar.timeInMillis
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onDateDialogDismissed()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener = null
        fragment = null
    }
}
