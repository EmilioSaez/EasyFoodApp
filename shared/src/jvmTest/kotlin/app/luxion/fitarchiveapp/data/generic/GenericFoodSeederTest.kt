package app.luxion.fitarchiveapp.data.generic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenericFoodSeederTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var importer: GenericFoodImporter
    private var reads = 0

    private val sample = buildString {
        appendLine(GenericFoodFormat.MAGIC)
        appendLine(GenericFoodFormat.COLUMNS.joinToString("\t"))
        for (i in 1..600) appendLine("$i\tFood number $i\t100\t1\t2\t3\t\t\t\t")
    }

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        importer = GenericFoodImporter(db.productQueries)
        reads = 0
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun seeder(read: suspend () -> String = { reads++; sample }) =
        GenericFoodSeeder(importer, nowMillis = { 5 }, readResource = read)

    @Test
    fun seedsOnceAndIsIdempotentAcrossInstances() = runTest {
        val first = seeder()
        first.ensureSeeded()
        assertEquals(GenericSeedState.Done, first.state.value)
        assertEquals(600, importer.importedCount())

        // Segundo arranque (otra instancia): ve la base cargada y ni siquiera lee el recurso.
        val second = seeder()
        second.ensureSeeded()
        assertEquals(GenericSeedState.Done, second.state.value)
        assertEquals(1, reads)
        assertEquals(600, importer.importedCount())
    }

    @Test
    fun concurrentCallsImportOnlyOnce() = runTest {
        val s = seeder()
        List(5) { async { s.ensureSeeded() } }.awaitAll()
        assertEquals(1, reads)
        assertEquals(600, importer.importedCount())
    }

    @Test
    fun reportsProgressUpToTotal() = runTest {
        val steps = mutableListOf<Pair<Int, Int>>()
        importer.import(sample, 0) { done, total -> steps += done to total }
        assertEquals(0 to 600, steps.first())
        assertEquals(600 to 600, steps.last())
        assertTrue(steps.map { it.first }.zipWithNext().all { (a, b) -> a < b }, "progreso monótono: $steps")
        assertTrue(steps.size > 3)
    }

    @Test
    fun failureIsReportedAndCanBeRetried() = runTest {
        var attempt = 0
        val s = seeder { if (attempt++ == 0) error("sin recurso") else sample }
        s.ensureSeeded()
        assertEquals("sin recurso", assertIs<GenericSeedState.Failed>(s.state.value).message)
        assertEquals(0, importer.importedCount())
        s.ensureSeeded()
        assertEquals(GenericSeedState.Done, s.state.value)
        assertEquals(600, importer.importedCount())
    }

    @Test
    fun invalidFormatLeavesDatabaseUntouched() = runTest {
        val s = seeder { "esto no es un TSV" }
        s.ensureSeeded()
        assertIs<GenericSeedState.Failed>(s.state.value)
        assertEquals(0, importer.importedCount())
    }

    @Test
    fun reimportsWhenStoredDataVersionIsOlder() = runTest {
        // Instalación anterior: alimentos GENERIC cargados en inglés y sin versión de datos.
        importer.import(sample, 0)
        db.productQueries.upsertGenericDataVersion(1)
        val translations = (listOf(GenericFoodFormat.TRANSLATION_MAGIC, GenericFoodFormat.TRANSLATION_COLUMNS.joinToString("\t")) +
            "1\tAlimento número 1").joinToString("\n")
        val s = GenericFoodSeeder(importer, nowMillis = { 5 }, readResource = { reads++; sample }, readTranslations = { translations })
        s.ensureSeeded()
        assertEquals(GenericSeedState.Done, s.state.value)
        assertEquals(1, reads)
        assertEquals("Alimento número 1", db.productQueries.selectByBarcode("generic:1").executeAsOne().name)
        assertEquals(GenericFoodFormat.DATA_VERSION, importer.importedDataVersion())
        assertEquals(600, importer.importedCount())

        // Ya está al día: otro arranque no vuelve a leer el recurso.
        GenericFoodSeeder(importer, nowMillis = { 5 }, readResource = { reads++; sample }, readTranslations = { translations })
            .ensureSeeded()
        assertEquals(1, reads)
    }
}
