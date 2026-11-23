// Reads the numbers off a photo of the bike console (on-device text recognition, offline).
// The parsing rules live in :core:model (BikeScreenParser); this module only turns a picture into text lines.
plugins {
    id("boratreino.android.library")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino.core.scan"
}

dependencies {
    // BikeReading is part of the public API.
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Bundled model: ships inside the APK, works offline, no Play Services download.
    implementation(libs.mlkit.text.recognition)
}
