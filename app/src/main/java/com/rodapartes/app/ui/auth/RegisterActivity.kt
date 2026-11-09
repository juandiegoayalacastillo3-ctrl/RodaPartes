package com.rodapartes.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.Role
import com.rodapartes.app.databinding.ActivityRegisterBinding
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

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

        val session = Session(this)
        session.name = if (isSeller) binding.etStore.text.toString() else name
        session.email = email
        session.phone = phone
        session.promos = binding.cbPromos.isChecked
        session.role = if (isSeller) Role.SELLER else Role.BUYER
        session.loggedIn = true

        if (isSeller) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.register_seller_dialog_title)
                .setMessage(R.string.register_seller_dialog_msg)
                .setPositiveButton(R.string.action_understood) { _, _ -> Router.goHome(this, Role.SELLER) }
                .setCancelable(false)
                .show()
        } else {
            Router.goHome(this, Role.BUYER)
        }
    }
}
