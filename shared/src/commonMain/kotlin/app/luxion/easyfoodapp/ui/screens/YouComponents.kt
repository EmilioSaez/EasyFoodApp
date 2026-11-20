package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.ui.theme.Spacing

/**
 * Piezas comunes de las pantallas de «Tú» (rediseño, docs/design.md §2.23): cabecera con título grande, tarjeta, rótulo
 * de sección y fila de enlace. Solo usan los tokens del tema (§1.8).
 */

/** Cabecera de pantalla: [onBack] pinta la flecha «Volver» (`null` en «Tú», que es raíz) y [actions] van a la derecha. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backDescription: String = "Volver",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDescription }) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}

/** Tarjeta clara del diseño: fondo `surfaceContainerLowest`, borde `outlineVariant` y forma `large`. */
@Composable
fun YouCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

/** Rótulo de sección en mayúsculas (labelMedium). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        // Los lectores de pantalla (y las pruebas) leen el texto tal cual, sin deletrear las mayúsculas.
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Fila de una lista dentro de una [YouCard]: título, subtítulo opcional y «›»; toda la fila es pulsable. */
@Composable
fun YouLinkRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lista de [YouLinkRow] separadas por un filete, dentro de una sola tarjeta. */
@Composable
fun YouLinkList(links: List<YouLink>, onOpen: (YouLink) -> Unit, modifier: Modifier = Modifier) {
    YouCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        links.forEachIndexed { i, link ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            YouLinkRow(link.title, link.subtitle, onClick = { onOpen(link) })
        }
    }
}

/** Colores de la tarjeta «hero» de Objetivos: oscura en claro (`inverseSurface`) y `surfaceContainerHigh` en oscuro. */
data class HeroColors(val container: Color, val content: Color, val muted: Color)

@Composable
fun heroColors(): HeroColors {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() > 0.5f) {
        HeroColors(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inverseOnSurface.copy(alpha = 0.75f))
    } else {
        HeroColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant)
    }
}
