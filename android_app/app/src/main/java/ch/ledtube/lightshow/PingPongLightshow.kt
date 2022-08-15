package ch.ledtube.lightshow

import ch.ledtube.dsp.SmoothingFilter
import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.set
import java.lang.Integer.max
import java.lang.Math.min

private const val TAG = "PingPongLightshow"


class PingPongLightshow(
    val millisPerPulse: Int = 2000,
    val millisPerColorCycle: Int = 10 * 1000,
    val tailFade: Double = 0.2): Lightshow() {

    var ledMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }

    val smoothing = SmoothingFilter(
        ledMatrix.copy(),
        alphaRise = 0.9,
        alphaDecay = tailFade
    )

    /**
     * Computes the animation progress for a cyclical animation given the current time in
     * millis and the milliseconds each cycle should take.
     *
     * @param currentMillis the current system time in milliseconds.
     * @param millisPerCycle the number of milliseconds each cycle should take.
     * @return a value between 0.0 and 1.0
     */
    fun cycleAnimationPos(currentMillis: Long, millisPerCycle: Int): Double {
        val animationStep = 1.0 * (currentMillis % millisPerCycle) / millisPerCycle
        val stepRadians = animationStep * Math.PI * 2
        val position = (Math.sin(stepRadians) + 1) / 2  // between 0 and 1
        return position
    }

    override fun getFrame(): NDArray<Double, D2> {
        val currentMillis = System.currentTimeMillis()
        val ledPosition = cycleAnimationPos(currentMillis, millisPerPulse)
        val brightLed = Math.floor(ledPosition * (getResolution()-1)).toInt()

        val oneThird = (1.0*millisPerColorCycle/3).toLong()
        val redPosition = cycleAnimationPos(currentMillis, millisPerColorCycle)
        val greenPosition = cycleAnimationPos(currentMillis+oneThird, millisPerColorCycle)
        val bluePosition = cycleAnimationPos(currentMillis+2*oneThird, millisPerColorCycle)

        var newMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }

        (max(0, brightLed-1) .. min(brightLed+1, getResolution()-1)).forEach{
            newMatrix[0, it] = (redPosition*255)
            newMatrix[1, it] = (greenPosition*255)
            newMatrix[2, it] = (bluePosition*255)
        }

        ledMatrix = smoothing.update(newMatrix)

        // todo put in filter

        return ledMatrix
    }


    override fun getFps(): Int {
        return 70
    }

}