# Remaining work

Status checked on 2026-10-30 against `main` at `072c7e3` plus the uncommitted working tree. Each row has evidence: a commit, a file, or a grep. "Uncommitted" means the code exists only in the working tree (widgets, tile, shortcuts, favourite action).

## Group 1 and 2: earlier prompts

| Item | Status | Evidence |
|---|---|---|
| Theme rename (display names) | Done | `cda6005`; `appearance/ThemeModel.kt` names are ETHEREAL, Halo, SOLACE, OCTAVE, ZENITH. Old names appear only in internal ids (`ANAHATA`, `ShrutiSpec`, …), as intended |
| Logo, adaptive icon with its own monochrome layer | Done | `a35a0ed`; `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi/ic_launcher.xml` |
| Splash and About use of the mark | Done | `res/values-v31/themes.xml` (Android 12+ only; older versions show the window background); `settings/SettingsScreens.kt` `AboutScreen` |
| Settings: large collapsing title, search, at most eight rows with live summaries, rounded cards | Done | `82f7472`; `settings/SettingsScreens.kt` (`LargeTopAppBar`, `SettingEntry` index, six `CategoryRow`s) |
| Settings: search synonyms "wave", "recap", "widgets" | Missing | No entries in the `SettingEntry` index |
| Settings: sub-screens with at most seven rows plus Advanced | Partial | `Group()` in `SettingsScreens.kt`. Appearance, Audio and Data sub-screens are in place. Deeper screens (Privacy, Storage, Appearance page) are unchanged |
| Settings: search opens the exact row | Partial | Search opens the right sub-screen, not the row |
| Settings: tests (index, row limits, old values load) | Missing | No test covers `SettingsScreens.kt` |
| Live ambient wave | Missing | grep `ambient ?wave|live ?wave`: no matches |
| Animation extras (HeartBurst … Vinyl, 25 items) | Missing | grep for each name: no matches. Related existing pieces: heart pop, icon morph and checkmark in `EffectsPolicy.kt` `PresetEffects`; skeleton rows in `ui/states` |
| Page transitions per theme, tab fade-through | Done | `ee73d2f`; `ui/motion/MotionRuntime.kt` `ThemeTransitions`, `appearance/tokens/TransitionTokens.kt` |
| Predictive back | Done (manifest flag) | `android:enableOnBackInvokedCallback="true"`. Not checked on a device |
| Shared elements gated by motion groups | Done | `ui/artwork/SharedArtwork.kt` |
| Scroll motion (collapsing titles, parallax, edge fades, fling pause, carousel scale, overscroll) | Missing | The `scrollEffects` and `parallax` flags exist in `MotionGroups` but nothing reads them |
| Song-change transitions | Partial | Mini-player title and artist only (`SongChange` in five chromes). Missing: full-player artwork, lyrics, progress reset, queue highlight |
| Bottom-sheet springs, skeleton cross-fade | Missing | |
| MotionGovernor | Done | `appearance/MotionPolicy.kt`, `MotionGovernorEffect`, `MotionPolicyTest` |
| Duplicate-request guard | Partial | `RepeatGuard` is used by `Navigator.recapStory` only |
| Motion settings in Appearance › Motion | Done | Page transitions, Scroll effects, Song change animation, Smart smoothing, Recap animations |
| Recap | Done, with gaps | `40e521f`, `072c7e3`. Gaps are listed for Group 1: slide instead of shape wipe, gradient style not per theme, Home card outside the scroll, switches only on the Recap screen, no note on Android 8–9 |

## Group 3: V3 audio and integration

| Item | Status | Evidence |
|---|---|---|
| Equalizer, bass boost, virtualizer, loudness | Done | `4d78452`; `playback/effects/AudioEffects.kt`, `AudioSuiteTest` |
| Effect failure isolation test | Done | `8d41c10` (audio suite tests) |
| Speed and pitch, gapless, crossfade 0–12 s with curve, ReplayGain | Done | `NowPlayingScreen.kt` SpeedDialog, `TransitionFader.kt`, `FadeCurve`, `VolumeMixer.kt` |
| Seek increments | Done | `Customization.SEEK_INCREMENTS` = 5/10/15/30 |
| Sleep timer with custom length, fade, end of song | Done | `e349fef`; `playback/SleepTimer.kt` |
| Visualiser gallery and controls | Done | `0da5a92`; `VisualStyle`, `VisualSettings`, `AudioScreens.kt` |
| Visualiser failure isolation test | Done | `8d41c10` ("visualiser guard") |
| Widgets small, medium, large (Glance) with configuration | Partial, uncommitted | `widget/SvaraWidgets.kt`, `WidgetConfigActivity.kt`, `docs/widgets.md`, `test/.../widget/WidgetLogicTest.kt`. The receivers were committed into the manifest in `ee73d2f` but the classes are not committed |
| Quick settings tile | Partial, uncommitted | `widget/PlaybackTileService.kt` |
| App shortcuts | Partial, uncommitted | `res/xml/shortcuts.xml`, `ic_shortcut_*.xml` |
| Notification favourite action | Partial, uncommitted | `playback/PlaybackService.kt` (`favourites`, `favouriteIds`) is modified in the working tree |
| Double and triple press | Done | `AppSettings.headsetMultiPress`, `PlaybackService.kt` |
| Auto-resume, stop on Bluetooth disconnect, focus, noisy, POST_NOTIFICATIONS, mediaPlayback type | Done | `RitualsController.kt`, `AndroidManifest.xml`, `PermissionScreen.kt` |
| Queue undo and stress test | Done | `QueueHistory.kt`, `8dae3b7` |
| Library filters, Library Doctor, history, statistics | Done | `3529ac5` |
| Playlist recently deleted, rule builder with pin | Done | `323b2eb` |
| Lyrics Studio with LRC sync and offset | Done | `e349fef`, `9a71a33` |
| Now Playing styles, shared-element transitions | Done | `e398320`, `873df5f` |
| Saved looks: duplicate, preview | Partial | duplicate exists (`LooksAndPalettes.kt`); preview not verified |

## Group 4: quality and safety

| Item | Status | Evidence |
|---|---|---|
| Backup export | Partial | `data/portability/Portability.kt` exports JSON. Import exists for M3U playlists only; there is no settings or history import, no zip, no validation |
| Crash log | Missing | grep `UncaughtExceptionHandler`: no matches |
| Accessibility pass at 130 and 200 percent | Missing | No test or record |
| Physical-device performance numbers | Missing | `docs/performance-baseline.md` has emulator facts only |

## Group 5: release

| Item | Status | Evidence |
|---|---|---|
| applicationId, versionCode, versionName for release | Partial | `app/build.gradle.kts` (`1`, `0.1.0`) |
| R8 and resource shrinking | Done | `app/build.gradle.kts` release: `isShrinkResources = true`, `optimization { enable = true }` |
| Baseline Profile | Done (generator) | `baselineprofile` plugin, `benchmark/` |
| Release signing from keystore.properties | Missing | Release signs with the debug key (`signingConfigs.getByName("debug")`) |
| Release checklist, known limitations, privacy docs | Missing | No such files in `docs/` |

## Baseline (2026-10-30)

`assembleDebug` and `testDebugUnitTest` both pass, using the uncommitted working tree. A clean checkout of `main` would fail, because the manifest names widget classes that are not committed.
