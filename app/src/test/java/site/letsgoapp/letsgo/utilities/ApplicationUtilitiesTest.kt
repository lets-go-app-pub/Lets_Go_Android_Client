package site.letsgoapp.letsgo.utilities

import account_state.AccountState
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.testUtilities.generateRandomOidForTesting

class MemberMutableListWrapperTests {

    private lateinit var firstDummyOid: String
    private lateinit var dummyOtherUser: OtherUsersInfo
    private lateinit var memberMutableListWrapper: MemberMutableListWrapper

    @Before
    fun setUp() {
        memberMutableListWrapper = MemberMutableListWrapper()

        firstDummyOid = generateRandomOidForTesting()
        dummyOtherUser = OtherUsersInfo(
            OtherUsersDataEntity(firstDummyOid),
            OtherUserChatRoomInfo(),
            mutableListOf(),
            mutableListOf(),
        )
    }

    private fun checkListAndMapResult(
        accountOID: String
    ) : OtherUsersInfo {
        val listResult = memberMutableListWrapper.getFromList(0)
        val mapResult = memberMutableListWrapper.getFromMap(accountOID)

        assertNotEquals(listResult, null)
        assertNotEquals(mapResult, null)

        assertEquals(listResult?.otherUsersDataEntity?.accountOID, accountOID)
        assertEquals(mapResult?.otherUsersDataEntity?.accountOID, accountOID)

        assertEquals(mapResult, listResult)

        return mapResult!!
    }

    private fun addUserAndMakeSureExists() {
        memberMutableListWrapper.add(dummyOtherUser)

        checkListAndMapResult(firstDummyOid)
    }

    @Test
    fun `add() other user to class`() {
        addUserAndMakeSureExists()
    }

    @Test
    fun `upsertAnElementByAccountOID() when user exists`() {

        addUserAndMakeSureExists()

        val newOtherUser = OtherUsersInfo(
            OtherUsersDataEntity(
                firstDummyOid,
                "new_thumbnail"
            ),
            OtherUserChatRoomInfo(),
            mutableListOf(),
            mutableListOf(),
        )

        memberMutableListWrapper.upsertAnElementByAccountOID(
            firstDummyOid,
            newOtherUser
        )

        val result = checkListAndMapResult(firstDummyOid)
        assertEquals(result, newOtherUser)
    }

    @Test
    fun `upsertAnElementByAccountOID() when user does not exist`() {
        memberMutableListWrapper.upsertAnElementByAccountOID(
            firstDummyOid,
            dummyOtherUser
        )

        checkListAndMapResult(firstDummyOid)
    }

    @Test
    fun updatePicturesUpdateAttemptedTimestampByAccountOIDs() {
        addUserAndMakeSureExists()

        val initialTimestamp = 5L
        val newTimestamp = 5L

        dummyOtherUser.otherUsersDataEntity.picturesUpdateAttemptedTimestamp = initialTimestamp

        memberMutableListWrapper.updatePicturesUpdateAttemptedTimestampByAccountOIDs(
            listOf(firstDummyOid),
            newTimestamp
        )

        val mapResult = memberMutableListWrapper.getFromMap(firstDummyOid)

        assertNotEquals(mapResult, null)
        assertEquals(mapResult?.otherUsersDataEntity?.picturesUpdateAttemptedTimestamp, newTimestamp)
    }

    @Test
    fun `updateAccountStateByAccountOID() with accountOID`() {
        addUserAndMakeSureExists()

        val initialTimestamp = 5L
        val newTimestamp = 5L

        dummyOtherUser.otherUsersDataEntity.picturesUpdateAttemptedTimestamp = initialTimestamp

        memberMutableListWrapper.updatePicturesUpdateAttemptedTimestampByAccountOIDs(
            listOf(firstDummyOid),
            newTimestamp
        )

        val mapResult = memberMutableListWrapper.getFromMap(firstDummyOid)

        assertNotEquals(mapResult, null)
        assertEquals(mapResult!!.otherUsersDataEntity.picturesUpdateAttemptedTimestamp, newTimestamp)
    }

    @Test
    fun `updateAccountStateByAccountOID() with OtherUsersInfo`() {
        addUserAndMakeSureExists()

        val initialAccountState = AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IN_CHAT_ROOM
        val newAccountState = AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN

        dummyOtherUser.chatRoom.accountStateInChatRoom = initialAccountState

        memberMutableListWrapper.updateAccountStateByAccountOID(
            firstDummyOid,
            newAccountState
        )

        val mapResult = memberMutableListWrapper.getFromMap(firstDummyOid)

        assertNotEquals(mapResult, null)
        assertEquals(mapResult?.chatRoom?.accountStateInChatRoom, newAccountState)
    }

}
