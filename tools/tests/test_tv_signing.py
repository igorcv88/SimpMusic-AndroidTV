"""tools/tv_signing.py reads secrets.env without printing it, writes Gradle's file, and checks the cert."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import tv_signing  # noqa: E402

EXPECTED = tv_signing.EXPECTED_CERT_SHA256
COLONS = ":".join(EXPECTED[i:i + 2] for i in range(0, 64, 2))


def secrets_file(folder, text):
    path = Path(folder) / "secrets.env"
    path.write_text(text, encoding="utf-8")
    return path


class ParseTest(unittest.TestCase):
    def test_quotes_comments_and_export_are_read_as_a_shell_would(self):
        values = tv_signing.parse_env('# key\nexport KEY_ALIAS="my alias"\nKEY_PASSWORD=\'p=w#d\'\n\n'
                                      "KEYSTORE_BASE64_PATH=x\nnot a line\n")
        self.assertEqual(values, {"KEY_ALIAS": "my alias", "KEY_PASSWORD": "p=w#d", "KEYSTORE_BASE64_PATH": "x"})

    def test_a_byte_order_mark_and_no_final_newline_are_fine(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = Path(tmp) / "k.jks"
            store.write_bytes(b"x")
            path = Path(tmp) / "secrets.env"
            path.write_bytes(("\ufeffKEYSTORE_PASSWORD=a\nKEY_ALIAS=b\nKEY_PASSWORD=c\n"
                              f"KEY_PATH={store.as_posix()}").encode("utf-8"))
            secrets = tv_signing.load_secrets(path)
        self.assertEqual(secrets["KEYSTORE_PASSWORD"], "a")
        self.assertEqual(Path(secrets["KEY_PATH"]), store.resolve())

    def test_missing_values_are_named_and_no_value_is_printed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = secrets_file(tmp, "KEYSTORE_PASSWORD=hunter2\nKEY_ALIAS=\n")
            with self.assertRaises(tv_signing.SigningError) as caught:
                tv_signing.load_secrets(path)
        message = str(caught.exception)
        for key in ("KEY_ALIAS", "KEY_PASSWORD", "KEY_PATH"):
            self.assertIn(key, message)
        self.assertNotIn("hunter2", message)

    def test_a_keystore_that_is_not_there_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = secrets_file(tmp, "KEYSTORE_PASSWORD=a\nKEY_ALIAS=b\nKEY_PASSWORD=c\nKEY_PATH=/no/such.jks\n")
            with self.assertRaisesRegex(tv_signing.SigningError, "KEY_PATH"):
                tv_signing.load_secrets(path)

    def test_no_secrets_file_says_how_to_make_one(self):
        with self.assertRaisesRegex(tv_signing.SigningError, "gitignored"):
            tv_signing.load_secrets(Path(tempfile.gettempdir()) / "no-such-secrets.env")


class PropertiesTest(unittest.TestCase):
    SECRETS = {"KEY_PATH": r"C:\keys\tv key.jks", "KEYSTORE_PASSWORD": "pä:ss=1",
               "KEY_ALIAS": "alias", "KEY_PASSWORD": "#k!"}

    def test_the_file_is_escaped_for_java_properties(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = tv_signing.write_properties(self.SECRETS, tmp).read_text(encoding="ascii")
        self.assertIn(r"storeFile=C\:\\keys\\tv\ key.jks", text)
        self.assertIn(r"storePassword=p\u00E4\:ss\=1", text)
        self.assertIn(r"keyPassword=\#k\!", text)

    def test_the_file_is_gone_after_the_block(self):
        with tv_signing.SigningProperties(self.SECRETS) as path:
            self.assertTrue(path.is_file())
        self.assertFalse(path.exists())
        self.assertFalse(path.parent.exists())

    def test_the_file_is_gone_after_a_failure_too(self):
        with self.assertRaises(RuntimeError):
            with tv_signing.SigningProperties(self.SECRETS) as path:
                raise RuntimeError("gradle failed")
        self.assertFalse(path.exists())


class VerifyTest(unittest.TestCase):
    def verify(self, stdout, returncode=0):
        result = mock.Mock(returncode=returncode, stdout=stdout, stderr="")
        with mock.patch.object(tv_signing.subprocess, "run", return_value=result):
            tv_signing.verify_apk("app.apk", apksigner="apksigner")

    def test_the_tv_key_passes(self):
        self.verify(f"Signer #1 certificate DN: CN=Unknown\nSigner #1 certificate SHA-256 digest: {EXPECTED}\n")

    def test_digests_with_colons_and_capitals_are_the_same_digest(self):
        self.verify(f"Signer #1 certificate SHA-256 digest: {COLONS.upper()}\n")

    def test_the_debug_key_is_refused(self):
        with self.assertRaisesRegex(tv_signing.SigningError, "not signed with the TV key"):
            self.verify("Signer #1 certificate SHA-256 digest: " + "ab" * 32 + "\n")

    def test_a_second_signer_is_refused(self):
        with self.assertRaises(tv_signing.SigningError):
            self.verify(f"Signer #1 certificate SHA-256 digest: {EXPECTED}\n"
                        "Signer #2 certificate SHA-256 digest: " + "cd" * 32 + "\n")

    def test_an_apk_that_does_not_verify_is_refused(self):
        with self.assertRaisesRegex(tv_signing.SigningError, "rejected"):
            self.verify("", returncode=1)

    def test_no_apksigner_says_where_to_get_it(self):
        with mock.patch.object(tv_signing, "find_apksigner", return_value=None):
            with self.assertRaisesRegex(tv_signing.SigningError, "build-tools"):
                tv_signing.verify_apk("app.apk")


class ApksignerTest(unittest.TestCase):
    def test_the_newest_build_tools_is_used(self):
        with tempfile.TemporaryDirectory() as tmp:
            name = "apksigner.bat" if tv_signing.os.name == "nt" else "apksigner"
            for version in ("34.0.0", "36.1.0", "9.0.0"):
                folder = Path(tmp) / "build-tools" / version
                folder.mkdir(parents=True)
                (folder / name).write_text("")
            with mock.patch.dict(tv_signing.os.environ, {"ANDROID_HOME": tmp}):
                self.assertEqual(Path(tv_signing.find_apksigner()).parent.name, "36.1.0")


if __name__ == "__main__":
    unittest.main()
