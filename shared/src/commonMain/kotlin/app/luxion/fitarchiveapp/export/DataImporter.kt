package app.luxion.fitarchiveapp.export

import app.luxion.fitarchiveapp.data.FavoriteKeys
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.db.CustomFoodQueries
import app.luxion.fitarchiveapp.db.DiaryEntryQueries
import app.luxion.fitarchiveapp.db.FavoriteFoodQueries
import app.luxion.fitarchiveapp.db.ProductQueries
import app.luxion.fitarchiveapp.db.MealTemplateQueries
import app.luxion.fitarchiveapp.db.RecipeQueries
import app.luxion.fitarchiveapp.db.SettingsQueries
import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.db.ExerciseQueries
import app.luxion.fitarchiveapp.db.WaterQueries
import app.luxion.fitarchiveapp.db.WeightEntryQueries
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.model.WaterRules
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.data.writeIngredients
import app.luxion.fitarchiveapp.data.writeItems
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.TemplateItem
import app.luxion.fitarchiveapp.model.WeightRules

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.QuickEntry
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.ProductCorrection
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
 * **Pesos**
 * - Identidad: la fecha (`date`); hay un registro por día.
 * - No existe en local: se añade.
 * - Existe con el mismo peso: duplicado, se ignora.
 * - Existe con peso distinto: gana el de `updatedAtMillis` más reciente; con empate, el local.
 * - Si el archivo trae la misma fecha dos veces, vale el de `updatedAtMillis` más reciente.
 * - El **peso objetivo** (uno solo) cuenta en el informe de pesos: sin objetivo local se añade; igual, se ignora;
 *   distinto, gana el de `updatedAtMillis` más reciente (con empate, el local). Fuera de rango es inválido.
 * - Las **comidas al día** (`mealsPerDay`, opcional) solo se toman si este dispositivo aún no ha elegido; si ya hay
 *   ajuste local se conserva. Un archivo sin el campo no cambia nada; fuera de 3 a 6 es inválido.
 * - Los **recordatorios** (`reminders`, opcional) siguen la misma política: solo se toman si este dispositivo aún no los
 *   ha configurado; se sanean (días 1 a 7, horas 0 a 1439).
 *
 * **Agua**
 * - Identidad: la fecha (`date`); hay un total por día. Misma política que los pesos: no existe en local, se añade;
 *   mismo total, se ignora; total distinto, gana el de `updatedAtMillis` más reciente (con empate, el local). Si el
 *   archivo trae la misma fecha dos veces, vale el más reciente. Totales fuera de 1 a 20 000 ml o fechas mal formadas
 *   son inválidos.
 * - El **objetivo de agua** (uno solo) cuenta en el informe de agua con la misma regla que el peso objetivo; fuera de
 *   500 a 10 000 ml es inválido.
 *
 * **Recetas**
 * - Identidad: igual que los alimentos propios: `(id, createdAtMillis)` y, si no hay coincidencia, el nombre sin
 *   distinguir mayúsculas ni espacios sobrantes. Los ingredientes viajan dentro de la receta (copia de nombre y
 *   valores por 100 g), así que no dependen de que el alimento exista en el dispositivo de destino.
 * - No existe en local: se añade con un id nuevo, conservando sus fechas.
 * - Existe con el mismo contenido (nombre, raciones e ingredientes en el mismo orden): duplicado, se ignora.
 * - Existe con contenido distinto: gana el de `updatedAtMillis` más reciente (se reemplazan también los ingredientes);
 *   con empate, el local.
 * - Una receta sin ingredientes, sin nombre, con raciones fuera de rango o con un ingrediente inválido se omite entera.
 *
 * **Favoritos**
 * - Identidad: el `barcode` (EAN o `generic:<id>`) o, en un alimento propio, nombre + marca sin distinguir
 *   mayúsculas ni espacios sobrantes (el id de un alimento propio es local de cada dispositivo).
 * - No existe en local: se añade, conservando su fecha. Existe: duplicado, se ignora (un favorito no tiene contenido
 *   que pueda diferir, así que no hay conflictos). Nunca se desmarca nada local.
 * - Un favorito propio solo se admite si ese alimento existe en local o llega en el mismo archivo; si no, es
 *   inválido. Uno sin `barcode` ni nombre, o con ambos, también.
 *
 * **Plantillas de comida** (`mealTemplates`, colección opcional)
 * - Identidad y política: las mismas que las recetas (`(id, createdAtMillis)` y, si no hay coincidencia, el nombre sin
 *   distinguir mayúsculas ni espacios sobrantes; gana el `updatedAtMillis` más reciente, con empate el local). Los
 *   alimentos viajan dentro de la plantilla con su cantidad y unidad y se comparan en el mismo orden.
 * - Una plantilla sin nombre, sin alimentos o con una cantidad ≤ 0 o nutrientes negativos se omite entera.
 * **Correcciones de productos** (valores nutricionales que el usuario puso sobre un producto)
 * - Identidad: el `barcode` (EAN o `generic:<id>`); hay una corrección por producto. Se importan aunque el producto no
 *   esté aún en local (se aplican cuando aparece). Mismo criterio que los pesos: no existe en local, se añade; mismos
 *   valores, se ignora; valores distintos, gana el de `updatedAtMillis` más reciente (con empate, el local).
 *   Valores no válidos (faltan kcal o macros, negativos, fuera de rango) son inválidos.
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
        val weights = planWeights(file.weights, target.weights(), invalid)
        val recipes = planRecipes(file.recipes, target.recipes(), invalid)
        val goal = planWeightGoal(file.weightGoal, target.weightGoal(), invalid)
        val meals = planMealsPerDay(file.mealsPerDay, target.mealsPerDay(), invalid)
        val reminders = if (target.reminders() == null) file.reminders?.sanitized() else null
        val water = planWater(file.water, target.water(), invalid)
        val waterGoal = planWaterGoal(file.waterGoal, target.waterGoal(), invalid)
        val favorites = planFavorites(file.favorites, target.favorites(), foods.finalKeys, foods.renamed, invalid)
        val templates = planTemplates(file.mealTemplates, target.mealTemplates(), invalid)
        val exercises = planExercises(file.exercises, target.exercises(), invalid)
        val exercisePercent = planExercisePercent(file.exercisePercent, target.exercisePercent(), invalid)
        val corrections = planCorrections(file.productCorrections, target.productCorrections(), invalid)
        return ImportPlan(
            changes = ImportChanges(
                products = products.writes,
                customFoodsToAdd = foods.adds,
                customFoodsToUpdate = foods.updates,
                diaryEntriesToAdd = diary.adds,
                weights = weights.writes,
                recipesToAdd = recipes.adds,
                recipesToUpdate = recipes.updates,
                weightGoal = goal.write,
                mealsPerDay = meals,
                reminders = reminders,
                water = water.writes,
                waterGoal = waterGoal.write,
                favoritesToAdd = favorites.adds,
                templatesToAdd = templates.adds,
                templatesToUpdate = templates.updates,
                exercisesToAdd = exercises.adds,
                exercisePercent = exercisePercent.write,
                productCorrections = corrections.writes,
            ),
            report = ImportReport(
                products.counts, foods.counts, diary.counts, invalid, weights.counts + goal.counts, recipes.counts,
                water.counts + waterGoal.counts, favorites.counts, templates.counts,
                exercise = exercises.counts + exercisePercent.counts,
                productCorrections = corrections.counts,
            ),
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
            // Exportaciones antiguas incluían la base genérica; ya viene con la app, así que no cuenta.
            if (p.source == GenericFoodImporter.SOURCE) continue
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
        /** Alimento del archivo reconocido como un alimento local con otro nombre/marca (clave de favorito → el local). */
        val renamed: Map<String, ExportedCustomFood> = emptyMap(),
        /** Claves de favorito de todos los alimentos propios que habrá tras la importación (locales, ya actualizados, y nuevos). */
        val finalKeys: Set<String> = emptySet(),
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
        val matched = linkedMapOf<String, KnownFood>()
        var c = ImportCounts()
        for (raw in incoming) {
            val f = raw.normalized()
            val problem = f.validationError()
            if (problem != null) {
                invalid += "Alimento propio \"${raw.name}\": $problem"; c = c.copy(invalid = c.invalid + 1); continue
            }
            val match = known.firstOrNull { it.food.id == f.id && it.food.createdAtMillis == f.createdAtMillis }
                ?: known.firstOrNull { it.food.key() == f.key() }
            if (match != null) matched[f.favoriteKey()] = match
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
        val renamed = matched.mapValues { it.value.food }.filter { (key, food) -> key != food.favoriteKey() }
        return FoodPlan(adds, updates.values.toList(), c, renamed, known.mapTo(mutableSetOf()) { it.food.favoriteKey() })
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

    // --- Recetas ---

    private class RecipePlan(
        val adds: List<ExportedRecipe>,
        val updates: List<RecipeUpdate>,
        val counts: ImportCounts,
    )

    /** Receta local o ya añadida en esta importación (`localId == null`). */
    private class KnownRecipe(val localId: Long?, var recipe: ExportedRecipe)

    private fun planRecipes(
        incoming: List<ExportedRecipe>,
        local: List<ExportedRecipe>,
        invalid: MutableList<String>,
    ): RecipePlan {
        val known = local.mapTo(mutableListOf()) { KnownRecipe(it.id, it) }
        val adds = mutableListOf<ExportedRecipe>()
        val updates = linkedMapOf<Long, RecipeUpdate>()
        var c = ImportCounts()
        for (raw in incoming) {
            val r = raw.normalized()
            val problem = r.validationError()
            if (problem != null) {
                invalid += "Receta \"${raw.name}\": $problem"; c = c.copy(invalid = c.invalid + 1); continue
            }
            val match = known.firstOrNull { it.recipe.id == r.id && it.recipe.createdAtMillis == r.createdAtMillis }
                ?: known.firstOrNull { it.recipe.name.lowercase() == r.name.lowercase() }
            when {
                match == null -> { c = c.copy(added = c.added + 1); adds += r; known += KnownRecipe(null, r) }
                match.recipe.sameContent(r) -> c = c.copy(unchanged = c.unchanged + 1)
                // Las añadidas en esta misma importación no se reescriben: se queda la primera.
                match.localId != null && r.updatedAtMillis > match.recipe.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1)
                    updates[match.localId] = RecipeUpdate(match.localId, r)
                    match.recipe = r.copy(id = match.recipe.id, createdAtMillis = match.recipe.createdAtMillis)
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return RecipePlan(adds, updates.values.toList(), c)
    }

    private fun ExportedRecipe.normalized() = copy(name = name.trim())

    private fun ExportedRecipe.validationError(): String? =
        try { toNewRecipe(); null } catch (e: IllegalArgumentException) { e.message ?: "datos no válidos" }

    private fun ExportedRecipe.sameContent(o: ExportedRecipe) =
        name.lowercase() == o.name.lowercase() && servings == o.servings && ingredients == o.ingredients

    // --- Plantillas de comida ---

    private class TemplatePlan(
        val adds: List<ExportedMealTemplate>,
        val updates: List<TemplateUpdate>,
        val counts: ImportCounts,
    )

    /** Plantilla local o ya añadida en esta importación (`localId == null`). */
    private class KnownTemplate(val localId: Long?, var template: ExportedMealTemplate)

    private fun planTemplates(
        incoming: List<ExportedMealTemplate>,
        local: List<ExportedMealTemplate>,
        invalid: MutableList<String>,
    ): TemplatePlan {
        val known = local.mapTo(mutableListOf()) { KnownTemplate(it.id, it) }
        val adds = mutableListOf<ExportedMealTemplate>()
        val updates = linkedMapOf<Long, TemplateUpdate>()
        var c = ImportCounts()
        for (raw in incoming) {
            val t = raw.copy(name = raw.name.trim())
            val problem = try { t.toNewTemplate(); null } catch (e: IllegalArgumentException) { e.message ?: "datos no válidos" }
            if (problem != null) {
                invalid += "Plantilla \"${raw.name}\": $problem"; c = c.copy(invalid = c.invalid + 1); continue
            }
            val match = known.firstOrNull { it.template.id == t.id && it.template.createdAtMillis == t.createdAtMillis }
                ?: known.firstOrNull { it.template.name.lowercase() == t.name.lowercase() }
            when {
                match == null -> { c = c.copy(added = c.added + 1); adds += t; known += KnownTemplate(null, t) }
                match.template.name.lowercase() == t.name.lowercase() && match.template.items == t.items ->
                    c = c.copy(unchanged = c.unchanged + 1)
                // Las añadidas en esta misma importación no se reescriben: se queda la primera.
                match.localId != null && t.updatedAtMillis > match.template.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1)
                    updates[match.localId] = TemplateUpdate(match.localId, t)
                    match.template = t.copy(id = match.template.id, createdAtMillis = match.template.createdAtMillis)
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return TemplatePlan(adds, updates.values.toList(), c)
    }

    // --- Pesos ---

    private class WeightPlan(val writes: List<ExportedWeight>, val counts: ImportCounts)

    private fun planWeights(
        incoming: List<ExportedWeight>,
        local: List<ExportedWeight>,
        invalid: MutableList<String>,
    ): WeightPlan {
        val byDate = local.associateByTo(mutableMapOf()) { it.date }
        val writes = linkedMapOf<String, ExportedWeight>()
        var c = ImportCounts()
        for (w in incoming) {
            val ok = try { NewWeightEntry(w.date, w.weightKg); true } catch (e: IllegalArgumentException) {
                invalid += "Peso del ${w.date} (${w.weightKg} kg): ${e.message}"
                c = c.copy(invalid = c.invalid + 1); false
            }
            if (!ok) continue
            val existing = byDate[w.date]
            when {
                existing == null -> { c = c.copy(added = c.added + 1); byDate[w.date] = w; writes[w.date] = w }
                existing.weightKg == w.weightKg -> c = c.copy(unchanged = c.unchanged + 1)
                w.updatedAtMillis > existing.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1); byDate[w.date] = w; writes[w.date] = w
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return WeightPlan(writes.values.toList(), c)
    }

    private class GoalPlan(val write: ExportedWeightGoal?, val counts: ImportCounts)

    private fun planWeightGoal(incoming: ExportedWeightGoal?, local: ExportedWeightGoal?, invalid: MutableList<String>): GoalPlan {
        if (incoming == null) return GoalPlan(null, ImportCounts())
        if (!incoming.targetKg.isFinite() || incoming.targetKg !in WeightRules.MIN_KG..WeightRules.MAX_KG) {
            invalid += "Peso objetivo (${incoming.targetKg} kg): fuera de rango"
            return GoalPlan(null, ImportCounts(invalid = 1))
        }
        return when {
            local == null -> GoalPlan(incoming, ImportCounts(added = 1))
            local.targetKg == incoming.targetKg -> GoalPlan(null, ImportCounts(unchanged = 1))
            incoming.updatedAtMillis > local.updatedAtMillis -> GoalPlan(incoming, ImportCounts(updated = 1))
            else -> GoalPlan(null, ImportCounts(keptLocal = 1))
        }
    }

    /**
     * Comidas al día: solo se toma la del archivo si este dispositivo aún no ha elegido (así la primera vez no se
     * vuelve a preguntar); si ya hay un ajuste local, gana el local. Sin el campo (archivos antiguos) no cambia nada.
     */
    private fun planMealsPerDay(incoming: Int?, local: Int?, invalid: MutableList<String>): Int? {
        if (incoming == null) return null
        if (!MealPlan.isValid(incoming)) {
            invalid += "Comidas al día ($incoming): fuera de rango"
            return null
        }
        return if (local == null) incoming else null
    }

    // --- Agua ---

    private class WaterPlan(val writes: List<ExportedWater>, val counts: ImportCounts)

    private fun planWater(
        incoming: List<ExportedWater>,
        local: List<ExportedWater>,
        invalid: MutableList<String>,
    ): WaterPlan {
        val byDate = local.associateByTo(mutableMapOf()) { it.date }
        val writes = linkedMapOf<String, ExportedWater>()
        var c = ImportCounts()
        for (w in incoming) {
            val problem = when {
                !DATE.matches(w.date) -> "fecha no válida"
                w.ml !in 1..WaterRules.MAX_DAY_ML -> "cantidad fuera de rango"
                else -> null
            }
            if (problem != null) {
                invalid += "Agua del ${w.date} (${w.ml} ml): $problem"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            val existing = byDate[w.date]
            when {
                existing == null -> { c = c.copy(added = c.added + 1); byDate[w.date] = w; writes[w.date] = w }
                existing.ml == w.ml -> c = c.copy(unchanged = c.unchanged + 1)
                w.updatedAtMillis > existing.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1); byDate[w.date] = w; writes[w.date] = w
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return WaterPlan(writes.values.toList(), c)
    }

    private class WaterGoalPlan(val write: ExportedWaterGoal?, val counts: ImportCounts)

    private fun planWaterGoal(incoming: ExportedWaterGoal?, local: ExportedWaterGoal?, invalid: MutableList<String>): WaterGoalPlan {
        if (incoming == null) return WaterGoalPlan(null, ImportCounts())
        if (!WaterRules.isValidGoal(incoming.goalMl)) {
            invalid += "Objetivo de agua (${incoming.goalMl} ml): fuera de rango"
            return WaterGoalPlan(null, ImportCounts(invalid = 1))
        }
        return when {
            local == null -> WaterGoalPlan(incoming, ImportCounts(added = 1))
            local.goalMl == incoming.goalMl -> WaterGoalPlan(null, ImportCounts(unchanged = 1))
            incoming.updatedAtMillis > local.updatedAtMillis -> WaterGoalPlan(incoming, ImportCounts(updated = 1))
            else -> WaterGoalPlan(null, ImportCounts(keptLocal = 1))
        }
    }

    // --- Ejercicio ---

    private class ExercisePlan(val adds: List<ExportedExercise>, val counts: ImportCounts)

    /**
     * Una actividad no tiene id estable entre dispositivos: se reconoce por fecha, nombre (sin mayúsculas), kcal y
     * duración. Las idénticas se ignoran; el resto se añade. Dos idénticas en el archivo se añaden las dos si en
     * local no había ninguna (son dos sesiones iguales el mismo día), pero no se duplican al reimportar.
     */
    private fun planExercises(
        incoming: List<ExportedExercise>,
        local: List<ExportedExercise>,
        invalid: MutableList<String>,
    ): ExercisePlan {
        fun key(e: ExportedExercise) = listOf(e.date, e.name.trim().lowercase(), e.kcal, e.minutes)
        // Cuántas hay ya de cada una: al reimportar el mismo archivo, las que ya existen no se vuelven a añadir.
        val remaining = local.groupingBy(::key).eachCount().toMutableMap()
        val adds = mutableListOf<ExportedExercise>()
        var c = ImportCounts()
        for (e in incoming) {
            val problem = when {
                !DATE.matches(e.date) -> "fecha no válida"
                e.name.isBlank() || e.name.trim().length > ExerciseRules.MAX_NAME_LENGTH -> "nombre no válido"
                !ExerciseRules.isValidKcal(e.kcal) -> "kcal fuera de rango"
                !ExerciseRules.isValidMinutes(e.minutes) -> "duración fuera de rango"
                else -> null
            }
            if (problem != null) {
                invalid += "Ejercicio «${e.name}» del ${e.date}: $problem"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            val k = key(e)
            val left = remaining[k] ?: 0
            if (left > 0) {
                remaining[k] = left - 1
                c = c.copy(unchanged = c.unchanged + 1)
            } else {
                adds += e.copy(name = e.name.trim())
                c = c.copy(added = c.added + 1)
            }
        }
        return ExercisePlan(adds, c)
    }

    private class ExercisePercentPlan(val write: ExportedExercisePercent?, val counts: ImportCounts)

    private fun planExercisePercent(
        incoming: ExportedExercisePercent?,
        local: ExportedExercisePercent?,
        invalid: MutableList<String>,
    ): ExercisePercentPlan {
        if (incoming == null) return ExercisePercentPlan(null, ImportCounts())
        if (!ExerciseRules.isValidPercent(incoming.percent)) {
            invalid += "Porcentaje de ejercicio (${incoming.percent} %): fuera de rango"
            return ExercisePercentPlan(null, ImportCounts(invalid = 1))
        }
        return when {
            local == null -> ExercisePercentPlan(incoming, ImportCounts(added = 1))
            local.percent == incoming.percent -> ExercisePercentPlan(null, ImportCounts(unchanged = 1))
            incoming.updatedAtMillis > local.updatedAtMillis -> ExercisePercentPlan(incoming, ImportCounts(updated = 1))
            else -> ExercisePercentPlan(null, ImportCounts(keptLocal = 1))
        }
    }

    // --- Favoritos ---

    private class FavoritePlan(val adds: List<ExportedFavorite>, val counts: ImportCounts)

    private fun ExportedCustomFood.favoriteKey() = "custom:${name.trim().lowercase()}|${brand?.trim()?.lowercase().orEmpty()}"

    /** Clave de identidad de un favorito, o `null` si no es válido (ni producto ni alimento propio, o ambos). */
    private fun ExportedFavorite.identity(): String? {
        val code = barcode?.trim().orEmpty()
        val name = customName?.trim().orEmpty()
        return when {
            code.isNotEmpty() && name.isEmpty() && customBrand == null -> "ean:$code"
            code.isEmpty() && name.isNotEmpty() -> "custom:${name.lowercase()}|${customBrand?.trim()?.lowercase().orEmpty()}"
            else -> null
        }
    }

    private fun planFavorites(
        incoming: List<ExportedFavorite>,
        local: List<ExportedFavorite>,
        customKeys: Set<String>,
        renamed: Map<String, ExportedCustomFood>,
        invalid: MutableList<String>,
    ): FavoritePlan {
        val known = local.mapNotNullTo(mutableSetOf()) { it.identity() }
        val adds = mutableListOf<ExportedFavorite>()
        var c = ImportCounts()
        for (raw in incoming) {
            // Un alimento propio que el archivo trae con otro nombre pero que ya existe en local (mismo id y fecha de
            // creación, p. ej. renombrado después de exportar) se enlaza con el local.
            val f = raw.identity()?.let { renamed[it] }
                ?.let { raw.copy(customName = it.name, customBrand = it.brand) } ?: raw
            val id = f.identity()
            if (id == null) {
                invalid += "Favorito \"${f.customName ?: f.barcode.orEmpty()}\": debe indicar un producto o un alimento propio"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            if (id.startsWith("custom:") && id !in customKeys) {
                invalid += "Favorito \"${f.customName}\": el alimento propio no existe"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            if (known.add(id)) { c = c.copy(added = c.added + 1); adds += f.normalized() }
            else c = c.copy(unchanged = c.unchanged + 1)
        }
        return FavoritePlan(adds, c)
    }

    private fun ExportedFavorite.normalized() = copy(
        barcode = barcode?.trim()?.ifEmpty { null },
        customName = customName?.trim()?.ifEmpty { null },
        customBrand = customBrand?.trim()?.ifEmpty { null },
    )

    // --- Correcciones de productos ---

    private class CorrectionPlan(val writes: List<ExportedProductCorrection>, val counts: ImportCounts)

    private fun planCorrections(
        incoming: List<ExportedProductCorrection>,
        local: List<ExportedProductCorrection>,
        invalid: MutableList<String>,
    ): CorrectionPlan {
        val byBarcode = local.associateByTo(mutableMapOf()) { it.barcode }
        val writes = linkedMapOf<String, ExportedProductCorrection>()
        var c = ImportCounts()
        for (raw in incoming) {
            val p = raw.copy(barcode = raw.barcode.trim())
            val problem = try {
                ProductCorrection(p.barcode, p.nutrients.toDomain()); null
            } catch (e: IllegalArgumentException) {
                e.message ?: "datos no válidos"
            }
            if (problem != null) {
                invalid += "Corrección del producto \"${raw.barcode}\": $problem"
                c = c.copy(invalid = c.invalid + 1); continue
            }
            val existing = byBarcode[p.barcode]
            when {
                existing == null -> { c = c.copy(added = c.added + 1); byBarcode[p.barcode] = p; writes[p.barcode] = p }
                existing.nutrients == p.nutrients -> c = c.copy(unchanged = c.unchanged + 1)
                p.updatedAtMillis > existing.updatedAtMillis -> {
                    c = c.copy(updated = c.updated + 1); byBarcode[p.barcode] = p; writes[p.barcode] = p
                }
                else -> c = c.copy(keptLocal = c.keptLocal + 1)
            }
        }
        return CorrectionPlan(writes.values.toList(), c)
    }

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
                require(!e.quick || (e.quantity == QuickEntry.QUANTITY_G && e.unit == QuantityUnit.GRAMS)) {
                    "entrada rápida con cantidad no válida"
                }
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
    /** Pesos a insertar o reemplazar por fecha. */
    val weights: List<ExportedWeight> = emptyList(),
    /** Recetas nuevas; el id lo asigna el almacén. */
    val recipesToAdd: List<ExportedRecipe> = emptyList(),
    val recipesToUpdate: List<RecipeUpdate> = emptyList(),
    /** Peso objetivo a fijar, o `null` si no cambia. */
    val weightGoal: ExportedWeightGoal? = null,
    /** Comidas al día a fijar, o `null` si no cambia. */
    val mealsPerDay: Int? = null,
    /** Recordatorios a fijar, o `null` si no cambian. */
    val reminders: ReminderSettings? = null,
    /** Totales de agua a insertar o reemplazar por fecha. */
    val water: List<ExportedWater> = emptyList(),
    /** Objetivo de agua a fijar, o `null` si no cambia. */
    val waterGoal: ExportedWaterGoal? = null,
    /** Favoritos nuevos; los propios se enlazan por nombre y marca con el alimento local. */
    val favoritesToAdd: List<ExportedFavorite> = emptyList(),
    /** Plantillas de comida nuevas; el id lo asigna el almacén. */
    val templatesToAdd: List<ExportedMealTemplate> = emptyList(),
    val templatesToUpdate: List<TemplateUpdate> = emptyList(),
    /** Actividades de ejercicio nuevas; el id lo asigna el almacén. */
    val exercisesToAdd: List<ExportedExercise> = emptyList(),
    /** Porcentaje de ejercicio a fijar, o `null` si no cambia. */
    val exercisePercent: ExportedExercisePercent? = null,
    /** Correcciones de productos a insertar o reemplazar por código. */
    val productCorrections: List<ExportedProductCorrection> = emptyList(),
) {
    val isEmpty: Boolean
        get() = products.isEmpty() && customFoodsToAdd.isEmpty() && customFoodsToUpdate.isEmpty() &&
            diaryEntriesToAdd.isEmpty() && weights.isEmpty() && recipesToAdd.isEmpty() && recipesToUpdate.isEmpty() &&
            weightGoal == null && mealsPerDay == null && reminders == null && water.isEmpty() && waterGoal == null && favoritesToAdd.isEmpty() &&
            templatesToAdd.isEmpty() && templatesToUpdate.isEmpty() &&
            exercisesToAdd.isEmpty() && exercisePercent == null && productCorrections.isEmpty()
}

/** Sustituye los datos de la receta local [localId] por los de [recipe] (se conservan su id y `createdAtMillis`). */
data class RecipeUpdate(val localId: Long, val recipe: ExportedRecipe)

/** Sustituye los datos de la plantilla local [localId] por los de [template] (se conservan su id y `createdAtMillis`). */
data class TemplateUpdate(val localId: Long, val template: ExportedMealTemplate)

/** Plantilla de un archivo de exportación como datos de dominio; lanza [IllegalArgumentException] si no es válida. */
fun ExportedMealTemplate.toNewTemplate() = NewMealTemplate(
    name = name,
    items = items.map {
        require(it.nutrients.presentValues().all { v -> v.isFinite() && v >= 0 }) {
            "los nutrientes de «${it.name}» no pueden ser negativos"
        }
        require(it.quantity > 0 && it.quantity.isFinite()) { "la cantidad de «${it.name}» debe ser mayor que 0" }
        TemplateItem(it.barcode, it.name, quantityOf(it.quantity, it.unit), it.servingSizeG, it.nutrients.toDomain())
    },
)

/** Receta de un archivo de exportación como datos de dominio; lanza [IllegalArgumentException] si no es válida. */
fun ExportedRecipe.toNewRecipe() = NewRecipe(
    name = name,
    servings = servings,
    ingredients = ingredients.map {
        require(it.nutrients.presentValues().all { v -> v.isFinite() && v >= 0 }) {
            "los nutrientes de «${it.name}» no pueden ser negativos"
        }
        RecipeIngredient(it.barcode, it.name, it.grams, it.nutrients.toDomain(), it.servingSizeG)
    },
)

private fun ExportedNutrients.presentValues() =
    listOfNotNull(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarsG, fiberG, saltG)

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
) {
    operator fun plus(o: ImportCounts) = ImportCounts(
        added + o.added, updated + o.updated, unchanged + o.unchanged, keptLocal + o.keptLocal, invalid + o.invalid,
    )
}

data class ImportReport(
    val products: ImportCounts,
    val customFoods: ImportCounts,
    val diaryEntries: ImportCounts,
    /** Descripción de cada elemento inválido omitido. */
    val invalidItems: List<String> = emptyList(),
    val weights: ImportCounts = ImportCounts(),
    val recipes: ImportCounts = ImportCounts(),
    /** Totales de agua y su objetivo. */
    val water: ImportCounts = ImportCounts(),
    val favorites: ImportCounts = ImportCounts(),
    val mealTemplates: ImportCounts = ImportCounts(),
    /** Actividades de ejercicio y su porcentaje. */
    val exercise: ImportCounts = ImportCounts(),
    /** Correcciones de valores de productos. */
    val productCorrections: ImportCounts = ImportCounts(),
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
    private val weightQueries: WeightEntryQueries,
    private val recipeQueries: RecipeQueries,
    private val settingsQueries: SettingsQueries,
    private val favoriteQueries: FavoriteFoodQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val waterQueries: WaterQueries? = null,
    private val templateQueries: MealTemplateQueries? = null,
    private val exerciseQueries: ExerciseQueries? = null,
) : ImportTarget, ExportSource by SqlDelightExportSource(
    productQueries, diaryQueries, customFoodQueries, weightQueries, recipeQueries, settingsQueries, favoriteQueries,
    dispatcher, waterQueries, templateQueries, exerciseQueries,
) {

    override suspend fun apply(changes: ImportChanges): Unit = withContext(dispatcher) {
        // Las consultas comparten el driver, así que una transacción abarca todas las tablas.
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
            changes.weights.forEach { w ->
                val created = weightQueries.selectByDate(w.date).executeAsOneOrNull()?.created_at ?: w.createdAtMillis
                weightQueries.upsert(w.date, w.weightKg, created, w.updatedAtMillis)
            }
            changes.recipesToAdd.forEach { r ->
                recipeQueries.insert(r.name, r.servings?.toLong(), r.createdAtMillis, r.updatedAtMillis)
                recipeQueries.writeIngredients(recipeQueries.lastInsertedId().executeAsOne(), r.toNewRecipe().ingredients)
            }
            changes.recipesToUpdate.forEach { (id, r) ->
                recipeQueries.update(r.name, r.servings?.toLong(), r.updatedAtMillis, id)
                recipeQueries.deleteIngredients(id)
                recipeQueries.writeIngredients(id, r.toNewRecipe().ingredients)
            }
            if (templateQueries != null) {
                changes.templatesToAdd.forEach { t ->
                    templateQueries.insert(t.name, t.createdAtMillis, t.updatedAtMillis)
                    templateQueries.writeItems(templateQueries.lastInsertedId().executeAsOne(), t.toNewTemplate().items)
                }
                changes.templatesToUpdate.forEach { (id, t) ->
                    templateQueries.update(t.name, t.updatedAtMillis, id)
                    templateQueries.deleteItems(id)
                    templateQueries.writeItems(id, t.toNewTemplate().items)
                }
            }
            changes.weightGoal?.let { weightQueries.upsertGoal(it.targetKg, it.updatedAtMillis) }
            changes.mealsPerDay?.let { settingsQueries.upsertMealsPerDay(it.toLong()) }
            changes.reminders?.let { settingsQueries.upsertReminders(it.toJson()) }
            if (waterQueries != null) {
                changes.water.forEach { waterQueries.upsert(it.date, it.ml.toLong(), it.updatedAtMillis) }
                changes.waterGoal?.let { waterQueries.upsertGoal(it.goalMl.toLong(), it.updatedAtMillis) }
            }
            if (exerciseQueries != null) {
                changes.exercisesToAdd.forEach {
                    exerciseQueries.insert(it.date, it.name, it.kcal.toLong(), it.minutes?.toLong(), it.createdAtMillis)
                }
                changes.exercisePercent?.let { exerciseQueries.upsertPercent(it.percent.toLong(), it.updatedAtMillis) }
            }
            if (changes.favoritesToAdd.isNotEmpty()) {
                // Después de los alimentos propios, para enlazar también los que acaban de entrar.
                val customIds = customFoodQueries.selectAll().executeAsList()
                    .associate { it.name.lowercase() to (it.brand?.lowercase().orEmpty()) to it.id }
                changes.favoritesToAdd.forEach { f ->
                    val key = f.barcode ?: customIds[f.customName!!.lowercase() to f.customBrand?.lowercase().orEmpty()]
                        ?.let(FavoriteKeys::custom)
                    if (key != null) favoriteQueries.insert(key, f.createdAtMillis)
                }
            }
            changes.productCorrections.forEach { c ->
                productQueries.upsertCorrection(
                    barcode = c.barcode, kcal = c.nutrients.kcal, protein_g = c.nutrients.proteinG,
                    carbs_g = c.nutrients.carbsG, sugars_g = c.nutrients.sugarsG, fat_g = c.nutrients.fatG,
                    saturated_fat_g = c.nutrients.saturatedFatG, fiber_g = c.nutrients.fiberG,
                    salt_g = c.nutrients.saltG, updated_at = c.updatedAtMillis,
                )
            }
            changes.diaryEntriesToAdd.forEach { e ->
                diaryQueries.insert(
                    date = e.date, meal = e.meal.name, barcode = e.barcode, name = e.name, quantity = e.quantity,
                    unit = e.unit.name, serving_size_g = e.servingSizeG, kcal_100g = e.nutrients.kcal,
                    protein_100g = e.nutrients.proteinG, carbs_100g = e.nutrients.carbsG,
                    fat_100g = e.nutrients.fatG, created_at = e.createdAtMillis, quick = if (e.quick) 1L else 0L,
                    saturated_fat_100g = e.nutrients.saturatedFatG, sugars_100g = e.nutrients.sugarsG,
                    fiber_100g = e.nutrients.fiberG, salt_100g = e.nutrients.saltG,
                )
            }
        }
    }
}
