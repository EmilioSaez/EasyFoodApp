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
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        val driver = AndroidSqliteDriver(EasyFoodDatabase.Schema, applicationContext, "easyfood.db")
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(EasyFoodDatabase(driver).productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.safeDrawingPadding()) { App(repository) }
        }
    }
}
