package app.luxion.fitarchiveapp.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

actual val isBarcodeScannerSupported: Boolean = true

/** Estado del permiso de cámara. Tras un rechazo iOS no vuelve a mostrar el diálogo. */
private enum class CameraAccess { Pending, Granted, Denied }

@OptIn(ExperimentalForeignApi::class)
private fun currentAccess(): CameraAccess =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraAccess.Granted
        AVAuthorizationStatusNotDetermined -> CameraAccess.Pending
        else -> CameraAccess.Denied // denegado o restringido
    }

@Composable
actual fun BarcodeScanner(
    onBarcode: (String) -> Unit,
    onError: (ScanError) -> Unit,
    modifier: Modifier,
) {
    val currentOnError by rememberUpdatedState(onError)
    var access by remember { mutableStateOf(currentAccess()) }

    LaunchedEffect(Unit) {
        when (access) {
            CameraAccess.Pending -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok ->
                // El callback llega en una cola arbitraria: se vuelve al hilo principal.
                dispatch_async(dispatch_get_main_queue()) {
                    access = if (ok) CameraAccess.Granted else CameraAccess.Denied
                    if (!ok) currentOnError(ScanError.PermissionDenied(permanently = true))
                }
            }
            // Ya denegado antes: iOS no vuelve a preguntar, solo se puede activar desde Ajustes.
            CameraAccess.Denied -> currentOnError(ScanError.PermissionDenied(permanently = true))
            CameraAccess.Granted -> Unit
        }
    }

    if (access == CameraAccess.Granted) CameraPreview(onBarcode, onError, modifier)
}

/** Recibe los códigos detectados; se guarda en el controlador porque `AVCaptureMetadataOutput` solo mantiene una referencia débil. */
private class MetadataDelegate(private val onCode: (String) -> Unit) :
    NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var delivered = false

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection,
    ) {
        if (delivered) return
        val value = didOutputMetadataObjects
            .firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue }
            ?: return
        delivered = true
        onCode(value)
    }
}

/** Sesión de captura con su capa de previsualización; `start`/`stop` bloquean, por eso van en [queue]. */
private class CameraController(
    private val queue: dispatch_queue_t,
    onBarcode: (String) -> Unit,
) {
    val session = AVCaptureSession()
    val delegate = MetadataDelegate(onBarcode)
    val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    /** Configura y arranca la sesión. Devuelve el error si no hay cámara o no se pudo abrir. */
    @OptIn(ExperimentalForeignApi::class)
    fun configure(): Throwable? {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: return IllegalStateException("No hay cámara disponible")
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: return IllegalStateException("No se pudo abrir la cámara")
        val output = AVCaptureMetadataOutput()
        if (!session.canAddInput(input) || !session.canAddOutput(output)) {
            return IllegalStateException("La sesión de captura no admite la cámara")
        }
        session.addInput(input)
        session.addOutput(output)
        // Los tipos solo se pueden fijar una vez añadida la salida a la sesión.
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code)
        return null
    }

    fun start() = dispatch_async(queue) { if (!session.running) session.startRunning() }

    fun stop() = dispatch_async(queue) { if (session.running) session.stopRunning() }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun CameraPreview(
    onBarcode: (String) -> Unit,
    onError: (ScanError) -> Unit,
    modifier: Modifier,
) {
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnError by rememberUpdatedState(onError)
    val controller = remember {
        CameraController(
            queue = dispatch_queue_create("app.luxion.fitarchiveapp.scanner", null),
            onBarcode = { currentOnBarcode(it) },
        )
    }

    DisposableEffect(controller) {
        val failure = controller.configure()
        if (failure != null) currentOnError(ScanError.CameraUnavailable(failure)) else controller.start()
        // Cierre limpio: se detiene la sesión y se sueltan entradas y salidas para liberar la cámara.
        onDispose {
            controller.stop()
            dispatch_async(dispatch_get_main_queue()) {
                controller.session.inputs.forEach { controller.session.removeInput(it as platform.AVFoundation.AVCaptureInput) }
                controller.session.outputs.forEach { controller.session.removeOutput(it as AVCaptureOutput) }
            }
        }
    }

    UIKitView(
        factory = { PreviewHostView(controller.previewLayer) },
        modifier = modifier.clipToBounds(),
        onRelease = { controller.previewLayer.removeFromSuperlayer() },
    )
}

/** Vista que mantiene la capa de previsualización del tamaño de sus límites. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewHostView(private val previewLayer: AVCaptureVideoPreviewLayer) :
    UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun layoutSubviews() {
        super.layoutSubviews()
        // Sin animación implícita para que la capa siga al tamaño de la vista.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}
