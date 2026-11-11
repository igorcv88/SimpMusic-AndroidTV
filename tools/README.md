# Android TV tools

Scripts for building, installing, checking and measuring SimpMusic on an Android TV over network ADB.
Most of them come from the CleanYourTV toolkit (MIT, see [LICENSE-CleanYourTV](LICENSE-CleanYourTV)) and
were adapted from a launcher to an app: they start SimpMusic through its `LEANBACK_LAUNCHER` entry, the
same intent the TV's home screen sends, and default to the debug package `com.maxrave.simpmusic.dev`.
Issue numbers such as `#105` in their comments refer to the upstream CleanYourTV repository, not to this one.

They need Python 3.9 or later. The TV tools also need the packages in `requirements.txt`:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
# Windows:
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

`trace_report.py`, `flamechart.py` and `screendiff.py` also need `requirements-perf.txt`. Every tool reads
the TV's address from `TV_HOST` (an IP, or IP:port) and `TV_PORT` (default 5555).

## Tools

| Tool | What it does |
|------|--------------|
| [`tools/tv_install.py`](tv_install.py) | Builds the debug (or `--release`) APK signed with the TV key, checks the certificate with apksigner, and installs the split for the TV's ABI. Exits 3, changing nothing, when the TV holds a build signed with another key. |
| [`tools/tv_smoke.py`](tv_smoke.py) | Starts the app through its TV entry and checks the entry, the start, the process, the MediaSession and the log for crashes; saves the log, the media_session dump and a screenshot. |
| [`tools/tv.py`](tv.py) | Minimal ADB-over-TCP client: `--probe`, one shell command, `--screenshot`, `--push`/`--pull`, `--batch`, `--reboot`. |
| [`tools/adb-closed.py`](adb-closed.py) | After debugging is switched off, exits 0 when the TV refuses the connection, 1 when the port is still open, and 2 when nothing answered or the check itself could not run. Standard library only. |
| [`tools/relay.py`](relay.py) | Forwards a port on this computer to the TV's ADB port, for an assistant whose sandbox cannot reach the TV. [Guide](relay.md). |
| [`tools/b64push.py`](b64push.py) | Fallback transfer: sends a file as base64 chunks through plain shell commands and checks it with md5sum before it replaces the target. |
| [`tools/bench.py`](bench.py) | Headline numbers: key latency, jank, cold and warm start, idle CPU and PSS, into `result.json`. |
| [`tools/trace.py`](trace.py) | Perfetto traces around a cold start, a warm start, D-pad navigation or your own steps. [Guide](trace.md). |
| [`tools/trace_report.py`](trace_report.py) | Start and navigation numbers from those traces, per run, median and range; `--vs` compares two sets. |
| [`tools/flamechart.py`](flamechart.py) | Draws one trace's main and render threads as a flame chart PNG. [Guide](flamechart.md). |
| [`tools/screendiff.py`](screendiff.py) | Compares a before and an after screenshot pixel by pixel (with `--mask` and `--blur` regions) and draws the pair for a pull request. [Guide](screendiff.md). |
| [`tools/release_properties.py`](release_properties.py) | Writes Gradle's signing properties file from the environment, escaped for `java.util.Properties` (CI). |
| [`tools/tv_signing.py`](tv_signing.py) | Reads `secrets.env`, writes the same file for a local build, and checks an APK's certificate. |

Run the tests with `python -m unittest discover -s tools/tests`.

## Connecting

1. On the TV, turn on Developer options (Settings → Device Preferences → About → press OK on *Build* seven
   times), then USB debugging, and Network debugging if the TV lists it.
2. Find the TV's IP under Settings → Network.
3. `TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py --probe`. The first time, the TV asks
   "Allow USB debugging?": tick *Always allow from this computer* and accept. The key `tv.py` makes is kept in
   `tools/adbkey/` (gitignored). If the TV already trusts your platform-tools key (`~/.android/adbkey`),
   that key is offered too and no prompt appears.

To use Google's adb and a transport it already holds (including encrypted Wireless debugging), set
`TV_ADB_BINARY` to the adb executable: see [native-adb.md](native-adb.md).

## Troubleshooting

- **"refused the connection"**: nothing listens for ADB on that port. Network debugging is off, or the TV
  only offers Wireless debugging.
