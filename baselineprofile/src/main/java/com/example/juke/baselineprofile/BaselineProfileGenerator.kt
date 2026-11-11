package com.example.juke.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/** Records the cold-start and first-navigation paths into the app's baseline profile. */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndTabs() = rule.collect(packageName = "com.example.juke", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Search")), 5_000)
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        listOf("Search", "Library", "Home").forEach { tab ->
            device.findObject(By.desc(tab))?.click()
            device.waitForIdle()
        }
    }
}
