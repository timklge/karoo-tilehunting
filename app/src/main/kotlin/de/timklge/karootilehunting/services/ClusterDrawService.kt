package de.timklge.karootilehunting.services

import android.content.Context
import android.util.Log
import androidx.annotation.ColorRes
import de.timklge.karootilehunting.Cluster
import de.timklge.karootilehunting.KarooTilehuntingExtension.Companion.TAG
import de.timklge.karootilehunting.KarooTilehuntingExtension.ExploredTilesData
import de.timklge.karootilehunting.R
import de.timklge.karootilehunting.Square
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.clusterTiles
import de.timklge.karootilehunting.coordsToTile
import de.timklge.karootilehunting.data.GpsCoords
import de.timklge.karootilehunting.data.UserPreferences
import de.timklge.karootilehunting.exploredTilesDataStore
import de.timklge.karootilehunting.lastKnownGpsCoordsDataStore
import de.timklge.karootilehunting.throttle
import de.timklge.karootilehunting.userPreferencesDataStore
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ClusterDrawService(private val karooSystem: KarooSystemServiceProvider,
                         private val applicationContext: Context) {

    private val gpsFlow = flow<GpsCoords> {
        val initialPosition = applicationContext.lastKnownGpsCoordsDataStore.data.firstOrNull()
        if (initialPosition != null && initialPosition.latitude != 0.0 && initialPosition.longitude != 0.0){
            Log.d(TAG, "Using last known GPS position: ${initialPosition.latitude}, ${initialPosition.longitude}")
            emit(initialPosition)
        }

        karooSystem.stream<OnLocationChanged>().collect {
            emit(GpsCoords.newBuilder().setLatitude(it.lat).setLongitude(it.lng).build())
        }
    }

    private var lastDrawnPolylines = setOf<ShowPolyline>()

    fun startJob(emitter: Emitter<MapEffect>): Job {
        val tileClusterJob = CoroutineScope(Dispatchers.IO).launch {
            val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { (it.zoomLevel / 2).roundToInt() * 2 }

            val gpsTileFlow = gpsFlow.map { coordsToTile(it.latitude, it.longitude) }.throttle(10_000L)

            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data.map {
                val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                val square = if(it.biggestSquareX != 0 && it.biggestSquareY != 0 && it.biggestSquareSize != 0) Square(it.biggestSquareX, it.biggestSquareY, it.biggestSquareSize) else null

                ExploredTilesData(exploredTiles, recentlyExploredTiles, square)
            }

            val settingsFlow = applicationContext.userPreferencesDataStore.data

            data class StreamData(val exploredTiles: ExploredTilesData,
                                  val settings: UserPreferences,
                                  val centerTile: Tile,
                                  val mapZoom: Int)

            combine(exploredTilesFlow, settingsFlow, gpsTileFlow, mapZoomFlow) { exploredTiles, settings, centerTile, mapZoom -> StreamData(exploredTiles, settings, centerTile, mapZoom) }
                .distinctUntilChanged()
                .collect { (exploredTilesData, settings, centerTile, mapZoom) ->
                    if (!settings.isDisabled){
                        val startTime = System.currentTimeMillis()
                        val tileLoadRadius = settings.tileDrawRange.let { if(it > 0) it else 3 }.coerceIn(2..5)
                        val showGridLines = !settings.hideGridLines
                        val viewSquare = Square(centerTile.x - tileLoadRadius, centerTile.y - tileLoadRadius, tileLoadRadius * 2)

                        val tileLoadRangeX = centerTile.x - tileLoadRadius..centerTile.x + tileLoadRadius
                        val tileLoadRangeY = centerTile.y - tileLoadRadius..centerTile.y + tileLoadRadius

                        val insetOffset = when (mapZoom) {
                            in 0..10 -> 175.0
                            11 -> 125.0
                            12 -> 75.0
                            13 -> 37.5
                            14 -> 25.0
                            15 -> 15.0
                            16 -> 10.0
                            else -> 5.0
                        }

                        val exploredTilesInRange = exploredTilesData.exploredTiles
                            .filter { it.x in tileLoadRangeX && it.y in tileLoadRangeY }
                            .map { Tile(it.x, it.y) }.toSet()

                        Log.i(TAG, "Explored tiles: ${exploredTilesInRange.size} - Center Tile: $centerTile - Map Zoom: $mapZoom")

                        val square = exploredTilesData.square
                        Log.i(TAG, "Largest square: $square")

                        val recentlyExploredTiles = exploredTilesData.recentlyExploredTiles
                            .filter { it.x in tileLoadRangeX && it.y in tileLoadRangeY }
                            .map { Tile(it.x, it.y) }.toSet()

                        val squareTiles = exploredTilesInRange.intersect((square?.getAllTiles() ?: emptySet()).toSet())
                        val exploredTilesWithNeighbours = (exploredTilesInRange - squareTiles).filter { it.isSurrounded(exploredTilesData.exploredTiles) }.toSet()
                        val otherExploredTiles = (exploredTilesInRange - squareTiles - recentlyExploredTiles - exploredTilesWithNeighbours).toSet()
                        val unexploredTiles = viewSquare.getAllTiles() - exploredTilesInRange - recentlyExploredTiles
                        Log.i(TAG, "Unexplored tiles: ${unexploredTiles.size}")

                        val squareCluster = clusterTiles(squareTiles).singleOrNull()
                        val clusteredExploredTilesWithNeighbours = clusterTiles(exploredTilesWithNeighbours)
                        val clusteredExploredTiles = clusterTiles(otherExploredTiles)
                        val clusteredUnexploredTiles = clusterTiles(unexploredTiles)
                        val clusteredRecentlyExploredTiles = clusterTiles(recentlyExploredTiles)

                        val squareClusterGridLines = squareCluster?.getGridPolylines() ?: emptyList()
                        val clusteredExploredGridLines = clusteredExploredTiles.flatMap { it.getGridPolylines() }
                        val clusteredUnexploredGridLines = clusteredUnexploredTiles.flatMap { it.getGridPolylines() }
                        val clusteredRecentlyExploredGridLines = clusteredRecentlyExploredTiles.flatMap { it.getGridPolylines() }
                        val clusteredExploredTilesWithNeighboursGridLines = clusteredExploredTilesWithNeighbours.flatMap { it.getGridPolylines() }

                        fun getPolylineCommands(cluster: Cluster?, identifier: String, @ColorRes color: Int): List<ShowPolyline> {
                            return cluster?.getPolyline(insetOffset)?.map { polyline ->
                                val str = polyline.toPolyline(5)
                                ShowPolyline(
                                    id = "${identifier}-${str.hashCode()}",
                                    encodedPolyline = str,
                                    color = applicationContext.getColor(color),
                                    width = 10
                                )
                            } ?: emptyList()
                        }

                        val squareClusterPolyline = getPolylineCommands(squareCluster, "square-cluster",
                            R.color.blue
                        ).toSet()

                        val clusteredExploredPolylines = clusteredExploredTiles.map {
                            getPolylineCommands(it, "clustered-explored", R.color.red)
                        }.flatten().toSet()

                        val clusteredUnexploredPolylines = clusteredUnexploredTiles.map {
                            getPolylineCommands(it, "clustered-unexplored", R.color.gray)
                        }.flatten().toSet()

                        val clusteredRecentlyExploredPolylines = clusteredRecentlyExploredTiles.map {
                            getPolylineCommands(it, "clustered-recent", R.color.lime)
                        }.flatten().toSet()

                        val clusteredExploredTilesWithNeighboursPolylines = clusteredExploredTilesWithNeighbours.map {
                            getPolylineCommands(it, "clustered-explored-neighbours", R.color.green)
                        }.flatten().toSet()

                        val squareClusterGridPolylines = squareClusterGridLines.map { ShowPolyline(id = "square-cluster-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.blue),
                            width = 5)
                        }.toSet()

                        val clusteredExploredGridPolylines = clusteredExploredGridLines.map { ShowPolyline(id = "clustered-explored-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.red),
                            width = 5)
                        }.toSet()

                        val clusteredUnexploredGridPolylines = clusteredUnexploredGridLines.map {
                            ShowPolyline(id = "clustered-unexplored-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.gray),
                                width = 5)
                        }.toSet()

                        val clusteredRecentlyExploredGridPolylines = clusteredRecentlyExploredGridLines.map {
                            ShowPolyline(id = "clustered-recent-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.lime),
                                width = 5)
                        }.toSet()

                        val clusteredExploredTilesWithNeighboursGridPolylines = clusteredExploredTilesWithNeighboursGridLines.map {
                            ShowPolyline(id = "clustered-explored-neighbours-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.green),
                                width = 5)
                        }.toSet()

                        val gridLines = if (showGridLines){
                            clusteredExploredGridPolylines + clusteredUnexploredGridPolylines +
                                    squareClusterGridPolylines + clusteredRecentlyExploredGridPolylines + clusteredExploredTilesWithNeighboursGridPolylines
                        } else {
                            emptySet()
                        }

                        val polylines = gridLines + clusteredExploredPolylines + squareClusterPolyline +
                                clusteredUnexploredPolylines + clusteredRecentlyExploredPolylines + clusteredExploredTilesWithNeighboursPolylines

                        val newPolylines = polylines - lastDrawnPolylines
                        val droppedPolylines = lastDrawnPolylines - polylines

                        Log.i(TAG, "Map update took ${System.currentTimeMillis() - startTime}ms - added ${newPolylines.size} polylines - removed ${droppedPolylines.size} polylines - ${polylines.size} total")

                        newPolylines.forEach { emitter.onNext(it) }
                        droppedPolylines.forEach { emitter.onNext(HidePolyline(it.id)) }

                        lastDrawnPolylines = polylines
                    } else {
                        Log.d(TAG, "Map is disabled - ${lastDrawnPolylines.size} previously drawn")

                        lastDrawnPolylines.forEach { emitter.onNext(HidePolyline(it.id)) }
                        lastDrawnPolylines = emptySet()
                    }
                }
        }

        emitter.setCancellable {
            Log.d(TAG, "Stopping map effect")

            tileClusterJob.cancel()
        }

        return tileClusterJob
    }
}