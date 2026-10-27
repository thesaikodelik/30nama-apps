package app.nama.core

import java.text.NumberFormat
import java.util.Locale

object Fmt {
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Replaces ASCII digits with Persian ones. */
    fun persianDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
    }

    /** Player clock: "2:03" or "1:02:03". */
    fun clock(seconds: Double): String {
        val s = maxOf(0, (if (seconds.isFinite()) seconds else 0.0).toInt())
        val h = s / 3600
        val m = s % 3600 / 60
        val x = s % 60
        return if (h > 0) format("%d:%02d:%02d", h, m, x) else format("%d:%02d", m, x)
    }

    /** Runtime in minutes as "1h 35m" / "۱ ساعت و ۳۵ دقیقه". */
    fun runtime(minutes: Int?, lang: Lang): String? {
        if (minutes == null || minutes <= 0) return null
        val h = minutes / 60
        val m = minutes % 60
        return when (lang) {
            Lang.en -> listOfNotNull(if (h > 0) "${h}h" else null, if (m > 0) "${m}m" else null).joinToString(" ")
            Lang.ar -> listOfNotNull(if (h > 0) "$h س" else null, if (m > 0) "$m د" else null).joinToString(" ")
            Lang.fa -> persianDigits(listOfNotNull(if (h > 0) "$h ساعت" else null, if (m > 0) "$m دقیقه" else null).joinToString(" و "))
        }
    }

    fun score(value: Double): String = value.fixed(if (value >= 10) 0 else 1)

    /** 84827 -> "84,827" / "۸۴٬۸۲۷". */
    fun count(value: Int, lang: Lang): String =
        NumberFormat.getIntegerInstance(if (lang == Lang.fa) Locale("fa", "IR") else Locale.US).format(value.toLong())
}
