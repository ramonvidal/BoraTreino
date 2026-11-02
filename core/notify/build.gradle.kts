// Rest timer notification, training-day reminders (WorkManager, AlarmManager).
plugins {
    id("boratreino.android.library")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino.core.notify"
    // Robolectric tests need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    // Generates the factory for the @HiltWorker worker.
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
}
