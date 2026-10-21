package de.ronnyporsch.open_rgb.kmusicplayer.util

fun md5(input: String): String {
    val bytes = input.encodeToByteArray()
    return md5Bytes(bytes).joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

private fun md5Bytes(message: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    val k = IntArray(64) { i ->
        val t = kotlin.math.abs(kotlin.math.sin((i + 1).toDouble()))
        (t * 4294967296.0).toLong().toInt()
    }

    var a0 = 0x67452301
    var b0 = 0xEFCDAB89.toInt()
    var c0 = 0x98BADCFE.toInt()
    var d0 = 0x10325476

    val originalLenBits = message.size.toLong() * 8
    val padded = buildList {
        addAll(message.toList())
        add(0x80.toByte())
        while ((size % 64) != 56) add(0.toByte())
        for (i in 0 until 8) add(((originalLenBits shr (i * 8)) and 0xFF).toByte())
    }.toByteArray()

    for (offset in padded.indices step 64) {
        val m = IntArray(16) { i ->
            val j = offset + i * 4
            (padded[j].toInt() and 0xFF) or
                    ((padded[j + 1].toInt() and 0xFF) shl 8) or
                    ((padded[j + 2].toInt() and 0xFF) shl 16) or
                    ((padded[j + 3].toInt() and 0xFF) shl 24)
        }

        var a = a0; var b = b0; var c = c0; var d = d0

        for (i in 0 until 64) {
            val f: Int
            val g: Int
            when {
                i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }

            val temp = d
            d = c
            c = b
            val x = a + f + k[i] + m[g]
            b += (x shl s[i]) or (x ushr (32 - s[i]))
            a = temp
        }

        a0 += a; b0 += b; c0 += c; d0 += d
    }

    return byteArrayOf(
        a0.toByte(), (a0 shr 8).toByte(), (a0 shr 16).toByte(), (a0 shr 24).toByte(),
        b0.toByte(), (b0 shr 8).toByte(), (b0 shr 16).toByte(), (b0 shr 24).toByte(),
        c0.toByte(), (c0 shr 8).toByte(), (c0 shr 16).toByte(), (c0 shr 24).toByte(),
        d0.toByte(), (d0 shr 8).toByte(), (d0 shr 16).toByte(), (d0 shr 24).toByte(),
    )
}

fun generateSalt(length: Int = 12): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
