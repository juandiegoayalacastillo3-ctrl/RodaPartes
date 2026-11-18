package com.rodapartes.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.util.snack
import com.rodapartes.app.databinding.ActivityRecoverPasswordBinding
import com.rodapartes.app.util.visible
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecoverPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoverPasswordBinding
    private var countdown: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBackToLogin.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { send() }
        binding.btnResend.setOnClickListener {
            lifecycleScope.launch {
                runCatching { AuthRepository.sendPasswordReset(binding.etEmail.text.toString().trim()) }
                startCountdown()
            }
        }
    }

    /** Valida el correo y pide a Firebase el correo de restablecimiento. */
    private fun send() {
        val email = binding.etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        binding.tilEmail.error = null
        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            try {
                // Firebase envía el enlace de un solo uso al correo (RF-AUT-05)
                AuthRepository.sendPasswordReset(email)
                // Misma respuesta exista o no el correo (no revela cuentas)
                binding.tvSentBody.text = getString(R.string.recover_sent_body, email)
                binding.groupForm.visible(false)
                binding.groupSent.visible(true)
                startCountdown()
            } catch (e: Exception) {
                binding.root.snack(AuthRepository.friendlyMessage(e))
            } finally {
                binding.btnSend.isEnabled = true
            }
        }
    }

    /** Evita reenvíos seguidos: el botón se habilita después de 30 s. */
    private fun startCountdown() {
        countdown?.cancel()
        countdown = lifecycleScope.launch {
            binding.btnResend.isEnabled = false
            for (s in 30 downTo 1) {
                binding.btnResend.text = getString(R.string.recover_resend_in, s)
                delay(1000)
            }
            binding.btnResend.text = getString(R.string.recover_resend)
            binding.btnResend.isEnabled = true
        }
    }
}
