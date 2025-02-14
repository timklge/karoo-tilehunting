package de.timklge.karootilehunting.datastores

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.data.UserPreferences
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {

    override val defaultValue: UserPreferences = UserPreferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        try {
            return UserPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to read protobuf")
            return UserPreferences.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) =
        t.writeTo(output)
}

val Context.userPreferencesDataStore: DataStore<UserPreferences> by dataStore(fileName = "user_preferences.pb",
    serializer = UserPreferencesSerializer
)
