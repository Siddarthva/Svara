# V3 audit and baseline

Recorded before any V3 code change. Spec: `SVARA_V3_SPEC.md`. Paths are relative to `app/src/main/java/com/svara/app/` unless they start with `app/`.

## Read first

- Read: `SVARA_V3_SPEC.md`, `README.md`, `docs/design-source-map.md`, `docs/design-verification.md`, `docs/v2.1-findings.md`, `docs/performance-baseline.md`.
- `.claude/` holds only `scheduled_tasks.lock` (session bookkeeping). No instructions, so no conflicts.
- The design source files in the project root (four JSON/MD files) and `docs/screenshots/` stay untracked, as instructed.

## Architecture at a glance

| Layer | Where | Notes |
|---|---|---|
| UI | `ui/`, `home/`, `library/`, `playlists/`, `nowplaying/`, `settings/`, `ui/presets/*` | Compose + Material 3. Anāhata uses the shared screens; Shruti, Tāla, Rāga, Spanda have preset composables reading only tokens (`appearance/tokens`). |
| Navigation | `ui/navigation/Routes.kt`, `ui/AppScaffold.kt` | Navigation Compose, string routes, one `Navigator`. |
| Playback | `playback/PlaybackService.kt` (MediaSessionService, one ExoPlayer, `WAKE_MODE_NONE`), `PlaybackController.kt` (MediaController wrapper, one `StateFlow<PlaybackState>`), `SleepTimer.kt`, `TransitionFader.kt`, `VolumeMixer.kt`, `rituals/RitualsController.kt`, `analysis/AudioAnalysis.kt` (pass-through `SpectrumAudioProcessor`) | Custom session commands: SET_QUEUE, ADD_SONGS, MOVE, RESTORE_QUEUE. |
| Theme | `appearance/ThemeModel.kt`, `EffectsPolicy.kt`, `Customization.kt`, `colour/*`, `tokens/*`, `ui/theme/*` | Five presets, 64 swatches, ContrastEngine, EffectivePalette. |
| Persistence | Room `data/db` (v4, schemas `app/schemas/2–4.json`), DataStore `data/prefs/FlagsStore.kt`, settings as key/value rows (`SettingEntity`) | Migrations 1→2→3→4 with tests (`DatabaseMigrationTest`, `SchemaV2/V3MigrationTest`). |
| DI | Hilt, `di/` | |
| Stack | Kotlin, Compose BOM, Media3, Room, Paging 3, Coil, Palette, Biometric | compileSdk/targetSdk 37, minSdk 26. |

## Spec items against current code

Status: **Implemented** (meets the V3 item), **Partial** (exists, V3 asks for more), **Missing**.

### Section 2: v2.1 preservation

| Item | Status | Files |
|---|---|---|
| Five presets, tokens, EffectsPolicy | Implemented | `appearance/ThemeModel.kt`, `appearance/EffectsPolicy.kt`, `appearance/tokens/*` |
| 64 swatches, hue slider, four colour sources, background tone | Implemented | `appearance/colour/Swatches.kt`, `settings/AppearancePage.kt` |
| Contrast engine, high contrast keeps hue | Implemented | `appearance/colour/ContrastEngine.kt`, `EffectivePalette.kt` |
| Cached palette, ambient cross-fade, cached blur | Implemented | `data/appearance/LooksAndPalettes.kt`, `appearance/colour/AmbientColours.kt`, `data/artwork/BlurredArtwork.kt` |
| Custom covers with resolution order | Implemented | `data/covers/*`, `settings/CoverEditor.kt` |
| Bundled Roboto, marquee fade, Rāga header | Implemented | `app/src/main/res/font`, `ui/presets/common/PresetBasics.kt`, `ui/presets/raga/RagaScreens.kt` |
| No-INTERNET build check | Implemented | `app/build.gradle.kts` (`CheckNoInternetPermission`) |
| Raw-value scan, brand-name scan, no-mood scan tests | Implemented | `test/.../PresetSourceRulesTest.kt`, `BrandNameScanTest.kt`, `NoMoodScanTest.kt` |
| Scanner never called by appearance changes | Implemented (instrumented) | `androidTest/.../AppearancePlaybackTest.kt` |

