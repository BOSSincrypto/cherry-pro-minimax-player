#!/usr/bin/env bash
# scripts/check-apk-size.sh
#
# Fails the CI build if the release APK exceeds the configured budget.
# The budget is intentionally generous (14 MB) — v1's Media3 + Compose
# stack should land well under 10 MB with R8.

set -euo pipefail

BUDGET_MB="${APK_BUDGET_MB:-14}"

APK="$(find app/build/outputs/apk/release -name '*.apk' | head -n1)"
if [[ -z "${APK}" ]]; then
  echo "check-apk-size: no release APK found under app/build/outputs/apk/release"
  exit 1
fi

SIZE_MB="$(du -m "${APK}" | cut -f1)"
echo "check-apk-size: ${APK} is ${SIZE_MB} MB (budget ${BUDGET_MB} MB)"

if [[ "${SIZE_MB}" -gt "${BUDGET_MB}" ]]; then
  echo "check-apk-size: APK exceeds ${BUDGET_MB} MB budget"
  exit 1
fi

echo "check-apk-size: OK"