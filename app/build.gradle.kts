plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.local.chargemeter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.local.chargemeter"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha27")
    implementation("androidx.compose.material:material-icons-extended:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("dev.chrisbanes.haze:haze:2.0.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
    ksp("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
