package app.luxion.fitarchiveapp.export

import app.luxion.fitarchiveapp.data.CachedProduct
import app.luxion.fitarchiveapp.data.FavoriteKeys
import app.luxion.fitarchiveapp.data.readAll
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.data.toCached
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.toDomain
import app.luxion.fitarchiveapp.db.CustomFoodQueries
import app.luxion.fitarchiveapp.db.DiaryEntryQueries
import app.luxion.fitarchiveapp.db.ExerciseQueries
import app.luxion.fitarchiveapp.db.FavoriteFoodQueries
import app.luxion.fitarchiveapp.db.ProductQueries
import app.luxion.fitarchiveapp.db.SettingsQueries
import app.luxion.fitarchiveapp.db.MealTemplateQueries
import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.db.RecipeQueries
import app.luxion.fitarchiveapp.db.WaterQueries
import app.luxion.fitarchiveapp.db.WeightEntryQueries
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Recipe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Datos locales que se pueden exportar. Cada almacén nuevo (alimentos propios, objetivos) añadirá un método. */
interface ExportSource {
    suspend fun products(): List<ExportedProduct>
    suspend fun diaryEntries(): List<DiaryEntry>
    suspend fun customFoods(): List<ExportedCustomFood> = emptyList()
    suspend fun weights(): List<ExportedWeight> = emptyList()
    suspend fun recipes(): List<ExportedRecipe> = emptyList()
    suspend fun weightGoal(): ExportedWeightGoal? = null
    suspend fun mealsPerDay(): Int? = null
    suspend fun water(): List<ExportedWater> = emptyList()
    suspend fun waterGoal(): ExportedWaterGoal? = null
    suspend fun favorites(): List<ExportedFavorite> = emptyList()
    suspend fun mealTemplates(): List<ExportedMealTemplate> = emptyList()
    suspend fun exercises(): List<ExportedExercise> = emptyList()
    suspend fun exercisePercent(): ExportedExercisePercent? = null
    suspend fun productCorrections(): List<ExportedProductCorrection> = emptyList()
    suspend fun reminders(): ReminderSettings? = null
}

/** Genera el JSON de exportación a partir de [source]. */
class DataExporter(
    private val source: ExportSource,
    private val nowMillis: () -> Long,
) {
    suspend fun buildFile(): ExportFile = ExportFile(
        schemaVersion = ExportCodec.CURRENT_VERSION,
        exportedAtMillis = nowMillis(),
        // La base genérica viaja con la app (se siembra en cada dispositivo): exportarla multiplicaría el tamaño
        // por ~100 (miles de códigos QR) sin aportar nada. Las entradas del diario la referencian por clave.
        products = source.products().filter { it.source != GenericFoodImporter.SOURCE },
        diaryEntries = source.diaryEntries().map { it.toExported() },
        customFoods = source.customFoods(),
        weights = source.weights(),
        recipes = source.recipes(),
        weightGoal = source.weightGoal(),
        mealsPerDay = source.mealsPerDay(),
        water = source.water(),
        waterGoal = source.waterGoal(),
        favorites = source.favorites(),
        mealTemplates = source.mealTemplates(),
        exercises = source.exercises(),
        exercisePercent = source.exercisePercent(),
        productCorrections = source.productCorrections(),
        reminders = source.reminders(),
    )

    suspend fun exportToJson(): String = ExportCodec.encode(buildFile())
}

fun DiaryEntry.toExported() = ExportedDiaryEntry(
    id = id, date = date, meal = meal, barcode = barcode, name = name,
    quantity = quantity.amount, unit = quantity.unit, servingSizeG = servingSizeG,
    nutrients = nutrients.toExported(), createdAtMillis = createdAtMillis, quick = quick,
)

