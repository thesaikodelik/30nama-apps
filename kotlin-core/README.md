# ماژول `kotlin-core`

منطق بدون رابط برنامهٔ Apple TV و مک (`30nama/Core`)، به Kotlin خالص (JVM، پکیج `app.nama.core`). مرجعش فقط `30nama/Core` است: هر تغییری در منطق اول آن‌جا نوشته می‌شود و بعد این‌جا. اندروید TV آن را می‌خواند (`android/settings.gradle.kts`). جز kotlinx-coroutines وابستگی ندارد؛ JSON با نوع آزاد `Json` و تجزیه‌گر خود ماژول خوانده می‌شود و HTTP از یک `Transport` می‌گذرد که پیش‌فرضش `HttpURLConnection` است (هم روی JVM، هم روی اندروید).

این ماژول از `quest/core` (۵ آبان ۱۴۰۵) کپی شد، که خودش ترجمهٔ فایل‌به‌فایل `30nama/Core` بود. برنامهٔ کوئست هنوز نسخهٔ خودش را در `quest/core` دارد و از این پوشه چیزی نمی‌خواند. صاحب پروژه: منطق اندروید نباید از برنامهٔ کوئست بیاید، که هنوز کامل نیست.

چه چیزهایی این‌جاست:

- `Json`، `APIClient` با `RequestGate` (هم‌زمانی و فاصلهٔ درخواست‌ها، پیدا کردن سرور، تلاش دوباره، `background`، توکن نشست) و API سایت (`site`)
- `Actions.kt`: اکشن‌هایی که برنامه صدا می‌زند، با همان پارامترهای برنامهٔ Apple TV و مک (ورود با کد، خانه، صفحهٔ عنوان، پخش، گزارش پیشرفت، لیست‌ها و …). این لایه در Swift جدا نیست و فقط این‌جاست.
- مدل‌ها و تجزیه‌گرها: `Models`، `Formatting`، `Subtitles`، `BlurHash`، `Segments`، `FilteredHosts`، `LogoIndex`، `TMDB`، `Aparat`، `Trending`، `ContinueDismissals`
- رشته‌ها: `L10n.kt` (کلاس `Loc`) و `L10nStrings.kt`، که با `python kotlin-core/tools/gen-l10n.py` از `L10n.swift` ساخته می‌شود. دستی ویرایشش نکنید.
- `Ratings`، `AdvancedSearch`، `SiteAPI`، `SubSource` (با `Zip`)، `MovieLines`، `Notifications`، `EpisodeAlerts`، `DownloadRules`

تست‌ها همان تست‌های `Tests/NamaCoreTests` هستند به JUnit، با همان نام‌ها و همان فیکسچرها، به‌علاوهٔ چند تست برای چیزهایی که فقط این‌جاست (تجزیه‌گر JSON، `Actions`، ترنسپورت پیش‌فرض). از داخل `android/`:

<div dir="ltr">

```
./gradlew :core:test
```

</div>

یک تست بررسی می‌کند کلیدهای `S` با `L10n.swift` یکی باشند؛ اگر رشته‌ای در Swift اضافه شد و اسکریپت اجرا نشد، همان تست می‌افتد.

برگردانده نشده: `MacRelease.swift` (به‌روزرسان اندروید کلید و روال خودش را دارد) و `RatingSync.swift` (همگام‌سازی iCloud). کلید API سایت در ریپو نیست؛ برنامه آن را از تنظیمات بیلد خودش در `SiteConfig(key = …)` می‌دهد.
