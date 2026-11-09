package app.luxion.fitarchiveapp.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de ApexApp (§1.7): xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 · xxxl 48 y margen de pantalla 20.
 * Los nombres anteriores se conservan; ojo: `xl`, `xxl` y `xxxl` ahora valen 24, 32 y 48 (antes 20, 24 y 32).
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Margen horizontal de pantalla. */
    val screen = 20.dp

    // Nombres anteriores a la Fase 11.
    val s = sm
    val m = md
    val l = lg

    /** Hueco inferior para FAB / barra inferior. */
    val bottomInset = 96.dp
}
