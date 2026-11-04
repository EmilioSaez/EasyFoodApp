package com.easyfood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.easyfood.ui.theme.Spacing

/** Acción de navegación de muestra en una pantalla placeholder. */
class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * Esqueleto temporal de pantalla: barra superior con «atrás» opcional y botones para probar la
 * navegación. Cada pantalla real lo sustituye en su propio archivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    actions: List<PlaceholderAction>,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = "Atrás" },
                        ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions.forEachIndexed { i, a ->
                if (i == 0) Button(onClick = a.onClick) { Text(a.label) }
                else OutlinedButton(onClick = a.onClick) { Text(a.label) }
            }
        }
    }
}
