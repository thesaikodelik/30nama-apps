package app.nama.quest.data

import kotlinx.coroutines.delay

/**
 * Fictional titles with drawn artwork, and a public HLS test stream. Nothing here comes from 30nama.
 */
class DemoCatalog : Catalog {
  override val isDemo = true
  override var lang = app.nama.core.Lang.en
  override val signedIn = false

  private val films =
      listOf(
          Tile("d1", "سایه‌های شمال", "Northern Shadows", year = "2023", genres = listOf("درام", "معمایی"), rating = "8.1", art = Art(0xFF1D2B53, 0xFFE07A5F)),
          Tile("d2", "آخرین قطار", "The Last Train", year = "2022", genres = listOf("هیجانی"), rating = "7.6", art = Art(0xFF2D1E2F, 0xFFF2CC8F)),
          Tile("d3", "باغ نارنج", "Orange Garden", year = "2024", genres = listOf("خانوادگی"), rating = "8.4", art = Art(0xFF264653, 0xFFF4A261)),
          Tile("d4", "مدار صفر", "Orbit Zero", year = "2023", genres = listOf("علمی‌تخیلی"), rating = "7.9", art = Art(0xFF0B132B, 0xFF5BC0BE)),
          Tile("d5", "شب‌های تهران", "Tehran Nights", year = "2021", genres = listOf("جنایی"), rating = "8.0", art = Art(0xFF14080E, 0xFFC9184A)),
          Tile("d6", "کوه خاموش", "Silent Peak", year = "2020", genres = listOf("ماجرایی"), rating = "7.4", art = Art(0xFF1B263B, 0xFFA3CEF1)),
          Tile("d7", "نامه‌ای از دریا", "Letter from the Sea", year = "2024", genres = listOf("عاشقانه"), rating = "7.8", art = Art(0xFF03045E, 0xFF90E0EF)),
          Tile("d8", "رد پا", "Footprints", year = "2022", genres = listOf("مستند"), rating = "8.6", art = Art(0xFF283618, 0xFFDDA15E)),
          Tile("d9", "پرواز ۷۲", "Flight 72", year = "2024", genres = listOf("هیجانی"), rating = "7.5", art = Art(0xFF101820, 0xFFF95738)),
          Tile("d10", "ستاره‌های کویر", "Desert Stars", year = "2021", genres = listOf("انیمیشن"), rating = "8.2", art = Art(0xFF240046, 0xFFFF9E00)),
      )
  private val series =
      listOf(
          Tile("s1", "شهر شیشه‌ای", "Glass City", year = "2023", genres = listOf("درام"), rating = "8.3", isSeries = true, art = Art(0xFF22223B, 0xFFC9ADA7)),
          Tile("s2", "خانهٔ پدری", "The Old House", year = "2019", genres = listOf("درام"), rating = "7.7", isSeries = true, art = Art(0xFF3D2C2E, 0xFFE9C46A)),
          Tile("s3", "فرکانس", "Frequency", year = "2024", genres = listOf("علمی‌تخیلی"), rating = "8.0", isSeries = true, art = Art(0xFF0D1B2A, 0xFF7B2CBF)),
      )

  override suspend fun home(): List<Row> {
    delay(250)
    val continuing =
        listOf(
            series[0].copy(progress = 0.62f, note = "فصل ۲ · قسمت ۴ · ۲۳ دقیقه مانده"),
            films[2].copy(progress = 0.35f, note = "۶۴ دقیقه مانده"),
            series[2].copy(progress = 0.12f, note = "فصل ۱ · قسمت ۱ · ۴۱ دقیقه مانده"),
            films[6].copy(progress = 0.8f, note = "۱۸ دقیقه مانده"),
        )
    return listOf(
        Row("continue", "ادامهٔ تماشا", continuing, landscape = true),
        Row("trending", "پرطرفدار این هفته", films.take(8)),
        Row("series", "سریال‌ها", series + films.takeLast(3)),
    )
  }

  override suspend fun details(tile: Tile): Details {
    delay(200)
    val people =
        listOf(
            Person("نگار رحیمی", "کارگردان"),
            Person("آرش مقدم", "بازیگر"),
            Person("لیلا فرهادی", "بازیگر"),
            Person("کاوه نیک‌نام", "بازیگر"),
            Person("مینا صدری", "آهنگساز"),
            Person("رضا توانا", "فیلمبردار"),
        )
    val seasons =
        if (!tile.isSeries) emptyList()
        else
            listOf(1 to 6, 2 to 8).map { (s, n) ->
              Season(s, (1..n).map { e -> Episode("$s-$e", s, e, "قسمت $e", minutes = 42 + (e * 7) % 15, watched = s == 1 && e < 3) })
            }
    return Details(
        tile = tile,
        overview =
            "داستانی ساختگی برای پیش‌نمایش: این متن جای خلاصهٔ فیلم را نگه می‌دارد تا اندازهٔ پنل و فاصلهٔ خط‌ها در هدست دیده شود.",
        runtimeMinutes = if (tile.isSeries) null else 108,
        director = "نگار رحیمی",
        people = people,
        seasons = seasons,
        resume = seasons.firstOrNull()?.episodes?.getOrNull(2),
    )
  }

  override suspend fun stream(details: Details, episode: Episode?): Stream {
    fun vtt(lines: List<String>): String {
      val b = StringBuilder("WEBVTT\n\n")
      lines.forEachIndexed { i, line ->
        b.append("00:00:%02d.000 --> 00:00:%02d.500\n%s\n\n".format(i * 4 + 1, i * 4 + 4, line))
      }
      // mpv reads inline text through its memory:// protocol; it has no data: URLs.
      return "memory://" + b
    }
    return Stream(
        url = DEMO_VIDEO,
        title = details.tile.title,
        subtitles =
            listOf(
                SubtitleTrack(vtt((1..14).map { "این خط شمارهٔ $it زیرنویس آزمایشی است" }), "fa", "فارسی"),
                SubtitleTrack(vtt((1..14).map { "Sample subtitle line $it" }), "en", "English"),
            ),
        introStart = 8.0,
        introEnd = 40.0,
    )
  }

  override suspend fun loginCode() = LoginCode("482917", "30nama.com/auth")

  override suspend fun pollLogin(code: LoginCode): Boolean = false

  companion object {
    // Mux's Big Buck Bunny: plain MPEG-TS HLS with a few renditions, the same shape as 30nama's
    // streams. (Apple's fMP4 sample the Mac uses opens dozens of renditions at once in FFmpeg.)
    const val DEMO_VIDEO = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
  }
}
