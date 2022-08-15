package ch.ledtube.devicediscovery.db

import androidx.room.*

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device")
    fun getAll(): List<Device>

    @Query("SELECT * FROM device WHERE ipPort IN (:deviceIps)")
    fun loadAllByIpPort(deviceIps: Array<String>): List<Device>

    @Query("SELECT * FROM device WHERE ipPort = :deviceIps LIMIT 1")
    fun loadOneByIpPort(deviceIps: String): Device

//    @Query("SELECT * FROM client WHERE first_name LIKE :first AND " +
//            "last_name LIKE :last LIMIT 1")
//    fun findByName(first: String, last: String): User

    @Insert
    fun insertAll(vararg devices: Device)

    @Delete
    fun delete(device: Device)

    @Update
    fun update(device: Device)

}