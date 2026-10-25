<div dir="rtl">

# ۳۰نما برای متا کوئست

نسخهٔ کوئست ۳۰نما، با Kotlin و Meta Spatial SDK. فعلاً فقط فضای «اتاق من» را دارد: پنل‌ها و پردهٔ بزرگ در اتاق واقعی کاربر (passthrough) شناورند. قاعده‌های کار در بخش «نسخهٔ کوئست» در [AGENTS.md](../AGENTS.md) است، تحقیق در [docs/research.md](docs/research.md)، و کارهای باز در [TASKS.md](../TASKS.md).

## پوشه‌ها

- `core/`: منطق بدون رابط، برگردان `30nama/Core` به Kotlin خالص، با تست‌هایی که همان فیکسچرهای `Tests/NamaCoreTests` را می‌خوانند ([core/README.md](core/README.md))
- `app/`: خود برنامه
  - `NamaActivity.kt`: ثبت پنل‌ها و چرخهٔ صحنه
  - `Stage.kt`: جای هر پنل در اتاق (`Layout`)، حرکت نرم بین حالت‌ها، تاریک شدن اتاق هنگام پخش، دکمه‌های دسته
  - `AppModel.kt`: حالت برنامه (ورود، خانه، عنوان، پخش)
  - `data/`: کاتالوگ واقعی (`LiveCatalog`، روی `core`) و نمایشی (`DemoCatalog`)
  - `ui/`: پنل‌های Compose
  - `player/` و `cpp/`: پخش‌کنندهٔ mpv
- `tools/`: ساختن libmpv ([tools/LIBMPV.md](tools/LIBMPV.md)) و ساختن جدول رشته‌ها از `L10n.swift` (`gen-l10n.py`)

## ساختن

لازم: JDK 21، Android SDK با NDK `28.2.13676358`، و یک بار ساختن libmpv در WSL یا لینوکس:

<div dir="ltr">

```
wsl -d Ubuntu -- bash quest/tools/build-libmpv.sh   # once; fills app/src/main/jniLibs and cpp/include
cd quest
./gradlew :core:test
./gradlew :app:installDebug
```

</div>

`local.properties` با `sdk.dir` را خودتان بسازید؛ وارد ریپو نمی‌شود.

## دیدن پنل‌ها روی کامپیوتر

برای بیشتر تغییرها لازم نیست هدست را سر بگذارید. این دستور هر پنل (دیوار با همهٔ بخش‌ها و صفحه‌هایش، صفحهٔ عنوان فیلم و سریال، کنترل پخش، زیرنویس) را با همان اندازه‌ای که در هدست دارد، در هر سه زبان، به PNG تبدیل می‌کند:

<div dir="ltr">

```
./gradlew :app:testDebugUnitTest -Pshots                   # build/shots/{en,fa,ar}/*.png
./gradlew :app:testDebugUnitTest -Pshots -Pshots.lang=fa   # one language
./gradlew :app:testDebugUnitTest -Pshots -Pshots.trace     # also print every request it answered
```

</div>

- کار با Robolectric و Roborazzi روی خود JVM انجام می‌شود (`app/src/test/.../PanelShots.kt`). داده از فیکسچرهای مشترک `Tests/NamaCoreTests/Fixtures` می‌آید و از شبکه چیزی گرفته نمی‌شود. به‌جای پوسترها رنگ‌های ساختگی کشیده می‌شوند و حساب کاربری هم ساختگی است.
- چیزهایی که این عکس‌ها نشان نمی‌دهند: جای پنل‌ها در فضا و فاصله‌شان، پردهٔ ویدئو، اشاره با دسته و دست، کیبورد سیستم و تیرگی اتاق.
- شبیه‌ساز متا (Meta Spatial Simulator) برنامه‌هایی را که چند پنل و Spatial SDK دارند اجرا نمی‌کند؛ این ابزار جای آن است.

## آزمودن روی هدست

نسخهٔ debug چند ورودی دارد که هر حالت را مستقیم از adb باز می‌کند، مثل `NAMA_OPEN` در مک:

<div dir="ltr">

```
adb shell am start -n app.nama.quest/.NamaActivity --es place home|title|play|login --es lang en|fa|ar
adb shell am start -n app.nama.quest/.NamaActivity --es place play --ez demo true   # demo catalogue, no account
adb shell am start -n app.nama.quest/.NamaActivity --es section settings   # a wall section: search, movies, browse, profile…
adb shell am start -n app.nama.quest/.NamaActivity --es place play --ei row 0   # the first title of a home row (0: continue watching)
adb shell am start -n app.nama.quest/.NamaActivity --ez room false --ez gaze true --ef tilt 16   # captures: no room, laid out along the headset's gaze, aimed 16° up
adb exec-out screencap -p > shot.png    # both eyes, side by side; passthrough itself does not show
```

</div>

- اگر هدست روی سر نیست، برای بیدار نگه داشتنش: `adb shell am broadcast -a com.oculus.vrpowermanager.prox_close`. بعد از کار: `adb shell am broadcast -a com.oculus.vrpowermanager.automation_disable`.
- بدون ردیابی (هدست روی میز، اتاق تاریک)، سیستم پنجرهٔ «Finding position in room» را نشان می‌دهد و برنامه باز نمی‌شود. برای آزمایش موقت: `adb shell setprop debug.oculus.guardian_pause 1`، و بعد حتماً `0`. در این حالت برنامه پنل‌ها را دور یک چشم نشسته در ارتفاع ۱٫۲ متری می‌چیند؛ فاصله‌ها را فقط با هدست روی سر بسنجید.
- برای عکس از هدستی که روی میز است: `room false` اتاق کاربر را از عکس بیرون نگه می‌دارد، `gaze true` صحنه را رو به همان جهتی می‌چیند که هدست نگاه می‌کند (با کجی‌اش)، و `tilt` نگاه را چند درجه بالا می‌برد تا دیوار پوسترها هم در کادر بیاید. با حساب واقعی، پخش را زود ببندید: برنامه جای تماشا را به سرور می‌گوید.
- کار با دسته و دست را نمی‌شود از adb آزمود. هر تغییری در پنل‌ها را با هدست روی سر ببینید.

## چیزهایی که دربارهٔ لایه‌ها باید دانست

- هر پنل یک لایهٔ compositor است. ترتیب لایه‌ها با `zIndex` است، نه با فاصله. پرده `zIndex = -1` دارد تا زیرنویس و کنترل‌ها رویش بیایند.
- ترکیب لایه‌ها در نور خطی است. مشکی نیمه‌شفاف روی هدست خیلی کم‌رنگ‌تر از مانیتور دیده می‌شود. برای تاریک کردن، روشنایی خود لایه را کم کنید (`setColorScaleBias`، مثل تریلر صفحهٔ عنوان).
- پنل‌های Compose با `PanelShapeLayerBlendType.ALPHA_BLEND` ساخته می‌شوند. حالت پیش‌فرض، آلفا را با یک آستانه می‌بُرد و لبه‌های نرم را از بین می‌برد.

</div>
