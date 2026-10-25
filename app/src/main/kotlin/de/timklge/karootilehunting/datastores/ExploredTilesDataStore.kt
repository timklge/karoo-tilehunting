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

val Context.exploredTilesDataStore: DataStore<ExploredTiles> by dataStore(
    fileName = "explored_tiles.pb",
    serializer = ExploredTilesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { ExploredTilesSerializer.defaultValue }
)

