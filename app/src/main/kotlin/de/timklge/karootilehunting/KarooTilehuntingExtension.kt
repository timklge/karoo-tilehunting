package de.timklge.karootilehunting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import de.timklge.karootilehunting.data.GpsCoords
import de.timklge.karootilehunting.data.UserPreferences
import de.timklge.karootilehunting.datatypes.ExploredTilesDataType
import de.timklge.karootilehunting.datatypes.RecentlyExploredTilesDataType
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

class KarooTilehuntingExtension : KarooExtension("karoo-tilehunting", "1.0-beta1") {
    companion object {
        const val TAG = "karoo-tilehunting"
    }

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val statshuntersTilesProvider: StatshuntersTilesProvider by inject()

    private var updateLastKnownGpsPositionJob: Job? = null
    private var serviceJob: Job? = null
    private var tileDownloadJob: Job? = null
    private var addExploredTilesJob: Job? = null

    override val types by lazy {
        listOf(
            ExploredTilesDataType(karooSystem.karooSystemService, applicationContext),
            RecentlyExploredTilesDataType(karooSystem.karooSystemService, applicationContext)
        )
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "de.timklge.karootilehunting.DEBUG_CLEAR_TILES" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        applicationContext.exploredTilesDataStore.updateData {
                            it.toBuilder()
                                .clearRecentlyExploredTiles()
                                .setLastDownloadedAt(0)
                                .build()
                        }
                    }
                }
                "de.timklge.karootilehunting.DEBUG_GPS" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val lat = intent.getFloatExtra( "lat", 0.0f)
                        val lng = intent.getFloatExtra("lng", 0.0f)

                        if (lat != 0.0f && lng != 0.0f){
                            applicationContext.lastKnownGpsCoordsDataStore.updateData {
                                it.toBuilder().setLatitude(lat.toDouble()).setLongitude(lng.toDouble()).build()
                            }
                        }
                    }
                }
                // Add more actions as needed
            }
        }
    }

    data class ExploredTilesData(val exploredTiles: Set<Tile>, val recentlyExploredTiles: Set<Tile>)

    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.d(TAG, "Starting map effect")

        val gpsFlow = flow<GpsCoords> {
            applicationContext.lastKnownGpsCoordsDataStore.data.collect {
                Log.d(TAG, "Last known GPS position: ${it.latitude}, ${it.longitude}")
                emit(it)
            }

            /* TODO val initialPosition = applicationContext.lastKnownGpsCoordsDataStore.data.firstOrNull()
            if (initialPosition != null && initialPosition.latitude != 0.0 && initialPosition.longitude != 0.0){
                Log.d(TAG, "Using last known GPS position: ${initialPosition.latitude}, ${initialPosition.longitude}")
                emit(initialPosition)
            }

            karooSystem.stream<OnLocationChanged>().collect {
                emit(GpsCoords.newBuilder().setLatitude(it.lat).setLongitude(it.lng).build())
            } */
        }

        val tileClusterJob = CoroutineScope(Dispatchers.IO).launch {
            val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { (it.zoomLevel / 2).roundToInt() * 2 }

            val gpsTileFlow = gpsFlow.map { coordsToTile(it.latitude, it.longitude) }.throttle(10_000L)
            // val gpsTileFlow = flowOf(Tile(8798, 5483))

            var lastDrawnPolylines = setOf<ShowPolyline>()

            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data.map {
                val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()

                ExploredTilesData(exploredTiles, recentlyExploredTiles)
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

                    val square = getSquare(exploredTilesData.exploredTiles)
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

                    val squareClusterPolyline = getPolylineCommands(squareCluster, "square-cluster", R.color.blue).toSet()

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
                        getPolylineCommands(it, "clustered-explored-neighbours", R.color.green      )
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
                }
            }

        emitter.setCancellable {
            // TODO Hide all?

            Log.d(TAG, "Stopping map effect")

            tileClusterJob.cancel()
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // Register the broadcast receiver
        registerReceiver(
            broadcastReceiver,
            IntentFilter().apply {
                addAction("de.timklge.karootilehunting.DEBUG_GPS")
            }
        )

        mediaPlayer = MediaPlayer.create(this, R.raw.alert6)

        Log.d(TAG, "Starting karoo tilehunting extension")

        updateLastKnownGpsPositionJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.stream<OnLocationChanged>().throttle(60_000L).collect { event ->
                applicationContext.lastKnownGpsCoordsDataStore.updateData { location ->
                    location.toBuilder().setLatitude(event.lat).setLongitude(event.lng).build()
                }

                Log.d(TAG, "Updated last known GPS position: ${event.lat}, ${event.lng}")
            }
        }

        addExploredTilesJob = CoroutineScope(Dispatchers.IO).launch {
            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data
                .map {
                    val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()

                    ExploredTilesData(exploredTiles, recentlyExploredTiles)
                }

            val locationFlow = applicationContext.lastKnownGpsCoordsDataStore.data.map {
                Log.d(TAG, "Last known GPS position: ${it.latitude}, ${it.longitude}")

                OnLocationChanged(lat = it.latitude, lng = it.longitude, orientation = null)
            } // karooSystem.stream<OnLocationChanged>()
            val rideStateFlow = karooSystem.stream<RideState>()

            data class StreamData(val exploredTiles: ExploredTilesData, val location: OnLocationChanged, val rideState: RideState)

            combine(exploredTilesFlow, locationFlow, rideStateFlow) { exploredTiles, location, rideState -> StreamData(exploredTiles, location, rideState) }
                .filter { (_, _, rideState) -> rideState is RideState.Recording }
                .filter { (_, location, rideState) ->
                    val tile = coordsToTile(location.lat, location.lng)

                    val tileCorners = listOf(
                        CurrentCorner.TOP_LEFT.getCoords(tile),
                        CurrentCorner.TOP_RIGHT.getCoords(tile),
                        CurrentCorner.BOTTOM_RIGHT.getCoords(tile),
                        CurrentCorner.BOTTOM_LEFT.getCoords(tile)
                    )

                    val point = Point.fromLngLat(location.lng, location.lat)

                    // Convert margin from meters to degrees (approximate)
                    val margin = TurfConversion.convertLength(
                        20.0,
                        TurfConstants.UNIT_METERS,
                        TurfConstants.UNIT_DEGREES
                    )

                    // Check if point is inside the tile boundaries with margin
                    point.longitude() > tileCorners[0].longitude() + margin &&
                            point.longitude() < tileCorners[1].longitude() - margin &&
                            point.latitude() < tileCorners[0].latitude() - margin &&
                            point.latitude() > tileCorners[3].latitude() + margin
                }.filter { (exploredTiles, location) ->
                    val tile = coordsToTile(location.lat, location.lng)

                    !exploredTiles.exploredTiles.contains(tile) && !exploredTiles.recentlyExploredTiles.contains(tile)
                }.collect { (_, location) ->
                    Log.i(TAG, "New tile explored: ${location.lat}, ${location.lng}")

                    karooSystem.karooSystemService.dispatch(
                        InRideAlert(id = "newtile-${System.currentTimeMillis()}",
                            icon = R.drawable.crosshair,
                            title = "Tilehunting",
                            detail = "New tile explored",
                            autoDismissMs = 15_000L,
                            backgroundColor = R.color.lime,
                            textColor = R.color.black
                        )
                    )

                    karooSystem.karooSystemService.dispatch(
                        PlayBeepPattern(listOf(
                            PlayBeepPattern.Tone(4_000, 500),
                            PlayBeepPattern.Tone(4_500, 500),
                            PlayBeepPattern.Tone(4_000, 500)
                        ))
                    )

                    mediaPlayer?.start()

                    applicationContext.exploredTilesDataStore.updateData {
                        it.toBuilder().addRecentlyExploredTiles(de.timklge.karootilehunting.data.Tile.newBuilder().setX(coordsToTile(location.lat, location.lng).x).setY(coordsToTile(location.lat, location.lng).y).build()).build()
                    }
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
                .map { (sharecode, exploredTiles) -> StreamData(sharecode, exploredTiles.lastDownloadedAt) }
                .distinctUntilChanged()
                .filter { (_, lastDownloadedAt) -> lastDownloadedAt < System.currentTimeMillis() - 1000 * 60 * 60 * 24}
                .collect {
                    Log.d(TAG, "Starting tile download job")

                    applicationContext.exploredTilesDataStore.updateData {
                        it.toBuilder()
                            .clearExploredTiles()
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
                                Log.d(TAG, "New explored tile count: ${updatedExploredTiles.size}, $activityCount activities")

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

        addExploredTilesJob?.cancel()
        addExploredTilesJob = null

        unregisterReceiver(broadcastReceiver)

        super.onDestroy()
    }
}