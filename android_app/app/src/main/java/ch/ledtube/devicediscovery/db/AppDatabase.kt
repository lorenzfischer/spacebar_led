package ch.ledtube.devicediscovery.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Device::class], version = 2)
abstract class AppDatabase: RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        val DB_NAME = "LohLedDb"
    }
}