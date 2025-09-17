package site.letsgoapp.letsgo.utilities

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import categorytimeframe.CategoryTimeFrame
import loginsupport.NeededVeriInfoResponse
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.GlobalValues.getIconDrawable
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import java.io.IOException

data class ReturnUserSelectedCategoriesAndAgeDataHolder(
    val age: Int,
    val categoriesArrayList: MutableList<CategoryTimeFrame.CategoryActivityMessage>
)

data class NeededVeriInfoDataHolder(
    val neededVeriInfoResponse: NeededVeriInfoResponse,
    val errorStatus: GrpcFunctionErrorStatusEnum
)

data class SetCategoriesUpdatedForViewModelDataHolder(
    val updatedCategories: MutableList<CategoryTimeFrame.CategoryActivityMessage>,
    val categoriesTimestamp: Long
)

const val FRAGMENT_CALLED_FROM_KEY = "fragment_called_from_key"

enum class SelectCategoriesFragmentCalledFrom {
    UNKNOWN,
    LOGIN_FRAGMENT,
    APPLICATION_PROFILE_FRAGMENT;

    companion object {
        fun setVal(value: Int?): SelectCategoriesFragmentCalledFrom {
            return when (value) {
                1 -> LOGIN_FRAGMENT
                2 -> APPLICATION_PROFILE_FRAGMENT
                else -> UNKNOWN
            }
        }
    }
}

data class LetsGoSingleActivity(
    val textView: TextView,
    var activityIndex: Int = -1, //-1 means no activity set
    var timeFrames: MutableList<LetsGoActivityTimeFrame> = mutableListOf(),
) {

    override fun toString(): String {

        var returnStr = "Single Activity\n" +
                "textView.text: ${textView.text}\n" +
                "activityIndex: $activityIndex\n" +
                "timeFrames: {\n"

        for (t in timeFrames) {
            "t: ${t.toString(2)}"
        }

        returnStr += "}\n"

        return returnStr
    }
}

//values start at 0 and go to their max; ex: month values are 0-11
//year is actual year
//day is actual day
//hour is calendar type Hour_Of_Day
//minute is calendar type Minute
//takes in the Calendar.Year Calendar.Month and Calendar.Day_Of_Month
//userSelectedIndex is the index this will be saved to at the end, -1 means a new index will be added
//cancelEnum tells the StartStopChooserDialog where to navigate when cancel is pressed
data class CategoryTimeBlock(
    var startYear: Int = -1,
    var startMonth: Int = -1,
    var startDay: Int = -1,
    var startHour: Int = -1,
    var startMinute: Int = -1,
    var stopYear: Int = -1,
    var stopMonth: Int = -1,
    var stopDay: Int = -1,
    var stopHour: Int = -1,
    var stopMinute: Int = -1,
    var startTimeTimestamp: Long = -1L,
    var stopTimeTimestamp: Long = -1L,
    var timeFrameIndex: Int = -1,
    var calledFromEnum: StartStopChooserDialogCallerEnum = StartStopChooserDialogCallerEnum.UNKNOWN,
    var userSelectedIndex: Int = -1,
    var activityIndex: Int = -1,
)

data class LetsGoActivityTimeFrame(val startTimeTimestamp: Long, val stopTimeTimestamp: Long) {
    fun toString(numSpacesForIndex: Int): String {

        var indentation = ""

        for (i in 0..numSpacesForIndex) {
            indentation += ' '
        }

        return "$indentation Timeframe {\n" +
                "$indentation startTimeTimestamp: $startTimeTimestamp\n" +
                "$indentation stopTimeTimestamp: $stopTimeTimestamp\n" +
                "$indentation }\n"
    }
}

enum class TimeFrameFrom {
    USER,
    MATCH
}

data class TimeFrameTimeDataClass(var timeStamp: Long, var isStopTime: Boolean)

