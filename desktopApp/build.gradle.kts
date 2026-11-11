import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.FileSystems
import java.nio.file.Files
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
    "ui/icons/JukeIconPreview.kt", "database/MusicDatabase.kt", "push/Push.kt",
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
        // the sidebar drives the app's LibraryViewModel; a nav entry's own copy would never see what it opens
        filesMatching("**/LibraryScreen.kt") {
            filter { it.replace("libraryViewModel: LibraryViewModel = viewModel(),", "libraryViewModel: LibraryViewModel = viewModel(com.example.juke.desktop.DesktopViewModels),") }
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
                const val VERSION_NAME = "3.0.2-beta"
                const val VERSION_CODE = 30
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
    // tests import JNA on every OS (the Now Playing test runs on Linux CI too)
    testImplementation("net.java.dev.jna:jna:5.17.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.androidx.room.compiler)
    testImplementation(kotlin("test"))

    // Audio: JavaFX Media decodes AAC/m4a, MP3 and HLS, which is everything the backend serves
    for (m in listOf("base", "graphics", "media", "swing")) implementation("org.openjfx:javafx-$m:21.0.5:$fx")
}

tasks.test {
    useJUnit(); System.getProperty("ghostUri")?.let { systemProperty("ghostUri", it) }; System.getProperty("qsize")?.let { systemProperty("qsize", it) }; testLogging { showStandardStreams = true }
    // CI has no audio device, no display and no Spotify/backend access; these probes need all three, so they run on a dev Mac only
    if (System.getenv("CI") != null) filter {
        listOf("DeleteTest", "HttpStreamTest", "PlaybackSmokeTest", "SeekAccuracyTest", "RestoreTest", "MaximizeTest").forEach { excludeTestsMatching(it) }
    }
}
ksp { arg("room.schemaLocation", layout.buildDirectory.dir("schemas").get().asFile.path) }

// Skiko's and SQLite's jars carry native libraries for every platform (skiko's macos-arm64 jar even holds the
// 21 MB Intel one). The app only ever loads this build machine's, so drop the rest from the shrunk jars.
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    doLast {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
        val (skiko, sqlite) = when {
            os.contains("mac") -> if (arm) "macos-arm64" to "osx_arm64" else "macos-x64" to "osx_x64"
            os.contains("win") -> "windows-x64" to "windows_x64"
            else -> if (arm) "linux-arm64" to "linux_arm64" else "linux-x64" to "linux_x64"
        }
        val native = Regex("""\.(dylib|so|dll|jnilib)(\.sha256)?$""")
        outputs.files.asFileTree.matching { include("skiko-awt-runtime-*.jar", "sqlite-bundled-*.jar") }.forEach { jar ->
            FileSystems.newFileSystem(jar.toPath()).use { fs ->
                val drop = Files.walk(fs.getPath("/")).use { s ->
                    s.filter { native.containsMatchIn(it.toString()) && skiko !in it.toString() && sqlite !in it.toString() }.toList()
                }
                drop.forEach { Files.delete(it) }
                if (drop.isNotEmpty()) logger.lifecycle("${jar.name}: dropped ${drop.size} other-platform native files")
            }
        }
    }
}

// LZMA instead of jpackage's zlib: ~15 MB smaller download. Opens on macOS 10.15+, and the JDK 21 runtime needs 11+ anyway.
tasks.matching { it.name == "packageReleaseDmg" }.configureEach {
    doLast {
        outputs.files.asFileTree.matching { include("*.dmg") }.forEach { dmg ->
            val tmp = File(dmg.parentFile, "lzma-${dmg.name}")
            tmp.delete()
            providers.exec { commandLine("hdiutil", "convert", dmg.path, "-format", "ULMO", "-o", tmp.path, "-quiet") }.result.get()
            if (!tmp.renameTo(dmg.also { it.delete() })) error("couldn't replace ${dmg.name}")
            logger.lifecycle("${dmg.name}: ${dmg.length() / 1_048_576} MB (LZMA)")
        }
    }
}

compose.desktop.application {
    mainClass = "com.example.juke.desktop.MainKt"
    // a native crash (JNA, audio, graphics) kills the JVM; this is where it leaves its log (see NativeCrash.kt)
    // JVM defaults are G1 and a heap ceiling of a quarter of RAM, and a ~100 MB young generation whose pages all stay resident.
    // The live heap here is ~20 MB, so: leanest collector, a cap, and a young generation that fits the churn.
    jvmArgs += listOf("-XX:+UseSerialGC", "-Xmx768m", "-Xmn24m")
    jvmArgs += "-XX:ErrorFile=${if (os.isWindows) "C:/Windows/Temp" else "/tmp"}/juke-hs_err_%p.log"
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
        windows {
            iconFile.set(project.file("icons/JUKE.ico"))
            menu = true            // Start menu entry: searchable from Start; right-click it to pin to the taskbar
            menuGroup = "JUKE"
            shortcut = true        // desktop shortcut
            dirChooser = true
        }
        linux {
            iconFile.set(project.file("icons/JUKE.png"))
            shortcut = true // application-menu entry (found by the launcher search)
        }
        packageVersion = "3.0.2"
        modules("java.base", "java.desktop", "java.logging", "java.sql", "jdk.unsupported", "jdk.crypto.ec", "java.naming", "java.net.http", "java.xml", "java.management")
    }
}
