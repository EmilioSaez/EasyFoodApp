package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Se muestra en lugar de la app si la base de datos no se pudo actualizar (los datos siguen a salvo, ver [message]). */
@Composable
fun MigrationErrorScreen(message: String) {
    EasyFoodTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m, androidx.compose.ui.Alignment.CenterVertically),
            ) {
                Text("No se pudo abrir tu base de datos", style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Cierra la app y vuelve a abrirla. Si el problema sigue, no borres los datos de la app: las copias " +
                        "anteriores a la actualización siguen guardadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
