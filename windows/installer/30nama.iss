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
; Dark, as the app is: its own pictures, the title bar of the same dark, no rules between parts.
WizardStyle=modern dark includetitlebar hidebevels
; The plain size, not the modern style's fifth larger: its pages say little, and the room went empty.
WizardSizePercent=100
WizardBackImageFile=art\back-596.png,art\back-796.png,art\back-994.png,art\back-1192.png,art\back-1490.png
WizardImageFile=art\side-202.png,art\side-269.png,art\side-336.png,art\side-404.png,art\side-505.png
WizardSmallImageFile=art\small-58.png,art\small-77.png,art\small-97.png,art\small-116.png,art\small-124.png,art\small-143.png,art\small-159.png
; The language is asked first, on a page of the installer's own ([Code], ChooseLanguage), with the
; language of Windows already chosen; Inno Setup's own dialog is a plain drop-down list.
ShowLanguageDialog=no
; The app's icon with an install badge (windows/tools/make-installer-art.py).
SetupIconFile={#Repo}\windows\installer\setup.ico
UninstallDisplayIcon={app}\Win - 30nama.exe
UninstallDisplayName=30nama
; No licence to accept: the MIT licence and the notices of the parts it ships with go beside the app.
; Welcome, where to (first install only), then straight to installing.
DisableWelcomePage=no
DisableReadyPage=yes
OutputDir={#Output}
OutputBaseFilename=30nama-windows-{#Version}-setup
Compression=lzma2/ultra64
SolidCompression=yes
; The app may be running, beside the clock; it is closed for the files to be replaced.
CloseApplications=force
RestartApplications=no

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
; Inno Setup's user-contributed Persian (Peyman Mohammadi, github.com/peymanr34/inno-setup-translations).
Name: "fa"; MessagesFile: "Farsi.isl"
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"

[LangOptions]
; Persian and Arabic in the app's own typeface, Vazirmatn, carried inside Setup and loaded for it alone.
; Vazirmatn is the work of the late Saber Rastikerdar (صابر راستی‌کردار), who gave it to everyone,
; free. With gratitude, and in his memory.
; Setup sizes its windows by how large the font measures against these. Vazirmatn measures about
; 1.4 times Segoe UI 9 (whose are 7 and 15), so with its own measures the Persian and Arabic
; windows are the size of the English, rather than 40% larger.
fa.DialogFontBaseScaleHeight=20
fa.DialogFontBaseScaleWidth=10
ar.DialogFontBaseScaleHeight=20
ar.DialogFontBaseScaleWidth=10
fa.DialogFontName=Vazirmatn
fa.WelcomeFontName=Vazirmatn
ar.DialogFontName=Vazirmatn
ar.WelcomeFontName=Vazirmatn

[Messages]
en.WelcomeLabel1=Welcome to 30nama
en.WelcomeLabel2=Films, series and anime from 30nama, in an app made for Windows. An unofficial app, not made by 30nama.%n%n[name/ver] will be installed for you alone: no administrator is needed.
fa.WelcomeLabel1=به ۳۰نما خوش آمدید
fa.WelcomeLabel2=فیلم، سریال و انیمهٔ ۳۰نما در برنامه‌ای برای ویندوز. این برنامه غیررسمی است و ساختهٔ خود ۳۰نما نیست.%n%n[name/ver] فقط برای حساب شما نصب می‌شود و دسترسی مدیر سیستم لازم نیست.
ar.WelcomeLabel1=مرحبًا بك في 30nama
ar.WelcomeLabel2=أفلام ومسلسلات وأنمي 30nama في تطبيق مصنوع لويندوز. تطبيق غير رسمي، لم تصنعه 30nama.%n%nسيُثبَّت [name/ver] لحسابك وحدك، دون الحاجة إلى صلاحيات المسؤول.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[CustomMessages]
; When a file of the installed app is still held open; %1 is the file's name.
; While Setup waits for the app to close, with a moving bar so it does not look stuck.
en.WaitingTitle=Waiting for 30nama to close
en.WaitingText=Close 30nama (its icon beside the clock too); installing then goes on by itself.
fa.WaitingTitle=در انتظار بسته شدن 30nama
fa.WaitingText=برنامهٔ 30nama را ببندید (آیکونش کنار ساعت را هم)؛ بعد نصب خودش ادامه پیدا می‌کند.
ar.WaitingTitle=بانتظار إغلاق 30nama
ar.WaitingText=أغلق 30nama (وأيقونته بجانب الساعة أيضًا)؛ ثم يتابع التثبيت تلقائيًا.
en.AppInUse=Close 30nama (also its icon beside the clock) and run Setup again. Nothing was changed.%n%nIn use: %1
fa.AppInUse=برنامهٔ 30nama را ببندید (آیکونش کنار ساعت را هم) و نصب‌کننده را دوباره اجرا کنید. چیزی تغییر نکرد.%n%nدر حال استفاده: %1
ar.AppInUse=أغلق 30nama (وأيقونته بجانب الساعة أيضًا) وشغّل برنامج التثبيت مرة أخرى. لم يتغيّر شيء.%n%nقيد الاستخدام: %1

[Files]
; For Setup itself, not installed: the typeface of its Persian and Arabic pages.
Source: "{#Repo}\30nama\Shared\Fonts\Vazirmatn-Regular.ttf"; Flags: dontcopy
Source: "{#Repo}\30nama\Shared\Fonts\Vazirmatn-Bold.ttf"; Flags: dontcopy
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
const
  FR_PRIVATE = $10;

function AddFontResourceEx(FileName: String; Flags: Cardinal; Reserved: Integer): Integer;
  external 'AddFontResourceExW@gdi32.dll stdcall';

var
  ChosenLanguage: String;

// Setup was started in a language already: it chose on its first run, or was told by the updater.
function LanguageGiven: Boolean;
var
  I: Integer;
begin
  Result := False;
  for I := 1 to ParamCount do
    if Pos('/LANG=', Uppercase(ParamStr(I))) = 1 then Result := True;
end;

procedure LanguageClick(Sender: TObject);
begin
  case TComponent(Sender).Tag of
    0: ChosenLanguage := 'fa';
    1: ChosenLanguage := 'en';
  else
    ChosenLanguage := 'ar';
  end;
end;

// The first page: three large buttons, each language in its own words, the language of Windows lit.
function ChooseLanguage: Boolean;
var
  Form: TSetupForm;
  Title: TNewStaticText;
  Button: TNewButton;
  Names: array[0..2] of String;
  Codes: array[0..2] of String;
  I: Integer;
begin
  Names[0] := 'فارسی'; Codes[0] := 'fa';
  Names[1] := 'English'; Codes[1] := 'en';
  Names[2] := 'العربية'; Codes[2] := 'ar';
  Form := CreateCustomForm(ScaleX(340), ScaleY(300), False, False);
  try
    Form.Caption := '30nama';
    Form.Position := poScreenCenter;
    Title := TNewStaticText.Create(Form);
    Title.Parent := Form;
    Title.Caption := 'زبان  ·  Language  ·  اللغة';
    Title.Font.Name := 'Vazirmatn';
    Title.Font.Size := 13;
    Title.Font.Style := [fsBold];
    Title.AutoSize := True;
    Title.Top := ScaleY(26);
    Title.Left := (Form.ClientWidth - Title.Width) div 2;
    for I := 0 to 2 do
    begin
      Button := TNewButton.Create(Form);
      Button.Parent := Form;
      Button.Caption := Names[I];
      Button.Tag := I;
      Button.Font.Name := 'Vazirmatn';
      Button.Font.Size := 12;
      if Codes[I] = ActiveLanguage then Button.Font.Style := [fsBold];
      Button.Default := Codes[I] = ActiveLanguage;
      Button.Width := ScaleX(260);
      Button.Height := ScaleY(46);
      Button.Left := (Form.ClientWidth - Button.Width) div 2;
      Button.Top := ScaleY(84) + I * ScaleY(60);
      Button.OnClick := @LanguageClick;
      Button.ModalResult := mrOk;
      if Button.Default then Form.ActiveControl := Button;
    end;
    ChosenLanguage := '';
    Result := Form.ShowModal = mrOk;
  finally
    Form.Free;
  end;
end;

// The installing page shows only how far it has got: the name of each file going by is no use to anyone.
procedure InitializeWizard;
begin
  WizardForm.FilenameLabel.Visible := False;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpInstalling then WizardForm.FilenameLabel.Visible := False;
end;

function InitializeSetup: Boolean;
var
  Code: Integer;
begin
  Result := True;
  ExtractTemporaryFile('Vazirmatn-Regular.ttf');
  ExtractTemporaryFile('Vazirmatn-Bold.ttf');
  AddFontResourceEx(ExpandConstant('{tmp}\Vazirmatn-Regular.ttf'), FR_PRIVATE, 0);
  AddFontResourceEx(ExpandConstant('{tmp}\Vazirmatn-Bold.ttf'), FR_PRIVATE, 0);
  // An update runs quietly, in the language it is given; nothing is asked.
  if WizardSilent or LanguageGiven then Exit;
  if not ChooseLanguage then
  begin
    Result := False;
    Exit;
  end;
  // Another language than this run's: Setup starts again in it, and this run ends.
  if ChosenLanguage <> ActiveLanguage then
  begin
    Exec(ExpandConstant('{srcexe}'), '/LANG=' + ChosenLanguage, '', SW_SHOWNORMAL, ewNoWait, Code);
    Result := False;
  end;
end;
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
  Tries, Step, Limit, Code: Integer;
  Waiting: TOutputMarqueeProgressWizardPage;
begin
  Result := '';
  Folder := ExpandConstant('{app}');
  if not DirExists(Folder) then Exit;
  Blocked := FirstHeld(Folder);
  if Blocked = '' then Exit;
  // Quietly, fifteen seconds: the app quitting for the update lets go in that time. Shown, two
  // minutes, with a moving bar and what to do, and Setup goes on by itself once the app is closed.
  Waiting := nil;
  Limit := 30;
  if not WizardSilent then
  begin
    Limit := 240;
    Waiting := CreateOutputMarqueeProgressPage(CustomMessage('WaitingTitle'), CustomMessage('WaitingText'));
    Waiting.Show;
  end;
  try
    for Tries := 1 to Limit do
    begin
      // Short sleeps, so the window keeps answering and the bar keeps moving.
      for Step := 1 to 10 do
      begin
        if Waiting <> nil then Waiting.Animate;
        Sleep(50);
      end;
      Blocked := FirstHeld(Folder);
      if Blocked = '' then Exit;
    end;
  finally
    if Waiting <> nil then Waiting.Hide;
  end;
  Log('Not installed, nothing was replaced: ' + Blocked + ' is in use');
  Result := FmtMessage(CustomMessage('AppInUse'), [ExtractFileName(Blocked)]);
  // The updater quit for this; the old version opens again, and tells of the failure itself.
  if WizardSilent then
    ExecAsOriginalUser(ExpandConstant('{app}\Win - 30nama.exe'), '', Folder, SW_SHOWNORMAL, ewNoWait, Code);
end;
