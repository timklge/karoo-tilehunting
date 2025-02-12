package de.timklge.karootilehunting

import android.util.Log
import androidx.annotation.ColorRes
import de.timklge.karootilehunting.data.GpsCoords
import de.timklge.karootilehunting.data.UserPreferences
import de.timklge.karootilehunting.datatypes.ExploredTilesDataType
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

class KarooTilehuntingExtension : KarooExtension("karoo-tilehunting", "1.0") {
    companion object {
        const val TAG = "karoo-tilehunting"
    }

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val tilehuntingViewModelProvider: TilehuntingViewModelProvider by inject()
    private val statshuntersTilesProvider: StatshuntersTilesProvider by inject()

    private var updateLastKnownGpsPositionJob: Job? = null
    private var serviceJob: Job? = null
    private var tileDownloadJob: Job? = null

    override val types by lazy {
        listOf(
            ExploredTilesDataType(karooSystem.karooSystemService, tilehuntingViewModelProvider, applicationContext),
        )
    }

    @OptIn(FlowPreview::class)
    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.d(TAG, "Starting map effect")

        val gpsFlow = flow<GpsCoords> {
            val initialPosition = applicationContext.lastKnownGpsCoordsDataStore.data.firstOrNull()
            if (initialPosition != null && initialPosition.latitude != 0.0 && initialPosition.longitude != 0.0){
                Log.d(TAG, "Using last known GPS position: ${initialPosition.latitude}, ${initialPosition.longitude}")
                emit(initialPosition)
            }

            karooSystem.stream<OnLocationChanged>().collect {
                emit(GpsCoords.newBuilder().setLatitude(it.lat).setLongitude(it.lng).build())
            }
        }

        val tileClusterJob = CoroutineScope(Dispatchers.IO).launch {
            val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { (it.zoomLevel / 2).roundToInt() * 2 }

            val gpsTileFlow = gpsFlow.map { coordsToTile(it.latitude, it.longitude) }.throttle(10_000L)

            var lastDrawnPolylines = setOf<String>() // ids

            data class ExploredTilesData(val exploredTiles: Set<Tile>, val recentlyExploredTiles: Set<Tile>)

            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data.map {
                val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()

                ExploredTilesData(recentlyExploredTiles, exploredTiles)
            }

            val settingsFlow = applicationContext.userPreferencesDataStore.data

            data class StreamData(val exploredTiles: ExploredTilesData,
                                  val settings: UserPreferences,
                                  val centerTile: Tile,
                                  val mapZoom: Int)

            combine(exploredTilesFlow, settingsFlow, gpsTileFlow, mapZoomFlow) { exploredTiles, settings, centerTile, mapZoom -> StreamData(exploredTiles, settings, centerTile, mapZoom) }
                .distinctUntilChanged()
                .collect { (exploredTilesData, settings, centerTile, mapZoom) ->
                    val startTime = System.currentTimeMillis()
                    val tileLoadRadius = settings.tileDrawRange.let { if(it > 0) it else 3 }.coerceIn(2..5)
                    val showGridLines = !settings.hideGridLines
                    val viewSquare = Square(centerTile.x - tileLoadRadius, centerTile.y - tileLoadRadius, tileLoadRadius * 2)

                    val tileLoadRangeX = centerTile.x - tileLoadRadius..centerTile.x + tileLoadRadius
                    val tileLoadRangeY = centerTile.y - tileLoadRadius..centerTile.y + tileLoadRadius

                    val insetOffset = when (mapZoom) {
                        in 0..10 -> 150.0  // Reduced from 1000.0
                        11 -> 100.0        // Reduced from 500.0
                        12 -> 75.0         // Reduced from 250.0
                        13 -> 50.0         // Reduced from 100.0
                        14 -> 25.0         // Reduced from 50.0
                        15 -> 15.0         // Reduced from 25.0
                        16 -> 10.0
                        else -> 5.0
                    }

                    val exploredTilesInRange = exploredTilesData.exploredTiles
                        .filter { it.x in tileLoadRangeX && it.y in tileLoadRangeY }
                        .map { Tile(it.x, it.y) }.toSet()

                    Log.i(TAG, "Explored tiles: ${exploredTilesInRange.size} - Center Tile: ${centerTile} - Map Zoom: ${mapZoom}")

                    val square = getSquare(exploredTilesData.exploredTiles)
                    Log.i(TAG, "Largest square: $square")

                    val recentlyExploredTiles = exploredTilesData.recentlyExploredTiles
                        .filter { it.x in tileLoadRangeX && it.y in tileLoadRangeY }
                        .map { Tile(it.x, it.y) }.toSet()

                    val squareTiles = exploredTilesInRange.intersect((square?.getAllTiles() ?: emptySet()).toSet())
                    val exploredTilesNotInSquare = exploredTilesInRange - squareTiles - recentlyExploredTiles
                    val unexploredTiles = viewSquare.getAllTiles() - exploredTilesInRange - recentlyExploredTiles
                    Log.i(TAG, "Unexplored tiles: ${unexploredTiles.size}")

                    val squareCluster = clusterTiles(squareTiles).singleOrNull()
                    val clusteredExploredTiles = clusterTiles(exploredTilesNotInSquare)
                    val clusteredUnexploredTiles = clusterTiles(unexploredTiles)
                    val clusteredRecentlyExploredTiles = clusterTiles(recentlyExploredTiles)

                    val squareClusterGridLines = squareCluster?.getGridPolylines() ?: emptyList()
                    val clusteredExploredGridLines = clusteredExploredTiles.flatMap { it.getGridPolylines() }
                    val clusteredUnexploredGridLines = clusteredUnexploredTiles.flatMap { it.getGridPolylines() }
                    val clusteredRecentlyExploredGridLines = clusteredRecentlyExploredTiles.flatMap { it.getGridPolylines() }

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

                    val squareClusterPolyline = getPolylineCommands(squareCluster, "square-cluster", R.color.blue).toSet()

                    val clusteredExploredPolylines = clusteredExploredTiles.map {
                        getPolylineCommands(it, "clustered-explored", R.color.green)
                    }.flatten().toSet()

                    val clusteredUnexploredPolylines = clusteredUnexploredTiles.map {
                        getPolylineCommands(it, "clustered-unexplored", R.color.red)
                    }.flatten().toSet()

                    val clusteredRecentlyExploredPolylines = clusteredRecentlyExploredTiles.map {
                        getPolylineCommands(it, "clustered-recent", R.color.lime)
                    }.flatten().toSet()

                    val squareClusterGridPolylines = squareClusterGridLines.map { ShowPolyline(id = "square-cluster-grid-${it.hashCode()}",
                        encodedPolyline = it.toPolyline(5),
                        color = applicationContext.getColor(R.color.blue),
                        width = 5)
                    }.toSet()

                    val clusteredExploredGridPolylines = clusteredExploredGridLines.map { ShowPolyline(id = "clustered-explored-grid-${it.hashCode()}",
                        encodedPolyline = it.toPolyline(5),
                        color = applicationContext.getColor(R.color.green),
                        width = 5)
                    }.toSet()

                    val clusteredUnexploredGridPolylines = clusteredUnexploredGridLines.map {
                        ShowPolyline(id = "clustered-unexplored-grid-${it.hashCode()}",
                        encodedPolyline = it.toPolyline(5),
                        color = applicationContext.getColor(R.color.red),
                        width = 5)
                    }.toSet()

                    val clusteredRecentlyExploredGridPolylines = clusteredRecentlyExploredGridLines.map {
                        ShowPolyline(id = "clustered-recent-grid-${it.hashCode()}",
                        encodedPolyline = it.toPolyline(5),
                        color = applicationContext.getColor(R.color.lime),
                        width = 5)
                    }.toSet()

                    val gridLines = if (showGridLines){
                        clusteredExploredGridPolylines + clusteredUnexploredGridPolylines + squareClusterGridPolylines + clusteredRecentlyExploredGridPolylines
                    } else {
                        emptySet()
                    }

                    val polylines = gridLines + clusteredExploredPolylines + squareClusterPolyline + clusteredUnexploredPolylines + clusteredRecentlyExploredPolylines
                    val polylineIds = polylines.associateBy { it.id }

                    val newPolylines = polylines.filter { it.id !in lastDrawnPolylines }.toSet()
                    val droppedPolylines = lastDrawnPolylines.filter { !polylineIds.containsKey(it) }

                    newPolylines.forEach { emitter.onNext(it) }
                    droppedPolylines.forEach { emitter.onNext(HidePolyline(it)) }

                    lastDrawnPolylines = polylineIds.keys

                    Log.i(TAG, "Map update took ${System.currentTimeMillis() - startTime}ms")
                }
            }

