# Base genérica de alimentos

Alimentos sin código de barras (fruta, pan, carne…) embebidos en la app, con origen `GENERIC`.

## Fuente y licencia

- **Fuente**: [USDA FoodData Central](https://fdc.nal.usda.gov/), conjunto **SR Legacy** (publicación 04/2018, 7 793 alimentos con energía).
- **Licencia**: dominio público, **CC0 1.0 Universal** (<https://creativecommons.org/publicdomain/zero/1.0/>; DOI 10.15482/USDA.ADC/1529216). Se puede redistribuir sin permiso; USDA agradece que se cite FoodData Central como fuente, y la app lo hace aquí.
- **BEDCA descartada**: su web declara «Copyright © 2007 Consorcio BEDCA y AESAN, todos los derechos reservados» y no ofrece licencia de redistribución. Solo podría incluirse con permiso expreso de sus titulares.
- **Limitación**: los nombres están en inglés (así los publica USDA). La búsqueda FTS funciona sobre ellos; una traducción al español queda como mejora futura.

## Recurso embebido

`shared/src/commonMain/composeResources/files/generic_foods.tsv` (~750 KB). Texto UTF-8, tabuladores:

- Líneas `#...`: metadatos. La primera es `#easyfood-generic-foods v1`.
- Primera línea sin `#`: cabecera `id name kcal protein_g carbs_g fat_g saturated_fat_g sugars_g fiber_g salt_g`.
- Una fila por alimento, valores **por 100 g**; celda vacía = dato no disponible. `salt_g` = sodio × 2,5.

Se regenera con `python tools/generate_generic_foods.py <carpeta del CSV de SR Legacy> <salida.tsv>`.

## Importación

`GenericFoodImporter` (`shared/.../data/generic`) lee el recurso (`readEmbeddedGenericFoods()`) y lo guarda en `ProductEntity` con `source = 'GENERIC'` y clave `generic:<fdcId>` (no choca con un EAN), en una sola transacción. Los triggers de `ProductFts` lo indexan para la búsqueda de texto existente. Es idempotente. Las filas mal formadas se descartan y se cuentan en `GenericFoodImportResult.skipped`.

### Carga al arrancar

`GenericFoodSeeder` (`shared/.../data/generic`) la lanza desde `App` (`genericSeeder`) en segundo plano: si ya hay alimentos `GENERIC` no hace nada (ni lee el recurso); si no, parsea e importa fuera del hilo de UI y publica `state` (`Running(done, total)` → `Done` / `Failed`). `App` muestra una franja con el avance (`SeedProgressBanner`). Llamadas simultáneas se serializan y un fallo se puede reintentar. Es una sola transacción: no queda una carga a medias.

### Rendimiento (7 793 alimentos, SQLite en memoria, JVM)

Medido con `GenericFoodBenchmarkTest`:

| | antes | después |
|---|---|---|
| primera importación | 6 466 ms | ~850 ms |
| reimportación | 14 354 ms | ~890 ms |
| búsqueda FTS (media) | 3,8 ms | 2,8 ms |

La causa era que los triggers de `ProductFts` borraban por `barcode` (columna `UNINDEXED`), recorriendo toda la tabla FTS en cada fila (O(n²)). Ahora `ProductFts.rowid` = `ProductEntity.rowid` y se borra por rowid; para conservar el rowid, `ProductEntity.upsert` usa `ON CONFLICT DO UPDATE` en vez de `INSERT OR REPLACE`. La migración `2.sqm` reconstruye el índice y añade `product_source_idx` (para `countBySource`). **No ejecutar `VACUUM`**: puede renumerar los rowids y desincronizar el índice.

Pendiente: mostrar el origen en la UI.
