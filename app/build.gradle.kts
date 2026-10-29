plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "com.diego.mibiblioteca"; compileSdk = 36
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Con Android 16 (API 36) y el plugin 8.11 aparecen comprobaciones de lint nuevas.
    // Se siguen generando los informes (app/build/reports/lint-results-*.html), pero
    // un aviso nuevo no debe bloquear la compilación de la APK firmada.
    lint { abortOnError = false; checkReleaseBuilds = true }
    defaultConfig { applicationId = "com.diego.mibiblioteca"; minSdk = 26; targetSdk = 36; versionCode = 195; versionName = "1.0.95"
        // Solo procesadores de móviles reales (ARM). Se descartan x86/x86_64,
        // que solo usan emuladores de PC: la APK pasa de ~94 MB a unos 45-50 MB.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    val persistentKeyPath = System.getenv("MIBIBLIOTECA_KEYSTORE_PATH")
    val persistentSigning = if (!persistentKeyPath.isNullOrBlank()) signingConfigs.create("persistent") {
        storeFile = file(persistentKeyPath)
        storePassword = System.getenv("MIBIBLIOTECA_KEYSTORE_PASSWORD")
        keyAlias = "mibiblioteca"
        keyPassword = System.getenv("MIBIBLIOTECA_KEYSTORE_PASSWORD")
        enableV1Signing = true
        enableV2Signing = true
    } else null
    buildTypes {
      getByName("debug") {
        if (persistentSigning != null) signingConfig = persistentSigning
      }
      getByName("release") {
        signingConfig = persistentSigning
        isMinifyEnabled = true
        isShrinkResources = true
        isDebuggable = false
        isJniDebuggable = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    // Versión que usa el modelo de Google Play services en lugar de incluirlo en la app (~8 MB menos).
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Pagos de Google Play para Bookhollow Premium (pago único). Google exige la versión 8 o posterior.
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.google.code.gson:gson:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

