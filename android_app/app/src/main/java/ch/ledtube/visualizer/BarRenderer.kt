package ch.ledtube.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect


private const val TAG = "Renderer"

class BarRenderer(val numberOfBars: Int = 16): Renderer {

    private val paint = Paint()
    private val bars = FloatArray(numberOfBars)
    private val maxVals = FloatArray(numberOfBars)
    private var lastUpdateMillis = System.currentTimeMillis()
    private val smoothingMillis = 500

    init {
        this.paint.setColor(Color.argb(122, 0, 255, 0));
        this.paint.style = Paint.Style.FILL_AND_STROKE
    }

    fun drawBar(canvas: Canvas, rect: Rect, value: Float, bar: Int, barWidth: Int, maxHeight: Int) {
        val left = bar * barWidth
        val right = left + barWidth
        val barRect = Rect(
            left,
            rect.bottom - (value * maxHeight).toInt(),
            right,
            rect.bottom)
//        Log.d(TAG, "bar: ${bar} val: ${value} left: ${left} right: ${right}")
        canvas.drawRect(barRect, paint)
    }

    /**
     * Renders the data created by fast fourier transform.
     * DEPRECATED, WE DON'T USE THIS ANYMORE, WE USE THE doublerray one
     *
     * @param canvas the canvas to draw into
     * @param rect the rectangle
     */
    fun renderFftBytes(canvas: Canvas, rect: Rect, data: ByteArray?, samplingRate: Int) {
        //  some good code here: https://stackoverflow.com/questions/5226553/bad-spectrum-from-androids-fft-output-visualiser

        if (data != null) {
            val barWidth = (rect.right - rect.left) / numberOfBars
            val maxHeight = rect.bottom - rect.top
            val currentMillis = System.currentTimeMillis()
            val millisSinceLastUpdate = currentMillis - lastUpdateMillis
            lastUpdateMillis = currentMillis
            val decay = (millisSinceLastUpdate * 1.0f / smoothingMillis) * 120

            val n: Int = data.size
            val numberOfValues = n / 2
            val valuesPerBin = numberOfValues / numberOfBars

            val magnitudes = FloatArray(numberOfValues + 1)
            magnitudes[0] = 0.0f // Math.abs(data[0].toInt()).toFloat() // DC
            magnitudes[n / 2] = Math.abs(data[1].toInt()).toFloat() // Nyquist

            for (i in 1 until data.size / 2) {
                val rfk: Byte = data[2 * i]
                val ifk: Byte = data[2 * i + 1]
                var magnitude = (rfk * rfk + ifk * ifk).toDouble()
                magnitudes[i] = (10 * Math.log10(magnitude)).toFloat()
            }

            for (bar in 0 until numberOfBars) {
                val oldVal = bars[bar]
                val slice = bar*valuesPerBin until (bar*valuesPerBin)+valuesPerBin
                val newVal = magnitudes.slice(slice).max() - 5
//                val newVal = magnitudes[bar]
                //val newVal = magnitudes[valuesPerBin * bar) + 1]  // skip the DC
                val decayedVal = Math.max(oldVal - decay, 0.0f)
                bars[bar] = if (newVal > decayedVal) {
                    newVal
                } else {
                    decayedVal
                }
                maxVals[bar] = Math.max(bars[bar], maxVals[bar])
                drawBar(canvas, rect, Math.min(bars[bar] / maxVals[bar], 1.0f) , bar, barWidth, maxHeight)

            }

//              Log.d(TAG, frequencies.joinToString(separator = ", "))
//              Log.d(TAG, "max: ${bars.max()}")


//            val n: Int = data.size
//            val numberOfValues = n / 2
//            // for music, no more than 10% of the spectrum is really interesting, it seems
//            val numberOfInterestingValues = (0.10 * numberOfValues).toInt()
//            val valuesPerBin = numberOfInterestingValues / numberOfBars


//            var frequencies = LongArray(numberOfValues + 1)
//            val magnitudes = FloatArray(numberOfValues + 1)
//            magnitudes[0] = 0.0f // Math.abs(data[0].toInt()).toFloat() // DC
//            magnitudes[n / 2] = Math.abs(data[1].toInt()).toFloat() // Nyquist

//            for (k in 1 until n / 2) {
//                val i = k * 2
////                magnitudes[k] = Math.hypot(data[i].toDouble(), data[i + 1].toDouble()).toFloat()  // max about 130
//                magnitudes[k] = 10 * Math.log10(Math.hypot(data[i].toDouble(), data[i + 1].toDouble())).toFloat()  // max about 19
////                magnitudes[k] = 10 * Math.log10((  // max about 40
////                            (data[i]*data[i]) + (data[i + 1]*data[i + 1])
////                ).toDouble()).toFloat()
//                frequencies[k] = k.toLong() * samplingRate / n
//            }
//
////            Log.d(TAG, frequencies.joinToString(separator = ", "))
//
////            val currentMillis = System.currentTimeMillis()
////            val millisSinceLastUpdate = currentMillis - lastUpdateMillis
////            lastUpdateMillis = currentMillis
////            val decay = (millisSinceLastUpdate * 1.0f / smoothingMillis) * 19
//
//            for (bar in 0 until numberOfBars) {
//                val oldVal = bars[bar]
//                val slice = bar*valuesPerBin until (bar*valuesPerBin)+valuesPerBin
////                Log.d(TAG, "bar: ${bar} valuesPerBin: ${valuesPerBin} slice: ${slice.toString()}")
//                val newVal = magnitudes.slice(slice).max() - 5
////                val newVal = magnitudes[bar]
//                //val newVal = magnitudes[valuesPerBin * bar) + 1]  // skip the DC
//                val decayedVal = Math.max(oldVal - decay, 0.0f)
//                bars[bar] = if (newVal > decayedVal) {
//                    newVal
//                } else {
//                    decayedVal
//                }
//                maxVals[bar] = Math.max(bars[bar], maxVals[bar])
//                drawBar(canvas, rect, Math.min(bars[bar] / maxVals[bar], 1.0f) , bar, barWidth, maxHeight)
//
//            }
//            Log.d(TAG, frequencies.joinToString(separator = ", "))
//            Log.d(TAG, "max: ${bars.max()}")
        }
    }

    override fun renderFftDoubles(
        canvas: Canvas,
        rect: Rect,
        data: DoubleArray
    ) {
        val barWidth = (rect.right - rect.left) / numberOfBars
        val maxHeight = rect.bottom - rect.top
        val currentMillis = System.currentTimeMillis()
        val millisSinceLastUpdate = currentMillis - lastUpdateMillis
        lastUpdateMillis = currentMillis
        val decay = (millisSinceLastUpdate * 1.0f / smoothingMillis) * 120

        val numberOfValues = data.size
        val valuesPerBin = numberOfValues / numberOfBars

        for (bar in data.indices) {
            val oldVal = bars[bar]
            val newVal = data[bar]
//            val decayedVal = Math.max(oldVal - decay, 0.0f)
//            bars[bar] = if (newVal > decayedVal) {
//                newVal.toFloat()
//            } else {
//                decayedVal
//            }
            bars[bar] = newVal.toFloat()
            maxVals[bar] = Math.max(bars[bar], maxVals[bar])
            drawBar(canvas, rect, Math.min(bars[bar] / maxVals[bar], 1.0f) , bar, barWidth, maxHeight)

        }
    }

}