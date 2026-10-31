plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "boratreino.android.application"
            implementationClass = "com.boratreino.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "boratreino.android.library"
            implementationClass = "com.boratreino.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "boratreino.android.compose"
            implementationClass = "com.boratreino.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "boratreino.android.hilt"
            implementationClass = "com.boratreino.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "boratreino.android.feature"
            implementationClass = "com.boratreino.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidComposeTests") {
            id = "boratreino.android.compose.tests"
            implementationClass = "com.boratreino.buildlogic.AndroidComposeTestsConventionPlugin"
        }
        register("jvmLibrary") {
            id = "boratreino.jvm.library"
            implementationClass = "com.boratreino.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
