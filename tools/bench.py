#!/usr/bin/env python3
"""Measure SimpMusic's key latency, jank, start times and idle cost on a TV.

The app under test is --package (default com.maxrave.simpmusic.dev, the debug build), started
through its own LEANBACK_LAUNCHER entry - the one the TV's home screen uses. The script presses a
fixed sequence of D-pad keys inside the app, one at a time, and reads the frame timestamps that
`dumpsys gfxinfo <pkg> framestats` reports:

  * response  = the first frame that FINISHED after the key was injected
  * movement  = the end of the continuous run of frames that started with it
  * jank      = janky frames / total frames, from the same dump

It also measures the cold start (process force-stopped, `am start -W`), the warm start (the app
brought back in front of another app while its process is alive), the idle CPU of the whole device
and of the app, and the app's own PSS. The cold start force-stops the app, so playback stops;
nothing else on the TV is changed.

The TV is reached through tools/tv.py, which speaks ADB over TCP with the adb_shell library, so no
adb binary is needed (set TV_ADB_BINARY to use one; see tools/native-adb.md).

Usage:
    TV_HOST=192.168.1.50 python tools/bench.py --keys 36 --series 3
    TV_HOST=192.168.1.50 python tools/bench.py --package com.maxrave.simpmusic --out-dir release-results
    TV_HOST=192.168.1.50 python tools/bench.py --app com.android.tv.settings/.MainSettings

--app names the app the cold and warm starts leave SimpMusic for. Without it, the TV's Settings,
else the first leanback app that is not SimpMusic.

The output is a summary plus the raw per-key numbers, so a run can be compared with another one or
re-analysed later.

Exit status: 0 the run is measured and written; 2 an --out-dir that cannot be written into, judged
before the TV is opened, a folder that cannot be created, a TV that cannot be reached (tools/tv.py's
sentence says which failure it was), a package with no TV entry on the TV, and a run that never got
as far as the TV - no --host and no TV_HOST, a --port (or TV_PORT) tools/tv.py cannot read, a
checkout tools/tv.py cannot be loaded from - nothing was measured, and nothing was written;
1 anything else, including a result.json that could not be written after the run, whose numbers are then printed.

Why the frame is matched by its completion time and not by its intended vsync: on a screen that
moves the focus in a single frame (no animation), the frame that reacts to a key is born from the
vsync BEFORE the key and only finishes after it. Matching on the intended vsync drops most of those
keys and leaves a sample too small to say anything.
"""
import argparse
import atexit
import importlib.util
import json
import os
from pathlib import Path
import re
import statistics
import sys
import time

DEFAULT_PACKAGE = "com.maxrave.simpmusic.dev"
PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")

# The sequence the key probe uses: moves inside a row and between rows, including the edges.
DEFAULT_SEQUENCE = (22, 22, 22, 21, 21, 21, 20, 22, 22, 21, 21, 19)

# Android key codes: 19 is UP, 20 is DOWN, 21 is LEFT, 22 is RIGHT.
KEYCODE_NAMES = {19: "KEYCODE_DPAD_UP", 20: "KEYCODE_DPAD_DOWN", 21: "KEYCODE_DPAD_LEFT",
                 22: "KEYCODE_DPAD_RIGHT", 66: "KEYCODE_ENTER", 3: "KEYCODE_HOME",
                 4: "KEYCODE_BACK"}

# An activity as package/activity, e.g. org.videolan.vlc/.StartActivity (the same pattern as tools/trace.py).
COMPONENT = re.compile(r"^[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+$")


