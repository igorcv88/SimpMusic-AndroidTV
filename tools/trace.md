# tools/trace.py and tools/trace_report.py

Record Perfetto traces of SimpMusic on the TV, then report where its time goes. For headline numbers
only (key latency, jank, cold and warm start, PSS), use `tools/bench.py` instead: it needs no extra install. To draw the
result for a pull request, use `tools/flamechart.py` ([guide](flamechart.md)).

## Setup

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt
```

Recording needs only `tools/requirements.txt` and a TV on Android 9 or later. The report's first run
downloads Perfetto's query engine (about 13 MB, SHA-256 checked) to `~/.local/share/perfetto`. To avoid
that, download `trace_processor_shell` yourself and pass `--engine PATH`. The path is read before any
trace is opened, so it has to be a file with its execute bit set: an empty one, a typo, a folder or a
file you forgot to `chmod +x` is answered with exit 2 and the path named, rather than a traceback out of
Perfetto that names neither. `tools/flamechart.py` takes the same option and answers the same way.

On Android 9 and 10, the TV's tracing services must be enabled first; they are enabled by default only
from Android 11. See Perfetto's [tracing-service setup](https://perfetto.dev/docs/quickstart/android-tracing#starting-the-tracing-services)
for the `persist.traced.enable` prerequisite on non-Pixel devices. This tool does not change that setting.
It sends a binary trace config, which Android 9 accepts without the `--txt` option added in Android 10.

## Record

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/trace.py cold --runs 5 --out traces/before
```

`--out` is the folder the traces are written into, and it is created if it is not there. A file where the
folder goes, a path through a file and an empty `--out` are refused before the TV is asked anything.

A TV that cannot be reached stops the run with `tools/tv.py`'s sentence - a port that refuses, a Wireless
debugging port that asks for TLS, an address nothing answers on, or, before the TV is asked anything at
all, no `TV_HOST` and a `TV_PORT` that is not a port number - and exit 2, before the folder is made.
Nothing was recorded, so nothing is half-written: `tools/bench.py` and
`tools/b64push.py` answer a TV like that the same way.

| Scenario | Recorded | Use for |
|---|---|---|
| `cold` | `am start -W` after a force-stop, another app in front | Start-up work (`onCreate`, DI, the first screen) |
| `warm` | `am start -W` with SimpMusic running behind another app | Returning to the app from the TV's home screen |
| `nav` | SimpMusic in front, then 24 D-pad moves 0.35 s apart | Focus, scrolling, drawing |
| `custom` | `--step CMD` (repeatable) after `--setup CMD` | Anything else; `sleep N` steps set the length |

- **Output:** each run writes `<scenario>-<n>.pftrace` (10–20 MB) and a `.json` with the commands and the
  TV's model and build. `traces/` is gitignored.
- **The other app:** `--app package/activity`, or by default the TV's Settings. Both this tool and
  `bench.py` bring an existing instance to the front across runs, rather than stacking new Settings
  activities. They do not clear the other app's data or force-stop it. Check that it is responsive
  after a long session; an already accumulated history is not removed by this flag.
- **The package:** `--package` names the app, `com.maxrave.simpmusic.dev` (the debug build) unless given;
  the release build is `com.maxrave.simpmusic`. It decides which process the recording follows and which
  one the report reads, so another build has to pass it to `trace.py`, `trace_report.py` and
  `flamechart.py` alike: with the wrong name the report prints `–` for every step instead of saying the
  package was not in the trace, and the chart stops at `no <package> process in the trace`.

## Report

Keep `nav` traces separate from start traces (`cold`, `warm`, `custom`), since their frame
counts use different time windows. Each input set and both sides of `--vs` must use the same family.
For a folder containing both, pass matching files such as `traces/nav-*.pftrace` instead of the folder.

```bash
tools/venv/bin/python tools/trace_report.py traces/before --vs traces/after --markdown
```

| Scenario | Numbers |
|---|---|
| Starts (`cold`, `warm`, `custom`) | Start to first frame drawn; the steps inside it (`bindApplication`, `onCreate`, `onStart`, `onResume`, first layout and draw); then the frames drawn and missed deadlines in the next 1.5 s |
| `nav` | Frame time (median, 90th percentile); RenderThread draw; GPU wait; missed deadlines |

With `--vs`, a third table shows the change in each median. A run over twice the median is starred.

Slices still open when the recording ends have no measured duration. The report leaves those durations
out and counts only completed draws. If the first RenderThread draw is unfinished, the first-frame time
is unavailable too; completed lifecycle steps are still reported.

Frame times and missed deadlines need [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline),
available on Android 12 and later. Without its data for the app's process, these metrics are omitted
(or shown as `–` beside measured runs). `nav` still counts RenderThread draws and reports their duration
and GPU waits where available; a draw's duration is not the full frame time.

## Rules

- **Repeat runs.** Record 5 runs per build, on the same TV with the same build type and settings.
  Compare medians.
- **Keep starred runs.** They're usually memory pressure on a 1 GB TV. Report them; don't drop them.
- **Starts use the TV entry.** `bench.py` and the `cold`/`warm` traces send `MAIN` with the
  `LEANBACK_LAUNCHER` category, as the TV's home screen does. Compare runs recorded with the same launch
  command.
- **Check the remote afterwards.** A force-stop switches off the accessibility helpers. `trace.py` puts the
  list back after the last run, but on some TCL sets a helper still won't bind (`tools/README.md#troubleshooting`).
- **Look in the right place for GPU savings.** Less overdraw shows as GPU wait in `nav`, not as main-thread
  time.
- **Expect no latency gain from off-main-thread work.** It removes work, but doesn't shorten the time to
  the first frame.
- **Never commit the `.pftrace` files.** They hold the TV's process list.

The whole before/after method for a pull request is in [`tools/README.md#performance`](../tools/README.md#performance).
