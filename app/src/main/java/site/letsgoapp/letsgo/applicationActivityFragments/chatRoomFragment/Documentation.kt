@file:Suppress("ClassName", "unused")


package site.letsgoapp.letsgo.applicationActivityFragments.chatRoomFragment

object chat_message_popup
/**
 * Because of how PopupMenu is shown on the device screen. It is shown either
 *  1) Above the view if there is room.
 *  2) Below the view if there is room.
 *  However if for example a text message takes up the entire screen and more, there
 *   will be no room for the PopupMenu to be displayed. And so it works by calling
 *   chatBubbleLayout.onTouchListener in order to record the position of
 *   the touch. Then inside chatBubbleLayout.onLongClickListener it will move a
 *   0dp x 0dp view to the location and use this view as the anchor for the PopupMenu.
 *  The map type message does not work this way. This is because the GoogleMap object
 *   does not have an easy to override onTouchEvent and so saving the location becomes
 *   much more difficult. However it should never be so large that it goes out of the
 *   screen so it is ignored.
 */