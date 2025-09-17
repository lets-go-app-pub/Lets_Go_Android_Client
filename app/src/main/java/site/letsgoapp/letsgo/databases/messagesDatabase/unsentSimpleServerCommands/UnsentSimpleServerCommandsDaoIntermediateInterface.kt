package site.letsgoapp.letsgo.databases.messagesDatabase.unsentSimpleServerCommands

interface UnsentSimpleServerCommandsDaoIntermediateInterface {

    //insert single message
    suspend fun insertMessage(unsentSimpleServerCommandsDataEntity: UnsentSimpleServerCommandsDataEntity)

    //select all
    suspend fun selectAll(): List<UnsentSimpleServerCommandsDataEntity>

    //delete all
    suspend fun clearTable()

}