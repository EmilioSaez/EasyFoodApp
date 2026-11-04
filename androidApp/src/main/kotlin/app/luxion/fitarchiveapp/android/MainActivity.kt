package app.luxion.fitarchiveapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.luxion.fitarchiveapp.data.OpenFoodFactsClient
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.ui.App
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val database by lazy {
        EasyFoodDatabase(AndroidSqliteDriver(EasyFoodDatabase.Schema, applicationContext, "easyfood.db"))
    }
    private val repository by lazy {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(database.productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }
    private val goalStore by lazy { SqlDelightGoalStore(database.dailyGoalQueries) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.safeDrawingPadding()) { App(repository, goalStore) }
        }
    }
}
