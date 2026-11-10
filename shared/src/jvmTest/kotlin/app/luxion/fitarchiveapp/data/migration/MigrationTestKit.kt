package app.luxion.fitarchiveapp.data.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Utilidades para probar migraciones de esquema (`N.sqm`) sin perder datos del usuario.
 *
 * Regla del repo: ningún cambio de esquema sin `.sqm` y sin test (ver AGENTS.md). Al añadir `N.sqm`:
 *  1. Crea `data/migration/MigrationNTest.kt` (lo exige [EverySqmHasATestTest]).
 *  2. Parte de [legacyDatabase] (`N`), inserta filas con [insert] usando el esquema ANTERIOR, toma un [snapshot],
 *     migra con [migrateToLatest] y comprueba con [assertPreserved] y [assertSameSchemaAsFresh].
 *  3. Si añades tablas nuevas, añade sus filas a [RepresentativeUserData] para que el test de todas las versiones las cubra.
 *
 * Ejemplo mínimo:
 * ```
 * class Migration11Test {
 *     @Test fun keepsData() = MigrationTestKit.legacyDatabase(11).use { d ->
 *         MigrationTestKit.insert(d, "MiTabla", mapOf("id" to 1, "x" to "a"))
 *         val before = MigrationTestKit.snapshot(d)
 *         MigrationTestKit.migrateToLatest(d, from = 11)
 *         MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
 *         MigrationTestKit.assertSameSchemaAsFresh(d)
 *     }
 * }
 * ```
 */
object MigrationTestKit {
    /** Versión actual del esquema (= número de la última migración + 1). */
    val latestVersion: Int get() = EasyFoodDatabase.Schema.version.toInt()

    /** Versiones antiguas desde las que se puede migrar. */
    val legacyVersions: IntRange get() = 1 until latestVersion

    /**
     * Esquema de la versión 1, congelado: lo que existía antes de la primera migración.
     * No lo edites: las bases posteriores se obtienen aplicando los `.sqm` reales sobre él.
     * (CustomFoodEntity se incluye porque la rama que la creó llegó a tener su propio 1.sqm, luego pisado.)
     */
    private val baselineV1 = listOf(
        """CREATE TABLE ProductEntity (
            barcode TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, brand TEXT, image_url TEXT, kcal REAL,
            protein_g REAL, carbs_g REAL, sugars_g REAL, fat_g REAL, saturated_fat_g REAL, fiber_g REAL, salt_g REAL,
            serving_size_g REAL, serving_label TEXT, source TEXT NOT NULL DEFAULT 'OPEN_FOOD_FACTS',
            cached_at INTEGER NOT NULL)""",
        "CREATE INDEX product_name_idx ON ProductEntity(name)",
        """CREATE TABLE DiaryEntryEntity (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL,
            meal TEXT NOT NULL CHECK (meal IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
            barcode TEXT, name TEXT NOT NULL, quantity REAL NOT NULL CHECK (quantity > 0),
            unit TEXT NOT NULL CHECK (unit IN ('GRAMS', 'SERVINGS')), serving_size_g REAL, kcal_100g REAL,
            protein_100g REAL, carbs_100g REAL, fat_100g REAL, created_at INTEGER NOT NULL)""",
        "CREATE INDEX diary_entry_date_idx ON DiaryEntryEntity(date, meal)",
        """CREATE TABLE CustomFoodEntity (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, brand TEXT,
            kcal REAL NOT NULL CHECK (kcal >= 0), protein_g REAL NOT NULL CHECK (protein_g >= 0),
            carbs_g REAL NOT NULL CHECK (carbs_g >= 0), fat_g REAL NOT NULL CHECK (fat_g >= 0),
            sugars_g REAL, saturated_fat_g REAL, fiber_g REAL, salt_g REAL,
            serving_size_g REAL CHECK (serving_size_g IS NULL OR serving_size_g > 0), serving_label TEXT,
            source TEXT NOT NULL DEFAULT 'CUSTOM' CHECK (source = 'CUSTOM'),
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)""",
        "CREATE INDEX custom_food_name_idx ON CustomFoodEntity(name)",
    )

    fun newDriver(): JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    /** Base en memoria tal y como estaba en la versión [version] (1..[latestVersion]), sin datos. */
    fun legacyDatabase(version: Int): JdbcSqliteDriver {
        require(version in 1..latestVersion) { "Versión $version fuera de 1..$latestVersion" }
        val driver = newDriver()
        baselineV1.forEach { driver.execute(null, it, 0) }
        if (version > 1) EasyFoodDatabase.Schema.migrate(driver, 1, version.toLong())
        return driver
    }

    fun migrateToLatest(driver: JdbcSqliteDriver, from: Int) {
        EasyFoodDatabase.Schema.migrate(driver, from.toLong(), EasyFoodDatabase.Schema.version)
    }

