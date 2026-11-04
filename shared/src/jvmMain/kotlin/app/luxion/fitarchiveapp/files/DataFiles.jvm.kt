package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Escritorio: diálogo de archivo nativo de AWT. */
private object AwtDataFiles : DataFiles {
    override val canSave = true
    override val canOpen = true

    override fun save(suggestedName: String, content: String, onResult: (FileResult<String>) -> Unit) {
        val file = choose(FileDialog.SAVE, suggestedName) ?: return onResult(FileResult.Cancelled)
        onResult(
            try {
                file.writeText(content)
                FileResult.Done(file.name)
            } catch (e: Exception) {
                FileResult.Failed("No se pudo guardar el archivo: ${e.message ?: "error de escritura"}.")
            },
        )
    }

    override fun open(onResult: (FileResult<String>) -> Unit) {
        val file = choose(FileDialog.LOAD, null) ?: return onResult(FileResult.Cancelled)
        onResult(
            try {
                if (file.length() > MAX_IMPORT_BYTES) {
                    FileResult.Failed("El archivo es demasiado grande para importarlo.")
                } else {
                    FileResult.Done(file.readText())
                }
            } catch (e: Exception) {
                FileResult.Failed("No se pudo leer el archivo: ${e.message ?: "error de lectura"}.")
            },
        )
    }

    private fun choose(mode: Int, name: String?): File? {
        val title = if (mode == FileDialog.SAVE) "Guardar exportación" else "Elegir archivo"
        val dialog = FileDialog(null as Frame?, title, mode)
        if (name != null) dialog.file = name
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return File(dialog.directory, file)
    }
}

@Composable
actual fun rememberDataFiles(): DataFiles = remember { AwtDataFiles }
