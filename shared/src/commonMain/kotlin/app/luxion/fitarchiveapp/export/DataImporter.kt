package app.luxion.fitarchiveapp.export

import app.luxion.fitarchiveapp.db.CustomFoodQueries
import app.luxion.fitarchiveapp.db.DiaryEntryQueries
import app.luxion.fitarchiveapp.db.ProductQueries

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.NewCustomFood
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Importación de un JSON de exportación fusionándolo con los datos locales. Nunca borra nada local.
 *
 * ## Política de duplicados y conflictos
 *
 * **Productos** (caché por código de barras)
 * - Identidad: el EAN (`barcode`).
 * - No existe en local: se añade.
 * - Existe con el mismo contenido (todo salvo la fecha): duplicado, se ignora.
 * - Existe con contenido distinto: gana el de fecha `updatedAtMillis` más reciente; con empate, el local.
 *
 * **Alimentos propios**
 * - Identidad: primero `(id, createdAtMillis)` (el mismo alimento reimportado aunque se haya renombrado
 *   en local); si no hay coincidencia, nombre + marca sin distinguir mayúsculas ni espacios sobrantes.
 *   El `id` por sí solo no vale: es un autoincremental distinto en cada dispositivo.
 * - No existe en local: se añade con un id nuevo, conservando sus fechas.
 * - Existe con el mismo contenido (nombre y marca sin distinguir mayúsculas, nutrientes, porción): duplicado, se ignora.
 * - Existe con contenido distinto: gana el de `updatedAtMillis` más reciente; con empate, el local.
 *
 * **Entradas del diario**
 * - Identidad: `(date, barcode, name, createdAtMillis)`; el `id` se ignora (autoincremental por dispositivo).
 *   Entradas con la misma identidad se emparejan una a una, así que dos entradas realmente idénticas
 *   siguen siendo dos.
 * - No existe en local: se añade con un id nuevo.
 * - Existe con el mismo contenido: duplicado, se ignora.
 * - Existe con comida/cantidad/nutrientes distintos: se conserva la local (el diario no tiene fecha de
 *   edición, y lo más probable es que el usuario la haya editado después de exportar) y se cuenta como
 *   conflicto.
 *
 * **Elementos inválidos** (cantidad ≤ 0, fecha mal formada, nutrientes negativos, nombre vacío…): se
 * omiten y se listan en el informe; el resto se importa.
 *
 * Todo se aplica en una única transacción: o entra todo o no entra nada.
 *
 * ## Versiones
 * [ExportCodec.decode] acepta cualquier `schemaVersion` entre 1 y [ExportCodec.CURRENT_VERSION]; las
 * colecciones que una versión antigua no traía (p. ej. `customFoods`) se leen como vacías. Si algún día se
 * sube la versión por un cambio incompatible, la migración de la versión anterior va en el codec, antes de
 * que el importador vea el archivo.
 */
class DataImporter(private val target: ImportTarget) {
    /** Calcula qué pasaría al importar [json] sin escribir nada. Lanza [ExportFormatException] si no es válido. */
    suspend fun preview(json: String): ImportPlan = plan(ExportCodec.decode(json))

    /** Importa [json] y devuelve el informe. Lanza [ExportFormatException] si no es válido (sin tocar nada). */
    suspend fun import(json: String): ImportReport {
        val plan = preview(json)
        if (!plan.changes.isEmpty) target.apply(plan.changes)
        return plan.report
    }

    suspend fun plan(file: ExportFile): ImportPlan {
        val invalid = mutableListOf<String>()
        val products = planProducts(file.products, target.products(), invalid)
        val foods = planCustomFoods(file.customFoods, target.customFoods(), invalid)
        val diary = planDiary(file.diaryEntries, target.diaryEntries(), invalid)
        return ImportPlan(
            changes = ImportChanges(
                products = products.writes,
                customFoodsToAdd = foods.adds,
                customFoodsToUpdate = foods.updates,
                diaryEntriesToAdd = diary.adds,
            ),
            report = ImportReport(products.counts, foods.counts, diary.counts, invalid),
        )
    }

    // --- Productos ---

    private class ProductPlan(val writes: List<ExportedProduct>, val counts: ImportCounts)

