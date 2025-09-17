package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnAttach
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentTimePickerBinding
import site.letsgoapp.letsgo.utilities.addStartAndStopTimeToActivityTimes
import site.letsgoapp.letsgo.utilities.getCalendarFromServerTimestamp
import java.util.*

class MyTimePickerDialog(
    private val title: String,
    private val isStartTime: Boolean,
    private val initializeHour: Int,
    private val initializeMinute: Int,
    private val dateIsToday: Boolean,
    private var fragment: Fragment?
) : DialogFragment() {

    private var listener: MyTimePickerStartDialogListener? = null

    interface MyTimePickerStartDialogListener {
        fun onTimeDialogPositiveClick(
            timePickerBinding: DialogFragmentTimePickerBinding,
            isStartTime: Boolean
        )

        fun onTimeDialogNegativeClick()
        fun onTimeDialogDismissed()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = fragment as MyTimePickerStartDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement NoticeDialogListener")
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            val binding = DialogFragmentTimePickerBinding.inflate(requireActivity().layoutInflater)

            builder.setView(binding.root)
            builder.setPositiveButton(getString(R.string.my_time_picker_positive_button)) { _, _ ->
                listener?.onTimeDialogPositiveClick(binding, isStartTime)
            }
            builder.setNegativeButton(getString(R.string.my_time_picker_negative_button)) { _, _ ->
                listener?.onTimeDialogNegativeClick()
            }
            builder.setTitle(title)

            binding.root.doOnAttach { thisView ->

                val viewBinding = DialogFragmentTimePickerBinding.bind(thisView)

                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //API 18-22
                    viewBinding.timePickerSpinnerTimePicker.currentHour = initializeHour
                    viewBinding.timePickerSpinnerTimePicker.currentMinute = initializeMinute
                } else { //API 23-current
                    viewBinding.timePickerSpinnerTimePicker.hour = initializeHour
                    viewBinding.timePickerSpinnerTimePicker.minute = initializeMinute
                }

                viewBinding.timePickerSpinnerTimePicker.setOnTimeChangedListener { listenerView, hourOfDay, minute ->

                    if (dateIsToday) {

                        val calendar = getCalendarFromServerTimestamp()

                        addStartAndStopTimeToActivityTimes(calendar)

                        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val currentMinute = calendar.get(Calendar.MINUTE)
                        if (hourOfDay < currentHour) {
                            @Suppress("DEPRECATION")
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //API 18-22
                                listenerView.currentHour =
                                    calendar.get(Calendar.HOUR_OF_DAY)
                                listenerView.currentMinute =
                                    calendar.get(Calendar.MINUTE)
                            } else { //API 23-current
                                listenerView.hour =
                                    calendar.get(Calendar.HOUR_OF_DAY)
                                listenerView.minute =
                                    calendar.get(Calendar.MINUTE)
                            }
                        } else if (currentHour == hourOfDay && minute < currentMinute) {
                            @Suppress("DEPRECATION")
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //API 18-22
                                listenerView.currentMinute =
                                    calendar.get(Calendar.MINUTE)
                            } else { //API 23-current
                                listenerView.minute =
                                    calendar.get(Calendar.MINUTE)
                            }
                        }
                    }
                }
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onTimeDialogDismissed()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener = null
        fragment = null
    }
}