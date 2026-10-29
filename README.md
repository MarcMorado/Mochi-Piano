# Mochi Piano

An Android app that teaches piano from zero on a real piano. The phone sits on the music stand,
connects to the piano over Bluetooth MIDI or a USB cable, and hears every key she plays.

Built for a Roland FP-10, but any MIDI keyboard works.

## What's inside

- **Course**: 20 lessons in 6 units, from posture and finger numbers to reading treble and bass clef,
  rhythm, both hands and chords. Every exercise is checked live from MIDI.
- **Note highway**: Rocksmith-style play mode. Notes fall onto the keyboard and each one is judged
  Perfect / Good / A bit off / Missed from the MIDI timestamps.
  - Speed from 25% to 100%, with auto speed-up after clean loops
  - Right hand, left hand or both. The other hand is played for her, on the piano itself
  - Wait mode: the song stops until she plays the right key
  - Dynamic difficulty: starts with the main notes of each section and adds more as she nails them
  - Loop any section; results show timing (early/late), accuracy by section and the most-missed keys
- **Import**: MusicXML (`.mxl`, `.musicxml`, e.g. exported from MuseScore) and MIDI (`.mid`).
  Hands come from the two staves (or two parts/tracks).
- **Games**: Note Ninja (read the staff, play the note), Key Finder, Echo (play back by ear),
  Rhythm Tap, metronome. Weak notes come back more often (Leitner boxes).
- **Daily plan**: warm-up, next lesson, review and a song, chosen from her progress.

Without a piano connected, on-screen keys stand in for the real ones, so everything can be tried.

## Install

Every push to `main` builds an APK with GitHub Actions and publishes it as a release.
On the phone, open the latest release and download **mochi-piano.apk**:

https://github.com/MarcMorado/mochi-piano/releases/latest/download/mochi-piano.apk

Allow "Install unknown apps" for the browser when asked. All builds are signed with the same
key (in `app/keystore/`), so new versions install over old ones and keep progress.

## Build locally

Open the folder in Android Studio (Koala or newer) and run, or:

```
gradle assembleDebug
```

Requires JDK 17. There is no Gradle wrapper jar in the repo; CI uses Gradle 8.9.

## Code map

| Path | What |
| --- | --- |
| `midi/MidiHub.kt` | BLE scan, USB discovery, MIDI parsing, sending notes to the piano |
| `audio/Sound.kt` | Previews (sent to the piano when connected, phone synth otherwise), clicks |
| `music/` | Song model, built-in songs, MusicXML and MIDI importers |
| `course/` | Lessons, practice builders and the daily plan |
| `game/GameEngine.kt` | Song clock, judging, wait mode, dynamic difficulty, loops |
| `ui/` | Compose screens and components (design: "Mochi Piano — app screens" canvas) |

## Licences

Fonts: Bricolage Grotesque, Geist, Geist Mono and Noto Music, under the SIL Open Font License (see `licenses/`).
Built-in songs are public domain.
