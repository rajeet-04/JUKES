package com.example.juke.analytics

import com.example.juke.account.Account
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import java.util.UUID

/** Wires the uploader to PostHog's /batch endpoint, the account email and the install id. */
object DailyReports {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** The account email when logged in, so every platform reports under one person; else the install id. */
    fun distinctId(context: android.content.Context): String =
        Account.email.value?.trim()?.lowercase()
            ?: AnalyticsManager.getInstance(context).installId()

    /** One id per install that outlives login and logout, so a report's uuid is the same on every retry. */
    private fun deviceId(context: android.content.Context): String {
        val prefs = context.applicationContext.getSharedPreferences("analytics_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("device_id", it).commit()) { "Could not save the report device ID" }
        }
    }

    /** Fire and forget; safe to call on every app open. */
    fun uploadPending(context: android.content.Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                DailyReportUploader(
                    dao = com.example.juke.database.MusicDatabase.getDatabase(app).playEventDao(),
                    pending = PrefsPendingBatches(app),
                    distinctId = { distinctId(app) },
                    deviceId = { deviceId(app) },
                    enabled = { AnalyticsManager.isEnabled(app) },
                    lock = Account.listeningUploadLock,
                    consentEpoch = AnalyticsManager::consentEpoch,
                    apiKey = { com.example.juke.BuildConfig.POSTHOG_API_KEY },
                    send = { body ->
                        val url = com.example.juke.BuildConfig.POSTHOG_HOST.trimEnd('/') + "/batch/"
                        com.example.juke.network.ApiClient.httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(body.toString())
                        }.status.isSuccess()
                    }
                ).uploadPending()
            }
        }
    }
}

/** Keeps the frozen batches across restarts, so a retry after a crash still repeats the same report. */
class PrefsPendingBatches(context: android.content.Context) : PendingBatches {
    private val prefs = context.applicationContext.getSharedPreferences("daily_report_pending", android.content.Context.MODE_PRIVATE)
    override fun get(key: String): List<Long>? = prefs.getString(key, null)?.split(',')?.mapNotNull { it.toLongOrNull() }
    override fun put(key: String, ids: List<Long>) {
        check(prefs.edit().putString(key, ids.joinToString(",")).commit()) { "Could not save the pending report" }
    }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
