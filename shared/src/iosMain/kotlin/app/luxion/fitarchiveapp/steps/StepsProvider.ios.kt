package app.luxion.fitarchiveapp.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.HealthKit.HKAuthorizationRequestStatusShouldRequest
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKStatisticsCollectionQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKUnit
import kotlin.coroutines.resume

/**
 * Pasos desde HealthKit (solo lectura). HealthKit no revela si se denegó la lectura: tras pedir el
 * permiso, [access] devuelve GRANTED y, si se denegó, las consultas vuelven vacías.
 */
@OptIn(ExperimentalForeignApi::class)
private class HealthKitStepsProvider : StepsProvider {
    private val store by lazy { HKHealthStore() }
    private val stepType get() = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)

    override suspend fun access(): StepsAccess {
        val type = stepType
        if (!HKHealthStore.isHealthDataAvailable() || type == null) return StepsAccess.UNAVAILABLE
        return suspendCancellableCoroutine { cont ->
            store.getRequestStatusForAuthorizationToShareTypes(emptySet<Any>(), setOf(type)) { status, error ->
                cont.resume(
                    when {
                        error != null -> StepsAccess.UNAVAILABLE
                        status == HKAuthorizationRequestStatusShouldRequest -> StepsAccess.NEEDS_PERMISSION
                        else -> StepsAccess.GRANTED
                    },
                )
            }
        }
    }

    override suspend fun requestAccess(): StepsAccess {
        val type = stepType
        if (!HKHealthStore.isHealthDataAvailable() || type == null) return StepsAccess.UNAVAILABLE
        suspendCancellableCoroutine { cont ->
            store.requestAuthorizationToShareTypes(null, setOf(type)) { _, _ -> cont.resume(Unit) }
        }
        return access()
    }

    override suspend fun dailySteps(fromDate: String, toDate: String): List<DailySteps> {
        val type = stepType ?: return emptyList()
        val start = parseDate(fromDate) ?: return emptyList()
        val end = parseDate(toDate)?.let { calendar.dateByAddingUnit(platform.Foundation.NSCalendarUnitDay, 1, it, 0u) }
            ?: return emptyList()
        val interval = NSDateComponents().apply { day = 1 }
        return suspendCancellableCoroutine { cont ->
            val query = HKStatisticsCollectionQuery(type, null, HKStatisticsOptionCumulativeSum, start, interval)
            query.initialResultsHandler = { _, collection, _ ->
                val days = mutableListOf<DailySteps>()
                collection?.enumerateStatisticsFromDate(start, end) { stats, _ ->
                    val count = stats?.sumQuantity()?.doubleValueForUnit(HKUnit.unitFromString("count"))
                    val date = stats?.startDate?.let { formatDate(it) }
                    if (count != null && date != null) days += DailySteps(date, count.toLong())
                }
                cont.resume(days)
            }
            store.executeQuery(query)
            cont.invokeOnCancellation { store.stopQuery(query) }
        }
    }

    private val calendar get() = NSCalendar.currentCalendar

    private fun parseDate(iso: String): NSDate? {
        val parts = iso.split('-').mapNotNull { it.toLongOrNull() }
        if (parts.size != 3) return null
        val components = NSDateComponents().apply {
            year = parts[0]
            month = parts[1]
            day = parts[2]
        }
        return calendar.dateFromComponents(components)
    }

    private fun formatDate(date: NSDate): String {
        val c = calendar.components(
            platform.Foundation.NSCalendarUnitYear or platform.Foundation.NSCalendarUnitMonth or platform.Foundation.NSCalendarUnitDay,
            date,
        )
        return "${c.year.toString().padStart(4, '0')}-${c.month.toString().padStart(2, '0')}-${c.day.toString().padStart(2, '0')}"
    }
}

@Composable
actual fun rememberStepsProvider(): StepsProvider = remember { HealthKitStepsProvider() }
