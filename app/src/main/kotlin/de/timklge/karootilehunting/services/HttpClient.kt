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
