package site.letsgoapp.letsgo.databases.iconsDatabase.icons

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import site.letsgoapp.letsgo.utilities.iconsMapTable

@Entity(tableName = "icon_info_table")
data class IconsDataEntity(

    @PrimaryKey(autoGenerate = false)
    var iconIndex: Int,

    //file path to the basic version of the icon
    @ColumnInfo(name = "icon_is_downloaded")
    var iconIsDownloaded: Boolean = false,

    //file path to the basic version of the icon
    @ColumnInfo(name = "icon_basic_path")
    var iconFilePath: String = "",

    //Resource drawable map to the basic version of the icon. This field works
    // with iconsMapTable.
    //NOTE: Stored this way because resource Ids can change when new resources
    // are added, so if I say upgrade my app to a newer version with all the new
    // drawables, the resource ids that are stored in the database could become
    // obsolete.
    /** See also [iconsMapTable] **/
    @ColumnInfo(name = "icon_basic_resource_entry_name")
    var iconBasicResourceEntryName: String = "",

    //timestamp icon was updated on server (NOT the time this client received the update, but
    // the actual timestamp stored inside the database)
    @ColumnInfo(name = "icon_timestamp")
    var iconTimestamp: Long = -1L,

    //timestamp icon was stored
    @ColumnInfo(name = "icon_active")
    var iconActive: Boolean = false

)

