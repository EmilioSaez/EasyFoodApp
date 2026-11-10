package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.ui.navigation.Route

/** «Foto del plato» en la fila «Herramientas» de la pestaña «IA» (docs/design.md §2.21): sin comida fijada, se elige en la revisión. */
val aiPlateAction = AiTabAction(
    id = "plate",
    label = "Foto del plato",
    icon = "▣",
    onClick = { env -> env.navigator.push(Route.PlatePhoto()) },
)
