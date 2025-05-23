package de.timklge.karootilehunting.services

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import de.timklge.karootilehunting.KarooTilehuntingExtension.Companion.TAG
import de.timklge.karootilehunting.KarooTilehuntingExtension.ExploredTilesData
import de.timklge.karootilehunting.R
import de.timklge.karootilehunting.Square
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.coordsToTile
import de.timklge.karootilehunting.data.UserPreferences
import de.timklge.karootilehunting.datastores.exploredTilesDataStore
import de.timklge.karootilehunting.datastores.userPreferencesDataStore
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.collections.map

class ExploreTilesService(private val karooSystem: KarooSystemServiceProvider) {
    companion object {
        val margin = TurfConversion.convertLength(
            5.0,
            TurfConstants.UNIT_METERS,
            TurfConstants.UNIT_DEGREES
        )
    }
    
    fun startJob(context: Context): Job {
        val mediaPlayer = MediaPlayer.create(context, R.raw.alert6)

        return CoroutineScope(Dispatchers.IO).launch {
            val exploredTilesFlow = context.exploredTilesDataStore.data
                .map {
                    val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val square = if(it.biggestSquareX != 0 && it.biggestSquareY != 0 && it.biggestSquareSize != 0) Square(it.biggestSquareX, it.biggestSquareY, it.biggestSquareSize) else null

                    ExploredTilesData(exploredTiles, recentlyExploredTiles, square)
                }

            val locationFlow = karooSystem.stream<OnLocationChanged>()
            val rideStateFlow = karooSystem.stream<RideState>()
            val settingsFlow = context.userPreferencesDataStore.data

            data class StreamData(val exploredTiles: ExploredTilesData, val location: OnLocationChanged, val rideState: RideState, val settings: UserPreferences)
            data class StreamDataTile(val exploredTiles: ExploredTilesData, val tile: Tile, val settings: UserPreferences)

            combine(exploredTilesFlow, locationFlow, rideStateFlow, settingsFlow) { exploredTiles, location, rideState, settings -> StreamData(exploredTiles, location, rideState, settings) }
                .filter { (_, _, rideState) -> rideState is RideState.Recording }
                .filter { (_, location, _) ->
                    val tile = coordsToTile(location.lat, location.lng)

                    tile.isInbounds(location.lng, location.lat)
                }.map { (exploredTiles, location, _, settings) ->
                    StreamDataTile(exploredTiles, coordsToTile(location.lat, location.lng), settings)
                }.filter { (exploredTiles, tile) ->
                    !exploredTiles.exploredTiles.contains(tile) && !exploredTiles.recentlyExploredTiles.contains(tile)
                }.distinctUntilChanged()
                .collect { (_, tile, settings) ->
                    Log.i(TAG, "New tile explored: ${tile.x}, ${tile.y}")

                    val intent = Intent("de.timklge.HIDE_POWERBAR").apply {
                        putExtra("duration", 20_000L)
                        putExtra("location", "top")
                    }

                    context.sendBroadcast(intent)

                    karooSystem.karooSystemService.dispatch(
                        InRideAlert(id = "newtile-${System.currentTimeMillis()}",
                            icon = R.drawable.crosshair,
                            title = "Tilehunting",
                            detail = "New tile explored",
                            autoDismissMs = 20_000L,
                            backgroundColor = R.color.lime,
                            textColor = R.color.black
                        )
                    )

                    if (settings.enableTileAlertSound){
                        if (!settings.enableCustomTileExploreSound || settings.customTileExploreSoundList.isEmpty()){
                            if (karooSystem.karooSystemService.hardwareType == HardwareType.K2){
                                karooSystem.karooSystemService.dispatch(
                                    PlayBeepPattern(listOf(
                                        PlayBeepPattern.Tone(4_000, 500),
                                        PlayBeepPattern.Tone(4_500, 500),
                                        PlayBeepPattern.Tone(4_000, 500)
                                    ))
                                )
                            } else {
                                karooSystem.karooSystemService.dispatch(
                                    PlayBeepPattern(listOf(
                                        PlayBeepPattern.Tone(1000, 150),
                                        PlayBeepPattern.Tone(1300, 150),
                                        PlayBeepPattern.Tone(1600, 200),
                                        PlayBeepPattern.Tone(1900, 300),
                                        PlayBeepPattern.Tone(1600, 150),
                                        PlayBeepPattern.Tone(1300, 150)
                                    ))
                                )
                            }
                        } else {
                            val playTones = PlayBeepPattern(
                                settings.customTileExploreSoundList.map { tune ->
                                    PlayBeepPattern.Tone(
                                        frequency = tune.freq,
                                        durationMs = tune.duration
                                    )
                                }
                            )

                            karooSystem.karooSystemService.dispatch(playTones)
                        }
                    }


                    mediaPlayer?.start()

                    context.exploredTilesDataStore.updateData { data ->
                        val exploredTiles = data.exploredTilesList.map { Tile(it.x, it.y) }.toSet() + tile
                        val recentlyExploredTiles = data.recentlyExploredTilesList.map { Tile(it.x, it.y) }.toSet() + tile
                        val updatedSquare = Square.getBiggestSquare(exploredTiles)

                        data.toBuilder()
                            .clearRecentlyExploredTiles()
                            .addAllRecentlyExploredTiles(recentlyExploredTiles.map { tile -> de.timklge.karootilehunting.data.Tile.newBuilder().setX(tile.x).setY(tile.y).build() })
                            .clearExploredTiles()
                            .addAllExploredTiles(exploredTiles.map { tile -> de.timklge.karootilehunting.data.Tile.newBuilder().setX(tile.x).setY(tile.y).build() })
                            .setBiggestSquareX(updatedSquare?.x ?: 0)
                            .setBiggestSquareY(updatedSquare?.y ?: 0)
                            .setBiggestSquareSize(updatedSquare?.size ?: 0)
                            .build()
                    }
                }
        }
    }
}