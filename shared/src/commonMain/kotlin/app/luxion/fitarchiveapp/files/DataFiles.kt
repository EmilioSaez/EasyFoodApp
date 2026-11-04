package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable

/** Resultado de una operación de archivo iniciada por el usuario. */
sealed interface FileResult<out T> {
    data class Done<T>(val value: T) : FileResult<T>

    /** El usuario cerró el selector sin elegir nada. */
    data object Cancelled : FileResult<Nothing>

    data class Failed(val message: String) : FileResult<Nothing>
}

/** Guardar y abrir archivos de texto con el selector del sistema. Cada plataforma lo implementa a su manera. */
interface DataFiles {
    val canSave: Boolean
    val canOpen: Boolean

    /** Pide dónde guardar [content] (con [suggestedName]) y lo escribe. Devuelve el nombre elegido. */
    fun save(suggestedName: String, content: String, onResult: (FileResult<String>) -> Unit)

    /** Pide un archivo y devuelve su contenido de texto. */
    fun open(onResult: (FileResult<String>) -> Unit)
}

/** Tamaño máximo de un archivo de importación (mismo límite que la transferencia por red local). */
const val MAX_IMPORT_BYTES = 32 * 1024 * 1024

@Composable
expect fun rememberDataFiles(): DataFiles
