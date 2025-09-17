package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R

class SelectChoicesDialog(
    private val title: String,
    private val arrayString: Array<String>,
    private val fragment: Fragment,
    private var okButtonAction: ((DialogInterface, Int) -> Unit)?
) : DialogFragment() {

    private var listener: SelectChoicesDialogListener? = null

    interface SelectChoicesDialogListener {
        fun onSelectChoiceDismissed()
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
            listener = fragment as SelectChoicesDialogListener
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
            builder.setTitle(title)
            builder.setItems(arrayString, okButtonAction)
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onSelectChoiceDismissed()
    }

    override fun onDestroy() {
        super.onDestroy()
        okButtonAction = null
        listener = null
    }
}