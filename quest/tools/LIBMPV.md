<div dir="rtl">

# libmpv برای کوئست

برنامهٔ کوئست ویدئو را با libmpv پخش می‌کند. libmpv و FFmpeg و بقیهٔ کتابخانه‌هایش وارد ریپو نمی‌شوند. اسکریپت `build-libmpv.sh` همه را از منبع می‌سازد و در پوشه‌های برنامه می‌گذارد. این پرونده می‌گوید چه چیزی، با چه نسخه و چه پرچم‌هایی ساخته می‌شود و مجوز هر کدام چیست.

## ساختن

روی لینوکس یا WSL (روی ویندوز توزیع Ubuntu)، بدون دسترسی root:

<div dir="ltr">

```
wsl -d Ubuntu
bash /mnt/d/AI_Projects/30nama/quest/tools/build-libmpv.sh           # only what is missing
bash /mnt/d/AI_Projects/30nama/quest/tools/build-libmpv.sh --clean   # everything from scratch
```

</div>

- پوشهٔ کار `~/nama-mpv` است (با `NAMA_MPV_WORK` عوض می‌شود). روی فایل‌سیستم خود لینوکس بماند، نه زیر `/mnt`؛ آن‌جا ساختن چند برابر کندتر است.
- ابزارهایی که لازم است و روی سیستم نیست (meson، ninja، pkgconf، gperf) را خود اسکریپت می‌گیرد و در پوشهٔ کار می‌سازد. از سیستم فقط این‌ها لازم است: `curl tar xz bzip2 unzip make gcc g++ python3 perl git`.
- هر فایلی که دانلود می‌شود SHA-256 ثابت دارد و اگر جور نباشد ساختن می‌ایستد. libplacebo از git گرفته می‌شود و commit آن ثابت است.
- ساختن کامل روی یک پردازندهٔ ۲۴ هسته‌ای حدود سه دقیقه است (بدون زمان دانلود).
- آخر کار، کتابخانه‌ها (strip شده) در `quest/app/src/main/jniLibs/arm64-v8a/` و سرفایل‌های mpv در `quest/app/src/main/cpp/include/mpv/` گذاشته می‌شوند. هر دو در `quest/.gitignore` هستند. نسخهٔ strip نشده برای دیباگ در `~/nama-mpv/prefix/arm64/lib` می‌ماند.
- اسکریپت در پایان این‌ها را می‌سنجد و اگر درست نباشد خطا می‌دهد: FFmpeg با مجوز LGPL پیکربندی شده، mpv با `HAVE_GPL 0` ساخته شده، هر کتابخانه‌ای که libmpv لازم دارد یا در APK هست یا خود اندروید دارد، و در هیچ فایلی مسیر پوشهٔ کاربر یا نام کاربری نیامده است. بعد SHA-256 و اندازهٔ هر فایل را چاپ می‌کند.

بعد از ساختن، `./gradlew :app:assembleDebug` از داخل `quest/` کتابخانهٔ JNI خودمان (`libnamampv.so`، از `app/src/main/cpp/`) را با CMake می‌سازد و همه را در APK می‌گذارد. اگر libmpv ساخته نشده باشد، CMake با پیغامی می‌ایستد که همین اسکریپت را نام می‌برد.

## نسخه‌ها

| جزء | نسخه | از کجا |
|---|---|---|
| Android NDK | r28c (28.2.13676358) | همان `ndkVersion` برنامه، تا libc++ یکی باشد |
| سطح API | 34 | Horizon OS اندروید ۱۴ است و `minSdk` هم ۳۴ است |
| mpv | 0.41.0 | تگ رسمی |
| FFmpeg | 8.1.3 | ffmpeg.org/releases |
| mbedTLS | 3.6.7 | برای https و HLS در FFmpeg |
| dav1d | 1.5.4 | AV1 نرم‌افزاری |
| libxml2 | 2.15.4 | برای fontconfig و DASH در FFmpeg |
| libplacebo | v7.360.1 (commit `cee9b076`) | لازمهٔ `vo=gpu` در mpv 0.41 |
| libass | 0.17.5 | زیرنویس (mpv بدون آن ساخته نمی‌شود) |
| freetype | 2.15.0 | |
| fribidi | 1.0.17 | |
| harfbuzz | 14.6.0 | |
| fontconfig | 2.18.3 | |
| libunibreak | 8.0 | |
| ابزار: meson، ninja، pkgconf، gperf | 1.12.1، 1.13.2، 2.5.1، 3.3 | فقط برای ساختن، داخل APK نمی‌روند |

