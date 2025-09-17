package site.letsgoapp.letsgo.applicationActivityFragments.messengerScreenFragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentCreateNewChatRoomBinding
import site.letsgoapp.letsgo.databinding.DialogFragmentJoinChatRoomBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.editTextFilters.ByteLengthFilter
import site.letsgoapp.letsgo.utilities.editTextFilters.LettersSpaceNumbersFilter

class CreateNewChatRoomTextDialog(
    private val title: String,
    private val editTextHint: String,
    private var createChatRoom: ((String) -> Unit)?
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
            _binding = DialogFragmentCreateNewChatRoomBinding.inflate(requireActivity().layoutInflater)

            binding.singleLineEditTextDialogEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytes
                )
            )

            binding.singleLineEditTextDialogEditText.hint = editTextHint

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                createChatRoom?.let { it1 ->
                    it1(
                        binding.singleLineEditTextDialogEditText.text.toString()
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
        binding.singleLineEditTextDialogEditText.isCursorVisible = false
        super.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        createChatRoom = null
        _binding = null
    }
}

class JoinChatRoomTextDialog(
    private val title: String,
    private val chatRoomIdEditTextHint: String,
    private val chatRoomPassEditTextHint: String,
    private val passedChatRoomId: String,
    private val passedChatRoomPassword: String,
    private var joinChatRoom: ((String, String) -> Unit)?
) : DialogFragment() {

    private var _binding: DialogFragmentJoinChatRoomBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return activity?.let {

            val builder = AlertDialog.Builder(it)
            _binding = DialogFragmentJoinChatRoomBinding.inflate(requireActivity().layoutInflater)

            binding.joinChatRoomDialogChatRoomIdEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberChatRoomIdChars
                ),
                LettersSpaceNumbersFilter(
                    allowLowerCase = true,
                    allowNumbers = true
                )
            )

            binding.joinChatRoomDialogChatRoomIdEditText.hint = chatRoomIdEditTextHint
            binding.joinChatRoomDialogChatRoomIdEditText.setText(passedChatRoomId)

            binding.joinChatRoomDialogChatRoomPassEditText.filters = arrayOf(
                ByteLengthFilter(
                    GlobalValues.server_imported_values.maximumNumberAllowedBytes
                )
            )

            binding.joinChatRoomDialogChatRoomPassEditText.hint = chatRoomPassEditTextHint
            binding.joinChatRoomDialogChatRoomPassEditText.setText(passedChatRoomPassword)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
            builder.setTitle(title)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                joinChatRoom?.let { it1 ->
                    it1(
                        binding.joinChatRoomDialogChatRoomIdEditText.text.toString(),
                        binding.joinChatRoomDialogChatRoomPassEditText.text.toString()
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
        binding.joinChatRoomDialogChatRoomIdEditText.isCursorVisible = false
        binding.joinChatRoomDialogChatRoomPassEditText.isCursorVisible = false
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        joinChatRoom = null
        _binding = null
    }
}