#!/usr/bin/env bash
# scripts/check-workflow.sh
#
# Structural guard for the GitHub Actions release workflow. Catches
# accidental deletion / rename / trigger drift in CI.

set -euo pipefail

WF=".github/workflows/release.yml"
[[ -f "${WF}" ]] || { echo "missing ${WF}"; exit 1; }

grep -q "actions/checkout@v4" "${WF}"      || { echo "missing actions/checkout@v4"; exit 1; }
grep -q "actions/setup-java@v4" "${WF}"    || { echo "missing actions/setup-java@v4"; exit 1; }
grep -q "softprops/action-gh-release" "${WF}" || { echo "missing softprops/action-gh-release"; exit 1; }
grep -q "branches: \[main\]" "${WF}"         || { echo "release workflow must trigger on push to main"; exit 1; }
grep -q "app-release.apk" "${WF}"           || { echo "release workflow must upload app-release.apk"; exit 1; }

echo "check-workflow: OK"