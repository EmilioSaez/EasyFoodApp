package app.luxion.fitarchiveapp.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Implementación para plataformas sin escáner: notifica [ScanError.Unsupported]. */
@Composable
internal fun UnsupportedBarcodeScanner(onError: (ScanError) -> Unit) {
    LaunchedEffect(Unit) { onError(ScanError.Unsupported) }
}