### Section 3: visual design system

| Item | Status | Files / gap |
|---|---|---|
| Reusable artwork system (square, portrait, wide, layered) | Partial | `ui/presets/common/PresetArt.kt`, `library/LibraryComponents.kt` (`AlbumCard`), `ui/components/SongComponents.kt`. Square only; no portrait/wide/layered playlist composition. |
| Display-sized, cached thumbnails | Partial | `data/artwork/Artwork.kt` (Coil disk cache, one loader). Not sized per card shape. |
| Deterministic fallback colour + music symbol + metadata | Partial | Fallback colour exists (`data/covers/Covers.kt`); symbol/metadata layout varies by surface. |
| Shared-element transition card / mini-player → Now Playing | Missing | No `SharedTransitionLayout` anywhere. |
| Ambient gradients on Home, Now Playing, mini-player, cards; 600 ms | Implemented | `appearance/colour/AmbientColours.kt`, `ui/presets/PresetPlayer.kt` |
| Parallax and lift | Missing | |

### Section 4A: Now Playing

| Item | Status | Files / gap |
|---|---|---|
| Artwork, titles, favourite pop, transport, seek, elapsed/remaining | Implemented | `nowplaying/NowPlayingScreen.kt`, `ui/presets/*/…Player.kt`, `ui/presets/common/PresetPlayerParts.kt` |
| Queue, lyrics, audio info, sleep timer, shuffle, repeat, speed | Implemented | same; `AudioInfoSheet`, `SleepTimerDialog`, `SpeedDialog` |
| Equalizer shortcut | Missing | No equalizer exists. |
| Aura and glow | Partial | `ui/presets/PresetPlayer.kt` (`AmbientGlow`, preset glow). Not a user option. |
| Expandable extra controls | Missing | |
| Gestures: swipe artwork to skip | Implemented | `NowPlayingScreen.kt` `SwipeableArtwork`, Spanda swipeable artwork |
| Gesture: swipe down to dismiss | Missing | |
| Three styles Classic / Cinema / Compact | Missing | Layout is fixed per preset (`PlayerLayout` enum). No user-selectable style. |

### Section 4B: queue

| Item | Status | Files / gap |
|---|---|---|
| Drag reorder, remove, clear with confirm + undo, save as playlist | Implemented | `nowplaying/QueueScreen.kt`, `ui/components/Reorderable.kt`, `domain/queue/QueueHistory.kt` |
| Play next, add to queue | Implemented | `PlaybackController.kt` (`ADD_SONGS`), song menus in `ui/components/SongComponents.kt` |
| Current track and position preserved while editing | Implemented | `PlaybackService.kt` `move`, `restoreQueueState` |
| Persistence and restore | Implemented | `PlaybackService.kt` `restoreQueue`, `androidTest/.../RestoreAfterRestartTest.kt` |
| Stress test (hundreds of random edits) | Missing | `QueueMathTest.kt` covers single operations only. |

### Section 4C: library

