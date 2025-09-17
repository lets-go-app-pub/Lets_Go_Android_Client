package site.letsgoapp.letsgo.applicationActivityFragments.timeFrameTutorialFragment

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import categorytimeframe.CategoryTimeFrame
import categorytimeframe.CategoryTimeFrame.CategoryTimeFrameMessage
import lets_go_event_status.LetsGoEventStatusOuterClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.databinding.FragmentTimeFrameTutorialBinding
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import java.util.*

class TimeFrameTutorialFragment : Fragment() {

    private var _binding: FragmentTimeFrameTutorialBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String

    private var applicationActivity: AppActivity? = null

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private val activitiesOrderedByCategoryReference =
        CategoriesAndActivities.activitiesOrderedByCategory
    private val allActivitiesReference = CategoriesAndActivities.allActivities

    private val topMargin =
        GlobalValues.applicationContext.resources.getDimension(R.dimen.match_list_item_margin_between_items).toInt()

    private val separatorLineOriginalWidth =
        UserInfoCardLogic.Companion.SeparatorLineWidthWrapper() // will be calculated dynamically

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimeFrameTutorialBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        applicationActivity = requireActivity() as AppActivity
        return binding.root        // Inflate the layout for this fragment
    }

    override fun onDestroyView() {
        _binding = null
        applicationActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Allow hyperlink to be clicked.
        binding.timeFrameTutorialFragmentTextView.movementMethod = LinkMovementMethod.getInstance()

        binding.timeFrameTutorialContinueButton.setSafeOnClickListener {
            applicationActivity?.navigate(
                R.id.timeFrameTutorialFragment,
                R.id.action_timeFrameTutorialFragment_to_matchScreenFragment
            )
        }

        val widthMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(
                getScreenWidth(requireActivity()),
                View.MeasureSpec.AT_MOST
            )
        val heightMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        //add this view to the root view temporarily to calculate the size the textView needs to be
        val tempMatchListItem =
            View.inflate(context, R.layout.view_user_info_card_time_frame, null) as LinearLayout
        binding.root.addView(tempMatchListItem)
        val tempDateItem =
            tempMatchListItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStartTimeTextView)
        tempDateItem.measure(widthMeasureSpec, heightMeasureSpec)
        val matchListItemDateTimeTextViewWidth = tempDateItem.measuredWidth
        val matchListItemDateTimeTextViewHeight = tempDateItem.measuredHeight
        binding.root.removeView(tempMatchListItem)

        val timeFrameBarHeight =
            requireContext().resources.getDimension(R.dimen.match_screen_card_adapter_time_frame_bar_height)
                .toInt()

        var primaryTitleBarOriginalHeight = -1

        val (firstActivityIndex, secondActivityIndex) = findTwoActivityIndexes()

        val currentTimeCalendar = getCalendarFromServerTimestamp()

        //Add one day.
        currentTimeCalendar.add(Calendar.DATE, 1)

        //Set time to 5:30PM
        currentTimeCalendar.set(Calendar.HOUR_OF_DAY, 17)
        currentTimeCalendar.set(Calendar.MINUTE, 30)
        currentTimeCalendar.set(Calendar.SECOND, 0)
        currentTimeCalendar.set(Calendar.MILLISECOND, 0)

        val firstUserActivity = CategoryTimeFrame.CategoryActivityMessage.newBuilder()
            .setActivityIndex(firstActivityIndex)
            .addTimeFrameArray(
                CategoryTimeFrameMessage.newBuilder()
                    .setStartTimeFrame(currentTimeCalendar.timeInMillis)
                    .setStopTimeFrame(currentTimeCalendar.timeInMillis + 1000L * 60L * 60L)
                    .build()
            )
            .build()

        //Set time to 5:00PM
        currentTimeCalendar.set(Calendar.MINUTE, 0)

        val firstMatchActivity = CategoryTimeFrame.CategoryActivityMessage.newBuilder()
            .setActivityIndex(firstActivityIndex)
            .addTimeFrameArray(
                CategoryTimeFrameMessage.newBuilder()
                    .setStartTimeFrame(currentTimeCalendar.timeInMillis)
                    .setStopTimeFrame(currentTimeCalendar.timeInMillis + 1000L * 60L * 60L)
                    .build()
            )
            .build()

        val firstActivityHolder = UserInfoCardLogic.ActivityHolder(
            requireContext(),
            GlideApp.with(this),
            activitiesOrderedByCategoryReference,
            allActivitiesReference,
            firstMatchActivity,
            firstUserActivity,
            categoryMatch = false,
            widthMeasureSpec,
            heightMeasureSpec,
            false,
            LetsGoEventStatusOuterClass.LetsGoEventStatus.NOT_AN_EVENT,
            primaryTitleBarOriginalHeight,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            timeFrameBarHeight,
            errorStore
        )

        primaryTitleBarOriginalHeight = firstActivityHolder.cardContainerOriginalHeight

        val secondUserActivity = CategoryTimeFrame.CategoryActivityMessage.newBuilder()
            .setActivityIndex(secondActivityIndex)
            .build()

        //Add a day to the calendar
        currentTimeCalendar.add(Calendar.DATE, 1)

        val secondMatchActivity = CategoryTimeFrame.CategoryActivityMessage.newBuilder()
            .setActivityIndex(secondActivityIndex)
            .addTimeFrameArray(
                CategoryTimeFrameMessage.newBuilder()
                    .setStartTimeFrame(currentTimeCalendar.timeInMillis)
                    .setStopTimeFrame(currentTimeCalendar.timeInMillis + 1000L * 60L * 60L * 2L)
                    .build()
            )
            .build()

        val secondActivityHolder = UserInfoCardLogic.ActivityHolder(
            requireContext(),
            GlideApp.with(this),
            activitiesOrderedByCategoryReference,
            allActivitiesReference,
            secondMatchActivity,
            secondUserActivity,
            categoryMatch = false,
            widthMeasureSpec,
            heightMeasureSpec,
            false,
            LetsGoEventStatusOuterClass.LetsGoEventStatus.NOT_AN_EVENT,
            primaryTitleBarOriginalHeight,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            timeFrameBarHeight,
            errorStore
        )

        binding.timeFrameTutorialListLinearLayout.addView(
            firstActivityHolder.activityHolder
        )

        binding.timeFrameTutorialListLinearLayout.addView(
            secondActivityHolder.activityHolder
        )

        //NOTE: The top margin for match_list_item can not be set until the layout has a parent. So it must be
        // set programmatically here because the views needs sorted before they are inserted.
        (firstActivityHolder.activityHolder.layoutParams as LinearLayout.LayoutParams).topMargin =
            topMargin

        UserInfoCardLogic.setupActivityHolderAsDropDown(
            firstActivityHolder,
            primaryTitleBarOriginalHeight,
            separatorLineOriginalWidth,
            topMargin
        )
        firstActivityHolder.expanded = true
        firstActivityHolder.chevron.rotation = 180F

        UserInfoCardLogic.setupActivityHolderAsDropDown(
            secondActivityHolder,
            primaryTitleBarOriginalHeight,
            separatorLineOriginalWidth,
            topMargin
        )

        secondActivityHolder.expanded = true
        secondActivityHolder.chevron.rotation = 180F
    }

    //This function will return two activity indexes. It will try to them manually by name. THEN if it
    // isn't found, find the first one that can match with any age.
    private fun findTwoActivityIndexes(): Pair<Int, Int> {
        var firstActivityIndex = 0
        var firstActivityCategoryIndex = 0
        var secondActivityIndex = 0

        val firstActivityString = "Basketball"
        val secondActivityString = "Hiking"

        for (activity in allActivitiesReference) {
            if (activity.activity.displayName == firstActivityString) {
                firstActivityIndex = activity.activity.index
                firstActivityCategoryIndex = activity.activity.categoryIndex
            }
            if (activity.activity.displayName == secondActivityString) {
                secondActivityIndex = activity.activity.index
            }
            if (firstActivityIndex > 0 && secondActivityIndex > 0) {
                break
            }
        }

        //First activity was not found.
        if (firstActivityIndex == 0) {

            val errorMessage = "firstActivityIndex for the tutorial with displayName $firstActivityString was not found."

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            for (activity in allActivitiesReference) {
                if (activity.activity.categoryIndex > 0
                    && activity.activity.minAge == GlobalValues.server_imported_values.lowestAllowedAge
                ) {
                    firstActivityIndex = activity.activity.index
                    firstActivityCategoryIndex = activity.activity.categoryIndex
                    break
                }
            }
        }

        //Second activity was not found.
        if (secondActivityIndex == 0) {

            val errorMessage = "secondActivityIndex for the tutorial with displayName $secondActivityString was not found."

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorMessage
            )

            for (activity in allActivitiesReference) {
                if (activity.activity.categoryIndex > 0
                    && activity.activity.categoryIndex != firstActivityCategoryIndex
                    && activity.activity.minAge == GlobalValues.server_imported_values.lowestAllowedAge
                ) {
                    secondActivityIndex = activity.activity.index
                    break
                }
            }
        }

        return Pair(firstActivityIndex, secondActivityIndex)
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        applicationActivity?.setupActivityMenuBars?.setupToolbarsTimeFrameTutorialFragment()
    }
}