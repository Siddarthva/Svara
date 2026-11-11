package com.svara.app.playback.rituals

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.svara.app.domain.model.AppSettings
import com.svara.app.playback.UserMessages

/**
 * Listening rituals that react to the device: resume when headphones come back, stop when a
 * Bluetooth device goes away, and pause after a long stretch of continuous playback. All are off
 * by default and only active while the playback service is alive.
 */
class RitualsController(
    private val context: Context,
    private val player: Player,
    private val settings: () -> AppSettings,
    private val messages: UserMessages,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var pausedByDisconnect = false
    private var continuousPlayMs = 0L

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (!settings().resumeOnHeadphones || !pausedByDisconnect) return
            if (added.none { it.isSink && it.type in HEADPHONE_TYPES }) return
            if (inCall()) return
            pausedByDisconnect = false
            player.play()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (!settings().stopOnBluetoothDisconnect) return
            if (removed.none { it.isSink && it.type in BLUETOOTH_TYPES }) return
            // Noisy-audio handling has already paused; stopping also releases the notification.
            player.pause()
            player.stop()
            pausedByDisconnect = false
            messages.emit("Stopped: Bluetooth device disconnected")
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) pausedByDisconnect = true
        if (playWhenReady) pausedByDisconnect = false
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) continuousPlayMs = 0
    }

    /** Called once a second while playing. */
    fun onPlayingTick(elapsedMs: Long) {
        val limit = settings().autoPauseMinutes
        if (limit <= 0) return
        continuousPlayMs += elapsedMs
        if (continuousPlayMs >= limit * 60_000L) {
            continuousPlayMs = 0
            player.pause()
            messages.emit("Paused after $limit minutes of playback")
        }
    }

    private fun inCall(): Boolean =
        audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

    private companion object {
        val BLUETOOTH_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        val HEADPHONE_TYPES = BLUETOOTH_TYPES + setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
