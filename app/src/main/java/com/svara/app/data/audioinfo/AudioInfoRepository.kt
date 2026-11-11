package com.svara.app.data.audioinfo

import androidx.core.net.toUri
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.model.AudioInfo
import com.svara.app.domain.model.OutputDeviceInfo
import com.svara.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File properties come from the file's own audio track; output capabilities come from the
 * connected device. Anything the platform doesn't report stays null and shows as "Not reported".
 */
@Singleton
class AudioInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun fileInfo(song: Song): AudioInfo = withContext(io) {
        var mime: String? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitrate: Int? = null
        var bitDepth: Int? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, song.contentUri.toUri(), null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!m.startsWith("audio/")) continue
                mime = m
                sampleRate = f.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                bitrate = f.intOrNull(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }
                bitDepth = f.intOrNull("bits-per-sample")
                    ?: f.intOrNull(MediaFormat.KEY_PCM_ENCODING)?.let(::pcmBits)
                break
            }
        } catch (_: Exception) {
            // Unreadable file: every field stays "Not reported".
        } finally {
            extractor.release()
        }
        AudioInfo(
            format = song.fileName.substringAfterLast('.', "").uppercase().ifEmpty { null },
            codec = mime?.let(::codecName),
            bitrate = bitrate,
            calculatedBitrate = if (song.durationMs > 0 && song.sizeBytes > 0) (song.sizeBytes * 8_000 / song.durationMs).toInt() else null,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            durationMs = song.durationMs.takeIf { it > 0 },
            sizeBytes = song.sizeBytes.takeIf { it > 0 },
            path = song.dataPath ?: "${song.volume}/${song.relativePath}${song.fileName}",
        )
    }

    fun outputDevices(): List<OutputDeviceInfo> {
        val am = context.getSystemService(AudioManager::class.java)
        val active = if (Build.VERSION.SDK_INT >= 33) {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            am.getAudioDevicesForAttributes(attrs).map { it.id }.toSet()
        } else emptySet()
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in MEDIA_OUTPUT_TYPES }
            .map { d ->
                OutputDeviceInfo(
                    name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeName(d.type),
                    type = typeName(d.type),
                    sampleRates = d.sampleRates.toList().sorted(),
                    channelCounts = d.channelCounts.toList().sorted(),
                    encodings = d.encodings.map(::encodingName).distinct(),
                    isActiveRoute = d.id in active,
                )
            }
            .sortedByDescending { it.isActiveRoute }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    companion object {
        private val LOSSLESS = setOf("FLAC", "ALAC", "PCM")
        private val STANDARD_KBPS = listOf(64, 96, 128, 160, 192, 224, 256, 320)

        /**
         * The short quality badge on Now Playing: "FLAC", "FLAC 24-bit", "WAV", "MP3 320",
         * "AAC 256". Lossy rates within 3 % of a standard rate snap to it (averages drift).
         * Null when the file reports nothing usable.
         */
        fun badge(info: AudioInfo): String? {
            val codec = info.codec ?: return info.format
            if (codec in LOSSLESS) {
                val name = if (codec == "PCM") info.format ?: "PCM" else codec
                return if ((info.bitDepth ?: 0) >= 24) "$name ${info.bitDepth}-bit" else name
            }
            val kbps = (info.bitrate ?: info.calculatedBitrate)?.let { it / 1000 } ?: return codec
            val snapped = STANDARD_KBPS.firstOrNull { kotlin.math.abs(it - kbps) <= it * 0.03 } ?: kbps
            return "$codec $snapped"
        }

        fun codecName(mime: String): String = when (mime) {
            "audio/mpeg" -> "MP3"
            "audio/flac" -> "FLAC"
            "audio/mp4a-latm" -> "AAC"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/alac" -> "ALAC"
            "audio/raw" -> "PCM"
            "audio/ac3" -> "AC-3"
            "audio/eac3" -> "E-AC-3"
            "audio/amr-wb" -> "AMR-WB"
            "audio/3gpp" -> "AMR-NB"
            else -> mime.removePrefix("audio/").uppercase()
        }

        fun pcmBits(encoding: Int): Int? = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> null
        }

        private val MEDIA_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
        ) + (if (Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) else emptySet())

        fun typeName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out"
            else -> if (Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) "Bluetooth LE" else "Other"
        }

        fun encodingName(encoding: Int): String = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
            AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
            AudioFormat.ENCODING_AC3 -> "AC-3"
            AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
            AudioFormat.ENCODING_DTS -> "DTS"
            AudioFormat.ENCODING_AAC_LC -> "AAC"
            AudioFormat.ENCODING_MP3 -> "MP3"
            AudioFormat.ENCODING_OPUS -> "Opus"
            else -> "Encoding $encoding"
        }
    }
}
