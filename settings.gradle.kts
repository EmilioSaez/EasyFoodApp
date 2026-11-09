rootProject.name = "EasyFoodApp"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // SQLite empaquetado para Android (el del sistema no trae FTS5).
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
    }
}

include(":shared")
include(":desktopApp")
include(":androidApp")
