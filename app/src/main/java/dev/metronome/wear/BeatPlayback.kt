package dev.metronome.wear

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.locks.LockSupport
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private const val TIMING_TAG = "MetronomeTiming"
private const val SAMPLE_RATE = 48_000
private const val FRAMES_PER_BLOCK = SAMPLE_RATE / 100
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val WARMUP_FRAMES = SAMPLE_RATE * 350 / 1_000L
private const val HAPTIC_LEAD_NS = 20_000_000L
private const val MAX_HAPTIC_LATE_NS = 100_000_000L

internal class BeatPlayback(
    initialSettings: MetronomeSettings,
    private val debuggable: Boolean,
    private val onVibrate: (accented: Boolean, level: Int) -> Unit,
    private val onCancelVibration: () -> Unit,
    private val onBeat: (Int) -> Unit,
) {
    @Volatile var settings = initialSettings
    @Volatile var running = true

    private val buffer = ShortArray(FRAMES_PER_BLOCK)
    private val cues = ArrayDeque<Cue>()
    private var pendingCue: Cue? = null
    private var framesWritten = 0L
    private var blockOffset = buffer.size
    private var nextBeatFrame = WARMUP_FRAMES.toDouble()
    private var intervalFrames = framesPerBeat(initialSettings.bpm)
    private var beat = -1
    private var clip = regularClick
    private var clipStartFrame = -regularClick.size.toLong()

    fun run() {
        if (!running) return
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = createTrack()
        try {
            track?.play()
            val clock = PresentationClock(track)
            var vibrationEnabled = settings.vibration
            while (running) {
                val currentSettings = settings
                if (vibrationEnabled && !currentSettings.vibration) onCancelVibration()
                vibrationEnabled = currentSettings.vibration

                val now = System.nanoTime()
                clock.refresh(now, framesWritten)
                val untilCue = dispatchCues(clock, now, currentSettings)
                if (blockOffset == buffer.size) renderBlock(currentSettings)
                val written = track?.write(
                    buffer,
                    blockOffset,
                    buffer.size - blockOffset,
                    AudioTrack.WRITE_NON_BLOCKING,
                ) ?: if (clock.timeOf(framesWritten) <= now + 80_000_000L) {
                    buffer.size - blockOffset
                } else {
                    0
                }

                when {
                    written > 0 -> {
                        blockOffset += written
                        framesWritten += written
                        pendingCue?.let { cue ->
                            if (cue.frame < framesWritten) {
                                cues.addLast(cue)
                                pendingCue = null
                            }
                        }
                    }
                    written == 0 -> LockSupport.parkNanos(untilCue.coerceIn(500_000L, 5_000_000L))
                    else -> {
                        Log.w(TIMING_TAG, "AudioTrack write failed: $written")
                        return
                    }
                }
            }
        } finally {
            try {
                track?.pause()
                track?.flush()
            } finally {
                track?.release()
                onCancelVibration()
            }
        }
    }

    private fun renderBlock(settings: MetronomeSettings) {
        val updatedInterval = framesPerBeat(settings.bpm)
        if (updatedInterval != intervalFrames) {
            if (beat >= 0) {
                val remaining = ((nextBeatFrame - framesWritten) / intervalFrames).coerceIn(0.0, 1.0)
                nextBeatFrame = framesWritten + remaining * updatedInterval
            }
            intervalFrames = updatedInterval
        }

        val endFrame = framesWritten + buffer.size
        val beatFrame = nextBeatFrame.roundToLong()
        if (beatFrame < endFrame) {
            beat = (beat.coerceAtMost(settings.beats - 1) + 1) % settings.beats
            pendingCue = Cue(beatFrame, beat, settings.bpm, settings.beats)
            clip = if (beat == 0) accentedClick else regularClick
            clipStartFrame = beatFrame
            nextBeatFrame += intervalFrames
        }

        buffer.fill(0)
        val copyStart = maxOf(framesWritten, clipStartFrame)
        val copyEnd = minOf(endFrame, clipStartFrame + clip.size)
        if (settings.sound && copyStart < copyEnd) {
            clip.copyInto(
                buffer,
                destinationOffset = (copyStart - framesWritten).toInt(),
                startIndex = (copyStart - clipStartFrame).toInt(),
                endIndex = (copyEnd - clipStartFrame).toInt(),
            )
        }
        blockOffset = 0
    }

    private fun dispatchCues(clock: PresentationClock, now: Long, settings: MetronomeSettings): Long {
        while (running) {
            val cue = cues.firstOrNull() ?: break
            val presentation = clock.timeOf(cue.frame)
            var untilNext = Long.MAX_VALUE
            if (cue.indicatorTimeNs == null) {
                val untilIndicator = presentation + settings.indicatorOffsetMs * 1_000_000L - now
                if (untilIndicator > 0) {
                    untilNext = untilIndicator
                } else {
                    cue.indicatorTimeNs = System.nanoTime()
                    onBeat(cue.beat)
                }
            }
            if (!cue.hapticDispatched) {
                val untilVibration = presentation - HAPTIC_LEAD_NS - now
                if (untilVibration > 0) {
                    untilNext = minOf(untilNext, untilVibration)
                } else {
                    cue.hapticDispatched = true
                    val accented = cue.beat == 0
                    val level = if (accented) settings.accentVibration else settings.regularVibration
                    if (settings.vibration && level > 0 && now - presentation < MAX_HAPTIC_LATE_NS) {
                        cue.hapticTimeNs = System.nanoTime()
                        onVibrate(accented, level)
                    }
                }
            }

            val untilPresentation = presentation - System.nanoTime()
            if (untilPresentation > 0) return minOf(untilNext, untilPresentation)
            if (untilNext != Long.MAX_VALUE) return untilNext
            cues.removeFirst()
            if (debuggable && Log.isLoggable(TIMING_TAG, Log.DEBUG)) {
                val hapticOffsetUs = cue.hapticTimeNs?.let { (it - presentation) / 1_000 }
                val indicatorOffsetUs = cue.indicatorTimeNs?.let { (it - presentation) / 1_000 }
                Log.d(
                    TIMING_TAG,
                    "bpm=${cue.bpm} beat=${cue.beat + 1}/${cue.beats} frame=${cue.frame} " +
                        "presentationNs=$presentation clock=${clock.source} " +
                        "hapticOffsetUs=$hapticOffsetUs indicatorOffsetUs=$indicatorOffsetUs " +
                        "underruns=${clock.underruns}",
                )
            }
        }
        return Long.MAX_VALUE
    }

    private fun createTrack(): AudioTrack? {
        val channel = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimumBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, channel, encoding)
        if (minimumBytes <= 0) {
            Log.w(TIMING_TAG, "48 kHz PCM output unavailable: $minimumBytes")
            return null
        }

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(encoding)
                        .setChannelMask(channel)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                // Leave enough room to keep feeding PCM during short scheduling delays.
                .setBufferSizeInBytes(maxOf(minimumBytes, SAMPLE_RATE * 2 * 220 / 1_000))
                .build()
        } catch (error: IllegalArgumentException) {
            Log.w(TIMING_TAG, "Could not open PCM output", error)
            null
        } catch (error: UnsupportedOperationException) {
            Log.w(TIMING_TAG, "Could not open PCM output", error)
            null
        }
    }

    private data class Cue(val frame: Long, val beat: Int, val bpm: Int, val beats: Int) {
        var indicatorTimeNs: Long? = null
        var hapticDispatched = false
        var hapticTimeNs: Long? = null
    }
}

