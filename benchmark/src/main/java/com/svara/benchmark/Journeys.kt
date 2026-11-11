package com.svara.benchmark

import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val PACKAGE = "com.svara.app"
private const val TIMEOUT_MS = 10_000L
private const val SHORT_MS = 2_000L

/** The display names the theme chooser shows, in order. */
val PRESETS = listOf("ETHEREAL", "Halo", "SOLACE", "OCTAVE", "ZENITH")

private const val SHORT_WAIT_MS = 1_000L

/** The benchmark build has no UI for granting permission, so grant it from the shell. */
fun MacrobenchmarkScope.grantAudioPermission() = grantAudioPermission(device)

private fun grantAudioPermission(device: UiDevice) {
    val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
    device.executeShellCommand("pm grant $PACKAGE $permission")
    if (Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
}

/**
 * Before cold-start runs: launch once outside measurement and pass onboarding, so every measured
 * start lands on Home. Uses the instrumentation's own device handle.
 */
fun passOnboardingOnce() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    grantAudioPermission(device)
    device.executeShellCommand("am start -W -n $PACKAGE/.MainActivity")
    skipOnboarding(device)
    device.pressHome()
}

fun MacrobenchmarkScope.skipOnboarding() = skipOnboarding(device)

private fun skipOnboarding(device: UiDevice) {
    if (device.wait(Until.hasObject(By.text("Skip")), SHORT_MS)) {
        device.findObject(By.text("Skip"))?.click()
        device.wait(Until.hasObject(By.text("Home")), SHORT_MS)
    }
    device.waitForIdle()
}

/** Launch (warm or first) and land on Home with onboarding out of the way. */
fun MacrobenchmarkScope.prepare() {
    grantAudioPermission()
    startActivityAndWait()
    skipOnboarding()
    goHome()
}

fun MacrobenchmarkScope.goHome() {
    val tab = device.findObject(By.res("tab_HOME")) ?: device.findObject(By.text("Home"))
    tab?.click()
    device.waitForIdle()
}

/** Settings: ETHEREAL's bottom-bar tab, or the settings icon on a layout preset's Home. */
fun MacrobenchmarkScope.openSettings() {
    goHome()
    val button = device.findObject(By.desc("Settings")) ?: device.findObject(By.text("Settings"))
    button?.click()
    device.wait(Until.hasObject(By.textContains(" · ")), TIMEOUT_MS)
}

/** Settings → Appearance; the Theme section (open by default) holds the preset cards. */
fun MacrobenchmarkScope.openThemeChooser() {
    openSettings()
    device.findObject(By.textContains(" · "))?.click()
    device.wait(Until.hasObject(By.text("Theme")), TIMEOUT_MS)
    if (!device.wait(Until.hasObject(By.desc("ETHEREAL preview")), SHORT_WAIT_MS)) {
        device.findObject(By.text("Theme"))?.click()
        device.wait(Until.hasObject(By.desc("ETHEREAL preview")), TIMEOUT_MS)
    }
}

/** Taps a preset card in the open theme chooser, scrolling to it first. */
fun MacrobenchmarkScope.tapPreset(name: String) {
    val selector = By.text(name)
    if (!device.hasObject(selector)) {
        device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
    }
    device.findObject(selector)?.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.choosePreset(name: String) {
    openThemeChooser()
    tapPreset(name)
    goHome()
}

/** The full Songs list in whatever preset is active. */
fun MacrobenchmarkScope.openLibrary() {
    goHome()
    val libraryTab = device.findObject(By.res("tab_LIBRARY"))
    if (libraryTab != null) {
        libraryTab.click()
        device.wait(Until.hasObject(By.text("Songs")), SHORT_MS)
        // Halo and ZENITH: "Songs" pill; SOLACE: "Songs" row; OCTAVE: "On device" pill.
        (device.findObject(By.text("Songs")) ?: device.findObject(By.text("On device")))?.click()
    } else {
        device.findObject(By.text("Songs"))?.click()
    }
    device.wait(Until.hasObject(By.res("songs_list")), TIMEOUT_MS)
    // Wait for the first page of songs, not just the empty list.
    device.wait(Until.hasObject(By.res("song_row")), TIMEOUT_MS)
}

fun MacrobenchmarkScope.scrollSongs(flings: Int = 4) {
    val list = device.findObject(By.res("songs_list")) ?: return
    // Keep the swipe away from the edges so it doesn't trigger system back gestures.
    list.setGestureMargin(device.displayWidth / 5)
    repeat(flings) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.startPlaybackFromLibrary() {
    if (device.hasObject(By.res("mini_player"))) return
    device.findObject(By.res("song_row"))?.click()
    device.wait(Until.hasObject(By.res("mini_player")), TIMEOUT_MS)
}

/** Opens Now Playing from the mini-player (which full-screen Now Playing hides) and closes it. */
fun MacrobenchmarkScope.openAndCloseNowPlaying() {
    device.findObject(By.res("mini_player"))?.click()
    device.wait(Until.gone(By.res("mini_player")), TIMEOUT_MS)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.res("mini_player")), TIMEOUT_MS)
}
