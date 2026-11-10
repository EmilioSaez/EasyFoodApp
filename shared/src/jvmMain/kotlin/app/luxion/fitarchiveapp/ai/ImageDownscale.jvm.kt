package app.luxion.fitarchiveapp.ai

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

actual fun downscaleToJpeg(source: ByteArray, maxSide: Int, quality: Int): ByteArray? {
    val image = try {
        ImageIO.read(ByteArrayInputStream(source))
    } catch (e: Exception) {
        null
    } ?: return null
    val (w, h) = planDownscale(image.width, image.height, maxSide) ?: return null
    // JPEG no admite canal alfa: se pinta sobre blanco en un RGB nuevo (también quita los metadatos).
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, w, h)
        g.drawImage(image.getScaledInstance(w, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
    } finally {
        g.dispose()
    }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull() ?: return null
    return try {
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality.coerceIn(1, 100) / 100f
        }
        val bytes = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bytes).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(out, null, null), param)
        }
        bytes.toByteArray()
    } finally {
        writer.dispose()
    }
}
