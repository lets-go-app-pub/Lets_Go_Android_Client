package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import site.letsgoapp.letsgo.utilities.PICTURE_SINGLE_PATH_FRAGMENT_ARGUMENT_KEY
import site.letsgoapp.letsgo.utilities.PICTURE_SINGLE_TIMESTAMP_FRAGMENT_ARGUMENT_KEY
import site.letsgoapp.letsgo.utilities.PictureInfo

class PicturesDialogCollectionAdapter(
    parentFragment: Fragment, picturesList: MutableList<PictureInfo>,
    private var closeDialog: () -> Unit
) :
    FragmentStateAdapter(parentFragment) {

    private val picturesInfo = picturesList

    override fun getItemCount(): Int {
        return picturesInfo.size
    }

    override fun createFragment(position: Int): Fragment {
        val newFragment = MatchPicturesDialogViewPagerFragment(closeDialog)
        val parametersBundle = Bundle()

        parametersBundle.putString(
            PICTURE_SINGLE_PATH_FRAGMENT_ARGUMENT_KEY,
            picturesInfo[position].picturePath
        )
        parametersBundle.putLong(
            PICTURE_SINGLE_TIMESTAMP_FRAGMENT_ARGUMENT_KEY,
            picturesInfo[position].timestampPictureLastUpdatedOnServer
        )

        newFragment.arguments = parametersBundle

        return newFragment
    }
}