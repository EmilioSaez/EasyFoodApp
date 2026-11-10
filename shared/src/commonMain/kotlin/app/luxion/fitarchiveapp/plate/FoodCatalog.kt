package app.luxion.fitarchiveapp.plate

import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.screens.FoodPick
import app.luxion.fitarchiveapp.ui.screens.matches
import app.luxion.fitarchiveapp.ui.screens.mergePicks

/** Resultado de enlazar un alimento detectado: [picks] ordenados (el primero es el elegido) y si se tuvo que aflojar la búsqueda. */
data class FoodMatch(val picks: List<FoodPick>, val loose: Boolean)

/**
 * Busca alimentos en la base del usuario (genérica en español, productos ya consultados y propios) para que kcal y
 * macros salgan de datos reales y no del modelo. Es la misma búsqueda que la pestaña Alimentos.
 */
class FoodCatalog(
    private val searchStore: ProductSearchStore?,
    private val customFoods: CustomFoodStore?,
) {
    /** Búsqueda libre (para «Cambiar alimento» y «Añadir»): propios primero, luego genéricos y por último productos. */
    suspend fun search(text: String, limit: Int = SEARCH_LIMIT): List<FoodPick> {
        if (text.isBlank()) return emptyList()
        val custom = customFoods?.all().orEmpty().filter { it.matches(text) }
        val products = searchStore?.search(text, limit).orEmpty()
        return mergePicks(custom, products).sortedBy { rank(it) }.take(limit)
    }

    /**
     * Enlaza un término de la IA («arroz blanco cocido»). Si no hay coincidencia exacta se van quitando palabras del
     * final («arroz blanco», «arroz»); en ese caso [FoodMatch.loose] avisa de que hay que revisarlo.
     */
    suspend fun match(term: String, limit: Int = ALTERNATIVES): FoodMatch {
        val words = term.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        var n = words.size
        while (n >= 1) {
            val picks = search(words.take(n).joinToString(" "), limit)
            if (picks.isNotEmpty()) return FoodMatch(picks, loose = n < words.size)
            n--
        }
        return FoodMatch(emptyList(), loose = false)
    }

    private fun rank(p: FoodPick): Int = when (p.result.source) {
        DataSource.Custom -> 0
        DataSource.Generic -> 1
        DataSource.OpenFoodFacts -> 2
    }

    companion object {
        const val SEARCH_LIMIT = 20
        const val ALTERNATIVES = 6
    }
}
