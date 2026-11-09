package app.luxion.fitarchiveapp.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Android: Storage Access Framework (crear / abrir documento), sin permisos de almacenamiento. */
@Composable
actual fun rememberDataFiles(): DataFiles {
    val resolver = LocalContext.current.contentResolver
    val pending = remember { Pending() }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val callback = pending.save
        val content = pending.content
        pending.save = null
        pending.content = null
        if (callback != null) {
            callback(if (uri == null || content == null) FileResult.Cancelled else resolver.write(uri, content))
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pending.open
        pending.open = null
        if (callback != null) callback(if (uri == null) FileResult.Cancelled else resolver.read(uri))
    }
    return remember(saver, opener) {
        object : DataFiles {
            override val canSave = true
            override val canOpen = true

            override fun save(suggestedName: String, content: String, onResult: (FileResult<String>) -> Unit) {
                pending.save = onResult
                pending.content = content
                saver.launch(suggestedName)
            }

            override fun open(onResult: (FileResult<String>) -> Unit) {
                pending.open = onResult
                // Los .json llegan con tipos MIME dispares según el proveedor, así que no se filtra.
                opener.launch(arrayOf("*/*"))
            }
        }
    }
}

private class Pending {
    var save: ((FileResult<String>) -> Unit)? = null
    var content: String? = null
    var open: ((FileResult<String>) -> Unit)? = null
}

private fun ContentResolver.write(uri: Uri, content: String): FileResult<String> = try {
    val out = openOutputStream(uri, "wt") ?: error("sin acceso al destino")
    out.use { it.write(content.encodeToByteArray()) }
    FileResult.Done(displayName(uri))
} catch (e: Exception) {
    FileResult.Failed("No se pudo guardar el archivo: ${e.message ?: "error de escritura"}.")
}

private fun ContentResolver.read(uri: Uri): FileResult<String> = try {
    val input = openInputStream(uri) ?: error("sin acceso al archivo")
    val bytes = input.use { it.readLimited(MAX_IMPORT_BYTES) }
    if (bytes == null) {
        FileResult.Failed("El archivo es demasiado grande para importarlo.")
    } else {
        FileResult.Done(bytes.decodeToString())
    }
} catch (e: Exception) {
    FileResult.Failed("No se pudo leer el archivo: ${e.message ?: "error de lectura"}.")
}

/** Lee todo el flujo; `null` si supera [limit] bytes (en SAF no hay un tamaño fiable de antemano). */
private fun InputStream.readLimited(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        if (out.size() + n > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private fun ContentResolver.displayName(uri: Uri): String =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "exportación.json"
