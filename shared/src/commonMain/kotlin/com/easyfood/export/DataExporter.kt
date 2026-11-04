package com.easyfood.export

import com.easyfood.data.CachedProduct
import com.easyfood.data.toCached
import com.easyfood.data.toDomain
import com.easyfood.db.CustomFoodQueries
import com.easyfood.db.DiaryEntryQueries
import com.easyfood.db.ProductQueries
import com.easyfood.model.CustomFood
import com.easyfood.model.DiaryEntry
import com.easyfood.model.Product
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Datos locales que se pueden exportar. Cada almacén nuevo (alimentos propios, objetivos) añadirá un método. */
interface ExportSource {
    suspend fun products(): List<ExportedProduct>
    suspend fun diaryEntries(): List<DiaryEntry>
    suspend fun customFoods(): List<ExportedCustomFood> = emptyList()
}

/** Genera el JSON de exportación a partir de [source]. */
class DataExporter(
    private val source: ExportSource,
    private val nowMillis: () -> Long,
) {
    suspend fun buildFile(): ExportFile = ExportFile(
        schemaVersion = ExportCodec.CURRENT_VERSION,
        exportedAtMillis = nowMillis(),
        products = source.products(),
        diaryEntries = source.diaryEntries().map { it.toExported() },
        customFoods = source.customFoods(),
    )

    suspend fun exportToJson(): String = ExportCodec.encode(buildFile())
}

fun DiaryEntry.toExported() = ExportedDiaryEntry(
    id = id, date = date, meal = meal, barcode = barcode, name = name,
    quantity = quantity.amount, unit = quantity.unit, servingSizeG = servingSizeG,
    nutrients = nutrients.toExported(), createdAtMillis = createdAtMillis,
)

fun CustomFood.toExported() = ExportedCustomFood(
    id = id, name = name, brand = brand, nutrients = nutrients.toExported(), servingSizeG = servingSizeG,
    servingLabel = servingLabel, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun ExportedDiaryEntry.toDomain() = DiaryEntry(
    id = id, date = date, meal = meal, barcode = barcode, name = name,
    quantity = quantityOf(quantity, unit), servingSizeG = servingSizeG,
    nutrients = nutrients.toDomain(), createdAtMillis = createdAtMillis,
)

fun ExportedProduct.toCachedProduct() = CachedProduct(
    product = Product(
        barcode = barcode, name = name, brand = brand, imageUrl = imageUrl,
        nutrients = nutrients.toDomain(), servingSizeG = servingSizeG, servingLabel = servingLabel,
    ),
    fetchedAtMillis = updatedAtMillis,
)

/** [ExportSource] respaldado por las tablas SQLDelight. */
class SqlDelightExportSource(
    private val productQueries: ProductQueries,
    private val diaryQueries: DiaryEntryQueries,
    private val customFoodQueries: CustomFoodQueries? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ExportSource {
    override suspend fun customFoods(): List<ExportedCustomFood> = withContext(dispatcher) {
        customFoodQueries?.selectAll()?.executeAsList().orEmpty().map { it.toDomain().toExported() }
    }

    override suspend fun products(): List<ExportedProduct> = withContext(dispatcher) {
        productQueries.selectAll().executeAsList().map {
            val p = it.toCached().product
            ExportedProduct(
                barcode = p.barcode, name = p.name, brand = p.brand, imageUrl = p.imageUrl,
                nutrients = p.nutrients.toExported(), servingSizeG = p.servingSizeG,
                servingLabel = p.servingLabel, source = it.source, updatedAtMillis = it.cached_at,
            )
        }
    }

    override suspend fun diaryEntries(): List<DiaryEntry> = withContext(dispatcher) {
        diaryQueries.selectAll().executeAsList().map { it.toDomain() }
    }
}
