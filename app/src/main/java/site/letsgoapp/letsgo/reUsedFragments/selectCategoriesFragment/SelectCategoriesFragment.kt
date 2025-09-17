package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import access_status.AccessStatusEnum
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import categorytimeframe.CategoryTimeFrame
import loginsupport.NeededVeriInfoResponse
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment.NoPredictiveAnimLinearLayoutManager
import site.letsgoapp.letsgo.databinding.DialogFragmentDatePickerCalendarBinding
import site.letsgoapp.letsgo.databinding.DialogFragmentTimePickerBinding
import site.letsgoapp.letsgo.databinding.FragmentSelectCategoriesBinding
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.loginActivityFragments.SharedLoginViewModel
import site.letsgoapp.letsgo.utilities.*
import java.lang.Integer.max
import java.util.*
import java.util.regex.Pattern
import kotlin.math.ceil


class SelectCategoriesFragment(
    private val initializeLoginActivity: Boolean = true,
    private val factoryProducer: (() -> ViewModelProvider.Factory)? = null,
) : Fragment(),
    MyDatePickerDialog.MyDatePickerDialogListener,
    SelectChoicesDialog.SelectChoicesDialogListener,
    MyTimePickerDialog.MyTimePickerStartDialogListener,
    StartStopChooserDialog.MyStartStopPickerDialogListener,
    StoredIconDataDialog.MyStoredIconDataDialogListener {

    private var _binding: FragmentSelectCategoriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var selectCategoriesViewModel: SelectCategoriesViewModel
    private lateinit var thisFragmentInstanceID: String

    //is set to true if any data is updated, the data will be sent to the database onPause
    private var dataUpdated = false

    //the holder for the time frame being updated
    private var categoryTimeBlock = CategoryTimeBlock()

    //recycler view adapter
    private var recyclerViewAdapter: CategoriesListAdapter? = null

    //dialogs
    //NOTE: Leak canary will show that these are retained, this is correct temporarily, however they are cleared
    // onDestroyView(). They are held like this in order to extract previous info from them when necessary. And
    // only one instance should exist at a time
    private var selectChoicesDialog: SelectChoicesDialog? = null
    private var startStopChooserDialog: StartStopChooserDialog? = null
    private var myTimePickerDialog: MyTimePickerDialog? = null
    private var myDatePickerDialog: MyDatePickerDialog? = null
    private var storedIconDataDialog: StoredIconDataDialog? = null

    //represents the selected activities of the user
    private var userSelectedActivities = ArrayList<LetsGoSingleActivity>()

    //used to store references to text views and null them in on cancel
    private var iconTextViewIndex = ArrayList<Int>()

    private lateinit var returnUserSelectedCategoriesAndAgeFromDatabaseObserver:
            Observer<EventWrapperWithKeyString<ReturnUserSelectedCategoriesAndAgeDataHolder>>

    private lateinit var neededVeriInfoObserver:
            Observer<EventWrapperWithKeyString<NeededVeriInfoDataHolder>>

    private lateinit var setCategoriesReturnValueObserver:
            Observer<EventWrapperWithKeyString<SetFieldsReturnValues>>

    private lateinit var finishedGettingLocationObserver:
            Observer<EventWrapperWithKeyString<LocationReturnErrorStatus>>

    private var userAge = GlobalValues.server_imported_values.lowestAllowedAge

    private lateinit var calledFrom: SelectCategoriesFragmentCalledFrom

    private lateinit var sharedApplicationViewModel: SharedApplicationViewModel

    private var deviceScreenWidth = 0

    private var loginActivity: LoginActivity? = null
    private var appActivity: AppActivity? = null

    private var sharedApplicationOrLoginViewModelInstanceId = ""

    private lateinit var thisFragment: Fragment

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    //Maximum size in pixels a word is, calculated from the allActivitiesReference.
    private var activityIconMaxLayoutWidthPX = 0
    private var activityIconMinSpaceBetweenPX = 0
    private var horizontalLineThicknessPX = 0
    private var activityIconMaxLayoutHeightPX = 0
    private var activityIconDrawableHeight = 0
    private var activityIconDrawableWidth = 0
    private var activityTextSizeInSp = 0F
    private var selectActivityScrollViewMargin = 0

    private var activitiesOrderedByCategoryReference =
        CategoriesAndActivities.ProtectedAccessList(
            errorStore,
            CategoriesAndActivities.CategoryActivities(Unit)
        )
    private var allActivitiesReference =
        CategoriesAndActivities.ProtectedAccessList(
            errorStore,
            CategoriesAndActivities.MutableActivityPair(Unit)
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thisFragment = this
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Inflate the layout for this fragment
        _binding = FragmentSelectCategoriesBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        setLoadingState(true)

        deviceScreenWidth = getScreenWidth(requireActivity())

        //Initialize categories view model for the fragment to access
        val viewModelFactory =
            SelectCategoriesViewModelFactory(
                (requireActivity().applicationContext as LetsGoApplicationClass).categoriesRepository
            )

        selectCategoriesViewModel =
            ViewModelProvider(this, viewModelFactory)[SelectCategoriesViewModel::class.java]

        //get the calling fragment enum
        calledFrom = SelectCategoriesFragmentCalledFrom.setVal(
            arguments?.getInt(
                FRAGMENT_CALLED_FROM_KEY
            )
        )

        activitiesOrderedByCategoryReference = CategoriesAndActivities.activitiesOrderedByCategory
        allActivitiesReference = CategoriesAndActivities.allActivities

        //Want these calculated before anything in onViewCreated runs so the values can be used.
        activityIconDrawableHeight =
            (resources.getDimension(R.dimen.select_categories_single_activity_icon_drawable_height)).toInt()
        activityIconDrawableWidth =
            (resources.getDimension(R.dimen.select_categories_single_activity_icon_drawable_width)).toInt()
        activityIconMinSpaceBetweenPX =
            (resources.getDimension(R.dimen.select_categories_single_activity_icon_min_space_between_icons)).toInt()
        horizontalLineThicknessPX =
            (resources.getDimension(R.dimen.border_line_thickness)).toInt()
        calculateMaxWidthOfIcons()
        activityIconMaxLayoutHeightPX =
            (resources.getDimension(R.dimen.select_categories_single_activity_max_icon_height)).toInt()

        activityTextSizeInSp =
            resources.getDimension(R.dimen.select_categories_single_activity_text_size) / resources.displayMetrics.scaledDensity

        selectActivityScrollViewMargin =
            resources.getDimension(R.dimen.select_categories_header_margin_horizontal).toInt()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeArrays()
        setPrimaryOnClickListeners()

        if (calledFrom == SelectCategoriesFragmentCalledFrom.APPLICATION_PROFILE_FRAGMENT) {

            if (initializeLoginActivity) {
                appActivity = requireActivity() as AppActivity
            }

            sharedApplicationViewModel =
                if (factoryProducer == null) {
                    ViewModelProvider(
                        requireActivity()
                    )[SharedApplicationViewModel::class.java]
                } else {
                    ViewModelProvider(
                        requireActivity(),
                        factoryProducer.invoke()
                    )[SharedApplicationViewModel::class.java]
                }

            sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment = true

            sharedApplicationOrLoginViewModelInstanceId = if (initializeLoginActivity) {
                appActivity!!.sharedApplicationViewModelInstanceId
            } else {
                "testing_id"
            }

            //NOTE: this must be passed as a function reference because the activity will outlive the
            // view model, however there is no guarantee that the fragment will and so a lambda containing
            // appActivity?.handleGrpcFunctionError() can result in appActivity being null
            appActivity?.let {
                selectCategoriesViewModel.setupErrorFunction(it::handleGrpcFunctionError)
                selectCategoriesViewModel.setupFailedToSetFunction(it::modifyingValueFailed)
            }

        } else if (calledFrom == SelectCategoriesFragmentCalledFrom.LOGIN_FRAGMENT) {

            sharedApplicationOrLoginViewModelInstanceId =
                if (initializeLoginActivity) {
                    loginActivity = requireActivity() as LoginActivity

                    //NOTE: this must be passed as a function reference because the activity will outlive the
                    // view model, however there is no guarantee that the fragment will and so a lambda containing
                    // loginActivity?.handleGrpcErrorStatusReturnValues() can result in loginActivity being null
                    selectCategoriesViewModel.setupErrorFunction(loginActivity!!::handleGrpcErrorStatusReturnValues)
                    loginActivity!!.sharedLoginViewModelInstanceId
                } else {
                    selectCategoriesViewModel.setupErrorFunction { }
                    "testing_id"
                }

            loginActivity?.setHalfGlobeImagesDisplayed(false)
        }

        returnUserSelectedCategoriesAndAgeFromDatabaseObserver =
            androidx.lifecycle.Observer { returnCategoriesEvent ->
                val response = returnCategoriesEvent.getContentIfNotHandled(thisFragmentInstanceID)
                if (response != null) {
                    handleReturnedUserSelectedCategoriesAndAge(response)
                }
            }

        selectCategoriesViewModel.returnUserSelectedCategoriesAndAgeFromDatabase
            .observe(viewLifecycleOwner, returnUserSelectedCategoriesAndAgeFromDatabaseObserver)

        if (calledFrom == SelectCategoriesFragmentCalledFrom.LOGIN_FRAGMENT) { //if this is called from the login function then some things need to be set

            binding.selectCategoriesContinueButton.visibility = View.VISIBLE

            neededVeriInfoObserver = androidx.lifecycle.Observer { returnNeedVeriInfoEvent ->
                val response =
                    returnNeedVeriInfoEvent.getContentIfNotHandled(thisFragmentInstanceID)
                if (response != null) {
                    handleNeededVeriInfoReturnValue(response)
                }
            }

            setCategoriesReturnValueObserver =
                androidx.lifecycle.Observer { returnSetCategoriesEvent ->
                    val response = returnSetCategoriesEvent.getContentIfNotHandled(
                        thisFragmentInstanceID
                    )
                    if (response != null) {
                        handleSetCategoriesReturnValue(response)
                    }
                }

            selectCategoriesViewModel.neededVeriInfo.observe(
                viewLifecycleOwner,
                neededVeriInfoObserver
            )

            selectCategoriesViewModel.setCategoriesReturnValue.observe(
                viewLifecycleOwner,
                setCategoriesReturnValueObserver
            )

            binding.selectCategoriesContinueButton.setSafeOnClickListener {

                var atLeastOneActivityExists = false

                for (a in userSelectedActivities) {
                    if (a.activityIndex != -1) {
                        atLeastOneActivityExists = true
                        break
                    }
                }

                if (atLeastOneActivityExists) {
                    setLoadingState(true)
                    setCategoriesToServer()
                } else {
                    binding.errorMessageSelectCategoriesTextView.visibility = View.VISIBLE
                    binding.errorMessageSelectCategoriesTextView.setText(R.string.select_categories_login_activity_select_one_activity)
                }
            }
        } else {
            binding.selectCategoriesContinueButton.visibility = View.GONE
        }

        val calendar = getCalendarFromServerTimestamp()

        //if it is past 5PM roll over to the next day,
        //this just consistently keeps the starting time from 5PM to 6PM
        if (calendar.get(Calendar.HOUR_OF_DAY) > 16) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        categoryTimeBlock.startYear = calendar.get(Calendar.YEAR)
        categoryTimeBlock.startMonth = calendar.get(Calendar.MONTH)
        categoryTimeBlock.startDay = calendar.get(Calendar.DAY_OF_MONTH)
        categoryTimeBlock.startHour = 17
        categoryTimeBlock.startMinute = 0

        categoryTimeBlock.stopYear = calendar.get(Calendar.YEAR)
        categoryTimeBlock.stopMonth = calendar.get(Calendar.MONTH)
        categoryTimeBlock.stopDay = calendar.get(Calendar.DAY_OF_MONTH)
        categoryTimeBlock.stopHour = 18
        categoryTimeBlock.stopMinute = 0

        selectCategoriesViewModel.getCategoriesAndAgeFromDatabase(thisFragmentInstanceID)
    }

    override fun onStart() {
        super.onStart()
        //There are two reasons for putting this setup inside onStart().
        // 1) If activity binding is currently being inflated, the initialization must be delayed
        //  until after onCreate() is called for the activity (this means the activity was
        //  re-created for some reason).
        // 2) Putting the toolbar setup inside onStart makes navigation look much cleaner than
        //  hiding the toolbars before the navigation actually occurs.
        appActivity?.setupActivityMenuBars?.setupToolbarsCategoriesFragment(viewLifecycleOwner)
    }

    private fun calculateMaxWidthOfIcons() {
        val paint = Paint()

        //The standard Android font is Roboto with the family of sans-serif.
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)

        //Convert sp to pixels.
        paint.textSize = resources.getDimension(R.dimen.select_categories_single_activity_text_size)

//            TypedValue.applyDimension(
//            TypedValue.COMPLEX_UNIT_SP,
//            GlobalValues.activityIconTextSize,
//            resources.displayMetrics
//        )

        val whitespacePattern = Pattern.compile("\\s+")

        var maxIconNameWordSize = 0

        //Find length of longest word in activities.
        for (singleActivity in allActivitiesReference) {
            if (singleActivity.activity.iconDisplayName.isNotBlank()) {
                val words = singleActivity.activity.iconDisplayName.trim().split(whitespacePattern)

                for (word in words) {

                    //Find the width of the word in pixels.
                    val widthInPx = paint.measureText(
                        word,
                        0,
                        word.length
                    )

                    maxIconNameWordSize = max(maxIconNameWordSize, ceil(widthInPx).toInt())
                }
            } else {
                val errorMessage = "Blank icon display found for activity $singleActivity\n" +
                        "$allActivitiesReference"

                categoriesErrorMessage(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                //Ok to continue here. Will simply be an empty name for the user.
            }
        }

        //Add a little to max icon name word size for safety sake.
        activityIconMaxLayoutWidthPX =
            max(
                (resources.getDimension(R.dimen.select_categories_single_activity_icon_drawable_width)).toInt(),
                maxIconNameWordSize + 4
            )
    }

    private fun calcSpaceBetweenIcons(
        textViewWidthPx: Int,
        numTextViewsPerRow: Int,
        widthToDisplayIconsPx: Int,
        displayStartAndEndMargins: Boolean
    ): Int {
        return if(numTextViewsPerRow == 0) {
            activityIconMinSpaceBetweenPX
        } else if(numTextViewsPerRow == 1) {
            (widthToDisplayIconsPx - textViewWidthPx)/2
        } else {
            //The minus one is because the text views have a space between them. However,
            // This means that there are only numTextViewsPerRow - 1 spaces.
            val modifier = if(displayStartAndEndMargins) 1 else -1

            val totalSpaceTakenByTextViews = numTextViewsPerRow * textViewWidthPx

            val individualSpaceBetween =
                (widthToDisplayIconsPx - totalSpaceTakenByTextViews) / (numTextViewsPerRow + modifier)
            if (individualSpaceBetween > activityIconMinSpaceBetweenPX) {
                individualSpaceBetween
            } else {
                activityIconMinSpaceBetweenPX
            }
        }
    }

    private fun generatePlusSignDrawable(): Drawable {
        val drawable = ResourcesCompat.getDrawable(
            requireContext().resources,
            R.drawable.icon_round_add_20,
            requireContext().theme
        )?.mutate()

        val iconColor = ResourcesCompat.getColor(
            requireContext().resources,
            R.color.plus_solid_color,
            requireContext().theme
        )

        val finalDrawable = generateUnSelectedIcon(
            drawable,
            requireContext(),
            iconColor,
            R.color.outside_card_background_color
        )

        finalDrawable.setBounds(
            0,
            0,
            activityIconDrawableHeight,
            activityIconDrawableWidth
        )

        return finalDrawable
    }

    private fun generateActivityTextView(
        spaceBetweenIcons: Int,
        textMsg: String?,
        textSizeInSp: Float,
        addMargin: Boolean,
        safeClickListener: SafeClickListener?,
        saveIconToTextView: (TextView) -> Unit
    ): TextView {
        return TextView(
            ContextThemeWrapper(
                context,
                R.style.clickable_icon_text_view
            ),
            null,
            0
        ).setupAsActivityTextView(
            spaceBetweenIcons,
            textMsg,
            safeClickListener,
            textSizeInSp,
            activityIconMaxLayoutWidthPX,
            activityIconMaxLayoutHeightPX,
            addMargin,
            saveIconToTextView
        )
    }

    private fun handleReturnedUserSelectedCategoriesAndAge(response: ReturnUserSelectedCategoriesAndAgeDataHolder) {

        //first is the age
        //second is the user selected categories list

        userAge = response.age

        if (response.categoriesArrayList.size > userSelectedActivities.size
            || response.categoriesArrayList.size > GlobalValues.server_imported_values.numberActivitiesStoredPerAccount
        ) { //if too many activities were passed, send an error message and clear the user categories

            val errorMsg = if (response.categoriesArrayList.size > userSelectedActivities.size) {
                "The number of activities stored in the database " +
                        "is larger than the number of activities available on select categories fragment."
            } else {
                "The number of activities available on from database" +
                        "is larger than the variable representing the number of activities."
            }

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            //this will clear categories so the error won't spam the server
            selectCategoriesViewModel.clearCategories()
        } else {

            //save activities to userSelectedActivities and change textViews on screen
            for (i in response.categoriesArrayList.indices) {

                userSelectedActivities[i].activityIndex =
                    response.categoriesArrayList[i].activityIndex
                userSelectedActivities[i].timeFrames.clear()

                for (t in response.categoriesArrayList[i].timeFrameArrayList) {
                    userSelectedActivities[i].timeFrames.add(
                        LetsGoActivityTimeFrame(
                            t.startTimeFrame,
                            t.stopTimeFrame
                        )
                    )
                }

                changeTextViewsAfterActivitySelected(
                    i,
                    userSelectedActivities[i].activityIndex,
                    setActivityTextView = false
                )
            }
        }

        val textViewWidthMaxWidth = activityIconMaxLayoutWidthPX

        val cardViewMargin =
            requireContext().resources.getDimension(R.dimen.card_background_margin_with_custom_card_compat)
        val recyclerViewMargin =
            requireContext().resources.getDimension(R.dimen.card_general_inner_padding)

        val emptySpaceWidthOfScrollView = (
                deviceScreenWidth
                        - 2 * cardViewMargin
                        - 2 * recyclerViewMargin
                ).toInt()

        //The number of margins between the text views is actually (numTextViewsPerRow - 1). This means
        // that the space between is instead added on to emptySpaceWidthOfScrollView to return the
        // correct calculation.
        val numTextViewsPerRow = max(
            1,
            (emptySpaceWidthOfScrollView + activityIconMinSpaceBetweenPX) / (textViewWidthMaxWidth + activityIconMinSpaceBetweenPX)
        )

        val spaceBetweenIcons = calcSpaceBetweenIcons(
            textViewWidthMaxWidth,
            numTextViewsPerRow,
            emptySpaceWidthOfScrollView,
            displayStartAndEndMargins = false
        )

        //Generate a list of category index values, sorted by 'orderNumber' field.
        //NOTE: Instead of creating a temp list based on activitiesOrderedByCategoryReference and
        // then sorting it and passing it to CategoriesListAdapter, instead created the sorted index
        // numbers to preserve consistency of using activitiesOrderedByCategoryReference list.
        val sortedIndexNumbers = activitiesOrderedByCategoryReference.sortedBy {
            it.category.orderNumber
        }.map {
            it.category.index
        }

        //set up recyclerView
        recyclerViewAdapter =
            CategoriesListAdapter(
                requireContext(),
                deviceScreenWidth,
                userAge,
                numTextViewsPerRow,
                sortedIndexNumbers,
                activitiesOrderedByCategoryReference,
                allActivitiesReference
            ) { activityIndex: Int, tableRow: TableRow ->

                var selectedIcon = false
                for (a in userSelectedActivities) {
                    if (a.activityIndex == -1) {
                        break
                    } else if (a.activityIndex == activityIndex) {
                        selectedIcon = true
                        break
                    }
                }

                val textView = generateActivityTextView(
                    spaceBetweenIcons,
                    allActivitiesReference[activityIndex].activity.iconDisplayName,
                    activityTextSizeInSp,
                    numTextViewsPerRow == 1 || tableRow.childCount != 0,
                    SafeClickListener {
                        //NOTE: this must be iterated through separately from the loop below
                        // for when icons are deleted from the center of the row
                        var activityUserIndexIfExists = -1
                        for (j in userSelectedActivities.indices) {
                            if (userSelectedActivities[j].activityIndex == activityIndex) {
                                activityUserIndexIfExists = j
                                break
                            }
                        }

                        if (activityUserIndexIfExists == -1) { //if activity is not already selected
                            var availableActivityIndex = -1
                            //check to see if any available activates
                            for (j in userSelectedActivities.indices) {
                                if (userSelectedActivities[j].activityIndex == -1) {
                                    availableActivityIndex = j
                                    break
                                }
                            }

                            if (availableActivityIndex > -1) { //if an activity is available this int is the index of it

                                categoryTimeBlock.userSelectedIndex = availableActivityIndex
                                categoryTimeBlock.activityIndex = activityIndex

                                showSelectChoiceDialog()

                            } else { //if activity list is full
                                Toast.makeText(
                                    GlobalValues.applicationContext,
                                    R.string.select_categories_activity_menu_full,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {  //if activity is already selected

                            categoryTimeBlock.userSelectedIndex = activityUserIndexIfExists
                            showStoredIconDataDialog()
                        }
                    },
                    saveIconToTextView = { textView ->
                        //save standard icon to activity text view
                        saveIconToTextView(
                            GlobalValues.applicationContext,
                            textView,
                            activityIndex,
                            selectedIcon,
                            activityIconDrawableHeight,
                            activityIconDrawableWidth,
                            activitiesOrderedByCategoryReference,
                            allActivitiesReference,
                            errorStore
                        )
                    }
                )

                tableRow.addView(textView)

                //Set for testing purposes. Essentially the text by itself does not make this
                // view unique. So the tag is set to the activityIndex which by nature should
                // be unique.
                textView.tag = activityIndex

                textView
            }

        binding.selectCategoriesRecyclerView.apply {
            adapter = recyclerViewAdapter
            //This adapter has fairly 'expensive' elements to build, however after they are built they are 'cheap' in
            // terms of memory. So loading some pages ahead of time (this should be ALL of the activities even when expanded,
            // to start). Admittedly this looses the value of a RecyclerView, however layoutManager can always be changed
            // back to a standard LinearLayoutManager later if required (or the parameters updated).
            layoutManager = NoPredictiveAnimLinearLayoutManager(requireContext(), 10)
            setHasFixedSize(true)
        }

        //NOTE: If a decoration is added, the decoration will not be hidden with
        // the rest of the ViewHolder.
//        val dividerItemDecoration = DividerItemDecoration(
//            binding.selectCategoriesRecyclerView.context,
//            resources.configuration.orientation
//        )
//
//        ContextCompat.getDrawable(
//            GlobalValues.applicationContext,
//            R.drawable.activity_recycler_view_divider_line
//        )
//            ?.let {
//                dividerItemDecoration.setDrawable(it)
//                binding.selectCategoriesRecyclerView.addItemDecoration(dividerItemDecoration)
//            }

        setLoadingState(false)
    }

    private fun handleSetCategoriesReturnValue(setCategoriesReturnVal: SetFieldsReturnValues) {

        binding.errorMessageSelectCategoriesTextView.text = null
        binding.errorMessageSelectCategoriesTextView.visibility = View.GONE

        if (loginActivity == null) {
            val errorMessage =
                "handleSetCategoriesReturnValue() was called from LoginActivity, this should never happen."

            categoriesErrorMessage(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )

            setLoadingState(false)
            return
        }

        //NOTE: make sure all ends run setLoadingState(false) (don't just set it at the end it looks bad)
        if (setCategoriesReturnVal.invalidParameterPassed) { //server returned invalid parameter
            binding.errorMessageSelectCategoriesTextView.setText(R.string.select_categories_invalid_info_passed)
            binding.errorMessageSelectCategoriesTextView.visibility = View.VISIBLE
            setLoadingState(false)
        } else {
            binding.errorMessageSelectCategoriesTextView.text = null
            if (setCategoriesReturnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                //NOTE: for handleSetCategoriesReturnValue() to be called this function must be from the login fragment, so
                // casting the activity to MainActivity is valid
                finishedGettingLocationObserver = androidx.lifecycle.Observer { wrapper ->

                    val result = wrapper.getContentIfNotHandled(thisFragmentInstanceID)

                    if (result != null) {

                        when (result) {
                            LocationReturnErrorStatus.SUCCESSFUL -> {
                                selectCategoriesViewModel.runNeedVeriInfo(
                                    thisFragmentInstanceID,
                                    GlobalValues.lastUpdatedLocationInfo.longitude,
                                    GlobalValues.lastUpdatedLocationInfo.latitude
                                )
                            }
                            else -> {
                                setLoadingState(false)
                            }
                        }
                    }
                }

                val sharedLoginViewModel: SharedLoginViewModel by activityViewModels(factoryProducer = factoryProducer) //syntactic sugar for initializing view model

                sharedLoginViewModel.mostRecentFragmentIDRequestingLocation =
                    thisFragmentInstanceID
                sharedLoginViewModel.finishedGettingLocation.observe(
                    viewLifecycleOwner,
                    finishedGettingLocationObserver
                )

                loginActivity?.getCurrentLocation()
            }
        }

        appActivity?.handleGrpcFunctionError(setCategoriesReturnVal.errorStatus)
        loginActivity?.handleGrpcErrorStatusReturnValues(setCategoriesReturnVal.errorStatus)
    }

    private fun handleNeededVeriInfoReturnValue(returnValue: NeededVeriInfoDataHolder) {
        binding.errorMessageSelectCategoriesTextView.visibility = View.GONE
        binding.errorMessageSelectCategoriesTextView.text = null

        if (returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {

            when (returnValue.neededVeriInfoResponse.accessStatus) {
                AccessStatusEnum.AccessStatus.STATUS_NOT_SET -> {
                    binding.errorMessageSelectCategoriesTextView.visibility = View.VISIBLE
                    binding.errorMessageSelectCategoriesTextView.setText(R.string.select_categories_connection_error)

                    setLoadingState(false)
                }
                AccessStatusEnum.AccessStatus.NEEDS_MORE_INFO -> {
                    val errorMsg =
                        "NEEDS_MORE_INFO was returned from NeededVerificationInfo after all info should have been entered."
                    neededVeriInfoErrorMessage(
                        errorMsg,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        returnValue.neededVeriInfoResponse
                    )

                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.select_categories_invalid_login_information,
                        Toast.LENGTH_LONG
                    ).show()

                    loginActivity?.navigateToSelectMethodAndClearBackStack()
                }
                AccessStatusEnum.AccessStatus.ACCESS_GRANTED -> {
                    //navigate to App Activity
                    val appActivityIntent = Intent(activity, AppActivity::class.java)
                    appActivityIntent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(appActivityIntent)

                    loginActivity?.finish()
                }
                //Suspended and Banned should never be reached from NeededVerificationInfo RPC.
                AccessStatusEnum.AccessStatus.SUSPENDED,
                AccessStatusEnum.AccessStatus.BANNED,
                AccessStatusEnum.AccessStatus.LG_ERR,
                AccessStatusEnum.AccessStatus.UNRECOGNIZED,
                null -> {
                    val errorMsg = "LG_ERR was returned from NeededVerificationInfo."
                    neededVeriInfoErrorMessage(
                        errorMsg,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors(),
                        returnValue.neededVeriInfoResponse
                    )

                    setLoadingState(false)

                    loginActivity?.handleGrpcErrorStatusReturnValues(
                        GrpcFunctionErrorStatusEnum.LOG_USER_OUT
                    )
                }
            }

        } else {
            setLoadingState(false)
        }

        //NOTE: set verification info should only be called from the main activity
        loginActivity?.handleGrpcErrorStatusReturnValues(
            returnValue.errorStatus
        )

    }

    private fun initializeArrays() {

        val textViewWidth = activityIconMaxLayoutWidthPX
        val spaceBetweenIcons = calcSpaceBetweenIcons(
            textViewWidth,
            GlobalValues.server_imported_values.numberActivitiesStoredPerAccount,
            deviceScreenWidth - 2 * selectActivityScrollViewMargin,
            displayStartAndEndMargins = true
        )

        binding.chooseCategoryLayout.minimumHeight =
            activityIconDrawableHeight +
                    binding.chooseCategoryLayout.paddingBottom +
                    binding.chooseCategoryLayout.paddingTop

        //add user activities
        for (i in 0 until GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {

            val newTextView = generateActivityTextView(
                spaceBetweenIcons,
                null,
                activityTextSizeInSp,
                true,
                null,
                saveIconToTextView = { textView ->

                    val drawable = generatePlusSignDrawable()

                    textView.setCompoundDrawablesRelative(
                        null, drawable, null, null
                    )
                }
            )

            //add the text view to this fragment
            binding.chooseCategoryLayout.addView(
                newTextView
            )

            //add the user selected categories to the array
            userSelectedActivities.add(
                LetsGoSingleActivity(
                    newTextView, -1, ArrayList()
                )
            )
        }

        if (userSelectedActivities.size != GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {
            val errorMsg = "The number of activities available on select categories fragment " +
                    "is different than the variable representing the number of activities.\n" +
                    "userSelectedActivities.size: ${userSelectedActivities.size}\n" +
                    "numberActivitiesStoredPerAccount: ${GlobalValues.server_imported_values.numberActivitiesStoredPerAccount}"

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        }

    }

    private fun setPrimaryOnClickListeners() {

        for (i in userSelectedActivities.indices) {
            userSelectedActivities[i].textView.setSafeOnClickListener(400) {
                if (userSelectedActivities[i].activityIndex != -1) {
                    categoryTimeBlock.userSelectedIndex = i

                    showStoredIconDataDialog()
                }
            }
        }
    }

    private fun showSelectChoiceDialog() {

        //NOTE: This is here for extendability
        if (GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount > 0) { //if no time frames are allowed

            selectChoicesDialog = SelectChoicesDialog(
                getString(R.string.select_choice_dialog_title),
                arrayOf(
                    getString(R.string.select_choice_dialog_choice_one),
                    getString(R.string.select_choice_dialog_choice_two)
                ),
                this
            ) { dialog, index ->

                if (index == 0) { //if anytime was selected
                    changeTextViewsAfterActivitySelected(
                        categoryTimeBlock.userSelectedIndex,
                        categoryTimeBlock.activityIndex
                    )
                    dataUpdated = true
                } else if (index == 1) { //if a specific time is selected
                    categoryTimeBlock.calledFromEnum =
                        StartStopChooserDialogCallerEnum.SELECT_CHOICE
                    categoryTimeBlock.timeFrameIndex = -1

                    showStartStopChooserDialog()
                }

                dialog.dismiss()
                selectChoicesDialog = null
            }

            binding.selectCategoriesFragmentRelativeLayout.setAllClickable(false)
            selectChoicesDialog?.show(childFragmentManager, "select_time_chooser")
        } else {
            changeTextViewsAfterActivitySelected(
                categoryTimeBlock.userSelectedIndex,
                categoryTimeBlock.activityIndex
            )
        }
    }

    private fun showStartStopChooserDialog() {
        startStopChooserDialog = StartStopChooserDialog(categoryTimeBlock, this)

        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(false)
        startStopChooserDialog?.show(childFragmentManager, "start_stop_chooser")
    }

    override fun onStartStopChooserDialogAcceptClick(inflatedView: View) {
        startStopChooserDialog = null
        val correctlyFormatted = runCalendarCheck()

        if (categoryTimeBlock.userSelectedIndex >= GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) { //if activityIndex is out of bounds
            val errorMsg = "activityIndex is out of bounds."

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        } else if (correctlyFormatted) { //if the start and stop time were correctly formatted then save them
            when {
                userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.size > GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount -> {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.select_categories_max_time_frames_reached,
                        Toast.LENGTH_LONG
                    ).show()

                    val errorMsg = getString(R.string.select_categories_max_time_frames_reached)

                    categoriesErrorMessage(
                        errorMsg,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
                categoryTimeBlock.timeFrameIndex == -1 -> { //if timeFrameIndex is -1 then add an element to time frame array

                    userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.add(
                        LetsGoActivityTimeFrame(
                            categoryTimeBlock.startTimeTimestamp,
                            categoryTimeBlock.stopTimeTimestamp
                        )
                    )

                    if (categoryTimeBlock.calledFromEnum == StartStopChooserDialogCallerEnum.SELECT_CHOICE) {
                        changeTextViewsAfterActivitySelected(
                            categoryTimeBlock.userSelectedIndex,
                            categoryTimeBlock.activityIndex
                        )
                    }

                    checkTimeFramesForOverlaps()

                    dataUpdated = true
                }
                userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.size > categoryTimeBlock.timeFrameIndex -> {//if timeFrameIndex is not -1 then it is the activity index

                    userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames[categoryTimeBlock.timeFrameIndex] =
                        (
                                LetsGoActivityTimeFrame(
                                    categoryTimeBlock.startTimeTimestamp,
                                    categoryTimeBlock.stopTimeTimestamp
                                )
                                )

                    if (categoryTimeBlock.calledFromEnum == StartStopChooserDialogCallerEnum.SELECT_CHOICE) {
                        changeTextViewsAfterActivitySelected(
                            categoryTimeBlock.userSelectedIndex,
                            categoryTimeBlock.activityIndex
                        )
                    }

                    checkTimeFramesForOverlaps()

                    dataUpdated = true
                }
                else -> {
                    val errorMsg = "Attempted to access an element out of index for timeFrames."

                    categoriesErrorMessage(
                        errorMsg,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
            }
        }

        if (categoryTimeBlock.calledFromEnum == StartStopChooserDialogCallerEnum.STORED_ICON_LOCATION) {
            showStoredIconDataDialog()
        }
    }

    private fun checkTimeFramesForOverlaps() {

        //NOTE: Because of how the system is set up, this function is easier to put in the
        // fragment instead of view models.
        val timeFrameTimes = mutableListOf<TimeFrameTimeDataClass>()

        for (t in userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames) {
            timeFrameTimes.add(
                TimeFrameTimeDataClass(
                    t.startTimeTimestamp,
                    false
                )
            )

            timeFrameTimes.add(
                TimeFrameTimeDataClass(
                    t.stopTimeTimestamp,
                    true
                )
            )
        }

        timeFrameTimes.sortWith(
            compareBy<TimeFrameTimeDataClass>
            { it.timeStamp }.thenBy
            { it.isStopTime }
        )

        val tempTimeFrames = mutableListOf<LetsGoActivityTimeFrame>()

        var nestingValue = 0
        var i = 0
        while (i < timeFrameTimes.size) {
            val startTime = timeFrameTimes[i].timeStamp
            nestingValue++

            while (nestingValue > 0) {
                i++
                if (timeFrameTimes[i].isStopTime) {
                    nestingValue--
                } else {
                    nestingValue++
                }
            }

            val stopTime = timeFrameTimes[i].timeStamp

            tempTimeFrames.add(
                LetsGoActivityTimeFrame(
                    startTime,
                    stopTime
                )
            )
            i++
        }

        //Compare by size because there is no guarantee that userSelectedActivities[].timeFrames is sorted.
        if (tempTimeFrames.size != userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.size) {
            Toast.makeText(
                GlobalValues.applicationContext,
                R.string.select_categories_combine_overlapping_time_frames,
                Toast.LENGTH_LONG
            ).show()
        }

        //This must be saved either way to guarantee that userSelectedActivities[].timeFrames is sorted.
        userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames = tempTimeFrames
    }

    override fun onStartStopChooserDialogCancelClick(inflatedView: View) {
        startStopChooserDialog = null

        when (categoryTimeBlock.calledFromEnum) {
            StartStopChooserDialogCallerEnum.SELECT_CHOICE -> {
                showSelectChoiceDialog()
            }
            StartStopChooserDialogCallerEnum.STORED_ICON_LOCATION -> {
                showStoredIconDataDialog()
            }
            StartStopChooserDialogCallerEnum.UNKNOWN -> {
                val errorMsg = "UNKNOWN enum was selected when it shouldn't be possible."

                categoriesErrorMessage(
                    errorMsg,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )
            }
        }
    }

    override fun onStartStopChooserDialogStartDateClick() {
        startStopChooserDialog?.dismiss()
        startStopChooserDialog = null
        showMyDateDialog(
            getString(R.string.select_categories_start_date_title), true
        )
    }

    override fun onStartStopChooserDialogStartTimeClick() {
        startStopChooserDialog?.dismiss()
        startStopChooserDialog = null
        showMyTimeDialog(
            getString(R.string.select_categories_start_time_title), true
        )
    }

    override fun onStartStopChooserDialogStopDateClick() {
        startStopChooserDialog?.dismiss()
        startStopChooserDialog = null
        showMyDateDialog(
            getString(R.string.select_categories_stop_date_title), false
        )
    }

    override fun onStartStopChooserDialogStopTimeClick() {
        startStopChooserDialog?.dismiss()
        startStopChooserDialog = null
        showMyTimeDialog(
            getString(R.string.select_categories_stop_time_title), false
        )
    }

    override fun onStartStopChooserDialogDismissed() {
        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(true)
    }

    private fun showMyDateDialog(title: String, isStartDate: Boolean) {

        //NOTE: DatePickerDialog only updates on value changed instead of accept so using a custom one
        myDatePickerDialog = if (isStartDate) {

            MyDatePickerDialog(
                title,
                true,
                categoryTimeBlock.startYear,
                categoryTimeBlock.startMonth,
                categoryTimeBlock.startDay,
                this
            )
        } else {

            MyDatePickerDialog(
                title,
                false,
                categoryTimeBlock.stopYear,
                categoryTimeBlock.stopMonth,
                categoryTimeBlock.stopDay,
                this
            )
        }

        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(false)
        myDatePickerDialog?.show(childFragmentManager, "date_picker")

    }

    override fun onDateDialogPositiveClick(
        datePickerBinding: DialogFragmentDatePickerCalendarBinding,
        isStartDate: Boolean
    ) {

        if (isStartDate) {
            categoryTimeBlock.startYear = datePickerBinding.datePickerDatePicker.year
            categoryTimeBlock.startMonth = datePickerBinding.datePickerDatePicker.month
            categoryTimeBlock.startDay = datePickerBinding.datePickerDatePicker.dayOfMonth

            //if the date made the time earlier than present time change it
            val calendar = getCalendarFromServerTimestamp()
            if (
                calendar.get(Calendar.YEAR) == categoryTimeBlock.startYear &&
                calendar.get(Calendar.MONTH) == categoryTimeBlock.startMonth &&
                calendar.get(Calendar.DAY_OF_MONTH) == categoryTimeBlock.startDay
            ) {

                val calendarMinutesInDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(
                    Calendar.MINUTE
                )
                val startTimeMinutes =
                    categoryTimeBlock.startHour * 60 + categoryTimeBlock.startMinute

                if (calendarMinutesInDay > startTimeMinutes) {
                    categoryTimeBlock.startHour = calendar.get(Calendar.HOUR_OF_DAY)
                    categoryTimeBlock.startMinute = calendar.get(Calendar.MINUTE)
                }
            }
        } else {
            categoryTimeBlock.stopYear = datePickerBinding.datePickerDatePicker.year
            categoryTimeBlock.stopMonth = datePickerBinding.datePickerDatePicker.month
            categoryTimeBlock.stopDay = datePickerBinding.datePickerDatePicker.dayOfMonth

            //if the date made the time earlier than present time change it
            val calendar = getCalendarFromServerTimestamp()
            if (
                calendar.get(Calendar.YEAR) == categoryTimeBlock.stopYear &&
                calendar.get(Calendar.MONTH) == categoryTimeBlock.stopMonth &&
                calendar.get(Calendar.DAY_OF_MONTH) == categoryTimeBlock.stopDay
            ) {

                val calendarMinutesInDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(
                    Calendar.MINUTE
                )
                val startTimeMinutes =
                    categoryTimeBlock.stopHour * 60 + categoryTimeBlock.stopMinute

                if (calendarMinutesInDay > startTimeMinutes) {
                    categoryTimeBlock.stopHour = calendar.get(Calendar.HOUR_OF_DAY)
                    categoryTimeBlock.stopMinute = calendar.get(Calendar.MINUTE)
                }
            }
        }

        myDatePickerDialog = null
        showStartStopChooserDialog()
    }

    override fun onDateDialogNegativeClick() {
        myDatePickerDialog = null
        showStartStopChooserDialog()
    }

    override fun onDateDialogDismissed() {
        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(true)
    }

    private fun showMyTimeDialog(title: String, isStartTime: Boolean) {

        //NOTE: TimePickerDialog only updates on value changed instead of accept so using custom dialog
        myTimePickerDialog = if (isStartTime) {

            val dateIsToday = checkIfToday(
                categoryTimeBlock.startYear,
                categoryTimeBlock.startMonth,
                categoryTimeBlock.startDay
            )

            MyTimePickerDialog(
                title,
                true,
                categoryTimeBlock.startHour,
                categoryTimeBlock.startMinute,
                dateIsToday,
                this
            )
        } else {

            val dateIsToday = checkIfToday(
                categoryTimeBlock.stopYear,
                categoryTimeBlock.stopMonth,
                categoryTimeBlock.stopDay
            )

            MyTimePickerDialog(
                title,
                false,
                categoryTimeBlock.stopHour,
                categoryTimeBlock.stopMinute,
                dateIsToday,
                this
            )
        }

        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(false)
        myTimePickerDialog?.show(childFragmentManager, "time_picker")

    }

    private fun checkIfToday(year: Int, month: Int, day: Int): Boolean {

        val calendar = getCalendarFromServerTimestamp()

        if (
            year == calendar.get(Calendar.YEAR) &&
            month == calendar.get(Calendar.MONTH) &&
            day == calendar.get(Calendar.DAY_OF_MONTH)
        ) {
            return true
        }

        return false
    }

    override fun onTimeDialogPositiveClick(
        timePickerBinding: DialogFragmentTimePickerBinding,
        isStartTime: Boolean
    ) {

        if (isStartTime) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //API 18-22
                categoryTimeBlock.startHour =
                    timePickerBinding.timePickerSpinnerTimePicker.currentHour
                categoryTimeBlock.startMinute =
                    timePickerBinding.timePickerSpinnerTimePicker.currentMinute
            } else { //API 23-current
                categoryTimeBlock.startHour = timePickerBinding.timePickerSpinnerTimePicker.hour
                categoryTimeBlock.startMinute = timePickerBinding.timePickerSpinnerTimePicker.minute
            }
        } else {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //API 18-22
                categoryTimeBlock.stopHour =
                    timePickerBinding.timePickerSpinnerTimePicker.currentHour
                categoryTimeBlock.stopMinute =
                    timePickerBinding.timePickerSpinnerTimePicker.currentMinute
            } else { //API 23-current
                categoryTimeBlock.stopHour = timePickerBinding.timePickerSpinnerTimePicker.hour
                categoryTimeBlock.stopMinute = timePickerBinding.timePickerSpinnerTimePicker.minute
            }
        }

        myTimePickerDialog = null
        showStartStopChooserDialog()

    }

    override fun onTimeDialogNegativeClick() {
        myTimePickerDialog = null
        showStartStopChooserDialog()
    }

    override fun onTimeDialogDismissed() {
        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(true)
    }

    private fun showStoredIconDataDialog() {
        if (categoryTimeBlock.userSelectedIndex < GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {

            storedIconDataDialog =
                StoredIconDataDialog(
                    userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames,
                    userSelectedActivities[categoryTimeBlock.userSelectedIndex].activityIndex,
                    activitiesOrderedByCategoryReference,
                    allActivitiesReference,
                    activityTextSizeInSp,
                    activityIconDrawableHeight,
                    activityIconDrawableWidth,
                    activityIconMaxLayoutHeightPX,
                    activityIconMaxLayoutWidthPX,
                    horizontalLineThicknessPX,
                    this,
                    errorStore
                )

            binding.selectCategoriesFragmentRelativeLayout.setAllClickable(false)
            storedIconDataDialog?.show(childFragmentManager, "stored_icon_data")
        } else {
            val errorMsg = "When userSelectedIndex was returned it was out of bounds."

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        }
    }

    override fun onStoredIconDataDialogPositiveClick() {
        //Leaving this here for extendability, it is the 'Ok' button
    }

    override fun onStoredIconDataDialogDeleteSubCategoryClick(inflatedView: View) {
        storedIconDataDialog?.dismiss()
        storedIconDataDialog = null
        changeTextViewsAfterActivityDeleted()

        dataUpdated = true
    }

    override fun onStoredIconDataDialogNewTimeFrameClick() {
        storedIconDataDialog?.dismiss()
        storedIconDataDialog = null
        categoryTimeBlock.calledFromEnum = StartStopChooserDialogCallerEnum.STORED_ICON_LOCATION
        categoryTimeBlock.timeFrameIndex = -1

        showStartStopChooserDialog()
    }

    override fun onStoredIconDataDialogEditClick(element: LetsGoActivityTimeFrame) {

        storedIconDataDialog?.dismiss()
        storedIconDataDialog = null

        if (categoryTimeBlock.userSelectedIndex < GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {
            categoryTimeBlock.calledFromEnum = StartStopChooserDialogCallerEnum.STORED_ICON_LOCATION

            //NOTE: this method allows to return -1 if not found and there should be no equal time frames so it should be all right
            categoryTimeBlock.timeFrameIndex =
                userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.indexOf(
                    element
                )

            val startTime = Calendar.getInstance()
            val stopTime = Calendar.getInstance()

            userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames[categoryTimeBlock.timeFrameIndex].apply {

                startTime.timeInMillis =
                    if (startTimeTimestamp == -1L) {
                        getCurrentTimestampInMillis()
                    } else {
                        startTimeTimestamp
                    }

                if (stopTimeTimestamp > 0) {
                    stopTime.timeInMillis = stopTimeTimestamp
                } else {
                    val errorMsg =
                        "An index was accessed and attempted to be edited with a stop time of < 0.\n" +
                                "categoryTimeBlock.userSelectedIndex: ${categoryTimeBlock.userSelectedIndex}\n" +
                                "categoryTimeBlock.timeFrameIndex: ${categoryTimeBlock.timeFrameIndex}\n"

                    categoriesErrorMessage(
                        errorMsg,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
            }

            //this will set the calendar time to the time being edited
            categoryTimeBlock.apply {
                startYear = startTime.get(Calendar.YEAR)
                startMonth = startTime.get(Calendar.MONTH)
                startDay = startTime.get(Calendar.DAY_OF_MONTH)
                startHour = startTime.get(Calendar.HOUR_OF_DAY)
                startMinute = startTime.get(Calendar.MINUTE)

                stopYear = stopTime.get(Calendar.YEAR)
                stopMonth = stopTime.get(Calendar.MONTH)
                stopDay = stopTime.get(Calendar.DAY_OF_MONTH)
                stopHour = stopTime.get(Calendar.HOUR_OF_DAY)
                stopMinute = stopTime.get(Calendar.MINUTE)
            }

            showStartStopChooserDialog()
        } else {
            val errorMsg = "When userSelectedIndex was returned it was out of bounds."

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        }

    }

    override fun onStoredIconDataDialogDeleteTimeFrameClick(element: LetsGoActivityTimeFrame) {
        if (categoryTimeBlock.userSelectedIndex < GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {
            userSelectedActivities[categoryTimeBlock.userSelectedIndex].timeFrames.remove(element)

            dataUpdated = true
        } else {
            val errorMsg = "When userSelectedIndex was returned it was out of bounds."

            categoriesErrorMessage(
                errorMsg,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors()
            )
        }

    }

    override fun onStoredIconDataDialogDismissed() {
        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(true)
    }

    //checks if valid data before saving it, will return false if an unmanageable error occurs
    private fun runCalendarCheck(): Boolean {

        var correctlyFormatted = true

        val currentTimestamp = getCurrentTimestampInMillis()

        var timestampCalendar = GregorianCalendar(
            categoryTimeBlock.stopYear,
            categoryTimeBlock.stopMonth,
            categoryTimeBlock.stopDay,
            categoryTimeBlock.stopHour,
            categoryTimeBlock.stopMinute
        )

        categoryTimeBlock.stopTimeTimestamp = timestampCalendar.timeInMillis

        timestampCalendar = GregorianCalendar(
            categoryTimeBlock.startYear,
            categoryTimeBlock.startMonth,
            categoryTimeBlock.startDay,
            categoryTimeBlock.startHour,
            categoryTimeBlock.startMinute
        )

        categoryTimeBlock.startTimeTimestamp = timestampCalendar.timeInMillis

        when {
            currentTimestamp >= categoryTimeBlock.startTimeTimestamp -> { //start time before current time
                categoryTimeBlock.startTimeTimestamp = -1
            }
            categoryTimeBlock.startTimeTimestamp > categoryTimeBlock.stopTimeTimestamp -> { //start time before stop time
                categoryTimeBlock.startTimeTimestamp = -1
                categoryTimeBlock.stopTimeTimestamp = -1
                correctlyFormatted = false

                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.select_categories_stop_time_before_start_time,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        return correctlyFormatted
    }

    //dismisses and clears all dialogs used for selecting activity times
    private fun clearSelectionDialogs() {
        selectChoicesDialog?.dismiss()
        selectChoicesDialog = null

        startStopChooserDialog?.dismiss()
        startStopChooserDialog = null

        myTimePickerDialog?.dismiss()
        myTimePickerDialog = null

        myDatePickerDialog?.dismiss()
        myDatePickerDialog = null

        storedIconDataDialog?.dismiss()
        storedIconDataDialog = null
    }

    override fun onDestroyView() {

        _binding = null
        recyclerViewAdapter = null

        clearSelectionDialogs()

        categoryTimeBlock = CategoryTimeBlock()
        userSelectedActivities.clear()

        appActivity?.removeLambdaCalledAtEndOfOnCreate(thisFragmentInstanceID)

        //this will remove the reference to the categories so the garbage collector can clear them from RAM
        for (a in allActivitiesReference)
            a.textViewWrapper.textView = null

        activitiesOrderedByCategoryReference =
            CategoriesAndActivities.ProtectedAccessList(
                errorStore,
                CategoriesAndActivities.CategoryActivities(
                    Unit
                )
            )
        allActivitiesReference =
            CategoriesAndActivities.ProtectedAccessList(
                errorStore,
                CategoriesAndActivities.MutableActivityPair(
                    Unit
                )
            )

        iconTextViewIndex.clear()

        loginActivity = null
        appActivity = null

        Log.i("runningFunc", "onDestroyView()")

        super.onDestroyView()
    }

    override fun onPause() {

        selectChoicesDialog?.dismiss()
        startStopChooserDialog?.dismiss()
        myTimePickerDialog?.dismiss()
        myDatePickerDialog?.dismiss()
        storedIconDataDialog?.dismiss()

        //NOTE: this will save the activity and the respective time frames and send them to the server
        // the time frames are saved in unix format in seconds (even though java returns its timestamps in milliseconds)
        // if 'anytime' is selected then the time frame array will be empty
        // if the present time is selected as start time it will be set to -1 and ANYTIME will be shown to the user
        // the stop time will never be set to -1
        // the time frame (both the start and stop) will be checked to follow this convention when the page is initially loaded

        if (dataUpdated) { //if data was changed while the user was on this screen
            setCategoriesToServer()
        }

        if (calledFrom == SelectCategoriesFragmentCalledFrom.APPLICATION_PROFILE_FRAGMENT) {
            sharedApplicationViewModel.doNotRunOnCreateViewInMatchScreenFragment = false
        }

        super.onPause()
    }

    private fun setCategoriesToServer() {

        val categoriesList = ArrayList<CategoryTimeFrame.CategoryActivityMessage>()

        for (i in 0 until GlobalValues.server_imported_values.numberActivitiesStoredPerAccount) {
            //-1 means unset, 0 is the 'unknown' activity, neither should ever be sent to the server
            if (userSelectedActivities[i].activityIndex > 0) {
                val timeFrameArray = ArrayList<CategoryTimeFrame.CategoryTimeFrameMessage>()

                for (t in userSelectedActivities[i].timeFrames) {
                    timeFrameArray.add(
                        CategoryTimeFrame.CategoryTimeFrameMessage.newBuilder()
                            .setStartTimeFrame(t.startTimeTimestamp)
                            .setStopTimeFrame(t.stopTimeTimestamp)
                            .build()
                    )
                }

                categoriesList.add(
                    CategoryTimeFrame.CategoryActivityMessage.newBuilder()
                        .setActivityIndex(userSelectedActivities[i].activityIndex)
                        .addAllTimeFrameArray(timeFrameArray)
                        .build()
                )
            }
        }

        var clearMatchList: () -> Unit = {}
        var functionToUpdateAlgorithmParametersCompleted: (Boolean) -> Unit = {}

        if (calledFrom == SelectCategoriesFragmentCalledFrom.APPLICATION_PROFILE_FRAGMENT) {
            clearMatchList = { sharedApplicationViewModel.callClearMatchesFromList() }
            functionToUpdateAlgorithmParametersCompleted = { successful ->
                sharedApplicationViewModel.functionToUpdateAlgorithmParametersCompleted(successful)
            }
        }

        if (categoriesList.isNotEmpty()) {
            selectCategoriesViewModel.setCategories(
                categoriesList,
                thisFragmentInstanceID,
                clearMatchList,
                functionToUpdateAlgorithmParametersCompleted,
                sharedApplicationOrLoginViewModelInstanceId
            )
        } else {
            appActivity?.noCategoriesEntered()
        }

        dataUpdated = false
    }

    //changes both text views after an activity was initially selected
    private fun changeTextViewsAfterActivityDeleted() {

        //grab the enum as an index before deleting the activity itself
        val userSelectedIndex = categoryTimeBlock.userSelectedIndex
        val activityIndex = userSelectedActivities[userSelectedIndex].activityIndex

        //save colored icon to activity text view from next activity text view
        saveIconToTextView(
            GlobalValues.applicationContext,
            allActivitiesReference[activityIndex].textViewWrapper.textView,
            activityIndex,
            selectedIcon = false,
            activityIconDrawableHeight,
            activityIconDrawableWidth,
            activitiesOrderedByCategoryReference,
            allActivitiesReference,
            errorStore
        )

        for (i in userSelectedIndex until userSelectedActivities.size) {

            if (i == userSelectedActivities.size - 1) { //if end of array and an element was deleted, this will need to be set to empty
                //copy activity
                userSelectedActivities[i].activityIndex = -1

                setSelectedActivityToEmpty(i)
            } else {
                val nextActivityIndex = userSelectedActivities[i + 1].activityIndex

                //copy activity
                userSelectedActivities[i].activityIndex =
                    userSelectedActivities[i + 1].activityIndex

                if (userSelectedActivities[i].activityIndex != -1) {

                    //copy text
                    userSelectedActivities[i].textView.text =
                        userSelectedActivities[i + 1].textView.text

                    //save colored icon to activity text view from next activity text view
                    saveIconToTextView(
                        GlobalValues.applicationContext,
                        userSelectedActivities[i].textView,
                        nextActivityIndex,
                        true,
                        activityIconDrawableHeight,
                        activityIconDrawableWidth,
                        activitiesOrderedByCategoryReference,
                        allActivitiesReference,
                        errorStore
                    )

                    //copy time frames
                    userSelectedActivities[i].timeFrames.clear()
                    for (t in userSelectedActivities[i + 1].timeFrames) {
                        userSelectedActivities[i].timeFrames.add(t)
                    }

                } else { //if next element is empty set current element to empty
                    setSelectedActivityToEmpty(i)
                }
            }
        }
    }

    private fun setSelectedActivityToEmpty(userSelectedIndex: Int) {

        //copy text
        userSelectedActivities[userSelectedIndex].textView.text = null

        val drawable = generatePlusSignDrawable()

        //copy picture
        userSelectedActivities[userSelectedIndex]
            .textView.setCompoundDrawablesRelative(
                null, drawable, null, null
            )

        //copy time frames
        userSelectedActivities[userSelectedIndex].timeFrames.clear()

    }

    //changes both text views after an activity was initially selected
    private fun changeTextViewsAfterActivitySelected(
        modifiedActivityIndex: Int,
        activityIndex: Int,
        setActivityTextView: Boolean = true
    ) {

        Log.i("changeTextViewsAfter", "entered function")

        //set the activity enum
        userSelectedActivities[modifiedActivityIndex].activityIndex = activityIndex

        //set the text in the text view
        userSelectedActivities[modifiedActivityIndex].textView.text =
            allActivitiesReference[activityIndex].activity.iconDisplayName

        if (setActivityTextView) {
            //save colored icon to activity text view
            saveIconToTextView(
                GlobalValues.applicationContext,
                allActivitiesReference[activityIndex].textViewWrapper.textView,
                activityIndex,
                selectedIcon = true,
                activityIconDrawableHeight,
                activityIconDrawableWidth,
                activitiesOrderedByCategoryReference,
                allActivitiesReference,
                errorStore
            )
        }

        //save colored icon to user selected activities text view
        saveIconToTextView(
            GlobalValues.applicationContext,
            userSelectedActivities[modifiedActivityIndex].textView,
            activityIndex,
            selectedIcon = true,
            activityIconDrawableHeight,
            activityIconDrawableWidth,
            activitiesOrderedByCategoryReference,
            allActivitiesReference,
            errorStore
        )

    }

    private fun View.setAllClickable(clickable: Boolean) {
        isClickable = clickable
        if (this is ViewGroup) children.forEach { child -> child.setAllClickable(clickable) }
    }

    override fun onSelectChoiceDismissed() {
        binding.selectCategoriesFragmentRelativeLayout.setAllClickable(true)
    }

    private fun setLoadingState(loading: Boolean) {
        Log.i("HAS_ALL_INFO", "setLoadingState $loading")
        if (loading) {
            binding.chooseCategoryLayout.visibility = View.GONE
            binding.selectCategoriesRecyclerCardView.visibility = View.GONE
            binding.continueButtonLayout.visibility = View.GONE

            binding.selectCategoriesPBar.visibility = View.VISIBLE
        } else {
            binding.chooseCategoryLayout.visibility = View.VISIBLE
            binding.selectCategoriesRecyclerCardView.visibility = View.VISIBLE

            if (calledFrom == SelectCategoriesFragmentCalledFrom.LOGIN_FRAGMENT) {
                binding.continueButtonLayout.visibility = View.VISIBLE
            }

            binding.selectCategoriesPBar.visibility = View.GONE
        }
    }

    private fun neededVeriInfoErrorMessage(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String,
        veriInfo: NeededVeriInfoResponse
    ) {

        var errMsg = passedErrMsg
        errMsg += veriInfo.toString()

        categoriesErrorMessage(errMsg, lineNumber, stackTrace)
    }

    private fun categoriesErrorMessage(passedErrMsg: String, lineNumber: Int, stackTrace: String) {

        var errMsg = passedErrMsg

        for (i in userSelectedActivities.indices) {
            errMsg += "Activity $i "
            errMsg += userSelectedActivities[i].toString()
        }

        errMsg += "\nCategory Block"
        errMsg += "\nActivity Index: ${categoryTimeBlock.activityIndex}"
        errMsg += "\nCalled From Enum: ${categoryTimeBlock.calledFromEnum.name}"
        errMsg += "\nSelected Activity Index: ${categoryTimeBlock.userSelectedIndex}"
        errMsg += "\nTime Frame Index: ${categoryTimeBlock.timeFrameIndex}"
        errMsg += "\nStart Minute: ${categoryTimeBlock.startMinute}"
        errMsg += "\nStart Hour: ${categoryTimeBlock.startHour}"
        errMsg += "\nStart Day: ${categoryTimeBlock.startDay}"
        errMsg += "\nStart Month: ${categoryTimeBlock.startMonth}"
        errMsg += "\nStart Year: ${categoryTimeBlock.startYear}"
        errMsg += "\nStart Timestamp: ${categoryTimeBlock.startTimeTimestamp}"
        errMsg += "\nStop Minute: ${categoryTimeBlock.stopMinute}"
        errMsg += "\nStop Hour: ${categoryTimeBlock.stopHour}"
        errMsg += "\nStop Day: ${categoryTimeBlock.stopDay}"
        errMsg += "\nStop Month: ${categoryTimeBlock.stopMonth}"
        errMsg += "\nStop Year: ${categoryTimeBlock.stopYear}"
        errMsg += "\nStop Timestamp: ${categoryTimeBlock.stopTimeTimestamp}"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errMsg
        )
    }

}