    private fun planProducts(
        incoming: List<ExportedProduct>,
        local: List<ExportedProduct>,
        invalid: MutableList<String>,
    ): ProductPlan {
        val byBarcode = local.associateByTo(mutableMapOf()) { it.barcode }
        val writes = linkedMapOf<String, ExportedProduct>()
        var c = ImportCounts()
        for (p in incoming) {
            if (p.barcode.isBlank() || p.name.isBlank()) {
                invalid += "Producto sin código de barras o sin nombre"; c = c.copy(invalid = c.invalid + 1); continue
            }
            val existing = byBarcode[p.barcode]
            when {
                existing == null -> { c = c.copy(added = c.added + 1); byBarcode[p.barcode] = p; writes[p.barcode] = p }
                existing.copy(updatedAtMillis = 0) == p.copy(updatedAtMillis = 0) -> c = c.copy(unchanged = c.unchanged + 1)
                p.updatedAtMillis > existing.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1); byBarcode[p.barcode] = p; writes[p.barcode] = p
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return ProductPlan(writes.values.toList(), c)
    }

    // --- Alimentos propios ---

    private class FoodPlan(
        val adds: List<ExportedCustomFood>,
        val updates: List<CustomFoodUpdate>,
        val counts: ImportCounts,
    )

    /** Alimento local o ya añadido en esta importación (`localId == null`). */
    private class KnownFood(val localId: Long?, var food: ExportedCustomFood)

    private fun planCustomFoods(
        incoming: List<ExportedCustomFood>,
        local: List<ExportedCustomFood>,
        invalid: MutableList<String>,
    ): FoodPlan {
        val known = local.mapTo(mutableListOf()) { KnownFood(it.id, it) }
        val adds = mutableListOf<ExportedCustomFood>()
        val updates = linkedMapOf<Long, CustomFoodUpdate>()
        var c = ImportCounts()
        for (raw in incoming) {
            val f = raw.normalized()
            val problem = f.validationError()
            if (problem != null) {
                invalid += "Alimento propio \"${raw.name}\": $problem"; c = c.copy(invalid = c.invalid + 1); continue
            }
            val match = known.firstOrNull { it.food.id == f.id && it.food.createdAtMillis == f.createdAtMillis }
                ?: known.firstOrNull { it.food.key() == f.key() }
            when {
                match == null -> { c = c.copy(added = c.added + 1); adds += f; known += KnownFood(null, f) }
                match.food.sameContent(f) -> c = c.copy(unchanged = c.unchanged + 1)
                // Los añadidos en esta misma importación no se reescriben: se queda el primero.
                match.localId != null && f.updatedAtMillis > match.food.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1)
                    updates[match.localId] = CustomFoodUpdate(match.localId, f)
                    match.food = f.copy(id = match.food.id, createdAtMillis = match.food.createdAtMillis)
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return FoodPlan(adds, updates.values.toList(), c)
    }

    private fun ExportedCustomFood.normalized() = copy(
        name = name.trim(),
        brand = brand?.trim()?.ifEmpty { null },
        servingLabel = servingLabel?.trim()?.ifEmpty { null },
    )

    private fun ExportedCustomFood.validationError(): String? =
        try { NewCustomFood(name, brand, nutrients.toDomain(), servingSizeG, servingLabel); null }
        catch (e: IllegalArgumentException) { e.message ?: "datos no válidos" }

    private fun ExportedCustomFood.key() = name.lowercase() to brand?.lowercase()

    private fun ExportedCustomFood.sameContent(o: ExportedCustomFood) =
        key() == o.key() && nutrients == o.nutrients && servingSizeG == o.servingSizeG &&
            servingLabel == o.servingLabel

    // --- Diario ---

    private class DiaryPlan(val adds: List<ExportedDiaryEntry>, val counts: ImportCounts)

    private data class DiaryKey(val date: String, val barcode: String?, val name: String, val createdAtMillis: Long)

    private fun DiaryEntry.key() = DiaryKey(date, barcode, name, createdAtMillis)

    private fun planDiary(
        incoming: List<ExportedDiaryEntry>,
        local: List<DiaryEntry>,
        invalid: MutableList<String>,
    ): DiaryPlan {
        val pending = local.groupBy { it.key() }.mapValues { it.value.toMutableList() }
        val adds = mutableListOf<ExportedDiaryEntry>()
        var c = ImportCounts()
        for (e in incoming) {
            val domain = try {
                require(DATE.matches(e.date)) { "fecha no válida" }
                require(e.name.isNotBlank()) { "nombre vacío" }
                e.toDomain()
            } catch (ex: IllegalArgumentException) {
                invalid += "Entrada del diario \"${e.name}\" (${e.date}): ${ex.message}"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            val match = pending[domain.key()]?.removeFirstOrNull()
            when {
                match == null -> { c = c.copy(added = c.added + 1); adds += e }
                match.copy(id = 0) == domain.copy(id = 0) -> c = c.copy(unchanged = c.unchanged + 1)
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return DiaryPlan(adds, c)
    }

    private companion object {
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

/** Cambios que se aplican a los almacenes locales. */
data class ImportChanges(
    /** Productos a insertar o reemplazar por EAN. */
    val products: List<ExportedProduct> = emptyList(),
    /** Alimentos propios nuevos; el id lo asigna el almacén. */
    val customFoodsToAdd: List<ExportedCustomFood> = emptyList(),
    val customFoodsToUpdate: List<CustomFoodUpdate> = emptyList(),
    /** Entradas nuevas del diario; el id lo asigna el almacén. */
    val diaryEntriesToAdd: List<ExportedDiaryEntry> = emptyList(),
) {
    val isEmpty: Boolean
        get() = products.isEmpty() && customFoodsToAdd.isEmpty() && customFoodsToUpdate.isEmpty() &&
            diaryEntriesToAdd.isEmpty()
}

/** Sustituye los datos del alimento local [localId] por los de [food] (se conservan su id y `createdAtMillis`). */
data class CustomFoodUpdate(val localId: Long, val food: ExportedCustomFood)

data class ImportPlan(val changes: ImportChanges, val report: ImportReport)

/** Resultado por tipo de dato. */
data class ImportCounts(
    /** Nuevos en local. */
    val added: Int = 0,
    /** Existían y se reemplazaron por la versión importada (más reciente). */
    val updated: Int = 0,
    /** Duplicados exactos, ignorados. */
    val unchanged: Int = 0,
    /** Conflictos en los que se conservó el dato local. */
    val keptLocal: Int = 0,
    /** Omitidos por ser inválidos. */
    val invalid: Int = 0,
)

data class ImportReport(
    val products: ImportCounts,
    val customFoods: ImportCounts,
    val diaryEntries: ImportCounts,
    /** Descripción de cada elemento inválido omitido. */
    val invalidItems: List<String> = emptyList(),
)

/** Almacenes locales sobre los que se importa: lectura (como la exportación) y escritura atómica. */
interface ImportTarget : ExportSource {
    /** Aplica todos los cambios en una única transacción. */
    suspend fun apply(changes: ImportChanges)
}

/** [ImportTarget] respaldado por las tablas SQLDelight. */
class SqlDelightImportTarget(
    private val productQueries: ProductQueries,
    private val diaryQueries: DiaryEntryQueries,
    private val customFoodQueries: CustomFoodQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ImportTarget, ExportSource by SqlDelightExportSource(productQueries, diaryQueries, customFoodQueries, dispatcher) {

    override suspend fun apply(changes: ImportChanges): Unit = withContext(dispatcher) {
        // Las consultas comparten el driver, así que una transacción abarca las tres tablas.
        diaryQueries.transaction {
            changes.products.forEach { p ->
                productQueries.upsert(
                    barcode = p.barcode, name = p.name, brand = p.brand, image_url = p.imageUrl,
                    kcal = p.nutrients.kcal, protein_g = p.nutrients.proteinG, carbs_g = p.nutrients.carbsG,
                    sugars_g = p.nutrients.sugarsG, fat_g = p.nutrients.fatG,
                    saturated_fat_g = p.nutrients.saturatedFatG, fiber_g = p.nutrients.fiberG,
                    salt_g = p.nutrients.saltG, serving_size_g = p.servingSizeG, serving_label = p.servingLabel,
                    source = p.source, cached_at = p.updatedAtMillis,
                )
            }
            changes.customFoodsToAdd.forEach { f ->
                customFoodQueries.insert(
                    name = f.name, brand = f.brand, kcal = f.nutrients.kcal!!, protein_g = f.nutrients.proteinG!!,
                    carbs_g = f.nutrients.carbsG!!, fat_g = f.nutrients.fatG!!, sugars_g = f.nutrients.sugarsG,
                    saturated_fat_g = f.nutrients.saturatedFatG, fiber_g = f.nutrients.fiberG,
                    salt_g = f.nutrients.saltG, serving_size_g = f.servingSizeG, serving_label = f.servingLabel,
                    created_at = f.createdAtMillis, updated_at = f.updatedAtMillis,
                )
            }
            changes.customFoodsToUpdate.forEach { (id, f) ->
                customFoodQueries.update(
                    name = f.name, brand = f.brand, kcal = f.nutrients.kcal!!, protein_g = f.nutrients.proteinG!!,
                    carbs_g = f.nutrients.carbsG!!, fat_g = f.nutrients.fatG!!, sugars_g = f.nutrients.sugarsG,
                    saturated_fat_g = f.nutrients.saturatedFatG, fiber_g = f.nutrients.fiberG,
                    salt_g = f.nutrients.saltG, serving_size_g = f.servingSizeG, serving_label = f.servingLabel,
                    updated_at = f.updatedAtMillis, id = id,
                )
            }
            changes.diaryEntriesToAdd.forEach { e ->
                diaryQueries.insert(
                    date = e.date, meal = e.meal.name, barcode = e.barcode, name = e.name, quantity = e.quantity,
                    unit = e.unit.name, serving_size_g = e.servingSizeG, kcal_100g = e.nutrients.kcal,
                    protein_100g = e.nutrients.proteinG, carbs_100g = e.nutrients.carbsG,
                    fat_100g = e.nutrients.fatG, created_at = e.createdAtMillis,
                )
            }
        }
    }
}
