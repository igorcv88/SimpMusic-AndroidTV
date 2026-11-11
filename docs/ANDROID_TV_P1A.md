# Android TV adaptation of the existing SimpMusic app (P1A)

## Entry and identity

`tvAndroidApp` reuses androidApp's Kotlin sources and resources, following vivoAndroidApp's
source-set pattern. Its isolated package remains `com.maxrave.simpmusic.tv` in debug and
release. Only TvActivity has LEANBACK_LAUNCHER. MainActivity is a non-exported alias for
existing internal PendingIntents and crash restart. Public phone deep links are not yet
advertised in the TV manifest.

TvActivity subclasses MainActivity and overrides only checkBuildIdentity(). All initialization,
SharedViewModel handling, service binding, intents, permissions and App(viewModel) rendering
remain in MainActivity. The base class becomes open to allow this reuse without copying its
initialization; its default identity method retains the exact upstream phone check. TV checks
its package and single current signing certificate against the fork's public TV SHA-256.
Rejection displays the reason, expected/found package and found SHA-256 and emits Logger.w.

SimpMusicApplication and its logging behavior are unchanged. There is no TvApplication logging
override. The complete existing UI, navigation graph, all player styles and existing login are
used; there are no parallel TvShell or TvLogin screens. Core, AutoMix, crossfade, EQ, queues and
Cast remain unchanged.

The installer and signed workflow target tvAndroidApp and include armeabi-v7a. The phone
package is preserved. A build-tool-only root override supplies the protobuf runtime needed by
lint: the scraper's global protobuf exclusion otherwise crashes lint with missing
ProtocolMessageEnum. The override affects androidLintTool only and does not change APK dependencies
or the core submodule.

## Remote control on the existing screens

There are no TV-only screens. The shared `composeApp` UI detects a TV once, at the `App()` root
(`rememberTvDevice()`: `FEATURE_LEANBACK` or `UI_MODE_TYPE_TELEVISION`), and exposes it as
`LocalTvDevice`. Every helper in `ui/tv/TvInput.kt` returns the modifier unchanged when that is
false, so phone and desktop keep their behavior; Desktop (`jvmMain`) and iOS answer `false`.

- `tvFocusIndicator()` marks focus the way TV launchers do: the element scales to 1.06 and gets
  a soft white backing (16% alpha) in its own shape, with no outline, and layout does not move.
  The backing follows the element's likely outline (a circle on square elements, a capsule on
  pills, a 16dp corner otherwise) because most call sites sit behind a `clip(...)`.
- `tvClickable`/`tvCombinedClickable` are Foundation's `clickable`/`combinedClickable` with that
  ring. Files import them under the original names (`import ...tvClickable as clickable`), so
  call sites stay untouched and the upstream diff is the import line.
- Material buttons (`Button`, `TextButton`, `IconButton`, radio buttons) take
  `modifier = Modifier.tvFocusIndicator()`, since they do not go through `clickable`.
- `tvRemoteActions` gives pointer-only surfaces (`pointerInput`/`detectTapGestures`, the mini
  player swipe) OK, long-press OK and left/right actions; the touch gestures stay attached.
- `tvSliderKeys` moves seek, volume, speed and equalizer controls by a fixed step on the arrows
  along the slider's axis. The equalizer bands need OK to start editing, so up/down still
  leaves the curve; OK or Back ends editing.
- `focusable(true)` on marquee titles and card children became `focusable(!LocalTvDevice.current)`,
  so the D-pad stops once per card instead of twice.
- `tvFocusRestorer()` on lists and carousels returns focus to the last item after Back;
  `tvInitialFocus()` puts focus on the player's dismiss/play control when it opens.
- The login WebView is focusable on TV and requests focus, so the TV keyboard reaches Google's
  fields. The login screen itself is the existing `LogInScreen`.

The tablet layout's Now Playing side panel (35% of the width) and floating mini player do not
work from a couch: the panel leaves the video small, and the capsule was only reachable by going
down the rail. On a TV, `App.kt` therefore routes them to existing pieces instead:

- Opening Now Playing navigates to the existing `FullscreenPlayer` (`FullscreenDestination`), the
  same screen phones use for fullscreen video. Audio tracks show the artwork with the synced
  lyric line, videos play full screen. On TV its controls show on entry with focus on
  play/pause, any OK/up/down brings them back (the waking key is swallowed so it presses
  nothing), and they hide 5 s after the last key; left/right on the bare picture seek 5 s. A
  queue button opens the existing `QueueBottomSheet`. Back leaves the player and playback goes on;
  the nav graph does not reopen the Now Playing sheet afterwards on TV.
- The floating mini player is hidden. `TvNowPlayingChip` sits in Home's top bar between the
  profile and the chips, Spotify TV style: artwork, then "Now Playing" over the track name. OK
  opens the player, holding OK plays or pauses. Other tabs have no mini player yet (P1B).

Not adapted in this PR: Library, playlists, album and artist pages beyond the shared list and
card components they reuse, Listen Together, Wrapped, and settings sub-screens other than the
equalizer, audio effects and the dialogs in `SettingScreen`. They are reachable, but some
controls there may lack the focus ring or a remote equivalent for a gesture (P1B).

## Package proposal

A future ID such as `io.github.igorcv88.simpmusic.tv` would identify the fork more clearly.
This PR proposes that ID but does not rename the installed package without user confirmation.
Renaming would require a separate data/login migration decision.

## Local installation and evidence

Use the existing ignored secrets.env and the tools/README.md setup. On Windows:

```powershell
$env:TV_HOST = "192.168.1.64"
tools/venv/Scripts/python.exe tools/tv_install.py
tools/venv/Scripts/python.exe tools/tv_smoke.py --package com.maxrave.simpmusic.tv
tools/venv/Scripts/python.exe tools/trace.py cold --runs 1 --package com.maxrave.simpmusic.tv --out traces/p1a
```

Use --release for the minified variant, signed with the same TV key. Never use
--uninstall-first without explicit user authorization. A debug-key APK is usable for build
checks but intentionally fails TV identity validation.

The smoke test saves complete logcat in smoke/. Logs, screenshots and traces are gitignored
and never committed. Before attaching evidence, redact cookies, tokens, email addresses,
account names and Wi-Fi details. Secrets remain outside code and version control.

Merge order: PR #1 -> PR #2 -> P1A. Do not merge ahead of the dependencies.

## Physical account experiment (Philips API 30)

A signed debug prototype was installed beside the existing .dev app. The Android account
chooser was operated with D-pad only. The Google authenticator showed an Allow/Deny
consent screen naming the requested weblogin token type. After Allow, it returned a token.
The next prototype accepted the returned HTTPS Google URL and loaded it in WebView.
Logcat outcome categories were TOKEN_URL_ACCEPTED followed by COOKIE_NOT_YET_PRESENT
(twice); the terminal page said "Sign in" and "to continue to YouTube", with an "Email or
phone" field. No authenticated music.youtube.com SAPISID cookie was produced. This proves
token return, not automatic YouTube Music sign-in. Consequently there is no production
"account from this TV" button. The consent and chooser screenshots contain account data
and remain private local evidence, outside Git.

The minimal probe used AccountManager.newChooseAccountIntent restricted to com.google,
then getAuthToken with `weblogin:service=youtube&continue=https://music.youtube.com/`, the
foreground Activity overload and a callback that inspected the result only in memory.
Only fixed outcome categories were emitted. The temporary probe is not shipped.

After this experiment, the user completed the WebView sign-in manually. SAPISID presence
was confirmed in the isolated TV app's private preferences without printing its value.
This validates the existing cookie login fallback, not automatic device-account login.
