package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** «Desayuno · Comida · Cena»: las comidas del preset de [count] comidas al día. */
fun mealsSummary(count: Int): String = MealPlan.preset(count).joinToString(" · ") { it.label }

/** Opciones de comidas al día (3 a 6), cada una con la lista de comidas que resulta (docs/design.md §2.9). */
@Composable
fun MealsPerDayPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MealPlan.options.forEach { count ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = selected == count, onClick = { onSelect(count) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == count, onClick = null)
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    Text("$count comidas", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mealsSummary(count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
