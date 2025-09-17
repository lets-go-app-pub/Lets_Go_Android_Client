package site.letsgoapp.letsgo.globalAccess

import android.widget.TextView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import requestmessages.RequestMessages.ServerActivityOrCategoryMessage
import site.letsgoapp.letsgo.utilities.StoreErrors
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.printStackTraceForErrors

object CategoriesAndActivities {

    data class SingleCategory(
        val index: Int,
        val orderNumber: Double,
        val name: String,
        val color: String,
        val minAge: Int
    ) {

        //pass Unit to build 'Unknown' category constructor
        constructor(@Suppress("UNUSED_PARAMETER") unit: Unit) : this(
            0,
            0.0,
            "Unknown",
            "#000000",
            121
        )

        constructor(index: Int, category: ServerActivityOrCategoryMessage?) : this(
            index,
            category?.orderNumber ?: 0.0,
            category?.displayName ?: "Unknown",
            category?.color ?: "#000000",
            category?.minAge ?: 121
        )
    }

    data class SingleActivity(
        val index: Int,
        val displayName: String,
        val iconDisplayName: String,
        val minAge: Int,
        val categoryIndex: Int,
        val iconIndex: Int,
    ) {

        //pass Unit to build 'Unknown' activity constructor
        constructor(@Suppress("UNUSED_PARAMETER") unit: Unit) : this(
            0,
            "Unknown",
            "Unknown",
            13,
            0,
            0
        )

        constructor(category: ServerActivityOrCategoryMessage) : this(
            category.index,
            category.displayName,
            category.iconDisplayName,
            category.minAge,
            category.categoryIndex,
            category.iconIndex
        )
    }

    class TextViewWrapper(_textView: TextView?) {
        @Volatile
        var textView: TextView? = _textView
    }

    class MutableActivityPair(_textViewWrapper: TextViewWrapper, _activity: SingleActivity) {

        //pass Unit to build 'Unknown' activity constructor
        constructor(@Suppress("UNUSED_PARAMETER") unit: Unit) : this(
            TextViewWrapper(
                null
            ),
            SingleActivity(Unit)
        )

        //TextViewWrapper is used here for a specific case, when the allActivities list is updated, and
        // the reference to the textView is passed through, there is the chance that the textView reference
        // could be COPIED (below inside setupCategoriesAndActivities()) then set to null (inside
        // SelectCategoriesFragment). This would cause a memory leak of the textView.
        val textViewWrapper: TextViewWrapper = _textViewWrapper
        val activity: SingleActivity = _activity
    }

    data class CategoryActivities(
        val category: SingleCategory,
        val activityIndexValues: List<Int>
    ) {

        //pass Unit to build 'Unknown' category constructor
        constructor(@Suppress("UNUSED_PARAMETER") unit: Unit) : this(
            SingleCategory(Unit),
            listOf(
                0
            )
        )

        constructor(pair: Pair<SingleCategory, ArrayList<Int>>) : this(
            pair.first,
            pair.second
        )
    }

    class ProtectedAccessList<T>(
        private var errorStore: StoreErrorsInterface,
        vararg element: T
    ) : List<T> by listOf(*element) {
        private val delegate = element

        override fun get(index: Int): T {
            return try {
                delegate[index]
            } catch (e: IndexOutOfBoundsException) {

                val errorMessage =
                    "IndexOutOfBoundsException was sent when running 'get' on Activities or Categories list.\n" +
                            "size: ${delegate.size}\n" +
                            "index: $index\n" +
                            "list: $delegate\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    e.stackTraceToString(),
                    errorMessage
                )

                delegate[0]
            }
        }
    }

    //A list of all categories with a list of the activity indexes used inside them.
    // List is ordered by category index.
    @Volatile
    var activitiesOrderedByCategory = ProtectedAccessList(StoreErrors(), CategoryActivities(Unit))
        private set

    //Each element stores a specific activity as well as the test view associated with it.
    // List is ordered by activity index.
    @Volatile
    var allActivities = ProtectedAccessList(StoreErrors(), MutableActivityPair(Unit))
        private set

    suspend fun setupCategoriesAndActivities(
        serverCategories: MutableList<ServerActivityOrCategoryMessage?>,
        serverActivities: MutableList<ServerActivityOrCategoryMessage?>,
        ioDispatcher: CoroutineDispatcher,
        errorStore: StoreErrorsInterface
    ) {
        withContext(ioDispatcher) {
            //save categories from server to client
            //activitiesOrderedByCategory.clear()

            val tempCategoriesList = mutableListOf<Pair<SingleCategory, ArrayList<Int>>>()

            for (i in serverCategories.indices) {
                tempCategoriesList.add(
                    Pair(
                        SingleCategory(i, serverCategories[i]!!),
                        arrayListOf()
                    )
                )
            }

            //using a temp list here instead of just modifying parent list in
            //case new activities were added the indexing will still be in order (even though it shouldn't happen)
            val tempActivitiesList = arrayListOf<MutableActivityPair>()

            for (activityPassedFromJava in serverActivities) {
                activityPassedFromJava?.let { singleActivity ->
                    var elementFound = false

                    //iterate to see if activity already exists inside list
                    // need to copy textView reference across if exists
                    for (element in allActivities) {
                        if (element.activity.index == singleActivity.index) {
                            elementFound = true
                            tempActivitiesList.add(
                                MutableActivityPair(
                                    element.textViewWrapper,
                                    SingleActivity(singleActivity)
                                )
                            )
                            break
                        }
                    }

                    //if element was not found, add it
                    if (!elementFound) {
                        tempActivitiesList.add(
                            MutableActivityPair(
                                TextViewWrapper(
                                    null
                                ),
                                SingleActivity(singleActivity)
                            )
                        )
                    }

                    if (singleActivity.categoryIndex < tempCategoriesList.size) {
                        //add activities to a list for sorting by category
                        tempCategoriesList[singleActivity.categoryIndex]
                            .second.add(singleActivity.index)
                    } else {
                        var errorMessage =
                            "When extracting activities to server, category index was too large.\n"
                        errorMessage += singleActivity.toString()
                        errorMessage += "index: ${singleActivity.categoryIndex}"

                        errorStore.storeError(
                            Thread.currentThread().stackTrace[2].fileName,
                            Thread.currentThread().stackTrace[2].lineNumber,
                            printStackTraceForErrors(),
                            errorMessage
                        )
                    }
                }
            }

            val generatedCategoriesList = mutableListOf<CategoryActivities>()
            for (category in tempCategoriesList) {
                generatedCategoriesList.add(CategoryActivities(category))
            }

            //Along with initializations above, this will guarantee that activitiesOrderedByCategory and
            // allActivities always have at least one element.
            if (generatedCategoriesList.isNotEmpty() && tempActivitiesList.isNotEmpty()) {
                activitiesOrderedByCategory =
                    ProtectedAccessList(
                        errorStore,
                        *generatedCategoriesList.toTypedArray()
                    )

                allActivities = ProtectedAccessList(
                    errorStore,
                    *tempActivitiesList.toTypedArray()
                )
            } else {

                //Do not update the arrays here, always leave at least one element inside.

                val errorMessage =
                    "Empty activities and/or categories lists passed to setupCategoriesAndActivities().\n" +
                            "serverCategories: $serverCategories\n" +
                            "serverActivities: $serverActivities\n"

                errorStore.storeError(
                    Thread.currentThread().stackTrace[2].fileName,
                    Thread.currentThread().stackTrace[2].lineNumber,
                    printStackTraceForErrors(),
                    errorMessage
                )
            }
        }

    }

}