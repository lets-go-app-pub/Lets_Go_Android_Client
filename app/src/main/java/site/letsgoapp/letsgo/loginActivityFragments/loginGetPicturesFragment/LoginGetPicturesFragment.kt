package site.letsgoapp.letsgo.loginActivityFragments.loginGetPicturesFragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginGetPicturesBinding
import site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment.SelectPicturesFragment
import site.letsgoapp.letsgo.utilities.FRAGMENT_CALLED_FROM_KEY
import site.letsgoapp.letsgo.utilities.SelectCategoriesFragmentCalledFrom
import site.letsgoapp.letsgo.utilities.buildCurrentFragmentID
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener

class LoginGetPicturesFragment(
    private val initializeLoginActivity: Boolean = true
) : Fragment() {

    private var _binding: FragmentLoginGetPicturesBinding? = null
    private val binding get() = _binding!!
    private var fragmentLoading = false

    private lateinit var thisFragmentInstanceID: String

    private var selectPicturesFragment: SelectPicturesFragment? = null
    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginGetPicturesBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)

        if(initializeLoginActivity) {
            loginActivity = requireActivity() as LoginActivity
        }

        loginActivity?.setHalfGlobeImagesDisplayed(true)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.i("LoginGetPic", "onViewCreated()")
        selectPicturesFragment =
            childFragmentManager.findFragmentById(R.id.selectPicturesLoginFragment) as SelectPicturesFragment

        selectPicturesFragment?.setFunctions { binding.loginGetPicturesErrorTextView.text = it }

        binding.getPictureContinueButton.setSafeOnClickListener {
            //if at least 1 picture is filled out, can move on
            if (selectPicturesFragment?.checkIfAtLeastOnePictureExists() == true) {
                val argumentBundle =
                    bundleOf(FRAGMENT_CALLED_FROM_KEY to SelectCategoriesFragmentCalledFrom.LOGIN_FRAGMENT.ordinal)
                loginActivity?.navigate(
                    R.id.loginGetPicturesFragment,
                    R.id.action_loginGetPicturesFragment_to_selectCategoriesFragment,
                    argumentBundle
                )
            } else {
                binding.loginGetPicturesErrorTextView.setText(R.string.get_pictures_choose_picture)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        fragmentLoading = false
        loginActivity = null
        selectPicturesFragment = null
        super.onDestroyView()
    }
}