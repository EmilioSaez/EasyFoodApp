package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.ui.components.MealsPerDayPicker
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Pantalla de la primera vez (docs/design.md §2.9): pregunta cuántas comidas se hacen al día antes de enseñar el
 * Diario. No se puede saltar; la opción por defecto ([MealPlan.DEFAULT_MEALS]) viene marcada.
 * [error] es un fallo al guardar, que se enseña sin cerrar la pantalla; [saving] bloquea el botón mientras se guarda.
 */
@Composable
fun MealsSetupScreen(onConfirm: (Int) -> Unit, error: String? = null, saving: Boolean = false) {
    var selected by remember { mutableStateOf(MealPlan.DEFAULT_MEALS) }
    Scaffold(
        bottomBar = {
            Button(
                onClick = { onConfirm(selected) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(Spacing.l).heightIn(min = 48.dp),
            ) { Text("Continuar") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("¿Cuántas comidas haces al día?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.xl))
            Text(
                "Tu Diario tendrá una sección por cada una. Podrás cambiarlo después en Objetivos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MealsPerDayPicker(selected, { selected = it })
            if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}
