package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightFavoriteStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Favoritos en exportar e importar (docs/design.md §2.2): portables, sin duplicados y sin romper archivos antiguos. */
class FavoritesTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val favorites = SqlDelightFavoriteStore(db.favoriteFoodQueries)
        val foods = SqlDelightCustomFoodStore(db.customFoodQueries)
        val products = SqlDelightProductStore(db.productQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private val n = Nutrients(100.0, 1.0, 1.0, 1.0)
    private fun food(name: String, brand: String? = null) = NewCustomFood(name, brand, n)

    private fun json(vararg favorites: ExportedFavorite, customFoods: List<ExportedCustomFood> = emptyList()) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, customFoods = customFoods, favorites = favorites.toList()))

    @Test
    fun formatVersionIsNotBumpedByTheNewCollection() {
        assertEquals(1, ExportCodec.CURRENT_VERSION)
    }

    @Test
    fun oldFilesWithoutTheFieldReadAsEmpty() {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        assertEquals(emptyList(), ExportCodec.decode(old).favorites)
    }

    @Test
    fun exportCarriesProductsByCodeAndCustomFoodsByName() = runTest {
        val src = Device()
        val id = src.foods.create(food("Pan casero", "Horno"), 1L)
        src.favorites.setFavorite("3017620422003", true, 10L)
        src.favorites.setFavorite("custom:$id", true, 20L)
        src.favorites.setFavorite("custom:999", true, 30L) // alimento propio borrado: no se exporta
        val exported = ExportCodec.decode(src.exporter.exportToJson()).favorites
        assertEquals(
            setOf(
                ExportedFavorite(barcode = "3017620422003", createdAtMillis = 10L),
                ExportedFavorite(customName = "Pan casero", customBrand = "Horno", createdAtMillis = 20L),
            ),
            exported.toSet(),
        )
        src.driver.close()
    }

    @Test
    fun customFavoriteTravelsToADeviceWithDifferentIds() = runTest {
        val src = Device()
        val srcId = src.foods.create(food("Pan casero"), 1L)
        src.favorites.setFavorite("custom:$srcId", true, 20L)
        val dst = Device()
        dst.foods.create(food("Otro"), 2L) // desplaza los ids
        dst.foods.create(food("Algo más"), 3L)

        val report = dst.importer.import(src.exporter.exportToJson())

        assertEquals(1, report.favorites.added)
        val dstId = dst.foods.all().first { it.name == "Pan casero" }.id
        assertTrue(dstId != srcId)
        assertEquals(listOf("custom:$dstId"), dst.favorites.keys())
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun customFavoriteLinksToTheLocalFoodRenamedSinceTheExport() = runTest {
        val src = Device()
        val srcId = src.foods.create(food("Pan casero"), 1L)
        src.favorites.setFavorite("custom:$srcId", true, 20L)
        val dst = Device()
        val dstId = dst.foods.create(food("Pan casero"), 1L) // mismo id y fecha de creación: es el mismo alimento
        dst.foods.update(dstId, food("Pan de horno"), 1L)

        val report = dst.importer.import(src.exporter.exportToJson())

        assertEquals(1, report.favorites.added)
        assertEquals(listOf("custom:$dstId"), dst.favorites.keys())
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun importingTwiceDoesNotDuplicate() = runTest {
        val src = Device()
        val id = src.foods.create(food("Pan casero"), 1L)
        src.favorites.setFavorite("custom:$id", true, 20L)
        src.favorites.setFavorite("123", true, 21L)
        val text = src.exporter.exportToJson()
        val dst = Device()

        val first = dst.importer.import(text)
        val second = dst.importer.import(text)

        assertEquals(2, first.favorites.added)
        assertEquals(0, second.favorites.added)
        assertEquals(2, second.favorites.unchanged)
        assertEquals(2, dst.favorites.keys().size)
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun mergesWithLocalFavoritesAndNeverUnmarksAnything() = runTest {
        val dst = Device()
        dst.favorites.setFavorite("111", true, 1L)
        val id = dst.foods.create(food("Pan casero"), 1L)
        dst.favorites.setFavorite("custom:$id", true, 2L)

        val report = dst.importer.import(
            json(
                ExportedFavorite(barcode = "111", createdAtMillis = 50L),
                ExportedFavorite(customName = "  pan CASERO ", createdAtMillis = 51L), // misma identidad sin importar mayúsculas
                ExportedFavorite(barcode = "222", createdAtMillis = 52L),
            ),
        )

        assertEquals(1, report.favorites.added)
        assertEquals(2, report.favorites.unchanged)
        assertEquals(setOf("111", "222", "custom:$id"), dst.favorites.keys().toSet())
        // El favorito local conserva su fecha.
        assertEquals(1L, dst.db.favoriteFoodQueries.selectAll().executeAsList().first { it.key == "111" }.created_at)
        dst.driver.close()
    }

    @Test
    fun customFavoriteLinksToAFoodThatArrivesInTheSameFile() = runTest {
        val dst = Device()
        val file = ExportedCustomFood(id = 1, name = "Pan casero", nutrients = ExportedNutrients(100.0, 1.0, 1.0, 1.0), createdAtMillis = 3L)
        val report = dst.importer.import(json(ExportedFavorite(customName = "Pan casero"), customFoods = listOf(file)))
        assertEquals(1, report.favorites.added)
        assertEquals(listOf("custom:${dst.foods.all().single().id}"), dst.favorites.keys())
        dst.driver.close()
    }

    @Test
    fun invalidFavoritesAreSkippedAndReported() = runTest {
        val dst = Device()
        val report = dst.importer.import(
            json(
                ExportedFavorite(), // ni producto ni propio
                ExportedFavorite(barcode = "1", customName = "A"), // los dos a la vez
                ExportedFavorite(customName = "No existe"), // alimento propio inexistente
                ExportedFavorite(barcode = "333"),
            ),
        )
        assertEquals(3, report.favorites.invalid)
        assertEquals(3, report.invalidItems.size)
        assertEquals(listOf("333"), dst.favorites.keys())
        dst.driver.close()
    }

    @Test
    fun aFileOnlyWithDuplicatesIsNotConfirmable() = runTest {
        val dst = Device()
        dst.favorites.setFavorite("111", true, 1L)
        val plan = dst.importer.preview(json(ExportedFavorite(barcode = "111")))
        assertTrue(plan.changes.isEmpty)
        dst.driver.close()
    }

    @Test
    fun genericProductFavoritesRoundTrip() = runTest {
        val src = Device()
        src.products.save(Product("generic:5", "Manzana", null, null, n), 1L)
        src.favorites.setFavorite("generic:5", true, 9L)
        val dst = Device()
        dst.importer.import(src.exporter.exportToJson())
        assertEquals(listOf("generic:5"), dst.favorites.keys())
        src.driver.close(); dst.driver.close()
    }
}
