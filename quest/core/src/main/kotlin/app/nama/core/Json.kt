package app.nama.core

import java.net.URI

/**
 * Loosely typed JSON value.
 *
 * The 30nama API is not consistent between endpoints: numbers arrive as strings, a field is an
 * object in one response and an array in another, and optional fields are `null`, `""` or
 * missing. Models read through this type instead of typed decoding so one odd field never fails
 * a whole screen.
 */
sealed class Json {
    data object Null : Json()
    data class Bool(val value: Boolean) : Json()
    data class Num(val value: Double) : Json()
    data class Str(val value: String) : Json()
    data class Arr(val items: List<Json>) : Json()
    data class Obj(val fields: Map<String, Json>) : Json()

    operator fun get(key: String): Json = (this as? Obj)?.fields?.get(key) ?: Null

    operator fun get(index: Int): Json = (this as? Arr)?.items?.getOrNull(index) ?: Null

    val isNull: Boolean get() = this == Null

    val array: List<Json> get() = (this as? Arr)?.items ?: emptyList()

    /** The fields of an object; nil for anything else. (`object` in Swift, a keyword here.) */
    val obj: Map<String, Json>? get() = (this as? Obj)?.fields

    /** String value; numbers are rendered without a trailing `.0`. */
    val string: String?
        get() = when (this) {
            is Str -> value
            is Num -> if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.swiftDescription()
            else -> null
        }

    /** Trimmed, non-empty string. */
    val text: String? get() = string?.trimWhitespaceAndNewlines()?.takeIf { it.isNotEmpty() }

    val double: Double?
        get() = when (this) {
            is Num -> value
            is Str -> value.trimWhitespace().asciiDouble()
            else -> null
        }

    /** Whole number, toward zero. Nil beyond what an `Int` holds; `long` takes the rest. */
    val int: Int? get() = long?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    val long: Long?
        get() {
            val d = double ?: return null
            if (!d.isFinite() || Math.abs(d) >= 1e15) return null
            return d.toLong()
        }

    val bool: Boolean
        get() = when (this) {
            is Bool -> value
            is Num -> value != 0.0
            is Str -> value.lowercase() in setOf("1", "true", "yes")
            else -> false
        }

    val url: URI? get() = urlOf(text)

    /** Serialised again, for a value we want to persist. */
    fun serialize(sortedKeys: Boolean = false): String = StringBuilder().also { write(it, sortedKeys) }.toString()

    fun data(): ByteArray = serialize().toByteArray(Charsets.UTF_8)

    private fun write(out: StringBuilder, sorted: Boolean) {
        when (this) {
            Null -> out.append("null")
            is Bool -> out.append(value)
            is Num -> out.append(
                if (!value.isFinite()) "null"
                else if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString()
                else value.toString(),
            )
            is Str -> quote(value, out)
            is Arr -> {
                out.append('[')
                items.forEachIndexed { i, v -> if (i > 0) out.append(','); v.write(out, sorted) }
                out.append(']')
            }
            is Obj -> {
                out.append('{')
                val entries = if (sorted) fields.entries.sortedBy { it.key } else fields.entries
                entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    quote(k, out)
                    out.append(':')
                    v.write(out, sorted)
                }
                out.append('}')
            }
        }
    }

    class ParseException(message: String) : Exception(message)

    companion object {
        /** Builds a value from Kotlin maps, lists, numbers, strings, booleans and nulls. */
        fun of(value: Any?): Json = when (value) {
            null -> Null
            is Json -> value
            is Boolean -> Bool(value)
            is Number -> Num(value.toDouble())
            is String -> Str(value)
            is URI -> Str(value.toString())
            is Map<*, *> -> Obj(LinkedHashMap<String, Json>().apply { for ((k, v) in value) put(k.toString(), of(v)) })
            is Iterable<*> -> Arr(value.map { of(it) })
            is Array<*> -> Arr(value.map { of(it) })
            else -> Null
        }

        fun parse(bytes: ByteArray): Json = parse(String(bytes, Charsets.UTF_8))

        /** Any JSON value, fragments included, as `JSONSerialization` with `.fragmentsAllowed`. */
        fun parse(text: String): Json = Parser(text.removePrefix("﻿")).document()

        private fun quote(s: String, out: StringBuilder) {
            out.append('"')
            for (c in s) {
                when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> if (c < ' ') out.append(format("\\u%04x", c.code)) else out.append(c)
                }
            }
            out.append('"')
        }
    }

    /** Recursive descent over the text. Nesting is capped so a hostile answer cannot overflow the stack. */
    private class Parser(private val s: String) {
        private var i = 0
        private var depth = 0

        fun document(): Json {
            val v = value()
            space()
            if (i != s.length) fail("trailing characters")
            return v
        }

        private fun fail(why: String): Nothing = throw ParseException("$why at $i")

        private fun space() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        private fun value(): Json {
            space()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> nested { obj() }
                '[' -> nested { arr() }
                '"' -> Str(str())
                't' -> literal("true", Bool(true))
                'f' -> literal("false", Bool(false))
                'n' -> literal("null", Null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        private inline fun nested(read: () -> Json): Json {
            if (++depth > 512) fail("nested too deep")
            return read().also { depth-- }
        }

        private fun literal(word: String, v: Json): Json {
            if (!s.startsWith(word, i)) fail("bad literal")
            i += word.length
            return v
        }

        private fun obj(): Json {
            i++
            val fields = LinkedHashMap<String, Json>()
            space()
            if (i < s.length && s[i] == '}') { i++; return Obj(fields) }
            while (true) {
                space()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val key = str()
                space()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                fields[key] = value()
                space()
                if (i >= s.length) fail("unexpected end")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return Obj(fields)
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun arr(): Json {
            i++
            val items = ArrayList<Json>()
            space()
            if (i < s.length && s[i] == ']') { i++; return Arr(items) }
            while (true) {
                items.add(value())
                space()
                if (i >= s.length) fail("unexpected end")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return Arr(items)
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            i++
            val out = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("bad \\u escape")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> fail("control character in string")
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Json {
            val start = i
            if (s[i] == '-') i++
            if (i >= s.length) fail("bad number")
            if (s[i] == '0') i++ else if (s[i] in '1'..'9') while (i < s.length && s[i] in '0'..'9') i++ else fail("bad number")
            if (i < s.length && s[i] == '.') {
                i++
                val digits = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == digits) fail("bad fraction")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val digits = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == digits) fail("bad exponent")
            }
            return Num(s.substring(start, i).toDouble())
        }
    }
}
