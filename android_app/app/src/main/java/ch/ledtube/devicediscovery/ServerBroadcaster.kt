package ch.ledtube.devicediscovery

import android.util.Log
import java.net.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

private const val TAG = "ServerBroadcaster"

class ServerBroadcaster(
    val serverPort: Int = 1337,
    val multicastPort: Int = 5555,
    val multicastAddress: InetAddress = InetAddress.getByName("224.1.1.1")
): Runnable {

    val running = AtomicBoolean(false)

    var sockets: List<MulticastSocket> = ArrayList()

    /**
     * Get all IP addresses that are non-localhost interfaces
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    fun getIPAddresses(useIPv4: Boolean = true): List<InetAddress> {
        val result = ArrayList<InetAddress>()
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.getInetAddresses())
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val isIPv4 = addr.hostAddress.indexOf(':') < 0
                        if (useIPv4 && isIPv4) {
                            result.add(addr)
                        } else if(!useIPv4) {
                            result.add(addr)
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            Log.e(TAG, "Problem when trying to get this device's IP address")
        } // for now eat exceptions
        return result
    }

    fun stopBroadcaster() {
        this.running.set(false)
        synchronized(this.running) {
            this.sockets.forEach{
                it.close()
            }
            this.sockets = ArrayList()
        }
        Log.d(TAG,"Broadcaster stopped")
    }

    override fun run() {
//        Log.d(TAG, "Starting the broadcaster")
        val ipAddresses = getIPAddresses()
        if (ipAddresses.size == 0) {
            Log.e(TAG, "Could not get the phone/tablet's IP address")
        } else {
            this.sockets = ipAddresses.map{
                val socket = MulticastSocket(multicastPort)
                socket.`interface` = it
                socket
            }

            this.running.set(true)
            while (this.running.get()) {
                this.sockets.forEach { socket ->
                    val ipAddress = socket.`interface`.hostAddress
//                    Log.d(TAG, "broadcasting ${socket.`interface`.hostAddress}")
                    val addressBytes = ipAddress.split(".").map { elem -> elem.toInt().toByte() }
                    // todo: add port as well, and also update the ESP8266 code
                    //            val portBytes = ByteArray(2)
                    //            for (i in 0..1) portBytes[i] = (serverPort shr (i*8)).toByte()
                    val messageBytes = addressBytes // + portBytes.toList()
                    val packet = DatagramPacket(
                        messageBytes.toByteArray(),
                        messageBytes.size,
                        this.multicastAddress,
                        this.multicastPort
                    )

                    // broadcast server address
                    synchronized(this.running) {
                        socket.send(packet)
                    }
                }
                Thread.sleep(1000)
            }
        }
    }
}