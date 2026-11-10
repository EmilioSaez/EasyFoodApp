package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Puntos de extensión de la pestaña «IA» (docs/ia.md, «Añadir una función a la pestaña»).
 *
 * Convención para que varias funciones (foto del plato, etiqueta…) se enganchen sin tocar `AiScreen`:
 *  - **Acción** ([AiTabAction]): un botón de la fila «Herramientas» (p. ej. «Foto del plato», «Etiqueta»). Su `onClick`
 *    suele hacer `env.navigator.push(Route.…)` a la pantalla de la función. La fila no se muestra si no hay acciones.
 *  - **Tarjeta** ([AiTabCard]): una tarjeta propia en la pestaña, debajo de las de sugerencias, si la función necesita
 *    espacio para resultados en la propia pestaña. Usa [AiCard] para mantener el aspecto.
 *
 * Cada función define su acción/tarjeta en **su propio archivo** (p. ej. `AiPlateAction.kt`) y añade **una línea** a
 * [aiTabActions] / [aiTabCards] en este archivo, en la posición reservada. Las tarjetas y acciones solo se ven con la IA
 * lista (clave, activada, aviso aceptado): la pestaña ya resuelve esos estados.
 */
class AiTabEnvironment(
    val navigator: Navigator,
    val module: AiModule,
    val diaryStore: DiaryStore,
    val today: () -> String,
    val mealChoices: MealChoices,
)

/** Botón de la fila «Herramientas». [id] es estable (sirve de clave); [icon] es un carácter, igual que en `EmptyState`. */
class AiTabAction(
    val id: String,
    val label: String,
    val icon: String,
    val onClick: (AiTabEnvironment) -> Unit,
)

/** Tarjeta adicional de la pestaña. [id] es estable. */
class AiTabCard(
    val id: String,
    val content: @Composable (AiTabEnvironment) -> Unit,
)

/** Acciones de la fila «Herramientas», en orden. Una línea por función, con una línea en blanco entre ellas para evitar conflictos de fusión. */
val aiTabActions: List<AiTabAction> = listOf(
    // ia-plato: aiPlateAction,

    // ia-etiqueta: aiLabelAction,
)

/** Tarjetas adicionales, tras «¿Qué como?», «¿Cómo voy?» y la pregunta libre. Misma convención que [aiTabActions]. */
val aiTabCards: List<AiTabCard> = listOf(
    // (vacío: ninguna función propone hoy una tarjeta propia)
)

/** Tarjeta con el aspecto de la pestaña: fondo `surfaceContainerLow`, título `titleSmall`/`primary`. */
@Composable
fun AiCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
