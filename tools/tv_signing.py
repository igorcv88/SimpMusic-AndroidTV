"""The TV signing key: read it from secrets.env, hand it to Gradle, check the APK carries it.

Every build installed on the TV is signed with one key, so a new build installs over the last one
without losing the app's data. Locally the key is described by a gitignored `secrets.env` at the
repository root:

    KEY_PATH=C:/path/to/keystore.jks
    KEYSTORE_PASSWORD=...
    KEY_ALIAS=...
    KEY_PASSWORD=...

CI writes the same four values from GitHub secrets (tools/release_properties.py). Gradle reads them
through `-PtvSigning=<properties file>` (androidApp/build.gradle.kts). Nothing here prints a secret.
"""
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

try:
    from release_properties import escape
except ModuleNotFoundError:   # imported as tools.tv_signing (the tests)
    from tools.release_properties import escape

ROOT = Path(__file__).resolve().parents[1]
SECRETS = ROOT / "secrets.env"
# SHA-256 of the certificate in the TV key. Public: it is printed by `apksigner verify --print-certs`
# on every APK the key signs. An APK whose certificate differs was not signed with the TV key.
EXPECTED_CERT_SHA256 = "e5d68192109e8c65f4d433cb4beda0a47c40e7b73c9e48832673b24a6b0b9257"
REQUIRED = ("KEY_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
LINE = re.compile(r"\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$")
CERT_LINE = re.compile(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)")


class SigningError(Exception):
    """The key cannot be used as described; the message names what is wrong, never a secret."""


def parse_env(text):
    """KEY=value lines, as a shell would read them: comments and blanks skipped, one layer of quotes off."""
    values = {}
    for raw in text.splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        match = LINE.match(raw)
        if not match:
            continue
        key, value = match.groups()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def load_secrets(path=SECRETS):
    """The four signing values from `path`; SigningError names the missing ones, not their values."""
    path = Path(path)
    if not path.is_file():
        raise SigningError(f"{path} does not exist: create it with {', '.join(REQUIRED)} (it is gitignored).")
    values = parse_env(path.read_text(encoding="utf-8-sig"))
    missing = [key for key in REQUIRED if not values.get(key)]
    if missing:
        raise SigningError(f"{path} has no value for {', '.join(missing)}.")
    store = Path(values["KEY_PATH"]).expanduser()
    if not store.is_file():
        raise SigningError(f"KEY_PATH in {path} does not name a file.")
    return {key: values[key] for key in REQUIRED} | {"KEY_PATH": str(store.resolve())}


def write_properties(secrets, folder):
    """Gradle's signing properties file inside `folder`, escaped for java.util.Properties."""
    values = {
        "storeFile": secrets["KEY_PATH"],
        "storePassword": secrets["KEYSTORE_PASSWORD"],
        "keyAlias": secrets["KEY_ALIAS"],
        "keyPassword": secrets["KEY_PASSWORD"],
    }
    path = Path(folder) / "tv-signing.properties"
    path.write_text("".join(f"{key}={escape(value)}\n" for key, value in values.items()), encoding="ascii")
    if os.name != "nt":
        os.chmod(path, 0o600)
    return path


class SigningProperties:
    """`with SigningProperties(secrets) as path:` - the file exists only inside the block."""

    def __init__(self, secrets):
        self.secrets = secrets
        self.folder = None

    def __enter__(self):
        self.folder = tempfile.mkdtemp(prefix="simpmusic-tv-signing-")
        return write_properties(self.secrets, self.folder)

    def __exit__(self, *exc):
        shutil.rmtree(self.folder, ignore_errors=True)
        return False


def sdk_dir():
    """The Android SDK: ANDROID_HOME, ANDROID_SDK_ROOT, then local.properties' sdk.dir."""
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return Path(os.environ[name])
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    return None


def version_key(name):
    return [int(part) if part.isdigit() else -1 for part in re.split(r"[.-]", name)]


def find_apksigner():
    """apksigner from the newest build-tools, else from PATH; None when there is neither."""
    sdk = sdk_dir()
    executable = "apksigner.bat" if os.name == "nt" else "apksigner"
    if sdk and (sdk / "build-tools").is_dir():
        for version in sorted((p.name for p in (sdk / "build-tools").iterdir()), key=version_key, reverse=True):
            candidate = sdk / "build-tools" / version / executable
            if candidate.is_file():
                return str(candidate)
    return shutil.which("apksigner")


def cert_digests(output):
    """The certificate SHA-256 digests `apksigner verify --print-certs` printed, lowercase, no colons."""
    return [match.replace(":", "").lower() for match in CERT_LINE.findall(output)]


def verify_apk(apk, apksigner=None, expected=EXPECTED_CERT_SHA256):
    """Raise SigningError unless `apk` verifies and is signed by the TV key alone."""
    apksigner = apksigner or find_apksigner()
    if not apksigner:
        raise SigningError("apksigner was not found: install Android SDK build-tools or set ANDROID_HOME.")
    result = subprocess.run([apksigner, "verify", "--print-certs", str(apk)],
                            capture_output=True, text=True, errors="replace")
    if result.returncode:
        raise SigningError(f"apksigner rejected {apk}: {(result.stderr or result.stdout).strip()}")
    digests = cert_digests(result.stdout)
    if digests != [expected]:
        found = ", ".join(digests) or "none"
        raise SigningError(f"{Path(apk).name} is not signed with the TV key (certificate SHA-256: {found}).")
