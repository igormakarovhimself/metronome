package dev.metronome.wear

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.concurrent.Executors

internal class BeatFeedback(context: Context) {
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "MetronomeAudio") }

    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    private val amplitudeControl = vibrator?.hasAmplitudeControl() == true
    private var playback: BeatPlayback? = null
    private var generation = 0L

    fun start(settings: MetronomeSettings, onBeat: (Int) -> Unit, onStopped: () -> Unit) {
        stop()
        val session = generation
        val player = BeatPlayback(
            initialSettings = settings,
            debuggable = debuggable,
            onVibrate = ::vibrate,
            onCancelVibration = { vibrator?.cancel() },
            onBeat = { beat ->
                main.post { if (generation == session) onBeat(beat) }
            },
        )
        playback = player
        worker.execute {
            try {
                player.run()
            } catch (error: RuntimeException) {
                Log.e("MetronomeTiming", "Playback stopped", error)
            } finally {
                main.post {
                    if (generation == session) {
                        playback = null
                        onStopped()
                    }
                }
            }
        }
    }

    fun updateSettings(settings: MetronomeSettings) {
        playback?.settings = settings
    }

    private fun vibrate(accented: Boolean, level: Int) {
        val durationMs = if (accented) 48L else 24L
        val pulse = if (amplitudeControl) {
            val amplitude = (level * 255 + MAX_VIBRATION_LEVEL / 2) / MAX_VIBRATION_LEVEL
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            val scaledDuration = (durationMs * level / MAX_VIBRATION_LEVEL).coerceAtLeast(8L)
            VibrationEffect.createOneShot(scaledDuration, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator?.vibrate(pulse)
    }

    fun stop() {
        generation++
        playback?.running = false
        playback = null
    }

    fun release() {
        stop()
        worker.shutdown()
    }
}
