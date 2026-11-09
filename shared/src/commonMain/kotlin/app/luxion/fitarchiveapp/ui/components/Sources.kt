package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

enum class DataSource(val label: String, val initial: String) {
    OpenFoodFacts("Open Food Facts", "OFF"),
    Generic("Genérico", "G"),
    Custom("Propio", "P"),
}

@Composable
private fun DataSource.containerColor(): Color = when (this) {
    DataSource.Generic -> MaterialTheme.colorScheme.secondaryContainer
    DataSource.OpenFoodFacts -> MaterialTheme.colorScheme.tertiaryContainer
    DataSource.Custom -> MaterialTheme.easyFoodColors.warningContainer
}

@Composable
private fun DataSource.contentColor(): Color = when (this) {
    DataSource.Generic -> MaterialTheme.colorScheme.onSecondaryContainer
    DataSource.OpenFoodFacts -> MaterialTheme.colorScheme.onTertiaryContainer
    DataSource.Custom -> MaterialTheme.easyFoodColors.onWarningContainer
}

@Composable
fun SourceAvatar(
    source: DataSource,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    /** Cuadrado de esquinas redondeadas en las filas (§2.14); el chip lo pide circular. */
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Box(
        modifier.size(size).background(source.containerColor(), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(source.initial, style = MaterialTheme.typography.labelMedium, color = source.contentColor())
    }
}

@Composable
fun SourceChip(source: DataSource, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(source.label) },
        leadingIcon = { SourceAvatar(source, size = 24.dp, shape = CircleShape) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = source.containerColor(),
            labelColor = source.contentColor(),
        ),
        border = null,
        modifier = modifier,
    )
}

/** Chip «Dudoso» para filas de lista con datos sospechosos. */
@Composable
fun DoubtfulChip(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.easyFoodColors
    Box(
        modifier.background(colors.warningContainer, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Dudoso",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onWarningContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
