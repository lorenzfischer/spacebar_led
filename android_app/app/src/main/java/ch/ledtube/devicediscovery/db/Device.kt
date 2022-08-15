package ch.ledtube.devicediscovery.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Device(
    /** The ip and port separated by a colon. */
    @PrimaryKey val ipPort: String,
    val battery: Float = 0.0f,
    val active: Boolean = true
)