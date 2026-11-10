package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable

/** Resultado de pedir una foto. Los bytes viven solo en memoria: nada de esto se escribe en disco. */
sealed interface PhotoResult {
    class Done(val bytes: ByteArray) : PhotoResult
    data object Cancelled : PhotoResult
    data class Failed(val message: String) : PhotoResult
}

/** Tamaño máximo de una imagen elegida de un archivo (una foto de móvil de 12 Mpx ronda los 5 MB). */
const val MAX_PHOTO_BYTES = 20 * 1024 * 1024

/** De dónde puede venir una foto en esta plataforma: cámara (Android, iOS) y/o un archivo de imagen (Android, escritorio). */
interface PhotoSource {
    val canTakePhoto: Boolean
    val canChooseFile: Boolean

    /** Abre la cámara y devuelve la foto hecha, en memoria. */
    fun takePhoto(onResult: (PhotoResult) -> Unit)

    /** Pide una imagen (JPEG, PNG...) con el selector del sistema y devuelve su contenido, en memoria. */
    fun chooseFile(onResult: (PhotoResult) -> Unit)
}

@Composable
expect fun rememberPhotoSource(): PhotoSource
