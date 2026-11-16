package app.luxion.fitarchiveapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import easyfoodapp.shared.generated.resources.Res
import easyfoodapp.shared.generated.resources.bricolage
import easyfoodapp.shared.generated.resources.figtree
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

// Cifras alineadas (tabulares) para display y headlines.
private const val TabularFigures = "tnum"

@OptIn(ExperimentalTextApi::class)
@Composable
private fun variableFamily(resource: FontResource): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    val fonts = weights.map { weight ->
        Font(resource, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
    return remember(fonts) { FontFamily(fonts) }
}

/**
 * Escala nueva (§1.7): Bricolage Grotesque para display, headlines y títulos grandes; Figtree para el resto.
 * Los estilos no definidos aquí conservan la escala por defecto de M3 con Figtree.
 */
@Composable
fun easyFoodTypography(): Typography {
    val display = variableFamily(Res.font.bricolage)
    val text = variableFamily(Res.font.figtree)
    return remember(display, text) {
        val base = Typography()
        Typography(
            displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2.5).sp, fontFeatureSettings = TabularFigures),
            displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-2).sp, fontFeatureSettings = TabularFigures),
            displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TabularFigures),
            headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp, fontFeatureSettings = TabularFigures),
            headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TabularFigures),
            headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = base.titleSmall.copy(fontFamily = text, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontFamily = text),
            labelLarge = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            // Rótulo de sección en mayúsculas con tracking (el texto se pasa ya en mayúsculas).
            labelMedium = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
            labelSmall = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

/** Para EAN y números tabulares (monoespaciada del sistema). */
val EanTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
