package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import app.luxion.easyfoodapp.ui.components.EasyFoodFilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.easyfoodapp.ai.AiError
import app.luxion.easyfoodapp.ai.AiModule
import app.luxion.easyfoodapp.ai.AiPrivacy
import app.luxion.easyfoodapp.data.CustomFoodStore
import app.luxion.easyfoodapp.data.DiaryStore
import app.luxion.easyfoodapp.data.ProductSearchStore
import app.luxion.easyfoodapp.photo.PhotoCapture
import app.luxion.easyfoodapp.plate.FoodCatalog
import app.luxion.easyfoodapp.plate.PlateAnalyzer
import app.luxion.easyfoodapp.plate.PlatePresenter
import app.luxion.easyfoodapp.plate.PlateState
import app.luxion.easyfoodapp.ui.components.EmptyState
import app.luxion.easyfoodapp.ui.components.EmptyStateAction
import app.luxion.easyfoodapp.ui.components.EmptyStateTone
import app.luxion.easyfoodapp.ui.components.label
import app.luxion.easyfoodapp.ui.navigation.Navigator
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Foto del plato (docs/design.md §2.21): foto (cámara o imagen) → notas opcionales → la IA detecta los alimentos →
 * revisión editable con kcal y macros de la base → solo al confirmar se registran en el diario.
 * [capture] se puede sustituir en las pruebas, que no tienen cámara ni selector de archivos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatePhotoScreen(
    navigator: Navigator,
    route: Route.PlatePhoto,
    ai: AiModule,
    searchStore: ProductSearchStore?,
    customFoods: CustomFoodStore?,
    diaryStore: DiaryStore,
    today: () -> String,
    mealChoices: MealChoices,
    capture: @Composable (onPhoto: (ByteArray) -> Unit, onError: (String) -> Unit, modifier: Modifier) -> Unit =
        { onPhoto, onError, modifier -> PhotoCapture(onPhoto, onError, modifier) },
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(ai, searchStore, customFoods, diaryStore) {
        PlatePresenter(
            ai.service, PlateAnalyzer(ai.service), FoodCatalog(searchStore, customFoods), diaryStore,
            aliases = ai.aliases, startInText = route.text,
        )
    }
    // La foto solo vive en memoria: se descarta al salir de la pantalla.
    DisposableEffect(presenter) { onDispose { presenter.discardPhoto() } }
    val state by presenter.state.collectAsState()
    // `textMode` no es observable: se lee tras `state` (que sí lo es) para que la barra superior y los textos se actualicen al cambiar de modo.
    val textMode = state.let { presenter.textMode }
    var notes by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(route.meal ?: mealChoices.defaultMeal()) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val date = route.date ?: today()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (textMode) "Apuntar por texto" else "Foto del plato") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is PlateState.Blocked -> EmptyState(
                    icon = "✦",
                    title = blockedTitle(s.error),
                    message = s.error.message,
                    tone = if (s.error is AiError.LimitReached) EmptyStateTone.Warning else EmptyStateTone.Neutral,
                    primaryAction = EmptyStateAction("Ajustes de IA") { navigator.push(Route.AiSettings) },
                    secondaryAction = EmptyStateAction("Volver") { navigator.pop() },
                    modifier = Modifier.align(Alignment.Center),
                )
                PlateState.Capture -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "Haz una foto del plato. Se envía a Anthropic reducida a 1024 px y sin metadatos, y no se guarda en el dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                    TextButton(
                        onClick = { presenter.startText() },
                        modifier = Modifier.padding(horizontal = Spacing.s),
                    ) { Text("Prefiero escribir lo que he comido") }
                    captureError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.l))
                    }
                    capture(
                        { bytes ->
                            captureError = null
                            scope.launch { presenter.onPhoto(bytes) }
                        },
                        { captureError = it },
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                PlateState.TextInput -> TextStep(
                    text = description,
                    onText = { description = it.take(PlateAnalyzer.MAX_TEXT) },
                    onAnalyze = { scope.launch { presenter.analyzeText(description) } },
                    onPhoto = if (route.text) null else ({ presenter.retake() }),
                )
                PlateState.Ready -> ReadyStep(
                    notes = notes,
                    onNotes = { notes = it.take(PlateAnalyzer.MAX_NOTES) },
                    onAnalyze = { scope.launch { presenter.analyze(notes) } },
                    onRetake = { presenter.retake() },
                )
                PlateState.Analyzing -> Column(
                    Modifier.align(Alignment.Center).padding(Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    CircularProgressIndicator()
                    Text(if (textMode) "Leyendo lo que has comido…" else "Mirando tu plato…", style = MaterialTheme.typography.titleMedium)
                }
                is PlateState.NoFood -> EmptyState(
                    icon = "?",
                    title = if (textMode) "No veo comida en el texto" else "No veo comida en la foto",
                    message = s.note ?: if (textMode) "Descríbela con alimentos y, si puedes, cantidades («200 g de arroz y un huevo»)." else "Prueba con otra foto con el plato bien visible y buena luz.",
                    primaryAction = if (textMode) EmptyStateAction("Cambiar el texto") { presenter.editText() } else EmptyStateAction("Otra foto") { presenter.retake() },
                    modifier = Modifier.align(Alignment.Center),
                )
                is PlateState.Failed -> EmptyState(
                    icon = "!",
                    title = if (textMode) "No se pudo leer la descripción" else "No se pudo analizar el plato",
                    message = s.message,
                    tone = EmptyStateTone.Error,
                    primaryAction = if (s.retryable) EmptyStateAction("Reintentar") {
                        scope.launch { if (textMode) presenter.analyzeText(presenter.lastDescription) else presenter.analyze(notes) }
                    } else null,
                    secondaryAction = if (textMode) EmptyStateAction("Cambiar el texto") { presenter.editText() } else EmptyStateAction("Otra foto") { presenter.retake() },
                    modifier = Modifier.align(Alignment.Center),
                )
                is PlateState.Review -> PlateReviewContent(
                    state = s,
                    presenter = presenter,
                    meal = meal,
                    onMeal = { meal = it },
                    mealChoices = mealChoices,
                    date = date,
                    modelId = ai.settings.load().model,
                    onConfirm = {
                        scope.launch { if (presenter.confirm(date, meal)) navigator.popToDiary() }
                    },
                )
            }
        }
    }
}

