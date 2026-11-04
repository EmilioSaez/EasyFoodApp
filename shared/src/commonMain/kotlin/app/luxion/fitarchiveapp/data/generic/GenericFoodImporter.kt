package app.luxion.fitarchiveapp.data.generic

import app.luxion.fitarchiveapp.db.ProductQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carga la base genérica en `ProductEntity` con origen [SOURCE]. Los triggers de `ProductFts`
 * la indexan para la búsqueda de texto. Es idempotente: reimportar actualiza las filas existentes.
 */
class GenericFoodImporter(
    private val queries: ProductQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Importa el contenido de un `generic_foods.tsv`. Lanza [IllegalArgumentException] si el formato no es válido.
     * Todo (también el parseo) se hace en [dispatcher], nunca en el hilo de quien llama.
     * [onProgress] recibe `(hechos, total)` cada [PROGRESS_STEP] filas y al terminar; se llama desde el hilo de [dispatcher].
     */
    suspend fun import(
        text: String,
        nowMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): GenericFoodImportResult = withContext(dispatcher) {
        val parsed = GenericFoodFormat.parse(text)
        val total = parsed.foods.size
        onProgress(0, total)
        queries.transaction {
            parsed.foods.forEachIndexed { index, food ->
                val product = food.toProduct()
                queries.upsert(
                    barcode = product.barcode,
                    name = product.name,
                    brand = null,
                    image_url = null,
                    kcal = product.nutrients.kcal,
                    protein_g = product.nutrients.proteinG,
                    carbs_g = product.nutrients.carbsG,
                    sugars_g = product.nutrients.sugarsG,
                    fat_g = product.nutrients.fatG,
                    saturated_fat_g = product.nutrients.saturatedFatG,
                    fiber_g = product.nutrients.fiberG,
                    salt_g = product.nutrients.saltG,
                    serving_size_g = null,
                    serving_label = null,
                    source = SOURCE,
                    cached_at = nowMillis,
                )
                if ((index + 1) % PROGRESS_STEP == 0 || index + 1 == total) onProgress(index + 1, total)
            }
        }
        GenericFoodImportResult(imported = total, skipped = parsed.skipped)
    }

    /** Cuántos alimentos genéricos hay ya cargados. */
    suspend fun importedCount(): Long = withContext(dispatcher) { queries.countBySource(SOURCE).executeAsOne() }

    companion object {
        const val SOURCE = "GENERIC"
        const val PROGRESS_STEP = 250
    }
}

data class GenericFoodImportResult(val imported: Int, val skipped: Int)
