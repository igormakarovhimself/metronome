package dev.metronome.wear

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales

private val MetronomeColors = ColorScheme(
    primary = Color(0xFFA6ECC8),
    onPrimary = Color(0xFF0D2920),
    primaryContainer = Color(0xFF254537),
    onPrimaryContainer = Color(0xFFC6F9DF),
    secondary = Color(0xFFBDCCFF),
    onSecondary = Color(0xFF1A2951),
    secondaryContainer = Color(0xFF2B385E),
    onSecondaryContainer = Color(0xFFE2E8FF),
    background = Color.Black,
    onBackground = Color(0xFFF2F5F3),
    surfaceContainer = Color(0xFF1B2521),
    onSurface = Color(0xFFF2F5F3),
    onSurfaceVariant = Color(0xFFBAC9C1),
    outline = Color(0xFF82948B),
    outlineVariant = Color(0xFF394941),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MetronomeApp(
    settings: MetronomeSettings,
    playing: Boolean,
    beat: () -> Int,
    onTogglePlayback: () -> Unit,
    onChangeTempo: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onChangeBeats: (Int) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onChangeIndicatorOffset: (Int) -> Unit,
    onChangeVibrationLevel: (Boolean, Int) -> Unit,
) {
    MaterialTheme(colorScheme = MetronomeColors) {
        AppScaffold(timeText = {}) {
            val state = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(
                scrollState = state,
                contentPadding = PaddingValues(top = 13.dp, bottom = 30.dp),
                scrollIndicator = {
                    if (!LocalScrollCaptureInProgress.current) ScrollIndicator(state = state)
                },
            ) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    contentPadding = contentPadding,
                    rotaryScrollableBehavior = null,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        PlaybackControls(settings, playing, beat, onTogglePlayback, onChangeTempo)
                    }
                    item {
                        Text(
                            text = stringResource(R.string.settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    item {
                        FilledTonalButton(
                            onClick = onTapTempo,
                            label = { Text(stringResource(R.string.tap_tempo)) },
                            secondaryLabel = { Text(stringResource(R.string.tap_tempo_hint, settings.bpm)) },
                            icon = { ControlIcon(R.drawable.ic_tap) },
                            modifier = Modifier.fillMaxWidth(0.86f)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        )
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.beats_per_bar),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Stepper(
                                value = settings.beats,
                                min = MIN_BEATS,
                                max = MAX_BEATS,
                                decreaseLabel = R.string.beats_down,
                                increaseLabel = R.string.beats_up,
                                onChange = onChangeBeats,
                            ) {
                                Text(
                                    text = settings.beats.toString(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    }
                    item {
                        SwitchButton(
                            label = { Text(stringResource(R.string.sound)) },
                            checked = settings.sound,
                            onCheckedChange = onSoundChange,
                            modifier = Modifier.fillMaxWidth(0.86f)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        )
                    }
                    item {
                        SwitchButton(
                            label = { Text(stringResource(R.string.vibration)) },
                            checked = settings.vibration,
                            onCheckedChange = onVibrationChange,
                            modifier = Modifier.fillMaxWidth(0.86f)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        )
                    }
                    item {
                        VibrationLevelControls(
                            label = R.string.regular_vibration,
                            level = settings.regularVibration,
                            onChange = { onChangeVibrationLevel(false, it) },
                        )
                    }
                    item {
                        VibrationLevelControls(
                            label = R.string.accent_vibration,
                            level = settings.accentVibration,
                            onChange = { onChangeVibrationLevel(true, it) },
                        )
                    }
                    item {
                        IndicatorOffsetControls(settings, beat, onChangeIndicatorOffset)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    settings: MetronomeSettings,
    playing: Boolean,
    beat: () -> Int,
    onTogglePlayback: () -> Unit,
    onChangeTempo: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val tempoDescription = stringResource(R.string.tempo_description, settings.bpm)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = colors.onSurfaceVariant,
            style = typography.labelSmall,
            modifier = Modifier.semantics { heading() },
        )
        Stepper(
            value = settings.bpm,
            min = MIN_BPM,
            max = MAX_BPM,
            decreaseLabel = R.string.tempo_down,
            increaseLabel = R.string.tempo_up,
            onChange = onChangeTempo,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics { contentDescription = tempoDescription },
            ) {
                BasicText(
                    text = settings.bpm.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    style = typography.displayMedium.copy(
                        color = colors.primary,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = typography.bodyMedium.fontSize,
                        maxFontSize = typography.displayMedium.fontSize,
                    ),
                )
                Text(
                    text = stringResource(R.string.bpm),
                    color = colors.onSurfaceVariant,
                    style = typography.labelSmall,
                )
            }
        }
        BeatIndicator(settings.beats, beat)
        Button(
            onClick = onTogglePlayback,
            modifier = Modifier.width(122.dp),
            colors = if (playing) {
                ButtonDefaults.buttonColors(
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            label = { Text(stringResource(if (playing) R.string.stop else R.string.start)) },
            icon = { ControlIcon(if (playing) R.drawable.ic_stop else R.drawable.ic_play) },
        )
    }
}

@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    @StringRes decreaseLabel: Int,
    @StringRes increaseLabel: Int,
    onChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 184.dp).fillMaxWidth(0.92f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(-1) }, enabled = value > min) {
            ControlIcon(R.drawable.ic_remove, stringResource(decreaseLabel))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
        IconButton(onClick = { onChange(1) }, enabled = value < max) {
            ControlIcon(R.drawable.ic_add, stringResource(increaseLabel))
        }
    }
}

@Composable
private fun VibrationLevelControls(@StringRes label: Int, level: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Stepper(
            value = level,
            min = 0,
            max = MAX_VIBRATION_LEVEL,
            decreaseLabel = R.string.vibration_weaker,
            increaseLabel = R.string.vibration_stronger,
            onChange = onChange,
        ) {
            Text(
                text = stringResource(R.string.vibration_level_value, level),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun IndicatorOffsetControls(
    settings: MetronomeSettings,
    beat: () -> Int,
    onChange: (Int) -> Unit,
) {
    val offset = settings.indicatorOffsetMs
    val signedOffset = if (offset > 0) "+$offset" else offset.toString()
    Column(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.indicator_offset),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Stepper(
            value = offset,
            min = MIN_INDICATOR_OFFSET_MS,
            max = MAX_INDICATOR_OFFSET_MS,
            decreaseLabel = R.string.indicator_earlier,
            increaseLabel = R.string.indicator_later,
            onChange = onChange,
        ) {
            Text(
                text = stringResource(R.string.indicator_offset_value, signedOffset),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
        BeatIndicator(settings.beats, beat)
        Text(
            text = stringResource(R.string.indicator_offset_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BeatIndicator(beats: Int, beat: () -> Int) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    Canvas(
        modifier = Modifier.size(width = 14.dp + 13.dp * (beats - 1), height = 8.dp)
            .semantics {
                val active = beat()
                contentDescription = if (active < 0) {
                    resources.getString(R.string.ready_beats, beats)
                } else {
                    resources.getString(R.string.active_beat, active + 1, beats)
                }
            },
    ) {
        val active = beat()
        val gap = 5.dp.toPx()
        val corner = CornerRadius(size.height / 2)
        var left = 0f
        repeat(beats) { index ->
            val color = when {
                index == active -> colors.primary
                index == 0 -> colors.outline
                else -> colors.outlineVariant
            }
            val width = if (index == 0) 14.dp.toPx() else 8.dp.toPx()
            drawRoundRect(color, Offset(left, 0f), Size(width, size.height), corner)
            left += width + gap
        }
    }
}

@Composable
private fun ControlIcon(@DrawableRes icon: Int, description: String? = null) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        modifier = Modifier.size(20.dp),
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun MetronomePreview() {
    MetronomeApp(
        settings = MetronomeSettings(),
        playing = true,
        beat = { 1 },
        onTogglePlayback = {},
        onChangeTempo = {},
        onTapTempo = {},
        onChangeBeats = {},
        onSoundChange = {},
        onVibrationChange = {},
        onChangeIndicatorOffset = {},
        onChangeVibrationLevel = { _, _ -> },
    )
}
