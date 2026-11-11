# UI fixes

One line per fix: screen, symptom, cause, fix. Checked on the emulator (Pixel_10 AVD, Android 16, 1080 × 2424, 60 Hz) at 100, 130 and 200 percent text size; screenshots in `docs/screenshots/` (not committed).

## Step 1: visual bugs

| Screen | Symptom | Cause | Fix |
|---|---|---|---|
| Library (ETHEREAL), Songs | First row could sit under the filter chips; chips scrolled with the list | Chips were a list item with no measured relationship to the rows below | Chips are a pinned header measured in the same layout pass as the list (`PinnedHeaderLayout`); the list's top content padding equals the chip row's height at any font size, so no frame has a wrong gap |
| All main screens | One-handed search button covered song titles, durations and menus | Scaffold FAB floats over content with nothing reserving space | The button shrinks away (scale and fade, or fade only, or instantly with motion off) while any list scrolls down and returns on scroll up, using one nested-scroll tracker on the NavHost; it reappears on every new screen |
| Every screen with the mini-player | List ran straight into the mini-player and the mini-player touched the bottom bar | No gap in the bottom chrome | The mini-player adds a `Spacing.s` gap above and below itself while something is queued; the content area ends above it, so no row is hidden behind it |
| Now Playing (all styles and themes) | Live wave drawn over the seek bar, times and controls | `AmbientWave` was the last child of the root Box, so it was drawn on top | Each player places `PlayerWaveLayer()` straight after its background: background, wave, artwork, content. ETHEREAL's styles draw their background in the Box and use a transparent Surface. The wave is clipped to the bottom third, still a draw-only Spacer with no pointer input |
| Now Playing (ETHEREAL) seek bar | Stray vertical line and pill-shaped split fill | Material 3 `Slider` draws its thumb as a vertical bar and splits the track around it | Replaced with the shared draw-phase `PresetScrubber`: one track, played portion, round thumb that grows only while dragging, a time bubble above the thumb while dragging (scales in with Scrubber magnify); Line, Thick and Dots styles kept |
| Now Playing (ETHEREAL) title | A two-line title pushed artist, album, heart, seek bar and controls down | Title had `maxLines = 2` but no minimum, so its height changed per song | Title area is always two lines (`minLines = maxLines = 2`, ellipsis); row aligned to the top so the heart sits on the first line |
| Mini-player (Halo, SOLACE, OCTAVE, ZENITH) | Title clipped hard at the left edge while it scrolled | The left fade only reached full strength after a fade's width of travel and stopped at the column edge, so glyphs were cut | Marquee drawing area reaches into the left padding and an erase gradient spans the lead and the first part of the column, ramping in as the text starts to move; resting text is untouched. Reduced motion and Efficiency show an ellipsis |
| Missing-artwork tiles | Plain placeholder symbol could fall under 3:1 | Symbol drawn at 60 percent alpha on `surfaceVariant` | Full-strength `onSurfaceVariant`. Coloured tiles already pick black or white by measured contrast (at least 4.5:1); a unit test sweeps the colour cube for at least 3:1 |
| Library (ETHEREAL) at 200 % text | Song count squeezed to one letter per line beside Play all and Shuffle | Count had `weight(1f)` in a Row the buttons already filled | Header is a `FlowRow`; the count wraps to its own line |
| Settings at 200 % text | Row summaries cut after a few words ("Crossfade off · sl…") | Summary limited to one line | Two lines with ellipsis |
| Search (all themes) at 200 % text | Placeholder wrapped to two lines and grew the field | Placeholder Text could wrap | One line with ellipsis |
| Bottom bar (ETHEREAL) at 200 % text | Labels broke mid-word ("Album s", "Playlis ts") | Label Text could wrap | One line with ellipsis |
| Now Playing | Whole screen recomposed on every progress tick (4 per second) | Progress was read as a value at screen level and passed into the seek bar | Progress is passed as `State` and read only in the seek bar's draw phase and the time text |
| Seek bar times | Time text recomposed 4 times a second | Read the raw position | Derived to whole seconds; recomposes once a second |
| Mini-player | Whole mini-player recomposed every second | Progress read at the top of `MiniPlayer` | Read only inside the progress lambda used in the draw phase |
| Live wave | Brush allocated every frame; composable recomposed on every frame of its fade | Brush built inside the draw loop; fade value read in composition | `drawWithCache` builds the brush once per size or colour; running flag is a `derivedStateOf` |


## Step 2: flow bugs

