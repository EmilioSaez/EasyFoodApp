package app.luxion.easyfoodapp.ui.screens

import app.luxion.easyfoodapp.ui.navigation.Route

/** «Apuntar por texto» en la fila «Herramientas» de la pestaña «IA» (docs/design.md §2.21): describe la comida y la IA busca los alimentos. */
val aiTextAction = AiTabAction(
    id = "text",
    label = "Por texto",
    icon = "✎",
    onClick = { env -> env.navigator.push(Route.PlatePhoto(text = true)) },
)
