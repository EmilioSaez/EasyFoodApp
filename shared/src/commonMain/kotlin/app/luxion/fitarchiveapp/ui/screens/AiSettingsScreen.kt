package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ai.AiModels
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiPrivacy
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Ajustes de IA: clave (solo en el dispositivo), activar, modelo, tope diario, prueba de conexión y uso (docs/design.md §2.16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(navigator: Navigator, module: AiModule, today: () -> String) {
    val state = remember(module) { AiSettingsState(module, today) }
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    LaunchedEffect(state) { state.refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes de IA") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // Clave de API
            Section("Clave de API de Anthropic") {
                Text(
                    "Cada persona usa su propia clave. Se guarda solo en este dispositivo, de forma protegida; nunca entra en la base de datos, en exportar/importar ni en copias de seguridad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasKey) {
                    Text("Clave guardada (${state.keyHint})", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(
                        onClick = { scope.launch { state.clearKey(); keyText = "" } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Borrar clave") }
                } else {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { Text("Pega aquí tu clave (sk-ant-…)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { scope.launch { state.saveKey(keyText); if (state.hasKey) keyText = "" } },
                        enabled = keyText.isNotBlank() && !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (state.busy) "Comprobando…" else "Guardar y probar") }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }

            // Activar
            Section("Activar la IA") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = state.settings.enabled && state.hasKey,
                        enabled = state.hasKey,
                        role = Role.Switch,
                        onValueChange = { want ->
                            scope.launch { if (!state.setEnabled(want)) showConsent = true }
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (state.hasKey) "IA activada" else "Necesita una clave",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = state.settings.enabled && state.hasKey, onCheckedChange = null, enabled = state.hasKey)
                }
                TextButton(onClick = { showConsent = true }) { Text("Ver aviso de privacidad") }
            }

            // Modelo
            Section("Modelo") {
                AiModels.options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                            selected = state.settings.model == option.id,
                            role = Role.RadioButton,
                            onClick = { scope.launch { state.setModel(option.id) } },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(selected = state.settings.model == option.id, onClick = null)
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${option.hint} · ${price(option.inputUsdPerMTok)} / ${price(option.outputUsdPerMTok)} $ por millón de tokens (entrada / salida)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Tope diario
            Section("Límite de llamadas al día") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedButton(onClick = { scope.launch { state.adjustLimit(-5) } }, modifier = Modifier.semantics { contentDescription = "Menos llamadas" }) { Text("−") }
                    Text("${state.settings.dailyLimit}", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { scope.launch { state.adjustLimit(5) } }, modifier = Modifier.semantics { contentDescription = "Más llamadas" }) { Text("+") }
                }
            }

            // Prueba de conexión
            Section("Probar conexión") {
                Button(
                    onClick = { scope.launch { state.runTest() } },
                    enabled = state.hasKey && state.test != AiTestResult.Running,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Probar conexión") }
                when (val t = state.test) {
                    null -> Unit
                    AiTestResult.Running -> Text("Comprobando…", style = MaterialTheme.typography.bodyMedium)
                    is AiTestResult.Success -> Text(
                        "Conexión correcta con ${t.info.displayName}. " +
                            "Imágenes: ${if (t.info.supportsImages) "sí" else "no"} · Salida estructurada: ${if (t.info.supportsStructuredOutputs) "sí" else "no"}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is AiTestResult.Failure -> Text(t.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            // Uso
            Section("Uso") {
                Text(
                    "Hoy: ${state.usage.callsToday} de ${state.settings.dailyLimit} llamadas",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Total: ${state.usage.callsTotal} llamadas · gasto estimado ${AiSettingsState.formatUsd(state.usage.estimatedUsd)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Es una estimación con los precios de la tabla; la cifra real es la de tu consola de Anthropic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { scope.launch { state.resetUsage() } }) { Text("Reiniciar contador") }
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(AiPrivacy.TITLE) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Se envía a Anthropic:", style = MaterialTheme.typography.titleSmall)
                    AiPrivacy.sent.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text("No se envía:", style = MaterialTheme.typography.titleSmall)
                    AiPrivacy.notSent.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(AiPrivacy.FOOTER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    if (!state.settings.consentAccepted) scope.launch { state.acceptConsentAndEnable() }
                }) { Text(if (state.settings.consentAccepted) "Cerrar" else "Aceptar") }
            },
            dismissButton = if (state.settings.consentAccepted) null else ({
                TextButton(onClick = { showConsent = false }) { Text("Cancelar") }
            }),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private fun price(v: Double): String = if (v < 1) "0,${(v * 100).toInt().toString().padStart(2, '0')}" else v.toString().removeSuffix(".0").replace('.', ',')
