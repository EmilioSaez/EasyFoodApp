package app.luxion.fitarchiveapp.data.supermarket

import easyfoodapp.shared.generated.resources.Res

/** Lee el recurso embebido `files/supermarket_products.tsv` (ver docs/supermarket-catalog.md). */
suspend fun readEmbeddedSupermarketProducts(): String = Res.readBytes("files/supermarket_products.tsv").decodeToString()
