package ch.ledtube.lightshow

import android.content.Context
import android.util.Log
import androidx.room.Room
import ch.ledtube.devicediscovery.db.AppDatabase
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LightshowServer"

/**
 * This class is responsible for sending "static" lightshows (which are not the result of a
 * music visualization) to the LED devices.
 *
 * @param context the activity context, we need this to get access to the db.
  */
class LightshowServer(val context: Context): Runnable {

    private var db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
        .fallbackToDestructiveMigration()
        .build()

    val running = AtomicBoolean(false)

    var lightshow: Lightshow = StaticWhiteLightshow()
        set(ls) {
            field = ls
            millisPerFrame = (1000.0f / ls.getFps()).toLong()
        }

    /** we use this to throttle the lightshow. */
    private var millisPerFrame: Long = 1000L


    fun stopLightshowServer() {
        Log.d(TAG, "stopping lightshow server")
        this.running.set(false)
    }

    override fun run() {
        Log.d(TAG, "starting lightshow server")

        val socket = DatagramSocket()
        var devices = db.deviceDao().getAll()
        var frame = 0
        var fpsMeasureMillis = System.currentTimeMillis()
        var totalSleepTime = 0L
        var startTime = 0L
        var elapsedPerStep = 0L

        this.running.set(true)
        while (this.running.get()) {
            // broadcast server address
            // Log.d(TAG, "Broadcasting our address " + packet.data)

            startTime = System.currentTimeMillis()

            // get next frame
            val frameData: NDArray<Double, D2> = lightshow.getFrame()
            val messageBytes = (0 until frameData.shape[1]).flatMap {
                listOf(
                    it.toByte(), // led index
                    frameData[0, it].toInt().toByte(), // red
                    frameData[1, it].toInt().toByte(), // green
                    frameData[2, it].toInt().toByte()) // blue
            }

            // and send it to all clients
            for (device in devices) {
                val (address, port) = device.ipPort.split(":")
                val packet = DatagramPacket(
                    messageBytes.toByteArray(),
                    messageBytes.size,
                    InetAddress.getByName(address),
                    port.toInt()
                )
                socket.send(packet)
            }
            elapsedPerStep = System.currentTimeMillis() - startTime
            val sleepTime = Math.max(0L, millisPerFrame-elapsedPerStep)

            if (System.currentTimeMillis() > fpsMeasureMillis + 1000) {
                val lightshowClass = lightshow.javaClass.toString()
                val load = ((1.0-(1.0*totalSleepTime/1000))*100).toInt()
                Log.d(TAG, "FPS: ${frame} load: ${load}% Class: ${lightshowClass}")
                frame = 0
                fpsMeasureMillis = System.currentTimeMillis()
                totalSleepTime = 0L

                // also update all the registered devices every second
                devices = db.deviceDao().getAll()
            }

            frame++
            totalSleepTime += sleepTime

            Thread.sleep(sleepTime)

        }
        Log.d(TAG, "lightshow server stopped")
    }

}