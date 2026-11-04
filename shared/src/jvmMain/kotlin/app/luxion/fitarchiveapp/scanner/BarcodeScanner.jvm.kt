package app.luxion.fitarchiveapp.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isBarcodeScannerSupported: Boolean = false

@Composable
actual fun BarcodeScanner(
    onBarcode: (String) -> Unit,
    onError: (ScanError) -> Unit,
    modifier: Modifier,
    mode: ScanMode,
) = UnsupportedBarcodeScanner(onError)
