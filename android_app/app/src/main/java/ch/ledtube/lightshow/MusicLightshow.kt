package ch.ledtube.lightshow

import ch.ledtube.visualizer.VisualizationController

abstract class MusicLightshow(var visualizationController: VisualizationController): Lightshow() {

    fun setUpdateReceiver(receiver: VisualizationController.FftUpdateReceiver) {
        visualizationController.updateReceiver = receiver
    }

    override fun getFps(): Int {
        // if it's a music lightshow, we need 70 frames
        return 70
    }

}