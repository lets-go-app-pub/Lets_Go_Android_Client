package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import site.letsgoapp.letsgo.databinding.FragmentMatchPicturesDialogViewPagerBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*

class MatchPicturesDialogViewPagerFragment(
    private var closeDialog: () -> Unit
) : Fragment() {

    private var _binding: FragmentMatchPicturesDialogViewPagerBinding? = null
    private val binding get() = _binding!!

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchPicturesDialogViewPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val picturePath = arguments?.getString(PICTURE_SINGLE_PATH_FRAGMENT_ARGUMENT_KEY)
        val pictureTimestamp =
            arguments?.getLong(PICTURE_SINGLE_TIMESTAMP_FRAGMENT_ARGUMENT_KEY) ?: -1L

        if (picturePath != null) {
            Glide
                .with(requireParentFragment())
                .load(picturePath)
                .error(GlobalValues.defaultPictureResourceID)
                .signature(generateFileObjectKey(pictureTimestamp))
                .into(binding.matchPicturesDialogViewPagerImageView)
        } else {
            val errorString = "picturePaths returned null which should never happen."

            errorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                errorString
            )
        }

        binding.matchPicturesDialogViewPagerImageView.setOnClickListener {
            closeDialog()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        closeDialog = {}
        super.onDestroy()
    }
}