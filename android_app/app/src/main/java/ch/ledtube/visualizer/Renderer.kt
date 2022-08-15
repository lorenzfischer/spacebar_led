package ch.ledtube.visualizer

import android.graphics.Canvas
import android.graphics.Rect

interface Renderer {

    /**
     * Renders the data created by fast fourier transform.
     * @param canvas the canvas to draw into
     * @param rect the rectangle
     * @param data the double array to render
     */
    fun renderFftDoubles(canvas: Canvas, rect: Rect, data: DoubleArray)

}