class TV:
    """The TV, reached with tools/tv.py's client over one ADB connection.

    tools/tv.py is the client this project already ships: it speaks ADB over TCP with the
    adb_shell library, so no adb binary is needed. That matters on macOS, where the platform
    tools are not installed by default and the system blocks the ones that are.
    """

    def __init__(self, host, port=5555, binary=None):
        self.host = host
        self.port = port
        self.serial = f"{host}:{port}"
        self.binary = binary
        self.client = None
        self._other_app = None

    def _connect(self):
        """tools/tv.py's ADB connection, opened on first use and kept for the whole run.

        Starting tools/tv.py per command paid a Python start and a fresh ADB handshake each time,
        several per key; through a relay a full run took over 45 minutes.
        """
        if self.client is None:
            here = str(Path(__file__).resolve().parent)
            if here not in sys.path:
                sys.path.insert(0, here)
            # tv.py reads its target from the environment when imported, including CLI overrides here.
            os.environ["TV_HOST"], os.environ["TV_PORT"] = str(self.host), str(self.port)
            try:
                import tv as tv_client  # noqa: E402
            except ModuleNotFoundError as e:
                # A checkout a reader has not built yet: no adb_shell, and no tools/venv beside this
                # file for main() to start again under. Nothing has been measured, so this is the 2 the
                # docstring gives a run that never reached the TV - it used to be a SystemExit carrying
                # this text, which the interpreter answers with 1, the status of a run whose numbers
                # exist and whose result.json could not be written.
                print(f"bench.py needs {e.name}: python3 -m venv tools/venv && "
                      "tools/venv/bin/pip install -r tools/requirements.txt. Nothing was measured, "
                      "and nothing was written.", file=sys.stderr)
                raise SystemExit(2) from e
            except SystemExit as exit_request:
                # tv.py refuses two shapes while it is imported, before it has a connection: a TV_PORT
                # it cannot read ("not a port number: 'abc'", which is also what `--host <ip>:70000`
                # and a TV_PORT of abc get) and, had main() not already stopped there, no TV_HOST.
                # bench.py hands the client its own --host/--port just above, so the second is main()'s
                # line and the first is a port typed wrong. Nothing was asked of the TV either time,
                # and the interpreter answers such a text with 1 - the status a run that measured and
                # could not write its result.json keeps - so a run that never started read as one that
                # did. tv.py's own text is printed here; a module that exits 0 answered the call
                # itself, and that status stands (the branch tools/theme_restart.py keeps).
                if not exit_request.code:
                    raise
                if isinstance(exit_request.code, str):
                    print(exit_request.code, file=sys.stderr)
                print("Nothing was measured, and nothing was written.", file=sys.stderr)
                raise SystemExit(2) from exit_request
            # Read back the target tv.py made of that environment: it splits `host:port` and refuses a
            # port outside 1..65535 there. Overriding these from the arguments (as this did) put the
            # `:port` back into the host, so `--host 192.0.2.20:5877` asked for a machine of that name.
            self.host, self.port = tv_client.HOST, tv_client.PORT
            self.serial = f"{self.host}:{self.port}"
            try:
                self.client = tv_client.connect()
            except Exception as error:
                # tools/tv.py words the failures that mean the TV was not reached: a port that refuses,
                # a Wireless debugging port that asks for TLS, an address nothing answers on, and the
                # macOS Local Network denial that looks like one. The errors it has nothing to add to (a
                # key file it cannot read, a connection reset in the handshake) keep their traceback.
                # Left alone, a refused port here ended the run in a ConnectionRefusedError traceback
                # with exit 1, naming neither the address nor what the run lost, and exit 1 is also what
                # a finished run whose result.json could not be written returns - so a TV that was
                # asleep read as a benchmark that ran. This is the one function both the first connect
                # and the reopen after a dropped connection go through; a run that stops here has
                # written nothing, because result.json comes at the end.
                why = tv_client.connect_error(error)
                if why is None:
                    raise
                print(f"{why}\nNothing was written: result.json is written at the end of a run.",
                      file=sys.stderr)
                raise SystemExit(2) from error
        return self.client

    def shell(self, command):
        """Run one shell command on the TV and return its output; bytes that aren't UTF-8 become U+FFFD.

        A dropped connection (the TV went to standby for a moment) is reopened once.
        """
        for attempt in (1, 2):
            try:
                out = self._connect().shell(command, read_timeout_s=120.0, decode=False)
                return out.decode("utf-8", "replace") if isinstance(out, bytes) else (out or "")
            except SystemExit:
                raise
            except Exception:
                self.close()
                if attempt == 2:
                    raise

    def close(self):
        if self.client is not None:
            try:
                self.client.close()
            except Exception:
                pass
            self.client = None

    def shell_ok(self, command):
        return self.shell(command).strip() == ""

    def app_component(self, package):
        """The package's TV entry, e.g. com.maxrave.simpmusic.dev/com.maxrave.simpmusic.MainActivity.

        Read from the LEANBACK_LAUNCHER activities - the list the TV's home screen draws its apps
        from - so a build without the TV entry is None rather than a phone activity the home
        screen could never open.
        """
        out = self.shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                         "-c android.intent.category.LEANBACK_LAUNCHER")
        for line in out.splitlines():
            line = line.strip()
            if line.startswith(package + "/") and COMPONENT.match(line):
                return line
        return None

    def focused_window(self):
        """The window that has the focus, e.g. "u0 com.maxrave.simpmusic.dev/...MainActivity".

        The dump has several "mCurrentFocus"/"mFocusedApp" lines; only the one that names a
        window counts, and a popup (a menu left open) is a window of its own.
        """
        out = self.shell("dumpsys window")
        for line in out.splitlines():
            match = re.search(r"mCurrentFocus=Window\{[^}]*\s(\S+?)\}", line)
            if match:
                return match.group(1)
        return None

    def is_in_front(self, package):
        """Whether the app is the focused window.

        An app that is not in front renders nothing, so every key would measure zero frames and
        the run would be meaningless. A popup from another package (the recents panel) counts as
        "not in front" too, because the app underneath is not drawing.
        """
        focused = self.focused_window() or ""
        return focused.startswith(f"{package}/")

    def package_of(self, component):
        return component.split("/", 1)[0]

    def version_of(self, package):
        out = self.shell(f"dumpsys package {package}")
        match = re.search(r"versionName=(\S+)", out)
        return match.group(1) if match else "?"

    def dumpsys(self, what):
        return self.shell(f"dumpsys {what}")


