/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

