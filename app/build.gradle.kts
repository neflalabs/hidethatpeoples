import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val gitCommitCount = providers.exec {
    isIgnoreExitValue = true
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

val gitTag = providers.exec {
    isIgnoreExitValue = true
    commandLine("git", "describe", "--tags", "--exact-match")
}.standardOutput.asText.map { it.trim().removePrefix("v") }

val autoVersionCode: Int = gitCommitCount.getOrElse(1)
val autoVersionName: String = System.getenv("APP_VERSION_NAME")?.trim()?.removePrefix("v")
    ?: gitTag.orNull?.takeIf { it.isNotBlank() }
    ?: "1.0.$autoVersionCode"

base {
    archivesName.set("HideThatPeoples")
}

android {
    namespace = "com.nefla.hidethatpeoples"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nefla.hidethatpeoples"
        minSdk = 26
        targetSdk = 35
        versionCode = autoVersionCode
        versionName = autoVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("release.keystore")
            val keystorePropsFile = rootProject.file("keystore.properties")
            val props = Properties()
            if (keystorePropsFile.exists()) {
                keystorePropsFile.inputStream().use { stream ->
                    props.load(stream)
                }
            }

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: props.getProperty("KEYSTORE_PASSWORD")
                    ?: "android"
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: props.getProperty("KEY_ALIAS")
                    ?: "hidethatpeoples"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: props.getProperty("KEY_PASSWORD")
                    ?: "android"
            } else {
                // Fallback to debug signature so local release builds are always signed & installable
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Standalone Local ADB (Wireless Debugging)
    implementation(libs.libadb.android)
    implementation(libs.bcpkix)
    implementation(libs.conscrypt.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
