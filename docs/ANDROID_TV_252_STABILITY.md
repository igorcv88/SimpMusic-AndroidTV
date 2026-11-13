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

These are TV-only presentation changes. The supplied screenshots and videos were inspected locally and are not included in Git. Their device appearance after these changes has not been verified.

## Material limits and physical acceptance

Eligible TV Home surfaces can reuse the existing preblurred ambient-artwork bitmap in screen coordinates. Settings and other destinations without an eligible scene use a neutral drawn material. This change adds no general live backdrop capture or blur for arbitrary content on API 30.

Physical acceptance remains outstanding for rapid lazy-list reuse, profile focus transitions, actual Android IME opening/Back/OK behavior, Search-to-Home Back transitions, crash-screen restart, playback notification re-entry, and the final Settings/rail appearance. No TV connection, installation, restart, data clearing, or new device capture was performed for this change.

## References

- [Compose modifier node attachment](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.Node)
- [State-based editor keyboard options](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/KeyboardOptions)
- [CustomActivityOnCrash restart discovery](https://github.com/ereza/CustomActivityOnCrash#customization)

## Validation performed

Using Android Studio's JBR on Windows, the following command passed with `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`:

```powershell
./gradlew.bat :composeApp:jvmTest --tests '*com.maxrave.simpmusic.ui.tv.*' :tvAndroidApp:compileDebugKotlin :androidApp:compileDebugKotlin --console=plain
```

It ran 56 TV JVM tests across 15 suites with no failures or skips, and compiled both Android app entry points plus the shared Android code. The Compose editor test uses an offscreen Skiko scene and passed in the forced headless JVM. Nine focused Python manifest/release-version tests passed. `git diff --check` passed.

A broader JVM run encountered the existing Windows failure `NetworkFirstInterceptorTest.localFilesAreNotSentThroughTheNetworkFirstPath`: Coil returned `ErrorResult` for the local-file fixture where the test expects `SuccessResult`. The unrelated image-cache code/test was not changed or excluded. These checks do not include signed-release packaging, installation, or physical-device acceptance.
