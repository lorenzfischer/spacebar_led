package ch.ledtube.devicediscovery

import android.content.Context
import android.util.Log
import androidx.room.Room
import ch.ledtube.devicediscovery.db.AppDatabase
import ch.ledtube.devicediscovery.db.Device
import java.io.DataInputStream
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "RegistrationServer"

class RegistrationServer(val context: Context, val serverPort: Int = 1337): Runnable {

    private var db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
        .fallbackToDestructiveMigration()
        .build()

    val running = AtomicBoolean(false)

    private var serverSocket: ServerSocket? = null

    // https://stackoverflow.com/questions/56872782/convert-byte-array-to-int-odd-result-java-and-kotlin
    fun littleEndianConversion(bytes: ByteArray): Int {
        var result = 0
        for (i in bytes.indices) {
            result = result or (bytes[i].toInt() shl 8 * i)
        }
        return result
    }

    fun stopRegistrationServer() {
        this.serverSocket?.let {
            this.running.set(false)
            this.serverSocket?.close()
            this.serverSocket = null
        }
    }

    override fun run() {
        var socket: Socket? = null

        Log.d(TAG, "Deleting all clients from the database")
        db?.deviceDao()?.getAll()?.forEach{ device -> db!!.deviceDao().delete(device) }

//        Log.d(TAG, "Starting the registation server")
        this.serverSocket = ServerSocket(serverPort)

        this.running.set(true)
        while (this.running.get()) {
            try {
                socket = serverSocket?.accept()
                if (socket != null) {
                    val dataInputStream = DataInputStream(socket!!.getInputStream())
                    val clientAddress = socket!!.inetAddress.hostAddress

                    val buffer = ByteArray(2) // ports are two bytes long
                    dataInputStream.read(buffer, 0, buffer.size)
                    val clientPort = littleEndianConversion(buffer)

                    val ipPort = "$clientAddress:$clientPort"
                    val device = Device(ipPort = ipPort)
                    if (db.deviceDao().loadOneByIpPort(ipPort) == null) {
                        db.deviceDao().insertAll(device)
                    } else {
                        db.deviceDao().update(device)
                    }
                    Log.d(TAG, "Registration of client ${clientAddress}:${clientPort}")
                }
            } catch (e: SocketException) {
                Log.d(TAG, "Socket was interrupted. Probably the service was stopped")
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Timeout on socket, trying again.")
            }

            Thread.sleep(1000)
        }

        socket?.close()
        this.serverSocket?.close()
        Log.d(TAG, "registration server runnable ran out!")
    }
}