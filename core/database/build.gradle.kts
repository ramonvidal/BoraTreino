// Room: tables, DAOs, migrations. Room types stay inside this module.
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.database"
}

dependencies {
    api(projects.core.model)
}