//Will convert the string to a mutable list. It will also check the timeframes and remove
// any out of date timeframes.
//Returns a boolean which is true if the list requires an update inside the database (at least
// one timeframe was removed) false if it does not. And the final mutable list.
fun convertStringToCategoryActivityMessageAndTrimTimes(categoriesString: String?):
        Pair<Boolean, MutableList<CategoryTimeFrame.CategoryActivityMessage>> {

    var extractedCategories = mutableListOf<CategoryTimeFrame.CategoryActivityMessage>()

    if (categoriesString?.isNotBlank() == true && categoriesString != "~") {

        categoriesString.let {
            extractedCategories = convertStringToCategoryActivityMessage(it)
        }

        val calendarStartTimestamp = getCurrentTimestampInMillis()

        //Don't want to take stop times taken into account because event stop times can come after
        // the max time below.
//        val calendarStopTimestamp =
//            calendarStartTimestamp + GlobalValues.server_imported_values.timeAvailableToSelectTimeFrames

        val updatedCategories = ArrayList<CategoryTimeFrame.CategoryActivityMessage>()
        for (c in extractedCategories) {

            val updateTimeFrames = ArrayList<CategoryTimeFrame.CategoryTimeFrameMessage>()
            for (t in c.timeFrameArrayList) {

                if (t.startTimeFrame <= t.stopTimeFrame
                    && calendarStartTimestamp < t.stopTimeFrame
                ) {

                    if (t.startTimeFrame < calendarStartTimestamp) {
                        updateTimeFrames.add(
                            CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder()
                                .setStartTimeFrame(-1)
                                .setStopTimeFrame(t.stopTimeFrame)
                                .build()
                        )
                    } else {
                        updateTimeFrames.add(
                            CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder()
                                .setStartTimeFrame(t.startTimeFrame)
                                .setStopTimeFrame(t.stopTimeFrame)
                                .build()
                        )
                    }
                }
            }

            updatedCategories.add(
                CategoryTimeFrame.CategoryActivityMessage.newBuilder()
                    .setActivityIndex(c.activityIndex)
                    .addAllTimeFrameArray(updateTimeFrames)
                    .build()
            )
        }

        var timeFramesUpdated = false

        //if the time frames were updated then return a bool signaling to update the database
        if (updatedCategories != extractedCategories) {
            timeFramesUpdated = true
        }

        return Pair(timeFramesUpdated, updatedCategories)
    } else { //if the string was "~"
        return Pair(false, extractedCategories)
    }
}

//NOTE: For most use cases use convertStringToCategoryActivityMessageAndTrimTimes() instead.
private fun convertStringToCategoryActivityMessage(categoriesString: String):
        MutableList<CategoryTimeFrame.CategoryActivityMessage> {

    val categories = ArrayList<CategoryTimeFrame.CategoryActivityMessage>()

    var i = 0
    //string format
    //activityType1[startTime1,stopTime1,startTime2,stopTime2...]activityType2[startTime3,stopTime3,startTime4,stopTime4...]...
    while (i < categoriesString.length) {

        var activityTypeString = ""
        while (categoriesString[i] != '[') { //extract activity type int value
            activityTypeString += categoriesString[i]
            i++
        }
        i++

        val timeFrameArray = ArrayList<CategoryTimeFrame.CategoryTimeFrameMessage>()
        while (categoriesString[i] != ']') { //extract time frames

            var timeFrameStartTime: Long = -1L
            var timeFrameStopTime: Long = -1L

            for (j in 0..1) { //extract the start and stop times for 1 time frame
                var timeFrameString = ""
                while (categoriesString[i] != ',') { //extract individual times
                    timeFrameString += categoriesString[i]
                    i++
                }
                if (j == 0) {
                    timeFrameStartTime = timeFrameString.toLong()
                } else {
                    timeFrameStopTime = timeFrameString.toLong()
                }
                i++
            }

            timeFrameArray.add(
                CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder()
                    .setStartTimeFrame(timeFrameStartTime)
                    .setStopTimeFrame(timeFrameStopTime)
                    .build()
            )

        }
        i++

        categories.add(
            CategoryTimeFrame.CategoryActivityMessage.newBuilder()
                .setActivityIndex(activityTypeString.toInt())
                .addAllTimeFrameArray(timeFrameArray)
                .build()
        )
    }

    return categories
}

