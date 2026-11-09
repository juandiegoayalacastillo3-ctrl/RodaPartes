package com.rodapartes.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.Role
import com.rodapartes.app.databinding.ActivityLoginBinding
import com.rodapartes.app.ui.buyer.BuyerActivity
import com.rodapartes.app.util.BiometricHelper
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: Session
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = Session(this)

        binding.btnLogin.setOnClickListener { login() }
        binding.btnForgot.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java))
        }
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnGoogle.setOnClickListener {
            // En la fase de backend: Credential Manager + Firebase Auth (RF-AUT-03)
            binding.root.snack(getString(R.string.login_google_demo))
            enter(Role.BUYER, "juan.google@gmail.com")
        }
        binding.btnFingerprint.setOnClickListener { loginWithFingerprint() }
        binding.btnGuest.setOnClickListener {
            startActivity(Intent(this, BuyerActivity::class.java))
        }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        if (!isValidPassword(password)) {
            failedAttempts++
            // Mensaje genérico: no revela si el correo existe (RF-AUT-02)
            binding.tilPassword.error = getString(R.string.error_login_generic)
            if (failedAttempts >= 5) lockTemporarily()
            return
        }

        // Demo: el rol se toma del correo. En producción viene del custom claim del token.
        val role = when {
            email.contains("admin") -> Role.ADMIN
            email.contains("vendedor") || email.contains("tienda") -> Role.SELLER
            else -> Role.BUYER
        }
        enter(role, email)
    }

    /** Bloqueo temporal después de 5 intentos fallidos (RF-AUT-02). */
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

    private fun loginWithFingerprint() {
        when {
            !session.biometricEnabled ->
                binding.root.snack(getString(R.string.bio_not_enabled))
            !BiometricHelper.isAvailable(this) ->
                binding.root.snack(getString(R.string.bio_not_available))
            else -> BiometricHelper.authenticate(
                this,
                onSuccess = { enter(session.role ?: Role.BUYER, session.email) },
                onError = { binding.root.snack(it) }
            )
        }
    }

    private fun enter(role: Role, email: String) {
        session.role = role
        session.email = email
        session.name = when (role) {
            Role.ADMIN -> "Ana Ruiz"
            Role.SELLER -> "MotoCentro 80"
            Role.BUYER -> session.name.takeIf { it != "MotoCentro 80" && it != "Ana Ruiz" } ?: "Juan Pérez"
        }
        session.loggedIn = true
        Router.goHome(this, role)
    }

    companion object {
        /** Mínimo 8 caracteres con letras y números (RF-AUT-01). */
        fun isValidPassword(p: String) =
            p.length >= 8 && p.any { it.isLetter() } && p.any { it.isDigit() }
    }
}
