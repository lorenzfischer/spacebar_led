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
import org.jetbrains.kotlinx.multik.ndarray.operations.times

private const val TAG = "MusicEnergyLightshow"

/**
 * Music Effect:
 *
 * Effect that expands from the center with increasing sound energy.
 *
 * Ported from Scott Lawsons Python code at
 * https://github.com/scottlawsonbc/audio-reactive-led-strip/blob/c38612fefee605382be91f4745912d60f67324e0/python/visualization.py#L127
 */
class MusicEnergyLightshow(controller: VisualizationController): MusicLightshow(controller) {

    var ledMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }
    val smoothing = SmoothingFilter(
        ledMatrix.copy(),
        alphaRise = 0.6,
        alphaDecay = 0.01
    )

    override fun getFrame(): NDArray<Double, D2> {
        // todo: generalise into "AbstractMusicLightShow"
        visualizationController.getVisualizationData()?.let {
            val visData = mk.ndarray(it)

            // compute intensity values for each color
            val middle = getResolution().floorDiv(2).toInt()
            val scaled = visData * (middle - 1).toDouble()
            val aThird = visData.size.floorDiv(3)
            val twoThirds = 2 * aThird
            val red = scaled[0..aThird].average().toInt()
            val green = scaled[aThird..twoThirds].average().toInt()
            val blue = scaled[twoThirds..visData.size].average().toInt()
            for (led_idx in 0 until middle) {
                val redV = if(led_idx <= red) 255.0 else 0.0
                val greenV = if(led_idx <= green) 255.0 else 0.0
                val blueV = if(led_idx <= blue) 255.0 else 0.0
                ledMatrix[0, middle+led_idx] = redV
                ledMatrix[1, middle+led_idx] = greenV
                ledMatrix[2, middle+led_idx] = blueV
                ledMatrix[0, middle-1-led_idx] = redV
                ledMatrix[1, middle-1-led_idx] = greenV
                ledMatrix[2, middle-1-led_idx] = blueV
            }
            ledMatrix = smoothing.update(ledMatrix)
        }
        return ledMatrix
    }
}