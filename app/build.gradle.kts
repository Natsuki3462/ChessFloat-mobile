plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.chessfloat.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chessfloat.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.01"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {

            keepDebugSymbols += "**/libstockfish.so"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("C:/Users/Administrator/.workbuddy-ai/binaries/python/versions/3.13.12/python.exe")

    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
