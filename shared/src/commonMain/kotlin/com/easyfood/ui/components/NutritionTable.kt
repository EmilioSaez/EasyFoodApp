package com.easyfood.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyfood.model.Nutrients
import com.easyfood.ui.theme.Spacing
import com.easyfood.ui.theme.easyFoodColors

/** Una fila de la tabla; [value] `null` se muestra como «Sin dato» en ámbar. */
data class NutritionRow(
    val label: String,
    val value: Double?,
    val unit: String,
    val indented: Boolean = false,
    val decimals: Int = 1,
)

/** Filas estándar de la ficha (por 100 g) a partir de los [Nutrients]. */
fun Nutrients.toRows(): List<NutritionRow> = listOf(
    NutritionRow("Energía", kcal, "kcal", decimals = 0),
    NutritionRow("Grasas", fatG, "g"),
    NutritionRow("saturadas", saturatedFatG, "g", indented = true),
    NutritionRow("Hidratos", carbsG, "g"),
    NutritionRow("azúcares", sugarsG, "g", indented = true),
    NutritionRow("Fibra", fiberG, "g"),
    NutritionRow("Proteína", proteinG, "g"),
    NutritionRow("Sal", saltG, "g"),
)

@Composable
fun NutritionTable(
    title: String,
    rows: List<NutritionRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.s)
                    .padding(start = if (row.indented) Spacing.l else 0.dp),
            ) {
                val labelColor =
                    if (row.indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Text(
                    if (row.indented) "↳ ${row.label}" else row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                )
                if (row.value == null) {
                    Text("Sin dato", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.easyFoodColors.warning)
                } else {
                    Text(
                        "${formatNumber(row.value, row.decimals)} ${row.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = labelColor,
                    )
                }
            }
        }
    }
}
