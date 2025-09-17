package site.letsgoapp.letsgo.utilities

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import site.letsgoapp.letsgo.globalAccess.GlobalValues

/** This is a mutex that will timeout and throw TimeoutCancellationException when it does. It
 * is here as a way to detect deadlock occurring. The exception is NOT meant to be caught.
 * Using it in one place as a test to make sure it works in production. It could
 * be useful more places in the future. **/
class CoroutineTimedMutex(private val timeoutInMillis: Long) {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        return if(GlobalValues.setupForTesting) {
            mutex.withLock {
                block()
            }
        } else {
            //NOTE: withTimeout() will finish immediately when running tests and a context that is
            // different from the current context is called. This is a problem specifically because
            // when a CoroutineReentrantLock runs at the same time, it will setup a new context.
            // Can see here for more info
            // https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md#using-withtimeout-inside-runtest
            withTimeout(timeoutInMillis) {
                mutex.withLock {
                    block()
                }
            }
        }
    }
}