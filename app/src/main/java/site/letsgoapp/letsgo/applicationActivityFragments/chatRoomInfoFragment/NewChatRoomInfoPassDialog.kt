package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomInfoFragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentCreateNewChatRoomBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter

class NewChatRoomInfoPassDialog(
    private val title: String,
    private val editTextHint: String,
    private val originalNamePass: String,
    private val editable: Boolean,
    private var saveNewNamePass: ((String) -> Unit)?
) : DialogFragment() {

    private var _binding: DialogFragmentCreateNewChatRoomBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding =
                DialogFragmentCreateNewChatRoomBinding.inflate(requireActivity().layoutInflater)

            binding.singleLineEditTextDialogEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytes
                )
            )

            binding.singleLineEditTextDialogEditText.hint = editTextHint
            binding.singleLineEditTextDialogEditText.setText(originalNamePass)

            if (!editable) {
                binding.singleLineEditTextDialogEditText.isFocusable = false
                binding.singleLineEditTextDialogEditText.setBackgroundResource(android.R.color.transparent)
            }

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->

                val nameOrPass = binding.singleLineEditTextDialogEditText.text.toString()

                if (editable && originalNamePass != nameOrPass) {
                    saveNewNamePass?.let { it1 -> it1(nameOrPass) }
                }
            }

            if (editable) {
                builder.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.dismiss()
                }
            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    override fun onDismiss(dialog: DialogInterface) {
        //Some kind of know bug that leaks the activity in older Android versions if
        // this is not done to a TextView inside the dialog.
        // https://issuetracker.google.com/issues/37064488
        //isCursorVisible is only relevant to editable TextViews (click it for more info).
        binding.singleLineEditTextDialogEditText.isCursorVisible = false
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveNewNamePass = null
        _binding = null
    }
}