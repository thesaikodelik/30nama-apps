# Third-party notices

## 30nama-tizenos

This app was written from the Tizen client
[msharifi99/30nama-tizenos](https://github.com/msharifi99/30nama-tizenos) and follows its use of
the 30nama API. That client is under the MIT License:

```
MIT License

Copyright (c) 2026 Rebin Sharifi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 30nama logo

`Tools/30nama-logo.png`, and the app icon, Top Shelf images and opening title drawn from it, show
the 30nama logo. It was enlarged from two files served by 30nama.com: the cube from `safari-pinned-tab.svg`,
the wordmark from the logo image on the site's loading page. The name and logo belong to 30nama.
They are used only to show which service the app connects to; this app is not made, endorsed or
approved by 30nama.

## Vazirmatn

The fonts in `30nama/Shared/Fonts` are [Vazirmatn](https://github.com/rastikerdar/vazirmatn),
licensed under the SIL Open Font License 1.1. The licence text is in
`30nama/Shared/Fonts/Vazirmatn-OFL.txt`. The Windows installer carries two of them for its
Persian and Arabic pages.

Vazirmatn is the work of Saber Rastikerdar, who made it and gave it to everyone, free, and who
has since passed away. Every Persian word in these apps is set in his letters. With gratitude,
and in his memory.

<div dir="rtl">

وزیرمتن کار صابر راستی‌کردار است که آن را ساخت و رایگان در اختیار همه گذاشت، و دیگر در میان ما نیست. هر کلمهٔ فارسی این برنامه‌ها با حروف او نوشته شده است. با سپاس، و به یاد او.

</div>

## Inno Setup's Persian messages (Windows installer)

`windows/installer/Farsi.isl` is the user-contributed Persian translation of Inno Setup's
messages, by Peyman Mohammadi ([inno-setup-translations](https://github.com/peymanr34/inno-setup-translations)),
from Inno Setup's own repository.

## mpv and the libraries it is built on (Mac app only)

The Mac app plays files the system cannot read (Matroska among them) with
[mpv](https://mpv.io), taken as prebuilt libraries from
[MPVKit](https://github.com/mpvkit/MPVKit), version 1.1.0-n9.0.2, in its LGPL build (the
`MPVKit` product, not `MPVKit-GPL`). Xcode fetches them when the `30nama-mac` target is built;
none of them is stored in this repository.

- mpv and FFmpeg are under the GNU Lesser General Public License, version 2.1 or later. So are
  GnuTLS and libbluray. Nettle and GMP are under the LGPL version 3 or, at your choice, the GPL
  version 2.
- libass, libplacebo, dav1d, FreeType, HarfBuzz, FriBidi, uchardet, Little CMS, LuaJIT, OpenSSL,
  MoltenVK, shaderc, libdovi and uavs3d come with it under their own permissive licences (ISC,
  BSD, MIT, FTL, Apache 2.0 and similar).

These libraries are linked into the app. The LGPL asks that whoever receives the app can replace
them with a changed version: this app's whole source is here under the MIT License, so it can be
rebuilt against any build of them. Their own sources and licence texts are at the links above and
in MPVKit's repository.

## mpv and the libraries it is built on (Windows app only)

The Windows app plays video with [mpv](https://mpv.io), through `libmpv-2.dll` taken prebuilt
from [shinchiro/mpv-winbuild-cmake](https://github.com/shinchiro/mpv-winbuild-cmake), the build
`mpv-dev-x86_64-20261017-git-dde0671072` (named and checked by SHA-256 in
`windows/tools/fetch-libmpv.ps1`). It is not stored in this repository; it ships inside the
Windows installer.

That build is configured with mpv's default GPL options and an FFmpeg built with `--enable-gpl
--enable-version3` (with x264 and x265 among others), so the library as a whole is under the GNU
General Public License, version 3. The app's own code is under the MIT License, which allows it to
be combined with GPL code; the Windows app as distributed is therefore under GPL version 3 as a
whole. The recipe the library was built from is in shinchiro's repository at that date, and the
sources of mpv, FFmpeg and the other libraries are at their own projects.

## Windows App SDK and WebView2

The Windows installer carries Microsoft's Windows App SDK (MIT License) and the WebView2 SDK
(Microsoft's BSD-style licence), both from NuGet. The trailer player uses the WebView2 runtime
that comes with Windows.

## Subtitle site

When 30nama has no subtitle of its own for a download, the Mac app reads the public pages of the
subtitle site 30nama itself links to (subsource.net) to find and fetch one. The app is not
affiliated with that site, and the subtitles belong to the people who made them.