        emitter.setCancellable {
            // TODO Hide all?

            Log.d(TAG, "Stopping map effect")

            tileClusterJob.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Starting karoo tilehunting extension")

        updateLastKnownGpsPositionJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.stream<OnLocationChanged>().throttle(60_000L).collect { event ->
                applicationContext.lastKnownGpsCoordsDataStore.updateData { location ->
                    location.toBuilder().setLatitude(event.lat).setLongitude(event.lng).build()
                }

                Log.d(TAG, "Updated last known GPS position: ${event.lat}, ${event.lng}")
            }
        }

        tileDownloadJob = CoroutineScope(Dispatchers.IO).launch {
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
                .filter { (_, exploredTiles) -> exploredTiles.lastDownloadedAt < System.currentTimeMillis() - 1000 * 60 * 60 * 24}
                .map { (sharecode, exploredTiles) -> StreamData(sharecode, exploredTiles.lastDownloadedAt) }
                .distinctUntilChanged()
                .collect {
                    Log.d(TAG, "Starting tile download job")

                    applicationContext.exploredTilesDataStore.updateData {
                        it.toBuilder()
                            .clearExploredTiles().clearRecentlyExploredTiles()
                            .setIsDownloading(true).setLastDownloadError("").setDownloadedActivities(0).build()
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
                                Log.d(TAG, "New explored tile count: ${updatedExploredTiles.size}, ${activityCount} activities")

                                exploredTiles.toBuilder()
                                    .setDownloadedActivities(activityCount)
                                    .clearExploredTiles()
                                    .addAllExploredTiles(updatedExploredTilesProto)
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

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null

        tileDownloadJob?.cancel()
        tileDownloadJob = null

        updateLastKnownGpsPositionJob?.cancel()
        updateLastKnownGpsPositionJob = null

        super.onDestroy()
    }
}