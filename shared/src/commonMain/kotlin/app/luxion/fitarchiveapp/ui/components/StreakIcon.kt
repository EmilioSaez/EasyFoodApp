package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/** Lienzo de los paths: 24 × 24, como los iconos de Material. */
private const val VIEWPORT = 24f

// «Llama gota»: cuerpo redondeado con una lengua lateral y una gota interior (docs/design.md, racha).
private const val FLAME = "M12 2.5C12 2.5 5.5 8.2 5.5 14.2C5.5 18.1 8.4 21.5 12 21.5C15.6 21.5 18.5 18.1 18.5 14.2" +
    "C18.5 11.4 17.7 9 16.8 6.6C16.2 8.4 15.3 9.8 13.9 10.6C14.6 7.6 13.6 4.6 12 2.5Z"
private const val DROP = "M12 12.5C12 12.5 9.2 15 9.2 17.2C9.2 18.9 10.4 20 12 20C13.6 20 14.8 18.9 14.8 17.2C14.8 15 12 12.5 12 12.5Z"

/** Opacidad de la llama apagada (racha 0 o día sin cumplir). */
internal const val INACTIVE_ALPHA = 0.38f

/**
 * Símbolo de racha propio (no emoji). Activo: llama en ultramar (`primary`) con gota limón (`accent`); apagado: silueta
 * atenuada en `onSurfaceVariant`. Legible desde ~12 dp (bajo el número de un calendario) hasta tamaños grandes.
 *
 * [tint] sustituye el color de la llama (p. ej. `onAccent` sobre la píldora limón); [dropColor] el de la gota (sobre un
 * fondo limón debe ser ese mismo limón). Es decorativo: quien lo use pone la descripción de accesibilidad.
 */
@Composable
fun StreakIcon(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    dropColor: Color = Color.Unspecified,
) {
    val flame = remember { PathParser().parsePathString(FLAME).toPath() }
    val drop = remember { PathParser().parsePathString(DROP).toPath() }
    val body = if (active) tint.orElse(MaterialTheme.colorScheme.primary) else tint.orElse(MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = INACTIVE_ALPHA)
    val inner = dropColor.orElse(MaterialTheme.easyFoodColors.accent)
    Canvas(modifier.size(size)) { drawFlame(flame, drop, body, inner, active) }
}

private fun Color.orElse(other: Color): Color = if (this == Color.Unspecified) other else this

private fun DrawScope.drawFlame(flame: Path, drop: Path, body: Color, inner: Color, active: Boolean) {
    val k = this.size.minDimension / VIEWPORT
    scale(k, k, pivot = Offset.Zero) {
        drawPath(flame, body)
        if (active) drawPath(drop, inner)
    }
}
