# ماژول `core` کوئست

منطق بدون رابط برنامهٔ کوئست، به Kotlin خالص (JVM، پکیج `app.nama.core`)، برگردانده از `30nama/Core`. جز kotlinx-coroutines وابستگی ندارد؛ JSON با نوع آزاد `Json` و تجزیه‌گر خود ماژول خوانده می‌شود و HTTP از یک `Transport` می‌گذرد که پیش‌فرضش `HttpURLConnection` است (هم روی JVM، هم روی اندروید).

چه چیزهایی این‌جاست:

- `Json`، `APIClient` با `RequestGate` (هم‌زمانی و فاصلهٔ درخواست‌ها، پیدا کردن سرور، تلاش دوباره، `background`، توکن نشست) و API سایت (`site`)
- `Actions.kt`: اکشن‌هایی که برنامه صدا می‌زند، با همان پارامترهای برنامهٔ Apple TV و مک (ورود با کد، خانه، صفحهٔ عنوان، پخش، گزارش پیشرفت، لیست‌ها و …). این لایه در Swift جدا نیست و فقط این‌جاست.
- مدل‌ها و تجزیه‌گرها: `Models`، `Formatting`، `Subtitles`، `BlurHash`، `Segments`، `FilteredHosts`، `LogoIndex`، `TMDB`، `Aparat`، `Trending`، `ContinueDismissals`
- رشته‌ها: `L10n.kt` (کلاس `Loc`) و `L10nStrings.kt`، که با `python quest/tools/gen-l10n.py` از `L10n.swift` ساخته می‌شود. دستی ویرایشش نکنید.
- `Ratings`، `AdvancedSearch`، `SiteAPI`، `SubSource` (با `Zip`)، `MovieLines`، `Notifications`، `EpisodeAlerts`، `DownloadRules`

تست‌ها همان تست‌های `Tests/NamaCoreTests` هستند به JUnit، با همان نام‌ها و همان فیکسچرها، به‌علاوهٔ چند تست برای چیزهایی که فقط این‌جاست (تجزیه‌گر JSON، `Actions`، ترنسپورت پیش‌فرض). از داخل `quest/`:

```
./gradlew :core:test
```

یک تست بررسی می‌کند کلیدهای `S` با `L10n.swift` یکی باشند؛ اگر رشته‌ای در Swift اضافه شد و اسکریپت اجرا نشد، همان تست می‌افتد.

برگردانده نشده: `MacRelease.swift` (به‌روزرسان کوئست کلید و روال خودش را دارد) و `RatingSync.swift` (همگام‌سازی iCloud). کلید API سایت در ریپو نیست؛ برنامه آن را از تنظیمات بیلد خودش در `SiteConfig(key = …)` می‌دهد.