# Keys to read the input log for before deciding this TV doesn't log injected keys.
KEY_LOG_PROBES = 3


def venv_python():
    """tools/venv's interpreter, which has adb_shell, or None."""
    here = Path(__file__).resolve().parent
    for candidate in (here / "venv" / "bin" / "python", here / "venv" / "Scripts" / "python.exe"):
        if candidate.exists():
            return str(candidate)
    return None


def key_event_time(tv, keycode, binary=None):
    """The uptime (ms) at which the key went down, read from the input log.

    The log line is matched by the key's own name: the buffer holds the keys injected before it,
    and the first line of the buffer is not necessarily the key of this measurement.
    """
    # Some TVs never log injected keys (a TCL C755 on Google TV 14 doesn't), and reading the log costs
    # seconds there: its main buffer holds about 100 MB. After KEY_LOG_PROBES keys without a line, stop
    # reading it; measure_key then times the key from just before it was sent, as it always did when
    # the line was missing. Only the newest lines are read, filtered on the TV.
    state = vars(tv).setdefault("_key_log", {"seen": False, "misses": 0})
    if not state["seen"] and state["misses"] >= KEY_LOG_PROBES:
        return None
    out = tv.shell("logcat -d -b main -t 2000 | grep injectKeyEvent")
    name = KEYCODE_NAMES.get(keycode, f"keyCode={keycode}")
    for line in reversed(out.splitlines()):
        if "injectKeyEvent" in line and "action=ACTION_DOWN" in line and name in line:
            match = re.search(r"eventTime=(\d+)", line)
            if match:
                state["seen"] = True
                return int(match.group(1))
    state["misses"] += 1
    return None

def android_uptime(tv, package=None, binary=None):
    """The Android uptime in ms, the clock the frame timestamps use.

    `dumpsys gfxinfo` prints it as "Uptime: <ms> Realtime: <ms>". /proc/uptime is NOT the same
    clock: it counts the time the device spent suspended, so it runs hours ahead of the frames.
    """
    what = f"dumpsys gfxinfo {package}" if package else "dumpsys gfxinfo"
    out = tv.shell(what)
    match = re.search(r"^Uptime:\s*(\d+)", out, re.M)
    return int(match.group(1)) if match else None