نسخه‌های وابستگی‌ها همان‌هایی است که mpv-android در اکتبر ۲۰۲۶ به کار می‌برد. mpv-android روی شاخهٔ اصلی mpv و FFmpeg 9 می‌سازد؛ ما تگ رسمی mpv 0.41.0 را گرفتیم و FFmpeg 8.1 را که با آن سازگار است.

## پرچم‌ها

**mpv:** `-Dgpl=false -Dlibmpv=true -Dcplayer=false -Dbuild-date=false`، با `android-media-ndk`، `egl-android`، `gl`، `audiotrack`، `aaudio` و `opensles` روشن، و `lua`، `javascript`، `vulkan`، `libarchive`، `uchardet`، `rubberband`، `zimg`، `lcms2`، `libbluray`، `dvdnav` و `cdda` خاموش. فهرست قابلیت‌های ساخت نهایی (همان که داخل libmpv نوشته می‌شود) `gpl` ندارد:

<div dir="ltr">

```
aaudio android android-media-ndk audiotrack cplugins egl-android ffmpeg gl glibc-thread-name glob
glob-posix iconv libass libavdevice libdl libplacebo linux-fstatfs memrchr opensles posix ppoll
pthread-condattr-setclock vector vt.h zlib
```

</div>

چیزهایی که برنامه لازم دارد در حالت LGPL هم هستند. پرونده‌های `vo_mediacodec_embed.c`، `opengl/context_android.c`، `android_common.c`، `hwdec_aimagereader.c`، `vo_gpu.c` و خروجی‌های صدای اندروید همه سرآیند LGPL دارند، و در `meson.build` هیچ‌کدام پشت `get_option('gpl')` نیستند. چیزهایی که `-Dgpl=false` خاموش می‌کند (X11، OSS، jack، DVD، CDDA، DVB، caca، direct3d) روی اندروید به کار نمی‌آیند.

**FFmpeg:** بدون `--enable-gpl` و بدون `--enable-nonfree`. با `--enable-version3`، چون FFmpeg استفاده از mbedTLS (Apache-2.0) را فقط با مجوز نسخهٔ ۳ می‌پذیرد. `--enable-jni --enable-mediacodec --enable-mbedtls --enable-libdav1d --enable-libxml2 --disable-vulkan --enable-shared --disable-static --disable-programs --disable-doc --disable-debug --disable-muxers --disable-encoders --disable-devices`، و فقط رمزگذارهای mjpeg و png و muxerهای mov، matroska و mpegts برای فرمان‌های screenshot و dump-cache. رشتهٔ مجوزی که `avutil_license()` برمی‌گرداند: `LGPL version 3 or later`.

**همه:** `-Wl,-z,max-page-size=16384` برای صفحه‌های ۱۶ کیلوبایتی. مسیرهایی که داخل فایل‌ها نوشته می‌شوند (رشتهٔ پیکربندی FFmpeg و mpv، `__FILE__`) نسبی‌اند یا با `-ffile-prefix-map` پاک می‌شوند، تا نام کاربری که ساخته داخل APK نیاید.

## فایل‌های خروجی

همه برای arm64-v8a. اندازه‌ها بعد از strip است:

<div dir="ltr">

| file | size | contains |
|---|---|---|
| libmpv.so | 5.8 MB | mpv, with libplacebo, libass, freetype, fribidi, harfbuzz, fontconfig, libxml2, libunibreak linked in |
| libavcodec.so | 11.7 MB | with dav1d |
| libavformat.so | 3.2 MB | with mbedTLS, libxml2 |
| libavfilter.so | 3.8 MB | |
| libavutil.so | 715 KB | |
| libswscale.so | 1.1 MB | |
| libswresample.so | 95 KB | |
| libavdevice.so | 8 KB | empty (devices off) but libmpv links it |
| libc++_shared.so | 1.2 MB | from the NDK; libplacebo is C++ |

