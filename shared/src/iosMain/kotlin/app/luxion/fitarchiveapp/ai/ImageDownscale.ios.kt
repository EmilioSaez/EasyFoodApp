package app.luxion.fitarchiveapp.ai

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun downscaleToJpeg(source: ByteArray, maxSide: Int, quality: Int): ByteArray? {
    if (source.isEmpty()) return null
    val data = source.usePinned { NSData.create(bytes = it.addressOf(0), length = source.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return null
    // `UIImage.size` ya tiene aplicada la orientación EXIF, y `drawInRect` la respeta.
    val (width, height) = image.size.useContents { width to height }
    val (w, h) = planDownscale(width.toInt(), height.toInt(), maxSide) ?: return null
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w.toDouble(), h.toDouble()), true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val jpeg = scaled?.let { UIImageJPEGRepresentation(it, quality.coerceIn(1, 100) / 100.0) } ?: return null
    return jpeg.bytes?.readBytes(jpeg.length.toInt())
}
