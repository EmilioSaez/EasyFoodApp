package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Escritorio: sin cámara; se sube una imagen desde un archivo con el diálogo nativo de AWT. */
private object AwtPhotoSource : PhotoSource {
    override val canTakePhoto = false
    override val canChooseFile = true

    override fun takePhoto(onResult: (PhotoResult) -> Unit) =
        onResult(PhotoResult.Failed("Este equipo no tiene cámara disponible. Sube una imagen desde un archivo."))

    override fun chooseFile(onResult: (PhotoResult) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Elegir foto de la etiqueta", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in IMAGE_EXTENSIONS }
        dialog.isVisible = true
        val name = dialog.file ?: return onResult(PhotoResult.Cancelled)
        onResult(readImage(File(dialog.directory, name)))
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "gif")

internal fun readImage(file: File): PhotoResult = try {
    if (file.length() > MAX_PHOTO_BYTES) {
        PhotoResult.Failed("La imagen es demasiado grande (máximo ${MAX_PHOTO_BYTES / (1024 * 1024)} MB).")
    } else {
        PhotoResult.Done(file.readBytes())
    }
} catch (e: Exception) {
    PhotoResult.Failed("No se pudo leer la imagen: ${e.message ?: "error de lectura"}.")
}

@Composable
actual fun rememberPhotoSource(): PhotoSource = remember { AwtPhotoSource }
