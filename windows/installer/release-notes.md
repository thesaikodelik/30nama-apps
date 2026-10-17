<div dir="rtl">

<!--
  Template for the notes of a Windows release (gh release create ... --notes-file).
  Before publishing: replace {VERSION} and {SHA256} (build-release.ps1 prints both), and write
  the "what's new" lines from CHANGELOG.md in plain Persian.
-->

## ۳۰نما برای ویندوز {VERSION}

برنامهٔ غیررسمی ۳۰نما برای ویندوز ۱۰ و ۱۱ (۶۴ بیتی)، با همان ظاهر برنامهٔ مک.

### تازه‌ها

- …

### نصب

۱. فایل `30nama-windows-{VERSION}-setup.exe` را از پایین همین صفحه دانلود کنید و اجرا کنید.
۲. چون برنامه گواهی امضای ویندوز ندارد، ویندوز بار اول پنجرهٔ آبی «Windows protected your PC» نشان می‌دهد. روی **More info** بزنید و بعد **Run anyway**. این فقط بار اول است.
۳. نصب برای حساب خود شما انجام می‌شود و رمز مدیر سیستم نمی‌خواهد. برنامه در منوی Start با نام 30nama پیدا می‌شود.

نسخه‌های بعد از داخل خود برنامه نصب می‌شوند: وقتی نسخهٔ تازه بیاید، زیر نوار بالا خبر می‌دهد.

### مطمئن شدن از فایل

SHA-256 فایل نصب:

```
{SHA256}
```

در PowerShell با این دستور می‌شود دید: `Get-FileHash .\30nama-windows-{VERSION}-setup.exe`

فایل `.sig` کنار آن امضای همین فایل است. به‌روزرسانی داخل برنامه فقط فایلی را نصب می‌کند که با این امضا بخواند.

</div>
