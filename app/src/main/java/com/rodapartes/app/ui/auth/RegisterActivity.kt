package com.rodapartes.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rodapartes.app.data.AuthRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.Role
import com.rodapartes.app.databinding.ActivityRegisterBinding
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Registro de cuenta (RF-AUT-01) como comprador o vendedor. El vendedor queda PENDIENTE hasta que el administrador lo apruebe. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnHaveAccount.setOnClickListener { finish() }

        // Muestra los campos de la tienda solo si elige "Vendedor"
        binding.toggleRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) binding.groupSeller.visible(checkedId == R.id.btnRoleSeller)
        }

        binding.btnRegister.setOnClickListener { register() }
    }

    /** Valida el formulario y registra la cuenta (RF-AUT-01). Los vendedores quedan pendientes. */
    private fun register() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val isSeller = binding.toggleRole.checkedButtonId == R.id.btnRoleSeller

        binding.tilName.error = if (name.length < 3) getString(R.string.error_required) else null
        binding.tilEmail.error = if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.error_email) else null
        binding.tilPhone.error = if (phone.length != 10) getString(R.string.error_phone) else null
        binding.tilPassword.error = if (!LoginActivity.isValidPassword(password)) getString(R.string.password_rule) else null
        if (isSeller) {
            binding.tilStore.error = if (binding.etStore.text.isNullOrBlank()) getString(R.string.error_required) else null
            binding.tilNit.error = if (binding.etNit.text.isNullOrBlank()) getString(R.string.error_required) else null
        }

        val fieldsOk = listOf(binding.tilName, binding.tilEmail, binding.tilPhone, binding.tilPassword)
            .all { it.error == null } && (!isSeller || (binding.tilStore.error == null && binding.tilNit.error == null))
        if (!fieldsOk) return

        if (!binding.cbTerms.isChecked) {
            binding.root.snack(getString(R.string.error_terms))
            return
        }

        // Crea la cuenta en Firebase Auth y el documento users/{uid} con el rol elegido
        val role = if (isSeller) Role.SELLER else Role.BUYER
        binding.btnRegister.isEnabled = false
        binding.btnRegister.setText(R.string.loading)
        lifecycleScope.launch {
            try {
                val user = AuthRepository.register(
                    name = name,
                    email = email,
                    phone = phone,
                    password = password,
                    role = role,
                    storeName = binding.etStore.text.toString().trim(),
                    nit = binding.etNit.text.toString().trim(),
                    promos = binding.cbPromos.isChecked
                )
                Session.user = user
                if (isSeller) {
                    MaterialAlertDialogBuilder(this@RegisterActivity)
                        .setTitle(R.string.register_seller_dialog_title)
                        .setMessage(R.string.register_seller_dialog_msg)
                        .setPositiveButton(R.string.action_understood) { _, _ -> Router.goHome(this@RegisterActivity, Role.SELLER) }
                        .setCancelable(false)
                        .show()
                } else {
                    Router.goHome(this@RegisterActivity, Role.BUYER)
                }
            } catch (e: Exception) {
                binding.btnRegister.isEnabled = true
                binding.btnRegister.setText(R.string.register_button)
                binding.root.snack(AuthRepository.friendlyMessage(e))
            }
        }
    }
}
