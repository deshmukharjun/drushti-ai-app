import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase: add to local.properties (project root, not committed):
// supabase.url=https://YOUR_PROJECT.supabase.co
// supabase.anon.key=your_anon_key
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.drushtiai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.drushtiai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val supabaseUrl = localProperties.getProperty("supabase.url", "https://YOUR_PROJECT.supabase.co")
        val supabaseKey = localProperties.getProperty("supabase.anon.key", "YOUR_ANON_KEY")
        // Backend (FastAPI) base URL. Set in local.properties:
        //   backend.url=http://192.168.1.10:8000
        // Use the LAN IP of the machine running the backend (NOT 127.0.0.1, since
        // Android sees its own loopback). For Android emulator on the same machine
        // use http://10.0.2.2:8000.
        val backendUrl = localProperties.getProperty("backend.url", "http://10.0.2.2:8000")
        // Optional default camera source registered on the backend if no camera
        // exists yet. "0" = first webcam on the backend host. Replace with an
        // RTSP URL like rtsp://user:pass@cam-host:554/stream for an IP camera.
        val backendCameraSource = localProperties.getProperty("backend.camera.source", "0")
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "BACKEND_URL", "\"${backendUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "BACKEND_CAMERA_SOURCE", "\"${backendCameraSource.replace("\"", "\\\"")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:2.4.1"))
    implementation("io.github.jan-tennert.supabase:supabase-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.glide)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}