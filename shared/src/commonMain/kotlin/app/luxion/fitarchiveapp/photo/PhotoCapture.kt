package app.luxion.fitarchiveapp.photo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Entrada de una foto para la IA (foto del plato). Android: cámara (CameraX, la foto se queda en memoria) o galería;
 * iOS: cámara o fototeca del sistema; escritorio: elegir una imagen. Entrega los bytes de la imagen tal cual (JPEG o
 * PNG, sin reducir: se reduce después con `downscaleToJpeg`). Nunca se escribe en disco.
 */
@Composable
expect fun PhotoCapture(
    onPhoto: (ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
)

/** `true` si esta plataforma puede hacer la foto con la cámara (en escritorio solo se sube una imagen). */
expect val photoCameraSupported: Boolean
