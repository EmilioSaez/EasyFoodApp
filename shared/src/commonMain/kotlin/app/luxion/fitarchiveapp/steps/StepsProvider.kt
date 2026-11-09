package app.luxion.fitarchiveapp.steps

import androidx.compose.runtime.Composable

/** Pasos de un día de calendario (`date` ISO `YYYY-MM-DD`). */
data class DailySteps(val date: String, val steps: Long)

/** Estado del acceso a los pasos del sensor. */
enum class StepsAccess {
    /** La plataforma no ofrece pasos (escritorio, o Health Connect ausente en Android). */
    UNAVAILABLE,

    /** Disponible, pero el usuario aún no ha concedido el permiso de lectura. */
    NEEDS_PERMISSION,

    /** Se pueden leer los pasos. */
    GRANTED,
}

/** Origen de los pasos del sensor: Health Connect (Android), HealthKit (iOS) o nada (escritorio). */
interface StepsProvider {
    suspend fun access(): StepsAccess

    /** Muestra el diálogo del sistema y devuelve el estado resultante. */
    suspend fun requestAccess(): StepsAccess

    /** Pasos por día entre [fromDate] y [toDate] (ambos incluidos); los días sin datos no aparecen. */
    suspend fun dailySteps(fromDate: String, toDate: String): List<DailySteps>
}

/** Implementación sin sensor: siempre no disponible. */
object UnavailableStepsProvider : StepsProvider {
    override suspend fun access() = StepsAccess.UNAVAILABLE
    override suspend fun requestAccess() = StepsAccess.UNAVAILABLE
    override suspend fun dailySteps(fromDate: String, toDate: String): List<DailySteps> = emptyList()
}

/** El proveedor de la plataforma; en Android necesita estar en la composición para pedir el permiso. */
@Composable
expect fun rememberStepsProvider(): StepsProvider
