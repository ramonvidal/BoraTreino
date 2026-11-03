plugins {
    id("boratreino.android.feature")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.feature.settings"
}

dependencies {
    implementation(projects.core.settings)
    implementation(projects.core.notify)
    implementation(projects.core.update)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
