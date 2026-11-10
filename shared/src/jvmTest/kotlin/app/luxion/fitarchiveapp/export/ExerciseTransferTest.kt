package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightExerciseStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.model.NewExercise
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ejercicio y su porcentaje en exportar e importar (docs/design.md §2.14). */
class ExerciseTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val exercise = SqlDelightExerciseStore(db.exerciseQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, waterQueries = db.waterQueries,
            exerciseQueries = db.exerciseQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private fun json(exercises: List<ExportedExercise> = emptyList(), percent: ExportedExercisePercent? = null) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, exercises = exercises, exercisePercent = percent))

    @Test
    fun theFormatVersionDoesNotChange() {
        assertEquals(1, ExportCodec.CURRENT_VERSION)
    }

    @Test
    fun exportCarriesActivitiesAndPercent() = runTest {
        val src = Device()
        src.exercise.add(NewExercise("2026-11-10", "Correr", 400, 40), 10L)
        src.exercise.add(NewExercise("2026-11-10", "Pesas", 150), 11L)
        src.exercise.setPercent(75, 20L)
        val file = ExportCodec.decode(src.exporter.exportToJson())
        assertEquals(
            listOf(
                ExportedExercise("2026-11-10", "Correr", 400, 40, 10L),
                ExportedExercise("2026-11-10", "Pesas", 150, null, 11L),
            ),
            file.exercises,
        )
        assertEquals(ExportedExercisePercent(75, 20L), file.exercisePercent)
        src.driver.close()
    }

    @Test
    fun exportWithoutExerciseIsEmpty() = runTest {
        val src = Device()
        val file = ExportCodec.decode(src.exporter.exportToJson())
        assertEquals(emptyList(), file.exercises)
        assertNull(file.exercisePercent)
        src.driver.close()
    }

    @Test
    fun oldFilesWithoutExerciseStillImportAndKeepLocalExercise() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        val file = ExportCodec.decode(old)
        assertEquals(emptyList(), file.exercises)
        assertNull(file.exercisePercent)
        val dst = Device()
        dst.exercise.add(NewExercise("2026-11-10", "Correr", 400), 1L)
        dst.exercise.setPercent(25, 2L)
        dst.importer.import(old)
        assertEquals(1, dst.exercise.entriesForDate("2026-11-10").size)
        assertEquals(25, dst.exercise.percent())
        dst.driver.close()
    }

    @Test
    fun roundTripBetweenDevices() = runTest {
        val src = Device()
        src.exercise.add(NewExercise("2026-11-09", "Bici", 300, 60), 10L)
        src.exercise.add(NewExercise("2026-11-10", "Correr", 400, 40), 11L)
        src.exercise.setPercent(100, 12L)
        val dst = Device()
        val report = dst.importer.import(src.exporter.exportToJson())
        assertEquals(listOf("Bici"), dst.exercise.entriesForDate("2026-11-09").map { it.name })
        assertEquals(40, dst.exercise.entriesForDate("2026-11-10").single().minutes)
        assertEquals(100, dst.exercise.percent())
        assertEquals(3, report.exercise.added) // 2 actividades + el porcentaje
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun importingTwiceDoesNotDuplicateActivities() = runTest {
        val src = Device()
        src.exercise.add(NewExercise("2026-11-10", "Correr", 400, 40), 10L)
        src.exercise.add(NewExercise("2026-11-10", "Correr", 400, 40), 11L) // dos sesiones iguales el mismo día
        src.exercise.setPercent(75, 12L)
        val text = src.exporter.exportToJson()
        val dst = Device()
        dst.importer.import(text)
        val second = dst.importer.import(text)
        assertEquals(2, dst.exercise.entriesForDate("2026-11-10").size)
        assertEquals(3, second.exercise.unchanged)
        assertEquals(0, second.exercise.added)
        dst.driver.close(); src.driver.close()
    }

    @Test
    fun anActivityAlreadyPresentLocallyIsNotDuplicatedAndLetterCaseIsIgnored() = runTest {
        val dst = Device()
        dst.exercise.add(NewExercise("2026-11-10", "Correr", 400, 40), 1L)
        val report = dst.importer.import(
            json(
                listOf(
                    ExportedExercise("2026-11-10", "correr", 400, 40),
                    ExportedExercise("2026-11-10", "Correr", 400, 45), // otra duración: es otra actividad
                ),
            ),
        )
        assertEquals(2, dst.exercise.entriesForDate("2026-11-10").size)
        assertEquals(1, report.exercise.unchanged)
        assertEquals(1, report.exercise.added)
        dst.driver.close()
    }

    @Test
    fun percentConflictTakesTheMostRecentAndTiesKeepLocal() = runTest {
        val dst = Device()
        dst.exercise.setPercent(25, 100L)
        dst.importer.import(json(percent = ExportedExercisePercent(75, 50L)))
        assertEquals(25, dst.exercise.percent()) // el local es más reciente
        dst.importer.import(json(percent = ExportedExercisePercent(75, 100L)))
        assertEquals(25, dst.exercise.percent()) // empate: gana el local
        dst.importer.import(json(percent = ExportedExercisePercent(0, 150L)))
        assertEquals(0, dst.exercise.percent())
        dst.driver.close()
    }

    @Test
    fun invalidItemsAreReportedAndSkipped() = runTest {
        val dst = Device()
        val report = dst.importer.import(
            json(
                exercises = listOf(
                    ExportedExercise("2026-11-10", "Correr", 0), ExportedExercise("2026-11-10", "Correr", 10_001),
                    ExportedExercise("10/11/2026", "Correr", 100), ExportedExercise("2026-11-10", " ", 100),
                    ExportedExercise("2026-11-10", "Correr", 100, 2000), ExportedExercise("2026-11-10", "Nadar", 250, 30),
                ),
                percent = ExportedExercisePercent(150),
            ),
        )
        assertEquals(listOf("Nadar"), dst.exercise.entriesForDate("2026-11-10").map { it.name })
        assertEquals(ExerciseRules.DEFAULT_PERCENT, dst.exercise.percent())
        assertEquals(6, report.exercise.invalid)
        assertTrue(report.invalidItems.any { it.contains("Porcentaje de ejercicio") })
        dst.driver.close()
    }

    @Test
    fun previewDoesNotWriteAndAFileWithOnlyExerciseIsNotEmpty() = runTest {
        val dst = Device()
        val plan = dst.importer.preview(json(listOf(ExportedExercise("2026-11-10", "Correr", 400)), ExportedExercisePercent(0)))
        assertEquals(2, plan.report.exercise.added)
        assertTrue(!plan.changes.isEmpty)
        assertEquals(emptyList(), dst.exercise.entriesForDate("2026-11-10"))
        assertEquals(ExerciseRules.DEFAULT_PERCENT, dst.exercise.percent())
        dst.driver.close()
    }
}
