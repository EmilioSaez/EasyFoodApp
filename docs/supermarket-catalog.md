# Catálogo de supermercados españoles (investigación)

Estado: **implementado** (decisión: vía Open Food Facts/ODbL aceptando el share-alike).

## Fuentes y licencia

| Fuente | Licencia / condiciones | ¿Se puede embeber? |
|---|---|---|
| Mercadona, Dia, Lidl, Carrefour (webs y apps propias) | Sin API pública ni licencia de datos abiertos. Los catálogos solo salen por scraping de endpoints no documentados, sujetos a los términos de cada web (derechos de la base de datos y de marca). | **No** sin permiso expreso de cada cadena |
| Scrapers/APIs no oficiales (p. ej. mercaapi, actores de Apify) | Redistribuyen datos extraídos sin autorización de la cadena; la licencia del código no cubre los datos. | **No** |
| [Open Food Facts](https://world.openfoodfacts.org) | Base de datos **ODbL 1.0**, contenidos bajo DbCL; imágenes CC BY-SA. | **Sí, con condiciones** (abajo) |

Open Food Facts ya cubre estos supermercados por marca blanca (cifras aproximadas del sitio, varían según la captura):
Hacendado ~10 700, Carrefour ~8 400, Mercadona ~3 600, Dia ~3 100, Lidl ~2 100 productos.

## Condiciones de la ODbL si se embebe un subconjunto de OFF

- **Atribución**: «© Open Food Facts contributors» visible donde se muestren los datos, y mantener el aviso de licencia.
- **Share-alike**: la base derivada embebida (el recurso con los productos extraídos) se debe ofrecer también bajo ODbL a quien reciba la app. Si el repo es público basta publicar el recurso con su aviso; el código de la app no se ve afectado.
- No se incluyen imágenes (CC BY-SA).
- La app ya consulta OFF en línea; esto sería una copia local offline filtrada (marcas blancas y supermercados, solo España).

## Implementación

- **Recurso**: `shared/src/commonMain/composeResources/files/supermarket_products.tsv` (15 515 productos, ~1,1 MB sin comprimir; ~0,4 MB en el APK). Columnas `code name brand kcal protein_g carbs_g fat_g saturated_fat_g sugars_g fiber_g salt_g`, por 100 g/ml. Cabecera con la atribución y la licencia ODbL. Sin imágenes.
- **Generación**: `python -I tools/generate_supermarket_catalog.py <food.parquet> <salida.tsv>` (volcado Parquet de OFF en Hugging Face, `pip install duckdb`). Filtra `en:spain`, no obsoletos, marcas o tiendas de Mercadona/Hacendado/Deliplus/Bosque Verde/Compy, Dia, Carrefour, Lidl/Milbona/Deluxe, Eroski, Alcampo/Auchan, Aldi/Milsani, Consum; exige kcal (≤ 900) y los tres macros, con suma ≤ 105 g. Nombre en español si existe.
- **Base de datos**: filas en `ProductEntity` con `source = 'SUPERMARKET'` y clave = EAN (un escaneo lo encuentra sin red). **Sin cambio de esquema** (`source` es texto libre), así que no hay `.sqm`. `ProductQueries.insertIfAbsent` nunca pisa una fila existente con el mismo EAN.
- **Carga**: `SupermarketSeeder` (análogo a `GenericFoodSeeder`) se lanza en el arranque tras la base genérica; una transacción; si ya hay filas `SUPERMARKET` no hace nada. No hay versión de datos: para actualizar el recurso habrá que añadir una (tabla meta + migración con test, como `GenericFoodsMetaEntity`).
- **Búsqueda**: la FTS de siempre; `ProductSearch.sq` resta 1,5 al `bm25` de las filas `SUPERMARKET`, así que a igualdad de relevancia van antes que OFF y genéricos. No cuentan como «tuyos» en `onlyMine`.
- **UI**: origen «Supermercado» (chip/avatar «S») en resultados y ficha; la ficha muestra «© Open Food Facts contributors (ODbL)». El diario sigue mostrando «Open Food Facts» (se deduce de la clave).
- **Copias de seguridad**: estos productos no se exportan ni se importan (viajan con la app).
- **Licencia del derivado**: el TSV es una base derivada de OFF y se ofrece bajo **ODbL 1.0** (también indicado en README y en su cabecera). El código de la app no se rige por ODbL.
