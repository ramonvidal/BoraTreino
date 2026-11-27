// Shared test fakes (stub repositories and settings, answering<T>(), TestClock). Tests only:
// modules add it with testImplementation; the module rules fail the build on any main-code dependency.
plugins {
    id("boratreino.android.library")
}

android {
    namespace = "com.boratreino.core.testing"
}

dependencies {
    // The stubs implement these interfaces, so tests that use them see the interfaces and model types too.
    api(projects.core.model)
    api(projects.core.data)
    api(projects.core.settings)
    api(libs.kotlinx.coroutines.android)
}
