package ch.ledtube

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.room.Room
import ch.ledtube.databinding.FragmentLedDeviceDiscoveryBinding
import ch.ledtube.devicediscovery.db.AppDatabase
import ch.ledtube.devicediscovery.db.Device
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class LedDeviceDisoveryFragment : Fragment() {

    private val TAG = "LedDiscoveryFragment"

    private var _binding: FragmentLedDeviceDiscoveryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    /** We use this to run multiple background threads. */
    private val executorService: ExecutorService = Executors.newFixedThreadPool(2)

    private var db: AppDatabase? = null

    /** Our local copy of all registered devices, we use this to update the UI. */
    private var devices: List<Device>? = null  // todo make thread safe

    /** We use this to make the view updater stop it's activity */
    private val viewUpdaterRunning = AtomicBoolean(false)

    private val VIEW_UPDATE_MILLIS = 1000L;

    /** If set, we have a connection to the LedService that we can interact with.*/
    private var ledServiceBinder: LedService? = null

    var ledServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            ledServiceBinder = (binder as LedService.RegistrationServiceBinder).service
            ledServiceBinder?.let {
                binding.switchSearchForLeds.isChecked = it.isLookingForClients()
            }
            Log.d(TAG, "connected to LED service")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.d(TAG, "disconnected from LED service")
            ledServiceBinder = null
        }
    }


    /**
     * This thread checks every {VIEW_UPDATE_MILLIS} what devices are registered in the database
     * and updates the view components accordingly.
     */
    private val viewUpdater = Runnable {
        this.viewUpdaterRunning.set(true)
        while (this.viewUpdaterRunning.get()) {
            if (db != null) {
                val devicesInDb = db!!.deviceDao().getAll()
                //Log.d(TAG, "Devices in DB ${devicesInDb}")
                if (!devicesInDb.equals(devices) && this.binding != null) {
                    this.devices = devicesInDb

                    activity?.runOnUiThread(Runnable {
                        val headerRow = this.binding.ledDevicesTable.get(0)
                        headerRow.setPadding((10 * resources.displayMetrics.density + 0.5f).toInt())

                        this.binding.ledDevicesTable.removeAllViews()
                        this.binding.ledDevicesTable.addView(headerRow)

                        for (device in this.devices!!) {
                            val tr = TableRow(context)

                            val ipCell = TextView(context)
                            ipCell.setText(device.ipPort)
                            tr.addView(ipCell)

                            val batteryCell = TextView(context)
                            batteryCell.setText("${(device.battery * 100).toInt()}%")
                            tr.addView(batteryCell)

                            val activeCell = TextView(context)
                            activeCell.setText(device.active.toString())
                            tr.addView(activeCell)

                            this.binding.ledDevicesTable.addView(tr)
                        }
                    })
                }
            }
            Thread.sleep(this.VIEW_UPDATE_MILLIS)
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


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLedDeviceDiscoveryBinding.inflate(inflater, container, false)

        this.db = context?.let {
            Room.databaseBuilder(it, AppDatabase::class.java, AppDatabase.DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
        }

//        Log.d(TAG, "view created")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.binding.switchSearchForLeds.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                this.ledServiceBinder?.let {
                    if(isChecked && !it.isLookingForClients()) {
                        it.startLookingForClients()
                    } else if(!isChecked) {
                        it.stopLookingForClients()
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        bindToLedService()
        this.executorService.execute(this.viewUpdater)
    }

    override fun onPause() {
        super.onPause()
        this.context?.unbindService(this.ledServiceConnection)
        this.viewUpdaterRunning.set(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        this.viewUpdaterRunning.set(false)
        // todo: unbind from service
        _binding = null
//        Log.d(TAG, "view destroyed")
    }
}