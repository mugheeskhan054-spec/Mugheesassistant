plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.poolaim.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.poolaim.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        debug {
            // Disables stripping and incremental splitting issues on prebuilt binaries
            packaging {
                jniLibs {
                    keepDebugSymbols += listOf("**/*.so")
                }
            }
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libc++_shared.so",
                "**/libopencv_java4.so"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.quickbirdstudios:opencv:4.5.3.0")
}
