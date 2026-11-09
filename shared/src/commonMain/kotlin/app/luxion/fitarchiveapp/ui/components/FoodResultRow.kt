package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Resultado de búsqueda: avatar de origen · nombre / «marca · tamaño · EAN» · kcal por 100 g. */
@Composable
fun FoodResultRow(
    name: String,
    detail: String,
    source: DataSource,
    kcalPer100g: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** `null` = sin estrella; si no, si el alimento es favorito y qué hacer al tocarla. */
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    ListItem(
        modifier = modifier.heightIn(min = 72.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SourceAvatar(source, size = 44.dp) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(kcalPer100g?.let { formatKcal(it) } ?: "—", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "kcal/100 g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (favorite != null) FavoriteStar(favorite, onToggleFavorite, name)
            }
        },
    )
}

/** Estrella de favorito: ★ si lo es, ☆ si no. [foodName] solo se usa para la descripción de accesibilidad. */
@Composable
fun FavoriteStar(favorite: Boolean, onToggle: () -> Unit, foodName: String, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp).semantics {
            contentDescription = if (favorite) "Quitar $foodName de favoritos" else "Añadir $foodName a favoritos"
        },
    ) {
        Text(
            if (favorite) "★" else "☆",
            style = MaterialTheme.typography.titleLarge,
            color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