fun CustomFood.toExported() = ExportedCustomFood(
    id = id, name = name, brand = brand, nutrients = nutrients.toExported(), servingSizeG = servingSizeG,
    servingLabel = servingLabel, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun Recipe.toExported() = ExportedRecipe(
    id = id, name = name, servings = servings,
    ingredients = ingredients.map {
        ExportedRecipeIngredient(it.barcode, it.name, it.grams, it.nutrients.toExported(), it.servingSizeG)
    },
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun MealTemplate.toExported() = ExportedMealTemplate(
    id = id, name = name,
    items = items.map {
        ExportedTemplateItem(it.barcode, it.name, it.quantity.amount, it.quantity.unit, it.servingSizeG, it.nutrients.toExported())
    },
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun ExportedDiaryEntry.toDomain() = DiaryEntry(
    id = id, date = date, meal = meal, barcode = barcode, name = name,
    quantity = quantityOf(quantity, unit), servingSizeG = servingSizeG,
    nutrients = nutrients.toDomain(), createdAtMillis = createdAtMillis, quick = quick,
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
    private val weightQueries: WeightEntryQueries? = null,
    private val recipeQueries: RecipeQueries? = null,
    private val settingsQueries: SettingsQueries? = null,
    private val favoriteQueries: FavoriteFoodQueries? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val waterQueries: WaterQueries? = null,
    private val templateQueries: MealTemplateQueries? = null,
    private val exerciseQueries: ExerciseQueries? = null,
) : ExportSource {
    override suspend fun mealTemplates(): List<ExportedMealTemplate> = withContext(dispatcher) {
        templateQueries?.transactionWithResult { templateQueries.readAll() }.orEmpty().map { it.toExported() }
    }

    override suspend fun exercises(): List<ExportedExercise> = withContext(dispatcher) {
        exerciseQueries?.selectAll()?.executeAsList().orEmpty().map {
            ExportedExercise(it.date, it.name, it.kcal.toInt(), it.minutes?.toInt(), it.created_at)
        }
    }

    override suspend fun exercisePercent(): ExportedExercisePercent? = withContext(dispatcher) {
        exerciseQueries?.selectPercent()?.executeAsOneOrNull()?.let { ExportedExercisePercent(it.percent.toInt(), it.updated_at) }
    }

    override suspend fun water(): List<ExportedWater> = withContext(dispatcher) {
        waterQueries?.selectAll()?.executeAsList().orEmpty().map { ExportedWater(it.date, it.ml.toInt(), it.updated_at) }
    }

    override suspend fun waterGoal(): ExportedWaterGoal? = withContext(dispatcher) {
        waterQueries?.selectGoal()?.executeAsOneOrNull()?.let { ExportedWaterGoal(it.goal_ml.toInt(), it.updated_at) }
    }

    override suspend fun favorites(): List<ExportedFavorite> = withContext(dispatcher) {
        val favorites = favoriteQueries?.selectAll()?.executeAsList().orEmpty()
        if (favorites.isEmpty()) return@withContext emptyList()
        val customById = customFoodQueries?.selectAll()?.executeAsList().orEmpty().associateBy { it.id }
        favorites.mapNotNull { f ->
            val customId = FavoriteKeys.customIdOf(f.key)
            when {
                customId == null -> ExportedFavorite(barcode = f.key, createdAtMillis = f.created_at)
                // Un alimento propio borrado deja un favorito huérfano: no se exporta.
                else -> customById[customId]?.let {
                    ExportedFavorite(customName = it.name, customBrand = it.brand, createdAtMillis = f.created_at)
                }
            }
        }
    }

    override suspend fun productCorrections(): List<ExportedProductCorrection> = withContext(dispatcher) {
        productQueries.selectAllCorrections().executeAsList().map {
            val c = it.toDomain()
            ExportedProductCorrection(c.barcode, c.nutrients.toExported(), c.updatedAtMillis)
        }
    }

    override suspend fun mealsPerDay(): Int? = withContext(dispatcher) {
        settingsQueries?.selectMealsPerDay()?.executeAsOneOrNull()?.meals_per_day?.toInt()
    }

    override suspend fun reminders(): ReminderSettings? = withContext(dispatcher) {
        ReminderSettings.fromJson(settingsQueries?.selectReminders()?.executeAsOneOrNull()?.reminders)
    }

    override suspend fun recipes(): List<ExportedRecipe> = withContext(dispatcher) {
        recipeQueries?.transactionWithResult { recipeQueries.readAll() }.orEmpty().map { it.toExported() }
    }

    override suspend fun weights(): List<ExportedWeight> = withContext(dispatcher) {
        weightQueries?.selectAll()?.executeAsList().orEmpty().map {
            ExportedWeight(it.date, it.weight_kg, it.created_at, it.updated_at)
        }
    }

    override suspend fun weightGoal(): ExportedWeightGoal? = withContext(dispatcher) {
        weightQueries?.selectGoal()?.executeAsOneOrNull()?.let { ExportedWeightGoal(it.target_kg, it.updated_at) }
    }

    override suspend fun customFoods(): List<ExportedCustomFood> = withContext(dispatcher) {
        customFoodQueries?.selectAll()?.executeAsList().orEmpty().map { it.toDomain().toExported() }
    }

    override suspend fun products(): List<ExportedProduct> = withContext(dispatcher) {
        // La base genérica (miles de filas) viaja con la app y se vuelve a cargar sola: no se exporta.
        productQueries.selectAll().executeAsList().filter { it.source != GenericFoodImporter.SOURCE }.map {
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
