package de.timklge.karootilehunting.services

import android.util.Log
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.data.Badge
import de.timklge.karootilehunting.data.Badges
import de.timklge.karootilehunting.data.GpsCoords
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import java.net.URLEncoder

@Suppress("BlockingMethodInNonBlockingContext")
class StatshuntersBadgesProvider(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
) {
    private suspend fun requestAvailableBadges(): BadgeResponse {
        val url = "https://www.statshunters.com/api/badges"

        Log.d(KarooTilehuntingExtension.TAG, "Http request to ${url}...")

        return client.get(url).body<BadgeResponse>().apply {
            Log.d(KarooTilehuntingExtension.TAG, "Parsed available badge response with ${badges.size} badges")
        }
    }

    suspend fun requestBadges(shareCode: String): Badges {
        try {
            val availableBadges = requestAvailableBadges().badges

            val shareCodeUrlEncoded = URLEncoder.encode(shareCode, "utf-8")
            val url = "https://www.statshunters.com/share/${shareCodeUrlEncoded}/api/achievements"

            Log.d(KarooTilehuntingExtension.TAG, "Http request to ${url}...")

            val call = client.get(url)
            if (!call.status.isSuccess()) {
                if (call.status.value == 403 || call.status.value == 401){
                    return Badges.newBuilder().setLastDownloadedAt(System.currentTimeMillis()).setError("Access denied").build()
                } else {
                    return Badges.newBuilder().setLastDownloadedAt(System.currentTimeMillis()).setError("Failed (${call.status.value})").build()
                }
            }

            val achievementsResponse = call.body<AchievementsResponse>().also {
                Log.d(KarooTilehuntingExtension.TAG, "Parsed achievements response with ${it.achievements.size} achievements")
            }
            val achievedBadges = achievementsResponse.achievements.associate { it.id to it.achievedAt }

            val badgeList = availableBadges.sortedBy { it.id }.map {
                val achievedAt = achievedBadges[it.id]
                val coordinates = if (it.coor is JsonArray) {
                    val lng = it.coor[0].toString().toDoubleOrNull()
                    val lat = it.coor[1].toString().toDoubleOrNull()
                    if (lat != null && lng != null) {
                        GpsCoords.newBuilder()
                            .setLatitude(lat)
                            .setLongitude(lng)
                            .build()
                    } else null
                } else null

                Badge.newBuilder()
                    .setId(it.id)
                    .setName(it.name)
                    .setInfo(it.info)
                    .apply { if (coordinates != null) setCoordinates(coordinates) }
                    .apply { if (achievedAt != null) setAchievedAt(achievedAt) }
                    .build()
            }

            return Badges.newBuilder()
                .addAllBadges(badgeList)
                .setLastDownloadedAt(System.currentTimeMillis())
                .build()
        } catch (e: Exception) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to fetch achievements", e)
            throw e
        }
    }
}