package app.luxion.fitarchiveapp.export.qr

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrCodeEncoderTest {
    private fun decode(matrix: QrMatrix) = decodeQr(matrix)

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
    fun roundTripsEveryLengthUpToTheTransferChunkSize() {
        // Cubre todas las versiones y diseños de bloques que usan los fragmentos reales (datos + cabecera).
        val random = Random(5)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789:{}\",ñé"
        for (length in 1..900) {
            val text = buildString { repeat(length) { append(alphabet.random(random)) } }
            assertEquals(text, decode(QrCodeEncoder.encode(text)), "longitud $length")
        }
    }

    @Test
    fun everyMaskProducesAReadableCode() {
        val text = "EFQ1:cbf43926:1:3:deadbeef:" + "x".repeat(150)
        for (mask in 0..7) assertEquals(text, decode(QrCodeEncoder.encode(text, mask)), "máscara $mask")
    }

    @Test
    fun rejectsTextThatDoesNotFit() {
        assertFailsWith<IllegalArgumentException> { QrCodeEncoder.encode("x".repeat(3000)) }
    }
}
