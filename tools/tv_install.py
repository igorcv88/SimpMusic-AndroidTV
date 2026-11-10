#!/usr/bin/env python3
"""Build SimpMusic signed with the TV key and install it on the TV, or install an APK already built.

    TV_HOST=192.168.1.50 tools/tv_install.py                  build the debug APK, sign, install
    TV_HOST=192.168.1.50 tools/tv_install.py --release        the release build instead
    TV_HOST=192.168.1.50 tools/tv_install.py --apk app.apk    install this APK (still checked)
    tools/tv_install.py --build-only                          build and check; no TV needed

The build is signed with the key secrets.env describes (tools/tv_signing.py), and every APK is checked
with apksigner before it reaches the TV: one that is not signed with the TV key is refused, so the TV
never ends up with a build a later one cannot update. The split for the TV's ABI is installed, else
the universal APK.

A build signed with another key cannot update the app on the TV (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
That is reported and nothing else happens: removing the old build also removes its data, the signed-in
account among it. --uninstall-first does that removal, and only when it is given.

Release builds check the signing certificate against upstream's (the "unofficial build" screen), so a
release signed with the TV key opens on that screen; the debug build skips the check.

Exit status: 0 installed (or built, with --build-only); 1 the build, the check or the install failed;
2 refused before anything was built or installed - a bad argument, no secrets.env, a TV not reached;
3 the TV holds a build signed with another key, and nothing was changed.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import tv_signing  # noqa: E402

ROOT = HERE.parent
APK_DIR = ROOT / "androidApp" / "build" / "outputs" / "apk"
MIN_SDK = 26
REMOTE_APK = "/data/local/tmp/simpmusic-tv-install.apk"
PACKAGES = {"debug": "com.maxrave.simpmusic.dev", "release": "com.maxrave.simpmusic"}
INCOMPATIBLE = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
STUDIO_JDKS = (Path(r"C:\Program Files\Android\Android Studio\jbr"),
               Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"),
               Path("/opt/android-studio/jbr"))


def parse(argv):
    p = argparse.ArgumentParser(description="Build SimpMusic signed with the TV key and install it on the TV.")
    p.add_argument("--release", action="store_true", help="the release build (default: debug)")
    p.add_argument("--apk", type=Path, help="install this APK instead of building one")
    p.add_argument("--build-only", action="store_true", help="build and check, do not install")
    p.add_argument("--secrets", type=Path, default=tv_signing.SECRETS, help="the secrets.env to sign with")
    p.add_argument("--uninstall-first", action="store_true",
                   help="remove the app first when the TV holds a build signed with another key "
                        "(this deletes its data)")
    p.add_argument("--full", action="store_true", help="the Full build (-PisFullBuild=true)")
    p.add_argument("--package", help="the APK's package, for --uninstall-first and the messages "
                                     "(default: the debug or release package)")
    args = p.parse_args(argv)
    if args.apk and args.build_only:
        p.error("--apk and --build-only: nothing would happen")
    if args.package and not re.fullmatch(r"[A-Za-z][\w]*(\.[A-Za-z][\w]*)+", args.package):
        p.error(f"--package: {args.package!r} is not a package name")
    if args.apk and args.uninstall_first and not args.package:
        p.error("--apk with --uninstall-first needs --package: the app to remove is named, never guessed")
    if args.apk and not args.apk.is_file():
        p.error(f"--apk: {args.apk} is not a file")
    return args


def build_type(args):
    return "release" if args.release else "debug"


def java_home():
    """JAVA_HOME if set, else Android Studio's bundled JDK where one is installed."""
    if os.environ.get("JAVA_HOME"):
        return os.environ["JAVA_HOME"]
    for jdk in STUDIO_JDKS:
        if (jdk / "bin").is_dir():
            return str(jdk)
    return None


def gradle_command(kind, properties, full=False):
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    task = f":androidApp:assemble{kind.capitalize()}"
    command = [wrapper, task, f"-PtvSigning={properties}"]
    if full:
        command.append("-PisFullBuild=true")
    return command


