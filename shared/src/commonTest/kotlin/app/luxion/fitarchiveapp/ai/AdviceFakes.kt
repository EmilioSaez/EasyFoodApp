package app.luxion.fitarchiveapp.ai

import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.ExerciseStore
import app.luxion.fitarchiveapp.data.FavoriteStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.ExerciseEntry
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.NewExercise
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.Recipe

/** Almacenes en memoria para probar el resumen y la pestaña de IA sin base de datos. */
internal class FakeAdviceDiary(initial: List<DiaryEntry> = emptyList()) : DiaryStore {
    val rows = initial.toMutableList()
    var failing = false
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun entriesForDate(date: String): List<DiaryEntry> {
        if (failing) throw IllegalStateException("db")
        return rows.filter { it.date == date }
    }
    override suspend fun entriesBetween(from: String, to: String) = rows.filter { it.date in from..to }.sortedBy { it.date }
    override suspend fun recentFoods(limit: Int): List<DiaryEntry> =
        rows.sortedByDescending { it.createdAtMillis }.distinctBy { it.name }.take(limit)
    override suspend fun find(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long {
        if (failing) throw IllegalStateException("db")
        val id = nextId++
        rows += DiaryEntry(id, entry.date, entry.meal, entry.barcode, entry.name, entry.quantity, entry.servingSizeG, entry.nutrients, createdAtMillis, entry.quick)
        return id
    }
    override suspend fun update(id: Long, meal: MealType, quantity: Quantity) = false
    override suspend fun delete(id: Long) = rows.removeAll { it.id == id }
}

internal class FakeAdviceGoals(var goals: DailyGoals = DailyGoals()) : GoalStore {
    override suspend fun get() = goals
    override suspend fun save(goals: DailyGoals) { this.goals = goals }
    override suspend fun clear() { goals = DailyGoals() }
}

internal class FakeAdviceFavorites(
    private val keys: List<String> = emptyList(),
    private val products: List<Product> = emptyList(),
) : FavoriteStore {
    override suspend fun keys() = keys
    override suspend fun products() = products
    override suspend fun setFavorite(key: String, favorite: Boolean, nowMillis: Long) = Unit
}

internal class FakeAdviceCustomFoods(private val foods: List<CustomFood> = emptyList()) : CustomFoodStore {
    override suspend fun all() = foods
    override suspend fun find(id: Long) = foods.firstOrNull { it.id == id }
    override suspend fun create(food: NewCustomFood, nowMillis: Long) = error("no usado")
    override suspend fun update(id: Long, food: NewCustomFood, nowMillis: Long) = false
    override suspend fun delete(id: Long) = false
}

internal class FakeAdviceRecipes(private val recipes: List<Recipe> = emptyList()) : RecipeStore {
    override suspend fun all() = recipes
    override suspend fun find(id: Long) = recipes.firstOrNull { it.id == id }
    override suspend fun create(recipe: NewRecipe, nowMillis: Long) = error("no usado")
    override suspend fun update(id: Long, recipe: NewRecipe, nowMillis: Long) = false
    override suspend fun delete(id: Long) = false
}

internal class FakeAdviceTemplates(private val templates: List<MealTemplate> = emptyList()) : MealTemplateStore {
    override suspend fun all() = templates
    override suspend fun find(id: Long) = templates.firstOrNull { it.id == id }
    override suspend fun create(template: NewMealTemplate, nowMillis: Long) = error("no usado")
    override suspend fun update(id: Long, template: NewMealTemplate, nowMillis: Long) = false
    override suspend fun rename(id: Long, name: String, nowMillis: Long) = false
    override suspend fun delete(id: Long) = false
}

internal class FakeAdviceWater(private val ml: Int = 0, private val goal: Int = 2000) : WaterStore {
    override suspend fun total(date: String) = ml
    override suspend fun add(date: String, deltaMl: Int, nowMillis: Long) = ml
    override suspend fun goalMl() = goal
    override suspend fun setGoalMl(ml: Int, nowMillis: Long) = Unit
}

internal class FakeAdviceExercise(private val entries: List<ExerciseEntry> = emptyList(), private val percent: Int = 50) : ExerciseStore {
    override suspend fun entriesForDate(date: String) = entries.filter { it.date == date }
    override suspend fun add(exercise: NewExercise, nowMillis: Long) = 0L
    override suspend fun delete(id: Long) = Unit
    override suspend fun dailyKcal(from: String, to: String) = emptyMap<String, Int>()
    override suspend fun percent() = percent
    override suspend fun setPercent(percent: Int, nowMillis: Long) = Unit
}

internal fun adviceEntry(
    id: Long,
    name: String = "Alimento $id",
    date: String = "2026-11-10",
    meal: MealType = MealType.LUNCH,
    kcal100: Double = 100.0,
    grams: Double = 100.0,
    fiber: Double? = null,
    salt: Double? = null,
    quick: Boolean = false,
) = DiaryEntry(
    id, date, meal, if (quick) null else "84100$id", name, Quantity(grams, QuantityUnit.GRAMS), null,
    Nutrients(kcal100, 10.0, 20.0, 5.0, fiberG = fiber, saltG = salt), createdAtMillis = id, quick = quick,
)