def measure_key(tv, keycode, binary=None, package=DEFAULT_PACKAGE):
    """Inject one key and return (response_ms, movement_ms, frames, janky) or None."""
    # The reset zeroes the gfxinfo counters. Without it, the previous key's frame would still be
    # in the window and the "first frame after the key" would be the previous key's.
    reset_out = tv.shell(f"dumpsys gfxinfo {package} reset")
    t_before = None
    match = re.search(r"^Uptime:\s*(\d+)", reset_out, re.M)
    if match:
        t_before = int(match.group(1))
    tv.shell(f"input keyevent {keycode}")
    time.sleep(1.0)
    stats = tv.shell(f"dumpsys gfxinfo {package} framestats")
    t_after = None
    match = re.search(r"^Uptime:\s*(\d+)", stats, re.M)
    if match:
        t_after = int(match.group(1))
    t0 = key_event_time(tv, keycode, binary)
    # The log line is only trusted when it falls inside the window between the reset (before the
    # key) and the dump (after it). A line left over from an earlier key is outside the window
    # and would give a latency of seconds.
    if t0 is None or (t_before is not None and t0 < t_before) \
            or (t_after is not None and t0 > t_after):
        t0 = t_before
    if t0 is None:
        return None
    rows = frame_rows(stats)
    threshold = t0 * 1_000_000
    first = last = prev = None
    for flags, intended, completed in rows:
        if flags != 0 or completed < threshold:
            continue
        if first is None:
            first, last, prev = completed, completed, intended
            continue
        if (intended - prev) / 1e6 > 50:      # a gap: the movement is over
            break
        last, prev = completed, intended
    if first is None:
        return None
    # A key that moves nothing (the end of a row, the focus on an edge) leaves the next
    # frame, caused by something else, to be measured as its answer. An Android TV answers in
    # 16 to 33 ms, so a first frame that only finishes more than 150 ms after the key is not the
    # answer to it: the key had no visual reaction and is left out of the numbers.
    if (first - threshold) / 1e6 > 150:
        return None
    frames = janky = 0
    for line in stats.splitlines():
        match = re.match(r"Total frames rendered:\s*(\d+)", line)
        if match:
            frames = int(match.group(1))
        match = re.match(r"Janky frames:\s*(\d+)", line)
        if match:
            janky = int(match.group(1))
    return ((first - threshold) / 1e6, (last - threshold) / 1e6, frames, janky)


def clear_log(tv, binary=None):
    tv.shell("logcat -c -b main")


def frame_rows(framestats):
    """The PROFILEDATA rows as (flags, intended_vsync_ns, completed_ns)."""
    rows = []
    inside = False
    columns = None
    for line in framestats.splitlines():
        if "---PROFILEDATA---" in line:
            inside = not inside
            columns = None
            continue
        if not inside:
            continue
        parts = [part.strip() for part in line.split(",")]
        if columns is None:
            # Android versions add fields; the header names stay the same.
            try:
                columns = [parts.index(name) for name in ("Flags", "IntendedVsync", "FrameCompleted")]
            except ValueError:
                pass
            continue
        try:
            rows.append(tuple(int(parts[index]) for index in columns))
        except (ValueError, IndexError):
            continue
    return rows


def other_app(tv, package=DEFAULT_PACKAGE):
    """An app to send the TV to, so SimpMusic leaves the front: --app when given, else the TV's
    Settings, else the first leanback app that isn't SimpMusic. tools/trace.py picks its own the same way.

    Settings is found by the action it answers, not by a hardcoded activity: the activity name differs
    between manufacturers (com.android.tv.settings/.MainSettings on Google TV, others elsewhere).
    Never the chooser: a plain LAUNCHER lookup resolves to android's ResolverActivity, a dialog, on a
    TCL C755 and a HOTACK L018 projector, and the run then measured against that dialog.
    """
    if tv._other_app:
        return tv._other_app
    candidates = tv.shell("cmd package resolve-activity --brief -a android.settings.SETTINGS").splitlines()
    candidates += tv.shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                           "-c android.intent.category.LEANBACK_LAUNCHER").splitlines()
    for line in candidates:
        line = line.strip()
        if COMPONENT.match(line) and not line.startswith(("android/", package + "/")) \
                and "Resolver" not in line:
            tv._other_app = line
            return line
    return None


