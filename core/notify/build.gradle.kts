// Rest timer notification, training-day reminders (WorkManager).
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.notify"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.settings)
}
