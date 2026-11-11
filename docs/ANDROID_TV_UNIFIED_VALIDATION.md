# Unified Android TV experience: what to check on the TV

Branch `codex/tv-unified-experience`. Nothing in this list has been checked on the Philips TV yet;
every item is a behaviour the code is written to produce and that only the device can confirm.

Device: Philips UHD Android TV, Android 11 (API 30), `armeabi-v7a`, ADB `192.168.1.64:5555`.
Always pass `-s 192.168.1.64:5555`. Do not uninstall or clear data: the app holds real data.

## Navigation

1. Cold start. Home loads and focus lands on the first content row, not on the rail and not on
   the profile. If Home fails to load, the first D-pad press must still give focus to something.
2. Home, profile avatar, LEFT: the rail opens with Home highlighted.
3. Any tab's content, LEFT at the left edge: the rail opens. RIGHT returns to the element that
   had focus before.
4. BACK from a tab's content: the rail opens with that tab highlighted.
5. BACK with the rail focused: Library, Mix, Search and Analytics return to Home; on Home the app
   closes. (Changed in this round: before, BACK in the rail bounced back to the content and the
   app could only be left with the Home button.)
6. Listen Again: opens with the first card focused; every card reachable; OK opens it; LEFT
   reaches the rail; BACK returns to the Home card that opened it.
7. Library: DOWN from the chips reaches Favorite, Followed, Most Played and the playlists; open
   one and press BACK: focus returns to the same card.
8. Search: opens with the field focused and no keyboard. OK opens the keyboard. BACK closes only
   the keyboard; a second BACK opens the rail and does not reopen the keyboard. DOWN with the
   keyboard closed reaches the suggestions and results; OK opens them.
9. Playlist and album: open with Play focused; one DOWN reaches the first track; UP from the
   first track returns to Play.

## Playback

10. Play on a playlist, a track row, a Home card and a Search result each open the fullscreen
    player.
11. Start playback from the phone (cast or media session) or let a queue resume on app start: the
    fullscreen player must not open by itself.
12. Player, BACK: with the controls showing they hide first; the next BACK returns to the screen
    and the element that started playback. Check from Home, Playlist, Library, Search and Listen
    Again. Audio keeps playing.
13. Video: controls show on any key, are reachable with the D-pad and hide again.

## Look

14. No back arrows anywhere on TV (Search, playlist, album, artist, settings, player).
15. No refresh indicator when moving through Mix, Home, Library or Search.
16. Focus is a translucent squircle with a thin light rim on cards, rows, chips, buttons and the
    rail. Only the profile avatar is round.
17. Focused cards in Listen Again, Home rows and Mix do not cover their neighbours or titles.
18. Home background: a smooth blur of the focused artwork, no visible blocks, no second copy of
    the artwork at the top right.
19. Rail open: translucent panel, readable labels, no white pill.
20. Now Playing chip with a long title stays inside its width and clear of the other header
    controls.
21. Mood chips move left and right without lag; the selected chip and the focused chip look
    different.
22. Quick Picks in both layouts: rows are not cramped, long titles are cut cleanly.

## Reduced motion

Read the current value first, test with 0, then put the original value back:

```
adb -s 192.168.1.64:5555 shell settings get global animator_duration_scale
adb -s 192.168.1.64:5555 shell settings put global animator_duration_scale 0
adb -s 192.168.1.64:5555 shell settings put global animator_duration_scale <original>
```

With 0: focus changes apply in the same frame, the rail opens without sliding, the ambient
picture does not drift, and focus is still clearly visible. The app observes the setting, so no
restart is needed.

## Known limits

- The ambient blur is a CPU box blur (three passes, radius 18) on a 256 x 256 decode, run by Coil
  off the main thread and cached per artwork. It does not use `RenderEffect`, so API 30 and newer
  versions take the same path. Its cost on the Philips has not been measured.
- Benchmark samples without an event timestamp are discarded. No latency or jank figure exists
  for this branch.
- `NetworkFirstInterceptorTest.localFilesAreNotSentThroughTheNetworkFirstPath` fails on Windows:
  it builds a `file:/C:/...` URI. The test and the code it covers are untouched by this branch.