def build(kind, secrets, full=False):
    env = dict(os.environ)
    home = java_home()
    if home:
        env["JAVA_HOME"] = home
    # A split left by an earlier build would otherwise be picked up as this build's.
    shutil.rmtree(APK_DIR / kind, ignore_errors=True)
    with tv_signing.SigningProperties(secrets) as properties:
        result = subprocess.run(gradle_command(kind, properties, full), cwd=ROOT, env=env)
    if result.returncode:
        raise SystemExit(f"Gradle failed (exit {result.returncode}); nothing was installed.")


def built_apks(kind, folder=APK_DIR):
    """{abi or 'universal': path} for the APKs `assemble<Kind>` left in the outputs folder."""
    apks = {}
    pattern = re.compile(rf"androidApp-(?:(.+)-)?{kind}\.apk$")
    for apk in sorted((folder / kind).glob("*.apk")):
        match = pattern.match(apk.name)
        if match:
            apks[match.group(1) or "universal"] = apk
    return apks


def pick_apk(apks, abilist):
    """The split for the first ABI the TV lists that was built, else the universal APK."""
    for abi in (a.strip() for a in abilist.split(",")):
        if abi in apks:
            return apks[abi]
    return apks.get("universal")


def connect():
    """tools/tv.py's connection; its own sentences, and exit 2, when the TV is not reached."""
    try:
        import tv  # noqa: E402  (reads TV_HOST at import)
    except ModuleNotFoundError as e:
        print(f"tv_install.py needs {e.name}: python3 -m venv tools/venv && "
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


def install(dev, apk, package, uninstall_first=False):
    """Push and `pm install -r`; returns the exit status the docstring gives."""
    try:
        dev.push(str(apk), REMOTE_APK, transport_timeout_s=600, read_timeout_s=600)
        out = dev.shell(f"pm install -r {REMOTE_APK}", read_timeout_s=600)
        if INCOMPATIBLE in out and uninstall_first:
            print(f"Removing {package} first, as --uninstall-first asked: its data goes with it.")
            dev.shell(f"pm uninstall {package}")
            out = dev.shell(f"pm install -r {REMOTE_APK}", read_timeout_s=600)
    finally:
        dev.shell(f"rm -f {REMOTE_APK}")
    if INCOMPATIBLE in out:
        print(f"The TV holds {package} signed with another key, so this build cannot update it, and "
              "nothing was changed. Removing it also removes its data (the signed-in account among "
              "it); to do that, run again with --uninstall-first.", file=sys.stderr)
        return 3
    if "Success" not in out:
        print(f"pm install failed: {out.strip()}", file=sys.stderr)
        return 1
    print(f"Installed {apk.name} as {package}.")
    return 0


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    kind = build_type(args)
    if not args.build_only and not os.environ.get("TV_HOST"):
        print("Set TV_HOST to the TV's IP address (or use --build-only).", file=sys.stderr)
        return 2
    dev = None if args.build_only else connect()
    abilist = "arm64-v8a,armeabi-v7a,x86_64"
    if dev is not None:
        sdk = dev.shell("getprop ro.build.version.sdk").strip()
        if sdk.isdigit() and int(sdk) < MIN_SDK:
            print(f"The TV runs API {sdk}; SimpMusic needs {MIN_SDK} or later.", file=sys.stderr)
            return 2
        abilist = dev.shell("getprop ro.product.cpu.abilist").strip() or abilist
    if args.apk:
        apk = args.apk
    else:
        try:
            secrets = tv_signing.load_secrets(args.secrets)
        except tv_signing.SigningError as e:
            print(e, file=sys.stderr)
            return 2
        build(kind, secrets, args.full)
        apk = pick_apk(built_apks(kind), abilist)
        if apk is None:
            print(f"Gradle left no APK for {abilist} in {APK_DIR / kind}.", file=sys.stderr)
            return 1
    try:
        tv_signing.verify_apk(apk)
    except tv_signing.SigningError as e:
        print(f"{e} Nothing was installed.", file=sys.stderr)
        return 1
    print(f"{apk.name}: signed with the TV key.")
    if dev is None:
        return 0
    return install(dev, apk, args.package or PACKAGES[kind], args.uninstall_first)


if __name__ == "__main__":
    sys.exit(main())
