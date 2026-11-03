// This phone's choices (DataStore): weekly goal, reminders, rest timer.
plugins {
    id("boratreino.android.library")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino.core.settings"
    // Robolectric tests need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
