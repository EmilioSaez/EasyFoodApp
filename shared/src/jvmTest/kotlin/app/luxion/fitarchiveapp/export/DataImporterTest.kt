package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightRecipeStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightGoal
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataImporterTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var products: SqlDelightProductStore
    private lateinit var diary: SqlDelightDiaryStore
    private lateinit var foods: SqlDelightCustomFoodStore
    private lateinit var exporter: DataExporter
    private lateinit var importer: DataImporter

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        products = SqlDelightProductStore(db.productQueries)
        diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        foods = SqlDelightCustomFoodStore(db.customFoodQueries)
        exporter = DataExporter(SqlDelightExportSource(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries)) { 5_000L }
        importer = DataImporter(SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries))
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val nutella = Product(
        barcode = "3017620422003", name = "Nutella", brand = "Ferrero", imageUrl = "https://x/y.jpg",
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9, 10.6, 56.3, null, 0.107),
        servingSizeG = 15.0, servingLabel = "15 g",
    )
    private val entry = NewDiaryEntry(
        date = "2026-11-04", meal = MealType.BREAKFAST, barcode = nutella.barcode, name = "Nutella",
        quantity = Quantity(2.0, QuantityUnit.SERVINGS), servingSizeG = 15.0,
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9),
    )
    private val pan = NewCustomFood("Pan casero", "Horno", Nutrients(250.0, 8.0, 50.0, 2.0, fiberG = 3.0), 40.0, "1 rebanada")

    private fun file(
        products: List<ExportedProduct> = emptyList(),
        diary: List<ExportedDiaryEntry> = emptyList(),
        foods: List<ExportedCustomFood> = emptyList(),
    ) = ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, products, diary, foods))

    private fun exportedNutella(updated: Long = 100L, name: String = "Nutella") = ExportedProduct(
        barcode = nutella.barcode, name = name, nutrients = ExportedNutrients(kcal = 539.0), updatedAtMillis = updated,
    )

    private fun exportedPan(id: Long = 1, created: Long = 10, updated: Long = 10, kcal: Double = 250.0, name: String = "Pan casero") =
        ExportedCustomFood(id, name, "Horno", ExportedNutrients(kcal, 8.0, 50.0, 2.0, fiberG = 3.0), 40.0, "1 rebanada", created, updated)

    private fun exportedEntry(id: Long = 1, created: Long = 100, meal: MealType = MealType.LUNCH, quantity: Double = 100.0) =
        ExportedDiaryEntry(id, "2026-11-04", meal, null, "Arroz", quantity, QuantityUnit.GRAMS, null,
            ExportedNutrients(130.0, 2.7, 28.0, 0.3), created)

    @Test
    fun roundTripIntoEmptyDatabaseRestoresEverything() = runTest {
        products.save(nutella, 1_234L)
        diary.add(entry, 100L)
        foods.create(pan, 50L)
        val json = exporter.exportToJson()

        val other = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
        val report = DataImporter(SqlDelightImportTarget(other.productQueries, other.diaryEntryQueries, other.customFoodQueries, other.weightEntryQueries, other.recipeQueries, other.settingsQueries, other.favoriteFoodQueries))
            .import(json)

        assertEquals(ImportCounts(added = 1), report.products)
        assertEquals(ImportCounts(added = 1), report.customFoods)
        assertEquals(ImportCounts(added = 1), report.diaryEntries)
        val again = DataExporter(SqlDelightExportSource(other.productQueries, other.diaryEntryQueries, other.customFoodQueries, other.weightEntryQueries, other.recipeQueries, other.settingsQueries, other.favoriteFoodQueries)) { 5_000L }
        assertEquals(ExportCodec.decode(json), ExportCodec.decode(again.exportToJson()))
    }

    @Test
    fun importingTheSameFileTwiceIsIdempotent() = runTest {
        products.save(nutella, 1_234L)
        diary.add(entry, 100L)
        diary.add(entry, 100L) // dos entradas realmente idénticas siguen siendo dos
        foods.create(pan, 50L)
        val json = exporter.exportToJson()

        val report = importer.import(json)

        assertEquals(ImportCounts(unchanged = 1), report.products)
        assertEquals(ImportCounts(unchanged = 1), report.customFoods)
        assertEquals(ImportCounts(unchanged = 2), report.diaryEntries)
        assertEquals(json, exporter.exportToJson())
    }

    @Test
    fun productConflictNewerImportWinsOlderKeepsLocal() = runTest {
        products.save(nutella, 500L)

        val older = importer.import(file(products = listOf(exportedNutella(updated = 400L, name = "Viejo"))))
        assertEquals(ImportCounts(keptLocal = 1), older.products)
        assertEquals("Nutella", products.find(nutella.barcode)!!.product.name)

        val newer = importer.import(file(products = listOf(exportedNutella(updated = 600L, name = "Nuevo"))))
        assertEquals(ImportCounts(updated = 1), newer.products)
        assertEquals("Nuevo", products.find(nutella.barcode)!!.product.name)
        assertEquals(600L, products.find(nutella.barcode)!!.fetchedAtMillis)

        val tie = importer.import(file(products = listOf(exportedNutella(updated = 600L, name = "Empate"))))
        assertEquals(ImportCounts(keptLocal = 1), tie.products)
    }

    @Test
    fun productsKeepTheirSource() = runTest {
        importer.import(file(products = listOf(exportedNutella().copy(source = "CUSTOM"))))
        assertEquals("CUSTOM", exporter.buildFile().products.single().source)
    }

    @Test
    fun genericFoodsAreNeitherExportedNorImported() = runTest {
        // La base genérica viaja con la app: ni se exporta ni cuenta como duplicado/nuevo al importar.
        val generic = exportedNutella().copy(barcode = "generic:1", source = "GENERIC")
        val plan = importer.preview(file(products = listOf(generic, exportedNutella().copy(barcode = "8412345678905"))))
        assertEquals(ImportCounts(added = 1), plan.report.products)
        assertEquals(listOf("8412345678905"), plan.changes.products.map { it.barcode })

        db.productQueries.upsert(
            "generic:2", "Manzana", null, null, 52.0, 0.3, 14.0, null, 0.2, null, null, null, null, null, "GENERIC", 1L,
        )
        assertEquals(emptyList(), exporter.buildFile().products)
    }

    @Test
    fun customFoodMatchedByContentIgnoringIdAndCase() = runTest {
        foods.create(pan, 50L) // id local 1
        // Otro dispositivo: mismo alimento con id 77, nombre en otro caso y espacios.
        val sameFood = exportedPan(id = 77, created = 999, updated = 999, name = "  PAN CASERO ")

        val report = importer.import(file(foods = listOf(sameFood)))

        assertEquals(ImportCounts(unchanged = 1), report.customFoods)
        assertEquals(1, foods.all().size)
    }

    @Test
    fun customFoodSameIdDifferentContentIsADifferentFood() = runTest {
        foods.create(pan, 50L) // id local 1, createdAt 50
        val other = exportedPan(id = 1, created = 7, updated = 7, name = "Queso") // id coincide por casualidad

        val report = importer.import(file(foods = listOf(other)))

        assertEquals(ImportCounts(added = 1), report.customFoods)
        assertEquals(listOf("Pan casero", "Queso"), foods.all().map { it.name })
    }

    @Test
    fun customFoodRenamedLocallyIsRecognisedByIdAndCreationDate() = runTest {
        val id = foods.create(pan, 50L)
        foods.update(id, pan.copy(name = "Pan de centeno"), 80L)
        // Export antiguo del mismo alimento (mismo id y createdAt 50, aún con el nombre viejo).
        val old = exportedPan(id = id, created = 50, updated = 50)

        val report = importer.import(file(foods = listOf(old)))

        assertEquals(ImportCounts(keptLocal = 1), report.customFoods)
        assertEquals(listOf("Pan de centeno"), foods.all().map { it.name })
    }

    @Test
    fun customFoodConflictNewestWins() = runTest {
        val id = foods.create(pan, 50L)

        val newer = importer.import(file(foods = listOf(exportedPan(id = 9, created = 20, updated = 90, kcal = 300.0, name = "pan casero"))))

        assertEquals(ImportCounts(updated = 1), newer.customFoods)
        val updated = foods.find(id)!!
        assertEquals(300.0, updated.nutrients.kcal)
        assertEquals(50L, updated.createdAtMillis)
        assertEquals(90L, updated.updatedAtMillis)
        assertEquals(1, foods.all().size)

        val older = importer.import(file(foods = listOf(exportedPan(id = 9, created = 20, updated = 60, kcal = 100.0, name = "Pan casero"))))
        assertEquals(ImportCounts(keptLocal = 1), older.customFoods)
        assertEquals(300.0, foods.find(id)!!.nutrients.kcal)
    }

    @Test
    fun diaryDuplicateIgnoresIdAndEditedLocalEntryWins() = runTest {
        val id = diary.add(entry.copy(name = "Arroz", barcode = null, meal = MealType.LUNCH,
            quantity = Quantity(100.0, QuantityUnit.GRAMS), servingSizeG = null,
            nutrients = Nutrients(130.0, 2.7, 28.0, 0.3)), 100L)

        val sameWithOtherId = importer.import(file(diary = listOf(exportedEntry(id = 555))))
        assertEquals(ImportCounts(unchanged = 1), sameWithOtherId.diaryEntries)

        diary.update(id, MealType.DINNER, Quantity(250.0, QuantityUnit.GRAMS)) // el usuario la edita
        val conflict = importer.import(file(diary = listOf(exportedEntry())))
        assertEquals(ImportCounts(keptLocal = 1), conflict.diaryEntries)
        val local = diary.find(id)!!
        assertEquals(MealType.DINNER, local.meal)
        assertEquals(250.0, local.quantity.amount)
        assertEquals(1, diary.entriesForDate("2026-11-04").size)
    }

    @Test
    fun diaryEntryWithOtherCreationTimeIsAnotherEntry() = runTest {
        diary.add(entry, 100L)
        val report = importer.import(file(diary = listOf(entry.toExportedWithId(1, 101L))))
        assertEquals(ImportCounts(added = 1), report.diaryEntries)
        assertEquals(2, diary.entriesForDate("2026-11-04").size)
    }

    @Test
    fun invalidItemsAreSkippedAndReportedRestIsImported() = runTest {
        val json = """{"schemaVersion":1,"exportedAtMillis":1,
          "products":[{"barcode":"","name":"x"},{"barcode":"111","name":"Bueno"}],
          "diaryEntries":[
            {"id":1,"date":"2026-11-04","meal":"LUNCH","name":"neg","quantity":-3.0,"unit":"GRAMS","createdAtMillis":1},
            {"id":2,"date":"ayer","meal":"LUNCH","name":"fecha","quantity":3.0,"unit":"GRAMS","createdAtMillis":2},
            {"id":3,"date":"2026-11-04","meal":"LUNCH","name":"ok","quantity":3.0,"unit":"GRAMS","createdAtMillis":3}],
          "customFoods":[{"id":1,"name":"Sin macros"},{"id":2,"name":"Neg","nutrients":{"kcal":-1,"proteinG":1,"carbsG":1,"fatG":1}}]}"""

        val report = importer.import(json)

        assertEquals(ImportCounts(added = 1, invalid = 1), report.products)
        assertEquals(ImportCounts(added = 1, invalid = 2), report.diaryEntries)
        assertEquals(ImportCounts(invalid = 2), report.customFoods)
        assertEquals(5, report.invalidItems.size)
        assertEquals(listOf("ok"), diary.entriesForDate("2026-11-04").map { it.name })
        assertEquals(emptyList(), foods.all())
    }

    @Test
    fun legacyVersion1FileWithoutCustomFoodsImports() = runTest {
        val report = importer.import("""{"schemaVersion":1,"exportedAtMillis":1,"products":[{"barcode":"111","name":"Viejo"}]}""")
        assertEquals(ImportCounts(added = 1), report.products)
        assertEquals(ImportCounts(), report.customFoods)
    }

    @Test
    fun invalidFileFailsWithoutTouchingData() = runTest {
        foods.create(pan, 50L)
        assertFailsWith<ExportFormatException> { importer.import("""{"schemaVersion":99,"exportedAtMillis":1}""") }
        assertFailsWith<ExportFormatException> { importer.import("basura") }
        assertEquals(1, foods.all().size)
    }

    @Test
    fun previewDoesNotWrite() = runTest {
        val plan = importer.preview(file(foods = listOf(exportedPan()), diary = listOf(exportedEntry())))
        assertEquals(ImportCounts(added = 1), plan.report.customFoods)
        assertTrue(!plan.changes.isEmpty)
        assertEquals(emptyList(), foods.all())
        assertEquals(emptyList(), diary.entriesForDate("2026-11-04"))
    }

    @Test
    fun duplicatesInsideTheSameFileAreNotInsertedTwice() = runTest {
        val report = importer.import(file(
            products = listOf(exportedNutella(), exportedNutella()),
            foods = listOf(exportedPan(), exportedPan(id = 2)),
        ))
        assertEquals(ImportCounts(added = 1, unchanged = 1), report.products)
        assertEquals(ImportCounts(added = 1, unchanged = 1), report.customFoods)
    }

    // --- Pesos ---

    private fun weightFile(vararg w: ExportedWeight) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, weights = w.toList()))

    @Test
    fun weightsRoundTripIntoEmptyDatabase() = runTest {
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.save(NewWeightEntry("2026-11-01", 80.5), 10L)
        weights.save(NewWeightEntry("2026-11-03", 79.5), 30L)
        val json = exporter.exportToJson()

        val other = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
        val report = DataImporter(
            SqlDelightImportTarget(other.productQueries, other.diaryEntryQueries, other.customFoodQueries, other.weightEntryQueries, other.recipeQueries, other.settingsQueries, other.favoriteFoodQueries),
        ).import(json)

        assertEquals(ImportCounts(added = 2), report.weights)
        assertEquals(weights.all(), SqlDelightWeightStore(other.weightEntryQueries).all())
    }

    @Test
    fun importingWeightsTwiceDoesNotDuplicate() = runTest {
        val json = weightFile(ExportedWeight("2026-11-01", 80.0, 5, 5), ExportedWeight("2026-11-02", 79.0, 6, 6))
        importer.import(json)
        val report = importer.import(json)
        assertEquals(ImportCounts(unchanged = 2), report.weights)
        assertEquals(2, SqlDelightWeightStore(db.weightEntryQueries).all().size)
    }

    @Test
    fun weightConflictNewerWinsTieKeepsLocal() = runTest {
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.save(NewWeightEntry("2026-11-01", 80.0), 100L)
        weights.save(NewWeightEntry("2026-11-02", 79.0), 100L)
        weights.save(NewWeightEntry("2026-11-03", 78.0), 100L)

        val report = importer.import(weightFile(
            ExportedWeight("2026-11-01", 81.0, 1, 200), // más reciente: gana el importado
            ExportedWeight("2026-11-02", 70.0, 1, 100), // empate: gana el local
            ExportedWeight("2026-11-03", 60.0, 1, 50),  // más antiguo: gana el local
        ))

        assertEquals(ImportCounts(updated = 1, keptLocal = 2), report.weights)
        assertEquals(listOf(81.0, 79.0, 78.0), weights.all().map { it.weightKg })
        assertEquals(100L, weights.all().first().createdAtMillis) // conserva la creación local
    }

    @Test
    fun duplicateDateInsideTheFileKeepsTheNewest() = runTest {
        val report = importer.import(weightFile(
            ExportedWeight("2026-11-01", 80.0, 1, 10),
            ExportedWeight("2026-11-01", 79.0, 1, 20),
            ExportedWeight("2026-11-01", 79.0, 1, 20),
        ))
        assertEquals(ImportCounts(added = 1, updated = 1, unchanged = 1), report.weights)
        assertEquals(listOf(79.0), SqlDelightWeightStore(db.weightEntryQueries).all().map { it.weightKg })
    }

    @Test
    fun invalidWeightsAreSkippedAndReported() = runTest {
        val report = importer.import(weightFile(
            ExportedWeight("2026-11-01", 80.0),
            ExportedWeight("2026-02-30", 80.0),
            ExportedWeight("2026-11-02", 5.0),
        ))
        assertEquals(ImportCounts(added = 1, invalid = 2), report.weights)
        assertEquals(2, report.invalidItems.size)
        assertEquals(listOf("2026-11-01"), SqlDelightWeightStore(db.weightEntryQueries).all().map { it.date })
    }

    @Test
    fun oldFilesWithoutWeightsStillImport() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[],"customFoods":[]}"""
        val report = importer.import(old)
        assertEquals(ImportCounts(), report.weights)
        val older = """{"schemaVersion":1,"exportedAtMillis":1}"""
        assertEquals(ImportCounts(), importer.import(older).weights)
        assertEquals(emptyList(), SqlDelightWeightStore(db.weightEntryQueries).all())
    }

    @Test
    fun weightsOnlyChangeIsNotEmptyAndPreviewDoesNotWrite() = runTest {
        val plan = importer.preview(weightFile(ExportedWeight("2026-11-01", 80.0, 1, 1)))
        assertTrue(!plan.changes.isEmpty)
        assertEquals(emptyList(), SqlDelightWeightStore(db.weightEntryQueries).all())
    }

    // --- Recetas ---

    private fun ing(name: String, grams: Double, barcode: String? = null) =
        ExportedRecipeIngredient(barcode, name, grams, ExportedNutrients(100.0, 5.0, 10.0, 2.0), 30.0)

    private fun recipe(id: Long, name: String, created: Long, updated: Long, servings: Int? = 2, vararg i: ExportedRecipeIngredient) =
        ExportedRecipe(id, name, servings, i.toList().ifEmpty { listOf(ing("Arroz", 100.0, "generic:1")) }, created, updated)

    private fun recipeFile(vararg r: ExportedRecipe) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, recipes = r.toList()))

    private fun localRecipes() = SqlDelightRecipeStore(db.recipeQueries)

    @Test
    fun recipesRoundTripIntoEmptyDatabase() = runTest {
        localRecipes().create(
            NewRecipe("Paella", 4, listOf(RecipeIngredient("generic:1", "Arroz", 200.0, Nutrients(350.0, 7.0, 78.0, 1.0), 50.0), RecipeIngredient(null, "Aceite", 10.0, Nutrients(900.0, 0.0, 0.0, 100.0)))),
            10L,
        )
        val json = exporter.exportToJson()

        val other = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
        val report = DataImporter(
            SqlDelightImportTarget(other.productQueries, other.diaryEntryQueries, other.customFoodQueries, other.weightEntryQueries, other.recipeQueries, other.settingsQueries, other.favoriteFoodQueries),
        ).import(json)

        assertEquals(ImportCounts(added = 1), report.recipes)
        assertEquals(
            localRecipes().all().map { it.copy(id = 0) },
            SqlDelightRecipeStore(other.recipeQueries).all().map { it.copy(id = 0) },
        )
    }

    @Test
    fun importingRecipesTwiceDoesNotDuplicate() = runTest {
        val json = recipeFile(recipe(1, "Paella", 5, 5), recipe(2, "Tortilla", 6, 6))
        importer.import(json)
        val report = importer.import(json)
        assertEquals(ImportCounts(unchanged = 2), report.recipes)
        assertEquals(2, localRecipes().all().size)
    }

    @Test
    fun recipeIsRecognisedByNameAfterBeingRenumbered() = runTest {
        localRecipes().create(NewRecipe("Paella", 2, listOf(RecipeIngredient("generic:1", "Arroz", 100.0, Nutrients(100.0, 5.0, 10.0, 2.0), 30.0))), 1L)
        val report = importer.import(recipeFile(recipe(77, " PAELLA ", 99, 99)))
        assertEquals(ImportCounts(unchanged = 1), report.recipes)
        assertEquals(1, localRecipes().all().size)
    }

    @Test
    fun recipeConflictNewerWinsAndReplacesIngredientsTieKeepsLocal() = runTest {
        val store = localRecipes()
        val a = store.create(NewRecipe("A", 2, listOf(RecipeIngredient(null, "Viejo", 10.0, Nutrients(1.0, 1.0, 1.0, 1.0)))), 100L)
        store.create(NewRecipe("B", 2, listOf(RecipeIngredient(null, "Local", 10.0, Nutrients(1.0, 1.0, 1.0, 1.0)))), 100L)
        val local = store.all()

        val report = importer.import(recipeFile(
            recipe(local[0].id, "A", 100, 200, 3, ing("Nuevo", 50.0)), // más reciente: gana el importado
            recipe(local[1].id, "B", 100, 100, 3, ing("Otro", 50.0)),  // empate: gana el local
        ))

        assertEquals(ImportCounts(updated = 1, keptLocal = 1), report.recipes)
        val after = store.all()
        assertEquals(listOf("Nuevo"), after[0].ingredients.map { it.name })
        assertEquals(3, after[0].servings)
        assertEquals(100L, store.find(a)!!.createdAtMillis)
        assertEquals(listOf("Local"), after[1].ingredients.map { it.name })
    }

    @Test
    fun invalidRecipesAreSkippedAndReported() = runTest {
        val report = importer.import(recipeFile(
            recipe(1, "Buena", 1, 1),
            ExportedRecipe(2, "Sin ingredientes", 2, emptyList(), 1, 1),
            recipe(3, "  ", 1, 1),
            recipe(4, "Raciones locas", 1, 1, servings = 0),
            recipe(5, "Gramos cero", 1, 1, 2, ing("X", 0.0)),
        ))
        assertEquals(ImportCounts(added = 1, invalid = 4), report.recipes)
        assertEquals(4, report.invalidItems.size)
        assertEquals(listOf("Buena"), localRecipes().all().map { it.name })
    }

    @Test
    fun oldFilesWithoutRecipesStillImport() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[],"customFoods":[],"weights":[]}"""
        assertEquals(ImportCounts(), importer.import(old).recipes)
        assertEquals(emptyList(), localRecipes().all())
    }

    @Test
    fun recipesOnlyChangeIsNotEmptyAndPreviewDoesNotWrite() = runTest {
        val plan = importer.preview(recipeFile(recipe(1, "Paella", 1, 1)))
        assertTrue(!plan.changes.isEmpty)
        assertEquals(emptyList(), localRecipes().all())
    }

    private fun NewDiaryEntry.toExportedWithId(id: Long, created: Long) = ExportedDiaryEntry(
        id, date, meal, barcode, name, quantity.amount, quantity.unit, servingSizeG, nutrients.toExported(), created,
    )

    // --- Peso objetivo ---

    private fun goalFile(goal: ExportedWeightGoal?) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, weightGoal = goal))

    @Test
    fun weightGoalRoundTripsIntoEmptyDatabase() = runTest {
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.setGoal(72.5, 40L)
        val json = exporter.exportToJson()

        val other = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
        val report = DataImporter(
            SqlDelightImportTarget(other.productQueries, other.diaryEntryQueries, other.customFoodQueries, other.weightEntryQueries, other.recipeQueries, other.settingsQueries, other.favoriteFoodQueries),
        ).import(json)

        assertEquals(ImportCounts(added = 1), report.weights)
        assertEquals(WeightGoal(72.5, 40L), SqlDelightWeightStore(other.weightEntryQueries).goal())
    }

    @Test
    fun importingTheSameGoalTwiceIsUnchanged() = runTest {
        val json = goalFile(ExportedWeightGoal(70.0, 5L))
        importer.import(json)
        assertEquals(ImportCounts(unchanged = 1), importer.import(json).weights)
    }

    @Test
    fun weightGoalConflictNewerWinsTieKeepsLocal() = runTest {
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.setGoal(75.0, 100L)

        assertEquals(ImportCounts(keptLocal = 1), importer.import(goalFile(ExportedWeightGoal(60.0, 100L))).weights)
        assertEquals(ImportCounts(keptLocal = 1), importer.import(goalFile(ExportedWeightGoal(60.0, 50L))).weights)
        assertEquals(75.0, weights.goal()?.targetKg)

        assertEquals(ImportCounts(updated = 1), importer.import(goalFile(ExportedWeightGoal(65.0, 200L))).weights)
        assertEquals(WeightGoal(65.0, 200L), weights.goal())
    }

    @Test
    fun invalidWeightGoalIsReportedAndSkipped() = runTest {
        val report = importer.import(goalFile(ExportedWeightGoal(5.0, 1L)))
        assertEquals(ImportCounts(invalid = 1), report.weights)
        assertEquals(1, report.invalidItems.size)
        assertNull(SqlDelightWeightStore(db.weightEntryQueries).goal())
    }

    @Test
    fun filesWithoutGoalLeaveTheLocalGoalUntouched() = runTest {
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.setGoal(75.0, 100L)
        // Archivo anterior a esta versión: sin el campo `weightGoal`.
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"weights":[{"date":"2026-11-01","weightKg":80.0}]}"""
        val report = importer.import(old)
        assertEquals(ImportCounts(added = 1), report.weights)
        assertEquals(75.0, weights.goal()?.targetKg)
        assertNull(ExportCodec.decode(old).weightGoal)
    }
}
