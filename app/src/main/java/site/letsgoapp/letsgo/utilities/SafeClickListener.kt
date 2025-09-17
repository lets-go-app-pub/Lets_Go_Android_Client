package site.letsgoapp.letsgo.utilities

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.TextView

class SafeClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (View) -> Unit
) : View.OnClickListener {
    private var lastTimeClicked: Long = 0
    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(v)
    }

    constructor(_onSafeCLick: (View) -> Unit) : this(
        1000, _onSafeCLick
    )
}

class SafeMenuClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (MenuItem) -> Unit
) : MenuItem.OnMenuItemClickListener {
    private var lastTimeClicked: Long = 0

    //NOTE: The return value for onMenuItemClick() states "if it returns true, no other callbacks will be executed."
    // however it seems that if the item is clicked WHILE the previous callback is still in
    // effect both can be called. This means the return value is not very relevant.
    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return false
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(item)
        return true
    }

    constructor(_onSafeCLick: (MenuItem) -> Unit) : this(
        1000, _onSafeCLick
    )
}

class SafeEditorActionClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (v: TextView?, actionId: Int, event: KeyEvent?) -> Boolean
) : TextView.OnEditorActionListener {
    private var lastTimeClicked: Long = 0

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return false
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        return onSafeCLick(v, actionId, event)
    }

    constructor(_onSafeCLick: (v: TextView?, actionId: Int, event: KeyEvent?) -> Boolean) : this(
        1000, _onSafeCLick
    )
}

//Prevents double clicks on the specified editor action button
fun TextView.setSafeEditorActionClickListener(onSafeClick: (v: TextView?, actionId: Int, event: KeyEvent?) -> Boolean) {
    val safeClickListener = SafeEditorActionClickListener { one, two, three ->
        onSafeClick(one, two, three)
    }
    setOnEditorActionListener(safeClickListener)
}

//Prevents double clicks on the specified menu item button
fun MenuItem.setSafeOnMenuItemClickListener(onSafeClick: (MenuItem) -> Unit) {
    val safeClickListener = SafeMenuClickListener {
        onSafeClick(it)
    }
    setOnMenuItemClickListener(safeClickListener)
}

//Prevents double clicks on the specified button
fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

fun View.setSafeOnClickListener(safeClickListener: SafeClickListener?) {
    safeClickListener?.let {
        setOnClickListener(it)
    }
}

//Prevents double clicks on the specified button
fun View.setSafeOnClickListener(defaultInterval: Int, onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener(defaultInterval) {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}