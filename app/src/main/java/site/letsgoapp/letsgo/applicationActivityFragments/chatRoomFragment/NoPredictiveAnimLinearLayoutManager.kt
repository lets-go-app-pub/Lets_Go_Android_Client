package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView

//this class is implemented to fix a bug, an exception intermittently occurs of
// RecyclerView: Inconsistency detected. Invalid item position
// read solution here https://stackoverflow.com/questions/30220771/recyclerview-inconsistency-detected-invalid-item-position
// It will also load some extra space for the layout in order to make scrolling appear smoother (otherwise pictures can still be white
// for loading their resource AND scrolling will 'stutter' when fast). Inside calculateExtraLayoutSpace() definition it says laying
// out extra pages is expensive, so use it sparingly (couldn't actually tell much difference in the profiler).
class NoPredictiveAnimLinearLayoutManager(activityContext: Context, private var mPages: Int) :
    LinearLayoutManager(activityContext) {
    private var mOrientationHelper: OrientationHelper? = null

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

    override fun setOrientation(orientation: Int) {
        super.setOrientation(orientation)
        mOrientationHelper = null
    }

    /**
     * Set the number of pages of layout that will be preloaded off-screen,
     * a page being a pixel measure equivalent to the on-screen size of the
     * recycler view.
     * @param pages the number of pages; can be `0` to disable preloading
     */
    @Suppress("unused")
    fun setPages(pages: Int) {
        mPages = pages
    }

    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        super.calculateExtraLayoutSpace(state, extraLayoutSpace)

        if (mOrientationHelper == null) {
            mOrientationHelper = OrientationHelper.createOrientationHelper(this, orientation)
        }

        val extraLayoutSpaceAll = mOrientationHelper!!.totalSpace * mPages

        extraLayoutSpace[0] = extraLayoutSpaceAll
        extraLayoutSpace[1] = extraLayoutSpaceAll
    }

}
