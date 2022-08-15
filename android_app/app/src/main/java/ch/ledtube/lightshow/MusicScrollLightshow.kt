package ch.ledtube.lightshow

import android.util.Log
import ch.ledtube.dsp.SmoothingFilter
import ch.ledtube.visualizer.VisualizationController
import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.average
import org.jetbrains.kotlinx.multik.ndarray.operations.joinToString
import org.jetbrains.kotlinx.multik.ndarray.operations.max
import org.jetbrains.kotlinx.multik.ndarray.operations.times

private const val TAG = "MusicScrollLightshow"

/**
 * Music Effect:
 *
 * Effect that originates in the center and scrolls outwards.
 *
 * Ported from Scott Lawsons Python code at
 * https://github.com/scottlawsonbc/audio-reactive-led-strip/blob/c38612fefee605382be91f4745912d60f67324e0/python/visualization.py#L105
 */
class MusicScrollLightshow(controller: VisualizationController): MusicLightshow(controller) {

    var ledMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }
    val smoothing = SmoothingFilter(
        ledMatrix.copy(),
        alphaRise = 0.9,
        alphaDecay = 0.005
    )

    override fun getFrame(): NDArray<Double, D2> {
        visualizationController.getVisualizationData()?.let {
            val visData = mk.ndarray(it)

//            Log.d(TAG, visData.joinToString(separator = ", ") { it.toString() })

            /*
              look at powering and normalising:

                global p
                y = y**2.0
                gain.update(y)
                y /= gain.value
                y *= 255.0

             */

            // compute intensity values for each color
            val of255 = visData * 255.0
            val middle = getResolution().floorDiv(2).toInt()
            val aThird = visData.size.floorDiv(3)
            val twoThirds = 2 * aThird
            val red = of255[0..aThird].max() ?: 0.0
            val green = of255[aThird..twoThirds].max() ?: 0.0
            val blue = of255[twoThirds..visData.size].max() ?: 0.0

            //Log.d(TAG, "red: ${red} green: ${green} blue: ${blue}")

            // move all other fields
            val res = getResolution()
            for (led_idx in res-1 downTo middle) {
                ledMatrix[0, led_idx] = ledMatrix[0, led_idx-1]
                ledMatrix[1, led_idx] = ledMatrix[1, led_idx-1]
                ledMatrix[2, led_idx] = ledMatrix[2, led_idx-1]
                ledMatrix[0, res-led_idx] = ledMatrix[0, res-led_idx+1]
                ledMatrix[1, res-led_idx] = ledMatrix[1, res-led_idx+1]
                ledMatrix[2, res-led_idx] = ledMatrix[2, res-led_idx+1]
            }
            ledMatrix *= 0.95

            ledMatrix[0, middle] = red
            ledMatrix[1, middle] = green
            ledMatrix[2, middle] = blue
            ledMatrix[0, middle-1] = red
            ledMatrix[1, middle-1] = green
            ledMatrix[2, middle-1] = blue
        }
        return ledMatrix
    }

    override fun getFps(): Int {
        return 70
    }
}