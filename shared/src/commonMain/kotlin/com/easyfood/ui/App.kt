package com.easyfood.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.ProductLookup
import kotlinx.coroutines.launch

/** Pantalla inicial: buscar un producto por EAN y ver sus calorías por 100 g. */
@Composable
fun App(client: OpenFoodFactsClient = remember { OpenFoodFactsClient() }) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var barcode by remember { mutableStateOf("") }
            var result by remember { mutableStateOf<ProductLookup?>(null) }

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EasyFood", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Código de barras (EAN)") },
                        singleLine = true,
                    )
                    Button(onClick = { scope.launch { result = client.findByBarcode(barcode) } }) {
                        Text("Buscar")
                    }
                }
                when (val r = result) {
                    null -> Unit
                    ProductLookup.NotFound -> Text("Producto no encontrado")
                    is ProductLookup.Failure -> Text("Error de red: ${r.cause.message}")
                    is ProductLookup.Found -> {
                        val p = r.product
                        Text(listOfNotNull(p.name, p.brand).joinToString(" · "))
                        Text("Energía: ${p.nutrients.kcal?.let { "$it kcal/100 g" } ?: "sin datos"}")
                        if (p.nutrients.needsReview) Text("Revisa estos datos: faltan o parecen erróneos")
                    }
                }
            }
        }
    }
}
