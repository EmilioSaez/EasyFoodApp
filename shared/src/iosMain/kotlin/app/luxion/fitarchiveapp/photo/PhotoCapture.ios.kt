package app.luxion.fitarchiveapp.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

actual val photoCameraSupported: Boolean = true

/**
 * Cámara o fototeca con el selector del sistema (`UIImagePickerController`): la foto llega como `UIImage` en memoria,
 * sin archivo. Solo se ha comprobado el tipado desde Windows (docs/ios-pendiente.md).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun PhotoCapture(onPhoto: (ByteArray) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    val currentOnPhoto = rememberUpdatedState(onPhoto)
    val currentOnError = rememberUpdatedState(onError)
    // El delegado se retiene mientras la pantalla esté en composición (UIKit solo guarda una referencia débil).
    val pickerDelegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                picker.dismissViewControllerAnimated(true, null)
                val jpeg = image?.let { UIImageJPEGRepresentation(it, 0.9) }
                val bytes = jpeg?.bytes?.readBytes(jpeg.length.toInt())
                if (bytes != null) currentOnPhoto.value(bytes) else currentOnError.value("No se pudo leer la imagen.")
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
            }
        }
    }
    fun present(source: UIImagePickerControllerSourceType) {
        if (!UIImagePickerController.isSourceTypeAvailable(source)) {
            currentOnError.value(
                if (source == UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera) "No se pudo abrir la cámara."
                else "No se pudo abrir la fototeca.",
            )
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = source
        picker.delegate = pickerDelegate
        var top: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(picker, true, null)
    }
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Haz una foto del plato o elige una de tu fototeca.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Hacer foto") }
        OutlinedButton(
            onClick = { present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Elegir de la fototeca") }
    }
}
