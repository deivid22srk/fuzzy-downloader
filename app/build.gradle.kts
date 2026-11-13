plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreEnv = System.getenv("KEYSTORE_PATH")
val keystoreFile = keystoreEnv?.let { file(it) } ?: rootProject.file("release.jks")
val hasReleaseKey = keystoreFile.exists()

android {
    namespace = "com.fuzzydl.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fuzzydl.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"

        buildConfigField("String", "TARGET_PACKAGE", "\"com.thegwchr.fuzzy\"")
        buildConfigField("String", "TARGET_DIR", "\"/storage/emulated/0/Android/data/com.thegwchr.fuzzy/files/game/\"")
        buildConfigField("String", "TARGET_APP_URL", "\"https://play.google.com/store/apps/details?id=com.thegwchr.fuzzy\"")
        buildConfigField("String", "SHIZUKU_URL", "\"https://github.com/RikkaApps/Shizuku\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storeType = System.getenv("KEYSTORE_TYPE") ?: "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/*.version",
            "META-INF/DEPENDENCIES",
            "kotlin/**",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}
