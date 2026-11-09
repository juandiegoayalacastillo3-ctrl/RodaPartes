package com.rodapartes.app.util

import android.content.Context
import com.rodapartes.app.data.Role

/**
 * Sesión local mínima (SharedPreferences) para el demo del frontend.
 * En la fase de backend el rol vendrá del custom claim del token de Firebase (RF-AUT-07)
 * y el token se guardará cifrado con el Android Keystore (RNF-SEG-03).
 */
class Session(context: Context) {

    private val prefs = context.getSharedPreferences("rodapartes_session", Context.MODE_PRIVATE)

    var role: Role?
        get() = prefs.getString(KEY_ROLE, null)?.let { Role.valueOf(it) }
        set(value) = prefs.edit().putString(KEY_ROLE, value?.name).apply()

    var name: String
        get() = prefs.getString(KEY_NAME, "Juan Pérez") ?: "Juan Pérez"
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "juan@correo.com") ?: "juan@correo.com"
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var phone: String
        get() = prefs.getString(KEY_PHONE, "300 123 4567") ?: "300 123 4567"
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Ingreso con huella activado desde el perfil (RF-PER-05). */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, false)
        set(value) = prefs.edit().putBoolean(KEY_BIO, value).apply()

    /** Consentimiento aparte para promociones (Ley 2300 de 2023). */
    var promos: Boolean
        get() = prefs.getBoolean(KEY_PROMOS, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMOS, value).apply()

    /** Cierra sesión pero conserva el último rol para el ingreso con huella. */
    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED, false).apply()
    }

    var loggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGED, value).apply()

    fun clearAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ROLE = "role"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_BIO = "biometric"
        const val KEY_PROMOS = "promos"
        const val KEY_LOGGED = "logged"
    }
}
