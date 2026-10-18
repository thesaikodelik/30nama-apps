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

[Code]
// Files are replaced one by one, and Inno Setup cannot put back a file it has already replaced. A
// file held (the app still closing, a program holding it shut to others) used to stop it halfway,
// with new and old files mixed and the new version number on them. So every file is checked before
// any is touched, and one still held after a wait leaves the copy as it was. Restart Manager is no
// help here: it closes all the programs using the files or none, and an antivirus it cannot close
// (Norton, on the computer this was tried on) makes it close none.

// The file cannot be opened for writing even while sharing it with everyone: it is a running
// program, or another program holds it shut. Antivirus handles, which share, do not count.
function Held(const Path: String): Boolean;
var
  Stream: TFileStream;
begin
  Result := False;
  try
    Stream := TFileStream.Create(Path, fmOpenReadWrite or fmShareDenyNone);
    Stream.Free;
  except
    Result := True;
  end;
end;

// The first file under Folder that is held, or ''.
function FirstHeld(const Folder: String): String;
var
  Found: TFindRec;
begin
  Result := '';
  if not FindFirst(AddBackslash(Folder) + '*', Found) then Exit;
  try
    repeat
      if (Found.Name <> '.') and (Found.Name <> '..') then
      begin
        if Found.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0 then
          Result := FirstHeld(AddBackslash(Folder) + Found.Name)
        else if Held(AddBackslash(Folder) + Found.Name) then
          Result := AddBackslash(Folder) + Found.Name;
      end;
    until (Result <> '') or not FindNext(Found);
  finally
    FindClose(Found);
  end;
end;

// A message here stops Setup before anything is changed.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Folder, Blocked: String;
  Tries, Code: Integer;
begin
  Result := '';
  Folder := ExpandConstant('{app}');
  if not DirExists(Folder) then Exit;
  // Fifteen seconds: the app quitting for the update lets go in that time.
  for Tries := 1 to 30 do
  begin
    Blocked := FirstHeld(Folder);
    if Blocked = '' then Exit;
    Sleep(500);
  end;
  Log('Not installed, nothing was replaced: ' + Blocked + ' is in use');
  Result := 'Close 30nama (also beside the clock) and run Setup again. Nothing was changed. In use: ' + Blocked;
  // The updater quit for this; the old version opens again, and tells of the failure itself.
  if WizardSilent then
    ExecAsOriginalUser(ExpandConstant('{app}\Win - 30nama.exe'), '', Folder, SW_SHOWNORMAL, ewNoWait, Code);
end;
