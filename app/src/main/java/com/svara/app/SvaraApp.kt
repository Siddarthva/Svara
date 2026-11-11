package com.svara.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.svara.app.data.appearance.DeviceConditions
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import android.os.StrictMode
import okio.Path.Companion.toOkioPath
import coil3.request.crossfade
import com.svara.app.data.artwork.ArtworkFetcher
import com.svara.app.data.artwork.ArtworkKeyer
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.playback.PlaybackController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SvaraApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var artworkLoader: ArtworkLoader
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var deviceConditions: DeviceConditions
    @Inject lateinit var themes: com.svara.app.data.appearance.ThemeRepository
    @Inject lateinit var customization: com.svara.app.data.appearance.CustomizationRepository
    @Inject lateinit var widgetUpdater: com.svara.app.widget.WidgetUpdater
    @Inject @com.svara.app.di.ApplicationScope lateinit var appScope: kotlinx.coroutines.CoroutineScope

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        com.svara.app.data.CrashLog.install(this)
        // Hold a controller only while the app is visible, so the service can stop when paused in the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                playbackController.connect()
                deviceConditions.start()
            }

            override fun onStop(owner: LifecycleOwner) {
                playbackController.disconnect()
                // Battery and thermal listeners only matter while the UI can show effects.
                deviceConditions.stop()
            }
        })
        // Widgets follow the theme: redraw them when the preset, colour or brightness changes (not on a timer).
        appScope.launch {
            kotlinx.coroutines.flow.combine(themes.resolved, customization.customization.map { it.brightness }) { t, b -> t to b }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    delay(WIDGET_APPEARANCE_DEBOUNCE_MS)
                    widgetUpdater.refresh()
                }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ArtworkKeyer())
                add(ArtworkFetcher.Factory(artworkLoader))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(ARTWORK_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    companion object {
        const val ARTWORK_DISK_CACHE_BYTES = 64L * 1024 * 1024
        const val WIDGET_APPEARANCE_DEBOUNCE_MS = 500L
    }
}
