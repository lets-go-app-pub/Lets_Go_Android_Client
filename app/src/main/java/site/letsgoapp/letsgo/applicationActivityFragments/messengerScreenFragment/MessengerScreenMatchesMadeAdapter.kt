package site.letsgoapp.letsgo.applicationActivityFragments.messengerScreenFragment

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.RequestOptions
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomWithMemberMapDataClass
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*
import site.letsgoapp.letsgo.utilities.glideAnnotation.GlideApp

class MessengerScreenMatchesMadeAdapter(
    private val context: Context,
    private val recyclerViewTitle: TextView,
    private val items: MutableList<ChatRoomWithMemberMapDataClass>,
    private val navigateToChatRoom: (String) -> Unit,
    private val errorStore: StoreErrorsInterface
) : RecyclerView.Adapter<MessengerScreenMatchesMadeAdapter.MatchMadeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchMadeViewHolder =
        MatchMadeViewHolder(
            LayoutInflater.from(context)
                .inflate(R.layout.list_item_made_match_display, parent, false)
        )

    override fun onBindViewHolder(holder: MatchMadeViewHolder, position: Int) {
        if (items[position].chatRoomMembers.size() > 0) {

            if (items[position].chatRoomMembers.size() != 1) {

                val errorMessage =
                    "Chat rooms should have exactly one member inside of them.\n" +
                            "position $position\n"

                sendError(
                    errorMessage,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    items[position]
                )

                //can continue here
            }

            holder.showView()

            items[position].chatRoomMembers.getFromList(0)?.let { member ->
                if (member.otherUsersDataEntity.thumbnailPath != "" && member.otherUsersDataEntity.thumbnailPath != "~") { //if thumbnail path exists

                    val signatureTimestamp = member.otherUsersDataEntity.thumbnailLastTimeUpdated

                    GlideApp.with(context)
                        .load(member.otherUsersDataEntity.thumbnailPath)
                        .error(GlobalValues.defaultPictureResourceID)
                        .signature(generateFileObjectKey(signatureTimestamp))
                        .apply(RequestOptions.circleCropTransform())
                        .into(holder.thumbnailImage)
                }
            }

            holder.thumbnailImage.setSafeOnClickListener {
                navigateToChatRoom(items[position].chatRoomId)
            }

        } else {
            val errorMessage =
                "Match made chat rooms found with no members inside (should have exactly one member, the matching user).\n" +
                        "position $position\n"

            sendError(
                errorMessage,
                Thread.currentThread().stackTrace[2].lineNumber,
                printStackTraceForErrors(),
                items[position]
            )

            holder.hideView()
        }

    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun sendError(
        errorMessage: String,
        lineNumber: Int,
        stackTrace: String,
        chatRoom: ChatRoomWithMemberMapDataClass
    ) {
        val errorMsg = errorMessage + "\n" +
                "chatRoomId: ${chatRoom.chatRoomId}\n" +
                "chatRoomName: ${chatRoom.chatRoomName}\n" +
                "chatRoomPassword: ${chatRoom.chatRoomPassword}\n" +
                "notificationsEnabled: ${chatRoom.notificationsEnabled}\n" +
                "accountState: ${chatRoom.accountState}\n" +
                "chatRoomMembers.size: ${chatRoom.chatRoomMembers.size()}\n" +
                "timeJoined: ${chatRoom.timeJoined}\n" +
                "matchingChatRoomOID: ${chatRoom.matchingChatRoomOID}\n" +
                "chatRoomLastObservedTime: ${chatRoom.chatRoomLastObservedTime}\n" +
                "userLastActivityTime: ${chatRoom.userLastActivityTime}\n" +
                "chatRoomLastActivityTime: ${chatRoom.chatRoomLastActivityTime}\n" +
                "lastTimeUpdated: ${chatRoom.lastTimeUpdated}\n" +
                "finalMessage: ${chatRoom.finalMessage}\n" +
                "finalPictureMessage: ${chatRoom.finalPictureMessage}\n" +
                "displayChatRoom: ${chatRoom.displayChatRoom}\n" +
                "showLoading: ${chatRoom.showLoading}\n"

        errorStore.storeError(
            Thread.currentThread().stackTrace[2].fileName,
            lineNumber,
            stackTrace,
            errorMsg,
            context.applicationContext
        )
    }

    class MatchMadeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailImage: ImageView =
            itemView.findViewById(R.id.madeMatchDisplayListItemImageView)
        private val primaryFrameLayout: FrameLayout =
            itemView.findViewById(R.id.madeMatchDisplayPrimaryFrameLayout)

        fun hideView() {
            primaryFrameLayout.visibility = View.GONE
        }

        fun showView() {
            primaryFrameLayout.visibility = View.VISIBLE
        }
    }

    fun updateRecyclerViewTitle() {

        if (items.isEmpty()) {
            recyclerViewTitle.visibility = View.GONE
        } else {
            recyclerViewTitle.visibility = View.VISIBLE
        }
    }

}