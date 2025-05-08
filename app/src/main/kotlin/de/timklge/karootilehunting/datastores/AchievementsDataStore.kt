package de.timklge.karootilehunting.datastores

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.data.Badges
import java.io.InputStream
import java.io.OutputStream

object AchievementsDataStore : Serializer<Badges> {
    override val defaultValue: Badges = Badges.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Badges {
        try {
            return Badges.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to read protobuf")
            return Badges.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: Badges, output: OutputStream) =
        t.writeTo(output)
}

val Context.achievementsDataStore: DataStore<Badges> by dataStore(
    fileName = "achievements.pb",
    serializer = AchievementsDataStore,
    corruptionHandler = ReplaceFileCorruptionHandler { AchievementsDataStore.defaultValue }
)

