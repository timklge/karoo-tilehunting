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

package de.timklge.karootilehunting.services

import android.util.Log
import de.timklge.karootilehunting.KarooTilehuntingExtension
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.net.URLEncoder

data class ActivitiesWithLines(val activities: List<Activity>, val lines: List<Line>?)

@Suppress("BlockingMethodInNonBlockingContext")
class StatshuntersTilesProvider(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
) {
    suspend fun requestTiles(shareCode: String): Flow<ActivitiesWithLines> = flow {
        var page = 1
        do {
            Log.d(KarooTilehuntingExtension.TAG, "Requesting page $page...")

            val newActivities = requestTilePage(shareCode, page)
            val newLines = try {
                requestLinePage(shareCode, page)
            } catch(e: HttpDownloadError){
                Log.e(KarooTilehuntingExtension.TAG, "Failed to download lines", e)

                null
            }

            emit(ActivitiesWithLines(newActivities.activities, newLines?.activities))
            page += 1
        } while (newActivities.activities.size >= newActivities.meta.limit)
    }

    private suspend fun requestTilePage(shareCode: String, page: Int = 1): ActivityListResponse {
        try {
            val shareCodeUrlEncoded = URLEncoder.encode(shareCode, "utf-8")
            val url = "https://www.statshunters.com/share/${shareCodeUrlEncoded}/api/activities?page=${page}"

            Log.d(KarooTilehuntingExtension.TAG, "Http request to ${url}...")

            return client.get(url).body<ActivityListResponse>().also {
                Log.d(KarooTilehuntingExtension.TAG, "Parsed activity response with ${it.activities.size} activities")
            }
        } catch (e: ClientRequestException) {
            throw HttpDownloadError(e.response.status.value, e.message)
        } catch (e: Exception) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to fetch tiles", e)
            throw e
        }
    }

    private suspend fun requestLinePage(shareCode: String, page: Int = 1): LinesListResponse {
        try {
            val shareCodeUrlEncoded = URLEncoder.encode(shareCode, "utf-8")
            val url = "https://www.statshunters.com/share/${shareCodeUrlEncoded}/api/activities/lines?page=${page}"

            Log.d(KarooTilehuntingExtension.TAG, "Http lines request to ${url}...")

            return client.get(url).body<LinesListResponse>().also {
                Log.d(KarooTilehuntingExtension.TAG, "Parsed activity response with ${it.activities.size} activities")
            }
        } catch (e: ClientRequestException) {
            throw HttpDownloadError(e.response.status.value, e.message)
        } catch (e: Exception) {
            Log.e(KarooTilehuntingExtension.TAG, "Failed to fetch lines", e)
            throw e
        }
    }
}