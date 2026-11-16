package app.luxion.easyfoodapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores que Material 3 no define: macros, avisos de datos dudosos y el acento limón. Todo texto cumple AA (4,5:1)
 * sobre su fondo. [accent] es el limón de la marca (botón «+», racha, tarjeta Coach); su texto es [onAccent].
 */
data class EasyFoodColors(
    val macroProtein: Color,
    val macroCarbs: Color,
    val macroFat: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warning: Color,
    val accent: Color,
    val onAccent: Color,
    /** Tarjeta «hero» de Hoy (kcal): fondo, texto, texto atenuado, pista de las barras, macros y aviso sobre ese fondo. */
    val hero: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    val heroTrack: Color,
    val heroProtein: Color,
    val heroCarbs: Color,
    val heroFat: Color,
    val heroWarning: Color,
)

internal val DarkEasyFoodColors = EasyFoodColors(
    macroProtein = Color(0xFFB79CFF),
    macroCarbs = Color(0xFFFFB44D),
    macroFat = Color(0xFF5FD9CE),
    warningContainer = Color(0xFF3A2A0C),
    onWarningContainer = Color(0xFFFFE0B8),
    warning = Color(0xFFFFC46B),
    accent = Color(0xFFE3F067),
    onAccent = Color(0xFF12152B),
    hero = Color(0xFF1F2440),
    onHero = Color(0xFFEEF0FA),
    onHeroVariant = Color(0xFFA9AFCB),
    heroTrack = Color(0xFF343B64),
    heroProtein = Color(0xFFB79CFF),
    heroCarbs = Color(0xFFFFB44D),
    heroFat = Color(0xFF5FD9CE),
    heroWarning = Color(0xFFFFC46B),
)

internal val LightEasyFoodColors = EasyFoodColors(
    macroProtein = Color(0xFF6A3FD0),
    macroCarbs = Color(0xFFA85F00),
    macroFat = Color(0xFF0B7F78),
    warningContainer = Color(0xFFFFEBC9),
    onWarningContainer = Color(0xFF3A2000),
    warning = Color(0xFF8A4B00),
    accent = Color(0xFFDDEB4A),
    onAccent = Color(0xFF12152B),
    hero = Color(0xFF12152B),
    onHero = Color(0xFFF5F6FA),
    onHeroVariant = Color(0xFFB9BFDD),
    heroTrack = Color(0xFF2B3052),
    heroProtein = Color(0xFFB79CFF),
    heroCarbs = Color(0xFFFFB44D),
    heroFat = Color(0xFF5FD9CE),
    heroWarning = Color(0xFFFFC46B),
)

val LocalEasyFoodColors = staticCompositionLocalOf { DarkEasyFoodColors }

/** Acceso corto: `MaterialTheme.easyFoodColors`. */
val MaterialTheme.easyFoodColors: EasyFoodColors
    @Composable @ReadOnlyComposable get() = LocalEasyFoodColors.current

// Identidad «ultramar y limón» (docs/design.md §1.7). Roles: primary = ultramar, tertiary = éxito (verde), error = aviso ámbar.
val EasyFoodDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA5B3FF),
    onPrimary = Color(0xFF0D1020),
    primaryContainer = Color(0xFF26307A),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFA9AFCB),
    onSecondary = Color(0xFF0D1020),
    secondaryContainer = Color(0xFF1F2440),
    onSecondaryContainer = Color(0xFFEEF0FA),
    tertiary = Color(0xFF6FDBB0),
    onTertiary = Color(0xFF003826),
    tertiaryContainer = Color(0xFF12362B),
    onTertiaryContainer = Color(0xFFBFF5DF),
    error = Color(0xFFFFC46B),
    onError = Color(0xFF3A2A0C),
    errorContainer = Color(0xFF3A2A0C),
    onErrorContainer = Color(0xFFFFE0B8),
    background = Color(0xFF0D1020),
    onBackground = Color(0xFFEEF0FA),
    surface = Color(0xFF0D1020),
    onSurface = Color(0xFFEEF0FA),
    surfaceVariant = Color(0xFF1F2440),
    onSurfaceVariant = Color(0xFFA9AFCB),
    surfaceContainerLowest = Color(0xFF0A0C1A),
    surfaceContainerLow = Color(0xFF161A30),
    surfaceContainer = Color(0xFF1B2038),
    surfaceContainerHigh = Color(0xFF1F2440),
    surfaceContainerHighest = Color(0xFF2B3152),
    outline = Color(0xFF7C84A8),
    outlineVariant = Color(0xFF2B3152),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEEF0FA),
    inverseOnSurface = Color(0xFF0D1020),
    inversePrimary = Color(0xFF3446E0),
)

val EasyFoodLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3446E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0B1569),
    secondary = Color(0xFF4A5070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF6),
    onSecondaryContainer = Color(0xFF12152B),
    tertiary = Color(0xFF17795A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9F2E8),
    onTertiaryContainer = Color(0xFF00281B),
    error = Color(0xFF8A4B00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBC9),
    onErrorContainer = Color(0xFF3A2000),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF12152B),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF12152B),
    surfaceVariant = Color(0xFFECEEF6),
    onSurfaceVariant = Color(0xFF4A5070),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFECEEF6),
    surfaceContainerHighest = Color(0xFFE3E6F1),
    outline = Color(0xFF636A8A),
    outlineVariant = Color(0xFFDADDEA),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF12152B),
    inverseOnSurface = Color(0xFFF5F6FA),
    inversePrimary = Color(0xFFA5B3FF),
)
