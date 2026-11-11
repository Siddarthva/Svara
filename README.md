# SVARĀ

*Your music. Your world.*

SVARĀ is an offline-first, fully local music player for Android. It plays the audio files already on your device. There's no account, no cloud library, and no copy of any song. The local database holds only references, playlists, favorites, history, the queue, settings, and cached tags.

**Status:** Phase 1 (a complete, plain player plus the performance baseline). The five presets, Theme Studio, and RGB mode are Phase 2.

## Setup

Requirements: Android Studio with the bundled JBR (JDK 21+), Android SDK 37, and a device or emulator on Android 8.0 (API 26) or newer.

```bash
git clone <repo> Svara && cd Svara
./gradlew :app:installDebug          # debug build (StrictMode + LeakCanary)
./gradlew :app:assembleRelease       # R8 + resource shrinking + Baseline Profile
```

The release build is signed with the debug key so it can be installed for benchmarking. Replace `signingConfig` in `app/build.gradle.kts` before publishing.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (≤ 12) | Read audio files and their tags. Requested after a rationale screen, with "don't ask again" handled by linking to system settings. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background with notification and lock-screen controls. |
| Persisted SAF folder grants (optional) | Folders you include or exclude in **Settings → Music folders**. A grant also lets SVARĀ read `.lrc`/`.txt` lyrics files beside your songs. |

SVARĀ has no `INTERNET` permission. Media3 merges `ACCESS_NETWORK_STATE` and `WAKE_LOCK` into the manifest; SVARĀ uses neither (the player runs with `WAKE_MODE_NONE`).

## Architecture

Four layers, one playback-state stream:

1. **UI** (`ui`, `home`, `library`, `playlists`, `nowplaying`, `settings`): Compose + Material 3. Composables read ViewModel state and contain no playback logic.
2. **Playback controller** (`playback`): `PlaybackService` hosts the single ExoPlayer in a `MediaSessionService`. `PlaybackController` wraps a `MediaController` and exposes one `StateFlow<PlaybackState>`. The sleep timer lives here too.
3. **Local music engine** (`data/mediastore`, `data/artwork`, `data/lyrics`): MediaStore scanning, artwork, and local lyrics.
4. **Storage + database** (`data/db`, `data/repository`, `data/prefs`): Room for library cache, playlists, favorites, history, queue, and settings. DataStore holds simple flags.

```
app/src/main/java/com/svara/app
├── SvaraApp.kt, MainActivity.kt
├── di/                    Hilt modules, dispatchers (scan dispatcher is single-threaded)
├── domain/
│   ├── model/             Song, Album, FolderRule, QueueSnapshot, AppSettings…
│   ├── library/           FolderFilter, TextKeys, SongQueries (SQL), sorting/search helpers
│   ├── queue/             QueueMath, QueueRestore (pure, unit tested)
│   └── repository/        Repository interfaces
├── data/
│   ├── db/                Room entities, DAOs (indexed sort/search keys, SQL joins)
│   ├── mediastore/        MediaStoreAudioSource (cancellable, reports progress)
│   ├── repository/        Library (Paging 3), playlists, favorites, history, settings, folders
│   ├── artwork/           One loader for Coil (UI) and Media3 (notification); disk-cached
│   ├── lyrics/            Embedded (USLT / TXXX / Vorbis / MP4) and sidecar .lrc/.txt
│   └── prefs/             DataStore flags
├── playback/              PlaybackService, PlaybackController, SleepTimer, MediaItems
├── ui/                    App scaffold, navigation, shared components, theme
├── home/ library/ playlists/ nowplaying/ settings/
benchmark/                 Baseline Profile generator + Macrobenchmarks
```

How it handles scale (10,000+ songs):
- The Songs list is paged from Room with stable keys. Sorting and search run in SQLite on precomputed, indexed, accent-folded keys.
- Folder rules are precomputed into an indexed `visible` column, so changing a rule never rescans.
- "Play all" and bulk actions pass song ids, never full rows. The service builds MediaItems itself, so a 10k-song queue crosses IPC as about 80 KB of longs.
- Rescans diff a lightweight fingerprint (id, modified time, size, visibility) and write only the changes.

Battery behavior:
- No app wake lock (`WAKE_MODE_NONE`). Buffers are tuned for local files (5–15 s instead of the 50 s streaming defaults).
- When paused, nothing runs: the service's history/position loop exists only while audio plays, the UI progress flow emits once and stops, and the sleep timer is a single suspended coroutine.
- Progress sampling matches what's on screen: 250 ms for the Now Playing seek bar, 1 s for the mini-player, and nothing when the app is hidden.
- No background scanning. The library rescans on app start and on pull-to-refresh only.

## Visual presets

Five presets change presentation only. Switching never creates a player, interrupts playback, touches the queue or position, or starts a scan.

| Preset | Layout source (project root) | Character |
|---|---|---|
| ETHEREAL (default) | SVARĀ's own design | Balanced, artwork-centred; fully customisable in Theme Studio |
| Halo | `spotify_ui_design_features.json` | Dark and dense: greeting, 6-item quick grid, carousels, white play circle, lyrics card |
| SOLACE | `apple_music_ui_design_features.json` | Bright red on black, large titles, hero pager, paused-artwork spring, blurred backdrop |
| OCTAVE | `gaana_ui_design_features.json` | Bold red, banner carousel, genre chips, breathing overlay (Immersive), karaoke lyrics |
| ZENITH | `amazon_music_ui_design_features.md` (JSON text) | Black and cyan, swipeable artwork, credits card, optional audio-reactive layer |

