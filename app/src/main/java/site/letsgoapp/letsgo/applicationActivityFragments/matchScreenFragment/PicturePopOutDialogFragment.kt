package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databinding.FragmentPicturePopoutDialogBinding
import site.letsgoapp.letsgo.utilities.*

class PicturePopOutDialogFragment : DialogFragment() {

    private var _binding: FragmentPicturePopoutDialogBinding? = null
    private val binding get() = _binding!!

    private var picturePaths: String? = null
    private var pictureIndex: Int? = null
    private var picturesDialogCollectionAdapter: PicturesDialogCollectionAdapter? = null
    private var pageChange: ViewPager2.OnPageChangeCallback? = null

    private var previousIndex = 0

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private var picIndexHeight: Int = 0
    private var picIndexMargin: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPicturePopoutDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        picIndexHeight = resources.getDimension(R.dimen.picture_index_height).toInt()
        picIndexMargin = resources.getDimension(R.dimen.picture_index_margin).toInt()

        //matchPicturesDialogViewPager
        picturePaths = arguments?.getString(PICTURE_STRING_FRAGMENT_ARGUMENT_KEY)
        pictureIndex = arguments?.getInt(PICTURE_INDEX_NUMBER_FRAGMENT_ARGUMENT_KEY)

        picturePaths?.let {

            Log.i("pictureInfoList", "PicturePopOutDialogFragment() picturePaths: $picturePaths")

            val picturesList = convertPicturesStringToList(picturePaths)

            //setup tabs at top
            if (picturesList.size > 1) {

                setupPictureIndexImage(
                    requireContext(),
                    picturesList,
                    binding.matchPicturesDialogPictureIndexLinearLayout,
                    picIndexHeight,
                    picIndexMargin
                )

                //set the top tab system to move
                pageChange = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)

                        val initialView =
                            binding.matchPicturesDialogPictureIndexLinearLayout.getChildAt(
                                previousIndex
                            )

                        setBackgroundPictureIndexImage(
                            requireContext(),
                            initialView,
                            false
                        )
                        //initialView.setBackgroundResource(R.drawable.background_image_button_border)

                        val newView =
                            binding.matchPicturesDialogPictureIndexLinearLayout.getChildAt(position)

                        val border = GradientDrawable()

                        border.setColor(
                            ResourcesCompat.getColor(
                                requireContext().resources,
                                R.color.colorBlack,
                                null
                            )
                        )

                        border.cornerRadius =
                            requireContext().resources.getDimension(R.dimen.image_button_border_radius)
                        newView.background = border

                        previousIndex = position

                    }
                }

                if (pageChange != null) {
                    pageChange?.let {
                        binding.matchPicturesDialogViewPager.registerOnPageChangeCallback(it)
                    }
                } else {
                    val errorString = "pageChange returned null which should never happen."
                    storeError(
                        errorString,
                        Thread.currentThread().stackTrace[2].lineNumber,
                        Thread.currentThread().stackTrace[2].fileName
                    )
                }
            }

            //TESTING_NOTE: make sure this doesn't leak with passing in a reference to itself
            picturesDialogCollectionAdapter =
                PicturesDialogCollectionAdapter(
                    this,
                    picturesList
                ) { dialog?.dismiss() }

            binding.matchPicturesDialogViewPager.adapter = picturesDialogCollectionAdapter

            //NOTE: this will allow it to start at the index that was selected, had it commented out because 'looks bad'
            // however it feels better to have it in
            pictureIndex?.let {
                binding.matchPicturesDialogViewPager.currentItem = it
            }

        }

        if (picturePaths == null) {

            val errorString = "picturePaths returned null which should never happen."
            storeError(
                errorString,
                Thread.currentThread().stackTrace[2].lineNumber,
                Thread.currentThread().stackTrace[2].fileName
            )
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // the content
        val root = FrameLayout(requireContext())
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // creating the fullscreen dialog
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        return dialog
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
            passedErrMsg
        )
    }

    override fun onDestroyView() {
        pageChange?.let {
            binding.matchPicturesDialogViewPager.unregisterOnPageChangeCallback(it)
            pageChange = null
        }

        _binding = null
        picturesDialogCollectionAdapter = null
        super.onDestroyView()
    }

}