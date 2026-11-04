package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import app.luxion.fitarchiveapp.model.MealType

/** Destinos de la app (docs/design.md §4). Los argumentos viajan en la propia ruta. */
sealed interface Route {
    /** Diario del día (pantalla de inicio). */
    data object Diary : Route

    /** Añadir alimento; [meal] es la comida preseleccionada. */
    data class Add(val meal: MealType = MealType.SNACK) : Route

    /** Escáner de cámara + entrada manual de EAN. */
    data class Scanner(val meal: MealType = MealType.SNACK) : Route

    /** Crear alimento propio; [barcode] viene prerrelleno si se abre desde un EAN no encontrado. */
    data class CustomFood(val barcode: String? = null, val meal: MealType = MealType.SNACK) : Route

    /**
     * Ficha de producto. Alta: [barcode] del producto y [meal] destino. Edición de una entrada
     * del diario: [entryId] informado.
     */
    data class ProductDetail(
        val barcode: String? = null,
        val meal: MealType = MealType.SNACK,
        val entryId: Long? = null,
    ) : Route

    /** Objetivos y TDEE. */
    data object Goals : Route
}

/** Pestañas de la barra inferior. */
enum class TopLevelTab(val label: String, val icon: String, val route: Route) {
    Diary("Diario", "▤", Route.Diary),
    Foods("Alimentos", "⌕", Route.Add()),
    Goals("Objetivos", "◎", Route.Goals),
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
