package app.luxion.fitarchiveapp.backup

import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportedProduct
import app.luxion.fitarchiveapp.export.ImportChanges
import app.luxion.fitarchiveapp.export.ImportTarget
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.ui.screens.DataPresenter
import app.luxion.fitarchiveapp.ui.screens.ImportStage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [BackupFiles] en memoria: la «base» es un texto con su versión. */
internal class FakeBackupFiles(var dbVersion: Long? = null, var dbContent: String = "datos") : BackupFiles {
    val files = linkedMapOf<String, String>()
    val copies = mutableMapOf<String, Pair<Long, String>>()
    var failCopy = false
    var failRestore = false

    override fun names() = (files.keys + copies.keys).toList()
    override fun readText(name: String) = files[name] ?: error("no existe $name")
    override fun writeText(name: String, content: String) { files[name] = content }
    override fun delete(name: String) { files.remove(name); copies.remove(name) }
    override fun databaseVersion() = dbVersion
    override fun copyDatabaseTo(name: String) {
        if (failCopy) error("disco lleno")
        copies[name] = dbVersion!! to dbContent
    }
    override fun restoreDatabaseFrom(name: String) {
        if (failRestore) error("bloqueada")
        val (v, c) = copies.getValue(name)
        dbVersion = v
        dbContent = c
    }
}

class BackupManagerTest {
    private var now = 1_000_000_000_000L
    private var day = "2026-11-10"
    private var json = "{\"a\":1}"
    private val files = FakeBackupFiles()

    private fun manager(keep: Int = 3) = BackupManager(files, { json }, { now }, { day }, keep)

    private val week = BackupManager.INTERVAL_MILLIS

    @Test
    fun isEnabledByDefaultAndRemembersTheSetting() = runTest {
        val m = manager()
        assertTrue(m.isEnabled())
        m.setEnabled(false)
        assertFalse(manager().isEnabled())
        m.setEnabled(true)
        assertTrue(manager().isEnabled())
    }

    @Test
    fun firstRunMakesACopy() = runTest {
        val info = assertNotNull(manager().runIfDue())
        assertEquals("2026-11-10", info.date)
        assertEquals(json, files.files[info.name])
        assertEquals(info, manager().last())
    }

    @Test
    fun doesNotCopyAgainBeforeAWeek() = runTest {
        val m = manager()
        m.runIfDue()
        now += week - 1
        assertNull(m.runIfDue())
        assertEquals(1, m.list().size)
        now += 1
        day = "2026-11-17"
        assertNotNull(m.runIfDue())
        assertEquals(2, m.list().size)
    }

    @Test
    fun disabledDoesNothingButManualCopyStillWorks() = runTest {
        val m = manager()
        m.setEnabled(false)
        assertNull(m.runIfDue())
        assertTrue(m.list().isEmpty())
        assertNotNull(m.backupNow())
        assertEquals(1, m.list().size)
    }

    @Test
    fun keepsOnlyTheLatestCopiesNewestFirst() = runTest {
        val m = manager(keep = 3)
        repeat(5) {
            now += week
            day = "2026-12-0${it + 1}"
            m.backupNow()
        }
        val list = m.list()
        assertEquals(listOf("2026-12-05", "2026-12-04", "2026-12-03"), list.map { it.date })
        // Los ajustes no cuentan como copia ni se rotan.
        m.setEnabled(false)
        assertEquals(3, m.list().size)
        assertTrue(BackupManager.SETTINGS_FILE in files.files)
    }

    @Test
    fun backupFailureIsSwallowedByRunIfDue() = runTest {
        val m = BackupManager(files, { error("sin acceso") }, { now }, { day })
        assertNull(m.runIfDue())
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun ignoresUnrelatedFilesAndBadNames() = runTest {
        files.files["easyfood-2026-11-04.json"] = "{}" // exportación manual
        files.files["easyfood-auto-xx.json"] = "{}"
        files.files["easyfood-auto-2026-11-10-5.json.tmp"] = "{}"
        files.copies["pre-migracion-v1-a-2-2026-11-10-5.db"] = 1L to "x"
        assertTrue(manager().list().isEmpty())
    }

    @Test
    fun dueRule() {
        assertTrue(BackupManager.isDue(null, 5))
        assertFalse(BackupManager.isDue(100, 100 + week - 1))
        assertTrue(BackupManager.isDue(100, 100 + week))
        // Reloj atrasado respecto a la última copia: no se bloquea para siempre.
        assertTrue(BackupManager.isDue(100, 50))
    }

    @Test
    fun restoringACopyGoesThroughTheImportPreview() = runTest {
        val target = object : ImportTarget {
            var applied: ImportChanges? = null
            override suspend fun products() = emptyList<ExportedProduct>()
            override suspend fun diaryEntries() = emptyList<DiaryEntry>()
            override suspend fun apply(changes: ImportChanges) { applied = changes }
        }
        val exporter = DataExporter(target) { 7 }
        val m = BackupManager(files, exporter::exportToJson, { now }, { day })
        val info = m.backupNow()
        val presenter = DataPresenter(exporter, DataImporter(target), m)

        presenter.stageBackup(info)

        // Nada se escribe hasta confirmar; una copia sin diferencias solo ofrece cerrar.
        assertIs<ImportStage.Preview>(presenter.stage)
        assertNull(target.applied)
    }

    @Test
    fun restoringAMissingCopyFailsCleanly() = runTest {
        val target = object : ImportTarget {
            override suspend fun products() = emptyList<ExportedProduct>()
            override suspend fun diaryEntries() = emptyList<DiaryEntry>()
            override suspend fun apply(changes: ImportChanges) {}
        }
        val m = manager()
        val presenter = DataPresenter(DataExporter(target) { 7 }, DataImporter(target), m)
        presenter.stageBackup(BackupInfo("easyfood-auto-2026-11-10-1.json", "2026-11-10", 1))
        assertIs<ImportStage.Failed>(presenter.stage)
    }
}

class MigrationBackupTest {
    private var now = 1_000L
    private val files = FakeBackupFiles(dbVersion = 9)

