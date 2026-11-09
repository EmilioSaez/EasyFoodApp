package app.luxion.fitarchiveapp.steps

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import java.time.Period

private val STEPS_PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

/** Pasos desde Health Connect (permiso de solo lectura `READ_STEPS`). */
private class HealthConnectStepsProvider(
    private val context: Context,
    private val launchPermissions: (Set<String>) -> Unit,
) : StepsProvider {
    private var pending: CompletableDeferred<Set<String>>? = null

    private val client: HealthConnectClient?
        get() = if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null

    fun onPermissionResult(granted: Set<String>) {
        pending?.complete(granted)
        pending = null
    }

    override suspend fun access(): StepsAccess {
        val client = client ?: return StepsAccess.UNAVAILABLE
        val granted = client.permissionController.getGrantedPermissions()
        return if (granted.containsAll(STEPS_PERMISSIONS)) StepsAccess.GRANTED else StepsAccess.NEEDS_PERMISSION
    }

    override suspend fun requestAccess(): StepsAccess {
        if (client == null) return StepsAccess.UNAVAILABLE
        val result = CompletableDeferred<Set<String>>().also { pending = it }
        launchPermissions(STEPS_PERMISSIONS)
        result.await()
        return access()
    }

    override suspend fun dailySteps(fromDate: String, toDate: String): List<DailySteps> {
        val client = client ?: return emptyList()
        val from = LocalDate.parse(fromDate)
        val to = LocalDate.parse(toDate)
        if (to.isBefore(from)) return emptyList()
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return response.mapNotNull { day ->
            val steps = day.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            DailySteps(day.startTime.toLocalDate().toString(), steps)
        }
    }
}

@Composable
actual fun rememberStepsProvider(): StepsProvider {
    val context = LocalContext.current
    // El proveedor se crea antes que el lanzador, que necesita su callback: se enlazan por referencia.
    val holder = remember { arrayOfNulls<HealthConnectStepsProvider>(1) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        holder[0]?.onPermissionResult(granted)
    }
    return remember(context, launcher) {
        HealthConnectStepsProvider(context.applicationContext) { launcher.launch(it) }.also { holder[0] = it }
    }
}