| Screen | Symptom | Cause | Fix |
|---|---|---|---|
| Any screen after rotation, font or locale change | Came back on another screen or filter (Library reset to its first filter) | The start-tab `LaunchedEffect(Unit)` ran again after recreation and navigated on top of the restored back stack | Runs once per launch (`rememberSaveable` flag); the restored stack is left alone. Checked on the emulator: Library > Artists stays on Artists through portrait > landscape > portrait |
| App start (process still alive, settings in memory) | Crash: "Navigation graph has not been set" | The start-tab and launch-route effects could navigate before NavHost set its graph (found by the new Library instrumented test) | Both wait for the first back-stack entry before navigating |
| Library in landscape (phone) | About one row visible between the bars | Title bar, tabs, mini-player and bottom bar all fixed | Below 480 dp of height the title bar scrolls away with the list and returns on scroll up |
| Every screen | Double tap opened a screen twice; double back could pop past the first screen and leave a blank host | Navigator navigated and popped unconditionally | Navigator acts only when the current entry is resumed (not mid-transition); back never pops the last entry. Tabs stay unguarded (single-top, state restored); widget and shortcut launch routes bypass the guard |
| Now Playing, mini-player | Double tap on play/pause, next, previous, shuffle, repeat or the heart acted twice (flicker, double skip, un-heart) | No debounce | `TapGuard`: the same action within 300 ms runs once; different actions never block each other (unit tested) |
| Song menus, bulk actions | Double tap on Play next or Add to queue queued the songs twice | No debounce | Same guard, keyed by action and song ids |
| App start, rotation | White flash before the first frame and on recreation (app is dark by default) | Base theme `Theme.Material.Light` with no window background | Window background is the app's dark background in the theme (and on the Android 12+ splash), and follows the active theme's background at runtime |
| Lists with missing artwork | Tiles flashed the plain placeholder before their colour on every revisit (scrolling back, tab switch) | Fallback colour looked up asynchronously from Room each time, starting from null | Bounded in-memory cache (512 entries) of the colours last handed out; a tile seen before starts with its colour. Room stays the source of truth |

## Step 3: smoothness and refresh rate

| Area | Symptom | Cause | Fix |
|---|---|---|---|
| Display | App ran at whatever rate the system picked | No frame-rate request | **Smooth display** (Appearance > Performance, on by default, in Reset this section and Saved looks): Android 11+ sets `preferredDisplayModeId` to the fastest mode at the current resolution, older versions `preferredRefreshRate`; never a mode that changes resolution; off clears the request. The system may still lower it (battery saver, low battery, heat) |
| Motion governor | A still screen woke the app on every vsync (60 to 120 times a second) while visible | Choreographer frame callback re-posted every frame to time frames | JankStats (frame metrics) reports only drawn frames, so nothing runs while nothing is drawn. Same governor rules: over 10 percent janky in a 3 s window steps down one level, 30 stable seconds steps back up, never below the floor. Added: a pause between frames starts a new window, and a window needs 30 frames before it can count as janky |
| Lists | Artwork crossfaded in while a list was flinging | Crossfade on every request | Requests built during a fling skip the crossfade (read once, no recomposition) |
| Lists (presets) | Row compositions not reused across types | No `contentType` | `contentType` on preset library lists and grids |
| Startup | (checked) | | Crash log stays first; player connection and device listeners already wait for the app to be started; nothing heavy left to defer |

Item 9 of the brief (walking every screen at three text sizes in five themes, light and dark) was done for Library, Home, mini-player and Now Playing in ETHEREAL and OCTAVE, and Library in Halo, at 130 and 200 percent; the remaining screens and the light themes were not walked in this pass.

## Step 4: measurements

No phone was connected. Everything below is from the **emulator** (Pixel_10 AVD, `sdk_gphone64_x86_64`, Android 17, 1080 × 2424, headless, SwiftShader software rendering). Emulator frame figures describe the host's software renderer more than the app; the system Settings app is given as a baseline under the same script.

| Measure | Build | Result |
|---|---|---|
| Cold start (`am start -W`, 5 runs) | release-like (R8 on, not debuggable, Baseline Profile compiled with `speed-profile`), emulator | 1185, 1419, 651, 933, 644 ms (median 933 ms) |
| Cold start (6 runs) | debug, emulator | 4041, 5569, 5313, 4722, 3465 ms (one invalid 0 ms reading dropped) |
| Library scroll, 60 songs, 10 flings (`dumpsys gfxinfo`) | release-like, emulator | 116 frames, 57.8 % janky, p50 40 ms, p90 101 ms, p99 150 ms |
| Same script, system Settings app (baseline) | emulator | 212 frames, 58.0 % janky, p50 53 ms, p90 69 ms, p99 85 ms |
| Display modes (`dumpsys display`) | emulator | one 60 Hz mode (rates 60, 30, 20) |
| Rate in use while scrolling (`dumpsys SurfaceFlinger --latency`) | emulator | 16.67 ms period (60 Hz) |
| Now Playing open, tab switch, preset switch, Settings open, scroll in each of five themes, Now Playing 5 minutes with the wave (frames, CPU) | | Not measured |

Targets: jank under 2 percent: Not met on the emulator (software rendering; the baseline app shows the same); on a phone: Not measured. p99 within the frame budget: Not met on the emulator; on a phone: Not measured. Cold start under 1.5 s: Met on the emulator with the release-like build (all five runs); on the test phone: Not measured.

## Step 5: tests

- Unit: `ChromeLayoutTest` (search button scroll logic, fallback tile contrast), `TapGuardTest` (debounce and idempotency), `DisplayRateTest` (mode choice, Smooth display default, section and saved looks), `MotionPolicyTest` (governor: no step-down from a few frames after a pause; step down, floor, recovery).
- Instrumented (emulator, 5-minute songs): `UiLayoutTest` (Now Playing title area keeps its height for short and very long titles), `LibraryLayoutTest` (first song row fully below the chips; recreation keeps the Library tab; this test found the start-up navigation crash above), `NowPlayingStylesPlaybackTest` (three styles × five presets during playback keep the player, queue and position and start no scan). That last test had been failing since the live wave was added (its frame loop keeps Compose from reporting idle while music plays); it now turns the wave off for its run.

