#!/usr/bin/env bash
# Sends changed models to the debug app running on the headset, which swaps them in about a
# second later (HotAssets in src/debug): no build, no install, the headset stays on.
#
#   quest/tools/hot-reload.sh                       # every model in app/src/main/assets/models
#   quest/tools/hot-reload.sh models/magazine_open.glb ...
#   quest/tools/hot-reload.sh --from <dir> models/magazine_open.glb   # a file not in the repo
#   quest/tools/hot-reload.sh --clear               # back to the apk's own (on the next start)
#
# Paths are relative to app/src/main/assets, as the app asks for them (apk:///models/…).
# What was sent stays until the next install or --clear.
set -euo pipefail
# Git Bash on Windows would turn the headset's paths into Windows ones.
export MSYS_NO_PATHCONV=1
PKG=app.nama.quest
DEST=/sdcard/Android/data/$PKG/files/hot
SRC="$(dirname "$0")/../app/src/main/assets"

if [[ "${1:-}" == "--clear" ]]; then
  adb shell rm -rf "$DEST"
  echo "cleared; the apk's own models come back when the app restarts"
  exit 0
fi
if [[ "${1:-}" == "--from" ]]; then
  SRC="$2"
  shift 2
fi
cd "$SRC"

files=("$@")
[[ ${#files[@]} -eq 0 ]] && files=(models/*)
for f in "${files[@]}"; do
  adb shell mkdir -p "$DEST/$(dirname "$f")"
  adb push "$f" "$DEST/$f" >/dev/null
  # Newer than the install, so the app keeps it when it restarts.
  adb shell touch "$DEST/$f"
  echo "sent $f"
done
adb shell am broadcast -a $PKG.HOT_RELOAD -p $PKG >/dev/null
echo "reload asked"
