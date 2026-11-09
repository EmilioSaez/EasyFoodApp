package app.luxion.fitarchiveapp.scanner

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/** Abre los ajustes de la app, donde se puede activar el permiso de cámara denegado. */
@Composable
actual fun rememberAppSettingsOpener(): (() -> Unit)? = {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}
