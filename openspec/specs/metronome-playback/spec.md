# Metronome Playback

## Purpose

Let a musician set a tempo on a Wear OS watch and follow a repeating, accented beat while practicing.

## Requirements

### Requirement: Set the tempo from the watch

The app SHALL support tempos from 30 to 240 BPM. It SHALL accept changes from the on-screen buttons and from rotary input on watches that expose it.

#### Scenario: Adjust the tempo with buttons or rotary input

- **WHEN** the user adjusts the tempo with the buttons or rotary input
- **THEN** the BPM changes within the supported range and the display shows the selected value

#### Scenario: Set the tempo by tapping

- **WHEN** the user taps the tempo control at a regular interval within the supported range
- **THEN** the app sets the BPM from the average of up to four recent valid tap intervals

### Requirement: Follow the beat within a bar

The app SHALL support one to eight beats per bar. The first beat of each bar SHALL be accented, and the watch SHALL highlight the current beat while playback is active.

#### Scenario: Start playback with four beats per bar

- **GIVEN** the metronome is stopped and configured for four beats per bar
- **WHEN** the user starts playback
- **THEN** the app produces an accented first beat and three regular beats before repeating the bar

#### Scenario: Change tempo during playback

- **WHEN** the user changes the BPM while playback is active
- **THEN** subsequent beats use the new tempo without requiring a restart

### Requirement: Keep playback tied to the watch screen

The app SHALL keep the screen awake during playback. It SHALL stop playback when the user presses Stop or the activity stops.

#### Scenario: Leave the app during playback

- **GIVEN** the metronome is playing
- **WHEN** the user goes back to the watch face
- **THEN** the metronome stops and the beat indicator returns to its idle state

#### Scenario: Reopen the app

- **GIVEN** the user changed the tempo or number of beats per bar
- **WHEN** the user closes and reopens the app
- **THEN** the app restores those values and waits for the user to start playback
