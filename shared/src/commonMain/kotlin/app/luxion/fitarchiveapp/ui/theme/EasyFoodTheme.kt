package app.luxion.fitarchiveapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colores que Material 3 no define (macros y avisos de datos dudosos). */
data class EasyFoodColors(
    val macroProtein: Color = Color(0xFFFF5449),
    val macroCarbs: Color = Color(0xFFFFB067),
    val macroFat: Color = Color(0xFFBDBDBD),
    val warningContainer: Color = Color(0xFF4D3800),
    val onWarningContainer: Color = Color(0xFFFFDF9E),
    val warning: Color = Color(0xFFFFC94D),
)

val LocalEasyFoodColors = staticCompositionLocalOf { EasyFoodColors() }

/** Acceso corto: `MaterialTheme.easyFoodColors`. */
val MaterialTheme.easyFoodColors: EasyFoodColors
    @Composable @ReadOnlyComposable get() = LocalEasyFoodColors.current

val EasyFoodDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5449),
    onPrimary = Color(0xFF1A0000),
    primaryContainer = Color(0xFF8C0009),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB7),
    secondaryContainer = Color(0xFF4A1C19),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFF2F2F2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E0E0E),
    surface = Color(0xFF0E0E0E),
    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353535),
    onSurface = Color(0xFFF2F2F2),
    onBackground = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3D3D3D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFFC00010),
)

/** Tamaños y pesos de docs/design.md §1.3 sobre la escala por defecto de M3. */
val EasyFoodTypography = Typography(
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Para EAN y números tabulares (monoespaciada del sistema hasta empaquetar Roboto Mono). */
val EanTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)

val EasyFoodShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val PillShape = CircleShape

@Composable
fun EasyFoodTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEasyFoodColors provides EasyFoodColors()) {
        MaterialTheme(
            colorScheme = EasyFoodDarkColorScheme,
            typography = EasyFoodTypography,
            shapes = EasyFoodShapes,
            content = content,
        )
    }
}
