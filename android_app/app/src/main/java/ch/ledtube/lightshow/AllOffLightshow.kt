package ch.ledtube.lightshow

import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray

class AllOffLightshow: Lightshow() {

    val ledMatrix: NDArray<Double, D2> = mk.d2array(3, getResolution()) { 0.0 }

    override fun getFrame(): NDArray<Double, D2> {
        return ledMatrix
    }

}