- **Wireless debugging only (Android 11+)**: its port asks for TLS, which `tv.py` cannot speak. Pair once with
  the stock adb (`adb pair`, then `adb connect` to the port the Wireless debugging screen shows), run
  `adb tcpip 5555`, and use port 5555. It usually closes at the next reboot; `adb connect` and
  `adb tcpip 5555` open it again with no new pairing. Some TVs use another fixed port (5877 on the Wanbo
  T6R Max): give it as `TV_PORT` or `192.168.1.50:5877`.
- **The TV answers from a terminal but not from an assistant**: the assistant's sandbox cannot reach your
  network. Run [`tools/relay.py`](relay.md) in an ordinary terminal and point the tools at it.
- **macOS: "No route to host"** (`EHOSTUNREACH`) to a TV that is on: Local Network privacy. The permission
  belongs to the terminal app that runs the tool, not to python or adb; grant it under System Settings →
  Privacy & Security → Local Network and restart the terminal. `tools/tv.py` and the stock adb fail the
  same way. So does the closing check: `tools/adb-closed.py` prints
  `could not check <ip>:5555 ([Errno 65] No route to host)` and ends with exit 2, which proves nothing about the
  port, so only a "closed" from a granted terminal counts.
- **No "wireless debugging" toggle**: older TVs only have Network debugging, or ADB over the network is
  opened by the stock adb over USB with `adb tcpip 5555`.
- **Screenshots come back empty (0 bytes)**: Live TV, HDMI inputs and some streaming apps are protected
  and capture as nothing. Check what is on screen before the ADB connection. A few MediaTek sets write a
  damaged PNG when the screenshot goes anywhere but `/data/local/tmp`, which is where `tv.py` writes it.
- **A tool stops with "lost the connection"**: the TV dropped ADB, usually because it went to standby or
  rebooted. The command may or may not have run; check before repeating anything that changes the TV.
- **Perfetto on some TCL sets**: after a force-stop an accessibility helper may not bind again. `trace.py`
  restores the enabled list after the last run; turn the service off and on again if it still is not bound.

## Signing

Every build installed on the TV is signed with one key, so each build installs over the last one and the
app keeps its data and sign-in. `androidApp/build.gradle.kts` takes it as an optional Gradle property:

```bash
./gradlew :androidApp:assembleDebug -PtvSigning=<properties file>
```

The file holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without the property the build
signs as before (the debug key, or unsigned release). Locally, `tools/tv_install.py` writes that file from
a gitignored `secrets.env` at the repository root, passes it to Gradle and deletes it afterwards:

```
KEY_PATH=/path/to/keystore.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

In CI, the *Android TV APK* workflow (`.github/workflows/tv-apk.yml`) reads the GitHub secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Both paths check the result with
`apksigner` against the certificate digest in `tools/tv_signing.py`, and refuse any other certificate.

Install the **debug** build on the TV. A release build compares its signing certificate with upstream's
published keys and shows the "unofficial build" screen when it is signed with any other key; debug builds
skip that check. A build signed with another key (the Android debug key, say) cannot update one signed
with the TV key: `tv_install.py` stops with exit 3 and changes nothing. `--uninstall-first` removes the
app first, and its data with it.

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv_install.py
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv_smoke.py
```

`tv_smoke.py` saves its evidence under `smoke/`, which is gitignored: screenshots and logs can show account
names.

## Performance

Measure before and after a change on the same TV, with the same build type and nothing else running:

```bash
tools/venv/bin/python -I tools/bench.py --out-dir work/bench-before        # on the old build
tools/venv/bin/python -I tools/trace.py cold --runs 5 --out traces/before
# install the new build (tools/tv_install.py), then:
tools/venv/bin/python -I tools/bench.py --out-dir work/bench-after
tools/venv/bin/python -I tools/trace.py cold --runs 5 --out traces/after
tools/venv/bin/python -I tools/trace_report.py traces/before --vs traces/after --markdown
```

The debug build is not minified, so its start times are slower than a release build's; compare debug with
debug. `bench.py` exits 2 without measuring when the package has no TV entry on the TV. Post the numbers
from both runs, not one, and say which TV and which build they came from. `work/`, `traces/` and
`bench-results/` are gitignored.
