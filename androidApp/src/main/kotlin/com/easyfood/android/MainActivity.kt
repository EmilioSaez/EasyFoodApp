package com.easyfood.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.ProductRepository
import com.easyfood.data.SqlDelightDiaryStore
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App

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
    private val diaryStore by lazy { SqlDelightDiaryStore(database.diaryEntryQueries) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.safeDrawingPadding()) { App(repository, diaryStore, today = { java.time.LocalDate.now().toString() }) }
        }
    }
}
