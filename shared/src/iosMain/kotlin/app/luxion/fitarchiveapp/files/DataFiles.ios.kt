package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Pendiente (docs/ios.md): UIDocumentPickerViewController y UIActivityViewController. Sin Mac no se
// puede probar, así que por ahora iOS solo transfiere por QR y red local.
private object UnsupportedDataFiles : DataFiles {
    override val canSave = false
    override val canOpen = false

    override fun save(suggestedName: String, content: String, onResult: (FileResult<String>) -> Unit) =
        onResult(FileResult.Failed("Guardar archivos aún no está disponible en iOS."))

    override fun open(onResult: (FileResult<String>) -> Unit) =
        onResult(FileResult.Failed("Abrir archivos aún no está disponible en iOS."))
}

@Composable
actual fun rememberDataFiles(): DataFiles = remember { UnsupportedDataFiles }
