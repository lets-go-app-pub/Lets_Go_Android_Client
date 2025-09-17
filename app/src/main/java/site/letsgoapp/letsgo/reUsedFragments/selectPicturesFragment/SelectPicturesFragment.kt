package site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.LetsGoApplicationClass
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment.PicturePopOutDialogFragment
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.databinding.FragmentSelectPicturesBinding
import site.letsgoapp.letsgo.databinding.ViewSelectPictureBoxBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.StartDeleteFile
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class SelectPicturesFragment : Fragment() {

    //NOTE: using application context for file management

    private var _binding: FragmentSelectPicturesBinding? = null
    private val binding get() = _binding!!

    private lateinit var selectPicturesViewModel: SelectPicturesViewModel
    private lateinit var thisFragmentInstanceID: String

    private var fragmentLoading = false
    private val pictureImageViews =
        ArrayList<PictureDataClassForFragment>() //stored the objects associated with an image view

    private lateinit var returnAllPicturesObserver: Observer<EventWrapperWithKeyString<ReturnAllPicturesDataValues>>
    private lateinit var setPictureReturnValueObserver: Observer<EventWrapperWithKeyString<SetPictureReturnDataHolder>>
    private lateinit var returnSinglePictureObserver: Observer<EventWrapperWithKeyString<ReturnSinglePictureValues>>

    //This will hold a reference to the parents text view so it must be cleared in onDestroyView()
    private lateinit var setErrorTextView: (String?) -> Unit //a lambda to take in values for a text view and set them

    //default is larger than largest possible index
    private var firstPictureIndex =
        GlobalValues.server_imported_values.numberPicturesStoredPerAccount

    private var currentFragment: Fragment? = null

    private var picturesList = mutableListOf<PictureInfo>()

    private var sharedApplicationOrLoginViewModelInstanceId = ""

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private inner class RegisterForRequestPicture {

        private var passedIndex = 0
        private val receivePictureResult =
            //called on the main thread (can modify views and stuff)
            registerForActivityResult(ActivityResultContracts.StartActivityForResult())
            { result ->
                //result.resultCode == Activity.RESULT_OK is checked inside the function
                receivePictureResult(passedIndex, result)
            }

        fun runRegisterForRequestPictureActivityResult(intent: Intent, index: Int) {
            passedIndex = index
            receivePictureResult.launch(intent)
        }
    }

    private var registerForRequestPicture: RegisterForRequestPicture? = null

    private val pictureStillBeingRequested = Handler(Looper.getMainLooper())
    private val checkPictureToken = "Check_Picture_Token_"

    fun checkIfAtLeastOnePictureExists(): Boolean {

        for (p in pictureImageViews) {
            if (p.userPictureAtThisIndex) {
                return true
            }
        }

        return false
    }

    fun setFunctions(_setErrorTextView: (String?) -> Unit) {
        setErrorTextView = _setErrorTextView
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSelectPicturesBinding.inflate(inflater, container, false)

        val activity = requireActivity()

        sharedApplicationOrLoginViewModelInstanceId =
            when (activity) {
                is LoginActivity -> {
                    activity.sharedLoginViewModelInstanceId
                }
                is AppActivity -> {
                    activity.sharedApplicationViewModelInstanceId
                }
                else -> {
                    val errorMessage =
                        "Error when initializing pictures fragment, the activity starting it was neither LoginActivity or AppActivity.\n" +
                                "activity: $activity\n"
                    if (!GlobalValues.setupForTesting) { //When testing the activity could be an empty activity.
                        picturesErrorMessage(
                            errorMessage,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )
                    }
                    ""
                }
            }

        //Initialize categories view model for the fragment to access
        val viewModelFactory =
            SelectPicturesViewModelFactory(
                (GlobalValues.applicationContext as LetsGoApplicationClass).picturesRepository,
                ServiceLocator.testingDeleteFileInterface ?: StartDeleteFile(GlobalValues.applicationContext)
            )
        selectPicturesViewModel =
            ViewModelProvider(this, viewModelFactory)[SelectPicturesViewModel::class.java]
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        registerForRequestPicture = RegisterForRequestPicture()

        initializeViewImageArray()

        //NOTE: this must be set up here, if the fragment is re-used (for example the back button is pressed)
        // then if will not be set because it is cleared onDestroyView()
        currentFragment = this

        setPictureReturnValueObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let { result ->
                handleTimestampReturnValue(result)
            }
        }

        selectPicturesViewModel.setPictureReturnValue.observe(
            viewLifecycleOwner,
            setPictureReturnValueObserver
        )

        returnAllPicturesObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let { result ->
                savePicturesToImageViews(result)
            }
        }

        selectPicturesViewModel.returnAllPictures.observe(
            viewLifecycleOwner,
            returnAllPicturesObserver
        )

        returnSinglePictureObserver = Observer { wrapper ->
            wrapper.getContentIfNotHandled(thisFragmentInstanceID)?.let { result ->
                saveSinglePictureToImageViews(
                    result.index,
                    result.pictureDataEntity
                )
            }
        }

        selectPicturesViewModel.returnSinglePicture.observe(
            viewLifecycleOwner,
            returnSinglePictureObserver
        )

        //send request for pictures from database
        selectPicturesViewModel.retrievePicturesFromDatabase(thisFragmentInstanceID)

        for (i in pictureImageViews.indices) {
            pictureImageViews[i].imageView.setSafeOnClickListener {

                var index = i
                it.isEnabled = false

                //check if any other pictures before this one have not been loaded, if they have not use that picture instead
                for (j in 0 until i) {
                    if (!pictureImageViews[j].userPictureAtThisIndex &&
                        pictureImageViews[j].imageStatus == ImageStatusEnum.SHOWING_PICTURE
                    ) {
                        index = j
                        break
                    }
                }

                if (pictureImageViews[index].imageStatus == ImageStatusEnum.SHOWING_LOADING) {

                    var foundPicture = false
                    //this if statement will be reached if the previous imageViews all had pictures set or were loading and this one is loading
                    for (j in i until pictureImageViews.size) {
                        if (!pictureImageViews[j].userPictureAtThisIndex &&
                            pictureImageViews[j].imageStatus == ImageStatusEnum.SHOWING_PICTURE
                        ) {
                            foundPicture = true
                            selectPicturesWithIntent(
                                requireActivity(),
                                childFragmentManager,
                                {
                                    try {
                                        val imageFile = createTemporaryImageFile(
                                            getString(R.string.user_pictures_from_camera_file_prefix),
                                            requireContext()
                                        )
                                        pictureImageViews[index].tempFilePath =
                                            imageFile?.absolutePath.toString()
                                        imageFile
                                    } catch (e: java.lang.Exception) {
                                        when (e) {
                                            is IOException, is IllegalArgumentException, is SecurityException -> {
                                                val errorMessage =
                                                    "Error when creating a temporary picture using createTemporaryImageFile().\n" +
                                                            "Exception: ${e.message}\n"
                                                picturesErrorMessage(
                                                    errorMessage,
                                                    Thread.currentThread().stackTrace[2].lineNumber,
                                                    printStackTraceForErrors()
                                                )
                                                null
                                            }
                                            else -> throw e
                                        }
                                    }
                                },
                                { intent ->
                                    registerForRequestPicture?.runRegisterForRequestPictureActivityResult(
                                        intent,
                                        index
                                    )
                                }
                            )
                            break
                        }
                    }

                    //onResume will not be called in this case
                    if (!foundPicture)
                        it.isEnabled = true

                    //will do nothing here if no pictures are available to be loaded into
                } else { //if image status is NOT loading

                    selectPicturesWithIntent(
                        requireActivity(),
                        childFragmentManager,
                        createImageFile = {
                            try {
                                val imageFile = createTemporaryImageFile(
                                    getString(R.string.user_pictures_from_camera_file_prefix),
                                    requireContext()
                                )
                                pictureImageViews[index].tempFilePath =
                                    imageFile?.absolutePath.toString()
                                imageFile
                            } catch (e: java.lang.Exception) {
                                when (e) {
                                    is IOException, is IllegalArgumentException, is SecurityException -> {
                                        val errorMessage =
                                            "Error when creating a temporary picture using createTemporaryImageFile().\n" +
                                                    "Exception: ${e.message}\n"
                                        picturesErrorMessage(
                                            errorMessage,
                                            Thread.currentThread().stackTrace[2].lineNumber,
                                            printStackTraceForErrors()
                                        )
                                        null
                                    }
                                    else -> throw e
                                }
                            }
                        },
                        startActivityIntent = { intent ->
                            registerForRequestPicture?.runRegisterForRequestPictureActivityResult(
                                intent,
                                index
                            )
                        }
                    )
                }
            }
        }
    }

    private fun initializeViewImageArray() {

        for (i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
            val childBinding = ViewSelectPictureBoxBinding.inflate(layoutInflater)
            binding.categoryListItemFlexBoxLayout.addView(childBinding.root)
            pictureImageViews.add(
                PictureDataClassForFragment(
                    childBinding.pictureSlotImageView,
                    childBinding.pictureSlotProgressBar
                )
            )
        }
    }

    private fun deleteTempPictureFile(index: Int) {
        if (index < 0 || pictureImageViews.size <= index) {
            return
        }
        try {
            //delete the file if the camera was not used to avoid leaving an empty file around
            File(pictureImageViews[index].tempFilePath).delete()
        } catch (e: Exception) {
            when (e) {
                is IOException, is SecurityException -> {
                    val errorMessage = "Error when deleting a temporary picture.\n" +
                            "Exception: ${e.message}\n"
                    picturesErrorMessage(
                        errorMessage,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )
                }
                else -> throw e
            }
        }
    }

    private fun receivePictureResult(requestIndex: Int, result: ActivityResult) {

        var requestCodeMatch = false
        var index = -1

        for (i in pictureImageViews.indices) {
            if (requestIndex == i) {
                index = requestIndex
                requestCodeMatch = true
                break
            }
        }

        if (requestCodeMatch && result.resultCode == Activity.RESULT_OK) {

            val imageUri: Uri =
                if (result.data?.data != null) { //this means file or photo chooser was used
                    result.data!!.data!!
                } else { //this means the camera was used
                    Uri.fromFile(File(pictureImageViews[index].tempFilePath))
                }

            setImageViewStatus(ImageStatusEnum.SHOWING_LOADING, index)

            CoroutineScope(Dispatchers.Main).launch {
                //NOTE: deleteTempPictureFile() is called from inside extractByteArray();
                extractByteArray(
                    imageUri,
                    index
                )
            }
        } else {
            deleteTempPictureFile(index)
        }
    }

    //NOTE: It is this functions responsibility to clean up by calling deleteTempPictureFile().
    private suspend fun extractByteArray(
        imageUri: Uri,
        index: Int
    ) = withContext(ServiceLocator.globalIODispatcher) {

        //NOTE: Glide was not used originally here. However, it does a good job handling
        // that the camera rotates the picture on certain devices.
        GlideApp.with(requireContext())
            .asBitmap()
            .load(imageUri)
            .listener(object : RequestListener<Bitmap?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    isFirstResource: Boolean,
                ): Boolean {

                    deleteTempPictureFile(index)

                    val errorString =
                        "An error occurred when extracting a bitmap from a uri using Glide.\n" +
                                "GlideException: ${e?.message}\n" +
                                "isFirstResource: $isFirstResource" +
                                "ImageURI: $imageUri\n" +
                                "Index: $index"

                    picturesErrorMessage(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        printStackTraceForErrors()
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            GlobalValues.applicationContext,
                            R.string.get_pictures_error_loading_picture,
                            Toast.LENGTH_SHORT
                        ).show()
                        setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)
                    }
                    return false
                }

                override fun onResourceReady(
                    pictureBitmap: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (pictureBitmap != null) {
                        CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                            Log.i("first_stuff", "isRecycled: ${pictureBitmap.isRecycled}")
                            formatBitmapAndCreateThumbnail(
                                pictureBitmap,
                                index
                            )
                            deleteTempPictureFile(index)
                        }
                    }
                    else {
                        //NOTE: This seems to be possible when.
                        // 1) User clicks 'Files' when device is offline.
                        // 2) User selects a file that has not been downloaded by the device.

                        deleteTempPictureFile(index)

                        val errorString =
                            "An input stream extracting a Uri returned an exception.\n" +
                                    "isFirstResource: $isFirstResource" +
                                    "ImageURI: $imageUri\n" +
                                    "Index: $index"

                        picturesErrorMessage(
                            errorString,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors()
                        )

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                GlobalValues.applicationContext,
                                R.string.get_pictures_error_loading_picture,
                                Toast.LENGTH_SHORT
                            ).show()
                            setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)
                        }
                    }

                    return false
                }
            })
            .submit()
    }

    //If the width is larger than the height, this will limit the height and force it to be cropped
    // as a square. If the width is smaller than or equal to the height, this will limit the height
    // and maintain the aspect ration.

    private suspend fun formatBitmapAndCreateThumbnail(
        pictureBitmapRaw: Bitmap,
        index: Int
    ) = withContext(ServiceLocator.globalIODispatcher) {

        Log.i("pic_bitmap_stats", "pictureThumbnailMaximumCroppedSizePx: ${GlobalValues.server_imported_values.pictureThumbnailMaximumCroppedSizePx} height: ${pictureBitmapRaw.height} width: ${pictureBitmapRaw.width} size: ${pictureBitmapRaw.byteCount}")

        val thumbnailOStream = ByteArrayOutputStream()
        val pictureOStream = ByteArrayOutputStream()

        val (thumbnailHeight, thumbnailWidth) = calculateHeightAndWidthForBitmap(
            GlobalValues.server_imported_values.pictureThumbnailMaximumCroppedSizePx,
            pictureBitmapRaw.height,
            pictureBitmapRaw.width
        )

        val thumbnailBitmap = ThumbnailUtils.extractThumbnail(
            pictureBitmapRaw,
            thumbnailWidth,
            thumbnailHeight
        )

        thumbnailBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            GlobalValues.server_imported_values.imageQualityValue,
            thumbnailOStream
        )

        val (pictureHeight, pictureWidth) = calculateHeightAndWidthForBitmap(
            GlobalValues.server_imported_values.pictureMaximumCroppedSizePx,
            pictureBitmapRaw.height,
            pictureBitmapRaw.width
        )

        val pictureBitmap = ThumbnailUtils.extractThumbnail(
            pictureBitmapRaw,
            pictureWidth,
            pictureHeight,
        )

        pictureBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            GlobalValues.server_imported_values.imageQualityValue,
            pictureOStream
        )

        val thumbnailByteArray = thumbnailOStream.toByteArray()
        val pictureByteArray = pictureOStream.toByteArray()

        Log.i("picture_size", "pictureByteArray: ${pictureByteArray.size} pictureBitmap.width ${pictureBitmap.width} pictureBitmap.height ${pictureBitmap.height}")
        Log.i("picture_size", "thumbnailByteArray: ${thumbnailByteArray.size} thumbnailBitmap.width ${thumbnailBitmap.width} thumbnailBitmap.height ${thumbnailBitmap.height}")

        //At this point onDestroy could have already been called (or is currently being called) which can create a data
        // race for pictureImageViews. The Main context with the check below will provide protection for that.
        withContext(Dispatchers.Main) innerContext@{

            if (index >= pictureImageViews.size) {
                return@innerContext
            }

            if (pictureByteArray.size < GlobalValues.server_imported_values.maximumPictureSizeInBytes && pictureByteArray.isNotEmpty()
                && thumbnailByteArray.size < GlobalValues.server_imported_values.maximumPictureThumbnailSizeInBytes && thumbnailByteArray.isNotEmpty()
            ) { //if the file is not too big

                //storing the byte array here so I can avoid storing the reference in the live data
                //this way this will be the final reference and I can remove it after loading the image
                pictureImageViews[index].compressedByteArray = pictureByteArray

                Log.i("LoginGetPic", "sending Picture!")

                //don't ever let this function be called without setting the byteArrays first
                selectPicturesViewModel.sendPicture(
                    pictureByteArray,
                    thumbnailByteArray,
                    index,
                    firstPictureIndex = (index <= firstPictureIndex),
                    thisFragmentInstanceID,
                    sharedApplicationOrLoginViewModelInstanceId
                )

            } else { //if file is too big

                var toastMessage = getString(R.string.get_pictures_picture_incompatible)

                if (pictureByteArray.size > GlobalValues.server_imported_values.maximumPictureSizeInBytes) {

                    //These are the same for now but they may change
                    toastMessage = getString(R.string.get_pictures_picture_incompatible)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        GlobalValues.applicationContext,
                        toastMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                    setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)
                }
            }
        }
    }

    private fun savePicturesToImageViews(picturesArray: ReturnAllPicturesDataValues) {
        for (i in picturesArray.allPictures.indices) {
            saveSinglePictureToImageViews(
                i,
                picturesArray.allPictures[i]
            )
        }
    }

    private fun saveSinglePictureToImageViews(
        index: Int,
        pictureDataEntity: AccountPictureDataEntity?
    ) {
        if (pictureDataEntity == null
            || pictureDataEntity.picturePath.isBlank()
            || pictureDataEntity.pictureSize <= 0
        ) {
            Log.i("LoginGetPic", "saveSinglePictureToImageViews() picture broken/empty")
            setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)
        } else {
            Log.i("LoginGetPic", "savePicturesToImageViews() setting index $index")
            if (index < firstPictureIndex) {
                firstPictureIndex = index
            }
            setImageViewStatus(ImageStatusEnum.SHOWING_LOADING, index)
            if (!GlobalValues.setPicturesBools[index].get()) { //picture is not still being requested
                Log.i("LoginGetPic", "saveSinglePictureToImageViews() picture NOT being requested")
                CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                    setImageView(pictureDataEntity, index)
                }
            } else {
                Log.i(
                    "LoginGetPic",
                    "saveSinglePictureToImageViews() picture still being requested"
                )
                //Poll until the setPicturesBools has been set to false, then request the picture from the database.
                setupToPollIndex(index)
            }
        }
    }

    private fun setupToPollIndex(index: Int) {
        pictureStillBeingRequested.postAtTime(
            {
                if (!GlobalValues.setPicturesBools[index].get()) {
                    runBlocking {
                        selectPicturesViewModel.retrieveSinglePicture(index, thisFragmentInstanceID)
                    }
                } else {
                    setupToPollIndex(index)
                }
            },
            checkPictureToken,
            SystemClock.uptimeMillis() + 500L, //arbitrary polling delay here
        )
    }

    private suspend fun setImageView(
        pictureEntity: AccountPictureDataEntity,
        index: Int
    ) = withContext(ServiceLocator.globalIODispatcher) {

        val file = File(pictureEntity.picturePath)

        val fileSize =
            try {
                file.readBytes().size
            } catch (e: IOException) {
                val errorMessage =
                    "Exception when attempting to run readBytes() on a picture File." +
                            "Exception: ${e.message}"

                picturesErrorMessage(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

                0
            }

        //This is started from a separate CoRoutine, because onDestroy could have been called and to avoid
        // data races, this block will be called on Main thread.
        withContext(Dispatchers.Main) innerContext@{

            if (fileSize != pictureEntity.pictureSize) {

                selectPicturesViewModel.deletePictureOnClient(index, pictureEntity.picturePath)

                setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)

                val errorString =
                    "An image loaded from the database had an incorrect byte size./n" +
                            "File Size: $fileSize\n" +
                            "Database Size Value: ${pictureEntity.pictureSize}"

                picturesErrorMessage(
                    errorString,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors()
                )

            } else {
                currentFragment?.let {

                    //setUserPictureAtIndex() requires a proper index, if not available then onDestroy has already
                    // been called.
                    if (index >= pictureImageViews.size) {
                        return@innerContext
                    }

                    GlideApp.with(it)
                        .load(file)
                        .error(GlobalValues.defaultPictureResourceID)
                        .signature(generateFileObjectKey(pictureEntity.pictureTimestamp))
                        .apply(RequestOptions.circleCropTransform())
                        .listener(object : RequestListener<Drawable?> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable?>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable?>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                //pictureImageViews is cleared in onDestroy, so adding a check here
                                if (index < pictureImageViews.size) {
                                    pictureImageViews[index].imageView.setBackgroundResource(0)
                                }
                                return false
                            }
                        }
                        )
                        .into(pictureImageViews[index].imageView)

                    setUserPictureAtIndex(
                        index,
                        pictureEntity
                    )

                    setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)
                }
            }
        }
    }

    private fun handleTimestampReturnValue(
        returnValue: SetPictureReturnDataHolder
    ) {

        val index = returnValue.pictureIndex
        setErrorTextView(null)

        when {
            returnValue.invalidParameterPassed -> { //server returned invalid
                Toast.makeText(
                    GlobalValues.applicationContext,
                    R.string.get_pictures_invalid_picture,
                    Toast.LENGTH_LONG
                ).show()
            }
            returnValue.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS -> { //successfully validated and stored email
                if (index < firstPictureIndex) {
                    firstPictureIndex = index
                }

                val pictureBitmap =
                    BitmapFactory.decodeByteArray(
                        pictureImageViews[index].compressedByteArray,
                        0, pictureImageViews[index].compressedByteArray!!.size
                    )

                GlideApp.with(this)
                    .load(pictureBitmap)
                    .error(GlobalValues.defaultPictureResourceID)
                    .signature(generateFileObjectKey(returnValue.updatedTimestamp))
                    .apply(RequestOptions.circleCropTransform())
                    .listener(object : RequestListener<Drawable?> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable?>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable?>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            pictureImageViews[index].imageView.setBackgroundResource(0)
                            return false
                        }
                    }
                    )
                    .into(pictureImageViews[index].imageView)

                setUserPictureAtIndex(
                    index,
                    AccountPictureDataEntity(
                        index,
                        returnValue.picturePath,
                        returnValue.pictureSize,
                        returnValue.updatedTimestamp
                    )
                )
            }
        }

        setImageViewStatus(ImageStatusEnum.SHOWING_PICTURE, index)

        //clear the byte array to free RAM, this should be the final byteArray reference remaining
        pictureImageViews[index].compressedByteArray = null
    }

    private fun setUserPictureAtIndex(index: Int, userPictureEntity: AccountPictureDataEntity) {
        pictureImageViews[index].userPictureAtThisIndex = true
        pictureImageViews[index].userPictureEntity = userPictureEntity
        setupPictureLongClickDialog(index)
    }

    //TESTING_NOTE: Make sure that when deleting a picture in the middle of the array this works.
    // The generated list index is different than the actual array index.
    private fun setupPictureLongClickDialog(index: Int) {
        pictureImageViews[index].imageView.setOnLongClickListener {

            //NOTE: The index inside picturesList is different than the index inside of pictureImageViews (and can change). Because
            // this is generated inside setOnLongClickListener() then the index should be correct for this dialog fragment.
            var mappedIndex = 0
            picturesList.clear()
            for (i in 0 until pictureImageViews.size) {
                if (pictureImageViews[i].userPictureAtThisIndex) {
                    if (i == index) mappedIndex = picturesList.size
                    picturesList.add(
                        PictureInfo(
                            pictureImageViews[i].userPictureEntity.picturePath,
                            picturesList.size,
                            -1
                        )
                    )
                }
            }

            val picturesString = convertPicturesListToString(picturesList)

            val bundle = Bundle()
            bundle.putString(PICTURE_STRING_FRAGMENT_ARGUMENT_KEY, picturesString)
            bundle.putInt(
                PICTURE_INDEX_NUMBER_FRAGMENT_ARGUMENT_KEY,
                mappedIndex
            )

            val dialogFragment = PicturePopOutDialogFragment()

            dialogFragment.arguments = bundle
            dialogFragment.show(childFragmentManager, "DialogFragment")

            true
        }
    }

    private fun setImageViewStatus(displayType: ImageStatusEnum, index: Int) {
        when (displayType) {
            ImageStatusEnum.SHOWING_LOADING -> {
                pictureImageViews[index].progressBar.visibility = View.VISIBLE

                pictureImageViews[index].imageStatus = displayType
            }
            ImageStatusEnum.SHOWING_PICTURE -> {
                pictureImageViews[index].progressBar.visibility = View.GONE

                pictureImageViews[index].imageStatus = displayType
            }
        }
    }

    private fun picturesErrorMessage(
        passedErrMsg: String,
        lineNumber: Int,
        stackTrace: String
    ) {

        //NOTE: Make sure to use GlobalValues.applicationContext instead of
        // requireActivity().applicationContext just in case this is called AFTER the
        // fragment is disconnected.
        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            passedErrMsg
        )
    }

    override fun onResume() {
        super.onResume()
        //these can be disabled when the intent is called
        for (i in pictureImageViews.indices) {
            pictureImageViews[i].imageView.isEnabled = true
        }
    }

    override fun onDestroyView() {

        pictureStillBeingRequested.removeCallbacksAndMessages(checkPictureToken)

        _binding = null
        fragmentLoading = false
        setErrorTextView = {}
        firstPictureIndex = GlobalValues.server_imported_values.numberPicturesStoredPerAccount
        pictureImageViews.clear()
        setErrorTextView = {}
        currentFragment = null
        registerForRequestPicture = null
        super.onDestroyView()
    }

    companion object {

        @VisibleForTesting
        fun calculateHeightAndWidthForBitmap(
            maxSizePx: Int,
            rawBitmapHeightPx: Int,
            rawBitmapWidthPx: Int
        ): Pair<Int, Int> {
            //Crop edges if landscape type picture.
            val croppedWidth = minOf(rawBitmapHeightPx, rawBitmapWidthPx)

            //Scale picture to max size.
            val height = minOf(maxSizePx, rawBitmapHeightPx)
            val width = (height.toFloat() * croppedWidth.toFloat()/rawBitmapHeightPx.toFloat()).toInt()

            return Pair(height, width)
        }
    }
}
