package com.example.juke.push

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.juke.BuildConfig
import com.example.juke.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Announcements sent by hand from the terminal (tools/notify.ts) through Firebase Cloud Messaging.
 * Every install listens on two topics: "all", and its own version ("v2.8.0-beta"), so a message can go to
 * everyone or to one version only. Messages are data-only, so this app draws them (same look in the
 * foreground and background) on its own "Announcements" channel; tapping one opens its link, else the app.
 * Does nothing until the build has app/google-services.json (see app/build.gradle.kts).
 */
object Push {
    private const val TAG = "Push"
    const val CHANNEL = "announcements"
    val versionTopic = "v" + BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._~%-]"), "-")

    private fun configured(c: Context) = FirebaseApp.getApps(c).isNotEmpty()

    /** From Application.onCreate. Subscribing is idempotent; Firebase retries it when offline. */
    fun init(c: Context) {
        if (!configured(c)) return
        createChannel(c)
        val fm = FirebaseMessaging.getInstance()
        for (topic in listOf("all", versionTopic)) fm.subscribeToTopic(topic).addOnFailureListener { Log.w(TAG, "subscribe $topic failed", it) }
        // an update changes the version topic: leave the old one so version-targeted messages stop arriving
        val prefs = c.getSharedPreferences("push", Context.MODE_PRIVATE)
        prefs.getString("topic", null)?.takeIf { it != versionTopic }?.let { fm.unsubscribeFromTopic(it) }
        prefs.edit { putString("topic", versionTopic) }
    }

    /** Android 13+: ask once for the notification permission (from the main activity). */
    fun askPermissionOnce(a: Activity) {
        if (Build.VERSION.SDK_INT < 33 || !configured(a)) return
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = a.getSharedPreferences("push", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit { putBoolean("asked", true) }
        a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
    }

    private fun createChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "Announcements", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "News from JUKE: new versions, the desktop app and the like" }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    internal fun show(c: Context, title: String, body: String, link: String?) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return
        createChannel(c)
        val open = link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = (title + body).hashCode()
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (e: SecurityException) { Log.w(TAG, "no notification permission") }
    }
}

/** Receives the announcements; registered in the manifest. */
class JukeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val title = d["title"] ?: message.notification?.title ?: return
        Push.show(this, title, d["body"] ?: message.notification?.body.orEmpty(), d["link"]?.takeIf { it.isNotBlank() })
    }

    override fun onNewToken(token: String) { /* topics only: nothing to register */ }
}