fun convertCategoryActivityMessageToStringWithErrorChecking(categories: List<CategoryTimeFrame.CategoryActivityMessage>): String {

    //This block prevents the database from storing info that is too long
    val updatedCategories = ArrayList<CategoryTimeFrame.CategoryActivityMessage>()
    for (i in categories.indices) {

        if (i < GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {

            val updatedTimeFrameArray = ArrayList<CategoryTimeFrame.CategoryTimeFrameMessage>()
            for (j in categories[i].timeFrameArrayList.indices) {

                if (j < GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount) {

                    updatedTimeFrameArray.add(
                        CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(categories[i].timeFrameArrayList[j].startTimeFrame)
                            .setStopTimeFrame(categories[i].timeFrameArrayList[j].stopTimeFrame)
                            .build()
                    )
                }
            }
            updatedCategories.add(
                CategoryTimeFrame.CategoryActivityMessage.newBuilder()
                    .setActivityIndex(categories[i].activityIndex)
                    .addAllTimeFrameArray(updatedTimeFrameArray)
                    .build()
            )
        }
    }

    return convertCategoryActivityMessageToString(updatedCategories)
}

fun convertCategoryActivityMessageToString(categories: MutableList<CategoryTimeFrame.CategoryActivityMessage>): String {

    var finalString = ""

    //string format
    //activityType1[startTime1,stopTime1,startTime2,stopTime2...]activityType2[startTime3,stopTime3,startTime4,stopTime4...]...
    for (i in categories.indices) {
        finalString += categories[i].activityIndex.toString() +
                "["

        for (j in categories[i].timeFrameArrayList.indices) {
            finalString +=
                categories[i].timeFrameArrayList[j].startTimeFrame.toString() +
                        "," +
                        categories[i].timeFrameArrayList[j].stopTimeFrame.toString() +
                        ","
        }

        finalString += "]"
    }

    return finalString
}

fun saveIconToTextView(
    context: Context,
    textView: TextView?,
    activityIndex: Int,
    selectedIcon: Boolean,
    activityIconDrawableHeight: Int,
    activityIconDrawableWidth: Int,
    activitiesOrderedByCategoryReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.CategoryActivities>,
    allActivitiesReference: CategoriesAndActivities.ProtectedAccessList<CategoriesAndActivities.MutableActivityPair>,
    errorStore: StoreErrorsInterface
) {
    textView?.let {

        val drawable = getIconDrawable(
            allActivitiesReference[activityIndex].activity.iconIndex,
            context,
            errorStore
        )

        val finalDrawable = if (selectedIcon) {
            generateSelectedIcon(
                drawable,
                Color.parseColor(
                    activitiesOrderedByCategoryReference[
                            allActivitiesReference[activityIndex].activity.categoryIndex
                    ].category.color
                ),
                context
            )
        } else {
            generateUnSelectedIcon(
                drawable,
                context,
                Color.parseColor(
                    activitiesOrderedByCategoryReference[
                            allActivitiesReference[activityIndex].activity.categoryIndex
                    ].category.color
                ),
                R.color.card_background_color
            )
        }

        finalDrawable.setBounds(
            0,
            0,
            activityIconDrawableHeight,
            activityIconDrawableWidth
        )

        textView.setCompoundDrawablesRelative(
            null, finalDrawable, null, null
        )
    }
}

suspend fun setPictureToDeleted(
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    pictureIndex: Int,
    deleteFileInterface: StartDeleteFileInterface
) {
    val pictureToReplace =
        accountPicturesDataSource.getSinglePicture(pictureIndex)

    if (pictureToReplace != null) {
        //delete previously stored the picture
        deleteFileInterface.sendFileToWorkManager(pictureToReplace.picturePath)
        accountPicturesDataSource.removeSinglePicture(pictureIndex)
    }
}

data class SaveUserPictureReturnValues(
    val picturePath: String,
    val errorString: String
)

//save picture to file and database
//transaction wrapper should lock ACCOUNTS database
//returns "~" if no error, any other return signifies an error
suspend fun saveUserPictureToFileAndDatabase(
    applicationContext: Context,
    accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    pictureIndex: Int,
    pictureInBytes: ByteArray,
    pictureSize: Int,
    timestamp: Long,
    deleteFileInterface: StartDeleteFileInterface,
    transactionWrapper: TransactionWrapper? = null,
): SaveUserPictureReturnValues {

    var picturePath = ""

    if (pictureSize > 0) { //if the file size is not 0
        val file = generateCurrentUserPictureFile(pictureIndex, timestamp, applicationContext)

        val newTransactionWrapper = transactionWrapper
            ?: ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.ACCOUNTS
            )

        //save the picture to file
        //NOTE: not using another coRoutine because I only want the database values stored if the file
        //is successfully saved to 'disk'
        try {
            file.writeBytes(pictureInBytes)
            picturePath = file.absolutePath
            newTransactionWrapper.runTransaction {

                val pictureToReplace =
                    accountPicturesDataSource.getSinglePicture(pictureIndex)

                if (pictureToReplace != null
                    && pictureToReplace.picturePath != file.absolutePath
                    && pictureToReplace.picturePath.isNotBlank()
                ) {
                    //delete previously stored picture
                    deleteFileInterface.sendFileToWorkManager(pictureToReplace.picturePath)
                }

                //save the picture to the database
                accountPicturesDataSource.insertSinglePicture(
                    pictureIndex,
                    file.absolutePath,
                    pictureSize,
                    timestamp
                )

            }

        } catch (ex: IOException) {

            return SaveUserPictureReturnValues(
                "",
                "IOException from requestPictures\nIndex Number: $pictureIndex" +
                        "\nPicture Size: $pictureSize\nPicture Timestamp: $timestamp"
            )
        }
    } else if (pictureIndex > 0) { //if picture size is 0, this means that the picture has been deleted
        setPictureToDeleted(
            accountPicturesDataSource,
            pictureIndex,
            deleteFileInterface
        )
    }

    return SaveUserPictureReturnValues(
        picturePath,
        "~"
    )

}

