package app.luxion.fitarchiveapp.files

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Android: la cámara es una vista de CameraX en un diálogo a pantalla completa y la foto se recibe en memoria
 * (`ImageCapture.OnImageCapturedCallback`), sin crear ningún archivo. La galería usa el selector del sistema
 * (`GetContent`), que tampoco copia nada al disco.
 */
@Composable
actual fun rememberPhotoSource(): PhotoSource {
    val context = LocalContext.current
    val pending = remember { PendingPhoto() }
    var cameraOpen by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraOpen = true
        else pending.finish(PhotoResult.Failed("Se necesita el permiso de cámara. Actívalo en Ajustes o elige una imagen de la galería."))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending.finish(
            if (uri == null) PhotoResult.Cancelled else try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_PHOTO_BYTES) }
                if (bytes == null) PhotoResult.Failed("La imagen es demasiado grande o no se pudo abrir.") else PhotoResult.Done(bytes)
            } catch (e: Exception) {
                PhotoResult.Failed("No se pudo leer la imagen: ${e.message ?: "error de lectura"}.")
            },
        )
    }

    if (cameraOpen) {
        Dialog(
            onDismissRequest = { cameraOpen = false; pending.finish(PhotoResult.Cancelled) },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CameraCapture(
                onPhoto = { cameraOpen = false; pending.finish(PhotoResult.Done(it)) },
                onError = { cameraOpen = false; pending.finish(PhotoResult.Failed(it)) },
                onCancel = { cameraOpen = false; pending.finish(PhotoResult.Cancelled) },
            )
        }
    }

    return remember(permission, gallery) {
        object : PhotoSource {
            override val canTakePhoto = true
            override val canChooseFile = true

            override fun takePhoto(onResult: (PhotoResult) -> Unit) {
                pending.callback = onResult
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) cameraOpen = true else permission.launch(Manifest.permission.CAMERA)
            }

            override fun chooseFile(onResult: (PhotoResult) -> Unit) {
                pending.callback = onResult
                gallery.launch("image/*")
            }
        }
    }
}

private class PendingPhoto {
    var callback: ((PhotoResult) -> Unit)? = null

    fun finish(result: PhotoResult) {
        val c = callback
        callback = null
        c?.invoke(result)
    }
}

@Composable
private fun CameraCapture(onPhoto: (ByteArray) -> Unit, onError: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                currentOnError("No se pudo abrir la cámara. Comprueba que ninguna otra app la esté usando.")
            }
        }, mainExecutor)
        onDispose { provider?.unbindAll() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clipToBounds())
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Cancelar") }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = try {
                                    image.toUprightJpeg()
                                } catch (e: Exception) {
                                    null
                                } finally {
                                    image.close()
                                }
                                if (bytes != null) currentOnPhoto(bytes) else currentOnError("No se pudo procesar la foto.")
                            }

                            override fun onError(exception: ImageCaptureException) {
                                currentOnError("No se pudo hacer la foto. Inténtalo de nuevo.")
                            }
                        },
                    )
                },
            ) { Text(if (busy) "Procesando…" else "Hacer foto") }
        }
    }
}

/** Fotograma de CameraX -> JPEG derecho (la rotación viaja aparte, no en EXIF), todo en memoria. */
private fun ImageProxy.toUprightJpeg(): ByteArray? {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    val upright = if (degrees == 0) bitmap else
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    return ByteArrayOutputStream().use { out ->
        if (upright.compress(Bitmap.CompressFormat.JPEG, 92, out)) out.toByteArray() else null
    }
}

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
