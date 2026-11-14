# Android TV 2.5.2 stability

The TV player addition is documented separately in [Official song / music-video switching](ANDROID_TV_RENDITION_SWITCH.md), including the exact-link policy, transactional source preparation and hardware acceptance limits.

## Diagnosis and changes

The supplied crash screenshot identifies build 2.5.2 on Android 11/API 30. Its complete R8 map ID matches the retained mapping: `3ad18d5c62d1d9e717d10d667b0c6f968fe1eecb43752fe29c4f52b808950bd3`. The relevant chain resolves to Compose ancestor traversal called by `TvGlowAnchorNode.onPlaced`. The anchor performed that traversal before checking attachment. A separate draw-node check happened too late to protect it.

`TvGlowAnchorNode` now rejects detached placement before traversal. Related focus and coordinate callbacks reject detached state before accessing owners or coordinates, and focus animation/state resets on detach. The regression invokes the actual detached modifier callback and reproduced the original `visitAncestors called on an unattached node` exception before correction. This establishes the failing code path; it does not establish physical-device acceptance of all scrolling, lazy reuse, or navigation sequences.

The secondary crash-handler failure used the phone `MainActivity` class, which R8 renamed even though the TV manifest retained an alias with the original name. TV now declares CustomActivityOnCrash's `RESTART` action on the real `TvActivity`, and shared crash configuration allows the library to discover that activity. Mobile uses its existing launcher fallback. Inspection of the project's cached CustomActivityOnCrash 2.4.0 confirms this discovery order. Media-session re-entry also uses the running activity's class, which selects `TvActivity` for TV and `MainActivity` for mobile. No blanket keep rules were added.

## Search and navigation

The TV Search field remains a state-based `OutlinedTextField`. It now enables its text-input session on actual editor focus. Previously, `showKeyboardOnFocus = false` disabled that session and a separate keyboard-controller request could not create it. Its existing TV key modifier supplies no additional focus target. OK can request the keyboard again while editor focus remains; IME Search submits the retained text, and vertical directional navigation hides the keyboard and leaves the editor.

A JVM Compose test exercises the production editor through a real intercepted platform-text-input session, text entry, submission, an OK request after a simulated keyboard hide, and directional exit. The session-on-focus test timed out before the correction. Simulated keyboard hiding is not a physical Android Back test.

When Back changes the active destination while the rail owns focus, the resumed destination now receives focus once its content is ready. Same-entry lifecycle/layout refreshes retain rail focus. Existing explicit selected-entry redirection and destination-scoped restoration remain in place.

## TV presentation

The TV profile uses its existing circular focus shape without a second fill over the focus material. Focus material uses a lighter fill, sheen, and rim. Settings removes TV row dividers and stops clipping the first focused row's top edge; mobile retains its existing dividers and clipping. The rail removes its selection dot and persistent selected tile fill, keeps selected icon/text emphasis, and insets its smaller brand mark with the label aligned to the entries. A section with a More destination exposes that destination once through More; a distinct channel destination can still be opened from its title.

These are TV-only presentation changes. Supplied and newly collected private screenshots and videos remain outside Git. The subsequent API 30 supplement and physical correction work are described below.

## Material limits and physical acceptance

The initial stability commits reused Home's preblurred ambient scene and did not supply arbitrary live backdrop blur. The subsequent [API 30 backdrop supplement](ANDROID_TV_API30_BACKDROP.md) replaces that limitation with an isolated software producer on eligible TV content. It excludes rail/focus materials, performs bounded worker blur and samples the current route. Fullscreen playback disables that producer; it cannot capture a separate video SurfaceView.

The owner subsequently authorized iterative installation and physical testing. A signed debug APK at d790f54e was installed on the Philips Android 11/API 30 without uninstalling or clearing app data. Private captures show source-correlated rail blur in Home, Search and Settings. The native Search keyboard opened, accepted input and hid with Back; the following Back opened the Search rail. Settings LEFT entered its rail. Existing remote playlists loaded.

That physical candidate still showed a Home artwork rim around the full card text slot, square native indication outside the circular avatar and Settings corners, and an unsuccessful avatar LEFT handoff. These findings triggered a further scoped correction; the earlier renderer tests alone did not establish their device appearance. Final geometry acceptance (see the correction wave below), rapid lazy reuse, crash-screen restart, playback notification re-entry and physical official-rendition switching remain pending. No data clearing or GitHub Release publication was performed.

The final geometry correction wave is implemented in the commit that follows b3c50beb. Home artwork focus rims are bound to the artwork (the outer Card mark owns the anchor, so auto-backing marks on clickables no longer take it), the native indication is removed on TV cards, the avatar is clipped to a circle, and Settings rows take their corners from the actual group body, including rows nested in inner columns. Popup and Dialog windows keep their focus and panel material in their own root rather than registering with the main-window backdrop. LEFT on the Home avatar requests the rail directly and falls back to normal focus handling if the request is rejected.

Validation of that wave: the 86 TV JVM tests across 22 suites passed with no failures or skips, together with TV and mobile debug Kotlin compilation. Three of those tests initially asserted through simulated arrow keys and a Dialog scrim, neither of which behaves as on a device in the JVM harness (a simulated arrow press does not trigger default focus movement); they now call the same focus entry points the production code uses (`restoreContent()` for the rail's exit toward content, `FocusManager.moveFocus` for the fallback) and sample pixels the foreign window does not cover. Rail RIGHT through real D-pad input, the Escape/Back dismissal of the avatar menu and the on-screen result of the final wave were not exercised by these tests, and the final wave has not been installed on the Philips; those remain physical acceptance items.

## References

- [Compose modifier node attachment](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.Node)
- [State-based editor keyboard options](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/KeyboardOptions)
- [CustomActivityOnCrash restart discovery](https://github.com/ereza/CustomActivityOnCrash#customization)

## Validation performed

The initial stability commits passed the following command using Android Studio's JBR on Windows with `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`:

```powershell
./gradlew.bat :composeApp:jvmTest --tests '*com.maxrave.simpmusic.ui.tv.*' :tvAndroidApp:compileDebugKotlin :androidApp:compileDebugKotlin --console=plain
```

It ran 56 TV JVM tests across 15 suites with no failures or skips, and compiled both Android app entry points plus the shared Android code. The Compose editor test uses an offscreen Skiko scene and passed in the forced headless JVM. Nine focused Python manifest/release-version tests passed. `git diff --check` passed.

A broader JVM run encountered the existing Windows failure `NetworkFirstInterceptorTest.localFilesAreNotSentThroughTheNetworkFirstPath`: Coil returned `ErrorResult` for the local-file fixture where the test expects `SuccessResult`. The unrelated image-cache code/test was not changed or excluded.

The API 30 supplement at d790f54e passed 69 focused TV JVM tests and shared JVM/Android plus TV/mobile debug Kotlin compilation. The player addition at b3c50beb, with core35c7a114, passed 38 domain, 3 exact-counterpart, 15 Media3 and 70 TV JVM tests, plus TV/mobile debug Kotlin compilation. Independent static reviews approved those implementations without findings. A full local data run also exposed an unchanged BrowserCookie synthetic ordering failure; focused counterpart tests pass. These checks do not exercise the real ExoPlayer candidate transaction or prove physical switching in either direction. Final correction/build/device results must be reported against their exact later commit.
