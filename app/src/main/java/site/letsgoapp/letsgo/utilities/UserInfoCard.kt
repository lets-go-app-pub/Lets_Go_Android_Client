package site.letsgoapp.letsgo.utilities

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentManager
import categorytimeframe.CategoryTimeFrame
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.card.MaterialCardView
import lets_go_event_status.LetsGoEventStatusOuterClass.LetsGoEventStatus
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment.PicturePopOutDialogFragment
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.getIconDrawable
import user_account_type.UserAccountTypeOuterClass.UserAccountType
import java.io.File
import java.lang.Long.max


//initializes a single card item containing a users information
class UserInfoCardLogic(
    private val context: Context, //application context is fine here, it is used to request values
    private val glideContext: RequestManager,
    private val runUpdatesIfUserPicturesHaveNotBeenUpdatedRecently: Boolean,
    private val primaryCardView: View,
    deviceScreenWidth: Int,
    private val userAge: Int,
    private val userActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage>,
    private val matchListItemDateTimeTextViewWidth: Int,
    private val matchListItemDateTimeTextViewHeight: Int,
    private val childFragmentManager: FragmentManager,
    private val requestUpdateSingleUser: (userAccountOID: String) -> Unit,
    private val updateOtherUserToObserved: (userAccountOID: String) -> Unit,
    private val errorStore: StoreErrorsInterface
) {
    companion object {

        private const val LIST_ITEM_EXPAND_DURATION = 300L

        data class SeparatorLineWidthWrapper(
            var separatorLineOriginalWidth: Int = -1
        )

        fun setupActivityHolderAsDropDown(
            activityHolder: ActivityHolder,
            primaryTitleBarOriginalHeight: Int,
            separatorLineWidthWrapper: SeparatorLineWidthWrapper,
            additionalLayoutHeight: Int
        ) {
            activityHolder.apply {

                val collapseOnClickListener: (View) -> Unit = {
                    if (!this.animationRunning) {
                        if (this.expanded) {

                            //contract view model
                            expandItem(
                                this,
                                expand = false,
                                animate = true,
                                primaryTitleBarOriginalHeight,
                                separatorLineWidthWrapper,
                                additionalLayoutHeight
                            )
                            this.expanded = false
                        } else {

                            //expand view model
                            expandItem(
                                this,
                                expand = true,
                                animate = true,
                                primaryTitleBarOriginalHeight,
                                separatorLineWidthWrapper,
                                additionalLayoutHeight
                            )
                            this.expanded = true
                        }
                    }
                }

                this.titleBarLayout.setOnClickListener(collapseOnClickListener)
                this.timeFramesLayout.setOnClickListener(collapseOnClickListener)
            }
        }

        fun expandItem(
            activityHolder: ActivityHolder,
            expand: Boolean,
            animate: Boolean,
            primaryTitleBarOriginalHeight: Int,
            separatorLineWidthWrapper: SeparatorLineWidthWrapper,
            additionalLayoutHeight: Int
        ) {

            if (separatorLineWidthWrapper.separatorLineOriginalWidth <= 0 && activityHolder.titleBarLayout.width > 0) {

                //NOTE: Must use titleBarLayout as the measurement instead of timeFramesLayout because
                // timeFramesLayout is still set to View.GONE at this point.
                separatorLineWidthWrapper.separatorLineOriginalWidth =
                    activityHolder.titleBarLayout.width -
                            activityHolder.timeFramesLayout.paddingStart -
                            activityHolder.timeFramesLayout.paddingEnd -
                            2 * GlobalValues.applicationContext.resources.getDimension(R.dimen.match_screen_card_adapter_time_frame_separator_bar_margin)
                        .toInt()
            }

            if (animate) {
                val animator = getValueAnimator(
                    expand,
                    LIST_ITEM_EXPAND_DURATION,
                    AccelerateDecelerateInterpolator()
                ) { progress ->
                    setExpandProgress(
                        activityHolder,
                        expand,
                        progress,
                        primaryTitleBarOriginalHeight,
                        separatorLineWidthWrapper,
                        additionalLayoutHeight
                    )
                }
                if (expand) {
                    animator.doOnStart {
                        activityHolder.timeFramesLayout.isVisible = true
                        activityHolder.animationRunning = false
                    }
                } else {
                    animator.doOnEnd {
                        activityHolder.timeFramesLayout.isVisible = false
                        activityHolder.animationRunning = false
                    }
                }
                activityHolder.animationRunning = true
                animator.start()
            } else {
                // show expandView only if we have expandedHeight (onViewAttached)
                activityHolder.timeFramesLayout.isVisible =
                    expand && activityHolder.expandedHeight >= 0
                setExpandProgress(
                    activityHolder,
                    expand,
                    if (expand) 1f else 0f,
                    primaryTitleBarOriginalHeight,
                    separatorLineWidthWrapper,
                    additionalLayoutHeight
                )
            }
        }

        //this function will set the width, height color and rotation of the chevron to a % based on 'progress' variable
        //if the expandedHeight or originalHeight have not been initialized, it will not change height
        private fun setExpandProgress(
            activityHolder: ActivityHolder,
            expand: Boolean,
            progress: Float,
            primaryTitleBarOriginalHeight: Int,
            separatorLineWidthWrapper: SeparatorLineWidthWrapper,
            additionalLayoutHeight: Int
        ) {

            activityHolder.activityHolder.apply {
                if (activityHolder.expandedHeight > 0 && primaryTitleBarOriginalHeight > 0) {
                    //shrink or expand layout vertically
                    val nextHeight =
                        (primaryTitleBarOriginalHeight + (activityHolder.expandedHeight - primaryTitleBarOriginalHeight) * progress).toInt()

                    layoutParams.height = nextHeight

                    this.doOnPreDraw {
                        if (expand) {
                            val rect = Rect(0, 0, width, nextHeight + additionalLayoutHeight)
                            requestRectangleOnScreen(rect, false)
                        }
                    }

                    //shrink or expand separator lines horizontally
                    activityHolder.setSeparatorLineViewWidth((separatorLineWidthWrapper.separatorLineOriginalWidth.toFloat() / 4.0 + separatorLineWidthWrapper.separatorLineOriginalWidth.toFloat() * (3.0 / 4.0 * progress)).toInt())
                }

                requestLayout()
            }

            activityHolder.chevron.rotation = 180 * progress
        }
    }

    private var primaryTitleBarOriginalHeight = -1 // will be calculated dynamically
    private val separatorLineOriginalWidth =
        SeparatorLineWidthWrapper() // will be calculated dynamically

    private val widthMeasureSpec: Int =
        View.MeasureSpec.makeMeasureSpec(deviceScreenWidth, View.MeasureSpec.AT_MOST)
    private val heightMeasureSpec: Int =
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    private lateinit var holder: ViewHolder

    private val topMargin =
        context.resources.getDimension(R.dimen.match_list_item_margin_between_items).toInt()

    private val timeFrameBarHeight =
        context.resources.getDimension(R.dimen.match_screen_card_adapter_time_frame_bar_height)
            .toInt()

    private val picIndexHeight =
        context.resources.getDimension(R.dimen.picture_index_height).toInt()
    private val picIndexMargin =
        context.resources.getDimension(R.dimen.picture_index_margin).toInt()

    private val activitiesOrderedByCategoryReference =
        CategoriesAndActivities.activitiesOrderedByCategory
    private val allActivitiesReference = CategoriesAndActivities.allActivities

    private enum class DisplayType {
        USER,
        EVENT,
        EVENT_ADMIN
    }

    private fun requestUpdateForUser(accountOID: String) {
        Log.i("userInfoCard", "requestUpdateForUser() for accountOID: $accountOID")
        requestUpdateSingleUser(accountOID)
        holder.picturesUpdating = true
    }

    fun initializationIfMatchingAccountOID(
        otherUser: OtherUsersDataEntity,
        hideMatchingInfo: Boolean,
        eventActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage> = mutableListOf(),
    ): Boolean {

        if (holder.accountOID == otherUser.accountOID) {
            //this picturesUpdating being set here is not a perfect solution (the live data that calls this could be called from
            // a different update source OR this could be called twice), however the repository (updateSingleChatRoomMemberInfo())
            // will only allow updates every ~30 seconds to the same user to be run and so even if an extra update slips through
            // nothing should change
            holder.picturesUpdating = false
            initializeInfo(otherUser, hideMatchingInfo, eventActivities)
            return true
        }

        return false
    }

    private fun setupPictures(accountOID: String, picturesString: String) {

        val pictureInfoList = convertPicturesStringToList(picturesString)
        Log.i("pictureInfoList", "setupPictures() pictureInfoList: $pictureInfoList")

        if (pictureInfoList.isNotEmpty()) {

            holder.pictureInfo.clear()
            var downloadRequired = false
            val validPictureInfo = mutableListOf<PictureInfo>()

            //check for validity of pictures
            for (i in pictureInfoList.indices) {
                if (pictureInfoList[i].picturePath != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER
                    && File(pictureInfoList[i].picturePath).isImage()
                ) {
                    validPictureInfo.add(pictureInfoList[i])
                    holder.pictureInfo.add(
                        PictureInfo(
                            pictureInfoList[i].picturePath,
                            holder.pictureInfo.size,
                            pictureInfoList[i].timestampPictureLastUpdatedOnServer
                        )
                    )
                } else if (pictureInfoList[i].picturePath != GlobalValues.PICTURE_NOT_FOUND_ON_SERVER) {
                    downloadRequired = true
                }
            }

            if (holder.pictureInfo.isEmpty()) { //no pictures stored
                requestUpdateForUser(accountOID)

                glideContext
                    .load(GlobalValues.defaultPictureResourceID)
                    .error(GlobalValues.defaultPictureResourceID)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.pictureImageView)

                return
            } else if (downloadRequired) { //picture info is NOT empty however it requires an update
                requestUpdateForUser(accountOID)
            }

            Log.i(
                "nameAndNumPics",
                "lineNumber: ${Thread.currentThread().stackTrace[2].lineNumber}"
            )

            holder.pictureImageView.setSafeOnClickListener {
                //NOTE: I believe that when this view is destroyed this dialog fragment will be cleared because it goes with the pictureImageView

                //no reason to run isImage() again here, so stored valid pics
                if (validPictureInfo.isNotEmpty()) {

                    val validPicturesString = convertPicturesListToString(validPictureInfo)

                    val dialogFragment = PicturePopOutDialogFragment()

                    Log.i(
                        "pictureImageViewClick",
                        "holder.pictureSelectedIndex: ${holder.pictureSelectedIndex}"
                    )

                    val bundle = Bundle()
                    bundle.putString(PICTURE_STRING_FRAGMENT_ARGUMENT_KEY, validPicturesString)
                    bundle.putInt(
                        PICTURE_INDEX_NUMBER_FRAGMENT_ARGUMENT_KEY,
                        holder.pictureSelectedIndex
                    )
                    dialogFragment.arguments = bundle

                    dialogFragment.show(childFragmentManager, "DialogFragment")
                }
            }

            Log.i(
                "nameAndNumPics",
                "pictureInfo.size: ${holder.pictureInfo.size}"
            )

            glideContext
                .load(holder.pictureInfo[0].picturePath)
                .signature(generateFileObjectKey(holder.pictureInfo[0].timestampPictureLastUpdatedOnServer))
                .circleCrop()
                .listener(
                    object : RequestListener<Drawable?> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable?>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            requestUpdateForUser(accountOID)
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable?>,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            return false
                        }
                    }
                )
                .error(GlobalValues.defaultPictureResourceID)
                .into(holder.pictureImageView)

            if (holder.pictureInfo.size > 1) {

                setupPictureIndexImage(
                    context,
                    holder.pictureInfo,
                    holder.pictureIndexLayout,
                    picIndexHeight,
                    picIndexMargin
                )

                holder.leftChevronImageView.setOnClickListener {
                    holder.pictureSelectedIndex = navigateToNextPicture(
                        accountOID,
                        true,
                        holder.pictureSelectedIndex,
                        holder.pictureInfo,
                        holder.pictureImageView,
                        holder.pictureIndexLayout
                    )
                }

                holder.rightChevronImageView.setOnClickListener {
                    holder.pictureSelectedIndex = navigateToNextPicture(
                        accountOID,
                        false,
                        holder.pictureSelectedIndex,
                        holder.pictureInfo,
                        holder.pictureImageView,
                        holder.pictureIndexLayout
                    )
                }

            } else { //if only 1 picture

                holder.pictureIndexLayout.visibility = View.GONE
                holder.leftChevronImageView.visibility = View.INVISIBLE
                holder.rightChevronImageView.visibility = View.INVISIBLE
            }
        } else { //no pictures in list

            //NOTE: It IS possible to reach this point if say the user has an
            // inappropriate picture and it is removed.

            holder.pictureIndexLayout.visibility = View.GONE
            holder.leftChevronImageView.visibility = View.INVISIBLE
            holder.rightChevronImageView.visibility = View.INVISIBLE

            requestUpdateForUser(accountOID)

            glideContext
                .load(GlobalValues.defaultPictureResourceID)
                .error(GlobalValues.defaultPictureResourceID)
                .apply(RequestOptions.circleCropTransform())
                .into(holder.pictureImageView)

        }

        Log.i(
            "nameAndNumPics",
            "completed UserInfoCard.setupPictures()"
        )
    }

    fun initializeInfo(
        otherUser: OtherUsersDataEntity,
        hideUserMatchingInfo: Boolean,
        eventActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage> = mutableListOf(),
    ) {

        holder = ViewHolder(primaryCardView)

        //This CardStackView seems to re-use the fragments
        holder.clearViewHolder()

        val displayType =
            if (otherUser.accountType >= UserAccountType.ADMIN_GENERATED_EVENT_TYPE.number) {
               DisplayType.EVENT
            } else if (otherUser.name == GlobalValues.server_imported_values.adminFirstName) {
                DisplayType.EVENT_ADMIN
            } else {
                DisplayType.USER
            }

        if (holder.accountOID != otherUser.accountOID) { //if first time initializeInfo is running or user is changing
            holder.accountOID = otherUser.accountOID
            updateOtherUserToObserved(holder.accountOID)
        }

        if (displayType == DisplayType.EVENT) {
            holder.eventTypeTextView.visibility = View.VISIBLE

            if (otherUser.accountType == UserAccountType.USER_GENERATED_EVENT_TYPE.number) {
                holder.eventTypeTextView.text = context.getString(
                    R.string.user_event
                )
            } else {
                holder.eventTypeTextView.text = context.getString(
                    R.string.local_event
                )
            }
        } else {
            holder.eventTypeTextView.visibility = View.GONE
        }

        if (hideUserMatchingInfo) {
            holder.rightOverlay.visibility = View.GONE
            holder.leftOverlay.visibility = View.GONE
        } else {
            holder.rightOverlay.visibility = View.VISIBLE
            holder.leftOverlay.visibility = View.VISIBLE
        }

        Log.i(
            "MatchScreenFrag",
            "accountOID: ${otherUser.accountOID}; name: ${otherUser.name}"
        )

        Log.i(
            "Match_name_stuff",
            "name: ${otherUser.name} adminFirstName: ${GlobalValues.server_imported_values.adminFirstName}"
        )

        holder.nameAgeView.text =
            when (displayType) {
                DisplayType.USER -> {
                    context.getString(
                        R.string.match_screen_fragment_name_age_text,
                        otherUser.name,
                        otherUser.age
                    )
                }
                DisplayType.EVENT -> {
                    otherUser.name
                }
                DisplayType.EVENT_ADMIN ->  {
                    displayEventAdminName(otherUser.name)
                }
            }

        if (hideUserMatchingInfo && (otherUser.cityName == "" || otherUser.cityName == "~")) { //if no city is stored and hiding matching info
            holder.locationView.visibility = View.GONE
        } else {
            holder.locationView.visibility = View.VISIBLE

            holder.locationView.text =
                if (hideUserMatchingInfo || otherUser.distance.compareTo(-1.0) == 0) {
                    otherUser.cityName
                } else if (otherUser.cityName != "" && otherUser.cityName != "~") {
                    context.getString(
                        R.string.match_screen_fragment_location_text,
                        otherUser.cityName,
                        String.format("%.1f", otherUser.distance)
                    )
                } else {
                    context.getString(
                        R.string.match_screen_fragment_no_city_location_text,
                        String.format("%.1f", otherUser.distance)
                    )
                }
        }

        if (otherUser.bio != "" && otherUser.bio != "~") {
            holder.bioView.text = otherUser.bio
        } else {
            holder.bioView.visibility = View.GONE
        }

        //update the pictures every so often if the calling fragment requests it
        if (runUpdatesIfUserPicturesHaveNotBeenUpdatedRecently
            && (getCurrentTimestampInMillis() - otherUser.picturesUpdateAttemptedTimestamp) > GlobalValues.server_imported_values.timeBetweenUpdatingSingleUser
        ) {
            requestUpdateForUser(otherUser.accountOID)
        }

        holder.pictureCardView.radius =
            context.resources.getDimension(R.dimen.half_user_picture_frame_size)

        setupPictures(otherUser.accountOID, otherUser.pictures)

        val matchActivities =
            when (displayType) {
                DisplayType.USER -> {
                    convertStringToCategoryActivityMessageAndTrimTimes(otherUser.activities).second
                }
                DisplayType.EVENT -> {
                    val activities = convertStringToCategoryActivityMessageAndTrimTimes(otherUser.activities).second
                    if (otherUser.eventStatus != LetsGoEventStatus.ONGOING.number) {
                        //Clear all timeframes so below a message can be displayed in place
                        // of ANYTIME
                        for (activity in activities) {
                           activity.timeFrameArrayList.clear()
                        }
                    }
                    activities
                }
                DisplayType.EVENT_ADMIN -> {
                    eventActivities
                }
            }

        for (matchActivity in matchActivities) {
            if (matchActivity.activityIndex < allActivitiesReference.size) {
                val matchActivityFromList =
                    allActivitiesReference[matchActivity.activityIndex]
                //if user is under min age to view this activity, do not show it
                if (userAge >= matchActivityFromList.activity.minAge) {

                    //if there is no user activity that matches then null will be passed to the ActivityHolder
                    var userActivity: CategoryTimeFrame.CategoryActivityMessage? = null
                    var categoryMatch = false
                    val matchCategoryIndex = matchActivityFromList.activity.categoryIndex

                    //check if any matching activities
                    for (a in userActivities) {
                        when {
                            a.activityIndex == matchActivity.activityIndex -> { //activity match
                                userActivity = a
                            }
                            a.activityIndex >= allActivitiesReference.size -> {

                                val errorMessage =
                                    "The user activity should always be in the copy of the CategoriesAndActivities allActivitiesReference list.\n" +
                                            "activity_index: ${a.activityIndex}\n" +
                                            "allActivities: ${allActivitiesReference}\n"

                                errorStore.storeError(
                                    Thread.currentThread().stackTrace[2].fileName,
                                    Thread.currentThread().stackTrace[2].lineNumber,
                                    printStackTraceForErrors(),
                                    errorMessage
                                )

                                //NOTE: can continue
                            }
                            allActivitiesReference[a.activityIndex].activity.categoryIndex ==
                                    matchCategoryIndex -> {
                                categoryMatch = true
                            }
                        }
                    }

                    if (primaryTitleBarOriginalHeight < 0) {

                        val activityHolder = ActivityHolder(
                            context,
                            glideContext,
                            activitiesOrderedByCategoryReference,
                            allActivitiesReference,
                            matchActivity,
                            userActivity,
                            categoryMatch,
                            widthMeasureSpec,
                            heightMeasureSpec,
                            displayType == DisplayType.EVENT,
                            LetsGoEventStatus.forNumber(otherUser.eventStatus),
                            primaryTitleBarOriginalHeight,
                            matchListItemDateTimeTextViewWidth,
                            matchListItemDateTimeTextViewHeight,
                            timeFrameBarHeight,
                            errorStore
                        )

                        holder.matchActivityLayouts.add(
                            Pair(
                                activityHolder.totalPointValue,
                                activityHolder
                            )
                        )

                        primaryTitleBarOriginalHeight =
                            holder.matchActivityLayouts.last().second.cardContainerOriginalHeight
                    } else {
                        val activityHolder = ActivityHolder(
                            context,
                            glideContext,
                            activitiesOrderedByCategoryReference,
                            allActivitiesReference,
                            matchActivity,
                            userActivity,
                            categoryMatch,
                            widthMeasureSpec,
                            heightMeasureSpec,
                            displayType == DisplayType.EVENT,
                            LetsGoEventStatus.forNumber(otherUser.eventStatus),
                            primaryTitleBarOriginalHeight,
                            matchListItemDateTimeTextViewWidth,
                            matchListItemDateTimeTextViewHeight,
                            timeFrameBarHeight,
                            errorStore
                        )

                        holder.matchActivityLayouts.add(
                            Pair(
                                activityHolder.totalPointValue,
                                activityHolder
                            )
                        )
                    }

                    setupActivityHolderAsDropDown(
                        holder.matchActivityLayouts.last().second,
                        primaryTitleBarOriginalHeight,
                        separatorLineOriginalWidth,
                        topMargin
                    )
                }
            }
        }

        //sort the activities by point value with highest values first
        holder.matchActivityLayouts.sortWith(
            compareBy
            { -it.first }
        )

        //insert the activities ordered by point value
        for (m in holder.matchActivityLayouts) {
            m.second.apply {
                holder.linearLayout.addView(this.activityHolder)

                //NOTE: The top margin for match_list_item can not be set until the layout has a parent. So it must be
                // set programmatically here because the views needs sorted before they are inserted.
                (this.activityHolder.layoutParams as LinearLayout.LayoutParams).topMargin =
                    topMargin
                expandItem(
                    this,
                    this.expanded,
                    false,
                    primaryTitleBarOriginalHeight,
                    separatorLineOriginalWidth,
                    topMargin
                )
            }
        }

        holder.matchLayoutScrollView.fullScroll(ScrollView.FOCUS_UP)
    }

    private fun navigateToNextPicture(
        accountOID: String,
        leftChevronClicked: Boolean,
        selectedIndex: Int,
        picturePath: MutableList<PictureInfo>,
        pictureImageView: ImageView,
        pictureIndexLayout: LinearLayout,
    ): Int {

        val newIndex =
            if (leftChevronClicked && selectedIndex == 0) {
                picturePath.size - 1
            } else if (leftChevronClicked) {
                selectedIndex - 1
            } else if (selectedIndex == picturePath.size - 1) {
                0
            } else {
                selectedIndex + 1
            }

        glideContext
            .load(picturePath[newIndex].picturePath)
            .signature(generateFileObjectKey(picturePath[newIndex].timestampPictureLastUpdatedOnServer))
            .apply(RequestOptions.circleCropTransform())
            .listener(
                object : RequestListener<Drawable?> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        requestUpdateForUser(accountOID)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable?>,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        return false
                    }
                }
            )
            .error(GlobalValues.defaultPictureResourceID)
            .into(pictureImageView)

        val initialView = pictureIndexLayout.getChildAt(selectedIndex)

        setBackgroundPictureIndexImage(
            context,
            initialView,
            false
        )
        //initialView.setBackgroundResource(R.drawable.background_image_button_border)

        val newView = pictureIndexLayout.getChildAt(newIndex)

        val border = GradientDrawable()

        border.setColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.colorGrey,
                null
            )
        )

        border.cornerRadius = context.resources.getDimension(R.dimen.image_button_border_radius)
        newView.background = border

        return newIndex
    }

    class ViewHolder(itemView: View) {

        var accountOID: String = ""

        var pictureInfo = mutableListOf<PictureInfo>()
        var pictureSelectedIndex: Int = 0

        var pictureCardView: MaterialCardView =
            itemView.findViewById(R.id.matchLayoutPictureInclude)
        var pictureImageView: ImageView = itemView.findViewById(R.id.matchLayoutPictureImageView)
        var pictureIndexLayout: LinearLayout =
            itemView.findViewById(R.id.matchLayoutPictureIndexLinearLayout)
        var leftChevronImageView: ImageView =
            itemView.findViewById(R.id.matchLayoutLeftChevronImageView)
        var rightChevronImageView: ImageView =
            itemView.findViewById(R.id.matchLayoutRightChevronImageView)
        var matchLayoutScrollView: ScrollView = itemView.findViewById(R.id.matchLayoutScrollView)

        var nameAgeView: TextView = itemView.findViewById(R.id.matchLayoutNameAgeTextView)
        var locationView: TextView = itemView.findViewById(R.id.matchLayoutLocationTextView)
        var bioView: TextView = itemView.findViewById(R.id.matchLayoutBioTextView)

        var rightOverlay: FrameLayout = itemView.findViewById(R.id.right_overlay)
        var leftOverlay: FrameLayout = itemView.findViewById(R.id.left_overlay)

        var eventTypeTextView: TextView = itemView.findViewById(R.id.matchLayoutEventTypeTextView)

        var linearLayout: LinearLayout =
            itemView.findViewById(R.id.matchLayoutActivitiesListLinearLayout)

        var matchActivityLayouts = mutableListOf<Pair<Double, ActivityHolder>>()

        //will be set to true if requestUpdateSingleUser was called and a response is waiting
        var picturesUpdating = false

        init {
            itemView.tag = "NotSwiped"
        }

        fun clearViewHolder() {

            pictureInfo = mutableListOf()
            pictureSelectedIndex = 0
            pictureIndexLayout.removeAllViews()
            linearLayout.removeAllViews()
            matchActivityLayouts = mutableListOf()
            pictureImageView.setOnClickListener(null)
            leftChevronImageView.setOnClickListener(null)
            rightChevronImageView.setOnClickListener(null)

            pictureIndexLayout.visibility = View.VISIBLE
            leftChevronImageView.visibility = View.VISIBLE
            rightChevronImageView.visibility = View.VISIBLE
            bioView.visibility = View.VISIBLE

            picturesUpdating = false
        }
    }

    class ActivityHolder(
        private val context: Context,
        private val glideContext: RequestManager,
        private val activitiesOrderedByCategoryReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.CategoryActivities>,
        allActivitiesReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.MutableActivityPair>,
        passedMatchActivity: CategoryTimeFrame.CategoryActivityMessage,
        passedUserActivity: CategoryTimeFrame.CategoryActivityMessage?,
        categoryMatch: Boolean,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        isEvent: Boolean,
        eventStatus: LetsGoEventStatus,
        var cardContainerOriginalHeight: Int,
        private val matchListItemDateTimeTextViewWidth: Int,
        private val matchListItemDateTimeTextViewHeight: Int,
        private val timeFrameBarHeight: Int,
        private val errorStore: StoreErrorsInterface
    ) { //errorMessage, LineNumber, Filename are the params for sendErrorMessage

        val expandedHeight: Int
        var animationRunning = false
        val timeFramesLayout: LinearLayout
        val titleBarLayout: ConstraintLayout
        private val parentLinearLayout: LinearLayout

        //private val parentFrameLayout: FrameLayout
        val chevron: ImageView
        private val activityLabel: TextView
        private val activityIcon: ImageView
        val activityHolder: LinearLayout =
            View.inflate(
                context,
                R.layout.list_item_user_info_card_activity,
                null
            ) as LinearLayout

        var totalPointValue =
            0.0 //estimated point value of this activity, used for ordering activities

        var expanded = false

        //will be set to true if the initial separator bar was added
        private var addSeparatorBar = false

        //store separator lines so they can be hidden and shown when handling drop down menu
        private val separatorLineViews = mutableListOf<View>()

        fun setSeparatorLineViewWidth(widthInPx: Int) {
            separatorLineViews.forEach {
                it.layoutParams.width = widthInPx
            }
        }

        private fun setupForSinglePointTime(
            activity: CategoryTimeFrame.CategoryActivityMessage.Builder,
            timeframeIndex: Int
        ) {
            if (activity.timeFrameArrayList[timeframeIndex].stopTimeFrame == activity.timeFrameArrayList[timeframeIndex].startTimeFrame) {
                //30 seconds is used instead of a minute. This is because total time is calculated as
                // stopTime-startTime. This means that if say stopTime is 7:01 and startTime is 7:00 then
                // the total time will be 60 seconds. When the bars are displayed therefore a one minute
                // total time will look the same as a two minute total time. 30 seconds is used so that
                // it will be 'half' of that. This method will not scale properly to higher numbers. However,
                // those situations will be much more difficult for the user to identify exactly. Also it
                // may be worth noting that the smallest granularity the times will be stored in is minutes
                // (although this is not guaranteed).
                activity
                    .setTimeFrameArray(
                        timeframeIndex,
                        activity.timeFrameArrayList[timeframeIndex].toBuilder()
                            .setStopTimeFrame(activity.timeFrameArrayList[timeframeIndex].stopTimeFrame + 30L * 1000L)
                    )
            }
        }

        init {

            //initialize default values
            timeFramesLayout = activityHolder.findViewById(R.id.matchListItemLinearLayout)
            titleBarLayout =
                activityHolder.findViewById(R.id.matchListItemEntireTitleConstraintLayout)
            parentLinearLayout =
                activityHolder.findViewById(R.id.matchListItemWholeCardLinearLayout)
            //parentFrameLayout = activityHolder.findViewById(R.id.matchListItemWholeCardFrameLayout)
            chevron = activityHolder.findViewById(R.id.matchListItemChevronImageView)
            activityLabel = activityHolder.findViewById(R.id.matchListItemLabelTextView)
            activityIcon = activityHolder.findViewById(R.id.matchListItemImageImageView)

            //if the activity matches, this will give a set amount of points
            if (passedUserActivity != null) {
                totalPointValue += GlobalValues.server_imported_values.activityMatchWeight
            }

            //if category match, add a small amount of points for sorting purposes
            if (categoryMatch) {
                totalPointValue += GlobalValues.server_imported_values.categoriesMatchWeight
            }

            if (passedMatchActivity.timeFrameArrayList.isEmpty()) { //if match is anytime
                val anytimeView = View.inflate(
                    context,
                    R.layout.view_user_info_card_anytime,
                    null
                )

                val anytimeTextView =
                    anytimeView.findViewById<TextView>(R.id.matchAnytimeTextViewTextView)

                anytimeTextView.text =
                    if (isEvent) {
                        if (eventStatus == LetsGoEventStatus.CANCELED) {
                            context.getString(R.string.match_screen_fragment_canceled_text)
                        } else {
                            context.getString(R.string.match_screen_fragment_completed_text)
                        }
                    } else {
                        context.getString(R.string.match_screen_fragment_anytime_text)
                    }

                timeFramesLayout.addView(anytimeView)
            } else {

                val matchActivity = passedMatchActivity.toBuilder()
                val currentTimeStamp = getCurrentTimestampInMillis()

                //if the first value in the array is -1L set it to the current time for calculating 'whole time' purposes
                if (matchActivity.timeFrameArrayList.size > 0
                    && matchActivity.timeFrameArrayList[0].startTimeFrame < currentTimeStamp
                ) {
                    matchActivity.timeFrameArrayList[0].toBuilder().startTimeFrame =
                        currentTimeStamp
                }

                //this will hold a list the same size as the matchActivity, values will be set to true if they are already displayed
                val matchTimeFramesDisplayed = mutableListOf<Boolean>()
                var overlappingOrBetweenTimeFrameFound =
                    false //this will be set to true if an overlapping or between time frame is displayed

                for (t in matchActivity.timeFrameArrayList) {
                    matchTimeFramesDisplayed.add(false)
                }

                //if the userActivities variable is not null it means this activity matches the match activity
                passedUserActivity?.let {
                    if (passedUserActivity.timeFrameArrayList.isNotEmpty()) {

                        val timeFrameTimes = mutableListOf<TimeFrameSingleTimeDataClass>()

                        val userActivity = passedUserActivity.toBuilder()

                        //if the first value in the array is -1L set it to the current time for calculating 'whole time' purposes
                        if (userActivity.timeFrameArrayList.size > 0
                            && userActivity.timeFrameArrayList[0].startTimeFrame < currentTimeStamp
                        ) {
                            userActivity.timeFrameArrayList[0].toBuilder().startTimeFrame =
                                currentTimeStamp
                        }

                        var totalOverlapTime =
                            0L //total overlapping time between match time frames and user time frames
                        var totalMatchTime = 0L //total time in the match time frames
                        var totalUserTime = 0L //total time in user time frames

                        for (i in matchActivity.timeFrameArrayList.indices) {
                            timeFrameTimes.add(
                                TimeFrameSingleTimeDataClass(
                                    matchActivity.timeFrameArrayList[i].startTimeFrame,
                                    false,
                                    TimeFrameFrom.MATCH,
                                    i
                                )
                            )

                            setupForSinglePointTime(matchActivity, i)

                            timeFrameTimes.add(
                                TimeFrameSingleTimeDataClass(
                                    //Can look below at the comment for why this function is used.
                                    matchActivity.timeFrameArrayList[i].stopTimeFrame,
                                    true,
                                    TimeFrameFrom.MATCH,
                                    i
                                )
                            )

                            totalMatchTime += matchActivity.timeFrameArrayList[i].stopTimeFrame - matchActivity.timeFrameArrayList[i].startTimeFrame
                        }

                        for (i in userActivity.timeFrameArrayList.indices) {
                            timeFrameTimes.add(
                                TimeFrameSingleTimeDataClass(
                                    userActivity.timeFrameArrayList[i].startTimeFrame,
                                    false,
                                    TimeFrameFrom.USER,
                                    i
                                )
                            )

                            setupForSinglePointTime(userActivity, i)

                            timeFrameTimes.add(
                                TimeFrameSingleTimeDataClass(
                                    //Can look below at the comment for why this function is used.
                                    userActivity.timeFrameArrayList[i].stopTimeFrame,
                                    true,
                                    TimeFrameFrom.USER,
                                    i
                                )
                            )

                            totalUserTime += userActivity.timeFrameArrayList[i].stopTimeFrame - userActivity.timeFrameArrayList[i].startTimeFrame
                        }

                        /** There are a few situations to take into consideration here.
                         *
                         * First is the situation where userA stop time is the same as userB start
                         * time with standard timeframes.
                         * star---stop
                         *        star----stop
                         * In this situation when sorting the time should ideally be marked as a
                         * 'close time' not an 'overlapping time'.
                         *
                         * The second situation is when userA selects a single time as their choice
                         * and this time overlaps with a start time of userB.
                         * time
                         * star----stop
                         * In this situation the time should ideally be marked as an 'overlapping
                         * time' AND be displayed (ie not be 0).
                         *
                         * A third situation is when userA selects a single time as their choice
                         * and this time overlaps with a stop time of userB.
                         *         time
                         * star----stop
                         * In this situation the time should ideally be marked as an 'between
                         * time' AND be displayed (ie not be 0).
                         *
                         * The problem with these situations is that they require different
                         * sorting methods. The first one will require sorting by timestamps, then force the
                         * start time to come AFTER stop time in case of an overlap. The second one will require
                         * sorting by timestamps, then start time to come BEFORE stop time in in case of overlap.
                         *
                         * In order to fix this problem what was done is to simply not allow the second and third
                         * situation to occur. This was done above by checking to see if startTime==stopTime then set the
                         * stopTime to 30 seconds ahead. Because granularity is minutes (although this is not
                         * guaranteed) this should fix the problem. Also it removes any complications of dealing
                         * with a total time that is 0.
                         **/
                        timeFrameTimes.sortWith(
                            compareBy<TimeFrameSingleTimeDataClass>
                            {
                                it.timeStamp
                            }.thenBy {
                                //Forces stop time first.
                                !it.isStopTime
                            }
                        )

                        //These will save the index value from matchActivity or userActivities in
                        // which a between time or overlap time occurred.
                        val overlapIndexes = mutableListOf<Pair<Int, TimeFrameFrom>>()
                        val betweenIndexes = mutableListOf<Pair<Int, TimeFrameFrom>>()
                        var nestingValue = 0

                        var outerOverlappingTimeFrameStartIndex = -1
                        var matchOverlappingTimeFrameStartIndex = -1
                        var userOverlappingTimeFrameStartIndex = -1
                        for (i in timeFrameTimes.indices) {
                            if (timeFrameTimes[i].isStopTime) {

                                nestingValue--

                                //NOTE: If there are multiple time frames that are 'close' then only
                                // the closest is displayed.
                                if (nestingValue == 1) {
                                    //user and match overlapping time frames should be set here
                                    outerOverlappingTimeFrameStartIndex = i

                                    //if the overlap that is stopping is from the currently saved 'outer' index then change the 'outer' index
                                    if (timeFrameTimes[outerOverlappingTimeFrameStartIndex].timeFrameFrom == timeFrameTimes[i].timeFrameFrom) {
                                        if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.USER) {
                                            outerOverlappingTimeFrameStartIndex =
                                                matchOverlappingTimeFrameStartIndex
                                        } else if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.MATCH) {
                                            outerOverlappingTimeFrameStartIndex =
                                                userOverlappingTimeFrameStartIndex
                                        }
                                    }

                                    if (i == timeFrameTimes.size - 1) {
                                        val errorMessage =
                                            "When calculating between times to be displayed nested value was 1 on the final index.\n" +
                                                    "i: $i\n" +
                                                    "timeFrameTimes[i-1]: ${timeFrameTimes[i - 1].timeFrameFrom}\n" +
                                                    "timeFrameTimes[i]: ${timeFrameTimes[i].timeFrameFrom}\n" +
                                                    "timeFrameTimes[i+1]: ${timeFrameTimes[i + 1].timeFrameFrom}\n"

                                        storeError(
                                            errorMessage,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors()
                                        )

                                        return@let //don't display any time frames
                                    }
                                }

                                if (nestingValue == 0
                                    && i != timeFrameTimes.size - 1
                                    && timeFrameTimes[i + 1].timeStamp - timeFrameTimes[i].timeStamp <= GlobalValues.server_imported_values.maxBetweenTime
                                    && timeFrameTimes[i + 1].timeFrameFrom != timeFrameTimes[i].timeFrameFrom
                                ) { //if the nesting value is 0, it is a stop time, the time gap is less than the between time, this is not the final index and it is from a different
                                    // location than the next account then this is the start of the time between 2 time frames
                                    betweenIndexes.add(
                                        Pair(
                                            timeFrameTimes[i].initialArrayIndex,
                                            timeFrameTimes[i].timeFrameFrom
                                        )
                                    )
                                    betweenIndexes.add(
                                        Pair(
                                            timeFrameTimes[i + 1].initialArrayIndex,
                                            timeFrameTimes[i + 1].timeFrameFrom
                                        )
                                    )

                                    //add the points on to the total point value for this 'between' time
                                    totalPointValue +=
                                        GlobalValues.server_imported_values.betweenActivityTimesWeight * (1.0 - (timeFrameTimes[i + 1].timeStamp - timeFrameTimes[i].timeStamp).toDouble() / GlobalValues.server_imported_values.maxBetweenTime)
                                }
                            } else { //is start time

                                nestingValue++
                                if (nestingValue == 1) { //if the nesting value is 1 and it is a start time, save the index to know where the overlap time started
                                    outerOverlappingTimeFrameStartIndex = i

                                    if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.USER) {
                                        userOverlappingTimeFrameStartIndex = i
                                    } else if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.MATCH) {
                                        matchOverlappingTimeFrameStartIndex = i
                                    }
                                } else if (nestingValue == 2) { //if the nesting value is 2 and it is a start time, this is the start of an overlap

                                    if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.USER) {
                                        userOverlappingTimeFrameStartIndex = i
                                    } else if (timeFrameTimes[i].timeFrameFrom == TimeFrameFrom.MATCH) {
                                        matchOverlappingTimeFrameStartIndex = i
                                    }

                                    //save overlapping time frames
                                    overlapIndexes.add(
                                        Pair(
                                            timeFrameTimes[outerOverlappingTimeFrameStartIndex].initialArrayIndex,
                                            timeFrameTimes[outerOverlappingTimeFrameStartIndex].timeFrameFrom
                                        )
                                    )
                                    overlapIndexes.add(
                                        Pair(
                                            timeFrameTimes[i].initialArrayIndex,
                                            timeFrameTimes[i].timeFrameFrom
                                        )
                                    )

                                    totalOverlapTime += timeFrameTimes[i + 1].timeStamp - timeFrameTimes[i].timeStamp

                                    if (i == timeFrameTimes.size - 1) {
                                        val errorMessage =
                                            "When calculating overlapping times to be displayed nested value was 2 on the final index.\n" +
                                                    "i: $i\n" +
                                                    "timeFrameTimes[i-1]: ${timeFrameTimes[i - 1].timeFrameFrom}\n" +
                                                    "timeFrameTimes[i]: ${timeFrameTimes[i].timeFrameFrom}\n" +
                                                    "timeFrameTimes[i+1]: ${timeFrameTimes[i + 1].timeFrameFrom}\n"

                                        storeError(
                                            errorMessage,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors()
                                        )

                                        return@let //don't display any time frames
                                    }
                                }
                            }
                        }

                        totalPointValue += if (totalMatchTime < totalUserTime) {
                            GlobalValues.server_imported_values.overlappingActivityTimesWeight * (totalOverlapTime.toDouble() / totalMatchTime.toDouble())
                        } else {
                            GlobalValues.server_imported_values.overlappingActivityTimesWeight * (totalOverlapTime.toDouble() / totalUserTime.toDouble())
                        }

                        if ((overlapIndexes.size and 1) == 1
                            || (betweenIndexes.size and 1) == 1
                        ) { //check if multiples of 2
                            val errorMessage =
                                "Overlapping or between times were incorrectly formatted when being displayed.\n" +
                                        "overlapIndexes: ${overlapIndexes}\n" +
                                        "betweenIndexes: ${betweenIndexes}\n" +
                                        "timeFrameTimes: ${timeFrameTimes}\n" +
                                        "userActivity: ${userActivity}\n" +
                                        "matchActivity: ${matchActivity}\n"

                            storeError(
                                errorMessage,
                                Thread.currentThread().stackTrace[2].lineNumber,
                                printStackTraceForErrors()
                            )

                            return@let //don't display any time frames
                        }

                        var i =
                            betweenIndexes.size - 2 //2nd to last element because these are in 'pairs'
                        var j = 0

                        //if an element is already an overlap time, do not display it as a between time as well
                        while (i >= 0) {

                            while (j in overlapIndexes.indices) {

                                if (betweenIndexes[i] == overlapIndexes[j]
                                    || betweenIndexes[i + 1] == overlapIndexes[j]
                                    || betweenIndexes[i] == overlapIndexes[j + 1]
                                    || betweenIndexes[i + 1] == overlapIndexes[j + 1]
                                ) {
                                    //started at the back of the list so elements can be removed while the loop is running
                                    betweenIndexes.removeAt(i + 1)
                                    betweenIndexes.removeAt(i)
                                    break
                                }

                                j += 2
                            }
                            i -= 2
                        }

                        if (overlapIndexes.isNotEmpty()) {

                            Log.i("UserInfoCard", "found overlapping indexes")

                            overlappingOrBetweenTimeFrameFound = true

                            if (overlapIndexes.size / 2 > 1) {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_overlapping_times_plural_text))
                            } else {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_overlapping_times_singular_text))
                            }

                            setupOverlapAndBetweenTimesViews(
                                overlapIndexes,
                                matchActivity,
                                userActivity,
                                matchTimeFramesDisplayed,
                                currentTimeStamp
                            )
                        }

                        if (betweenIndexes.isNotEmpty()) {

                            Log.i("UserInfoCard", "found between indexes")

                            overlappingOrBetweenTimeFrameFound = true

                            if (betweenIndexes.size / 2 > 1) {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_close_times_plural_text))
                            } else {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_close_times_singular_text))
                            }

                            setupOverlapAndBetweenTimesViews(
                                betweenIndexes,
                                matchActivity,
                                userActivity,
                                matchTimeFramesDisplayed,
                                currentTimeStamp
                            )
                        }
                    }
                }

                var initialSpaceHasBeenInserted = false
                for (i in matchActivity.timeFrameArrayList.indices) {
                    if (!matchTimeFramesDisplayed[i]) {

                        //only insert the first separator bar once
                        if (!initialSpaceHasBeenInserted) {
                            initialSpaceHasBeenInserted = true

                            if (overlappingOrBetweenTimeFrameFound) {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_other_times_plural_text))
                            } else {
                                setupTitleTextView(context.getString(R.string.match_screen_fragment_other_times_singular_text))
                            }
                        }

                        addSeparatorBarToLayout()

                        setupTimeFrameViews(
                            matchActivity.timeFrameArrayList[i].stopTimeFrame - matchActivity.timeFrameArrayList[i].startTimeFrame,
                            false,
                            matchActivity.timeFrameArrayList[i].startTimeFrame,
                            matchActivity.timeFrameArrayList[i].stopTimeFrame,
                            matchActivity.timeFrameArrayList[i],
                            currentTimeStamp,
                            R.color.match_list_item_matching_user_time_frame_bar,
                            //android.R.color.holo_blue_light,
                            centerTimeFrameBar = true
                        )
                    }
                }
            }

            if (passedMatchActivity.activityIndex < allActivitiesReference.size) {
                allActivitiesReference[passedMatchActivity.activityIndex].activity.apply {

                    val color = ResourcesCompat.getColor(
                        context.resources,
                        R.color.color_white_foreground,
                        context.theme
                    )

                    activityLabel.text = this.displayName
                    activityLabel.setTextColor(color)

                    val drawable = getIconDrawable(
                        this.iconIndex,
                        context,
                        errorStore
                    )

                    glideContext
                        .load(drawable)
                        .error(GlobalValues.defaultIconImageID)
                        .into(activityIcon)

                    ImageViewCompat.setImageTintList(
                        activityIcon,
                        ColorStateList.valueOf(color)
                    )

                    titleBarLayout.background = buildGradientDrawableWithTint(
                        context,
                        activitiesOrderedByCategoryReference[this.categoryIndex].category.color,
                        false
                    )

                    setupBackground(
                        parentLinearLayout,
                        activitiesOrderedByCategoryReference[this.categoryIndex].category.color,
                        errorStore
                    )

                }
            } else {
                val errorMessage =
                    "The passed activity should always be in the copy of the CategoriesAndActivities allActivitiesReference list.\n" +
                            "passedMatchActivity: ${passedMatchActivity.activityIndex}\n" +
                            "allActivities: ${allActivitiesReference}\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )

                //can continue here
            }

            //calculate expanded height
            timeFramesLayout.measure(widthMeasureSpec, heightMeasureSpec)

            //calculate height of category title bar, only needs to be done once
            if (cardContainerOriginalHeight < 0) {
                titleBarLayout.measure(widthMeasureSpec, heightMeasureSpec)
                parentLinearLayout.measure(widthMeasureSpec, heightMeasureSpec)
                val extraHeight =
                    parentLinearLayout.measuredHeight - titleBarLayout.measuredHeight - timeFramesLayout.measuredHeight
                cardContainerOriginalHeight = titleBarLayout.measuredHeight + extraHeight
            }

            expandedHeight = timeFramesLayout.measuredHeight + cardContainerOriginalHeight

            Log.i(
                "heights",
                "cardContainerOriginalHeight: $cardContainerOriginalHeight expandedHeight: $expandedHeight"
            )

            chevron.setImageResource(android.R.drawable.arrow_down_float)
        }

        private fun setupTitleTextView(title: String) {

            addSeparatorBarToLayout()

            val textViewLayout =
                View.inflate(
                    context,
                    R.layout.view_user_info_card_time_frame_title,
                    null
                ) as FrameLayout
            textViewLayout.findViewById<TextView>(R.id.matchTimeFrameTitleTextView).text = title
            timeFramesLayout.addView(textViewLayout)
        }

        //Will set up necessary values to generate an overlap or between time colored bar pair. Will also set
        // each timeFrame that is accessed to 'displayed'.
        private fun setupOverlapAndBetweenTimesViews(
            passedIndexes: MutableList<Pair<Int, TimeFrameFrom>>,
            matchActivity: CategoryTimeFrame.CategoryActivityMessage.Builder,
            userActivity: CategoryTimeFrame.CategoryActivityMessage.Builder,
            matchTimeFramesDisplayed: MutableList<Boolean>,
            currentTimeStamp: Long,
        ) {

            for (i in 0 until passedIndexes.size step 2) {

                addSeparatorBarToLayout()

                var userTimeFrame =
                    CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder().build()
                var matchTimeFrame =
                    CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder().build()

                if (passedIndexes[i].second == TimeFrameFrom.MATCH
                    && passedIndexes[i + 1].second == TimeFrameFrom.USER
                ) {
                    matchTimeFrame = matchActivity.timeFrameArrayList[passedIndexes[i].first]
                    userTimeFrame = userActivity.timeFrameArrayList[passedIndexes[i + 1].first]
                    matchTimeFramesDisplayed[passedIndexes[i].first] = true

                } else if (passedIndexes[i].second == TimeFrameFrom.USER
                    && passedIndexes[i + 1].second == TimeFrameFrom.MATCH
                ) {
                    userTimeFrame = userActivity.timeFrameArrayList[passedIndexes[i].first]
                    matchTimeFrame =
                        matchActivity.timeFrameArrayList[passedIndexes[i + 1].first]
                    matchTimeFramesDisplayed[passedIndexes[i + 1].first] = true
                } else {
                    val errorMessage =
                        "When calculating passed indexes to be displayed two indexes were from the same 'location'.\n" +
                                "'location'[i]: ${passedIndexes[i].second}\n" +
                                "'location'[i+1]: ${passedIndexes[i + 1].second}\n" +
                                "matchActivity: ${matchActivity}\n" +
                                "userActivity: ${userActivity}\n" +
                                "passedIndexes: ${passedIndexes}\n" +
                                "matchTimeFramesDisplayed: ${matchTimeFramesDisplayed}\n" +
                                "currentTimeStamp: ${currentTimeStamp}\n"

                    storeError(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }

                val firstStartTime =
                    if (userTimeFrame.startTimeFrame < matchTimeFrame.startTimeFrame) {
                        userTimeFrame.startTimeFrame
                    } else {
                        matchTimeFrame.startTimeFrame
                    }

                val finalStopTime =
                    if (userTimeFrame.stopTimeFrame < matchTimeFrame.stopTimeFrame) {
                        matchTimeFrame.stopTimeFrame
                    } else {
                        userTimeFrame.stopTimeFrame
                    }

                val wholeTime = finalStopTime - firstStartTime

                setupTimeFrameViews(
                    wholeTime,
                    false,
                    firstStartTime,
                    finalStopTime,
                    matchTimeFrame,
                    currentTimeStamp,
                    R.color.match_list_item_matching_user_time_frame_bar
                )

                setupTimeFrameViews(
                    wholeTime,
                    true,
                    firstStartTime,
                    finalStopTime,
                    userTimeFrame,
                    currentTimeStamp,
                    R.color.match_list_item_current_user_time_frame_bar
                )

            }
        }

        //Will set up a single colored bar by building the layout match_time_frame.xml and modifying the parameters
        private fun setupTimeFrameViews(
            wholeTime: Long,
            isUserTimeFrame: Boolean,
            entireTimeFrameStartTime: Long,
            entireTimeFrameStopTime: Long,
            timeFrame: CategoryTimeFrame.CategoryTimeFrameMessage,
            currentTimeStamp: Long,
            resourceColorID: Int,
            centerTimeFrameBar: Boolean = false
        ) {

            //It may be possible for wholeTime to be zero if the start and stop time are the same.
            val wholeTimeDouble = max(1, wholeTime).toDouble()

            val timeframeItem: LinearLayout =
                View.inflate(
                    context,
                    R.layout.view_user_info_card_time_frame,
                    null
                ) as LinearLayout

            val startTimeFrame =
                timeframeItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStartTimeTextView)

            val startTimeFrameTextViewLayout = LinearLayout.LayoutParams(
                matchListItemDateTimeTextViewWidth,
                matchListItemDateTimeTextViewHeight
            ).apply {
                gravity = Gravity.CENTER
            }

            startTimeFrame.layoutParams = startTimeFrameTextViewLayout

            if (timeFrame.startTimeFrame <= currentTimeStamp) {
                //set to single line style for formatting
                startTimeFrame.inputType = EditorInfo.TYPE_CLASS_TEXT

                //create match time
                startTimeFrame.text = context.getString(R.string.match_screen_fragment_now_text)
            } else {
                //create match time
                startTimeFrame.text =
                    formatUnixTimeStampToDateString(
                        timeFrame.startTimeFrame,
                        true,
                        errorStore
                    )
            }

            val stopTimeFrame =
                timeframeItem.findViewById<TextView>(R.id.matchTimeFramesOverlapStopTimeTextView)

            stopTimeFrame.layoutParams.apply {
                width = matchListItemDateTimeTextViewWidth
                height = matchListItemDateTimeTextViewHeight
            }

            stopTimeFrame.text =
                formatUnixTimeStampToDateString(
                    timeFrame.stopTimeFrame,
                    true,
                    errorStore
                )

            //Because a value if -1 is the same as the current time, the start times must be set to the current time
            // in order to get mathematical consistency for calculation the weights.
            val updatedTimeFrameStartTime =
                if (timeFrame.startTimeFrame < currentTimeStamp) currentTimeStamp else timeFrame.startTimeFrame
            val updatedEntireTimeFrameStartTime =
                if (entireTimeFrameStartTime < currentTimeStamp) currentTimeStamp else entireTimeFrameStartTime

            val timeFrameStartPercent: Float =
                ((updatedTimeFrameStartTime - updatedEntireTimeFrameStartTime).toDouble() / wholeTimeDouble).toFloat()

            val startSpaceLayoutParams = LinearLayout.LayoutParams(
                0, 0
            ).apply {
                weight = timeFrameStartPercent
            }

            val timeFrameStopPercent: Float =
                ((entireTimeFrameStopTime - timeFrame.stopTimeFrame).toDouble() / wholeTimeDouble).toFloat()

            Log.i(
                "userInfoCard_stuff",
                "timeFrameStartPercent: $timeFrameStartPercent timeFrameStopPercent: $timeFrameStopPercent"
            )
            Log.i(
                "userInfoCard_stuff",
                "entireTimeFrameStartTime: $entireTimeFrameStartTime entireTimeFrameStopTime: $entireTimeFrameStopTime"
            )
            Log.i(
                "userInfoCard_stuff",
                "timeFrame.startTimeFrame: ${timeFrame.startTimeFrame} timeFrame.stopTimeFrame: ${timeFrame.stopTimeFrame}"
            )

            timeframeItem.findViewById<Space>(R.id.matchTimeFramesOverlapStartTimeSpace).layoutParams =
                startSpaceLayoutParams

            val stopSpaceLayoutParams = LinearLayout.LayoutParams(
                0, 0
            ).apply {
                weight = timeFrameStopPercent
            }

            timeframeItem.findViewById<Space>(R.id.matchTimeFramesOverlapStopTimeSpace).layoutParams =
                stopSpaceLayoutParams

            //There is the chance that the difference between stopTimeFrame and startTimeFrame could be zero. Want
            // to avoid this.
            val timeFrameColorTextViewPercent: Float =
                ((max(
                    1,
                    timeFrame.stopTimeFrame - timeFrame.startTimeFrame
                )).toDouble() / wholeTimeDouble).toFloat()

            val matchTimeLayoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = timeFrameColorTextViewPercent
                height = timeFrameBarHeight

                when {
                    isUserTimeFrame -> {
                        gravity = Gravity.TOP
                        setMargins(
                            0,
                            context.resources.getDimension(R.dimen.match_screen_card_adapter_user_time_frame_top_margin)
                                .toInt(),
                            0,
                            0
                        )
                    }
                    centerTimeFrameBar -> {
                        gravity = Gravity.CENTER
                    }
                    else -> {
                        gravity = Gravity.BOTTOM
                        setMargins(
                            0,
                            0,
                            0,
                            context.resources.getDimension(R.dimen.match_screen_card_adapter_match_time_frame_bottom_margin)
                                .toInt()
                        )
                    }
                }
            }

            val colorTextView =
                timeframeItem.findViewById<TextView>(R.id.matchTimeFramesOverlapColorTextView)

            colorTextView.apply {

                layoutParams = matchTimeLayoutParams

                this.setBackgroundResource(R.drawable.background_rounded_corners_no_stroke)
                this.background.colorFilter = PorterDuffColorFilter(
                    ResourcesCompat.getColor(
                        context.resources,
                        resourceColorID,
                        null
                    ),
                    PorterDuff.Mode.SRC_IN
                )

            }

            timeFramesLayout.addView(timeframeItem)
        }

        private fun addSeparatorBarToLayout() {

            //avoiding the first separator bar for this activity
            if (!addSeparatorBar) {
                addSeparatorBar = true
                return
            }

            val separatorLineView = View(context, null)

            separatorLineViews.add(separatorLineView)

            val separatorLineLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimension(R.dimen.match_screen_card_adapter_separator_bar_width)
                    .toInt()
            )

            separatorLineLayoutParams.gravity = Gravity.CENTER_HORIZONTAL

            separatorLineView.setBackgroundColor(
                ResourcesCompat.getColor(
                    context.resources,
                    R.color.match_list_border_separator_color,
                    null
                )
            )
            separatorLineView.layoutParams = separatorLineLayoutParams

            timeFramesLayout.addView(separatorLineView)
        }

        private fun storeError(
            passedErrMsg: String,
            lineNumber: Int,
            stackTrace: String
        ) {
            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                lineNumber,
                stackTrace,
                passedErrMsg,
                context.applicationContext
            )
        }
    }

}