    private fun migration(keep: Int = 3) = MigrationBackup(files, { now }, { "2026-11-10" }, keep)

    @Test
    fun copiesBeforeMigratingToANewerVersion() {
        val copy = assertNotNull(migration().protect(10))
        assertEquals(9, copy.from)
        assertEquals(10, copy.to)
        assertEquals(9L to "datos", files.copies[copy.name])
        assertEquals(listOf(copy), migration().copies())
    }

    @Test
    fun doesNothingWhenThereIsNothingToMigrate() {
        assertNull(migration().protect(9))
        files.dbVersion = 12
        assertNull(migration().protect(10))
        files.dbVersion = null // instalación nueva
        assertNull(migration().protect(10))
        assertTrue(files.copies.isEmpty())
    }

    @Test
    fun keepsTheLatestCopies() {
        val m = migration(keep = 2)
        for (v in 3L..6L) {
            now += 1
            files.dbVersion = v
            m.protect(v + 1)
        }
        assertEquals(listOf(6L, 5L), m.copies().map { it.from })
    }

    @Test
    fun aFailedCopyDoesNotBlockStartup() {
        files.failCopy = true
        assertNull(migration().protect(10))
    }

    @Test
    fun failedMigrationRestoresTheDatabase() {
        val ex = assertFailsWith<MigrationFailedException> {
            migration().openProtected(10) {
                files.dbVersion = 10 // la migración deja la base a medias
                files.dbContent = "corrupta"
                error("sql roto")
            }
        }
        assertTrue(ex.restored)
        assertEquals(9L, files.dbVersion)
        assertEquals("datos", files.dbContent)
        assertEquals("sql roto", ex.cause?.message)
    }

    @Test
    fun reportsWhenRestoreItselfFails() {
        files.failRestore = true
        val ex = assertFailsWith<MigrationFailedException> { migration().openProtected(10) { error("x") } }
        assertFalse(ex.restored)
        assertTrue("pre-migracion" in ex.message.orEmpty())
    }

    @Test
    fun successfulMigrationKeepsTheCopyAndReturnsTheResult() {
        val result = migration().openProtected(10) { "abierta" }
        assertEquals("abierta", result)
        assertEquals(1, migration().copies().size)
    }

    @Test
    fun errorsWithoutAMigrationAreRethrownUntouched() {
        files.dbVersion = 10
        val ex = assertFailsWith<IllegalStateException> { migration().openProtected(10) { error("otro") } }
        assertEquals("otro", ex.message)
        assertTrue(files.copies.isEmpty())
    }
}

class SqliteHeaderTest {
    private fun header(version: Long): ByteArray {
        val h = ByteArray(100)
        "SQLite format 3".encodeToByteArray().copyInto(h)
        for (i in 0..3) h[60 + i] = (version shr (8 * (3 - i))).toByte()
        return h
    }

    @Test
    fun readsUserVersion() {
        assertEquals(10L, sqliteUserVersion(header(10)))
        assertEquals(0L, sqliteUserVersion(header(0)))
        assertEquals(70_000L, sqliteUserVersion(header(70_000)))
    }

    @Test
    fun rejectsNonSqliteOrTruncatedFiles() {
        assertNull(sqliteUserVersion(ByteArray(100)))
        assertNull(sqliteUserVersion(header(3).copyOf(40)))
        assertNull(sqliteUserVersion(ByteArray(0)))
    }
}

class BackupSummaryTest {
    private val last = BackupInfo("easyfood-auto-2026-11-10-5.json", "2026-11-10", 5)

    @Test
    fun describesTheLastCopyOrWhyThereIsNone() {
        assertEquals("Última copia: 2026-11-10", app.luxion.fitarchiveapp.ui.screens.backupSummary(true, last))
        assertEquals("Última copia: 2026-11-10 · desactivada", app.luxion.fitarchiveapp.ui.screens.backupSummary(false, last))
        assertTrue("primera" in app.luxion.fitarchiveapp.ui.screens.backupSummary(true, null))
        assertTrue("Desactivada" in app.luxion.fitarchiveapp.ui.screens.backupSummary(false, null))
    }
}
