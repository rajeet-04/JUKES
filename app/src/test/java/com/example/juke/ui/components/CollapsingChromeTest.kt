package com.example.juke.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsingChromeTest {
    private val chrome = CollapsingChrome(thresholdPx = 100f)

    private fun scroll(dy: Float) {
        assertEquals(Offset.Zero,
            chrome.connection.onPostScroll(Offset(0f, dy), Offset.Zero, NestedScrollSource.UserInput))
    }

    @Test
    fun foldsAfterScrollingDownPastThreshold_andUnfoldsScrollingUp() {
        scroll(-60f); assertFalse(chrome.collapsed)   // under the threshold
        scroll(-50f); assertTrue(chrome.collapsed)    // 110 px down the list
        scroll(80f); assertTrue(chrome.collapsed)     // reversing, still under the threshold
        scroll(30f); assertFalse(chrome.collapsed)    // 110 px back up
    }

    @Test
    fun reversingResetsTheCount_andListEndsDoNothing() {
        scroll(-90f); scroll(20f); scroll(-90f)
        assertFalse(chrome.collapsed)                 // never 100 px in one direction
        scroll(0f); assertFalse(chrome.collapsed)     // nothing consumed at a list's end
        scroll(-20f); assertTrue(chrome.collapsed)
        chrome.expand(); assertFalse(chrome.collapsed)
    }
}
