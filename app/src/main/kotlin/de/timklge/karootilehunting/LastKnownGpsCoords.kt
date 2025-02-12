package de.timklge.karootilehunting

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.timklge.karootilehunting.data.GpsCoords
import java.io.InputStream
import java.io.OutputStream

object GpsCoordsSerializer : Serializer<GpsCoords> {

    override val defaultValue: GpsCoords = GpsCoords.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): GpsCoords {
        try {
            return GpsCoords.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to read protobuf")
            return GpsCoords.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: GpsCoords, output: OutputStream) =
        t.writeTo(output)
}

val Context.lastKnownGpsCoordsDataStore: DataStore<GpsCoords> by dataStore(fileName = "last_known_gps.pb",
    serializer = GpsCoordsSerializer)
