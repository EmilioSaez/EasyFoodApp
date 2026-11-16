package app.luxion.easyfoodapp.ai

import app.luxion.easyfoodapp.data.CustomFoodStore
import app.luxion.easyfoodapp.data.DiaryStore
import app.luxion.easyfoodapp.data.FavoriteKeys
import app.luxion.easyfoodapp.data.FavoriteStore
import app.luxion.easyfoodapp.data.GoalStore
import app.luxion.easyfoodapp.data.MealTemplateStore
import app.luxion.easyfoodapp.data.RecipeStore
import app.luxion.easyfoodapp.data.WaterStore
import app.luxion.easyfoodapp.domain.IntakeGuidelines
import app.luxion.easyfoodapp.domain.PeriodRange
import app.luxion.easyfoodapp.domain.PeriodSummaries
import app.luxion.easyfoodapp.domain.RecipeCalculator
import app.luxion.easyfoodapp.domain.SummaryPeriod
import app.luxion.easyfoodapp.model.DiaryEntry
import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.model.Nutrients
import kotlin.math.round

/**
 * Reúne de los almacenes locales el resumen mínimo que se envía a la IA ([AiAdviceContext]). Solo lee; no guarda nada.
 * Los almacenes opcionales (agua, plantillas) pueden faltar: ese dato simplemente no se envía.
 */
