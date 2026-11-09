package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportCodec
import app.luxion.fitarchiveapp.export.ExportFile
import app.luxion.fitarchiveapp.export.ExportedDiaryEntry
import app.luxion.fitarchiveapp.export.ExportedProduct
import app.luxion.fitarchiveapp.export.ImportChanges
import app.luxion.fitarchiveapp.export.ImportCounts
import app.luxion.fitarchiveapp.export.ImportTarget
import app.luxion.fitarchiveapp.export.qr.QrTransfer
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.transfer.LanTransferError
import app.luxion.fitarchiveapp.transfer.LanTransferException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTarget(
    var products: List<ExportedProduct> = emptyList(),
) : ImportTarget {
    var applied: ImportChanges? = null
    override suspend fun products() = products
    override suspend fun diaryEntries(): List<DiaryEntry> = emptyList()
    override suspend fun apply(changes: ImportChanges) {
        applied = changes
        products = products + changes.products
    }
}

class DataStateTest {
    private val product = ExportedProduct(barcode = "8480000000001", name = "Leche")
    private val diary = ExportedDiaryEntry(
        id = 1, date = "2026-11-01", meal = MealType.BREAKFAST, name = "Café", quantity = 100.0,
        unit = QuantityUnit.GRAMS, createdAtMillis = 5,
    )

    private fun fileJson(vararg products: ExportedProduct, entries: List<ExportedDiaryEntry> = emptyList()) =
        ExportCodec.encode(ExportFile(1, 0, products.toList(), entries))

    private fun presenter(target: ImportTarget) =
        DataPresenter(DataExporter(target) { 42 }, DataImporter(target))

    @Test
    fun fileNameUsesDate() = assertEquals("easyfood-2026-11-04.json", exportFileName("2026-11-04"))

    @Test
    fun describesCountsOmittingZeros() {
        assertEquals("Sin cambios", ImportCounts().describe())
        assertEquals("1 nuevo", ImportCounts(added = 1).describe())
        assertEquals(
            "3 nuevos · 1 actualizado · 2 duplicados · 1 conflicto (se conserva el local) · 2 inválidos",
            ImportCounts(added = 3, updated = 1, unchanged = 2, keptLocal = 1, invalid = 2).describe(),
        )
    }

    @Test
    fun previewShowsOnlyKindsWithData() = runTest {
        val p = presenter(FakeTarget())
        p.stage(fileJson(product))
        val stage = assertIs<ImportStage.Preview>(p.stage)
        assertTrue(stage.preview.canConfirm)
        assertEquals(listOf("Productos"), stage.preview.rows.map { it.label })
        assertEquals("1 nuevo", stage.preview.rows.single().summary)
    }

    @Test
    fun previewOfAlreadyKnownDataCannotBeConfirmed() = runTest {
        val target = FakeTarget(products = listOf(product))
        val p = presenter(target)
        p.stage(fileJson(product))
        val stage = assertIs<ImportStage.Preview>(p.stage)
        assertFalse(stage.preview.canConfirm)
        assertEquals(1, stage.preview.duplicates)
        p.confirm()
        assertIs<ImportStage.Preview>(p.stage) // no cambia de fase
        assertNull(target.applied)
    }

    @Test
    fun previewDoesNotWriteAndConfirmApplies() = runTest {
        val target = FakeTarget()
        val p = presenter(target)
        p.stage(fileJson(product, entries = listOf(diary)))
        assertNull(target.applied)
        p.confirm()
        val done = assertIs<ImportStage.Done>(p.stage)
        assertNotNull(target.applied)
        assertEquals(1, done.report.products.added)
        assertEquals("Importados 2 elementos.", done.report.summary())
        p.dismiss()
        assertIs<ImportStage.Idle>(p.stage)
    }

    @Test
    fun invalidJsonBecomesReadableError() = runTest {
        val p = presenter(FakeTarget())
        p.stage("esto no es json")
        val failed = assertIs<ImportStage.Failed>(p.stage)
        assertTrue(failed.message.endsWith("."))
        assertFalse(failed.message.endsWith(".."))
    }

    @Test
    fun futureVersionIsRejectedWithMessage() = runTest {
        val p = presenter(FakeTarget())
        p.stage("""{"schemaVersion":99,"exportedAtMillis":0}""")
        assertTrue(assertIs<ImportStage.Failed>(p.stage).message.contains("99"))
    }

