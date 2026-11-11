"""Plan the next Android TV release from the checked-in Gradle version catalog.

The release workflow builds with the bumped catalog, then commits that same
catalog only after all APKs are built and signature-verified. A failed publish
can be retried: the previous release-bump commit is reused while untagged.
"""

from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


NAME_PATTERN = re.compile(r'^(version-name\s*=\s*")(\d+\.\d+\.\d+)("[^\n]*)$', re.M)
CODE_PATTERN = re.compile(r'^(version-code\s*=\s*")(\d+)("[^\n]*)$', re.M)
TAG_PATTERN = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
PENDING_PATTERN = re.compile(r"^chore\(tv-release\): bump to (v\d+\.\d+\.\d+) \[skip ci\]$")


@dataclass(frozen=True)
class VersionPlan:
    tag: str
    version_name: str
    version_code: int
    updated_catalog: str
    needs_commit: bool


def plan_version(catalog: str, tip_subject: str, remote_tags: set[str]) -> VersionPlan:
    """Never reuse a tag or reduce the versionCode of an installed TV APK."""
    names = list(NAME_PATTERN.finditer(catalog))
    codes = list(CODE_PATTERN.finditer(catalog))
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("Expected one version-name and one version-code in the catalog")

    current = names[0].group(2)
    current_code = int(codes[0].group(2))
    major, minor, patch = map(int, current.split("."))
    current_tuple = (major, minor, patch)
    published = {tuple(map(int, match.groups())) for tag in remote_tags if (match := TAG_PATTERN.fullmatch(tag))}

    pending = PENDING_PATTERN.fullmatch(tip_subject)
    if pending and pending.group(1) == f"v{current}":
        if f"v{current}" in remote_tags:
            raise ValueError(f"{current} is already tagged; cannot republish over it")
        return VersionPlan(f"v{current}", current, current_code, catalog, False)

    if published and max(published) > current_tuple:
        raise ValueError("A published tag is newer than the version catalog; update the catalog first")

    next_name = f"{major}.{minor}.{patch + 1}"
    next_tag = f"v{next_name}"
    if next_tag in remote_tags:
        raise ValueError(f"Refusing to overwrite existing tag {next_tag}")
    next_code = current_code + 1
    if next_code > 2_100_000_000:
        raise ValueError("Android versionCode limit approached")

    updated = NAME_PATTERN.sub(lambda m: m.group(1) + next_name + m.group(3), catalog, count=1)
    updated = CODE_PATTERN.sub(lambda m: m.group(1) + str(next_code) + m.group(3), updated, count=1)
    return VersionPlan(next_tag, next_name, next_code, updated, True)


def git_stdout(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def prepare(catalog_path: Path, output_path: Path) -> VersionPlan:
    subject = git_stdout("log", "-1", "--format=%s")
    refs = git_stdout("ls-remote", "--tags", "--refs", "origin")
    tags = {line.rsplit("refs/tags/", 1)[1] for line in refs.splitlines() if "refs/tags/" in line}
    content = catalog_path.read_text(encoding="utf-8")
    plan = plan_version(content, subject, tags)
    if plan.needs_commit:
        catalog_path.write_text(plan.updated_catalog, encoding="utf-8")
    with output_path.open("a", encoding="utf-8") as output:
        output.write(f"tag={plan.tag}\n")
        output.write(f"version_name={plan.version_name}\n")
        output.write(f"version_code={plan.version_code}\n")
        output.write(f"needs_commit={str(plan.needs_commit).lower()}\n")
    return plan


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--github-output", required=True, type=Path)
    args = parser.parse_args()
    plan = prepare(args.catalog, args.github_output)
    print(f"Release version: {plan.tag}; Android versionCode: {plan.version_code}")
    print("Resuming an untagged version-bump commit" if not plan.needs_commit else "Version catalog prepared for verified release")


if __name__ == "__main__":
    main()
