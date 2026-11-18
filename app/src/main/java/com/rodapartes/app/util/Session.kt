package com.rodapartes.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rodapartes.app.data.AppUser
import com.rodapartes.app.data.Fire

/**
 * Datos de la sesión actual.
 *
 * - [user] es el perfil leído de Firestore (users/{uid}) al iniciar sesión. Se guarda en memoria
 *   para no consultar la base de datos en cada pantalla.
 * - La sesión de Firebase Auth persiste sola entre aperturas de la app.
 */
object Session {

    /** Perfil del usuario con sesión iniciada; null si es invitado. */
    var user: AppUser? = null

    val isLoggedIn get() = Fire.auth.currentUser != null && user != null

    fun clear() {
        user = null
    }
}

/**
 * Almacén cifrado para el ingreso con huella (RF-AUT-04, RNF-SEG-03).
 *
 * Cuando el usuario activa la huella, su correo y contraseña se guardan con
 * EncryptedSharedPreferences, que cifra con una llave del Android Keystore (AES-256).
 * La llave nunca sale del dispositivo. La huella en sí jamás llega a la app: BiometricPrompt
 * solo responde "éxito" o "error" y, con éxito, se usan estas credenciales para iniciar sesión.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "rodapartes_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** ¿Hay credenciales guardadas para entrar con huella? */
    val biometricEnabled: Boolean get() = prefs.contains(KEY_EMAIL) && prefs.contains(KEY_PASSWORD)

    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val password: String? get() = prefs.getString(KEY_PASSWORD, null)

    fun enableBiometric(email: String, password: String) {
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_PASSWORD, password).apply()
    }

    fun disableBiometric() {
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val KEY_EMAIL = "bio_email"
        const val KEY_PASSWORD = "bio_password"
    }
}
