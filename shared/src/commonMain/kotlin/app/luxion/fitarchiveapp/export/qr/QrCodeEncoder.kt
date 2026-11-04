package app.luxion.fitarchiveapp.export.qr

import kotlin.math.abs

/**
 * Generador de códigos QR (ISO/IEC 18004) en Kotlin puro, para no depender de librerías por plataforma.
 * Modo byte (UTF-8), corrección de errores nivel L (máxima capacidad: los fragmentos se muestran en pantalla,
 * sin daño físico) y versión 1–40 automática. Lanza [IllegalArgumentException] si el texto no cabe.
 */
object QrCodeEncoder : QrEncoder {
    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 40

    private val ECC_CODEWORDS_PER_BLOCK = intArrayOf(
        -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28,
        30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
    )
    private val NUM_ERROR_CORRECTION_BLOCKS = intArrayOf(
        -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17,
        18, 19, 19, 20, 21, 22, 24, 25,
    )

    override fun encode(text: String): QrMatrix {
        val bytes = text.encodeToByteArray()
        val version = (MIN_VERSION..MAX_VERSION).firstOrNull { v ->
            4 + countBits(v) + 8 * bytes.size <= dataCodewords(v) * 8
        } ?: throw IllegalArgumentException("El texto es demasiado largo para un código QR")
        val data = encodeData(bytes, version)
        val all = addEccAndInterleave(data, version)
        return build(all, version)
    }

    private fun countBits(version: Int) = if (version < 10) 8 else 16

    private fun rawModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewords(version: Int) =
        rawModules(version) / 8 - ECC_CODEWORDS_PER_BLOCK[version] * NUM_ERROR_CORRECTION_BLOCKS[version]

