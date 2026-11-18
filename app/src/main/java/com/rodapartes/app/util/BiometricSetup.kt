package com.rodapartes.app.util

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.Fire
import com.rodapartes.app.databinding.DialogConfirmPasswordBinding
import kotlinx.coroutines.launch

/**
 * Pasos para activar el ingreso con huella (RF-AUT-04, RF-PER-05). Sirve para los tres roles.
 *
 * 1. Se confirma la contraseña contra Firebase (reautenticación).
 * 2. Se pide la huella con BiometricPrompt.
 * 3. Si todo sale bien, el correo y la contraseña quedan cifrados en [SecureStore].
 */
object BiometricSetup {

    /** Diálogo para escribir la contraseña. [onPassword] recibe el texto escrito. */
    fun askPassword(fragment: Fragment, title: Int, onCancel: () -> Unit = {}, onPassword: (String) -> Unit) {
        askPassword(fragment.requireActivity(), title, onCancel, onPassword)
    }

    fun askPassword(activity: FragmentActivity, title: Int, onCancel: () -> Unit = {}, onPassword: (String) -> Unit) {
        val dialog = DialogConfirmPasswordBinding.inflate(activity.layoutInflater)
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(dialog.root)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val password = dialog.etPassword.text.toString()
                if (password.isBlank()) onCancel() else onPassword(password)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    /** Flujo completo desde el perfil: contraseña → reautenticación → huella → guardar. */
    fun enableWithPassword(fragment: Fragment, onResult: (Boolean) -> Unit) {
        val activity = fragment.requireActivity()
        if (!BiometricHelper.isAvailable(activity)) {
            activity.findViewById<android.view.View>(android.R.id.content).snack(activity.getString(R.string.bio_not_available))
            onResult(false)
            return
        }
        askPassword(fragment, R.string.bio_confirm_title, onCancel = { onResult(false) }) { password ->
            activity.lifecycleScope.launch {
                try {
                    AuthRepository.reauthenticate(password)
                    enable(activity, Fire.auth.currentUser?.email.orEmpty(), password, onResult)
                } catch (e: Exception) {
                    activity.findViewById<android.view.View>(android.R.id.content).snack(AuthRepository.friendlyMessage(e))
                    onResult(false)
                }
            }
        }
    }

    /** Pide la huella y, si es correcta, guarda las credenciales cifradas. */
    fun enable(activity: FragmentActivity, email: String, password: String, onResult: (Boolean) -> Unit) {
        BiometricHelper.authenticate(
            activity,
            onSuccess = {
                SecureStore(activity).enableBiometric(email, password)
                activity.findViewById<android.view.View>(android.R.id.content).snack(activity.getString(R.string.bio_enabled))
                onResult(true)
            },
            onError = {
                activity.findViewById<android.view.View>(android.R.id.content).snack(it)
                onResult(false)
            }
        )
    }

    /** ¿Ya se le ofreció activar la huella a este correo y dijo que no? */
    fun wasOffered(context: Context, email: String) =
        context.getSharedPreferences("rodapartes_prefs", Context.MODE_PRIVATE).getBoolean("bio_offered_$email", false)

    fun markOffered(context: Context, email: String) =
        context.getSharedPreferences("rodapartes_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("bio_offered_$email", true).apply()
}
