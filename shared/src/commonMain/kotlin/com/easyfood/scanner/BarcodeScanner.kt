package com.easyfood.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Motivos por los que el escáner no puede entregar un código. */
sealed interface ScanError {
    /** La plataforma no incluye escáner (escritorio, iOS por ahora). */
    data object Unsupported : ScanError

    /** El usuario denegó el permiso de cámara; [permanently] si hay que activarlo desde Ajustes. */
    data class PermissionDenied(val permanently: Boolean) : ScanError

    /** No hay cámara o no se pudo abrir. */
    data class CameraUnavailable(val cause: Throwable? = null) : ScanError
}

/** Texto en español para mostrar al usuario. */
fun ScanError.message(): String = when (this) {
    ScanError.Unsupported -> "El escáner no está disponible en esta plataforma. Escribe el código a mano."
    is ScanError.PermissionDenied ->
        if (permanently) "Permiso de cámara denegado. Actívalo en Ajustes para escanear."
        else "Se necesita el permiso de cámara para escanear."
    is ScanError.CameraUnavailable -> "No se pudo abrir la cámara."
}

/** `true` si esta plataforma puede escanear códigos de barras con la cámara. */
expect val isBarcodeScannerSupported: Boolean

/**
 * Vista de escáner: pide el permiso de cámara, muestra la previsualización y llama a [onBarcode]
 * con el primer código EAN/UPC leído. Los fallos se notifican con [onError].
 */
@Composable
expect fun BarcodeScanner(
    onBarcode: (String) -> Unit,
    onError: (ScanError) -> Unit,
    modifier: Modifier = Modifier,
)
