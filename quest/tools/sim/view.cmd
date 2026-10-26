@echo off
rem Double-click: the magazines on the computer, in the browser (see tools/sim/view).
rem view.cmd --device: with the headset's own covers, pages and screen (adb on the PATH).
cd /d "%~dp0..\.."
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
call gradlew.bat -q :app:testDebugUnitTest -Pviewer --tests app.nama.quest.SceneExport || goto :eof
python tools\blender\sim_textures.py build\viewer
copy /y app\src\main\assets\models\*.glb build\viewer\ >nul
copy /y tools\sim\viewer.html build\viewer\ >nul
del /q build\viewer\art.json build\viewer\m*-p*.jpg build\viewer\screen.jpg 2>nul
if /i "%~1"=="--device" call :device
start "" http://localhost:8765/viewer.html
cd build\viewer
python -m http.server 8765
goto :eof

rem The headset (a debug build) prints them once started with "--ez dump true"; then they are pulled.
:device
set D=/sdcard/Android/data/app.nama.quest/files/scene/art
adb shell rm -rf %D%
adb shell am force-stop app.nama.quest
adb shell am start -n app.nama.quest/.NamaActivity --ez dump true >nul
echo Waiting for the headset's pictures...
:wait
timeout /t 2 /nobreak >nul
adb shell ls %D%/art.json >nul 2>&1 || goto wait
adb pull %D%/. build\viewer\ >nul
goto :eof
