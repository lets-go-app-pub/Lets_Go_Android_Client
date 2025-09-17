package site.letsgoapp.letsgo.reUsedFragments.selectPicturesFragment

import androidx.lifecycle.*
import kotlinx.coroutines.*
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDataEntity
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.SelectPicturesRepository
import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface
import site.letsgoapp.letsgo.utilities.*

class SelectPicturesViewModel(
    private val repository: SelectPicturesRepository,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val thisSharedPicturesViewModelInstanceId = generateViewModelInstanceId()

    private fun <T> keyStringObserverFactory(block: (T) -> Unit): KeyStringObserver<T> {
        return KeyStringObserver(block, thisSharedPicturesViewModelInstanceId)
    }

    private val _returnAllPictures: MutableLiveData<EventWrapperWithKeyString<ReturnAllPicturesDataValues>> =
        MutableLiveData()
    val returnAllPictures: LiveData<EventWrapperWithKeyString<ReturnAllPicturesDataValues>> =
        _returnAllPictures
    private var returnAllPicturesObserver =
        keyStringObserverFactory<DataHolderWrapper<ReturnAllPicturesDataValues>> { result ->
            _returnAllPictures.value =
                EventWrapperWithKeyString(
                    result.dataHolder,
                    result.fragmentInstanceId
                )
        }

    private val _returnSinglePicture: MutableLiveData<EventWrapperWithKeyString<ReturnSinglePictureValues>> =
        MutableLiveData()
    val returnSinglePicture: LiveData<EventWrapperWithKeyString<ReturnSinglePictureValues>> =
        _returnSinglePicture
    private var returnSinglePictureObserver =
        keyStringObserverFactory<DataHolderWrapper<ReturnSinglePictureValues>> { result ->
            _returnSinglePicture.value =
                EventWrapperWithKeyString(
                    result.dataHolder,
                    result.fragmentInstanceId
                )
        }

    private val _setPictureReturnValue: MutableLiveData<EventWrapperWithKeyString<SetPictureReturnDataHolder>> =
        MutableLiveData()
    val setPictureReturnValue: LiveData<EventWrapperWithKeyString<SetPictureReturnDataHolder>> =
        _setPictureReturnValue
    private val setPictureReturnValueObserver =
        keyStringObserverFactory<DataHolderWrapper<SetPictureReturnDataHolder>> { result ->
            _setPictureReturnValue.value =
                EventWrapperWithKeyString(
                    result.dataHolder,
                    result.fragmentInstanceId
                )
        }

    fun retrievePicturesFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.retrieveAllPictures(callingFragmentInstanceID, thisSharedPicturesViewModelInstanceId)
        }
    }

    suspend fun retrieveSinglePicture(
        index: Int,
        callingFragmentInstanceID: String
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.retrieveSinglePicture(index, callingFragmentInstanceID, thisSharedPicturesViewModelInstanceId)
        }
    }

    //send picture to server
    fun sendPicture(
        fileInBytes: ByteArray,
        thumbnailByteArray: ByteArray,
        pictureIndex: Int,
        firstPictureIndex: Boolean,
        fragmentInstanceID: String,
        sharedApplicationOrLoginViewModelInstanceId: String,
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.setPicture(
                fileInBytes,
                thumbnailByteArray,
                pictureIndex,
                firstPictureIndex,
                fragmentInstanceID,
                thisSharedPicturesViewModelInstanceId,
                sharedApplicationOrLoginViewModelInstanceId,
                deleteFileInterface
            )
        }
    }

    fun deletePictureOnClient(pictureIndex: Int, picturePath: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.deletePictureOnClient(pictureIndex, picturePath)
        }
    }

    init {
        repository.returnAllPictures.observeForever(returnAllPicturesObserver) //returns a list of all pictures from database
        repository.setPictureReturnValue.observeForever(setPictureReturnValueObserver)
        repository.returnSinglePicture.observeForever(returnSinglePictureObserver)
    }

    override fun onCleared() {
        repository.returnAllPictures.removeObserver(returnAllPicturesObserver)
        repository.setPictureReturnValue.removeObserver(setPictureReturnValueObserver)
        repository.returnSinglePicture.removeObserver(returnSinglePictureObserver)

        super.onCleared()
    }
}

/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 */
class SelectPicturesViewModelFactory(
    private val repository: SelectPicturesRepository,
    private val deleteFileInterface: StartDeleteFileInterface,
    private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SelectPicturesViewModel::class.java)) {
            return SelectPicturesViewModel(repository, deleteFileInterface, ioDispatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}