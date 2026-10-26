#!/usr/bin/env bash
# The other half of the debug app's "note for Claude" (src/debug/.../Notes.kt). Follows the
# headset's log: when a note starts (left stick click) it takes the headset's screenshot at once,
# before the panel is up, and puts it next to the note on the headset; when the note is sent it
# copies both to quest/build/notes/<id>/ and prints the text.
#
#   quest/tools/notes.sh          # watch until stopped
#   quest/tools/notes.sh pull     # copy every note on the headset, and stop
set -euo pipefail
# Git Bash on Windows would turn the headset's paths into Windows ones.
export MSYS_NO_PATHCONV=1
# D:/… rather than /d/… on Windows, which adb.exe reads.
OUT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))/build/notes"
DEV=/sdcard/Android/data/app.nama.quest/files/notes
mkdir -p "$OUT"

text() { python -I -X utf8 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["text"])' "$1"; }

if [[ "${1:-}" == "pull" ]]; then
  adb pull "$DEV/." "$OUT" >/dev/null 2>&1 || true
  for n in "$OUT"/*/note.json; do [[ -f "$n" ]] && echo "$(basename "$(dirname "$n")"): $(text "$n")"; done
  exit 0
fi

echo "watching for notes; they go to $OUT"
adb logcat -T 1 -s Nama:I | while read -r line; do
  # Not with tr in the pipe: it would hold the lines back until its buffer fills.
  line="${line%$'\r'}"
  case "$line" in
    *note-start*)
      id="${line##*note-start }"
      mkdir -p "$OUT/$id"
      # </dev/null: adb would otherwise read, and eat, the log lines still coming.
      adb exec-out screencap -p > "$OUT/$id/shot.png" </dev/null
      adb shell mkdir -p "$DEV/$id" </dev/null
      adb push "$OUT/$id/shot.png" "$DEV/$id/shot.png" >/dev/null 2>&1 </dev/null
      echo "note $id: picture taken"
      ;;
    *note-saved*)
      id="${line##*note-saved }"
      adb pull "$DEV/$id/note.json" "$OUT/$id/note.json" >/dev/null 2>&1 </dev/null
      echo "NOTE $id: $(text "$OUT/$id/note.json")"
      ;;
    *note-cancel*)
      echo "note ${line##*note-cancel }: cancelled"
      ;;
  esac
done
