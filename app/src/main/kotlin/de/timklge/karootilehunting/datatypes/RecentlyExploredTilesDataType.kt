package de.timklge.karootilehunting.datatypes

import android.content.Context
import de.timklge.karootilehunting.datastores.exploredTilesDataStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecentlyExploredTilesDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-tilehunting", "explored_tiles_trip") {
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            applicationContext.exploredTilesDataStore.data.collect { exploredTiles ->
                val count = exploredTiles.recentlyExploredTilesCount
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId,
                            mapOf(DataType.Field.SINGLE to count.toDouble())
                        )
                    )
                )
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }
}