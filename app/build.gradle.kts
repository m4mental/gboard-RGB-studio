plugins {
    id("com.android.application")
}

fun getPropOrEnv(name: String): String? =
    (project.findProperty(name) as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val ciRunNumber = getPropOrEnv("VERSION_CODE") ?: getPropOrEnv("GITHUB_RUN_NUMBER")
val computedVersionCode = ciRunNumber?.toIntOrNull()?.coerceAtLeast(1) ?: 1
val computedVersionName = ciRunNumber?.let { "1.0.$it" } ?: "1.0"

val keystorePath = getPropOrEnv("KEYSTORE_PATH")
val storePassword = getPropOrEnv("KEYSTORE_PASSWORD") ?: getPropOrEnv("STORE_PASSWORD")
val keyAlias = getPropOrEnv("KEY_ALIAS")
val keyPassword = getPropOrEnv("KEY_PASSWORD")

val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
    !storePassword.isNullOrBlank() &&
    !keyAlias.isNullOrBlank() &&
    !keyPassword.isNullOrBlank()

android {
    namespace = "com.custom.gboardrgb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.custom.gboardrgb"
        minSdk = 29
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(keystorePath!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
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
}

gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any {
        it.name.contains("Release", ignoreCase = true) &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
    }
    if (needsReleaseSigning && !hasReleaseSigning) {
        throw GradleException(
            "Release signing inputs are absent! Define KEYSTORE_PATH, KEYSTORE_PASSWORD (or STORE_PASSWORD), KEY_ALIAS, and KEY_PASSWORD via environment variables or Gradle properties."
        )
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
}
