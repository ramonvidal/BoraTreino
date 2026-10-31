pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "BoraTreino"

// See CLAUDE.md, "Module structure", for what may depend on what.
include(":app")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:settings")
include(":core:ui")
include(":core:notify")
include(":core:update")
include(":feature:today")
include(":feature:calendar")
include(":feature:progress")
include(":feature:routines")
include(":feature:settings")
include(":feature:update")
