// This phone's choices (DataStore): weekly goal, reminders, rest timer.
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.settings"
}

dependencies {
    api(projects.core.model)
}
