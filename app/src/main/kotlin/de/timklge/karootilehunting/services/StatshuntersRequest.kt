/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karootilehunting.services

import de.timklge.karootilehunting.Tile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Line(
    val id: Long,
    val data: String
)

@Serializable
data class LinesListResponse(
    val activities: List<Line>,
    val meta: Meta
)

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
    val foreignId: String? = null,
    @SerialName("user_id")
    val userId: Int? = null,
    val name: String? = null,
    val gearId: Int? = null,
    val gearForeignId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val distance: Double? = null,
    val avg: Double? = null,
    @SerialName("total_elevation_gain")
    val totalElevationGain: Double? = null,
    @SerialName("moving_time")
    val movingTime: Int? = null,
    @SerialName("elapsed_time")
    val elapsedTime: Int? = null,
    @SerialName("max_speed")
    val maxSpeed: Double? = null,
    @SerialName("average_cadence")
    val averageCadence: Double? = null,
    @SerialName("average_heartrate")
    val averageHeartrate: Double? = null,
    @SerialName("max_heartrate")
    val maxHeartrate: Double? = null,
    val kilojoules: Double? = null,
    val commute: Int? = null,
    val trainer: Int? = null,
    @SerialName("workout_type")
    val workoutType: Int? = null,
    val date: String? = null,
    val timezone: String? = null,
    val type: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val status: String? = null,
    val utcDate: String? = null,
    val countries: List<Country> = emptyList(),
    val regions: List<Region> = emptyList(),
    val tiles: List<Tile> = emptyList()
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

@Serializable
data class Achievement(
    val id: Int,
    @SerialName("achieved_at")
    val achievedAt: String
)

@Serializable
data class AchievementsResponse(
    val achievements: List<Achievement>
)