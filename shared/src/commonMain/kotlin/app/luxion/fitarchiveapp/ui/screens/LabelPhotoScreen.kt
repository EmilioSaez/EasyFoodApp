package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ai.AiError
import app.luxion.fitarchiveapp.ai.AiResult
import app.luxion.fitarchiveapp.ai.AiService
import app.luxion.fitarchiveapp.ai.LabelReader
import app.luxion.fitarchiveapp.ai.downscaleToJpeg
import app.luxion.fitarchiveapp.files.PhotoResult
import app.luxion.fitarchiveapp.files.PhotoSource
import app.luxion.fitarchiveapp.files.rememberPhotoSource
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Qué se ve en la pantalla de foto de etiqueta. La foto vive solo en memoria y se conserva mientras haya un error. */
internal sealed interface LabelPhotoState {
    /** Sin foto todavía; [notice] explica por qué no hay (cámara cancelada o con error). */
    data class Idle(val notice: String? = null) : LabelPhotoState

    data class Reading(val photo: ByteArray) : LabelPhotoState

    data class Failed(val photo: ByteArray, val message: String, val retryable: Boolean) : LabelPhotoState
}

/**
 * Alta de un alimento propio fotografiando su etiqueta (docs/design.md §2.17). La IA lee el nombre, los ingredientes y
 * la tabla nutricional; el resultado abre el formulario de alimento propio ya relleno para que se confirme o corrija.
 * Sin IA lista (sin clave, desactivada, sin aviso o con el tope diario alcanzado) no se pide ni la foto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPhotoScreen(
    navigator: Navigator,
    route: Route.LabelPhoto,
    ai: AiService,
    photoSource: PhotoSource = rememberPhotoSource(),
) {
    val scope = rememberCoroutineScope()
    val reader = remember(ai) { LabelReader(ai) }
    var state by remember { mutableStateOf<LabelPhotoState>(LabelPhotoState.Idle()) }
    val blocker = if (state is LabelPhotoState.Idle) ai.blocker() else null

    fun manual() = navigator.replace(Route.CustomFood(route.barcode, route.meal))

    fun read(photo: ByteArray) {
        state = LabelPhotoState.Reading(photo)
        scope.launch {
            val result = try {
                reader.read(photo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiResult.Err(AiError.BadResponse("error inesperado"))
            }
            when (result) {
                is AiResult.Ok -> {
                    val label = result.value
                    if (label.isEmpty) {
                        state = LabelPhotoState.Failed(
                            photo,
                            "No se ha podido leer ningún dato. ¿Se ve la tabla nutricional completa y con buena luz?",
                            retryable = false,
                        )
                    } else {
                        navigator.replace(
                            Route.CustomFood(
                                barcode = route.barcode,
                                meal = route.meal,
                                prefill = label.toFormState(),
                                brand = label.brand,
                                unreadFields = label.unreadFields,
                            ),
                        )
                    }
                }
                is AiResult.Err -> state = LabelPhotoState.Failed(photo, result.error.message, result.error.retryable)
            }
        }
    }

    fun onPhoto(result: PhotoResult) {
        when (result) {
            is PhotoResult.Done -> read(result.bytes)
            PhotoResult.Cancelled -> Unit
            is PhotoResult.Failed -> state = LabelPhotoState.Idle(result.message)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Foto de la etiqueta") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Cerrar" },
                    ) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            when (val s = state) {
                is LabelPhotoState.Idle -> {
                    if (blocker != null) {
                        Notice(blocker.message, isError = true)
                        if (blocker !is AiError.LimitReached) {
                            Button(onClick = { navigator.push(Route.AiSettings) }, Modifier.fillMaxWidth()) { Text("Ajustes de IA") }
                        }
                    } else {
                        Text("Fotografía la etiqueta", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            "La IA lee el nombre, los ingredientes y la tabla nutricional por 100 g. Después lo revisas y lo " +
                                "corriges antes de guardarlo como alimento propio" +
                                (route.barcode?.let { " con el código $it." } ?: "."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "La foto se envía a Anthropic con tu clave, reducida a 1024 px y sin metadatos. No se guarda en el dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        s.notice?.let { Notice(it, isError = true) }
                        if (photoSource.canTakePhoto) {
                            Button(onClick = { photoSource.takePhoto(::onPhoto) }, Modifier.fillMaxWidth()) { Text("Hacer foto") }
                        }
                        if (photoSource.canChooseFile) {
                            val label = if (photoSource.canTakePhoto) "Elegir imagen" else "Subir imagen"
                            val mod = Modifier.fillMaxWidth()
                            if (photoSource.canTakePhoto) {
                                OutlinedButton(onClick = { photoSource.chooseFile(::onPhoto) }, mod) { Text(label) }
                            } else {
                                Button(onClick = { photoSource.chooseFile(::onPhoto) }, mod) { Text(label) }
                            }
                        }
                    }
                    OutlinedButton(onClick = ::manual, Modifier.fillMaxWidth()) { Text("Rellenar a mano") }
                }
                is LabelPhotoState.Reading -> {
                    PhotoPreview(s.photo)
                    Text("Leyendo la etiqueta…", style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is LabelPhotoState.Failed -> {
                    PhotoPreview(s.photo)
                    Notice(s.message, isError = true)
                    if (s.retryable) Button(onClick = { read(s.photo) }, Modifier.fillMaxWidth()) { Text("Reintentar") }
                    if (photoSource.canTakePhoto || photoSource.canChooseFile) {
                        OutlinedButton(onClick = { state = LabelPhotoState.Idle() }, Modifier.fillMaxWidth()) { Text("Probar con otra foto") }
                    }
                    OutlinedButton(onClick = ::manual, Modifier.fillMaxWidth()) { Text("Rellenar a mano") }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, isError: Boolean) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().background(container, MaterialTheme.shapes.medium).padding(Spacing.l),
    )
}

/** Miniatura de la foto enviada (reducida a 512 px; la original no se retiene más que para reintentar). */
@Composable
private fun PhotoPreview(photo: ByteArray) {
    val bitmap = remember(photo) {
        try {
            (downscaleToJpeg(photo, maxSide = 512) ?: photo).decodeToImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Foto de la etiqueta",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        )
    } else {
        Text("Foto de la etiqueta cargada", style = MaterialTheme.typography.bodyMedium)
    }
}