//adds a filled circle background of the specified color to the image view
fun generateUnSelectedIcon(
    passedDrawable: Drawable?,
    context: Context,
    iconColor: Int,
    backgroundColorId: Int
): Drawable {

    val bitmap = Bitmap.createBitmap(
        GlobalValues.server_imported_values.activityIconWidthInPixels,
        GlobalValues.server_imported_values.activityIconHeightInPixels,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)

    val paint = Paint()
    paint.style = Paint.Style.FILL
    paint.color = iconColor
    paint.isAntiAlias = true

    //Convoluted Kotlin way of typing "radius = Math.min(canvas.width, canvas.height/2)"
    val radius = canvas.width.coerceAtMost(canvas.height / 2)

    canvas.drawCircle(
        (canvas.width / 2).toFloat(),
        (canvas.height / 2).toFloat(),
        radius.toFloat(),
        paint
    )
    //Color.parseColor(GlobalValues.server_imported_values.activityIconBackgroundColor)
    paint.color =
        ResourcesCompat.getColor(
            context.resources,
            backgroundColorId,
            context.theme
        )

    canvas.drawCircle(
        (canvas.width / 2).toFloat(),
        (canvas.height / 2).toFloat(),
        radius.toFloat() - GlobalValues.server_imported_values.activityIconBorderWidth.toFloat(),
        paint
    )

    //NOTE: setBounds MUST BE SET for .draw() to work
    passedDrawable?.setBounds(
        GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconWidthInPixels - GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconHeightInPixels - GlobalValues.server_imported_values.activityIconPadding
    )

    passedDrawable?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
    passedDrawable?.draw(canvas)

    return BitmapDrawable(context.resources, bitmap)
}

