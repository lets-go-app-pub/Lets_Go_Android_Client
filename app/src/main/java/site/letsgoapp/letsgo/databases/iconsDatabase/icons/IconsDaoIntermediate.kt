package site.letsgoapp.letsgo.databases.iconsDatabase.icons

import kotlinx.coroutines.CoroutineDispatcher
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

class IconsDaoIntermediate(
    private val iconsDatabaseDao: IconsDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : IconsDaoIntermediateInterface {

    override suspend fun insertOneIcon(iconsDataEntity: IconsDataEntity) {
        iconsDatabaseDao.insertIcon(iconsDataEntity)
    }

    override suspend fun insertAllIcons(iconsDataEntities: List<IconsDataEntity>) {
        iconsDatabaseDao.insertAllIcons(iconsDataEntities)
    }

    override suspend fun getSingleIcon(iconIndex: Int): IconsDataEntity? {
        return iconsDatabaseDao.getSingleIcon(iconIndex)
    }

    override suspend fun getAllIcons(): List<IconsDataEntity> {
        return iconsDatabaseDao.getAllIcons()
    }

    override suspend fun getAllIconTimestamps(): List<Long> {
        return iconsDatabaseDao.getAllIconTimestamps()
    }

    override suspend fun clearTable() {
        iconsDatabaseDao.clearTable()
    }
}