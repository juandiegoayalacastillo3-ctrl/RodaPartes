// Archivo de build de nivel superior: versiones de los plugins del proyecto
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Lee google-services.json y configura Firebase en la app
    id("com.google.gms.google-services") version "4.4.2" apply false
}
