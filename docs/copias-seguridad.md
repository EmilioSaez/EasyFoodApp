# Copias de seguridad automáticas

Dos protecciones independientes, sin tabla ni migración nueva (el ajuste vive en la carpeta de copias). Código en
`shared/src/commonMain/.../backup/`; la parte de archivos es `expect/actual` (`fileBackupFiles`).

## 1. Antes de migrar la base de datos

Si al abrir la app la versión del esquema (`PRAGMA user_version`) es menor que la de la app, se **copia el archivo
`.db`** (y su `-wal` si lo hay) antes de aplicar la migración. No se usa el JSON de exportación porque el código nuevo
no sabe leer el esquema viejo. La versión se lee de la cabecera del archivo SQLite (bytes 60–63), así que no hace falta
abrir la base y vale igual en las tres plataformas.

- Archivo: `pre-migracion-v<desde>-a-<hasta>-<fecha>-<millis>.db`. Se conservan las **3** últimas.
- Si la migración falla, se cierra la base, se **restaura** la copia y se muestra `MigrationErrorScreen` con el motivo
  (`MigrationFailedException`). Los datos quedan como antes de actualizar. Si no se pudiera restaurar, el mensaje indica
  la copia que sigue en la carpeta.
- Si la copia previa falla (p. ej. disco lleno) no se bloquea el arranque: se migra igualmente (en Android la migración
  es transaccional; en escritorio no lo es, por eso la restauración).
- Una instalación nueva (sin archivo) o una base ya en la última versión no generan copia.

## 2. Copia periódica (JSON)

Con la exportación de siempre (`DataExporter`), cada **7 días**: 5 s después de abrir la app se mira la fecha de la
última copia y, si toca, se guarda otra; si la app sigue abierta se vuelve a mirar cada 6 h. Se conservan las **6**
últimas (`easyfood-auto-<fecha>-<millis>.json`). Un fallo al copiar nunca molesta al usuario.

En **Datos → Archivo → Copia de seguridad automática**: interruptor (activada por defecto), fecha de la última copia,
«Copiar ahora» (aunque esté desactivada) y la lista de copias con «Restaurar». Restaurar abre la **misma vista previa de
importación** que cualquier otro origen: se fusiona con lo que haya y no se borra nada.

El interruptor se guarda como `auto-backup.txt` (`on`/`off`) en la carpeta de copias y la «última copia» se deduce de
los nombres de archivo, por lo que no se toca `SettingsEntity`.

## Ubicación

| Plataforma | Copias | Base |
|---|---|---|
| Escritorio | `~/.easyfood/backups/` | `~/.easyfood/easyfood.db` |
| Android | `files/backups/` (almacenamiento interno de la app) | `databases/easyfood.db` |
| iOS | `Application Support/EasyFood/backups/` | la que use el driver nativo (se busca en Documents y Application Support) |

Las copias viven junto a la app: si se desinstala (Android) se pierden con ella, igual que los datos. Para guardar una
copia fuera, usar «Guardar archivo JSON».

## Pruebas

`BackupManagerTest`, `MigrationBackupTest` y `SqliteHeaderTest` (common, con archivos simulados) y
`FileBackupFilesTest` (JVM, con una base SQLite real: copia, restauración y fallo de migración).
