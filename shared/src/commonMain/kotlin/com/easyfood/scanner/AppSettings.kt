package com.easyfood.scanner

import androidx.compose.runtime.Composable

/**
 * Devuelve una acción que abre los ajustes de la app del sistema (para activar el permiso de cámara),
 * o `null` si la plataforma no tiene esa pantalla.
 */
@Composable
expect fun rememberAppSettingsOpener(): (() -> Unit)?
