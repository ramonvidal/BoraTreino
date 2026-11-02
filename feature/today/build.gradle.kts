plugins {
    id("boratreino.android.feature")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.feature.today"
}

dependencies {
    implementation(projects.core.notify)
    implementation(projects.core.settings)
    implementation(libs.kotlinx.coroutines.android)
}
