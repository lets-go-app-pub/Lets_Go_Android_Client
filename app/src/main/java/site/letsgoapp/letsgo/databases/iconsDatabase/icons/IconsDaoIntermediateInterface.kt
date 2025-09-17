package site.letsgoapp.letsgo.databases.iconsDatabase.icons

interface IconsDaoIntermediateInterface {

    suspend fun insertOneIcon(iconsDataEntity: IconsDataEntity)

    suspend fun insertAllIcons(iconsDataEntities: List<IconsDataEntity>)

    suspend fun getSingleIcon(iconIndex: Int): IconsDataEntity?

    suspend fun getAllIcons(): List<IconsDataEntity>

    suspend fun getAllIconTimestamps(): List<Long>

    suspend fun clearTable()
}