package app.luxion.easyfoodapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.luxion.easyfoodapp.ui.theme.PillShape
import app.luxion.easyfoodapp.ui.theme.Spacing
import app.luxion.easyfoodapp.ui.theme.easyFoodColors

/**
 * Tarjeta rápida de peso del Diario (media anchura): último peso («78,4 kg») y, o bien el cambio respecto al anterior
 * ([deltaText]), o bien —si [due], toca pesarse— el aviso en ámbar con los días desde el último registro. Toda la tarjeta
 * abre el registro de peso. [daysSinceLast] es `null` si nunca se ha registrado.
 */
@Composable
fun WeightReminderCard(
    lastKg: Double?,
    deltaText: String?,
    due: Boolean,
    daysSinceLast: Long?,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.easyFoodColors
    OutlinedCard(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onRegister)
                .semantics { contentDescription = "Registrar peso" }
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("PESO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (lastKg == null) buildAnnotatedString { append("—") } else buildAnnotatedString {
                    append(formatGrams(lastKg))
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily)) { append(" kg") }
                },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
            if (due) {
                Badge("Toca pesarse", colors.warningContainer, colors.onWarningContainer)
                Text(
                    if (daysSinceLast == null) "Aún no has registrado tu peso."
                    else "Hace $daysSinceLast días que no registras tu peso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (deltaText != null) {
                Badge(deltaText, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun Badge(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = Modifier.clip(PillShape).background(container).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}
