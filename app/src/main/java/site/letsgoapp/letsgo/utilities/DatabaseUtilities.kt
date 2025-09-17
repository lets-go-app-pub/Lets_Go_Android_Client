package site.letsgoapp.letsgo.utilities

data class PictureInfo(
    val picturePath: String,
    val indexOfPictureForUser: Int,
    val timestampPictureLastUpdatedOnServer: Long
)

//the first member of the pair is the file path the second is the info for the picture
fun convertPicturesListToString(pictureInfo: MutableList<PictureInfo>): String {

    var returnString = ""
    for (i in pictureInfo.indices) {

        val pictureString =
            pictureInfo[i].picturePath + PICTURE_ELEMENT_FIELDS_TO_STRING_SEPARATOR + pictureInfo[i].indexOfPictureForUser + PICTURE_ELEMENT_FIELDS_TO_STRING_SEPARATOR + pictureInfo[i].timestampPictureLastUpdatedOnServer

        returnString += if (i == pictureInfo.size - 1) {
            pictureString
        } else {
            pictureString + MATCH_ARRAY_TO_STRING_SEPARATOR
        }
    }

    return returnString
}

fun convertPicturesStringToList(filePaths: String?): MutableList<PictureInfo> {

    filePaths?.let {
        if (it.isNotEmpty()) {
            val separatorRegex = Regex(MATCH_ARRAY_TO_STRING_SEPARATOR)
            val pictures = separatorRegex.split(it)

            val returnList = mutableListOf<PictureInfo>()
            for (p in pictures) {

                val separatorFieldsRegex = Regex(PICTURE_ELEMENT_FIELDS_TO_STRING_SEPARATOR)
                val fields = separatorFieldsRegex.split(p)

                if (fields.size == 3) {
                    returnList.add(
                        PictureInfo(
                            fields[0],
                            fields[1].toInt(),
                            fields[2].toLong()
                        )
                    )
                }
            }

            return returnList
        }
    }

    return mutableListOf()
}