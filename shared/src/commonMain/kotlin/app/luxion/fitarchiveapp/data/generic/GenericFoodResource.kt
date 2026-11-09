package app.luxion.fitarchiveapp.data.generic

import easyfoodapp.shared.generated.resources.Res

/** Lee el recurso embebido `files/generic_foods.tsv` (ver docs/generic-foods.md). */
suspend fun readEmbeddedGenericFoods(): String = Res.readBytes("files/generic_foods.tsv").decodeToString()

/** Lee el recurso embebido `files/generic_foods_es.tsv`: nombres en español por id (ver docs/generic-foods.md). */
suspend fun readEmbeddedGenericTranslations(): String = Res.readBytes("files/generic_foods_es.tsv").decodeToString()
