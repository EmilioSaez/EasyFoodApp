package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.DailyGoals

/** Acceso a los objetivos diarios (la implementación real es [SqlDelightGoalStore]). */
interface GoalStore {
    /** Objetivos guardados; [DailyGoals] vacío si no hay ninguno. */
    suspend fun get(): DailyGoals
    suspend fun save(goals: DailyGoals)
    suspend fun clear()
}
