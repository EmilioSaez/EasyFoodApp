package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.ProductResult
import app.luxion.fitarchiveapp.model.EanValidation
import app.luxion.fitarchiveapp.model.EanValidator
import app.luxion.fitarchiveapp.scanner.BarcodeScanner
import app.luxion.fitarchiveapp.scanner.ScanError
import app.luxion.fitarchiveapp.scanner.isBarcodeScannerSupported
import app.luxion.fitarchiveapp.scanner.rememberAppSettingsOpener
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.EanTextStyle
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MAX_EAN_DIGITS = 14

/** Resultado de una búsqueda que no llegó a la ficha. */
private sealed interface Outcome {
    val ean: String

    data class NotFound(override val ean: String) : Outcome
    data class Failed(override val ean: String, val reason: String?) : Outcome
}

/**
 * Escáner + EAN manual (docs/design.md §2.3). Con cámara (Android, iOS) escanea; sin ella (escritorio)
 * solo queda la entrada manual. Al encontrar el producto navega a la ficha.
 */
@Composable
fun ScannerScreen(navigator: Navigator, route: Route.Scanner, repository: ProductRepository) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val openSettings = rememberAppSettingsOpener()
    val eanFocus = remember { FocusRequester() }

    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var cameraError by remember { mutableStateOf<ScanError?>(null) }
    // Cambiarla recrea la vista de cámara (reintentar tras un permiso o tras «Escanear otro»).
    var cameraKey by remember { mutableIntStateOf(0) }

    val validation = EanValidator.validate(input)
    val digits = input.length
    val showCamera = isBarcodeScannerSupported && cameraError == null && outcome == null && !loading

    fun lookup(ean: String) {
        if (loading) return
        loading = true
        outcome = null
        scope.launch {
            val result = try {
                repository.getByBarcode(ean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProductResult.Failure(e)
            }
            loading = false
            when (result) {
                is ProductResult.Found -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    navigator.replace(Route.ProductDetail(result.product.barcode, route.meal))
                }
                ProductResult.NotFound -> outcome = Outcome.NotFound(ean)
                is ProductResult.Failure -> outcome = Outcome.Failed(ean, result.cause.message)
            }
        }
    }

    fun onScanned(code: String) {
        when (val v = EanValidator.validate(code)) {
            is EanValidation.Valid -> {
                input = v.ean
                lookup(v.ean)
            }
            // Lectura corrupta: se vuelve a la cámara para intentarlo otra vez.
            is EanValidation.Invalid -> cameraKey++
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
        if (showCamera) {
            key(cameraKey) {
                BarcodeScanner(
                    onBarcode = ::onScanned,
                    onError = { error -> if (error != ScanError.Unsupported) cameraError = error },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ScanFrame(Modifier.align(Alignment.Center).padding(bottom = 120.dp))
        } else {
            Box(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 280.dp), contentAlignment = Alignment.Center) {
                val current = outcome
                val camError = cameraError
                when {
                    loading -> Text("Buscando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    current is Outcome.NotFound -> EmptyState(
                        icon = "?",
                        title = "Código no encontrado",
                        message = "No hay ningún producto con el código ${current.ean}.",
                        tone = EmptyStateTone.Warning,
                        primaryAction = EmptyStateAction("Crear con este código") {
                            navigator.replace(Route.CustomFood(current.ean, route.meal))
                        },
                        secondaryAction = EmptyStateAction("Escanear otro") {
                            outcome = null
                            input = ""
                            cameraError = null
                            cameraKey++
                            if (!isBarcodeScannerSupported) eanFocus.requestFocus()
                        },
                    )
                    current is Outcome.Failed -> EmptyState(
                        icon = "!",
                        title = "No se pudo consultar",
                        message = "Open Food Facts no responde. Comprueba la conexión e inténtalo de nuevo.",
                        tone = EmptyStateTone.Warning,
                        primaryAction = EmptyStateAction("Reintentar") { lookup(current.ean) },
                        secondaryAction = EmptyStateAction("Crear con este código") {
                            navigator.replace(Route.CustomFood(current.ean, route.meal))
                        },
                    )
                    camError != null -> CameraErrorState(
                        error = camError,
                        onOpenSettings = openSettings,
                        onRetry = { cameraError = null; cameraKey++ },
                        onType = { eanFocus.requestFocus() },
                    )
                    else -> EmptyState(
                        icon = "▥",
                        title = "Escribe el código",
                        message = "Introduce el código de barras del producto para buscarlo.",
                    )
                }
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { navigator.pop() },
                modifier = Modifier.semantics { contentDescription = "Cerrar" },
            ) { Text("✕", style = MaterialTheme.typography.titleLarge) }
            Text("Escanear código", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        ManualEanSheet(
            input = input,
            digits = digits,
            error = (validation as? EanValidation.Invalid)?.reason.takeIf { digits >= 8 },
            canSearch = validation is EanValidation.Valid && !loading,
            loading = loading,
            onInput = { text ->
                input = text.filter { it in '0'..'9' }.take(MAX_EAN_DIGITS)
                if (outcome != null) outcome = null
            },
            onSearch = { (validation as? EanValidation.Valid)?.let { lookup(it.ean) } },
            focusRequester = eanFocus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(
            Modifier.size(width = 300.dp, height = 190.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l).height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            "Apunta al código de barras",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(Spacing.s),
        )
    }
}

@Composable
private fun CameraErrorState(
    error: ScanError,
    onOpenSettings: (() -> Unit)?,
    onRetry: () -> Unit,
    onType: () -> Unit,
) {
    val denied = error as? ScanError.PermissionDenied
    val primary = when {
        denied?.permanently == true && onOpenSettings != null -> EmptyStateAction("Abrir ajustes", onOpenSettings)
        else -> EmptyStateAction(if (denied != null) "Conceder permiso" else "Reintentar", onRetry)
    }
    EmptyState(
        icon = "▣",
        title = if (denied != null) "Sin acceso a la cámara" else "No se pudo abrir la cámara",
        message = when {
            denied == null -> "Comprueba que ninguna otra app la esté usando, o escribe el código a mano."
            denied.permanently -> "Activa el permiso de cámara en Ajustes para escanear, o escribe el código a mano."
            else -> "Se necesita el permiso de cámara para escanear, o puedes escribir el código a mano."
        },
        tone = EmptyStateTone.Error,
        primaryAction = primary,
        secondaryAction = EmptyStateAction("Escribir código", onType),
    )
}

@Composable
private fun ManualEanSheet(
    input: String,
    digits: Int,
    error: String?,
    canSearch: Boolean,
    loading: Boolean,
    onInput: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .padding(horizontal = Spacing.xxl, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.width(32.dp).height(4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )
        Text("¿No se lee el código?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Text(
            "Escríbelo tal como aparece bajo las barras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text("Código EAN") },
            textStyle = MaterialTheme.typography.bodyLarge.merge(EanTextStyle),
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: "8, 12, 13 o 14 dígitos · $digits") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (canSearch) onSearch() }),
        )
        Button(onClick = onSearch, enabled = canSearch, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (loading) "Buscando…" else "Buscar producto")
        }
    }
}
