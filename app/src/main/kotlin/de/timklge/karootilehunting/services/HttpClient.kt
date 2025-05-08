package de.timklge.karootilehunting.services

import de.timklge.karootilehunting.KarooTilehuntingExtension
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val jsonWithUnknownKeys = Json(builderAction = {
    ignoreUnknownKeys = true
})

class HttpDownloadError(val httpError: Int, message: String): Throwable(message)

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
