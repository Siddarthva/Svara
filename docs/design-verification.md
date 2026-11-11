# Design verification

Each preset's code compared with its design source (project root), then with the build brief. Paths use the short names from `docs/design-source-map.md`: `S` = `spotify_ui_design_features.json` (SHRUTI), `A` = `apple_music_ui_design_features.json` (TĀLA), `G` = `gaana_ui_design_features.json` (RĀGA), `M` = `amazon_music_ui_design_features.md` (SPANDA, parsed after removing the stray `\[` / `\]`). File references are relative to `app/src/main/java/com/svara/app/`; token files are `appearance/tokens/<Preset>Spec.kt`.

Checked at commit `332a791` (before the fixes below). "Derived" means the source gives no value and the source map records how it was chosen; "Brief" means the build brief set it and the source map records why it wins.

The README describes architecture and performance, not preset layouts, so no row needed a README-vs-JSON decision. One JSON-vs-brief disagreement that touches visible text is recorded in SPANDA (banner CTA label).

---

## SHRUTI (`S`)

| Preset | Item | Source path (JSON key) | Expected | Found in code (file:line) | Match | Action |
|---|---|---|---|---|---|---|
| SHRUTI | background | `global_design_attributes.color_palette.backgrounds.base_background.hex` | `#121212` | ShrutiSpec.kt:21 | Yes | — |
| SHRUTI | surface | `…backgrounds.elevated_background.hex` | `#181818` | ShrutiSpec.kt:22 | Yes | — |
| SHRUTI | surfaceHighlight | `…backgrounds.highlight_background.hex` | `#282828` | ShrutiSpec.kt:23 | Yes | — |
| SHRUTI | primaryText | `…typography_colors.primary_text.hex` | `#FFFFFF` | ShrutiSpec.kt:24 | Yes | — |
| SHRUTI | secondaryText | `…typography_colors.secondary_text.hex` | `#B3B3B3` | ShrutiSpec.kt:25 | Yes | — |
| SHRUTI | accent | `…brand_primary.hex` | `#1DB954` | ShrutiSpec.kt:26 | Yes | — |
| SHRUTI | onAccent | not in source (brief) | `#000000` | ShrutiSpec.kt:27 | Yes | — |
| SHRUTI | divider | `…highlight_background.usage` ("subtle dividers") | `#282828` | ShrutiSpec.kt:29 | Yes | — |
| SHRUTI | Typography h1 | `typography.hierarchy.h1` | Bold, 24 | ShrutiSpec.kt:47 (`display` 24 sp 700) | Yes | — |
| SHRUTI | Typography h2 | `…hierarchy.h2` | Bold, 16–20 | ShrutiSpec.kt:49 (`title` 18 sp 700) | Yes | — |
| SHRUTI | Typography body_primary | `…hierarchy.body_primary` | Medium, 14 | ShrutiSpec.kt:51 (`bodyEmphasis` 14 sp 500) | Yes | — |
| SHRUTI | Typography body_secondary | `…hierarchy.body_secondary` | Regular, 12 | ShrutiSpec.kt:52 (`subhead` 12 sp 400) | Yes | — |
| SHRUTI | Typography micro_copy | `…hierarchy.micro_copy` | Regular, 10–11 | ShrutiSpec.kt:54 (`micro` 10 sp 400) | Yes | Line heights derived (1.2× / 1.4× / 1.3×); the source states none |
| SHRUTI | Font | `typography.primary_font` | proprietary face | Roboto (system sans-serif) | Yes (omitted by rule) | Documented in source map |
| SHRUTI | Radius: artwork | `spacing_and_layout.corner_radius.cards` | 8 | ShrutiSpec.kt:61 | Yes | — |
| SHRUTI | Radius: card (quick grid) | `screens_and_views.home_screen.recent_grid.item_design` ("rounded corners") | rounded | ShrutiSpec.kt:62 (4 dp, brief) | Yes | — |
| SHRUTI | Radius: pill | `…corner_radius.buttons` | fully rounded | ThemeTokens `PILL_PERCENT` 50 % | Yes | — |
| SHRUTI | Radius: sheet | `…corner_radius.bottom_sheet` | 16–24 | ShrutiSpec.kt:63 (20) | Yes | — |
| SHRUTI | Margins / grid | `spacing_and_layout.margins`, `.grid_system` | 16, 8 pt grid | ShrutiSpec.kt:68 | Yes | — |
| SHRUTI | Bottom nav labels and order | `core_ui_components.bottom_navigation_bar.items[*].label` | Home, Search, Your Library | ShrutiSpec.kt:82, 85 | Yes | — |
| SHRUTI | Bottom nav icons | `…items[*].icon_inactive / icon_active` | outlined → filled, solid white | ShrutiSpec.kt:83–84; ui/presets/common/PresetChrome.kt:58 | Yes | — |
| SHRUTI | Bottom nav interaction | `…bottom_navigation_bar.interaction` | scale-down on tap, labels visible | ShrutiSpec.kt:70–71; PresetChrome.kt:96 | Yes | — |
| SHRUTI | Bottom nav background | `…bottom_navigation_bar.background_color` | `#181818` with blur | PresetChrome.kt:79 (surface, no blur) | Partial | Blur forbidden by the brief (source map C13) |
| SHRUTI | Mini-player position and layout | `core_ui_components.now_playing_bar_mini.position / layout` | floats above bar; art + text left, play right | ui/presets/shruti/ShrutiChrome.kt:32 | Yes | Connect icon omitted (source map) |
| SHRUTI | Mini-player background | `…now_playing_bar_mini.background` | artwork colour | ShrutiChrome.kt:38 (surface) | Partial | Brief wins (source map C9) |
| SHRUTI | Mini-player gestures | `…now_playing_bar_mini.interaction` | tap / swipe sideways skips / swipe up expands | ShrutiChrome.kt:43; PresetChrome.kt:139 | Yes | — |
| SHRUTI | Home header | `screens_and_views.home_screen.header` | greeting; profile, bell, history | ui/presets/shruti/ShrutiScreens.kt:67 (greeting, history, settings) | Yes | Profile and bell replaced by brief |
| SHRUTI | Home recent grid | `…home_screen.recent_grid` | 2 columns, 6–8, square image left, `#282828`, rounded | ShrutiScreens.kt:116 | Yes | — |
| SHRUTI | Home carousels | `…home_screen.carousels` | "Jump back in", "Made for you"; 150 art; 2-line title; no borders | ShrutiScreens.kt:105–107 | Yes | — |
| SHRUTI | Home dynamic background | `…home_screen.dynamic_background` | radial gradient from top-left in latest artwork colour | ShrutiScreens.kt:78 | Yes | Static per brief |
| SHRUTI | Search bar | `…search_screen.search_bar` | sticky, white field, black text, pill, icon left | ShrutiScreens.kt:172 | Yes | Camera icon omitted (source map) |
| SHRUTI | Search browse-all grid | `…search_screen.browse_all_grid` | 2-column cards, vivid colours, bold white top-left label, image rotated ~25° overflowing bottom-right | — | No | Fixed in `91498e7` |
| SHRUTI | Library filters | `…your_library_screen.header.filters` | pill toggles, horizontal scroll | ShrutiScreens.kt:148–157 | Yes | Podcasts and Downloaded omitted; Songs added (source map) |
| SHRUTI | Library sort | `…list_view.sorting` | Recents, Recently Added, Alphabetical, Creator | ShrutiScreens.kt:159 | Partial | "Recents" needs per-album last-played time, which the database doesn't keep; left out and recorded |
| SHRUTI | Library grid/list toggle | `…list_view.layout_toggle` | grid view vs list view | — | No | Fixed in `91498e7` |
| SHRUTI | Library item shapes | `…list_view.items` | circular artists, square albums and playlists | ui/presets/common/PresetLibrary.kt (PresetRow `circular`) | Yes | Pins and new-content dots omitted (source map) |
| SHRUTI | Now Playing background | `…now_playing_screen.background.gradient_behavior` | artwork-colour vertical gradient to black | ui/presets/shruti/ShrutiPlayer.kt:90 | Yes | "Breathing" not used (brief, C4) |
| SHRUTI | Now Playing header | `…now_playing_screen.header` | context label, chevron-down left, ellipsis right | ShrutiPlayer.kt:141–149 | Yes | — |
| SHRUTI | Now Playing art | `…album_art` | large square, ~50 % height, heavy shadow | ShrutiSpec.kt:100–101; ShrutiPlayer.kt:95 | Yes | Elevation 8 dp per brief |
| SHRUTI | Track info | `…track_info` | left-aligned, bold title marquee, tappable artist | ShrutiPlayer.kt:100 | Yes | — |
| SHRUTI | Scrubber | `…controls.timeline_slider` | thin white line, thicker elapsed, small dot, times both sides | ShrutiSpec.kt:97–99 | Yes | — |
| SHRUTI | Controls order | `…controls.primary_buttons` | shuffle, previous, play (white circle, black icon), next, repeat | ShrutiPlayer.kt:155 | Yes | — |
| SHRUTI | Bottom actions | `…bottom_actions` | devices left, share right | ShrutiPlayer.kt:124–126 (queue left, share right) | Yes | Devices → queue (brief) |
| SHRUTI | Lyrics card | `…lyrics_card` | below fold, rounded, artwork colour, tap to expand, current line white | ShrutiPlayer.kt:183, 209 | Yes | — |
| SHRUTI | Like animation | `interaction_and_animation.microinteractions.like_button` | burst/pop, outline → filled green | ShrutiPlayer.kt (heart tints, no pop) | Partial | Fixed in `91498e7` |
| SHRUTI | Haptics | `interaction_and_animation.haptic_feedback` | on play, like, and while adjusting the scrub bar | play and like only | Partial | Fixed in `91498e7` |
| SHRUTI | Press timing | brief | 0.95 / 100 ms; play 0.92 / 80 ms | ShrutiSpec.kt:70–71; ThemeTokens | Yes | — |

