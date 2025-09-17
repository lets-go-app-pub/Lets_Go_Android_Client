package site.letsgoapp.letsgo.testUtilities


fun generateRandomString(charPool: String, length: Int): String {
    return (1..length)
        .map { kotlin.random.Random.nextInt(0, charPool.length) }
        .map( charPool::get)
        .joinToString("");
}

fun generateRandomChatRoomIdForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    val charPool = "0123456789abcdefghijklmnopqrstuvwxyz"

    return generateRandomString(
        charPool,
        8
    )
}

fun generateRandomOidForTesting(): String {
    //Duplicated it to make selecting a number more fair.
    val hexCharPool = "01234567890abcdef" +
            "01234567890ABCDEF"

    return generateRandomString(
        hexCharPool,
        24
    )
}