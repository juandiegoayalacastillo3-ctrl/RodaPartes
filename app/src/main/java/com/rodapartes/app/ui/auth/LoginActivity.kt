package com.rodapartes.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.databinding.ActivityLoginBinding
import com.rodapartes.app.ui.buyer.BuyerActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.data.Role
import com.rodapartes.app.util.BiometricHelper
import com.rodapartes.app.util.BiometricSetup
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.SecureStore
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Inicio de sesión con correo y contraseña (RF-AUT-02) o con huella (RF-AUT-04).
 * Después de autenticar, el rol se lee de Firestore y se abre la interfaz que corresponde.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var secure: SecureStore
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        secure = SecureStore(this)

        binding.btnLogin.setOnClickListener { login() }
        binding.btnForgot.setOnClickListener { startActivity(Intent(this, RecoverPasswordActivity::class.java)) }
        binding.btnRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.btnFingerprint.setOnClickListener { loginWithFingerprint() }
        binding.btnGuest.setOnClickListener { startActivity(Intent(this, BuyerActivity::class.java)) }

        // Si la huella está activa se ofrece de una vez al abrir la pantalla
        if (secure.biometricEnabled && BiometricHelper.isAvailable(this)) {
            binding.etEmail.setText(secure.email)
            loginWithFingerprint()
        }
    }

    /** Valida los campos y autentica contra Firebase Auth. */
    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_required)
            return
        }
        signIn(email, password)
    }

    /** Llama a Firebase; si falla 5 veces seguidas bloquea el botón 30 s (RF-AUT-02). */
    private fun signIn(email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val user = AuthRepository.login(email, password)
                Session.user = user
                offerBiometricThenGoHome(email, password, user.roleEnum)
            } catch (e: Exception) {
                setLoading(false)
                failedAttempts++
                binding.tilPassword.error = AuthRepository.friendlyMessage(e)
                if (failedAttempts >= 5) lockTemporarily()
            }
        }
    }

    /** Bloqueo temporal después de 5 intentos fallidos. */
    private fun lockTemporarily() {
        binding.btnLogin.isEnabled = false
        lifecycleScope.launch {
            for (s in 30 downTo 1) {
                binding.btnLogin.text = getString(R.string.login_locked, s)
                delay(1000)
            }
            failedAttempts = 0
            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = getString(R.string.login_button)
        }
    }

    /**
     * Ingreso con huella: BiometricPrompt valida la huella en el propio dispositivo y, si es
     * correcta, se usan las credenciales guardadas cifradas en el Keystore para iniciar sesión.
     */
    private fun loginWithFingerprint() {
        when {
            !secure.biometricEnabled -> binding.root.snack(getString(R.string.bio_not_enabled))
            !BiometricHelper.isAvailable(this) -> binding.root.snack(getString(R.string.bio_not_available))
            else -> BiometricHelper.authenticate(
                this,
                onSuccess = { signIn(secure.email.orEmpty(), secure.password.orEmpty()) },
                onError = { binding.root.snack(it) }
            )
        }
    }

    /**
     * Después del primer ingreso con contraseña ofrece activar la huella (para los tres roles).
     * Si el usuario dice que no, no se vuelve a preguntar para ese correo.
     */
    private fun offerBiometricThenGoHome(email: String, password: String, role: Role) {
        val alreadyEnabled = secure.biometricEnabled && secure.email == email
        if (alreadyEnabled || !BiometricHelper.isAvailable(this) || BiometricSetup.wasOffered(this, email)) {
            Router.goHome(this, role)
            return
        }
        BiometricSetup.markOffered(this, email)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bio_offer_title)
            .setMessage(R.string.bio_offer_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.bio_offer_yes) { _, _ ->
                BiometricSetup.enable(this, email, password) { Router.goHome(this, role) }
            }
            .setNegativeButton(R.string.action_not_now) { _, _ -> Router.goHome(this, role) }
            .show()
    }

    /** Muestra la carga y bloquea los botones mientras se valida el ingreso. */
    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnFingerprint.isEnabled = !loading
        binding.btnLogin.text = getString(if (loading) R.string.loading else R.string.login_button)
    }

    companion object {
        /** Mínimo 8 caracteres con letras y números (RF-AUT-01). */
        fun isValidPassword(p: String) =
            p.length >= 8 && p.any { it.isLetter() } && p.any { it.isDigit() }
    }
}
