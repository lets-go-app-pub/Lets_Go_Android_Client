package site.letsgoapp.letsgo

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.repositories.*
import site.letsgoapp.letsgo.standAloneObjects.chatStreamObject.ChatStreamObject
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginFunctions
import site.letsgoapp.letsgo.standAloneObjects.loginFunctions.LoginSupportFunctions
import site.letsgoapp.letsgo.utilities.cancelChatStreamWorker
import site.letsgoapp.letsgo.utilities.generateNewInstallationId
import site.letsgoapp.letsgo.utilities.startChatStreamWorker
import site.letsgoapp.letsgo.utilities.startCleanDatabaseWorker
import site.letsgoapp.letsgo.workers.chatStreamWorker.ChatStreamWorker
import site.letsgoapp.letsgo.workers.chatStreamWorker.NotificationInfo
import site.letsgoapp.letsgo.workers.error_handling.UncaughtExceptionHandler
import kotlin.concurrent.withLock

class LetsGoApplicationClass : MultiDexApplication(), Application.ActivityLifecycleCallbacks,
    ComponentCallbacks2 {

    val loginRepository: LoginRepository
        get() = ServiceLocator.provideLoginRepository(applicationContext)

    val applicationRepository: ApplicationRepository
        get() = ServiceLocator.provideApplicationRepository(applicationContext)

    val chatStreamWorkerRepository: ChatStreamWorkerRepository
        get() = ServiceLocator.provideChatStreamWorkerRepository(applicationContext)

    val notificationInfoRepository: NotificationInfoRepository
        get() = ServiceLocator.provideNotificationInfoRepository(applicationContext)

    val cleanDatabaseWorkerRepository: CleanDatabaseWorkerRepository
        get() = ServiceLocator.provideCleanDatabaseWorkerRepository(applicationContext)

    val categoriesRepository: SelectCategoriesRepository
        get() = ServiceLocator.provideSelectCategoriesRepository(applicationContext)

    val picturesRepository: SelectPicturesRepository
        get() = ServiceLocator.provideSelectPicturesRepository(applicationContext)

    val loginFunctions: LoginFunctions
        get() = ServiceLocator.provideLoginFunctions(applicationContext)

    val loginSupportFunctions: LoginSupportFunctions
        get() = ServiceLocator.provideLoginSupportFunctions(applicationContext)

    val chatStreamObject: ChatStreamObject
        get() = ServiceLocator.provideChatStreamObject(applicationContext)

    private var numActivitiesActive = 0

    //protects the chatStreamWorker start/stop from running at the same time
    //do not lock inside chatStreamWorkerMutex, deadlock could occur
    private var startStopCoroutineMutex = Mutex()

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is 'new' and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.chat_messages_channel_name)
            val descriptionText = getString(R.string.chat_messages_channel_description)

            //Importance is set here, however it is only used on devices API 26 or higher, the notification
            // priority is used for lower API levels instead. The priority is set NotificationInfo.kt.
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                ChatStreamWorker.CHAT_MESSAGE_CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
            }

            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Suppress("unused")
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    override fun onCreate() {

        //setupStrictMode()

        //Mandatory
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(
            UncaughtExceptionHandler(
                Thread.getDefaultUncaughtExceptionHandler(),
                applicationContext
            )
        )
        GlobalValues.applicationContext = applicationContext

        createNotificationChannel()

        //register to view activity life cycles with overridden functions
        registerActivityLifecycleCallbacks(this)

        val sharedPreferences = getSharedPreferences(
            getString(R.string.shared_preferences_lets_go_key),
            MODE_PRIVATE
        )

        val installationId =
            sharedPreferences.getString(
                getString(R.string.shared_preferences_installation_id),
                null
            )

        if (installationId == null) { // installationId has not been generated
            generateNewInstallationId(applicationContext, sharedPreferences)
        } else { // installationId has been generated
            GlobalValues.initialSetInstallationId(installationId)
        }

        for(i in 0 until GlobalValues.server_imported_values.numberPicturesStoredPerAccount) {
            GlobalValues.setPicturesBools.add()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        //Required to be overridden with ActivityLifecycleCallbacks
        GlobalValues.anActivityWasCreated = true
    }

    override fun onActivityStarted(activity: Activity) {

        //NOTE: Using this method instead of ProcessLifecycleOwner because ProcessLifecycleOwner takes into account configuration
        // changes and so there is a delay before @OnLifecycleEvent(Lifecycle.Event.ON_STOP) the however Let's Go has no configuration changes
        // and so the delay is 'unwanted'.

        if (numActivitiesActive == 0) { //if application is starting
            Log.i("StreamWorker", "onStart")

            GlobalValues.anActivityCurrentlyRunning = true

            //NOTE: There is a situation in which this will not cancel properly it happens when.
            //1) inside worker1 it gets past continueChatStreamWorker and hits enqueue WorkManager.getInstance(applicationContext).enqueue(worker2)
            //2) cancel is called and cancels worker1
            //3) because of how stopping works worker1 is canceled THEN enqueues worker2
            //4) worker2 is still running after the cancel
            //the mutex inside the companion object should fix the potential problem
            CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                startStopCoroutineMutex.withLock {
                    cancelChatStreamWorker()

                    //This locks a mutex which also wraps database calls, it must be done outside the main thread.
                    NotificationInfo.clearNotifications(applicationContext)

                    //After the app is properly started, none of the received messages require to be sent to a notification.
                    chatStreamWorkerRepository.updateAllMessagesToDoNotRequireNotifications()
                }
            }
        }

        numActivitiesActive++
    }

    override fun onActivityResumed(activity: Activity) {
        //Required to be overridden with ActivityLifecycleCallbacks
        //NOTE: do not use this with onActivityPaused() to measure application state because of order of events
        //Pause A → Create B → Start B → Resume B → Stop A
        //this means numActivitiesActive will intermittently be 0
    }

    override fun onActivityPaused(activity: Activity) {
        //Required to be overridden with ActivityLifecycleCallbacks
        //NOTE: do not use this with onActivityPaused() to measure application state because of order of events
        //Pause A → Create B → Start B → Resume B → Stop A
        //this means numActivitiesActive will intermittently be 0
    }

    override fun onActivityStopped(activity: Activity) {

        //NOTE: using this method instead of ProcessLifecycleOwner because ProcessLifecycleOwner takes into account configuration
        // changes and so there is a delay before @OnLifecycleEvent(Lifecycle.Event.ON_STOP) the however Let's Go has no configuration changes
        // and so the delay is 'unwanted'
        numActivitiesActive--

        //if application is stopping
        if (numActivitiesActive == 0) {
            Log.i("StreamWorker", "onStop")

            GlobalValues.anActivityCurrentlyRunning = false

            //start chat stream worker if not testing
            //If the ChatStreamWorker is set when an activity is stopped, there are potential
            // problems where it can run into the next test. This can be avoided as it is
            // inside cleanupPreviouslySetValues(), however that way is very slow, it takes
            // nearly a second for each test
            if(!GlobalValues.setupForTesting) {
                CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                    startStopCoroutineMutex.withLock {
                        startChatStreamWorker(applicationContext)
                    }
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        //Required to be overridden with ActivityLifecycleCallbacks
    }

    override fun onActivityDestroyed(activity: Activity) {
        //Required to be overridden with ActivityLifecycleCallbacks
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        //NOTE: Glide automatically calls GlideApp.get(applicationContext).trimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */
            }

            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */
            }

            else -> {
                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
            }

        }

    }

}

class LetsGoRuntimeException(message: String) : Exception(message)

//Log.i("LoginFunction", Thread.currentThread().stackTrace[2].lineNumber.toString())

/*
//This function allows to set a LiveData that only observes once
fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    observe(lifecycleOwner, object : Observer<T> {
        override fun onChanged(t: T?) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })

    /*
    observeForever(object : Observer<T> {
        override fun onChanged(t: T?) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
     */
}
 */