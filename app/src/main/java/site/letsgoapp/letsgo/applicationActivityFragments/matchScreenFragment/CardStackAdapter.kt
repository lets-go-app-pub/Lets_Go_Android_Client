package site.letsgoapp.letsgo.applicationActivityFragments.matchScreenFragment

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import categorytimeframe.CategoryTimeFrame
import com.bumptech.glide.RequestManager
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.otherUsersDatabase.matches.MatchesDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.OtherUsersDataEntity
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.UserInfoCardLogic

class CardStackAdapter(
    private val context: Context,
    private val glideContext: RequestManager,
    private var items: MutableList<Pair<OtherUsersDataEntity, MatchesDataEntity>>,
    private val deviceScreenWidth: Int,
    private val userAge: Int,
    private val userActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage>,
    private val matchListItemDateTimeTextViewWidth: Int,
    private val matchListItemDateTimeTextViewHeight: Int,
    private val childFragmentManager: FragmentManager,
    private val requestUpdateSingleUser: (userAccountOID: String) -> Unit,
    private val updateOtherUserToObserved: (userAccountOID: String) -> Unit,
    private val errorStore: StoreErrorsInterface
) : RecyclerView.Adapter<CardStackAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_matching_user, parent, false),
            context,
            glideContext,
            deviceScreenWidth,
            userAge,
            userActivities,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            childFragmentManager,
            requestUpdateSingleUser,
            updateOtherUserToObserved,
            errorStore
        )

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.userInfoCardLogic.initializeInfo(items[position].first, false)
    }

    class ViewHolder(
        itemView: View,
        context: Context,
        glideContext: RequestManager,
        deviceScreenWidth: Int,
        userAge: Int,
        userActivities: MutableList<CategoryTimeFrame.CategoryActivityMessage>,
        matchListItemDateTimeTextViewWidth: Int,
        matchListItemDateTimeTextViewHeight: Int,
        childFragmentManager: FragmentManager,
        requestUpdateSingleUser: (userAccountOID: String) -> Unit,
        updateOtherUserToObserved: (userAccountOID: String) -> Unit,
        errorStore: StoreErrorsInterface
    ) : RecyclerView.ViewHolder(itemView) {

        //NOTE: only 1 view holder will be created for each item, then it will be reused

        private val primaryCardLayout: View = itemView.findViewById(R.id.matchPrimaryViewCardView)

        val userInfoCardLogic = UserInfoCardLogic(
            context,
            glideContext,
            false,
            primaryCardLayout,
            deviceScreenWidth,
            userAge,
            userActivities,
            matchListItemDateTimeTextViewWidth,
            matchListItemDateTimeTextViewHeight,
            childFragmentManager,
            requestUpdateSingleUser,
            updateOtherUserToObserved,
            errorStore
        )

        init {
            Log.i("created", "created")
        }

    }

}