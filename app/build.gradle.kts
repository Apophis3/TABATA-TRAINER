plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tuapp.tabatatrainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuapp.tabatatrainer"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.5-GPS"

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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
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

dependencies {

    // =========================================================================
    // Jetpack Compose - BOM (SOLO UNA DEFINICIÓN: La más reciente)
    // =========================================================================
    // Usamos la BOM 2024.06.00 para alinear todas las versiones
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Librerías de Compose (Sin número de versión, la BOM se encarga)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    // =========================================================================
    // Core Android
    // =========================================================================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // =========================================================================
    // Navigation
    // =========================================================================
    implementation("androidx.navigation:navigation-compose:2.7.7") // Actualizado ligeramente

    // =========================================================================
    // Lifecycle
    // =========================================================================
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    // =========================================================================
    // Hilt (Dependency Injection)
    // =========================================================================
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // =========================================================================
    // Room (Base de datos local)
    // =========================================================================
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // =========================================================================
    // Coroutines
    // =========================================================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // =========================================================================
    // DataStore (Preferencias)
    // =========================================================================
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // =========================================================================
    // Timber (Logging)
    // =========================================================================
    implementation("com.jakewharton.timber:timber:5.0.1")

    // =========================================================================
    // Google Play Services - Location (GPS)
    // =========================================================================
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // =========================================================================
    // Apache POI (Excel export)
    // =========================================================================
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // =========================================================================
    // GOOGLE MAPS
    // =========================================================================
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    // =========================================================================
    // ANT+ Plugin Library
    // =========================================================================
    implementation(files("libs/antpluginlib_3-9-0.aar"))

    // =========================================================================
    // Testing
    // =========================================================================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Para testing también usamos la BOM nueva
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}