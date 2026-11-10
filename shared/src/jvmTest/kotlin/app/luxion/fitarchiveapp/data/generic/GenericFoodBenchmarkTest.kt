package app.luxion.fitarchiveapp.data.generic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Benchmark simple de la importación de la base genérica y de la búsqueda FTS con la carga real
 * (~7 800 alimentos). Imprime los tiempos y falla si se pasan de un techo muy holgado
 * (regresión grosera, no una medida fina).
 */
class GenericFoodBenchmarkTest {
    @Test
    fun importAndSearchTimings() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        val importer = GenericFoodImporter(db.productQueries)
        val search = SqlDelightProductSearchStore(db.productSearchQueries)
        val text = readEmbeddedGenericFoods()

        var result = GenericFoodImportResult(0, 0)
        val firstImportMs = measureTimeMillis { result = importer.import(text, 0) }
        val reimportMs = measureTimeMillis { importer.import(text, 1) }
        assertEquals(result.imported.toLong(), importer.importedCount())

        val queries = listOf("apple", "chicken breast", "pan", "whole milk", "ch", "a")
        search.search("apple") // calentar
        val searchMs = measureTimeMillis { repeat(20) { queries.forEach { search.search(it) } } }
        val perSearchMs = searchMs.toDouble() / (20 * queries.size)

        // Las variantes nuevas: erratas (vocabulario), supermercado, solo mis productos y uso frecuente (diario).
        repeat(300) {
            driver.execute(null, "INSERT INTO DiaryEntryEntity(date, meal, barcode, name, quantity, unit, created_at) " +
                "VALUES ('2026-01-01', 'LUNCH', 'generic:${it % 40}', 'x', 100, 'GRAMS', $it)", 0)
        }
        val coldMs = measureTimeMillis { SqlDelightProductSearchStore(db.productSearchQueries).search("aple") }
        val extra = listOf("aple", "chiken brest", "mercadona leche", "apples", "ch")
        extra.forEach { search.search(it) }
        val extraMs = measureTimeMillis { repeat(20) { extra.forEach { q -> search.search(q) } } }
        val onlyMineMs = measureTimeMillis { repeat(20) { search.search("ch", onlyMine = true) } }
        println("BENCH2 primeraBusquedaConVocabulario=${coldMs}ms variantes=${"%.2f".format(extraMs.toDouble() / (20 * extra.size))}ms/consulta soloMios=${"%.2f".format(onlyMineMs / 20.0)}ms/consulta")
        assertTrue(extraMs.toDouble() / (20 * extra.size) < 100, "búsqueda tolerante demasiado lenta")
        println("BENCH imported=${result.imported} firstImport=${firstImportMs}ms reimport=${reimportMs}ms search=${"%.2f".format(perSearchMs)}ms/consulta")
        assertTrue(firstImportMs < 30_000, "importación demasiado lenta: ${firstImportMs}ms")
        assertTrue(perSearchMs < 100, "búsqueda demasiado lenta: ${perSearchMs}ms")
        driver.close()
    }
}
