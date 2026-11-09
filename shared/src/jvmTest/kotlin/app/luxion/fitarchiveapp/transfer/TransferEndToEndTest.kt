package app.luxion.fitarchiveapp.transfer

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportCodec
import app.luxion.fitarchiveapp.export.SqlDelightExportSource
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.export.qr.QrCodeEncoder
import app.luxion.fitarchiveapp.export.qr.QrMatrix
import app.luxion.fitarchiveapp.export.qr.QrTransfer
import app.luxion.fitarchiveapp.export.qr.decodeQr
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.screens.DataPresenter
import app.luxion.fitarchiveapp.ui.screens.ImportStage
import app.luxion.fitarchiveapp.ui.screens.QrFrames
import app.luxion.fitarchiveapp.ui.screens.QrReceiveModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verificación de punta a punta de la transferencia entre dos dispositivos (dos bases de datos y sockets
 * reales en loopback, o QR codificados y decodificados con ZXing), pasando por el mismo [DataPresenter]
 * que usa la pantalla «Datos».
 */
class TransferEndToEndTest {
    private val drivers = mutableListOf<JdbcSqliteDriver>()
    private val hosts = mutableListOf<LanTransferHost>()

    @AfterTest
    fun tearDown() {
        hosts.forEach { it.stop() }
        drivers.forEach { it.close() }
    }

