package site.letsgoapp.letsgo.utilities

import android.content.Context
import android.util.Log
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import site.letsgoapp.letsgo.R


//class attaches handles to the menu, allowing it to be selectively dismissed by message UUID and account OID
class AdminUserOptionsFragmentPopupMenu(private val activityContext: Context) {

    //user options pop up menu, allows for things like block & report, kick, ban etc...
    private var adminUserOptionsFragmentPopupMenu: PopupMenu? = null

    //the messageUUID that this menu instance was selected for (only relevant in chatRoomFragment, not in chatRoomInfoFragment)
    private var _messageUUIDHandle: String = ""

    //the accountOID that this menu instance was selected for
    private var _accountOIDHandle: String = ""

    fun showOtherUserNotInChatRoomOnlyBlockAndReportPopupMenu(
        v: View,
        blockAndReportMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    blockAndReportMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    fun showBlockEventPopupMenu(
        v: View,
        blockAndReportMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        Log.i("makingAPopupMenu", "makingAPopupMenu")
        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    blockAndReportMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    fun showUnblockEventPopupMenu(
        v: View,
        unblockMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    unblockMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    fun showUserNoAdminBlockAndReportPopupMenu(
        v: View,
        blockAndReportMember: () -> Unit,
        inviteMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        Log.i("makingAPopupMenu", "makingAPopupMenu")
        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    inviteMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    blockAndReportMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    fun showUserNoAdminUnblockPopupMenu(
        v: View,
        unblockMember: () -> Unit,
        inviteMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)?.isVisible =
            false
        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    inviteMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    unblockMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    private fun showUserAdminPopupMenu(
        adminUserOptionsFragmentPopupMenu: PopupMenu?,
        promoteNewAdmin: () -> Unit,
        kickMember: () -> Unit,
        banMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuPromoteToAdminMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    promoteNewAdmin()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuKickMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    kickMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBanMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    banMember()
                    true
                }
            }

    }

    fun showUserAdminBlockAndReportPopupMenu(
        v: View,
        promoteNewAdmin: () -> Unit,
        kickMember: () -> Unit,
        banMember: () -> Unit,
        blockAndReportMember: () -> Unit,
        inviteMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        showUserAdminPopupMenu(
            adminUserOptionsFragmentPopupMenu,
            promoteNewAdmin,
            kickMember,
            banMember,
            accountOIDHandle,
            messageUUIDHandle
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)?.isVisible =
            false

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    inviteMember()
                    true
                }
            }

        //show unblock
        val blockReportItem =
            adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)

        blockReportItem?.let {
            it.isVisible = true

            it.setOnMenuItemClickListener {
                blockAndReportMember()
                true
            }
        }

        adminUserOptionsFragmentPopupMenu?.show()
    }

    fun showUserAdminUnblockPopupMenu(
        v: View,
        promoteNewAdmin: () -> Unit,
        kickMember: () -> Unit,
        banMember: () -> Unit,
        unblockMember: () -> Unit,
        inviteMember: () -> Unit,
        accountOIDHandle: String,
        messageUUIDHandle: String = "",
    ) {

        _accountOIDHandle = accountOIDHandle
        _messageUUIDHandle = messageUUIDHandle

        adminUserOptionsFragmentPopupMenu = PopupMenu(activityContext, v)
        val inflater: MenuInflater? = adminUserOptionsFragmentPopupMenu?.menuInflater
        inflater?.inflate(
            R.menu.chat_room_info_admin_options_popup_menu,
            adminUserOptionsFragmentPopupMenu?.menu
        )

        showUserAdminPopupMenu(
            adminUserOptionsFragmentPopupMenu,
            promoteNewAdmin,
            kickMember,
            banMember,
            accountOIDHandle,
            messageUUIDHandle
        )

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnInviteMenuItem)
            ?.let {
                it.isVisible = true

                it.setOnMenuItemClickListener {
                    inviteMember()
                    true
                }
            }

        adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuBlockReportMenuItem)?.isVisible =
            false

        //show block and report
        val unblockItem =
            adminUserOptionsFragmentPopupMenu?.menu?.findItem(R.id.adminOptionsPopupMenuUnBlockMenuItem)

        unblockItem?.let {
            it.isVisible = true

            it.setOnMenuItemClickListener {
                unblockMember()
                true
            }
        }

        adminUserOptionsFragmentPopupMenu?.show()

    }

    fun dismissForUUID(messageUUID: String) {
        if (_messageUUIDHandle == messageUUID) {
            dismiss()
        }
    }

    fun dismissForAccountOID(accountOID: String) {
        if (_accountOIDHandle == accountOID) {
            dismiss()
        }
    }

    fun dismiss() {
        adminUserOptionsFragmentPopupMenu?.dismiss()
        adminUserOptionsFragmentPopupMenu = null
    }
}