package site.letsgoapp.letsgo.applicationActivityFragments.profileScreenFragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFeedbackActivitySuggestionBinding
import site.letsgoapp.letsgo.databinding.DialogFeedbackEditTextBinding
import site.letsgoapp.letsgo.databinding.DialogFragmentJoinChatRoomBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter

class FeedbackActivitySuggestionDialog(
    private val title: String,
    private var sendActivitySuggestionFeedback: ((String, String) -> Unit)?
) : DialogFragment() {

    private var _binding: DialogFeedbackActivitySuggestionBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding =
                DialogFeedbackActivitySuggestionBinding.inflate(requireActivity().layoutInflater)

            binding.dialogFeedbackActivitySuggestionNameEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytes
                )
            )

            binding.dialogFeedbackActivitySuggestionOtherEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytesUserFeedback
                )
            )

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                sendActivitySuggestionFeedback?.let { it1 ->
                    it1(
                        binding.dialogFeedbackActivitySuggestionNameEditText.text.toString(),
                        binding.dialogFeedbackActivitySuggestionOtherEditText.text.toString()
                    )
                }
            }
            builder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        binding.dialogFeedbackActivitySuggestionNameEditText.isCursorVisible = false
        binding.dialogFeedbackActivitySuggestionOtherEditText.isCursorVisible = false
        super.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        sendActivitySuggestionFeedback = null
        _binding = null
    }
}

class FeedbackTextEditDialog(
    private val title: String,
    private val editTextHint: String,
    private var sendFeedback: ((String) -> Unit)?
) : DialogFragment() {

    private var _binding: DialogFeedbackEditTextBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding = DialogFeedbackEditTextBinding.inflate(requireActivity().layoutInflater)

            binding.dialogFeedbackActivitySuggestionOtherEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytesUserFeedback
                )
            )

            binding.dialogFeedbackActivitySuggestionOtherEditText.hint = editTextHint

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                sendFeedback?.let { it1 ->
                    it1(
                        binding.dialogFeedbackActivitySuggestionOtherEditText.text.toString()
                    )
                }
            }
            builder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        binding.dialogFeedbackActivitySuggestionOtherEditText.isCursorVisible = false
        super.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        sendFeedback = null
        _binding = null
    }
}