package ch.ledtube.devicediscovery

import android.util.Log
import java.net.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ServerBroadcaster"

class ServerBroadcaster(
    val serverPort: Int = 1337,
    val multicastPort: Int = 5555,
    val multicastAddress: InetAddress = InetAddress.getByName("224.1.1.1")
): Runnable {

    val running = AtomicBoolean(false)

    var socket: MulticastSocket? = null

    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    fun getIPAddress(useIPv4: Boolean = true): String? {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.getInetAddresses())
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                    0,
                                    delim
                                ).uppercase(
                                    Locale.getDefault()
                                )
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            Log.e(TAG, "Problem when trying to get this device's IP address")
        } // for now eat exceptions
        return ""
    }

    /**
     * Get all the wifi interfaces
     * @return  a list of network interfaces
     */
    private fun getWIFIInterfaces(): List<NetworkInterface>? {
        val wifiInterfaces = ArrayList<NetworkInterface>()
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isUp && networkInterface.name.contains("wlan", ignoreCase = true)) {
                    wifiInterfaces.add(networkInterface)
                }
            }
        } catch (ignored: Exception) {
            Log.e(TAG, "Problem when trying to get this device's IP address")
        } // for now eat exceptions
        return wifiInterfaces
    }

    private fun broadcastOnAllWIFIDevices() {
        val wifiInterfaces = getWIFIInterfaces()
        if (wifiInterfaces == null) {
            Log.e(TAG, "Could not get the phone/tablet's IP address")
        } else {
            for (wifiInterface in wifiInterfaces) {
                for (wifiAddress in wifiInterface.inetAddresses) {
                    val ipAddressString = wifiAddress.hostAddress
                    val isIPv4 = ipAddressString.indexOf(':') < 0
                    if (isIPv4) {
                        val addressBytes =
                            ipAddressString.split(".").map { elem -> elem.toInt().toByte() }
                        val messageBytes = addressBytes // + portBytes.toList()
                        val packet = DatagramPacket(
                            messageBytes.toByteArray(),
                            messageBytes.size,
                            this.multicastAddress,
                            this.multicastPort
                        )

                        // broadcast server address
                        synchronized(this.running) {
                            Log.d(TAG, "broadcasting $ipAddressString")
                            this.socket?.networkInterface =
                                wifiInterface // send on specific interface
                            this.socket?.send(packet)
                        }
                    }
                }
            }
        }
    }

    fun stopBroadcaster() {
        this.running.set(false)
        synchronized(this.running) {
            this.socket?.close()
            this.socket = null
        }
        Log.d(TAG,"Broadcaster stopped")
    }

    override fun run() {
        Log.d(TAG, "Starting the broadcaster")
        this.socket = MulticastSocket(multicastPort)

        this.running.set(true)
        while (this.running.get()) {
            broadcastOnAllWIFIDevices()
            Thread.sleep(5 * 1000)
        }
    }
}