package com.rodapartes.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
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
        binding.btnResend.setOnClickListener { startCountdown() }
    }

    private fun send() {
        val email = binding.etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        binding.tilEmail.error = null
        // Misma respuesta exista o no el correo (no revela cuentas)
        binding.tvSentBody.text = getString(R.string.recover_sent_body, email)
        binding.groupForm.visible(false)
        binding.groupSent.visible(true)
        startCountdown()
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
