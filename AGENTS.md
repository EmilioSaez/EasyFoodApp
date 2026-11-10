# AGENTS.md

Reglas del repo para agentes y colaboradores (se suman a las preferencias globales de cada uno).

## Base de datos (SQLDelight): ningún cambio de esquema sin `.sqm` y sin test

Los datos del usuario viven en el dispositivo; una migración mal hecha los destruye sin remedio. Por eso:

- **Todo cambio de esquema** (tabla, columna, índice, trigger, CHECK) en un `.sq` **lleva su migración** `N.sqm` en
  `shared/src/commonMain/sqldelight/app/luxion/fitarchiveapp/db/`, y **su test**. Sin los tres, no se fusiona.
- `N.sqm` migra de la versión `N` a la `N+1`. El siguiente número libre es el de la última migración + 1; si dos ramas
  usan el mismo número, la segunda en llegar a `develop` renumera la suya (`MigrationsCompletenessTest` y
  `EverySqmHasATestTest` fallan si hay duplicados o huecos).
- Escribe las migraciones **idempotentes** (`CREATE TABLE IF NOT EXISTS`…) cuando sea posible, y para cambiar un `CHECK`
  reconstruye la tabla conservando filas e ids (ver `7.sqm`). Nunca edites una migración ya publicada: añade otra.
- **Test obligatorio**: `shared/src/jvmTest/kotlin/app/luxion/fitarchiveapp/data/migration/Migration<N>Test.kt`, usando
  `MigrationTestKit`:
  ```kotlin
  class Migration11Test {
      @Test fun keepsData() = MigrationTestKit.legacyDatabase(11).use { d ->      // base en el esquema ANTERIOR
          MigrationTestKit.insert(d, "MiTabla", mapOf("id" to 1, "x" to "a"))     // datos de la tabla tocada
          RepresentativeUserData.seed(d, 11)                                      // y de todas las demás
          val before = MigrationTestKit.snapshot(d)
          MigrationTestKit.migrateToLatest(d, from = 11)
          MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))  // nada se pierde ni cambia
          MigrationTestKit.assertSameSchemaAsFresh(d)                             // = esquema de una base nueva
          // + comprobaciones propias de la migración (valores por defecto, datos derivados…)
      }
  }
  ```
- Si añades tablas o columnas, **añade sus filas a `RepresentativeUserData`**: así `MigrationsKeepUserDataTest` las
  cubre desde cada versión antigua (v1…) y `GenericReimportKeepsUserDataTest` comprueba que la reimportación de la base
  genérica no las toca.
- Ejecutar: `./gradlew :shared:jvmTest --tests '*data.migration.*'` (también corren en el CI, `db-migrations.yml`).