## TĀLA (`A`)

| Preset | Item | Source path (JSON key) | Expected | Found in code (file:line) | Match | Action |
|---|---|---|---|---|---|---|
| TĀLA | background | `global_design_attributes.color_palette.backgrounds.dark_mode.base` | `#000000` | TalaSpec.kt:19 | Yes | — |
| TĀLA | surface | `…dark_mode.secondary` | `#1C1C1E` | TalaSpec.kt:20 | Yes | — |
| TĀLA | surfaceHighlight | not in source | derived `#242426` | TalaSpec.kt:21 | Yes (derived) | — |
| TĀLA | primaryText | brief | `#FFFFFF` | TalaSpec.kt:22 | Yes | — |
| TĀLA | secondaryText | brief | `#8E8E93` | TalaSpec.kt:23 | Yes | Light variant alpha raised for AA (C3) |
| TĀLA | accent | `…brand_primary.hex` | `#FA243C` | TalaSpec.kt:24 | Yes | Text uses derived `accentText` (C2) |
| TĀLA | onAccent | brief | `#FFFFFF` | TalaSpec.kt:25 | Yes | — |
| TĀLA | divider | brief | `#38383A` | TalaSpec.kt:27 | Yes | — |
| TĀLA | Light palette | `…light_mode.base / secondary` | `#FFFFFF`, `#F2F2F7` | TalaSpec.kt:32–33 | Yes | — |
| TĀLA | Typography (7 levels) | `typography.hierarchy.*` | 34 B, 28 B, 22 SB, 17 SB, 17 R, 15 R, 12 R | TalaSpec.kt:49–55 | Yes | — |
| TĀLA | Radius: artwork / hero | `spacing_and_layout.corner_radius.album_art` | 8–12, larger for hero | TalaSpec.kt:63, 67 | Yes | — |
| TĀLA | Radius: sheets | `…corner_radius.modals` | large continuous | TalaSpec.kt:65 (28) | Yes | — |
| TĀLA | Margins | `…layout_margins` | 16–20 | TalaSpec.kt:71 (20) | Yes | — |
| TĀLA | Bottom nav | `core_ui_components.bottom_navigation_bar.items`, `.interaction` | outlined inactive, filled red active | TalaSpec.kt:87–89; PresetChrome.kt:58 | Yes | Browse and Radio omitted (source map) |
| TĀLA | Tab tint transition | brief | 150 ms cross-fade | TalaSpec.kt:81 | Yes | — |
| TĀLA | Mini-player | `core_ui_components.now_playing_bar_mini` | translucent, hairline, art + shadow, title/artist, play/pause + next, tap / long press | ui/presets/tala/TalaChrome.kt:61–85 | Yes | Live blur only Immersive, API 31+ |
| TĀLA | Home header | `screens_and_views.listen_now_screen.header` | large title → inline on scroll | ui/presets/tala/TalaScreens.kt:66–100 | Yes | Settings icon added (source map "Additions") |
| TĀLA | Home hero | `…listen_now_screen.hero_section` | large editorial artwork, slight margins | TalaScreens.kt:139 | Yes | — |
| TĀLA | Home carousels | `…listen_now_screen.carousels` | horizontal, square art, text under, generous spacing | TalaScreens.kt:127–129; TalaSpec.kt:71 (gutter 16) | Yes | — |
| TĀLA | Library list | `…library_screen.list_view` | Playlists, Artists, Albums, Songs, Made for You, Downloaded; red chevrons | TalaScreens.kt:171 | Yes | Made for You omitted; Downloaded → "On this device" |
| TĀLA | Library recently added | `…library_screen.recently_added` | 2-column grid, bold title, regular artist | TalaScreens.kt:171 | Yes | — |
| TĀLA | Now Playing background | `…now_playing_screen.background` | animated mesh gradient | ui/presets/tala/TalaPlayer.kt:78 (cached blur + 60 % scrim) | Partial | Brief wins (C5) |
| TĀLA | Now Playing header | `…now_playing_screen.header` | pill dismiss indicator, ellipsis | TalaPlayer.kt:84–93 | Yes | — |
| TĀLA | Album art states | `…album_art.state_playing / state_paused` | large, soft shadow; scales down with spring when paused | TalaPlayer.kt:131; TalaSpec.kt:76–84 | Yes | — |
| TĀLA | Scrubber | `…controls.scrubber` | thick, expands when dragging, times adjust | TalaSpec.kt:101–103; TalaPlayer.kt:108–113 | Yes | — |
| TĀLA | Primary controls | `…controls.primary_actions` | large play/pause flanked by skip back/forward | TalaPlayer.kt:169 | Yes | — |
| TĀLA | Volume slider | `…controls.volume_slider` | horizontal slider | — | Yes (omitted) | Brief: volume follows the system (source map) |
| TĀLA | Bottom actions | `…controls.bottom_actions` | lyrics, AirPlay-style routing, queue | TalaPlayer.kt:116–118 | Yes | Routing is a documented placeholder |
| TĀLA | Lyrics view | `…lyrics_view` | full-screen overlay, bold active line, others faded, soft scroll | TalaPlayer.kt:196; TalaSpec.kt:83, 107–110 | Yes | No blur on other lines (C7) |
| TĀLA | Sheets | `interaction_and_animation.transitions.modal_sheets` | bottom sheets, swipe to dismiss | TalaChrome.kt:96; NowPlayingScreen sheets | Yes | — |
| TĀLA | Haptics | `…haptic_feedback.scrubbing / buttons` | ticks while scrubbing; tap feedback | TalaPlayer.kt:112; transport `a.haptic()` | Yes | — |