</div>

SHA-256 هر فایل را اسکریپت در پایان چاپ می‌کند. ساخت از صفر دو بار پشت هم همین خروجی‌ها را بایت به بایت داد؛ با همان نسخه‌ها و همان NDK باید یکی باشند؛ اگر نبودند، اول نسخهٔ ابزارهای میزبان (gcc برای pkgconf و gperf) را مقایسه کنید.

## مجوزها

برنامه با libmpv و FFmpeg پویا پیوند می‌خورد: هر کدام یک فایل `.so` جداست که کاربر می‌تواند با ساخت خودش جایگزین کند. این همان چیزی است که LGPL می‌خواهد. بقیهٔ کتابخانه‌ها ایستا داخل یکی از این فایل‌ها هستند.

| جزء | مجوز | کجاست |
|---|---|---|
| mpv | LGPL-2.1-or-later (با `-Dgpl=false`) | libmpv.so |
| FFmpeg | LGPL-3.0-or-later (به خاطر `--enable-version3`) | libav*.so، libsw*.so |
| libplacebo | LGPL-2.1-or-later | داخل libmpv.so |
| fribidi | LGPL-2.1-or-later | داخل libmpv.so |
| libass | ISC | داخل libmpv.so |
| harfbuzz | MIT | داخل libmpv.so |
| fontconfig | MIT (شبه HPND) | داخل libmpv.so |
| freetype | FreeType License (FTL)، که از دو مجوزش انتخاب شده | داخل libmpv.so |
| libxml2 | MIT | داخل libmpv.so و libavformat.so |
| libunibreak | zlib | داخل libmpv.so |
| mbedTLS | Apache-2.0 (از دو مجوز Apache-2.0 یا GPL-2.0-or-later) | داخل libavformat.so |
| dav1d | BSD-2-Clause | داخل libavcodec.so |
| libc++ (NDK) | Apache-2.0 with LLVM exception | libc++_shared.so |

نتیجه: هیچ جزء GPL در کار نیست و مجموعه زیر LGPL نسخهٔ ۳ است (mpv و libplacebo و fribidi «نسخهٔ ۲٫۱ یا بالاتر»اند و با FFmpeg نسخهٔ ۳ جمع می‌شوند).

پیش از اولین ریلیز:
- متن مجوز هر جزء در THIRD-PARTY-NOTICES.md و داخل برنامه بیاید. FTL می‌خواهد در مستندات نام FreeType Project آمده باشد.
- LGPL می‌خواهد منبع همان نسخه‌ها در دسترس باشد. اسکریپت منبع‌ها را از جای اصلی‌شان با SHA-256 ثابت می‌گیرد؛ کنار هر ریلیز کوئست یک آرشیو از `~/nama-mpv/downloads` (همهٔ منبع‌ها) و همین اسکریپت گذاشته شود، مثل آرشیو mpv نسخهٔ ویندوز.

## محدودیت‌های شناخته‌شده

- با `vo=mediacodec_embed` تصویر مستقیم از MediaCodec به Surface می‌رود و mpv نمی‌تواند زیرنویس روی آن بکشد. زیرنویس متنی از ویژگی `sub-text` گرفته و روی پنل جدای جلوی پرده نشان داده می‌شود. زیرنویس تصویری (PGS بلوری، DVD) در این حالت نشان داده نمی‌شود؛ در حالت نرم‌افزاری (`vo=gpu`) خود mpv آن را می‌کشد.
- mpv بدون uchardet ساخته شده و iconv اندروید فقط UTF-8 و UTF-16 و UTF-32 را می‌شناسد. زیرنویس فارسی با کدگذاری Windows-1256 را باید خود برنامه پیش از دادن به mpv به UTF-8 برگرداند.
- mbedTLS در FFmpeg گواهی‌های اندروید را نمی‌شناسد و mpv به‌طور پیش‌فرض گواهی سرور را نمی‌سنجد (`tls-verify=no`). برای سنجیدن، باید از پوشهٔ گواهی‌های سیستم یک فایل PEM ساخت و به `tls-ca-file` داد.

</div>
