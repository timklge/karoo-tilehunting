package de.timklge.karootilehunting

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.timklge.karootilehunting.data.ExploredTiles
import java.io.InputStream
import java.io.OutputStream

object ExploredTilesSerializer : Serializer<ExploredTiles> {

    override val defaultValue: ExploredTiles = ExploredTiles.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ExploredTiles {
        try {
            return ExploredTiles.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to read protobuf")
            return ExploredTiles.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: ExploredTiles, output: OutputStream) =
        t.writeTo(output)
}

val Context.exploredTilesDataStore: DataStore<ExploredTiles> by dataStore(fileName = "explored_tiles.pb",
    serializer = ExploredTilesSerializer)
