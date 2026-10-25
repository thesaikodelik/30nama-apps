package app.nama.core

import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Reads the files out of a zip archive held in memory: stored and deflated entries, through the
 * central directory. Subtitle sites hand out archives. `java.util.zip.ZipInputStream` reads the
 * local headers instead and would unpack an entry whatever size the directory claims, so this
 * follows the Swift side's reader and its limit.
 */
object Zip {
    /**
     * Larger than any subtitle file; an entry that says it is bigger is not unpacked. The size is
     * what the archive claims, and an archive from a stranger may claim anything.
     */
    const val LARGEST = 20 * 1024 * 1024

    data class File(val name: String, val data: ByteArray) {
        override fun equals(other: Any?): Boolean = other is File && other.name == name && other.data.contentEquals(data)
        override fun hashCode(): Int = name.hashCode() * 31 + data.contentHashCode()
    }

    /**
     * Name and contents of every file in `data`. Folders are left out, and so is anything that
     * cannot be read; names keep only their last part, so nothing points outside where it is put.
     */
    fun files(data: ByteArray): List<File> {
        val b = data
        val end = endRecord(b) ?: return emptyList()
        val count = u16(b, end + 10)
        var at = u32(b, end + 16)
        val out = mutableListOf<File>()
        repeat(count) {
            if (at + 46 > b.size || u32(b, at.toInt()) != 0x02014B50L) return out
            val entry = at.toInt()
            val method = u16(b, entry + 10)
            val packed = u32(b, entry + 20)
            val size = u32(b, entry + 24)
            val nameLength = u16(b, entry + 28)
            val extra = u16(b, entry + 30)
            val comment = u16(b, entry + 32)
            val local = u32(b, entry + 42)
            val nameEnd = entry + 46 + nameLength
            if (nameEnd > b.size) return out
            val name = String(b, entry + 46, nameLength, Charsets.UTF_8)
            at = (nameEnd + extra + comment).toLong()
            if (name.endsWith("/") || size > LARGEST || local + 30 > b.size || u32(b, local.toInt()) != 0x04034B50L) return@repeat
            val start = local + 30 + u16(b, local.toInt() + 26) + u16(b, local.toInt() + 28)
            if (start + packed > b.size) return@repeat
            val body = b.copyOfRange(start.toInt(), (start + packed).toInt())
            val file = when (method) {
                0 -> body
                8 -> inflate(body, size.toInt())
                else -> null
            }
            if (file != null) out.add(File(name.substringAfterLast('/').substringAfterLast('\\'), file))
        }
        return out
    }

    private fun endRecord(b: ByteArray): Int? {
        if (b.size < 22) return null
        // The record ends the file, followed only by a comment of up to 64 KB.
        var i = b.size - 22
        val stop = maxOf(0, b.size - 22 - 65_535)
        while (i >= stop) {
            if (u32(b, i) == 0x06054B50L) return i
            i--
        }
        return null
    }

    /** Raw deflate, exactly `size` bytes of it, or nil. */
    private fun inflate(data: ByteArray, size: Int): ByteArray? {
        if (size <= 0) return ByteArray(0)
        val inflater = Inflater(true)
        return try {
            inflater.setInput(data)
            val out = ByteArray(size)
            var written = 0
            while (written < size && !inflater.finished()) {
                val n = inflater.inflate(out, written, size - written)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                written += n
            }
            if (written == size) out else null
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
}
