import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.neteinstein.donaclone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neteinstein.donaclone"
        minSdk = 30
        targetSdk = 35
        // Use BUILD_NUMBER from the environment (the GitHub Actions run number, see
        // .github/workflows/release.yml) so every release gets a version code that reliably
        // increases build over build - the in-app update check compares against this, not
        // versionName, which stays static between releases. Defaults to 1 for local builds.
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GITHUB_REPOSITORY ("owner/repo") is set automatically by GitHub Actions on every run.
        // Injecting it lets UpdateRepositoryImpl point at whichever repo actually built the APK
        // (e.g. a fork) instead of a hardcoded one; fall back to this repo when building locally.
        val githubRepoSlug = System.getenv("GITHUB_REPOSITORY") ?: "neteinstein/DONA-Clone"
        buildConfigField("String", "GITHUB_REPO_SLUG", "\"$githubRepoSlug\"")
    }

    signingConfigs {
        create("release") {
            // Signing configuration sourced from environment variables / GitHub Actions secrets.
            // KEYSTORE_FILE is always the base64-encoded contents of the keystore, not a path.
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                val keystorePath = file("$buildDir/release.keystore")
                keystorePath.parentFile.mkdirs()
                keystorePath.writeBytes(Base64.getDecoder().decode(keystoreFile))
                storeFile = keystorePath
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the real release key when CI provides one; otherwise fall back to debug
            // signing so `./gradlew assembleRelease` still works on a developer machine without
            // the release signing secrets configured.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:login"))
    implementation(project(":feature:houses"))
    implementation(project(":feature:devices"))
    implementation(project(":feature:ambiences"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
