# Unified Android TV implementation

User handoff of 2026-11-11 is the acceptance specification. One consolidated local proposal; no push, PR or merge.

Preserve base 0d3a8e1a and local TvMotion, Home Quick Picks, TvFocus reduced-motion and validation edits.

## Design
Navigation owns rail focus and remembers each back-stack entry separately. Destination lifecycle activation arms restoration; laid-out focus targets restore their saveable identity once. Rail creation never requests Home. Direct UI playback emits a separate event; MediaSession state does not trigger navigation. Details provide Play and first-row entry targets. Search explicitly activates IME.

Shared material uses restrained squircle geometry and translucent illumination, circular profile, small interruptible scale. Artwork is blurred off-main and cached, with no duplicate header art. Mobile behavior stays conditional.

## Execution and evidence
- Destination reachability: Search, Browse/Listen Again, Library.
- Detail/player entry, controls, BACK and visual arrows.
- Shared focus/material, Home, Quick Picks, ambient processing.
- Shell integration, playback event, destination restoration, global refresh/back audit.
- Integrated Android compile, TV JVM tests, tooling benchmark tests, signed ABI-compatible APK, safe physical checks and consolidated report.

Physical acceptance requires owner inspection. Invalid benchmark timestamps never become latency evidence. Screenshots and signing configuration remain local.
