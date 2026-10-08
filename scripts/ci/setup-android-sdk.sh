#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root" ]]; then
  echo "Android SDK directory is missing; check ANDROID_HOME on the runner." >&2
  exit 1
fi

# GitHub's runner has SDK tools installed, but sdkmanager need not be on PATH.
sdkmanager_path="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager_path" && -d "$sdk_root/cmdline-tools" ]]; then
  sdkmanager_path=$(find "$sdk_root/cmdline-tools" -type f -path '*/bin/sdkmanager' | sort -V | tail -n 1)
fi
if [[ ! -x "$sdkmanager_path" ]]; then
  echo "No executable sdkmanager found in $sdk_root/cmdline-tools." >&2
  exit 1
fi

"$sdkmanager_path" --sdk_root="$sdk_root" \
  "platform-tools" "platforms;android-37" "build-tools;36.0.0" "cmake;3.22.1"
