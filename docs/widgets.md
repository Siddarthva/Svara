# Widgets, quick settings tile and shortcuts

SVARĀ V3 adds three home-screen widgets built with Jetpack Glance, a quick settings tile and three app shortcuts. All of them talk to the same `MediaSession` as the app, through a short-lived `MediaController`.

## The widgets

| Widget | Default size | Shows |
|---|---|---|
| Small | 2 × 1 cells | Artwork thumbnail, title, artist, play/pause; previous and next once the widget is at least 220 dp wide |
| Medium | 4 × 2 | Artwork, title and artist, progress, previous / play-pause / next |
| Large | 4 × 4 | Large artwork, title and artist, progress, controls, favourite, open-queue shortcut and the next three titles |

Each widget has its own settings (artwork on or off, background transparent / translucent / solid, colour from the preset, the app's current colour or the album artwork, compact or expanded, text and controls on or off). They open when the widget is placed (the configuration is optional, so placing it without changes is fine) and again from the launcher's **Reconfigure** where the launcher offers it. Colours come from the same effective palette as the app, so the widgets follow the active preset, brightness and custom colour.

Nothing queued: the widget shows a music symbol and "Nothing queued. Tap to choose music.", and opens the app.

## How and when they update

- The playback service writes a small snapshot (song, artist, play state, position, favourite, next titles) when the **track changes, playback starts or stops, the user seeks, the queue changes or the favourite changes**. Bursts are coalesced (300 ms). There is **no periodic update**: `updatePeriodMillis` is 0 and nothing polls.
- The app redraws widgets when the **preset, colour or brightness** changes (500 ms after the last change).
- When the service stops, the widgets keep the last song, shown paused.

### Progress

Android widgets can't animate a progress bar without being redrawn many times a minute, which costs battery and is throttled by the system. SVARĀ therefore shows **progress as of the last update** (track change, play/pause, seek). It does not move while the song plays. This is intentional, not a fault.

## Controls when the app isn't running

Widget buttons and the tile run the command on the real session. If the playback service isn't running, connecting to it starts it; the service restores the saved queue (paused), and **play** then starts it. With no saved queue, the app opens instead.

Opening the app from a widget's artwork or title, or from the queue button, works from the home screen. Android 10 and later block apps from opening screens from the background, so if a widget command needs to open the app (no queue at all), some launchers may ignore it; tapping the widget's artwork always works.

## Quick settings tile

**SVARĀ** (play or pause). Add it from the quick settings editor. It shows the current song as its subtitle (Android 10+) and refreshes when the shade opens; it does no work while hidden.

## App shortcuts

Long-press the SVARĀ icon: **Shuffle all**, **Resume** and **Recently played**.

## Launcher differences and limits

- **Transparency:** "Transparent" removes SVARĀ's background, but some launchers draw their own panel or shadow behind widgets. "Translucent" uses the theme background at 78 % opacity.
- **Rounded corners** follow the system on Android 12+; older launchers may show square corners.
- **Sizes:** cells differ between launchers and screen densities. The small widget hides previous/next below 220 dp width and drops to one line below 80 dp height.
- **Reconfigure:** not every launcher offers it; remove and re-add the widget to change its settings there.
- **Artwork** is decoded at widget size (192, 320 or 640 px) through the same loader as the app, so custom covers appear in widgets too. Very large widgets on high-density screens may look slightly soft.
- **Lock screen:** Android shows SVARĀ's controls on the lock screen through the media session (not a widget); see the notification section of the final report.

## Verification status

Built and installed on the Android 17 emulator. Widget placement, configuration and taps are listed in the phone checklist; anything not run on a physical phone is marked so in the report.
