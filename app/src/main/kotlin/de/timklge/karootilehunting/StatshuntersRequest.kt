package de.timklge.karootilehunting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityListResponse(
    val activities: List<Activity>,
    val meta: Meta
)

@Serializable
data class Meta(
    val limit: Int
)

@Serializable
data class Activity(
    val id: Int,
    val foreignId: String,
    @SerialName("user_id")
    val userId: Int,
    val name: String,
    val gearId: Int? = null,
    val gearForeignId: String? = null,
    val lat: Double,
    val lng: Double,
    val distance: Double,
    val avg: Double,
    @SerialName("total_elevation_gain")
    val totalElevationGain: Double,
    @SerialName("moving_time")
    val movingTime: Int,
    @SerialName("elapsed_time")
    val elapsedTime: Int,
    @SerialName("max_speed")
    val maxSpeed: Double,
    @SerialName("average_cadence")
    val averageCadence: Double,
    @SerialName("average_heartrate")
    val averageHeartrate: Double,
    @SerialName("max_heartrate")
    val maxHeartrate: Double,
    val kilojoules: Double,
    val commute: Int,
    val trainer: Int,
    @SerialName("workout_type")
    val workoutType: Int,
    val date: String,
    val timezone: String,
    val type: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val status: String,
    val utcDate: String,
    val countries: List<Country>,
    val regions: List<Region>,
    val tiles: List<Tile>
)

@Serializable
data class Country(
    val id: Int,
    val foreignId: Int,
    val name: String
)

@Serializable
class Region(
    // Placeholder for region properties. Adjust as needed.
)