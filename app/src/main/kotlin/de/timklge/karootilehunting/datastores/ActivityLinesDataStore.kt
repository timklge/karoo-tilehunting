package de.timklge.karootilehunting.datastores

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.data.PastActivities
import java.io.InputStream
import java.io.OutputStream

object ActivityLinesDataStore : Serializer<PastActivities> {

    override val defaultValue: PastActivities = PastActivities.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PastActivities {
        try {
            return PastActivities.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to read protobuf")
            return PastActivities.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: PastActivities, output: OutputStream) =
        t.writeTo(output)
}

val Context.activityLinesDataStore: DataStore<PastActivities> by dataStore(
    fileName = "activity_lines.pb",
    serializer = ActivityLinesDataStore,
    corruptionHandler = ReplaceFileCorruptionHandler { ActivityLinesDataStore.defaultValue }
)

