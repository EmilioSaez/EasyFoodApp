package app.luxion.easyfoodapp.data.supermarket

import app.luxion.easyfoodapp.db.ProductQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carga el catálogo de supermercados en `ProductEntity` con origen [SOURCE]. Nunca pisa una fila existente con el mismo
 * EAN (p. ej. un producto de Open Food Facts que el usuario ya consultó o tiene en favoritos): usa `insertIfAbsent`.
 */
class SupermarketImporter(
    private val queries: ProductQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun import(
        text: String,
        nowMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SupermarketImportResult = withContext(dispatcher) {
        val parsed = SupermarketFormat.parse(text)
        val total = parsed.products.size
        onProgress(0, total)
        queries.transaction {
            parsed.products.forEachIndexed { index, p ->
                queries.insertIfAbsent(
                    barcode = p.barcode, name = p.name, brand = p.brand, image_url = null,
                    kcal = p.nutrients.kcal, protein_g = p.nutrients.proteinG, carbs_g = p.nutrients.carbsG,
                    sugars_g = p.nutrients.sugarsG, fat_g = p.nutrients.fatG,
                    saturated_fat_g = p.nutrients.saturatedFatG, fiber_g = p.nutrients.fiberG,
                    salt_g = p.nutrients.saltG, serving_size_g = null, serving_label = null,
                    source = SOURCE, cached_at = nowMillis,
                )
                if ((index + 1) % PROGRESS_STEP == 0 || index + 1 == total) onProgress(index + 1, total)
            }
        }
        SupermarketImportResult(imported = total, skipped = parsed.skipped)
    }

    /** Cuántos productos del catálogo hay ya cargados. */
    suspend fun importedCount(): Long = withContext(dispatcher) { queries.countBySource(SOURCE).executeAsOne() }

    companion object {
        const val SOURCE = "SUPERMARKET"
        const val PROGRESS_STEP = 500
    }
}

data class SupermarketImportResult(val imported: Int, val skipped: Int)
