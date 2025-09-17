package site.letsgoapp.letsgo.loginActivityFragments.loginPromotionsOptInFragment

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginPromotionsOptInBinding
import site.letsgoapp.letsgo.databinding.FragmentLoginShowRulesBinding
import site.letsgoapp.letsgo.utilities.buildCurrentFragmentID
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener

class LoginPromotionsOptInFragment : Fragment() {

    private var _binding: FragmentLoginPromotionsOptInBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginPromotionsOptInBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)
        loginActivity = requireActivity() as LoginActivity
        loginActivity?.setHalfGlobeImagesDisplayed(false)
        return binding.root        // Inflate the layout for this fragment
    }

    override fun onDestroyView() {
        _binding = null
        loginActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Allow hyperlink to be clicked.
        binding.promotionsOptInHyperlinkTextView.movementMethod = LinkMovementMethod.getInstance()

        binding.promotionsOptInContinueButton.setSafeOnClickListener {
            loginActivity?.navigate(
                R.id.loginPromotionsOptInFragment,
                R.id.action_loginPromotionsOptInFragment_to_loginGetBirthdayFragment
            )
        }

    }
}