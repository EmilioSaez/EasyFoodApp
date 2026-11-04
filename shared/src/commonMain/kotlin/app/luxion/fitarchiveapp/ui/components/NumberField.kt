package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Campo numérico con sufijo de unidad. Acepta «,» o «.» como separador decimal y solo deja
 * dígitos y un separador; el valor se entrega como texto para que el llamador lo parsee con [parseNumber].
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeNumber(it)) },
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.extraSmall,
    )
}

/** Deja solo dígitos y como mucho un separador decimal (normalizado a «,»). */
fun sanitizeNumber(raw: String): String {
    var seenSeparator = false
    return buildString {
        for (c in raw) {
            when {
                c.isDigit() -> append(c)
                (c == ',' || c == '.') && !seenSeparator -> { seenSeparator = true; append(',') }
            }
        }
    }
}

fun parseNumber(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
