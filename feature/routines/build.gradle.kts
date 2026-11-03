plugins {
    id("boratreino.android.feature")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.feature.routines"
}

dependencies {
    implementation(projects.core.settings)
}
