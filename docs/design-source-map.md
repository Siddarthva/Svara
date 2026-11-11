# Design source map

Where every visual value for the four layout presets comes from. Sources sit in the project root:

| Preset | Source file | Short name used below |
|---|---|---|
| SHRUTI | `spotify_ui_design_features.json` | `S` |
| TĀLA | `apple_music_ui_design_features.json` | `A` |
| RĀGA | `gaana_ui_design_features.json` | `G` |
| SPANDA | `amazon_music_ui_design_features.md` (JSON text; stray `\[` / `\]` backslashes removed before parsing) | `M` |
| ANĀHATA | SVARĀ's own design (unchanged except shared tokens) | — |

Paths are written relative to the file's root object, for example `S.global_design_attributes.color_palette.brand_primary.hex`.
Labels: **JSON** = taken from the file; **prompt** = the build brief gives it and the JSON does not; **derived** = neither gives it (or the given value fails a rule), so it was chosen from the same preset's palette by the stated method.

Conventions:
- JSON "pt"/"px" are read as Android `sp` (type) and `dp` (sizes).
- Where the JSON gives a range ("16pt-20pt"), the brief's single value is used when it gives one; otherwise the midpoint, rounded to a whole or half unit.
- Contrast: text pairs need WCAG AA 4.5:1; icons and non-text indicators need 3:1 (WCAG 1.4.11). Ratios below are computed with the WCAG relative-luminance formula; the unit test `PresetContrastTest` recomputes all of them.

## Token additions beyond the brief's palette list

The brief's palette has nine roles. Two derived roles are added, because the brand accents can't serve every use and still pass AA:

- `accentText`: the accent when it is drawn as **text** on background or surface. Equal to `accent` when that already passes 4.5:1; otherwise the accent mixed toward black (light) or white (dark) in 0.5 % steps until it passes 4.6:1 on both background and surface.
- `accentFill`: the fill behind **text** labels on accent buttons (pills such as "Play"). Equal to `accent` when `onAccent` text passes 4.5:1 on it; otherwise darkened in 0.5 % steps until it does. Icon-only accent fills (the play/pause circle) keep `accent`, where 3:1 applies.

---

## SHRUTI (`S`)

### Palette

| Token | Dark | Light | Source |
|---|---|---|---|
| background | `#121212` | `#FFFFFF` | dark: JSON `S.global_design_attributes.color_palette.backgrounds.base_background.hex`; light: prompt |
| surface | `#181818` | `#F5F5F5` | dark: JSON `…backgrounds.elevated_background.hex`; light: prompt |
| surfaceHighlight | `#282828` | `#EDEDED` | dark: JSON `…backgrounds.highlight_background.hex`; light: derived (secondaryText still 4.5:1 on it) |
| primaryText | `#FFFFFF` | `#000000` | dark: JSON `…typography_colors.primary_text.hex`; light: prompt |
| secondaryText | `#B3B3B3` | `#6A6A6A` | dark: JSON `…typography_colors.secondary_text.hex`; light: prompt |
| accent | `#1DB954` | `#19A049` | dark: JSON `…brand_primary.hex`; light: **derived** (see conflict C1) |
| accentText | `#1DB954` | `#14803A` | dark: = accent (7.24:1); light: **derived** (see conflict C1) |
| accentFill | `#1DB954` | `#19A049` | = accent (black text passes) |
| onAccent | `#000000` | `#000000` | prompt (8.12:1 dark; 6.6:1 light) |
| scrim | `#99000000` | `#66000000` | derived (sheet scrim) |
| divider | `#282828` | `#E0E0E0` | dark: JSON `…highlight_background.usage` ("subtle dividers"); light: derived |

### Type scale (Roboto, system sans-serif)

`S.global_design_attributes.typography.primary_font` names a proprietary font; it is **not** used (prompt rule 4).

| Slot | Size / weight | Source |
|---|---|---|
| display | 24 sp Bold | JSON `S.….typography.hierarchy.h1` ("Bold, 24pt+") — greeting, playlist titles |
| headline | 22 sp Bold | prompt (Now Playing title) |
| title | 18 sp Bold | JSON `…hierarchy.h2` ("Bold, 16pt-20pt") → prompt value 18 |
| body | 14 sp Regular | derived from `body_primary` size |
| bodyEmphasis | 14 sp Medium | JSON `…hierarchy.body_primary` |
| subhead | 12 sp Regular | JSON `…hierarchy.body_secondary` |
| caption | 11 sp Regular | derived (upper end of `micro_copy` "10pt-11pt") |
| micro | 10 sp Regular | JSON `…hierarchy.micro_copy` → prompt value 10 |

