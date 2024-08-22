package ch.ledtube.lightshow

import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray

private const val TAG = "Lightshow"

abstract class Lightshow() {

    /**
     * Returns the number of frames per second (FPS) this lightshow would optimally want to
     * run at.
     */
    open fun getFps(): Int {
        return 4  // for static lightshows, 4 frames a second is enough
    }


    /**
     * @return the resolution (i.e. the number of LEDs) this lightshow operates at. Default is 144.
     */
    fun getResolution(): Int {
        return 140
    }


    /** This method returns a list of RgbValues that has the length of @see getResolution(). */
    abstract fun getFrame(): NDArray<Double, D2>

}