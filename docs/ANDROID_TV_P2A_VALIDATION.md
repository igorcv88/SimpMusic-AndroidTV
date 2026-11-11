# Android TV P2-A validation in progress

P2-A is not complete. The working branch starts at `976b3b13355a159d2d2b719fc3951adbe5baadb0` on top of `claude/tv-p1b-remote-gaps`.

On the Philips 2021/22 Android TV (Android 11, API 30, armeabi-v7a), the signed release installed over the existing package without removing its data. The smoke test passed entry, launch, process, MediaSession and crash checks. With a card shelf first, UP from a focused card reached Now Playing, and OK opened the fullscreen player. Back returned to Home with focus on the rail, rather than on the chip that opened the player; focus restoration remains unresolved.

`App.kt` removes the rail in fullscreen and recreates it after Back. The rail's initial-focus effect requests Home on every recreation, which can reclaim focus on return. The content focus restorer also spans destinations, so removing that request alone has not been shown to restore the original focused element. Validate Home, Library, Search and externally opened player flows before considering a fix complete.

A signed debug experiment restricted the rail's initial Home request to the first successful request in an app session. On the Philips TV, Home's Now Playing chip opened the player, but Back still returned focus to Home in the rail. The experiment was removed because it did not restore the origin. Search also requests its text field and shows the keyboard on composition when its query is empty; restoration must coordinate that request as well. The original signed release was reinstalled without removing app data. The debug smoke test timed out on launch, while process, MediaSession, entry and crash checks passed; the Home screen appeared later.

Quick Picks was first in a previous Home response and a card shelf was first in a later response. The first-shelf Quick Picks focus change and navigation between grid rows still need physical validation in both arrangements. The profile gained a visible focus indication on the first RIGHT from the rail, confirmed by screenshot and the accessibility focus tree. Screenshots and recordings contain personal information and remain local.

The system animator scale observer and the cancellation of an in-flight focus animation when reduced motion becomes active compile, but a recording did not establish whether the scale changed during a running animation. The original scale was restored. Repeat this test with timestamped frames.

The benchmark's input log had no injected-key timestamps. Its old fallback used the earlier graphics reset time; on this TV, first frames in an instrumented run arrived about 400 ms later, beyond the 150 ms acceptance limit. A foreground dialog also interfered with one run. Six samples were still lost after dismissing it. The benchmark now discards samples when the actual key timestamp or measurement bounds are missing or invalid; no reliable input timestamp source has yet been established for this TV. Do not use the earlier latency or jank values. The SHA of the previously installed baseline APK has not been established.

Remaining work includes the Home components and visual treatments in the P2-A plan, focus and Back regression paths, a trustworthy benchmark timestamp, trace, complete screenshots and owner approval of the ambient background comparison. P2-B and P2-C are outside this work.
