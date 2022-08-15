package ch.ledtube.lightshow

import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.set

class PulsatingLightshow(val millisPerPulse: Int = 1000, val minIntensity: Int = 20): Lightshow() {

    val ledMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }

    override fun getFrame(): NDArray<Double, D2> {
        val currentMillis = System.currentTimeMillis()
        val animationStep = 1.0 * (currentMillis % millisPerPulse) / millisPerPulse
        val stepRadians = animationStep * Math.PI * 2
        val intensity = minIntensity + ((Math.sin(stepRadians) + 1) / 2 * (255-minIntensity))

        (0 until getResolution()).map{
            ledMatrix[0, it] = intensity // we're just setting red
        }
        return ledMatrix
    }

    override fun getFps(): Int {
        return 70
    }
}