package ch.ledtube.power

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import ch.ledtube.R

/**
 * Sorry, didn't come up with a better name ;)
 *
 * Based on wake lock idea: https://developer.android.com/topic/performance/vitals/wakelock#best-practices
 * "One remaining use-case for partial wake locks is to ensure that a music app continues to play when
 * the screen is off."
 *
 * You can acquire a wake lock using the PowerManager to keep the CPU running and prevent device
 * from going into "deep sleep" mode. This should help to ensure that your service continues to work
 * in the background with good performance even when the screen is off.
 */
class BatteryDrainer(context: Context) {
    private var wakeLock: PowerManager.WakeLock

    init {
        val appName = context.getString(R.string.app_name)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$appName::MyWakelockTag")
    }

    /**
     * Will acquire wake lock for 2h by default. Hopefully will be enough to dance \o/
     *
     * You can also do `BatteryDrainer.acquireWakeLock(null)` to dance for unlimited time...
     * or until phone battery runs out...Be extremely careful with it, can drain battery!
     */
    @SuppressLint("WakelockTimeout")
    fun startDraining(timeout: Long? = 120 * 60 * 1000L) {
        if (wakeLock.isHeld) { // 1 should be enough
            return
        }

        timeout?.let {
            wakeLock.acquire(it)
        } ?: wakeLock.acquire()
    }

    /**
     * This has to be called properly when you stop dancing, otherwise you battery will keep draining...
     * We can discuss it later
     */
    fun stopDraining() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}