| Item | Status | Files / gap |
|---|---|---|
| Songs, Albums, Artists, Folders, Genres tabs | Implemented | `library/LibraryScreen.kt`, `ui/presets/common/PresetLibrary.kt` |
| Playlists, Recently played, Recently added, Most played, Favourites | Implemented | `library/CollectionScreen.kt` (`CollectionType`), `playlists/PlaylistsScreens.kt` |
| Listening history screen (chronological) | Partial | Recently played exists; no dated history view. |
| Sorting (title, artist, album, date added, duration, location) | Implemented | `domain/model/Models.kt` `SongSortField`, `domain/library/LibraryLogic.kt` |
| Indexed accent-folded search | Implemented | `library/SearchScreen.kt`, `data/db/Daos.kt` |
| Filters | Partial | Only All / Downloads (`SongFilter`). |
| Multi-select, bulk add to playlist, bulk favourite | Partial | Selection in `LibraryViewModel`, `CollectionViewModel`, `library/LibraryComponents.kt` `SelectionTopBar`; verify bulk favourite in Phase 2. |
| Folder exclusions, system folders | Implemented | `data/repository/FolderRepositoryImpl.kt`, `settings/SettingsScreens.kt` `FoldersScreen` |
| Incremental rescan, no scan on screen open | Implemented | `data/repository/LibraryRepositoryImpl.kt` (fingerprint diff); scan on app start and pull-to-refresh only |
| Duplicate detection | Implemented | `LibraryRepositoryImpl.duplicates()`, `library/LibraryCheckScreen.kt` |
| Missing-artwork / missing-metadata indicators | Missing | |
| Missing-file detection | Implemented | `LibraryRepositoryImpl.missingFiles()` |
| Revoked folder access recovery | Implemented | `FolderRule.accessGranted`, "Restore access" in `FoldersScreen` |

### Section 4D: Playlist Studio

| Item | Status | Files / gap |
|---|---|---|
| Create, rename, reorder, delete | Implemented | `playlists/PlaylistsScreens.kt`, `data/repository/UserDataRepositories.kt` |
| Duplicate playlist | Missing | |
| Recently deleted (30 days) | Missing | Delete is permanent. |
| Description | Missing | `PlaylistEntity` has no description column. |
| Automatic or chosen artwork, layered composition | Partial | Cover from first song (`Playlist.coverSongId`); no chosen artwork, no composition. |
| Add from library screens, save queue as playlist | Implemented | Song menus, `QueueScreen.kt` |
| M3U import and export | Implemented | `domain/playlist/M3u.kt`, `data/portability/Portability.kt` |
| Smart playlists, limit, pin-to-freeze | Partial | `SmartRule` has five fixed rules (recently played, most played, not played recently, recently added, favourite artists), limits 10–100, pin. No rule builder (artist, album, folder, genre, duration, play count), no live preview. |

### Section 5: lyrics

| Item | Status | Files / gap |
|---|---|---|
| Embedded (USLT, TXXX, Vorbis, MP4) and sidecar `.lrc` / `.txt` | Implemented | `data/lyrics/LocalLyricsRepository.kt` |
| LRC parsing, active line | Implemented | `LyricsText.parseTimed`, `activeIndex` |
| Synced highlight, auto-scroll | Partial | Layout presets only (`ui/presets/common/PresetPlayerParts.kt`); Anāhata's `LyricsPanel` shows plain text. |
| Full-screen lyrics | Partial | `LyricsPosition.FULL_SCREEN` option; no dedicated lyrics screen. |
| Manual import, edit, per-song offset, text size, spacing, palette background | Missing | |
| Import action when none | Missing | Empty state explains file naming only. |
| Off main thread | Implemented | IO dispatcher in repository |

### Section 6: audio

| Item | Status | Files / gap |
|---|---|---|
| Equalizer, bass boost, virtualizer, loudness enhancer | Missing | No `android.media.audiofx` use. |
| Speed with pitch preservation | Implemented | `NowPlayingScreen.kt` `SpeedDialog`, `AppSettings.preservePitch` |
| Gapless | Implemented | ExoPlayer default; `gaplessAlbums` skips fades within an album |
| Crossfade 0–12 s | Implemented | `playback/TransitionFader.kt` (fade-out/fade-in on one player), slider 0–12 s in `settings/SettingsScreens.kt` |
| Crossfade fade-curve option | Missing | Linear only. |
| ReplayGain | Implemented | `playback/VolumeMixer.kt`, `AppSettings.volumeLeveling` |
| Seek increments | Partial | 5 / 10 / 15 s (`Customization.SEEK_INCREMENTS`). |
| Sleep timer: presets, end of track, fade-out, in service | Partial | `playback/SleepTimer.kt`; presets 15/30/60 + end of track; no custom duration. |

### Section 7: Spanda visualiser