    inline fun <T> JdbcSqliteDriver.use(block: (JdbcSqliteDriver) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    // --- Consultas crudas -------------------------------------------------------------------------------------

    fun <T> query(driver: JdbcSqliteDriver, sql: String, read: (SqlCursor) -> T): List<T> =
        driver.executeQuery(null, sql, { c ->
            val out = mutableListOf<T>()
            while (c.next().value) out += read(c)
            QueryResult.Value(out)
        }, 0).value

    fun scalar(driver: JdbcSqliteDriver, sql: String): Long = query(driver, sql) { it.getLong(0)!! }.single()

    /** Tablas reales (sin las internas de SQLite ni las auxiliares del índice FTS5). */
    fun userTables(driver: JdbcSqliteDriver): List<String> = query(
        driver, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
    ) { it.getString(0)!! }.filterNot { it.startsWith("ProductFts_") }

    fun hasTable(driver: JdbcSqliteDriver, table: String) = table in userTables(driver)

    fun columns(driver: JdbcSqliteDriver, table: String): List<String> =
        query(driver, "PRAGMA table_info(\"$table\")") { it.getString(1)!! }

    // --- Inserción de filas -----------------------------------------------------------------------------------

    private fun literal(v: Any?): String = when (v) {
        null -> "NULL"
        is Number -> v.toString()
        else -> "'" + v.toString().replace("'", "''") + "'"
    }

    /**
     * Inserta [row] ignorando las columnas que la tabla no tiene en la versión actual de la base, y devuelve `false`
     * si la tabla no existe aún. Así una misma fila "completa" sirve para cualquier versión antigua.
     */
    fun insert(driver: JdbcSqliteDriver, table: String, row: Map<String, Any?>): Boolean {
        if (!hasTable(driver, table)) return false
        val cols = columns(driver, table).toSet()
        val used = row.filterKeys { it in cols }
        driver.execute(
            null,
            "INSERT INTO \"$table\"(${used.keys.joinToString { "\"$it\"" }}) VALUES (${used.values.joinToString { literal(it) }})",
            0,
        )
        return true
    }

    // --- Instantáneas y comparación ---------------------------------------------------------------------------

    /** Fila como `columna -> valor SQL literal` (vía `quote()`: distingue 1 de '1', NULL de '' y REAL de INTEGER). */
    fun dump(driver: JdbcSqliteDriver, table: String): List<Map<String, String>> {
        val cols = columns(driver, table)
        return query(driver, "SELECT ${cols.joinToString { "quote(\"$it\")" }} FROM \"$table\"") { c ->
            cols.indices.associate { cols[it] to c.getString(it)!! }
        }
    }

    /** Todas las filas de todas las tablas de usuario. */
    fun snapshot(driver: JdbcSqliteDriver): Map<String, List<Map<String, String>>> =
        userTables(driver).associateWith { dump(driver, it) }

    /**
     * Cada fila de [before] debe seguir en [after] con los mismos valores en las columnas que ya tenía (las columnas
     * nuevas se ignoran) y sin filas de más. Devuelve los problemas encontrados (vacío = todo conservado).
     */
    fun differences(
        before: Map<String, List<Map<String, String>>>,
        after: Map<String, List<Map<String, String>>>,
    ): List<String> {
        val problems = mutableListOf<String>()
        for ((table, oldRows) in before) {
            val newRows = after[table]
            if (newRows == null) {
                if (oldRows.isNotEmpty()) problems += "$table: la tabla ha desaparecido con ${oldRows.size} filas"
                continue
            }
            val cols = oldRows.firstOrNull()?.keys ?: continue
            val expected = oldRows.map { it.toSortedMap().toString() }.sorted()
            val actual = newRows.map { r -> r.filterKeys { it in cols }.toSortedMap().toString() }.sorted()
            if (expected != actual) {
                problems += "$table: esperado ${expected.size} filas $expected pero hay ${actual.size} $actual"
            }
        }
        return problems
    }

    fun assertPreserved(
        before: Map<String, List<Map<String, String>>>,
        after: Map<String, List<Map<String, String>>>,
    ) {
        val problems = differences(before, after)
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** Estructura de la base: columnas de cada tabla, índices y triggers. Ignora el orden y el SQL literal. */
    fun schemaFingerprint(driver: JdbcSqliteDriver): Map<String, Set<String>> {
        val result = sortedMapOf<String, Set<String>>()
        for (t in userTables(driver)) {
            result["table $t"] = query(driver, "PRAGMA table_info(\"$t\")") {
                "${it.getString(1)} ${it.getString(2)} notnull=${it.getLong(3)} default=${it.getString(4)} pk=${it.getLong(5)}"
            }.toSet()
        }
        val named = query(
            driver,
            "SELECT type, name, tbl_name FROM sqlite_master WHERE type IN ('index', 'trigger') " +
                "AND name NOT LIKE 'sqlite_%' AND tbl_name NOT LIKE 'ProductFts_%'",
        ) { "${it.getString(0)} ${it.getString(1)} on ${it.getString(2)}" }
        result["indexes y triggers"] = named.toSet()
        return result
    }

    /** La base migrada debe tener exactamente la estructura de una creada de cero con el esquema actual. */
    fun assertSameSchemaAsFresh(migrated: JdbcSqliteDriver) {
        val fresh = newDriver()
        try {
            EasyFoodDatabase.Schema.create(fresh)
            assertEquals(schemaFingerprint(fresh), schemaFingerprint(migrated))
        } finally {
            fresh.close()
        }
    }
}
