package de.timklge.karootilehunting

import android.util.Log
import de.timklge.karootilehunting.datatypes.ExploredTilesDataType
import de.timklge.karootilehunting.datatypes.RecentlyExploredTilesDataType
import de.timklge.karootilehunting.services.ClusterDrawService
import de.timklge.karootilehunting.services.ExploreTilesService
import de.timklge.karootilehunting.services.KarooSystemServiceProvider
import de.timklge.karootilehunting.services.TileDownloadService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class KarooTilehuntingExtension : KarooExtension("karoo-tilehunting", "1.0-beta6") {
    companion object {
        const val TAG = "karoo-tilehunting"
    }

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val tileDownloadService: TileDownloadService by inject()
    private val tileDrawer: ClusterDrawService by inject()
    private val exploreTilesService: ExploreTilesService by inject()

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

    data class ExploredTilesData(val exploredTiles: Set<Tile>, val recentlyExploredTiles: Set<Tile>, val square: Square?)

    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.d(TAG, "Starting map effect")

        tileDrawer.startJob(emitter)
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

        addExploredTilesJob = exploreTilesService.startJob(this)

        tileDownloadJob = tileDownloadService.startJob()
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

        super.onDestroy()
    }
}