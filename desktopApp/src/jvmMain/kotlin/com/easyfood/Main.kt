package com.easyfood

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyfood.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
        App()
    }
}
