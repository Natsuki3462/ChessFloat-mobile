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
        versionCode = 6
        versionName = "0.6"

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
            // libstockfish.so 其实是可执行文件，不能被 strip
            keepDebugSymbols += "**/libstockfish.so"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        // python-chess 是纯 Python 包，直接 vendor 在 src/main/python/chess 下，
        // 不走 Chaquopy 的 pip 索引（那边只有很老的版本），因此不需要 buildPython。
        // 若以后要用 pip 装包，取消下面这行注释并改成你本机的 Python 路径：
        // buildPython("/usr/bin/python3")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
