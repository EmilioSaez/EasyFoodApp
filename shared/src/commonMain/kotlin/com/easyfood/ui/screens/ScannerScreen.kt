package com.easyfood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.easyfood.data.ProductRepository
import com.easyfood.data.ProductResult
import com.easyfood.model.EanValidation
import com.easyfood.model.EanValidator
import com.easyfood.scanner.BarcodeScanner
import com.easyfood.scanner.ScanError
import com.easyfood.scanner.isBarcodeScannerSupported
import com.easyfood.scanner.message
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Escáner + EAN manual (docs/design.md §2.3). De momento conserva la búsqueda por EAN que había
 * en la pantalla inicial; la sesión que implemente el escáner la rediseña.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(navigator: Navigator, route: Route.Scanner, repository: ProductRepository) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Escanear código") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        val scope = rememberCoroutineScope()
        var barcode by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ProductResult?>(null) }
        var validationError by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var scanning by remember { mutableStateOf(false) }
        var scanError by remember { mutableStateOf<ScanError?>(null) }

        fun search() {
            if (loading) return
            when (val v = EanValidator.validate(barcode)) {
                is EanValidation.Invalid -> {
                    validationError = v.reason
                    result = null
                }
                is EanValidation.Valid -> {
                    validationError = null
                    loading = true
                    scope.launch {
                        result = try {
                            repository.getByBarcode(v.ean)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ProductResult.Failure(e)
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        }

        fun onScanned(code: String) {
            scanning = false
            barcode = code
            search()
        }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it; validationError = null },
                    label = { Text("Código de barras (EAN)") },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                Button(onClick = ::search, enabled = !loading) {
                    Text(if (loading) "Buscando…" else "Buscar")
                }
            }
            if (isBarcodeScannerSupported) {
                OutlinedButton(onClick = { scanError = null; scanning = !scanning }) {
                    Text(if (scanning) "Cerrar escáner" else "Escanear código")
                }
            }
            if (scanning) {
                BarcodeScanner(
                    onBarcode = ::onScanned,
                    onError = { scanning = false; scanError = it },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            }
            scanError?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            when (val r = result) {
                null -> Unit
                ProductResult.NotFound -> {
                    Text("Producto no encontrado")
                    OutlinedButton(onClick = { navigator.replace(Route.CustomFood(barcode, route.meal)) }) {
                        Text("Crear con este código")
                    }
                }
                is ProductResult.Failure ->
                    Text("No se pudo consultar el producto: ${r.cause.message ?: "error de red"}")
                is ProductResult.Found -> {
                    val p = r.product
                    Text(listOfNotNull(p.name, p.brand).joinToString(" · "))
                    Text("Energía: ${p.nutrients.kcal?.let { "$it kcal/100 g" } ?: "sin datos"}")
                    if (r.stale) Text("Sin conexión: datos guardados, pueden estar desactualizados")
                    if (p.nutrients.needsReview) Text("Revisa estos datos: faltan o parecen erróneos")
                    Button(onClick = { navigator.replace(Route.ProductDetail(p.barcode, route.meal)) }) {
                        Text("Ver ficha")
                    }
                }
            }
        }
    }
}
