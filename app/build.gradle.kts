// Wires the features together; the only module that may depend on features.
plugins {
    id("boratreino.android.application")
    id("boratreino.android.compose")
    id("boratreino.android.hilt")
}

android {
    namespace = "com.boratreino"

    // Every CI build gets a higher number, so a new APK always installs over the old one.
    val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").map(String::toInt).getOrElse(1)

    defaultConfig {
        applicationId = "com.boratreino"
        versionCode = buildNumber
        versionName = "0.$buildNumber"
    }

    // Android only installs an update signed with the same key as the installed app, so every
    // published build must use one fixed key. CI writes it from the SIGNING_KEYSTORE_BASE64
    // secret (see README); it is never committed. Without it, builds fall back to the machine's
    // debug key, which is fine locally but must never be published.
    val boraTreinoKeystore = file("boratreino-release.keystore")
    if (boraTreinoKeystore.exists()) {
        signingConfigs.create("boratreino") {
            storeFile = boraTreinoKeystore
            storePassword = "boratreino"
            keyAlias = "boratreino"
            keyPassword = "boratreino"
        }
    }
    val signing = signingConfigs.findByName("boratreino") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            // Same key as releases, so either installs over the other without uninstalling.
            signingConfig = signing
        }
        release {
            signingConfig = signing
            // Shrinking would make the APK smaller, but can break code that's only found at
            // runtime; worth turning on once there are UI tests on a real device.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.notify)
    // Hilt needs the data and database modules on the app's classpath to find their bindings.
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.settings)
    implementation(projects.feature.today)
    implementation(projects.feature.calendar)
    implementation(projects.feature.progress)
    implementation(projects.feature.routines)
    implementation(projects.feature.settings)
    implementation(projects.feature.update)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Gives WorkManager Hilt's worker factory (see BoraTreinoApp).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
}
