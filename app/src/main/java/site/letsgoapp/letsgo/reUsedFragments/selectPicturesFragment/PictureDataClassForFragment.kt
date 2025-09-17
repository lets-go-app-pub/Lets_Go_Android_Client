package site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment

import android.widget.ImageView
import android.widget.ProgressBar
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity

//this enum initially had more than 2 values, just leaving it here instead of a bool because it was already implemented
enum class ImageStatusEnum {
    SHOWING_LOADING, //imageView is showing progress bar
    SHOWING_PICTURE, //imageView is showing an image
}

data class PictureDataClassForFragment(
    val imageView: ImageView,
    val progressBar: ProgressBar,
    var tempFilePath: String = "",
    var imageStatus: ImageStatusEnum = ImageStatusEnum.SHOWING_PICTURE,
    var userPictureAtThisIndex: Boolean = false,
    var userPictureEntity: AccountPictureDataEntity = AccountPictureDataEntity(),
    var compressedByteArray: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PictureDataClassForFragment

        if (compressedByteArray != null) {
            if (other.compressedByteArray == null) return false
            if (!compressedByteArray!!.contentEquals(other.compressedByteArray!!)) return false
        } else if (other.compressedByteArray != null) return false

        return true
    }

    override fun hashCode(): Int {
        return compressedByteArray?.contentHashCode() ?: 0
    }
}