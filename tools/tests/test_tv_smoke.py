"""tools/tv_smoke.py reads the TV's answers the way the PR #1 validation read them by hand."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import tv_smoke  # noqa: E402

PACKAGE = "com.maxrave.simpmusic.dev"
ENTRY = f"{PACKAGE}/com.maxrave.simpmusic.MainActivity"


class FakeDevice:
    def __init__(self, leanback=ENTRY, start="Status: ok\nWaitTime: 900\n", pid="4321",
                 sessions=f"  SimpMusic package={PACKAGE}\n", log=""):
        self.answers = {"query-activities": leanback + "\n", "am start": start, "pidof": pid,
                        "media_session": sessions, "logcat -d": log}
        self.commands = []

    def shell(self, command, **kwargs):
        self.commands.append(command)
        return next((v for k, v in self.answers.items() if k in command), "")

    def pull(self, remote, local):
        Path(local).write_bytes(b"png")


class ReadTest(unittest.TestCase):
    def test_the_entry_is_the_packages_own(self):
        self.assertEqual(tv_smoke.tv_entry(f"com.maxrave.simpmusic/x.Main\n{ENTRY}\n", PACKAGE), ENTRY)
        self.assertIsNone(tv_smoke.tv_entry("com.maxrave.simpmusic/x.Main\n", PACKAGE))

    def test_another_apps_session_is_not_this_one(self):
        # PR #1: the now-playing card on the TV was Spotify's session, not SimpMusic's.
        dump = "  Spotify package=com.spotify.tv.android\n"
        self.assertFalse(tv_smoke.session_registered(dump, PACKAGE))
        self.assertFalse(tv_smoke.session_registered("package=com.maxrave.simpmusic\n", PACKAGE))
        self.assertTrue(tv_smoke.session_registered(f"package={PACKAGE} active=true\n", PACKAGE))

    def test_a_crash_of_this_app_is_found_and_anothers_is_not(self):
        log = ("E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: com.other, PID: 9\n"
               "E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: " + PACKAGE + ", PID: 77\n"
               "E ActivityManager: ANR in " + PACKAGE + "\n")
        self.assertEqual(len(tv_smoke.crashes(log, PACKAGE)), 2)
        self.assertEqual(tv_smoke.crashes("E AndroidRuntime: FATAL EXCEPTION: main\nProcess: com.other\n", PACKAGE), [])

    def test_the_start_goes_through_the_tv_entry(self):
        self.assertIn("-c android.intent.category.LEANBACK_LAUNCHER -n " + ENTRY, tv_smoke.start_command(ENTRY))


class RunTest(unittest.TestCase):
    def run_checks(self, dev):
        args = tv_smoke.parse(["--wait", "0"])
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(tv_smoke.time, "sleep"):
            results = tv_smoke.run(dev, args, Path(tmp))
            saved = sorted(p.name for p in Path(tmp).iterdir())
        return {name: passed for name, passed, _ in results}, saved, dev.commands

    def test_a_healthy_start_passes_every_check_and_keeps_the_evidence(self):
        results, saved, commands = self.run_checks(FakeDevice())
        self.assertEqual(results, {"entry": True, "start": True, "process": True, "media": True, "crash": True})
        self.assertEqual(saved, ["logcat.txt", "media_session.txt", "screen.png"])
        self.assertLess(commands.index("logcat -c"), next(i for i, c in enumerate(commands) if "am start" in c))
        self.assertEqual(commands[-1], f"rm -f {tv_smoke.SHOT_REMOTE}")

    def test_no_tv_entry_stops_before_starting_anything(self):
        results, _, commands = self.run_checks(FakeDevice(leanback=""))
        self.assertEqual(results, {"entry": False})
        self.assertFalse([c for c in commands if "am start" in c])

    def test_a_timeout_a_dead_process_and_a_crash_each_fail(self):
        log = f"E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: {PACKAGE}, PID: 1\n"
        results, _, _ = self.run_checks(FakeDevice(start="Status: timeout\n", pid="", sessions="", log=log))
        self.assertEqual(results, {"entry": True, "start": False, "process": False, "media": False, "crash": False})

    def test_a_slow_first_frame_names_the_status_and_the_time(self):
        args = tv_smoke.parse(["--wait", "0"])
        dev = FakeDevice(start="Status: timeout\nWaitTime: 10789\n")
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(tv_smoke.time, "sleep"):
            results = {name: detail for name, _, detail in tv_smoke.run(dev, args, Path(tmp))}
        self.assertEqual(results["start"], "Status: timeout, 10789 ms")

    def test_a_failed_screenshot_keeps_the_verdicts(self):
        dev = FakeDevice()
        dev.pull = mock.Mock(side_effect=OSError("pull failed"))
        results, saved, _ = self.run_checks_quietly(dev)
        self.assertTrue(all(results.values()))
        self.assertNotIn("screen.png", saved)

    def run_checks_quietly(self, dev):
        with mock.patch("sys.stderr"):
            return self.run_checks(dev)

    def test_bad_arguments_are_refused(self):
        for argv in (["--package", "x; reboot"], ["--wait", "-1"]):
            with self.subTest(argv=argv), mock.patch("sys.stderr"), self.assertRaises(SystemExit) as caught:
                tv_smoke.parse(argv)
            self.assertEqual(caught.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