//adds a filled circle background of the specified color to the image view
fun generateSelectedIcon(
    passedDrawable: Drawable?,
    backgroundColor: Int,
    context: Context
): Drawable {

    val bitmap = Bitmap.createBitmap(
        GlobalValues.server_imported_values.activityIconWidthInPixels,
        GlobalValues.server_imported_values.activityIconHeightInPixels,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)

    val paint = Paint()
    paint.style = Paint.Style.FILL
    paint.color = backgroundColor
    paint.isAntiAlias = true

    //Convoluted Kotlin way of typing "radius = Math.min(canvas.width, canvas.height/2)"
    val radius = canvas.width.coerceAtMost(canvas.height / 2)

    canvas.drawCircle(
        (canvas.width / 2).toFloat(),
        (canvas.height / 2).toFloat(),
        radius.toFloat(),
        paint
    )

    //NOTE: setBounds MUST BE SET for .draw() to work
    passedDrawable?.setBounds(
        GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconWidthInPixels - GlobalValues.server_imported_values.activityIconPadding,
        GlobalValues.server_imported_values.activityIconHeightInPixels - GlobalValues.server_imported_values.activityIconPadding,
    )
    passedDrawable?.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    passedDrawable?.draw(canvas)

    return BitmapDrawable(context.resources, bitmap)
}

fun TextView.setupAsActivityTextView(
    spaceBetweenIcons: Int,
    textMsg: String?,
    safeClickListener: SafeClickListener?,
    textSizeInSp: Float,
    activityIconMaxLayoutWidthPX: Int,
    activityIconMaxLayoutHeightPX: Int,
    addMargin: Boolean,
    saveIconToTextView: (TextView) -> Unit
): TextView {
    setHorizontallyScrolling(false)
    textSize = textSizeInSp
    gravity = Gravity.CENTER

    text = textMsg

    saveIconToTextView(this)

    layoutParams =
        TableRow.LayoutParams(
            activityIconMaxLayoutWidthPX,
            TableRow.LayoutParams.WRAP_CONTENT //GlobalValues.getActivityIconMaxLayoutHeight(context)
        ).apply {
            maxHeight = activityIconMaxLayoutHeightPX
            if(addMargin)
                marginStart = spaceBetweenIcons
        }

    //NOTE: setting maxLines MUST go after the layout params are set, or they will not work.
    maxLines = 3
    ellipsize = TextUtils.TruncateAt.END

    setSafeOnClickListener(safeClickListener)

    return this
}

//strokeColorId will only be used if setStroke==true.
fun buildGradientDrawableWithTint(
    context: Context,
    hexColorString: String,
    setStroke: Boolean,
    strokeColorId: Int = R.color.colorWhite
): GradientDrawable {
    return GradientDrawable().apply {
        colors = setupColorsForGradients(hexColorString)
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        gradientType = GradientDrawable.LINEAR_GRADIENT
        cornerRadius = context.resources.getDimension(R.dimen.various_items_border_radius)

        if(setStroke) {
            val hexColor = java.lang.String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(context, strokeColorId))

            setStroke(
                3,
                Color.parseColor(
                    hexColor.generateTintedColor(
                        -30
                    )
                )
            )
        }
    }
}

fun setupBackground(
    viewWithBackground: View,
    hexColorString: String,
    errorStore: StoreErrorsInterface
) {
    val drawable = (viewWithBackground.background as LayerDrawable)
    drawable.mutate()

    if(0 < drawable.numberOfLayers) { //has at least one element
        val item1 = drawable.getDrawable(0) as GradientDrawable

        //change the colors of the first drawable
        item1.colors = setupColorsForGradients(hexColorString)

        viewWithBackground.background = drawable
    } else {
        val errorMessage =
            "Error when setting up a background, padded drawable has no layers.\n" +
                    "hexColorString: $hexColorString" +
                    "numberOfLayers: ${drawable.numberOfLayers}"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            Thread.currentThread().stackTrace[2].lineNumber,
            printStackTraceForErrors(),
            errorMessage,
            GlobalValues.applicationContext
        )
    }
}

fun setupColorsForGradients(
    hexColorString: String
): IntArray {
    return intArrayOf(
        Color.parseColor(hexColorString),
        Color.parseColor(
            hexColorString.generateTintedColor(
                GlobalValues.GRADIENT_TINT_AMOUNT
            )
        )
    )
}