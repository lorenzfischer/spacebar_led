package ch.ledtube

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ch.ledtube.databinding.FragmentVisualizationBinding
import ch.ledtube.lightshow.AllOffLightshow
import ch.ledtube.lightshow.MusicEnergyLightshow
import ch.ledtube.lightshow.MusicLightshow
import ch.ledtube.lightshow.MusicScrollLightshow
import ch.ledtube.visualizer.VisualizationController

private const val TAG = "VisualizationFragment"

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class VisualizationFragment : Fragment() {

    private var _binding: FragmentVisualizationBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var visController: VisualizationController? = null

    // todo: replace all of this service binding stuff with a function within the service
    /** If set, we have a connection to the LedService that we can interact with.*/
    private var ledServiceBinder: LedService? = null

    var ledServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            ledServiceBinder = (binder as LedService.RegistrationServiceBinder).service
            // make sure we are the ones receiving the updates here
            ledServiceBinder!!.getCurrentlyRunningLightshow()?.let {
                if (it is MusicLightshow) {
                    (it as MusicLightshow).setUpdateReceiver(binding.visualizerview)
                }
            }
            Log.d(TAG, "connected to LED service")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.d(TAG, "disconnected from LED service")
            ledServiceBinder = null
        }
    }

    fun bindToLedService() {
        // connect to the foreground service and possibly also start it
        val i = Intent(activity, LedService::class.java)
        this.context?.let {
            ContextCompat.startForegroundService(it, i);
            it.bindService(i, ledServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonVisualizeEnergy.setOnClickListener {
                if (this.visController == null) {
                    this.visController = VisualizationController()
                }
                Utils.safeLet(visController, ledServiceBinder) { ctrlr, ledService ->
                    if (ledServiceBinder?.getCurrentlyRunningLightshow() !is MusicEnergyLightshow) {
                        Log.d(TAG, "starting MusicEnergyLightshow")
                        binding.buttonVisualizeEnergy.setText(R.string.button_visualization_stop)
                        ctrlr.startVisualizer(requireActivity())
                        ctrlr.updateReceiver = binding.visualizerview
                        ledService.startSendingLightshow(MusicEnergyLightshow(ctrlr))
                    }
                }
        }

        binding.buttonVisualizeScroll.setOnClickListener {
            if (this.visController == null) {
                this.visController = VisualizationController()
            }
            Utils.safeLet(visController, ledServiceBinder) { ctrlr, ledService ->
                if (ledServiceBinder?.getCurrentlyRunningLightshow() !is MusicScrollLightshow) {
                    Log.d(TAG, "starting MusicScrollLightshow")
                    binding.buttonVisualizeScroll.setText(R.string.button_visualization_stop)
                    ctrlr.startVisualizer(requireActivity())
                    ctrlr.updateReceiver = binding.visualizerview
                    ledService.startSendingLightshow(MusicScrollLightshow(ctrlr))
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        bindToLedService()
    }

    override fun onPause() {
        super.onPause()
        this.visController?.updateReceiver = null // stop receiving updates
        this.context?.unbindService(this.ledServiceConnection)
    }

    override fun onDestroyView() {
        super.onDestroyView()
//        this.visualizer?.deleteVisualizer() todo: clean up!
        _binding = null
    }
}