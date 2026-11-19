plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.movin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.movin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    flavorDimensions += "runtime"
    productFlavors {
        create("termux") {
            dimension = "runtime"
            // Matches Termux's compiled /data/data/com.termux/files/usr prefix for native speed (no proot)
            applicationId = "com.termux"
            buildConfigField("Boolean", "IS_NATIVE_TERMUX_PREFIX", "true")
            buildConfigField("String", "LINUX_PREFIX", "\"/data/data/com.termux/files/usr\"")
            buildConfigField("String", "LINUX_HOME", "\"/data/data/com.termux/files/home\"")
        }
        create("standalone") {
            dimension = "runtime"
            applicationId = "com.movin.app"
            buildConfigField("Boolean", "IS_NATIVE_TERMUX_PREFIX", "false")
            buildConfigField("String", "LINUX_PREFIX", "\"/data/data/com.movin.app/files/usr\"")
            buildConfigField("String", "LINUX_HOME", "\"/data/data/com.movin.app/files/home\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
