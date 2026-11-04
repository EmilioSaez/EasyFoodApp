package com.easyfood.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.easyfood.ui.theme.Spacing

/** Fila del diario: nombre · «cantidad · origen» · chip «Dudoso» · kcal a la derecha. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiaryEntryRow(
    name: String,
    quantityText: String,
    source: DataSource,
    kcal: Double?,
    modifier: Modifier = Modifier,
    doubtful: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$quantityText · ${source.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (doubtful) DoubtfulChip(Modifier.padding(start = Spacing.s))
            }
        },
        trailingContent = {
            Text(
                kcal?.let { formatKcal(it) } ?: "—",
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}
