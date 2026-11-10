package app.luxion.fitarchiveapp.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.darwin.NSObject
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController

/**
 * iOS: cámara con `UIImagePickerController` (la foto llega como `UIImage` en memoria, sin pasar por la fototeca).
 * Sin selector de archivos por ahora. Solo se ha comprobado el tipado; ver `docs/ios-pendiente.md`.
 */
private class IosPhotoSource : PhotoSource {
    override val canTakePhoto: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )
    override val canChooseFile = false

    // `UIImagePickerController.delegate` es una referencia débil: hay que conservar el delegado mientras dura la cámara.
    private var activeDelegate: Any? = null

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun takePhoto(onResult: (PhotoResult) -> Unit) {
        val presenter = topViewController()
        if (!canTakePhoto || presenter == null) {
            return onResult(PhotoResult.Failed("No se pudo abrir la cámara. Comprueba el permiso de cámara en Ajustes."))
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                val data = image?.let { UIImageJPEGRepresentation(it, 0.9) }
                val bytes = data?.bytes?.readBytes(data.length.toInt())
                picker.dismissViewControllerAnimated(true, null)
                activeDelegate = null
                onResult(if (bytes != null) PhotoResult.Done(bytes) else PhotoResult.Failed("No se pudo leer la foto."))
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
                activeDelegate = null
                onResult(PhotoResult.Cancelled)
            }
        }
        activeDelegate = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
    }

    override fun chooseFile(onResult: (PhotoResult) -> Unit) =
        onResult(PhotoResult.Failed("Elegir una imagen aún no está disponible en iOS."))

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

@Composable
actual fun rememberPhotoSource(): PhotoSource = remember { IosPhotoSource() }
