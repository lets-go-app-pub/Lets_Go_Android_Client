package site.letsgoapp.letsgo.repositories

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.utilities.*

class SelectPicturesRepository(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val _returnAllPictures: KeyStringMutableLiveData<DataHolderWrapper<ReturnAllPicturesDataValues>> =
        KeyStringMutableLiveData()
    val returnAllPictures: KeyStringLiveData<DataHolderWrapper<ReturnAllPicturesDataValues>> =
        _returnAllPictures

    private val _returnSinglePicture: KeyStringMutableLiveData<DataHolderWrapper<ReturnSinglePictureValues>> =
        KeyStringMutableLiveData()
    val returnSinglePicture: KeyStringLiveData<DataHolderWrapper<ReturnSinglePictureValues>> =
        _returnSinglePicture

    private val _setFirstPictureReturnValue: KeyStringMutableLiveData<AccountPictureDataEntity> =
        KeyStringMutableLiveData()
    val setFirstPictureReturnValue: KeyStringLiveData<AccountPictureDataEntity> =
        _setFirstPictureReturnValue

    private val _setPictureReturnValue: KeyStringMutableLiveData<DataHolderWrapper<SetPictureReturnDataHolder>> =
        KeyStringMutableLiveData()
    val setPictureReturnValue: KeyStringLiveData<DataHolderWrapper<SetPictureReturnDataHolder>> =
        _setPictureReturnValue

    private val _returnGrpcFunctionErrorStatusEnumToActivity: KeyStringMutableLiveData<GrpcFunctionErrorStatusEnum> =
        KeyStringMutableLiveData()
    val returnGrpcFunctionErrorStatusEnumToActivity: KeyStringLiveData<GrpcFunctionErrorStatusEnum> =
        _returnGrpcFunctionErrorStatusEnumToActivity

    suspend fun retrieveAllPictures(
        callingFragmentInstanceID: String,
        sharedPicturesViewModelInstanceId: String
    ) {
        withContext(ioDispatcher) {
            val allPictures = accountPicturesDataSource.getAllPictures()
            withContext(Dispatchers.Main) {
                _returnAllPictures.setValue(
                    DataHolderWrapper(
                        ReturnAllPicturesDataValues(
                            allPictures
                        ),
                        callingFragmentInstanceID
                    ),
                    sharedPicturesViewModelInstanceId
                )
            }
        }
    }

    suspend fun retrieveSinglePicture(
        index: Int,
        callingFragmentInstanceID: String,
        sharedPicturesViewModelInstanceId: String
    ) = withContext(ioDispatcher) {
        val picture = accountPicturesDataSource.getSinglePicture(index)
        withContext(Dispatchers.Main) {
            _returnSinglePicture.setValue(
                DataHolderWrapper(
                    ReturnSinglePictureValues(
                        index,
                        picture,
                    ),
                    callingFragmentInstanceID
                ),
                sharedPicturesViewModelInstanceId
            )
        }
    }

    //sets the picture on the server
    suspend fun setPicture(
        fileInBytes: ByteArray,
        thumbnailByteArray: ByteArray,
        pictureIndex: Int,
        firstPictureIndex: Boolean,
        fragmentInstanceID: String,
        sharedPicturesViewModelInstanceId: String,
        sharedApplicationOrLoginViewModelInstanceId: String,
        deleteFileInterface: StartDeleteFileInterface,
    ) = withContext(ioDispatcher) {

        val returnVal =
            if (GlobalValues.setPicturesBools[pictureIndex].compareAndSet(false, true)) {

                val ret = runSetPictureRPC(
                    applicationContext,
                    accountInfoDataSource,
                    accountPicturesDataSource,
                    clientsIntermediate,
                    errorHandling,
                    deleteFileInterface,
                    ioDispatcher,
                    fileInBytes,
                    thumbnailByteArray,
                    pictureIndex
                )
                GlobalValues.setPicturesBools[pictureIndex].set(false)
                ret
            } else {
                return@withContext
            }

        //return first picture to sharedApplicationViewModel
        if (firstPictureIndex && returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
            accountPicturesDataSource.getFirstPicture()?.let {
                withContext(Dispatchers.Main) {
                    _setFirstPictureReturnValue.setValue(
                        it,
                        sharedApplicationOrLoginViewModelInstanceId
                    )
                }
            }
        }

        withContext(Dispatchers.Main) {

            //must be handled by fragment even if an error exists
            _setPictureReturnValue.setValue(
                DataHolderWrapper(
                    returnVal,
                    fragmentInstanceID
                ),
                sharedPicturesViewModelInstanceId
            )

            if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                _returnGrpcFunctionErrorStatusEnumToActivity.setValue(
                    returnVal.errorStatus,
                    sharedApplicationOrLoginViewModelInstanceId
                )
            }
        }
    }

    suspend fun deletePictureOnClient(pictureIndex: Int, picturePath: String) {
        withContext(ioDispatcher) {

            //set default values for picture, doing it this way so if default values are changed they will adjust here as well
            val accountPictureDataEntity = AccountPictureDataEntity(pictureIndex)
            accountPicturesDataSource.insertSinglePicture(
                accountPictureDataEntity.pictureIndex,
                accountPictureDataEntity.picturePath,
                accountPictureDataEntity.pictureSize,
                accountPictureDataEntity.pictureTimestamp
            )

            //delete the picture
            deleteFileInterface.sendFileToWorkManager(picturePath)
        }
    }

}