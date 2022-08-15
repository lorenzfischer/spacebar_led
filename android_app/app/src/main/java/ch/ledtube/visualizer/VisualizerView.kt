package ch.ledtube.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View

private const val TAG = "VisualizerView"

/**
 * TODO: document your custom view class.
 */
class VisualizerView: View, VisualizationController.FftUpdateReceiver {

    private var fftDoubles: DoubleArray? = null
    private var renderer: Renderer? = null

    constructor(context: Context) : super(context) {
        init(null, 0)
    }


    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }


    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }


    private fun init(attrs: AttributeSet?, defStyle: Int) {
        this.renderer = BarRenderer()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = Rect(this.paddingLeft, this.paddingTop,
                  this.width-paddingRight, this.height-paddingBottom)

        if (this.fftDoubles != null) {
            this.renderer?.renderFftDoubles(canvas, rect, this.fftDoubles!!)
        }

    }

    override fun onVisualizerDataCapture(fft: DoubleArray) {
        this.fftDoubles = fft
        this.postInvalidate() // force redrawing
    }
}