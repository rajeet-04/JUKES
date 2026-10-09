package com.example.juke

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class JukeApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Build the heavy singletons (HTTP client, Room open + migrations, stream cache) on a
        // background thread so the first frame and PlaybackService.onCreate find them ready.
        Thread {
            com.example.juke.network.ApiClient.httpClient
            com.example.juke.database.MusicDatabase.getDatabase(this).openHelper.writableDatabase
            com.example.juke.services.PlaybackService.StreamCacheManager.getCache(this)
        }.apply { name = "startup-warmup"; priority = Thread.NORM_PRIORITY - 1; start() }
        // Progressive playback (Audio settings), on by default
        com.example.juke.network.JukesApi.progressiveEnabled =
            getSharedPreferences("music_settings_prefs", MODE_PRIVATE).getBoolean("progressive_playback", true)
        // Continue any Spotify import that was cut off when the app was closed, and again each time
        // the app comes back to the foreground (the process may have been kept alive with its
        // network blocked, which parks the import).
        val imports = com.example.juke.services.PlaylistImportManager.get(this)
        imports.resume()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) = imports.resume()
            }
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 25% of heap — keep aggressive in-memory caching for fast track switches
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // Increased from 2% → 5% so Spotify CDN thumbnails survive across sessions
                    .maxSizePercent(0.05)
                    .build()
            }
            // Always read from / write to both caches
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // Respect server Cache-Control but still serve stale from disk while revalidating
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}