Line heights: derived, 1.2× for display/headline/title, 1.4× for body roles, 1.3× for caption/micro. Letter spacing: 0, except micro uppercase labels ("PLAYING FROM") at 0.08 em (derived).

### Shape and spacing

| Token | Value | Source |
|---|---|---|
| artworkRadius | 8 dp | JSON `S.….spacing_and_layout.corner_radius.cards` |
| cardRadius | 4 dp | prompt (recent grid tiles) |
| pillRadius | 50 % | JSON `…corner_radius.buttons` ("Fully rounded") |
| sheetTopRadius | 20 dp | JSON `…corner_radius.bottom_sheet` ("16px to 24px") → midpoint |
| miniPlayerRadius | 8 dp | derived (= artworkRadius) |
| lyricsCardRadius | 12 dp | prompt |
| screenMargin | 16 dp | JSON `…spacing_and_layout.margins` |
| gridGutter | 8 dp | JSON `…spacing_and_layout.grid_system` ("8pt grid") |
| rowHeight | 64 dp | derived (48 dp artwork + 2 × 8 dp) |
| sectionGap | 24 dp | derived (3 × 8 dp grid) |
| carouselArtwork | 150 dp | JSON `S.screens_and_views.home_screen.carousels.item_design` ("150x150px approx") |
| recentTileArtwork | 48 dp | prompt |
| miniArtwork | 48 dp | prompt |
| playerArtwork | 50 % of screen height | JSON `…now_playing_screen.album_art.design` |
| artworkElevation | 8 dp | prompt |
| scrubberTrack | 4 dp | prompt |
| scrubberHandle | 12 dp (dragging only) | prompt |

### Motion

| Token | Value | Source |
|---|---|---|
| pressScale | 0.95 | prompt (`S.core_ui_components.bottom_navigation_bar.interaction` "Subtle scale-down animation on tap") |
| pressDurationMs | 100 | prompt |
| playPressScale / ms | 0.92 / 80 | prompt |
| transitionMs | 200 | derived (Balanced range 150–250) |
| enableArtworkMotion | false | prompt |
| enableBlur | false | prompt (Part 3) |
| enableAmbientBackground | false | prompt (static gradients) |

### Navigation

| Item | Value | Source |
|---|---|---|
| visibleTabs | Home, Search, Your Library | JSON `S.core_ui_components.bottom_navigation_bar.items[*].label` |
| tabStyle | outlined-to-filled, active tint primaryText, inactive secondaryText | JSON `…items[*].icon_inactive / icon_active`; tints from prompt |
| labels | always visible | JSON `…bottom_navigation_bar.interaction` ("Labels remain visible") |

---

## TĀLA (`A`)

### Palette

| Token | Dark | Light | Source |
|---|---|---|---|
| background | `#000000` | `#FFFFFF` | JSON `A.global_design_attributes.color_palette.backgrounds.dark_mode.base` / `light_mode.base` |
| surface | `#1C1C1E` | `#F2F2F7` | JSON `…dark_mode.secondary` / `light_mode.secondary` |
| surfaceHighlight | `#242426` | `#E5E5EA` | derived (one step above surface; secondaryText still passes) |
| primaryText | `#FFFFFF` | `#000000` | prompt |
| secondaryText | `#8E8E93` | `#3C3C43` at 76 % (≈ `#6B6B70`) | dark: prompt; light: prompt colour, **alpha derived** (conflict C3) |
| accent | `#FA243C` | `#FA243C` | JSON `A.….color_palette.brand_primary.hex` |
| accentText | `#FA364C` | `#D41F33` | **derived** (conflict C2) |
| accentFill | `#E22136` | `#E22136` | **derived** (conflict C2) |
| onAccent | `#FFFFFF` | `#FFFFFF` | prompt |
| scrim | `#99000000` | `#99000000` | prompt (60 % black over the Now Playing backdrop) |
| divider | `#38383A` | `#C6C6C8` | dark: prompt; light: derived |

