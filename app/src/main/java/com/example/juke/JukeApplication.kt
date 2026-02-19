package com.example.juke

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.example.juke.analytics.AnalyticsManager

class JukeApplication : Application() {

    companion object {
        // const val POSTHOG_API_KEY = "YOUR_POSTHOG_API_KEY"
        const val POSTHOG_API_KEY="phc_8eQnEp9lKyfrHmUj87mxzXZrWyPDumI423B7SaLViwc"
        const val POSTHOG_HOST = "https://us.i.posthog.com"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize PostHog
        val config = PostHogAndroidConfig(
            apiKey = POSTHOG_API_KEY,
            host = POSTHOG_HOST
        )
        PostHogAndroid.setup(this, config)

        // Initialize Analytics Manager
        AnalyticsManager.initialize(this)
    }
}