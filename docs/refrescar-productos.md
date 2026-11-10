# Refresco de productos de Open Food Facts

Los productos de Open Food Facts (OFF) que la app ya consultó se guardan en `ProductEntity` y cambian con el tiempo en OFF (reformulaciones, correcciones de la comunidad). Este documento describe cómo se mantienen al día sin tocar nada que pertenezca al usuario.

## Qué se refresca y qué no

| Dato | ¿Se modifica al refrescar? |
|---|---|
| `ProductEntity` con `source = 'OPEN_FOOD_FACTS'` | Sí (nombre, marca, imagen, nutrientes, porción, `cached_at`) |
| `ProductEntity` genérico (`generic:<id>`) o de otro origen | **No**: la consulta `updateRefreshed` filtra por origen |
| Alimentos propios (`CustomFoodEntity`) | **No** |
| Entradas del diario (`DiaryEntryEntity`) | **No**: cada entrada guarda una copia de los valores por 100 g con los que se registró |
| Ingredientes de recetas | **No**: también guardan su copia |
| Favoritos | **No**: solo guardan la clave; la ficha mostrará los valores nuevos |
| Correcciones manuales del usuario | **No** (viven en su propia tabla; ver el PR de correcciones) |

**Decisión sobre el pasado del diario:** las entradas antiguas conservan los valores con los que se registraron. Cambiarlas con retraso reescribiría el historial de kcal de días ya cerrados (rachas, estado de día, resúmenes). La ficha avisa de ello cuando hubo un cambio. Las entradas nuevas usan los valores refrescados.

## Esquema (migración 14 → versión 15)

`14.sqm` crea la tabla `ProductRefreshEntity` (no modifica `ProductEntity`):

| Columna | Significado |
|---|---|
| `barcode` (PK) | EAN del producto |
| `refreshed_at` | Última vez que OFF confirmó o actualizó el producto |
| `checked_at` | Última vez que OFF respondió (también si el producto ya no existe o se rechazó el dato nuevo) |
| `changed_at`, `change_summary` | Último cambio nutricional relevante y su resumen («Energía 250 → 230 kcal») |

- Es idempotente (`IF NOT EXISTS`, `INSERT OR IGNORE`) y rellena `refreshed_at` con `cached_at` para las filas ya existentes de OFF, de modo que no se pierde ningún dato.
- Un trigger borra la fila de seguimiento cuando se borra el producto.
- Sin fila = nunca refrescado; la ficha usa entonces `cached_at` como fecha de actualización.

## Cambios relevantes

`ProductChange.summary` compara los valores por 100 g antes y después. Es relevante (y se avisa) si:

- la energía cambia ≥ 5 kcal **y** ≥ 5 %;
- proteína, hidratos o grasas cambian ≥ 1 g **y** ≥ 10 %;
- un dato aparece o desaparece.

Las diferencias de redondeo se aplican en silencio. Cambios de nombre, imagen o porción se guardan pero no se avisan.

## Salvaguardas

- Si el dato nuevo es peor que el guardado (sin energía o con kcal absurdas, `Nutrients.needsReview`), **no lo sustituye**: se conserva la copia local y solo se anota la consulta (`checked_at`).
- Si el producto ya no está en OFF, se conserva la copia local.
- Sin red: el refresco falla sin modificar nada.

## Refresco manual

En la ficha de un producto de OFF (no en la edición de una entrada del diario), la tarjeta «Datos de Open Food Facts» muestra «actualizado hoy / hace N días», el último cambio relevante si lo hubo y el botón «Actualizar datos». Tras pulsarlo informa de: valores actualizados (con el detalle), sin cambios relevantes, datos incompletos en OFF (se conservan los guardados), producto retirado o sin conexión.

## Refresco automático

`ProductRefresher`, lanzado desde `App` (parámetro `backgroundRefresh`, activo en Android, escritorio e iOS) 15 s después de arrancar:

- Candidatos: productos de OFF que estén en el diario o en favoritos y cuya última consulta (`checked_at`, `refreshed_at` o `cached_at`) tenga más de **14 días**; los más antiguos primero.
- Máximo **20 peticiones** por pasada, con **500 ms** entre ellas; una pasada por arranque.
- Se detiene a la primera falla de red y cualquier excepción se ignora: sin conexión no hace nada.
- Los productos retirados o rechazados también marcan `checked_at`, así que no se reintentan en cada arranque.

Además, la carga normal de la ficha (`getByBarcode`) ya refrescaba un producto caducado (30 días); ahora usa la misma lógica y también registra el cambio.
