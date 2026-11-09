package app.luxion.fitarchiveapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import easyfoodapp.shared.generated.resources.Res
import easyfoodapp.shared.generated.resources.inter
import easyfoodapp.shared.generated.resources.sora
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/** Colores que Material 3 no define (macros y avisos de datos dudosos). Todo texto cumple AA (4,5:1) sobre su fondo. */
data class EasyFoodColors(
    val macroProtein: Color,
    val macroCarbs: Color,
    val macroFat: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warning: Color,
)

private val DarkEasyFoodColors = EasyFoodColors(
    macroProtein = Color(0xFFFF5C5E),
    macroCarbs = Color(0xFF6FB1FF),
    macroFat = Color(0xFFBDBDBD),
    warningContainer = Color(0xFF5C3A0E),
    onWarningContainer = Color(0xFFFFE0B8),
    warning = Color(0xFFFFB454),
)

private val LightEasyFoodColors = EasyFoodColors(
    macroProtein = Color(0xFFB41319),
    macroCarbs = Color(0xFF1F5FA8),
    macroFat = Color(0xFF5F5F5F),
    warningContainer = Color(0xFFFFDDB0),
    onWarningContainer = Color(0xFF2E1A00),
    warning = Color(0xFF8A5000),
)

val LocalEasyFoodColors = staticCompositionLocalOf { DarkEasyFoodColors }

/** Acceso corto: `MaterialTheme.easyFoodColors`. */
val MaterialTheme.easyFoodColors: EasyFoodColors
    @Composable @ReadOnlyComposable get() = LocalEasyFoodColors.current

// Tema de ApexApp (docs/design.md §1.7): rojo sobre negro, un solo acento. tertiary = éxito, error = ámbar.
val EasyFoodDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF4D4F),
    onPrimary = Color(0xFF3A0004),
    primaryContainer = Color(0xFF8C1218),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFF5BD68A),
    onTertiary = Color(0xFF00391B),
    tertiaryContainer = Color(0xFF14522E),
    onTertiaryContainer = Color(0xFFB6F2CB),
    error = Color(0xFFFFB454),
    onError = Color(0xFF4A2A00),
    errorContainer = Color(0xFF5C3A0E),
    onErrorContainer = Color(0xFFFFE0B8),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0B0B0B),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFF383838),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF0B0B0B),
    inversePrimary = Color(0xFFC4161C),
)

val EasyFoodLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFC4161C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF3A0004),
    secondary = Color(0xFF4A4A4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0DCDC),
    onSecondaryContainer = Color(0xFF0B0B0B),
    tertiary = Color(0xFF1B7A43),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB6F2CB),
    onTertiaryContainer = Color(0xFF00210F),
    error = Color(0xFF9A5B00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDDB0),
    onErrorContainer = Color(0xFF2E1A00),
    background = Color(0xFFF7F5F5),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFF7F5F5),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFE0DCDC),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EEEE),
    surfaceContainer = Color(0xFFEAE6E6),
    surfaceContainerHigh = Color(0xFFE4E0E0),
    surfaceContainerHighest = Color(0xFFE0DCDC),
    outline = Color(0xFF6E6A6A),
    outlineVariant = Color(0xFFCAC5C5),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2A2A2A),
    inverseOnSurface = Color(0xFFF1EEEE),
    inversePrimary = Color(0xFFFF4D4F),
)

// Cifras alineadas (tabulares) para display y headlineLarge.
private const val TabularFigures = "tnum"

@OptIn(ExperimentalTextApi::class)
@Composable
private fun variableFamily(resource: FontResource): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
    val fonts = weights.map { weight ->
        Font(resource, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
    return remember(fonts) { FontFamily(fonts) }
}

/**
 * Escala de ApexApp (§1.7): Sora para display, headlineLarge y titleLarge; Inter para el resto.
 * Los estilos que ApexApp no define conservan la escala por defecto de M3, pero con Inter para no mezclar con la fuente del sistema.
 */
@Composable
fun easyFoodTypography(): Typography {
    val sora = variableFamily(Res.font.sora)
    val inter = variableFamily(Res.font.inter)
    return remember(sora, inter) {
        val base = Typography()
        Typography(
            displayLarge = TextStyle(fontFamily = sora, fontWeight = FontWeight.Normal, fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-3).sp, fontFeatureSettings = TabularFigures),
            displayMedium = TextStyle(fontFamily = sora, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-2).sp, fontFeatureSettings = TabularFigures),
            displaySmall = base.displaySmall.copy(fontFamily = inter),
            headlineLarge = TextStyle(fontFamily = sora, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp, fontFeatureSettings = TabularFigures),
            headlineMedium = base.headlineMedium.copy(fontFamily = inter),
            headlineSmall = base.headlineSmall.copy(fontFamily = inter),
            titleLarge = TextStyle(fontFamily = sora, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
            titleMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = base.titleSmall.copy(fontFamily = inter),
            bodyLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontFamily = inter),
            labelLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            // Rótulo de sección en mayúsculas con tracking (el texto se pasa ya en mayúsculas).
            labelMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
            labelSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

/** Para EAN y números tabulares (monoespaciada del sistema hasta empaquetar Roboto Mono). */
val EanTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)

// Una sola decisión de radio: esquinas casi rectas en todo. Los puntos de estado usan CircleShape.
private val Radius = 4.dp

val EasyFoodShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius),
    small = RoundedCornerShape(Radius),
    medium = RoundedCornerShape(Radius),
    large = RoundedCornerShape(Radius),
    extraLarge = RoundedCornerShape(Radius),
)

val PillShape = CircleShape

/** Sigue al sistema (claro/oscuro); sin color dinámico. */
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
