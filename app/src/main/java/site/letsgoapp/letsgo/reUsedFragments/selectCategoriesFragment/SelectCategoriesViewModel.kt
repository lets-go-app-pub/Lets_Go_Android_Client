package site.letsgoapp.letsgo.reUsedFragments.selectCategoriesFragment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import categorytimeframe.CategoryTimeFrame
import kotlinx.coroutines.*
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.SelectCategoriesRepository
import site.letsgoapp.letsgo.utilities.*

class SelectCategoriesViewModel(
    private val repository: SelectCategoriesRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private var errorLambda: ((GrpcFunctionErrorStatusEnum) -> Unit)? = null
    private var failedToSet: ((String) -> Unit)? = null

    private val thisSelectCategoriesViewModelInstanceId = generateViewModelInstanceId()

    private fun <T> keyStringObserverFactory(block: (T) -> Unit): KeyStringObserver<T> {
        return KeyStringObserver(block, thisSelectCategoriesViewModelInstanceId)
    }

    fun setupErrorFunction(_errorLambda: (GrpcFunctionErrorStatusEnum) -> Unit) {
        errorLambda = _errorLambda
    }

    fun setupFailedToSetFunction(_failedToSet: (String) -> Unit) {
        failedToSet = _failedToSet
    }

    private val _returnUserSelectedCategoriesAndAgeFromDatabase: MutableLiveData<EventWrapperWithKeyString<ReturnUserSelectedCategoriesAndAgeDataHolder>> =
        MutableLiveData()
    val returnUserSelectedCategoriesAndAgeFromDatabase: LiveData<EventWrapperWithKeyString<ReturnUserSelectedCategoriesAndAgeDataHolder>> =
        _returnUserSelectedCategoriesAndAgeFromDatabase
    private var returnUserSelectedCategoriesFromDatabaseObserver =
        keyStringObserverFactory<DataHolderWrapper<ReturnUserSelectedCategoriesAndAgeDataHolder>> {
            _returnUserSelectedCategoriesAndAgeFromDatabase.value = EventWrapperWithKeyString(
                it.dataHolder,
                it.fragmentInstanceId
            )
        }

    private val _setCategoriesReturnValue: MutableLiveData<EventWrapperWithKeyString<SetFieldsReturnValues>> =
        MutableLiveData()
    val setCategoriesReturnValue: LiveData<EventWrapperWithKeyString<SetFieldsReturnValues>> =
        _setCategoriesReturnValue

    private val _neededVeriInfo: MutableLiveData<EventWrapperWithKeyString<NeededVeriInfoDataHolder>> =
        MutableLiveData()
    val neededVeriInfo: LiveData<EventWrapperWithKeyString<NeededVeriInfoDataHolder>> =
        _neededVeriInfo
    private var neededVeriInfoObserver =
        keyStringObserverFactory<DataHolderWrapper<NeededVeriInfoDataHolder>> {
            _neededVeriInfo.value =
                EventWrapperWithKeyString(
                    it.dataHolder,
                    it.fragmentInstanceId
                )
        }

    fun runNeedVeriInfo(
        callingFragmentInstanceID: String,
        userLongitude: Double,
        userLatitude: Double
    ) {
        CoroutineScope(ioDispatcher).launch {
            repository.runNeedVeriInfo(
                callingFragmentInstanceID,
                userLongitude,
                userLatitude,
                thisSelectCategoriesViewModelInstanceId
            )
        }
    }

    //sets the categories to the server
    fun setCategories(
        categoriesList: ArrayList<CategoryTimeFrame.CategoryActivityMessage>,
        callingFragmentInstanceID: String,
        clearMatchesList: () -> Unit,
        functionToUpdateAlgorithmParametersCompleted: (Boolean) -> Unit,
        sharedApplicationViewModelInstanceId: String
    ) //these 2 functions are used by the application to clear matches in a different view model
    {
        CoroutineScope(ioDispatcher).launch {
            clearMatchesList()
            val returnVal = repository.setCategories(
                categoriesList,
                sharedApplicationViewModelInstanceId
            )

            //functionToUpdateAlgorithmParametersCompleted() will be set to {} when called from the login activity
            functionToUpdateAlgorithmParametersCompleted(returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS)

            //errorLambda can access the views so must be called using Main
            withContext(Dispatchers.Main) {
                if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                    _setCategoriesReturnValue.value = EventWrapperWithKeyString(
                        returnVal,
                        callingFragmentInstanceID
                    )
                } else if (returnVal.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {
                    errorLambda?.let { it(returnVal.errorStatus) }
                    failedToSet?.let { it(GlobalValues.applicationContext.getString(R.string.activities)) }
                }
            }
        }
    }

    fun getCategoriesAndAgeFromDatabase(callingFragmentInstanceID: String) {
        CoroutineScope(ioDispatcher).launch {
            repository.getCategoriesAndAgeFromDatabase(
                callingFragmentInstanceID,
                thisSelectCategoriesViewModelInstanceId
            )
        }
    }

    fun clearCategories() {
        CoroutineScope(ioDispatcher).launch {
            repository.clearCategories()
        }
    }

    init {

        repository.neededVeriInfo.observeForever(neededVeriInfoObserver) //needsVeriInfo return value
        //repository.setCategoriesReturnValue.observeForever(setCategoriesReturnValueObserver) //timestamp from set field clients

        repository.returnUserSelectedCategoriesAndAgeFromDatabase.observeForever(
            returnUserSelectedCategoriesFromDatabaseObserver
        ) //categories and user age
    }

    override fun onCleared() {

        repository.neededVeriInfo.removeObserver(neededVeriInfoObserver) //needsVeriInfo return value
        //repository.setCategoriesReturnValue.removeObserver(setCategoriesReturnValueObserver) //timestamp from set field clients

        repository.returnUserSelectedCategoriesAndAgeFromDatabase.removeObserver(
            returnUserSelectedCategoriesFromDatabaseObserver
        ) //categories and user age

        errorLambda = null
        failedToSet = null

        super.onCleared()
    }

}

/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 */
class SelectCategoriesViewModelFactory(
    private val repository: SelectCategoriesRepository,
    private val ioDispatcher: CoroutineDispatcher = ServiceLocator.globalIODispatcher
) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SelectCategoriesViewModel::class.java)) {
            return SelectCategoriesViewModel(repository, ioDispatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}