## RĀGA (`G`)

| Preset | Item | Source path (JSON key) | Expected | Found in code (file:line) | Match | Action |
|---|---|---|---|---|---|---|
| RĀGA | background | `…backgrounds.dark_mode.base_background` | `#121212` | RagaSpec.kt:17 | Yes | — |
| RĀGA | surface | `…dark_mode.elevated_background` | `#1E1E1E` | RagaSpec.kt:18 | Yes | — |
| RĀGA | surfaceHighlight | not in source | derived `#2A2A2A` | RagaSpec.kt:19 | Yes (derived) | — |
| RĀGA | primaryText | `…typography_colors.primary_text` ("White in dark mode") | `#FFFFFF` | RagaSpec.kt:20 | Yes | — |
| RĀGA | secondaryText | `…typography_colors.secondary_text` ("Muted grey") | brief `#9E9E9E` | RagaSpec.kt:21 | Yes | — |
| RĀGA | accent | `…brand_primary.hex` | `#E52D27` | RagaSpec.kt:22 | Yes | — |
| RĀGA | onAccent | brief | `#FFFFFF` | RagaSpec.kt:23 | Yes | — |
| RĀGA | divider | brief (derived) | `#2A2A2A` | RagaSpec.kt:25 | Yes | — |
| RĀGA | Light palette | `…light_mode.*` | `#FFFFFF`, `#F5F5F5` | RagaSpec.kt:30–31 | Yes | — |
| RĀGA | Typography | `typography.hierarchy.*` | h1 Bold 20; h2 Semibold 16–18; body Medium 14; secondary Regular 12 | RagaSpec.kt:46–51 | Yes | — |
| RĀGA | Radius: artwork / pill | `…corner_radius.album_art / buttons` | 8; fully rounded | RagaSpec.kt:60 | Yes | — |
| RĀGA | Bottom nav | `core_ui_components.bottom_navigation_bar` | Home, Podcasts, Search, My Music; active red, inactive grey | RagaSpec.kt:86–89 | Yes | Podcasts omitted (source map) |
| RĀGA | Mini-player | `…now_playing_mini_player` | docked; tiny thumb; scrolling title+artist; play/pause, next; tap except buttons | ui/presets/raga/RagaChrome.kt:27 | Yes | — |
| RĀGA | Home header | `screens_and_views.home_screen.top_header` | logo, language selector, settings, voice search | ui/presets/raga/RagaScreens.kt:86–95 | Yes | Wordmark text; voice search omitted; search icon from brief |
| RĀGA | Home feed | `…home_screen.content_feed.carousels` | Top Charts, New Releases, Moods & Genres, Radio | RagaScreens.kt:113–118 | Yes | Radio omitted |
| RĀGA | Home cards | `…content_feed.card_design` | square art, 2-line text | RagaScreens.kt:114–116 (`titleLines = 2`) | Yes | — |
| RĀGA | Home banners | `…content_feed.promotions` | large rectangular banner carousel | RagaScreens.kt:130 | Yes | Premium and sponsor content omitted |
| RĀGA | Search bar | `…search_and_browse_screen.search_bar` | prominent top bar, placeholder | RagaScreens.kt:201 | Yes | Microphone omitted |
| RĀGA | Browse categories | `…search_and_browse_screen.browse_categories` | moods, decades, languages; gradient; bold centred text | RagaScreens.kt:195–196 | Partial | Decades need a year-range collection query the library doesn't have; moods appear only with mood tags (none read yet). Recorded |
| RĀGA | My Music tabs | `…my_music_screen.header` | Downloads, Playlists, Liked Songs, Local Files | RagaScreens.kt:175–178 | Yes | Downloads labelled "On device" |
| RĀGA | My Music rows | `…my_music_screen.list_view.items` | art left, title/artist, three dots | PresetLibrary.kt (PresetSongRow) | Yes | Offline marks hidden (brief) |
| RĀGA | Now Playing background | `…now_playing_screen.background` | blurred art + dark overlay | ui/presets/raga/RagaPlayer.kt:80 | Yes | — |
| RĀGA | Now Playing header | `…now_playing_screen.header` | down arrow, source context, options | RagaPlayer.kt:92–97 | Yes | — |
| RĀGA | Track info | `…track_info` | title, artist, heart, download | RagaPlayer.kt:113–119 | Yes | Download omitted (all local) |
| RĀGA | Controls | `…controls.primary_actions` | shuffle, previous, play, next, repeat | RagaPlayer.kt:164 | Yes | — |
| RĀGA | Lyrics | `…lyrics_view` | button or swipe up; karaoke in red or white | RagaPlayer.kt:102–108, 185 | Yes | — |
| RĀGA | Like animation | `interaction_and_animation.microinteractions.like_animation` | fills red, small pop | RagaPlayer.kt:142; RagaSpec.kt:78–79 | Yes | — |

