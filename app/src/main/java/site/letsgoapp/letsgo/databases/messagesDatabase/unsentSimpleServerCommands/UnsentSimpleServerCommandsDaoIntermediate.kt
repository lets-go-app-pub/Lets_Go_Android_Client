package site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import site.letsgoapp.letsgo.globalAccess.ServiceLocator

class UnsentSimpleServerCommandsDaoIntermediate(
    private val unsentSimpleServerCommandsDatabaseDao: UnsentSimpleServerCommandsDatabaseDao,
    @Suppress("unused") private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : UnsentSimpleServerCommandsDaoIntermediateInterface {

    //insert single message
    override suspend fun insertMessage(unsentSimpleServerCommandsDataEntity: UnsentSimpleServerCommandsDataEntity) {
        unsentSimpleServerCommandsDatabaseDao.insertMessage(unsentSimpleServerCommandsDataEntity)
    }

    //select all
    override suspend fun selectAll(): List<UnsentSimpleServerCommandsDataEntity> {
        return unsentSimpleServerCommandsDatabaseDao.selectAll()
    }

    //delete all
    override suspend fun clearTable() {
        unsentSimpleServerCommandsDatabaseDao.clearTable()
    }

}