package dev.metronome.wear

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.math.roundToInt

internal const val MIN_BPM = 30
internal const val MAX_BPM = 240
internal const val MIN_BEATS = 1
internal const val MAX_BEATS = 8
internal const val MIN_INDICATOR_OFFSET_MS = -200
internal const val MAX_INDICATOR_OFFSET_MS = 200
internal const val INDICATOR_OFFSET_STEP_MS = 5
internal const val MAX_VIBRATION_LEVEL = 100
internal const val VIBRATION_LEVEL_STEP = 5
private const val DEFAULT_REGULAR_VIBRATION = 80
private const val DEFAULT_ACCENT_VIBRATION = 100

internal data class MetronomeSettings(
    val bpm: Int = 120,
    val beats: Int = 4,
    val sound: Boolean = true,
    val vibration: Boolean = false,
    val indicatorOffsetMs: Int = 0,
    val regularVibration: Int = DEFAULT_REGULAR_VIBRATION,
    val accentVibration: Int = DEFAULT_ACCENT_VIBRATION,
)

internal class Metronome(
    private val preferences: SharedPreferences,
    private val feedback: BeatFeedback,
) {
    var settings by mutableStateOf(
        MetronomeSettings(
            bpm = preferences.getInt("bpm", 120).coerceIn(MIN_BPM, MAX_BPM),
            beats = preferences.getInt("beats", 4).coerceIn(MIN_BEATS, MAX_BEATS),
            sound = preferences.getBoolean("sound", true),
            vibration = preferences.getBoolean("vibration", false),
            indicatorOffsetMs = preferences.getInt("indicatorOffsetMs", 0)
                .coerceIn(MIN_INDICATOR_OFFSET_MS, MAX_INDICATOR_OFFSET_MS),
            regularVibration = preferences.getInt("regularVibration", DEFAULT_REGULAR_VIBRATION)
                .coerceIn(0, MAX_VIBRATION_LEVEL),
            accentVibration = preferences.getInt("accentVibration", DEFAULT_ACCENT_VIBRATION)
                .coerceIn(0, MAX_VIBRATION_LEVEL),
        )
    )
        private set

    var playing by mutableStateOf(false)
        private set

    var beat by mutableIntStateOf(-1)
        private set

    private val tapIntervals = ArrayDeque<Long>()
    private var lastTap = 0L

    fun togglePlayback() {
        if (playing) {
            stop()
        } else {
            playing = true
            feedback.start(
                settings,
                onBeat = { beat = it.coerceAtMost(settings.beats - 1) },
                onStopped = ::stop,
            )
        }
    }

    fun stop() {
        feedback.stop()
        playing = false
        beat = -1
    }

    fun changeTempo(delta: Int) {
        tapIntervals.clear()
        lastTap = 0
        setTempo(settings.bpm + delta)
    }

    fun tapTempo() {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastTap
        if (lastTap == 0L || elapsed !in 250L..2_000L) {
            tapIntervals.clear()
        } else {
            if (tapIntervals.size == 4) tapIntervals.removeFirst()
            tapIntervals.addLast(elapsed)
            setTempo((60_000.0 / tapIntervals.average()).roundToInt())
        }
        lastTap = now
    }

    fun changeBeats(delta: Int) {
        val beats = (settings.beats + delta).coerceIn(MIN_BEATS, MAX_BEATS)
        if (beats == settings.beats) return
        if (beat >= beats) beat = beats - 1
        updateSettings(settings.copy(beats = beats))
    }

    fun setSound(enabled: Boolean) {
        updateSettings(settings.copy(sound = enabled))
    }

    fun setVibration(enabled: Boolean) {
        updateSettings(settings.copy(vibration = enabled))
    }

    fun changeIndicatorOffset(delta: Int) {
        val offset = (settings.indicatorOffsetMs + delta * INDICATOR_OFFSET_STEP_MS)
            .coerceIn(MIN_INDICATOR_OFFSET_MS, MAX_INDICATOR_OFFSET_MS)
        if (offset != settings.indicatorOffsetMs) {
            updateSettings(settings.copy(indicatorOffsetMs = offset))
        }
    }

    fun changeVibrationLevel(accented: Boolean, delta: Int) {
        val current = if (accented) settings.accentVibration else settings.regularVibration
        val level = (current + delta * VIBRATION_LEVEL_STEP).coerceIn(0, MAX_VIBRATION_LEVEL)
        if (level == current) return
        updateSettings(
            if (accented) settings.copy(accentVibration = level)
            else settings.copy(regularVibration = level)
        )
    }

    private fun setTempo(bpm: Int) {
        val tempo = bpm.coerceIn(MIN_BPM, MAX_BPM)
        if (tempo == settings.bpm) return

        updateSettings(settings.copy(bpm = tempo))
    }

    private fun updateSettings(updated: MetronomeSettings) {
        settings = updated
        feedback.updateSettings(updated)
        preferences.edit {
            putInt("bpm", updated.bpm)
            putInt("beats", updated.beats)
            putBoolean("sound", updated.sound)
            putBoolean("vibration", updated.vibration)
            putInt("indicatorOffsetMs", updated.indicatorOffsetMs)
            putInt("regularVibration", updated.regularVibration)
            putInt("accentVibration", updated.accentVibration)
        }
    }
}
