package de.timklge.karootilehunting.services

import android.util.Log
import de.timklge.karootilehunting.KarooTilehuntingExtension
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

val jsonWithUnknownKeys = Json(builderAction = {
    ignoreUnknownKeys = true
})

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

    class HttpDownloadError(val httpError: Int, message: String): Throwable(message)

    private suspend fun requestTilePage(shareCode: String, page: Int = 1): ActivityListResponse {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(jsonWithUnknownKeys)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
            }
            defaultRequest {
                userAgent(KarooTilehuntingExtension.TAG)
            }
        }

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
        } finally {
            client.close()
        }
    }

    private suspend fun requestLinePage(shareCode: String, page: Int = 1): LinesListResponse {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(jsonWithUnknownKeys)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
            }
            defaultRequest {
                userAgent(KarooTilehuntingExtension.TAG)
            }
        }

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
        } finally {
            client.close()
        }
    }
}