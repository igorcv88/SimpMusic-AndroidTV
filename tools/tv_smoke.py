#!/usr/bin/env python3
"""Start SimpMusic on the TV the way the TV's home screen does, and check it came up.

    TV_HOST=192.168.1.50 tools/tv_smoke.py [--package PKG] [--wait SECONDS] [--out DIR]

Checks, in order:
    entry     the package has a LEANBACK_LAUNCHER activity (it shows on the TV's home screen)
    start     `am start -W` through that entry answers Status: ok
    process   the app's process is alive --wait seconds later
    media     the app's MediaSession is registered (dumpsys media_session)
    crash     no FATAL EXCEPTION or ANR for the package in the log since the start

The log since the start, the media_session dump and a screenshot are saved to --out
(default smoke/<timestamp>), so a failure can be read afterwards. Those files can show the TV's
screen and account names: keep them out of commits.

Exit status: 0 every check passed; 1 a check failed (each one is printed); 2 nothing was checked - a bad
argument, or the TV was not reached.
"""
import argparse
import datetime
from pathlib import Path
import re
import sys
import time

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

DEFAULT_PACKAGE = "com.maxrave.simpmusic.dev"
PACKAGE = re.compile(r"^[A-Za-z][\w]*(\.[A-Za-z][\w]*)+$")
COMPONENT = re.compile(r"^[A-Za-z][\w.]*/[A-Za-z0-9_.$]+$")
SHOT_REMOTE = "/data/local/tmp/simpmusic-tv-smoke.png"


def parse(argv):
    p = argparse.ArgumentParser(description="Start SimpMusic on the TV and check it came up.")
    p.add_argument("--package", default=DEFAULT_PACKAGE, help=f"the app's package (default {DEFAULT_PACKAGE})")
    p.add_argument("--wait", type=float, default=15.0, help="seconds between the start and the checks (default 15)")
    p.add_argument("--out", type=Path, help="where the evidence goes (default smoke/<timestamp>)")
    args = p.parse_args(argv)
    if not PACKAGE.match(args.package):
        p.error(f"--package: {args.package!r} is not a package name")
    if not 0 <= args.wait <= 300:
        p.error("--wait: between 0 and 300 seconds")
    if args.out is not None and args.out.exists() and not args.out.is_dir():
        p.error(f"--out: {args.out} is a file")
    return args


def tv_entry(leanback, package):
    """The package's LEANBACK_LAUNCHER component in `query-activities --brief` output, or None."""
    for line in leanback.splitlines():
        line = line.strip()
        if line.startswith(package + "/") and COMPONENT.match(line):
            return line
    return None


def start_command(component):
    return f"am start -W -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n {component}"


def started(out):
    return "Status: ok" in out


def session_registered(dump, package):
    """Whether dumpsys media_session lists a session owned by the package."""
    return re.search(rf"\bpackage={re.escape(package)}\b", dump) is not None


def crashes(log, package, pid=None):
    """FATAL EXCEPTION / ANR lines that name the package (or its pid) in a logcat dump."""
    found = []
    lines = log.splitlines()
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" in line:
            window = "\n".join(lines[i:i + 3])
            if package in window or (pid and f"PID: {pid}" in window):
                found.append(line.strip())
        elif re.search(r"\bANR in\b", line) and package in line:
            found.append(line.strip())
    return found


def connect():
    try:
        import tv  # noqa: E402  (reads TV_HOST at import)
    except ModuleNotFoundError as e:
        print(f"tv_smoke.py needs {e.name}: python3 -m venv tools/venv && "
              "tools/venv/bin/pip install -r tools/requirements.txt", file=sys.stderr)
        raise SystemExit(2) from e
    except SystemExit as e:
        if isinstance(e.code, str):
            print(e.code, file=sys.stderr)
        raise SystemExit(2) from e
    try:
        return tv.connect()
    except Exception as e:
        print(tv.connect_error(e) or f"Could not reach the TV: {e}", file=sys.stderr)
        raise SystemExit(2) from e


def run(dev, args, out_dir):
    """Every check, in order; returns [(name, passed, detail)]."""
    results = []
    leanback = dev.shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                         "-c android.intent.category.LEANBACK_LAUNCHER")
    component = tv_entry(leanback, args.package)
    results.append(("entry", component is not None, component or "no LEANBACK_LAUNCHER activity"))
    if component is None:
        return results
    dev.shell("logcat -c")
    dev.shell(f"am force-stop {args.package}")
    answer = dev.shell(start_command(component), read_timeout_s=60)
    wait = re.search(r"WaitTime:\s*(\d+)", answer)
    status = re.search(r"Status:\s*(\S+)", answer)
    detail = f"Status: {status.group(1) if status else 'none'}"
    if wait:
        # `timeout` means am gave up waiting for the first frame (10 s), not that the app failed to open.
        detail += f", {wait.group(1)} ms"
    results.append(("start", started(answer), detail))
    time.sleep(args.wait)
    pid = dev.shell(f"pidof {args.package}").strip()
    results.append(("process", bool(pid), f"pid {pid}" if pid else "not running"))
    dump = dev.shell("dumpsys media_session")
    (out_dir / "media_session.txt").write_text(dump, encoding="utf-8")
    results.append(("media", session_registered(dump, args.package),
                    "session registered" if session_registered(dump, args.package) else "no session"))
    log = dev.shell("logcat -d -b main,system,crash", read_timeout_s=60)
    (out_dir / "logcat.txt").write_text(log, encoding="utf-8")
    found = crashes(log, args.package, pid or None)
    results.append(("crash", not found, "; ".join(found) or "none"))
    try:
        dev.shell(f"screencap -p {SHOT_REMOTE}")
        dev.pull(SHOT_REMOTE, str(out_dir / "screen.png"))
        dev.shell(f"rm -f {SHOT_REMOTE}")
    except Exception as e:
        # Evidence, not a check: the verdicts above still stand and are still printed.
        print(f"no screenshot: {e}", file=sys.stderr)
    return results


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    out_dir = args.out or Path("smoke") / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    dev = connect()
    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        results = run(dev, args, out_dir)
    finally:
        dev.close()
    for name, passed, detail in results:
        print(f"{'PASS' if passed else 'FAIL'}  {name:<8} {detail}")
    print(f"Evidence: {out_dir}")
    return 0 if all(passed for _, passed, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())
