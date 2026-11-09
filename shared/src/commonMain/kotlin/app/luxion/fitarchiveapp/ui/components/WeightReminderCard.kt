package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/** Aviso del Diario cuando toca pesarse, a lo ancho (52 dp) en ámbar; [daysSinceLast] es `null` si nunca se ha registrado el peso. */
@Composable
fun WeightReminderCard(daysSinceLast: Long?, onRegister: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.easyFoodColors
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.warningContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = Spacing.l, end = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text("Toca pesarse", style = MaterialTheme.typography.titleMedium, color = colors.onWarningContainer)
                Text(
                    if (daysSinceLast == null) "Aún no has registrado tu peso."
                    else "Hace $daysSinceLast días que no registras tu peso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onWarningContainer,
                )
            }
            TextButton(onClick = onRegister) { Text("Registrar peso", color = colors.onWarningContainer) }
        }
    }
}
