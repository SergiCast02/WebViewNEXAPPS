import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nexus.nexapps"
    compileSdk = 34

    defaultConfig {
        applicationId         = "com.nexus.nexapps"
        minSdk                = 25
        targetSdk             = 34
        versionCode           = 2025
        versionName           = "2025.1.15.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("nexusApp") {
            keyAlias     = "nexusAPP"
            keyPassword  = "nexusAPP"
            storePassword= "nexusAPP"
            storeFile    = file("C:\\Users\\enriz\\AndroidStudioProjects\\NEXAPPS\\keystore.jks")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig   = signingConfigs["nexusApp"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Ajusta a la versión de tu Kotlin compiler plugin
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packagingOptions {
        resources {
            // excluye metadatos duplicados
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    // WebView / navegador embebido
    implementation("androidx.webkit:webkit:1.12.0")
    implementation("androidx.browser:browser:1.8.0")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:23.1.2")

    // Retrofit / OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // Core y UI clásicas
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Compose BOM para alinear versiones
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))

    // Jetpack Compose (las versiones las toma el BOM)
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")               // Material2
    implementation("androidx.compose.material3:material3")             // Material3
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Fuerza jvmTarget="1.8" en todas las tareas Kotlin
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}
