package ch.ledtube.visualizer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ch.ledtube.Utils
import ch.ledtube.dsp.Complex
import ch.ledtube.dsp.FFT
import ch.ledtube.dsp.MelFilterbank
import ch.ledtube.dsp.SmoothingFilter
import org.jetbrains.kotlinx.multik.api.d1array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import java.nio.ByteBuffer


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

    private val rate = 44100
    private val channels = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val audioBufferSizeBytes = numFftBuckets // AudioRecord.getMinBufferSize(rate, channels,encoding)

    /** We use this object to listen to the microphone of the phone. */
    private var micRecord: AudioRecord? = null

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
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    fun startVisualizer(owner: FragmentActivity) {
        if (allPermissionsGranted(owner)) {
            if (visualizer == null) {
                Log.d(TAG, "starting visualiser")

                // connect to microphone
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    channels,
                    encoding,
                    audioBufferSizeBytes
                )

                Log.d(TAG, "started recording")
                micRecord!!.startRecording()

                visualizer = Visualizer(0)  // 0 => "apply to the output mix."
//                visualizer = Visualizer(micRecord!!.audioSessionId)
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
        return Utils.safeLet(visualizer, melBank) { vis, melB -> // todo: redo this without visualizer in case of mic input

            micRecord?.let {
                val audioBuffer = ByteArray(audioBufferSizeBytes)
                it.read(audioBuffer, 0, audioBufferSizeBytes);
//                val magnitude = DoubleArray(audioBufferSizeBytes / 2)

                //Create Complex array for use in FFT
                val complexInputArray = arrayOfNulls<Complex>(audioBufferSizeBytes)
                for (i in 0 until audioBufferSizeBytes) {
                    complexInputArray[i] = Complex(audioBuffer[i].toDouble(), 0.0)
                }

                //Obtain array of FFT data
                val fftComplexArray: Array<Complex> = FFT.fft(complexInputArray) // returns fftTempArray.size() / 2
                (0..audioBufferSizeBytes/2).forEach {
                    buffer[2 * it] = fftComplexArray[it].re().toInt().toByte()
                    buffer[2 * it + 1] = fftComplexArray[it].im().toInt().toByte()
                }

                // remove copy!
//                Log.d(TAG, "I'm in here now!")
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
            }

            // todo: reactivate
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
            }

            return null
        }
    }

    // todo: call this!!
    fun deleteVisualizer() {
        visualizer?.release()
        micRecord?.stop()
        micRecord?.release()
        micRecord = null
    }

}




// https://stackoverflow.com/questions/42153673/how-to-calculate-frequency-level-from-audio-recorder-mic-input-data
// 
//int bufferSizeInBytes = 1024;
//short[] buffer = new short[bufferSizeInBytes];
//class Recording extends Thread {
//
//    @Override
//    public void run() {
//
//        while () {
//
//            if (true) {
//                int bufferReadResult = audioInput.read(buffer, 0, bufferSizeInBytes); // record data from mic into buffer
//                if (bufferReadResult > 0) {
//                    calculate();
//                }
//            }
//        }
//    }
//}
//public void calculate() {
//
//    double[] magnitude = new double[bufferSizeInBytes / 2];
//
//    //Create Complex array for use in FFT
//    Complex[] fftTempArray = new Complex[bufferSizeInBytes];
//    for (int i = 0; i < bufferSizeInBytes; i++) {
//        fftTempArray[i] = new Complex(buffer[i], 0);
//    }
//
//    //Obtain array of FFT data
//    final Complex[] fftArray = FFT.fft(fftTempArray);
//    // calculate power spectrum (magnitude) values from fft[]
//    for (int i = 0; i < (bufferSizeInBytes / 2) - 1; ++i) {
//
//        double real = fftArray[i].re();
//        double imaginary = fftArray[i].im();
//        magnitude[i] = Math.sqrt(real * real + imaginary * imaginary);
//
//    }
//
//    // find largest peak in power spectrum
//    double max_magnitude = magnitude[0];
//    int max_index = 0;
//    for (int i = 0; i < magnitude.length; ++i) {
//        if (magnitude[i] > max_magnitude) {
//            max_magnitude = (int) magnitude[i];
//            max_index = i;
//        }
//    }
//    double freq = 44100 * max_index / bufferSizeInBytes;//here will get frequency in hz like(17000,18000..etc)
//
//}




// what I had before:
//                // connect to microphone
//                val rate = 44100
//                val channels = AudioFormat.CHANNEL_IN_MONO
//                val encoding = AudioFormat.ENCODING_PCM_16BIT
//                val bufferSize = AudioRecord.getMinBufferSize(rate, channels,encoding)
//                micRecord = AudioRecord(
//                    MediaRecorder.AudioSource.MIC,
//                    rate,
//                    channels,
//                    encoding,
//                    bufferSize
//                )
////                micTrack = AudioTrack.Builder()
////                    .setAudioAttributes(
////                        AudioAttributes.Builder()
////                            .setUsage(AudioAttributes.USAGE_MEDIA)
////                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
////                            .build()
////                    )
////                    .setAudioFormat(
////                        AudioFormat.Builder()
////                            .setEncoding(encoding)
////                            .setSampleRate(rate)
////                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
////                            .build()
////                    )
////                    .setBufferSizeInBytes(bufferSize)
////                    .build()
////                val buffer = ByteArray(bufferSize);
//                micRecord!!.startRecording()
//                Log.d(TAG, "started recording")
//                Thread{
//                    micRecord!!.startRecording()
////                    micTrack!!.play()
////
////                    while(micTrack != null) {
////                        Utils.safeLet(micRecord, micTrack) { rec, track ->
////                            rec.read(buffer, 0, bufferSize);
////                            track.write(buffer, 0, buffer.size);
////                        }
////                    }
//                }.run()