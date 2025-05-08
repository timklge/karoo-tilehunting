package de.timklge.karootilehunting.services

import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.datastores.achievementsDataStore
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BadgeDrawService(private val karooSystem: KarooSystemServiceProvider,
                       private val applicationContext: Context
) {
    val badgeFlow = applicationContext.achievementsDataStore.data
    val previouslyDrawnBadges = mutableSetOf<String>()

    fun startJob(emitter: Emitter<MapEffect>): Job {
        val drawJob = CoroutineScope(Dispatchers.IO).launch {
            previouslyDrawnBadges.chunked(100).forEach { chunk ->
                emitter.onNext(HideSymbols(chunk))
            }
            previouslyDrawnBadges.clear()

            badgeFlow.collect { badges ->
                val location = Point.fromLngLat(14.3092124, 51.1169619)

                val badgesInRange = badges.badgesList.filter {
                    it.hasCoordinates() && TurfMeasurement.distance(location, Point.fromLngLat(it.coordinates.longitude, it.coordinates.latitude), TurfConstants.UNIT_KILOMETERS) < 300
                }

                badgesInRange.chunked(100).forEach { chunk ->
                    val symbols = chunk.map {
                        Symbol.POI(
                            id = "badge-${it.id}",
                            lat = it.coordinates.latitude,
                            lng = it.coordinates.longitude,
                            name = it.name,
                            type = if (it.achievedAt.isNullOrBlank()) Symbol.POI.Types.GENERIC else Symbol.POI.Types.SUMMIT
                        )
                    }

                    Log.d(KarooTilehuntingExtension.TAG, "Drawing badges: ${symbols.size}")

                    emitter.onNext(ShowSymbols(symbols))

                    previouslyDrawnBadges.addAll(badgesInRange.map { "badge-${it.id}" })
                }
            }
        }
        return drawJob
    }
}