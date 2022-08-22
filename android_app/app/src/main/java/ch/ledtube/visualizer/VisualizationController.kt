package ch.ledtube.visualizer

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ch.ledtube.Utils
import ch.ledtube.dsp.SmoothingFilter
import ch.ledtube.dsp.MelFilterbank
import org.jetbrains.kotlinx.multik.api.d1array
import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.max
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray

private const val TAG = "VisualizationController"

/**
 * This class does the admin work for getting the audio data from the Android system and passes it
 * on to other services and views.
 */
class VisualizationController(
        val numMelBands: Int = 16
    ) {

    interface FftUpdateReceiver {
        /**
         * This method will be called with the values captured from the audio device.
         */
        fun onVisualizerDataCapture(fft: DoubleArray)
    }

    private val PERMISSION_REQUEST_CODE = 1337 // TODO figure out where you need to put this
    private var visualizer: Visualizer? = null

    private val numFftBuckets = numMelBands * 4  // TODO: think about this a bit more!

    private var buffer = ByteArray(numFftBuckets * 2)
    private var melBank: MelFilterbank? = null

    private var gainFilter = SmoothingFilter(
        mk.d1array(numMelBands) { 0.1 },
        alphaDecay=0.001, alphaRise=0.75
    )

    /**
     * This receiver will be informed about FFt updates, whenever theyare requested over
     * {#getVisualizationData}. This enables us to inform some UI component whenever an update
     * is requested.
     *
     * @see #getVisualizationData
     */
    var updateReceiver: FftUpdateReceiver? = null

    private var smoothingFilter = SmoothingFilter(
        mk.d1array(numMelBands) { 0.1 },
        alphaDecay=0.2, alphaRise=0.99
    )

    /**
     * @return true if this visualizer has already been started, false otherwise.
     */
    fun isStarted(): Boolean {
        return this.visualizer != null
    }

    /**
     * Starts the visualizer which is able to receive audio data from the system.
     * This code might pop up a dialog asking the user for permission to listen in on the audio.
     * For this reason, we need an activity that we can attach the dialog to.
     *
     * @param owner the activity we can use to pop up the premission dialog.
     */
    fun startVisualizer(owner: FragmentActivity) {
        if (allPermissionsGranted(owner)) {
            if (visualizer == null) {
                Log.d(TAG, "starting visualiser")
                visualizer = Visualizer(0)  // 0 => "apply to the output mix."
                visualizer!!.enabled = false
                visualizer!!.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED  // otherwise we get loud silence between songs
                visualizer!!.captureSize = numFftBuckets * 2 // we will only get half of this as our frequency bands
                visualizer!!.enabled = true;
                melBank = MelFilterbank(
                    numFftBands=numFftBuckets, numMelBands=numMelBands, sampleRate=this.visualizer!!.samplingRate/1000)
            } else {
                Log.d(TAG, "already running visualizer")
            }
        } else {
            Log.d(TAG, "permissions not granted.. not doin' nothing!")
        }
    }

    private fun allPermissionsGranted(owner: FragmentActivity): Boolean {
        if (ContextCompat.checkSelfPermission(owner, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(owner, Manifest.permission.MODIFY_AUDIO_SETTINGS)
            != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(owner, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
//            if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity,
//                    Manifest.permission.READ_CONTACTS)) {
//                // Show an explanation to the user *asynchronously* -- don't block
//                // this thread waiting for the user's response! After the user
//                // sees the explanation, try again to request the permission.
//            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(owner, arrayOf(
                                                                Manifest.permission.RECORD_AUDIO,
                                                                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                                                                 Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSION_REQUEST_CODE);
//            }
            return false // the user will have to click again TODO: make this nicer
        } else {
            return true
        }
    }

    fun getVisualizationData(): DoubleArray? {
        return Utils.safeLet(visualizer, melBank) { vis, melB ->
            if (vis.getFft(buffer) == Visualizer.SUCCESS) {

                val melValues: D1Array<Double> = melB.convertToMel(buffer)

                // make differences starker
                val starker = melValues * melValues * melValues

                // todo: gaussian smoothing
                val gain = gainFilter.update(starker)
                val gainNormalized = starker / gain
                val smoothed = smoothingFilter.update(gainNormalized)
                val doubleArray = smoothed.toDoubleArray()

                // update the receiver, if one is registered
                this.updateReceiver?.let {
                    it.onVisualizerDataCapture(doubleArray!!)
                }

                return doubleArray
            } else {
                return null
            }
        }
    }

    fun deleteVisualizer() {
        visualizer?.release()
    }

}