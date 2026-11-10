package app.luxion.fitarchiveapp.data.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * Filas representativas de TODAS las tablas con datos de usuario, escritas con el esquema más reciente.
 * [MigrationTestKit.insert] descarta las columnas y tablas que aún no existían en la versión de la base, así que
 * el mismo conjunto sirve para sembrar cualquier versión antigua.
 *
 * Al añadir una tabla o columna nueva con una migración, añade aquí sus filas.
 * Usa ids no consecutivos en las tablas AUTOINCREMENT: así se comprueba que la migración conserva los ids.
 */
object RepresentativeUserData {
    /** Comidas que solo existen desde la v8 (7.sqm); antes el CHECK de DiaryEntryEntity las rechaza. */
    private val mealsSinceV8 = setOf("MID_MORNING", "LATE_SNACK")

    private val rows: List<Pair<String, Map<String, Any?>>> = listOf(
        // Alimentos propios (con y sin opcionales, nombre con acentos/ñ/emoji y comilla).
        "CustomFoodEntity" to mapOf(
            "id" to 3, "name" to "Tortilla de la abuela ñandú 🍳 (d'Ana)", "brand" to "Casa", "kcal" to 190.5,
            "protein_g" to 11.0, "carbs_g" to 2.5, "fat_g" to 14.2, "sugars_g" to 1.1, "saturated_fat_g" to 3.3,
            "fiber_g" to 0.4, "salt_g" to 0.9, "serving_size_g" to 120.0, "serving_label" to "1 ración",
            "source" to "CUSTOM", "created_at" to 1_700_000_000_111, "updated_at" to 1_700_000_005_222,
        ),
        "CustomFoodEntity" to mapOf(
            "id" to 8, "name" to "Batido mínimo", "brand" to null, "kcal" to 0.0, "protein_g" to 0.0,
            "carbs_g" to 0.0, "fat_g" to 0.0, "serving_size_g" to null, "serving_label" to null,
            "source" to "CUSTOM", "created_at" to 1_700_000_100_000, "updated_at" to 1_700_000_100_000,
        ),
        // Caché de productos: uno de Open Food Facts, un genérico en inglés (base antigua) y uno con todo a NULL.
        "ProductEntity" to mapOf(
            "barcode" to "8410000000017", "name" to "Leche entera", "brand" to "Hacendado",
            "image_url" to "https://example.org/leche.jpg", "kcal" to 64.0, "protein_g" to 3.1, "carbs_g" to 4.7,
            "sugars_g" to 4.7, "fat_g" to 3.6, "saturated_fat_g" to 2.3, "fiber_g" to 0.0, "salt_g" to 0.1,
            "serving_size_g" to 250.0, "serving_label" to "1 vaso", "source" to "OPEN_FOOD_FACTS",
            "cached_at" to 1_700_000_200_000,
        ),
        "ProductEntity" to mapOf(
            "barcode" to "generic:1", "name" to "Apples, raw, with skin", "kcal" to 52.0, "protein_g" to 0.26,
            "carbs_g" to 13.8, "fat_g" to 0.17, "source" to "GENERIC", "cached_at" to 1_700_000_300_000,
        ),
        "ProductEntity" to mapOf(
            "barcode" to "5000000000002", "name" to "Producto sin datos", "source" to "OPEN_FOOD_FACTS",
            "cached_at" to 1_700_000_400_000,
        ),
        // Diario: todas las comidas, ambas unidades, nutrientes nulos, alimento genérico y propio.
        diary(2, "2026-11-01", "BREAKFAST", "8410000000017", "Leche entera", 200.0, "GRAMS", 250.0, 64.0, 3.1, 4.7, 3.6),
        diary(5, "2026-11-01", "LUNCH", "generic:1", "Apples, raw, with skin", 1.5, "SERVINGS", 182.0, 52.0, 0.26, 13.8, 0.17),
        diary(6, "2026-11-01", "SNACK", null, "Tortilla de la abuela ñandú 🍳 (d'Ana)", 1.0, "SERVINGS", 120.0, 190.5, 11.0, 2.5, 14.2),
        diary(9, "2026-11-02", "DINNER", null, "Algo sin nutrientes", 80.0, "GRAMS", null, null, null, null, null),
        diary(11, "2026-11-02", "MID_MORNING", "8410000000017", "Leche entera", 100.0, "GRAMS", 250.0, 64.0, 3.1, 4.7, 3.6),
        diary(12, "2026-11-02", "LATE_SNACK", "generic:1", "Apples, raw, with skin", 90.0, "GRAMS", null, 52.0, 0.26, 13.8, 0.17),
        // Entradas rápidas (12.sqm): 100 g con los totales en las columnas por 100 g; con macros a medias y sin nombre propio.
        "DiaryEntryEntity" to mapOf(
            "id" to 14, "date" to "2026-11-03", "meal" to "LUNCH", "barcode" to null, "name" to "Menú del día (d'Ana) 🍝",
            "quantity" to 100.0, "unit" to "GRAMS", "serving_size_g" to null, "kcal_100g" to 850.0, "protein_100g" to 30.0,
            "carbs_100g" to null, "fat_100g" to 40.0, "created_at" to 1_700_001_000_014L, "quick" to 1,
        ),
        "DiaryEntryEntity" to mapOf(
            "id" to 15, "date" to "2026-11-03", "meal" to "DINNER", "barcode" to null, "name" to "Entrada rápida",
            "quantity" to 100.0, "unit" to "GRAMS", "serving_size_g" to null, "kcal_100g" to 500.0, "protein_100g" to null,
            "carbs_100g" to null, "fat_100g" to null, "created_at" to 1_700_001_000_015L, "quick" to 1,
        ),
        // Objetivos diarios (fixed solo existe desde la v4).
        "DailyGoalEntity" to mapOf(
            "id" to 1, "kcal" to 2200, "protein_g" to 150, "carbs_g" to 250, "fat_g" to 70, "fixed" to 1,
        ),
        // Peso: registros y objetivo.
        "WeightEntryEntity" to mapOf("date" to "2026-10-30", "weight_kg" to 82.4, "created_at" to 1, "updated_at" to 2),
        "WeightEntryEntity" to mapOf("date" to "2026-11-02", "weight_kg" to 81.9, "created_at" to 3, "updated_at" to 4),
        "WeightEntryEntity" to mapOf("date" to "2026-11-09", "weight_kg" to 80.05, "created_at" to 5, "updated_at" to 6),
        "WeightGoalEntity" to mapOf("id" to 1, "target_kg" to 75.5, "updated_at" to 7),
        // Recetas con ingredientes (alimento de EAN, genérico y propio sin barcode).
        "RecipeEntity" to mapOf("id" to 4, "name" to "Ensalada de manzana", "servings" to 2, "created_at" to 10, "updated_at" to 11),
        "RecipeEntity" to mapOf("id" to 9, "name" to "Receta sin raciones", "servings" to null, "created_at" to 12, "updated_at" to 12),
        "RecipeIngredientEntity" to mapOf(
            "id" to 6, "recipe_id" to 4, "position" to 0, "barcode" to "generic:1", "name" to "Apples, raw, with skin",
            "grams" to 250.0, "serving_size_g" to null, "kcal_100g" to 52.0, "protein_100g" to 0.26,
            "carbs_100g" to 13.8, "fat_100g" to 0.17,
        ),
        "RecipeIngredientEntity" to mapOf(
            "id" to 7, "recipe_id" to 4, "position" to 1, "barcode" to null, "name" to "Tortilla de la abuela ñandú 🍳 (d'Ana)",
            "grams" to 120.0, "serving_size_g" to 120.0, "kcal_100g" to 190.5, "protein_100g" to 11.0,
            "carbs_100g" to 2.5, "fat_100g" to 14.2,
        ),
        "RecipeIngredientEntity" to mapOf("id" to 8, "recipe_id" to 9, "position" to 0, "name" to "Sal", "grams" to 1.0),
        // Ajustes y versión de la base genérica ya importada (1 = nombres en inglés).
        "SettingsEntity" to mapOf("id" to 1, "meals_per_day" to 5),
        "GenericFoodsMetaEntity" to mapOf("id" to 1, "data_version" to 1),
        // Favoritos: EAN, genérico y propio.
        "FavoriteFoodEntity" to mapOf("key" to "8410000000017", "created_at" to 1_700_000_500_000),
        "FavoriteFoodEntity" to mapOf("key" to "generic:1", "created_at" to 1_700_000_600_000),
        "FavoriteFoodEntity" to mapOf("key" to "custom:3", "created_at" to 1_700_000_700_000),
        // Agua: totales por día y objetivo.
        "WaterEntity" to mapOf("date" to "2026-11-01", "ml" to 1750, "updated_at" to 20),
        "WaterEntity" to mapOf("date" to "2026-11-02", "ml" to 20000, "updated_at" to 21),
        "WaterGoalEntity" to mapOf("id" to 1, "goal_ml" to 2500, "updated_at" to 22),
        // Ejercicio (13.sqm): con y sin duración, nombre con acentos/comilla, varias el mismo día, y el porcentaje.
        "ExerciseEntity" to mapOf(
            "id" to 3, "date" to "2026-11-01", "name" to "Correr ñandú 🏃 (d'Ana)", "kcal" to 420, "minutes" to 40,
            "created_at" to 40,
        ),
        "ExerciseEntity" to mapOf("id" to 6, "date" to "2026-11-01", "name" to "Pesas", "kcal" to 150, "minutes" to null, "created_at" to 41),
        "ExerciseEntity" to mapOf("id" to 10, "date" to "2026-11-02", "name" to "Bici", "kcal" to 10000, "minutes" to 1440, "created_at" to 42),
        "ExerciseSettingsEntity" to mapOf("id" to 1, "percent" to 75, "updated_at" to 43),
        // Seguimiento del refresco de productos de OFF (14.sqm): con cambio relevante y solo consultado.
        "ProductRefreshEntity" to mapOf(
            "barcode" to "8410000000017", "refreshed_at" to 1_700_000_500_000, "checked_at" to 1_700_000_500_000,
            "changed_at" to 1_700_000_500_000, "change_summary" to "Energía 60 → 64 kcal · Grasas 3 → 3,6 g",
        ),
        "ProductRefreshEntity" to mapOf(
            "barcode" to "5000000000002", "refreshed_at" to null, "checked_at" to 1_700_000_600_000,
            "changed_at" to null, "change_summary" to null,
        ),
        // Plantillas de comida (11.sqm): gramos y porciones, alimento con EAN, genérico y propio sin barcode.
        "MealTemplateEntity" to mapOf("id" to 2, "name" to "Mi desayuno de siempre", "created_at" to 30, "updated_at" to 31),
        "MealTemplateEntity" to mapOf("id" to 7, "name" to "Cena ñoña 🍽 (d'Ana)", "created_at" to 32, "updated_at" to 32),
        "MealTemplateItemEntity" to mapOf(
            "id" to 4, "template_id" to 2, "position" to 0, "barcode" to "8410000000017", "name" to "Leche entera",
            "quantity" to 200.0, "unit" to "GRAMS", "serving_size_g" to 250.0, "kcal_100g" to 64.0,
            "protein_100g" to 3.1, "carbs_100g" to 4.7, "fat_100g" to 3.6,
        ),
        "MealTemplateItemEntity" to mapOf(
            "id" to 5, "template_id" to 2, "position" to 1, "barcode" to "generic:1", "name" to "Apples, raw, with skin",
            "quantity" to 1.5, "unit" to "SERVINGS", "serving_size_g" to 182.0, "kcal_100g" to 52.0,
            "protein_100g" to 0.26, "carbs_100g" to 13.8, "fat_100g" to 0.17,
        ),
        "MealTemplateItemEntity" to mapOf(
            "id" to 9, "template_id" to 7, "position" to 0, "barcode" to null, "name" to "Algo sin nutrientes",
            "quantity" to 80.0, "unit" to "GRAMS",
        ),
    )

    private fun diary(
        id: Int, date: String, meal: String, barcode: String?, name: String, quantity: Double, unit: String,
        servingG: Double?, kcal: Double?, protein: Double?, carbs: Double?, fat: Double?,
    ): Pair<String, Map<String, Any?>> = "DiaryEntryEntity" to mapOf(
        "id" to id, "date" to date, "meal" to meal, "barcode" to barcode, "name" to name, "quantity" to quantity,
        "unit" to unit, "serving_size_g" to servingG, "kcal_100g" to kcal, "protein_100g" to protein,
        "carbs_100g" to carbs, "fat_100g" to fat, "created_at" to 1_700_001_000_000L + id,
    )

    /** Siembra [driver] (una base en la [version] indicada) con todas las filas que su esquema admite. */
    fun seed(driver: JdbcSqliteDriver, version: Int) {
        for ((table, row) in rows) {
            if (table == "DiaryEntryEntity" && row["meal"] in mealsSinceV8 && version < 8) continue
            MigrationTestKit.insert(driver, table, row)
        }
    }
}
