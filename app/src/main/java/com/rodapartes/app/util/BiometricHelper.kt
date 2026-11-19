package com.rodapartes.app.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.rodapartes.app.R

/**
 * Ingreso con huella (RF-AUT-04) usando BiometricPrompt con clase BIOMETRIC_STRONG.
 * La huella se valida en el propio dispositivo: la app solo recibe "éxito" o "error",
 * nunca la huella (Decreto 1377 de 2013, dato sensible).
 */
object BiometricHelper {

    /** true si el celular tiene sensor de huella y al menos una huella registrada. */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Muestra el diálogo de huella del sistema y avisa si la verificación fue correcta. */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            /** La huella coincidió: se continúa con el ingreso. */
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            /** El usuario canceló o hubo un error del sensor. */
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Si el usuario toca "Usar contraseña" no se muestra error
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED
                ) onError(errString.toString())
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.bio_title))
            .setSubtitle(activity.getString(R.string.bio_subtitle))
            .setNegativeButtonText(activity.getString(R.string.bio_use_password))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info)
    }
}
