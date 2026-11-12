# Android TV audit fixes - 12 November 2026

The supplied local F01-F12 audit was implemented against `951bf076`. Its private screenshots/videos are not repository assets. Their observations establish the original symptoms, not visual acceptance of the new APK. No TV connection, ADB, installation, data clearing or new device capture was used.

## Shared contracts

- Layout containers must not become page-wide focus surfaces because their titles are actionable. The Home title/avatar and More/Play all are separate targets; non-actionable titles have no focus target.
- Artwork retains its 1.025 scale and artwork anchor. A 4 dp glow plus scale growth fits the 10 dp artwork gutter; shelves reserve 12 dp vertical padding. Rows stay at scale 1 and have inset slots. Controls retain 1.015 scale, preserving avatar and Mood shapes.
- Focus glass uses a neutral fill, 1 dp specular rim and subdued eligible scene (16% focus, 18% panel, saturation 0.45). Theme Color belongs to semantic indicators/progress. API 30 reuses the existing off-main blurred bitmap; no runtime backdrop refraction is promised.
- Only visible Home may sample Home artwork. Other routes use neutral material. New artwork requests clear the old material and reject late outgoing callbacks. Route exit retains the Home scene for restoration.
- Rail selection hands focus to the active ready destination entry. Incidental layout cannot steal rail focus. Explicit screen entry controls take precedence over group fallback. RIGHT restores content; BACK from root content opens the rail; BACK in the rail follows the existing back stack.
- Fullscreen TV uses a footer and one hidden key target. Wake-up D-pad/OK consumes press and release. Visible D-pad navigates; timeline LEFT/RIGHT seeks 5-second steps. Controls time out after 5 seconds. BACK first hides, then pops the origin without a playback command.

## Findings

| ID | Cause and change | Files and verification | Remaining limit |
|---|---|---|---|
| F01 | The whole header carried a click target. TV uses a compact actionable title/avatar distinct from More/Play all, with long-title ellipsis. The mobile branch remains intact. | `AdapterItems.kt`; optional channel/endpoint/avatar branches inspected; compilation. | Owner checks long-title rendering and actual target bounds. |
| F02 | Scale and external halo exceeded reserved space. Reduced halo, added vertical shelf padding and Quick Pick/chip edge insets, removed row scale. | `TvFocus.kt`, `TvTokens.kt`, `AdapterItems.kt`, `HomeScreen.kt`; geometry tests failed on old halo/row scale. | First/middle/last cards, grid positions and reduced-motion rendering need device acceptance. |
| F03 | Global artwork and amplified saturation were applied across routes. Added destination eligibility/request ownership and subdued scene contribution. Home's retained scene is not deleted on route exit. | `App.kt`, `TvSceneGlass.kt`, `TvAmbientBackground.kt`; bitmap lifecycle, late-request and luminance/contrast tests. | No physical color calibration or runtime blur claim. |
| F04 | Selection retained rail focus and items nearly filled its panel. Added active/readiness handoff, item inset and current-route selection. Settings must not be restored as a saved Home tab. | `TvNavigationRail.kt`, `TvNavigationState.kt`, `TvRailRoutes.kt`; handoff, no incidental theft, retry, entry precedence and real navigation tests. | Owner checks collapse and D-pad transitions. |
| F05 | Avatar requester/LEFT properties belonged to an outer layout box. They now belong to the clickable avatar; its size/shape is preserved. | `HomeScreen.kt`; avatar focus-history restoration test. | Actual avatar LEFT to rail and RIGHT restoration need device acceptance. |
| F06 | Material Switch had an independent target with generic backing. TV has one inset toggleable row with Switch semantics and a passive switch. ON uses semantic color; OFF/focus are neutral. Mobile retains its switch target. | `SettingItem.kt`; callback ownership/passive-switch source inspection and compilation. | OK once, UP/DOWN and clipping need manual acceptance. |
| F07 | Empty-search marketing interrupted the grid. Excluded on TV; retained spacing below the input and neutral route material. | `SearchScreen.kt`; empty-state builder branches inspected and compiled. | IME and rendered layout need manual acceptance. |
| F08 | Search/More lacked indicators and Heart was smaller. Portrait/landscape groups now use individual circle indicators and 48 dp TV Heart. | `PlaylistScreen.kt`, `ModalBottomSheet.kt`; explicit Play entry wins over group fallback in tests. | Per-button D-pad/OK needs device acceptance. |
| F09 | Scaled page-wide rows and stale tint exaggerated focus. Tracks now use real insets, unscaled small shape and neutral material outside Home. | `FullWidthItems.kt`, shared focus/material; geometry and material tests. | No frame-time/jank measurement was performed. |
| F10 | Touch layout exposed five central buttons/two invisible seek halves. TV now has a dedicated footer with transport, timeline, queue, subtitles and More; mobile selects its existing touch player. | `FullscreenPlayer.kt`, `TvFullscreenPlayer.kt`, `TvPlayerRemote.kt`; wake/hide/leave policy tests. | Hardware key routing, timing and visible focus need owner review. |
| F11 | Historical Home return lacks route/key telemetry. TV BACK explicitly pops the origin; focus restoration rejects inactive entries. No network-error cause is asserted. | `TvFullscreenPlayer.kt`, `TvNavigationState.kt`; real navigation restores the same entry ID for Home/Playlist/Search/Library; inactive Home focus rejected. | Historical physical failure was not reproduced. |
| F12 | Main/precache streaming and Canvas used writable cache; PiP hooks lacked TV/capability gates. TV disables streaming/Canvas writes while preserving download reads and explicit download writes. Canvas limit is 32 MiB; thumbnail limit 64 MiB. Mobile retains its existing configuration. PiP requires request, non-TV, API at least 26 and capability. | Core `PlaybackPlatformPolicy.kt`, `TelevisionDevice.kt`, `Media3ServiceModule.kt`, `DownloadUtils.kt`, `MediaPlayerView.kt`; root `SimpMusicApplication.kt`. Four policy tests and media unit suites pass; Android compilation. | No before/after storage measurement or historical PiP stack trace. Existing streaming cache remains read-only/manual clear; downloads, DB and login are not deleted. |