| Item | Status | Files / gap |
|---|---|---|
| Analysis tap, smoothed bands, fps caps, stops when hidden | Implemented | `playback/analysis/AudioAnalysis.kt`, `SpectrumMath.kt`, `NowPlayingViewModels.kt` (`show`/`hide`), `EffectsPolicy` |
| Styles | Partial | `VisualiserMode` Off / Ambient / Reactive / Full Spectrum; `ui/presets/Visualiser.kt` (`BassPulse`, `AmbientGlow`), `ui/presets/spanda/SpandaReactive.kt`. No gallery of six styles. |
| Controls (intensity, bars, glow, opacity, colour source, artwork, sensitivity, full screen) | Missing | |
| Playback continues if visual throws | Partial | Architecture separates them; no test. |

### Sections 8 and 9: widgets and Android integration

| Item | Status | Files / gap |
|---|---|---|
| Home-screen widgets (Glance) | Missing | No Glance dependency, no `AppWidgetProvider`. |
| Quick settings tile | Missing | |
| App shortcuts | Missing | |
| MediaSession, notification, lock screen, background playback | Implemented | `PlaybackService.kt`, `DefaultMediaNotificationProvider` |
| Favourite custom notification action | Missing | |
| Media buttons, resume from MediaButtonReceiver | Implemented | `AndroidManifest.xml`, `onPlaybackResumption` |
| Double / triple press mapping | Missing | Media3 default handling only. |
| Auto-resume on reconnect, stop on Bluetooth disconnect (opt-in) | Implemented | `playback/rituals/RitualsController.kt` |
| Audio focus, noisy (headphones out) | Implemented | `setAudioAttributes(..., handleAudioFocus = true)`, `setHandleAudioBecomingNoisy` |
| Notification permission (13+), foreground type mediaPlayback | Implemented | `AndroidManifest.xml`, `ui/PermissionScreen.kt` |

### Section 10: personalisation

| Item | Status | Files / gap |
|---|---|---|
| Appearance hub with sections, resets, save as look, studio links | Implemented | `settings/AppearancePage.kt`, `settings/AppearanceScreens.kt` |
| Saved looks: save, rename, delete, apply | Implemented | `data/appearance/LooksAndPalettes.kt` |
| Saved looks: duplicate, preview | Missing | |
| Home section order, nav destinations, gestures, artwork corner, motion | Implemented | `appearance/Customization.kt`, `AppSettings.homeSections` |
| Now Playing presentation, mini-player behaviour, visualiser presentation, widget appearance | Missing | Depend on V3 features. |

### Section 11: quality of life

| Item | Status | Files / gap |
|---|---|---|
| Undo for queue changes | Implemented | `domain/queue/QueueHistory.kt` |
| Undo for playlist changes | Partial | Playlist entry removal; check in Phase 2. |
| History and play counts, pause and erase | Implemented | `AppSettings.historyEnabled`, `SettingsScreens.kt` `PrivacyScreen` (clear history, reset statistics) |
| Local statistics screen (top songs, artists, weekly time) | Missing | |
| Library Doctor | Partial | `library/LibraryCheckScreen.kt`: duplicates and missing files, review only. No missing artwork, inconsistent artists, empty metadata, title errors, overrides, preview, undo. |
| Backup export | Partial | `data/portability/Portability.kt` `exportBackup`, `SettingsViewModel.exportBackup` (JSON of playlists, settings, cover assignments). No import, no zip, no validation. |
| Default startup screen | Missing | |
| Search shortcuts | Missing | |
| One-handed options | Partial | `AppSettings.leftHanded`. |
| Haptics, reduced motion, accessible labels | Implemented | `Customization.haptics`, `AppSettings.reducedMotion` |
| Empty states | Partial | `ui/components/Common.kt` `EmptyState`, used in library, collections, playlists, search, lyrics. |
| Loading skeleton rows | Partial | `LibraryScreen.kt` `SongPlaceholderRow` (Songs only). |
| Error states | Partial | Library scan error (`EmptyLibrary`), playback error message. |
| Permission-denied state | Implemented | `ui/PermissionScreen.kt` |

