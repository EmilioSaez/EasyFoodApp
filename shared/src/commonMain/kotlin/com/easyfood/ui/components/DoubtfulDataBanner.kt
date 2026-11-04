package com.easyfood.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyfood.ui.theme.Spacing
import com.easyfood.ui.theme.easyFoodColors

/** Aviso ámbar «Revisa estos datos» con los motivos y la acción de corregir. */
@Composable
fun DoubtfulDataBanner(
    reasons: List<String>,
    onCorrect: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.easyFoodColors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.warningContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("⚠", color = colors.onWarningContainer, style = MaterialTheme.typography.titleMedium)
            Text("Revisa estos datos", color = colors.onWarningContainer, style = MaterialTheme.typography.titleMedium)
        }
        reasons.forEach {
            Text("• $it", color = colors.onWarningContainer, style = MaterialTheme.typography.bodyMedium)
        }
        if (onCorrect != null) {
            TextButton(
                onClick = onCorrect,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.warning),
            ) { Text("Corregir y guardar como propio") }
        }
    }
}
