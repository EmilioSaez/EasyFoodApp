package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Aviso del Diario cuando toca pesarse; [daysSinceLast] es `null` si nunca se ha registrado el peso. */
@Composable
fun WeightReminderCard(daysSinceLast: Long?, onRegister: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                "Toca pesarse",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                if (daysSinceLast == null) "Aún no has registrado tu peso."
                else "Hace $daysSinceLast días que no registras tu peso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onRegister) { Text("Registrar peso") }
        }
    }
}
