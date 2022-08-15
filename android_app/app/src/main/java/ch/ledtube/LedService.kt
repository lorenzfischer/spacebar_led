package ch.ledtube

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import ch.ledtube.devicediscovery.RegistrationServer
import ch.ledtube.devicediscovery.ServerBroadcaster
import ch.ledtube.lightshow.Lightshow
import ch.ledtube.lightshow.LightshowServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private const val TAG = "RegistrationService"

/**
 * This is the main "foreground" service of the LED app. It takes care of broadcasting the service,
 * managing registered clients, and also of sending the light show to the led devices.
 */
class LedService: Service() {

    companion object {
        const val NOTIFICATION_ID = 1
    }

    /** This will be given to components that bind to this service so they can interact with us.*/
    private val binder: IBinder = RegistrationServiceBinder(this)

    /** We use this to run multiple background threads. */
    private val executorService: ExecutorService = Executors.newFixedThreadPool(3)

    /** This class broadcasts our address using Multicast> */
    private var broadcaster: ServerBroadcaster? = null

    /** This class is responsible for sending the lightshow to all client devices. */
    private var lightshowServer: LightshowServer? = null

    /** This guy is received registration requests by clients */
    private var registrationServer: RegistrationServer? = null

    fun isSendingLightshow(): Boolean {
        return this.lightshowServer?.running?.get() ?: false
    }

    fun getCurrentlyRunningLightshow(): Lightshow? {
        if (this.lightshowServer != null) {
            return this.lightshowServer!!.lightshow
        } else {
            return null
        }
    }

    fun isLookingForClients(): Boolean {
        return this.broadcaster?.running?.get() ?: this.registrationServer?.running?.get() ?: false
    }

    fun startSendingLightshow(lightshow: Lightshow) {
        setLightshow(lightshow)
        startSendingLightshow()
    }

    fun startSendingLightshow() {
        this.lightshowServer?.let {
            if (!it.running.get()) {
                this.executorService.execute(it)
            }
        }
    }

    fun setLightshow(lightshow: Lightshow) {
        this.lightshowServer?.let {
            it.lightshow = lightshow
        }
    }

    fun stopSendingLightshow() {
        this.lightshowServer?.let {
            it.stopLightshowServer()
        }
    }

    /**
     * Starts the process of looking for clients if it wasn't already running.
     * @return if we started the process, false if it was already running.
     */
    fun startLookingForClients(): Boolean {
        Log.d(TAG, "Start looking for clients")
        var result = false
        this.registrationServer!!.let {
            if (!it.running.get()) {
                this.executorService.execute(it)
                result = true
            }
        }
        this.broadcaster!!.let {
            if (!it.running.get()){
                this.executorService.execute(it)
                result = true
            }
        }
        return result
    }


    fun stopLookingForClients() {
        Log.d(TAG, "Stop looking for clients")
        this.registrationServer?.let {
            it.stopRegistrationServer()
        }
        this.broadcaster?.let {
            it.stopBroadcaster()
        }
    }


    override fun onCreate() {
        this.broadcaster = ServerBroadcaster()
        this.lightshowServer = LightshowServer(this)
        this.registrationServer = RegistrationServer(this)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel("my_service", "My Background Service")
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("title text")
            .setContentText("context text")
//                        .setSmallIcon(R.drawable.icon)
//                        .setContentIntent(pendingIntent)
            .setTicker("I guess this will be some longer text scrolling by")
            .setContentIntent(pendingIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY_COMPATIBILITY  // todo: check whether this is correct
    }


    // jesus fuck, I guess chosing sensible defaults wasn't an option?
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }


    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        return this.binder
    }


    override fun onUnbind(intent: Intent): Boolean {
        // All clients have unbound with unbindService()
        // todo: check if there is still a thread running and if not, shut the service down
        Log.d(TAG, "All clients have unbound")
        if (!isLookingForClients() && !isSendingLightshow()) {
            Log.d(TAG, "All clients have unbound and there is nothing running. Shutting down.")
            stopSelf()
        }

        return false // todo: do we want to allow this?
    }


    override fun onRebind(intent: Intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }


    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        Log.d(TAG, "shutting down the LED service")
        stopLookingForClients()
    }


    class RegistrationServiceBinder(val service: LedService) : Binder()
}