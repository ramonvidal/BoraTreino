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
        versionCode = buildNumber
        versionName = "0.$buildNumber"
    }

    // Two distributions of the same app. `sideload` is the GitHub APK with the in-app updater;
    // `play` is the Google Play bundle, which must not update itself or use the network.
    flavorDimensions += "distribution"

    // Android only installs an update signed with the same key as the installed app, so every
    // published build must use one fixed key per distribution. CI writes them from secrets (see
    // README); they are never committed. Without them, builds fall back to the machine's debug
    // key, which is fine locally but must never be published.
    //
    // Signing is chosen per flavor. AGP gives a signingConfig on a build type priority over the
    // flavor's, so the build types below leave it unset (and `debug` clears its default).
    val boraTreinoKeystore = file("boratreino-release.keystore")
    if (boraTreinoKeystore.exists()) {
        signingConfigs.create("boratreino") {
            storeFile = boraTreinoKeystore
            storePassword = "boratreino"
            keyAlias = "boratreino"
            keyPassword = "boratreino"
        }
    }
    val sideloadSigning = signingConfigs.findByName("boratreino") ?: signingConfigs.getByName("debug")

    // The Google Play upload key: Play re-signs what we upload with its own app signing key.
    val uploadKeystore = file("boratreino-upload.keystore")
    val uploadStorePassword = providers.environmentVariable("UPLOAD_STORE_PASSWORD").orNull
    // A PKCS12 keystore protects its key with the store password, so one password serves both.
    if (uploadKeystore.exists() && uploadStorePassword != null) {
        signingConfigs.create("upload") {
            storeFile = uploadKeystore
            storePassword = uploadStorePassword
            keyAlias = "upload"
            keyPassword = uploadStorePassword
        }
    } else {
        logger.warn("Upload key or its password is missing: the play variant is signed with the debug key and must not be published.")
    }
    val playSigning = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")

    productFlavors {
        create("sideload") {
            dimension = "distribution"
            applicationId = "com.boratreino"
            signingConfig = sideloadSigning
        }
        create("play") {
            dimension = "distribution"
            applicationId = "com.punyplatypus.boratreino"
            signingConfig = playSigning
        }
    }

    buildTypes {
        debug {
            // Clears the debug key AGP sets by default, so each flavor's key applies. Sideload
            // debug is signed like its releases (either installs over the other); play debug uses
            // the debug key.
            signingConfig = null
        }
        release {
            // No signingConfig here: the flavor's applies.
            // R8 shrinks and obfuscates the release builds (Google Play asks for it). What has to keep
            // its names (enums stored by name, Health Connect's protobuf) is in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    bundle {
        // Play would otherwise deliver only the device's language, and the in-app language choice
        // (Baianes on an English phone) would have nothing to switch to. Density and ABI splits
        // stay at their defaults.
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.notify)
    implementation(projects.core.health)
    // Hilt needs the data and database modules on the app's classpath to find their bindings.
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.settings)
    implementation(projects.feature.today)
    implementation(projects.feature.calendar)
    implementation(projects.feature.progress)
    implementation(projects.feature.routines)
    implementation(projects.feature.settings)
    // The updater ships in the GitHub APK only; Google Play forbids apps that update themselves.
    // :core:update (INTERNET, REQUEST_INSTALL_PACKAGES) arrives only through it.
    "sideloadImplementation"(projects.feature.update)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Gives WorkManager Hilt's worker factory (see BoraTreinoApp).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
}