    private inner class Device(val diaryEntries: Int = 0) {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            EasyFoodDatabase.Schema.create(it)
            drivers += it
        }
        val db = EasyFoodDatabase(driver)
        val products = SqlDelightProductStore(db.productQueries)
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val foods = SqlDelightCustomFoodStore(db.customFoodQueries)
        private val source = SqlDelightExportSource(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries)
        val exporter = DataExporter(source) { 9_000L }
        val importer = DataImporter(SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries))
        val presenter = DataPresenter(exporter, importer)

        fun snapshot() = ExportCodec.decode(runBlocking { exporter.exportToJson() })

        /** Contenido sin ids (autoincrementales por dispositivo, el importador los ignora) ni orden. */
        fun content() = snapshot().let { f ->
            f.copy(
                exportedAtMillis = 0,
                products = f.products.sortedBy { it.barcode },
                customFoods = f.customFoods.map { it.copy(id = 0) }.sortedBy { it.name },
                diaryEntries = f.diaryEntries.map { it.copy(id = 0) }
                    .sortedWith(compareBy({ it.date }, { it.createdAtMillis }, { it.quantity })),
            )
        }
    }

    private suspend fun Device.seed(entries: Int = 3) {
        products.save(Product("3017620422003", "Nutella", "Ferrero", null, Nutrients(539.0, 6.3, 57.5, 30.9), 15.0, "15 g"), 1_000L)
        products.save(Product("8410000000017", "Galletas ñandú 🍪", "Casa", null, Nutrients(450.0, 6.0, 70.0, 16.0), null, null), 2_000L)
        foods.create(NewCustomFood("Pan casero", "Horno", Nutrients(250.0, 8.0, 50.0, 2.0), 40.0, "1 rebanada"), 50L)
        foods.create(NewCustomFood("Tortilla de patata", null, Nutrients(190.0, 8.0, 14.0, 11.0), 150.0, "1 ración"), 60L)
        repeat(entries) { i ->
            diary.add(
                NewDiaryEntry(
                    "2026-11-%02d".format(1 + i % 28), MealType.BREAKFAST, "3017620422003", "Nutella",
                    Quantity(1.0 + i, QuantityUnit.SERVINGS), 15.0, Nutrients(539.0, 6.3, 57.5, 30.9),
                ),
                100L + i,
            )
        }
    }

    private fun lanHost(device: Device, ttl: Long = 60_000) =
        LanTransferHost(exportJson = device.presenter::exportJson, nowMillis = System::currentTimeMillis, ttlMillis = ttl)
            .also { hosts += it }

    // --- Red local ---

    @Test
    fun `lan send and receive goes through preview and confirm and a second transfer is all duplicates`() = runBlocking<Unit> {
        val a = Device().also { it.seed() }
        val b = Device()
        val offer = lanHost(a).start()

        b.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer.port, offer.code) }
        val preview = assertIs<ImportStage.Preview>(b.presenter.stage)
        assertTrue(preview.preview.canConfirm)
        assertEquals(0, preview.preview.duplicates)
        // Aún no se ha escrito nada.
        assertEquals(0, b.snapshot().products.size)

        b.presenter.confirm()
        val done = assertIs<ImportStage.Done>(b.presenter.stage)
        assertEquals(2, done.report.products.added)
        assertEquals(2, done.report.customFoods.added)
        assertEquals(3, done.report.diaryEntries.added)
        assertEquals(a.content(), b.content())

        // Segunda transferencia con la misma información: todo duplicado, nada que confirmar.
        val offer2 = lanHost(a).start()
        b.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer2.port, offer2.code) }
        val again = assertIs<ImportStage.Preview>(b.presenter.stage)
        assertTrue(!again.preview.canConfirm)
        assertEquals(7, again.preview.duplicates)
        assertEquals(a.content(), b.content())
    }

    @Test
    fun `lan wrong code leaves the receiver in a failed stage and untouched`() = runBlocking<Unit> {
        val a = Device().also { it.seed() }
        val b = Device()
        val offer = lanHost(a).start()
        b.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer.port, "000000") }
        assertIs<ImportStage.Failed>(b.presenter.stage)
        assertEquals(0, b.snapshot().products.size)
        // Con el código bueno todavía funciona.
        b.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer.port, offer.code) }
        assertIs<ImportStage.Preview>(b.presenter.stage)
    }

    @Test
    fun `lan transfers a multi megabyte export intact every time`() = runBlocking<Unit> {
        val a = Device().also { it.seed(entries = 12_000) }
        val json = a.presenter.exportJson()
        assertTrue(json.length > 2_000_000, "el JSON debe ser grande, es ${json.length}")
        // Varias veces: el cierre prematuro del socket del anfitrión es un fallo intermitente.
        repeat(15) { n ->
            val offer = lanHost(a).start()
            assertEquals(json, LanTransferClient.fetch("127.0.0.1", offer.port, offer.code), "intento $n")
        }
        val b = Device()
        assertEquals(12_000, b.importer.import(json).diaryEntries.added)
    }

    @Test
    fun `lan only one of several simultaneous clients gets the data`() = runBlocking<Unit> {
        val a = Device().also { it.seed() }
        val offer = lanHost(a).start()
        val results = (1..5).map {
            async { runCatching { LanTransferClient.fetch("127.0.0.1", offer.port, offer.code) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess }, results.toString())
    }

    @Test
    fun `lan receiving from yourself is all duplicates`() = runBlocking<Unit> {
        val a = Device().also { it.seed() }
        val offer = lanHost(a).start()
        a.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer.port, offer.code) }
        val p = assertIs<ImportStage.Preview>(a.presenter.stage)
        assertTrue(!p.preview.canConfirm)
    }

    // --- Fusión ---

    @Test
    fun `merge keeps local data adds the new and counts duplicates and conflicts`() = runBlocking<Unit> {
        val a = Device().also { it.seed(entries = 2) }
        val b = Device()
        // b ya tiene lo mismo (duplicados) y además datos propios; la Nutella local es más reciente (gana el local).
        b.seed(entries = 2)
        b.products.save(Product("3017620422003", "Nutella", "Ferrero", null, Nutrients(540.0, 6.3, 57.5, 30.9), 15.0, "15 g"), 5_000L)
        b.products.save(Product("1111111111111", "Solo en b", null, null, Nutrients(1.0, 1.0, 1.0, 1.0), null, null), 1L)
        // a tiene una entrada más y un producto nuevo.
        a.products.save(Product("2222222222222", "Solo en a", null, null, Nutrients(2.0, 2.0, 2.0, 2.0), null, null), 1L)
        a.diary.add(
            NewDiaryEntry("2026-12-01", MealType.DINNER, null, "Cena", Quantity(100.0, QuantityUnit.GRAMS), null, Nutrients(100.0, 1.0, 1.0, 1.0)),
            777L,
        )

        val offer = lanHost(a).start()
        b.presenter.stageFrom { LanTransferClient.fetch("127.0.0.1", offer.port, offer.code) }
        val preview = assertIs<ImportStage.Preview>(b.presenter.stage)
        assertEquals(1, preview.preview.conflicts)
        b.presenter.confirm()
        val done = assertIs<ImportStage.Done>(b.presenter.stage)
        assertEquals(1, done.report.products.added)
        assertEquals(1, done.report.products.keptLocal)
        assertEquals(1, done.report.diaryEntries.added)
        assertEquals(2, done.report.diaryEntries.unchanged)
        assertEquals(2, done.report.customFoods.unchanged)

        val nutella = b.snapshot().products.first { it.barcode == "3017620422003" }
        assertEquals(540.0, nutella.nutrients.kcal) // se conserva lo local
        assertTrue(b.snapshot().products.any { it.barcode == "1111111111111" }) // nada local se borra
        assertTrue(b.snapshot().products.any { it.barcode == "2222222222222" })
        assertEquals(3, b.snapshot().diaryEntries.size)
        assertEquals(2, b.snapshot().customFoods.size)
    }

    // --- QR ---

    private fun decode(matrix: QrMatrix) = decodeQr(matrix)

    /** Lo que ve la cámara: cada fragmento dibujado como QR y leído con ZXing. */
    private fun scannedFrames(json: String) =
        QrTransfer.split(json, QrFrames.CHUNK_SIZE).map { decode(QrCodeEncoder.encode(it)) }

    @Test
    fun `qr full sequence is drawn decoded received and imported with the screen's model`() = runBlocking<Unit> {
        val a = Device().also { it.seed(entries = 40) }
        val b = Device()
        val frames = scannedFrames(a.presenter.exportJson())
        assertTrue(frames.size > 3, "debe haber varios códigos, hay ${frames.size}")

        val model = QrReceiveModel()
        // Cámara simulada: orden aleatorio, repeticiones continuas, un QR ajeno y un fragmento dañado.
        val feed = (frames + frames.take(3) + "https://example.com" + frames[0].dropLast(1) + "#").shuffled(Random(3))
        var json: String? = null
        for (read in feed) {
            val out = model.offer(read)
            if (out != null) { json = out; break }
        }
        assertNotNull(json)
        assertTrue(model.ui.complete)

        b.presenter.stage(json)
        assertIs<ImportStage.Preview>(b.presenter.stage)
        b.presenter.confirm()
        val done = assertIs<ImportStage.Done>(b.presenter.stage)
        assertEquals(40, done.report.diaryEntries.added)
        assertEquals(a.content(), b.content())

        // Repetir la transferencia por QR: solo duplicados.
        val model2 = QrReceiveModel()
        val json2 = frames.firstNotNullOf { model2.offer(it) }
        b.presenter.stage(json2)
        assertTrue(!assertIs<ImportStage.Preview>(b.presenter.stage).preview.canConfirm)
    }

    @Test
    fun `qr model keeps progress after a foreign code and finishes`() = runBlocking<Unit> {
        val a = Device().also { it.seed(entries = 30) }
        val frames = scannedFrames(a.presenter.exportJson())
        val model = QrReceiveModel()
        assertNull(model.offer(frames[0]))
        assertNull(model.offer("hola"))
        assertEquals(1, model.ui.received)
        assertEquals("No es un fragmento de transferencia", model.ui.message)
        val json = frames.drop(1).firstNotNullOf { model.offer(it) }
        assertEquals(a.snapshot(), ExportCodec.decode(json)) // mismo JSON, sin pasar por la base
    }

    @Test
    fun `qr transfer of a export with unicode survives every chunk size`() = runBlocking<Unit> {
        val a = Device().also { it.seed(entries = 5) }
        val json = a.presenter.exportJson()
        for (size in listOf(50, 97, 333, 400, 600)) {
            val model = QrReceiveModel()
            val out = QrTransfer.split(json, size).firstNotNullOf { model.offer(decode(QrCodeEncoder.encode(it))) }
            assertEquals(ExportCodec.decode(json), ExportCodec.decode(out), "chunk $size")
        }
    }
}
