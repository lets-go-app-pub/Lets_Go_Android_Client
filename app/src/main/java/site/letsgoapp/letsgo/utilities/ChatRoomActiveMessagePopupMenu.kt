package site.letsgoapp.letsgo.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.MenuInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.GlobalValues

//class attaches a handle to the menu, allowing it to be selectively dismissed by message UUID
class ChatRoomActiveMessagePopupMenu(private val activityContext: Context) {

    private var chatRoomMessagesPopupMenu: PopupMenu? = null

    //the messageUUID that this menu instance was selected for
    private var _messageUUIDHandle: String = ""

    fun showChatRoomTextMessagePopupMenu(
        v: View,
        editSelected: () -> Unit,
        replySelected: () -> Unit,
        deleteSelected: () -> Unit,
        editable: Boolean,
        copyText: String,
        messageUUIDHandle: String,
    ) {
        _messageUUIDHandle = messageUUIDHandle

        Log.i("makingAPopupMenu", "showChatRoomTextMessagePopupMenu()")
        chatRoomMessagesPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = chatRoomMessagesPopupMenu?.menuInflater
        inflater?.inflate(R.menu.chat_room_messages_popup_menu, chatRoomMessagesPopupMenu?.menu)
        chatRoomMessagesPopupMenu?.setForceShowIcon(true)

        if (editable) { //if editable
            chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuEditItem)
                ?.setOnMenuItemClickListener {
                    editSelected()
                    true
                }
        } else { //if not editable by current user
            chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuEditItem)?.isVisible =
                false
        }

        chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuReplyItem)
            ?.setOnMenuItemClickListener {
                replySelected()
                true
            }

        chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuDeleteItem)
            ?.setOnMenuItemClickListener {
                deleteSelected()
                true
            }

        if (copyText.isNotEmpty()) {
            chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuCopyItem)
                ?.setOnMenuItemClickListener {
                    val clipboard =
                        activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip =
                        ClipData.newPlainText(
                            activityContext.applicationContext.getString(R.string.chat_room_fragment_message_copied_clipboard_label),
                            copyText
                        )
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(
                        GlobalValues.applicationContext,
                        R.string.chat_room_fragment_message_copied_notification,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
        } else {
            chatRoomMessagesPopupMenu?.menu?.findItem(R.id.chatRoomMessagesPopupMenuCopyItem)?.isVisible =
                false
        }

        chatRoomMessagesPopupMenu?.show()

    }

    fun dismissForUUID(messageUUID: String) {
        if (_messageUUIDHandle == messageUUID) {
            dismiss()
        }
    }

    fun dismiss() {
        chatRoomMessagesPopupMenu?.dismiss()
        chatRoomMessagesPopupMenu = null
    }

}