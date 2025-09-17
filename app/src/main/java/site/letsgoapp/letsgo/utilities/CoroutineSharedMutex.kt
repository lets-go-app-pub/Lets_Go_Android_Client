package site.letsgoapp.letsgo.utilities

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

/** This is fair **/
class CoroutineSharedMutex {

    private var numWaitingForPrimaryLock = AtomicInteger(0)
    private var locked = false

    //follows locked variable
    private val mutableSharedCondition = MutableStateFlow(locked)
    private val sharedCondition: StateFlow<Boolean> = mutableSharedCondition

    private val primaryCondition = Channel<Unit>(Channel.RENDEZVOUS)

    private val primaryMutex = Mutex()
    private var numRunning = AtomicInteger(0)

    suspend fun <T> withPrimaryLock(block: suspend () -> T): T {
        try {
            lockPrimary()
            return block()
        } finally {
            unLockPrimary()
        }
    }

    suspend fun <T> withSharedLock(block: suspend () -> T): T {
        try {
            lockShared()
            return block()
        } finally {
            unLockShared()
        }
    }

    private suspend fun lockShared() {

        //need this variable because there is no guarantee that onSubscription() will be
        // the same coRoutine calling it
        val currentCoroutineContext = currentCoroutineContext()
        primaryMutex.lock(currentCoroutineContext)
        sharedCondition
            .onSubscription {
                numRunning.incrementAndGet()
                if (primaryMutex.holdsLock(currentCoroutineContext))
                    primaryMutex.unlock()
            }
            .takeWhile {
                it
            }.collect {}
    }

    private fun unLockShared() {
        if (numRunning.decrementAndGet() == 0) {
            primaryCondition.trySend(Unit)
        }
    }

    suspend fun tryLock(): Boolean {

        Log.i("CoroutineSharedMutex", "tryLock()")

        if (!primaryMutex.tryLock(currentCoroutineContext())) { //if failed to acquire lock
            if (!locked && numWaitingForPrimaryLock.get() == 0) { //if the primary lock is NOT locked and has no primaries waiting for it (shared is using the lock)
                numWaitingForPrimaryLock.incrementAndGet()
                primaryMutex.lock(currentCoroutineContext())
                numWaitingForPrimaryLock.decrementAndGet()

                //there is a gap between the above if statement and the numWaitingForPrimaryLock.incrementAndGet()
                // where multiple coRoutines could 'get in', this condition will fix that
                if (numWaitingForPrimaryLock.get() > 0) {
                    return false
                }
            } else {
                return false
            }
        }

        lockPrimaryHelper()

        return true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun lockPrimary() {

        if (!primaryMutex.tryLock(currentCoroutineContext())) { //if failed to acquire lock
            numWaitingForPrimaryLock.incrementAndGet()
            primaryMutex.lock(currentCoroutineContext())
            numWaitingForPrimaryLock.decrementAndGet()
        }

        lockPrimaryHelper()
    }

    private suspend fun lockPrimaryHelper() {
        locked = true
        mutableSharedCondition.emit(locked)

        if (numRunning.get() > 0) {
            primaryCondition.receive()
        }
    }

    suspend fun unLockPrimary() {
        locked = false
        //NOTE: If .tryEmit() returns false, it means that it will suspend (not here, because .tryEmit() is not
        // a suspending function) until it can run emit. So this is just a non-blocking version of .emit().
        // This is needed because if the coRoutine is cancelled, it will end at emit if a suspending function is
        // used.
        mutableSharedCondition.tryEmit(locked)
        //this condition is necessary in case the coRoutine is cancelled
        if (primaryMutex.holdsLock(currentCoroutineContext()))
            primaryMutex.unlock()
    }
}