package site.letsgoapp.letsgo.testingUtility

import account_state.AccountState
import android.content.Context
import android.media.ThumbnailUtils
import android.util.Log
import categorytimeframe.CategoryTimeFrame.CategoryActivityMessage
import categorytimeframe.CategoryTimeFrame.CategoryTimeFrameMessage
import com.google.protobuf.ByteString
import findmatches.FindMatches
import member_shared_info.MemberSharedInfoMessageOuterClass
import requestmessages.RequestMessages
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDataEntity
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.convertBlockedAccountsSetToString
import site.letsgoapp.letsgo.databases.otherUsersDatabase.chatRooms.ChatRoomDataClass
import site.letsgoapp.letsgo.databases.otherUsersDatabase.otherUsers.*
import site.letsgoapp.letsgo.globalAccess.CategoriesAndActivities
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment.SelectPicturesFragment
import site.letsgoapp.letsgo.testingUtility.fakes.fakeClientSourceIntermediate.FakeClientSourceIntermediate
import site.letsgoapp.letsgo.utilities.*
import java.util.*

const val ONE_YEAR_IN_MS = 365L * 24L * 60L * 60L * 1000L

fun generateRandomString(
    length: Int,
    charPool: String = "0123456789abcdefghijklmnopqrstuvwxyz"
): String {
    return (1..length).map { kotlin.random.Random.nextInt(0, charPool.length) }.map(charPool::get)
        .joinToString("")
}

fun generateTextMessageString(): String {
    return generateRandomString(
        (1 .. GlobalValues.server_imported_values.maximumNumberAllowedBytesTextMessage).random(),
        "abcdefghijklmnopqrstuvwxyz1234567890,./;'[]~!@#$%^&*()_+\\|ABCDEFGHIJKLMNOPQRSTUVWXYZ{}:<>? \n\t"
    ).trimEnd()
}

fun generateRandomChatRoomIdForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    return generateRandomString(
        8
    )
}

fun generateRandomChatRoomPasswordForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    return generateRandomString(
        3
    )
}

fun generateRandomOidForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    val hexCharPool = "01234567890abcdef" + "01234567890ABCDEF"

    return generateRandomString(
        24, hexCharPool
    )
}

fun generateRandomEmailForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    val hexCharPool = "01234567890abcdef" + "01234567890ABCDEF"
    val minNumberDigits = 10
    val maxNumberDigits = 10

    return generateRandomString((minNumberDigits..maxNumberDigits).random(), hexCharPool) +
            "@" + generateRandomString((minNumberDigits..maxNumberDigits).random(), hexCharPool) +
            "." + generateRandomString(
        (minNumberDigits..maxNumberDigits).random(), hexCharPool
    )
}

fun generateRandomPhoneNumberForTesting(): String {
    val hexCharPool = "0123456789"

    var randomPhoneNumber = "+11"

    //First digit of area code cannot be 0 or 1.
    while (randomPhoneNumber[2] == '0' || randomPhoneNumber[2] == '1') {
        randomPhoneNumber = "+1" + generateRandomString(
            10, hexCharPool
        )
    }

    return randomPhoneNumber
}

fun generateRandomVerificationCodeForTesting(length: Int = GlobalValues.verificationCodeNumberOfDigits): String {
    val hexCharPool = "0123456789"

    return generateRandomString(
        length, hexCharPool
    )
}

