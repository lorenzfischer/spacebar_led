package ch.ledtube.dsp

import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.map
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times

/**
 * Exponential smoothing filter. Ported from Scott Lawson's Python code:
 * https://github.com/scottlawsonbc/audio-reactive-led-strip/blob/master/python/dsp.py
 */
class SmoothingFilter<D: Dimension>(
    var values: NDArray<Double, D>,
    val alphaDecay: Double = 0.5,
    val alphaRise: Double = 0.5) {

    init {
        assert(0.0 < alphaDecay && alphaDecay < 1.0)
        assert(0.0 < alphaRise && alphaRise < 1.0)
    }

    /**  Updates the provided values according to the filters decay and rise values. */
    fun update(newValues: NDArray<Double, D>): NDArray<Double, D> {
        var alpha: NDArray<Double, D> = newValues - this.values
        alpha = alpha.map{
            if (it > 0.0) {
                this.alphaRise
            } else {
                this.alphaDecay
            }

        }
        this.values = alpha * newValues + (1.0 - alpha) * this.values
        return this.values
    }
}