package com.juke.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * HTTP client configuration for all API requests.
 * 
 * Uses Ktor HTTP client with:
 * - JSON content negotiation
 * - Request/response logging
 * - 2-minute timeout for downloads
 * - Custom User-Agent headers
 */
object ApiClient {
    
    val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 minutes for downloads
            connectTimeoutMillis = 30_000  // 30 seconds to establish connection
            socketTimeoutMillis = 60_000   // 1 minute socket timeout
        }
        
        defaultRequest {
            headers.append("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
            headers.append("Content-Type", "application/json")
        }
    }
}