    private fun encodeData(bytes: ByteArray, version: Int): ByteArray {
        val bits = ArrayList<Boolean>()
        fun append(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add((value ushr i) and 1 != 0)
        }
        append(0b0100, 4)
        append(bytes.size, countBits(version))
        bytes.forEach { append(it.toInt() and 0xFF, 8) }
        val capacityBits = dataCodewords(version) * 8
        append(0, minOf(4, capacityBits - bits.size))
        append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) {
            append(pad, 8)
            pad = pad xor (0xEC xor 0x11)
        }
        val out = ByteArray(bits.size / 8)
        bits.forEachIndexed { i, b -> if (b) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte() }
        return out
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int): ByteArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[version]
        val eccLen = ECC_CODEWORDS_PER_BLOCK[version]
        val rawCodewords = rawModules(version) / 8
        val numShort = numBlocks - rawCodewords % numBlocks
        val shortLen = rawCodewords / numBlocks
        val divisor = reedSolomonDivisor(eccLen)
        val blocks = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortLen - eccLen + if (i < numShort) 0 else 1
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val ecc = reedSolomonRemainder(dat, divisor)
            val block = ByteArray(shortLen + 1)
            dat.copyInto(block)
            // En los bloques cortos queda un hueco (índice shortLen - eccLen) que el entrelazado se salta.
            ecc.copyInto(block, shortLen + 1 - eccLen)
            blocks.add(block)
        }
        val result = ByteArray(rawCodewords)
        var r = 0
        for (i in 0..shortLen) {
            for ((j, block) in blocks.withIndex()) {
                if (i != shortLen - eccLen || j >= numShort) result[r++] = block[i]
            }
        }
        return result
    }

    private fun reedSolomonDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = multiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
            }
            root = multiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            result.copyInto(result, 0, 1, result.size)
            result[result.size - 1] = 0
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor multiply(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
        }
        return result
    }

    private fun multiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    // --- Matriz ---

    private class Grid(val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunction(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }
    }

    private fun build(codewords: ByteArray, version: Int): QrMatrix {
        val size = version * 4 + 17
        var best: Grid? = null
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val g = Grid(size)
            drawFunctionPatterns(g, version)
            drawCodewords(g, codewords)
            applyMask(g, mask)
            drawFormatBits(g, mask)
            val p = penalty(g)
            if (p < bestPenalty) {
                best = g
                bestPenalty = p
            }
        }
        val g = checkNotNull(best)
        val flat = BooleanArray(size * size)
        for (y in 0 until size) for (x in 0 until size) flat[y * size + x] = g.modules[y][x]
        return QrMatrix(size, flat)
    }

    private fun drawFunctionPatterns(g: Grid, version: Int) {
        val size = g.size
        for (i in 0 until size) {
            g.setFunction(6, i, i % 2 == 0)
            g.setFunction(i, 6, i % 2 == 0)
        }
        drawFinder(g, 3, 3)
        drawFinder(g, size - 4, 3)
        drawFinder(g, 3, size - 4)
        val align = alignmentPositions(version, size)
        for (i in align.indices) for (j in align.indices) {
            if ((i == 0 && j == 0) || (i == 0 && j == align.lastIndex) || (i == align.lastIndex && j == 0)) continue
            drawAlignment(g, align[i], align[j])
        }
        drawFormatBits(g, 0) // reserva el espacio; se vuelve a dibujar con la máscara elegida
        drawVersion(g, version)
    }

    private fun drawFinder(g: Grid, cx: Int, cy: Int) {
        for (dy in -4..4) for (dx in -4..4) {
            val x = cx + dx
            val y = cy + dy
            if (x in 0 until g.size && y in 0 until g.size) {
                val dist = maxOf(abs(dx), abs(dy))
                g.setFunction(x, y, dist != 2 && dist != 4)
            }
        }
    }

    private fun drawAlignment(g: Grid, cx: Int, cy: Int) {
        for (dy in -2..2) for (dx in -2..2) {
            g.setFunction(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
        }
    }

    private fun alignmentPositions(version: Int, size: Int): List<Int> {
        if (version == 1) return emptyList()
        val numAlign = version / 7 + 2
        val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
        val result = ArrayList<Int>()
        var pos = size - 7
        repeat(numAlign - 1) {
            result.add(0, pos)
            pos -= step
        }
        result.add(0, 6)
        return result
    }

    private fun drawFormatBits(g: Grid, mask: Int) {
        val data = (1 shl 3) or mask // nivel L = 01
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int) = (bits ushr i) and 1 != 0
        val size = g.size
        for (i in 0..5) g.setFunction(8, i, bit(i))
        g.setFunction(8, 7, bit(6))
        g.setFunction(8, 8, bit(7))
        g.setFunction(7, 8, bit(8))
        for (i in 9..14) g.setFunction(14 - i, 8, bit(i))
        for (i in 0..7) g.setFunction(size - 1 - i, 8, bit(i))
        for (i in 8..14) g.setFunction(8, size - 15 + i, bit(i))
        g.setFunction(8, size - 8, true)
    }

    private fun drawVersion(g: Grid, version: Int) {
        if (version < 7) return
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val dark = (bits ushr i) and 1 != 0
            val a = g.size - 11 + i % 3
            val b = i / 3
            g.setFunction(a, b, dark)
            g.setFunction(b, a, dark)
        }
    }

    private fun drawCodewords(g: Grid, data: ByteArray) {
        var i = 0
        var right = g.size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until g.size) {
                for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) g.size - 1 - vert else vert
                    if (!g.isFunction[y][x] && i < data.size * 8) {
                        g.modules[y][x] = (data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(g: Grid, mask: Int) {
        for (y in 0 until g.size) for (x in 0 until g.size) {
            val invert = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (x / 3 + y / 2) % 2 == 0
                5 -> x * y % 2 + x * y % 3 == 0
                6 -> (x * y % 2 + x * y % 3) % 2 == 0
                else -> ((x + y) % 2 + x * y % 3) % 2 == 0
            }
            if (invert && !g.isFunction[y][x]) g.modules[y][x] = !g.modules[y][x]
        }
    }

    /** Penalización de las cuatro reglas de la norma; sirve solo para elegir la máscara. */
    private fun penalty(g: Grid): Int {
        val n = g.size
        val m = g.modules
        var result = 0
        // Regla 1: rachas de 5 o más módulos iguales (filas y columnas).
        for (a in 0 until n) for (horizontal in booleanArrayOf(true, false)) {
            var run = 1
            for (b in 1 until n) {
                val same = if (horizontal) m[a][b] == m[a][b - 1] else m[b][a] == m[b - 1][a]
                if (same) {
                    run++
                } else {
                    if (run >= 5) result += 3 + (run - 5)
                    run = 1
                }
            }
            if (run >= 5) result += 3 + (run - 5)
        }
        // Regla 2: bloques 2x2 del mismo color.
        for (y in 0 until n - 1) for (x in 0 until n - 1) {
            val c = m[y][x]
            if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) result += 3
        }
        // Regla 3: patrón tipo buscador 1:1:3:1:1 con cuatro módulos claros a un lado.
        val pattern = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        for (a in 0 until n) for (b in 0..n - 11) {
            var hf = true
            var hr = true
            var vf = true
            var vr = true
            for (k in 0 until 11) {
                if (m[a][b + k] != pattern[k]) hf = false
                if (m[a][b + k] != pattern[10 - k]) hr = false
                if (m[b + k][a] != pattern[k]) vf = false
                if (m[b + k][a] != pattern[10 - k]) vr = false
            }
            if (hf) result += 40
            if (hr) result += 40
            if (vf) result += 40
            if (vr) result += 40
        }
        // Regla 4: proporción de módulos oscuros.
        var dark = 0
        for (row in m) for (c in row) if (c) dark++
        val total = n * n
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        result += k * 10
        return result
    }
}
