package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnAttach
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentStartStopDatetimeBinding
import site.letsgoapp.letsgo.utilities.CategoryTimeBlock
import site.letsgoapp.letsgo.utilities.convertDateToString
import site.letsgoapp.letsgo.utilities.convertTimeToString
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener
import java.util.*

class StartStopChooserDialog(
    private val categoryTimeBlock: CategoryTimeBlock,
    private var fragment: Fragment?
) : DialogFragment() {

    private var listener: MyStartStopPickerDialogListener? = null
    private var _binding: DialogFragmentStartStopDatetimeBinding? = null
    private val binding get() = _binding!!

    interface MyStartStopPickerDialogListener {
        fun onStartStopChooserDialogAcceptClick(inflatedView: View)
        fun onStartStopChooserDialogCancelClick(inflatedView: View)
        fun onStartStopChooserDialogStartDateClick()
        fun onStartStopChooserDialogStartTimeClick()
        fun onStartStopChooserDialogStopDateClick()
        fun onStartStopChooserDialogStopTimeClick()
        fun onStartStopChooserDialogDismissed()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = fragment as MyStartStopPickerDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement NoticeDialogListener")
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding =
                DialogFragmentStartStopDatetimeBinding.inflate(requireActivity().layoutInflater)

            builder.setView(binding.root)
            builder.setTitle(getString(R.string.start_stop_chooser_title))
            builder.setPositiveButton(getString(R.string.start_stop_chooser_positive_button)) { _, _ ->
                listener?.onStartStopChooserDialogAcceptClick(binding.root)
            }
            builder.setNegativeButton(getString(R.string.start_stop_chooser_negative_button)) { _, _ ->
                listener?.onStartStopChooserDialogCancelClick(binding.root)
            }

            binding.root.doOnAttach { view ->

                val viewBinding = DialogFragmentStartStopDatetimeBinding.bind(view)

                updateViewValue(categoryTimeBlock)

                viewBinding.selectStartDateLinearLayout.setSafeOnClickListener {
                    listener?.onStartStopChooserDialogStartDateClick()
                }
                viewBinding.selectStartTimeLinearLayout.setSafeOnClickListener {
                    listener?.onStartStopChooserDialogStartTimeClick()
                }
                viewBinding.selectStopDateLinearLayout.setSafeOnClickListener {
                    listener?.onStartStopChooserDialogStopDateClick()
                }
                viewBinding.selectStopTimeLinearLayout.setSafeOnClickListener {
                    listener?.onStartStopChooserDialogStopTimeClick()
                }

            }

            return builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    private fun updateViewValue(passedCategoryTimeBlock: CategoryTimeBlock) {

        val startTimeString = convertTimeToString(
            passedCategoryTimeBlock.startHour, passedCategoryTimeBlock.startMinute
        )

        val stopTimeString = convertTimeToString(
            passedCategoryTimeBlock.stopHour, passedCategoryTimeBlock.stopMinute
        )

        binding.setStartDateTextView.text = convertDateToString(
            passedCategoryTimeBlock.startYear,
            passedCategoryTimeBlock.startMonth,
            passedCategoryTimeBlock.startDay
        )
        binding.setStartTimeTextView.text = startTimeString
        binding.setStopDateTextView.text = convertDateToString(
            passedCategoryTimeBlock.stopYear,
            passedCategoryTimeBlock.stopMonth,
            passedCategoryTimeBlock.stopDay
        )
        binding.setStopTimeTextView.text = stopTimeString

        if (!runCalendarCheck()) {
            binding.enterStartStopDialogErrorMessageTextView.setText(R.string.start_stop_chooser_warning_invalid_time_frame)
        } else {
            binding.enterStartStopDialogErrorMessageTextView.text = ""
        }

    }

    //in case day changed while selecting this will re-do the date
    private fun runCalendarCheck(): Boolean {

        var timestampCalendar = GregorianCalendar(
            categoryTimeBlock.stopYear,
            categoryTimeBlock.stopMonth,
            categoryTimeBlock.stopDay,
            categoryTimeBlock.stopHour,
            categoryTimeBlock.stopMinute
        )

        categoryTimeBlock.stopTimeTimestamp = timestampCalendar.timeInMillis

        timestampCalendar = GregorianCalendar(
            categoryTimeBlock.startYear,
            categoryTimeBlock.startMonth,
            categoryTimeBlock.startDay,
            categoryTimeBlock.startHour,
            categoryTimeBlock.startMinute
        )

        categoryTimeBlock.startTimeTimestamp = timestampCalendar.timeInMillis

        if (categoryTimeBlock.startTimeTimestamp > categoryTimeBlock.stopTimeTimestamp) {
            return false
        }

        return true
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onStartStopChooserDialogDismissed()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener = null
        fragment = null
        _binding = null
    }
}