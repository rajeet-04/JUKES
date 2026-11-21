package com.example.juke

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.graphics.ColorUtils
import com.example.juke.models.Track
import com.example.juke.ui.components.HeroTrackCard
import com.example.juke.ui.components.player.PlaybackSeekSlider
import com.example.juke.ui.theme.ExtractedColors
import com.example.juke.ui.theme.JUKETheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BetaUiRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextPlayerOpensLyricsFromTickerAndDockReturnsToArt() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        lateinit var vm: com.example.juke.viewmodels.MusicViewModel
        compose.runOnUiThread { vm = com.example.juke.viewmodels.MusicViewModel(app) }
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                com.example.juke.ui.screens.PlayerScreen(vm, onDismiss = {}, onNavigateToArtist = {},
                    onNavigateToAlbum = {}, onShareTrack = {})
            }
        }
        compose.runOnIdle {
            vm.playTrackLocal(Track(uuid = "layout-fixture", title = "Layout test", artist = "Artist",
                durationSec = 120, syncedLyrics = "[00:00.00]First line\n[00:30.00]Second line"))
        }
        // The ticker under the artwork opens the lyrics view in the stage, and the controls stay put.
        // (Which fixture line it shows depends on where playback is, so find it by its action.)
        val ticker = androidx.compose.ui.test.SemanticsMatcher("opens lyrics") {
            it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == "Open lyrics"
        }
        compose.waitUntil(5_000) { compose.onAllNodes(ticker).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(ticker).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Adjust lyrics timing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback position").assertIsDisplayed()
        // Tapping the open dock tab goes back to the artwork.
        compose.onNode(androidx.compose.ui.test.hasText("Lyrics") and androidx.compose.ui.test.isSelected()).performClick()
        compose.onNodeWithContentDescription("Adjust lyrics timing").assertDoesNotExist()
        compose.onNode(ticker).assertIsDisplayed()
    }

    @Test fun seekCanBeAdjustedThroughAccessibility() {
        val position = mutableLongStateOf(0L)
        compose.setContent {
            JUKETheme { PlaybackSeekSlider(position.longValue, 100_000L, { position.longValue = it }) }
        }
        compose.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.runOnIdle { assertEquals(75_000L, position.longValue) }
        val bitmap = compose.onNodeWithContentDescription("Playback position").captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext
            .openFileOutput("beta-circular-seek.png", Context.MODE_PRIVATE).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
    }

    @Test fun unknownDurationDisablesSeek() {
        compose.setContent { JUKETheme { PlaybackSeekSlider(0L, 0L, {}) } }
        compose.onNodeWithContentDescription("Playback position").assertIsNotEnabled()
    }

    @Test fun recentTrackExposesAnAccessiblePlayAction() {
        var played = false
        compose.setContent {
            JUKETheme {
                HeroTrackCard(Track(uuid = "test", title = "Test song", artist = "Artist", durationSec = 100),
                    onClick = { played = true })
            }
        }
        compose.onNodeWithText("Test song").assertHasClickAction().performClick()
        compose.runOnIdle { assertTrue(played) }
    }

    @Test fun artworkAccentPreservesBothAppearanceAndContrast() {
        val dark = mutableStateOf(false)
        var scheme: ColorScheme? = null
        compose.setContent {
            JUKETheme(darkTheme = dark.value, dynamicColor = false,
                extractedColors = ExtractedColors(primary = Color(0xFF777777))) {
                scheme = androidx.compose.material3.MaterialTheme.colorScheme
            }
        }
        fun checkContrast(colors: ColorScheme) {
            assertTrue(ColorUtils.calculateContrast(colors.primary.toArgb(), colors.surface.toArgb()) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(colors.onPrimary.toArgb(), colors.primary.toArgb()) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(colors.onSurface.toArgb(), colors.surface.toArgb()) >= 4.5)
        }
        compose.runOnIdle {
            checkContrast(requireNotNull(scheme))
            assertTrue(ColorUtils.calculateLuminance(requireNotNull(scheme).background.toArgb()) > 0.5)
            dark.value = true
        }
        compose.runOnIdle {
            checkContrast(requireNotNull(scheme))
            assertTrue(ColorUtils.calculateLuminance(requireNotNull(scheme).background.toArgb()) < 0.1)
        }
    }
}
