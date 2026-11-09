plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rodapartes.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rodapartes.app"
        minSdk = 26          // Android 8.0 (RNF-ESC-04)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-frontend"
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // R8 (RNF-SEG-05)
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

    // ViewBinding: acceso tipado a las vistas de cada layout XML
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.biometric:biometric:1.1.0")
}
