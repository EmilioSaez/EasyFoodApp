package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.runtime.Composable

/** Intercepta el gesto/botón «atrás» del sistema (Android); sin efecto en escritorio e iOS. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
