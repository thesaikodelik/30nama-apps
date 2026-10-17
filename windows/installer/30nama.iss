; The Windows installer, built by windows/tools/build-release.ps1 with Inno Setup 6:
;   ISCC /DVersion=1.0.0 /DSource=<app build folder> /DRepo=<repository root> /DOutput=<folder> 30nama.iss
;
; It installs for the current user only, under %LOCALAPPDATA%\Programs, so it needs no
; administrator and the in-app updater can run it quietly over the copy that is running:
;   <installer> /SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS

#ifndef Version
  #error Pass /DVersion=X.Y.Z
#endif

[Setup]
; Fixed for good: Windows knows the installed app by it, and an update by the same id replaces it.
AppId={{E5F6C2F8-7720-499E-A6E8-346E44AA90C2}
AppName=30nama
AppVersion={#Version}
AppVerName=30nama {#Version}
AppPublisher=thesaikodelik
AppPublisherURL=https://github.com/thesaikodelik/30nama-apps
AppSupportURL=https://github.com/thesaikodelik/30nama-apps/issues
AppUpdatesURL=https://github.com/thesaikodelik/30nama-apps/releases
VersionInfoVersion={#Version}
DefaultDirName={autopf}\30nama
DefaultGroupName=30nama
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.19041
WizardStyle=modern
SetupIconFile={#Repo}\windows\src\Nama.App\Assets\30nama.ico
UninstallDisplayIcon={app}\Win - 30nama.exe
UninstallDisplayName=30nama
LicenseFile={#Repo}\LICENSE
OutputDir={#Output}
OutputBaseFilename=30nama-windows-{#Version}-setup
Compression=lzma2/ultra64
SolidCompression=yes
; The app may be running, beside the clock; it is closed for the files to be replaced.
CloseApplications=force
RestartApplications=no

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#Source}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Repo}\LICENSE"; DestDir: "{app}"; DestName: "LICENSE.txt"; Flags: ignoreversion
Source: "{#Repo}\THIRD-PARTY-NOTICES.md"; DestDir: "{app}"; Flags: ignoreversion

[InstallDelete]
; What an older version left that this one no longer has.
Type: filesandordirs; Name: "{app}\*.pdb"

[Registry]
; The name the app's notifications go by (EpisodeWatch.AppId); the app writes it, the uninstaller takes it away.
Root: HKCU; Subkey: "Software\Classes\AppUserModelId\thesaikodelik.30nama"; Flags: uninsdeletekey

[Icons]
Name: "{autoprograms}\30nama"; Filename: "{app}\Win - 30nama.exe"
Name: "{autodesktop}\30nama"; Filename: "{app}\Win - 30nama.exe"; Tasks: desktopicon

[Run]
; Also after a quiet install by the updater, which is how the new version opens again.
Filename: "{app}\Win - 30nama.exe"; Description: "{cm:LaunchProgram,30nama}"; Flags: nowait postinstall