Every value a preset uses, with its JSON path, every derived value, every conflict between a source and the brief, and every omitted streaming feature, is listed in [`docs/design-source-map.md`](docs/design-source-map.md). Values live in `appearance/tokens` (one spec object per preset plus `ThemeTokens`); preset composables in `ui/presets` read only those tokens, and `PresetSourceRulesTest` fails on any raw colour, size or duration there. No other app's name, logo or font is used; text is Roboto and icons are Material Symbols.

Performance modes (Settings → Appearance, Performance mode section):

| Mode | What runs |
|---|---|
| Efficiency | No animation except progress and 100 ms tap feedback; static backgrounds; no artwork spring; no audio-reactive layer |
| Balanced (default) | 150–250 ms transitions; tap scale, heart pop, tab tint cross-fade, SOLACE's artwork spring; static backgrounds |
| Immersive | Transitions up to 400 ms; OCTAVE's breathing overlay ≤ 10 fps; ZENITH's audio-reactive layer ≤ 30 fps while visible; SOLACE's live-blur mini-player on Android 12+ |

Battery saver or severe thermal status switches the effective mode to Efficiency with a one-line notice. Reduced motion removes all animation except progress indicators. Backdrop blur is always a cached 32 px bitmap (LRU of 24 albums, rebuilt only when the artwork changes); album colours are extracted once from a 128 px image and cached in Room.

## Appearance and covers

Settings → Appearance is one scrolling page. A pinned preview (mini-player, card, button, toggle, progress bar) updates as you change things. Sections collapse and remember their state; Theme and Colour start open.

- **Theme**: the five presets. Applying one shows "Theme applied. Playback unchanged."
- **Colour**: four sources (Preset colours, Album artwork, Custom accent, Monochrome); 64 named swatches in eight groups from one data file (`appearance/colour/Swatches.kt`); a hue slider with hex and copy (moving it deselects the swatch); Restore preset colours; background tone (Pure black, Soft dark, Tinted); Light, Dark or Follow system; High contrast. A custom accent survives preset switches.
- **Contrast**: `ContrastEngine` checks every accent against the theme's backgrounds (text 4.5:1, large text and non-text 3:1; 7:1 for text with High contrast). A failing colour changes lightness only, in OKLCH, keeping its hue and as much chroma as fits, and the page says "Adjusted for readability" and for which role. High contrast strengthens each preset's own palette instead of forcing one shared accent.
- **Ambient**: with the Album artwork source, the playing cover's colour tints the home header, Now Playing, the mini-player and album tiles (intensity 0–100, default 60). Track changes cross-fade over 600 ms in Balanced and Immersive and switch instantly in Efficiency. Tint strength is capped so text keeps its contrast; text colours never come from the tint.
- **Cinematic blur**: Now Playing and album headers use a softened cover built once per cover version and strength (five steps) in a 24-entry cache. Frosted surfaces and Tint are static. Lists and carousels are never blurred. Efficiency and battery saver show a plain scrim. The only live blur is SOLACE's mini-player on Android 12+ in Immersive.
- **Motion**: animation style, transition speed, intensity, haptics. Micro-animations (play/pause morph, heart pop, press scale, tab icon fill, playlist checkmark, scrubber growth with 5 s haptic ticks, section fade-in) are short, draw-phase only, and off in Efficiency and with Reduced motion.
- **Advanced** links to Theme Studio and the Navigation, Now Playing, Gesture, Typography, Artwork, Motion and Control studios. Each section has Reset this section; the footer has Reset everything and Save as look.

**Custom covers.** "Change cover" is in a song's menu, the Now Playing options, the album page and the Audio information sheet. Pick an image, adjust the square crop, and apply it to the song or the whole album. Covers resolve in one order everywhere: song cover → album cover → embedded or MediaStore art → a placeholder in a colour derived from the album name. The image is scaled to at most 1024 px, cropped square and saved as JPEG in app-private storage (`files/covers`); **your audio files are never changed**. Each change bumps a cover version that is part of the image, colour and blur cache keys, so every surface refreshes without a rescan. The notification picks up a new cover at once; the lock screen and system media controls show it from the next track. Settings → Storage shows the space used and can clear all custom covers. Exports list cover assignments but not the images.

## Running tests

```bash
./gradlew :app:testDebugUnitTest            # JVM + Robolectric (78 tests)
./gradlew :app:connectedDebugAndroidTest    # playback service on a device/emulator
```

- **Unit:** queue math and restore, folder rules, text keys, SQL sort/search/paging, scan diff and cancellation, playlists, favorites, history, queue snapshot, sleep timer, settings and lyrics codecs.
- **Instrumented:** `PlaybackServiceTest` starts the real service, plays, skips, checks that shuffle keeps the current song first, and verifies playback continues after the UI's controller is released.

On Windows, Robolectric's cache lives in `.robolectric/`, because Robolectric breaks on user paths containing spaces.

## Running benchmarks

```bash
./gradlew :app:generateBaselineProfile                      # writes app/src/release/generated/baselineProfiles
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # startup, Songs scroll, open Now Playing
scripts/measure_idle.sh 10                                  # CPU/wake locks while paused, screen off
```

Benchmarks grant the audio permission themselves, but need music on the device. To get a comparable library, generate the synthetic set described in "Test library" below.

## Measured results

See [`docs/phase1-results.md`](docs/phase1-results.md).

## Known limitations

See [`docs/phase1-results.md`](docs/phase1-results.md#known-limitations).
