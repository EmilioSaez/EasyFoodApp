package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.screens.CustomFoodFormState

/** Destinos de la app (docs/design.md §4). Los argumentos viajan en la propia ruta. */
sealed interface Route {
    /** Diario del día (pantalla de inicio). */
    data object Diary : Route

    /**
     * Añadir alimento; [meal] es la comida preseleccionada (`null` desde la pestaña: se elige en la ficha).
     * [templates] indica que se ve la lista de plantillas (así se vuelve a ella tras editar una).
     * [date] es el día al que se apunta una entrada rápida (`null` = hoy).
     */
    data class Add(val meal: MealType? = null, val templates: Boolean = false, val date: String? = null) : Route

    /**
     * Entrada rápida de kcal y macros sin alimento, para [meal] y [date] (`null` = la actual / hoy). Con [entryId]
     * informado se edita esa entrada rápida del diario.
     */
    data class QuickEntry(val meal: MealType? = null, val date: String? = null, val entryId: Long? = null) : Route

    /** Escáner de cámara + entrada manual de EAN. */
    data class Scanner(val meal: MealType? = null) : Route

    /**
     * Crear alimento propio; [barcode] viene prerrelleno si se abre desde un EAN no encontrado.
     * Con [foodId] informado se edita (y se puede borrar) ese alimento. Con [prefill] el formulario
     * arranca relleno (copia de un producto) y [brand] se conserva al guardar. [unreadFields] distinto de `null`
     * indica que el formulario viene de la lectura de una etiqueta por IA: marca los campos que no se leyeron
     * (ver `LabelReading.unreadFields`) y muestra el aviso de datos dudosos.
     */
    data class CustomFood(
        val barcode: String? = null,
        val meal: MealType? = null,
        val foodId: Long? = null,
        val prefill: CustomFoodFormState? = null,
        val brand: String? = null,
        val unreadFields: Set<String>? = null,
    ) : Route

    /**
     * Alta por foto de la etiqueta con IA. [barcode] es el EAN que no se encontró (si venía del escáner o de la
     * entrada manual) y se conserva al guardar el alimento propio.
     */
    data class LabelPhoto(val barcode: String? = null, val meal: MealType? = null) : Route

    /**
     * Ficha de producto. Alta: [barcode] del producto y [meal] destino. Edición de una entrada
     * del diario: [entryId] informado.
     */
    data class ProductDetail(
        val barcode: String? = null,
        val meal: MealType? = null,
        val entryId: Long? = null,
    ) : Route

    /** Lista de recetas. */
    data object Recipes : Route

    /** Detalle de una receta: macros, ingredientes y añadirla al diario. */
    data class RecipeDetail(val recipeId: Long) : Route

    /** Crear una plantilla de comida, o editarla (renombrarla o borrarla) si se indica [templateId]. */
    data class TemplateEdit(val templateId: Long? = null) : Route

    /** Crear una receta, o editarla (y borrarla) si se indica [recipeId]. */
    data class RecipeEdit(val recipeId: Long? = null) : Route

    /** Objetivos y TDEE. */
    data object Goals : Route

    /** Seguimiento de peso: registro, historial y gráfica. */
    data object Weight : Route

    /** Recordatorios locales: comidas, peso y agua. */
    data object Reminders : Route

    /** Resumen semanal y mensual, derivado del diario, los objetivos y los pesajes; se abre desde el Diario. */
    data object Summary : Route

    /** Datos: exportar, importar y transferir por QR o red local. */
    data object Data : Route

    /** Pestaña «IA» (placeholder hasta las funciones de los PRs siguientes). */
    data object Ai : Route

    /** Foto del plato: la IA detecta los alimentos y se registran, tras revisarlos, en [meal] y [date] (`null` = la actual / hoy). */
    data class PlatePhoto(val meal: MealType? = null, val date: String? = null) : Route

    /** Ajustes de IA: clave, modelo, tope diario, prueba de conexión y uso. */
    data object AiSettings : Route
}

/** Pestañas de la barra inferior. */
enum class TopLevelTab(val label: String, val icon: String, val route: Route) {
    Diary("Diario", "▤", Route.Diary),
    Foods("Alimentos", "⌕", Route.Add()),
    Recipes("Recetas", "≣", Route.Recipes),
    Weight("Mi peso", "↗", Route.Weight),
    Goals("Objetivos", "◎", Route.Goals),
    Ai("IA", "✦", Route.Ai),
}

/**
 * Pila de navegación mínima y observable (sin dependencias extra). La raíz es siempre el Diario;
 * [navigateTab] reinicia la pila en la pestaña elegida.
 */
@Stable
class Navigator(start: Route = Route.Diary) {
    private val stack = mutableStateListOf(start)

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    /** Vuelve a la pantalla anterior; devuelve false si ya estamos en la raíz. */
    fun pop(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Sustituye la pantalla actual (p. ej. de Escáner a Ficha sin dejar el escáner en la pila). */
    fun replace(route: Route) {
        stack[stack.lastIndex] = route
    }

    /** Cambia de pestaña: pila nueva con el Diario como raíz (salvo que la pestaña sea el Diario). */
    fun navigateTab(tab: TopLevelTab) {
        stack.clear()
        stack.add(Route.Diary)
        if (tab != TopLevelTab.Diary) stack.add(tab.route)
    }

    /** Vuelve al Diario vaciando la pila (tras añadir un alimento). */
    fun popToDiary() {
        stack.clear()
        stack.add(Route.Diary)
    }
}
