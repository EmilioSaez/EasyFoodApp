package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Agua y su objetivo en exportar e importar (docs/design.md §2.13). */
class WaterTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val water = SqlDelightWaterStore(db.waterQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, waterQueries = db.waterQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private fun json(water: List<ExportedWater> = emptyList(), goal: ExportedWaterGoal? = null) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, water = water, waterGoal = goal))

    @Test
    fun exportCarriesTotalsAndGoal() = runTest {
        val src = Device()
        src.water.add("2026-11-04", 750, 10L)
        src.water.setGoalMl(2500, 20L)
        val file = ExportCodec.decode(src.exporter.exportToJson())
        assertEquals(listOf(ExportedWater("2026-11-04", 750, 10L)), file.water)
        assertEquals(ExportedWaterGoal(2500, 20L), file.waterGoal)
        src.driver.close()
    }

    @Test
    fun exportWithoutWaterIsEmpty() = runTest {
        val src = Device()
        val file = ExportCodec.decode(src.exporter.exportToJson())
        assertEquals(emptyList(), file.water)
        assertNull(file.waterGoal)
        src.driver.close()
    }

    @Test
    fun oldFilesWithoutWaterStillImportAndKeepLocalWater() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        val file = ExportCodec.decode(old)
        assertEquals(emptyList(), file.water)
        assertNull(file.waterGoal)
        val dst = Device()
        dst.water.add("2026-11-04", 250, 1L)
        dst.importer.import(old)
        assertEquals(250, dst.water.total("2026-11-04"))
        dst.driver.close()
    }

    @Test
    fun roundTripBetweenDevices() = runTest {
        val src = Device()
        src.water.add("2026-11-03", 1500, 10L)
        src.water.add("2026-11-04", 500, 11L)
        src.water.setGoalMl(3000, 12L)
        val dst = Device()
        val report = dst.importer.import(src.exporter.exportToJson())
        assertEquals(1500, dst.water.total("2026-11-03"))
        assertEquals(500, dst.water.total("2026-11-04"))
        assertEquals(3000, dst.water.goalMl())
        assertEquals(3, report.water.added) // 2 días + el objetivo
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun importingTwiceDoesNotDuplicateOrDoubleTheTotals() = runTest {
        val src = Device()
        src.water.add("2026-11-04", 500, 10L)
        src.water.setGoalMl(3000, 12L)
        val text = src.exporter.exportToJson()
        val dst = Device()
        dst.importer.import(text)
        val second = dst.importer.import(text)
        assertEquals(500, dst.water.total("2026-11-04"))
        assertEquals(2, second.water.unchanged)
        assertEquals(0, second.water.added + second.water.updated)
        assertEquals(1, ExportCodec.decode(dst.exporter.exportToJson()).water.size)
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun conflictingDayTakesTheMostRecentAndTiesKeepLocal() = runTest {
        val dst = Device()
        dst.water.add("2026-11-04", 500, 100L)
        dst.water.add("2026-11-05", 500, 100L)
        dst.water.add("2026-11-06", 500, 100L)
        val report = dst.importer.import(
            json(
                listOf(
                    ExportedWater("2026-11-04", 1000, 200L), // más reciente: gana el archivo
                    ExportedWater("2026-11-05", 1000, 50L), // más antiguo: gana el local
                    ExportedWater("2026-11-06", 1000, 100L), // empate: gana el local
                ),
            ),
        )
        assertEquals(1000, dst.water.total("2026-11-04"))
        assertEquals(500, dst.water.total("2026-11-05"))
        assertEquals(500, dst.water.total("2026-11-06"))
        assertEquals(1, report.water.updated)
        assertEquals(2, report.water.keptLocal)
        dst.driver.close()
    }

    @Test
    fun duplicateDateInsideTheFileKeepsTheMostRecent() = runTest {
        val dst = Device()
        dst.importer.import(
            json(listOf(ExportedWater("2026-11-04", 300, 10L), ExportedWater("2026-11-04", 900, 20L))),
        )
        assertEquals(900, dst.water.total("2026-11-04"))
        dst.driver.close()
    }

    @Test
    fun goalConflictFollowsTheSameRule() = runTest {
        val dst = Device()
        dst.water.setGoalMl(2500, 100L)
        dst.importer.import(json(goal = ExportedWaterGoal(3000, 50L)))
        assertEquals(2500, dst.water.goalMl()) // el local es más reciente
        dst.importer.import(json(goal = ExportedWaterGoal(3000, 150L)))
        assertEquals(3000, dst.water.goalMl())
        dst.driver.close()
    }

    @Test
    fun invalidItemsAreReportedAndSkipped() = runTest {
        val dst = Device()
        val report = dst.importer.import(
            json(
                water = listOf(
                    ExportedWater("2026-11-04", 0), ExportedWater("2026-11-05", 99_999),
                    ExportedWater("04/11/2026", 500), ExportedWater("2026-11-06", 400),
                ),
                goal = ExportedWaterGoal(50),
            ),
        )
        assertEquals(400, dst.water.total("2026-11-06"))
        assertEquals(0, dst.water.total("2026-11-04"))
        assertEquals(2000, dst.water.goalMl())
        assertEquals(4, report.water.invalid)
        assertEquals(4, report.invalidItems.size)
        assertTrue(report.invalidItems.any { it.contains("Objetivo de agua") })
        dst.driver.close()
    }

    @Test
    fun previewDoesNotWrite() = runTest {
        val dst = Device()
        val plan = dst.importer.preview(json(listOf(ExportedWater("2026-11-04", 500)), ExportedWaterGoal(3000)))
        assertEquals(2, plan.report.water.added)
        assertEquals(0, dst.water.total("2026-11-04"))
        assertEquals(2000, dst.water.goalMl())
        dst.driver.close()
    }

    @Test
    fun aFileWithOnlyWaterIsNotAnEmptyImport() = runTest {
        val dst = Device()
        val plan = dst.importer.preview(json(listOf(ExportedWater("2026-11-04", 500))))
        assertTrue(!plan.changes.isEmpty)
        dst.driver.close()
    }
}
