package site.letsgoapp.letsgo.databases

/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.databases.otherUsersDatabase.OtherUsersDatabase
import site.letsgoapp.letsgo.gRPC.proto_file_classes.LoginFunctionAccountTypeEnum
import site.letsgoapp.letsgo.utilities.GenderEnum
import java.io.IOException

*/

//TESTING_NOTE: when testing database queries, make sure to test them with no rows inside the database; some queries like SELECT *, MAX(x) will return null for each parameter instead of the whole entity

/*
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AccountInfoDatabaseDaoTest {

    //These are essentially arbitrary
    private val phoneNumber = "+17023033061"
    private val loginToken = "loginToken"
    private val timestamp = 10L
    private val newTimestamp = 15L
    private val email = "email@email.com"
    private val firstName = "firstName"
    private val gender = GenderEnum.GENDER_UNKNOWN
    private val genderOther = "~"
    private val birthYear = 2000
    private val birthMonth = 1
    private val birthDayOfMonth = 1

    // Executes each task synchronously using Architecture Components.
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: OtherUsersDatabase

    @Before
    fun createDb() {
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        db = Room.inMemoryDatabaseBuilder(getApplicationContext(), OtherUsersDatabase::class.java)
            // Allowing main thread queries, just for testing.
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAccount_getAccountInfoForErrors() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        val extractedAccount = db.accountInfoDatabaseDao.getAccountInfoForErrors()
        assertThat(account, `is`(extractedAccount))
    }

    @Test
    fun getAll_deleteAllButAccount() {

        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        var allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
        assertThat(allAccounts[0], `is`(account))

        val account2 = generateRoomAccount()
        account2!!.loginToken = "loginToken2"
        account2!!.phoneNumber = "deletePhoneNumber"
        db.accountInfoDatabaseDao.insertAccount(account2!!)

        allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(2))
        assertThat(allAccounts[0], `is`(account))
        assertThat(allAccounts[1], `is`(account2))

        db.accountInfoDatabaseDao.deleteAllButAccount(phoneNumber)

        allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
        assertThat(allAccounts[0], `is`(account))

        db.accountInfoDatabaseDao.clearTable()

        allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(0))

    }

    @Test
    fun clearTable() {

        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        val account2 = generateRoomAccount()
        account2!!.phoneNumber = "phoneNumber2"
        db.accountInfoDatabaseDao.insertAccount(account2!!)

        var allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(2))

        db.accountInfoDatabaseDao.clearTable()
        allAccounts = db.accountInfoDatabaseDao.getAll()

        assertThat(allAccounts.size, `is`(0))

    }

    @Test
    fun getPhoneNumber() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)
        val extractedNumber = db.accountInfoDatabaseDao.getPhoneNumber()

        assertThat(extractedNumber, `is`(phoneNumber))
    }

    @Test
    fun getAccountInfo() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        val extractedAccount = db.accountInfoDatabaseDao.getAccountInfo(phoneNumber)

        assertThat(extractedAccount, `is`(account))
    }

    @Test
    fun getAccountType_setAccountType() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setAccountType(LoginFunctionAccountTypeEnum.GOOGLE_ACCOUNT.getVal())

        val extractedValue = db.accountInfoDatabaseDao.getInitialLoginId()

        assertThat(
            LoginFunctionAccountTypeEnum.setVal(extractedValue!!), `is`(
                LoginFunctionAccountTypeEnum.GOOGLE_ACCOUNT))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getLoginToken_setLoginToken() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setLoginToken("newLoginToken")

        val extractedValue = db.accountInfoDatabaseDao.getLoginToken()

        assertThat(extractedValue, `is`("newLoginToken"))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getRequiresEmailAddressVerification_setRequiresEmailAddressVerification() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setRequiresEmailAddressVerification(true)

        val extractedValue = db.accountInfoDatabaseDao.getRequiresEmailAddressVerification()

        assertThat(extractedValue, `is`(true))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getEmail_setEmail() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setEmail("newEmail")

        val extractedValue = db.accountInfoDatabaseDao.getEmail()

        assertThat(extractedValue, `is`("newEmail"))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getEmailTimestamp_setEmailTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setEmailTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getEmailTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getFirstName_setFirstName() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setFirstName("newFirstName")

        val extractedValue = db.accountInfoDatabaseDao.getFirstName()

        assertThat(extractedValue, `is`("newFirstName"))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getFirstNameTimestamp_setFirstNameTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setFirstNameTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getFirstNameTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getGender_setGender() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setGender(GenderEnum.GENDER_FEMALE.getVal())

        val extractedValue = db.accountInfoDatabaseDao.getGender()

        assertThat(GenderEnum.setVal(extractedValue!!), `is`(GenderEnum.GENDER_FEMALE))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getGenderOtherName_setGenderOtherName() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setGenderOtherName("newGenderOther")

        val extractedValue = db.accountInfoDatabaseDao.getGenderOtherName()

        assertThat(extractedValue, `is`("newGenderOther"))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getGenderTimestamp_setGenderTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setGenderTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getGenderTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getBirthYear_setBirthYear() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setBirthYear(1986)

        val extractedValue = db.accountInfoDatabaseDao.getBirthYear()

        assertThat(extractedValue, `is`(1986))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getBirthMonth_setBirthMonth() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setBirthYear(10)

        val extractedValue = db.accountInfoDatabaseDao.getBirthYear()

        assertThat(extractedValue, `is`(10))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getBirthDayOfMonth_setBirthDayOfMonth() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setBirthDayOfMonth(23)

        val extractedValue = db.accountInfoDatabaseDao.getBirthDayOfMonth()

        assertThat(extractedValue, `is`(23))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getBirthdayTimestamp_setBirthdayTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setBirthdayTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getBirthdayTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getPictures_setPictures() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setPictures(5)

        val extractedValue = db.accountInfoDatabaseDao.getPictures()

        assertThat(extractedValue, `is`(5))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getPicturesTimestamp_setPicturesTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setPicturesTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getPicturesTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getCategories_setCategories() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setCategories(5)

        val extractedValue = db.accountInfoDatabaseDao.getCategories()

        assertThat(extractedValue, `is`(5))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    @Test
    fun getCategoriesTimestamp_setCategoriesTimestamp() {
        val account = generateRoomAccount()
        db.accountInfoDatabaseDao.insertAccount(account!!)

        db.accountInfoDatabaseDao.setCategoriesTimestamp(newTimestamp)

        val extractedValue = db.accountInfoDatabaseDao.getCategoriesTimestamp()

        assertThat(extractedValue, `is`(newTimestamp))

        val allAccounts = db.accountInfoDatabaseDao.getAll()
        assertThat(allAccounts.size, `is`(1))
    }

    //creates and stores an account inside the fake room database
    private fun generateRoomAccount() : AccountInfoDataEntity? {
        return AccountInfoDataEntity(
            phoneNumber, loginToken, LoginFunctionAccountTypeEnum.PHONE_ACCOUNT.getVal(),
            false, email, timestamp, firstName,
            timestamp, gender.getVal(), genderOther, timestamp,
            birthYear, birthMonth, birthDayOfMonth,
            timestamp, -1, timestamp, -1, timestamp
        )
    }
}*/

