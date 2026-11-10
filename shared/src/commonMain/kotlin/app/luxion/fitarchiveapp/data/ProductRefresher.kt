package app.luxion.fitarchiveapp.data

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Resultado de una pasada de [ProductRefresher.run]. */
data class RefreshRun(
    /** Productos consultados con respuesta de Open Food Facts. */
    val checked: Int,
    /** Los que tenían un cambio nutricional relevante: (código, resumen). */
    val changed: List<Pair<String, String>>,
    /** La pasada se cortó por falta de red. */
    val offline: Boolean,
)

/**
 * Refresco oportunista en segundo plano de los productos de Open Food Facts que el usuario usa (diario o favoritos)
 * y llevan más de [minAge] sin consultarse. Pocas peticiones por pasada ([maxPerRun]), espaciadas por [pause]
 * (cortesía con el servicio) y se detiene a la primera falla de red: sin conexión no hace nada ni molesta.
 * Solo actualiza la caché de productos: el diario, las recetas, los alimentos propios y los genéricos no se tocan.
 */
class ProductRefresher(
    private val repository: ProductRepository,
    private val minAge: Duration = 14.days,
    private val maxPerRun: Int = 20,
    private val pause: Duration = Duration.ZERO,
) {
    suspend fun run(): RefreshRun {
        var checked = 0
        val changed = mutableListOf<Pair<String, String>>()
        for (barcode in repository.refreshCandidates(minAge, maxPerRun)) {
            when (val r = repository.refresh(barcode)) {
                is RefreshResult.Failure -> return RefreshRun(checked, changed, offline = true)
                is RefreshResult.Updated -> {
                    checked++
                    r.change?.let { changed += barcode to it }
                }
                RefreshResult.NotFound, RefreshResult.Rejected -> checked++
                RefreshResult.Skipped -> Unit
            }
            if (pause > Duration.ZERO) delay(pause)
        }
        return RefreshRun(checked, changed, offline = false)
    }
}
