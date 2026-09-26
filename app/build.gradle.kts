import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ── Versionado automático ───────────────────────────────────────────────
// version.properties guarda MAJOR.MINOR.BUILD; cada assemble/install sube BUILD en 1,
// así cada APK generado tiene un número único (versionName "1.6.7", versionCode 7).
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply { versionFile.inputStream().use { load(it) } }
val isApkBuild = gradle.startParameter.taskNames.any { name ->
    name.substringAfterLast(':').let { it.startsWith("assemble") || it.startsWith("install") }
}
if (isApkBuild) {
    versionProps["BUILD"] = (versionProps.getProperty("BUILD").toInt() + 1).toString()
    versionFile.outputStream().use {
        versionProps.store(it, "Version de la app: MAJOR y MINOR a mano, BUILD sube solo en cada APK")
    }
}
val appVersionCode = versionProps.getProperty("BUILD").toInt()
val appVersionName = "${versionProps.getProperty("MAJOR")}.${versionProps.getProperty("MINOR")}.$appVersionCode"

android {
    namespace = "com.tuapp.tabatatrainer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tuapp.tabatatrainer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE.txt"
            // Exclusiones para Apache POI
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
        }
    }
}

// Copia cada APK generado a /apk con la versión en el nombre: TabataTrainer-1.6.7-debug.apk
listOf("debug", "release").forEach { type ->
    val cap = type.replaceFirstChar { it.uppercase() }
    val copyTask = tasks.register<Copy>("copy${cap}ApkWithVersion") {
        from(layout.buildDirectory.dir("outputs/apk/$type"))
        include("*.apk")
        into(rootProject.layout.projectDirectory.dir("apk"))
        rename { "TabataTrainer-$appVersionName-$type.apk" }
    }
    tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(copyTask) }
}

dependencies {
    // Jetpack Compose (versiones alineadas por la BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Hilt (inyección de dependencias)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (base de datos local)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines, DataStore, Timber
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)

    // Google Play Services: GPS y mapas
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.maps.utils)

    // Apache POI (exportar a Excel)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)

    // ANT+ Plugin Library
    implementation(files("libs/antpluginlib_3-9-0.aar"))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
