package app.nama.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

// Small pieces of Foundation the Swift side leans on, written to behave the same way: number
// parsing that takes only ASCII digits, whitespace as CharacterSet defines it, URLs read leniently.

/** `CharacterSet.whitespaces`: tab and the space separators. */
internal fun Char.isSwiftWhitespace(): Boolean = this == '\t' || Character.getType(this) == Character.SPACE_SEPARATOR.toInt()

/** `CharacterSet.newlines`. */
internal fun Char.isSwiftNewline(): Boolean = this in '\u000A'..'\u000D' || this == '\u0085' || this == ' ' || this == ' '

/** `trimmingCharacters(in: .whitespaces)` */
internal fun String.trimWhitespace(): String = trim { it.isSwiftWhitespace() }

/** `trimmingCharacters(in: .whitespacesAndNewlines)` */
internal fun String.trimWhitespaceAndNewlines(): String = trim { it.isSwiftWhitespace() || it.isSwiftNewline() }

/** Swift's `Int(String)`: an optional sign and ASCII digits only. Kotlin's own also takes Persian digits. */
internal fun String.asciiInt(): Int? {
    if (isEmpty()) return null
    val digits = if (this[0] == '+' || this[0] == '-') substring(1) else this
    if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
    return toIntOrNull()
}

private val decimal = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")

/** Swift's `Double(String)`: decimal notation, or inf / nan. Kotlin's own also takes "1.5f" and hex. */
internal fun String.asciiDouble(): Double? {
    if (decimal.matches(this)) return toDoubleOrNull()
    return when (lowercase().removePrefix("+")) {
        "inf", "infinity" -> Double.POSITIVE_INFINITY
        "-inf", "-infinity" -> Double.NEGATIVE_INFINITY
        "nan" -> Double.NaN
        else -> null
    }
}

/** `String(Double)` as Swift writes it: "7.5", "1e+16", "1e-05". */
internal fun Double.swiftDescription(): String {
    if (isNaN()) return "nan"
    if (isInfinite()) return if (this > 0) "inf" else "-inf"
    val plain = toString()
    val e = plain.indexOf('E')
    if (e < 0) return plain
    val exponent = plain.substring(e + 1).toInt()
    if (exponent in -4..15) return BigDecimal(plain).stripTrailingZeros().toPlainString().let { if (it.contains('.')) it else "$it.0" }
    val mantissa = plain.substring(0, e).removeSuffix(".0")
    val sign = if (exponent < 0) "-" else "+"
    return mantissa + "e" + sign + Math.abs(exponent).toString().padStart(2, '0')
}

/** `String(format: "%.Nf")`: rounded the way C rounds the binary value, which Java's formatter does not. */
internal fun Double.fixed(places: Int): String = BigDecimal(this).setScale(places, RoundingMode.HALF_EVEN).toPlainString()

/** `String(format:)` with ASCII digits whatever the device's locale. */
internal fun format(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)

/** Bytes as UTF-8, or nil when they are not valid UTF-8, as `String(data:encoding:.utf8)`. */
internal fun ByteArray.utf8OrNull(): String? = decodeOrNull(Charsets.UTF_8)

internal fun ByteArray.decodeOrNull(charset: Charset): String? = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this)).toString()
} catch (_: CharacterCodingException) {
    null
}

// MARK: URLs

private const val HEX = "0123456789ABCDEF"

/** Percent-encodes every UTF-8 byte of `s` whose character is not an ASCII one in `allowed`. */
internal fun percentEncode(s: String, allowed: (Char) -> Boolean): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val chunk = String(Character.toChars(cp))
        if (cp < 128 && allowed(cp.toChar())) {
            out.append(chunk)
        } else {
            for (b in chunk.toByteArray(Charsets.UTF_8)) {
                val v = b.toInt() and 0xFF
                out.append('%').append(HEX[v shr 4]).append(HEX[v and 15])
            }
        }
        i += Character.charCount(cp)
    }
    return out.toString()
}

internal fun Char.isUnreserved(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

/** Characters a URL may hold as they are; everything else is escaped. */
private fun Char.isURLCharacter(): Boolean = isUnreserved() || this in ":/?#[]@!$&'()*+,;="

/**
 * A URL from text that may not quite be one, as Foundation's `URL(string:)` takes it: what a URL
 * cannot hold (spaces, non-ASCII letters, a second `#`) is percent-encoded, escapes already there
 * are kept. Nil only for nothing at all.
 */
fun urlOf(text: String?): URI? {
    if (text.isNullOrEmpty()) return null
    try {
        if (text.all { it.code < 128 }) return URI(text)
    } catch (_: Exception) {
    }
    val out = StringBuilder()
    var fragment = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        val isEscape = ch == '%' && i + 2 < text.length && text[i + 1].isHexDigit() && text[i + 2].isHexDigit()
        when {
            isEscape -> out.append(ch)
            ch == '#' && !fragment -> { fragment = true; out.append(ch) }
            ch == '#' -> out.append("%23")
            ch.isURLCharacter() -> out.append(ch)
            else -> {
                val cp = text.codePointAt(i)
                out.append(percentEncode(String(Character.toChars(cp))) { false })
                i += Character.charCount(cp)
                continue
            }
        }
        i++
    }
    return try {
        URI(out.toString())
    } catch (_: Exception) {
        null
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** The last part of the path, decoded, as `URL.lastPathComponent`: "/" for the root, "" for none. */
val URI.lastPathComponent: String
    get() {
        val path = path ?: return ""
        if (path.isEmpty()) return ""
        if (path == "/") return "/"
        return path.trimEnd('/').substringAfterLast('/')
    }

/** The last part of the path without its extension. */
internal fun String.withoutExtension(): String {
    val dot = lastIndexOf('.')
    return if (dot > 0) substring(0, dot) else this
}

/**
 * A regex whose \s, \d and  know Unicode, as ICU's do in Swift. The JVM needs the (?U) flag for
 * that; Android's engine is ICU itself, already behaves so, and rejects (?U) as a syntax error.
 */
fun unicodeRegex(pattern: String): Regex = Regex(if (onAndroid) pattern else "(?U)$pattern")

private val onAndroid: Boolean = System.getProperty("java.vm.name")?.contains("Dalvik") == true
