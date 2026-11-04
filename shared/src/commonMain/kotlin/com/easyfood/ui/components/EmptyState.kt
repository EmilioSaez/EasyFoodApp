package com.easyfood.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyfood.ui.theme.Spacing
import com.easyfood.ui.theme.easyFoodColors

enum class EmptyStateTone { Neutral, Warning, Error }

class EmptyStateAction(val label: String, val onClick: () -> Unit)

/** Estado vacío / error reutilizable: icono en círculo de 72 dp, título, texto y hasta dos acciones. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: EmptyStateTone = EmptyStateTone.Neutral,
    primaryAction: EmptyStateAction? = null,
    secondaryAction: EmptyStateAction? = null,
) {
    val colors = MaterialTheme.easyFoodColors
    val (container, content) = when (tone) {
        EmptyStateTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        EmptyStateTone.Warning -> colors.warningContainer to colors.onWarningContainer
        EmptyStateTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Column(
        modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(72.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, style = MaterialTheme.typography.headlineSmall, color = content)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        primaryAction?.let { Button(onClick = it.onClick) { Text(it.label) } }
        secondaryAction?.let { OutlinedButton(onClick = it.onClick) { Text(it.label) } }
    }
}