## Storage and navigation evidence

The Cached collection reads fully cached IDs through `CacheRepositoryImpl.getFullyCachedSongIds()`. Existing cached tracks remain readable; new TV streams no longer become disk-cached songs. Explicit downloads remain user-managed without automatic eviction. Coil thumbnails are a separate bounded cache. Prior app-data totals alone cannot prove pathological streaming growth. External media-session playback does not emit the UI `TvPlaybackRequests` used to open fullscreen; only UI playback commands do.

The PiP gate follows Android's [capability check](https://developer.android.com/develop/ui/views/picture-in-picture). Read-only factories follow the [Media3 sample](https://github.com/androidx/media/blob/release/docsamples/src/main/java/androidx/media3/docsamples/exoplayer/DownloadingMedia.kt). Android runtime paths live in `core`; the parent pins its scoped commit through the project-owner fork.

## Validation

JVM and Android main compilation completed. The complete Compose JVM suite ran 117 tests: 116 passed and the unchanged `NetworkFirstInterceptorTest.localFilesAreNotSentThroughTheNetworkFirstPath` failed on Windows (expected Coil SuccessResult, got ErrorResult). The controller confirmed the same failure in a prior untouched build log. Neither assertion nor interceptor was changed. Shared policy tests (4), Android media3 tests (15) and media3-ui tests (1) passed. The final focused TV suite passed 50/50 tests, including the actual Settings/root saved-state and player-origin navigation tests.

The controller ran the existing Python tools suite in its existing venv: 468 tests, 8 failures and 24 skips, involving Windows abbreviated loopback resolution, POSIX path expectations/hints and escaped PermissionError paths. The 33 focused signing/install/properties tests passed using mocks; they did not install an APK.

## Owner acceptance after the signed APK

Check first/last Quick Picks and Listen Again; All and approved Mood/avatar; actionable section title versus More; avatar LEFT and RIGHT; rail Settings/Home selection and collapse; switch OK once; Search without marketing; Playlist Play first, then Heart/Search/More; inset tracks; footer transport/timeline/queue/subtitles; BACK hides then returns to the triggering playlist item; semantic custom theme colors; rapid artwork changes and animator scale 0.

Signed APK build, independent review and PR are controller responsibilities. Compilation and unit tests do not establish physical visual acceptance.