class AiAdviceSource(
    private val diary: DiaryStore,
    private val goals: GoalStore,
    private val favorites: FavoriteStore,
    private val customFoods: CustomFoodStore,
    private val recipes: RecipeStore,
    private val templates: MealTemplateStore? = null,
    private val water: WaterStore? = null,
) {
    /** Contexto de [date] para [currentMeal]. [withCandidates] y [withWeek] evitan lecturas que la función no necesita. */
    suspend fun build(
        date: String,
        currentMeal: MealType,
        withCandidates: Boolean = false,
        withWeek: Boolean = false,
        withFoods: Boolean = false,
    ): AiAdviceContext {
        val entries = diary.entriesForDate(date)
        val g = goals.get()
        val totals = entries.map { it.totals }
        val day = AiDaySummary(
            kcal = totals.sumOf { it.kcal ?: 0.0 },
            proteinG = totals.sumOf { it.proteinG ?: 0.0 },
            carbsG = totals.sumOf { it.carbsG ?: 0.0 },
            fatG = totals.sumOf { it.fatG ?: 0.0 },
            goalKcal = g.kcal?.toDouble(),
            goalProteinG = g.proteinG?.toDouble(),
            goalCarbsG = g.carbsG?.toDouble(),
            goalFatG = g.fatG?.toDouble(),
        )
        return AiAdviceContext(
            day = day,
            currentMeal = currentMeal,
            currentMealFoods = entries.filter { it.meal == currentMeal }.take(MAX_MEAL_FOODS).map {
                val kcal = it.totals.kcal?.let { k -> " · ${round(k).toInt()} kcal" }.orEmpty()
                it.name + kcal
            },
            waterMl = water?.total(date) ?: 0,
            waterGoalMl = water?.goalMl(),
            intake = IntakeGuidelines.intake(entries, g.kcal),
            candidates = if (withCandidates) candidates() else AiCandidates(),
            week = if (withWeek) week(date, g.kcal) else null,
            todayFoods = if (withFoods) entries.take(AiAdviceLimits.MAX_TODAY_FOODS).map(::foodLine) else emptyList(),
        )
    }

    private suspend fun candidates(): AiCandidates {
        val limit = AiAdviceLimits.MAX_PER_SOURCE
        val favs = favoriteCandidates().take(limit)
        val taken = favs.map { it.name.lowercase() }.toSet()
        val recents = diary.recentFoods(RECENT_LOOKUP).filter { !it.quick }
            .map(::entryCandidate)
            .filter { it.name.lowercase() !in taken }
            .distinctBy { it.name.lowercase() }
            .take(limit)
        val templateList = templates?.all().orEmpty().take(limit).map { t ->
            val kcal = t.items.mapNotNull { it.totals.kcal }.takeIf { it.size == t.items.size }?.sum()
            AiCandidate(t.name, kcal?.let { round(it).toInt() }, if (t.items.size == 1) "1 alimento" else "${t.items.size} alimentos")
        }
        val recipeList = recipes.all().take(limit).map { r ->
            val perServing = RecipeCalculator.perServing(r.ingredients, r.servings)
            val shown = perServing ?: RecipeCalculator.totals(r.ingredients)
            AiCandidate(r.name, shown.kcal?.let { round(it).toInt() }, if (perServing != null) "1 ración" else "receta entera")
        }
        return AiCandidates(favs, recents, templateList, recipeList)
    }

    private suspend fun favoriteCandidates(): List<AiCandidate> {
        val products = favorites.products().associateBy { it.barcode }
        val custom = customFoods.all().associateBy { it.id }
        return favorites.keys().mapNotNull { key ->
            val customId = FavoriteKeys.customIdOf(key)
            when {
                customId != null -> custom[customId]?.let { portion(it.name, it.nutrients, it.servingSizeG) }
                else -> products[key]?.let { portion(it.name, it.nutrients, it.servingSizeG) }
            }
        }
    }

    private fun portion(name: String, n: Nutrients, servingG: Double?): AiCandidate {
        val grams = servingG ?: 100.0
        return AiCandidate(name, n.kcal?.let { round(it * grams / 100.0).toInt() }, "${gramsText(grams)} g")
    }

    private fun entryCandidate(e: DiaryEntry): AiCandidate =
        AiCandidate(e.name, e.totals.kcal?.let { round(it).toInt() }, e.grams?.let { "${gramsText(it)} g" })

    private suspend fun week(date: String, goalKcal: Int?): AiWeekSummary {
        val range = PeriodRange.of(SummaryPeriod.WEEK, date)
        val entries = diary.entriesBetween(range.start, date)
        val summary = PeriodSummaries.build(range, date, goalKcal, entries, emptyList())
        val perDay = entries.groupBy { it.date }.values.map { IntakeGuidelines.intake(it, goalKcal) }
        fun avg(label: String): Double? =
            perDay.mapNotNull { list -> list.firstOrNull { it.label == label && it.hasData }?.totalG }
                .takeIf { it.isNotEmpty() }?.average()
        return AiWeekSummary(
            loggedDays = summary.loggedDays,
            daysSoFar = summary.days.count { it.date <= date },
            avgKcal = summary.averages?.kcal,
            avgProteinG = summary.averages?.proteinG,
            avgCarbsG = summary.averages?.carbsG,
            avgFatG = summary.averages?.fatG,
            goalKcal = goalKcal,
            completedDays = summary.completedDays,
            avgFiberG = avg("Fibra"),
            avgSaltG = avg("Sal"),
            avgSugarsG = avg("Azúcares"),
            topFoods = entries.filter { !it.quick }.groupingBy { it.name.lowercase() }.eachCount()
                .entries.sortedByDescending { it.value }.take(AiAdviceLimits.MAX_TOP_FOODS)
                .map { e -> entries.first { it.name.lowercase() == e.key }.name },
        )
    }

    private fun foodLine(e: DiaryEntry): String = buildString {
        append(e.name)
        e.grams?.let { append(" · ").append(gramsText(it)).append(" g") }
        e.totals.kcal?.let { append(" · ").append(round(it).toInt()).append(" kcal") }
    }

    private fun gramsText(g: Double): String = if (g == round(g)) round(g).toLong().toString() else (round(g * 10) / 10).toString().replace('.', ',')

    private companion object {
        const val MAX_MEAL_FOODS = 8
        const val RECENT_LOOKUP = 30
    }
}
