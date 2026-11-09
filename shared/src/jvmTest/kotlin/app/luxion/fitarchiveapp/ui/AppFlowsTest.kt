package app.luxion.fitarchiveapp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.ProductLookup
import app.luxion.fitarchiveapp.data.ProductRemoteSource
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightRecipeStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlin.test.Test

/** La [App] completa sobre una base SQLite en memoria y un Open Food Facts simulado. */
@OptIn(ExperimentalTestApi::class)
class AppFlowsTest {
    private val nutella = Product(
        barcode = "3017620422003", name = "Nutella", brand = "Ferrero", imageUrl = null,
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9, 10.6, 56.3, null, 0.107),
        servingSizeG = 15.0, servingLabel = "15 g",
    )

    private val remote = object : ProductRemoteSource {
        override suspend fun findByBarcode(barcode: String) =
            if (barcode == nutella.barcode) ProductLookup.Found(nutella) else ProductLookup.NotFound
    }

    /** [recentWeight]: guarda un peso de hoy para que el aviso semanal no ocupe sitio en el Diario. */
    private fun runApp(recentWeight: Boolean = false, block: ComposeUiTest.(EasyFoodDatabase) -> Unit) = runComposeUiTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        if (recentWeight) {
            kotlinx.coroutines.runBlocking {
                SqlDelightWeightStore(db.weightEntryQueries).save(NewWeightEntry("2026-11-04", 80.0), 1L)
            }
        }
        setContent {
            App(
                repository = ProductRepository(remote, SqlDelightProductStore(db.productQueries), nowMillis = System::currentTimeMillis),
                diaryStore = SqlDelightDiaryStore(db.diaryEntryQueries),
                goalStore = SqlDelightGoalStore(db.dailyGoalQueries),
                searchStore = SqlDelightProductSearchStore(db.productSearchQueries),
                customFoodStore = SqlDelightCustomFoodStore(db.customFoodQueries),
                weightStore = SqlDelightWeightStore(db.weightEntryQueries),
                recipeStore = SqlDelightRecipeStore(db.recipeQueries),
                importTarget = SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries),
                today = { "2026-11-04" },
            )
        }
        try { block(db) } finally { driver.close() }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.addNutellaByManualEan() {
        onNodeWithText("+ Añadir alimento").performClick()
        onNodeWithText("Escáner").performClick()
        onNodeWithText("Código EAN").performTextInput(nutella.barcode)
        onNodeWithText("Buscar producto").performClick()
        waitForText("Añadir a Snacks")
        onNodeWithText("Añadir a Snacks").performClick()
        waitGone("Añadir a Snacks")
    }

    @Test
    fun manualEanAddsEntryToDiary() = runApp(recentWeight = true) { _ ->
        waitForText("Nada apuntado hoy")
        addNutellaByManualEan()
        waitForText("Nutella")
        waitGone("Nada apuntado hoy")
        onNodeWithText("Nutella").assertIsDisplayed()
    }

    @Test
    fun entryCanBeEditedAndDeleted() = runApp(recentWeight = true) { _ ->
        waitForText("Nada apuntado hoy")
        addNutellaByManualEan()
        waitForText("Nutella")

        onNodeWithText("Nutella").performClick()
        waitForText("Guardar cambios")
        onNodeWithText("Guardar cambios").performClick()
        waitGone("Guardar cambios")
        waitForText("Nutella")

        onNodeWithText("Nutella").performClick()
        waitForText("Borrar")
        onNodeWithText("Borrar").performClick()
        waitForText("Nada apuntado hoy")
    }

    @Test
    fun tdeeGoalsAreCalculatedAndSaved() = runApp { db ->
        waitForText("Nada apuntado hoy")
        onNodeWithText("Objetivos").performClick()
        waitForText("Tus datos")
        onNodeWithText("Edad").performTextInput("30")
        onNodeWithText("Peso").performTextInput("80")
        onNodeWithText("Altura").performTextInput("180")
        onNodeWithText("Guardar objetivos").performClick()
        waitUntil(timeoutMillis = 20_000) { SqlDelightGoalStore(db.dailyGoalQueries).let { kotlinx.coroutines.runBlocking { it.get().kcal } } != null }
        val goals = kotlinx.coroutines.runBlocking { SqlDelightGoalStore(db.dailyGoalQueries).get() }
        // Mifflin-St Jeor, hombre 30 a / 80 kg / 180 cm: BMR 1780 × 1,55 (actividad moderada).
        kotlin.test.assertEquals(2759, goals.kcal!!)
    }

    @Test
    fun recipeIsCreatedFromSearchedIngredientsAndAddedToDiaryAsFoods() = runApp { db ->
        kotlinx.coroutines.runBlocking {
            SqlDelightCustomFoodStore(db.customFoodQueries)
                .create(NewCustomFood("Pan casero", null, Nutrients(250.0, 8.0, 50.0, 2.0)), 1L)
        }
        waitForText("Nada apuntado hoy")
        onNodeWithText("Recetas").performClick()
        waitForText("Aún no tienes recetas")
        onNodeWithText("Crear receta").performClick()
        waitForText("Nueva receta")

        // Guardar sin ingredientes no crea nada y avisa.
        onNodeWithText("Guardar receta").performClick()
        waitForText("Escribe un nombre")
        waitForText("Añade al menos un ingrediente")

        onNodeWithText("Nombre").performTextInput("Tostada")
        onNodeWithText("Raciones (opcional)").performTextInput("2")
        onNodeWithText("+ Añadir ingrediente").performClick()
        onNodeWithText("Buscar alimento").performTextInput("pan")
        waitForText("Pan casero")
        onNodeWithText("Pan casero").performClick()
        waitForText("Cantidad")
        onNodeWithText("Cantidad").performTextReplacement("200")
        onNodeWithText("Añadir").performClick()
        waitForText("200 g · 500 kcal")
        onNodeWithText("Guardar receta").performClick()

        waitForText("1 ingrediente · 2 raciones")
        val recipes = kotlinx.coroutines.runBlocking { SqlDelightRecipeStore(db.recipeQueries).all() }
        kotlin.test.assertEquals(listOf("Tostada"), recipes.map { it.name })
        kotlin.test.assertEquals(200.0, recipes.single().ingredients.single().grams)

        // Detalle: una ración son 250 kcal; se añade una (la mitad) al diario, una entrada por ingrediente.
        onNodeWithText("Tostada").performClick()
        waitForText("Por ración · 1 de 2")
        onNodeWithText("Añadir a Comida").performClick()
        waitForText("Pan casero")
        val entries = kotlinx.coroutines.runBlocking { SqlDelightDiaryStore(db.diaryEntryQueries).entriesForDate("2026-11-04") }
        kotlin.test.assertEquals(listOf("Pan casero"), entries.map { it.name })
        kotlin.test.assertEquals(100.0, entries.single().grams)
        kotlin.test.assertEquals(MealType.LUNCH, entries.single().meal)
        kotlin.test.assertNull(entries.single().barcode)
    }

    private fun weights(db: EasyFoodDatabase) =
        kotlinx.coroutines.runBlocking { SqlDelightWeightStore(db.weightEntryQueries).all() }

    private fun goal(db: EasyFoodDatabase) =
        kotlinx.coroutines.runBlocking { SqlDelightWeightStore(db.weightEntryQueries).goal() }?.targetKg

    /** El historial es una lista perezosa: hay que desplazarla hasta la fila para que exista. */
    private fun ComposeUiTest.scrollToRow(description: String) =
        onNode(hasScrollAction()).performScrollToNode(hasContentDescription(description))

    private fun ComposeUiTest.openWeight() {
        waitForText("Nada apuntado hoy")
        onNodeWithText("Mi peso").performClick()
        waitForText("Registrar peso")
    }

    @Test
    fun weightCanBeRecordedEditedAndDeleted() = runApp { db ->
        openWeight()
        waitForText("Aún no has registrado tu peso. Guarda el primero arriba para ver cómo evoluciona.")

        // Alta con coma decimal y fecha por defecto (hoy).
        onNodeWithText("Peso").performTextInput("78,46")
        onNodeWithText("Guardar peso").performClick()
        waitForText("Peso guardado")
        kotlin.test.assertEquals(listOf("2026-11-04" to 78.5), weights(db).map { it.date to it.weightKg })
        waitForText("78,5 kg")
        scrollToRow("Editar peso del 2026-11-04")
        onNodeWithContentDescription("Editar peso del 2026-11-04").assertIsDisplayed()

        // Segundo registro con punto decimal y fecha anterior: la gráfica ya calcula la diferencia.
        onNodeWithText("Fecha").performTextReplacement("2026-10-30")
        onNodeWithText("Peso").performTextInput("80.0")
        onNodeWithText("Guardar peso").performClick()
        waitUntil(timeoutMillis = 20_000) { weights(db).size == 2 }
        waitForText("−1,5 kg desde el 30 oct")

        // Editar el peso más reciente.
        scrollToRow("Editar peso del 2026-11-04")
        onNodeWithContentDescription("Editar peso del 2026-11-04").performClick()
        onNode(hasScrollAction()).performScrollToIndex(0) // el formulario queda arriba
        waitForText("Editar registro")
        onNodeWithText("Peso").performTextReplacement("77,0")
        onNodeWithText("Guardar cambios").performClick()
        waitForText("Registro actualizado")
        kotlin.test.assertEquals(77.0, weights(db).last().weightKg)

        // Cancelar el borrado no borra; confirmarlo sí. (El aviso anterior tapa la última fila hasta que se va.)
        waitGone("Registro actualizado")
        scrollToRow("Borrar peso del 2026-10-30")
        onNodeWithContentDescription("Borrar peso del 2026-10-30").performClick()
        waitForText("¿Borrar este registro?")
        onNodeWithText("Cancelar").performClick()
        waitGone("¿Borrar este registro?")
        kotlin.test.assertEquals(2, weights(db).size)
        onNodeWithContentDescription("Borrar peso del 2026-10-30").performClick()
        waitForText("¿Borrar este registro?")
        onNodeWithText("Borrar").performClick()
        waitUntil(timeoutMillis = 20_000) { weights(db).size == 1 }
    }

    @Test
    fun weightReminderLeadsToWeightScreenAndDisappearsOnceRegistered() = runApp { db ->
        waitForText("Toca pesarse")
        waitForText("Aún no has registrado tu peso.")
        onNodeWithText("Registrar peso").performClick()
        waitForText("Guardar peso")
        onNodeWithText("Peso").performTextInput("78,5")
        onNodeWithText("Guardar peso").performClick()
        waitForText("Peso guardado")
        kotlin.test.assertEquals(1, weights(db).size)

        onNodeWithText("Diario").performClick()
        waitForText("Nada apuntado hoy")
        kotlin.test.assertTrue(onAllNodes(hasText("Toca pesarse")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun weightReminderShowsDaysSinceLastOnlyFromSevenDays() = runApp { db ->
        kotlinx.coroutines.runBlocking {
            SqlDelightWeightStore(db.weightEntryQueries).save(NewWeightEntry("2026-10-28", 80.0), 1L) // 7 días
        }
        // La pantalla ya se compuso vacía; reabrirla desde otra pestaña fuerza la relectura.
        waitForText("Nada apuntado hoy")
        onNodeWithText("Objetivos").performClick()
        onNodeWithText("Diario").performClick()
        waitForText("Hace 7 días que no registras tu peso.")

        kotlinx.coroutines.runBlocking {
            SqlDelightWeightStore(db.weightEntryQueries).save(NewWeightEntry("2026-11-01", 79.0), 2L) // 3 días
        }
        onNodeWithText("Objetivos").performClick()
        onNodeWithText("Diario").performClick()
        waitForText("Nada apuntado hoy")
        waitGone("Toca pesarse")
    }

    @Test
    fun invalidWeightAndFutureDateAreRejected()= runApp { db ->
        openWeight()
        onNodeWithText("Guardar peso").performClick()
        waitForText("Escribe tu peso")
        onNodeWithText("Peso").performTextInput("5")
        waitForText("Debe estar entre 20 y 500 kg")
        onNodeWithText("Peso").performTextReplacement("80")
        onNodeWithText("Fecha").performTextReplacement("2026-11-05")
        waitForText("La fecha no puede ser futura")
        onNodeWithText("Guardar peso").performClick()
        kotlin.test.assertEquals(emptyList(), weights(db))
    }

    @Test
    fun weightRangesAndExistingRecordsAreShown() = runApp { db ->
        kotlinx.coroutines.runBlocking {
            val s = SqlDelightWeightStore(db.weightEntryQueries)
            s.save(NewWeightEntry("2026-06-01", 90.0), 1L)
            s.save(NewWeightEntry("2026-10-20", 82.0), 2L)
            s.save(NewWeightEntry("2026-11-04", 80.0), 3L)
        }
        openWeight()
        waitForText("−2,0 kg desde el 20 oct") // 30 días
        onNodeWithText("Todo").performClick()
        waitForText("−10,0 kg desde el 1 jun")
        onNodeWithText("90 días").performClick()
        waitForText("−2,0 kg desde el 20 oct")
        onNodeWithContentDescription("Gráfica de peso: 2 registros, de 20 oct a 4 nov, último 80,0 kg").assertIsDisplayed()
        scrollToRow("Editar peso del 2026-06-01")
        onNodeWithContentDescription("Editar peso del 2026-06-01").assertIsDisplayed() // el historial lo muestra todo
    }

    @Test
    fun weightGoalCanBeSetChangedAndRemoved() = runApp { db ->
        openWeight()
        waitForText("Peso objetivo")
        onNodeWithText("Objetivo").performTextInput("75,5")
        onNodeWithText("Guardar objetivo").performClick()
        waitForText("Peso objetivo guardado")
        kotlin.test.assertEquals(75.5, goal(db))
        waitForText("75,5 kg")
        waitForText("Registra tu peso para ver cuánto te falta.")

        onNodeWithText("Cambiar").performClick()
        onNodeWithText("Objetivo").performTextReplacement("74")
        onNodeWithText("Guardar objetivo").performClick()
        waitUntil(timeoutMillis = 20_000) { goal(db) == 74.0 }

        waitGone("Peso objetivo guardado")
        onNodeWithText("Quitar").performClick()
        waitUntil(timeoutMillis = 20_000) { goal(db) == null }
        waitForText("Guardar objetivo")
    }

    @Test
    fun goalIsTheSameOnEveryDayAndChangingItUpdatesAllOfThem() = runApp { db ->
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val goals = SqlDelightGoalStore(db.dailyGoalQueries)
        val oneHundredKcal = Nutrients(100.0, 1.0, 10.0, 1.0)
        kotlinx.coroutines.runBlocking {
            for ((i, date) in listOf("2026-11-03", "2026-11-04", "2026-11-05").withIndex()) {
                diary.add(
                    NewDiaryEntry(
                        date, MealType.LUNCH, null, "Comida $i", Quantity(100.0, QuantityUnit.GRAMS), null, oneHundredKcal,
                    ),
                    createdAtMillis = i.toLong(),
                )
            }
            goals.save(DailyGoals(kcal = 2000))
        }

        fun ComposeUiTest.openDiary() {
            // En Objetivos el texto también es el título: la pestaña es el último nodo.
            onAllNodesWithText("Objetivos").onLast().performClick()
            waitForText("Tus datos")
            onNodeWithText("Diario").performClick()
        }

        fun ComposeUiTest.assertGoalOnThreeDays(goal: String) {
            val summary = "100 / $goal kcal"
            openDiary()
            waitForText(summary) // hoy
            onNodeWithContentDescription("Día anterior").performClick()
            waitForText("Comida 0")
            waitForText(summary)
            onNodeWithContentDescription("Día siguiente").performClick()
            onNodeWithContentDescription("Día siguiente").performClick()
            waitForText("Comida 2")
            waitForText(summary)
        }

        assertGoalOnThreeDays("2.000")

        // Cambiar el objetivo desde la pantalla de Objetivos lo cambia en todos los días.
        onAllNodesWithText("Objetivos").onLast().performClick()
        waitForText("Tus datos")
        onNodeWithText("Edad").performTextInput("30")
        onNodeWithText("Peso").performTextInput("80")
        onNodeWithText("Altura").performTextInput("180")
        onNodeWithText("Guardar objetivos").performClick()
        waitForText("Objetivos guardados")
        kotlin.test.assertEquals(2759, kotlinx.coroutines.runBlocking { goals.get().kcal })
        assertGoalOnThreeDays("2.759")
    }

    private fun ComposeUiTest.createPanCasero() {
        onNodeWithText("+ Añadir alimento").performClick()
        onNodeWithText("Alimento propio").performClick()
        waitForText("Valores por 100 g")
        onNodeWithText("Nombre").performTextInput("Pan casero")
        onNodeWithText("Energía").performTextInput("250")
        onNodeWithText("Proteína").performTextInput("8")
        onNodeWithText("Hidratos").performTextInput("50")
        onNodeWithText("Grasas").performTextInput("2")
        onNodeWithText("Guardar").performClick()
        waitForText("Buscar alimento")
        onNodeWithText("Buscar alimento").performTextInput("pan")
        waitForText("Pan casero")
    }

    @Test
    fun customFoodCanBeFoundAndAddedToDiary() = runApp(recentWeight = true) { _ ->
        waitForText("Nada apuntado hoy")
        createPanCasero()
        onNodeWithText("Pan casero").performClick()
        waitForText("Añadir a Snacks")
        onNodeWithText("Añadir a Snacks").performClick()
        waitGone("Añadir a Snacks")
        waitForText("Pan casero")
        waitGone("Nada apuntado hoy")
    }

    @Test
    fun customFoodCanBeEditedAndDeleted() = runApp { db ->
        waitForText("Nada apuntado hoy")
        createPanCasero()
        onNodeWithText("Pan casero").performClick()
        waitForText("Añadir a Snacks")

        onNodeWithContentDescription("Editar alimento propio").performClick()
        waitForText("Editar alimento")
        onNodeWithText("Pan casero").performTextReplacement("Pan casero integral")
        onNodeWithText("Guardar").performClick()
        waitForText("Pan casero integral")
        kotlin.test.assertEquals(listOf("Pan casero integral"), db.customFoodQueries.selectAll().executeAsList().map { it.name })

        onNodeWithContentDescription("Editar alimento propio").performClick()
        waitForText("Borrar alimento")
        onNodeWithText("Borrar alimento").performClick()
        // Cancelar no borra nada.
        waitForText("¿Borrar este alimento?")
        onNodeWithText("Cancelar").performClick()
        waitGone("¿Borrar este alimento?")
        kotlin.test.assertEquals(1, db.customFoodQueries.selectAll().executeAsList().size)
        onNodeWithText("Borrar alimento").performClick()
        onNodeWithText("Borrar").performClick()
        waitForText("Buscar alimento")
        kotlin.test.assertEquals(0, db.customFoodQueries.selectAll().executeAsList().size)
    }

    @Test
    fun copyAsCustomFoodPrefillsFormAndSavesCopy() = runApp { db ->
        waitForText("Nada apuntado hoy")
        onNodeWithText("+ Añadir alimento").performClick()
        onNodeWithText("Escáner").performClick()
        onNodeWithText("Código EAN").performTextInput(nutella.barcode)
        onNodeWithText("Buscar producto").performClick()
        waitForText("Añadir a Snacks")

        onNodeWithContentDescription("Copiar como alimento propio").performClick()
        waitForText("Valores por 100 g")
        onNodeWithText("Nutella").assertIsDisplayed()
        onNodeWithText("539").assertIsDisplayed()
        onNodeWithText("15 g").assertIsDisplayed()

        onNodeWithText("Guardar").performClick()
        waitForText("Añadir a Snacks")
        val saved = db.customFoodQueries.selectAll().executeAsList()
        kotlin.test.assertEquals(1, saved.size)
        kotlin.test.assertEquals("Nutella", saved.single().name)
        kotlin.test.assertEquals("Ferrero", saved.single().brand)
    }

    @Test
    fun genericFoodDetailShowsGenericSource() = runApp { db ->
        db.productQueries.upsert(
            "generic:1", "Manzana cruda", null, null, 52.0, 0.3, 14.0, 10.0, 0.2, null, 2.4, null, null, null, "GENERIC", 1L,
        )
        waitForText("Nada apuntado hoy")
        onNodeWithText("Alimentos").performClick()
        waitForText("Buscar alimento")
        onNodeWithText("Buscar alimento").performTextInput("manzana")
        waitForText("Manzana cruda")
        onNodeWithText("Manzana cruda").performClick()
        // Desde la pestaña no hay comida preseleccionada: la ficha propone la que toca por la hora.
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText("Añadir a ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        waitForText("Genérico")
        // La clave interna no se enseña como si fuera un código de barras.
        kotlin.test.assertTrue(onAllNodes(hasText("generic:1")).fetchSemanticsNodes().isEmpty())
        kotlin.test.assertTrue(onAllNodes(hasContentDescription("Copiar como alimento propio")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun foodsTabHasNoMealPickerNorSourceFilters() = runApp(recentWeight = true) { _ ->
        waitForText("Nada apuntado hoy")
        onNodeWithText("Alimentos").performClick()
        waitForText("Buscar alimento")
        onNodeWithText("Recientes").assertIsDisplayed()
        onNodeWithText("Escáner").assertIsDisplayed()
        onNodeWithText("Alimento propio").assertIsDisplayed()
        for (gone in listOf("Todos", "Genérico", "Open Food Facts", "Propios", "Código EAN")) {
            kotlin.test.assertTrue(onAllNodes(hasText(gone)).fetchSemanticsNodes().isEmpty(), gone)
        }
        kotlin.test.assertTrue(onAllNodes(hasText("▾", substring = true)).fetchSemanticsNodes().isEmpty())
        waitForText("Aún no hay recientes")
    }

    @Test
    fun recentsListShowsDiaryFoodsAndTheButtonReturnsToIt() = runApp(recentWeight = true) { _ ->
        waitForText("Nada apuntado hoy")
        addNutellaByManualEan()
        waitForText("Nutella")

        onNodeWithText("Alimentos").performClick()
        waitForText("Últimos alimentos · 1")
        onNodeWithText("Nutella").assertIsDisplayed()

        onNodeWithText("Buscar alimento").performTextInput("zzzz")
        waitForText("Sin resultados para «zzzz»")
        kotlin.test.assertTrue(onAllNodes(hasText("Últimos alimentos", substring = true)).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Recientes").performClick()
        waitForText("Últimos alimentos · 1")

        onNodeWithText("Nutella").performClick()
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText("Añadir a ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