### Sections 12, 13, 15, 16: performance, accessibility, testing, release

| Item | Status | Files / gap |
|---|---|---|
| Paging, stable keys, off-main scanning, bounded caches | Implemented | README "How it handles scale"; `data/artwork/BlurredArtwork.kt` (24-entry LRU) |
| Performance modes, battery saver, thermal | Implemented | `appearance/EffectsPolicy.kt`, `data/appearance/DeviceConditions.kt` |
| Physical-device measurements | Missing | `docs/performance-baseline.md`: emulator facts only, no numbers. |
| WCAG AA engine | Implemented | `ContrastEngine`, `PaletteContrastTest` |
| Crash log | Missing | No `UncaughtExceptionHandler`. |
| Unit and instrumented tests | Partial | See baseline below; missing: queue stress, lyrics sync, widget, permission revocation, process recreation (beyond restore), interruption. |
| applicationId, versionCode, versionName | Partial | `com.svara.app`, `1`, `0.1.0`; set for V3 in Phase 5. |
| Adaptive icon, monochrome layer | Partial | `res/mipmap-anydpi/ic_launcher.xml` has a monochrome layer reusing the coloured foreground. |
| Splash | Missing | No `core-splashscreen`; window theme only. |
| R8 and resource shrinking | Implemented | `app/build.gradle.kts` release |
| Baseline Profile | Implemented (generator) | `benchmark/`, `baselineprofile` plugin |
| Release signing from `keystore.properties` | Missing | Release signs with the debug key. |
| Release checklist, known limitations, privacy docs | Missing | |

## Conflicts between the prompt and the spec

| Topic | Spec | Prompt (wins) |
|---|---|---|
| Equalizer shortcut in Phase 1 Now Playing | Section 4A lists it | Shown disabled until Phase 3 (equalizer is built in Phase 3). |
| Smart playlist rules | "artists, albums, folders, manual criteria" | Adds genre, duration and played count, live preview, maximum track count. |
| Crossfade range | "configurable" | 0 to 12 s with a fade-curve option. Crossfade stays a fade-out/fade-in on the single ExoPlayer (scope decision confirmed on 2026-10-26). |
| Library Doctor writes | "user-confirmed metadata corrections" | Corrections are stored as database overrides unless the format allows a safe write and the user confirms. |

## Baseline

Commit `17d94924fd422f719b5a91fcf5eede4c44e57d12`, run on 2026-10-27, one job at a time.

| Step | Result |
|---|---|
| `gradlew clean assembleDebug` | Passed (5 min 58 s). `app/build/outputs/apk/debug/app-debug.apk`, 29,103,383 bytes (27.8 MiB). |
| `gradlew testDebugUnitTest` | Passed: 189 tests, 0 failures, 0 errors, 0 skipped. |
| `gradlew lintDebug` | Passed: 0 errors, 1 warning (`UsableSpace`). |
| `gradlew connectedDebugAndroidTest`, first run | 8 of 9 passed. `RestoreAfterRestartTest.queueComesBackPausedAtTheSavedPosition` failed: "queue was not restored". |
| `gradlew connectedDebugAndroidTest`, after the test fix below | Passed: 9 of 9. |

Device for instrumented tests: Android Emulator AVD "Pixel_10", Android 17 (API 37), x86_64, headless (`-no-window -no-audio`). Emulator only; no physical device was used. `connectedDebugAndroidTest` uninstalls the app when it finishes.

**The one failure.** Logcat shows System UI's media-resume check (`MediaResumeListener: Testing if we can connect`) binding `PlaybackService` again about 3 s after the previous test stopped it. That instance ran its one-time restore while the saved snapshot was still empty; the test then wrote its snapshot and connected to the already-running service, which never restores twice. This is a race in the test's setup, not in the app (on a real start the snapshot is already saved before anything binds). Fix: the test now saves the snapshot and checks the service stayed stopped across the save, retrying up to five times (`androidTest/.../RestoreAfterRestartTest.kt`). App code is unchanged.
