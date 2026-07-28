import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.alertnotes"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.alertnotes"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing. Credentials come from keystore.properties (git-ignored)
    // or, on CI, from the matching environment variables — never from source
    // control. When neither is present the config is simply not created, so a
    // contributor without the keystore can still run `assembleRelease` and get
    // an unsigned APK instead of a build failure.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun credential(key: String, env: String): String? =
        keystoreProperties.getProperty(key) ?: System.getenv(env)

    val storeFilePath = credential("storeFile", "ALERTNOTES_STORE_FILE")
    val hasSigningCredentials = storeFilePath != null &&
        rootProject.file(storeFilePath).exists()

    signingConfigs {
        if (hasSigningCredentials) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = credential("storePassword", "ALERTNOTES_STORE_PASSWORD")
                keyAlias = credential("keyAlias", "ALERTNOTES_KEY_ALIAS")
                keyPassword = credential("keyPassword", "ALERTNOTES_KEY_PASSWORD")
                // Both schemes: v1 for API 26–27, v2+ for everything newer.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 on: shrinks the APK (material-icons-extended alone is
            // huge unshrunk) and obfuscates the release build. Room, Hilt,
            // and kotlinx-serialization ship consumer keep rules; the rules
            // this app adds on top live in proguard-rules.pro.
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // No applicationIdSuffix here: google-services.json is keyed to
            // the com.alertnotes package, and suffixing the debug id makes the
            // Google Services plugin fail to find a matching client.
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt-generated components reference these annotations (CLASS retention only).
    compileOnly(libs.errorprone.annotations)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase (online-version branch). BoM pins every Firebase artifact.
    // Online mode is strictly additive: Auth for accounts, Firestore for
    // user profiles and the one-time reminder upload. FCM/Functions are
    // not wired up yet.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.kotlinx.coroutines.play.services)
    // Fused Location Provider: the reliable path to a fix (last-known +
    // high-accuracy updates) for location-proof acknowledgements.
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation("com.google.firebase:firebase-messaging")
}
