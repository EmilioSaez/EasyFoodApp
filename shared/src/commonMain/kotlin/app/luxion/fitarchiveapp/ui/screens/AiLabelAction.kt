package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.ui.navigation.Route

/** «Etiqueta» en la fila «Herramientas» de la pestaña IA: crea un alimento propio fotografiando su etiqueta (docs/design.md §2.20). */
val aiLabelAction = AiTabAction(
    id = "label",
    label = "Etiqueta",
    icon = "▣",
    onClick = { env -> env.navigator.push(Route.LabelPhoto()) },
)
