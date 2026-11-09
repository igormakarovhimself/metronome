package dev.metronome.wear

import android.media.AudioManager
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private lateinit var feedback: BeatFeedback
    private lateinit var metronome: Metronome
    private var rotaryRemainder = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        feedback = BeatFeedback(this)
        metronome = Metronome(getPreferences(MODE_PRIVATE), feedback)
        setContent {
            LaunchedEffect(metronome.playing) {
                if (metronome.playing) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            MetronomeApp(
                settings = metronome.settings,
                playing = metronome.playing,
                beat = { metronome.beat },
                onTogglePlayback = metronome::togglePlayback,
                onChangeTempo = metronome::changeTempo,
                onTapTempo = metronome::tapTempo,
                onChangeBeats = metronome::changeBeats,
                onSoundChange = metronome::setSound,
                onVibrationChange = metronome::setVibration,
                onChangeIndicatorOffset = metronome::changeIndicatorOffset,
                onChangeVibrationLevel = metronome::changeVibrationLevel,
            )
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            // Android reports forward rotary scrolling as a negative AXIS_SCROLL value.
            rotaryRemainder -= event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val steps = rotaryRemainder.toInt()
            if (steps != 0) {
                rotaryRemainder -= steps
                metronome.changeTempo(steps.coerceIn(-20, 20))
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onStop() {
        metronome.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }
}
