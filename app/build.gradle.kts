import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Automated versioning: versionName comes from a git tag (`v0.1.0-rc1`)
 * or the `LUMOCRAFT_VERSION_NAME` environment variable (GitHub Actions
 * build numbers/inputs), versionCode is derived from the git tags that
 * are not newer than the current version, so it grows monotonically
 * without manual edits. `LUMOCRAFT_VERSION_CODE` overrides the derived
 * code (e.g. from a CI build number).
 */
private fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder("git", *args)
        .redirectErrorStream(false)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0) output.ifEmpty { null } else null
} catch (_: Exception) {
    null
}

/** Minimal SemVer (core + optional prerelease) for tag-derived codes. */
private data class SemVer(val major: Int, val minor: Int, val patch: Int, val prerelease: List<String>)

private fun parseVersion(raw: String): SemVer? {
    val input = raw.trim().removePrefix("v").takeIf { it.isNotEmpty() } ?: return null
    val core = input.substringBefore('-')
    val prerelease = input.substringAfter('-', missingDelimiterValue = "")
        .takeIf { it.isNotEmpty() }?.split('.')?.filter { it.isNotEmpty() } ?: emptyList()
    val parts = core.split('.')
    if (parts.size !in 1..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    return SemVer(
        major = numbers.getOrElse(0) { 0 },
        minor = numbers.getOrElse(1) { 0 },
        patch = numbers.getOrElse(2) { 0 },
        prerelease = prerelease
    )
}

private fun compareVersions(a: SemVer, b: SemVer): Int {
    val core = when {
        a.major != b.major -> a.major.compareTo(b.major)
        a.minor != b.minor -> a.minor.compareTo(b.minor)
        a.patch != b.patch -> a.patch.compareTo(b.patch)
        else -> 0
    }
    if (core != 0) return core
    return when {
        a.prerelease.isEmpty() && b.prerelease.isEmpty() -> 0
        a.prerelease.isEmpty() -> 1
        b.prerelease.isEmpty() -> -1
        else -> {
            val shared = minOf(a.prerelease.size, b.prerelease.size)
            for (i in 0 until shared) {
                val x = a.prerelease[i]
                val y = b.prerelease[i]
                val xN = x.toIntOrNull()
                val yN = y.toIntOrNull()
                val result = when {
                    xN != null && yN != null -> xN.compareTo(yN)
                    xN != null -> -1
                    yN != null -> 1
                    else -> x.compareTo(y)
                }
                if (result != 0) return result
            }
            a.prerelease.size.compareTo(b.prerelease.size)
        }
    }
}

private val versionNameRegex = Regex("[0-9]+(\\.[0-9]+){0,2}(-[A-Za-z0-9.-]+)?")

private fun derivedVersionName(): String? {
    System.getenv("LUMOCRAFT_VERSION_NAME")?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv("GITHUB_REF_NAME")?.trim()?.removePrefix("v")?.takeIf {
        it.isNotBlank() && versionNameRegex.matches(it)
    }?.let { return it }
    return gitOutput("describe", "--tags", "--abbrev=0")?.removePrefix("v")?.takeIf {
        it.isNotBlank() && versionNameRegex.matches(it)
    }
}

private fun derivedVersionCode(): Int {
    System.getenv("LUMOCRAFT_VERSION_CODE")?.toIntOrNull()?.let { return it }
    val tags = gitOutput("tag")?.lines() ?: emptyList()
    if (tags.isNotEmpty()) {
        val current = derivedVersionName()?.let { parseVersion(it) }
        if (current != null) {
            return tags.mapNotNull { parseVersion(it) }
                .count { compareVersions(it, current) <= 0 }
                .coerceAtLeast(1)
        }
    }
    System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { return it }
    return 1
}

val generatedVersionName: String by lazy { derivedVersionName() ?: "0.1.0-rc1" }
val generatedVersionCode: Int by lazy { derivedVersionCode() }

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

    /**
     * The in-process JVM launcher (app/src/main/cpp) is the only launch
     * path: Android mounts app-writable directories with `noexec`, so
     * exec'ing the extracted runtime's bin/java fails with Permission
     * denied. The native library builds with the NDK and loads the
     * runtime's libjli.so at launch time instead. ABIs are limited to
     * the architectures the runtimes support (see RuntimeArchitecture).
     */
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.lumocraft.app"
        minSdk = 26
        targetSdk = 35
        versionCode = generatedVersionCode
        versionName = generatedVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

    testOptions {
        unitTests {
            // Robolectric needs real Android resources/org.json in tests.
            isIncludeAndroidResources = true
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
    implementation(libs.tukaani.xz)
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

/** Prints the resolved version name/code; used by CI and release tooling. */
tasks.register("printVersionName") {
    doLast { println(generatedVersionName) }
}

tasks.register("printVersionCode") {
    doLast { println(generatedVersionCode) }
}
