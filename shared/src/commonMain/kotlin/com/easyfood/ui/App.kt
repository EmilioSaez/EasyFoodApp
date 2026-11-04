package com.easyfood.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.easyfood.data.ProductRepository
import com.easyfood.data.ProductResult
import com.easyfood.model.EanValidation
import com.easyfood.model.EanValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pantalla inicial: buscar un producto por EAN (entrada manual) y ver sus calorías por 100 g. */
@Composable
fun App(repository: ProductRepository) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var barcode by remember { mutableStateOf("") }
            var result by remember { mutableStateOf<ProductResult?>(null) }
            var validationError by remember { mutableStateOf<String?>(null) }
            var loading by remember { mutableStateOf(false) }

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

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EasyFood", style = MaterialTheme.typography.headlineMedium)
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
                when (val r = result) {
                    null -> Unit
                    ProductResult.NotFound -> Text("Producto no encontrado")
                    is ProductResult.Failure ->
                        Text("No se pudo consultar el producto: ${r.cause.message ?: "error de red"}")
                    is ProductResult.Found -> {
                        val p = r.product
                        Text(listOfNotNull(p.name, p.brand).joinToString(" · "))
                        Text("Energía: ${p.nutrients.kcal?.let { "$it kcal/100 g" } ?: "sin datos"}")
                        if (r.stale) Text("Sin conexión: datos guardados, pueden estar desactualizados")
                        if (p.nutrients.needsReview) Text("Revisa estos datos: faltan o parecen erróneos")
                    }
                }
            }
        }
    }
}
