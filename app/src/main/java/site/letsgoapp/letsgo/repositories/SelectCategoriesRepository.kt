package site.letsgoapp.letsgo.repositories

import android.content.Context
import categorytimeframe.CategoryTimeFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountInfo.AccountInfoDaoIntermediateInterface
import site.letsgoapp.letsgo.databases.accountInfoDatabase.accountPicture.AccountPictureDaoIntermediateInterface
import site.letsgoapp.letsgo.gRPC.ClientsInterface
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.*

class SelectCategoriesRepository(
    private val applicationContext: Context,
    private val accountInfoDataSource: AccountInfoDaoIntermediateInterface,
    private val accountPicturesDataSource: AccountPictureDaoIntermediateInterface,
    private val clientsIntermediate: ClientsInterface,
    private val errorHandling: StoreErrorsInterface,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val _returnUserSelectedCategoriesAndAgeFromDatabase: KeyStringMutableLiveData<DataHolderWrapper<ReturnUserSelectedCategoriesAndAgeDataHolder>> =
        KeyStringMutableLiveData()
    val returnUserSelectedCategoriesAndAgeFromDatabase: KeyStringLiveData<DataHolderWrapper<ReturnUserSelectedCategoriesAndAgeDataHolder>> =
        _returnUserSelectedCategoriesAndAgeFromDatabase

    private val _setCategoriesUpdatedForViewModel: KeyStringMutableLiveData<SetCategoriesUpdatedForViewModelDataHolder> =
        KeyStringMutableLiveData()
    val setCategoriesUpdatedForViewModel: KeyStringLiveData<SetCategoriesUpdatedForViewModelDataHolder> =
        _setCategoriesUpdatedForViewModel

    private val _neededVeriInfo: KeyStringMutableLiveData<DataHolderWrapper<NeededVeriInfoDataHolder>> =
        KeyStringMutableLiveData()
    val neededVeriInfo: KeyStringLiveData<DataHolderWrapper<NeededVeriInfoDataHolder>> =
        _neededVeriInfo

    //checks if the user needs verification information
    suspend fun runNeedVeriInfo(
        callingFragmentInstanceID: String,
        userLongitude: Double,
        userLatitude: Double,
        selectCategoriesViewModelInstanceId: String
    ) {
        withContext(ioDispatcher) {

            val returnPair = runNeedVeriInfoClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                userLongitude,
                userLatitude
            )

            if (returnPair.errorStatus != GrpcFunctionErrorStatusEnum.DO_NOTHING) {

                withContext(Dispatchers.Main) {

                    _neededVeriInfo.setValue(
                        DataHolderWrapper(
                            returnPair,
                            callingFragmentInstanceID
                        ),
                        selectCategoriesViewModelInstanceId
                    )
                }
            }
        }
    }

    //sets the categories on the server
    suspend fun setCategories(
        categoriesList: ArrayList<CategoryTimeFrame.CategoryActivityMessage>,
        sharedApplicationViewModelInstanceId: String
    ): SetFieldsReturnValues =
        withContext(ioDispatcher) { //these 2 functions are used by the application to clear matches in a different view model

            val returnVal = setCategoriesClient(
                applicationContext,
                accountInfoDataSource,
                accountPicturesDataSource,
                clientsIntermediate,
                errorHandling,
                ioDispatcher,
                categoriesList
            )

            if (returnVal.errorStatus == GrpcFunctionErrorStatusEnum.NO_ERRORS) {
                withContext(Dispatchers.Main) {
                    _setCategoriesUpdatedForViewModel.setValue(
                        SetCategoriesUpdatedForViewModelDataHolder(
                            categoriesList,
                            returnVal.updatedTimestamp
                        ),
                        sharedApplicationViewModelInstanceId
                    )
                }
            }

            return@withContext SetFieldsReturnValues(
                returnVal.errorStatus,
                returnVal.invalidParameterPassed
            )
        }

    suspend fun getCategoriesAndAgeFromDatabase(
        callingFragmentInstanceID: String,
        selectCategoriesViewModelInstanceId: String
    ) {
        withContext(ioDispatcher) {
            var ageAndCategories: ReturnUserSelectedCategoriesAndAgeDataHolder? =
                null
            val transactionWrapper = ServiceLocator.provideTransactionWrapper(
                applicationContext,
                DatabasesToRunTransactionIn.ACCOUNTS
            )
            transactionWrapper.runTransaction {
                ageAndCategories = accountInfoDataSource.getCategoriesAndAge(this)
            }

            ageAndCategories?.let {
                withContext(Dispatchers.Main) {
                    _returnUserSelectedCategoriesAndAgeFromDatabase.setValue(
                        DataHolderWrapper(
                            it,
                            callingFragmentInstanceID
                        ),
                        selectCategoriesViewModelInstanceId
                    )
                }
            }
        }
    }

    suspend fun clearCategories() {
        withContext(ioDispatcher) {
            accountInfoDataSource.setCategoryInfo("~", -1L)
        }
    }

}