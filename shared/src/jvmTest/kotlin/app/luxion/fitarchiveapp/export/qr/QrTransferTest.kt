package app.luxion.fitarchiveapp.export.qr

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportCodec
import app.luxion.fitarchiveapp.export.ExportFormatException
import app.luxion.fitarchiveapp.export.ImportCounts
import app.luxion.fitarchiveapp.export.SqlDelightExportSource
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QrTransferTest {
    private val json = """{"schemaVersion":1,"note":"ñandú 🍎 café","items":[${(1..200).joinToString(",") { "\"valor $it\"" }}]}"""

    @Test
    fun crc32MatchesKnownVector() {
        assertEquals("cbf43926", Crc32.hex("123456789"))
    }

    @Test
    fun splitsIntoNumberedChunksAndReassemblesInOrder() {
        val fragments = QrTransfer.split(json, chunkSize = 100)
        assertTrue(fragments.size > 3)
        val rx = QrTransferReceiver()
        fragments.forEach { assertIs<QrScanResult.Accepted>(rx.accept(it)) }
        assertTrue(rx.isComplete)
        assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(rx.assemble()))
    }

    @Test
    fun toleratesShuffledOrderAndRepeats() {
        val fragments = QrTransfer.split(json, chunkSize = 50)
        val feed = (fragments + fragments.take(5) + fragments).shuffled(Random(7))
        val rx = QrTransferReceiver()
        var dups = 0
        feed.forEach { if (rx.accept(it) is QrScanResult.Duplicate) dups++ }
        assertTrue(dups > 0)
        assertEquals(fragments.size, rx.receivedCount)
        assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(rx.assemble()))
    }

    @Test
    fun neverSplitsSurrogatePairs() {
        val emoji = """{"schemaVersion":1,"x":"${"🍎".repeat(50)}"}"""
        for (size in 2..9) {
            val rx = QrTransferReceiver()
            QrTransfer.split(emoji, size).forEach { assertIs<QrScanResult.Accepted>(rx.accept(it)) }
            assertEquals(Json.parseToJsonElement(emoji), Json.parseToJsonElement(rx.assemble()))
        }
    }

    @Test
    fun reportsMissingFragments() {
        val fragments = QrTransfer.split(json, chunkSize = 100)
        val rx = QrTransferReceiver()
        rx.accept(fragments.first())
        rx.accept(fragments.last())
        assertEquals((2 until fragments.size).toList(), rx.missing)
        assertFailsWith<ExportFormatException> { rx.assemble() }
    }

    @Test
    fun rejectsCorruptForeignAndGarbageReads() {
        val a = QrTransfer.split(json, 100)
        val b = QrTransfer.split(json.replace("valor", "otro"), 100)
        val rx = QrTransferReceiver()
        assertIs<QrScanResult.Rejected>(rx.accept("https://example.com"))
        assertIs<QrScanResult.Rejected>(rx.accept(a[0].dropLast(1) + "#")) // datos corruptos
        assertEquals(0, rx.receivedCount)
        assertIs<QrScanResult.Accepted>(rx.accept(a[0]))
        assertIs<QrScanResult.Rejected>(rx.accept(b[1])) // otra transferencia
        assertEquals(1, rx.receivedCount)
        rx.reset()
        assertIs<QrScanResult.Accepted>(rx.accept(b[1]))
    }

    @Test
    fun invalidJsonCannotBeSplit() {
        assertFailsWith<ExportFormatException> { QrTransfer.split("no es json") }
    }

    @Test
    fun endToEndExportToQrToImport() = runTest {
        val src = newDb()
        SqlDelightProductStore(src.productQueries).save(
            Product("3017620422003", "Nutella", "Ferrero", null, Nutrients(539.0, 6.3, 57.5, 30.9), 15.0, "15 g"), 1_234L,
        )
        SqlDelightDiaryStore(src.diaryEntryQueries).add(
            NewDiaryEntry("2026-11-04", MealType.BREAKFAST, "3017620422003", "Nutella",
                Quantity(2.0, QuantityUnit.SERVINGS), 15.0, Nutrients(539.0, 6.3, 57.5, 30.9)), 100L,
        )
        SqlDelightCustomFoodStore(src.customFoodQueries).create(
            NewCustomFood("Pan casero", "Horno", Nutrients(250.0, 8.0, 50.0, 2.0), 40.0, "1 rebanada"), 50L,
        )
        val exported = DataExporter(SqlDelightExportSource(src.productQueries, src.diaryEntryQueries, src.customFoodQueries, src.weightEntryQueries, src.recipeQueries, src.settingsQueries, src.favoriteFoodQueries)) { 5_000L }
            .exportToJson()

        val rx = QrTransferReceiver()
        QrTransfer.split(exported, 120).shuffled(Random(1)).forEach { rx.accept(it) }

        val dst = newDb()
        val importer = DataImporter(SqlDelightImportTarget(dst.productQueries, dst.diaryEntryQueries, dst.customFoodQueries, dst.weightEntryQueries, dst.recipeQueries, dst.settingsQueries, dst.favoriteFoodQueries))
        assertEquals(ImportCounts(added = 1), rx.preview(importer).report.products)
        val report = rx.import(importer)
        assertEquals(ImportCounts(added = 1), report.products)
        assertEquals(ImportCounts(added = 1), report.customFoods)
        assertEquals(ImportCounts(added = 1), report.diaryEntries)
        val back = DataExporter(SqlDelightExportSource(dst.productQueries, dst.diaryEntryQueries, dst.customFoodQueries, dst.weightEntryQueries, dst.recipeQueries, dst.settingsQueries, dst.favoriteFoodQueries)) { 5_000L }
        assertEquals(ExportCodec.decode(exported), ExportCodec.decode(back.exportToJson()))
    }

    private fun newDb() = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
}
