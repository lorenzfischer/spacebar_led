package ch.ledtube.lightshow

import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray

private const val TAG = "Lightshow"

abstract class Lightshow() {

    // todo: think of the advantage of making this immutable
    class LedValue(val ledIndex: Int, var red: Int, var green: Int, var blue:Int) {

        fun asByteList(): List<Byte> {
            return listOf<Byte>(ledIndex.toByte(), red.toByte(), green.toByte(), blue.toByte())
        }

        fun dimBy(rate: Double): LedValue {
            red = ((1-rate) * red).toInt()
            green = ((1-rate) * green).toInt()
            blue = ((1-rate) * blue).toInt()
            return this
        }

        override fun toString(): String {
            return "r:$red g:$green b:$blue"
        }

    }


    init {

    }


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