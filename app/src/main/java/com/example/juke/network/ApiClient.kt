package com.example.juke.network

import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * HTTP client configuration for all API requests.
 */
object ApiClient {

    /**
     * One OkHttp connection pool for the API calls and the player (see PlaybackService): the
     * player's first request to the backend reuses the connection that prepare/poll just opened,
     * so a stream starts without a fresh TCP + TLS handshake.
     */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttp
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
            // Never log credentials (installation tokens, session cookies).
            sanitizeHeader { name ->
                name.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    name.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                    name.equals(HttpHeaders.SetCookie, ignoreCase = true)
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        defaultRequest {
            headers.append(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
            )
            headers.append("Content-Type", "application/json")
        }
    }
}