def accessibility_services(tv, package):
    """The accessibility services enabled for a package, as the setting holds them."""
    out = tv.shell("settings get secure enabled_accessibility_services")
    return [part.strip() for part in out.split(":") if part.strip().startswith(package + "/")]


def _norm_component(component):
    """`pkg/pkg.Classe` and `pkg/.Classe` are the same component; normalise so it is not added twice."""
    if "/" not in component:
        return component
    package, cls = component.split("/", 1)
    if cls.startswith(package + "."):
        return package + "/" + cls[len(package):]
    return component


def restore_accessibility(tv, package, services):
    """Put back the accessibility services a force-stop took down.

    `am force-stop` kills the process the service lives in, and on some TVs it also **removes that
    package's services** from `enabled_accessibility_services` — they do not come back when the
    app starts again. Reported on a TCL 55C715 (#503) and reproduced on a TCL BeyondTV2
    (Android TV 9): the setting went from one service to empty after a single stop.

    The list is a colon-separated set shared by every app, so this **appends** what is missing
    instead of writing each service on its own: `settings put secure enabled_accessibility_services
    <one service>` replaces the whole list, which would wipe the app's other services and any
    other app's.
    """
    if not services:
        return
    current = tv.shell("settings get secure enabled_accessibility_services").strip()
    if current == "null":
        current = ""
    merged = [part for part in current.split(":") if part]
    known = {_norm_component(part) for part in merged}
    added = False
    for service in services:
        if _norm_component(service) not in known:
            known.add(_norm_component(service))
            merged.append(service)
            added = True
    if added:
        tv.shell("settings put secure enabled_accessibility_services " + ":".join(merged))
        tv.shell("settings put secure accessibility_enabled 1")


def start_command(component):
    """`am start -W` through the TV entry, the intent the TV's home screen sends."""
    return ("am start -W -a android.intent.action.MAIN "
            f"-c android.intent.category.LEANBACK_LAUNCHER -n {component}")


def wait_time(out):
    """The WaitTime `am start -W` printed, in ms, or None."""
    match = re.search(r"WaitTime:\s*(\d+)", out)
    return int(match.group(1)) if match else None


def cold_start(tv, component, repeats):
    """The `am start -W` WaitTime of the app from a stopped process, in ms.

    The accessibility services are put back **after** the measurement, not before. Writing that
    setting makes the system bind the service, which starts the app's process — the one thing a
    cold start must not have. Restoring first turned every "cold" start into a warm one.
    """
    values = []
    package = tv.package_of(component)
    other = other_app(tv, package)
    if not other:
        print("Could not find another app to switch to; the cold start is not measured.")
        return values
    # Read once, before the first stop: the force-stop clears the setting on some TVs, so after the
    # first run there would be nothing left to read.
    services = accessibility_services(tv, package)
    try:
        for _ in range(repeats):
            # Reuse Settings even if its task was first opened with ACTION_SETTINGS.
            # A component-only start can otherwise stack another activity on every run.
            tv.shell(f"am start -W --activity-reorder-to-front -n {other}")
            time.sleep(2)
            tv.shell(f"am force-stop {package}")
            time.sleep(3)
            value = wait_time(tv.shell(start_command(component)))
            if value is not None:
                values.append(value)
            time.sleep(3)
    finally:
        restore_accessibility(tv, package, services)
    return values


