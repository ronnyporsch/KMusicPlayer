package de.ronnyporsch.open_rgb.kmusicplayer.platform

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writers for tags ffmpeg can't produce: it can only write one value per tag, but Picard-style
 * multi-valued tags (ARTISTS, ALBUMARTISTS) need one Vorbis comment / MP4 data atom per value.
 */
internal object AudioTags {

    /**
     * Replaces the comment header of an Ogg Opus or Ogg Vorbis file with [comments]
     * (key/value pairs, keys may repeat for multi-valued tags).
     */
    fun writeOggComments(file: File, comments: List<Pair<String, String>>) {
        val bytes = file.readBytes()
        val pages = parseOggPages(bytes)

        // Collect the header packets; audio data always starts on a fresh page after them
        val packets = mutableListOf<ByteArrayOutputStream>()
        var packetOpen = false
        var headerCount = Int.MAX_VALUE
        var firstAudioPage = -1
        for ((index, page) in pages.withIndex()) {
            var offset = 0
            for (lace in page.lacing) {
                if (!packetOpen) {
                    packets += ByteArrayOutputStream()
                    packetOpen = true
                }
                packets.last().write(page.data, offset, lace)
                offset += lace
                if (lace < 255) packetOpen = false
            }
            if (headerCount == Int.MAX_VALUE && packets.isNotEmpty() && !packetOpen) {
                val first = packets.first().toByteArray()
                headerCount = when {
                    first.startsWith(OPUS_HEAD) -> 2
                    first.startsWith(VORBIS_IDENTIFICATION) -> 3
                    else -> throw IOException("Not an Ogg Opus or Vorbis file: $file")
                }
            }
            if (packets.size >= headerCount && !packetOpen) {
                if (packets.size != headerCount) throw IOException("Unexpected Ogg header layout in $file")
                firstAudioPage = index + 1
                break
            }
        }
        if (firstAudioPage < 0) throw IOException("Incomplete Ogg headers in $file")

        val headers = packets.map { it.toByteArray() }
        val isOpus = headerCount == 2
        val commentPrefix = if (isOpus) OPUS_TAGS else VORBIS_COMMENT
        val oldComment = headers[1]
        if (!oldComment.startsWith(commentPrefix)) throw IOException("Missing Ogg comment header in $file")
        val vendorLength = ByteBuffer.wrap(oldComment, commentPrefix.size, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val vendor = oldComment.copyOfRange(commentPrefix.size + 4, commentPrefix.size + 4 + vendorLength)

        val newComment = ByteArrayOutputStream().apply {
            write(commentPrefix)
            writeIntLe(vendor.size)
            write(vendor)
            writeIntLe(comments.size)
            for ((key, value) in comments) {
                val entry = "$key=$value".toByteArray()
                writeIntLe(entry.size)
                write(entry)
            }
            if (!isOpus) write(1) // framing bit
        }.toByteArray()

        // The identification header stays on its own first page; the others are repaginated
        val serial = pages.first().serial
        val out = ByteArrayOutputStream(bytes.size + newComment.size)
        var sequence = 0
        out.write(pages.first().withSequence(sequence++).encode())
        for (page in paginate(listOf(newComment) + headers.drop(2), serial, sequence)) {
            out.write(page.encode())
            sequence++
        }
        for (page in pages.drop(firstAudioPage)) out.write(page.withSequence(sequence++).encode())
        file.writeBytes(out.toByteArray())
    }

    /**
     * Adds iTunes freeform atoms (`----:com.apple.iTunes:<name>`, one data atom per value) to the
     * `moov/udta/meta/ilst` atom of an MP4 file, which must already exist (ffmpeg writes it along with the other tags).
     */
    fun addMp4FreeformTags(file: File, tags: Map<String, List<String>>) {
        val entries = tags.filterValues { it.isNotEmpty() }
        if (entries.isEmpty()) return
        val bytes = file.readBytes()

        val moov = findAtoms(bytes, 0, bytes.size).firstOrNull { it.type == "moov" }
            ?: throw IOException("No moov atom in $file")
        val udta = findAtoms(bytes, moov.contentStart, moov.end).firstOrNull { it.type == "udta" }
            ?: throw IOException("No udta atom in $file")
        val meta = findAtoms(bytes, udta.contentStart, udta.end).firstOrNull { it.type == "meta" }
            ?: throw IOException("No meta atom in $file")
        // meta is a full box: version and flags precede its children
        val ilst = findAtoms(bytes, meta.contentStart + 4, meta.end).firstOrNull { it.type == "ilst" }
            ?: throw IOException("No ilst atom in $file")

        val inserted = ByteArrayOutputStream().apply {
            for ((name, values) in entries) {
                write(atom("----", atom("mean", ByteArray(4) + "com.apple.iTunes".toByteArray()) +
                    atom("name", ByteArray(4) + name.toByteArray()) +
                    values.fold(ByteArray(0)) { acc, value ->
                        // Type 1 = UTF-8 text, followed by an empty locale
                        acc + atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + value.toByteArray())
                    }))
            }
        }.toByteArray()
        val delta = inserted.size

        val result = bytes.copyOfRange(0, ilst.end) + inserted + bytes.copyOfRange(ilst.end, bytes.size)
        val buffer = ByteBuffer.wrap(result)
        for (parent in listOf(moov, udta, meta, ilst)) {
            if (parent.headerSize != 8) throw IOException("Unsupported 64-bit ${parent.type} atom in $file")
            buffer.putInt(parent.start, buffer.getInt(parent.start) + delta)
        }
        // Sample offsets point into mdat; they shift if it comes after the grown moov atom
        val mdatAfterMoov = findAtoms(bytes, 0, bytes.size).any { it.type == "mdat" && it.start > moov.start }
        if (mdatAfterMoov) shiftChunkOffsets(result, moov.contentStart, moov.end + delta, delta)
        file.writeBytes(result)
    }

