// Writes finished trainings to Health Connect (optional, write-only).
plugins {
    id("boratreino.android.library")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino.core.health"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.settings)

    // ActivityResultContract is part of the public API (permissionContract).
    api(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.connect)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
