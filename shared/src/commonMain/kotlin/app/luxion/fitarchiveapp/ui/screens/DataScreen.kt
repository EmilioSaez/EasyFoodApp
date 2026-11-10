package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.backup.BackupInfo
import app.luxion.fitarchiveapp.backup.BackupManager
import app.luxion.fitarchiveapp.export.qr.QrCodeEncoder
import app.luxion.fitarchiveapp.export.qr.QrMatrix
import app.luxion.fitarchiveapp.export.qr.QrTransfer
import app.luxion.fitarchiveapp.files.DataFiles
import app.luxion.fitarchiveapp.files.FileResult
import app.luxion.fitarchiveapp.files.rememberDataFiles
import app.luxion.fitarchiveapp.scanner.BarcodeScanner
import app.luxion.fitarchiveapp.scanner.ScanError
import app.luxion.fitarchiveapp.scanner.ScanMode
import app.luxion.fitarchiveapp.scanner.isBarcodeScannerSupported
import app.luxion.fitarchiveapp.scanner.message
import app.luxion.fitarchiveapp.scanner.rememberAppSettingsOpener
import app.luxion.fitarchiveapp.transfer.LanOffer
import app.luxion.fitarchiveapp.transfer.LanTransferClient
import app.luxion.fitarchiveapp.transfer.LanTransferHost
import app.luxion.fitarchiveapp.transfer.localIpAddresses
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.EanTextStyle
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

private enum class Channel(val label: String) { File("Archivo"), Qr("QR"), Lan("Red local") }

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

/**
 * Datos: exportar e importar JSON, y transferir entre dispositivos por QR o red local.
 * Toda la importación pasa por la misma vista previa de duplicados y fusión antes de confirmar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(navigator: Navigator, presenter: DataPresenter, today: () -> String) {
    val files = rememberDataFiles()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(Channel.File) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Datos") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Volver" },
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                Channel.entries.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = channel == c,
                        onClick = { channel = c },
                        shape = SegmentedButtonDefaults.itemShape(i, Channel.entries.size),
                    ) { Text(c.label) }
                }
            }
            when (channel) {
                Channel.File -> FileSection(presenter, files, today) { snackbar.showSnackbar(it) }
                Channel.Qr -> QrSection(presenter)
                Channel.Lan -> LanSection(presenter)
            }
        }
    }

    ImportDialog(presenter, onConfirm = { scope.launch { presenter.confirm() } })
}

@Composable
private fun SectionCard(title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

// --- Archivo ---

@Composable
private fun FileSection(
    presenter: DataPresenter,
    files: DataFiles,
    today: () -> String,
    showMessage: suspend (String) -> Any,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    SectionCard(
        "Exportar",
        "Guarda todo (productos, alimentos propios y diario) en un archivo JSON. Sirve de copia de seguridad.",
    ) {
        Button(
            enabled = files.canSave && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                busy = true
                scope.launch {
                    val json = presenter.export().getOrElse {
                        busy = false
                        showMessage(it.message ?: "No se pudo exportar.")
                        return@launch
                    }
                    files.save(exportFileName(today()), json) { result ->
                        busy = false
                        scope.launch {
                            when (result) {
                                is FileResult.Done -> showMessage("Guardado: ${result.value}")
                                is FileResult.Failed -> showMessage(result.message)
                                FileResult.Cancelled -> Unit
                            }
                        }
                    }
                }
            },
        ) { Text(if (busy) "Exportando…" else "Guardar archivo JSON") }
        if (!files.canSave) UnavailableNote("En esta plataforma aún no se pueden guardar archivos; usa QR o red local.")
    }

    SectionCard(
        "Importar",
        "Elige un archivo exportado por EasyFood. Antes de añadir nada verás qué es nuevo y qué ya existe; no se borra nada.",
    ) {
        OutlinedButton(
            enabled = files.canOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                files.open { result ->
                    scope.launch {
                        when (result) {
                            is FileResult.Done -> presenter.stage(result.value)
                            is FileResult.Failed -> presenter.fail(result.message)
                            FileResult.Cancelled -> Unit
                        }
                    }
                }
            },
        ) { Text("Elegir archivo…") }
        if (!files.canOpen) UnavailableNote("En esta plataforma aún no se pueden abrir archivos; usa QR o red local.")
    }

    presenter.backups?.let { BackupSection(presenter, it) { msg -> showMessage(msg) } }
}

// --- Copias de seguridad automáticas ---

@Composable
private fun BackupSection(presenter: DataPresenter, backups: BackupManager, showMessage: suspend (String) -> Any) {
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var copies by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(backups, reload) {
        enabled = backups.isEnabled()
        copies = backups.list()
        loaded = true
    }

    SectionCard(
        "Copia de seguridad automática",
        "Una vez por semana, al abrir la app, se guarda una copia en este dispositivo con el mismo formato que la " +
            "exportación. Se conservan las últimas ${BackupManager.DEFAULT_KEEP}. Si borras la app, se pierden con ella: " +
            "guarda también un archivo JSON.",
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Copia semanal automática", style = MaterialTheme.typography.bodyLarge)
                Text(
                    backupSummary(enabled, copies.firstOrNull()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                enabled = loaded,
                onCheckedChange = { value ->
                    enabled = value
                    scope.launch {
                        try {
                            backups.setEnabled(value)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            enabled = !value
                            showMessage("No se pudo cambiar el ajuste.")
                        }
                    }
                },
                modifier = Modifier.semantics { contentDescription = "Copia semanal automática" },
            )
        }
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val info = backups.backupNow()
                        showMessage("Copia guardada (${info.date})")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        showMessage("No se pudo guardar la copia.")
                    } finally {
                        busy = false
                        reload++
                    }
                }
            },
        ) { Text(if (busy) "Copiando…" else "Copiar ahora") }
        if (copies.isNotEmpty()) {
            Text("Copias guardadas", style = MaterialTheme.typography.labelLarge)
            copies.forEach { info ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(info.date, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { scope.launch { presenter.stageBackup(info) } }) { Text("Restaurar") }
                }
            }
            Text(
                "Restaurar muestra antes una vista previa: se añade lo que falte y no se borra nada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Última copia: 2026-11-10» o el motivo por el que no hay. */