    @Test
    fun fetchFailureMapsLanErrors() = runTest {
        val p = presenter(FakeTarget())
        p.stageFrom { throw LanTransferException(LanTransferError.Denied, "x") }
        assertEquals(lanErrorMessage(LanTransferError.Denied), assertIs<ImportStage.Failed>(p.stage).message)
    }

    @Test
    fun fetchedJsonGoesThroughPreview() = runTest {
        val p = presenter(FakeTarget())
        p.stageFrom { fileJson(product) }
        assertIs<ImportStage.Preview>(p.stage)
    }

    @Test
    fun exportProducesDecodableJson() = runTest {
        val target = FakeTarget(products = listOf(product))
        val json = presenter(target).export().getOrThrow()
        val file = ExportCodec.decode(json)
        assertEquals(42, file.exportedAtMillis)
        assertEquals("Leche", file.products.single().name)
    }

    @Test
    fun everyLanErrorHasAMessage() {
        LanTransferError.entries.forEach { assertTrue(lanErrorMessage(it).isNotBlank()) }
    }

    @Test
    fun qrFramesCycle() {
        val f = QrFrames(listOf("a", "b", "c"))
        assertEquals(1, f.next(0))
        assertEquals(0, f.next(2))
        assertEquals(2, f.previous(0))
        assertEquals("Código 2 de 3", f.label(1))
        assertFalse(f.isLarge)
        assertTrue(QrFrames(List(QrFrames.LARGE_THRESHOLD + 1) { "x" }).isLarge)
    }

    @Test
    fun qrReceiveTracksProgressAndAssembles() {
        val json = fileJson(product, entries = listOf(diary))
        val frames = QrTransfer.split(json, chunkSize = 120)
        assertTrue(frames.size > 2)
        val model = QrReceiveModel()
        assertEquals("Apunta al primer código", model.ui.label)
        assertNull(model.offer(frames[1]))
        assertEquals("Recibidos 1 de ${frames.size}", model.ui.label)
        assertNull(model.offer(frames[1])) // repetido
        assertEquals(1, model.ui.received)
        assertNull(model.offer("https://ejemplo.com")) // QR ajeno: avisa sin perder progreso
        assertNotNull(model.ui.message)
        assertEquals(1, model.ui.received)
        var result: String? = null
        frames.forEach { result = model.offer(it) ?: result }
        assertTrue(model.ui.complete)
        assertEquals(1f, model.ui.fraction)
        assertEquals(1, ExportCodec.decode(assertNotNull(result)).products.size)
    }

    @Test
    fun qrResetClearsProgress() {
        val model = QrReceiveModel()
        model.offer(QrTransfer.split(fileJson(product), 50).first())
        model.reset()
        assertEquals(0, model.ui.received)
        assertEquals(0f, model.ui.fraction)
    }

    @Test
    fun lanFormAcceptsHostPortAndCode() {
        val v = LanForm(" 192.168.1.20:5050 ", " 123456 ").validate()
        assertEquals(LanRequest("192.168.1.20", 5050, "123456"), v.request)
    }

    @Test
    fun lanFormReportsEachFieldError() {
        assertNotNull(LanForm("", "").validate().addressError)
        assertNotNull(LanForm("", "").validate().codeError)
        assertNotNull(LanForm("192.168.1.20", "123456").validate().addressError) // sin puerto
        assertNotNull(LanForm("192.168.1.20:0", "123456").validate().addressError)
        assertNotNull(LanForm("192.168.1.20:70000", "123456").validate().addressError)
        assertNotNull(LanForm(":5050", "123456").validate().addressError)
        assertNotNull(LanForm("192.168.1.20:5050", "12345").validate().codeError)
        assertNotNull(LanForm("192.168.1.20:5050", "12a456").validate().codeError)
        assertNull(LanForm("192.168.1.20:5050", "123456").validate().let { it.addressError ?: it.codeError })
    }

    @Test
    fun lanAddressLabelAndCountdown() {
        assertEquals("10.0.0.5:4000", lanAddressLabel(listOf("10.0.0.5", "192.168.0.2"), 4000))
        assertNull(lanAddressLabel(emptyList(), 4000))
        assertEquals(120, secondsLeft(120_000, 0))
        assertEquals(1, secondsLeft(1_000, 1))
        assertEquals(0, secondsLeft(1_000, 5_000))
    }
}
