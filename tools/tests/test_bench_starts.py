"""tools/bench.py starts SimpMusic through its TV entry, cold and warm, and finds that entry by package.

Upstream's bench.py measured a launcher: it looked up the component that answers HOME and timed the
HOME key. SimpMusic is an app, so the TV home screen reaches it through its LEANBACK_LAUNCHER entry -
the intent-filter the TV launcher bootstrap added to MainActivity. These tests pin that the tool
starts the app through that same intent, that a cold start stops the process first and a warm start
does not, and that a build without the TV entry is not mistaken for one that has it.
"""
from pathlib import Path
import sys
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools import bench  # noqa: E402

PACKAGE = "com.maxrave.simpmusic.dev"
ENTRY = f"{PACKAGE}/com.maxrave.simpmusic.MainActivity"
SETTINGS = "com.android.tv.settings/.MainSettings"


class FakeTV:
    """Answers the commands bench.py sends with fixed text and records them."""

    def __init__(self, leanback=(ENTRY,), wait_times=None):
        self.commands = []
        self._other_app = None
        self.leanback = list(leanback)
        self.wait_times = list(wait_times or [])

    def package_of(self, component):
        return component.split("/", 1)[0]

    def shell(self, command):
        self.commands.append(command)
        if "query-activities" in command and "LEANBACK_LAUNCHER" in command:
            return "".join(f"{line}\n" for line in self.leanback)
        if "resolve-activity" in command and "SETTINGS" in command:
            return f"priority=0 preferredOrder=0\n{SETTINGS}\n"
        if command.startswith("am start -W") and "LEANBACK_LAUNCHER" in command:
            value = self.wait_times.pop(0) if self.wait_times else 900
            return f"Status: ok\nWaitTime: {value}\nComplete\n"
        if "enabled_accessibility_services" in command:
            return "null\n"
        return ""

    app_component = bench.TV.app_component


class AppComponentTest(unittest.TestCase):
    def test_the_entry_of_the_package_is_found_among_the_leanback_apps(self):
        tv = FakeTV(leanback=["com.spotify.tv.android/.SpotifyTVActivity", ENTRY])
        self.assertEqual(tv.app_component(PACKAGE), ENTRY)

    def test_another_build_of_the_same_app_is_not_taken_for_this_one(self):
        # The release build is com.maxrave.simpmusic; the debug build's name starts with it.
        tv = FakeTV(leanback=["com.maxrave.simpmusic/com.maxrave.simpmusic.MainActivity"])
        self.assertIsNone(tv.app_component(PACKAGE))

    def test_a_build_without_the_tv_entry_has_none(self):
        self.assertIsNone(FakeTV(leanback=[]).app_component(PACKAGE))


class StartTest(unittest.TestCase):
    def setUp(self):
        patcher = mock.patch.object(bench.time, "sleep", lambda seconds: None)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_the_start_command_is_the_intent_the_tv_home_screen_sends(self):
        command = bench.start_command(ENTRY)
        self.assertIn("-a android.intent.action.MAIN", command)
        self.assertIn("-c android.intent.category.LEANBACK_LAUNCHER", command)
        self.assertIn(f"-n {ENTRY}", command)
        self.assertNotIn("category.HOME", command)

    def test_a_cold_start_stops_the_process_before_every_start(self):
        tv = FakeTV(wait_times=[1200, 1300])
        self.assertEqual(bench.cold_start(tv, ENTRY, 2), [1200, 1300])
        starts = [i for i, c in enumerate(tv.commands) if "LEANBACK_LAUNCHER -n" in c]
        stops = [i for i, c in enumerate(tv.commands) if c == f"am force-stop {PACKAGE}"]
        self.assertEqual(len(starts), 2)
        self.assertEqual(len(stops), 2)
        for stop, start in zip(stops, starts):
            self.assertLess(stop, start, "the app was started before it was stopped")

    def test_a_warm_start_never_stops_the_process(self):
        tv = FakeTV(wait_times=[2500, 300, 280, 310])
        # The first start only makes sure the process exists; the three after it are measured.
        self.assertEqual(bench.warm_start(tv, ENTRY, 3), [300, 280, 310])
        self.assertFalse([c for c in tv.commands if "force-stop" in c])

    def test_both_leave_simpmusic_for_another_app_first(self):
        for measure in (bench.cold_start, bench.warm_start):
            with self.subTest(measure=measure.__name__):
                tv = FakeTV()
                measure(tv, ENTRY, 1)
                others = [c for c in tv.commands if c.endswith(f"-n {SETTINGS}")]
                self.assertTrue(others, "SimpMusic was never sent to the background")

    def test_the_app_under_test_is_never_the_other_app(self):
        tv = FakeTV(leanback=[ENTRY, "com.spotify.tv.android/.SpotifyTVActivity"])
        with mock.patch.object(tv, "shell", wraps=tv.shell) as shell:
            shell.side_effect = lambda command: ("" if "resolve-activity" in command
                                                 else FakeTV.shell(tv, command))
            self.assertEqual(bench.other_app(tv, PACKAGE), "com.spotify.tv.android/.SpotifyTVActivity")

    def test_no_other_app_measures_nothing_instead_of_a_wrong_number(self):
        tv = FakeTV(leanback=[ENTRY])
        with mock.patch.object(tv, "shell", side_effect=lambda command: (
                "" if "SETTINGS" in command else FakeTV.shell(tv, command))):
            self.assertEqual(bench.cold_start(tv, ENTRY, 3), [])
            self.assertEqual(bench.warm_start(tv, ENTRY, 3), [])

    def test_a_start_with_no_wait_time_is_left_out(self):
        self.assertIsNone(bench.wait_time("Status: timeout\nComplete\n"))
        self.assertEqual(bench.wait_time("Status: ok\nWaitTime: 10579\n"), 10579)


if __name__ == "__main__":
    unittest.main()
