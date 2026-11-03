// Theme, shared composables and strings used by more than one feature.
plugins {
    id("boratreino.android.library")
    id("boratreino.android.compose")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.core.ui"
}

dependencies {
    implementation(projects.core.model)
}
