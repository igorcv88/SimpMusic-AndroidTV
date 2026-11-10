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
