package ch.ledtube.dsp

import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * This class implements the Mel Filter Bank. It is a port of the Mel Filter Bank from
 * here: https://github.com/scottlawsonbc/audio-reactive-led-strip/blob/master/python/melbank.py
 */
class MelFilterbank(
    val numMelBands: Int = 24, val freqMin: Double = 20.0, val freqMax: Double = 8000.0,
    val numFftBands: Int = 630, val sampleRate: Int = 44100
    // todo: switch fftbands to 512 again
    // todo: switch sampleRate back to 16000 ?
) {

    companion object {

        /**
         * Returns mel-frequency from linear frequency input.
         * @param freq the frequency in Hertz (todo: nd array)
         * @return a scalar mel value
         */
        fun hertzToMel(freq: Double): Double {
            return 2595.0 * log10(1 + (freq / 700.0))
        }


        /**
         * Converts a mel value into it's representation in Hertz.
         * @param mel the value in mel (todo: look at nd array)
         * @return the value in Hertz
         */
        fun melToHertz(mel: Double): Double {
            return 700.0 * (10.toDouble().pow(mel / 2595.0)) - 700.0
        }

    }


    /**
     * Compute the center, lower, and upper frequencies in Hertz for the required number of
     * Mel bands.
     *
     * @return a matrix (NDArray<Double, D2>) with the columns lowerFreqs, centerFreqs,
     *         upperFreqs
     */
    private fun computeMelFrequencies(): NDArray<Double, D2> {
        val melMax = hertzToMel(freqMax)
        val melMin = hertzToMel(freqMin)
        val deltaMel = abs(melMax - melMin) / (numMelBands + 1.0)
        val freqsMel =  deltaMel * mk.arange<Double>(start=0, stop=numMelBands + 2) + melMin

        val numFreqs = freqsMel.size
        val lowerEdgesMel = freqsMel[0 .. numFreqs-2].asDNArray()
        val upperEdgesMel = freqsMel[2 .. numFreqs].asDNArray()
        val centerFreqsMel = freqsMel[1 .. numFreqs-1].asDNArray()
        val bandLowerCenterUpper: NDArray<Double, D2> = mk.ndarray(mk[
                lowerEdgesMel.toList(),
                centerFreqsMel.toList(),
                upperEdgesMel.toList()
        ]).transpose()
        return bandLowerCenterUpper
    }


    /**
     * Transformation matrix for the mel spectrum.
     * Use this with fft spectra of num_fft_bands_bands length
     * and multiply the spectrum with the melmat
     * this will tranform your fft-spectrum
     * to a mel-spectrum.
     *
     * rows = meldBands
     * columns = fftBands
     */
    private var melMatrix: NDArray<Double, D2> = mk.zeros(numMelBands, numFftBands)

    init {
        val melFrequencies: NDArray<Double, D2> = computeMelFrequencies()
        val freqs = mk.linspace<Double>(0.0, sampleRate / 2.0, numFftBands)
        for (melBand in 0 until numMelBands) {
            val lower = melToHertz(melFrequencies[melBand][0])
            val center = melToHertz(melFrequencies[melBand][1])
            val upper = melToHertz(melFrequencies[melBand][2])
            freqs.forEachIndexed { freqIdx, freq ->
                if (freq >= lower && freq <= center) {
                    melMatrix[melBand, freqIdx] = (freq - lower) / (center - lower)
                }
                if (freq >= center && freq <= upper) {
                    melMatrix[melBand, freqIdx] = (upper - freq) / (upper - center)
                }
            }
        }
    }

    /** Converts the measured FFT values into a MEL filterbank, using the precomputed MEL Matrix.*/
    fun convertToMel(fftValues: ByteArray): D1Array<Double> {
        val n: Int = fftValues.size
        val numberOfValues = n / 2
        val magnitudes = DoubleArray(numberOfValues) { 0.0 }
        magnitudes[0] = Math.abs(fftValues[0].toInt()).toDouble()// DC
//        magnitudes[n / 2] = Math.abs(fftValues[1].toInt()).toDouble()// Nyquist
        for (i in 1 until fftValues.size / 2) {
            val rfk: Byte = fftValues[2 * i]
            val ifk: Byte = fftValues[2 * i + 1]
            var magnitude = (rfk * rfk + ifk * ifk).toDouble()
            magnitudes[i] = max(0.0, (10 * Math.log10(magnitude)))
        }

        val fftArrayBroadcasted: List<List<Double>> = List(this.melMatrix.shape[0]) { magnitudes.toList() }
        val fftNdArray: NDArray<Double, D2> = mk.ndarray(fftArrayBroadcasted)
        val melValues = fftNdArray * this.melMatrix
        val melSums: List<Double> = (0 until numMelBands).map {
            melValues[it].sum()
        }
         return mk.ndarray(melSums)
    }

    // documentation: https://github.com/Kotlin/multik
    // more: https://kotlin.github.io/multik/multik-core/org.jetbrains.kotlinx.multik.api/arange.html

}