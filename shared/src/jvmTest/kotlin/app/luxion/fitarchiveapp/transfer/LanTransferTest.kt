package app.luxion.fitarchiveapp.transfer

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportCodec
import app.luxion.fitarchiveapp.export.ExportFile
import app.luxion.fitarchiveapp.export.ExportedNutrients
import app.luxion.fitarchiveapp.export.ExportedProduct
import app.luxion.fitarchiveapp.export.SqlDelightExportSource
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import kotlinx.coroutines.runBlocking
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanTransferTest {
    private val hosts = mutableListOf<LanTransferHost>()
    private var now = 1_000L

    private val json = ExportCodec.encode(
        ExportFile(
            schemaVersion = ExportCodec.CURRENT_VERSION,
            exportedAtMillis = 5_000L,
            products = listOf(
                ExportedProduct(
                    barcode = "3017620422003", name = "Nutella", brand = "Ferrero", imageUrl = null,
                    nutrients = ExportedNutrients(), servingSizeG = 15.0, servingLabel = "15 g",
                    source = "off", updatedAtMillis = 1L,
                ),
            ),
        ),
    )

    private fun host(
        payload: String = json,
        ttl: Long = 60_000,
        attempts: Int = 3,
    ) = LanTransferHost(
        exportJson = { payload }, nowMillis = { now }, ttlMillis = ttl, maxAttempts = attempts,
        newCode = { "123456" },
    ).also { hosts += it }

    @AfterTest
    fun tearDown() = hosts.forEach { it.stop() }

    private suspend fun fetch(offer: LanOffer, code: String = offer.code) =
        LanTransferClient.fetch("127.0.0.1", offer.port, code)

    @Test
    fun `transfers the json with the right code`() = runBlocking {
        val offer = host().start()
        assertEquals(json, fetch(offer))
    }

    @Test
    fun `offer is single use`() = runBlocking {
        val h = host()
        val offer = h.start()
        fetch(offer)
        assertFalse(h.isOpen)
        assertFailsWith<LanTransferException> { fetch(offer) }
        Unit
    }

    @Test
    fun `wrong code is denied and does not consume the offer`() = runBlocking {
        val offer = host().start()
        val e = assertFailsWith<LanTransferException> { fetch(offer, "000000") }
        assertEquals(LanTransferError.Denied, e.error)
        assertEquals(json, fetch(offer))
    }

    @Test
    fun `offer closes after too many failed attempts even with the right code`() = runBlocking {
        val h = host(attempts = 2)
        val offer = h.start()
        repeat(2) { assertFailsWith<LanTransferException> { fetch(offer, "000000") } }
        assertFalse(h.isOpen)
        assertFailsWith<LanTransferException> { fetch(offer) }
        Unit
    }

    @Test
    fun `expired offer is denied`() = runBlocking {
        val offer = host(ttl = 10_000).start()
        now += 10_000
        val e = assertFailsWith<LanTransferException> { fetch(offer) }
        assertEquals(LanTransferError.Denied, e.error)
    }

    @Test
    fun `offer stops listening after the ttl`() = runBlocking {
        val h = host(ttl = 200)
        val offer = h.start()
        Thread.sleep(600)
        assertFalse(h.isOpen)
        val e = assertFailsWith<LanTransferException> { fetch(offer) }
        assertEquals(LanTransferError.Unreachable, e.error)
    }

    @Test
    fun `garbage requests are denied without leaking data`() = runBlocking {
        val h = host()
        val offer = h.start()
        for (req in listOf("GET / HTTP/1.1\r\n\r\n", "EASYFOOD-LAN/1\n", "EASYFOOD-LAN/1 ${"9".repeat(500)}\n")) {
            Socket("127.0.0.1", offer.port).use { s ->
                s.getOutputStream().write(req.toByteArray())
                s.getOutputStream().flush()
                val reply = s.getInputStream().readBytes().decodeToString()
                assertEquals("DENIED\n", reply)
                assertFalse(reply.contains("Nutella"))
            }
        }
    }

    @Test
    fun `random pairing codes have the right shape`() {
        repeat(50) { assertTrue(Regex("\\d{$PAIRING_CODE_LENGTH}").matches(generatePairingCode())) }
    }

    @Test
    fun `client rejects oversized and invalid payloads`() = runBlocking {
        val offer = host().start()
        val e = assertFailsWith<LanTransferException> {
            LanTransferClient.fetch("127.0.0.1", offer.port, offer.code, maxBytes = 10)
        }
        assertEquals(LanTransferError.TooLarge, e.error)

        val bad = host(payload = "no es json").start()
        val e2 = assertFailsWith<LanTransferException> { fetch(bad) }
        assertEquals(LanTransferError.InvalidData, e2.error)
    }

    @Test
    fun `received json goes through the importer`() = runBlocking {
        fun db() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).let { d ->
            EasyFoodDatabase.Schema.create(d)
            d to EasyFoodDatabase(d)
        }
        val (srcDriver, src) = db()
        val (dstDriver, dst) = db()
        try {
            val importerSrc = DataImporter(SqlDelightImportTarget(src.productQueries, src.diaryEntryQueries, src.customFoodQueries, src.weightEntryQueries, src.recipeQueries, src.settingsQueries, src.favoriteFoodQueries))
            importerSrc.import(json)
            val exporter = DataExporter(SqlDelightExportSource(src.productQueries, src.diaryEntryQueries, src.customFoodQueries, src.weightEntryQueries, src.recipeQueries, src.settingsQueries, src.favoriteFoodQueries)) { now }
            val h = LanTransferHost(exportJson = exporter::exportToJson, nowMillis = { now }).also { hosts += it }
            val offer = h.start()
            val importer = DataImporter(SqlDelightImportTarget(dst.productQueries, dst.diaryEntryQueries, dst.customFoodQueries, dst.weightEntryQueries, dst.recipeQueries, dst.settingsQueries, dst.favoriteFoodQueries))
            LanTransferClient.fetchAndImport(importer, "127.0.0.1", offer.port, offer.code)
            assertEquals(1, dst.productQueries.selectAll().executeAsList().size)
        } finally {
            srcDriver.close()
            dstDriver.close()
        }
    }
}
