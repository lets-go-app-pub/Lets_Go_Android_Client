package site.letsgoapp.letsgo.utilities

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

//This will lock around a single coRoutine and allow it to access other instances of the withLock() function
// it requires the coroutine to have the CoroutineCustomId set
//If a coroutineScope is passed to this object without CoroutineCustomId set this will simply run the block without a lock
//NOTE: it is tempting to think that a Mutex() object can work like a normal Reentrant lock however there are some problems
// with this, first of all because it is not thread confined the coroutine can do things like jump to a different thread
// if instead the lock is locked around an object, because coroutines can both work on the same thread and invoke the
// same object then this approach will not work either. @Synchronized or synchronized will not work either because
// when a delay() (or anything that suspends the coroutine happens such as a server call) then the Synchronized is released
// and another coroutine can access that suspending block, causing the coroutines to execute in parallel
//NOTE: while there is no hard proof fair ordering was observed when testing (it makes sense there are no ways
// to suspend the coRoutine until the primaryMutexLock.lock() is called and the Mutex() class itself is fair ordering)
//NOTE: This class is also far from an ideal solution, it defeats the value than coroutines bring over threads
//TESTING_NOTE: make sure to test this by 1) getting to a depth, 2) cancelling a coRoutine (the parent or the child)
class CoroutineReentrantLock {
    private val primaryMutexLock = Mutex()

    @Volatile
    private var currentOwnerName: String? = null
    private var depth = 0

    @Suppress("unused")
    fun isLocked(): Boolean {
        return currentOwnerName != null
    }

    private data class CoroutineCustomReentrantLockId(
        val name: String,
    ) : AbstractCoroutineContextElement(CoroutineCustomReentrantLockId) {

        companion object Key : CoroutineContext.Key<CoroutineCustomReentrantLockId>

        override fun toString(): String = "CoroutineCustomId($name)"
    }

    //TESTING_NOTE: this has not been tested since the change to have it create the custom ID
    suspend fun <T> withLock(block: suspend () -> T): T {
        val name: String
        val context =
            if (coroutineContext[CoroutineCustomReentrantLockId] == null) {
                name = generateCoroutineCustomId()
                CoroutineCustomReentrantLockId(name) + coroutineContext
            } else {
                name = coroutineContext[CoroutineCustomReentrantLockId]!!.name
                coroutineContext
            }

        return withContext(context) {
            try {
                lock(name)
                return@withContext block()
            } finally {
                unLock(name)
            }
        }
    }

    suspend fun holdsLock(): Boolean {
        val name = coroutineContext[CoroutineCustomReentrantLockId]?.name
        return name == currentOwnerName
    }

    private suspend fun lock(name: String) {

        val successfullyLocked = primaryMutexLock.tryLock()

        if (!successfullyLocked && name != currentOwnerName) {
            primaryMutexLock.lock()
        }

        when (currentOwnerName) {
            name -> { //this means this owner already locked
                depth++
            }
            else -> { //this means this is a new owner
                currentOwnerName = name
                depth = 1
            }
        }
    }

    private fun unLock(
        name: String
    ) {

        val tempCurrentOwnerName = currentOwnerName

        if (tempCurrentOwnerName != name) { //correct owner was NOT used
            //This can happen with coRoutine cancellations, for example
            // #1 locks
            // #2 locks (and sits at .lock)
            // #2 is cancelled and tries to run unLock() without owning anything
            return
        }

        try {
            depth--
            if (depth <= 0) {
                depth = 0
                currentOwnerName = null
                primaryMutexLock.unlock()
            }
        } catch (e: IllegalStateException) {

            val errorMessage =
                "Exception was thrown when attempting to unlock CoroutineReentrantLock.\n" +
                        "e: ${e.message}\n"

            ServiceLocator.globalErrorStore.storeError(
                Thread.currentThread().stackTrace[2].fileName,
                Thread.currentThread().stackTrace[2].lineNumber,
                e.stackTraceToString(),
                errorMessage
            )

            //can continue here
        }
    }

    private fun generateCoroutineCustomId(): String {
        return Thread.currentThread().id.toString() + "_" + SystemClock.elapsedRealtimeNanos()
            .toString() + "_" + generateRandomCode()
    }

    private fun generateRandomCode(): String {
        var returnVal = ""

        repeat(4) {
            //NOTE: the first 32 and #127 are not 'actual' chars here
            // so don't use them (for example I could get an end of text char)
            returnVal += Random.nextInt(32, 126).toChar()
        }

        return returnVal
    }
}