internal fun backupSummary(enabled: Boolean, last: BackupInfo?): String = when {
    last != null -> "Última copia: ${last.date}" + if (enabled) "" else " · desactivada"
    enabled -> "Aún no hay copias; se hará la primera al abrir la app."
    else -> "Desactivada · aún no hay copias."
}

@Composable
private fun UnavailableNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.easyFoodColors.warning)

// --- QR ---

@Composable
private fun QrSection(presenter: DataPresenter) {
    val scope = rememberCoroutineScope()
    var frames by remember { mutableStateOf<QrFrames?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val shown = frames
    when {
        shown != null -> QrSender(shown, onClose = { frames = null })
        receiving -> QrReceiver(
            onJson = { json ->
                receiving = false
                scope.launch { presenter.stage(json) }
            },
            onClose = { receiving = false },
        )
        else -> {
            SectionCard(
                "Enviar por QR",
                "Muestra una secuencia de códigos QR que el otro dispositivo escanea con la cámara. Sin red ni archivos.",
            ) {
                Button(
                    enabled = !preparing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = {
                        preparing = true
                        error = null
                        scope.launch {
                            try {
                                val json = presenter.export().getOrThrow()
                                frames = withContext(Dispatchers.Default) {
                                    QrFrames(QrTransfer.split(json, QrFrames.CHUNK_SIZE))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "No se pudieron preparar los códigos."
                            }
                            preparing = false
                        }
                    },
                ) { Text(if (preparing) "Preparando…" else "Mostrar códigos QR") }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            SectionCard(
                "Recibir por QR",
                "Escanea con la cámara los códigos del otro dispositivo, en cualquier orden. Verás la vista previa antes de importar.",
            ) {
                OutlinedButton(
                    enabled = isBarcodeScannerSupported,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = { receiving = true },
                ) { Text("Escanear códigos") }
                if (!isBarcodeScannerSupported) {
                    UnavailableNote("La cámara solo está disponible en Android. En esta plataforma recibe por archivo o red local.")
                }
            }
        }
    }
}

@Composable
private fun QrSender(frames: QrFrames, onClose: () -> Unit) {
    var index by remember(frames) { mutableIntStateOf(0) }
    var playing by remember(frames) { mutableStateOf(true) }
    LaunchedEffect(frames, playing) {
        while (playing && frames.size > 1) {
            delay(1_200)
            index = frames.next(index)
        }
    }
    val matrix = remember(frames, index) { QrCodeEncoder.encode(frames.frames[index]) }

    SectionCard(
        "Enviar por QR",
        "En el otro dispositivo abre Datos → QR → Escanear códigos y apunta a esta pantalla hasta recibirlos todos.",
    ) {
        QrCode(matrix, Modifier.fillMaxWidth().widthIn(max = 360.dp).align(Alignment.CenterHorizontally))
        Text(
            frames.label(index),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (frames.isLarge) {
            Text(
                "Son muchos códigos. Para esta cantidad de datos es más cómodo usar un archivo o la red local.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.easyFoodColors.warning,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(
                onClick = { playing = false; index = frames.previous(index) },
                enabled = frames.size > 1,
                modifier = Modifier.weight(1f),
            ) { Text("Anterior") }
            OutlinedButton(
                onClick = { playing = !playing },
                enabled = frames.size > 1,
                modifier = Modifier.weight(1f),
            ) { Text(if (playing) "Pausar" else "Seguir") }
            OutlinedButton(
                onClick = { playing = false; index = frames.next(index) },
                enabled = frames.size > 1,
                modifier = Modifier.weight(1f),
            ) { Text("Siguiente") }
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Terminar") }
    }
}

/** QR sobre fondo blanco con zona de silencio, aunque el tema sea oscuro (si no, no se lee). */
@Composable
private fun QrCode(matrix: QrMatrix, modifier: Modifier = Modifier) {
    val quiet = 4
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = "Código QR" }) {
        drawRect(Color.White)
        val cell = size.width / (matrix.size + quiet * 2)
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                drawRect(
                    Color.Black,
                    topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                    // Un poco más grande para que no queden líneas finas entre módulos.
                    size = Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

@Composable
private fun QrReceiver(onJson: (String) -> Unit, onClose: () -> Unit) {
    val model = remember { QrReceiveModel() }
    val openSettings = rememberAppSettingsOpener()
    var cameraError by remember { mutableStateOf<ScanError?>(null) }
    var cameraKey by remember { mutableIntStateOf(0) }
    val ui = model.ui

    SectionCard("Recibir por QR", "Apunta la cámara a los códigos del otro dispositivo. El orden da igual.") {
        val error = cameraError
        if (error != null) {
            val denied = error as? ScanError.PermissionDenied
            EmptyState(
                icon = "▣",
                title = if (denied != null) "Sin acceso a la cámara" else "No se pudo abrir la cámara",
                message = error.message(),
                tone = EmptyStateTone.Error,
                primaryAction = if (denied?.permanently == true && openSettings != null) {
                    EmptyStateAction("Abrir ajustes", openSettings)
                } else {
                    EmptyStateAction("Reintentar") { cameraError = null; cameraKey++ }
                },
                secondaryAction = EmptyStateAction("Cancelar", onClose),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.scrim, MaterialTheme.shapes.large),
            ) {
                key(cameraKey) {
                    BarcodeScanner(
                        onBarcode = { text -> model.offer(text)?.let(onJson) },
                        onError = { e -> if (e != ScanError.Unsupported) cameraError = e },
                        modifier = Modifier.fillMaxSize(),
                        mode = ScanMode.Qr,
                    )
                }
            }
            Text(ui.label, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { ui.fraction }, modifier = Modifier.fillMaxWidth())
            ui.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.easyFoodColors.warning)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { model.reset() }, enabled = ui.total > 0) { Text("Empezar de nuevo") }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        }
    }
}

// --- Red local ---

@Composable
private fun LanSection(presenter: DataPresenter) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf<LanTransferHost?>(null) }
    var offer by remember { mutableStateOf<LanOffer?>(null) }
    var ips by remember { mutableStateOf(emptyList<String>()) }
    var offerError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { host?.stop() } }

    val current = offer
    if (current != null) {
        LanOfferCard(
            offer = current,
            ips = ips,
            isOpen = { host?.isOpen == true },
            onStop = { host?.stop(); host = null; offer = null },
        )
    } else {
        SectionCard(
            "Enviar por red local",
            "Los dos dispositivos deben estar en la misma red Wi-Fi. Se muestra una dirección y un código de un solo uso.",
        ) {
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = {
                    offerError = null
                    scope.launch {
                        val h = LanTransferHost(exportJson = presenter::exportJson, nowMillis = ::nowMillis)
                        try {
                            offer = h.start()
                            host = h
                            ips = localIpAddresses()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            h.stop()
                            offerError = "No se pudo abrir la transferencia. Comprueba que estás conectado a una red."
                        }
                    }
                },
            ) { Text("Mostrar código") }
            offerError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
    LanReceiveCard(presenter)
}

@Composable
private fun LanOfferCard(offer: LanOffer, ips: List<String>, isOpen: () -> Boolean, onStop: () -> Unit) {
    var now by remember(offer) { mutableStateOf(nowMillis()) }
    LaunchedEffect(offer) {
        while (true) {
            delay(500)
            now = nowMillis()
        }
    }
    val left = secondsLeft(offer.expiresAtMillis, now)
    val open = isOpen()
    val address = lanAddressLabel(ips, offer.port)

    SectionCard(
        "Enviar por red local",
        if (open) "En el otro dispositivo abre Datos → Red local → Recibir y escribe estos datos."
        else if (left > 0) "La oferta se ha cerrado: se entregaron los datos o hubo demasiados intentos fallidos."
        else "La oferta ha caducado.",
    ) {
        if (open) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Código", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    offer.code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall.merge(EanTextStyle),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Dirección",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                if (address != null) {
                    Text(address, style = MaterialTheme.typography.titleLarge.merge(EanTextStyle))
                    if (ips.size > 1) {
                        Text(
                            "Otras direcciones: " + ips.drop(1).joinToString { "$it:${offer.port}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "No se ha podido detectar la dirección de este dispositivo. Búscala en los ajustes de Wi-Fi y añade el puerto :${offer.port}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.easyFoodColors.warning,
                    )
                }
                Text(
                    "Caduca en $left s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            Text(
                "El tráfico no va cifrado: úsalo solo en una red de confianza.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onStop, modifier = Modifier.align(Alignment.End)) { Text(if (open) "Cancelar" else "Cerrar") }
    }
}

@Composable
private fun LanReceiveCard(presenter: DataPresenter) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(LanForm()) }
    var submitted by remember { mutableStateOf(false) }
    val validation = form.validate()
    val working = presenter.stage is ImportStage.Working

    SectionCard(
        "Recibir por red local",
        "Escribe la dirección y el código que muestra el otro dispositivo. Verás la vista previa antes de importar.",
    ) {
        OutlinedTextField(
            value = form.address,
            onValueChange = { form = form.copy(address = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dirección") },
            placeholder = { Text("192.168.1.20:5050") },
            singleLine = true,
            isError = submitted && validation.addressError != null,
            supportingText = { if (submitted) validation.addressError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = form.code,
            onValueChange = { form = form.copy(code = it.filter(Char::isDigit).take(6)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código") },
            singleLine = true,
            isError = submitted && validation.codeError != null,
            supportingText = { if (submitted) validation.codeError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(
            enabled = !working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                submitted = true
                val request = validation.request ?: return@OutlinedButton
                scope.launch {
                    presenter.stageFrom { LanTransferClient.fetch(request.host, request.port, request.code) }
                }
            },
        ) { Text(if (working) "Conectando…" else "Recibir datos") }
    }
}

// --- Importación: vista previa, resultado y errores (comunes a los tres canales) ---

@Composable
private fun ImportDialog(presenter: DataPresenter, onConfirm: () -> Unit) {
    when (val stage = presenter.stage) {
        ImportStage.Idle -> Unit
        ImportStage.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Procesando…") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
        is ImportStage.Preview -> {
            val preview = stage.preview
            AlertDialog(
                onDismissRequest = presenter::dismiss,
                title = { Text("Vista previa de la importación") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        if (!preview.canConfirm) {
                            Text(
                                "No hay nada nuevo: todo lo del archivo ya está en este dispositivo.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "Esto es lo que pasará al importar. Los datos de este dispositivo no se borran.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ImportRows(preview.rows)
                        if (preview.conflicts > 0) {
                            Text(
                                "En los conflictos se conserva siempre lo que ya tienes aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.easyFoodColors.warning,
                            )
                        }
                        InvalidItems(preview.invalidItems)
                    }
                },
                confirmButton = {
                    if (preview.canConfirm) Button(onClick = onConfirm) { Text("Importar") }
                    else TextButton(onClick = presenter::dismiss) { Text("Cerrar") }
                },
                dismissButton = {
                    if (preview.canConfirm) TextButton(onClick = presenter::dismiss) { Text("Cancelar") }
                },
            )
        }
        is ImportStage.Done -> AlertDialog(
            onDismissRequest = presenter::dismiss,
            title = { Text("Importación terminada") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stage.report.summary(), style = MaterialTheme.typography.bodyMedium)
                    ImportRows(stage.report.rows())
                    InvalidItems(stage.report.invalidItems)
                }
            },
            confirmButton = { Button(onClick = presenter::dismiss) { Text("Listo") } },
        )
        is ImportStage.Failed -> AlertDialog(
            onDismissRequest = presenter::dismiss,
            title = { Text("No se pudo importar") },
            text = { Text(stage.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = presenter::dismiss) { Text("Entendido") } },
        )
    }
}

@Composable
private fun ImportRows(rows: List<ImportPreviewRow>) {
    rows.forEach { row ->
        Column(Modifier.fillMaxWidth()) {
            Text(row.label, style = MaterialTheme.typography.titleMedium)
            Text(row.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InvalidItems(items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Se omitirán por no ser válidos:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        items.take(5).forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (items.size > 5) {
            Text("y ${items.size - 5} más", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
