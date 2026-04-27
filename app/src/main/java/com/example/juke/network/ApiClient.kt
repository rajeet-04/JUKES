package com.example.juke.network

import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * HTTP client configuration for all API requests.
 */
object ApiClient {

    val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                retryOnConnectionFailure(true)
            }
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
