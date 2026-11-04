package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.data.generic.GenericSeedState
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Franja fina con el avance de la carga inicial de alimentos genéricos; no ocupa nada si no hay nada que contar. */
@Composable
fun SeedProgressBanner(state: GenericSeedState, modifier: Modifier = Modifier) {
    when (state) {
        is GenericSeedState.Running -> Column(modifier.fillMaxWidth()) {
            val fraction = state.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                "Preparando la base de alimentos…" + if (fraction != null) " ${(fraction * 100).toInt()} %" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
            )
        }
        is GenericSeedState.Failed -> Text(
            "No se pudo cargar la base de alimentos genéricos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
        )
        GenericSeedState.Idle, GenericSeedState.Done -> Unit
    }
}
