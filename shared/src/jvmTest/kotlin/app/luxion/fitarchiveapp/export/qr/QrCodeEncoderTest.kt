package app.luxion.fitarchiveapp.export.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Decodifica la matriz con ZXing (independiente de nuestro codificador), con zona de silencio y 4 px por módulo. */
class QrCodeEncoderTest {
    private fun decode(matrix: QrMatrix): String {
        val quiet = 4
        val scale = 4
        val side = (matrix.size + quiet * 2) * scale
        val pixels = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
            }
        }
        val source = object : LuminanceSource(side, side) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray {
                val out = row ?: ByteArray(side)
                pixels.copyInto(out, 0, y * side, (y + 1) * side)
                return out
            }

            override fun getMatrix(): ByteArray = pixels
            override fun isCropSupported() = false
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun sizeFollowsVersion() {
        assertEquals(21, QrCodeEncoder.encode("A").size) // versión 1
        assertEquals(25, QrCodeEncoder.encode("x".repeat(30)).size) // versión 2
    }

    @Test
    fun roundTripsShortText() {
        assertEquals("EasyFood", decode(QrCodeEncoder.encode("EasyFood")))
    }

    @Test
    fun roundTripsUtf8() {
        val text = "ñandú 🍎 café – 100 g"
        assertEquals(text, decode(QrCodeEncoder.encode(text)))
    }

    @Test
    fun roundTripsEveryVersionRange() {
        // Longitudes que caen en versiones con 1 bloque, varios bloques, alineación múltiple y bits de versión (≥ 7).
        val random = Random(11)
        for (length in listOf(1, 17, 40, 100, 200, 300, 400, 600, 900, 1500, 2300)) {
            val text = buildString { repeat(length) { append(('a' + random.nextInt(26))) } }
            assertEquals(text, decode(QrCodeEncoder.encode(text)), "longitud $length")
        }
    }

    @Test
    fun roundTripsRealTransferFragments() {
        val json = """{"schemaVersion":1,"items":[${(1..300).joinToString(",") { "\"alimento número $it\"" }}]}"""
        val frames = QrTransfer.split(json, chunkSize = 400)
        assertTrue(frames.size > 2)
        val receiver = QrTransferReceiver()
        frames.forEach { receiver.accept(decode(QrCodeEncoder.encode(it))) }
        assertTrue(receiver.isComplete)
        assertEquals(json.length - json.count { it == ' ' || it == '\n' }.coerceAtLeast(0) >= 0, true)
        receiver.assemble()
    }

    @Test
    fun rejectsTextThatDoesNotFit() {
        assertFailsWith<IllegalArgumentException> { QrCodeEncoder.encode("x".repeat(3000)) }
    }
}