## SPANDA (`M`)

| Preset | Item | Source path (JSON key) | Expected | Found in code (file:line) | Match | Action |
|---|---|---|---|---|---|---|
| SPANDA | background | `…backgrounds.base_background.hex` | `#000000` | SpandaSpec.kt:24 | Yes | — |
| SPANDA | surface | `…backgrounds.elevated_background.hex` | `#191922` | SpandaSpec.kt:25 | Yes | — |
| SPANDA | surfaceHighlight | not in source | derived `#22222B` | SpandaSpec.kt:26 | Yes (derived) | — |
| SPANDA | primaryText | `…typography_colors.primary_text.hex` | `#FFFFFF` | SpandaSpec.kt:27 | Yes | — |
| SPANDA | secondaryText | `…typography_colors.secondary_text.hex` | `#8A8A8F` | SpandaSpec.kt:28 | Yes | — |
| SPANDA | accent | `…brand_primary.hex` | `#00A8E1` | SpandaSpec.kt:29 | Yes | — |
| SPANDA | onAccent | brief | `#000000` | SpandaSpec.kt:30 | Yes | — |
| SPANDA | divider | not in source | derived `#22222B` | SpandaSpec.kt:32 | Yes (derived) | — |
| SPANDA | Typography | `typography.hierarchy.*` | h1 Bold 22; h2 Bold 16–18; body Medium 14–15; secondary Regular 12–13 | SpandaSpec.kt:50–55 | Yes | — |
| SPANDA | Radius: cards / pill | `…corner_radius.cards / buttons` | 4–8; pill | SpandaSpec.kt:64–65 (6) | Yes | — |
| SPANDA | Bottom nav | `core_ui_components.bottom_navigation_bar` | Home, Find, Library, Alexa; outlined → filled cyan; background `#191922` | SpandaSpec.kt:85–88; PresetChrome.kt:79 | Yes | Voice-assistant tab omitted |
| SPANDA | Mini-player | `…now_playing_mini_player` | floating; thumb left; title/artist; play/pause, skip; tap; swipe skips | ui/presets/spanda/SpandaChrome.kt:30 | Yes | — |
| SPANDA | Home header | `screens_and_views.home_screen.header` | logo; settings, cast | ui/presets/spanda/SpandaScreens.kt:59 | Yes | Wordmark text; cast omitted |
| SPANDA | Home carousels | `…home_screen.carousels` | "My Discovery Mix", "Podcasts for You", "Stations" | SpandaScreens.kt:73–76 | Yes | Podcasts and Stations omitted |
| SPANDA | Home banner | `…home_screen.promotional_banners` | edge-to-edge featured banner with "Listen Now" CTA | SpandaScreens.kt:83–101 ("Play" pill) | Partial | JSON label "Listen Now" vs brief "Play": brief kept (it states the action); recorded |
| SPANDA | Find search | `…find_screen.search_bar` | sticky, microphone | SpandaScreens.kt:123 | Yes | Microphone omitted |
| SPANDA | Find browse | `…find_screen.browse_categories` | colourful rounded cards: Genres, Moods, Spatial Audio, New Releases | SpandaScreens.kt:117–118 | Yes | Spatial Audio omitted; Moods only with tags; New Releases = "Recently added" |
| SPANDA | Library | `…library_screen.filters`, `.list_view` | pills: Playlists, Songs, Albums, Artists, Podcasts; rows with vertical ellipsis | SpandaScreens.kt:131–139 | Yes | Podcasts and Offline omitted |
| SPANDA | Now Playing background | `…now_playing_screen.background` | blurred art fading to dark at the bottom | ui/presets/spanda/SpandaPlayer.kt:96 | Yes | — |
| SPANDA | Now Playing header | `…now_playing_screen.header` | down-chevron, cast, context menu | SpandaPlayer.kt:101–106 | Yes | Cast omitted |
| SPANDA | Album art | `…album_art` | large centred square, swipe to change tracks | SpandaPlayer.kt:110–111, 165 | Yes | — |
| SPANDA | Track info + badge | `…track_info` | left-aligned title/artist, quality badge | SpandaPlayer.kt:114–120 | Yes | — |
| SPANDA | Controls | `…controls` | thin scrubber; shuffle, previous, play, next, repeat | SpandaSpec.kt:99–101; SpandaPlayer.kt:216 | Yes | — |
| SPANDA | X-Ray card | `…x_ray_feature` | card below controls: lyrics, credits; swipe up for lyrics | SpandaPlayer.kt:129, 237 | Yes | Trivia omitted |
| SPANDA | Quality badge sheet | `interaction_and_animation.microinteractions.audio_quality_badges` | tap opens bit depth and sample rate | SpandaPlayer.kt:120 → Audio Information sheet | Yes | — |
| SPANDA | Like animation | `…microinteractions.like_button` | heart fills white or cyan, scale-up-and-down pop | — (no heart on Spanda Now Playing) | No | Fixed in `e7611e6` |
| SPANDA | Tab switching | `…transitions.tab_switching` | instant cut or very fast cross-fade | SpandaSpec.kt:75 (150 ms); no tab tint fade | Yes | — |
| SPANDA | Audio-reactive layer | brief | off by default; Ambient / Reactive ≤ 20 fps / Full ≤ 30 fps; Immersive only | ThemeModel `visualiser = OFF`; ui/presets/spanda/SpandaReactive.kt:37–80; EffectsPolicy `PresetEffects` | Yes | — |

