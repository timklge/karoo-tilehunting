package de.timklge.karootilehunting.services

import android.content.Context
import android.util.Log
import de.timklge.karootilehunting.KarooTilehuntingExtension.Companion.TAG
import de.timklge.karootilehunting.Square
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.exploredTilesDataStore
import de.timklge.karootilehunting.userPreferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TileDownloadService(private val applicationContext: Context, val statshuntersTilesProvider: StatshuntersTilesProvider) {
    fun startJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            applicationContext.exploredTilesDataStore.updateData {
                val updated = it.toBuilder().setIsDownloading(false).build()

                if (!it.lastDownloadError.isNullOrBlank()){
                    updated.toBuilder().setLastDownloadedAt(0).build()
                } else {
                    updated
                }
            }

            data class StreamData(val sharecode: String, val lastUpdatedAt: Long)

            val settingsCodeFlow = applicationContext.userPreferencesDataStore.data.map { it.statshuntersSharecode }.filter { !it.isNullOrBlank() }
            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data

            combine(settingsCodeFlow, exploredTilesFlow) { sharecode, exploredTiles -> sharecode to exploredTiles }
                .map { (sharecode, exploredTiles) -> StreamData(sharecode, exploredTiles.lastDownloadedAt) }
                .distinctUntilChanged()
                .filter { (_, lastDownloadedAt) -> lastDownloadedAt < System.currentTimeMillis() - 1000 * 60 * 60 * 24}
                .collect {
                    Log.d(TAG, "Starting tile download job")

                    applicationContext.exploredTilesDataStore.updateData {
                        it.toBuilder()
                            .clearExploredTiles()
                            .setIsDownloading(true)
                            .setLastDownloadError("")
                            .setDownloadedActivities(0)
                            .setBiggestSquareX(0)
                            .setBiggestSquareY(0)
                            .setBiggestSquareSize(0)
                            .build()
                    }

                    try {
                        var activityCount = 0
                        val sharecode = applicationContext.userPreferencesDataStore.data.first().statshuntersSharecode
                        statshuntersTilesProvider.requestTiles(sharecode.trim()).collect { activities ->
                            Log.d(TAG, "Received ${activities.size} activities")

                            applicationContext.exploredTilesDataStore.updateData { exploredTiles ->
                                val alreadyExploredTiles =
                                    exploredTiles.exploredTilesList.map { Tile(it.x, it.y) }.toSet()
                                val newTiles = activities.flatMap {
                                    it.tiles.map { Tile(it.x, it.y) }
                                }.toSet()
                                activityCount += activities.size
                                val updatedExploredTiles = alreadyExploredTiles + newTiles
                                val updatedExploredTilesProto = updatedExploredTiles.map {
                                    de.timklge.karootilehunting.data.Tile.newBuilder().setX(it.x).setY(it.y).build()
                                }
                                Log.d(TAG, "New explored tile count: ${updatedExploredTiles.size}, $activityCount activities")
                                val updatedSquare = Square.getBiggestSquare(updatedExploredTiles)
                                Log.d(TAG, "New square: $updatedSquare")

                                exploredTiles.toBuilder()
                                    .setDownloadedActivities(activityCount)
                                    .clearExploredTiles()
                                    .addAllExploredTiles(updatedExploredTilesProto)
                                    .setBiggestSquareX(updatedSquare?.x ?: 0)
                                    .setBiggestSquareY(updatedSquare?.y ?: 0)
                                    .setBiggestSquareSize(updatedSquare?.size ?: 0)
                                    .build()
                            }
                        }

                        applicationContext.exploredTilesDataStore.updateData {
                            it.toBuilder().setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    } catch(e: CancellationException){
                        Log.d(TAG, "Download job cancelled")
                    } catch(e: Throwable){
                        Log.e(TAG, "Failed to download tiles", e)

                        applicationContext.exploredTilesDataStore.updateData {
                            var errorMessage = e.message ?: "Unknown error"
                            if (e is StatshuntersTilesProvider.HttpDownloadError){
                                when (e.httpError) {
                                    401, 403 -> errorMessage = "Access denied"
                                    0 -> errorMessage = "No internet connection"
                                    404 -> errorMessage = "Not found"
                                }
                            }

                            it.toBuilder().setLastDownloadError(errorMessage).setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    }
                }
        }
    }
}