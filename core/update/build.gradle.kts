// Updating the app itself from the project's GitHub release page.
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.update"
}

dependencies {
    api(projects.core.model)
}
