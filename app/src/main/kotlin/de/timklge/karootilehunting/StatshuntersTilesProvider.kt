package de.timklge.karootilehunting

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
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

class StatshuntersTilesProvider(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val context: Context,
) {
    suspend fun requestTiles(shareCode: String): Flow<List<Activity>> = flow {
        var page = 1
        do {
            Log.d(KarooTilehuntingExtension.TAG, "Requesting page $page of activities...")

            val newActivities = requestTilePage(shareCode, page)
            emit(newActivities.activities)
            page += 1
        } while (newActivities.activities.size >= newActivities.meta.limit)
    }

    class HttpDownloadError(val httpError: Int, message: String): Throwable(message)

    private suspend fun requestTilePage(shareCode: String, page: Int = 1): ActivityListResponse {
        return callbackFlow {
            val shareCodeUrlEncoded = withContext(Dispatchers.IO) { URLEncoder.encode(shareCode, "utf-8") }
            val url = "https://www.statshunters.com/share/${shareCodeUrlEncoded}/api/activities?page=${page}"

            Log.d(KarooTilehuntingExtension.TAG, "Http request to ${url}...")

            val listenerId = karooSystemServiceProvider.karooSystemService.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooTilehuntingExtension.TAG, "Accept-Encoding" to "gzip"),
                ),
            ) { event: OnHttpResponse ->
                if (event.state is HttpResponseState.Complete){
                    val completeEvent = (event.state as HttpResponseState.Complete)

                    try {
                        if (!completeEvent.error.isNullOrBlank() || completeEvent.statusCode !in 200..<300){
                            close(HttpDownloadError(completeEvent.statusCode, completeEvent.error ?: "Unknown error"))
                            return@addConsumer
                        }

                        val responseBody = completeEvent.body?.let { body ->
                            GZIPInputStream(body.inputStream()).bufferedReader().use { it.readText() }
                        } ?: error("Failed to read response")

                        Log.d(KarooTilehuntingExtension.TAG, "Http response event; size ${completeEvent.body?.size}")

                        val response = try {
                            jsonWithUnknownKeys.decodeFromString(ActivityListResponse.serializer(), responseBody)
                        } catch (e: Exception) {
                            Log.e(KarooTilehuntingExtension.TAG, "Failed to parse response: ${completeEvent.body}", e)
                            throw e
                        }

                        Log.d(KarooTilehuntingExtension.TAG, "Parsed activity response with ${response.activities.size} activities")

                        trySendBlocking(response)

                        close()
                    } catch(e: Throwable){
                        Log.e(KarooTilehuntingExtension.TAG, "Failed to process response", e)

                        close(e)
                    }
                }
            }
            awaitClose() {
                karooSystemServiceProvider.karooSystemService.removeConsumer(listenerId)
            }
        }.single()
    }
}