    private fun shiftChunkOffsets(bytes: ByteArray, from: Int, to: Int, delta: Int) {
        val buffer = ByteBuffer.wrap(bytes)
        for (atom in findAtoms(bytes, from, to)) {
            when (atom.type) {
                "trak", "mdia", "minf", "stbl" -> shiftChunkOffsets(bytes, atom.contentStart, atom.end, delta)
                "stco" -> {
                    val count = buffer.getInt(atom.contentStart + 4)
                    for (i in 0 until count) {
                        val pos = atom.contentStart + 8 + i * 4
                        buffer.putInt(pos, (buffer.getInt(pos).toLong() and 0xFFFFFFFFL).plus(delta).toInt())
                    }
                }
                "co64" -> {
                    val count = buffer.getInt(atom.contentStart + 4)
                    for (i in 0 until count) {
                        val pos = atom.contentStart + 8 + i * 8
                        buffer.putLong(pos, buffer.getLong(pos) + delta)
                    }
                }
            }
        }
    }

    private class Mp4Atom(val type: String, val start: Int, val headerSize: Int, val end: Int) {
        val contentStart get() = start + headerSize
    }

    private fun findAtoms(bytes: ByteArray, from: Int, to: Int): List<Mp4Atom> {
        val buffer = ByteBuffer.wrap(bytes)
        val atoms = mutableListOf<Mp4Atom>()
        var pos = from
        while (pos + 8 <= to) {
            var size = buffer.getInt(pos).toLong() and 0xFFFFFFFFL
            var headerSize = 8
            when (size) {
                0L -> size = (to - pos).toLong()
                1L -> {
                    size = buffer.getLong(pos + 8)
                    headerSize = 16
                }
            }
            if (size < headerSize || pos + size > to) throw IOException("Corrupt MP4 atom at $pos")
            atoms += Mp4Atom(String(bytes, pos + 4, 4, Charsets.ISO_8859_1), pos, headerSize, (pos + size).toInt())
            pos += size.toInt()
        }
        return atoms
    }

