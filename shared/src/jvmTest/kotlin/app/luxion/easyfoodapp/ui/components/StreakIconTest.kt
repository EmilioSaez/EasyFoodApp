package app.luxion.easyfoodapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StreakIconTest {
    @Test
    fun rendersActiveAndInactiveAtSmallAndLargeSizes() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StreakIcon(active = true, size = 12.dp, modifier = Modifier.semantics { contentDescription = "a12" })
                StreakIcon(active = false, size = 12.dp, modifier = Modifier.semantics { contentDescription = "i12" })
                StreakIcon(active = true, size = 96.dp, modifier = Modifier.semantics { contentDescription = "a96" })
            }
        }
        onNodeWithContentDescription("a12").assertWidthIsEqualTo(12.dp).assertHeightIsEqualTo(12.dp)
        onNodeWithContentDescription("i12").assertWidthIsEqualTo(12.dp)
        onNodeWithContentDescription("a96").assertWidthIsEqualTo(96.dp)
    }
}
