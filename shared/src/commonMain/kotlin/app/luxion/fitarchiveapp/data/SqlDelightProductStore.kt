package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.ProductEntity
import app.luxion.fitarchiveapp.db.ProductQueries
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.ProductCorrection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ProductLocalStore] respaldado por la tabla `ProductEntity` de SQLDelight. */
class SqlDelightProductStore(
    private val queries: ProductQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProductLocalStore {
    override suspend fun find(barcode: String): CachedProduct? = withContext(dispatcher) {
        val cached = queries.selectByBarcode(barcode).executeAsOneOrNull()?.toCached() ?: return@withContext null
        cached.copy(product = cached.product.withCorrection(queries.selectCorrection(barcode).executeAsOneOrNull()?.toDomain()))
    }

    override suspend fun applyCorrection(product: Product): Product = withContext(dispatcher) {
        product.withCorrection(queries.selectCorrection(product.barcode).executeAsOneOrNull()?.toDomain())
    }

    override suspend fun saveCorrection(correction: ProductCorrection): Unit = withContext(dispatcher) {
        val n = correction.nutrients
        queries.upsertCorrection(
            barcode = correction.barcode, kcal = n.kcal, protein_g = n.proteinG, carbs_g = n.carbsG,
            sugars_g = n.sugarsG, fat_g = n.fatG, saturated_fat_g = n.saturatedFatG, fiber_g = n.fiberG,
            salt_g = n.saltG, updated_at = correction.updatedAtMillis,
        )
    }

    override suspend fun clearCorrection(barcode: String): Unit = withContext(dispatcher) {
        queries.deleteCorrection(barcode)
    }

    override suspend fun save(product: Product, fetchedAtMillis: Long): Unit = withContext(dispatcher) {
        queries.upsert(
            barcode = product.barcode,
            name = product.name,
            brand = product.brand,
            image_url = product.imageUrl,
            kcal = product.nutrients.kcal,
            protein_g = product.nutrients.proteinG,
            carbs_g = product.nutrients.carbsG,
            sugars_g = product.nutrients.sugarsG,
            fat_g = product.nutrients.fatG,
            saturated_fat_g = product.nutrients.saturatedFatG,
            fiber_g = product.nutrients.fiberG,
            salt_g = product.nutrients.saltG,
            serving_size_g = product.servingSizeG,
            serving_label = product.servingLabel,
            source = SOURCE_OPEN_FOOD_FACTS,
            cached_at = fetchedAtMillis,
        )
    }

    override suspend fun saveRefreshed(product: Product, nowMillis: Long, changeSummary: String?): Boolean =
        withContext(dispatcher) {
            queries.transactionWithResult {
                val existing = queries.selectByBarcode(product.barcode).executeAsOneOrNull()
                if (existing == null || existing.source != SOURCE_OPEN_FOOD_FACTS) {
                    false // genéricos y propios no se refrescan
                } else {
                    queries.updateRefreshed(
                        name = product.name,
                        brand = product.brand,
                        imageUrl = product.imageUrl,
                        kcal = product.nutrients.kcal,
                        proteinG = product.nutrients.proteinG,
                        carbsG = product.nutrients.carbsG,
                        sugarsG = product.nutrients.sugarsG,
                        fatG = product.nutrients.fatG,
                        saturatedFatG = product.nutrients.saturatedFatG,
                        fiberG = product.nutrients.fiberG,
                        saltG = product.nutrients.saltG,
                        servingSizeG = product.servingSizeG,
                        servingLabel = product.servingLabel,
                        now = nowMillis,
                        barcode = product.barcode,
                    )
                    queries.recordRefresh(
                        barcode = product.barcode, refreshedAt = nowMillis, now = nowMillis, summary = changeSummary,
                    )
                    true
                }
            }
        }

    override suspend fun markChecked(barcode: String, nowMillis: Long): Unit = withContext(dispatcher) {
        queries.transaction {
            val existing = queries.selectByBarcode(barcode).executeAsOneOrNull()
            if (existing?.source == SOURCE_OPEN_FOOD_FACTS) {
                queries.recordRefresh(barcode = barcode, refreshedAt = null, now = nowMillis, summary = null)
            }
        }
    }

    override suspend fun refreshInfo(barcode: String): ProductRefreshInfo? = withContext(dispatcher) {
        queries.selectRefreshInfo(barcode).executeAsOneOrNull()?.let {
            ProductRefreshInfo(it.refreshed_at, it.changed_at, it.change_summary)
        }
    }

    override suspend fun refreshCandidates(staleBeforeMillis: Long, limit: Int): List<String> = withContext(dispatcher) {
        queries.selectRefreshCandidates(staleBefore = staleBeforeMillis, limit = limit.toLong()).executeAsList()
    }

    private companion object {
        const val SOURCE_OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS"
    }
}

internal fun ProductEntity.toCached() = CachedProduct(
    product = Product(
        barcode = barcode,
        name = name,
        brand = brand,
        imageUrl = image_url,
        nutrients = Nutrients(
            kcal = kcal,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            saturatedFatG = saturated_fat_g,
            sugarsG = sugars_g,
            fiberG = fiber_g,
            saltG = salt_g,
        ),
        servingSizeG = serving_size_g,
        servingLabel = serving_label,
    ),
    fetchedAtMillis = cached_at,
)