    private fun atom(type: String, content: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + content.size).putInt(8 + content.size).put(type.toByteArray(Charsets.ISO_8859_1))
            .put(content).array()

    private class OggPage(
        val headerType: Int,
        val granule: Long,
        val serial: Int,
        val sequence: Int,
        val lacing: IntArray,
        val data: ByteArray,
    ) {
        fun withSequence(sequence: Int) = OggPage(headerType, granule, serial, sequence, lacing, data)

        fun encode(): ByteArray {
            val buffer = ByteBuffer.allocate(27 + lacing.size + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(OGG_CAPTURE).put(0).put(headerType.toByte())
                .putLong(granule).putInt(serial).putInt(sequence).putInt(0)
                .put(lacing.size.toByte())
            lacing.forEach { buffer.put(it.toByte()) }
            buffer.put(data)
            val page = buffer.array()
            ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN).putInt(22, oggCrc(page))
            return page
        }
    }

    private fun parseOggPages(bytes: ByteArray): List<OggPage> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val pages = mutableListOf<OggPage>()
        var pos = 0
        while (pos < bytes.size) {
            if (pos + 27 > bytes.size || !bytes.copyOfRange(pos, pos + 4).contentEquals(OGG_CAPTURE)) {
                throw IOException("Corrupt Ogg page at $pos")
            }
            val segments = bytes[pos + 26].toInt() and 0xFF
            val lacing = IntArray(segments) { bytes[pos + 27 + it].toInt() and 0xFF }
            val dataStart = pos + 27 + segments
            val dataEnd = dataStart + lacing.sum()
            if (dataEnd > bytes.size) throw IOException("Truncated Ogg page at $pos")
            pages += OggPage(
                headerType = bytes[pos + 5].toInt() and 0xFF,
                granule = buffer.getLong(pos + 6),
                serial = buffer.getInt(pos + 14),
                sequence = buffer.getInt(pos + 18),
                lacing = lacing,
                data = bytes.copyOfRange(dataStart, dataEnd),
            )
            pos = dataEnd
        }
        return pages
    }

    /** Lays out [packets] on consecutive pages (at most 255 segments each), as header pages with granule 0. */
    private fun paginate(packets: List<ByteArray>, serial: Int, firstSequence: Int): List<OggPage> {
        // (segment size, ends a packet) for every segment of every packet
        val segments = packets.flatMap { packet ->
            List(packet.size / 255) { 255 to false } + ((packet.size % 255) to true)
        }
        val data = packets.fold(ByteArray(0)) { acc, packet -> acc + packet }
        val pages = mutableListOf<OggPage>()
        var offset = 0
        var continued = false
        for (chunk in segments.chunked(255)) {
            val size = chunk.sumOf { it.first }
            pages += OggPage(
                headerType = if (continued) 0x01 else 0,
                // -1: no packet finishes on this page
                granule = if (chunk.any { it.second }) 0 else -1,
                serial = serial,
                sequence = firstSequence + pages.size,
                lacing = chunk.map { it.first }.toIntArray(),
                data = data.copyOfRange(offset, offset + size),
            )
            offset += size
            continued = !chunk.last().second
        }
        return pages
    }

    private val OGG_CRC_TABLE = IntArray(256) { index ->
        var crc = index shl 24
        repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        crc
    }

    private fun oggCrc(page: ByteArray): Int =
        page.fold(0) { crc, byte -> (crc shl 8) xor OGG_CRC_TABLE[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF] }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private fun ByteArrayOutputStream.writeIntLe(value: Int) =
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())

    private val OGG_CAPTURE = "OggS".toByteArray()
    private val OPUS_HEAD = "OpusHead".toByteArray()
    private val OPUS_TAGS = "OpusTags".toByteArray()
    private val VORBIS_IDENTIFICATION = byteArrayOf(1) + "vorbis".toByteArray()
    private val VORBIS_COMMENT = byteArrayOf(3) + "vorbis".toByteArray()
}
