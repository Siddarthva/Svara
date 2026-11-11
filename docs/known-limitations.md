# Known limitations

Only what is known to be missing or limited. Nothing here is a hidden feature.

## Not built
- **Animation extras:** none of the 25 items (HeartBurst, EqualizerBars, PressDepress, ScrubberMagnify, KineticLyrics, GradientBleed, MiniPlayerSwipe, ElasticCover, PillMorph, SpringContextMenu, LivingCover, ExpandDrawer, RadarPulse, CardFlip, FrostedDepth, audio badge, PlayPauseMorph, TabIndicatorSlide, ListStagger, SkeletonShimmer, SleepTimerRing, NoteDrift, ArtworkLift, PullToRefreshWave, Vinyl option) has been built. Existing micro-animations remain: heart pop, icon morph, checkmark, section fade.
- **Live ambient wave:** not built.
- **Scroll motion:** collapsing titles (outside Settings and SOLACE), parallax, edge fades, fast-fling pausing, carousel scale and custom overscroll are not built. The "Scroll effects" switch has no visible effect yet.
- **Song-change transitions** cover the mini-player title only. Full-player artwork, lyrics, the progress bar and the queue highlight swap instantly.
- **Bottom-sheet springs and skeleton cross-fades** follow Material defaults.
- **Accessibility pass at 130 and 200 percent text** and **device performance numbers** have not been measured.

## Platform limits
- **Save to gallery** for recap images needs Android 10 or later. On Android 8 and 9, use Share.
- **Splash mark** shows on Android 12 and later; older versions show a plain window.
- **Predictive back preview** needs Android 14 or later.
- **Equalizer and effects** depend on what the device reports. Unsupported effects are hidden, and output is not bit-perfect.
- **Widgets:** see `docs/widgets.md` for launcher differences. Progress is coarse or omitted where Android limits updates.

## Backup
- Restore replaces settings and adds playlists, smart playlists, favourites and history. Folder access must be granted again, and custom cover images are not part of the backup (only which songs have them). There is no zip export of covers.
- Saved looks are not in the backup file.

## Verification
- Everything above was checked with unit tests and builds only. Nothing in this round was run on the emulator or a phone.
