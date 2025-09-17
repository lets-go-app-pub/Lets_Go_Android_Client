package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.setPadding
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.SafeClickListener
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener

class ActivityTimeRowCompoundView(
    context: Context
) : LinearLayout(context) {

    private val editImageView = ImageView(
        ContextThemeWrapper(context, R.style.dialog_clickable_text_view), null, 0
    )
    private val timeLayout = LinearLayout(context)
    private val timeStartTextView = TextView(context)
    private val timeToTextView = TextView(context)
    private val timeStopTextView = TextView(context)
    private val deleteImageView = ImageView(
        ContextThemeWrapper(context, R.style.dialog_clickable_text_view), null, 0
    )

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        setPadding(context.resources.getDimension(R.dimen.dialog_layout_primary_layout_padding).toInt())

        //NOTE: for some reason the layout gravity from the style is not applying so applying it here manually
        editImageView.setImageResource(R.drawable.icon_round_edit_activity_time_24)
        editImageView.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }

        timeLayout.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        timeLayout.orientation = VERTICAL

        timeStartTextView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        timeStartTextView.gravity = Gravity.CENTER

        timeToTextView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        timeToTextView.gravity = Gravity.CENTER
        timeToTextView.setText(R.string.start_stop_time_linker)

        timeStopTextView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        timeStopTextView.gravity = Gravity.CENTER

        timeLayout.addView(timeStartTextView)
        timeLayout.addView(timeToTextView)
        timeLayout.addView(timeStopTextView)

        //NOTE: for some reason the layout gravity from the style is not applying so applying it here manually
        deleteImageView.setImageResource(R.drawable.icon_round_close_red_24)
        deleteImageView.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        addView(editImageView)
        addView(timeLayout)
        addView(deleteImageView)

    }

    fun setStartText(msg: String) {
        timeStartTextView.text = msg
    }

    fun setStopText(msg: String) {
        timeStopTextView.text = msg
    }

    fun setDeleteOnClickListener(l: SafeClickListener) {
        deleteImageView.setSafeOnClickListener(l)
    }

    fun setEditOnClickListener(l: SafeClickListener) {
        editImageView.setSafeOnClickListener(l)
    }
}
