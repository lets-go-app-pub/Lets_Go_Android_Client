package site.letsgoapp.letsgo.utilities

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import site.letsgoapp.letsgo.globalAccess.GlobalValues

/**
 * Will make (and lock) a mutex for the 'id' that is passed into it. If a mutex is already created
 * will respond differently based on the function.
 * Lock will time out after GlobalValues.lockRPCTimeoutTime amount of time has passed and throw the
 * exception. This is done in order to help detect deadlocks from the server. The error will be thrown
 * and caught by the UncaughtExceptionHandler.
 */
class LockByIdMap {

    data class MutexWithReferenceCount(
        var referenceCount: Int = 1,
        var elementMutex: Mutex = Mutex()
    )

    private val mapMutex = Mutex()
    private val map = mutableMapOf<String, MutexWithReferenceCount>()

    //will run runWhenAcquiredLockBlock() if the lock is acquired
    //will run runWhenDidNotAcquireLockBlock() if the lock is not acquired
    suspend fun runIfNotLockedWith(
        id: String,
        runWhenAcquiredLockBlock: suspend () -> Unit,
        runWhenDidNotAcquireLockBlock: suspend () -> Unit
    ) {
        try {
            if(GlobalValues.setupForTesting) {
                val successfullyLocked = tryLockMutex(id)
                if (successfullyLocked) {
                    runWhenAcquiredLockBlock()
                } else {
                    runWhenDidNotAcquireLockBlock()
                }
            } else {
                withTimeout(GlobalValues.lockRPCTimeoutTime) {
                    val successfullyLocked = tryLockMutex(id)
                    if (successfullyLocked) {
                        runWhenAcquiredLockBlock()
                    } else {
                        runWhenDidNotAcquireLockBlock()
                    }
                }
            }

        } finally {
            unLockMutex(id)
        }
    }

    suspend fun runWithLock(id: String, block: suspend (wasLocked: Boolean) -> Unit) {
        try {
            val wasLocked = lockMutex(id)
            block(wasLocked)
        } finally {
            unLockMutex(id)
        }
    }

    private suspend fun tryLockMutex(id: String): Boolean {
        var value: MutexWithReferenceCount?

        mapMutex.withLock {
            value = map[id]
            if (value == null) {
                value = MutexWithReferenceCount()
                map[id] = value!!
            } else {
                value!!.referenceCount++
            }
        }

        //NOTE: cannot put lock() inside mapMutex or deadlock can occur
        return value!!.elementMutex.tryLock(currentCoroutineContext())
    }

    private suspend fun lockMutex(id: String): Boolean {
        var value: MutexWithReferenceCount?

        mapMutex.withLock {
            value = map[id]
            if (value == null) {
                value = MutexWithReferenceCount()
                map[id] = value!!
            } else {
                value!!.referenceCount++
            }
        }

        //NOTE: cannot put lock() inside mapMutex or deadlock can occur
        val acquiredLock = value!!.elementMutex.tryLock(currentCoroutineContext())

        if (!acquiredLock) {
            value!!.elementMutex.lock(currentCoroutineContext())
        }

        return !acquiredLock
    }

    private suspend fun unLockMutex(id: String) {
        val value: MutexWithReferenceCount?

        //NOTE: because unlock() occurs instantly it is OK to put it inside mapMutex
        try {
            mapMutex.withLock {
                value = map[id]

                if (value != null) {
                    if (value.referenceCount > 1) {
                        value.referenceCount--
                    } else {
                        map.remove(id)
                    }
                }

                if (value?.elementMutex?.holdsLock(currentCoroutineContext()) == true)
                    value.elementMutex.unlock(currentCoroutineContext())

            }
        } catch (e: IllegalStateException) {
            /** This can happen if the coRoutine is cancelled. **/
        }
    }
}