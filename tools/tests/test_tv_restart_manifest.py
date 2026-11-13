"""CAOC must restart a real TV activity, rather than the phone activity alias."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class TvRestartManifestTest(unittest.TestCase):
    def test_crash_restart_and_leanback_launcher_resolve_to_same_real_activity(self):
        application = ET.parse(ROOT / "tvAndroidApp/src/main/AndroidManifest.xml").getroot().find("application")
        restart = []
        launchers = []
        for activity in application.findall("activity"):
            for intent_filter in activity.findall("intent-filter"):
                actions = {item.get(ANDROID + "name") for item in intent_filter.findall("action")}
                categories = {item.get(ANDROID + "name") for item in intent_filter.findall("category")}
                if "cat.ereza.customactivityoncrash.RESTART" in actions:
                    restart.append(activity.get(ANDROID + "name"))
                if "android.intent.action.MAIN" in actions and "android.intent.category.LEANBACK_LAUNCHER" in categories:
                    launchers.append(activity.get(ANDROID + "name"))
        self.assertEqual([".tv.TvActivity"], launchers)
        self.assertEqual(launchers, restart, "CAOC discovery must find the real manifest activity kept by R8")


if __name__ == "__main__":
    unittest.main()
