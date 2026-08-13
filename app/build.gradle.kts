import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing is provided through the CI workflow: the keystore is
 * base64-decoded to a file and the passwords/alias are exported as
 * environment variables. When those secrets are absent (local dev,
 * fork builds) the release APK falls back to the debug keystore so the
 * build never breaks.
 */
val releaseSigningConfigured: Boolean by lazy {
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val alias = System.getenv("ANDROID_KEY_ALIAS")
    val keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val storePassword = System.getenv("ANDROID_STORE_PASSWORD")
    !keystorePath.isNullOrBlank() && File(keystorePath).isFile &&
        !alias.isNullOrBlank() && !keyPassword.isNullOrBlank() && !storePassword.isNullOrBlank()
}

android {
    namespace = "com.lumocraft.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumocraft.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-rc1"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = File(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.commons.compress)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
