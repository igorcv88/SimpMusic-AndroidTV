"""tools/tv_install.py installs only TV-key builds, picks the TV's ABI, and never uninstalls by itself."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import tv_install  # noqa: E402

PACKAGE = "com.maxrave.simpmusic.dev"


class FakeDevice:
    def __init__(self, answers):
        self.answers = list(answers)
        self.commands = []
        self.pushed = []

    def push(self, local, remote, **kwargs):
        self.pushed.append((local, remote))

    def shell(self, command, **kwargs):
        self.commands.append(command)
        if command.startswith("pm install"):
            return self.answers.pop(0)
        return ""


class PickTest(unittest.TestCase):
    def apks(self, names):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        folder = Path(tmp.name) / "debug"
        folder.mkdir()
        for name in names:
            (folder / name).write_bytes(b"")
        return tv_install.built_apks("debug", Path(tmp.name))

    def test_the_split_for_the_tvs_first_abi_wins(self):
        apks = self.apks(["androidApp-arm64-v8a-debug.apk", "androidApp-armeabi-v7a-debug.apk",
                          "androidApp-universal-debug.apk"])
        self.assertEqual(tv_install.pick_apk(apks, "armeabi-v7a,armeabi").name, "androidApp-armeabi-v7a-debug.apk")
        self.assertEqual(tv_install.pick_apk(apks, "arm64-v8a,armeabi-v7a").name, "androidApp-arm64-v8a-debug.apk")

    def test_the_universal_apk_when_no_split_fits(self):
        apks = self.apks(["androidApp-arm64-v8a-debug.apk", "androidApp-universal-debug.apk"])
        self.assertEqual(tv_install.pick_apk(apks, "x86").name, "androidApp-universal-debug.apk")

    def test_a_build_without_splits_is_the_universal_apk(self):
        apks = self.apks(["androidApp-debug.apk"])
        self.assertEqual(tv_install.pick_apk(apks, "arm64-v8a").name, "androidApp-debug.apk")

    def test_nothing_built_is_none(self):
        self.assertIsNone(tv_install.pick_apk(self.apks([]), "arm64-v8a"))


class GradleTest(unittest.TestCase):
    def test_the_signing_file_reaches_gradle_as_a_property(self):
        command = tv_install.gradle_command("debug", Path("/tmp/x/tv-signing.properties"))
        self.assertEqual(command[1:], [":androidApp:assembleDebug", "-PtvSigning=" + str(Path("/tmp/x/tv-signing.properties"))])
        self.assertIn("-PisFullBuild=true", tv_install.gradle_command("release", Path("p"), full=True))


class InstallTest(unittest.TestCase):
    def setUp(self):
        self.apk = Path("androidApp-arm64-v8a-debug.apk")

    def test_success_installs_and_cleans_up(self):
        dev = FakeDevice(["Success\n"])
        self.assertEqual(tv_install.install(dev, self.apk, PACKAGE), 0)
        self.assertEqual(dev.pushed, [(str(self.apk), tv_install.REMOTE_APK)])
        self.assertEqual(dev.commands[-1], f"rm -f {tv_install.REMOTE_APK}")

    def test_another_key_on_the_tv_changes_nothing(self):
        dev = FakeDevice(["Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]\n"])
        with mock.patch("sys.stderr"):
            self.assertEqual(tv_install.install(dev, self.apk, PACKAGE), 3)
        self.assertFalse([c for c in dev.commands if "uninstall" in c], "the app was removed without being asked")
        self.assertEqual(dev.commands[-1], f"rm -f {tv_install.REMOTE_APK}")

    def test_uninstall_first_removes_only_when_asked_and_only_on_a_key_mismatch(self):
        dev = FakeDevice(["Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n", "Success\n"])
        with mock.patch("builtins.print"):
            self.assertEqual(tv_install.install(dev, self.apk, PACKAGE, uninstall_first=True), 0)
        self.assertIn(f"pm uninstall {PACKAGE}", dev.commands)
        dev = FakeDevice(["Success\n"])
        tv_install.install(dev, self.apk, PACKAGE, uninstall_first=True)
        self.assertFalse([c for c in dev.commands if "uninstall" in c])

    def test_a_failed_push_leaves_nothing_behind(self):
        dev = FakeDevice([])
        dev.push = mock.Mock(side_effect=OSError("connection reset"))
        with self.assertRaises(OSError):
            tv_install.install(dev, self.apk, PACKAGE)
        self.assertEqual(dev.commands, [f"rm -f {tv_install.REMOTE_APK}"])

    def test_another_failure_is_1(self):
        dev = FakeDevice(["Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]\n"])
        with mock.patch("sys.stderr"):
            self.assertEqual(tv_install.install(dev, self.apk, PACKAGE), 1)


class MainTest(unittest.TestCase):
    def test_no_tv_host_is_2_before_anything_is_built(self):
        with mock.patch.dict(tv_install.os.environ, {}, clear=True), \
                mock.patch.object(tv_install, "build") as build, mock.patch("sys.stderr"):
            self.assertEqual(tv_install.main([]), 2)
        build.assert_not_called()

    def test_an_apk_not_signed_with_the_tv_key_never_reaches_the_tv(self):
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as handle:
            apk = Path(handle.name)
        self.addCleanup(apk.unlink)
        dev = FakeDevice([])
        error = tv_install.tv_signing.SigningError("app.apk is not signed with the TV key")
        with mock.patch.dict(tv_install.os.environ, {"TV_HOST": "192.0.2.1"}), \
                mock.patch.object(tv_install, "connect", return_value=dev), \
                mock.patch.object(tv_install.tv_signing, "verify_apk", side_effect=error), \
                mock.patch("sys.stderr"):
            self.assertEqual(tv_install.main(["--apk", str(apk)]), 1)
        self.assertEqual(dev.pushed, [])

    def test_a_tv_below_the_minimum_sdk_is_refused(self):
        dev = mock.Mock()
        dev.shell.side_effect = lambda command, **kw: "25\n" if "sdk" in command else ""
        with mock.patch.dict(tv_install.os.environ, {"TV_HOST": "192.0.2.1"}), \
                mock.patch.object(tv_install, "connect", return_value=dev), mock.patch("sys.stderr"):
            self.assertEqual(tv_install.main([]), 2)

    def test_bad_arguments_are_refused(self):
        for argv in (["--apk", "x.apk", "--build-only"], ["--apk", "no-such.apk"], ["--package", "x; reboot"],
                     ["--apk", "x.apk", "--uninstall-first"]):
            with self.subTest(argv=argv), mock.patch("sys.stderr"), self.assertRaises(SystemExit) as caught:
                tv_install.parse(argv)
            self.assertEqual(caught.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
