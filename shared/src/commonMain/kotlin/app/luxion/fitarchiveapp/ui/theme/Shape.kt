package app.luxion.fitarchiveapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Formas (docs/design.md §1.7): campos 12 · chips 16 · avisos 20 · tarjetas 24 · hojas 32; botones y chips de acción, píldora. */
val EasyFoodShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val PillShape = CircleShape
