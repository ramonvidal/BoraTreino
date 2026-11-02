plugins {
    id("boratreino.android.feature")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.feature.calendar"
}

dependencies {
    implementation(projects.core.settings)
    implementation(libs.kotlinx.coroutines.android)
}
