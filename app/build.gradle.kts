plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.arcimage.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arcimage.app"
        minSdk = 28
        targetSdk = 36
        // CI overrides these via -PversionCode / -PversionName (e.g. from a v1.2.3 tag)
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Configure supported locales matching res/xml/locales_config.xml
    androidResources {
        localeFilters += listOf("en", "de", "it", "fr", "es", "el")
    }

    // Release signing: credentials come from environment variables (GitHub Actions secrets)
    // or ~/.gradle/gradle.properties — never from the repository.
    fun signingValue(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

    val releaseKeystorePath = signingValue("ARCIMAGE_KEYSTORE_PATH")
    val hasReleaseSigning = releaseKeystorePath != null && file(releaseKeystorePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = signingValue("ARCIMAGE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("ARCIMAGE_KEY_ALIAS")
                keyPassword = signingValue("ARCIMAGE_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // Universal & split ABI support for Googlebook OS x86_64 and ARM64 devices.
    // Disabled for App Bundle builds: Play generates per-ABI splits from the .aab itself,
    // and AGP fails bundling when APK ABI splits are enabled.
    val isBundleBuild = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !isBundleBuild
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        jniLibs {
            // 16 KB page alignment compatibility required for modern Android 15–17+ & Googlebook OS
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android & AppCompat (Per-App Language Preferences API)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // Lifecycle, ViewModel & SavedStateHandle (Cross-Device Continuity & Window Resize Persistence)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Jetpack Compose Material 3 & Material You Dynamic Color
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Material 3 Adaptive Layout (ListDetailPaneScaffold & WindowSizeClass for Googlebook OS)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)

    // Jetpack Glance AppWidget with Material 3 (Googlebook OS Desktop & Android Homescreen Widget)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Storage, Recursive Folder Access, EXIF Metadata & HEIC/HEIF Encoding
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.heifwriter)

    // Coroutines & Async Image Thumbnails
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    // Archiver & Extractor Engines:
    // 1. Zip4j: Standard & AES-256 encrypted ZIP compression and extraction
    implementation(libs.zip4j)
    // 2. Apache Commons Compress + Tukaani XZ: 7z, TAR, GZ, BZ2, XZ compression & extraction
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    // 3. 7-Zip-JBinding-4Android: Native ARM64 & x86_64 extraction for RAR4, RAR5 & password-protected RAR/7z
    implementation(libs.sevenzip.jbinding.android)

    // Unit Testing (JUnit 4, Robolectric, Coroutines Test, Turbine Flow Assertions)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Android Instrumentation & Compose UI Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
