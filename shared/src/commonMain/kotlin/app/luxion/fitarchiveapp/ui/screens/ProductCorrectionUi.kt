package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.NutritionTable
import app.luxion.fitarchiveapp.ui.components.toRows
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Acciones sobre los datos de un producto de la base (OFF o genérico): corregir los valores en local, volver al
 * original y abrir el producto en Open Food Facts. Si ya está corregido muestra «Corregido por ti» con los valores
 * originales.
 */
@Composable
internal fun ProductCorrectionSection(
    product: Product,
    busy: Boolean,
    error: String?,
    onSave: (Nutrients) -> Unit,
    onRevert: () -> Unit,
) {
    var editing by remember(product) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val offUrl = product.openFoodFactsUrl()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (product.isCorrected) {
                Text("Corregido por ti", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Estos valores son los que has puesto tú; se conservan aunque el producto se actualice. " +
                        "Solo están en este dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                product.originalNutrients?.let { NutritionTable("Valores originales por 100 g", it.toRows()) }
            } else {
                Text(
                    "¿Algún valor no es correcto? Puedes cambiarlo solo en tu dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = { editing = true }, enabled = !busy) {
                    Text(if (product.isCorrected) "Editar corrección" else "Corregir valores")
                }
                if (product.isCorrected) {
                    OutlinedButton(onClick = onRevert, enabled = !busy) { Text("Volver al original") }
                }
            }
            if (offUrl != null) {
                TextButton(onClick = { uriHandler.openUri(offUrl) }) { Text("Corregir en Open Food Facts") }
                Text(
                    "Se abre la página del producto en el navegador. No se envía nada automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (editing) {
        CorrectionDialog(
            initial = product.nutrients,
            onDismiss = { editing = false },
            onConfirm = { editing = false; onSave(it) },
        )
    }
}

@Composable
private fun CorrectionDialog(initial: Nutrients, onDismiss: () -> Unit, onConfirm: (Nutrients) -> Unit) {
    var form by remember { mutableStateOf(correctionFormStateOf(initial)) }
    var showErrors by remember { mutableStateOf(false) }
    val errors = validateCorrection(form, requireAll = showErrors)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir valores por 100 g") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CorrectionRow(
                    CorrectionField("Energía", form.kcal, "kcal", errors.kcal) { form = form.copy(kcal = it) },
                    CorrectionField("Proteína", form.protein, "g", errors.protein) { form = form.copy(protein = it) },
                )
                CorrectionRow(
                    CorrectionField("Hidratos", form.carbs, "g", errors.carbs) { form = form.copy(carbs = it) },
                    CorrectionField("Grasas", form.fat, "g", errors.fat) { form = form.copy(fat = it) },
                )
                CorrectionRow(
                    CorrectionField("Saturadas", form.saturated, "g", errors.saturated) { form = form.copy(saturated = it) },
                    CorrectionField("Azúcares", form.sugars, "g", errors.sugars) { form = form.copy(sugars = it) },
                )
                CorrectionRow(
                    CorrectionField("Fibra", form.fiber, "g", errors.fiber) { form = form.copy(fiber = it) },
                    CorrectionField("Sal", form.salt, "g", errors.salt) { form = form.copy(salt = it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                form.toCorrectedNutrients()?.let(onConfirm)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private class CorrectionField(
    val label: String,
    val value: String,
    val suffix: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

@Composable
private fun CorrectionRow(left: CorrectionField, right: CorrectionField) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(left, right).forEach { f ->
            NumberField(
                value = f.value, onValueChange = f.onChange, label = f.label, suffix = f.suffix,
                isError = f.error != null, supportingText = f.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
