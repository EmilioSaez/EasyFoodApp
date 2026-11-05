package app.luxion.fitarchiveapp.export.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Decodifica una [QrMatrix] con ZXing, independiente de nuestro codificador: la dibuja con zona de silencio
 * y [scale] px por módulo y la lee como imagen.
 *
 * Usa `PURE_BARCODE` (el modo de ZXing para imágenes limpias, sin perspectiva ni ruido). Sin él, el
 * detector de ZXing falla con ~3 % de los QR sintéticos perfectos, incluidos los de su propio
 * `QRCodeWriter` (comprobado con 1200 longitudes de texto); el contenido de esos QR es correcto.
 */
internal fun decodeQr(matrix: QrMatrix, scale: Int = 4): String {
    val quiet = 4
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
    val hints = mapOf<DecodeHintType, Any>(DecodeHintType.PURE_BARCODE to true)
    return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
}