def warm_start(tv, component, repeats):
    """The `am start -W` WaitTime of the app brought back in front of another app, in ms.

    The process stays alive between the runs, so this is the start a viewer gets when they come
    back to SimpMusic from the home screen while it plays in the background.
    """
    values = []
    package = tv.package_of(component)
    other = other_app(tv, package)
    if not other:
        print("Could not find another app to switch to; the warm start is not measured.")
        return values
    tv.shell(start_command(component))     # the process has to exist before the first run
    time.sleep(3)
    for _ in range(repeats):
        tv.shell(f"am start -W --activity-reorder-to-front -n {other}")
        time.sleep(3)
        value = wait_time(tv.shell(start_command(component)))
        if value is not None:
            values.append(value)
        time.sleep(2)
    return values


def app_pid(tv, package):
    """The pid of the app's process, or None."""
    out = tv.shell(f"pidof {package}").strip()
    if not out:
        return None
    return out.split()[0]


def idle_cpu(tv, package, seconds):
    """The CPU of the whole device and of the app, over `seconds`, from /proc/stat."""
    def sample():
        return tv.shell("head -1 /proc/stat; cat /proc/[0-9]*/stat 2>/dev/null").splitlines()

    def parse(lines):
        for line in lines:
            if line.startswith("cpu "):
                fields = [int(x) for x in line.split()[1:]]
                return (sum(fields), fields[3] + fields[4])
        return None

    def process_ticks(lines, pid):
        """utime + stime of one pid, in clock ticks."""
        prefix = f"{pid} ("
        for line in lines:
            if line.startswith(prefix):
                parts = line[line.index(")") + 2:].split()
                if len(parts) > 12:
                    return int(parts[11]) + int(parts[12])
        return None

    # The ticks are in USER_HZ (100 on Android). The percentage is of ONE core, like `top` shows.
    ticks_per_second = 100
    before = sample()
    pid = app_pid(tv, package)
    before_ticks = process_ticks(before, pid) if pid else None
    before_cpu = parse(before)
    time.sleep(seconds)
    after = sample()
    after_cpu = parse(after)
    after_ticks = process_ticks(after, pid) if pid else None
    if not before_cpu or not after_cpu:
        return None, None
    total = after_cpu[0] - before_cpu[0]
    idle = after_cpu[1] - before_cpu[1]
    device = 0.0 if total == 0 else 100 * (1 - idle / total)
    app = None
    if before_ticks is not None and after_ticks is not None:
        app = 100 * (after_ticks - before_ticks) / (ticks_per_second * seconds)
    return device, app


def pss_of(tv, package):
    out = tv.shell(f"dumpsys meminfo {package}")
    match = re.search(r"^\s*TOTAL(?: PSS)?:\s+(\d+)\b", out, re.M)
    return int(match.group(1)) // 1024 if match else None


def stats(values):
    if not values:
        return {"p50": None, "p95": None, "p99": None, "max": None, "n": 0}
    ordered = sorted(values)
    def pick(fraction):
        index = min(len(ordered) - 1, int(len(ordered) * fraction + 0.999) - 1)
        return ordered[max(0, index)]
    return {"p50": int(statistics.median(ordered)), "p95": pick(0.95),
            "p99": pick(0.99), "max": ordered[-1], "n": len(ordered)}


