package com.easyfood.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyfood.model.QuantityUnit
import com.easyfood.ui.theme.Spacing

/**
 * Selector de cantidad: Gramos | Porciones, y fila − [número] +.
 * Paso: 1 porción o 10 g. «Porciones» se desactiva si [servingsEnabled] es false.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantitySelector(
    unit: QuantityUnit,
    onUnitChange: (QuantityUnit) -> Unit,
    amountText: String,
    onAmountTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    servingsEnabled: Boolean = true,
) {
    val step = if (unit == QuantityUnit.GRAMS) 10.0 else 1.0
    fun bump(delta: Double) {
        val next = ((parseNumber(amountText) ?: 0.0) + delta).coerceAtLeast(0.0)
        onAmountTextChange(formatGrams(next).replace(".", ""))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = unit == QuantityUnit.GRAMS,
                onClick = { onUnitChange(QuantityUnit.GRAMS) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Gramos") }
            SegmentedButton(
                selected = unit == QuantityUnit.SERVINGS,
                onClick = { onUnitChange(QuantityUnit.SERVINGS) },
                enabled = servingsEnabled,
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Porciones") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(onClick = { bump(-step) }, modifier = Modifier.size(48.dp)) { Text("−") }
            OutlinedTextField(
                value = amountText,
                onValueChange = { onAmountTextChange(sanitizeNumber(it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                suffix = { Text(if (unit == QuantityUnit.GRAMS) "g" else "porc.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.extraSmall,
            )
            OutlinedIconButton(onClick = { bump(step) }, modifier = Modifier.size(48.dp)) { Text("+") }
        }
    }
}
