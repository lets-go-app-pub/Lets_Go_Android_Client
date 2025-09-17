package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnAttach
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.DialogFragmentStoredUserActivityBinding
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*

class StoredIconDataDialog(
    private val timeFrames: MutableList<LetsGoActivityTimeFrame>,
    private val activityIndex: Int,
    private val activitiesOrderedByCategoryReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.CategoryActivities>,
    private val allActivitiesReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.MutableActivityPair>,
    private val textSizeInSp: Float,
    private val activityIconDrawableHeight: Int,
    private val activityIconDrawableWidth: Int,
    private val activityIconMaxLayoutHeightPX: Int,
    private val activityIconMaxLayoutWidthPX: Int,
    private val horizontalLineThicknessPX: Int,
    private var fragment: Fragment?,
    private val errorStore: StoreErrorsInterface
) : DialogFragment() {

    private var _binding: DialogFragmentStoredUserActivityBinding? = null
    private val binding get() = _binding!!

    private var listener: MyStoredIconDataDialogListener? = null
    private var newTimeFrameButtonEnabled = true

    private val timeFrameRowViews = mutableListOf<Pair<ActivityTimeRowCompoundView, View>>()

    interface MyStoredIconDataDialogListener {
        fun onStoredIconDataDialogPositiveClick()
        fun onStoredIconDataDialogDeleteSubCategoryClick(inflatedView: View)
        fun onStoredIconDataDialogNewTimeFrameClick()
        fun onStoredIconDataDialogEditClick(element: LetsGoActivityTimeFrame)
        fun onStoredIconDataDialogDeleteTimeFrameClick(element: LetsGoActivityTimeFrame)
        fun onStoredIconDataDialogDismissed()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_dialog_rounded_corners)
    }

    override fun onAttach(context: Context) {

        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = fragment as MyStoredIconDataDialogListener
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
            _binding =
                DialogFragmentStoredUserActivityBinding.inflate(requireActivity().layoutInflater)

            builder.setView(binding.root)
            builder.setTitle("")
            builder.setPositiveButton(android.R.string.ok) { _, _ ->

            }

            binding.root.doOnAttach { thisView ->

                val viewBinding = DialogFragmentStoredUserActivityBinding.bind(thisView)

                val context = requireContext()

                viewBinding.iconStoredActivityDialogTextView.setupAsActivityTextView(
                    0,
                    allActivitiesReference[activityIndex].activity.iconDisplayName,
                    null,
                    textSizeInSp,
                    activityIconMaxLayoutWidthPX,
                    activityIconMaxLayoutHeightPX,
                    false
                ) { textView ->
                    //save standard icon to activity text view
                    saveIconToTextView(
                        context.applicationContext,
                        textView,
                        activityIndex,
                        selectedIcon = true,
                        activityIconDrawableHeight,
                        activityIconDrawableWidth,
                        activitiesOrderedByCategoryReference,
                        allActivitiesReference,
                        errorStore
                    )
                }

                viewBinding.timeFramesDefaultValueStoredIconDataTextView.text =
                    getString(
                        R.string.select_categories_anytime_message,
                        allActivitiesReference[activityIndex].activity.displayName
                    )

                //add the rows representing the different time frames
                addTimeFrameRows()

                viewBinding.deleteActivityStoredIconDataTextView.setSafeOnClickListener {
                    listener?.onStoredIconDataDialogDeleteSubCategoryClick(binding.root)
                }

                if (GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount == 0) { //support for the case of maxNumberOfTImeFrames is 0
                    viewBinding.newTimeFrameStoredIconDataTextView.visibility = View.GONE
                } else {
                    if (timeFrames.size >= GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount) {
                        viewBinding.newTimeFrameStoredIconDataTextView.visibility = View.GONE
                        viewBinding.newTimeFramePlaceHolderStoredIconDataTextView.visibility =
                            View.VISIBLE
                        newTimeFrameButtonEnabled = false
                    }

                    viewBinding.newTimeFrameStoredIconDataTextView.setSafeOnClickListener(400) {
                        //the value timeFrames.size can change on delete pressed, so it has to be constantly
                        //checked for whenever this button is pressed
                        listener?.onStoredIconDataDialogNewTimeFrameClick()
                    }

                }

            }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")

    }

    private fun addTimeFrameRows() {

        if (timeFrames.isNotEmpty()) {
            binding.timeFramesDefaultValueStoredIconDataTextView.visibility = View.GONE
            binding.timeFramesHeaderStoredIconDataLinearLayout.visibility = View.VISIBLE
        }

        for (t in timeFrames) {

            val row = ActivityTimeRowCompoundView(requireContext())
            row.setStartText(
                " ${
                    formatUnixTimeStampToDateString(
                        t.startTimeTimestamp,
                        errorStore = errorStore
                    )
                }"
            )
            row.setStopText(
                " ${
                    formatUnixTimeStampToDateString(
                        t.stopTimeTimestamp,
                        errorStore = errorStore
                    )
                }"
            )

            val horizontalLineView = View(context)

            horizontalLineView.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    horizontalLineThicknessPX
                )
            horizontalLineView.setBackgroundColor(Color.DKGRAY)

            binding.timeFramesLinearLayoutStoredIconDataLinearLayout.addView(row)
            binding.timeFramesLinearLayoutStoredIconDataLinearLayout.addView(horizontalLineView)

            row.setDeleteOnClickListener(SafeClickListener {

                timeFrameRowViews.remove(Pair(row, horizontalLineView))

                binding.timeFramesLinearLayoutStoredIconDataLinearLayout.removeView(row)
                binding.timeFramesLinearLayoutStoredIconDataLinearLayout.removeView(
                    horizontalLineView
                )

                //This will send it back and update the array in the Fragment, it could be done here but I would rather
                //keep the primary array only updated in the Fragment itself
                listener?.onStoredIconDataDialogDeleteTimeFrameClick(t)

                if (!newTimeFrameButtonEnabled && timeFrames.size < GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount) {
                    binding.newTimeFrameStoredIconDataTextView.visibility = View.VISIBLE
                    binding.newTimeFramePlaceHolderStoredIconDataTextView.visibility = View.GONE
                    newTimeFrameButtonEnabled = true
                }

                if (timeFrames.isEmpty()) {
                    binding.timeFramesDefaultValueStoredIconDataTextView.visibility = View.VISIBLE
                    binding.timeFramesHeaderStoredIconDataLinearLayout.visibility = View.GONE
                }

            }
            )

            row.setEditOnClickListener(SafeClickListener {
                listener?.onStoredIconDataDialogEditClick(t)
            }
            )

            timeFrameRowViews.add(Pair(row, horizontalLineView))

        }

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onStoredIconDataDialogDismissed()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener = null
        fragment = null
        _binding = null
    }
}