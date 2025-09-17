package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.util.Log
import android.view.View
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.Direction

class CardStackListenerImpl(
    private val cardSwipedRight: () -> Unit, //Thumbs up
    private val cardSwipedLeft: () -> Unit //Thumbs Down
) : CardStackListener {
    override fun onCardDisappeared(view: View?, position: Int) {
        //NOTE: this function is called BEFORE onCardSwiped
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {

    }

    override fun onCardSwiped(direction: Direction?) {
        Log.i("positions", "runningOnCardSwiped")
        when (direction) {
            Direction.Left -> {
                cardSwipedLeft()
            }
            Direction.Right -> {
                cardSwipedRight()
            }
            else -> {}
        }
    }

    override fun onCardCanceled() {}

    override fun onCardAppeared(view: View?, position: Int) {}

    override fun onCardRewound() {}
}