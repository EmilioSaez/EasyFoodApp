package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.FavoriteKeys
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.Product

/**
 * «Favoritos»: los alimentos marcados con estrella como filas de resultado, por nombre. Un producto (EAN o genérico)
 * solo aparece si está en la caché local ([products]); un alimento propio, si todavía existe en [customFoods]. Lo que
 * ya no tiene ficha a la que ir se omite (el favorito sigue guardado hasta que se desmarque).
 */
fun favoriteFoodResults(keys: Collection<String>, products: List<Product>, customFoods: List<CustomFood>): List<FoodResult> {
    val wanted = keys.toSet()
    val fromProducts = products.filter { it.barcode in wanted }.map { it.toFoodResult() }
    val fromCustom = customFoods.filter { FavoriteKeys.custom(it.id) in wanted }.map { it.toFoodResult() }
    return (fromProducts + fromCustom).distinctBy { it.key }.sortedBy { it.name.lowercase() }
}