### Type scale

| Slot | Size / weight | Source |
|---|---|---|
| display | 34 sp Bold | JSON `A.….typography.hierarchy.large_title` |
| headline | 28 sp Bold | JSON `…hierarchy.title_1` |
| title | 22 sp SemiBold | JSON `…hierarchy.title_2` |
| body | 17 sp Regular | JSON `…hierarchy.body` |
| bodyEmphasis | 17 sp SemiBold | JSON `…hierarchy.headline` (also the collapsed inline title) |
| subhead | 15 sp Regular | JSON `…hierarchy.subhead` |
| caption | 12 sp Regular | JSON `…hierarchy.caption` |
| micro | 11 sp Regular | derived (one step below caption) |
| lyricsActive | 28 sp Bold | prompt |

Line heights derived as for SHRUTI. Letter spacing 0 (the JSON's "dynamic tracking" belongs to a proprietary font).

### Shape and spacing

| Token | Value | Source |
|---|---|---|
| artworkRadius | 8 dp | JSON `A.….corner_radius.album_art` ("8px to 12px") → prompt 8 for lists |
| cardRadius (hero) | 12 dp | prompt ("larger for featured hero items") |
| pillRadius | 50 % | prompt |
| sheetTopRadius | 28 dp | prompt (`…corner_radius.modals` "Large continuous curve") |
| miniPlayerRadius | 12 dp | derived (= hero radius) |
| screenMargin | 20 dp | JSON `A.….spacing_and_layout.layout_margins` ("16px to 20px") — upper end, see C8 |
| gridGutter | 16 dp | prompt ("16 dp spacing between items"; JSON "Generous spacing") |
| rowHeight | 56 dp | derived |
| sectionGap | 32 dp | derived (JSON "ample whitespace") |
| carouselArtwork | 150 dp | prompt |
| largeTitleCollapse | 24 dp scroll | prompt |
| chevron | 16 dp | prompt |
| scrubberTrack | 6 dp → 10 dp dragging | prompt (`A.….controls.scrubber` "Thick timeline bar that expands when dragging") |

### Motion

| Token | Value | Source |
|---|---|---|
| tab tint cross-fade | 150 ms | prompt |
| pager page change | 200 ms, no bounce | prompt |
| pausedArtworkScale | 0.9 | prompt (`A.….album_art.state_paused`) |
| springStiffness | 1500 (Compose `StiffnessMedium`) | prompt ("medium stiffness") |
| springDamping | 0.8 | prompt |
| lyrics scroll | 250 ms ease-out | prompt |
| pressScale / ms | 0.95 / 100 | derived (shared tap feedback) |
| enableArtworkMotion | true (pause spring only) | prompt |
| enableBlur | true (mini-player, API 31+, Immersive only) | prompt Part 3 |
| enableAmbientBackground | false | prompt (conflict C5) |
| haptic tick | each 5 s while scrubbing | prompt (`A.interaction_and_animation.haptic_feedback.scrubbing`) |

### Navigation

| Item | Value | Source |
|---|---|---|
| visibleTabs | Home, Library, Search | prompt; order from JSON `A.core_ui_components.bottom_navigation_bar.items` with "Listen Now" → "Home" |
| tabStyle | outlined-to-filled, active tint accent | JSON `…bottom_navigation_bar.interaction` |

---

## RĀGA (`G`)

### Palette

| Token | Dark | Light | Source |
|---|---|---|---|
| background | `#121212` | `#FFFFFF` | JSON `G.global_design_attributes.color_palette.backgrounds.dark_mode.base_background` / `light_mode.base_background` |
| surface | `#1E1E1E` | `#F5F5F5` | JSON `…dark_mode.elevated_background` / `light_mode.elevated_background` |
| surfaceHighlight | `#2A2A2A` | `#ECECEC` | derived |
| primaryText | `#FFFFFF` | `#000000` | JSON `…typography_colors.primary_text` (described, not hex) |
| secondaryText | `#9E9E9E` | `#6B6B6B` | prompt (JSON says only "Muted grey") |
| accent | `#E52D27` | `#E52D27` | JSON `G.….brand_primary.hex` |
| accentText | `#EA524D` | `#D52A24` | **derived** (conflict C2) |
| accentFill | `#E02C26` | `#E02C26` | **derived** (conflict C2) |
| onAccent | `#FFFFFF` | `#FFFFFF` | prompt |
| scrim | `#99000000` | `#99000000` | prompt (60 %) |
| divider | `#2A2A2A` | `#E0E0E0` | dark: prompt (derived); light: derived |

### Type scale

| Slot | Size / weight | Source |
|---|---|---|
| display | 20 sp Bold | JSON `G.….typography.hierarchy.h1` ("Bold, 20pt+") |
| headline | 20 sp Bold | same (screen titles, Now Playing title, karaoke line) |
| title | 17 sp SemiBold | JSON `…hierarchy.h2` ("Semibold, 16pt-18pt") → prompt 17 |
| body | 14 sp Regular | derived from body_primary size |
| bodyEmphasis | 14 sp Medium | JSON `…hierarchy.body_primary` |
| subhead | 12 sp Regular | JSON `…hierarchy.body_secondary` |
| caption | 12 sp Regular | derived (= body_secondary) |
| micro | 10 sp Regular | derived |

The JSON's `primary_font` is already the system sans-serif.

### Shape and spacing

| Token | Value | Source |
|---|---|---|
| artworkRadius | 8 dp | JSON `G.….corner_radius.album_art` |
| cardRadius (category tiles) | 12 dp | prompt |
| pillRadius | 50 % | JSON `…corner_radius.buttons` |
| sheetTopRadius | 16 dp | derived |
| miniPlayerRadius | 0 dp | derived (JSON "Docked", solid bar) |
| screenMargin | 16 dp | JSON `…spacing_and_layout.margins` |
| gridGutter | 12 dp | derived |
| rowHeight | 64 dp | derived (48 dp artwork) |
| sectionGap | 24 dp | derived |
| carouselArtwork | 128 dp | prompt |
| bannerAspect | 16:9 | prompt |
| miniArtwork | 40 dp | prompt (JSON "Tiny thumbnail") |
| scrubberHandle | 8 dp | prompt |

### Motion

| Token | Value | Source |
|---|---|---|
| heart pop | 1.0 → 1.25 → 1.0 in 200 ms, tap only | prompt (`G.interaction_and_animation.microinteractions.like_animation`) |
| playPressScale / ms | 0.92 / 80 | prompt |
| pressScale / ms | 0.95 / 100 | derived |
| transitionMs | 200 | derived (JSON "Standard cross-fades") |
| overlay breathing | 55 %–65 % over 6 s, ≤ 10 fps, Immersive only | prompt |
| enableArtworkMotion | false | prompt |
| enableBlur | false (cached bitmap only) | prompt Part 3 |
| enableAmbientBackground | true (Immersive only) | prompt |

### Navigation

| Item | Value | Source |
|---|---|---|
| visibleTabs | Home, Search, My Music | JSON `G.core_ui_components.bottom_navigation_bar.items` minus Podcasts |
| tabStyle | outlined-to-filled, active tint accent (icon + label) | JSON `…bottom_navigation_bar.interaction`; see conflict C6 |

---

## SPANDA (`M`)

### Palette

| Token | Dark | Light | Source |
|---|---|---|---|
| background | `#000000` | `#FFFFFF` | dark: JSON `M.global_design_attributes.color_palette.backgrounds.base_background.hex`; light: prompt |
| surface | `#191922` | `#F2F2F5` | dark: JSON `…elevated_background.hex`; light: prompt |
| surfaceHighlight | `#22222B` | `#E6E6EB` | derived |
| primaryText | `#FFFFFF` | `#000000` | dark: JSON `…typography_colors.primary_text.hex`; light: prompt |
| secondaryText | `#8A8A8F` | `#5F5F66` | dark: JSON `…typography_colors.secondary_text.hex`; light: prompt |
| accent | `#00A8E1` | `#0094C6` | dark: JSON `…brand_primary.hex`; light: **derived** (conflict C1) |
| accentText | `#00A8E1` | `#00769E` | dark: = accent (7.69:1); light: **derived** (conflict C1) |
| accentFill | `#00A8E1` | `#0094C6` | = accent (black text passes) |
| onAccent | `#000000` | `#000000` | prompt (7.69:1 dark; 6.3:1 light) |
| scrim | `#73000000` | `#73000000` | prompt (45 % black) |
| divider | `#22222B` | `#DCDCE2` | derived |

### Type scale

`M.global_design_attributes.typography.primary_font` names a proprietary font; it is **not** used.

| Slot | Size / weight | Source |
|---|---|---|
| display | 22 sp Bold | JSON `M.….typography.hierarchy.h1` ("Bold, 22pt+") |
| headline | 22 sp Bold | same (carousel headers, Now Playing title) |
| title | 17 sp Bold | JSON `…hierarchy.h2` ("Bold, 16pt-18pt") → prompt 17 |
| body | 14.5 sp Regular | derived from body_primary size |
| bodyEmphasis | 14.5 sp Medium | JSON `…hierarchy.body_primary` ("14pt-15pt") → prompt 14.5 |
| subhead | 12.5 sp Regular | JSON `…hierarchy.body_secondary` ("12pt-13pt") → prompt 12.5 |
| caption | 12.5 sp Regular | derived (= subhead) |
| micro | 11 sp Regular | derived |

### Shape and spacing

| Token | Value | Source |
|---|---|---|
| artworkRadius / cardRadius | 6 dp | JSON `M.….corner_radius.cards` ("approx 4px to 8px") → prompt 6 |
| pillRadius | 50 % | JSON `…corner_radius.buttons` |
| searchFieldRadius | 8 dp | prompt |
| sheetTopRadius | 16 dp | derived |
| miniPlayerRadius | 6 dp | derived (= cardRadius) |
| screenMargin | 16 dp | JSON `…spacing_and_layout.margins` ("16px to 20px") — lower end, see C8 |
| gridGutter | 12 dp | derived |
| rowHeight | 64 dp | derived |
| sectionGap | 24 dp | derived |
| carouselArtwork | 128 dp | prompt |
| artistArtwork | 96 dp, circular | prompt |
| artworkElevation | 16 dp | prompt |
| scrubberTrack | 3 dp | prompt (`M.….controls.scrubber` "Thin progress bar") |
| scrubberHandle | 10 dp (dragging only) | prompt |
| swipe threshold | 35 % of width | prompt |

### Motion

| Token | Value | Source |
|---|---|---|
| swipe snap | 200 ms spring | prompt |
| playPressScale / ms | 0.92 / 80 | prompt |
| pressScale / ms | 0.95 / 100 | derived |
| transitionMs | 150 | derived (`M.interaction_and_animation.transitions.tab_switching` "Instant cut or very fast cross-fade") |
| glow fps | ≤ 20 (Reactive), bars ≤ 30 (Full Spectrum) | prompt |
| enableArtworkMotion | true (finger-follow swipe) | prompt |
| enableBlur | false (cached bitmap only) | prompt Part 3 |
| enableAmbientBackground | true (audio-reactive layer, off by default) | prompt |

### Navigation

| Item | Value | Source |
|---|---|---|
| visibleTabs | Home, Find, Library | JSON `M.core_ui_components.bottom_navigation_bar.items` minus the voice-assistant tab |
| tabStyle | outlined-to-filled, active tint accent | JSON `…items[*].icon_active` |

---

## ANĀHATA

Unchanged: keeps its palette, the 5-step type scale (12/15/18/26/36 sp), radii 8/16/28 dp, and the user's Navigation Studio tab list. It gets the new token fields with values equal to what it already draws, so nothing on screen moves. tabStyle = tinted-pill (the Material 3 indicator it already uses).

---

## Contrast checks (recorded results)

| Pair | Ratio | Result |
|---|---|---|
| SHRUTI light `#1DB954` text on `#FFFFFF` | 2.59 | fails → accentText derived |
| SHRUTI light `#178F41` (prompt's fallback) on `#FFFFFF` | 4.17 | **fails** → `#14803A` (5.03; 4.61 on `#F5F5F5`) |
| SHRUTI light `#1DB954` as a filled control on `#FFFFFF` | 2.59 | fails 3:1 → accent `#19A049` (3.41) |
| TĀLA dark `#FA243C` text on `#1C1C1E` | 4.36 | fails → accentText `#FA364C` (4.62) |
| TĀLA light `#FA243C` text on `#FFFFFF` | 3.90 | fails → accentText `#D41F33` (5.19) |
| TĀLA white text on `#FA243C` | 3.90 | fails → accentFill `#E22136` (4.66); icons on `#FA243C` pass 3:1 |
| TĀLA light secondary `#3C3C43` @ 60 % on `#FFFFFF` | 3.44 | **fails** → 76 % alpha (4.96 on `#F2F2F7`) |
| RĀGA dark `#E52D27` text on `#121212` | 4.23 | fails → accentText `#EA524D` |
| RĀGA light `#E52D27` text on `#FFFFFF` | 4.43 | fails → accentText `#D52A24` (5.02) |
| RĀGA white text on `#E52D27` | 4.43 | fails → accentFill `#E02C26` (4.61); icons pass 3:1 |
| SPANDA light `#00A8E1` on `#FFFFFF` | 2.73 | fails → accent `#0094C6` (3.47) for fills |
| SPANDA light `#0086B3` (prompt's fallback) on `#FFFFFF` | 4.15 | **fails** → accentText `#00769E` (5.14; 4.60 on `#F2F2F5`) |
| SPANDA black on `#00A8E1` | 7.69 | passes |

All dark secondary-text pairs pass on background and surface (5.08–8.93).

---

## Conflicts and how each was resolved

- **C1 — Light accents (SHRUTI, SPANDA).** The prompt's text fallbacks `#178F41` and `#0086B3` fail 4.5:1 on white. The JSON has no light palette for either. Used the derived values above; the brand accents stay unchanged in dark mode.
- **C2 — Red accents as text and under white labels (TĀLA, RĀGA).** The JSON reds are kept for icons, fills under icons, progress and active tints (all ≥ 3:1). Text in accent colour uses `accentText`; text labels on accent pills use `accentFill`. Both are within a few percent of the JSON red.
- **C3 — TĀLA light secondaryText.** The prompt gives `#3C3C43` at 60 %, which fails AA (3.44). Same colour kept; alpha raised to 76 %.
- **C4 — Now Playing gradient "breathing" (SHRUTI).** `S.screens_and_views.now_playing_screen.background.gradient_behavior` says "breathing". The prompt says static. Prompt wins (behaviour rule; battery).
- **C5 — Animated mesh background (TĀLA).** `A.screens_and_views.now_playing_screen.background` is a real-time animated gradient. The prompt specifies a static cached blur with a 60 % scrim. Prompt wins (behaviour rule; battery).
- **C6 — RĀGA tab style.** The JSON describes a colour change only. The prompt's three styles don't include "colour only"; outlined-to-filled with accent tint is the closest (the icon fill change is the only addition). The underline style is not used by any preset.
- **C7 — Lyrics past/upcoming lines (TĀLA).** The JSON says past and upcoming lines are "heavily blurred or semi-transparent". The prompt says 40 % alpha, no blur. Prompt wins (no blur over scrolling content).
- **C8 — Margin ranges.** TĀLA's "16px to 20px" → 20 dp (iOS standard leading margin, and it separates TĀLA's spacious look from the others). SPANDA's identical range → 16 dp (JSON calls it "Standard"; it matches the 128 dp carousel rhythm).
- **C9 — SHRUTI mini-player background.** `S.core_ui_components.now_playing_bar_mini.background` follows the artwork colour. The prompt says surface colour with a 1 dp divider. Prompt wins (it states the mini-player behaviour explicitly).
- **C10 — SHRUTI mini-player layout.** The JSON has a device-connect icon on the right. Omitted (no casting); play/pause only, per the prompt.
- **C11 — RĀGA mini-player background.** JSON allows "a blurred hint from the album art". Prompt says solid surface. Prompt wins (no blur outside the TĀLA mini-player).
- **C12 — RĀGA scrubber.** JSON gives no handle size; the prompt gives 8 dp and a white track. Prompt used.
- **C13 — SHRUTI bottom navigation background.** JSON says translucent with blur. Part 3 forbids blur outside the TĀLA mini-player. Solid `surface` used.
- **C14 — TĀLA tab bar and sheets translucency.** JSON uses blur on tab bars and sheets. Part 3 forbids it. Solid surfaces used; only the mini-player blurs (API 31+, Immersive).
- **C15 — Existing preset personalities.** The current code describes TĀLA as "Fast, functional" with Efficiency as its default tier, and SHRUTI with a serif face. The new layouts replace these: all four use Roboto, and every preset defaults to Balanced (the prompt names Balanced as the default mode). Display names stay the same.

## JSON items omitted, and why

| Source | Item | Reason |
|---|---|---|
| all | Brand names, logos, wordmarks, proprietary fonts (`primary_font`, `design_system`, `app_name`) | Prompt rule 4 |
| `S` | Home header profile picture and bell (`home_screen.header.actions`) | Replaced by history and settings icons (prompt) |
| `S` | Search bar camera icon (`search_screen.search_bar.design`) | Scannable codes are a streaming feature |
| `S` | Browse-all rotated category imagery (`browse_all_grid.card_design.imagery`) | Not in this pass's layout list; SHRUTI's Search keeps the shared search screen styled with SHRUTI tokens |
| `S` | Library Podcasts / Downloaded filters | No podcasts; everything is on the device |
| `S` | Canvas video background (`now_playing_screen.background.canvas_behavior`) | Streaming-only content; battery |
| `S` | Devices / connect icon (`now_playing_screen.bottom_actions.devices`, mini-player connect icon) | No casting; queue icon placed there (prompt) |
| `S` | Equalizer animation in device menu | No device menu |
| `S` | Green "new content" dot, pin icon in Library | No remote content feed |
| `A` | Browse and Radio tabs | No catalogue, no radio |
| `A` | Made for You library row | No recommendation service |
| `A` | Volume slider (`now_playing_screen.controls.volume_slider`) | Prompt: volume follows the system |
| `A` | Audio routing icon | Hidden; Settings shows "Coming in a later update." |
| `A` | Animated cover art; add-to-library checkmark | Streaming-only content; all songs are already in the library |
| `A` | Translucent tab bar and sheets | Part 3 blur rule (C14) |
| `G` | Podcasts tab | No podcast source |
| `G` | Voice search icon (header and search bar) | No voice search |
| `G` | Premium and sponsor banners | No accounts or ads |
| `G` | Radio carousel | No radio |
| `G` | Download icon and download progress ring | All music is local |
| `G` | Animated video-loop artwork | Streaming-only content |
| `G` | Logo at top left | Replaced by the SVARĀ wordmark as plain text |
| `M` | Voice-assistant tab, voice overlay and light bar (`interaction_and_animation.alexa_voice_ui`) | No voice assistant |
| `M` | Cast icon (home header and Now Playing) | No casting |
| `M` | Podcasts carousel and filter, Stations carousel | No podcast or station source |
| `M` | Offline Mode toggle | The whole library is local |
| `M` | Spatial Audio category | Not a property of local files the app reads |
| `M` | Trivia in the lyrics/credits card | No remote data; credits come from local tags |
| `M` | "HD / Ultra HD / Atmos" badge names | Replaced by the file's real format ("FLAC", "MP3 320") |
| `M` | Logo at top | Replaced by the SVARĀ wordmark as plain text |

## Additions and interpretations made while building

Not in the sources; each is needed to keep an existing feature reachable or to resolve an ambiguity.

| Preset | Item | Why |
|---|---|---|
| SHRUTI | "Songs" pill in Your Library | The source's filters have no song list; without it the full Songs list would be unreachable in this preset. |
| SHRUTI, RĀGA, SPANDA | "Playing from" shows the album | The player doesn't record where a queue was started; recording it would change queue logic, which this pass must not touch. |
| TĀLA | Settings icon on Home's large title | Settings is not one of TĀLA's tabs and the source's header has no other way in. |
| TĀLA | Skip buttons: tap changes track, long press seeks by the Control Studio increment | The brief ties the skip buttons to the seek increment; tapping keeps track changes available on Now Playing. |
| SPANDA | Spectrum bars interpolate bass, mid and treble | The analyser reports three band energies, not per-bin spectra. |
| SPANDA | Overlay rises above 45 % only for very bright artwork | At 45 %, white text can fall below 4.5:1 on near-white covers; the overlay is raised just enough (from the cached bitmap's brightest pixel). |
| All four | Album, artist and playlist lists are in-memory lists, not Paging 3 | Only Songs has a paged query; paging the aggregate lists needs new DAO queries. They are small (albums ≈ songs / 10) and use stable keys. |
| All four | Lyrics lines other than the current one use the brief's 40 % / 50 % alpha | Deliberately de-emphasised per the brief; they fall below 4.5:1 by design. The current line passes. |