private fun blockedTitle(e: AiError): String = when (e) {
    AiError.NoKey -> "Configura tu clave"
    AiError.Disabled -> "La IA está desactivada"
    AiError.ConsentRequired -> AiPrivacy.TITLE
    is AiError.LimitReached -> "Tope diario alcanzado"
    else -> "IA no disponible"
}

/** Paso de «Apuntar por texto»: una frase con lo que se ha comido; [onPhoto] (si procede) vuelve a la foto. */
@Composable
private fun TextStep(text: String, onText: (String) -> Unit, onAnalyze: () -> Unit, onPhoto: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text("¿Qué has comido?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Escríbelo como lo dirías: «2 huevos revueltos, una tostada con aceite y un café con leche». " +
                "Con cantidades sale más exacto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Lo que has comido") },
            minLines = 3,
            supportingText = { Text("${text.length}/${PlateAnalyzer.MAX_TEXT}") },
            shape = MaterialTheme.shapes.extraSmall,
        )
        Text(
            "Se enviará a Anthropic solo este texto. Nada más del diario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAnalyze, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Buscar los alimentos") }
        onPhoto?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Mejor con una foto") } }
    }
}

@Composable
private fun ReadyStep(notes: String, onNotes: (String) -> Unit, onAnalyze: () -> Unit, onRetake: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text("Foto lista", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Si quieres afinar, di algo que ayude: una cantidad conocida («con 200 g de arroz») o un objeto de referencia " +
                "(«el plato mide 26 cm», «junto a una cuchara sopera»).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notas (opcional)") },
            minLines = 2,
            shape = MaterialTheme.shapes.extraSmall,
        )
        Text(
            "Se enviará a Anthropic la foto y estas notas. Nada más del diario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) { Text("Analizar el plato") }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("Otra foto") }
    }
}

/** Selector de comida de la revisión. */
@Composable
internal fun MealChips(meal: app.luxion.easyfoodapp.model.MealType, mealChoices: MealChoices, onMeal: (app.luxion.easyfoodapp.model.MealType) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        mealChoices.options(meal).forEach { m ->
            EasyFoodFilterChip(selected = meal == m, onClick = { onMeal(m) }, label = { Text(m.label) })
        }
    }
}
