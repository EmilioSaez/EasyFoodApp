package com.easyfood.data.generic

import easyfoodapp.shared.generated.resources.Res

/** Lee el recurso embebido `files/generic_foods.tsv` (ver docs/generic-foods.md). */
suspend fun readEmbeddedGenericFoods(): String = Res.readBytes("files/generic_foods.tsv").decodeToString()
