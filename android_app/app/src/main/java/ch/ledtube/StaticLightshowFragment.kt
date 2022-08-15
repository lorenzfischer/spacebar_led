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
import ch.ledtube.databinding.FragmentStaticLightshowBinding
import ch.ledtube.lightshow.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class StaticLightshowFragment : Fragment() {

    private val TAG = "StaticLightshowFragment"

    private var _binding: FragmentStaticLightshowBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    /** We use this to run multiple background threads. */
    private val executorService: ExecutorService = Executors.newFixedThreadPool(3)

    // todo: replace all of this service binding stuff with a function within the service
    /** If set, we have a connection to the LedService that we can interact with.*/
    private var ledServiceBinder: LedService? = null

    var ledServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            ledServiceBinder = (binder as LedService.RegistrationServiceBinder).service
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
        _binding = FragmentStaticLightshowBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.binding.buttonWhiteStatic.setOnClickListener(
            View.OnClickListener {
                ledServiceBinder?.let {
                    it.startSendingLightshow(StaticWhiteLightshow())
                }
            }
        )

        this.binding.buttonPulsating.setOnClickListener(
            View.OnClickListener {
                ledServiceBinder?.let {
                    it.startSendingLightshow(PulsatingLightshow())
                }
            }
        )

        this.binding.buttonPingpong.setOnClickListener(
            View.OnClickListener {
                ledServiceBinder?.let {
                    it.startSendingLightshow(PingPongLightshow())
                }
            }
        )


        this.binding.buttonOff.setOnClickListener(
            View.OnClickListener {
                ledServiceBinder?.let {
                    it.startSendingLightshow(AllOffLightshow())
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        bindToLedService()
    }

    override fun onPause() {
        super.onPause()
        this.context?.unbindService(this.ledServiceConnection)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}