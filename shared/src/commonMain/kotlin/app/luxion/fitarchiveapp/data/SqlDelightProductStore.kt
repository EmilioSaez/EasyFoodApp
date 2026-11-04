package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.ProductEntity
import app.luxion.fitarchiveapp.db.ProductQueries
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ProductLocalStore] respaldado por la tabla `ProductEntity` de SQLDelight. */
class SqlDelightProductStore(
    private val queries: ProductQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProductLocalStore {
    override suspend fun find(barcode: String): CachedProduct? = withContext(dispatcher) {
        queries.selectByBarcode(barcode).executeAsOneOrNull()?.toCached()
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
