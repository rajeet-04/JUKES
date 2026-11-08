import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin { jvmToolchain(21) }

// Reference the Android sources directly (no drift); Android-only files are excluded below and
// re-implemented under src/main/kotlin.
val androidSrc = rootProject.file("app/src/main/java")
val androidOnly = listOf(
    "MainActivity.kt", "JukeApplication.kt", "services/UpdateManager.kt", "services/MusicService.kt.keep", "ui/theme/Glass.kt", "ui/theme/Type.kt",
    "services/PlaybackService.kt", "services/AutoLibrary.kt",
    "services/BoostAudioProcessor.kt", "services/EdgeSilenceProcessor.kt",
    "services/AudioEffectController.kt", "services/PlaylistBypassDataSource.kt",
    "services/ArtworkProvider.kt", "utils/HapticHelper.kt", "analytics/AnalyticsManager.kt",
    "ui/screens/YoutubeSignInScreen.kt",
    "ui/icons/JukeIconPreview.kt", "database/MusicDatabase.kt",
).map { "com/example/juke/$it" }
val stageAndroid by tasks.registering(Sync::class) {
    from(androidSrc) {
        androidOnly.forEach { exclude(it) }
        // every viewModel() goes through ours, which always uses the app factory (nav entries bring their own, which can't build AndroidViewModels on desktop)
        filesMatching("**/*.kt") {
            filter { it.replace("import androidx.lifecycle.viewmodel.compose.viewModel", "import com.example.juke.desktop.viewModel") }
        }
        // desktop's one search bar lives in the top bar
        filesMatching("**/SearchScreen.kt") {
            filter { it.replace("            SearchInput(", "            if (false) SearchInput(") }
        }
        // Android-only blur mask -> Skia's
        filesMatching("**/GlassNavigation.kt") {
            filter { it.replace("var librarySection by rememberSaveable { mutableStateOf(0) }", "var librarySection by rememberSaveable { mutableStateOf(if (uiState.selectedPlaylist != null) 1 else 0) }") }
            filter { it.replace("android.graphics.BlurMaskFilter(bloomPx, android.graphics.BlurMaskFilter.Blur.NORMAL)", "org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, bloomPx / 2f)") }
        }
    }
    into(layout.buildDirectory.dir("generated/androidSrc"))
}
sourceSets.main { kotlin.srcDir(stageAndroid) }

// BuildConfig, from the same local.properties the APK reads
val genBuildConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildconfig")
    val props = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
    fun p(k: String, d: String) = props.getProperty(k, d)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/example/juke/BuildConfig.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("""
            package com.example.juke
            object BuildConfig {
                const val DEBUG = false
                const val VERSION_NAME = "2.8.0-beta"
                const val VERSION_CODE = 26
                const val SPOTIFY_TOKEN_URL = "${p("SPOTIFY_TOKEN_URL", "https://spotdown-kv-worker.meek.workers.dev/spotify-token")}"
                const val SHARE_BASE_URL = "${p("SHARE_BASE_URL", "https://j.rajeet.in")}"
                const val SPOTIFY_TOKEN_KEY = "${p("SPOTIFY_TOKEN_KEY", "")}"
                const val POSTHOG_API_KEY = ""
                const val POSTHOG_HOST = ""
                const val JUKE_BACKEND_URL = "${p("JUKE_BACKEND_URL", "https://ms.rajeet.in")}"
                const val JUKE_BACKEND_KEY = "${p("JUKE_BACKEND_KEY", "unused")}"
                const val JUKE_BACKEND_V1 = ${p("JUKE_BACKEND_V1", "true")}
            }
        """.trimIndent())
    }
}
sourceSets.main { kotlin.srcDir(genBuildConfig) }

val os = org.gradle.internal.os.OperatingSystem.current()
val fx = when {
    os.isMacOsX -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
    os.isWindows -> "win"
    else -> "linux"
}

dependencies {
    implementation(compose.desktop.currentOs) // only this OS's Skiko natives
    implementation(compose.material3)
    implementation(libs.cmp.icons.extended)
    implementation(libs.jb.lifecycle.viewmodel)
    implementation(libs.jb.lifecycle.runtime.compose)
    implementation(libs.jb.navigation.compose)
    implementation(libs.haze)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.websockets)
    implementation(libs.gson)
    implementation("org.json:json:20240303")
    // Now Playing / media keys on macOS go through the Objective-C runtime; other OSes never load it.
    if (os.isMacOsX) implementation("net.java.dev.jna:jna:5.17.0") else compileOnly("net.java.dev.jna:jna:5.17.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.androidx.room.compiler)
    testImplementation(kotlin("test"))

    // Audio: JavaFX Media decodes AAC/m4a, MP3 and HLS, which is everything the backend serves
    for (m in listOf("base", "graphics", "media", "swing")) implementation("org.openjfx:javafx-$m:21.0.5:$fx")
}

tasks.test { useJUnit(); System.getProperty("ghostUri")?.let { systemProperty("ghostUri", it) }; System.getProperty("qsize")?.let { systemProperty("qsize", it) }; testLogging { showStandardStreams = true } }
ksp { arg("room.schemaLocation", layout.buildDirectory.dir("schemas").get().asFile.path) }

compose.desktop.application {
    mainClass = "com.example.juke.desktop.MainKt"
    buildTypes.release.proguard {
        isEnabled = true
        obfuscate = false // ponytail: obfuscation buys little size, costs debuggability
        optimize = false // shrinking is the win; the optimizer broke nothing here but risks reflective code
        configurationFiles.from("proguard-rules.pro")
    }
    nativeDistributions {
        // dmg only builds on mac, msi on windows, deb on linux: one command per OS
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "JUKE"
        macOS {
            iconFile.set(project.file("icons/JUKE.icns"))
            // juke:// links (the "Open in JUKES" button on shared playlists and Jam pages) open this app
            infoPlist {
                extraKeysRawXml = """
                    <key>CFBundleURLTypes</key>
                    <array><dict>
                        <key>CFBundleURLName</key><string>JUKE</string>
                        <key>CFBundleURLSchemes</key><array><string>juke</string></array>
                    </dict></array>
                """.trimIndent()
            }
        }
        windows { iconFile.set(project.file("icons/JUKE.ico")) }
        linux { iconFile.set(project.file("icons/JUKE.png")) }
        packageVersion = "1.0.0"
        modules("java.base", "java.desktop", "java.logging", "java.sql", "jdk.unsupported", "jdk.crypto.ec", "java.naming", "java.net.http", "java.xml", "java.management")
    }
}
