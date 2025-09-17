package site.letsgoapp.letsgo.loginActivityFragments.loginShowRulesFragment

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.LoginActivity
import site.letsgoapp.letsgo.databinding.FragmentLoginShowRulesBinding
import site.letsgoapp.letsgo.utilities.buildCurrentFragmentID
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener

class LoginShowRulesFragment : Fragment() {

    private var _binding: FragmentLoginShowRulesBinding? = null
    private val binding get() = _binding!!

    private lateinit var thisFragmentInstanceID: String

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginShowRulesBinding.inflate(inflater, container, false)
        thisFragmentInstanceID = buildCurrentFragmentID(this::class.simpleName)
        loginActivity = requireActivity() as LoginActivity
        loginActivity?.setHalfGlobeImagesDisplayed(false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        loginActivity = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.showRulesContinueButton.setSafeOnClickListener {
            loginActivity?.navigate(
                R.id.loginShowRulesFragment,
                R.id.action_loginShowRulesFragment_to_loginPromotionsOptInFragment
            )
        }

    }
}
