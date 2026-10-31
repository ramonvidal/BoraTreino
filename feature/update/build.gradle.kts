plugins {
    id("boratreino.android.feature")
    id("boratreino.android.compose.tests")
}

android {
    namespace = "com.boratreino.feature.update"
}

dependencies {
    implementation(projects.core.update)
}
