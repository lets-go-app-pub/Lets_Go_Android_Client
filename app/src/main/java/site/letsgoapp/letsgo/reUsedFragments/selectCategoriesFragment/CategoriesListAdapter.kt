package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.utilities.buildGradientDrawableWithTint
import site.letsgoapp.letsgo.utilities.getValueAnimator


class CategoriesListAdapter(
    private val context: Context,
    deviceScreenWidth: Int,
    private val userAge: Int,
    private val numTextViewsPerRow: Int,
    private val sortedIndexNumbers: List<Int>,
    private val activitiesOrderedByCategoryReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.CategoryActivities>,
    private val allActivitiesReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.MutableActivityPair>,
    private val setTextView: (Int, TableRow) -> TextView
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class LayoutTypes {
        LAYOUT_HEADER,
        LAYOUT_MEMBER
    }

    companion object {
        const val LIST_ITEM_EXPAND_DURATION = 300L
    }

    private var cardContainerOriginalHeight = -1 // will be calculated dynamically

    private val widthMeasureSpec: Int =
        View.MeasureSpec.makeMeasureSpec(deviceScreenWidth, View.MeasureSpec.AT_MOST)
    private val heightMeasureSpec: Int =
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    private val distanceBetweenRows =
        (context.resources.getDimension(R.dimen.select_categories_single_activity_distance_between_rows)).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == LayoutTypes.LAYOUT_HEADER.ordinal) {
            CategoriesHeaderViewHolder(
                LayoutInflater.from(context)
                    .inflate(R.layout.list_item_categories_header, parent, false)
            )
        } else {
            CategoryViewHolder(
                LayoutInflater.from(context).inflate(R.layout.list_item_category, parent, false)
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            LayoutTypes.LAYOUT_HEADER.ordinal
        } else {
            LayoutTypes.LAYOUT_MEMBER.ordinal
        }
    }

    override fun getItemCount(): Int {
        return sortedIndexNumbers.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //no need to do anything special with the header, it is simply a text view
        if (holder.itemViewType != LayoutTypes.LAYOUT_HEADER.ordinal)
            setupCategoryViewHolder(holder as CategoryViewHolder, position)
    }

    private fun setupCategoryViewHolder(holder: CategoryViewHolder, position: Int) {

        val index = sortedIndexNumbers[position]

        //if the user is not old enough to see this category and the category has at least one activity; set visibility to gone
        if (activitiesOrderedByCategoryReference[index].category.minAge > userAge
            || activitiesOrderedByCategoryReference[index].activityIndexValues.isEmpty()
        ) {
            holder.hideLayout()
        } else {

            holder.resetViewHolderValuesToDefault()

            //calculate height of category title bar, only needs to be done once
            if (cardContainerOriginalHeight < 0) {
                holder.titleBarLayout.measure(widthMeasureSpec, heightMeasureSpec)

                cardContainerOriginalHeight =
                    holder.titleBarLayout.measuredHeight +
                            holder.titleBarLayout.marginTop +
                            holder.titleBarLayout.marginBottom +
                            holder.cardViewPrimaryLayout.paddingTop +
                            holder.cardViewPrimaryLayout.paddingBottom

            }

            var activityAdded = false

            //These variables are used to hold a copy of the lists in case they are saved over during a login.
            //It also it makes the names shorter.
            val categoryAndActivityReference = activitiesOrderedByCategoryReference[index]

            //pair is activity <Name, Index>
            val orderedActivityIndex = mutableListOf<Pair<String, Int>>()

            //set up the text views for this category
            for (activityIndex in categoryAndActivityReference.activityIndexValues) {
                //if the user is not old enough to see this, do not push the activity in
                if (allActivitiesReference[activityIndex].activity.minAge > userAge) {
                    continue
                }

                activityAdded = true
                orderedActivityIndex.add(
                    Pair(
                        allActivitiesReference[activityIndex].activity.iconDisplayName,
                        activityIndex
                    )
                )
            }

            //sort by activity name
            orderedActivityIndex.sortBy { it.first }

            var mostRecentTableRow: TableRow? = null

            for (activityNameIndex in orderedActivityIndex) {

                if (mostRecentTableRow == null || mostRecentTableRow.childCount >= numTextViewsPerRow) {

                    mostRecentTableRow?.let {
                        val params = it.layoutParams as TableLayout.LayoutParams
                        params.topMargin = distanceBetweenRows
                    }

                    mostRecentTableRow = TableRow(context)
                    mostRecentTableRow.gravity = Gravity.TOP
                    holder.tableLayout.addView(mostRecentTableRow)
                }

                allActivitiesReference[activityNameIndex.second].textViewWrapper.textView =
                    setTextView(activityNameIndex.second, mostRecentTableRow)
            }

            mostRecentTableRow?.let {
                val params = it.layoutParams as TableLayout.LayoutParams
                params.topMargin = distanceBetweenRows
                params.bottomMargin = distanceBetweenRows
            }

            //if no activities were added set the visibility of this category to gone
            if (!activityAdded) {
                holder.hideLayout()
            } else {

                holder.categoryName.text =
                    categoryAndActivityReference.category.name

                holder.titleBarLayout.background = buildGradientDrawableWithTint(
                    context,
                    categoryAndActivityReference.category.color,
                    true,
                    R.color.color_activities_background
                )

                //calculate expanded height
                holder.tableLayout.measure(widthMeasureSpec, heightMeasureSpec)
                holder.expandedHeight =
                    holder.tableLayout.measuredHeight + cardContainerOriginalHeight

                Log.i(
                    "category_info",
                    "categoryName: ${holder.categoryName.text} cardContainerOriginalHeight: $cardContainerOriginalHeight expandedHeight: ${holder.expandedHeight}"
                )

                holder.chevron.setImageResource(android.R.drawable.arrow_down_float)

                expandItem(holder, holder.expanded, animate = false)

                val collapseOnClickListener: (View) -> Unit = {
                    if (!holder.animationRunning) {
                        if (holder.expanded) {

                            //contract view model
                            expandItem(holder, expand = false, animate = true)
                            holder.expanded = false
                        } else {

                            //expand view model
                            expandItem(holder, expand = true, animate = true)
                            holder.expanded = true
                        }
                    }
                }

                holder.titleBarLayout.setOnClickListener(collapseOnClickListener)
                holder.categoryName.setOnClickListener(collapseOnClickListener)
                holder.chevron.setOnClickListener(collapseOnClickListener)
            }
        }
    }

    private fun expandItem(holder: CategoryViewHolder, expand: Boolean, animate: Boolean) {
        if (animate) {
            val animator = getValueAnimator(
                expand,
                LIST_ITEM_EXPAND_DURATION,
                AccelerateDecelerateInterpolator()
            ) { progress ->
                setExpandProgress(holder, expand, progress)
            }

            if (expand)
                animator.doOnStart {
                    holder.tableLayout.isVisible = true
                    holder.animationRunning = false
                }
            else
                animator.doOnEnd {
                    holder.tableLayout.isVisible = false
                    holder.animationRunning = false
                }

            holder.animationRunning = true
            animator.start()
        } else {
            // show expandView only if we have expandedHeight (onViewAttached)
            holder.tableLayout.isVisible = expand && holder.expandedHeight >= 0
            setExpandProgress(holder, expand, if (expand) 1f else 0f)
        }

    }

    //this function will set the width, height color and rotation of the chevron to a % based on 'progress' variable
    //if the expandedHeight or originalHeight have not been initialized, it will not change height
    private fun setExpandProgress(holder: CategoryViewHolder, expand: Boolean, progress: Float) {
        holder.cardViewPrimaryLayout.apply {
            if (holder.expandedHeight > 0 && cardContainerOriginalHeight > 0) {
                val nextHeight =
                    (cardContainerOriginalHeight + (holder.expandedHeight - cardContainerOriginalHeight) * progress).toInt()

                layoutParams.height = nextHeight

                this.doOnPreDraw {
                    //Make sure to bring the view fully into to the screen.
                    if (expand) {
                        val rect = Rect(0, 0, width, nextHeight)
                        requestRectangleOnScreen(rect, false)
                    }
                }
            }

            requestLayout()
        }

        holder.chevron.rotation = 180 * progress
    }

    class CategoriesHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleBarLayout: ConstraintLayout =
            itemView.findViewById(R.id.categoryTitleConstraintLayout)
        val categoryName: TextView = itemView.findViewById(R.id.categoryListItemTitleTextView)
        val chevron: ImageView = itemView.findViewById(R.id.categoryListItemChevron)
        val tableLayout: TableLayout = itemView.findViewById(R.id.categoryListItemTableLayout)
        val cardViewPrimaryLayout: LinearLayout =
            itemView.findViewById(R.id.cardContainerLinearLayout)

        var expanded = false
        var expandedHeight = -1 // will be calculated dynamically
        var animationRunning = false // will be calculated dynamically

        fun resetViewHolderValuesToDefault() {
            expanded = false
            expandedHeight = -1 // will be calculated dynamically
            animationRunning = false // will be calculated dynamically
            tableLayout.removeAllViews()
            showLayout()
            titleBarLayout.setOnClickListener(null)
            categoryName.setOnClickListener(null)
            chevron.setOnClickListener(null)
        }

        fun hideLayout() {
            itemView.visibility = View.GONE
            cardViewPrimaryLayout.visibility = View.GONE
            cardViewPrimaryLayout.layoutParams = RecyclerView.LayoutParams(0, 0)
        }

        private fun showLayout() {
            itemView.visibility = View.VISIBLE
            cardViewPrimaryLayout.visibility = View.VISIBLE
            cardViewPrimaryLayout.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
    }
}