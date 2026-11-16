package app.luxion.easyfoodapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Tema de EasyFood (docs/design.md §1.7). Los tokens viven en este paquete: `Color.kt` (esquemas y colores extra),
 * `Type.kt` (tipografía), `Shape.kt` (formas) y `Spacing.kt` (espaciado). Sigue al sistema (claro/oscuro); sin color dinámico.
 */
@Composable
fun EasyFoodTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEasyFoodColors provides if (darkTheme) DarkEasyFoodColors else LightEasyFoodColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) EasyFoodDarkColorScheme else EasyFoodLightColorScheme,
            typography = easyFoodTypography(),
            shapes = EasyFoodShapes,
            content = content,
        )
    }
}
