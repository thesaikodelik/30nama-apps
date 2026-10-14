#!/bin/zsh
# Packs a built 30nama.app into the disk image people download: the app and a link to
# Applications side by side over a background that says what to do, the app's icon on the disk
# itself, and the licences in a folder Finder keeps out of sight.
# Run from the repository root: Tools/make-dmg.sh <path to 30nama.app> <output .dmg>
set -euo pipefail
APP=$1
OUT=$2
NAME=30nama
work=$(mktemp -d)
trap 'hdiutil detach "/Volumes/$NAME" -quiet 2>/dev/null || true; rm -rf "$work"' EXIT

swift Tools/dmg-background.swift "$work"
tiffutil -cathidpicheck "$work/background.png" "$work/background@2x.png" -out "$work/background.tiff" 2>/dev/null

stage="$work/stage"
mkdir -p "$stage/.background" "$stage/Licenses"
ditto "$APP" "$stage/$NAME.app"
ln -s /Applications "$stage/Applications"
cp "$work/background.tiff" "$stage/.background/background.tiff"
cp LICENSE "$stage/Licenses/LICENSE.txt"
cp THIRD-PARTY-NOTICES.md "$stage/Licenses/THIRD-PARTY-NOTICES.md"

hdiutil detach "/Volumes/$NAME" -quiet 2>/dev/null || true
# Another disk of the same name would take the new one's place under /Volumes.
[[ ! -e "/Volumes/$NAME" ]] || { echo "/Volumes/$NAME is in use" >&2; exit 1; }
hdiutil create -srcfolder "$stage" -volname "$NAME" -fs HFS+ -format UDRW -size 300m "$work/rw.dmg" -quiet
hdiutil attach "$work/rw.dmg" -readwrite -noverify -noautoopen -quiet
volume="/Volumes/$NAME"
chflags hidden "$volume/Licenses"

# Finder keeps a window's look in the folder's .DS_Store; asking Finder to arrange it writes one.
osascript <<APPLESCRIPT
tell application "Finder"
    tell disk "$NAME"
        open
        set current view of container window to icon view
        set toolbar visible of container window to false
        set statusbar visible of container window to false
        set the bounds of container window to {200, 120, 860, 568}
        set options to the icon view options of container window
        set arrangement of options to not arranged
        set icon size of options to 120
        set text size of options to 13
        set background picture of options to file ".background:background.tiff"
        set position of item "$NAME.app" of container window to {170, 200}
        set position of item "Applications" of container window to {490, 200}
        update without registering applications
        delay 2
        close
    end tell
end tell
APPLESCRIPT

# The disk's own icon, the app's, for the desktop and the Finder sidebar. Set last: Finder
# arranging the window removes it, and from the source folder it does not reach the image.
cp "$APP/Contents/Resources/AppIcon.icns" "$volume/.VolumeIcon.icns"
SetFile -a C "$volume"

sync
hdiutil detach "$volume" -quiet
rm -f "$OUT"
hdiutil convert "$work/rw.dmg" -format UDZO -imagekey zlib-level=9 -o "$OUT" -quiet
echo "made $OUT"