## ANĀHATA

No external source. Its tokens (AnahataSpec.kt:18–88) equal the values its composables drew before the token model (the earlier `Palettes.ANAHATA`, `TypeScale` 12/15/18/26/36, `Radii` 8/16/28 and the sizes now in `ui/theme/AnahataTokens.kt`). Match: Yes for every row; nothing to fix.

---

## Omissions checked against the source map

Every omission in the tables above (podcasts, voice search and voice assistant, Radio, Browse, device connect and cast, streaming-only artwork and video, premium and sponsor content, download state, trivia, Spatial Audio) is listed in `docs/design-source-map.md` → "JSON items omitted". The SHRUTI browse-all grid was listed there with a weak reason ("not in this pass's layout list"); it is not a streaming feature, so it is treated as a defect and fixed here.

## Brand-name scan

Searched `app/src` (sources, resources, manifest, file names; generated Baseline Profiles excluded) and `benchmark/src` for: Spotify, Apple, Music (as a brand), Gaana, Amazon, Alexa, Circular, SF Pro, Ember.

| Term | Matches | Verdict |
|---|---|---|
| Spotify, Gaana, Amazon, Alexa, SF Pro, Ember | 0 | — |
| Apple | 1: `playlists/PlaylistsScreens.kt:275` `"application/vnd.apple.mpegurl"` | Registered IANA media type for `.m3u8` playlists, needed by M3U import. Not branding; allowlisted in `BrandNameScanTest` |
| Circular | `CircularProgressIndicator`, `CircleShape` and "circular artwork" comments | Material component and plain English, not the font name "Circular Std"; no match for the font |
| Music (as a brand) | 0 ("Apple Music", "Amazon Music" absent); "My Music" is RĀGA's tab label from its source and is generic | — |

