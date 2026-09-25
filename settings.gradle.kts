pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // =========================================================================
        // Repositorio ANT+ SDK
        // =========================================================================
        // El SDK de ANT+ está disponible en el repositorio de Dynastream/Garmin
        //maven {
        //    url = uri("https://www.thisisant.com/assets/artifacts/")
            // Alternativa si el anterior no funciona:
            // url = uri("https://jitpack.io")

    }
}

rootProject.name = "TabataTrainer"
include(":app")