private class PresentationClock(private val track: AudioTrack?) {
    private val timestamp = AudioTimestamp()
    private var anchorTimeNs = System.nanoTime()
    private var anchorFrame = 0L
    private var lastPollNs = anchorTimeNs - NANOS_PER_SECOND
    private var candidateFrame = -1L
    private var lastStatusNs = lastPollNs
    private var deviceId = 0
    var underruns = 0
        private set
    var source = if (track == null) "monotonic" else "play-start"
        private set

    fun timeOf(frame: Long): Long =
        anchorTimeNs + (frame - anchorFrame) * NANOS_PER_SECOND / SAMPLE_RATE

    fun refresh(now: Long, framesWritten: Long) {
        val output = track ?: return
        if (now - lastStatusNs >= NANOS_PER_SECOND) {
            lastStatusNs = now
            val routedDevice = output.routedDevice?.id ?: 0
            val currentUnderruns = output.underrunCount
            if (deviceId != routedDevice || underruns != currentUnderruns) {
                deviceId = routedDevice
                underruns = currentUnderruns
                candidateFrame = -1L
                source = "resync"
            }
        }
        val pollInterval = if (source == "timestamp") 10 * NANOS_PER_SECOND else 25_000_000L
        if (now - lastPollNs < pollInterval) return
        lastPollNs = now

        if (source == "play-start" || source == "resync") {
            val head = unwrapFrame(output.playbackHeadPosition.toLong(), framesWritten)
            if (head > 0) {
                anchorFrame = head
                anchorTimeNs = now
                source = "playhead"
            }
        }
        if (output.getTimestamp(timestamp)) {
            val frame = unwrapFrame(timestamp.framePosition, framesWritten)
            if (frame >= 0 && timestamp.nanoTime > 0) {
                if (candidateFrame >= 0 && frame > candidateFrame) {
                    anchorFrame = frame
                    anchorTimeNs = timestamp.nanoTime
                    source = "timestamp"
                }
                candidateFrame = frame
            }
        }
    }

    private fun unwrapFrame(position: Long, framesWritten: Long): Long {
        val frame = (framesWritten and -0x1_0000_0000L) + (position and 0xffff_ffffL)
        return if (frame <= framesWritten) frame else frame - 0x1_0000_0000L
    }
}

private fun framesPerBeat(bpm: Int) = 60.0 * SAMPLE_RATE / bpm

private val regularClick = createClick(rate = 1.0, volume = 0.75)
private val accentedClick = createClick(rate = 1.35, volume = 1.0)

private fun createClick(rate: Double, volume: Double): ShortArray =
    ShortArray((SAMPLE_RATE * 0.048 / rate).roundToInt()) { frame ->
        val time = frame * rate / SAMPLE_RATE
        val envelope = minOf(time / 0.001, 1.0) * exp(-time / 0.008) *
            ((0.048 - time) / 0.004).coerceIn(0.0, 1.0)
        val signal = 0.72 * sin(2 * PI * 1320 * time) + 0.28 * sin(2 * PI * 2110 * time)
        (27_500 * volume * envelope * signal).roundToInt().toShort()
    }
