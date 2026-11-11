# SimpMusic on Android TV — P2 visual design spec

Status: draft for owner review, 2026-11-10. Implements the owner's request to make the TV app look
TV-native and production-ready. The plan that executes it is
`docs/superpowers/plans/2026-11-10-tv-p2-visual.md`.

## Sources

- Android TV design guides (developer.android.com/design/ui/tv): design-for-tv, layouts, typography,
  cards, navigation drawer, tabs, immersive list, and the playback and Now Playing training pages.
  The color, motion and focus-system pages returned 404 when read on 2026-11-10, so the values for
  those topics below are this spec's decisions, not quotes.
- The `apple-design` skill (WWDC design talks): springs instead of fixed durations, interruptible
  motion, material weight as hierarchy, reduced-motion and reduced-transparency fallbacks,
  size-specific tracking, and "where am I, where can I go, how do I leave" on every screen.
- Owner feedback (memory `feedback-tv-ui-aesthetics`): no outline focus rings (they "look like an
  accessibility selector"), no phone or tablet layouts unchanged on TV, a fullscreen player, and
  Spotify's TV app as the reference, with Now Playing in Home's top bar beside the profile.

Where the two sources conflict, the Android TV guide wins: it covers what is specific to TV, such
as reading at 3 m, overscan, the focus model and panel contrast.

## Device facts that constrain the design

- Owner's test TV: Philips, Android 11 (API 30), armeabi-v7a, about 480 MB free storage, release
  APK about 30 MB. The canvas is 960 × 540 dp (16:9, 1 px = 1 dp at MDPI).
- Glass capability comes in three tiers, so they are not treated as visually equivalent:
  - **API 30 (the owner's TV):** no `RenderEffect`. Neither `Modifier.blur` nor Kyant backdrop
    blur or refraction renders (`isLyricsBlurSupported()` already returns false below API 31).
    Glass is drawn without sampling the backdrop.
  - **API 31–32:** `RenderEffect` blur works, but there is no `RuntimeShader`, so no lens or
    refraction.
  - **API 33+:** the full Kyant Liquid Glass.

  Each tier gets its own screenshot before it is called done.
- Many TV owners set the animator scale to 0. Every animation must have a reduced-motion form.

## Principles

1. Focus is the interface. The focused element is the most visible thing on screen. It is shown
   by scale, light and elevation, never by an outline.
2. Content first. Artwork and video carry the color. Chrome is neutral, translucent and recedes.
3. One clear path. Rows move left and right, sections move up and down, and Back always goes one
   level up and never exits playback by surprise.
4. Readable at 3 m. Nothing below 12 sp. Secondary text keeps a contrast of at least 4.5:1.
5. Motion explains. Springs start from the current value and can be interrupted, so holding a
   D-pad key never queues animations.
6. Phone and desktop do not change. Every rule here applies only when `LocalTvDevice` is true.

## Layout tokens (`ui/tv/TvTokens.kt`)

| Token | Value | Source |
|---|---|---|
| Safe area, horizontal | 48 dp | Android TV guide, layouts |
| Safe area, vertical | 27 dp | Android TV guide, layouts |
| Backgrounds | full-bleed, never clipped to the safe area | Android TV guide, layouts |
| Gutter and card gap | 20 dp | Android TV guide, layouts and cards |
| Gap between rows | 32 dp | decision |
| Header to its row | 12 dp | decision |
| Rail, collapsed | 72 dp | decision |
| Rail, expanded (modal, over content) | 248 dp | decision |
| Rail item height | 48 dp | decision |
| Square card (album, playlist, mix) | 148 dp, 1:1 | decision: 5 cards plus a peek in the 840 dp beside the rail |
| Wide card (video, episode) | 268 × 151 dp, 16:9 | Android TV guide: 3-up width |
| Artist card | 148 dp circle | decision |
| Quick-pick row item | 412 dp wide, 56 dp tall | Android TV guide: 2-up width |
| Corner, card | 12 dp | decision |
| Corner, small (thumbnails in rows) | 8 dp | decision |
| Corner, panel and dialog | 24 dp | decision |
| Controls | capsule (50%) | decision |
| Side panel width | 400 dp | decision |

## Focus (`ui/tv/TvFocus.kt`)

There are four styles. They are selected by the call site, and all of them animate with the
focus spring.

| Style | Used on | Focused look |
|---|---|---|
| `Card` | artwork cards | scale 1.10, a 24 dp shadow tinted by the artwork at 35% alpha, and the title going from secondary to primary text |
| `Control` | buttons, chips, rail items, top-bar items | scale 1.05; the fill turns `focusFill` and the content turns `onFocus` (white pill, dark content, as on the Google TV launcher) |
| `Row` | list rows (tracks, settings) | scale 1.02 and a `surface2` backing in the row's shape |
| `Backing` | legacy call sites not yet migrated (P1 `tvFocusIndicator()`) | scale 1.06 and a `surface2` backing |

Rows of cards keep the focused card at the 48 dp start keyline once scrolling starts, Leanback
style. Lists of sections place the focused row's top at 25% of the viewport height.

## Color (`ui/tv/TvColors.kt`)

The TV forces the dark scheme. API 30 has no dynamic wallpaper color.

| Token | Value |
|---|---|
| `background` | #0E0E11 |
| `surface1` | white at 6% |
| `surface2` | white at 12% |
| `glassFill` | white at 8%, with a 1 dp rim going from white 22% at the top to 0% at the bottom |
| `textPrimary` | #F5F5F7 |
| `textSecondary` | #F5F5F7 at 64% (must be ≥ 4.5:1 on `background`) |
| `textTertiary` | #F5F5F7 at 40% (large text and decoration only) |
| `focusFill` | #F5F5F7 |
| `onFocus` | #0E0E11 |
| `accent` | the artwork tint (`rememberNowPlayingGlowTint`), falling back to the app accent; used only for progress, the selected indicator and the playing indicator |
| `scrim` | black at 0% to 85%, as a vertical or horizontal gradient |

Large flat areas never use pure white (#FFFFFF), which blooms on TV panels.

## Typography (`ui/tv/TvTypography.kt`)

The font stays Poppins, the app's brand font. It is not decorative, so the guide's rule allows it.
Sizes are in sp, with line height as a multiple of the size and tracking in sp.

| Role | Size | Line height | Tracking |
|---|---|---|---|
| displayLarge | 48 | 1.12 | −0.5 |
| displayMedium | 40 | 1.12 | −0.5 |
| displaySmall | 34 | 1.15 | −0.25 |
| headlineLarge | 30 | 1.2 | −0.25 |
| headlineMedium | 26 | 1.2 | −0.25 |
| headlineSmall | 22 | 1.25 | 0 |
| titleLarge | 20 | 1.3 | 0 |
| titleMedium | 16 | 1.35 | 0 |
| titleSmall | 14 | 1.35 | 0.1 |
| bodyLarge | 16 | 1.45 | 0 |
| bodyMedium | 14 | 1.45 | 0 |
| bodySmall | 13 | 1.4 | 0.1 |
| labelLarge | 14 | 1.3 | 0.1 |
| labelMedium | 13 | 1.3 | 0.2 |
| labelSmall | 12 | 1.3 | 0.2 |

Display styles are only for short text such as the player title. Section headers use
headlineSmall. Cards use titleSmall for the title and bodySmall for the subtitle.

## Motion (`ui/tv/TvMotion.kt`)

| Token | Value |
|---|---|
| Focus spring | dampingRatio 1.0, stiffness 600 (about 0.26 s, no bounce) |
| Panel spring (rail, side panel) | dampingRatio 0.9, stiffness 380 (about 0.32 s) |
| Fade in | 180 ms |
| Fade out | 140 ms |
| Overlay hide timeout (player) | 5 000 ms after the last key |
| Artwork drift (player) | scale 1.00 → 1.04 and back over 24 s, linear and repeating |
| Track change | outgoing artwork exits toward the direction of travel; incoming enters from the other side (next: from the right) |

Reduced motion applies when the system animator scale is 0. It means immediate: springs snap,
slides, drift and cross-fades are removed, and every change happens in one frame. Focus scale and
color are still applied at once, so feedback remains.

## Materials (`ui/tv/TvGlass.kt`, `ui/tv/TvAmbientBackground.kt`)

- **TV glass** is `glassFill` plus the rim, with a 16 dp black shadow at 30%. On API 31+ with the
  Liquid Glass setting on, it uses the existing `Modifier.liquidGlass`. On API 30 it is drawn
  without the backdrop, so it looks the same with or without blur.
- **Ambient background.** The current artwork is decoded small through Coil and stretched to full
  screen with bilinear filtering, which aims to read as a heavy blur on every API level for almost
  no memory. It is drawn at 55% over `background`, under a bottom scrim. With no artwork, only
  `background` is drawn.
  - **Decode size:** a single constant, `AMBIENT_DECODE_PX`, starting at 32.
  - **Not yet approved:** the look is accepted only after the owner sees screenshots at 16, 32
    and 64 px, because upscaling can read as pixelation instead of blur.
  - **Fallback:** if every size looks pixelated, the plan's fallback is a vertical gradient
    between two palette colors of the artwork.
- Material weight follows hierarchy: the rail and the top bar use `surface1`, panels and dialogs
  use glass plus a scrim behind them, and the player's controls sit on a gradient scrim rather
  than a panel.

## Screens

### P2-A: identity (navigation, Home, cards)

- **Rail.** A collapsed 72 dp rail of icons. When focus enters it, it expands over the content to
  248 dp with labels and a left-to-right scrim. There are at most six destinations. The selected
  destination shows an `accent` indicator dot, and the focused one uses the `Control` style. The
  profile and settings sit in the bottom section.
- **Top bar (Home).** Profile, then the Now Playing chip, then the mood chips, all in the
  `Control` style.
- **Immersive header (Home).** The first content row drives a header 220 dp tall. It shows the
  focused card's artwork aligned to the top-right with a cinematic scrim, the title in
  displaySmall and the subtitle in bodyLarge. It cross-fades when focus moves, after focus has
  stayed on a card for 300 ms, so holding a key does not reload images.
- **Cards and rows** use the sizes, gaps and `Card` focus above, with the start keyline scrolling.
- **Now Playing chip** keeps its P1 placement and takes the `Control` style; its artwork shows
  an animated playing indicator.

### P2-B: cinematic player

- **Audio.** On the left, 320 dp artwork (corner 16 dp, tinted shadow, drift). On the right, the
  title in displaySmall, the artist in titleLarge at `textSecondary`, and a synced lyrics pane of
  five lines: one before, the current line, and three after. The current line is in headlineSmall
  at `textPrimary`; the others are in titleLarge at `textTertiary`. Behind it all is the ambient
  background. At the bottom there is a full-width progress bar inside the safe area and a
  centered control row.
- **Video.** Full bleed. The controls overlay the bottom on a scrim. Up or down shows the title and
  progress without pausing, as the Android TV playback guide requires.
- **Keys** follow the Android TV playback table:
  - **Center:** play or pause, which also shows the controls. While the controls are visible,
    Center activates the focused control.
  - **Up/down:** show the info and controls without pausing. The waking key presses nothing.
  - **Left/right single press:** seek ±5 s.
  - **Left/right held:** scrub at a hold-time based speed, not per key repeat:
    - 10 s of media per second of hold in the first second;
    - 30 s/s from 1 to 3 s;
    - 60 s/s after that, and never faster.

    The target position is shown while holding and committed once on key-up, clamped to
    [0, duration − 1 s].
  - **Back:** unchanged from P1. It leaves the player to the previous destination. Hiding the
    controls first would be a separate functional decision for the owner.
- **Queue** opens as a right side panel of 400 dp on glass.
- **Lyrics** has a full-screen mode, where the existing `FullscreenLyricsContent` uses the TV type
  scale.

### P2-C: consistency

- **Bottom sheets** become right side panels on TV through one shared `AdaptiveModalSheet`.
- **Dialogs** become centered glass panels through one shared `AppAlertDialog`.
- **Album, playlist and artist pages** use the Android TV "Content Details" template. The header
  has the artwork (240 dp), the title, metadata and a row of `Control` buttons (Play, Shuffle,
  Save, More), with the track list below using `Row` focus.
- **Library** uses a pill tab row at the top.
- **Search** shows the system IME, then results in TV card rows.
- **Settings** is a single column, at most 720 dp wide and starting at the safe area, with rows in
  the `Row` style.

### P2-D: production finish

- **Screen on.** The screen stays on only while video plays. Audio lets the system screensaver
  start; the player dims its chrome to 40% after 30 s without input.
- **System card.** Selecting the system Now Playing card opens the player.
- **Accessibility.** Every icon-only control has a content description, and the layout holds at
  font scale 1.3.
- **Performance budgets** are measured with `tools/bench.py` and `tools/trace.py` against the P1
  baseline:
  - cold start ≤ 3.0 s;
  - median D-pad key-to-frame ≤ 100 ms on Home;
  - janky frames ≤ 5% while scrolling a row;
  - PSS ≤ P1 + 15%.
- **Strings.** New strings exist in English, Vietnamese and Brazilian Portuguese.
