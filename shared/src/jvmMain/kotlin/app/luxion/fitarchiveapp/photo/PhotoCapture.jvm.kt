package app.luxion.fitarchiveapp.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual val photoCameraSupported: Boolean = false

/** Máximo que se lee de un archivo elegido (una foto de móvil pesa 2 a 10 MB). */
private const val MAX_BYTES = 30L * 1024 * 1024

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp")

@Composable
actual fun PhotoCapture(onPhoto: (ByteArray) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "En escritorio no hay cámara: elige una foto de tu plato (JPG o PNG).",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = {
            val file = chooseImage()
            if (file != null) {
                when {
                    file.extension.lowercase() !in imageExtensions -> onError("Elige un archivo de imagen (JPG o PNG).")
                    file.length() > MAX_BYTES -> onError("La imagen es demasiado grande.")
                    else -> try {
                        onPhoto(file.readBytes())
                    } catch (e: Exception) {
                        onError("No se pudo leer el archivo.")
                    }
                }
            }
        }) { Text("Elegir imagen…") }
    }
}

private fun chooseImage(): File? {
    val dialog = FileDialog(null as Frame?, "Elegir foto del plato", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in imageExtensions }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
