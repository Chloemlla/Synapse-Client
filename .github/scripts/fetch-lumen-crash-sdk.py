#!/usr/bin/env python3
"""Resolve the latest lumen-crash SDK release and stage its Maven artifacts locally.

Prints LUMEN_CRASH_VERSION=<version> by default (or just the version with --print-version)
and writes android/lumen-crash.resolved.version so Gradle builds without a hardcoded version.
"""

import argparse
import hashlib
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

OWNER_REPO = "Chloemlla/Project-Lumen"
REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_DIR = REPO_ROOT / "android"
LOCAL_MAVEN = ANDROID_DIR / "local-maven"
RESOLVED_VERSION_FILE = ANDROID_DIR / "lumen-crash.resolved.version"

ARTIFACTS = ["lumen-crash", "lumen-crash-core"]
SUFFIXES = [".aar", ".pom", ".module", "-sources.jar"]

RELEASES_API = f"https://api.github.com/repos/{OWNER_REPO}/releases?per_page=100"
DOWNLOAD_BASE = f"https://github.com/{OWNER_REPO}/releases/download"


def api_headers():
    headers = {"Accept": "application/vnd.github+json"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def http_get(url, headers=None):
    last_error = None
    for _attempt in range(2):
        try:
            request = urllib.request.Request(url, headers=headers or {})
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last_error = exc
    raise last_error


def resolve_latest_tag():
    payload = json.loads(http_get(RELEASES_API, headers=api_headers()).decode("utf-8"))
    if not isinstance(payload, list):
        print("Failed to list releases from GitHub API.", file=sys.stderr)
        sys.exit(1)
    candidates = [
        release
        for release in payload
        if not release.get("draft") and (release.get("tag_name") or "").startswith("lumen-crash-v")
    ]
    if not candidates:
        print("No non-draft lumen-crash-v* release found.", file=sys.stderr)
        sys.exit(1)
    latest = max(
        candidates,
        key=lambda release: release.get("published_at") or release.get("created_at") or "",
    )
    return latest["tag_name"]


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def validate_checksums(downloaded, checksums_path):
    checksums = {}
    for line in checksums_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) >= 2:
            checksums[pathlib.Path(parts[-1]).name] = parts[0].lower()
    for path in downloaded:
        expected = checksums.get(path.name)
        if expected is None:
            print(f"warning: no checksum entry for {path.name}", file=sys.stderr)
        elif sha256_hex(path.read_bytes()) != expected:
            print(f"warning: sha256 mismatch for {path.name}", file=sys.stderr)


def stage_release(tag_name):
    version = tag_name.removeprefix("lumen-crash-v")
    downloaded = []
    for artifact in ARTIFACTS:
        for suffix in SUFFIXES:
            filename = f"{artifact}-{version}{suffix}"
            dest = LOCAL_MAVEN / "com" / "chloemlla" / "lumen" / artifact / version / filename
            if dest.is_file():
                downloaded.append(dest)
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            url = f"{DOWNLOAD_BASE}/{tag_name}/{filename}"
            print(f"Downloading {filename}")
            dest.write_bytes(http_get(url))
            downloaded.append(dest)

    checksums_dest = LOCAL_MAVEN / "checksums.txt"
    if not checksums_dest.is_file():
        checksums_dest.write_bytes(http_get(f"{DOWNLOAD_BASE}/{tag_name}/checksums.txt"))
    validate_checksums(downloaded, checksums_dest)
    return version


def main():
    parser = argparse.ArgumentParser(description="Resolve and stage the lumen-crash SDK release.")
    parser.add_argument(
        "--print-version",
        action="store_true",
        help="print only the resolved version to stdout",
    )
    parser.add_argument(
        "--version",
        metavar="X.Y.Z-shortSha",
        help="pin an explicit version instead of resolving the latest release",
    )
    args = parser.parse_args()

    tag_name = resolve_latest_tag() if args.version is None else f"lumen-crash-v{args.version}"
    version = tag_name.removeprefix("lumen-crash-v")

    if args.print_version:
        print(version)
        return

    stage_release(tag_name)
    RESOLVED_VERSION_FILE.write_text(version + "\n", encoding="utf-8")
    print(f"LUMEN_CRASH_VERSION={version}")


if __name__ == "__main__":
    main()