def out_dir(value):
    """`--out-dir` as typed, or an argparse error before the TV is opened.

    The folder may not exist yet: bench.py creates it. What it cannot do is write into a file, run
    through a file, or nowhere - os.makedirs() answered those with a traceback after the TV had
    already been asked for the app's TV entry and what version it runs, at the start of a run
    that then measured nothing. The same rule as tools/trace.py's --out (tools/README.md#performance), and the same reason for judging it in argparse: it costs nothing and it happens first.
    """
    if not value:
        raise argparse.ArgumentTypeError("needs a folder to write the results into")
    if os.path.exists(value) and not os.path.isdir(value):
        raise argparse.ArgumentTypeError(f"{value} is a file, not a folder to write the results into")
    # makedirs() creates the whole chain, so the nearest folder that is already there decides whether
    # it can: a file anywhere above the last part is NotADirectoryError whatever follows it.
    existing = os.path.dirname(os.path.abspath(value))
    while existing and not os.path.exists(existing):
        parent = os.path.dirname(existing)
        if parent == existing:
            break
        existing = parent
    if os.path.exists(existing) and not os.path.isdir(existing):
        raise argparse.ArgumentTypeError(
            f"{value} runs through {existing}, which is a file, not a folder")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=os.environ.get("TV_HOST"),
                        help="TV address, IP or IP:port (or set TV_HOST)")
    # Left as typed: tools/tv.py parses TV_HOST/TV_PORT when TV._connect imports it, refuses a port that
    # is not 1..65535 there, and splits the port out of the address. int() here ran before any of that,
    # so TV_PORT=abc (or an empty TV_PORT, which tools/tv.py reads as 5555) was a ValueError traceback.
    parser.add_argument("--port", default=os.environ.get("TV_PORT") or "5555",
                        help="the TV's ADB port (or set TV_PORT); default 5555")
    parser.add_argument("--keys", type=int, default=36, help="keys per series")
    parser.add_argument("--series", type=int, default=3, help="how many series")
    parser.add_argument("--cold", type=int, default=5, help="cold starts")
    parser.add_argument("--warm", type=int, default=10, help="warm starts")
    parser.add_argument("--idle", type=int, default=60, help="idle seconds")
    parser.add_argument("--out-dir", type=out_dir, default="bench-results",
                        metavar="DIR", help="where to write the results (default bench-results)")
    parser.add_argument("--app", metavar="COMPONENT",
                        help="the app to leave SimpMusic for, as package/activity (default: found on the TV)")
    parser.add_argument("--package", default=DEFAULT_PACKAGE,
                        help=f"the app's package (default {DEFAULT_PACKAGE}, the debug build)")
    args = parser.parse_args()
    if not PACKAGE.match(args.package):
        parser.error(f"not a package name: {args.package!r}")
    if args.app and not COMPONENT.match(args.app):
        parser.error(f"--app needs package/activity, not {args.app!r}")
    # Each of these five is a count of what the run measures, and zero of one measures nothing there
    # while the run still exits 0: --series 0 and --keys 0 wrote a result.json whose key numbers are
    # all null, and --cold 0 / --warm 0 the same for their fields, which in that file cannot be
    # told apart from a TV that does not report them. --idle is worse than silent: idle_cpu() divides
    # by the seconds, so 0 was a ZeroDivisionError and a negative one a ValueError out of time.sleep,
    # both after the keys, the cold starts, the warm starts and the 20 s settle - a full run's numbers
    # gone, with the traceback and exit 1 that a run which wrote nothing returns. Judged here, before
    # the host is looked at and before the venv re-exec, the way --out-dir and tools/trace.py's --runs
    # are; no run loses a capability, since a TV that cannot do a cold start already reports null.
    for option, unit in (("keys", "keys per series"), ("series", "series"),
                         ("cold", "cold starts"), ("warm", "warm starts")):
        if getattr(args, option) < 1:
            parser.error(f"--{option} is the number of {unit}: pass at least 1, not {getattr(args, option)}")
    if args.idle < 1:
        parser.error(f"--idle is the number of idle seconds: pass at least 1, not {args.idle}")
    if not args.host:
        # Nothing has been measured: a run with no address is the 2 the docstring gives a run that never
        # got as far as the TV, not the 1 a run keeps when its numbers exist and result.json could not be
        # written. The line names both ways to pass the address, the two this run was given.
        print("bench.py needs the TV's address: pass --host <ip> (or set TV_HOST). Nothing was "
              "measured, and nothing was written.", file=sys.stderr)
        raise SystemExit(2)
    venv = venv_python()
    if importlib.util.find_spec("adb_shell") is None and venv \
            and os.path.realpath(venv) != os.path.realpath(sys.executable):
        # Started with a Python that lacks adb_shell: run again with the project's venv, which has it.
        os.execv(venv, [venv, os.path.abspath(__file__), *sys.argv[1:]])

    tv = TV(args.host, args.port)
    atexit.register(tv.close)
    tv._other_app = args.app
    package = args.package
    component = tv.app_component(package)
    if not component:
        print(f"{package} has no TV entry (LEANBACK_LAUNCHER) on this TV: install a SimpMusic TV build "
              "first (tools/tv_install.py). Nothing was measured, and nothing was written.", file=sys.stderr)
        return 2
    version = tv.version_of(package)
    try:
        os.makedirs(args.out_dir, exist_ok=True)
    except OSError as e:
        # --out-dir itself was judged in argparse; a folder that exists but cannot be created into
        # (a read-only checkout, a full disk) is only knowable here. Say it before a run of several
        # minutes measures keys for a folder that will not take the result; the TV was only read.
        print(f"cannot create {args.out_dir}: {e.strerror or e}. Nothing was measured, and nothing "
              "on the TV was changed.", file=sys.stderr)
        return 2

    print(f"App: {component} ({version})")
    print(f"Keys: {args.series} x {args.keys}; writing to {args.out_dir}")

    samples = []
    for series in range(1, args.series + 1):
        print(f"Series {series}/{args.series}")
        tv.shell(start_command(component))
        time.sleep(3)
        if not tv.is_in_front(package):
            print("SimpMusic is not the focused window; the keys would measure nothing.")
            print(f"Focused: {tv.focused_window()}")
            return 1
        for index in range(args.keys):
            keycode = DEFAULT_SEQUENCE[index % len(DEFAULT_SEQUENCE)]
            measured = measure_key(tv, keycode, package=package)
            if measured is None:
                samples.append({"series": series, "key": keycode, "measured": False})
                continue
            response, movement, frames, janky = measured
            samples.append({"series": series, "key": keycode, "measured": True,
                            "response_ms": round(response, 1),
                            "movement_ms": round(movement, 1),
                            "frames": frames, "janky": janky})

    measured = [s for s in samples if s.get("measured")]
    responses = [s["response_ms"] for s in measured]
    movements = [s["movement_ms"] for s in measured]
    frames = sum(s.get("frames", 0) for s in measured)
    janky = sum(s.get("janky", 0) for s in measured)

    print("Cold starts")
    colds = cold_start(tv, component, args.cold)
    print("Warm starts")
    warms = warm_start(tv, component, args.warm)
    print(f"Idle {args.idle}s")
    tv.shell(start_command(component))
    time.sleep(20)
    device_cpu, app_cpu = idle_cpu(tv, package, args.idle)
    pss = pss_of(tv, package)

    result = {
        "app": component, "version": version, "package": package,
        "host": args.host,
        "environment": {
            "android_release": tv.shell("getprop ro.build.version.release").strip(),
            "sdk": tv.shell("getprop ro.build.version.sdk").strip(),
            "model": tv.shell("getprop ro.product.model").strip(),
            "fingerprint": tv.shell("getprop ro.build.fingerprint").strip(),
        },
        "keys": {"series": args.series, "per_series": args.keys,
                 "measured": len(measured), "lost": len(samples) - len(measured)},
        "response_ms": stats(responses),
        "movement_ms": stats(movements),
        "jank_pct": round(100 * janky / frames, 2) if frames else None,
        "frames": frames, "janky_frames": janky,
        "cold_start_ms": stats(colds),
        "warm_start_ms": stats(warms),
        "idle_cpu_pct": round(device_cpu, 1) if device_cpu is not None else None,
        "idle_app_cpu_pct": round(app_cpu, 1) if app_cpu is not None else None,
        "pss_mb": pss,
        "samples": samples,
    }
    path = os.path.join(args.out_dir, "result.json")
    summary = {k: v for k, v in result.items() if k != "samples"}
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2)
    except OSError as e:
        # The folder was writable when the run started - it can be removed, or fill up, during the
        # minutes it measures. The numbers were measured on the TV and cannot be measured again the
        # same way, so they are printed: this is the only copy, and exit 1 says the file is not it.
        print(json.dumps(summary, indent=2))
        print(f"cannot write {path}: {e.strerror or e}. Nothing was written: the numbers above are "
              "this run's only copy.", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2))
    print(f"Written: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
