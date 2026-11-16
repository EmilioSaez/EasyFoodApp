package app.luxion.easyfoodapp.data

import app.luxion.easyfoodapp.db.ProductCorrectionEntity
import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Product
import app.luxion.easyfoodapp.model.ProductCorrection

internal fun ProductCorrectionEntity.toDomain() = ProductCorrection(
    barcode = barcode,
    nutrients = Nutrients(
        kcal = kcal, proteinG = protein_g, carbsG = carbs_g, fatG = fat_g,
        saturatedFatG = saturated_fat_g, sugarsG = sugars_g, fiberG = fiber_g, saltG = salt_g,
    ),
    updatedAtMillis = updated_at,
)

/**
 * [this] con los valores de [correction] en lugar de los suyos, que pasan a [Product.originalNutrients].
 * Sin corrección devuelve el producto tal cual. Nombre, marca y porción no se corrigen.
 */
internal fun Product.withCorrection(correction: ProductCorrection?): Product =
    if (correction == null) this else copy(nutrients = correction.nutrients, originalNutrients = nutrients)
