package com.example.juke.account

import android.content.Context
import com.example.juke.BuildConfig
import com.example.juke.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import androidx.core.content.edit

/**
 * A JUKES account: email + password on the Worker (spotdown-kv-worker/src/account.ts), no email sending.
 * The password never leaves the device: it is stretched with PBKDF2 here (salt = the email) and only the
 * result is sent. Logging in gives this device its own token; [LibrarySync] and [com.example.juke.jam.Connect] use it.
 */
object Account {
    @Serializable private data class Auth(val email: String, val key: String, val device: String)
    @Serializable private data class Session(val token: String = "", val email: String = "", val hand: String = "", val error: String? = null)
    @Serializable private data class Keys(val key: String, val newKey: String = "")
    @Serializable private data class Err(val error: String? = null)

    private const val ITERATIONS = 120_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    internal val BASE = BuildConfig.SHARE_BASE_URL.trimEnd('/')

    private val _email = MutableStateFlow<String?>(null)
    /** The logged-in email, or null. */
    val email: StateFlow<String?> = _email
    @Volatile var token: String? = null; private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        token = p.getString("token", null)
        _email.value = if (token != null) p.getString("email", null) else null
    }

    /** Same input, same output on every device; the Worker salts and hashes it again. */
    internal fun stretch(email: String, password: String): String {
        val spec = PBEKeySpec(password.toCharArray(), "jukes:${email.trim().lowercase()}".toByteArray(), ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    val isDesktop get() = android.os.Build.MANUFACTURER == "Desktop"

    /** "Pixel 8" on a phone; the computer's name on desktop. */
    fun deviceName(): String = runCatching {
        if (isDesktop) java.net.InetAddress.getLocalHost().hostName.removeSuffix(".local").replace('-', ' ')
        else android.os.Build.MODEL
    }.getOrNull()?.takeIf { it.isNotBlank() }?.take(40) ?: "Device"

    internal fun HttpRequestBuilder.auth() {
        header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
        token?.let { header("Authorization", "Bearer $it") }
    }

    private fun message(raw: String, fallback: String) =
        runCatching { json.decodeFromString<Err>(raw).error }.getOrNull() ?: fallback

    /** Logs in (or creates the account). Null on success, else a message to show. */
    suspend fun signIn(c: Context, email: String, password: String, create: Boolean): String? {
        val e = email.trim().lowercase()
        if (!e.contains('@') || !e.substringAfter('@').contains('.')) return "Enter a valid email"
        if (password.length < 8) return "Use at least 8 characters for the password"
        return try {
            val (key, device) = withContext(Dispatchers.IO) { stretch(e, password) to deviceName() }
            val res = ApiClient.httpClient.post("$BASE/account/${if (create) "register" else "login"}") {
                header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(Auth(e, key, device)))
            }
            val raw = res.body<String>()
            if (!res.status.isSuccess()) return message(raw, "Couldn't ${if (create) "create the account" else "log in"} (${res.status.value})")
            val s = json.decodeFromString<Session>(raw)
            prefs(c).edit {
                putString("token", s.token).putString("email", s.email).putString("hand", s.hand)
            }
            load(c)
            null
        } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { "Couldn't reach JUKES. Check your connection." }
    }

    suspend fun signOut(c: Context) {
        try { ApiClient.httpClient.post("$BASE/account/logout") { auth() } } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        forget(c)
    }

    /** The token stopped working (logged out elsewhere, account deleted): drop it locally. */
    fun forget(c: Context) {
        prefs(c).edit {clear()}
        load(c)
    }

    /** Null on success, else a message. With [newPassword] null this deletes the account. */
    suspend fun passwordAction(c: Context, password: String, newPassword: String?): String? {
        val e = _email.value ?: return "Not logged in"
        if (newPassword != null && newPassword.length < 8) return "Use at least 8 characters for the new password"
        return try {
            val keys = withContext(Dispatchers.Default) { Keys(stretch(e, password), newPassword?.let { stretch(e, it) } ?: "") }
            val res = ApiClient.httpClient.post("$BASE/account/${if (newPassword == null) "delete" else "password"}") {
                auth(); contentType(ContentType.Application.Json); setBody(json.encodeToString(keys))
            }
            if (!res.status.isSuccess()) return message(res.body<String>(), "Something went wrong (${res.status.value})")
            if (newPassword == null) forget(c)
            null
        } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { "Couldn't reach JUKES. Check your connection." }
    }

    /** The account's library document, or null (offline / logged out). Clears the login on 401. */
    internal suspend fun getLibrary(c: Context): String? = try {
        val res = ApiClient.httpClient.get("$BASE/account/library") { auth() }
        if (res.status.value == 401) { forget(c); null } else if (res.status.isSuccess()) res.body<String>() else null
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

}