`BrandNameScanTest` covers the same terms on every unit-test run.

## Summary (before fixes)

| Preset | Yes | Partial | No |
|---|---|---|---|
| SHRUTI | 41 | 5 | 2 |
| TĀLA | 30 | 1 | 0 |
| RĀGA | 26 | 1 | 0 |
| SPANDA | 26 | 1 | 1 |
| ANĀHATA | all | 0 | 0 |

Partials kept by decision (brief wins or data not available): SHRUTI nav blur, mini-player colour, "Recents" sort; TĀLA animated background; RĀGA decades; SPANDA banner label. Fixes for the rest are listed below.

## Fixes

| Preset | Item | Commit |
|---|---|---|
| SHRUTI | Browse-all grid on Search | `91498e7` |
| SHRUTI | Library grid/list toggle | `91498e7` |
| SHRUTI | Like pop | `91498e7` |
| SHRUTI | Haptic ticks while scrubbing | `91498e7` |
| SPANDA | Heart with fill and pop | `e7611e6` |

## After fixes

| Preset | Yes | Partial | No |
|---|---|---|---|
| SHRUTI | 45 | 3 | 0 |
| TĀLA | 30 | 1 | 0 |
| RĀGA | 26 | 1 | 0 |
| SPANDA | 27 | 1 | 0 |
| ANĀHATA | all | 0 | 0 |

Remaining Partials are decisions recorded above (brief wins, or the data the source assumes isn't stored).
