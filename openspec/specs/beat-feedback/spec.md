# Beat Feedback

## Purpose

Let a musician hear, feel, and see the same beat, with controls for signal strength and display timing on their watch.

## Requirements

### Requirement: Enable sound and vibration independently

The app SHALL provide separate sound and vibration controls. Audible clicks SHALL be placed at beat positions within a continuous PCM audio stream. Vibration SHALL use the beat timeline, using the audio presentation estimate when available.

#### Scenario: Follow the beat by vibration alone

- **GIVEN** vibration is enabled and the selected beat types have nonzero vibration levels
- **WHEN** the user disables sound
- **THEN** the app continues to vibrate on beats and update the beat indicator

#### Scenario: Play clicks at a steady tempo

- **GIVEN** sound is enabled and the tempo is steady at 120 BPM
- **WHEN** the app generates successive clicks
- **THEN** the clicks are placed 24,000 samples apart in the 48 kHz audio stream

### Requirement: Adjust regular and accented vibration separately

The app SHALL provide independent vibration levels from 0 to 100% for regular and accented beats, in steps of five percentage points. A zero level SHALL suppress vibration for that beat type. The app SHALL use amplitude control when supported by the watch and vary pulse duration otherwise.

#### Scenario: Increase vibration on regular beats

- **GIVEN** vibration is enabled
- **WHEN** the user increases the regular beat level during playback
- **THEN** subsequent regular beats use the new level and accented beats continue at their configured level

#### Scenario: Turn vibration off for one beat type

- **GIVEN** vibration is enabled
- **WHEN** the user sets the accented beat level to 0%
- **THEN** accented beats stop vibrating and regular beats use their configured level

### Requirement: Calibrate the beat indicator

The app SHALL let the user move the beat indicator earlier or later relative to the estimated audio presentation time. The available offsets SHALL range from −200 to +200 milliseconds in five-millisecond steps. A new installation SHALL start at zero offset.

#### Scenario: Move the indicator earlier

- **WHEN** the user chooses a negative offset during playback
- **THEN** the app schedules the indicator earlier relative to the estimated sound and shows the result in the settings preview

#### Scenario: Move the indicator later

- **WHEN** the user chooses a positive offset during playback
- **THEN** the app schedules the indicator later relative to the estimated sound and uses the audio beat timeline for vibration

### Requirement: Restore the feedback settings

The app SHALL persist sound and vibration toggles, both vibration levels, and the indicator offset.

#### Scenario: Reopen after changing feedback settings

- **WHEN** the user closes and reopens the app after adjusting feedback
- **THEN** the settings show the previous values and use them when playback starts
