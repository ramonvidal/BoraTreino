// Updating the app itself from the project's GitHub release page.
plugins {
    id("boratreino.android.library")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino.core.update"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
