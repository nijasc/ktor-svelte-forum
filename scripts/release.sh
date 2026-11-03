#!/usr/bin/env bash
# Usage: scripts/release.sh 0.2.0
# Sets the version, commits it, tags it and pushes. The Release workflow does the rest.
set -euo pipefail

version="${1:?Usage: scripts/release.sh <version>}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || { echo "Not a valid version: $version" >&2; exit 1; }

cd "$(dirname "$0")/.."
[[ "$(git branch --show-current)" == "main" ]] || { echo "Release from main." >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first." >&2; exit 1; }
git rev-parse "v$version" >/dev/null 2>&1 && { echo "Tag v$version already exists." >&2; exit 1; }

sed -i "s/^version=.*/version=$version/" gradle.properties
git commit -am "Release $version"
git tag -a "v$version" -m "Release $version"
git push origin main "v$version"
