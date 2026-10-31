// Repositories over the database, backup export / import. Callers see :core:model types only.
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.data"
}

dependencies {
    // Repositories take and return model types, so callers get them too.
    api(projects.core.model)
    implementation(projects.core.database)
}
