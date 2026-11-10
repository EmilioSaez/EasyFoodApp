package app.luxion.fitarchiveapp.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual fun downscaleToJpeg(source: ByteArray, maxSide: Int, quality: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // Decodifica ya reducida por potencias de 2 para no cargar una foto de 12 Mpx entera en memoria.
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val rotation = try {
        when (ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    val upright = if (rotation == 0f) decoded else
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
    val (w, h) = planDownscale(upright.width, upright.height, maxSide) ?: return null
    val scaled = if (w == upright.width && h == upright.height) upright else Bitmap.createScaledBitmap(upright, w, h, true)
    // Comprimir a JPEG descarta los metadatos EXIF (ubicación incluida).
    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), it) }.toByteArray()
}
