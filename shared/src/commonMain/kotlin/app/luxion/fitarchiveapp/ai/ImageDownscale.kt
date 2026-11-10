package app.luxion.fitarchiveapp.ai

/** Lado máximo de las imágenes que se envían a la IA. */
const val AI_IMAGE_MAX_SIDE = 1024

/** Calidad JPEG (0 a 100) de las imágenes que se envían. */
const val AI_IMAGE_JPEG_QUALITY = 80

/**
 * Tamaño de destino al reducir una imagen de [width]×[height] para que su lado mayor no pase de [maxSide],
 * conservando la proporción. Nunca agranda. Devuelve `null` si las medidas no son válidas.
 */
fun planDownscale(width: Int, height: Int, maxSide: Int = AI_IMAGE_MAX_SIDE): Pair<Int, Int>? {
    if (width <= 0 || height <= 0 || maxSide <= 0) return null
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toDouble() / longest
    return maxOf(1, kotlin.math.round(width * scale).toInt()) to maxOf(1, kotlin.math.round(height * scale).toInt())
}

/**
 * Decodifica una imagen (JPEG, PNG...), la gira según su orientación si la plataforma la expone, la reduce a un
 * lado mayor de [maxSide] px y la devuelve como JPEG sin metadatos. `null` si no se puede decodificar.
 */
expect fun downscaleToJpeg(
    source: ByteArray,
    maxSide: Int = AI_IMAGE_MAX_SIDE,
    quality: Int = AI_IMAGE_JPEG_QUALITY,
): ByteArray?
