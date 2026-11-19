plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase se configura con google-services.json (proyecto "rodapartes").
// Se aplica solo si el archivo existe para que el proyecto también compile sin él.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.rodapartes.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rodapartes.app"
        minSdk = 26          // Android 8.0 (RNF-ESC-04)
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        // Llave RESTRINGIDA de pruebas de Stripe (rk_test_...): solo crea y lee sesiones de
        // Checkout y no cobra dinero real. No se guarda en el repositorio: llega por la variable
        // de entorno STRIPE_KEY (secreto de GitHub Actions) o por gradle.properties local.
        val stripeKey = System.getenv("STRIPE_KEY") ?: (project.findProperty("stripeKey") as String?) ?: ""
        buildConfigField("String", "STRIPE_KEY", "\"$stripeKey\"")
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
        buildConfig = true
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

    // Firebase: autenticación, base de datos Firestore y Storage para las fotos
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    // .await() para usar las tareas de Firebase dentro de corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Geolocalización (Fused Location Provider de Google Play Services)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Carga de imágenes desde URL (fotos de Storage)
    implementation("io.coil-kt:coil:2.7.0")

    // Guardado cifrado de credenciales para el ingreso con huella (Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