//guarantees that locks will always be locked in one order; the order the keys are passed in,
// will throw an IllegalStateException
//TESTING_NOTE: this has not been tested
//TESTING_NOTE: make sure to test canceling a coRoutine
class ReentrantLocksOrderGuarantor(
    vararg mutexKeys: String
) {

    //these are only written on class initialization and read after that meaning they do
    // not need to be (or already are?) thread safe
    private val locksMapOfIndexInList = mutableMapOf<String, Int>()
    private val locksList = mutableListOf<CoroutineReentrantLock>()

    private data class CoroutineLockIndexNumber(
        var highestIndexValueLocked: Int,
    ) : AbstractCoroutineContextElement(CoroutineLockIndexNumber) {

        companion object Key : CoroutineContext.Key<CoroutineLockIndexNumber>

        override fun toString(): String = "CoroutineLockIndexNumber($highestIndexValueLocked)"
    }

    init {
        for (key in mutexKeys) {
            if (locksMapOfIndexInList[key] == null) {
                locksList.add(CoroutineReentrantLock())
                locksMapOfIndexInList[key] = locksList.lastIndex
            }
        }
    }

    @Suppress("unused")
    suspend fun <T> allMutexWithLock(block: suspend () -> T) =
        withContext(provideContext()) {
            runNestedLocks(
                block,
                List(locksList.size) { index ->
                    index
                }
            )
        }

    suspend fun <T> singleMutexWithLock(mutexKey: String, block: suspend () -> T): T {
        return specificMutexWithLocks(listOf(mutexKey), block)
    }

    //NOTE: List<String> is used instead of varargs so that the block can be passed outside the parenthesis when
    // calling the method
    suspend fun <T> specificMutexWithLocks(mutexKeys: List<String>, block: suspend () -> T): T {
        return withContext(provideContext()) {

            val listOfIndexToLock = mutableListOf<Int>()

            for (key in mutexKeys) {
                locksMapOfIndexInList[key]?.let { listOfIndexToLock.add(it) }
            }

            listOfIndexToLock.sort()

            return@withContext runNestedLocks(
                block,
                listOfIndexToLock
            )
        }
    }

    private suspend fun <T> runNestedLocks(
        block: suspend () -> T,
        listOfIndexToLock: List<Int>,
        currentIndexInListParameter: Int = 0,
    ): T {

        if (currentIndexInListParameter < listOfIndexToLock.size) { //run with lock

            val previouslyLockedIndex =
                coroutineContext[CoroutineLockIndexNumber]!!.highestIndexValueLocked
            val currentIndexToLock = listOfIndexToLock[currentIndexInListParameter]

            return when {
                previouslyLockedIndex < currentIndexToLock -> {

                    coroutineContext[CoroutineLockIndexNumber]!!.highestIndexValueLocked =
                        currentIndexToLock

                    val returnVal = locksList[currentIndexToLock].withLock {
                        return@withLock runNestedLocks(
                            block,
                            listOfIndexToLock,
                            currentIndexInListParameter + 1
                        )
                    }

                    //set the previously locked index back to the index that was locked before this
                    coroutineContext[CoroutineLockIndexNumber]!!.highestIndexValueLocked =
                        previouslyLockedIndex

                    returnVal
                }
                //this means the reEntrant lock was already locked by this coRoutine, do not want to store
                // the highestIndexValueLocked in this because it needs to be the HIGHEST index that is currently
                // locked
                locksList[currentIndexToLock].holdsLock() -> {

                    locksList[currentIndexToLock].withLock {
                        runNestedLocks(
                            block,
                            listOfIndexToLock,
                            currentIndexInListParameter + 1
                        )
                    }
                }
                else -> { //previouslyLockedIndex > currentIndexInListParameter && currentCoroutine does not hold lock
                    throw IllegalStateException("Inside CoroutineReentrantLock, the locks were locked out of order.\nlistOfIndexToLock: $listOfIndexToLock\ncurrentIndexToLock: $currentIndexToLock")
                }
            }
        } else { //locks have all been nested
            return block()
        }
    }

    //will return this context if it contains a CoroutineLockIndexNumber and will add a CoroutineLockIndexNumber
    // if the context does NOT contain a CoroutineLockIndexNumber
    private suspend fun provideContext(): CoroutineContext {
        return if (coroutineContext[CoroutineLockIndexNumber] == null) {
            createContextContainingCoroutineLockIndexNumber()
        } else {
            coroutineContext
        }
    }

    private suspend fun createContextContainingCoroutineLockIndexNumber(): CoroutineContext {
        return CoroutineLockIndexNumber(-1) + coroutineContext
    }

}