fun generateRandomFirstNameForTesting(): String {
    val hexCharPool = "abcdefghijklmnopqrstuvwxyz"

    return generateRandomString(
        (2..GlobalValues.server_imported_values.maximumNumberAllowedBytesFirstName).random(),
        hexCharPool
    ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun generateRandomGenderForTesting(): String {
    return when ((0..1).random()) {
        0 -> {
            GlobalValues.MALE_GENDER_VALUE
        }
        else -> {
            GlobalValues.FEMALE_GENDER_VALUE
        }
    }
}

fun generateRandomUserBioForTesting(): String {
    val hexCharPool =
        "abcdefghijklmnopqrstuvwxyz1234567890,./;'[]~!@#$%^&*()_+\\|ABCDEFGHIJKLMNOPQRSTUVWXYZ{}:<>? \n\t"

    return generateRandomString(
        (0..GlobalValues.server_imported_values.maximumNumberAllowedBytesUserBio).random(),
        hexCharPool
    ).trimEnd()
}

fun generateRandomUserCityForTesting(): String {
    val hexCharPool = "abcdefghijklmnopqrstuvwxyz "

    val generatedCityName = generateRandomString(
        (0..GlobalValues.server_imported_values.maximumNumberAllowedBytes).random(), hexCharPool
    ).trim()

    var formattedString = ""
    var previousChar = ' '
    for (c in generatedCityName) {
        formattedString += if (previousChar == ' ') {
            c.uppercaseChar()
        } else {
            c
        }
        previousChar = c
    }

    return formattedString
}

fun generateRandomGenderRange(): MutableList<String> {
    val genderRange = mutableListOf<String>()

    when((0..2).random()) {
        0 -> {
            genderRange.add(
                GlobalValues.MALE_GENDER_VALUE
            )
        }
        1 -> {
            genderRange.add(
                GlobalValues.FEMALE_GENDER_VALUE
            )
        }
        else -> {
            genderRange.add(
                GlobalValues.MALE_GENDER_VALUE
            )
            genderRange.add(
                GlobalValues.FEMALE_GENDER_VALUE
            )
        }
    }

    return genderRange
}

fun generateRandomBlockedAccountsSet(): MutableSet<String> {
    val blockedAccounts = mutableSetOf<String>()
    val numElements = (0..20).random()

    for (i in 0 until numElements) {
        blockedAccounts.add(
            generateRandomOidForTesting()
        )
    }

    return blockedAccounts
}

fun generateRandomCategoriesForTesting(): MutableList<CategoryActivityMessage> {

    val categoriesIndexes = mutableSetOf<Int>()
    val categories = mutableListOf<CategoryActivityMessage>()

    //guarantee at least one value
    categoriesIndexes.add(1)

    if (CategoriesAndActivities.allActivities.size > 2) {
        val numElementsToAdd =
            (0 until GlobalValues.server_imported_values.numberActivitiesStoredPerAccount).random()
        for (i in 0 until numElementsToAdd) {
            val currentSize = categoriesIndexes.size
            while (categoriesIndexes.size == currentSize) {
                categoriesIndexes.add(
                    (2 until CategoriesAndActivities.allActivities.size).random()
                )
            }
        }
    }

    val currentTimeInMinutes = System.currentTimeMillis() / (60 * 1000)

    for (index in categoriesIndexes) {
        val timeFrameMessages = mutableListOf<CategoryTimeFrameMessage>()

        val numTimeFrames =
            (0 until GlobalValues.server_imported_values.numberTimeFramesStoredPerAccount).random()
        var timeFrameStartInMinutes = currentTimeInMinutes
        val timeFrameStopInMinutes =
            currentTimeInMinutes + GlobalValues.server_imported_values.timeAvailableToSelectTimeFrames / (60 * 1000)

        for (i in 0 until numTimeFrames) {
            //NOTE: Using minutes because they are the smallest unit a user can choose.
            val start = (timeFrameStartInMinutes + 1 until timeFrameStopInMinutes).random()
            val stop = (start..timeFrameStopInMinutes).random()

            timeFrameStartInMinutes = stop

            timeFrameMessages.add(
                CategoryTimeFrameMessage.newBuilder()
                    .setStartTimeFrame(start * 60 * 1000)
                    .setStopTimeFrame(stop * 60 * 1000)
                    .build()
            )

            if (timeFrameStopInMinutes <= (stop - 1)
                || timeFrameStartInMinutes + 1 >= timeFrameStopInMinutes
            ) {
                break
            }
        }

        categories.add(
            CategoryActivityMessage.newBuilder()
                .setActivityIndex(index)
                .addAllTimeFrameArray(
                    timeFrameMessages
                )
                .build()
        )
    }

    return categories
}

fun generateRandomBirthdayForTesting(): Calendar {
    val birthdayCalendar = Calendar.getInstance()

    val currentYear = birthdayCalendar.get(Calendar.YEAR)

    val lowestAllowedAge = GlobalValues.server_imported_values.lowestAllowedAge
    val highestAllowedAge = GlobalValues.server_imported_values.highestAllowedAge

    var randomDay: Int
    var randomMonth: Int
    var randomYear: Int

    do {
        randomDay = (1..28).random()
        randomMonth = (0..11).random() //MONTH is a value from 0 to 11 for Calendar
        randomYear =
            (currentYear - highestAllowedAge..currentYear - lowestAllowedAge).random()

        val age = calcPersonAgeNoError(
            randomYear,
            randomMonth + 1,
            randomDay
        ) {
            val calendar = Calendar.getInstance()
            calendar
        }

    } while (age < lowestAllowedAge
        || highestAllowedAge < age
    )

    birthdayCalendar.set(
        randomYear, randomMonth, randomDay
    )

    return birthdayCalendar
}

fun generateRandomBirthdayStringForTesting(birthdayCalendar: Calendar): String {

    var birthdayString = ""

    //MONTH is a value from 0 to 11 for Calendar
    if (birthdayCalendar.get(Calendar.MONTH) < 9) {
        birthdayString += "0"
    }

    birthdayString += (birthdayCalendar.get(Calendar.MONTH) + 1).toString()
    birthdayString += "/"

    if (birthdayCalendar.get(Calendar.DAY_OF_MONTH) < 10) {
        birthdayString += "0"
    }

    birthdayString += birthdayCalendar.get(Calendar.DAY_OF_MONTH).toString()
    birthdayString += "/"
    birthdayString += birthdayCalendar.get(Calendar.YEAR).toString()

    return birthdayString
}

fun generateRandomPointValue(): Double {
    return -1000 + Math.random() * (100000 - -100000)
}

fun generateRandomOtherUsersMatched(): Boolean {
    return (0..1).random() == 1
}

fun generateRandomExpirationTimeInFuture(currentTimestamp: Long = System.currentTimeMillis()): Long {
    return currentTimestamp + (5 % 60 * 1000..60 * 60 * 1000).random()
}

fun generateRandomTimestampForTesting(): Long {
    return System.currentTimeMillis() - (0..ONE_YEAR_IN_MS).random()
}

fun generateMinAndMaxMatchableAges(
    userAge: Int,
    errorStore: StoreErrorsInterface
): Pair<Int, Int> {
    val userMatchableAgeRange = getMinAndMaxMatchableAges(
        userAge,
        errorStore
    )

    val min = (userMatchableAgeRange.minAge..userMatchableAgeRange.maxAge).random()
    val max = (min..userMatchableAgeRange.maxAge).random()

    return Pair(min, max)
}

//This is meant to set everything to random, anything that needs
// to be specifically set can be set on return.
fun generateRandomValidAccountInfoDataEntity(
    initialAccountCreation: Boolean,
    errorStore: StoreErrorsInterface
): AccountInfoDataEntity {

    val birthdayCalendar = generateRandomBirthdayForTesting()

    val userAge = calcPersonAgeNoError(
        birthdayCalendar.get(Calendar.YEAR),
        birthdayCalendar.get(Calendar.MONTH) + 1,
        birthdayCalendar.get(Calendar.DAY_OF_MONTH)
    ) {
        val calendar = Calendar.getInstance()
        calendar
    }

    val (userMinAgeRange, userMaxAgeRange) = generateMinAndMaxMatchableAges(
        userAge,
        errorStore
    )

    return if (initialAccountCreation) {
        val acct = AccountInfoDataEntity(
            generateRandomPhoneNumberForTesting(),
            generateRandomOidForTesting(),
            (1..3).random(),
            (0..1).random(),
            true,
            "~",
            -1,
            "~",
            -1,
            "~",
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            "",
            -1,
            "",
            "",
            "",
            -1,
            -1,
            30,
            -1,
            "",
            0
        )

        Log.i("print_accounts", "acct\n$acct")

        acct
    } else {
        val acct = AccountInfoDataEntity(
            generateRandomPhoneNumberForTesting(),
            generateRandomOidForTesting(),
            (1..3).random(),
            (0..1).random(),
            (0..1).random() == 1,
            generateRandomEmailForTesting(),
            generateRandomTimestampForTesting(),
            generateRandomFirstNameForTesting(),
            generateRandomTimestampForTesting(),
            when ((0..1).random()) {
                0 -> {
                    GlobalValues.MALE_GENDER_VALUE
                }
                else -> {
                    GlobalValues.FEMALE_GENDER_VALUE
                }
            },
            generateRandomTimestampForTesting(),
            birthdayCalendar.get(Calendar.YEAR),
            birthdayCalendar.get(Calendar.MONTH) + 1,
            birthdayCalendar.get(Calendar.DAY_OF_MONTH),
            userAge,
            generateRandomTimestampForTesting(),
            convertCategoryActivityMessageToString(generateRandomCategoriesForTesting()),
            generateRandomTimestampForTesting(),
            generateRandomUserBioForTesting(),
            generateRandomUserCityForTesting(),
            convertGenderRangeToString(generateRandomGenderRange()),
            userMinAgeRange,
            userMaxAgeRange,
            (GlobalValues.server_imported_values.minimumAllowedDistance..GlobalValues.server_imported_values.maximumAllowedDistance).random(),
            generateRandomTimestampForTesting(),
            convertBlockedAccountsSetToString(generateRandomBlockedAccountsSet()),
            (0..3).random()
        )
        Log.i("print_accounts", "acct\n$acct")

        acct
    }
}

fun generateRandomOtherUsersDataEntityNoPicture(
    thumbnailPath: String,
    thumbnailIndexNumber: Int,
    userOid: String,
    currentTimestamp: Long,
    picturesInfo: MutableList<PictureInfo>,
    objectsRequiringInfo: MutableSet<ObjectRequiringInfo>,
    chatRoomObjects: MutableMap<String, OtherUserChatRoomInfo>,
): OtherUsersDataEntity {
    val acct = OtherUsersDataEntity(
        userOid,
        thumbnailPath,
        thumbnailIndexNumber,
        currentTimestamp,
        convertObjectsRequiringInfoSetToString(
            objectsRequiringInfo
        ),
        convertChatRoomObjectsMapToString(
            chatRoomObjects
        ),
        GlobalValues.server_imported_values.minimumAllowedDistance + Math.random() * (GlobalValues.server_imported_values.maximumAllowedDistance - GlobalValues.server_imported_values.minimumAllowedDistance),
        currentTimestamp,
        currentTimestamp,
        convertPicturesListToString(
            picturesInfo
        ),
        currentTimestamp,
        generateRandomFirstNameForTesting(),
        (18..GlobalValues.server_imported_values.highestAllowedAge).random(),
        generateRandomGenderForTesting(),
        if ((0..1).random() == 1) generateRandomUserCityForTesting() else "",
        if ((0..1).random() == 1) generateRandomUserBioForTesting() else "",
        convertCategoryActivityMessageToString(
            generateRandomCategoriesForTesting()
        )
    )

    Log.i("print_accounts", "acct\n$acct")

    return acct
}

fun generateRandomOtherUsersDataEntityWithPicture(
    applicationContext: Context,
    userOid: String,
    fakeStoreErrors: StoreErrorsInterface,
    currentTimestamp: Long,
    objectsRequiringInfo: MutableSet<ObjectRequiringInfo> = mutableSetOf(),
    chatRoomObjects: MutableMap<String, OtherUserChatRoomInfo> = mutableMapOf()
): OtherUsersDataEntity {
    var thumbnailIndexNumber = -1
    var thumbnailPath = ""
    val picturesInfo = mutableListOf<PictureInfo>()

    val pictureBitmap = drawable2Bitmap(
        applicationContext.resources.getDrawable(
            R.drawable.picture_pexels_andrew_pt_733500,
            applicationContext.theme
        )
    )

    val pictureByteArray = bitmap2Bytes(
        pictureBitmap
    )

    //NOTE: It IS possible to have zero pictures added here, however this could actually
    // happen if a user has all of their picture deleted.
    for (i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
        if ((0..1).random() == 1) { //add picture

            val file = generateOtherUserPictureFile(
                userOid,
                i,
                currentTimestamp,
                applicationContext
            )

            writePictureToFile(
                file,
                userOid,
                i,
                ByteString.copyFrom(pictureByteArray),
                fakeStoreErrors
            )

            picturesInfo.add(
                PictureInfo(
                    file.absolutePath,
                    i,
                    currentTimestamp
                )
            )

            if (thumbnailIndexNumber == -1) {
                thumbnailIndexNumber = i

                GlobalValues.server_imported_values.pictureThumbnailMaximumCroppedSizePx

                val (thumbnailHeight, thumbnailWidth) = SelectPicturesFragment.calculateHeightAndWidthForBitmap(
                    GlobalValues.server_imported_values.pictureThumbnailMaximumCroppedSizePx,
                    pictureBitmap.height,
                    pictureBitmap.width
                )

                val thumbnailBitmap = ThumbnailUtils.extractThumbnail(
                    pictureBitmap,
                    thumbnailWidth,
                    thumbnailHeight
                )

                val thumbnailByteArray = bitmap2Bytes(thumbnailBitmap)

                val thumbnailFile = generateOtherUserThumbnailFile(
                    userOid,
                    currentTimestamp,
                    applicationContext
                )

                writeThumbnailToFile(
                    thumbnailFile,
                    userOid,
                    ByteString.copyFrom(thumbnailByteArray),
                    fakeStoreErrors
                )

                thumbnailPath = thumbnailFile.absolutePath
            }
        }
    }

    return generateRandomOtherUsersDataEntityNoPicture(
        thumbnailPath,
        thumbnailIndexNumber,
        userOid,
        currentTimestamp,
        picturesInfo,
        objectsRequiringInfo,
        chatRoomObjects,
    )
}

fun generateRandomNewChatRoom(): ChatRoomDataClass {
    val lastActivityTimeTimestamp = System.currentTimeMillis()

    return ChatRoomDataClass(
        generateRandomChatRoomIdForTesting(),
        generateRandomString((0..100).random()),
        generateRandomString((0..100).random()),
        notificationsEnabled = true,
        AccountState.AccountStateInChatRoom.ACCOUNT_STATE_IS_ADMIN,
        mutableListOf(),
        lastActivityTimeTimestamp,
        "",
        lastActivityTimeTimestamp, //setting this to be consistent with server
        lastActivityTimeTimestamp,
        lastActivityTimeTimestamp,
        lastActivityTimeTimestamp
    )
}

fun generateRandomThumbnail(
    applicationContext: Context
): ByteArray {

    val pictureBitmap = drawable2Bitmap(
        applicationContext.resources.getDrawable(
            R.drawable.picture_pexels_andrew_pt_733500,
            applicationContext.theme
        )
    )

    val (thumbnailHeight, thumbnailWidth) = SelectPicturesFragment.calculateHeightAndWidthForBitmap(
        GlobalValues.server_imported_values.pictureThumbnailMaximumCroppedSizePx,
        pictureBitmap.height,
        pictureBitmap.width
    )

    val thumbnailBitmap = ThumbnailUtils.extractThumbnail(
        pictureBitmap,
        thumbnailWidth,
        thumbnailHeight
    )

    return bitmap2Bytes(thumbnailBitmap)
}

fun generateRandomMemberSharedInfoMessageForMatch(
    applicationContext: Context,
    //This data entity is just used for saving info to the match below, specifics like the
    // thumbnail path do not actually matter.
    passedOtherUserDataEntity: OtherUsersDataEntity? = null
): MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage {

    val otherUserDataEntity =
        passedOtherUserDataEntity ?: generateRandomOtherUsersDataEntityNoPicture(
            "",
            0,
            generateRandomOidForTesting(),
            System.currentTimeMillis(),
            mutableListOf(),
            mutableSetOf(),
            mutableMapOf()
        )

    val pictureByteString =
        ByteString.copyFrom(
            drawable2Bytes(
                applicationContext.resources.getDrawable(
                    R.drawable.lets_go_logo,
                    applicationContext.theme
                )
            )
        )

    var thumbnailIndex = -1

    val pictureMessagesList = mutableListOf<RequestMessages.PictureMessage>()

    //NOTE: It IS possible to have zero pictures added here, however this could actually
    // happen if a user has all of their picture deleted.
    for (j in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
        if ((0..1).random() == 1) { //add picture
            thumbnailIndex = j

            pictureMessagesList.add(
                RequestMessages.PictureMessage.newBuilder()
                    .setFileInBytes(pictureByteString)
                    .setFileSize(pictureByteString.size())
                    .setIndexNumber(j)
                    .setTimestampPictureLastUpdated(System.currentTimeMillis())
                    .build()
            )
        }
    }

    return MemberSharedInfoMessageOuterClass.MemberSharedInfoMessage.newBuilder().apply {
        accountOid = otherUserDataEntity.accountOID
        accountName = otherUserDataEntity.name

        if (thumbnailIndex != -1) {
            accountThumbnail = pictureByteString
            accountThumbnailSize = pictureByteString.size()
            accountThumbnailIndex = thumbnailIndex
            accountThumbnailTimestamp = System.currentTimeMillis()
        } else {
            accountThumbnail = ByteString.copyFrom("".toByteArray())
            accountThumbnailSize = 0
            accountThumbnailIndex = 0
            accountThumbnailTimestamp = System.currentTimeMillis()
        }

        addAllPicture(pictureMessagesList)

        picturesCheckedForUpdates = true

        age = otherUserDataEntity.age
        gender = otherUserDataEntity.gender
        cityName = otherUserDataEntity.cityName
        bio = otherUserDataEntity.bio
        distance = otherUserDataEntity.distance

        addAllActivities(
            convertStringToCategoryActivityMessageAndTrimTimes(
                otherUserDataEntity.activities
            ).second
        )

        timestampOtherUserInfoUpdated = System.currentTimeMillis()
        currentTimestamp = System.currentTimeMillis()
    }.build()
}

fun generateRandomFindMatchesResponse(
    applicationContext: Context,
    otherUserDataEntity: OtherUsersDataEntity? = null
): FindMatches.FindMatchesResponse {
    return FindMatches.FindMatchesResponse.newBuilder()
        .setSingleMatch(
            FindMatches.SingleMatchMessage.newBuilder()
                .setMemberInfo(
                    generateRandomMemberSharedInfoMessageForMatch(
                        applicationContext,
                        otherUserDataEntity
                    )
                )
                .setPointValue(generateRandomPointValue())
                .setExpirationTime(generateRandomExpirationTimeInFuture())
                .setOtherUserMatch(generateRandomOtherUsersMatched())
                .setSwipesRemaining(40)
                .setSwipesTimeBeforeReset(FakeClientSourceIntermediate.TIME_BETWEEN_SWIPES_UPDATED - System.currentTimeMillis() % FakeClientSourceIntermediate.TIME_BETWEEN_SWIPES_UPDATED)
                .setTimestamp(System.currentTimeMillis())
                .build()
        )
        .build()
}

//NOTE: Because the other user is not stored inside the database yet the OtherUsersDataEntity
// returned is incomplete. Specifically the pictures string is not set up.
fun generateRandomMatchOnServer(
    applicationContext: Context
) : Pair<OtherUsersDataEntity, FindMatches.FindMatchesResponse> {
    val matchOtherUserDataEntity = generateRandomOtherUsersDataEntityNoPicture(
        "",
        0,
        generateRandomOidForTesting(),
        System.currentTimeMillis(),
        mutableListOf(),
        mutableSetOf(),
        mutableMapOf()
    )

    val matchResponse = generateRandomFindMatchesResponse(
        applicationContext,
        matchOtherUserDataEntity
    )

    return Pair(matchOtherUserDataEntity, matchResponse)
}
